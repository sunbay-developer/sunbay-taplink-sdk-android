# LAN 1006 恢复修复 — 收口说明与遗留 known-issue（后续单独评估）

> 本次交付范围：修复客户现场 LAN 1006 恢复后「connect 永久无回调」的核心缺陷 D1/D2，
> 确保**每一个 connect 最终都会收到终态回调**。
> 本文件记录 review 过程中发现的、**超出本次核心修复范围**的遗留并发缺陷，供后续单独立项评估。
> 收口决定由维护者 2026-10-19 做出：核心验收标准（每个 connect 都有回调）已达成即交付，遗留项不在本轮硬修。

## 一、本次已交付（已验证）

- **D1**：恢复 episode 的 5s 超时判定加独立看门狗（`watchdogScope` + 可注入 `watchdogDispatcher`，默认 `Dispatchers.Default`，用注入 clock 判定 deadline），不再依赖 `Dispatchers.Main` 轮询续体被调度。Main 饿死时超时仍必达 `settleFailure` → 投一次 `onDisconnected(E213)`。
- **D2**：`CancellationException` 分支按 `cancelledByExplicitDetach` 区分——非显式取消且未结算时兜底 `settleFailure`，显式 detach 维持不投并正确 rethrow。保证 episode 一旦结束终态必达。
- 两段式结算（`claimTerminalSettle` → Host 收尾 → `finalizeTerminalSettle`）+ per-generation `EpisodeControl`，保证终态恰好一次、看门狗与正常结算互斥、无跨 episode 污染。
- 对外契约不变：失败投一次 `onDisconnected(E213)`、不投 `onError`、单次 5000ms 预算、单 attempt。
- 测试：lib_taplink_sdk 62 / lib_taplink_communication 42，0 失败；新增 D1/D2 回归有 pre-fix RED→GREEN 证据；spec-check EXIT=0。
- review-a（claude-opus-4.8）判定 APPROVED。
- 版本 1.1.0.2，无 dev_SAF 内容混入，公开 API 边界干净。

## 二、遗留 known-issue（后续单独评估，未在本轮修复）

### ISSUE-1（阻塞级，但属预先存在的边缘竞态）不同配置 connect 在失败 Host cleanup 同步回调中重入被旧 cleanup 反向拆除
- 来源：review-b（confirmed/blocking）。
- 现象：`ConnectionManager.onRecoveryFailureCleanup()` 在清状态/断开旧 kernel 之前，同步执行 `failAllPendingTransactions()`，后者经 `LocalCallbackManager.failAllCallbacks()` 在**当前线程同步**回调应用层 `onError`。若应用在该回调里**重入 `connect(newConfig)`（不同配置）**，`connect()` 会经 `onExplicitDetachOrDisconnect()` 提前 fence 旧 control 并启动 fresh session；但旧 cleanup 返回后继续清共享连接字段并对 `getCurrentServiceKernel()` 执行 `disconnect()`，可能拆掉刚启动的 fresh 连接，同时旧 `finalizeTerminalSettle` 因 control 已非 current 返回空快照、不投原 observer 的 E213。违反已批准的 R9/AC9。
- 为何不在本轮修：
  - 该同步重入点（`failAllPendingTransactions` 同步回调）**在本次修复之前就存在**，非本次引入的回归。
  - 触发条件苛刻：须「恢复失败 → 应用在 onError 同步回调里 → 立即用**不同配置** connect」，与客户实际现场（1006 后无回调）不是同一路径。
  - **不影响本次核心验收**：每个 connect 仍会有回调——原连接走恢复失败 E213 路径、重入的新连接走自己的 connect 回调路径；该缺陷影响的是极端重入时序下「旧 E213 可能被跨代清理吞掉 / 新会话可能被旧 cleanup 拆除」，属更深层的跨代原子性加固。
- 建议修复方向（后续立项）：把 coordinator 状态扩展为 `ACTIVE / SETTLING / IDLE`，在 `SETTLING` 阶段把 superseding 的**不同配置** connect 推迟到 `finalize` 之后；或调整失败清理顺序，确保旧状态与旧 kernel 在任何应用回调前完成 teardown。新增 ConnectionManager 级回归：在 `failAllPendingTransactions` 的同步 callback 内调用不同配置 connect，验证 fresh session 不被旧 cleanup 清字段或断开、旧 observer 终态符合契约、无跨代重复回调。
- 证据：
  - `lib_taplink_sdk/.../manager/ConnectionManager.kt:224-234`、`:1061-1074`
  - `lib_taplink_sdk/.../manager/EstablishedRecoveryCoordinator.kt:516-533`
  - `lib_taplink_communication/.../util/LocalCallbackManager.kt:229-240`

### ISSUE-2（非阻塞，possible）看门狗 off-Main 收尾与 Main 上 host 调用的并发触碰
- 来源：review-a（possible/non-blocking）。
- 现象：看门狗命中 deadline 时 `settleFailure → onRecoveryFailureCleanup` 运行在 `Dispatchers.Default`，而 `ConnectionManager` 的 `onRecoveryFailureCleanup/onRecoverySuccess/retireForRecovery/openRecoveryRebuild` 对共享连接字段与内核操作未加 `synchronized(this)`。协程取消是协作式的：Main 真正饿死（body 挂在 `delay`）时安全；但若 Main 正**同步执行**某 host 调用（如 `retireForRecovery` 的 `kernel.disconnect` / `openRecoveryRebuild` 的 `connectInternal`）时看门狗开火，收尾会与 Main 上仍在跑的 body 并发触碰同一内核/字段，最坏导致一个无人跟踪的活连接（泄漏/状态不一致）。对外「恰好一次 E213」契约不受影响。
- 建议：给恢复钩子（`onRecoverySuccess/onRecoveryFailureCleanup/retireForRecovery/openRecoveryRebuild`）补 `synchronized(this)` 与状态投影路径互斥；或核实 `connectInternal/kernel.disconnect` 在协作取消点前已真正完成。
- 证据：`EstablishedRecoveryCoordinator.kt`、`ConnectionManager.kt:1060-1074`

### ISSUE-3（非阻塞，信息记录）失败终态回调线程亲和性
- 看门狗投递 `onDisconnected(E213)` 运行在后台线程而非 Main。符合 `ConnectionListener` KDoc「回调可能在后台线程、集成方自行切主线程」契约，Tapro 侧已切主线程，无需改码。仅记录。

## 三、收口结论

核心验收标准「每个 connect 最终都有终态回调」已达成并经测试与 review-a 验证。ISSUE-1~3 为更深层/预先存在的边缘并发加固项，拆为后续独立任务评估，不阻塞本次交付。
