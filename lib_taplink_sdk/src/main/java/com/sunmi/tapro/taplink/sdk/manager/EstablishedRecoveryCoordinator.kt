package com.sunmi.tapro.taplink.sdk.manager

import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.communication.interfaces.recovery.RecoveryPrereqResult
import com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Single, connection-level owner of established-connection fault recovery for ALL SDK adapters.
 *
 * An established connection that drops abnormally gets EXACTLY ONE SDK-internal
 * same-protocol/same-terminal rebuild attempt (bounded by a 5000ms monotonic budget) before a final
 * disconnect is reported. Recovery is fully silent to callers: no onReconnecting, no onError, no
 * transient onDisconnected. Each distinct observer gets exactly one terminal callback — one
 * onConnected on success, or one onDisconnected(E213) on failure (never onError). The registered
 * ConnectionListener is never removed by the SDK during recovery.
 *
 * This class is the only place that owns the recovery epoch, resource retirement, identity
 * validation, callback dedup, and in-flight continuation/cleanup orchestration. It sits above the
 * adapters and is NOT a per-kernel loop.
 *
 * SPEC-ESTABLISHED-RECOVERY-001 (design §3.1, §5, §7; R1/R4/R5/R6/R7/R8/R9/R10/R12/R14/R15).
 *
 * Internal to the SDK; never exported to the public API.
 */
/**
 * The operations the coordinator drives on the connection layer. Implemented by [ConnectionManager]
 * in production and by fakes in deterministic JVM controller tests so the coordinator's epoch /
 * dedup / deadline / terminal-once logic can be exercised through the real callback wiring.
 *
 * SPEC-ESTABLISHED-RECOVERY-001 (design §3.1).
 */
internal interface RecoveryHost {
    fun captureCurrentTerminalIdentity(): TerminalIdentity?
    fun currentConnectionConfigForRecovery(): com.sunmi.tapro.taplink.sdk.config.ConnectionConfig?
    fun recoveryObservers(): List<ConnectionListener>
    fun setEstablishedRecoveryActive(active: Boolean)
    fun evaluateRecoveryPrerequisite(): RecoveryPrereqResult
    fun retireForRecovery()
    fun openRecoveryRebuild(originalConfig: com.sunmi.tapro.taplink.sdk.config.ConnectionConfig?)
    fun isRecoveryTransportReady(): Boolean
    suspend fun validateRecoveredIdentity(expected: TerminalIdentity): Boolean
    fun onRecoverySuccess()
    fun onRecoveryFailureCleanup(reason: String)
    fun getConnectedDeviceId(): String?
    fun getTaproVersion(): String?
}

internal class EstablishedRecoveryCoordinator(
    private val connectionManager: RecoveryHost,
    private val clock: RecoveryClock,
    private val scope: CoroutineScope,
    /**
     * Dispatcher for the independent recovery-budget watchdog (D1 fix). Defaults to
     * [Dispatchers.Default] so the deadline-reached settle is driven off a thread pool that is NOT
     * the Main-confined [scope]; this makes the "deadline must settle" guarantee independent of Main
     * scheduling (ANR / blocking integrator callbacks). JVM tests may inject an Unconfined dispatcher
     * so the watchdog is driven deterministically by the injected [clock].
     */
    private val watchdogDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val TAG = "EstablishedRecovery"

    companion object {
        /** Total monotonic budget for a single recovery episode (cleanup + rebuild + readiness). */
        const val RECOVERY_BUDGET_MS = 5000L

        /** Poll interval while waiting for the rebuilt transport to become ready. */
        private const val POLL_MS = 50L

        /**
         * Watchdog self-check heartbeat. Short real-time tick (NOT the budget) used only to re-read
         * the injected monotonic [clock] against the episode deadline. It does not change the single
         * 5000ms budget semantics; it only turns "deadline reached => settle" from something that
         * depends on Main polling continuations into a scheduling-independent hard guarantee.
         */
        private const val WATCHDOG_TICK_MS = 25L
    }

    /**
     * Scope for the independent budget watchdog, kept separate from [scope] so Main starvation on the
     * episode scope cannot also starve the watchdog (D1 fix).
     */
    private val watchdogScope = CoroutineScope(SupervisorJob() + watchdogDispatcher)

    private val epochCounter = AtomicLong(0)

    /**
     * Per-episode mutable control block. All state that a settle/cancel path reads or mutates
     * (the one-time [settled] gate, the [cancelledByExplicitDetach] flag, and the episode/watchdog
     * [Job]s) is bundled here and bound to a single [RecoveryEpisode] generation so a stale episode
     * can NEVER touch a newer episode's gate. The coordinator keeps exactly one [currentControl] at a
     * time; a settle only acts when it is still the current control AND wins its OWN [settled] CAS,
     * both checked inside the coordinator's synchronized boundary. This is the fix for the cross-epoch
     * poisoning race: a late/stale episode settling against its own (no-longer-current) control cannot
     * flip a successor episode's token to `true`.
     *
     * SPEC-ESTABLISHED-RECOVERY-001 (design §3.6; R4/R6/R7).
     */
    private class EpisodeControl(val episode: RecoveryEpisode) {
        /** One-time settle gate for THIS episode only; never shared across generations. */
        val settled = AtomicBoolean(false)

        /**
         * Distinguishes an explicit caller detach/disconnect cancel (which intentionally delivers no
         * callback) from any other cancellation of the episode coroutine (D2 fix). Only the explicit
         * detach path sets this on THIS episode's control; any other cancel must still back-stop a
         * single terminal onDisconnected(E213) so an ended episode never goes silent. Per-episode so a
         * stale episode's delayed cancellation reads its OWN flag, not a successor's.
         */
        @Volatile
        var cancelledByExplicitDetach = false

        /** The coroutine running this episode body; cancelled on explicit detach/disconnect. */
        @Volatile
        var episodeJob: Job? = null

        /** The watchdog coroutine for this episode; cancelled on settle/clear of this generation. */
        @Volatile
        var watchdogJob: Job? = null
    }

    /** `true` from the synchronous claim until the terminal settle of the current episode. */
    @Volatile
    private var recovering = false

    /** The control block for the currently-owned episode, or null when idle. */
    @Volatile
    private var currentControl: EpisodeControl? = null

    /**
     * Observers that must receive the single terminal outcome of the current episode. Identity set so
     * a listener registered in multiple roles is notified at most once.
     */
    private val episodeObservers = Collections.newSetFromMap(IdentityHashMap<ConnectionListener, Boolean>())

    /** @return `true` while an episode is active (claim..settle). */
    fun isRecovering(): Boolean = recovering

    /** @return the epoch currently owned, or 0 if idle. Used for stale-callback fencing (R7). */
    fun currentEpoch(): Long = currentControl?.episode?.epoch ?: 0L

    /**
     * Synchronous entry from [ConnectionManager]'s fault paths. Claims the recovery epoch BEFORE any
     * status listener can emit a terminal callback or clear payments, then schedules the episode body.
     *
     * @return `true` only when [event] is an established fault and this call claimed a NEW episode;
     *         when `true` the caller MUST early-return without classifying/forwarding the fault.
     */
    fun interceptIfEstablishedFault(event: TransportFaultEvent): Boolean {
        if (!event.established) {
            // R3: first-connect / never-established faults keep the existing strategy.
            return false
        }
        return claimEpoch(event)
    }

    /** @return `true` if a new episode was claimed; `false` if one is already active (merge). */
    @Synchronized
    private fun claimEpoch(event: TransportFaultEvent): Boolean {
        if (recovering) {
            // R4/AC4: a duplicate fault during an active episode merges; it does not stack a second
            // attempt or budget. Make sure the originally-faulting observers are retained.
            addEpisodeObserversFromManager()
            LogUtil.d(TAG, "Duplicate established fault merged into active episode (epoch=${currentEpoch()})")
            return true
        }
        val epoch = epochCounter.incrementAndGet()
        val expectedIdentity = connectionManager.captureCurrentTerminalIdentity()
        val episode = RecoveryEpisode(
            sessionId = event.sessionId,
            epoch = epoch,
            deadlineElapsedMs = clock.elapsedRealtimeMs() + RECOVERY_BUDGET_MS,
            originalConfig = connectionManager.currentConnectionConfigForRecovery(),
            expectedIdentity = expectedIdentity
        )
        // Bind all mutable settle/cancel state to THIS generation. A stale episode can only ever
        // mutate its own control, never this (or any future) control's gate (cross-epoch fix).
        val control = EpisodeControl(episode)
        currentControl = control
        recovering = true
        synchronized(episodeObservers) { episodeObservers.clear() }
        addEpisodeObserversFromManager()
        // R10/R11: gate PaymentManager so new requests are rejected before send and no physical
        // disconnect cleanup runs during the attempt.
        connectionManager.setEstablishedRecoveryActive(true)
        LogUtil.i(TAG, "Claimed established-recovery epoch=$epoch (session=${event.sessionId})")
        // Serialize the episode on the coordinator's own scope. In production that scope is confined
        // to Dispatchers.Main so there is a single actor-style execution path (design §5); tests pass
        // a controlled scope/dispatcher.
        control.episodeJob = scope.launch { runEpisode(control) }
        // D1: start an independent budget watchdog on a dispatcher that is NOT the Main-confined
        // episode scope, so the "deadline reached => settleFailure" guarantee survives Main
        // starvation. settleFailure acts only when this control is still current and wins its OWN
        // settled CAS (both inside the synchronized boundary), so a watchdog firing after a normal
        // settle is swallowed and a stale generation cannot poison a successor.
        startWatchdog(control)
        return true
    }

    /**
     * D1 fix: a budget watchdog that does not depend on the episode scope being scheduled. It wakes
     * on a short real-time tick and re-reads the injected monotonic [clock]; once the episode
     * deadline is reached it forces [settleFailure] for THIS [control]. Because it runs on
     * [watchdogDispatcher] (default [Dispatchers.Default]), it keeps working even when the
     * Main-confined [scope] cannot dispatch the polling continuation. On winning the settle it also
     * cancels this generation's exact episode job so a later Main wakeup cannot run stale rebuild /
     * cleanup / settle work. The deadline value and single 5000ms budget are unchanged; only the
     * trigger of the terminal settle is made scheduling-independent.
     */
    private fun startWatchdog(control: EpisodeControl) {
        // If the episode already settled synchronously inside scope.launch { runEpisode(...) } (e.g.
        // a prerequisite denial / already-past deadline under an Unconfined or Main-eager dispatcher),
        // the settle ran before this watchdog job existed and therefore could not cancel it. Skip
        // launching entirely in that case so no stale ~5s job accumulates.
        if (control.settled.get() || currentControl !== control) {
            LogUtil.d(TAG, "Episode already settled before watchdog start; skipping (epoch=${control.episode.epoch})")
            return
        }
        val job = watchdogScope.launch {
            while (isActive && clock.elapsedRealtimeMs() < control.episode.deadlineElapsedMs) {
                delay(WATCHDOG_TICK_MS)
            }
            if (isActive) {
                LogUtil.w(TAG, "Watchdog hit budget deadline; forcing settle (epoch=${control.episode.epoch})")
                settleFailure(control)
            }
        }
        control.watchdogJob = job
        // Guard the launch-vs-settle gap too: if the episode settled between the pre-check above and
        // the job being stored, the winning settle's cancel of control.watchdogJob may have observed
        // a null field. Re-check and cancel here so the watchdog cannot outlive its settled episode.
        if (control.settled.get() || currentControl !== control) {
            job.cancel()
        }
    }

    /** Collect the current fault-time observers (the global connection listener). */
    private fun addEpisodeObserversFromManager() {
        synchronized(episodeObservers) {
            connectionManager.recoveryObservers().forEach { episodeObservers.add(it) }
        }
    }

    /**
     * Register a caller that issued a same-config connect() during an active episode so it joins the
     * single terminal outcome instead of opening a second transport (R9).
     *
     * The join MUST be atomic with the terminal settle's observer snapshot + clear, otherwise a
     * background settle can snapshot and clear [episodeObservers] between this method reading
     * `recovering` and writing the listener, dropping the caller into an already-emptied set while
     * still returning `true` — the caller would then neither reconnect nor ever receive a terminal
     * callback. Holding the coordinator monitor (the SAME lock used by [claimEpoch],
     * [claimTerminalSettle]/[finalizeTerminalSettle] and [onExplicitDetachOrDisconnect]) closes that
     * window: inside the lock we re-confirm the episode is still current before adding the listener,
     * so the observer is guaranteed to be in the snapshot the winning settle takes.
     *
     * Settling-window join (blocking review fix): the observer snapshot is deliberately deferred to
     * [finalizeTerminalSettle], so between [claimTerminalSettle] winning (`control.settled == true`,
     * jobs cancelled, Host teardown running) and finalize taking the snapshot, [episodeObservers] is
     * still live and NOT yet cleared. Rejecting a same-config join in that window was wrong: the real
     * [ConnectionManager.connect] does not return on a `false` here but falls through to
     * `disconnect()` (because `isConnected()==false` while recovery is active), which routes into
     * [onExplicitDetachOrDisconnect] and clears the very [episodeObservers] the winning settle still
     * has to notify — so the ORIGINAL observer's single terminal E213 would be lost (violates AC6).
     * The fix: keep admitting same-config joiners for as long as THIS generation is still current
     * (i.e. until [finalizeTerminalSettle] flips `recovering=false` / drops `currentControl` under the
     * same lock). A joiner admitted during the settling window lands in the still-live observer set
     * and therefore receives the SAME single terminal outcome from finalize's snapshot. Once finalize
     * has run, `recovering` is `false` here and the caller instead starts a fresh connection.
     *
     * @return `true` if the caller was joined to the current, not-yet-finalized episode.
     */
    @Synchronized
    fun joinIfRecovering(listener: ConnectionListener): Boolean {
        if (!recovering) return false
        val control = currentControl ?: return false
        // NOTE: we intentionally do NOT reject when control.settled is already true. Until
        // finalizeTerminalSettle has run (still under this lock), the observer set is live and the
        // deferred terminal snapshot has not been taken; admitting the joiner here guarantees it is
        // included in that single snapshot instead of being dropped into disconnect()/clear.
        val duringSettlingWindow = control.settled.get()
        synchronized(episodeObservers) { episodeObservers.add(listener) }
        LogUtil.d(
            TAG,
            "Caller joined active recovery episode (epoch=${currentEpoch()}, settlingWindow=$duringSettlingWindow)"
        )
        return true
    }

    /**
     * The single episode body: retire old resources -> open one rebuild -> validate identity ->
     * settle. Bounded by the monotonic deadline; no retry interval, no second attempt (R4).
     */
    private suspend fun runEpisode(control: EpisodeControl) {
        val episode = control.episode
        try {
            // R12: prerequisite check is read-only and non-interactive. A denial ends the attempt.
            val prereq = connectionManager.evaluateRecoveryPrerequisite()
            if (prereq is RecoveryPrereqResult.Denied) {
                LogUtil.w(TAG, "Recovery prerequisite denied: ${prereq.diagnostic}")
                settleFailure(control)
                return
            }

            // R7: retire the old epoch and await safe release before opening a successor, bounded by
            // the remaining budget; a stuck cleanup is fenced at the deadline.
            connectionManager.retireForRecovery()
            if (deadlineReached(episode)) {
                LogUtil.w(TAG, "Deadline reached during cleanup; fencing and failing (epoch=${episode.epoch})")
                settleFailure(control)
                return
            }

            // R4/R8: open EXACTLY ONE same-protocol/same-config rebuild. R12: a transiently-absent USB
            // peer may appear within the remaining budget; the rebuild+poll below tolerates that.
            connectionManager.openRecoveryRebuild(episode.originalConfig)

            val ready = pollUntilReadyOrDeadline(episode)
            if (!ready) {
                LogUtil.w(TAG, "Recovery rebuild not ready within budget (epoch=${episode.epoch})")
                settleFailure(control)
                return
            }

            // R8: validate the ORIGINAL terminal identity + readiness (SUB_SCREEN needs screen init).
            val expected = episode.expectedIdentity ?: TerminalIdentity(null)
            val identityOk = connectionManager.validateRecoveredIdentity(expected)
            if (!identityOk) {
                LogUtil.w(TAG, "Recovered-link identity not confirmed; refusing to attach (epoch=${episode.epoch})")
                settleFailure(control)
                return
            }

            settleSuccess(control)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // D2 fix: an explicit detach/disconnect intentionally delivers no callback (settle is
            // owned by that caller). Any OTHER cancellation (e.g. a parent/scope child-job cancel)
            // must still back-stop a single terminal onDisconnected(E213) so an ended episode never
            // goes silent. The explicit-detach flag is read from THIS episode's control (never a
            // successor's), and settleFailure only acts when this control is still current and wins
            // its OWN settled CAS, so a late/stale cancellation cannot poison a newer generation. The
            // CancellationException is still rethrown to honor structured-concurrency propagation.
            if (!control.cancelledByExplicitDetach && !control.settled.get()) {
                LogUtil.w(TAG, "Recovery episode non-explicitly cancelled; back-stopping failure (epoch=${episode.epoch})")
                settleFailure(control)
            } else {
                LogUtil.d(TAG, "Recovery episode cancelled (epoch=${episode.epoch})")
            }
            throw cancellation
        } catch (e: Exception) {
            LogUtil.e(TAG, "Recovery episode error (epoch=${episode.epoch}): ${e.message}")
            settleFailure(control)
        }
    }

    private fun deadlineReached(episode: RecoveryEpisode): Boolean =
        clock.elapsedRealtimeMs() >= episode.deadlineElapsedMs

    private suspend fun pollUntilReadyOrDeadline(episode: RecoveryEpisode): Boolean {
        while (scope.isActive && !deadlineReached(episode)) {
            if (connectionManager.isRecoveryTransportReady()) return true
            delay(POLL_MS)
        }
        return connectionManager.isRecoveryTransportReady()
    }

    /**
     * Phase 1 of the terminal settle: claim sole ownership of [control]'s single terminal outcome.
     * Returns `true` to exactly one caller (success / failure / watchdog / cancel back-stop) across
     * this episode's lifetime, and only when [control] is still the current generation. The
     * current-check and the one-time CAS both happen inside the coordinator's synchronized boundary so
     * there is no check-then-act window and a stale generation CANNOT flip a successor's gate: a late
     * settle on an old [control] whose `currentControl !== control` returns `false` without ever
     * touching the new episode's token.
     *
     * Crucially this phase does NOT yet release the recovery gate, `currentControl`, or `recovering`,
     * and it does NOT clear the observer set. Those belong to [finalizeTerminalSettle] and are held
     * until the Host-side teardown ([onRecoverySuccess]/[onRecoveryFailureCleanup]) has completed, so
     * that during teardown: (a) PaymentManager keeps rejecting new requests (gate stays closed) —
     * otherwise a new request could slip in after the gate dropped but before
     * failAllPendingTransactions/identity-clear, be sent on a dying link, then be killed by this
     * episode's cleanup; and (b) a new established fault cannot claim a successor episode that the old
     * episode's teardown would then poison by clearing shared connection fields / disconnecting the
     * live kernel. We DO cancel this generation's jobs here so no stale rebuild/poll/settle keeps
     * running once ownership is won.
     *
     * @return `true` if this call won the terminal settle for the still-current episode; the caller
     *         must then run Host teardown and call [finalizeTerminalSettle]. `false` if the settle was
     *         already claimed / this control is no longer current (the caller must deliver nothing).
     */
    @Synchronized
    private fun claimTerminalSettle(control: EpisodeControl): Boolean {
        if (currentControl !== control) return false
        if (!control.settled.compareAndSet(false, true)) return false
        // Won ownership: stop this generation's coroutines immediately, but keep the gate /
        // currentControl / recovering flag in place until Host teardown finishes (see KDoc).
        control.watchdogJob?.cancel()
        control.episodeJob?.cancel()
        return true
    }

    /**
     * Phase 2 of the terminal settle: after the Host-side teardown has fully run, release the recovery
     * gate and coordinator state under the coordinator lock and return the observer snapshot to
     * notify. Keeping the gate/`currentControl`/`recovering` held across the Host teardown (phase 1 ->
     * Host teardown -> phase 2) closes the "gate dropped before Host finished" window described in
     * [claimTerminalSettle]. The observer snapshot is taken here (after the gate is dropped so no new
     * joiner can be admitted, and inside the same lock as the clear) so the winner's list is race-free
     * against [joinIfRecovering].
     *
     * Carries [control] and re-checks it is still the current generation before touching coordinator
     * state: a finalize must only ever release the gate / clear observers for the generation it
     * actually settled. In the current flow this always holds (the gate is held from
     * [claimTerminalSettle] across Host teardown, and [claimEpoch] is synchronized so no successor can
     * claim while `recovering` is true), but guarding here prevents a stale finalize from
     * unconditionally wiping a changed coordinator state.
     *
     * @return the observers to notify exactly once; empty if this control is no longer current.
     */
    @Synchronized
    private fun finalizeTerminalSettle(control: EpisodeControl): List<ConnectionListener> {
        if (currentControl !== control) {
            LogUtil.w(TAG, "finalizeTerminalSettle for a non-current generation; skipping state clear (epoch=${control.episode.epoch})")
            return emptyList()
        }
        recovering = false
        connectionManager.setEstablishedRecoveryActive(false)
        currentControl = null
        val observers = synchronized(episodeObservers) {
            val snapshot = episodeObservers.toList()
            episodeObservers.clear()
            snapshot
        }
        return observers
    }

    private fun settleSuccess(control: EpisodeControl) {
        if (!claimTerminalSettle(control)) return
        val episode = control.episode
        LogUtil.i(TAG, "Recovery SUCCESS (epoch=${episode.epoch})")
        // R15/R10: re-attach the rebuilt receive route to the SAME callback/traceId mapping BEFORE the
        // earliest continued payload; never replay, never swap callback, never clear dedup. This runs
        // while the gate is still closed and this episode is still the current generation, so no new
        // request can race ahead of the re-attach and no new fault can claim a successor episode.
        // try/finally so a throwing Host teardown can never strand the gate / recovering flag: the
        // gate is deliberately held across onRecoverySuccess(), so if it throws we still MUST run
        // finalizeTerminalSettle to drop the gate, clear state and release observers.
        val observers = try {
            connectionManager.onRecoverySuccess()
            finalizeTerminalSettle(control)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Host re-attach threw during recovery success; finalizing to release gate: ${e.message}")
            finalizeTerminalSettle(control)
        }
        val deviceId = connectionManager.getConnectedDeviceId() ?: "unknown"
        val taproVersion = connectionManager.getTaproVersion() ?: "unknown"
        observers.forEach { observer ->
            try {
                observer.onConnected(deviceId, taproVersion)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Observer onConnected threw during recovery success: ${e.message}")
            }
        }
    }

    private fun settleFailure(control: EpisodeControl) {
        if (!claimTerminalSettle(control)) return
        val episode = control.episode
        LogUtil.w(TAG, "Recovery FAILURE (epoch=${episode.epoch}); one terminal disconnect (E213)")
        val reason = "Code: ${InnerErrorCode.E213.code}, Message: ${InnerErrorCode.E213.description}"
        // R10/Q4: clear/fail in-flight tx callbacks via the existing physical-disconnect path, exactly
        // once, together with the one onDisconnected(E213). No synthetic early E306. This runs while
        // the gate is still closed and this episode is still the current generation, so a new request
        // cannot slip in between the gate drop and failAllPendingTransactions and be killed by this
        // cleanup, and a new fault cannot claim a successor episode that this cleanup would poison.
        // try/finally so a throwing Host teardown can never strand the gate / recovering flag: the
        // gate is deliberately held across onRecoveryFailureCleanup(), so if it throws we still MUST
        // run finalizeTerminalSettle to drop the gate, clear state and deliver the one E213.
        val observers = try {
            connectionManager.onRecoveryFailureCleanup(reason)
            finalizeTerminalSettle(control)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Host cleanup threw during recovery failure; finalizing to release gate: ${e.message}")
            finalizeTerminalSettle(control)
        }
        observers.forEach { observer ->
            try {
                observer.onDisconnected(reason)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Observer onDisconnected threw during recovery failure: ${e.message}")
            }
        }
    }

    /**
     * Explicit caller removeConnectionListener()/disconnect() or a superseding new-config connect:
     * cancel and fence the current episode. No later old success/error/new connection. This is the
     * ONLY path (together with the ConnectionManager that calls it) that stops recovery externally; it
     * never removes a ConnectionListener itself (R9).
     */
    @Synchronized
    fun onExplicitDetachOrDisconnect() {
        val control = currentControl ?: return
        if (!recovering) return
        LogUtil.d(TAG, "Explicit detach/disconnect fences recovery (epoch=${control.episode.epoch})")
        // D2: mark this as the explicit-detach cancel BEFORE cancelling so runEpisode's cancellation
        // branch (reading THIS control's flag) keeps the existing "no callback on explicit detach"
        // semantics. Burn this generation's one-time gate and tear down its jobs + coordinator state
        // under the same lock so a late/stale cancellation cannot settle, and a successor claims a
        // clean coordinator.
        control.cancelledByExplicitDetach = true
        control.settled.set(true)
        recovering = false
        connectionManager.setEstablishedRecoveryActive(false)
        currentControl = null
        synchronized(episodeObservers) { episodeObservers.clear() }
        control.watchdogJob?.cancel()
        control.episodeJob?.cancel()
    }
}
