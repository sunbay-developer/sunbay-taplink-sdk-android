# Verification — Established Connection Recovery (SPEC-ESTABLISHED-RECOVERY-001)

Worktree: `/Users/sm4306/StudioProjects/taplinksdk/.worktrees/established-recovery`
Branch: `feat/established-recovery`. All commands run from the worktree root.

## What was run and the result

| Check | Command | Result |
|---|---|---|
| Compile communication module | `./gradlew :lib_taplink_communication:compileDebugKotlin` | PASSED |
| Compile SDK module | `./gradlew :lib_taplink_sdk:compileDebugKotlin` | PASSED |
| SDK unit tests | `./gradlew :lib_taplink_sdk:testDebugUnitTest` | PASSED (44 tests, 0 failures) |
| Communication unit tests | `./gradlew :lib_taplink_communication:testDebugUnitTest` | PASSED (42 tests, 0 failures) |
| Spec mechanical check | `./config/speccheck/spec-check.sh` | PASSED — no 🔴 (one pre-existing 🟡 on a different spec, `SPEC-LAN-RECOVERY-001` SRC-4, not this change) |
| Whitespace/diff hygiene | `git diff --check` | CLEAN |

Note: `local.properties` (`sdk.dir`) was created in the worktree so Gradle could resolve the Android
SDK; it is gitignored and not part of the change.

## New tests (per approved AC → TC-ER mapping)

Controller tests drive the REAL `EstablishedRecoveryCoordinator` logic and real `ConnectionListener`
callbacks; the integration test drives the real `ConnectionManager` + `LanClientKernel` + local
WebSocket transport.

- `lib_taplink_sdk/.../manager/EstablishedRecoveryControllerTest.kt`
  - firstConnectFaultIsNotIntercepted — §AC3 / TC-ER-003
  - successDeliversExactlyOneConnectedPerObserverNoError — §AC6 / TC-ER-006
  - identityUnconfirmedFailsWithSingleE213AndCleanupOnce — §AC6/§AC10 / TC-ER-006, TC-ER-010
  - duplicateFaultsMergeIntoOneEpisode — §AC4 / TC-ER-004
  - deniedPrerequisiteFailsWithoutRebuild — §AC12 / TC-ER-012
  - explicitDetachFencesEpisode — §AC9 / TC-ER-009
  - throwingObserverDoesNotBlockOthers — §AC6 / TC-ER-009
  - joinedCallerReceivesSingleTerminalOutcome — §AC9 / TC-ER-009
  - recoveryTogglesPaymentGate — §AC5/§AC11 / TC-ER-005, TC-ER-011
  - retireRunsBeforeRebuild — §AC7 / TC-ER-007
- `lib_taplink_sdk/.../manager/EstablishedRecoveryBudgetTest.kt`
  - deadlineDuringPollingFailsOnceWithOneAttempt — §AC4 / TC-ER-004
  - deadlineBeforeRebuildFencesWithoutOpeningSuccessor — §AC4/§AC7 / TC-ER-004, TC-ER-007
- `lib_taplink_sdk/.../manager/EstablishedRecoveryWiringTest.kt`
  - establishedDropYieldsSingleE213DisconnectNoError — §AC5/§AC6/§AC10 / TC-ER-005, TC-ER-006, TC-ER-010
    (real transport; established fault NEVER surfaces onError; exactly one terminal outcome; listener retained)
- `lib_taplink_communication/.../EstablishedRecoveryAdapterWiringTest.kt`
  - lanPrerequisiteIsAvailableWithoutUsbPermission — §AC1/§AC14 / TC-ER-001, TC-ER-014
  - lanIdentityValidatesSameSerialOnly — §AC8 / TC-ER-008
  - lanMissingSerialFailsClosed — §AC8 / TC-ER-008

### Note on test harness

The coordinator episode runs on the Main-confined `sdkScope` in production. Plain JVM unit tests do
not provide a dispatching Main looper (no `kotlinx-coroutines-test` dependency in this repo), so the
real-wiring integration tests inject an `Unconfined` scope and a scaled `RecoveryClock` into the
coordinator via reflection to let the real episode body settle on the test thread. The coordinator's
production code (scope/clock) is unchanged; only the test substitutes them.

## Regressions preserved (kept green)

- `LanRecoveryIntegrationTest` (first-connect recovery, discovery, cancellation, fast-recovery error
  path, kernel-status self-healing). The one test that encoded the OLD "established drop → immediate
  onDisconnected" behavior (`establishedLinkDropStillNotifiesTheObserver`) was updated to the approved
  new behavior: the observer is still notified exactly once, via the recovery outcome, never onError.
- `FieldSetterCompatibilityTest` (config-setter compatibility) — no public config signature change.
- `TaplinkClientIntegrationTest` (payment result / INIT) — no amount/tip/Refund/Abort/INIT change.
- Communication suite (handshake order, peer restart, discovery retry, heartbeat, protocol).

## UNVERIFIED (hardware / Host — NOT authorized here)

See `blockers.md`. B1 (VSP/RS232/AOA serial-anchor capture on real devices + non-LAN Host-side
continuation), B2 (SUB_SCREEN screen readiness), B3 (USB physical re-enumeration/replug/port power),
B4 (local AIDL process-restart semantics), B5 (AOA accessory shared classes) are NOT JVM-verifiable
and are recorded as UNVERIFIED. Non-LAN Host end-to-end continuation lacking Host-support evidence is
a BLOCKED gap, not a passing cross-protocol test.

## Boundary compliance

- No AAR packaging, Maven publish, or `assembleRelease`/publish task was run.
- No `SdkVersion` bump; no downstream repo edits (tapro / taplinkdemo / sunbay-open-docs).
- No new public API; recovery types are internal to the embedded `communication` module and the SDK
  `manager` package (both non-exported). Public signatures unchanged (KDoc-only doc updates on
  `ConnectionConfig.autoReconnect` and `ConnectionListener`).
