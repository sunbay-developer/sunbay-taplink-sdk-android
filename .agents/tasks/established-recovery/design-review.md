# 设计评审：已建立连接异常的一次性恢复（SPEC-ESTABLISHED-RECOVERY-001）

- 评审对象：`.agents/tasks/established-recovery/design.md`
- 权威来源（已逐一通读并核对）：
  - Spec：`docs/spec/TAPLINK_ESTABLISHED_RECOVERY_SPEC.md`（active v1.2, L3 已批准）
  - Coverage：`docs/spec/coverage/SPEC-ESTABLISHED-RECOVERY-001.yaml`
  - 需求来源：`docs/spec/sources/TAPLINK_ESTABLISHED_RECOVERY_REQUEST.md`（S1–S14，Q1–Q4 原文裁决）
  - Plan：`docs/TAPLINK_ESTABLISHED_RECOVERY_IMPLEMENTATION_PLAN.md`
- 评审方式：对设计中所有"现状如此"的断言（方法名、行号、现有行为）回到 worktree 内实际源码核对，不以设计自述为准。
- 背景：本设计已经历一轮 CHANGES_REQUESTED（设计 §14 逐条回应了 2 HIGH + 3 MEDIUM + 2 NIT）。本轮重点是：(1) 验证 §14 的回应是否真的落到实处且代码证据属实；(2) 核对对每条维护者裁决（Q1–Q4、S14）的忠实度；(3) 查找新引入的歧义/缺口。

---

## 一、裁决忠实度核对（易错不变量 a–k）

逐项对照用户原始消息与来源 S10–S14、Spec R1–R15：

- **(a) established 故障绝不以公开 `onError` 冒出** — 设计 §4.3/§9 明确："established 故障的 `onError` 对外一律不发"，`onError` 仅保留给 `established=false` 的首连/显式 connect。忠实于 S14（"onError 只在调用 connect() 时有对应回调"）与 R6。✔
- **(b) 恢复期间无进度/`onError`/中途 `onDisconnected`，transport ERROR/DISCONNECTED 仅内部诊断** — §4.2/§4.5/§9 覆盖；并点名 kernel 的 `notifyConnectionDisconnected` 在 established 场景不再意味"对外终局"。忠实于 S11、R6/R5。✔
- **(c) `ConnectionListener` 绝不被恢复流程移除，仅显式 `removeConnectionListener()`/`disconnect()` 移除** — §3.4 把 `onExplicitDetachOrDisconnect()` 定为唯一移除入口；§13 对 re-init 的归类（整实例销毁，非"恢复期移除监听器"）处理得当。忠实于 S14、R6/R9。✔
- **(d) 每个 observer 恰好一个终局回调（成功 onConnected / 失败 onDisconnected(E213)，绝不 onError）** — §7.1/§7.2 分别给出成功、失败收口，身份去重复用既有 `notifyDistinctConnectionErrors` 模式。忠实于 R6/AC6。✔
- **(e) 单次串行尝试、5000ms 单调预算、不叠加重试链、既有请求超时不触发第二次 episode** — §5 `attemptLimit=1` + `elapsedRealtime` 预算；§4.6 以 `&& !coordinator.isRecovering()` 门与 `tryFastRecovery`/`ReconnectManager` 互斥；§7.1 第 4 点与 R11 明确原超时到期不触发二次 episode。忠实于 S10/Q1、R4。✔
- **(f) 原身份校验，绝不自动切换终端** — §3.3 `validateRecoveredIdentity` 默认 `false`（无法确认即拒），§6 逐适配器身份锚点，身份不可确认→§7.2 失败不接别终端。忠实于 S12/Q3、R8。✔
- **(g) 恢复失败在"单一终局那一刻"经既有物理断连/`failAllPendingTransactions` 清交易回调，无合成早期 E306** — §7.2 第 2 点明确"此刻（而非更早）"，§4.6 关闭 established 路径下的 `failAllPendingTransactions(E306,...)`。忠实于 S13/Q4、R10。✔
- **(h) 恢复成功保留原 PaymentCallback + traceId 映射并重挂接收路由（R15）** — §3.5/§7.1 第 2 点，不新建回调、不重放，重挂 `registerDataReceiver` 到既有 `LocalCallbackManager`。忠实于 S8/R15。✔
- **(i) 恢复期间新请求以 E213 拒绝、不入队** — §3.5 `isEstablishedRecoveryActive` 门在序列化/发送前 `onFailure(E213)`。忠实于 R11/AC11。✔
- **(j) 非交互恢复，无权限/配对 UI** — §3.3/§3.6/§6 `recoveryIntent` + `evaluateRecoveryPrerequisite()` 短路授权分支。忠实于 S6/R14。✔
- **(k) 不泄漏内部类型到公开 API、无新公开 API** — §3.3 新接口方法在 `communication` 内部库带默认实现；§13 明确不暴露 `impl/manager/protocol/persistence/communication`。忠实于 R13、仓库 AGENT §2。✔

结论：11 条易错不变量全部被设计正确承接，无误读裁决。

---

## 二、现状断言的代码核对（Verified Assumptions 见文末）

设计大量引用现有代码的方法名/行号/行为。逐一回到 worktree 源码核对，结果全部属实（细节见文末"Verified Assumptions"）。尤其是上一轮两个 HIGH 的根因，均在真实代码中确认成立：

- **Finding 1（恢复期逻辑态投影不满足）** 的根因属实：`ConnectionManager.setupKernelStatusListener`（实测 1620 起）中 `synchronized(this){ connectionStatus = newStatus }`（实测约 1643 行）发生在 `isSelfHealing` 守卫**之外、之前**，守卫（`isSelfHealing = reconnectManager?.isReconnecting() == true || isFastRecovering`，实测 1659 行）只保护 `connectionDisconnectedListener?.invoke()` 与 `onDisconnected` 透传，**不**保护 `connectionStatus` 字段本身。`isConnected()`（实测 713）会从 kernel 实际状态同步、`isConnecting()`（实测 808）只看 CONNECTING/WAIT_CONNECTING/`reconnectManager`。故恢复期若不新增投影改动，`isConnected()`/`isConnecting()` 确会返回 false，违反 R5/AC5。设计 §3.4 的三处点名改动针对性正确。✔
- **Finding 2（去重集被清空）** 的根因属实：`PaymentManager.onPhysicalDisconnected()`（实测 119 起）调用 `responseProcessor.clearProcessedTerminalEvents()`（实测 125），由 `setConnectionDisconnectedListener { onPhysicalDisconnected() }`（实测 105）注册；`connectionDisconnectedListener?.invoke()` 有两个触发点：`updateConnectionStatus(DISCONNECTED)`（实测 984）与 `setupKernelStatusListener` 分支（实测 1677）。设计三层封堵（§4.1 早返回、§4.2 守卫扩展、§4.4 幂等兜底）定位精确。✔
- **Finding 3（USB serial 锚点非既有能力）** 属实：`VSPClientKernel` 仅持 `targetDeviceName` + VID/PID（`ProbeTable.addProduct(0x16d0, 0x087e, ...)`），全文件**无** `UsbDevice.serialNumber` 读取。设计 §6 改为明确二分（有权限则捕获比对、无可信锚点则 `validateRecoveredIdentity` 返回 false 走失败收口），并在 §10 B1 标为实现期验证项，表述严谨。✔
- **Finding 4（recoveryIntent 授权分支点名）** 属实：`usbManager.requestPermission`、`permissionReceiver`、`waitingPermission` 均在 VSP kernel 实际存在，设计 §6 逐入口点名正确。✔
- **Finding 5（fast-recovery 短路硬保证）** 属实：`tryFastRecovery` 在 `isConnectionError==false` 分支经 `computeCanFastRecover` 进入（实测 488/501），失败路径 `failAllPendingTransactions(E306,...)`（实测 622、630）正是"先 E306 后 E213"，`FAST_RECOVERY_BUDGET_MS=5000`（实测 1884）。设计 §4.6 以 `&& !coordinator.isRecovering()` 门短路、关闭 established 路径下的 E306 早清，定位与手段正确。✔

另核对设计 §3.5/§7 复用的既有设施均真实存在：`internalReconnectListener`（no-op 监听器，实测 65）、`notifyDistinctConnectionErrors`（`ConnectionFailureDispatch.kt`，IdentityHashMap 去重）、`registerDataReceiver`/`setDataReceiver`/`dataReceiver` 接收链、`ResponseProcessor.clearProcessedTerminalEvents`（实测 441）。重挂接收路由与成功版去重分发在既有机制上可行。

---

## 三、R/AC 覆盖完整性

设计 §12 把 R1/AC1–R15/AC15 全部映射到具体设计落点（§3–§7），无遗漏；Cloud 明确 N/A（与 Spec §1、coverage `connection_modes` 一致）。阻塞项（B1–B5）在 §10 显式标注为"JVM 不可验证、不得声称已工作"，与 Plan §3 续推矩阵、coverage 的 per-adapter evidence 要求一致，未冒充已验证。

---

## 四、findings 列表

1. **NIT —— 内部监听器命名不一致（§3.6/§5 vs 现有代码）。** 设计正文把恢复复用的内部监听器称为 `internalRecoveryListener`（§5 step 3），而现有代码里是 `internalReconnectListener`（ConnectionManager.kt:65）。两者指向同一"吸收重建期回调、不转发集成方"的 no-op 模式，但命名差异可能让实现者误以为要新建一个类。
   - 具体修正：在 §5 step 3 注明"复用既有 `internalReconnectListener`（或为恢复语义新建等价内部监听器）"，与现有标识符对齐，避免歧义。

2. **NIT —— `onConnected` 终局投递的 deviceId/taproVersion 来源未写明（§7.1 第 3 点）。** 设计说成功时"对去重后的每个 observer 调一次 `onConnected(deviceId, taproVersion)`"，但未说明恢复场景下这两个值取自何处。恢复成功时尚未重跑 INIT（R10/R15 明确不重放、不重置），而 `connectedDeviceId`/`connectedTaproVersion` 是 INIT 成功时经 `updateDeviceInfo` 填充的既有值。实现者可能误新取或误清。
   - 具体修正：在 §7.1 注明"`onConnected` 使用恢复前已缓存的 `connectedDeviceId`/`connectedTaproVersion`（恢复不重跑 INIT、不改写设备信息）；若原会话尚未 INIT 成功则沿用既有空/默认语义"，与 R10"不重置/不重放"保持一致。

（无 HIGH、无 MEDIUM。）

---

## 五、裁定

- HIGH 数量：0
- MEDIUM 数量：0
- NIT 数量：2

机械裁定：HIGH + MEDIUM = 0 → **APPROVED**。

两个 NIT 为实现期可直接澄清的措辞问题，不改变任何已批准语义，不构成阻塞。设计对 11 条易错不变量全部忠实承接，对上一轮 5 个 HIGH/MEDIUM 的回应均有真实代码证据支撑，R1–R15/AC1–AC15 覆盖完整，阻塞项标注诚实。可进入实现（TDD 按已批准 AC 推进，B1–B5 硬件/Host 续推项须按 coverage per-adapter evidence 在实现期补真机证据，设计中不得声称已验证）。

---

## Verified Assumptions（经源码核对为真）

1. `setupKernelStatusListener` 中 `connectionStatus = newStatus` 的写入在 `isSelfHealing` 守卫之外且先于守卫执行；守卫 `reconnectManager?.isReconnecting() == true || isFastRecovering` 仅门控对外 `onDisconnected`/`connectionDisconnectedListener`（ConnectionManager.kt:1643、1659、1666–1677）。
2. `isConnected()` 会从 kernel 实际状态同步并以 kernel 为准（ConnectionManager.kt:713 起）；`isConnecting()` 仅检查 CONNECTING/WAIT_CONNECTING/`reconnectManager`，不含任何 coordinator（ConnectionManager.kt:808–813）。
3. `connectionDisconnectedListener?.invoke()` 的两个触发点：`updateConnectionStatus(DISCONNECTED)`（ConnectionManager.kt:984）与 `setupKernelStatusListener` 分支（ConnectionManager.kt:1677）。
4. `PaymentManager.onPhysicalDisconnected()`（PaymentManager.kt:119）调用 `responseProcessor.clearProcessedTerminalEvents()`（PaymentManager.kt:125），由 `setConnectionDisconnectedListener`（PaymentManager.kt:105）注册。
5. `VSPClientKernel` 仅持 `targetDeviceName`（:114）+ VID/PID（`ProbeTable.addProduct(0x16d0, 0x087e, ...)` :439），全文件无 `UsbDevice.serialNumber` 读取；授权入口 `usbManager.requestPermission`（:476/:1101）、`permissionReceiver`（:127）、`waitingPermission`（:209）均存在；`handleConnectionError()`（:896）在 CONNECTED 时 `updateStatus(ERROR)` + `notifyConnectionDisconnected(E232)`，与设计/来源 S4 描述一致。
6. fast-recovery：`computeCanFastRecover`（ConnectionManager.kt:550）→ `tryFastRecovery`（:594）在 `isConnectionError==false` 分支（:488/:501）进入；失败路径 `failAllPendingTransactions(E306,...)`（:622、:630）为"先 E306 后 E213"；`FAST_RECOVERY_BUDGET_MS=5000L`（:1884）。
7. 复用设施真实存在：`internalReconnectListener`（ConnectionManager.kt:65，no-op）、`notifyDistinctConnectionErrors`（ConnectionFailureDispatch.kt:9，含"每 observer 一次"注释）、`registerDataReceiver`/`setDataReceiver`/`dataReceiver`（ConnectionManager.kt:895/878/147）、`ResponseProcessor.clearProcessedTerminalEvents`（ResponseProcessor.kt:441，操作 `processedTerminalEvents`）。
8. `isConnectionError`（ConnectionManager.kt:1118）在 `connectInternal.onDisconnected`（:431）中分类，设计 §4.1 的"在 `isConnectionError` 之前早返回"位置可行。

## Unverified / Wrong Assumptions

- 未发现设计中与源码相悖的断言（无 Wrong）。
- 以下为设计已自行标注、本评审亦无法在 JVM/静态层证伪、留待实现期真机/Host 验证的项（设计 §10 B1–B5，与 coverage per-adapter evidence 一致，设计未冒称已验证）：
  - B1：VSP/RS232/AOA 的可信身份锚点能否在有权限时捕获；非 LAN 适配器 Host 端续推是否存活（现 Host 续推规格仅 LAN、onOpen rebind）。
  - B2：SUB_SCREEN 屏初始化就绪判定。
  - B3：USB 物理 re-enumeration/attach、拔插、端口断电的预算内一次等待。
  - B4：本地 AIDL 进程重启的绑定生命周期语义。
  - B5：AOA Accessory 共享类仅审计、无公开 client 路由。
  - 这些均不改变本轮 APPROVED 裁定，但须在实现期按 coverage 要求补齐证据后方可声称端到端成立。
