# LAN 1006 恢复 D1/D2 终态必达修复 — 行为/语义审查 (review-a)

修复目标是关闭 `EstablishedRecoveryCoordinator` 的两个真缺陷：D1（5s 预算只靠 Main 线程上的 `delay(50)` 轮询自查 deadline，Main 饿死则 `settleFailure` 永不触发、`onDisconnected(E213)` 静默到底）与 D2（`CancellationException` 分支把 settle 责任交给取消方，非显式取消时 episode 结束却两不投）。实现用一个独立于 Main 调度的看门狗（可注入 `watchdogDispatcher`，默认 `Dispatchers.Default`，用注入时钟判定 deadline）保证超时必结算，用每 episode 的 `cancelledByExplicitDetach` 标志在非显式取消时兜底 `settleFailure`，并把所有 settle/cancel 状态收敛进 per-generation `EpisodeControl` + 两阶段 `claimTerminalSettle`/`finalizeTerminalSettle` 来杜绝跨 episode 污染、gate 过早释放、settling-window join 丢回调。对外契约（失败一次 `onDisconnected(E213)`、不投 `onError`、无中间回调、单次 5000ms 预算、单 attempt）保持不变。

Watch for: 看门狗命中 deadline 时，失败结算及其 Host 收尾（`onRecoveryFailureCleanup`）现在运行在 `Dispatchers.Default` 而非 Main，而 `ConnectionManager` 的恢复钩子对共享连接字段/内核操作**未加 `synchronized(this)`**；在 Main 被阻塞于某个同步 host 调用（retire/rebuild）的窗口内，看门狗线程的收尾可能与 Main 上仍在执行的 episode body 并发触碰同一内核/字段（possible，非阻塞）。对外回调契约本身不受影响。

**Verdict**: APPROVED

## High-level view

D1 的修复是对的且落到了根因上。看门狗跑在独立 `watchdogScope`（`watchdogDispatcher`，默认 `Dispatchers.Default`），与 Main-confined 的 episode `scope`、与 `pollUntilReadyOrDeadline` 轮询完全解耦；到点判定走注入的 `clock.elapsedRealtimeMs()`（与 `deadlineReached` 同源）而非写死的真实 5000ms `delay`，所以既能在生产中不受 Main 饿死影响，又能被现有「反射注入 Unconfined + 快钟」的测试毫秒级确定性驱动。

D2 的修复也到位：`CancellationException` 分支仅在 `!cancelledByExplicitDetach && !settled.get()` 时兜底 `settleFailure`，显式 detach 仍不投回调，且始终 `throw cancellation` 保留结构化并发传播。`cancelledByExplicitDetach` 是 per-episode 的，不会被后继 episode 误读。

互斥与生命周期比 plan 更稳健：settle 被收敛成单一 `@Synchronized claimTerminalSettle`（锁内先验 `currentControl === control`、再抢自己的 `settled` CAS），消除了「CAS 先于 epoch 校验」的 check-then-act 窗口，杜绝旧 episode 翻掉新 episode 的闸门；看门狗 job 在 claim settle 与显式 detach 两处都被取消，无泄漏、无跨 episode 误触发。两阶段 settle 把 PaymentManager gate 和 `currentControl` 持有到 Host 收尾完成后再由 `finalizeTerminalSettle` 释放并快照 observer，关闭了「gate 过早释放」和「settling-window 同配置 connect 撤销原 observer 终态」两个窗口。

对外契约严格未变，公开 API 边界干净（新增 `watchdogDispatcher` 带默认值，位于 internal `manager` 包；`EpisodeControl` 为 private 嵌套类，无类型外泄）。dev_SAF 内容未被带入（编译参数仅 `-Xskip-metadata-version-check`、版本 `1.1.0.2`、RELEASE-NOTES 无 SAF）。用户已有的正确改动（`ConnectionFailureDispatch` 去重容错、`ConnectionManager.recoveryObservers` 并入 pending、observer 绑定、3 条原回归）均原样保留。

新增回归覆盖四个要求场景且断言充分；verification 与 CR 对关键用例给出了明确的 pre-fix RED 证据，不是永远通过的空测试。

<details>
<summary>Issues (2)</summary>

1. **Off-Main 收尾并发 / 潜在内核泄漏 (possible, 非阻塞)** — 看门狗命中 deadline 时 `settleFailure → onRecoveryFailureCleanup` 运行在 `Dispatchers.Default`，而 `onRecoveryFailureCleanup`/`onRecoverySuccess`/`retireForRecovery`/`openRecoveryRebuild` 对共享字段与内核均无 `synchronized(this)`；若 Main 阻塞在某同步 host 调用（而非挂起在 `delay`）时看门狗开火，收尾可能与仍在跑的 episode body 并发。需核实 `connectInternal`/`kernel.disconnect` 是否会在协作取消之前完成、以及是否应给恢复钩子加锁。
2. **终态回调线程亲和性变化 (confirmed, 非阻塞, 已被确认)** — 失败终态 `onDisconnected(E213)` 经看门狗投递时运行在后台线程而非 Main；符合 `ConnectionListener` KDoc「回调可能在后台线程、集成方自行切主线程」契约，Tapro 侧 observer 已切主线程，无需改码。

</details>

<details>
<summary>Details</summary>

### D1：独立看门狗，调度无关 + 时钟可注入

`startWatchdog(control)` 把看门狗放在 `watchdogScope = CoroutineScope(SupervisorJob() + watchdogDispatcher)`，与 episode 的 `scope`（生产仍是 `sdkScope` = `Dispatchers.Main`，`ConnectionManager.kt:193` 未改）物理隔离。看门狗循环

```kotlin
while (isActive && clock.elapsedRealtimeMs() < control.episode.deadlineElapsedMs) {
    delay(WATCHDOG_TICK_MS)   // 25ms 真实心跳，仅用于重读注入时钟
}
if (isActive) settleFailure(control)
```

关键正确性点：到点判定用注入的 `clock`（与 `deadlineReached` 同源），而不是 `delay(RECOVERY_BUDGET_MS)`。这同时满足了两个约束——生产上 deadline 的发现不依赖 Main 续体被调度（修 D1 根因），测试里把 `watchdogDispatcher` 反射/构造注入 `Unconfined` 且用 `StepClock`/快钟即可让看门狗在第一两个 tick 内确定触发，不会把毫秒级测试拖成真实 5s。`watchdogSettlesFailureWhenEpisodeScopeIsStarved` 用一个被 `Thread.sleep(60s)` 占满的单线程 executor 当 episode scope（Main 饿死的 JVM 等价物），断言终态 E213 来自看门狗（`rebuilds==0`，episode body 根本没跑），是对 D1 的直接锁定。

5000ms 预算语义未变：`RECOVERY_BUDGET_MS=5000L`、`deadlineElapsedMs` 计算、单 attempt 均保持；看门狗只把「到点→结算」从轮询依赖升级为调度无关，不是第二次 attempt。

`startWatchdog` 还处理了「episode 在看门狗 job 存在之前就同步结算完」（如 prerequisite 拒绝 / 已过期 deadline 在 eager/Unconfined 下）：launch 前后各做一次 `settled.get() || currentControl !== control` 检查并 `job.cancel()`，避免累积一个停不下来的 ~5s job。这是对快速连续失败场景的必要防泄漏。

### D2：非显式取消兜底，显式 detach 仍静默

```kotlin
} catch (cancellation: CancellationException) {
    if (!control.cancelledByExplicitDetach && !control.settled.get()) {
        settleFailure(control)
    }
    throw cancellation
}
```

显式 `disconnect()/removeConnectionListener()` 走 `onExplicitDetachOrDisconnect()`，它先把 `control.cancelledByExplicitDetach = true` 再取消，故该分支读到自己 control 的标志、保持「不投回调」语义；其它任何取消（父 scope 子 job 取消等）在 episode 未结算时兜底一次 `settleFailure`，且仍 rethrow 不破坏结构化并发。`nonExplicitCancellationBackStopsSingleFailure` 用 `cancelChildren()` 制造非显式取消并断言恰好一次 E213，`staleExplicitDetachCancellationDoesNotPoisonNextEpisode` 则断言显式 detach 的延迟取消读自己的标志、不投回调、也不污染后继 episode——两面都被覆盖。

### 互斥、生命周期与跨 episode 隔离

`claimTerminalSettle` 是唯一的一次性闸门，锁内顺序为先 `if (currentControl !== control) return false` 再 `settled.compareAndSet(false,true)`，消除了原「CAS 先、epoch 校验后」的 check-then-act 窗口：旧/迟到 episode 对自己（已非 current）的 control 结算会被第一道检查挡掉，绝不会翻掉后继 episode 的 token。`staleEpisodeResumingAfterWatchdogSettleDoesNotPoisonNextEpisode` 用 host barrier 精确编排「看门狗结算 A → B claim → A body 恢复并尝试结算 B」并断言 B 仍收到自己的唯一终态、A 不投第二次，锁定了这条隔离。

看门狗与正常结算的互斥依赖同一 CAS：正常 settle 先发生则看门狗的 `settleFailure` 被 CAS 吞掉，且 `claimTerminalSettle` 赢家会 `watchdogJob?.cancel()`。`liveWatchdogIsCancelledAndSwallowedByNormalSuccessSettle` 刻意让看门狗真实存活在循环中、success 在另一线程提交，断言只投一次 `onConnected`、零 `onDisconnected`，证明的是真正的 settle-vs-live-watchdog 竞争而非 eager 退化路径。看门狗在 `claimTerminalSettle` 与 `onExplicitDetachOrDisconnect` 两处都被取消，无泄漏。

两阶段 settle 的价值在于把 PaymentManager gate（`setEstablishedRecoveryActive`）、`currentControl`、`recovering` 持有到 Host 收尾（`onRecoverySuccess` 重挂 / `onRecoveryFailureCleanup` 清理）完成之后，再由 `finalizeTerminalSettle` 在同一把锁内释放 gate、清 `currentControl`、快照并清空 observer。这关闭了两个窗口：gate 在 Host 收尾前被释放导致新请求落到濒死链路再被本 episode 清理误杀；以及 settling-window 内同配置 `connect()` 落入 `disconnect()→onExplicitDetachOrDisconnect()` 清掉原 observer 的 `episodeObservers`，使原 observer 的终态 E213 丢失。`failureSettleKeepsRecoveryGateClosedUntilHostCleanupCompletes` / `successSettleKeepsRecoveryGateClosedUntilHostReattachCompletes` 用记录 host 断言了 `gate-on → host-teardown → gate-off` 的顺序。

`joinIfRecovering` 现在 `@Synchronized` 且在 `control.settled==true` 的 settling 窗口内仍接纳同配置 joiner（因为 observer 快照被推迟到 finalize，窗口内 observer 集仍存活）。`settlingWindowJoinIsAdmittedAndReceivesTheSingleTerminalOutcome`（coordinator 级，用阻塞 host 卡在 cleanup 制造窗口）与 `sameConfigConnectDuringSettlingWindowDoesNotStrandTheTerminalCallback`（ConnectionManager 级，走真实 `connect()` 链路）断言原 observer 与 late caller 各收恰好一次 E213。verification 记录这两条在 review-B 的拒绝式实现下 pre-fix RED（late joiner 收 0 次 / 真实 connect 超时），修复后转绿，是有效的回归而非空测试。

### off-Main 收尾并发（唯一实质 finding，非阻塞）

D1 把失败结算推到 `watchdogDispatcher`（默认 `Dispatchers.Default`）后，`settleFailure` 里的 `connectionManager.onRecoveryFailureCleanup(reason)` 也在该后台线程执行。`ConnectionManager.onRecoveryFailureCleanup`（`:1060` 附近）直接写 `connectionStatus`、`connectedDeviceId`、`currentConnectionConfig`、`establishedTerminalIdentity` 等共享字段并调用 `kernel.disconnect()`、`failAllPendingTransactions`，`onRecoverySuccess` 写 `connectionStatus` 并 `registerDataReceiver()/setupKernelStatusListener()`——这些恢复钩子与 `retireForRecovery`/`openRecoveryRebuild` **均未**包在 `synchronized(this)` 里（该类的 `synchronized(this)` 只出现在 `:1902/:2040/:2114` 的状态投影路径）。

原设计把 episode 限定在 Main 单 actor，所有 settle 与 Host 收尾都在 Main，天然无竞争。现在 `claimTerminalSettle` 虽然 `episodeJob?.cancel()`，但协程取消是协作式的：在真正的 D1 场景（Main 饿死、body 挂起在 `delay(50)`）里 body 是 inert 的，看门狗独占收尾，安全；但若 Main 此刻正**同步**执行某个 host 调用（`retireForRecovery` 的 `kernel.disconnect()` 或 `openRecoveryRebuild` 的 `connectInternal`），cancel 不会立刻生效，看门狗线程的 `onRecoveryFailureCleanup` 会与 Main 上仍在跑的 body 并发触碰同一内核/字段。最坏情形：body 的 `connectInternal` 在看门狗 `kernel.disconnect()` + 字段清空之后才建成一个 CONNECTED 内核，而 coordinator 已认为链路断开（`connectionStatus=DISCONNECTED`、config 置空），形成一个无人跟踪的活连接（泄漏/状态不一致）。

对外「恰好一次 E213」契约不受影响（CAS 保证单结算、单回调），所以不阻塞；但在支付连接关键路径上值得核实：`connectInternal`/`kernel.disconnect()` 是否会在协作取消点之前真正完成、或恢复钩子是否应补 `synchronized(this)` 以与状态投影路径互斥。plan §7「修复3」曾声称这些字段「已有 synchronized(this) 保护点」，但 settle 路径上的 `onRecoverySuccess`/`onRecoveryFailureCleanup` 实际没有，这个 gap 未在本次修复中关闭。

</details>

<details>
<summary>File map</summary>

- `lib_taplink_sdk/.../manager/EstablishedRecoveryCoordinator.kt` — 核心修复：看门狗 + 可注入 dispatcher、per-generation `EpisodeControl`、两阶段 `claimTerminalSettle`/`finalizeTerminalSettle`、D2 兜底、settling-window join。
- `lib_taplink_sdk/.../manager/ConnectionManager.kt` — 用户已有改动（`recoveryObservers` 并入 `takePendingConnectionListeners`、去重容错断开投递）；恢复钩子 `onRecoverySuccess`/`onRecoveryFailureCleanup` 未改、无锁（见 finding 1）。
- `lib_taplink_sdk/.../manager/ConnectionFailureDispatch.kt` — 用户已有改动：`notifyDistinctConnectionDisconnects` 去重 + 异常隔离。
- `lib_taplink_sdk/build.gradle.kts` — 版本 `1.1.0.1 → 1.1.0.2`；编译参数仅 `-Xskip-metadata-version-check`，无 SAF。
- `.../test/.../EstablishedRecoveryBudgetTest.kt` — 新增 D1/D2/跨 epoch/gate 顺序/settling-window 共 8 条 coordinator 级回归。
- `.../test/.../LanRecoveryIntegrationTest.kt` — 新增 `formalParameterOnlyListenerGetsOneDisconnectOnTimeout`、`sameConfigConnectDuringSettlingWindowDoesNotStrandTheTerminalCallback`；用户原 3 条回归保留。
- `docs/spec/changes/CR-ER-D1D2-20261019.yaml` — L2 CONFORMANCE_FIX 记录（R4/R6/R9，AC4/AC6 映射，RED/GREEN）。
- `docs/spec/coverage/SPEC-ESTABLISHED-RECOVERY-001.yaml` — TC-ER-004/006/009 additional_tests 映射 + 两条 bugfix 记录。
- `RELEASE-NOTES.md` — Unreleased「Connection callback completion」客户视角 bullet，无内部术语/SAF。
- 完整 diff：`git diff HEAD`（分支 `dev_pro`）。

</details>
