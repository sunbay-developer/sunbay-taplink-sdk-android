# Implementation Plan — LAN 1006 恢复 D1/D2 终态必达修复

> 仓库：`/Users/sm4306/StudioProjects/taplinksdk`（分支 `dev_pro`，已有未提交改动必须完整保留）。
> 目标：在用户现有未提交改动基础上，**增量**修复 `EstablishedRecoveryCoordinator` 的两个真缺陷——
> - **D1**：5s 恢复预算依赖 `Dispatchers.Main` 持续调度 `delay(50)` 轮询自查 deadline，Main 饿死则 `settleFailure` 永不触发、`onDisconnected(E213)` 静默到底。采用**方案 B：独立看门狗**（可注入调度器，默认 `Dispatchers.Default`），到点强制 `settleFailure`。
> - **D2**：`runEpisode` 的 `CancellationException` 分支把 settle 责任交给取消方，而取消方仅在显式 detach/disconnect 时调用且不投回调；非显式取消导致 episode 结束而 `settled` 仍为 false 时两不投。采用 `@Volatile cancelledByExplicitDetach` 标志兜底 `settleFailure`。
> **对外可观察语义完全不变**：仍是恢复失败投**恰好一次** `onDisconnected(E213)`，不改 `onError`、不改 5000ms 单次预算、不改单 attempt、不碰 observer 绑定、不把 impl/manager 内部类型暴露到公开包。

## 前置约束（所有步骤必须遵守，违反即判失败）

- **保留用户未提交改动**：禁止 `git stash/reset/checkout/切分支/新建 worktree`；禁止回退/覆盖以下已暂存改动：`ConnectionFailureDispatch.kt`（`notifyDistinctConnectionDisconnects`）、`ConnectionManager.kt`（`takePendingConnectionListeners` 并入 `recoveryObservers` + 普通断开去重容错投递）、`LanRecoveryIntegrationTest.kt` 新增 3 条回归、`SPEC-ESTABLISHED-RECOVERY-001.yaml` / `SPEC-LAN-RECOVERY-001.yaml` 的 TC 映射与 `queued_callback_bugfix` 记录、`build.gradle.kts` 版本 `1.1.0.2`、`RELEASE-NOTES.md` 的 `Unreleased` → `Fixed — Connection callback completion` 段。另有未跟踪新文件 `FastRecoveryModeAgnosticTest.kt`（`lib_taplink_sdk/src/test/.../manager/`）同属本 bug 改动，必须保留。
- **禁止改动已确认非缺陷项**：`EstablishedRecoveryCoordinator.settleFailure` 仍投 `onDisconnected(E213)`（SPEC-ESTABLISHED-RECOVERY-001 R6/AC6、`callback/ConnectionListener.kt` 契约），严禁改 `onError`；`observer` 绑定逻辑（`recoveryObservers()` / `ReconnectManager.getLastConnectionListener()`）不动；`RECOVERY_BUDGET_MS=5000L` 数值与单 attempt 语义不变。
- **不引入 dev_SAF 内容**：`build.gradle.kts` 第 58 行 `freeCompilerArgs += listOf("-Xskip-metadata-version-check")` 保持原样，**不得**加入 `-Xjvm-default=all-compatibility` 或任何 SAF 相关 `testImplementation`；版本号保持 `1.1.0.2`，不再 bump。
- **公开/内部边界**：所有改动集中在 `com.sunmi.tapro.taplink.sdk.manager`（内部包），不新增/修改任何 public 类型，不把 `manager/impl/protocol/persistence` 或 `lib_taplink_communication` 类型暴露到公开 API。
- **Git 提交**：本步骤不提交；若需提交，commit message 用英文 Conventional Commits（如 `fix: guarantee terminal recovery callback under main starvation and non-explicit cancel`）。

## 关键事实（已读代码核对，行号以当前真实代码为准）

- `EstablishedRecoveryCoordinator.kt`：构造参数 `(connectionManager: RecoveryHost, clock: RecoveryClock, scope: CoroutineScope)`；`RECOVERY_BUDGET_MS=5000L`、`POLL_MS=50L`（companion）；`episodeJob = scope.launch { runEpisode(episode) }` 在 `claimEpoch` 内（`@Synchronized`）；`settleFailure`/`settleSuccess` 均以 `settled.compareAndSet(false,true)` 为一次性闸门 + `currentEpisode?.epoch != episode.epoch` 为 epoch 围栏；`runEpisode` 的 `catch (cancellation: CancellationException) { ...; throw cancellation }` 分支不 settle；`clearStateAfterSettle()` 置 `episodeJob=null`；`onExplicitDetachOrDisconnect()` 置 `settled.set(true)` + `episodeJob?.cancel()` + `clearStateAfterSettle()`。
- `ConnectionManager.kt`：`private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)`；`EstablishedRecoveryCoordinator(this, SystemRecoveryClock(), sdkScope)`。
- `RecoveryClock.kt`：`internal interface RecoveryClock { fun elapsedRealtimeMs(): Long }`，生产实现 `SystemRecoveryClock`。
- 现有测试通过反射替换 coordinator 的 `scope` 字段为 `CoroutineScope(Dispatchers.Unconfined)` 并替换 `clock` 字段为快钟（`LanRecoveryIntegrationTest.installRecoveryTestScope`）；`EstablishedRecoveryBudgetTest` 用 `StepClock` 瞬跳 + `Dispatchers.Unconfined` 直接构造 coordinator。看门狗延迟必须走**可被测试控制**的时钟/调度，否则会把现有确定性测试变成真实 5s 挂起。

## 设计决策（看门狗的可注入方式——必须落实其一，并说明理由）

**决策：看门狗延迟复用注入的 `clock`（忙等 + 可注入 `watchdogDispatcher`），而非裸 `delay(RECOVERY_BUDGET_MS)`。**

理由：现有测试用 `StepClock`/6×快钟注入 `clock`，并把 `scope` 换成 `Unconfined`。若看门狗写 `scope.launch { delay(RECOVERY_BUDGET_MS); ... }`，在 `Unconfined` + 快钟下 `delay` 仍按**真实挂钟** 5000ms 挂起，测试会被拖成真实 5s 甚至误判。让看门狗的“到点”判定走与 episode 相同的**注入时钟** `clock.elapsedRealtimeMs()`（与 `deadlineReached` 同源），即可被快钟瞬跳驱动、被 `StepClock` 精确控制，又能在生产中独立于 Main 调度。实现方式：

1. 新增构造参数 `watchdogDispatcher: CoroutineDispatcher = Dispatchers.Default`（带默认值，不破坏现有 `ConnectionManager` 构造调用；测试可覆盖）。看门狗协程 `watchdogScope.launch(watchdogDispatcher) { ... }`，其中 `watchdogScope` 为 coordinator 自持的、与 `scope` 独立的 `CoroutineScope(SupervisorJob() + watchdogDispatcher)`，确保**不受** Main 饿死影响（这是修 D1 的本质）。
2. 看门狗循环以**短固定实时 `delay`（如 `delay(WATCHDOG_TICK_MS=25L)`）** 唤醒，每次用 `clock.elapsedRealtimeMs() >= episode.deadlineElapsedMs` 判断是否到点；到点即 `settleFailure(episode)`。固定 tick 是真实挂钟但极短（不改 5000ms 预算语义，仅作为看门狗的自检心跳）；在快钟测试下 `clock` 早已越过 deadline，第一两个 tick 内即触发，测试仍是毫秒级确定性完成。

> 备选（未采用）：方案 A `withTimeout` 包裹 rebuild/poll。否决原因：`withTimeout` 基于 `delay`，在 Main 饿死时其超时续体同样排在 Main 上得不到调度，**无法**解决 D1 根因（Main 饿死）；且 `TimeoutCancellationException` 需与 D2 的 `CancellationException` 分支小心区分，改动更脆弱。独立看门狗调度器从根上绕开 Main，侵入更小、与现有 CAS 闸门天然协作。

## 实现步骤

- [ ] 1. 给 `EstablishedRecoveryCoordinator` 增加可注入看门狗调度器与看门狗作用域/字段。
      在 `EstablishedRecoveryCoordinator.kt`：构造参数末尾新增 `private val watchdogDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default`；companion 内新增 `private const val WATCHDOG_TICK_MS = 25L`；类体新增 `private val watchdogScope = CoroutineScope(SupervisorJob() + watchdogDispatcher)` 与 `private var watchdogJob: Job? = null`。补充所需 import（`CoroutineDispatcher`、`Dispatchers`；`CoroutineScope`/`SupervisorJob` 按需）。保持 `scope`/`clock` 既有参数与语义不变。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/manager/EstablishedRecoveryCoordinator.kt`
      Verify: `./gradlew :lib_taplink_sdk:compileDebugKotlin --console=plain` 编译通过（此步单独编译，后续步骤再跑测试）。

- [ ] 2. D1：在 `claimEpoch` 启动 episode 处（`episodeJob = scope.launch { runEpisode(episode) }` 之后、仍在 `@Synchronized claimEpoch` 内）启动独立看门狗。
      新增私有方法 `private fun startWatchdog(episode: RecoveryEpisode)`：`watchdogJob = watchdogScope.launch { while (isActive && clock.elapsedRealtimeMs() < episode.deadlineElapsedMs) { delay(WATCHDOG_TICK_MS) }; settleFailure(episode) }`。在 `episodeJob = scope.launch { runEpisode(episode) }` 之后调用 `startWatchdog(episode)`。依赖 `settleFailure` 现有 `settled.compareAndSet(false,true)` 一次性闸门 + epoch 围栏保证：正常结算先发生时，看门狗的 `settleFailure` 被 CAS 吞掉；看门狗运行在 `watchdogDispatcher`（默认 `Dispatchers.Default`），不受 Main 饿死影响——这是 D1 的核心修复。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/manager/EstablishedRecoveryCoordinator.kt`
      Verify: `./gradlew :lib_taplink_sdk:compileDebugKotlin --console=plain` 通过。

- [ ] 3. D1：看门狗生命周期与 episode 绑定——在结算/清理时取消看门狗，避免泄漏与跨 episode 误触发（双保险）。
      在 `clearStateAfterSettle()` 内新增 `watchdogJob?.cancel(); watchdogJob = null`（置于现有 `episodeJob = null` 附近）。`onExplicitDetachOrDisconnect()` 走 `clearStateAfterSettle()`，故显式 detach 也会取消看门狗（显式 detach 维持不投回调语义不变）。看门狗协程内部已在 `settleFailure` 前经 `settled` CAS + epoch 围栏自校验，构成“取消 + 围栏”的双保险。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/manager/EstablishedRecoveryCoordinator.kt`
      Verify: `./gradlew :lib_taplink_sdk:compileDebugKotlin --console=plain` 通过。

- [ ] 4. D2：新增 `@Volatile private var cancelledByExplicitDetach = false`，在 `onExplicitDetachOrDisconnect()` 内（`settled.set(true)` 之前）置 `cancelledByExplicitDetach = true`；在 `claimEpoch` 新建 episode 时重置为 `false`（置于 `settled = AtomicBoolean(false)` 附近，保证每个新 episode 从 false 开始）。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/manager/EstablishedRecoveryCoordinator.kt`
      Verify: `./gradlew :lib_taplink_sdk:compileDebugKotlin --console=plain` 通过。

- [ ] 5. D2：改造 `runEpisode` 的 `catch (cancellation: CancellationException)` 分支，补非显式取消的终态兜底。
      分支逻辑改为：`if (!cancelledByExplicitDetach && !settled.get()) { settleFailure(episode) }`（显式 detach 取消维持现状不投回调；非显式取消且尚未结算时兜底 `settleFailure(episode)` 保证终态必达），随后**仍 `throw cancellation`** 以满足结构化并发的取消传播要求（不得吞掉）。注意：`settleFailure` 内部 CAS + epoch 围栏保证与看门狗/其它路径互斥、只投一次。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/manager/EstablishedRecoveryCoordinator.kt`
      Verify: `./gradlew :lib_taplink_sdk:compileDebugKotlin --console=plain` 通过。

- [ ] 6. 回归测试 TC-超时必达-Main受压：在 `Dispatchers.Main` 风格、轮询续体无法及时派发的条件下，断言预算到点后看门狗仍触发 `settleFailure`、observer 恰好一次 `onDisconnected(E213)`。
      新增单元测试：优先放在 `EstablishedRecoveryBudgetTest.kt`（沿用其 `StepClock` + `RecoveryHost` 假实现风格最简）。构造：`isRecoveryTransportReady()` 恒 false；给 coordinator 注入一个“饿死”的 `scope`（例如一个单线程调度器被长任务占满，或用一个永不派发 `delay` 续体的受控 dispatcher），而 `watchdogDispatcher` 注入 `Dispatchers.Unconfined` + `clock` 用 `StepClock`/快钟使看门狗 tick 内即判定到点。断言：`rec.disconnected.size == 1` 且含 `"213"`，`rec.connected.isEmpty()`、`rec.errors.isEmpty()`，`rebuilds` 至多 1。要点：证明超时结算来自看门狗而非 episode 轮询（episode 所在 scope 被饿死）。能先红（未加看门狗前该用例应挂起/失败）后绿更佳。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/test/java/com/sunmi/tapro/taplink/sdk/manager/EstablishedRecoveryBudgetTest.kt`
      Verify: `./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "com.sunmi.tapro.taplink.sdk.manager.EstablishedRecoveryBudgetTest" --console=plain` 新用例通过。

- [ ] 7. 回归测试 TC-非显式取消兜底：episode 进行中以非 `onExplicitDetachOrDisconnect` 方式取消 `episodeJob`（或取消父 scope 的子 job），断言 observer 仍收到恰好一次 `onDisconnected(E213)`。
      新增单元测试（同 `EstablishedRecoveryBudgetTest.kt` 风格）：`isRecoveryTransportReady()` 恒 false 使 episode 停在轮询；通过反射取 `episodeJob` 字段并 `cancel()`（模拟非显式取消，`cancelledByExplicitDetach` 仍为 false）；断言 `rec.disconnected.size == 1` 含 `"213"`，`rec.errors.isEmpty()`。注意避免与看门狗竞态：可把 `watchdogDispatcher` 设为一个不会先到点的受控调度/慢钟，或断言“恰好一次”以覆盖两路径都只投一次。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/test/java/com/sunmi/tapro/taplink/sdk/manager/EstablishedRecoveryBudgetTest.kt`
      Verify: `./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "com.sunmi.tapro.taplink.sdk.manager.EstablishedRecoveryBudgetTest" --console=plain` 新用例通过。

- [ ] 8. 回归测试 TC-看门狗与正常结算互斥：重建在 deadline 前成功 `settleSuccess`，随后看门狗到点，断言只投一次 `onConnected`、看门狗的 `settleFailure` 被 `settled` CAS 吞掉（无 `onDisconnected`）。
      新增单元测试（同 `EstablishedRecoveryBudgetTest.kt` 风格）：`host(ready = true, ...)`、`validateRecoveredIdentity` 返回 true，使 episode 快速 `settleSuccess`；`clock`/`watchdogDispatcher` 配置成看门狗随后仍会“到点”触发一次 `settleFailure`。断言：`rec.connected.size == 1`、`rec.disconnected.isEmpty()`、`rec.errors.isEmpty()`，`isRecovering()` 最终 false。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/test/java/com/sunmi/tapro/taplink/sdk/manager/EstablishedRecoveryBudgetTest.kt`
      Verify: `./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "com.sunmi.tapro.taplink.sdk.manager.EstablishedRecoveryBudgetTest" --console=plain` 新用例通过。

- [ ] 9. 回归测试 TC-仅形参listener-超时失败：只 `connect(endpoint, listener)`、从不 `setConnectionListener`，走真实超时失败，断言该形参 listener 收到恰好一次 `onDisconnected(E213)`。
      放在 `LanRecoveryIntegrationTest.kt`（沿用其 `setup`/`Events`/`installRecoveryTestScope` 基建；此文件已被用户改动，**新增** `@Test` 方法，不动既有方法）。流程：`setup { emptyList() }`；**不**调用 `manager.setConnectionListener(...)`；把该 listener 放入 `pendingConnectionListeners`（反射，模拟 connect-in-progress 形参 listener 入队，复现“仅形参 listener”路径），并 `installRecoveryTestScope(manager)` 让真实 episode 在 JVM 结算；反射取 `establishedRecoveryCoordinator` 并 `interceptIfEstablishedFault(TransportFaultEvent(0,0,"1006","test",true))` 驱动真实超时失败；`withTimeout` 等到 `listener.disconnected` 非空，断言恰好一次且含 `"213"`、`errors` 为空。确认 `recoveryObservers()`（含 `takePendingConnectionListeners()`）使形参 listener 进入 episode observers。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/test/java/com/sunmi/tapro/taplink/sdk/manager/LanRecoveryIntegrationTest.kt`
      Verify: `./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "com.sunmi.tapro.taplink.sdk.manager.LanRecoveryIntegrationTest" --console=plain` 新用例通过，且文件内用户已新增的 3 条回归（`recoveryTakesOwnershipOfPreviouslyQueuedCallers`、`recoveryFailureDeliversOneOutcomeToTransferredQueuedCaller`、`throwingDisconnectListenerDoesNotBlockOtherObservers`）仍通过。

- [ ] 10. 若 `installRecoveryTestScope` 反射替换 `scope`/`clock` 后看门狗仍走生产 `watchdogDispatcher`/真实 tick 导致不确定性，补一个反射注入点让测试可控看门狗。
      在 `LanRecoveryIntegrationTest.installRecoveryTestScope`（及必要时 `EstablishedRecoveryBudgetTest`）中，对已注入快钟的 coordinator，额外反射设置 `watchdogDispatcher`/`watchdogScope` 为 `Unconfined`（字段名以步骤 1 实际命名为准），确保看门狗在快钟下毫秒级确定触发而非真实 5s。此步仅在步骤 9 出现真实挂钟等待/不稳定时才需要；若步骤 6-9 已稳定可跳过，但需在验证中确认整类测试运行时间为毫秒级而非秒级。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/lib_taplink_sdk/src/test/java/com/sunmi/tapro/taplink/sdk/manager/LanRecoveryIntegrationTest.kt`（必要时 `EstablishedRecoveryBudgetTest.kt`）
      Verify: `./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "com.sunmi.tapro.taplink.sdk.manager.*Recovery*" --console=plain` 全绿且整体运行时间无异常拉长（证明看门狗受测试时钟控制）。

- [ ] 11. 补 L2 变更记录（SCR/CR）到 `docs/spec/changes/`。
      新建 `/Users/sm4306/StudioProjects/taplinksdk/docs/spec/changes/CR-ER-D1D2-20261019.yaml`（`change_id` 可自拟，保持此名即可），沿用 `SCR-ESTABLISHED-RECOVERY-001.yaml` 的 JSON-in-YAML 字段风格。内容要点：`spec_id: SPEC-ESTABLISHED-RECOVERY-001`；`rule_id: ["R4","R6"]`；`change_type: CONFORMANCE_FIX`（或等价表述“实现达标修复”）；`level: L2`；`change_origin: IMPLEMENTATION_DEFECT`；`reason`：实现未满足 AC4（5s 预算到点必结算）与 AC6（terminal-once 必达）的两类缺口——D1（Main 饿死致轮询超时判定跑不到）与 D2（非显式取消两不投）；`before`/`after`：before = 预算靠 Main 轮询自查、取消分支把 settle 交给取消方；after = 独立看门狗（可注入调度器默认 `Dispatchers.Default`）到点强制 `settleFailure` + 非显式取消兜底 `settleFailure`；**明确声明**预算语义不变（仍单次 5000ms、单 attempt），仅把“到点必结算”从依赖轮询升级为调度无关看门狗，**非第二次 attempt**，对外回调语义/错误码（E213）不变；`impact`：列出新增回归用例到 AC 的映射（TC-超时必达-Main受压→AC4/AC6、TC-非显式取消兜底→AC6、TC-看门狗与正常结算互斥→AC4/AC6、TC-仅形参listener-超时失败→AC6）；`approval`：标注为达标修复、无规则/批准变更。与已暂存的 `SPEC-ESTABLISHED-RECOVERY-001.yaml` 中 `queued_callback_bugfix` 记录风格一致。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/docs/spec/changes/CR-ER-D1D2-20261019.yaml`
      Verify: `./config/speccheck/spec-check.sh` 退出码 0（无 🔴 阻断项）。

- [ ] 12. 在已暂存的 `RELEASE-NOTES.md` `Unreleased` → `Fixed — Connection callback completion` 段**追加** D1/D2 修复说明（英文），不新增版本号（保持 `1.1.0.2`），不引入 SAF 内容。
      在该段落现有两条 bullet 之后追加两条（英文），语义对齐本修复但只描述对外可观察行为（不泄漏内部实现细节）：其一说明 established-link 恢复在预算到点时必定投递最终 disconnect，即使回调线程被占用；其二说明 episode 被非显式原因取消时仍会投递一次最终 disconnect。措辞保持与现有 bullet 一致的客户视角，不出现 `Dispatchers.Main`/`watchdog` 等内部术语（可用“regardless of callback-thread load”之类表述）。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/RELEASE-NOTES.md`
      Verify: 人工复核该段英文、无 SAF 内容、版本号仍为 `1.1.0.2`；`git diff -- RELEASE-NOTES.md` 仅为追加。

- [ ] 13. （可选，建议）在已暂存的 `SPEC-ESTABLISHED-RECOVERY-001.yaml` 的 `TC-ER-004`(AC4)/`TC-ER-006`(AC6) 的 `additional_tests` 中追加本次新增回归的全限定测试名，保持与现有 `queued_callback_bugfix` 风格一致的可追溯映射。
      追加项（按实际命名）：`EstablishedRecoveryBudgetTest.<超时必达-Main受压>`、`EstablishedRecoveryBudgetTest.<非显式取消兜底>`、`EstablishedRecoveryBudgetTest.<看门狗与正常结算互斥>`、`LanRecoveryIntegrationTest.<仅形参listener-超时失败>`。不改任何已批准的 rule/AC 文本，仅增补测试映射。
      Files: `/Users/sm4306/StudioProjects/taplinksdk/docs/spec/coverage/SPEC-ESTABLISHED-RECOVERY-001.yaml`
      Verify: `./config/speccheck/spec-check.sh` 退出码 0。

- [ ] 14. 全量回归：运行两模块单元测试，确保新增用例通过且无既有用例回归（含用户已新增的 3 条 + `FastRecoveryModeAgnosticTest`）。
      Files: 无（验证步骤）
      Verify: `./gradlew :lib_taplink_sdk:testDebugUnitTest :lib_taplink_communication:testDebugUnitTest --console=plain` 全绿；随后 `./config/speccheck/spec-check.sh` 退出码 0；`git diff --check` 无告警。

## 验证命令汇总

- 编译：`./gradlew :lib_taplink_sdk:compileDebugKotlin --console=plain`
- 聚焦测试：`./gradlew :lib_taplink_sdk:testDebugUnitTest --tests "com.sunmi.tapro.taplink.sdk.manager.EstablishedRecoveryBudgetTest" --tests "com.sunmi.tapro.taplink.sdk.manager.LanRecoveryIntegrationTest" --console=plain`
- 全量：`./gradlew :lib_taplink_sdk:testDebugUnitTest :lib_taplink_communication:testDebugUnitTest --console=plain`
- 规格机械校验：`./config/speccheck/spec-check.sh`
- Git 空白/冲突检查：`git diff --check`

## 完成判据（Definition of Done）

- `EstablishedRecoveryCoordinator` 在 Main 饿死与非显式取消两种条件下，恢复预算到点/ episode 结束时**必定**投递恰好一次 `onDisconnected(E213)`；正常成功路径仍只投一次 `onConnected` 且看门狗被 CAS 吞掉。
- 对外回调语义、E213 错误码、5000ms 单次预算、单 attempt、observer 绑定、公开 API 边界**均未改变**。
- 用户已有未提交改动（含 `FastRecoveryModeAgnosticTest.kt` 未跟踪文件）完整保留；未引入任何 dev_SAF 内容；版本号保持 `1.1.0.2`。
- 4 条新回归 + 用户已新增 3 条回归 + 两模块既有测试全部通过；L2 变更记录与 RELEASE-NOTES 已补；`spec-check.sh` 通过。
