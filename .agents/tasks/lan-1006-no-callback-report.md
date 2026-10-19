# TapLink SDK — LAN 连接建立后 1006 异常关闭、上层收不到回调 根因调查报告

> 只读调查，未改动任何代码。所有关键断言均附带 `文件路径:行号` 证据。
> 仓库：`/Users/sm4306/StudioProjects/taplinksdk`　分支：`dev_SAF`（HEAD `ab2e3e6`，已并入 `dev_pro`）

---

## 一、结论摘要（先给答案）

**最可能根因：SDK 新引入的「已建立连接一次性静默恢复」机制（EstablishedRecoveryCoordinator，提交 `fc741dd`）在本场景下吃掉了断开回调。**

关键链条是：

1. 客户端 WebSocket **确实 onOpen 成功**（与 Host 日志 `CONNECTED, mode: LAN, clientCount=1` 一致）。SDK 在 `onOpen` 时就把「连接成功」投递给上层 `ConnectionListener.onConnected(...)`，**不需要**等待任何应用层握手/sign-in 往返。
2. 因为连接已经到达过 `CONNECTED`，SDK 把本逻辑会话标记为「已建立」（`hasEverEstablishedThisSession = true`）。
3. 约 33ms 后发生 1006 异常关闭。由于「已建立」成立，这个断开被 `EstablishedRecoveryCoordinator.interceptIfEstablishedFault(...)` **同步拦截并 early-return**：按设计，这期间**不投递 `onError`、不投递任何“过渡态”的 `onDisconnected`、不投递 `onReconnecting`**——完全对调用方静默。
4. 协调器随后发起**唯一一次**同配置、同终端的静默重建（5000ms 预算）。只有当这次重建**最终成功**才投 `onConnected`，**最终失败**才投**一次** `onDisconnected`（固定为 E213，而不是底层 1006）。
5. 因此在 1006 发生后的那段恢复窗口内，上层“看起来没有收到任何回调”是**预期行为**；客户观察到的“没有连接回调”，极大概率是：**要么卡在这 5s 恢复窗口内尚未落地终态**，**要么恢复阶段的 onConnected 已经先触发过、而随后的静默重建失败只补了一个 E213 disconnected（不是客户期待的 1006/onError）**，让集成方误判为“连接回调没来”。

次要可叠加因素（详见第五、六节）：
- 本场景客户端**同时还持有一条 CABLE(USB) 连接**。SDK 的 `TaplinkServiceKernel` 是**单活动内核**模型，切到 LAN 会先 `disconnect()` 掉旧的 CABLE 内核。切换/旧连接清理与新 LAN 建连之间存在**状态与回调竞态窗口**，可能放大“回调丢失”的观感（见 §5）。
- 1006 被 SDK 明确归类为 **“正常断开（normalDisconnect）”而非连接错误**，所以**不会走 onError**，只可能走 disconnected / 恢复路径。这与客户“期待 onFailure/错误回调但没收到”的直觉冲突（见 §3）。

---

## 二、问题 1：LAN 客户端连接的完整代码路径（connect → WebSocket → onOpen → 上层回调）

调用分两层：`lib_taplink_sdk`（对外 API/状态机）→ `lib_taplink_communication`（WebSocket 实现）。`lib_taplink_sdk` 并非纯壳，它承载了连接状态机、重连、恢复协调等全部决策逻辑；真正的 socket I/O 在 `lib_taplink_communication`。

### 2.1 SDK 层入口与协议构建

- `ConnectionManager.connect(config, listener)` 是对外入口，做在途/重连/已连接判重后调用 `connectInternal`。
  - 文件：`lib_taplink_sdk/.../manager/ConnectionManager.kt:263`（`fun connect(...)`）
- `connectInternal` 构建协议串、设置 `CONNECTING`，并调用 `TaplinkServiceKernel.connect(...)`，传入 `ServiceConnectionCallback`。
  - `lib_taplink_sdk/.../manager/ConnectionManager.kt:383`（`private fun connectInternal`）
  - LAN 选项构建（含 `deferHandshakeFailureCallbacks`）：`ConnectionManager.kt:410`-`424`
  - 下发到内核：`serviceKernel?.connect(protocol, appId, secretKey, ..., connectionCallback = object : ServiceConnectionCallback {...})`：`ConnectionManager.kt:416`

### 2.2 内核选择（单活动内核）

- `TaplinkServiceKernel.connect(...)`：根据协议解析出 `LAN`，若当前已有**不同类型**的活动内核（如 CABLE）则先 `disconnect()` 旧内核，再 `currentServiceKernel = serviceKernel`，最后 `serviceKernel.connect(protocol, connectionCallback)`。
  - 文件：`lib_taplink_communication/.../TaplinkServiceKernel.kt:61`（`fun connect`）
  - 旧内核互斥断开：`TaplinkServiceKernel.kt:93`-`106`
  - 注释明确「同一时间只能有一个 serviceKernel 处于连接态」：`TaplinkServiceKernel.kt:38`-`40`、`47`-`50`

### 2.3 内核连接与 WebSocket 建连

- `AsyncServiceKernel.connect(...)`：先 `updateStatus(CONNECTING)` 并回调 `onWaitingConnect()`，保存 `currentConnectionCallback`，再 `performConnect(...)`。
  - 文件：`lib_taplink_communication/.../interfaces/AsyncServiceKernel.kt:76`（`override fun connect`）
  - 保存回调：`AsyncServiceKernel.kt:91`（`currentConnectionCallback = connectionCallback`）
  - `onWaitingConnect()`：`AsyncServiceKernel.kt:89`
- `LanClientKernel.performConnect(...)`：构建 `ws(s)://ip:port` URI，`updateStatusSafely(CONNECTING)`，调用 `connectionManager.connect(uri, deferHandshakeFailureCallbacks=...)`。
  - 文件：`lib_taplink_communication/.../lan/LanClientKernel.kt`，`performConnect` 起始处（方法内关键行：URI 构建、`connectionManager.connect(...)`）：见 `LanClientKernel.kt:530`-`585` 区间。
- `ConnectionManager.connect(...)`（**通信层**，注意与 SDK 层同名但不同类）：创建 `InternalWebSocketClient`（基于 **Java-WebSocket** `org.java_websocket.client.WebSocketClient`），`connectionLostTimeout=60s`，`newClient.connect()`，然后轮询 `isOpen` 直到超时（ws 15s / wss 45s）。
  - 文件：`lib_taplink_communication/.../lan/connection/ConnectionManager.kt:109`（`internal suspend fun connect`）
  - 客户端类型（自研封装 Java-WebSocket，非 OkHttp）：`ConnectionManager.kt:334`（`InternalWebSocketClient : WebSocketClient`）

### 2.4 onOpen → 连接成功回调上送

- WebSocket 打开：`InternalWebSocketClient.onOpen(...)` → `isConnected.set(true)` → `connectionListener?.onConnected()`。
  - 文件：`lib_taplink_communication/.../lan/connection/ConnectionManager.kt:339`-`348`
- 通信层 `ConnectionManager.ConnectionListener.onConnected()` 的实现在 `LanClientKernel.setupConnectionManager()`：
  - `hasEstablishedConnection = true` → `updateStatus(CONNECTED)`（**同步**，使 `canSendData()` 立刻为真）→ 协程内 `notifyConnectionSuccess(connectionData)`。
  - 文件：`lib_taplink_communication/.../lan/LanClientKernel.kt:914`-`984`（`onConnected`），其中 `updateStatus(CONNECTED)` 在 `:919`，`notifyConnectionSuccess` 在 `:981`。
- `AsyncServiceKernel.notifyConnectionSuccess` → `handleConnectionSuccess(callback, extra)` → `callback.onConnected(extraInfoMap)`。
  - `AsyncServiceKernel.kt:300`（`notifyConnectionSuccess`）
  - `BaseServiceKernel.handleConnectionSuccess`：`updateStatus(CONNECTED)` + `connectionCallback.onConnected(extraInfoMap)`：`BaseServiceKernel.kt:133`-`139`
- 回到 SDK 层 `ServiceConnectionCallback.onConnected(extraInfoMap)`：同步 kernel 状态、注册数据接收器、取 deviceId/taproVersion（LAN 模式此时通常为 `"unknown"`，真正信息在首笔 INIT 后补），然后 `updateConnectionStatus(CONNECTED, ...)`。
  - 文件：`lib_taplink_sdk/.../manager/ConnectionManager.kt:426`-`468`
- `updateConnectionStatus(CONNECTED)` 内：设 `hasEverEstablishedThisSession = true`、捕获终端身份锚点，并对所有去重后的 listener 调用 `onConnected(deviceId, taproVersion)`。
  - 文件：`lib_taplink_sdk/.../manager/ConnectionManager.kt:1152`-`1186`（CONNECTED 分支）；`hasEverEstablishedThisSession=true` 在 `:1154`，`l.onConnected(...)` 在 `:1177`。

**小结**：连接成功回调的唯一正路是 `WebSocket.onOpen → 通信层 onConnected → LanClientKernel.notifyConnectionSuccess → SDK ServiceCallback.onConnected → updateConnectionStatus(CONNECTED) → ConnectionListener.onConnected`。整条链路**没有**应用层握手/sign-in 前置条件。

---

## 三、问题 2：成功回调是 onOpen 即投递，还是等握手？33ms 的 1006 会不会让回调永不触发？

- **成功回调在 WebSocket `onOpen` 即投递**，无需等待任何 sign-in/鉴权往返。证据：`ConnectionManager.kt:339`-`348` 的 `onOpen` 直接 `onConnected()`；`LanClientKernel.kt:981` 在 `onConnected` 中立即 `notifyConnectionSuccess`。deviceId/taproVersion 在 LAN 模式下允许为 `"unknown"`，延迟到首笔 INIT 再补（`ConnectionManager.kt:450`-`460`），说明成功回调**不**以拿到设备信息为前提。
- 因此与 Host 日志吻合：Host 看到 `CONNECTED clientCount=1`（onOpen 成立），SDK 侧理应已把 `onConnected` 投给上层（或至少已进入 CONNECTED 状态机）。
- 但 1006 在 33ms 内到来会触发下述恢复/断开逻辑（§4）。**成功回调本身不会因 1006 而“永不触发”**——它在 onOpen 时已有机会触发。真正“看起来没回调”的，是 **1006 之后应有的断开/错误回调被恢复机制吸收**，以及 **onConnected 与随后的静默恢复失败之间的时序错位**。

> 关键澄清：若客户的集成代码期望“onConnected 之后才算连上”，那么在本例里 onConnected 可能**确实触发过**（毫秒级），紧接着 33ms 后链路就塌了。如果集成方把“连接成功”理解为“onConnected 且随后稳定”，就会主观判断“没收到有效连接回调”。

---

## 四、问题 3：1006（onClosed/onFailure）在 SDK 侧如何处理？是否会被吞掉？

### 4.1 通信层：onClose(1006) 如何上送

- `InternalWebSocketClient.onClose(code,reason,remote)`：`isConnected.set(false)`，并且**仅当** `!deferHandshakeFailureCallbacks || opened` 时才 `connectionListener?.onDisconnected(code,...)`。
  - 文件：`lib_taplink_communication/.../lan/connection/ConnectionManager.kt:350`-`357`
  - 本例 `opened=true`（已 onOpen），所以 onClose **会**被上送，不会被“延迟握手失败”策略吞掉。
- `LanClientKernel` 的 `onDisconnected(code,reason,remote)`：
  - 若 `suppressNextDisconnect`（仅在握手失败时置位）则吞掉——**本例不满足**（已建立连接）。
  - 计算 `isError = code == 1006 || code >= 1002`，置 `ERROR` 状态，然后 `notifyConnectionDisconnected(disconnectCode, msg)`，其中 `disconnectCode` 保留真实的 `"1006"`。
  - 文件：`lib_taplink_communication/.../lan/LanClientKernel.kt:985`-`1043`；`isError` 在 `:1021`，`notifyConnectionDisconnected` 在 `:1031`。
- 注意：1006 走的是 `onDisconnected`（内核 ConnectionListener），**不是** `onError`。`onError` 分支仅处理握手失败/异常（`LanClientKernel.kt:1044`-`1071`），而本例是「已建立后掉线」。

### 4.2 SDK 层：1006 被归类为“正常断开”，不投 onError

- `isConnectionError(code,msg)` 中，`"1006"` 被**显式列入 `normalDisconnectCodes`**（已建立连接后的真实掉线），返回 `false` → 不按连接错误处理、不投 `onError`。
  - 文件：`lib_taplink_sdk/.../manager/ConnectionManager.kt:1341`（方法）、`normalDisconnectCodes` 含 `"1006"`：`ConnectionManager.kt:1373`-`1380`，`if (normalDisconnectCodes.contains(code)) return false`：`ConnectionManager.kt:1383`-`1385`。

因此**问题 3 的直接答案**：1006 不会触发 `onError`；它要么走“正常断开 + 自动重连/快速恢复”路径，要么——在本例最关键的情况下——**被 EstablishedRecovery 拦截吸收**（见 4.3）。这正好解释了“为什么没有错误回调”。

### 4.3 【核心】EstablishedRecoveryCoordinator 吞掉断开回调

1006 的断开有两条进入 SDK 的途径，**两条都先过 EstablishedRecovery 拦截**：

- 途径 A（ServiceCallback.onDisconnected）：`ConnectionManager.kt:481`-`500` 处，在任何分类/回调/清理之前先调用 `establishedRecoveryCoordinator.interceptIfEstablishedFault(TransportFaultEvent(..., established = hasEverEstablishedThisSession))`；若返回 true 则 `syncStatusFromKernel(); return`——**不投 onError、不投过渡 onDisconnected、不清在途交易**。
- 途径 B（kernel 状态监听器掉到 DISCONNECTED/ERROR）：`setupKernelStatusListener` 中同样先 `interceptIfEstablishedFault(... code="1006" ...)`，命中则 `return@setStatusChangeListener`。
  - 文件：`lib_taplink_sdk/.../manager/ConnectionManager.kt:1858`-`1884`。

拦截是否生效取决于 `event.established`：

- `interceptIfEstablishedFault`：`if (!event.established) return false`（首连/从未建立则维持旧策略），否则 `claimEpoch`。
  - 文件：`lib_taplink_sdk/.../manager/EstablishedRecoveryCoordinator.kt:104`-`112`。
- 本例链路已经到达 `CONNECTED`（onOpen 成功），`hasEverEstablishedThisSession` 在 `updateConnectionStatus(CONNECTED)`（`ConnectionManager.kt:1154`）处被置 true。**只要该赋值早于 1006 拦截，拦截即成立**。

一旦 `claimEpoch` 成功：

- 恢复期间对调用方**完全静默**：类注释写明「no onReconnecting, no onError, no transient onDisconnected」，每个 observer 只会在终态拿**唯一一次**回调：成功 `onConnected`，失败 `onDisconnected(E213)`。
  - 文件：`EstablishedRecoveryCoordinator.kt:18`-`25`（契约说明）。
- 预算 5000ms（`RECOVERY_BUDGET_MS = 5000L`，`EstablishedRecoveryCoordinator.kt:66`）。
- 期间 `isConnected()` 返回 false、`isConnecting()` 返回 true（投影为“连接中”），且**不会**改写 `connectionStatus` 为终态：
  - `isConnected()` 恢复中强制 false：`ConnectionManager.kt:810`-`814`
  - `isConnecting()` 恢复中强制 true：`ConnectionManager.kt:862`-`866`
- 终态：
  - 成功：`settleSuccess` → `onRecoverySuccess()`（重挂数据接收、状态置 CONNECTED）→ 对 observers 投 `onConnected(deviceId, taproVersion)`。
    - `EstablishedRecoveryCoordinator.kt:221`-`238`；`ConnectionManager.onRecoverySuccess`：`ConnectionManager.kt:1049`-`1055`。
  - 失败：`settleFailure` → `onRecoveryFailureCleanup(E213)` → 对 observers 投**一次** `onDisconnected("Code: 213, ...")`。
    - `EstablishedRecoveryCoordinator.kt:240`-`256`；`ConnectionManager.onRecoveryFailureCleanup`：`ConnectionManager.kt:1061`-`1073`。

**这就是“连接成功但收不到回调”的最强解释**：在 1006 → 恢复窗口期内，SDK 刻意不投任何中间态回调；如果客户在这 5s 内观察，或集成方只盯着 `onError`（永远不会来），或没有接住恢复失败补发的 `onDisconnected(E213)`，就会表现为“连接没有回调”。

> 互斥性风险点：成功回调（onConnected）和失败回调并不会“两边都不投”，但**语义被改写**了——底层真实的 1006 永远不透传给客户，失败统一变成 E213。如果集成方的连接判定逻辑依赖具体错误码或依赖 onError，本质上会“收不到它期待的那个回调”。

---

## 五、问题 4：已有 CABLE(USB) 连接时再发起 LAN，互斥/切换是否误伤新 LAN 连接或丢回调？

本场景**恰好**满足“客户端同时持有一条 CABLE(USB AOA) CONNECTED，再发起 LAN”。核查结论：

1. **SDK 不支持同时持有多条活动连接**，是**单活动内核**模型：
   - `TaplinkServiceKernel.currentServiceKernel` 只保存一个内核，切换类型时 `oldServiceKernel.disconnect()` 后置空再建新内核。
   - 文件：`TaplinkServiceKernel.kt:38`-`40`（单内核注释）、`:93`-`108`（切类型先断旧）、`:117`（`currentServiceKernel = serviceKernel`）、`:122`（`serviceKernel.connect(...)`）。
2. 在 SDK 上层 `ConnectionManager`，若当前状态为 CONNECTED 且新配置不同（CABLE→LAN 属不同配置），会先 `disconnect()` 再连：
   - `ConnectionManager.kt:305`-`312`（different config → disconnect）。
   - 若旧的仍卡在 CONNECTING，`:276`-`296` 会取消旧尝试再新连。
3. **竞态与潜在误伤**：
   - 旧内核 `disconnect()` 是**同步**取消协程 + `performDisconnect()`（`AsyncServiceKernel.kt:170`-`210`），但 LanClientKernel 注释明确承认「取消与旧操作的 finally 可能落在不同调度轮次」，并为此在 `executeConnectionOperation` 里加了 3s 的 `OPERATION_LOCK_TIMEOUT_MS` 等待窗口（`LanClientKernel.kt`，常量定义 `:76` 附近，锁等待逻辑在 `executeConnectionOperation` 开头）。这说明切换期本身存在已知的时序敏感窗口。
   - 回调归属：LAN 内核把成功/失败都投给**自己的** `currentConnectionCallback`（`AsyncServiceKernel.kt:91` 每次 connect 覆盖），SDK 层按 `currentServiceKernel !== kernel` 过滤陈旧回调（`ConnectionManager.kt:1850`-`1852` 状态监听器里 `if (getCurrentServiceKernel() !== kernel) return`；通信层 `ConnectionManager` 也用 `client !== this` 过滤旧 socket，`connection/ConnectionManager.kt:340`、`351`、`372` 等）。所以“旧 CABLE 的 listener 覆盖新 LAN 回调”在设计上被防护了——**不是最可能根因**，但**切换时序**会与 §4 的恢复机制叠加，放大“回调缺失”的观感。
   - 需要特别注意：旧 CABLE 内核 `disconnect()` 会触发其自身的断开回调流程，如果与新 LAN 的 `onWaitingConnect/onConnected` 回调在主线程上交错，集成方若用同一个 ConnectionListener 处理两条链路的事件，可能把“CABLE 的断开”与“LAN 的连接”混淆。

**结论**：SDK 不会“同时持有两条连接”，切换 CABLE→LAN 一定会断旧连。虽然框架对“旧 listener 覆盖新回调”有过滤防护，但**切换竞态 + EstablishedRecovery 静默窗口**同时作用，是本例回调缺失观感的合理放大器。这一条需要**客户端日志**才能坐实是否发生了误断（见 §7）。

---

## 六、问题 5：回调线程/生命周期时序问题

1. **回调线程不一致，且文档已声明**：
   - WebSocket 回调（onOpen/onClose/onError）在 **Java-WebSocket 库的 worker 线程**触发（`connection/ConnectionManager.kt:334`-`374` 的 `InternalWebSocketClient`）。
   - 通信层 LanClientKernel 把成功/断开包进 `launchInScope{}`，运行在 `Dispatchers.IO`（`AsyncServiceKernel.kt:46` 的 `scope = CoroutineScope(SupervisorJob()+Dispatchers.IO+handler)`；`LanClientKernel.onConnected`/`onDisconnected` 内均 `launchInScope`）。
   - SDK 层 `sdkScope` 则是 `Dispatchers.Main`（`ConnectionManager.kt:220` 附近：`CoroutineScope(SupervisorJob()+Dispatchers.Main)`），EstablishedRecovery 的 episode 也跑在该 scope（`EstablishedRecoveryCoordinator.kt:142` 用传入 scope.launch）。
   - 但**最终对 `ConnectionListener.onConnected/onError/onDisconnected` 的调用线程并不保证是主线程**：`updateConnectionStatus`/`notifyConnectionSuccess` 是从内核 IO 协程同步链路上调回来的，SDK 规范（AGENT.md 第 4 节第 10 条）本就要求集成方“回调可能在后台线程，需自行切主线程”。若客户端在回调里直接刷新 UI 又没切主线程，可能表现为“回调像没来”（实际上来了但 UI 没更新/抛异常被吞）。回调里的异常都被 `try/catch` 吞并仅打日志（如 `ConnectionManager.kt:1178`-`1181`、`EstablishedRecoveryCoordinator.kt:232`-`234`）。
2. **listener 注册时序**：
   - `connect(config, listener)` 传入的 `listener` 会被一路带到 `updateConnectionStatus(..., listener=...)`，因此即便集成方没调用 `setConnectionListener` 设全局 listener，connect 时传入的 listener 也会收到回调（`ConnectionManager.kt:1163`-`1176` 去重后通知）。所以“listener 尚未注册导致回调无处投”的经典窗口在正常 connect 流程里不成立。
   - 但 **EstablishedRecovery 的 observer 快照**来自 `recoveryObservers()`：`listOfNotNull(connectionListener, reconnectManager?.getLastConnectionListener()) + takePendingConnectionListeners()`（`ConnectionManager.kt:1004`-`1006`）。注意它**取的是全局 `connectionListener` 和 reconnect/ pending 列表**，**并不包含**当次 connect 传入的那个 `listener` 形参（除非它同时也是全局 listener 或在 pending 列表里）。
   - **这是一个真实的回调丢失风险点**：若集成方**只**把 listener 作为 `connect(config, listener)` 的形参传入、而**从未调用 `setConnectionListener` 设置全局 listener**，那么：
     - 首次 onOpen 的 `onConnected` 能收到（走 `updateConnectionStatus` 的 listener 形参分支）；
     - 但 1006 触发 EstablishedRecovery 后，**恢复终态只通知 `recoveryObservers()`**——此时全局 `connectionListener` 为 null、pending 为空，**该 connect 形参 listener 不在 observer 集合里**，于是**恢复成功的 onConnected / 恢复失败的 onDisconnected(E213) 都投不到这个 listener**。
     - 结果正是“连上了（第一次 onConnected 可能都没赶上观察），随后彻底静默，收不到任何后续回调”。

**结论**：除了 §4 的静默窗口，**“只传 connect 形参 listener、未设全局 listener”叠加 EstablishedRecovery** 会造成**恢复终态回调投递不到该 listener**，这是除“恢复窗口期”外，另一个能让“永远收不到回调”成立的具体时序/生命周期缺陷。需用客户端日志确认集成方是否调用了 `setConnectionListener`。

---

## 七、最可能根因假设（按可能性排序）＋ 需要补充的客户端日志

### H1（最可能）1006 触发 EstablishedRecovery 静默窗口 + 恢复终态回调未落到集成方 listener
- 机制：§4.3 + §6.2。连接已建立 → 1006 被拦截 → 5s 静默恢复 → 若集成方只用 connect 形参 listener 未设全局 listener，则恢复终态（成功 onConnected / 失败 onDisconnected E213）投不到它，表现为“彻底没回调”。
- 需要的客户端日志（TAG / 关键字）：
  - `EstablishedRecovery`：`Claimed established-recovery epoch=`、`Recovery SUCCESS`、`Recovery FAILURE ... one terminal disconnect (E213)`、`Recovery rebuild not ready within budget`、`Recovery prerequisite denied`。
  - `ConnectionManager`（SDK）：`Connection status changed:`、`do not need callback`、`Setting connection listener` / `Removing connection listener`（判断是否设过全局 listener）。
  - 确认集成方是否调用过 `setConnectionListener`（搜集成侧代码/日志）。

### H2 恢复在 5s 预算内失败且集成方未处理 E213 的 onDisconnected
- 机制：§4.3 失败分支只补 `onDisconnected(E213)`，**不透传 1006、不发 onError**。集成方若只监听 onError 或只认“连接回调=onConnected”，会判定“没有连接回调”。
- 需要日志：`EstablishedRecovery: Recovery FAILURE (epoch=...); one terminal disconnect (E213)`；SDK `ConnectionManager` 的 `onRecoveryFailureCleanup` 相关（`failAllPendingTransactions`、状态置 DISCONNECTED）。

### H3 CABLE→LAN 切换竞态：旧 CABLE 内核 disconnect 与新 LAN 建连交错，导致新 LAN 被提前清理或回调混淆
- 机制：§5。单活动内核切换 + 3s 操作锁让渡窗口 + 两条链路共用一个 ConnectionListener。
- 需要日志：
  - `TaplinkServiceKernel`：`Disconnecting old service kernel before connecting new one. Old status=`、`Creating LAN service`。
  - `LanClientKernel`：`Starting CONNECT operation:`、`Another operation is in progress ... rejecting CONNECT after lock timeout`、`Performing LAN connection to:`、`LAN connection established`、`LAN connection closed: code=1006`。
  - `CableAoaHostKernel` / CABLE 相关断开日志的时间戳，确认是否与 LAN 建连交错。

### H4 回调已投递但在集成方侧线程/异常中丢失
- 机制：§6.1。回调非主线程；集成方回调体内异常被 SDK `try/catch` 吞掉只打日志。
- 需要日志：SDK 侧 `Error notifying connection listener:`、`Observer onConnected threw during recovery success:`、`Observer onDisconnected threw during recovery failure:`；以及集成方自身 UI 线程异常。

### H5（较低）握手期误吞——本例基本排除
- `deferHandshakeFailureCallbacks` 只在 **onOpen 之前**失败时吞回调（`connection/ConnectionManager.kt:351`-`356`、`LanHandshakeCallbackOrderTest.kt` 的 `bothHandshakeErrorCloseOrdersAreDeferred` 断言 `events.isEmpty()`）。本例 Host 已确认 onOpen 成功（`opened=true`），故握手期吞回调**不适用**。保留此项仅为完整性。

---

## 八、建议（仅建议，未改动代码）

1. **让 EstablishedRecovery 的 observer 覆盖 connect 形参 listener**（针对 H1/H2，优先级最高）：
   - `recoveryObservers()`（`ConnectionManager.kt:1004`）目前不含当次 connect 传入的 `listener`。建议在进入 connect/connectInternal 时把该 listener 纳入恢复 observer 集合（或在 `claimEpoch` 时把“当前活动 listener”并入），确保恢复终态必定能投到发起连接的调用方。
2. **恢复窗口可观测性**：在 claim/settle 时向上层补一个“恢复中/恢复结果”的可选信号（或至少保证失败分支的 `onDisconnected(E213)` 一定能被任何活动 listener 收到），避免 5s 静默被集成方误判为“无回调”。
3. **文档与集成规范**：在集成指南中强调——(a) 1006 在已建立连接后属“正常断开”，SDK 会做一次 5s 静默恢复，期间无中间回调，失败统一报 E213（不是 1006、不是 onError）；(b) **必须调用 `setConnectionListener` 设置全局 listener**（与 `disconnect()+removeConnectionListener()` 成对），不要只依赖 `connect(config, listener)` 的形参 listener。
4. **CABLE→LAN 切换**（针对 H3）：评估在“已有活动连接且用户显式切到 LAN”场景，是否需要在旧内核完全释放（而非仅 3s 让渡窗口）后再建 LAN，以消除竞态；或在切换时对两条链路使用可区分来源的回调上下文。
5. **复现与定位**：在客户端打开 SDK 调试日志（`logEnabled=true`，仅排障环境），按 H1–H4 的 TAG/关键字抓取一次完整复现日志，即可在 `EstablishedRecovery` 的 `Claimed/SUCCESS/FAILURE` 与 SDK `ConnectionManager` 状态流中坐实究竟停在哪个终态、observer 集合是否包含集成方 listener。

---

## 九、模块调用关系补充（lib_taplink_sdk ↔ lib_taplink_communication）

- `lib_taplink_sdk` **不是**纯壳：它持有连接状态机（`ConnectionStatus`）、重连（`ReconnectManager`）、一次性恢复（`EstablishedRecoveryCoordinator`）、LAN mDNS 设备身份匹配、以及对外 `ConnectionListener` 回调的去重/分发。
- `lib_taplink_communication` 负责传输实现：`TaplinkServiceKernel`（单活动内核调度）、`LanClientKernel`（LAN 生命周期）、`lan/connection/ConnectionManager`（基于 Java-WebSocket 的实际 socket）。
- 两层通过 `com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback`（SDK 侧以别名 `ServiceConnectionCallback` 引入，见 `ConnectionManager.kt` import）和 `IServiceKernel.setStatusChangeListener` 两条通道交互：前者是**主回调路径**，后者用于状态兜底同步。EstablishedRecovery 的拦截在**这两条通道的入口都做了**（§4.3），所以 1006 无论从哪条路进来都会先被恢复机制接管。

---

*报告完。内容基于只读源码核查，未修改任何文件；关键结论均附 `文件:行号` 证据。涉及第三方来源的内容已改写以符合许可要求。*
