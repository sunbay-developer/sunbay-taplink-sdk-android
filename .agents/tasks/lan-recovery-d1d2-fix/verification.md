# Verification — LAN 1006 恢复 D1/D2 + review 迭代修复

> 本次迭代处理 review.json 的 findings（2 blocking + 5 non-blocking）。
> 所有命令 cwd = `/Users/sm4306/StudioProjects/taplinksdk`，实际执行结果如下，reviewer 无需重跑。

## 本轮修复内容对应 review finding

| finding | 源 | 阻塞 | 修复 |
|---|---|---|---|
| 同配置 connect 撤销已 claim 的终态 | review-b | 是 | **settling-window join 修复**：`joinIfRecovering` 不再在 `control.settled==true` 时拒绝。observer 快照已延迟到 `finalizeTerminalSettle`，所以在 claim→finalize 窗口内 observer 集仍存活。只要本代仍是 `currentControl`（即 finalize 尚未把 `recovering` 置 false / 清 `currentControl`）就接纳同配置 joiner，使其与原 observer 共享 finalize 的同一份终态快照，各收恰好一次 E213。finalize 跑完后 `recovering==false`，调用方再走全新连接。`finalizeTerminalSettle(control)` 另加携带并复核本代 control（`currentControl === control`）后才撤 gate/清 observer，防止旧 finalize 无条件清理已变化状态。 |
| join 回归固化无回调结果 | review-b | 是 | 删除 `joinIsRejectedOnceEpisodeHasWonItsTerminalSettle`（它固化了缺陷）。新增 coordinator 级 `settlingWindowJoinIsAdmittedAndReceivesTheSingleTerminalOutcome` + **ConnectionManager 级** `sameConfigConnectDuringSettlingWindowDoesNotStrandTheTerminalCallback`（走真实 `manager.connect()` → join/disconnect 链路，阻塞 Host cleanup 制造 settling 窗口），断言原 observer 与 late caller 各收恰好一次 E213。 |
| Host-teardown throw strands the gate | review-a | 否 | `settleSuccess`/`settleFailure` 把 Host 收尾（onRecoverySuccess/onRecoveryFailureCleanup）包进 try/catch，无论是否抛出都保证 `finalizeTerminalSettle` 执行以撤 gate、清状态、投递终态。 |
| 两项新增覆盖缺少行为级 RED 证据 | review-b | 否 | `formalParameterOnlyListenerGetsOneDisconnectOnTimeout` 与 `liveWatchdogIsCancelledAndSwallowedByNormalSuccessSettle` 在 CR/本文件中明确标为 **preservation/integration coverage**（非 D1 pre-fix RED）。本轮两条新回归均给出可复现 pre-fix RED（见下）。 |
| 并发测试使用非线程安全记录器 | review-b | 否 | `EstablishedRecoveryBudgetTest.Rec` 的 connected/disconnected/errors 改为 `CopyOnWriteArrayList`，消除 executor/watchdog 线程与测试线程并发读写 `ArrayList` 的可见性/结构竞争。 |
| Watchdog callback thread / spec 空白 reflow | review-a | 否 | 仅供参考，无需改码（Tapro 侧 observer 切主线程；SPEC-LAN-RECOVERY-001 空白 reflow 属用户已暂存内容，spec-check EXIT=0）。 |

## 本轮新增/变更回归
- `EstablishedRecoveryBudgetTest.settlingWindowJoinIsAdmittedAndReceivesTheSingleTerminalOutcome`（新增，替换被删的 `joinIsRejected...`）
- `LanRecoveryIntegrationTest.sameConfigConnectDuringSettlingWindowDoesNotStrandTheTerminalCallback`（新增，ConnectionManager 级）
- `EstablishedRecoveryBudgetTest.Rec` 记录器改 `CopyOnWriteArrayList`

保留并通过：用户原 3 条回归（recoveryTakesOwnershipOfPreviouslyQueuedCallers / recoveryFailureDeliversOneOutcomeToTransferredQueuedCaller / throwingDisconnectListenerDoesNotBlockOtherObservers），以及上一轮 D1/D2/跨代/gate 回归。

## RED 证据（修复前先证红）

临时把 `EstablishedRecoveryCoordinator.joinIfRecovering` 回退为「`if (control.settled.get()) return false`」（review-b 的拒绝式实现），仅跑本轮两条新回归：

```
./gradlew :lib_taplink_sdk:testDebugUnitTest \
  --tests "*settlingWindowJoinIsAdmittedAndReceivesTheSingleTerminalOutcome" \
  --tests "*sameConfigConnectDuringSettlingWindowDoesNotStrandTheTerminalCallback" --console=plain
```

实际结果：`2 tests completed, 2 failed`
- `settlingWindowJoinIsAdmittedAndReceivesTheSingleTerminalOutcome` → `AssertionError at EstablishedRecoveryBudgetTest.kt:393`（join 被拒 → late joiner 收到 0 次回调，断言「恰好一次 E213」失败）
- `sameConfigConnectDuringSettlingWindowDoesNotStrandTheTerminalCallback` → `TimeoutCancellationException`（真实 `connect()` 落入 `disconnect()`→`onExplicitDetachOrDisconnect()`，清空原 observer 的 episodeObservers，原 observer 的 E213 被吞，等待超时）

随后恢复修复版（已确认源码无 `TEMP-REVERT` 残留），两条转绿。

## GREEN — 全量两模块（权威结果）

```
./gradlew :lib_taplink_sdk:testDebugUnitTest :lib_taplink_communication:testDebugUnitTest --console=plain
```
- BUILD SUCCESSFUL in 13s
- **lib_taplink_sdk: tests=62 skipped=0 failures=0 errors=0**（上一轮 review 时为 61，本轮净 +1：新增 2 条 settling-window 回归、删除 1 条固化缺陷的 join 回归）
- **lib_taplink_communication: tests=42 skipped=0 failures=0 errors=0**

编译期仅有一条既有告警：`LanRecoveryIntegrationTest.kt:246` `createLanMode(...)` deprecated（全文件既有用法，非本次引入）。

## spec-check

```
./config/speccheck/spec-check.sh   # EXIT=0
```
输出：`coverage: 3 份  SCR: 4 份`；仅 1 条 🟡 提示（`SPEC-LAN-RECOVERY-001.yaml: SRC-4 原文待人工核对`，属用户已暂存草案，非本次改动），**无阻断项**。

## git diff --check
`diff-check clean`（无空白/冲突告警）。

## 版本 / 卫生确认
- `lib_taplink_sdk/build.gradle.kts`：`NAME=1.1.0.2`、`freeCompilerArgs` 仅 `-Xskip-metadata-version-check`（无 `-Xjvm-default=all-compatibility`、无 SAF testImplementation）——未改动（仍为用户 staged 内容）。
- 用户已暂存改动（ConnectionManager.kt / ConnectionFailureDispatch.kt / SPEC-LAN-RECOVERY-001.yaml / build.gradle.kts）working tree 未触碰。
- observer 绑定逻辑未改（`recoveryObservers()` 等）；公开 API 边界未变（全部改动在内部 `manager` 包）。
- 对外回调语义不变：失败仍恰好一次 `onDisconnected(E213)`（非 onError）、成功恰好一次 `onConnected`、单次 5000ms 预算、单 attempt。未带入 dev_SAF 内容。
