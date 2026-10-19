# LAN 恢复终态看门狗与跨代结算

本次修复为已建立 LAN 连接的 1006 恢复增加独立预算看门狗，并为非显式取消补上唯一的 `onDisconnected(E213)` 终态。实现以每代 `EpisodeControl`、两阶段 settle 和 current-control 围栏统一看门狗、正常结算与取消路径；前一轮发现的 settling-window 同配置 `connect()` 丢回调问题也已改为继续 join。D1、D2 及同配置 join 已有 RED→GREEN 和全量验证证据，但失败 Host cleanup 同步触发交易回调时，重入的不同配置 `connect()` 仍会提前撤销当前 control，随后被旧 cleanup 清状态并断开，破坏新会话且吞掉旧 E213。该跨代竞态违反已批准的 R9/AC9，合入前需要修复并增加 ConnectionManager 级回归。

Watch for: **[confirmed]** `onRecoveryFailureCleanup()` 先同步执行交易失败回调；若回调重入不同配置 `connect()`，旧 episode 会被 fence，但旧 cleanup 返回后仍继续清连接字段并断开当前 kernel，可直接拆掉刚启动的新连接，同时旧 observer 快照已被清空。

**Verdict**: NEEDS_CHANGES

## High-level view

D1 已与 `Dispatchers.Main` 解耦：watchdog 使用独立 scope，生产默认跑在 `Dispatchers.Default`，deadline 仍由注入的 `RecoveryClock` 判定。Main 不派发轮询续体时仍有独立结算者，Unconfined scope 与快钟也能确定性驱动测试。

D2 使用每代独立的显式取消标志和 settle gate。非显式 `CancellationException` 仅在本代未 settled 时竞争失败终态并原样 rethrow；显式 detach 继续保持不投回调。所有结算者都须通过 `currentControl === control` 与本代 CAS，旧代不能烧掉新代 token。

claim → Host teardown → finalize 两阶段协议让支付门禁覆盖 teardown，并把 observer 快照推迟到 finalize。同配置 caller 在 settling window 内继续加入尚未快照的 observer 集，前序 reviewer 指出的原 observer E213 丢失问题已修复。

剩余缺口是不同配置 supersede。Host 失败清理会同步回调集成方代码；该代码重入 `connect(newConfig)` 后，可在旧 cleanup 完成前释放旧代并启动新连接，旧 cleanup 随即继续操作共享状态与当前 kernel，绕过两阶段协议要保证的跨代隔离。

<details>
<summary>Issues (1)</summary>

1. **[confirmed, blocking] 不同配置重入被旧 cleanup 反向拆除** — 交易失败回调内调用 `connect(newConfig)` 会提前 fence 旧 control 并启动 fresh session，旧 `onRecoveryFailureCleanup()` 返回后仍清共享字段并断开当前 kernel；应延迟 settling 阶段的 superseding connect，或保证旧资源清理在任何应用回调之前完成，并增加 ConnectionManager 级重入回归。

</details>

<details>
<summary>Details</summary>

## 独立 watchdog 保留可注入预算语义

`startWatchdog()`（`EstablishedRecoveryCoordinator.kt:224`）由独立 `watchdogScope` 驱动，25ms tick 只重读同一个 `clock`，deadline 仍是 episode claim 时生成的 `deadlineElapsedMs`；没有第二次 rebuild 或第二份预算。watchdog 启动前及 job 写回后都复核本代是否仍 current/未 settled，覆盖 Unconfined 下 episode 同步结算早于 watchdog 挂接的顺序；赢家由 `claimTerminalSettle()`（`:406`）取消本代两个 job。

`watchdogSettlesFailureWhenEpisodeScopeIsStarved` 把 episode executor 持续占满，再用 Unconfined + StepClock 让 watchdog 单独到点，修复前会因没有独立结算者而超时。生产 dispatcher 通过内部构造参数注入，集成测试也会反射替换 `watchdogScope` 并配合 6 倍快钟，因此没有把既有回归拖成真实 5 秒。

## D2 与 terminal-once 在每代 control 上汇合

取消分支（`EstablishedRecoveryCoordinator.kt:349`）仅在 `!cancelledByExplicitDetach && !settled.get()` 时调用 `settleFailure(control)`，随后继续抛出原 `CancellationException`。显式路径（`:516`）在取消 job 前写入本代标志并烧掉本代 gate。`claimTerminalSettle()` 先复核 `currentControl` 再竞争本代 CAS，`finalizeTerminalSettle(control)`（`:435`）再次复核后才释放 gate、清状态和快照 observers，因此 watchdog、正常结算、取消兜底与迟到旧代续体只能有一个有效赢家。

## 不同配置 connect 可在旧失败 cleanup 内重入并拆掉新链路

**[confirmed, high, blocking]** `ConnectionManager.onRecoveryFailureCleanup()`（`ConnectionManager.kt:1061-1074`）首先调用 `paymentManager.failAllPendingTransactions()`，之后才清状态/身份/配置并对 `TaplinkServiceKernel.getCurrentServiceKernel()` 执行 `disconnect()`。`LocalCallbackManager.failAllCallbacks()`（`LocalCallbackManager.kt:229-240`）在调用线程同步遍历并执行 `InnerCallback.onError`，所以应用回调可在 Host teardown 尚未完成时重入 SDK。

不同配置 `connect()` 在 recovery active 时会立即调用 `onExplicitDetachOrDisconnect()`（`ConnectionManager.kt:224-234`）。后者（`EstablishedRecoveryCoordinator.kt:516-533`）把 `recovering` 置 false、释放 gate、清空 `currentControl` 和 `episodeObservers`，随后 `connect()` 可启动 fresh session/kernel。回调返回后，旧 cleanup 继续清共享字段并断开“当前” kernel；旧 finalize 又因 control 已非 current 返回空快照。

```text
旧 settleFailure / Host cleanup          应用 PaymentCallback
--------------------------------        -------------------
failAllPendingTransactions
  -> onError -------------------------> connect(newConfig)
                                       fence old control
                                       clear old observers/gate
                                       start fresh connection
  <----------------------------------- return
clear shared connection state
 disconnect(getCurrentServiceKernel)    # 可拆 fresh kernel
finalize(old) -> non-current / empty     # 原 observer 无 E213
```

失败批量回调本身就是同步重入点，且 SDK 入口允许从任意线程调用，因此该路径可确定到达。它违反 active spec R9/AC9 的“new config starts fresh；obsolete cleanup/callback cannot mutate or close successor”。

修复需让不同配置 connect 识别“旧 Host teardown 尚未完成”，不能在该阶段 fence 后立刻 fresh-connect。可把 coordinator 状态扩展为 `ACTIVE / SETTLING / IDLE` 并在 `SETTLING` 延迟 superseding connect 到 finalize 后；或调整失败清理顺序/分发，使旧状态与旧 kernel 在任何应用回调前完成清理。回归应从 `failAllPendingTransactions` 的同步 callback 内调用不同配置 connect，并断言 fresh session 不被旧 cleanup 清字段或断开、旧 observer 的终态符合契约且无跨代重复回调。

## 回归证据与交付边界

coder 的 `verification.md` 记录 SDK 62/0、communication 42/0，spec-check EXIT=0，`git diff --check` clean；按任务约束本审查未重跑套件。D1、D2、跨代 token、两阶段 gate 和同配置 settling join 均有针对性证据；形参 listener 与 live-watchdog success 被正确标为 preservation/integration coverage。用户原三条回归 `recoveryTakesOwnershipOfPreviouslyQueuedCallers`、`recoveryFailureDeliversOneOutcomeToTransferredQueuedCaller`、`throwingDisconnectListenerDoesNotBlockOtherObservers` 仍在且记录为通过。现有 settling 测试只覆盖同配置 join，未覆盖上述不同配置重入。

失败仍为一次 `onDisconnected(E213)`、不投 `onError`，成功为一次 `onConnected`，无中间回调；预算仍为 5000ms、单 rebuild attempt。实现位于内部 `manager` 包，未向公开 API 泄漏内部类型。版本为 `1.1.0.2`，编译参数只有 `-Xskip-metadata-version-check`，RELEASE-NOTES 无 SAF；被忽略的用户 `FastRecoveryModeAgnosticTest.kt` 仍存在，observer 绑定来源也未被 D1/D2 改写。

</details>

<details>
<summary>File map</summary>

- `lib_taplink_sdk/src/main/.../manager/EstablishedRecoveryCoordinator.kt` — 独立 watchdog、每代 control、D2 兜底、两阶段 settle 与 settling-window join。
- `lib_taplink_sdk/src/main/.../manager/ConnectionManager.kt` — pending listener 转移、断开去重及连接/Host cleanup 链路。
- `lib_taplink_sdk/src/main/.../manager/ConnectionFailureDispatch.kt` — identity 去重与 listener 异常隔离。
- `lib_taplink_sdk/src/test/.../manager/EstablishedRecoveryBudgetTest.kt` — D1/D2、watchdog、跨代、gate 与 coordinator 级 join 回归。
- `lib_taplink_sdk/src/test/.../manager/LanRecoveryIntegrationTest.kt` — 形参 listener、用户原回归及真实同配置 settling connect。
- `docs/spec/changes/CR-ER-D1D2-20261019.yaml`、`docs/spec/coverage/*.yaml` — 达标修复、证据与测试映射。
- `lib_taplink_sdk/build.gradle.kts`、`RELEASE-NOTES.md` — 1.1.0.2 构建与客户可见修复说明。

完整差异：在 `/Users/sm4306/StudioProjects/taplinksdk` 执行 `git diff HEAD`；被忽略的测试/任务文件按实际路径单独查看。

</details>
