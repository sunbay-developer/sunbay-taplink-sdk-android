# 交易进行中断链重连后进度/结果投递能力调查

> 只读静态代码调查。覆盖 taplinksdk（SDK/接收方）、tapro（Host/发送方）、taplinkdemo（集成示例）。
> 场景：交易在途时 LAN 连接被动断开，随后（快速或常规）重连成功，交易的进度/结果能否在
> Host 端继续发送、SDK 端继续收到并回调给集成方。

---

## 摘要结论（先看这里）

**结论：当前实现无法保证「交易进行中断链→重连成功后，两边自动续上进度/结果」。存在一个确定的跨仓库端到端缺口。**

- **SDK 接收方（A）**：在途回调按 `traceId` 注册、保存在 `LocalCallbackManager.traceIdCallbackMap`。
  - 快速恢复（OPT-R1）成功分支**不**调用 `onPhysicalDisconnected()`，回调**得以保留**（符合前一轮 review 结论）。✅
  - 但快速恢复期间存在**伪断开风险**：`setupKernelStatusListener` 的 `isSelfHealing` 只判 `isReconnecting()`，**未判 `isFastRecovering`**。内核在快恢窗口内中间态落到 `DISCONNECTED` 时，会触发 `connectionDisconnectedListener` → `onPhysicalDisconnected()` → `clearProcessedTerminalEvents()`，削弱去重保护。⚠️（有缺口）
  - SDK **没有**「重连后主动 query 在途交易」的机制；`query()` 是集成方手动调用的公开 API。❌
- **Host 发送方（B）**：Host 是 LAN WebSocket **服务端**。交易响应按 `traceId → sender` 绑定，**绑定只在请求入站时建立**。客户端断开（WebSocket `onClose`）会 `invalidateResponseSender(sender)` **清掉绑定**，且重连后**没有任何重绑/缓存重发**逻辑。交易在 Host 侧继续跑完，但完成时 `resolveResponseSender` 返回 null，响应被丢弃（日志 "no valid bound response sender"）。**这是核心缺口。** ❌
- **协议/会话层（C）**：进度/结果是**单向推送**（Host→SDK，`responseSender.sendData`）。每个请求（含原交易、每次 QUERY）都用**全新随机 traceId**（`UUID.randomUUID()`）。SDK 重连后即使保留了原 traceId 的回调，Host 也**无法**对原 traceId 续推（绑定已失效且不重建）。唯一能拿回结果的路径是集成方**用新 QUERY（新 traceId）**主动查询——那条链路能工作（新请求会重新绑定），但原在途回调只能挂到 **180s 超时**（E306）。

**净结果**：
- 快速恢复成功 **且** 未被伪断开误伤时：SDK 回调还在，但 **Host 不会主动续推** → 原交易回调仍会 180s 超时，除非集成方手动 QUERY。
- 常规重连成功：SDK 侧 `onPhysicalDisconnected()` 已清在途 auto-connect 标志与 terminal 去重；Host 侧绑定也已失效。两边都不续推，必须靠集成方手动 QUERY（新 traceId）。

要实现用户要的「两边都能发送/收到进度/结果」，**必须新增 Host 侧重连重绑/结果重发 + 协议侧会话续连（稳定会话键）机制**（详见文末清单）。

---

## A. SDK 接收方 —— 重连后能否承接进度/结果并回调集成方

### A1. 在途回调的注册/分发 key 与超时、匹配、失败逻辑

**key = `traceId`（String）**，不是 transactionRequestId。

- 注册：`PaymentManager.sendPaymentRequest()` 调 `callbackManager.addTransactionCallback(basicRequest.traceId, internalCallback, isApp2App, requestTimeoutMillis)`
  （`taplinksdk/lib_taplink_sdk/.../manager/PaymentManager.kt:447-490`）。
- 存储：`LocalCallbackManager.traceIdCallbackMap: ConcurrentHashMap<String, CallbackWrapper<T>>`
  （`taplinksdk/lib_taplink_communication/.../util/LocalCallbackManager.kt:44-46`，`addCallbackByTraceId` 于 `:68-98`）。
- 匹配：收到响应 → `ResponseProcessor.processResponse()` 解析 `traceId` →
  - 进度（非终态）：`callbackManager.getCallbackByTraceId(traceId)`（**不移除**）
  - 终态（COMPLETED/CANCEL/ERROR）：`getAndRemoveCallbackByTraceId(traceId)`（**移除**）
  （`taplinksdk/lib_taplink_sdk/.../impl/ResponseProcessor.kt:52-99`；`shouldRemoveCallback` 于 `:380-405`）。
- 超时：交易回调默认 `TRANSACTION_TIMEOUT = 180_000L`（`LocalCallbackManager.kt:62`）；
  超时任务 `startTimeoutTaskForTraceId` 到点移除并回调 `onError(E306,"Callback timeout")`
  （`LocalCallbackManager.kt:308-331`）。注意 app2app 模式 `timestamp == -1L` 不超时。
- 批量失败：`failAllCallbacks(code,msg)` 清空两张表并对每个回调 `onError`
  （`LocalCallbackManager.kt:229-247`），由 `PaymentManager.failAllPendingTransactions()` 封装
  （`PaymentManager.kt:854-859`）。
- 是否有在途：`hasActiveCallbacks()`（`LocalCallbackManager.kt:253-256`）→ `PaymentManager.hasInFlightTransactions()`（`PaymentManager.kt:865-870`），OPT-R1 用它判断是否触发快速恢复。

### A2. 重连后按 key 注册的回调是否仍在？两条重连路径对比

回调存活完全取决于**是否调用 `onPhysicalDisconnected()`**：

- `PaymentManager.onPhysicalDisconnected()`（`PaymentManager.kt:119-127`）：清 `hasPendingAutoConnectTransaction`，并调 `responseProcessor.clearProcessedTerminalEvents()`。
  - 注意：它**不**调 `callbackManager.failAllCallbacks()`，所以**单纯调用它并不会清掉 traceId 回调**；真正清回调的是 `failAllPendingTransactions()`（E306）。
- 挂接点：`ConnectionManager.setConnectionDisconnectedListener { onPhysicalDisconnected() }`（`PaymentManager.kt:104-107`），该 listener 由 `connectionDisconnectedListener?.invoke()` 触发。

**路径对比：**

| 路径 | 是否调 `connectionDisconnectedListener`(→onPhysicalDisconnected) | 是否 failAllPendingTransactions | 在途 traceId 回调结局 |
|---|---|---|---|
| **OPT-R1 快速恢复成功** | **否**（直接 `return`，见 `ConnectionManager.kt:483-490`） | 否 | **保留**，继续等响应/180s 超时 ✅ |
| OPT-R1 快速恢复失败 | 是（`fastRecoveryFallback` 调 `updateConnectionStatus(DISCONNECTED)`） | **是**（E306，`ConnectionManager.kt:568-572`） | 立即 E306 收口 |
| 常规被动断开（不满足快恢条件） | 是（`updateConnectionStatus(DISCONNECTED,reason)` → `connectionDisconnectedListener?.invoke()`，`ConnectionManager.kt:928-932`） | 否（除非是 connectionError 分支） | 回调保留，但 terminal 去重被清 |
| 连接错误分支（`isConnectionError`） | — | **是**（`ConnectionManager.kt:430` failAllPendingTransactions） | 立即收口 |

证据：
- 快恢成功不触发断开通知：`ConnectionManager.kt:483-490`（`tryFastRecovery` 后 `syncStatusFromKernel(); return`），`tryFastRecovery` 成功分支仅记日志维持连接（`:557-567`）。
- DISCONNECTED 总会通知 PaymentManager：`ConnectionManager.kt:928-932`。

### A3. 重连成功后 Host 重推同一 traceId 能否匹配到回调？去重逻辑影响

- **匹配能力**：只要 `traceIdCallbackMap` 中该 traceId 回调还在（快恢成功即在），`processResponse` 就能命中并回调。但**前提是 Host 用同一 traceId 重推**——见 B/C，Host 不会这么做。
- **去重逻辑**：`ResponseProcessor.processedTerminalEvents: MutableSet<String>`，key=`"$traceId:$eventCode"`，终态只处理一次（`ResponseProcessor.kt:40,68-82`）。
  - 若 Host 真的重推同一 traceId 的**终态**，而 SDK 之前已处理过一次该终态，则会被当重复事件**丢弃**（`:72-74`）。
  - `clearProcessedTerminalEvents()`（`:441-446`）在 `onPhysicalDisconnected()` 时被调用；快恢成功**不**清，常规断开**会**清。清掉后重推的终态不会被当重复——但这同时意味着快恢期若被**伪断开**误触发清理，去重保护被削弱。
- **⚠️ 伪断开风险（已证实的代码缺口）**：`setupKernelStatusListener`
  （`ConnectionManager.kt:1547-1611`）里
  `val isSelfHealing = reconnectManager?.isReconnecting() == true`（`:1580`），
  判定 `droppedToInactive && wasActive && !isSelfHealing` 时会 `connectionDisconnectedListener?.invoke()` 并向集成方 `onDisconnected`（`:1582-1608`）。
  **`isSelfHealing` 没有把 `isFastRecovering` 计入**。OPT-R1 快速恢复期间内核真实会经历 `DISCONNECTED`（这正是触发快恢的原因），该监听器可能在快恢窗口内触发伪 `onDisconnected()` + `onPhysicalDisconnected()` → `clearProcessedTerminalEvents()`，并把一次"假断开"透传给集成方。回调本身不会被清（onPhysicalDisconnected 不 failAll），但去重保护被清、且集成方收到不该收到的 `onDisconnected`。

### A4. SDK 是否有「重连后主动 query 在途交易」？

**没有自动机制。**
- `query()` 存在，但是**集成方手动调用**的公开 API：`PaymentManager.query(QueryRequest, callback)`（`PaymentManager.kt` query 入口，内部 `convertQueryRequestToPaymentRequest` + `ensureLinkAliveThenExecute`）。
- 重连成功路径（快恢成功 / ReconnectManager.onConnected）里**没有**对在途 traceId 发起 query 的代码。
- 符合铁律"306 先 query 后决策"的是**集成方**责任；SDK 只暴露能力，不自动替集成方查询。taplinkdemo 也只在手动入口调用 `getClient().query(...)`（`taplinkdemo/lib_service/.../TaplinkPaymentService.kt:1087-1102`）。

---

## B. Tapro Host 发送方 —— 重连后是否继续发进度/结果

> 说明：Host 的 TapLink 收发实现在 **`tapro/lib_taplink_service`**（不在 `biz_taplink`）。LAN 模式下
> Host 是 **WebSocket 服务端**（`LanServerKernel`），SDK/集成方是客户端。

### B1. Host 如何回推进度/结果、按什么 id

- 绑定：请求入站时 `MessageManager.handleTransaction()` 对 LAN/CABLE 调
  `ecrSender.bindResponseSender(traceId, transactionRequestId, dataSource, sender)`
  （`lib_taplink_service/.../message/MessageManager.kt:247-277`）。
- 绑定表：`ResponseBindingRegistry`，同时按 `trace:<traceId>` 与 `txn:<transactionRequestId>` 建键
  （`lib_taplink_service/.../core/ResponseBindingRegistry.kt:21-32`）。
- 回推：交易每出一个事件（进度/终态），`ResponseHandler` 调
  `ecrSender.resolveResponseSender(traceId, transactionRequestId)` 取回原 WebSocket sender，
  `responseSender.sendData(responseTraceId, bytes, null)` 推回；终态后 `clearResponseSender`
  （`lib_taplink_service/.../message/ResponseHandler.kt:598-653`）。
- 实际写 socket：`LanServerKernel` 的 per-connection `IEcrSender.sendData`
  （`lib_taplink_service/.../protocol/lan/LanServerKernel.kt:825-846`）。
- → **进度/结果通过「入站时按 traceId 绑定的那个 WebSocket 连接」单向推回**。

### B2. Host 感知断链后：续推还是放弃绑定？

**放弃绑定，且不重建。**
- WebSocket 客户端断开 → `LanServerKernel.onClose()`：
  `val sender = responseSenders.remove(conn)` 后
  `TaplinkServiceManager.getInstance().invalidateResponseSender(sender)`
  → 把该 sender 关联的所有 `trace:`/`txn:` 绑定全部移除
  （`LanServerKernel.kt:1333-1358`；`invalidateResponseSender`→`ResponseBindingRegistry.invalidateSender` 于 `ResponseBindingRegistry.kt:66-73`）。
- 之后服务端回到 `WAITING_CONNECT`，LAN 通道状态置 `LISTENING`，**服务端继续监听、交易在 Host 侧继续执行**（`LanServerKernel.kt:1351-1356`；`MultiChannelManager.kt:314-331` 明确注释"server still listening"）。
- **没有任何**：重连后按 traceId/txnReqId 重绑新 sender 的逻辑；也**没有**缓存最后一次进度/结果、在新客户端连上后重发的逻辑（grep `rebind|resend|lastResponse|cacheLast|replay|onConnected` 在 `message/` 下无命中）。
- 结果：交易完成时 `ResponseHandler.resolveResponseSender()` 返回 null → 日志
  `"no valid bound response sender, traceId=..."`（`ResponseHandler.kt:629-634`），**响应被丢弃**。

### B3. 缺口是否存在？要两边续上还缺什么

**缺口确定存在。** 即使 SDK 侧回调因快恢成功而保留，Host 侧绑定已被 `onClose` 清除且不重建，Host 不会对原 traceId 续推 → SDK 原回调只能挂 180s 超时。

能工作的唯一现成链路：集成方**手动发新 QUERY**（新 traceId）→ Host `handleTransaction` 对新 traceId 重新 `bindResponseSender` → Host 查 POSP 返回结果 → SDK 新 QUERY 回调收到。但：
- 这需要集成方主动做（铁律 306 的 query-first 正是指这个），**不是**原在途交易回调自动续上；
- 原交易回调仍会 180s 后 E306。

---

## C. 协议/会话层 —— 两边如何对齐

### C1. 推送方向与 traceId 一致性

- **单向推送**：Host→SDK 用 `responseSender.sendData` 主动推进度/结果；SDK 侧由 `LocalCallbackManager` 按 traceId 对号入座。不是请求-响应式的拉取。
- **traceId 每请求全新**：`ProtocolRequestBuilder.generateTraceId() = UUID.randomUUID()...`
  （`taplinksdk/.../protocol/ProtocolRequestBuilder.kt:74,114`）。
- 因此：原交易的 traceId 在 SDK 侧随回调保留，但 Host 侧该 traceId 的绑定在断链时已失效；SDK **不会**用原 traceId 重新发起任何东西，Host 也**无从**对原 traceId 续推。两边唯一的"重新对齐"只能靠一条**新 traceId 的 QUERY**，而 QUERY 查询条件用的是 `transactionId`/`transactionRequestId`（业务 id），与原交易 traceId 无关
  （`PaymentManager.convertQueryRequestToPaymentRequest`：`originalTransactionRequestId` 等）。
- SDK 重连（快恢/常规）**没有在协议层携带"我是同一会话、请续推 traceId X"的信息**；Host 把新 WebSocket 连接当作全新客户端。

### C2. INIT 铁律与重连后状态

- 规范：INIT 首笔交易自动跑，断开重连后状态被清除（见 taplinksdk AGENT.md §4.7）。
- 快速恢复成功（不走 `onPhysicalDisconnected()`）：SDK 侧 `hasPendingAutoConnectTransaction` 与 terminal 去重**未被清**，连接被视为"从未断"，无需重跑 INIT——有利于"继续同一笔交易"。
- 常规重连：`onPhysicalDisconnected()` 清 auto-connect 标志与 terminal 去重（`PaymentManager.kt:119-127`），下一笔按规范重跑 INIT。
- ⚠️ 但 A3 的伪断开风险意味着：快恢窗口内若 `setupKernelStatusListener` 误判，会错误触发 `onPhysicalDisconnected()`，把本应"无缝续连"的快恢降级成"状态被清"，与设计意图相悖。

---

## 还缺哪些机制（要做到交易中断链重连后两边都能续上进度/结果）

### SDK 侧（taplinksdk）
1. **修复伪断开**：`setupKernelStatusListener` 的 `isSelfHealing` 应同时判 `isFastRecovering`，即
   `val isSelfHealing = reconnectManager?.isReconnecting() == true || isFastRecovering`
   （`ConnectionManager.kt:1580`），避免快恢窗口内误触发 `onDisconnected`/`onPhysicalDisconnected`/`clearProcessedTerminalEvents`。
2. **重连后在途交易的自动对账（可选但推荐）**：快恢/常规重连成功后，对仍在 `traceIdCallbackMap` 的在途交易，由 SDK 内部按其 `transactionRequestId` 自动发起一次 `query()`，把结果回灌到原回调（需与铁律"306 先 query"协调，避免重复扣款——query 本身是安全的只读查询）。
3. 若保持"集成方手动 query"策略，则需在**文档**明确：交易中断链即便快恢成功，原回调也可能 180s 超时，集成方须以 query 结果为准。

### Host 侧（tapro/lib_taplink_service）—— **主要缺口**
4. **重连重绑**：客户端在交易在途时重连后，Host 需要能把原交易的 `traceId/transactionRequestId` 重新绑定到新的 WebSocket sender（而非在 `onClose` 一律 `invalidateResponseSender` 后永不恢复）。可行方案：`onClose` 时对**有在途交易**的绑定不立即硬失效，保留一个短 TTL 的"待重绑"区；新客户端携带会话标识重连后，用稳定键（见协议侧）重绑。
5. **结果重发/缓存**：Host 缓存每笔在途交易最近一次进度与最终结果；新 sender 绑定成功（或收到 QUERY）时，按稳定键重发缓存的最新状态，确保重连后 SDK 能立即拿到当前进度/终态，而不是依赖恰好之后再出一个事件。
6. 明确 `onClose` 与 `invalidateResponseSender` 的粒度：区分"用户主动断开/换通道"（可硬失效）与"交易在途被动掉线"（应保留待重绑）。

### 协议/会话侧（两仓库共同约定）
7. **稳定会话键**：引入一个在断链重连后保持不变的会话/交易关联键（如 `transactionRequestId` 作为跨连接的稳定键，或新增 sessionId），让 Host 能把"新连接 + 老交易"对齐。当前 traceId 每请求随机、无法承担此职责；`transactionRequestId` 是现成的跨连接稳定候选（SDK 已在请求中携带，Host 已按 `txn:` 建键）。
8. **重连握手续推**：SDK 重连成功后，发送一个轻量"恢复/续连"帧，声明仍关注的 `transactionRequestId`；Host 据此重绑并重发最近状态。这是把"单向盲推"升级为"重连后可对账"的关键。
9. 两仓库文档（含 sunbay-open-docs 对外文档）需同步说明断链重连的进度/结果投递语义与集成方应做的 query-first 兜底。

---

## 附：与前一轮 review 结论的印证

- 「快恢成功不调 `onPhysicalDisconnected()`、在途回调保留、交易超时 180s」——**代码核实成立**
  （`ConnectionManager.kt:483-490,557-567`；`LocalCallbackManager.kt:62`）。
- 「重连后能否真收到原响应取决于 Host 在同一 traceId 续推」——**跨仓库核实为：Host 不会续推**，
  且因 traceId 每请求随机 + `onClose` 失效绑定 + 无重绑/缓存，原 traceId 续推在当前实现中**不可能发生**（`B2/C1`）。
- 「`isSelfHealing` 只判 `isReconnecting()` 未判 `isFastRecovering` 的伪断开风险」——**代码核实存在**
  （`ConnectionManager.kt:1580`）。
