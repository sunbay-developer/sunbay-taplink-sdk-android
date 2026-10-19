# LAN 1006 恢复失败后「终态回调是否必然触发」源码决定性核查

> 只读源码调查，未改动任何代码。所有关键断言附 `文件:行号` + 代码片段。
> 仓库：`/Users/sm4306/StudioProjects/taplinksdk`
> 目标：决定性回答「恢复在 5s 内没重连上时，SDK 是否必然给集成方一个明确的连接失败回调」，并定位最小修复点。

---

## 零、结论摘要（先给答案）

裁定为 **（A）为主、（B）排除、并叠加一个此前两份报告都未覆盖的真实缺陷 D1**。

- **（B）排除**：恢复失败用 `onDisconnected(E213)` 而非 `onError`，是**已批准 spec 的显式契约**，不是语义 bug。
  - 接口契约：`ConnectionListener.onDisconnected` KDoc 明确「已建立连接异常断开 → 先做一次静默恢复 → 恢复最终失败才投一次 `onDisconnected`，错误码 213」；`onError` KDoc 明确「`onError` 仅作用于显式 `connect()`，已建立连接后的 fault 绝不走 `onError`」（`callback/ConnectionListener.kt:31-45`、`callback/ConnectionListener.kt:47-62`）。
  - Spec 依据：`SPEC-ESTABLISHED-RECOVERY-001` 的 SRC-5 / R6 / AC6 均由维护者 2026-10-15 显式批准「恢复期间不发进度回调，仅终局一次结果回调，onError 的回调也不给调用端」「恢复失败 → 一次 `onDisconnected`（E213）」（`docs/spec/coverage/SPEC-ESTABLISHED-RECOVERY-001.yaml` SRC-5、R6/AC6，status `active`/`approved:true`）。
  - 所以「改成 onError」反而会**违反**已批准 spec，不可取。

- **（A）成立为主因**：存在**具体代码路径**使「5s 恢复窗口耗尽后 `settleFailure` 根本不被调用、`onDisconnected(E213)` 永不投递」成为可能——即客户现场「1006 之后 10 分钟 Host 无 CONNECTED、POS 端也无任何回调」的「静默到底」。核心缺陷有两个：

  - **D1（最强、与客户现象最吻合）：恢复 episode 跑在 `Dispatchers.Main` 单线程上，而 5s 超时判定依赖该线程能持续调度 `delay(50ms)` 轮询。一旦 Main 线程在恢复窗口内被阻塞（集成方在回调里做同步耗时/ANR、或 SDK 自身在 Main 上的其他 `sdkScope` 任务占用），`pollUntilReadyOrDeadline` 的 `delay` 续体不被调度，deadline 判定永远跑不到，`settleFailure` 不触发，终态回调「静默到底」。** 证据见 §二。
  - **D2（边界真空，理论上「episode 结束却两不投」的唯一缺口）：`runEpisode` 的所有返回分支都走 settle，`catch` 普通异常也走 `settleFailure`——唯独 `CancellationException` 分支把 settle 的责任「交给取消方」，但取消方 `onExplicitDetachOrDisconnect()` 只在集成方显式 `disconnect()/removeConnectionListener()` 时才清状态、且它本身不投任何回调。因此如果 episode 协程被取消（含 scope 级取消），就会「episode 结束、既不 settleSuccess 也不 settleFailure、无任何回调」。** 证据见 §三.3。

**最小修复方向（不改对外语义，只补「超时必达终态」保障）**：给恢复 episode 加一个**独立于轮询调度、独立于 Main 线程续体**的硬超时兜底（如 `withTimeout(RECOVERY_BUDGET_MS)` 包裹 `runEpisode` 并在 `finally` 里确保 `settleFailure`，或用一个单独的 `delay(RECOVERY_BUDGET_MS)` 看门狗协程在超时后强制 `settleFailure`），并为 `CancellationException` 分支补一个「取消方未投终态则由此处兜底投 E213」的保障。详见 §六。

---

## 一、问题 1：5000ms 预算如何实现？是否有可靠的超时定时器必定触发 settleFailure？

**结论：没有独立看门狗。5s 预算是「事件驱动 + 轮询自查 deadline」的混合，不是一个到点必触发的定时器。**

### 1.1 预算只是一个「截止时间戳」，不是定时器

`RECOVERY_BUDGET_MS = 5000L`（`manager/EstablishedRecoveryCoordinator.kt:66`）。它在 `claimEpoch` 里被加到当前时钟，算出 `deadlineElapsedMs` 存进 episode：

```kotlin
deadlineElapsedMs = clock.elapsedRealtimeMs() + RECOVERY_BUDGET_MS,
```
`manager/EstablishedRecoveryCoordinator.kt:132`

**没有任何 `withTimeout` / `Handler.postDelayed` / 独立 `delay(5000)` 看门狗**在到点时强制收尾。预算只被两处「主动查询」消费：`deadlineReached()`（`:227-228`）和轮询循环 `pollUntilReadyOrDeadline()`（`:230-235`）。

### 1.2 episode 的协程结构：单次 launch，无 withTimeout

```kotlin
episodeJob = scope.launch { runEpisode(episode) }
```
`manager/EstablishedRecoveryCoordinator.kt:142`

`runEpisode`（`:170-218`）顺序执行：`evaluateRecoveryPrerequisite` → `retireForRecovery` → `deadlineReached` 自查 → `openRecoveryRebuild` → `pollUntilReadyOrDeadline` → `validateRecoveredIdentity` → `settleSuccess`，异常落 `catch`。预算**完全靠**这条链路上的 `deadlineReached` 自查点和轮询循环来「发现超时」。

### 1.3 轮询才是「超时 → 失败」的唯一实际触发器

```kotlin
private suspend fun pollUntilReadyOrDeadline(episode: RecoveryEpisode): Boolean {
    while (scope.isActive && !deadlineReached(episode)) {
        if (connectionManager.isRecoveryTransportReady()) return true
        delay(POLL_MS)                      // POLL_MS = 50
    }
    return connectionManager.isRecoveryTransportReady()
}
```
`manager/EstablishedRecoveryCoordinator.kt:230-235`

客户场景（重建一直不 ready）下，`settleFailure` 的实际触发路径是：轮询循环 `deadlineReached` 变 true → 跳出 → `ready=false` → `runEpisode` 里 `if (!ready) { ...; settleFailure(episode); return }`（`:204-209`）。

**关键隐患**：这条超时路径要成立，**必须**让这个 `while` 循环持续得到调度——即 `delay(50ms)` 的续体必须能在 `scope`（生产为 `Dispatchers.Main`）上被反复唤醒，直到 `clock.elapsedRealtimeMs()` 真的越过 deadline。它不是一个独立的、线程无关的定时器。这正是 D1 的根。

---

## 二、D1：恢复 episode 跑在 `Dispatchers.Main`，超时判定依赖 Main 持续调度——Main 被占用即「静默到底」

### 2.1 生产 scope 就是单线程 Main

```kotlin
private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)   // :164
...
private val establishedRecoveryCoordinator: EstablishedRecoveryCoordinator =
    EstablishedRecoveryCoordinator(this, SystemRecoveryClock(), sdkScope)   // :193
```
`manager/ConnectionManager.kt:164`、`:193`

协调器类注释也确认「生产中该 scope 限定到 Dispatchers.Main，形成单 actor 执行路径」（`manager/EstablishedRecoveryCoordinator.kt:140-141` 附近注释）。于是 `runEpisode` 及其 `pollUntilReadyOrDeadline` 的每一次 `delay(50)` 续体都排在 Main 的消息队列里。

### 2.2 时钟是真实 `SystemClock.elapsedRealtime()`

```kotlin
internal class SystemRecoveryClock : RecoveryClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
```
`manager/RecoveryClock.kt:20-22`

`deadlineReached` 读的是真实墙钟（`manager/EstablishedRecoveryCoordinator.kt:227-228`）。真实时间照常流逝，但 **`deadlineReached` 只有在 `delay(50)` 续体被 Main 重新调度、循环回到判定时才会被求值**。如果 Main 在这 5s 窗口内持续被占用，续体排不上，`deadlineReached` 根本不被调用——墙钟过了 10 分钟也没用。

### 2.3 为什么 Main 被占用在本场景很现实

- **集成方回调同步耗时**：SDK 规范本就声明「回调可能在后台线程，需自行切主线程」（`callback/ConnectionListener.kt` 多处 KDoc）。但首个 `onConnected`（onOpen 即投）若集成方在其中做了同步耗时/弹窗/刷 UI 阻塞，恰好 33ms 后 1006 进入恢复，Main 被占住，轮询续体饿死。
- **SDK 自身也往 Main 上丢活**：`sdkScope.launch{}` 还被用于 mDNS 发现（`manager/ConnectionManager.kt:2177`、`:2257`）、`checkLinkAliveAsync`（`:872`）、fast-recovery（`:681`）等。恢复窗口与这些 Main 协程交错时，`delay(50)` 续体的实际唤醒间隔会被拉长甚至饿死。
- **AOA/发现回退**：重建 `connectInternal` 在 LAN 且 `autoReconnect!=false` 时会启用 service discovery fallback（`:410-424` 的 `LanConnectionOptions`，`allowServiceDiscoveryFallback` 等），其回调链也可能回到 Main。

**综合**：在 `Dispatchers.Main` 上用「`delay` 轮询自查 deadline」来实现 5s 超时，对 Main 调度延迟/饿死**不具备鲁棒性**。一旦 Main 在恢复窗口被阻塞，`settleFailure` 不触发、`onDisconnected(E213)` 永不投递——这与客户「1006 后长时间彻底无回调」的现象高度吻合。**这是真 bug（A 类），不是语义问题。**

> 旁证：集成测试 `establishedLinkDropStillNotifiesTheObserver` 必须用反射把 scope 换成 `Dispatchers.Unconfined` 并注入 6 倍快钟，才能让「真实 episode」在 JVM 里结算（`LanRecoveryIntegrationTest.kt:286-292` 的 `installRecoveryTestScope`、注释 `:270-276`）。注释明说「Main-confined sdkScope 在普通 JVM 测试里不派发」。这恰恰暴露：**生产路径对 Main 的调度是强依赖的**，而该依赖从未在接近生产（Main 受压/饿死）的条件下被验证。

---

## 三、问题 2 + 3：settleFailure 的所有触发点，与「episode 结束却两不投」的分支

### 3.1 settleFailure 的全部调用点（`runEpisode` 内，共 5 处 + catch）

`manager/EstablishedRecoveryCoordinator.kt`：
1. prerequisite denied：`:175-178` → `settleFailure(episode); return`
2. 清理后已过 deadline（stuck cleanup 被围栏）：`:188-191` → `settleFailure`
3. 轮询超时 / 重建未 ready：`:204-207` → `settleFailure`（**客户场景的正路**）
4. 身份校验不通过：`:211-214` → `settleFailure`
5. `catch (e: Exception)`：`:221-223` → `settleFailure`

settleSuccess 仅一处：`:216`（身份 OK 之后）。

### 3.2 settleFailure / onRecoveryFailureCleanup 投什么、投给谁

```kotlin
private fun settleFailure(episode: RecoveryEpisode) {
    if (!settled.compareAndSet(false, true)) return          // :247 一次性闸门
    if (currentEpisode?.epoch != episode.epoch) return       // :248 epoch 围栏
    ...
    val reason = "Code: ${InnerErrorCode.E213.code}, Message: ${InnerErrorCode.E213.description}"
    val observers = snapshotObservers()                      // :252
    connectionManager.onRecoveryFailureCleanup(reason)       // :255
    clearStateAfterSettle()
    observers.forEach { it.onDisconnected(reason) }          // :258-266
}
```
`manager/EstablishedRecoveryCoordinator.kt:246-266`

- 投的是 `onDisconnected(E213)`，**不是** `onError`（符合 §0 的 spec 契约）。
- observers 来自 `snapshotObservers()`（`:268-269`）= `episodeObservers`，它在 `claimEpoch` 时由 `addEpisodeObserversFromManager()` 从 `recoveryObservers()` 快照（`:151-156`）。
- `recoveryObservers()` = `listOfNotNull(connectionListener, reconnectManager?.getLastConnectionListener()) + takePendingConnectionListeners()`（`manager/ConnectionManager.kt:1004-1006`）。
- **observer 集合为空的风险**：前一份报告 `lan-connect-listener-recovery-binding.md` 已决定性确认——LAN `connect(config, listener)` 正常路径必走 `reconnectManager.prepareConnect(config, listener)`（`ConnectionManager.kt:329`）把形参 listener 存为 `lastConnectionListener`（`ReconnectManager.kt:89`），`getLastConnectionListener()` 返回它（`ReconnectManager.kt:510-512`），所以即便集成方没调 `setConnectionListener`，形参 listener 也在集合里。**本调查复核一致：observer 集合在正常 LAN connect 场景下非空，不是空快照 bug。** 即「settleFailure 被调用但没人收到」在本场景不成立；真正问题是 **settleFailure 可能根本不被调用**（D1/D2）。

### 3.3 D2：唯一「episode 结束却两不投」的真空——CancellationException 分支

```kotlin
} catch (cancellation: kotlinx.coroutines.CancellationException) {
    // Explicit disconnect/supersede cancelled the episode; settle is owned by the canceller.
    LogUtil.d(TAG, "Recovery episode cancelled (epoch=${episode.epoch})")
    throw cancellation
}
```
`manager/EstablishedRecoveryCoordinator.kt:218-221`

这是**唯一**一条「episode body 结束但不走 settleSuccess 也不走 settleFailure」的路径。它把终态责任「交给取消方」。取消方只有一个：

```kotlin
fun onExplicitDetachOrDisconnect() {
    if (!recovering) return
    settled.set(true)             // 直接标记 settled，但不投任何回调
    episodeJob?.cancel()
    clearStateAfterSettle()
}
```
`manager/EstablishedRecoveryCoordinator.kt:290-296`

- 该方法**只在**集成方显式 `removeConnectionListener()/disconnect()`（或 superseding 新 config connect）时才被调用，且它**不投 onConnected/onDisconnected**——设计上「用户主动断开不需要再补回调」。
- **缺陷**：如果 episode 协程因**其它原因**被取消（例如 `sdkScope` 的 `SupervisorJob` 子 job 被取消、进程/宿主生命周期收尾、或未来有人取消 `episodeJob` 而非走 `onExplicitDetachOrDisconnect`），`CancellationException` 分支吞掉异常、`settled` 可能仍为 false 但 episode 已死，**没有任何终态回调**。这是「episode 结束却静默」的第二个真空。
- 另外 `pollUntilReadyOrDeadline` 的 `while (scope.isActive && ...)`（`:231`）：若 `scope` 被取消（`isActive=false`），循环直接退出并 `return isRecoveryTransportReady()`（`:234`，客户场景为 false）→ 回到 `runEpisode` 正常走 settleFailure。这一条本身**会**投 E213（没问题）；真正危险的是 `delay` 抛 `CancellationException` 被 §3.3 的 catch 吞掉 → 两不投。

### 3.4 其它分支复核：均安全归结到 settle（除 §3.3 外无真空）

- prerequisite `Unsupported`（非 `Denied`）：不进 `:175` 的 Denied 分支，继续往下 → 最终必落某个 settle。`evaluateRecoveryPrerequisite` 在 kernel 缺失时返回 `Unsupported`（`ConnectionManager.kt` 的 override，`?: RecoveryPrereqResult.Unsupported`），**不会**提前 return。
- `retireForRecovery` / `openRecoveryRebuild` / `validateRecoveredIdentity` 抛异常：全被 `catch (e: Exception)` 兜住 → `settleFailure`（`:221-223`）。
- 重建期间又来一个 1006：`claimEpoch` 的 `if (recovering)` 合并分支（`:120-126`），不新开 episode、不新开预算，只并入 observers——安全。
- 重建短暂 ready 又掉：轮询某次 `isRecoveryTransportReady()==true` → settleSuccess 投 onConnected。但 Host 日志证实 10 分钟内无 CONNECTED，故生产中不会误判成功。

**问题 2 的决定性答案**：存在「5s 后 settleFailure 不被调用、E213 永不投递」的真实漏洞，两条：**D1（Main 调度饿死导致轮询超时判定跑不到）** 与 **D2（CancellationException 被吞、取消方不补终态）**。问题 3 的决定性答案：settleFailure **若被调用**，investor 集合在正常 LAN connect 下非空、能收到 E213；空快照不是本场景根因。

---

## 四、问题 3 补充：Cable→LAN 切换会不会在 claimEpoch 前后清掉 listener / pending

**不会清掉形参 listener。**
- `lastConnectionListener` 只在 `ReconnectManager.disconnect()`（手动断开）时置 null（`ReconnectManager.kt:243-244`）。1006 命中拦截时 early-return（`ConnectionManager.kt:481-500`），不进 ReconnectManager 手动 disconnect，所以恢复窗口内 `lastConnectionListener` 保持为本次 LAN connect 的形参 listener。
- Cable→LAN 切换在上层 `ConnectionManager` 走「different config → disconnect 再 connect」（前报告 §5 已证），而新 LAN connect 自己又会 `prepareConnect(lanConfig, lanListener)` 覆盖 `lastConnectionListener` 为 LAN listener——所以切换后 observer 指向的是 LAN 的 listener，正确。
- `captureCurrentTerminalIdentity()` 在 claim 时取（`EstablishedRecoveryCoordinator.kt:129`→`ConnectionManager.kt:captureCurrentTerminalIdentity`），LAN 场景可能为 `TerminalIdentity(null)`，`runEpisode` 用 `episode.expectedIdentity ?: TerminalIdentity(null)` 兜底（`:210`），不会因身份为空而 early-return（身份校验交给 `validateRecoveredIdentity`）。此处无「清空导致两不投」。

结论：切换不改变 observer 绑定正确性，不是本 bug 的根因；根因仍是 D1/D2 的「终态根本没触发」。

---

## 五、问题 4：恢复失败该走 onError 还是 onDisconnected —— 接口契约裁定

**裁定：onDisconnected(E213) 符合接口语义契约，（B）不是缺陷。**

- `onConnected`：物理传输建立、SDK 可接受交易即回调（`callback/ConnectionListener.kt:16-29`）。
- `onDisconnected`：KDoc 原文语义——已建立连接异常断开先做一次静默恢复，**恢复最终失败才投一次 onDisconnected，错误码 213**；恢复成功则投 onConnected（`callback/ConnectionListener.kt:31-45`）。
- `onError`：KDoc 原文语义——**仅作用于显式 connect() 尝试**，已建立连接后的 fault **绝不** 经 onError 上报（`callback/ConnectionListener.kt:47-62`）。
- Spec 背书：`SPEC-ESTABLISHED-RECOVERY-001` R6/AC6（`docs/spec/coverage/SPEC-ESTABLISHED-RECOVERY-001.yaml`）与 SRC-5 维护者 2026-10-15 显式批准该语义，status `active`、`approved:true`。

因此：把恢复失败改成 `onError` 会**违反**已批准 spec。用户诉求「必须给一个明确的连接失败回调」，在契约层面**已经有**正确答案（E213 onDisconnected）；问题不在「用错回调」，而在「该回调在超时路径下可能根本没被投出去」（§二/§三）。

---

## 六、问题 5：现有单测是否真的测过「超时→失败」路径

**结论：从未用「真实 5s 超时 + 生产 Main 调度」驱动过失败路径。所有失败用例要么用假钟瞬跳、要么把 scope 换成 Unconfined，恰好绕开了 D1。**

- `EstablishedRecoveryBudgetTest.deadlineDuringPollingFailsOnceWithOneAttempt`：用 `StepClock(listOf(0,100,200,6000))` 让第 4 次读表直接跳到 6000 越过 deadline，`scope=Dispatchers.Unconfined`，`isRecoveryTransportReady()` 恒 false（`EstablishedRecoveryBudgetTest.kt:67-82`）。**不是等 5s，是假钟瞬跳 + Unconfined 立即派发。**
- `EstablishedRecoveryBudgetTest.deadlineBeforeRebuildFencesWithoutOpeningSuccessor`：`StepClock(listOf(0,6000))`，同样瞬跳 + Unconfined（`:85-97`）。
- `LanRecoveryIntegrationTest.recoveryFailureDeliversOneOutcomeToTransferredQueuedCaller` 与 `establishedLinkDropStillNotifiesTheObserver`：都调用 `installRecoveryTestScope()` 把 coordinator 的 `scope` 反射替换为 `Dispatchers.Unconfined`、`clock` 替换为 6 倍快钟（`LanRecoveryIntegrationTest.kt:286-292`）。注释明说 Main-confined scope 在 JVM 测试里不派发（`:270-276`）。

**缺口（决定了 D1 从未被测到）**：没有任何用例在「`scope=Dispatchers.Main`、Main 线程被占用/饿死、真实 `SystemRecoveryClock`」条件下验证「5s 后 settleFailure 必达」。也没有用例覆盖 D2「episode 被非 `onExplicitDetachOrDisconnect` 路径取消时终态是否仍被补投」。因此 D1/D2 对应的「超时兜底」根本没有回归保护。

---

## 七、最小修复方向（改哪个文件哪几行、改成什么语义）

所有修复都在 `lib_taplink_sdk/.../manager/EstablishedRecoveryCoordinator.kt`，**不改对外回调语义**（仍是恢复失败投一次 `onDisconnected(E213)`），只把「超时必达终态」从「依赖 Main 轮询自查」升级为「线程/调度无关的硬保障」。因此属对已批准行为的**实现健壮性修复**，不改变可观察契约。

### 修复 1（针对 D1，最高优先）：独立看门狗保证超时必触发 settleFailure
在 `claimEpoch` 启动 episode 时，额外启动一个**不依赖轮询**的超时兜底。两种等价实现择一：

- 方案 A：用 `withTimeout` 包裹 rebuild/poll 段，超时异常落入 `catch` 走 `settleFailure`（注意 `withTimeout` 抛的是 `TimeoutCancellationException`，是 `CancellationException` 子类，需在 `runEpisode` 里**单独捕获它并走 settleFailure**，不能被 §3.3 的 `CancellationException` 分支吞掉）。
- 方案 B（侵入更小）：在 `claimEpoch` 里再 `scope.launch { delay(RECOVERY_BUDGET_MS); settleFailure(currentEpisode) }` 作为看门狗，`settled` 的 CAS 闸门（`:247`）天然保证与正常结算互斥、只投一次。
  - 为彻底摆脱「Main 饿死」，建议该看门狗（或整个 episode）运行在**专用单线程调度器**而非 `Dispatchers.Main`，或至少让看门狗使用 `Dispatchers.Default`，使 deadline 不受集成方 Main 阻塞影响。

涉及行：`EstablishedRecoveryCoordinator.kt:142`（episode 启动处）附近新增看门狗；`runEpisode` 的 catch 区（`:218-223`）区分 `TimeoutCancellationException` vs 外部取消。

### 修复 2（针对 D2）：CancellationException 分支补终态兜底
`EstablishedRecoveryCoordinator.kt:218-221` 当前「取消即交给取消方、自己不投」。改为：仅当取消确实来自 `onExplicitDetachOrDisconnect()`（可用一个 `@Volatile var cancelledByExplicitDetach` 标志区分）时才不投；其它任何取消导致 episode 结束而 `settled` 仍为 false 时，兜底 `settleFailure(episode)`，保证「episode 一旦结束，终态回调必达」。

### 修复 3（可选，针对 Main 依赖根因）：把 coordinator 的生产 scope 从 `Dispatchers.Main` 改为专用单线程/`Dispatchers.Default`
`ConnectionManager.kt:193` 当前传入 `sdkScope`（Main）。恢复 episode 的决策逻辑并不需要在 Main 上（最终回调投递可在 observer 侧自行切主线程，契约已声明）。改用独立调度器可从根本上消除「Main 饿死 → 超时判定饿死」。需评估对 `onRecoverySuccess/onRecoveryFailureCleanup` 中触碰的字段的线程可见性（这些字段读写已有 `synchronized(this)` 保护点，见 `ConnectionManager.kt:1903` 附近）。

---

## 八、需要补的回归测试场景

1. **TC-超时必达-Main受压**：`scope=Dispatchers.Main` + 真实（或可控但非瞬跳）时钟，在恢复窗口内让 Main 持续忙（投喂一批 Main 任务模拟饿死/ANR），断言在预算到点后仍 **恰好一次** `onDisconnected(E213)`，证明超时不依赖轮询续体被及时调度。（锁死 D1 修复）
2. **TC-非显式取消兜底**：episode 进行中，通过非 `onExplicitDetachOrDisconnect` 的方式取消 `episodeJob`（或取消父 scope 的子 job），断言 observer 仍收到一次 E213（而非两不投）。（锁死 D2 修复）
3. **TC-仅形参listener-超时失败**：只 `connect(endpoint, listener)`、从不 `setConnectionListener`，驱动 1006 established fault 并走**真实超时**失败，断言该形参 listener 收到恰好一次 `onDisconnected(E213)`（前一份报告 §6.2 已提该缺口，这里叠加「超时驱动」维度）。
4. **TC-看门狗与正常结算互斥**：重建在 deadline 前一刻成功（settleSuccess）而看门狗随后触发，断言只投一次 onConnected、看门狗的 settleFailure 被 `settled` CAS 吞掉。

---

## 九、SDD / SCR 判断

- 已存在 active spec：`SPEC-ESTABLISHED-RECOVERY-001`（`docs/spec/coverage/SPEC-ESTABLISHED-RECOVERY-001.yaml`，status `active`、`approved:true`、v1.2），已覆盖「已建立连接后恢复失败的对外回调语义」（R6/AC6）、单次 5s 预算与 deadline 围栏（R4/AC4、R7/AC7）。
- 本次修复 **不改变** 已批准的可观察契约（仍是失败投一次 `onDisconnected(E213)`、不投 onError、不发中间回调）。它修的是「R4/AC4 所承诺的 5s 预算到点必结算」与「R6 所承诺的 terminal-once 必达」在实现上对 Main 调度/取消的**鲁棒性缺口**——属于让实现回归到已批准 AC 的保障，而非新增/变更行为。
- 因此倾向判断为 **bug-fix 回归，不需要新开 L3 行为变更 SCR**；但由于触碰的是 critical 支付连接路径（R4/R6/R7/R10 关联），建议按 SDK `AGENTS.md §11.1` 走 **L2 记录**（在 `docs/spec/changes/` 补一条说明「实现未满足 AC4/AC6 的超时兜底，本次为达标修复」，附 before/after 与回归用例映射），并运行 `./config/speccheck/spec-check.sh`。若评审认为「新增看门狗/改调度器」构成对 R4「单 5000ms 预算、单 attempt」机制的实现方式变更，需在该 L2 记录中明确「预算语义不变、仅触发机制更鲁棒」，避免被误读为第二次 attempt。

---

## 十、证据索引（关键文件:行号）

- 预算常量 / deadline / 轮询：`EstablishedRecoveryCoordinator.kt:66`、`:132`、`:227-228`、`:230-235`
- episode 启动（单 launch，无 withTimeout）：`EstablishedRecoveryCoordinator.kt:142`
- settleFailure 全部触发点：`EstablishedRecoveryCoordinator.kt:175-178`、`:188-191`、`:204-207`、`:211-214`、`:221-223`
- settleFailure 实现（E213 / observers / 一次性闸门）：`EstablishedRecoveryCoordinator.kt:246-266`、`:268-269`
- CancellationException 两不投真空（D2）：`EstablishedRecoveryCoordinator.kt:218-221`、`:290-296`
- 生产 scope = Main（D1）：`ConnectionManager.kt:164`、`:193`
- 真实时钟：`RecoveryClock.kt:20-22`
- observer 集合来源（非空复核）：`ConnectionManager.kt:1004-1006`、`ReconnectManager.kt:89`、`:243-244`、`:510-512`
- 恢复 host 钩子（retire/openRebuild/isReady/failureCleanup）：`ConnectionManager.kt:1017-1073`
- 重建入口 connectInternal + 内部 listener：`ConnectionManager.kt:1026-1029`、`:65-76`
- 1006 两条拦截入口：`ConnectionManager.kt:481-500`、`:1849-1884`
- 接口契约（onDisconnected/onError 语义）：`callback/ConnectionListener.kt:31-45`、`:47-62`
- Spec（R4/R6/AC4/AC6、SRC-5）：`docs/spec/coverage/SPEC-ESTABLISHED-RECOVERY-001.yaml`
- 测试绕开 D1 的证据：`EstablishedRecoveryBudgetTest.kt:67-97`、`LanRecoveryIntegrationTest.kt:270-292`

---

*报告完。只读核查，未修改任何文件；关键结论均附 `文件:行号` 证据。涉及第三方来源的内容已改写以符合许可要求。*
