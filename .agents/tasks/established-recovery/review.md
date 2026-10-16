# SDK-managed one-shot recovery for established connections

The change introduces a connection-level `EstablishedRecoveryCoordinator` that becomes the single owner of every "already-established connection faulted" event across all SDK adapters. When an established link drops abnormally, the SDK now runs exactly one silent, internal, same-protocol/same-terminal rebuild within a 5000ms monotonic budget before reporting any terminal result; first-connect failures keep the existing behavior. Recovery is fully silent to callers (no progress, no `onError`, no transient `onDisconnected`), the registered `ConnectionListener` is never removed by the SDK, each distinct observer gets exactly one terminal callback (`onConnected` on success / `onDisconnected(E213)` on failure, never `onError`), and in-flight transaction callbacks are preserved on success or cleared exactly once on failure with no synthetic early E306. The work faithfully realizes the Q1–Q4 rulings (S10–S14) and R1–R15.

Watch for:
- **Review-base scoping (confirmed, non-blocking):** the task's change is exactly the single HEAD commit `fc741dd`; a `git diff main` surfaces ~157 inherited `dev_pro` commits (OPT-R1 fast recovery, `SdkVersion` 1.1.0→1.1.0.1 in `f935c54`, `secring.gpg` from `init project`, `sdk_publish.gradle`) that are NOT part of this task and are out of scope. Judged against the HEAD commit only.
- **Coverage `test_inventory` drift (likely, non-blocking):** `docs/spec/coverage/SPEC-ESTABLISHED-RECOVERY-001.yaml` still carries the spec-authoring-stage metadata `tests_executed: false`, `implementation_changed: false`, every `TC-ER-00x: PLANNED_NOT_IMPLEMENTED`, and an `independent_review` path under `/private/tmp`. The code and tests now exist; this is documentation drift, not a spec-check gate failure.

**Verdict**: APPROVED

## High-level view

The architecture matches design §2 option B: one coordinator above the adapters rather than per-kernel retry loops. `ConnectionManager` implements a `RecoveryHost` seam the coordinator drives (capture identity, retire, rebuild, readiness poll, validate, success/failure settle), which also makes the coordinator unit-testable through real callback wiring with scripted hosts and a fake clock.

The error-prone invariant — an established fault must NEVER become a public `onError` — is handled by claiming the recovery epoch synchronously at the single `connectInternal.onDisconnected` entry point *before* `isConnectionError` classification, and early-returning. That one early return blocks `onError`, transient `onDisconnected`, `failAllPendingTransactions`, `ReconnectManager`, legacy fast-recovery, and `updateConnectionStatus(DISCONNECTED/ERROR)` on the established path. The second fault entry (kernel status listener) is guarded the same way, and the self-healing guard is extended to treat `coordinator.isRecovering()` as self-healing so the kernel's ERROR→DISCONNECTED jitter neither surfaces a callback nor invokes `connectionDisconnectedListener`.

The recovery logical-state projection (`isConnected=false && isConnecting=true`) is enforced by the three named `ConnectionManager` changes from design Finding 1: the status-listener field write is guarded, `isConnecting()` returns true during recovery, and `isConnected()` short-circuits to false without syncing from the jittering kernel. The terminal-dedup set is protected both by the self-healing guard and by an idempotent backstop in `PaymentManager.onPhysicalDisconnected()`.

Terminal delivery is exactly-once via an `IdentityHashMap` observer set plus an `AtomicBoolean settled` CAS; success/failure/explicit-cancel all route through the single settle. Failure clears in-flight tx callbacks once as E213 (not E306) alongside the one `onDisconnected`. Payment gating rejects new requests with E213 before send. Identity validation fails closed per adapter (LAN mDNS serial; VSP/RS232/AOA USB serial captured only with existing permission; local AIDL same package), and `evaluateRecoveryPrerequisite()` is read-only and non-interactive.

Release boundary is respected in the reviewed commit: no `SdkVersion` bump, no AAR/publish task, no downstream edits, no new public API (recovery types live in internal `manager`/`communication` packages), English Conventional Commit with no scope parens, and spec/SCR/coverage/source docs committed together with the behavior code. Hardware/Host items (B1–B5) are honestly recorded as UNVERIFIED/BLOCKED.

<details>
<summary>Issues (2)</summary>

1. **Coverage test_inventory drift** — `SPEC-ESTABLISHED-RECOVERY-001.yaml` still shows `tests_executed: false`, `implementation_changed: false`, all `TC-ER-00x: PLANNED_NOT_IMPLEMENTED`, and a `/private/tmp` independent-review path, although the implementation and TC-ER-001..015 tests now exist. Non-blocking documentation drift; recommend updating the inventory to reflect implemented/executed status for traceability. Does not affect the spec-check gate (which only validates status/approval consistency, not inventory execution flags).
2. **`recoveryObservers()` relies on listener identity convergence** — the coordinator's observer set is built from `connectionListener` + `reconnectManager.getLastConnectionListener()`, not the fault-time `listener` passed into `connectInternal.onDisconnected`. In practice these are the same instance (the integrator registers one listener for both `setConnectionListener` and `connect`), and the real-transport wiring test confirms the terminal reaches it. Non-blocking; flagged only as a dependency worth keeping in mind if the SDK ever supports divergent per-connect listeners.

</details>

<details>
<summary>Details</summary>

### Single owner and the fault-interception seam (R1/R5/R6)

The coordinator is the sole owner. Both established-fault entry points funnel into `interceptIfEstablishedFault(TransportFaultEvent(..., established = hasEverEstablishedThisSession))`, which claims the epoch synchronously (`@Synchronized claimEpoch`, `AtomicLong` CAS) and returns true so the caller early-returns.

Entry point 1 is the top of `connectInternal`'s `ConnectionCallback.onDisconnected`, placed *before* `val isConnectionError = isConnectionError(code, msg)`. This is the structural guarantee that an established fault never reaches the `onError(E232)` branch — `onError` is thereby scoped to `connect()`/first-connect only, exactly as S14/Q2 ruled. Entry point 2 is `setupKernelStatusListener`, where a kernel drop to DISCONNECTED/ERROR while the SDK was active is also claimed before any field mutation or notification.

Because `claimEpoch` runs synchronously ahead of classification, the early return also blocks `updateConnectionStatus(DISCONNECTED/ERROR)` and its `connectionDisconnectedListener` invocation on this path — closing design leak points 2, 3, and trigger (a) of leak point 4.

### Full silence during recovery (R6)

`setupKernelStatusListener`'s self-healing guard is now `reconnectManager?.isReconnecting() == true || isFastRecovering || establishedRecoveryCoordinator.isRecovering()`, so the `droppedToInactive && wasActive && !isSelfHealing` branch neither surfaces `onDisconnected` nor invokes `connectionDisconnectedListener` during recovery (leak point 1 and trigger (b) of leak point 4). The real-transport `EstablishedRecoveryWiringTest.establishedDropYieldsSingleE213DisconnectNoError` asserts `events.errors.isEmpty()` and exactly one terminal outcome through the real `ConnectionManager` + `LanClientKernel` + local WebSocket — the established fault is absorbed internally with no `onError`/transient `onDisconnected`.

### Logical-state projection during recovery (R5/AC5)

The three named changes from design Finding 1 are present: the status-listener `synchronized(this){ connectionStatus = newStatus }` write is wrapped in `if (!establishedRecoveryCoordinator.isRecovering())`; `isConnecting()` returns true when recovering; `isConnected()` returns false without syncing from the kernel when recovering. Kernel ERROR→DISCONNECTED jitter can no longer flip the projection to a terminal state mid-recovery. `updateConnectionStatus` additionally short-circuits during recovery, recording `hasEverEstablishedThisSession = true` on a CONNECTED rebuild without surfacing anything.

### Terminal-dedup preservation (R10/R15)

Two layers guard `ResponseProcessor.clearProcessedTerminalEvents()`: the self-healing guard prevents `onPhysicalDisconnected()` from being invoked at all during recovery, and `PaymentManager.onPhysicalDisconnected()` additionally early-returns when `connectionManager.isEstablishedRecoveryActive()`. On success, `onRecoverySuccess()` re-registers the data receiver and re-runs `setupKernelStatusListener`, then restores CONNECTED — without replaying, swapping callbacks, or clearing dedup. The traceId→callback mapping lives in `LocalCallbackManager` and is not touched, so only the receive route is re-wired (R15, SDK side).

### Exactly one terminal, listener retention, cancellation (R6/R9)

`settleSuccess`/`settleFailure` both gate on `settled.compareAndSet(false, true)` and an epoch match, so success, failure, timeout, and explicit cancel each deliver at most one terminal. Observers are deduplicated by `IdentityHashMap`; a throwing observer is caught and does not block others (`throwingObserverDoesNotBlockOthers`). `joinIfRecovering` lets a same-config `connect()` caller join the single outcome; a different-config `connect()` fences the episode and starts a fresh session. `onExplicitDetachOrDisconnect()` (called only from `disconnect()`/`removeConnectionListener()`) is the sole external cancellation path and never removes the listener itself — `removeConnectionListener()` sets `connectionListener = null` only after fencing, which is the caller's explicit removal, not an SDK removal during recovery. SDK re-init is whole-instance teardown (design §13), correctly outside the "SDK removes listener during recovery" prohibition.

### One serial attempt, 5000ms budget, no stacked chains (R4)

`claimEpoch` merges duplicate faults into the active episode (`duplicateFaultsMergeIntoOneEpisode` asserts one rebuild). The episode body does retire → single `openRecoveryRebuild` → poll-until-ready-or-deadline, with `attemptLimit=1` and the deadline from the injected `RecoveryClock`. The legacy fast-recovery entry is gated with `!establishedRecoveryCoordinator.isRecovering()` so the coordinator-driven `connectInternal` rebuild cannot spawn a second 5000ms budget or the early-E306 cleanup. `EstablishedRecoveryBudgetTest` proves a deadline during polling fails once as E213 with exactly one rebuild, and a deadline before rebuild fences without opening a successor.

### Existing-request timeout does not spawn a second episode (R11)

The interception gate only fires on an established fault with no active episode; an in-flight request's own timeout path does not route through `interceptIfEstablishedFault`, and the controller tests confirm a new request during recovery is rejected with E213 (`recoveryTogglesPaymentGate`, plus `PaymentManager.executeSnapshot`'s pre-send E213 rejection) rather than opening a new episode.

### Original-identity validation, quarantine, no silent switch (R8)

`validateRecoveredIdentity` fails closed for every adapter when `!expected.isReliable`. LAN uses the mDNS-bound serial; `EstablishedRecoveryAdapterWiringTest` proves same-serial validates, different-serial refuses, and a missing serial fails closed. VSP/RS232/AOA capture `UsbDevice.serialNumber` only under existing permission (B1 honestly flags real-device capture as unverifiable); local AIDL rebinds only the same package. The coordinator calls `validateRecoveredIdentity` only after readiness and settles failure if identity is unconfirmed — never auto-attaching to another terminal.

### Failed recovery clears tx callbacks once as E213, no early E306 (R10/Q4)

`onRecoveryFailureCleanup` calls `failAllPendingTransactions(E213...)` exactly once, invoked from `settleFailure` alongside the single `onDisconnected(E213)`. The legacy `fastRecoveryFallback` E306 early-clear is unreachable on the established path (gated out), so no synthetic early E306 occurs. `identityUnconfirmedFailsWithSingleE213AndCleanupOnce` asserts one E213 disconnect, one cleanup, zero success, no onError.

### Non-interactive recovery (R14)

`evaluateRecoveryPrerequisite()` is read-only and returns `Denied` (not a permission prompt) when no existing effective authority is present; a `Denied` prerequisite ends the attempt before any rebuild (`deniedPrerequisiteFailsWithoutRebuild`). Adapter overrides place the check ahead of the permission/pairing branches.

### Public API surface and release boundary (R13/AC13)

The reviewed commit `fc741dd` touches `ConnectionListener.kt` and `ConnectionConfig.kt` with KDoc-only edits (no signature change), adds internal types in the `manager` and `communication.interfaces.recovery` packages, and adds the two `IServiceKernel` default methods (internal library, not exported). No `build.gradle.kts` / `SdkVersion` change is in this commit, no publish task, no downstream edits. `RELEASE-NOTES.md` documents the behavior change accurately. The commit message is an English Conventional Commit (`feat:`) with no scope parens. Spec, SCR, coverage, and source docs are committed in the same commit as the behavior code.

### Verification evidence

`verification.md` records compile + unit tests passing (SDK 44, communication 42), spec-check with no 🔴 (one pre-existing 🟡 on a different spec), and `git diff --check` clean. The spec-check gate (`spec_check.py`) validates status/approval/rule consistency and high-risk open questions; it does not gate on `test_inventory` execution flags, so the "no 🔴" claim is consistent with the stale inventory metadata. Blockers B1–B5 (non-LAN Host continuation, SUB_SCREEN screen readiness, USB re-enumeration, local AIDL process-restart, AOA accessory audit) are explicitly recorded as JVM-unverifiable and not claimed as passing. I did not re-run the suites; the evidence is internally consistent and no specific doubt warranted a spot-check beyond reading the code and tests.

</details>

<details>
<summary>File map</summary>

Reviewed scope = HEAD commit `fc741dd` only (31 files). Full diff: `git -C /Users/sm4306/StudioProjects/taplinksdk/.worktrees/established-recovery show fc741dd`.

- `sdk/manager/EstablishedRecoveryCoordinator.kt` — new single-owner coordinator + `RecoveryHost` seam.
- `sdk/manager/RecoveryClock.kt`, `RecoveryModel.kt` — injectable monotonic clock, `TransportFaultEvent`/`RecoveryEpisode`.
- `sdk/manager/ConnectionManager.kt` — fault interception, logical-state projection, retire/rebuild/validate hooks, session gate, fast-recovery mutual-exclusion.
- `sdk/manager/PaymentManager.kt` — E213 pre-send rejection + dedup-set backstop.
- `communication/interfaces/IServiceKernel.kt`, `interfaces/recovery/RecoveryIdentity.kt` — internal recovery hooks + identity/prereq types.
- `communication/.../{LanClientKernel,VSPClientKernel,SerialServiceKernel,CableAoaHostKernel,LocalServiceKernel}.kt` — per-adapter identity/prereq overrides.
- `sdk/callback/ConnectionListener.kt`, `sdk/config/ConnectionConfig.kt` — KDoc-only contract clarifications.
- Tests: `EstablishedRecoveryControllerTest.kt`, `EstablishedRecoveryBudgetTest.kt`, `EstablishedRecoveryWiringTest.kt` (SDK), `EstablishedRecoveryAdapterWiringTest.kt` (communication), `LanRecoveryIntegrationTest.kt` (updated to new established behavior).
- Docs: spec, SCR, coverage, source request, design/plan/verification/blockers, RELEASE-NOTES, `.gitignore` negative-ignores for the new tests.

</details>
