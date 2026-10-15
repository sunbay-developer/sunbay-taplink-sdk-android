# 技术设计：已建立连接异常的一次性恢复（SPEC-ESTABLISHED-RECOVERY-001）

- 范围：`taplinksdk` 仓库，worktree `/Users/sm4306/StudioProjects/taplinksdk/.worktrees/established-recovery`
- 权威依据：SPEC-ESTABLISHED-RECOVERY-001 v1.2（active, L3 已批准）、SCR-ESTABLISHED-RECOVERY-001（APPROVED）、coverage R1–R15 / AC1–AC15、实现计划 P1–P7、来源 S1–S14（Q1–Q4 原文裁决）
- 本设计只做"设计"，不改码；技术栈锁定为现有 SDK 栈（Kotlin + kotlinx.coroutines + Android USB/AIDL/WebSocket），不引入新库、不新增公开 API、不暴露 `impl/manager/protocol/persistence/communication` 内部类型。
- 术语：E213 = `InnerErrorCode.E213`（"Connection disconnected"）；E232 = "Unable to connect to Tapro"；E306 = 超时（查询优先）。「terminal callback」= 终局回调。

---

## 1. 概览

今天 SDK 对"已建立连接后的异常断开"的处理分散在各个 kernel 和 `ConnectionManager` 两层，彼此不一致：

- kernel 层（如 `VSPClientKernel.handleConnectionError()`）在检测到断链时**同步**地 `updateStatus(ERROR)` → `notifyConnectionDisconnected(232)` → 立即释放 USB 资源。
- `BaseServiceKernel.updateStatus()` 同步回调 kernel 的 `statusChangeListener`，而 `ConnectionManager.setupKernelStatusListener` 注册的监听器会在 SDK 还以为自己 CONNECTED 时**抢先**把 `onDisconnected` 透传给集成方。
- `ConnectionManager.connectInternal` 里的 `ConnectionCallback.onDisconnected` 又用错误码分类（`isConnectionError`）决定走 ERROR→`onError` 还是 DISCONNECTED→自动重连。
- 既有的"交易中快速恢复"（`tryFastRecovery`/`fastRecoveryFallback`）只在 `autoReconnect=true` **且**有在途交易时才触发，且失败时**提前**对在途交易打 E306——这正是本次要禁止的"合成早期 E306"。

本设计引入一个**连接级、位于协议适配器之上**的恢复协调器 `EstablishedRecoveryCoordinator`（放在 `sdk/manager` 内部包），作为"已建立连接故障"的唯一拥有者。它基于 session/epoch 做故障归一化、同步认领、串行退休旧 epoch、单次同协议/同终端重建、按身份校验收口，并保证对调用端**整个恢复期间静默**、只在终局投递"恰好一次"的回调。所有 kernel 的 established 故障通过一条归一化事件进入协调器，不再各自向上冒泡成 `onError`/`onDisconnected`。首连（未建立）失败路径完全不变。

关键设计选择与理由见后续各节；凡"X 或 Y"的分歧均在本设计中择一并给出理由。

---

## 2. 两个备选架构与选型

**选项 A（否决）**：在每个 kernel 内各自加一次重连循环。否决理由：违反计划 §2/§7"用一个连接级协调器，而非每个 kernel 各写重连循环"；预算会在 kernel 间叠加（R4）；无法集中保证"对调用端静默 + 终局一次"（R6）；无法集中做 epoch 退休与资源围栏（R7）。

**选项 B（选用）**：在 `ConnectionManager` 之内、协议适配器之上放一个 `EstablishedRecoveryCoordinator`，kernel 只负责把"已建立后发生的 transport 故障"归一化上报，不再自行决定对外回调语义。理由：恢复预算、静默、epoch、身份校验、交易续推/清理都集中在一处，天然满足 R4–R12/R15 的"单一拥有者 + 恰好一次"。既有的 `tryFastRecovery`/`ReconnectManager` 入口重构为"要么并入协调器、要么被协调器短路"，避免预算叠加与重复 disconnect（计划 §7）。

技术栈：协调器用 `kotlinx.coroutines`（SDK 已依赖），单调时钟用 `android.os.SystemClock.elapsedRealtime()`（单调、不受墙钟跳变影响；在 JVM 单测中通过注入的 `Clock` 抽象替换，见 §11）。

---

## 3. 组件职责

### 3.1 `EstablishedRecoveryCoordinator`（新增，`sdk/manager/`，`internal`）

单一拥有者。职责：

1. **故障归一化入口** `onTransportFault(fault: TransportFaultEvent)`：接收各适配器/状态链归一化后的故障事件，判断是否属于"已建立连接故障"，若是则认领 epoch 并启动唯一恢复 episode。
2. **epoch 认领/退休**（R5/R7）：持有 `RecoveryEpisode`（见 §3.6），`claimEpoch()` 为**同步**操作，在任何对外终局回调或 PaymentManager 物理断连清理之前完成。
3. **单次重建**（R4）：在 5000ms 单调预算内，用**原始不可变配置**发起恰好一次同协议重建，重建期间 `isConnecting=true`、`isConnected=false`。
4. **身份校验**（R8）：重建出现"物理已连"后，调用对应适配器的身份校验，仅在确认为原终端 + 就绪时才判成功。
5. **终局投递**（R6）：成功 → 对每个去重后的 observer 投一次 `onConnected`；失败 → 投一次 `onDisconnected(E213)`。两种情况都**不触发** `onError`、不移除 `ConnectionListener`。
6. **交易续推/清理编排**（R10/R11/R15）：成功时保留原 `PaymentCallback`/traceId 映射并重接收路由；失败时**在终局那一刻**走既有物理断连清理一次性清掉在途回调。
7. **取代/取消**（R9）：显式 `disconnect()`/新配置 `connect()`/SDK 清理 → 取消并围栏当前 episode。

### 3.2 `TransportFaultNormalizer`（新增，`sdk/manager/`，`internal`）—— 事件归一化

把来自不同层的信号归一化成单一 `TransportFaultEvent`，**不再用错误码 232/213 推断阶段**（计划 §7）。它消费：

- kernel `statusChangeListener` 的 `ERROR`/`DISCONNECTED`（经 `ConnectionManager.setupKernelStatusListener`）；
- `ConnectionCallback.onDisconnected(code,msg)`（`connectInternal` 内）；
  并附带 `sessionId/epoch/cause/established`（是否曾建立）上下文。

`established` 的判定来源：`ConnectionManager` 的会话态——本逻辑 session 内是否出现过 `ConnectionStatus.CONNECTED`（新增 `@Volatile private var hasEverEstablishedThisSession`）。这是 R3 的硬门：`false` 则完全走现有首连策略，协调器不介入。

### 3.3 `IServiceKernel` 身份校验扩展（`lib_taplink_communication/interfaces/`）

新增**内部**接口方法（默认实现，保证各 kernel 不被强制改动，且不进入公开 API）：

```kotlin
// IServiceKernel.kt （内部库，非公开 API）
/** 恢复专用：当前重建出的链路是否指向校验通过的「原终端」。默认 false（无法确认即拒绝，R8）。 */
suspend fun validateRecoveredIdentity(expected: TerminalIdentity): Boolean = false

/** 恢复意图下的只读前置能力评估：现有有效权限/目标是否可用；不得触发任何授权交互（R14）。 */
fun evaluateRecoveryPrerequisite(): RecoveryPrereqResult = RecoveryPrereqResult.Unsupported
```

`TerminalIdentity`、`RecoveryPrereqResult` 为 `communication` 内部类型，不导出。各适配器按 §6 覆写。

### 3.4 `ConnectionManager`（改造，`sdk/manager/`）

- 新增 `hasEverEstablishedThisSession` 会话态；在 `updateConnectionStatus(CONNECTED)` 置 `true`，在显式 `disconnect()`/新配置 `connect()` 开新 session 时清零。
- `connectInternal` 的 `ConnectionCallback.onDisconnected` 分支：**在调用 `isConnectionError` 分类与任何对外回调之前**，先问 `coordinator.interceptIfEstablishedFault(...)`；若被拦截，当前方法**不再**向调用端发 `onError`/`onDisconnected`、**不**调用 `paymentManager.failAllPendingTransactions`、**不**进入 `ReconnectManager`。
- `setupKernelStatusListener` 的 `droppedToInactive && wasActive` 分支：把现有 `isSelfHealing` 守卫扩展为"协调器 episode 活跃"也算 self-healing（即 `coordinator.isRecovering()`），从而抑制该分支把 `onDisconnected` 透传给集成方（R5/R6）。
- `removeConnectionListener()`/`disconnect()` 调用 `coordinator.onExplicitDetachOrDisconnect()`，这是**唯一**允许移除监听器 / 取消 episode 的入口（R9）。注：SDK re-init 走的是**旧 `ConnectionManager` 实例整体销毁**路径（`TaplinkApiImpl` 对旧实例调用 `disconnect()`+`removeConnectionListener()` 后丢弃并新建实例），协调器随旧实例一并取消，这属于显式生命周期重置，**不**构成"恢复期间 SDK 移除监听器"（不违反 S14/R6），见 §13。
- **恢复期间逻辑态投影（R5/AC5，必须新增代码，现状不满足）**：R5/AC5 要求故障被观察后 `isConnected=false && isConnecting=true`。但现状**不**满足——`setupKernelStatusListener`（约 1627–1646 行）在 kernel 掉到 DISCONNECTED/ERROR 时**无条件**执行 `synchronized(this){ connectionStatus = newStatus }`（现有 `isSelfHealing` 守卫只保护"是否对外发 `onDisconnected`/`invoke(connectionDisconnectedListener)`"，**不**保护 `connectionStatus` 字段本身），恢复期间 kernel 的 ERROR→DISCONNECTED 抖动会把 `connectionStatus` 覆盖为 DISCONNECTED，导致 `isConnecting()`（808–813 行，仅读 CONNECTING/WAIT_CONNECTING 或 `reconnectManager.isReconnecting()`；协调器**不是** `reconnectManager`）返回 false、`isConnected()`（713 行起，还会从 kernel 实际状态同步，恢复期 kernel 为 ERROR/DISCONNECTED）返回 false。为满足不变量，**本设计点名以下三处改动**：
  1. **`setupKernelStatusListener`**：在 `synchronized(this){ connectionStatus = newStatus }` 之前加 `coordinator.isRecovering()` 守卫——恢复期间**不**把 `connectionStatus` 覆盖为 DISCONNECTED/ERROR（保持为 CONNECTING）；
  2. **`isConnecting()`**：增加 `|| coordinator.isRecovering()` 项，恢复期间返回 true；
  3. **`isConnected()`**：当 `coordinator.isRecovering()` 为真时**不**从 kernel 同步、强制投影为"连接中"（返回 false，但不把 `connectionStatus` 改写成 DISCONNECTED/CONNECTED）。
  这三处是 `ConnectionManager` 作为对外状态投影唯一来源的硬改动，列入 §6 公共改造清单与 §12 的 R5 映射。

### 3.5 `PaymentManager` / `ResponseProcessor` / `LocalCallbackManager`（最小改造）

- `PaymentManager`：新增 `isEstablishedRecoveryActive` 门（由协调器置位）。`sendPaymentRequest` 入口在**序列化/发送之前**检查该门，为真则直接 `onFailure(E213)` 返回，不入队、不重放（R11）。恢复期间**不得**调用 `failAllPendingTransactions`、不得跑 `processPendingTransaction`（R10）。
- 续推（R15）：成功恢复时**不新建回调、不重放**。接收路由在"最早一帧续推负载到达前"重挂到同一 `LocalCallbackManager`（traceId→callback 映射是进程内常驻，不随 transport 重建而清除）。协调器成功收口后调用 `connectionManager.registerDataReceiver()` 等价路径把新 kernel 的 `registerDataReceiver` 重新接到既有 `dataReceiver`→`ResponseProcessor.processResponse` 链上。
  - **终局去重集 `ResponseProcessor.processedTerminalEvents` 的保留不是"天然"的，必须显式封堵清空链（见 §4 泄漏点 4）**：`PaymentManager.onPhysicalDisconnected()`（约 118–126 行）会调用 `responseProcessor.clearProcessedTerminalEvents()`，它由 `connectionManager.setConnectionDisconnectedListener { onPhysicalDisconnected() }` 注册，而 `connectionDisconnectedListener?.invoke()` 有**两个**独立触发点：(a) `updateConnectionStatus(DISCONNECTED)`（约 983 行）、(b) `setupKernelStatusListener` 的 `droppedToInactive && wasActive && !isSelfHealing` 分支（约 1664 行）。只要恢复期间任一路径触发它，去重集即被清空，续推帧会被当成"新事件"重复投递，违反 R10/R15。因此除保证恢复期间不调用 `failAllCallbacks`/`removeCallbackByTraceId` 外，还必须封堵 `clearProcessedTerminalEvents()`（见 §4.2/§4.4）。
- 失败清理（R10/Q4）：终局失败时，协调器调用既有 `paymentManager.failAllPendingTransactions(E213,...)`（走 `LocalCallbackManager.failAllCallbacks`）**恰好一次**，与那一次 `onDisconnected(E213)` 同时发生。禁止早于/恢复期间调用，禁止合成早期 E306。

### 3.6 内部状态模型

```kotlin
internal data class RecoveryEpisode(
    val sessionId: Long,
    val epoch: Long,
    val deadlineElapsedMs: Long,          // SystemClock.elapsedRealtime() + 5000
    val attemptLimit: Int = 1,
    val originalConfig: ConnectionConfig, // 原 ConnectionConfig 引用（其属性 setter 为 private，恢复期间不被外部改写），作为重建只读配置
    val expectedIdentity: TerminalIdentity?,
    val recoveryIntent: Boolean = true    // 区分「恢复意图」vs「显式用户连接意图」(R14)
)
```

`recoveryIntent=true` 在任何权限/配对分支前被强制检查（R14/§3 规格要求）。**具体落点**（§6 表已逐适配器点名）：VSP 的 `usbManager.requestPermission` 入口 / `permissionReceiver` / `waitingPermission` 分支、RS232 的特权授予兜底入口、AOA 的 switch/re-enumerate/post-enumeration device open 入口——这些会触发授权交互的现有分支，在 `recoveryIntent==true` 且无现有有效权限时，必须由 `evaluateRecoveryPrerequisite()` 短路为失败，**不进入**授权请求；首连/显式 connect（`recoveryIntent` 不适用）的授权行为完全不变。stale 的旧显式连接权限响应不得推进恢复 epoch（AC14）。

---

## 4. 故障拦截点：established 故障如何**绝不**变成公开 `onError`

这是 Q2/R6 最易错处。链路上有**四个**"泄漏点"，设计逐一封堵（第 4 个是去重集被清空，影响 R10/R15 而非直接 `onError`）。所有封堵都发生在**认领 epoch 之后**（R5：先同步认领，再谈回调）。

```
transport 故障（USB read 异常 / socket close / binder died / UART 卡死）
  │
  ├─(a) kernel.handleConnectionError()  →  updateStatus(ERROR/DISCONNECTED)
  │        → BaseServiceKernel.updateStatus 同步回调 statusChangeListener
  │             → [泄漏点1] ConnectionManager.setupKernelStatusListener 的
  │                droppedToInactive&&wasActive&&!isSelfHealing 分支：
  │                   ├─ 把 onDisconnected 透传集成方                （对外终局泄漏）
  │                   └─ invoke(connectionDisconnectedListener)
  │                        → PaymentManager.onPhysicalDisconnected()
  │                           → [泄漏点4] clearProcessedTerminalEvents() 清空去重集
  │
  └─(b) kernel.notifyConnectionDisconnected(code,msg)  （AsyncServiceKernel/各 kernel）
           → ConnectionCallback.onDisconnected(code,msg)  （connectInternal 内）
                → [泄漏点2] isConnectionError==true → updateConnectionStatus(ERROR)
                            → listener.onError(E232) / connectionListener.onError
                → [泄漏点3] isConnectionError==false → DISCONNECTED/ReconnectManager
                            → listener.onDisconnected / 既有 tryFastRecovery
                            → updateConnectionStatus(DISCONNECTED) 亦会
                               invoke(connectionDisconnectedListener) → [泄漏点4]
```

### 4.1 认领时机（R5）

在 `connectInternal` 的 `ConnectionCallback.onDisconnected` 的**最前面**（在 `isConnectionError` 之前）插入：

```kotlin
if (coordinator.interceptIfEstablishedFault(
        TransportFaultEvent(sessionId, currentEpoch, code, msg, established = hasEverEstablishedThisSession))) {
    syncStatusFromKernel()   // 保持内部态；logical CONNECTING
    return                   // 不分类、不回调、不清交易、不进 ReconnectManager
}
```

`interceptIfEstablishedFault` 内部**同步** `claimEpoch()`（CAS 到新 epoch，置 `isRecovering=true`），然后把实际重建调度到协程。认领是同步的，确保在任何监听器能发终局或清交易之前就"占位"（AC5：无论读/写/探测/status/close 事件以何种顺序到达，ownership 先被认领）。因 `claimEpoch()` 在 `isConnectionError` 分类之前同步完成，§4.1 的早返回能阻断 `connectInternal.onDisconnected` 内对 `updateConnectionStatus(DISCONNECTED/ERROR)` 的调用，从而阻断该路径上的 `connectionDisconnectedListener`（泄漏点 4 的触发点 (a)）。

### 4.2 封堵泄漏点 1 + 泄漏点 4 的触发点 (b)（kernel status 透传）

`setupKernelStatusListener` 中把守卫改为：
```kotlin
val isSelfHealing = reconnectManager?.isReconnecting() == true ||
    isFastRecovering || coordinator.isRecovering()
```
恢复期间 kernel 的 `ERROR→DISCONNECTED` 抖动被视为 self-healing，`droppedToInactive && wasActive && !isSelfHealing` 为假，于是该分支**既不**向集成方发 `onDisconnected`，**也不** `invoke(connectionDisconnectedListener)`——因此不会经 `onPhysicalDisconnected()` 清空 `ResponseProcessor` 的终局去重集（封堵泄漏点 4 的触发点 (b)，服务 R5/R6/R10/R15）。注意：该守卫**不**负责保护 `connectionStatus` 字段本身，字段保护是 Finding 1 的独立改动（§3.4 的三处）。

### 4.3 封堵泄漏点 2 / 3

由 §4.1 的早返回统一封堵：一旦被协调器拦截，`isConnectionError` 分类、`onError`、`onDisconnected`、`failAllPendingTransactions`、`ReconnectManager.onDisconnected`、既有 `tryFastRecovery`、以及 `updateConnectionStatus(DISCONNECTED)`（含其 `connectionDisconnectedListener` 调用）都不会执行。
`onError` 由此**仅**保留给首连/显式 `connect()`（`established=false`）路径——这正是 S14/Q2 的裁决："onError 只在 connect() 时有对应回调"。

### 4.4 封堵泄漏点 4 的兜底守卫（去重集清空）

§4.1（触发点 a）与 §4.2（触发点 b）已覆盖恢复期间两条已知触发链。为防御未来新增的、绕过上述两点直达 `connectionDisconnectedListener` 的路径，再加一道**幂等兜底**：`PaymentManager.onPhysicalDisconnected()` 开头增加 `if (connectionManager.isEstablishedRecoveryActive()) return` 守卫（`isEstablishedRecoveryActive` 由协调器置位，见 §3.5），恢复期间**跳过** `clearProcessedTerminalEvents()`。这样即便某条路径意外 invoke 了监听器，去重集也不会在恢复期间被清空，R10/R15 的 "terminal dedup 保留 / 重复抑制" 得到双重保障。失败收口（§7.2）在终局 `failAllPendingTransactions` 时，去重集随会话结束自然失效，无需额外清空。

### 4.5 kernel 层的配合（诊断化，不再对外终局）

kernel 的 `handleConnectionError()` 保持"更新内部状态 + 释放资源"的诊断职责，但其 `notifyConnectionDisconnected(...)` 在 established 场景下不再意味着"对外终局"——因为上层已被 §4.1 拦截。为满足 R7"旧 epoch 的 read/write/status/close/permission/handshake 回调不得污染后继资源"，kernel 的资源释放改为**由协调器在退休旧 epoch 时驱动**（见 §5），而非在 `handleConnectionError` 里立即、无协调地释放。VSP 现有"立即释放"注释描述的"first connect after replug fails"问题，由协调器的"先退休旧资源、await 安全释放、再开后继"顺序统一解决（R7），不再依赖 kernel 各自抢跑。

### 4.6 封堵既有 `tryFastRecovery` 二次触发（R4/AC4，防预算叠加与第二次早期 E306）

§4.1 的早返回只覆盖 `connectInternal.onDisconnected` 内 `isConnectionError==false` 才会调用 `tryFastRecovery` 的入口；但协调器自身在 §5 step 3 复用 `connectInternal(originalConfig, internalRecoveryListener)` 重建时，该 `connectInternal` 内部**仍可能**命中既有 fast-recovery 分支（现有代码在 in-flight + autoReconnect 条件下会以自己的 `FAST_RECOVERY_BUDGET_MS` 另起 `sdkScope.launch` 协程，且失败路径**先打 E306 再 E213**，正是本次要禁止的早期 E306）。为给出硬保证而非口号，**点名改动**：
- 把既有触发判定（`computeCanFastRecover(...)` / `tryFastRecovery` 的进入门）增加 `&& !coordinator.isRecovering()` 条件，确保协调器驱动的 `connectInternal` 重建期间**绝不**再触发 `tryFastRecovery`，从而不叠加第二条恢复协程、不叠加第二个 5000ms 预算；
- `fastRecoveryFallback`/`tryFastRecovery` 失败路径里的 `failAllPendingTransactions(E306, ...)`（约 621–647 行）在 established 恢复路径下**不执行**，在途交易清理统一由协调器 §7.2 在终局以 **E213** 清一次。
恢复期间逻辑态 CONNECTING（Finding 1 的 §3.4 改动）本身也让 `tryFastRecovery` 复用的 `isConnected()` 轮询不会误判，但上述 `!coordinator.isRecovering()` 门才是互斥的硬保证。

---

## 5. epoch 认领 / 退休 / 资源围栏（R5/R7/R9）

协调器单线程化地（单一 `Dispatchers.Main` 限定的 actor 式串行执行，复用 `ConnectionManager.sdkScope`）执行一次 episode：

1. **claim（同步）**：`epoch = epochCounter.incrementAndGet()`；`isRecovering=true`；记录 `deadlineElapsedMs`。
2. **retire 旧 epoch**：取消旧 epoch 的所有协程/Job（接收循环、握手等待、权限等待）；调用旧 kernel 的 `disconnect()`/`performDisconnect()` 等价清理；**await 安全释放**（有限等待，封顶在剩余预算内）。任何旧 epoch 的 read/write/status/close/permission/handshake 回调到达时，先比对 `epoch`，不等于当前 epoch 一律丢弃（在 `setupKernelStatusListener` 已有"kernel !== current"模式，推广为 epoch 比对）——不得关闭后继资源、不得发终局（R7）。
3. **open 后继**：仅在旧资源释放完成后，用 `originalConfig` 发起一次 `connectInternal(originalConfig, internalRecoveryListener)`。`internalRecoveryListener` 是**内部**监听器，吸收重建期间的 `onConnected/onError/onDisconnected`，不转发给集成方。
4. **deadline 围栏**：若 `elapsedRealtime() >= deadlineElapsedMs` 时清理仍卡住，则 fence（放弃等待卡死的清理），不开后继资源，直接走终局失败（R7 末句、AC7）。
5. **收口**：成功 → §7.1；失败/超时 → §7.2。`isRecovering=false`。

"一次"的强约束（R4/AC4）：`attemptLimit=1`，预算到期或首次重建失败即结束 episode，**不**追加 `ReconnectManager`/fast-recovery/mDNS/adaptor 重试链；`maxRetryCount` 不改变单次上限。真正恢复成功的连接若之后再故障，是**新** episode（`epoch` 自增、新 deadline）。

USB 临时缺失（R12/AC12）：重建中若设备尚未枚举/attach，在**剩余预算内**等待一次合格 re-enumeration/attach 后开一次；不立即失败、不重启预算。缺失/被拒权限或缺 AIDL 目标 → `evaluateRecoveryPrerequisite()` 返回失败 → 记为"失败的一次尝试"，不弹权限、不自旋、不切协议/目标。

---

## 6. 各适配器：具体改动点与身份校验

公共改造（上文）：`ConnectionManager`、`PaymentManager`、`ResponseProcessor`（去重集保留）、`TaplinkServiceKernel`（无需改语义，仅被协调器复用其 `getCurrentServiceKernel`/`connect`/`disconnect`）。各适配器只需实现 `validateRecoveredIdentity` 与 `evaluateRecoveryPrerequisite`，并把"故障检测"与"资源释放"解耦（释放交由协调器 retire 驱动）。

| 适配器 | 文件 | 身份锚点（R8） | 恢复前置（R14） | 备注/阻塞 |
|---|---|---|---|---|
| LAN/WebSocket | `communication/lan/LanClientKernel.kt`、`lan/connection/ConnectionManager.kt` | 已绑定的 mDNS serial（`getTargetServiceSerial()`/`parseServiceSerial`）；无可信 serial → 失败不接别的终端 | 无需 USB 权限 | 续推用既有 Host v1.2 onOpen traceId rebind，**不加 sign-in 依赖**（R15/AC15）；端到端依赖 Host，SDK 侧只保证回调/路由保留 |
| Cable USB_VSP | `communication/cable/vsp/VSPClientKernel.kt` | **锚点需新增捕获，非既有能力**：现有代码仅持有 `targetDeviceName` 与 VID/PID（`buildCustomProber`/`ProbeTable`），**未**读取/保存 `UsbDevice.serialNumber`。若该适配器在有访问权限时能从 `UsbDevice.serialNumber` 捕获并在首连成功时保存，则重建时比对该 serial；**若无法获得可信 serial（权限不足/设备不暴露/pre-INIT idle），`validateRecoveredIdentity` 必须返回 false → §7.2 失败收口，绝不接别的终端**。仅 VID/PID/bus path 一律不足 | 恢复进入重建前，若 `recoveryIntent==true` 且无现有有效 USB 权限 → `evaluateRecoveryPrerequisite()` 直接判失败，**不进入** `usbManager.requestPermission` 分支（现有权限请求入口 / `permissionReceiver` / `waitingPermission`），保留 authorization-required 诊断 | `handleConnectionError` 的"立即释放"改为协调器驱动；serial 锚点捕获与可用性属实现期验证项（**B1/§10**）；Host 侧续推未由 LAN-only spec 覆盖 → **阻塞项（见 §10）** |
| RS232 | `communication/cable/serial/SerialServiceKernel.kt` | 同 VSP：USB serial 锚点**需新增捕获**；无可信 serial → `validateRecoveredIdentity` 返回 false → 失败不接别终端 | 同 VSP；并点名**特权授予兜底入口**在 `recoveryIntent==true` 时不得进入（无交互授权） | 同 VSP 的 serial 锚点与 Host 续推阻塞项 |
| USB_AOA Host | `communication/cable/aoa/host/{CableAoaHostKernel,BaseCableAoaHostKernel}.kt` | 校验原 AOA 设备身份（同样**需新增**可信身份锚点捕获，而非假定已有）；attach/detach + handshake；无可信锚点 → 失败不接别终端 | **switch/re-enumerate/post-enumeration device open** 入口在 `recoveryIntent==true` 时不得触发授权 UI / `requestPermission`；stale 权限响应忽略（AC14） | detach 后重建的识别受 pre-INIT idle 限制（接受，Q3）；锚点可用性属 B1/§10 |
| 本地 AIDL（APP_TO_APP/ON_DEVICE） | `communication/local/kernel/LocalServiceKernel.kt` | 仅重绑**同一** package/signature/component；不绑任意匹配 service | 无 USB 权限；缺 AIDL 目标 → 前置失败 | 进程死亡（`onBindingDied`/`onServiceDisconnected`，现发 E213）不得被当作"可续推执行"；重建只恢复响应通道 |
| SUB_SCREEN（VSP + 屏初始化） | 复用 VSP kernel（`ProtocolConfigResolver` 把 SUB_SCREEN 映射到 VSP） | VSP 身份 + **屏初始化就绪**才算成功；仅 transport 连上不算（R8/AC8） | 同 VSP | 屏就绪判定需真机验证 → 阻塞项 |
| AOA Accessory 共享类 | `communication/cable/aoa/accessory/*`、`aoa/core/*`、`aoa/manager/CableAoaErrorHandler.kt` | 不新增公开 client 路由 | — | 仅做失败/清理接线一致性审计（计划/规格要求），不声称真机覆盖 |
| Cloud | — | — | — | N/A：SDK 无 Cloud 连接实体，不新建（`CrossDeviceStrategy` 已排除） |

`ProtocolConfigResolver` 不改：恢复复用 `originalConfig`，保持原协议/配置，天然"同协议、不切换"（R8）。公共 `CROSS_DEVICE` 仍只落到 LAN 或显式 Cable。

---

## 7. 终局回调：如何"恰好一次"

协调器维护本 episode 的 observer 集合（身份去重，复用 `ConnectionFailureDispatch.notifyDistinctConnectionErrors` 的 `IdentityHashMap` 模式，另加一个成功版 `notifyDistinctConnected`）。集合来源：触发故障时的 `listener`、全局 `connectionListener`、恢复期间以**相同配置** `connect()` 排队进来的 caller（R9：same-config 加入当前 episode，不开第二条 transport）。

### 7.1 成功收口（R6 成功分支 / R10 / R15 / AC6 / AC10 / AC15）
1. 身份校验通过 + 适配器就绪（SUB_SCREEN 需屏就绪）。
2. 重接收路由到既有 `LocalCallbackManager`（§3.5），确保"最早一帧续推负载"到达前路由已就位。
3. 对去重后的每个 observer 调一次 `onConnected(deviceId, taproVersion)`；**不**配对任何 `onError`/`onDisconnected`。
4. **不**跑 `processPendingTransaction`、**不**重放、**不**换 callback、**不**清终局去重集（去重集保留由 §4.2 + §4.4 两道守卫封堵 `clearProcessedTerminalEvents()` 保证）；在途交易继续用它自己的超时等待原响应（其超时到期也**不**触发第二次 episode，R11）。
5. `isRecovering=false`；逻辑态回到 CONNECTED。

### 7.2 失败收口（R6 失败分支 / R10 Q4 / AC6 / AC10）
1. 到预算 / 重建失败 / 身份不可确认 / 前置失败。
2. **此刻**（而非更早）调用一次 `paymentManager.failAllPendingTransactions(E213.code, E213.description)` → `LocalCallbackManager.failAllCallbacks`，清/失败在途交易回调。禁止合成早期 E306。
3. 对去重后的每个 observer 调一次 `onDisconnected("Code:213, Message:...")`；**绝不** `onError`。
4. **不**移除 `ConnectionListener`（R6/R9）；监听器仍挂着，终局确实送达。
5. 清理 `connectedDeviceId` 等资源态；`isRecovering=false`；逻辑态 DISCONNECTED。排队 caller 一并收到这一次终局并被释放。

> 既有 `fastRecoveryFallback` 当前就是"失败→E213+清理"的样板，但它在失败时**先打 E306** 再 E213。重构：由协调器 §7.2 承接，E306 早清被移除（§4.6 点名把 `failAllPendingTransactions(E306,...)` 在 established 路径下关闭）；`tryFastRecovery` 的"预算内轮询 `isConnected()`"模式可被协调器复用，但预算改为统一 5000ms 单调时钟、以 `!coordinator.isRecovering()` 门与 `tryFastRecovery`/`ReconnectManager` 互斥，确保不叠加（§4.6、R4/AC4）。

---

## 8. 校验规则（外部输入）

| 输入 | 必/选 | 类型/限制 | 失败行为 |
|---|---|---|---|
| `TransportFaultEvent.established` | 必 | Boolean；来源 `hasEverEstablishedThisSession` | `false` → 不介入，走首连策略（R3） |
| `TransportFaultEvent.epoch` | 必 | Long，与当前 epoch 比对 | 不等于当前 epoch → 丢弃该事件（R7） |
| 预算 | 固定 | 5000ms 单调（`elapsedRealtime`） | 到期 → §7.2 失败收口（R4） |
| `expectedIdentity` | 选（可能为 null） | 内部 `TerminalIdentity` | null 或校验不通过 → §7.2 失败，绝不接别的终端（R8） |
| 恢复期间新交易请求 | — | — | 序列化/发送前 `onFailure(E213)`，不入队/不重放（R11） |
| 恢复前置权限/目标 | — | `RecoveryPrereqResult` | 缺失/被拒 → 记为失败的一次尝试，不弹授权（R12/R14） |

恢复期间逻辑态不变量：`isConnected=false && isConnecting=true`。该不变量**现状不满足**，由 §3.4 点名的三处 `ConnectionManager` 改动强制实现（`ConnectionManager` 是对外状态投影的唯一来源）：恢复期间不把 `connectionStatus` 覆写为 DISCONNECTED/ERROR、`isConnecting()` 增加 `coordinator.isRecovering()`、`isConnected()` 恢复期不从 kernel 同步。

---

## 9. 错误处理（逐操作）

| 操作 | 失败条件 | 可恢复? | 调用端收到 | 日志级别 |
|---|---|---|---|---|
| 认领 epoch | 已有活跃 episode（并发故障） | 合并进当前 episode | 无（静默） | debug |
| 退休旧资源 | 旧 read/close 阻塞 | deadline fence 后继续 | 无 | warn（诊断） |
| 单次重建 | transport 打不开/握手失败 | 否（单次用尽） | §7.2：一次 onDisconnected(E213) | warn |
| 身份校验 | 身份不可确认/被拒 | 否 | §7.2：一次 onDisconnected(E213) | warn（保留诊断 cause） |
| 前置评估（USB/AIDL/权限） | 缺失/被拒 | 否（算失败的一次） | §7.2：一次 onDisconnected(E213)，保留 authorization-required 诊断 | warn |
| 预算超时 | 真实掉线 > 5000ms | 否 | §7.2 | warn |
| observer 回调抛异常 | 集成方回调 throw | 其他 observer 不受影响 | 该 observer 的异常被吞并记录 | error |
| 成功后续推帧先于身份校验到达 | 候选 peer 帧早到 | 隔离/缓冲至校验通过或丢弃 | 不投给原 callback，不据此宣告成功 | debug |

established 故障的 `onError` 对外**一律不发**（R6）。首连/显式 connect 的 `onError` 行为完全不变。

---

## 10. JVM 不可验证项（显式阻塞标注）

以下为硬件/Host 依赖，JVM 单测**无法**证伪，按计划 §3 作为阻塞项记录，不得在实现中声称"已工作"：

- **B1（VSP/RS232/AOA 身份锚点捕获 + 非 LAN 的 Host 端续推）**：
  - **身份锚点**：VSP/RS232/AOA 现有代码**未**捕获 `UsbDevice.serialNumber` 或等价可信终端身份；Spec §4.1 明示该锚点可用性为 Q3 实现期待解决项（"USB serial may be absent or inaccessible … lack of any usable anchor may make idle recovery fail"）。实现期需验证："是否能在有访问权限时捕获 serial 并保存"；在无可信锚点时**必须**按 R8 直接判失败（§6/§7.2），**不得**在实现中声称"锚点已就绪"。
  - **Host 端续推**：现有 Host 续推规格 `SPEC-HOST-LAN-TRACEID-REBIND-001` 仅 LAN、仅 onOpen rebind。非 LAN 适配器的"原响应路径在 transport 丢失后存活并重接到新 sender"需 Host 侧单独批准的 spec/SCR，**不得**悄悄拓宽 LAN Host 安全边界、**不得**加 sign-in 依赖。SDK 侧只能验证"回调/traceId 映射保留 + 路由重挂"，端到端续推为阻塞。
- **B2（SUB_SCREEN 屏初始化就绪）**：屏就绪判定与"transport 连上但屏未就绪不算成功"需真机验证。
- **B3（USB 物理 re-enumeration/attach、拔插、端口断电）**：R12 的"预算内等待一次 attach"真机路径需授权的设备验证。
- **B4（本地 AIDL 进程重启语义）**：进程死亡不得被误表述为"可续推执行"，需真机/集成验证绑定生命周期。
- **B5（AOA Accessory 共享类）**：仅审计，无公开 client 路由，不声称真机覆盖。

---

## 11. 可测性

- **可 JVM 单测（确定性控制器测试）**：epoch 认领/退休顺序、`attemptLimit=1`、deadline/cancel 围栏、重复故障合并、observer 身份去重、旧 epoch 回调不能关闭后继/不能发终局、恢复期间新请求被 E213 拒绝、成功后不重放/不清去重集、失败收口恰好一次（E213 + failAllPendingTransactions 同步一次、无早期 E306）、`onError` 对 established 故障永不触发、`hasEverEstablishedThisSession` 门控首连 vs 已建立。通过把 `SystemClock.elapsedRealtime` 包进可注入的 `internal interface RecoveryClock`（生产用 `elapsedRealtime`，测试用假时钟）实现确定性预算测试。参考既有 `LanRecoveryIntegrationTest`、`LanRecoveryCallbackTest`、`LanHandshakeCallbackOrderTest` 的写法与放置目录。
- **适配器接线测试**：故障归一化、资源退休顺序、就绪判定（可在 JVM 用 fake kernel 做；真硬件部分为 B1–B5 阻塞）。
- **回归**：保留并跑通既有 LAN 首连恢复、config-setter、payment 结果回归（`./gradlew :lib_taplink_sdk:testDebugUnitTest :lib_taplink_communication:testDebugUnitTest`），确保首连/`onError` 语义未回归。
- 真机/后端验证（B1–B5）需单独人工授权，未执行项如实记录、不声称通过。

---

## 12. 设计决策 → R1–R15 / AC1–AC15 映射

| 规则/AC | 设计落点 |
|---|---|
| R1/AC1 | §3.1 协调器为所有实际适配器的统一拥有者；§6 各适配器接入；Cloud N/A |
| R2/AC2 | §3.4 门控只看 `established`，不看 `autoReconnect`；§4.3 拦截与 autoReconnect 无关 |
| R3/AC3 | §3.2 `hasEverEstablishedThisSession` 门；§5"一次"不递归成首连恢复 |
| R4/AC4 | §5 `attemptLimit=1` + 5000ms 单调预算；§7 末不追加 ReconnectManager/fast/mDNS；§2 选项 B 不叠加预算 |
| R5/AC5 | §4.1 同步 `claimEpoch` 先于任何回调/清理；§3.4 **三处点名改动**（`setupKernelStatusListener` 加 `coordinator.isRecovering()` 守卫不覆写 `connectionStatus`、`isConnecting()` 加 `\|\| isRecovering()`、`isConnected()` 恢复期不从 kernel 同步）落实不变量 `isConnected=false/isConnecting=true`；§8 |
| R6/AC6 | §4.2/§4.3 三泄漏点封堵；§7 恰好一次、established 永不 `onError`、不移除监听器、排队 caller 一次收口 |
| R7/AC7 | §5 退休旧 epoch→await 释放→开后继；epoch 比对丢弃陈旧回调；deadline fence |
| R8/AC8 | §3.3 `validateRecoveredIdentity`；§6 各适配器锚点；身份不可确认→§7.2 失败不接别终端；SUB_SCREEN 需屏就绪 |
| R9/AC9 | §3.4 `onExplicitDetachOrDisconnect` 唯一移除入口；§7 same-config 加入、new-config 开新 session；observer 抛异常不阻断他人 |
| R10/AC10 | §3.5 + §7.1 成功不重放/保留映射与去重；§7.2 失败一次性清理、无早期 E306 |
| R11/AC11 | §3.5 `isEstablishedRecoveryActive` 门在发送前 E213 拒绝；原超时继续且不触发二次 episode |
| R12/AC12 | §5 USB 预算内等待一次 attach；§6 前置失败不弹权限/不自旋 |
| R13/AC13 | 无新公开 API、保留签名、后台回调允许；§11 回归；发布/文档走维护者人工节点（AGENTS §8） |
| R14/AC14 | §3.3 `evaluateRecoveryPrerequisite` 只读、`recoveryIntent` 标志先于权限分支；§6 禁 `requestPermission`/配对 UI；stale 权限响应忽略 |
| R15/AC15 | §3.5 + §7.1 重挂接收路由到同一 callback/traceId；LAN 用 Host v1.2 onOpen rebind 不加 sign-in；非 LAN 为 B1 阻塞 |

---

## 13. 不改动 / 边界

- 不新增公开 API、不新增公开协议、不做协议自动切换、不新建 Cloud 实体。
- 不暴露 `impl/manager/protocol/persistence/communication` 内部类型到公开 API（新接口方法在内部库、带默认实现）。
- 不改金额/小费/Refund/Abort 资格/INIT 公开可见性/签名/凭证/生产日志默认值。
- 不打包/发布 AAR、不升级下游依赖、不改 `sunbay-open-docs`——发布与文档同步走 AGENTS §8/§8.1 的维护者人工节点，待确认 Maven 版本后进行。
- Git 提交：英文 Conventional Commits（本仓库规范）；本设计阶段不提交。
- SDK re-init：属整实例生命周期重置——`TaplinkApiImpl` 对旧 `ConnectionManager` 调用 `disconnect()`+`removeConnectionListener()` 后整体丢弃并新建实例，协调器随旧实例一并取消。这是显式生命周期事件，**不**属于"恢复期间 SDK 移除监听器"（不违反 S14/R6）。

---

## 14. 复查发现回应（design-review.md，CHANGES_REQUESTED）

逐条回应评审（2 HIGH + 3 MEDIUM + 2 NIT），均 **address**，与 R1–R15 一致：

- **Finding 1 [HIGH] 恢复期逻辑态投影不满足**（address）：承认现状 `setupKernelStatusListener` 无条件覆写 `connectionStatus`、`isSelfHealing` 守卫不保护字段本身，恢复期 `isConnecting()/isConnected()` 会返回 false，违反 R5/AC5。已在 §3.4 点名**三处**具体改动（status-listener 加 `coordinator.isRecovering()` 守卫不覆写字段、`isConnecting()` 加 `|| isRecovering()`、`isConnected()` 恢复期不从 kernel 同步），并同步 §8 不变量表述（由"已满足"改为"现状不满足→由三处改动强制")与 §12 R5 映射。

- **Finding 2 [HIGH] 去重集被 `onPhysicalDisconnected()→clearProcessedTerminalEvents()` 清空**（address）：承认去重集"天然保留"论证不成立，漏掉了 `connectionDisconnectedListener` 独立清空链。已在 §4 诊断图新增**泄漏点 4**，并点名其两个触发点：(a) `updateConnectionStatus(DISCONNECTED)`、(b) `setupKernelStatusListener` 分支。封堵分三层：§4.1 早返回阻断 (a) 在 `connectInternal` 内的调用；§4.2 把 `isSelfHealing` 扩展为含 `coordinator.isRecovering()` 从而不 invoke 监听器，封堵 (b)；§4.4 新增 `PaymentManager.onPhysicalDisconnected()` 开头 `isEstablishedRecoveryActive()` 幂等兜底守卫，恢复期跳过 `clearProcessedTerminalEvents()`。§3.5、§7.1 第 4 点同步更新。

- **Finding 3 [MEDIUM] USB serial 身份锚点并非既有能力**（address）：§6 的 VSP/RS232/AOA 行已由肯定句改为明确二分——"**需新增捕获**（现有仅 `targetDeviceName`+VID/PID，未读 `UsbDevice.serialNumber`）；有权限时捕获并比对，无可信锚点时 `validateRecoveredIdentity` 返回 false → §7.2 失败不接别终端"。§10 的 B1 扩展为"身份锚点捕获 + 非 LAN Host 续推"两项实现期验证，明确不得声称锚点已就绪。

- **Finding 4 [MEDIUM] `recoveryIntent` 未点名授权分支**（address）：§6 表的"恢复前置（R14）"列已对 VSP 点名 `usbManager.requestPermission`/`permissionReceiver`/`waitingPermission`、RS232 点名特权授予兜底入口、AOA 点名 switch/re-enumerate/post-enumeration device open；§3.6 补充同一组落点清单，明确这些分支在 `recoveryIntent==true` 且无现有有效权限时被 `evaluateRecoveryPrerequisite()` 短路，可被 AC14 覆盖。

- **Finding 5 [MEDIUM] fast-recovery 短路缺硬保证**（address）：新增 §4.6，点名把既有 `computeCanFastRecover(...)`/`tryFastRecovery` 进入门增加 `&& !coordinator.isRecovering()`，确保协调器复用 `connectInternal` 重建时**不**再触发第二条 fast-recovery 协程与第二个预算；并点名 `fastRecoveryFallback`/`tryFastRecovery` 的 `failAllPendingTransactions(E306,...)` 早清在 established 路径下关闭，统一由 §7.2 以 E213 清一次。§7.2 脚注同步。

- **Finding 6 [NIT] "不可变快照"表述不精确**（address）：§3.6 改为"持有原 `ConnectionConfig` 引用（其属性 setter 为 private，恢复期间不被外部改写），作为重建只读配置"。

- **Finding 7 [NIT] re-init 归类未说明**（address）：§3.4 与 §13 补充说明——re-init 为旧 `ConnectionManager` 实例整体销毁（`disconnect()`+丢弃+新建），协调器随旧实例取消，属显式生命周期重置，不构成"恢复期间 SDK 移除监听器"（不违反 S14/R6）。

待确认项（§10 阻塞 B1–B5）须在实现期按 coverage 的 per-adapter evidence 要求补齐真机证据后方可声称端到端续推/硬件路径成立，设计中不得声称已验证。
