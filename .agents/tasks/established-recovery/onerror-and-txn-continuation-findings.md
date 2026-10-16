# 已建立连接后 onError 处理与在途交易恢复 — 只读调查报告

- 仓库：`/Users/sm4306/StudioProjects/taplinksdk`，分支 `dev_pro`
- 调查提交：`fc741dd`（feat: add SDK-managed one-shot recovery for established connections）
- 性质：只读调查，未修改任何文件、未构建/发布
- 术语：E213 = 连接已断开；E232 = 无法连接 Tapro（传输层诊断）；E306 = 超时；E307 = 已有在途交易拒绝新请求

---

## 摘要结论（先给答案）

分两层回答维护者关心的「能否恢复交易回调」：

- **SDK 侧能力：已就绪。** 恢复成功后，SDK **保留**原 `PaymentCallback` 与 `traceId→callback` 映射、**保留**终局去重集，并把新 kernel 的接收路由重挂回同一条 `dataReceiver → ResponseProcessor.processResponse` 链。因此只要续推帧带着**原 traceId**到达 SDK，`ResponseProcessor` 就能按 traceId 命中并把续推进度 + 终局响应投给原调用端的 `PaymentCallback`。恢复失败则统一以**一次** `onDisconnected(E213)` + **一次** `failAllPendingTransactions(E213)` 收口，无合成早期 E306。

- **Host / 硬件端到端：仅 LAN 已有真机验证证据，非 LAN 为阻塞项。** 续推能否真正发生取决于 Host 是否在新连接上按原 traceId 继续回推。
  - **LAN**：Host 侧有 `SPEC-HOST-LAN-TRACEID-REBIND-001 v1.2`（active，已批准，真机验证）——`onOpen` 接纳新连接即把全部在途绑定按 traceId 整体重指向新 sender，可端到端续推。
  - **非 LAN（VSP / RS232 / AOA / 本地 AIDL / SUB_SCREEN）**：Host 端续推**无支持证据**，是 `blockers.md` 的 **B1/B2/B4** 阻塞项；该 Host rebind 规格明确「本次范围仅 LAN；Cable 不做」。SDK 侧的回调保留 + 路由重挂在非 LAN 上也成立，但端到端续推未验证。

一句话：**SDK 侧「能恢复交易回调」的机制已完整就绪；是否真正恢复到调用端，LAN 已验证可行，非 LAN 受 Host/硬件阻塞（B1–B5），不得声称已工作。**

---

## 问题 1：连接【已建立之后】发生异常时，SDK 对 onError 的处理

### 1.1 established 故障从哪里被截获、如何判定「established 而非首次 connect」

判定依据是会话态 `hasEverEstablishedThisSession`，而非错误码推断。
- 字段声明：`ConnectionManager.kt:172` `@Volatile private var hasEverEstablishedThisSession = false`。
- 置位时机：连接到达 CONNECTED 时置 `true`——`updateConnectionStatus()` 的 `CONNECTED` 分支（`ConnectionManager.kt:1131-1133` 附近 `hasEverEstablishedThisSession = true`），以及恢复期间 rebuild 到 CONNECTED 时在 `updateConnectionStatus` 的 recovery 守卫分支里也会置位（`ConnectionManager.kt:1116-1118`）。
- 清零时机：显式 `disconnect()`（`ConnectionManager.kt:746`）与「不同配置的新 connect()」（`ConnectionManager.kt:232`）会把它清零并自增 `currentSessionId`，使下一次 connect 被当作首连。

故障有**两处**被截获，都在归一化为 `TransportFaultEvent` 后交给协调器：
1. **connect-callback 路径**：`connectInternal` 的 `ConnectionCallback.onDisconnected(code,msg)` 最前面（在 `isConnectionError` 分类之前）调用 `establishedRecoveryCoordinator.interceptIfEstablishedFault(...)`（`ConnectionManager.kt:483-492`），命中则 `syncStatusFromKernel(); return`。
2. **status-listener 路径**：`setupKernelStatusListener` 中 kernel 掉到 DISCONNECTED/ERROR 且 SDK 仍 active 时，先同步 `interceptIfEstablishedFault(...)`（`ConnectionManager.kt:1849-1865`，code 填 "1006"），命中则 `return@setStatusChangeListener`。

协调器的门控逻辑：`EstablishedRecoveryCoordinator.interceptIfEstablishedFault()`（`EstablishedRecoveryCoordinator.kt`）——`if (!event.established) return false`（首连/未建立走原策略），established 才 `claimEpoch()`（同步 CAS epoch、置 `recovering=true`）。

### 1.2 onError 现在在什么路径下才触发

`onError` **仅**在显式 `connect()`/首连阶段触发，established 故障**永不**触发 onError。
- 唯一对外发 onError 的位置是 `updateConnectionStatus()` 的 `ERROR` 分支：`listener?.onError(it)` / `connectionListener?.onError(it)` / `notifyPendingConnectionListenersError(it)`（`ConnectionManager.kt:1175-1182` 附近）。
- 该分支在恢复期间被整体短路：`updateConnectionStatus()` 开头 `if (establishedRecoveryCoordinator.isRecovering()) { ...; return }`（`ConnectionManager.kt:1115-1120`）——恢复期间只记录 established 态，不改 connectionStatus、不发任何回调。
- 到达 ERROR 分支的 `isConnectionError==true` 路径（泄漏点 2）本身已被 §1.1 的早返回挡在前面（established 命中即 `return`，根本不会分类）。
- 契约也在公开文档里写明：`ConnectionListener.onError` KDoc（`fc741dd` 改动）——"onError is scoped to an explicit connect attempt only. A fault on a connection that was already established is NEVER reported via onError"。

### 1.3 传输层/内核 error（如 232）在恢复期间走到哪里，是否转成诊断

232 等传输错误被降级为**内部诊断**，不对外成为 onError：
- kernel 侧：`VSPClientKernel.handleConnectionError()`（`VSPClientKernel.kt:913-940`）在 CONNECTED 下仍 `updateStatus(ERROR)` → `notifyConnectionDisconnected(E232.code, ...)` → 立即 `performDisconnect()` 释放 USB。这是 kernel 的诊断/资源职责。
- 但 `notifyConnectionDisconnected` 冒泡到 `ConnectionCallback.onDisconnected(code="232",...)` 后，被 §1.1 的 `interceptIfEstablishedFault` 以 `established=true` 拦截并早返回，**不再**进入 `isConnectionError` 分类，232 不会转成对外 `ConnectionError`/onError。
- `TransportFaultEvent.code/msg` 的 KDoc 明确标注 "internal only, not surfaced to callers"（`RecoveryModel.kt`）。协调器 settle 时对外用的是 E213，不透传 232。

### 1.4 恢复终局各自回调什么；ConnectionListener 是否被移除

- **成功**：`EstablishedRecoveryCoordinator.settleSuccess()` 调 `connectionManager.onRecoverySuccess()` 重挂路由后，对每个去重 observer 调一次 `observer.onConnected(deviceId, taproVersion)`，**不**配 onError/onDisconnected。
- **失败**：`settleFailure()` 先调 `connectionManager.onRecoveryFailureCleanup(reason)`（内部 `failAllPendingTransactions(E213)`），再对每个去重 observer 调一次 `observer.onDisconnected("Code: 213, ...")`，**绝不** onError。E213 常量：`InnerErrorCode.E213`。
- **恢复期间不移除 ConnectionListener**：协调器无任何 remove 逻辑；移除监听器的唯一入口是 `ConnectionManager.removeConnectionListener()`（`ConnectionManager.kt:960-966`）与 `disconnect()`，二者都调 `coordinator.onExplicitDetachOrDisconnect()` 来 fence 当前 episode。`onExplicitDetachOrDisconnect()`（协调器）只取消 episodeJob、不触碰监听器。契约在 ConnectionListener KDoc 亦写明 "The SDK does not remove a registered listener during recovery"。

---

## 问题 2：在途交易在 established 恢复过程中如何被处理/保存

### 2.1 原 PaymentCallback / traceId 映射 / 终局去重状态如何保留

- **traceId→callback 映射常驻**：`PaymentManager` 用 `LocalCallbackManager<InnerCallback?>` 持有映射（`PaymentManager.kt` 的 `callbackManager`），该映射进程内常驻，不随 transport 重建清除；原 `PaymentCallback` 被包进 `createInternalCallback()` 产出的 `InnerCallback` 并以 traceId 登记。恢复期间协调器**不**调 `failAllCallbacks`/`removeCallbackByTraceId`，映射因此被保留。
- **终局去重集保留（关键封堵）**：`ResponseProcessor.processedTerminalEvents`（`ResponseProcessor.kt:40`）以 `"$traceId:$eventCode"` 去重终局事件（`ResponseProcessor.kt:69-78`）。清空入口 `clearProcessedTerminalEvents()`（`ResponseProcessor.kt:441-446`）由 `PaymentManager.onPhysicalDisconnected()` 触发。恢复期间该清空被两道守卫封堵：
  1. `PaymentManager.onPhysicalDisconnected()` 开头 `if (connectionManager.isEstablishedRecoveryActive()) { ...; return }`（PaymentManager，幂等兜底），恢复期间直接 return，不清去重集；
  2. `setupKernelStatusListener` 的 `isSelfHealing` 守卫把 `establishedRecoveryCoordinator.isRecovering()` 并入（`ConnectionManager.kt:1906-1909`），使 `droppedToInactive && wasActive && !isSelfHealing` 为假（`ConnectionManager.kt:1915`），从而**既不**对外发 onDisconnected、**也不** `invoke(connectionDisconnectedListener)`（即不触发 onPhysicalDisconnected 清空链，`ConnectionManager.kt:1926`）。
- **逻辑态投影**：恢复期间 `isConnected()` 强制 false（`ConnectionManager.kt:795-798`）、`isConnecting()` 强制 true（`ConnectionManager.kt:894-896`）、`setupKernelStatusListener` 不把 `connectionStatus` 覆写为 DISCONNECTED/ERROR（`ConnectionManager.kt:1890` 附近 `if (!...isRecovering()) { synchronized{ connectionStatus=newStatus } }`）。这保证恢复期在途交易仍视为"连接中"，不被误判断链。

### 2.2 恢复【成功】时接收路由如何重挂；候选 peer 续推帧是否隔离

- 重挂路由：`ConnectionManager.onRecoverySuccess()`（`ConnectionManager.kt:1036-1041`）调 `registerDataReceiver()` 把**新 kernel** 的 `registerDataReceiver` 接到既有 `dataReceiver`，再 `setupKernelStatusListener()`，并把 `connectionStatus=CONNECTED`。既有 `dataReceiver` 由 `PaymentManager.init` 设为 `processReceivedResponse(...) → responseProcessor.processResponse(..., callbackManager)`，映射与去重集都是原来的——因此续推帧按 traceId 命中原 `PaymentCallback`。
- 重挂发生在终局之前：协调器 `settleSuccess()` 先调 `onRecoverySuccess()`（重挂），**再**对 observer 发 onConnected，KDoc 标注 "re-attach ... BEFORE the earliest continued payload"。
- **候选 peer 身份校验**：重建出现"物理已连"后，协调器先 `validateRecoveredIdentity(expected)`（LAN 比对 mDNS serial、USB 比对 serialNumber、本地 AIDL 比对包名），**校验通过才判成功并重挂路由**。校验在 `onRecoverySuccess()` 之前（`runEpisode` 顺序：ready → validate → settleSuccess）。因此身份未确认前不会把路由挂到候选 peer，不会据候选帧宣告成功；身份失败则走 §2.3 失败收口，**绝不接别的终端**（fail-closed，`expected.isReliable==false` 或 serial 不匹配一律返回 false）。
  - 说明：代码层面是「先校验身份、通过后才重挂路由」，隔离方式是"校验通过前根本不接收路由"；设计 §9 所述"候选帧早到则缓冲/丢弃"属更细粒度的目标，当前实现以"路由在校验后才挂"达成等价隔离效果。

### 2.3 恢复【失败】时在途交易回调如何清理

- 走既有 physical-disconnect / failAllPendingTransactions 路径，**恰好一次**：`EstablishedRecoveryCoordinator.settleFailure()` 用 `settled.compareAndSet(false,true)` 保证只执行一次，调 `connectionManager.onRecoveryFailureCleanup(reason)`。
- `onRecoveryFailureCleanup()`（`ConnectionManager.kt:1048-1058`）调一次 `paymentManager.failAllPendingTransactions(InnerErrorCode.E213.code, ...description)`（最终走 `LocalCallbackManager.failAllCallbacks`），与协调器那一次 `onDisconnected(E213)` 同时发生。
- **无合成早期 E306**：既有 `fastRecoveryFallback`/`tryFastRecovery` 的早期 E306 清理在 established 路径被互斥关闭——`canFastRecover = !establishedRecoveryCoordinator.isRecovering() && computeCanFastRecover(...)`（`ConnectionManager.kt:558-567`），协调器 episode 活跃时绝不触发 legacy fast-recovery 协程，也就不会走 `failAllPendingTransactions(E306,...)`。失败统一以 E213 清一次。

### 2.4 恢复期间新提交的请求是否以 E213 拒绝

是，且在**序列化/发送之前**拒绝、不入队、不重放：
- `PaymentManager` 有 `@Volatile private var establishedRecoveryActive`（由协调器经 `ConnectionManager.setEstablishedRecoveryActive` → `paymentManager.setEstablishedRecoveryActive` 置位，`ConnectionManager.kt:976-979`）。
- `executeSnapshot()` 入口：`if (establishedRecoveryActive) { callback.onFailure(E213, "...(recovery in progress)", ...); return }`（PaymentManager，位于连接状态判断之前）。
- 原有在途请求不受影响，继续用各自的 timeout 等原响应（协调器成功后其续推命中回调；若其 timeout 到期也不触发第二个 episode）。

---

## 问题 3（关键结论）：established 恢复【成功】后能否把在途交易回调恢复到调用端

### 3.1 SDK 侧（callback/traceId 保留 + 接收路由重挂）

**已就绪**。证据链：
- traceId→callback 映射常驻 `LocalCallbackManager`，恢复期间不清（§2.1）。
- 终局去重集 `processedTerminalEvents` 两道守卫封堵清空（§2.1）。
- 成功时 `onRecoverySuccess()` 在终局前重挂 `dataReceiver → ResponseProcessor.processResponse` 到新 kernel（§2.2）。
- `ResponseProcessor.processResponse()` 按 traceId 定位 callback：`getCallbackByTraceId`（续推进度，非终局）/ `getAndRemoveCallbackByTraceId`（终局）（`ResponseProcessor.kt:86-90`），命中后把进度/终局响应投给原 `PaymentCallback`。

因此只要带原 traceId 的续推帧到达 SDK，SDK 能把**续推进度 + 终局响应**正确投回调用端。这一侧有 JVM 单测覆盖（见 §3.3）。

### 3.2 Host 侧续推现状

续推能否真正发生，取决于 Host 是否在新连接上按原 traceId 继续回推：
- **LAN：已支持且真机验证。** `tapro/docs/spec/TAPLINK_LAN_TRACEID_REBIND_SPEC.md`（`SPEC-HOST-LAN-TRACEID-REBIND-001` v1.2，status active，L3 已批准）：§1.1 "真机已验证：改以交易 traceId 为锚点后，掉线重连后 Host 能按原 traceId 把 WAITING_CARD→ONLINE_PROCESSING→PRINTING→COMPLETED 全程推回，SDK 按 traceId 命中回调"；§1.2 规定 `onOpen` 接纳新连接即把全部在途绑定整体重指向新 sender（traceId 锚点）。v1.2 还把触发点从「签到成功回调」反转为「onOpen 接纳即重指」，正是为了配合 SDK 快恢**不重新签到**的事实。
- **非 LAN：无 Host 端到端续推证据，为阻塞项。** 该 Host 规格 §1.2/§1.3 明确「本次范围仅 LAN；Cable（VSP/RS232/AOA）不做」「不改 Cable 续推」。SDK 的 `blockers.md` **B1** 记录：非 LAN 的 Host 端续推需 Host 侧单独批准的 spec/SCR，JVM 只能证明「callback/traceId 映射保留 + 路由重挂」。SUB_SCREEN 屏就绪为 **B2**、本地 AIDL 进程重启语义为 **B4**（进程死亡只恢复响应通道，不等于"可续推执行"）。

### 3.3 哪些 JVM 已验证、哪些是硬件/Host 阻塞

JVM 已验证（`verification.md`，TC-ER-001..015）：
- established 掉线仅一次 E213 disconnect、永不 onError、监听器保留（`EstablishedRecoveryWiringTest.establishedDropYieldsSingleE213DisconnectNoError`，真实 LAN transport）。
- 成功每 observer 恰一次 onConnected 无 onError、失败恰一次 E213 + cleanup 一次、重复故障合并、单次预算/deadline fence、恢复期新请求 E213 拒绝、恢复切换 payment gate、retire 先于 rebuild（`EstablishedRecoveryControllerTest` / `EstablishedRecoveryBudgetTest`）。
- LAN 身份锚点：同 serial 才通过、缺 serial fail-closed、LAN 前置无需 USB 权限（`EstablishedRecoveryAdapterWiringTest`）。
- 回归保持绿：首连恢复、config-setter、payment 结果/INIT。

硬件/Host 阻塞（`blockers.md` B1–B5，均记为 UNVERIFIED，不得声称通过）：
- **B1**：VSP/RS232/AOA 真机 USB serial 锚点可用性 + 非 LAN Host 端续推。
- **B2**：SUB_SCREEN 屏初始化就绪（仅 transport 连上不算成功）。
- **B3**：USB 物理 re-enumeration/attach/拔插/端口断电（R12 预算内等待一次 attach）。
- **B4**：本地 AIDL 进程重启语义（只恢复响应通道）。
- **B5**：AOA Accessory 共享类（仅审计，无公开 client 路由）。

---

## 结论与建议

1. **直接回答维护者的核心问题**：SDK 在 established 恢复成功后，**具备**把在途交易的续推进度 + 终局响应恢复到调用端的完整机制（回调/traceId 保留 + 去重集保留 + 接收路由重挂）。能否真正恢复到调用端分模式：**LAN 已真机验证可行**；**非 LAN（VSP/RS232/AOA/本地 AIDL/SUB_SCREEN）受 Host/硬件阻塞（B1、B2、B4）**，SDK 侧机制成立但端到端未验证，不得对外声称已工作。

2. **onError 语义**：established 故障对外**永不** onError；onError 仅对应显式 connect()/首连。232 等传输错误降级为内部诊断，终局统一走 onConnected（成功）或一次 onDisconnected(E213)（失败）。

3. **建议（非本次实现范围）**：
   - 若要让非 LAN 具备端到端续推，需在 Host（tapro）侧为 Cable/本地 AIDL 建立对应 traceId 续推规格并真机验证（对应 B1/B2/B4），再在 SDK 侧确认 serial 锚点在目标机型可读。
   - §2.2 中「候选 peer 续推帧的隔离」当前以「身份校验通过后才重挂路由」实现等价效果；若需严格满足设计 §9 的"候选帧早到即缓冲/丢弃"，可在重挂前对早到帧增加显式缓冲，属增强项而非缺陷。
   - 发布/文档同步（RELEASE-NOTES、sunbay-open-docs 双语）按 AGENTS §8 的维护者人工节点处理，本调查不触及。

---

## 关键证据索引（文件:行）

| 主题 | 位置 |
|---|---|
| established 门控字段 | `lib_taplink_sdk/.../manager/ConnectionManager.kt:172` |
| connect-callback 拦截 | `ConnectionManager.kt:483-492` |
| status-listener 拦截 | `ConnectionManager.kt:1849-1865` |
| updateConnectionStatus 恢复期短路（挡 onError/onDisconnected） | `ConnectionManager.kt:1115-1120` |
| onError 唯一出口（ERROR 分支） | `ConnectionManager.kt:1175-1182` |
| isConnected 恢复期投影 false | `ConnectionManager.kt:795-798` |
| isConnecting 恢复期投影 true | `ConnectionManager.kt:894-896` |
| isSelfHealing 并入 isRecovering | `ConnectionManager.kt:1906-1909` |
| 不覆写 connectionStatus（恢复期） | `ConnectionManager.kt:1888-1891` 附近 |
| onRecoverySuccess 重挂路由 | `ConnectionManager.kt:1036-1041` |
| onRecoveryFailureCleanup（一次 E213 清理） | `ConnectionManager.kt:1048-1058` |
| canFastRecover 与协调器互斥 | `ConnectionManager.kt:558-567` |
| removeConnectionListener → fence | `ConnectionManager.kt:960-966` |
| 协调器 claim/settle/身份校验/单次预算 | `lib_taplink_sdk/.../manager/EstablishedRecoveryCoordinator.kt` |
| TransportFaultEvent（code 仅内部诊断） | `lib_taplink_sdk/.../manager/RecoveryModel.kt` |
| PaymentManager 恢复 gate / E213 拒绝新请求 / onPhysicalDisconnected 守卫 | `lib_taplink_sdk/.../manager/PaymentManager.kt` |
| 去重集 processedTerminalEvents / processResponse / clear | `lib_taplink_sdk/.../impl/ResponseProcessor.kt:40,52,69-90,441-446` |
| 身份接口（fail-closed 默认 false） | `lib_taplink_communication/.../interfaces/IServiceKernel.kt`、`.../interfaces/recovery/RecoveryIdentity.kt` |
| LAN 身份锚点（mDNS serial） | `lib_taplink_communication/.../lan/LanClientKernel.kt:247+` |
| VSP serial 锚点 + 非交互前置 | `lib_taplink_communication/.../cable/vsp/VSPClientKernel.kt:113+,587+,1101+`；`handleConnectionError`:913-940 |
| 本地 AIDL 包名锚点 | `lib_taplink_communication/.../local/kernel/LocalServiceKernel.kt:72+,147+,328+` |
| ConnectionListener onError/onDisconnected 新 KDoc | `lib_taplink_sdk/.../callback/ConnectionListener.kt`（fc741dd 改动） |
| 阻塞项 B1–B5 | `.agents/tasks/established-recovery/blockers.md` |
| JVM 验证清单 | `.agents/tasks/established-recovery/verification.md` |
| 设计（泄漏点/epoch/收口） | `.agents/tasks/established-recovery/design.md` |
| Host 侧 LAN traceId 续推规格 | `tapro/docs/spec/TAPLINK_LAN_TRACEID_REBIND_SPEC.md`（SPEC-HOST-LAN-TRACEID-REBIND-001 v1.2） |

> 行号基于提交 `fc741dd` 的工作区快照；个别位置为"附近"，以符号名为准定位。
