package com.sunmi.tapro.taplink.sdk.manager

import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.communication.interfaces.recovery.RecoveryPrereqResult
import com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val scope: CoroutineScope
) {
    private val TAG = "EstablishedRecovery"

    companion object {
        /** Total monotonic budget for a single recovery episode (cleanup + rebuild + readiness). */
        const val RECOVERY_BUDGET_MS = 5000L

        /** Poll interval while waiting for the rebuilt transport to become ready. */
        private const val POLL_MS = 50L
    }

    private val epochCounter = AtomicLong(0)

    /** `true` from the synchronous claim until the terminal settle of the current episode. */
    @Volatile
    private var recovering = false

    /** The currently-owned episode, or null when idle. */
    @Volatile
    private var currentEpisode: RecoveryEpisode? = null

    /** The coroutine running the current episode body; cancelled on explicit detach/disconnect. */
    private var episodeJob: Job? = null

    /**
     * Observers that must receive the single terminal outcome of the current episode. Identity set so
     * a listener registered in multiple roles is notified at most once.
     */
    private val episodeObservers = Collections.newSetFromMap(IdentityHashMap<ConnectionListener, Boolean>())

    /** Guards the one-time settle so success/failure/cancel deliver a terminal outcome exactly once. */
    private var settled = AtomicBoolean(false)

    /** @return `true` while an episode is active (claim..settle). */
    fun isRecovering(): Boolean = recovering

    /** @return the epoch currently owned, or 0 if idle. Used for stale-callback fencing (R7). */
    fun currentEpoch(): Long = currentEpisode?.epoch ?: 0L

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
        currentEpisode = episode
        recovering = true
        settled = AtomicBoolean(false)
        synchronized(episodeObservers) { episodeObservers.clear() }
        addEpisodeObserversFromManager()
        // R10/R11: gate PaymentManager so new requests are rejected before send and no physical
        // disconnect cleanup runs during the attempt.
        connectionManager.setEstablishedRecoveryActive(true)
        LogUtil.i(TAG, "Claimed established-recovery epoch=$epoch (session=${event.sessionId})")
        // Serialize the episode on the coordinator's own scope. In production that scope is confined
        // to Dispatchers.Main so there is a single actor-style execution path (design §5); tests pass
        // a controlled scope/dispatcher.
        episodeJob = scope.launch { runEpisode(episode) }
        return true
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
     * @return `true` if the caller was joined to an active episode.
     */
    fun joinIfRecovering(listener: ConnectionListener): Boolean {
        if (!recovering) return false
        synchronized(episodeObservers) { episodeObservers.add(listener) }
        LogUtil.d(TAG, "Caller joined active recovery episode (epoch=${currentEpoch()})")
        return true
    }

    /**
     * The single episode body: retire old resources -> open one rebuild -> validate identity ->
     * settle. Bounded by the monotonic deadline; no retry interval, no second attempt (R4).
     */
    private suspend fun runEpisode(episode: RecoveryEpisode) {
        try {
            // R12: prerequisite check is read-only and non-interactive. A denial ends the attempt.
            val prereq = connectionManager.evaluateRecoveryPrerequisite()
            if (prereq is RecoveryPrereqResult.Denied) {
                LogUtil.w(TAG, "Recovery prerequisite denied: ${prereq.diagnostic}")
                settleFailure(episode)
                return
            }

            // R7: retire the old epoch and await safe release before opening a successor, bounded by
            // the remaining budget; a stuck cleanup is fenced at the deadline.
            connectionManager.retireForRecovery()
            if (deadlineReached(episode)) {
                LogUtil.w(TAG, "Deadline reached during cleanup; fencing and failing (epoch=${episode.epoch})")
                settleFailure(episode)
                return
            }

            // R4/R8: open EXACTLY ONE same-protocol/same-config rebuild. R12: a transiently-absent USB
            // peer may appear within the remaining budget; the rebuild+poll below tolerates that.
            connectionManager.openRecoveryRebuild(episode.originalConfig)

            val ready = pollUntilReadyOrDeadline(episode)
            if (!ready) {
                LogUtil.w(TAG, "Recovery rebuild not ready within budget (epoch=${episode.epoch})")
                settleFailure(episode)
                return
            }

            // R8: validate the ORIGINAL terminal identity + readiness (SUB_SCREEN needs screen init).
            val expected = episode.expectedIdentity ?: TerminalIdentity(null)
            val identityOk = connectionManager.validateRecoveredIdentity(expected)
            if (!identityOk) {
                LogUtil.w(TAG, "Recovered-link identity not confirmed; refusing to attach (epoch=${episode.epoch})")
                settleFailure(episode)
                return
            }

            settleSuccess(episode)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Explicit disconnect/supersede cancelled the episode; settle is owned by the canceller.
            LogUtil.d(TAG, "Recovery episode cancelled (epoch=${episode.epoch})")
            throw cancellation
        } catch (e: Exception) {
            LogUtil.e(TAG, "Recovery episode error (epoch=${episode.epoch}): ${e.message}")
            settleFailure(episode)
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

    private fun settleSuccess(episode: RecoveryEpisode) {
        if (!settled.compareAndSet(false, true)) return
        if (currentEpisode?.epoch != episode.epoch) return
        LogUtil.i(TAG, "Recovery SUCCESS (epoch=${episode.epoch})")
        // R15/R10: re-attach the rebuilt receive route to the SAME callback/traceId mapping BEFORE the
        // earliest continued payload; never replay, never swap callback, never clear dedup.
        connectionManager.onRecoverySuccess()
        val deviceId = connectionManager.getConnectedDeviceId() ?: "unknown"
        val taproVersion = connectionManager.getTaproVersion() ?: "unknown"
        val observers = snapshotObservers()
        clearStateAfterSettle()
        observers.forEach { observer ->
            try {
                observer.onConnected(deviceId, taproVersion)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Observer onConnected threw during recovery success: ${e.message}")
            }
        }
    }

    private fun settleFailure(episode: RecoveryEpisode) {
        if (!settled.compareAndSet(false, true)) return
        if (currentEpisode?.epoch != episode.epoch) return
        LogUtil.w(TAG, "Recovery FAILURE (epoch=${episode.epoch}); one terminal disconnect (E213)")
        val reason = "Code: ${InnerErrorCode.E213.code}, Message: ${InnerErrorCode.E213.description}"
        val observers = snapshotObservers()
        // R10/Q4: clear/fail in-flight tx callbacks via the existing physical-disconnect path, exactly
        // once, together with the one onDisconnected(E213). No synthetic early E306.
        connectionManager.onRecoveryFailureCleanup(reason)
        clearStateAfterSettle()
        observers.forEach { observer ->
            try {
                observer.onDisconnected(reason)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Observer onDisconnected threw during recovery failure: ${e.message}")
            }
        }
    }

    private fun snapshotObservers(): List<ConnectionListener> =
        synchronized(episodeObservers) { episodeObservers.toList() }

    private fun clearStateAfterSettle() {
        recovering = false
        connectionManager.setEstablishedRecoveryActive(false)
        currentEpisode = null
        synchronized(episodeObservers) { episodeObservers.clear() }
        episodeJob = null
    }

    /**
     * Explicit caller removeConnectionListener()/disconnect() or a superseding new-config connect:
     * cancel and fence the current episode. No later old success/error/new connection. This is the
     * ONLY path (together with the ConnectionManager that calls it) that stops recovery externally; it
     * never removes a ConnectionListener itself (R9).
     */
    fun onExplicitDetachOrDisconnect() {
        if (!recovering) return
        LogUtil.d(TAG, "Explicit detach/disconnect fences recovery (epoch=${currentEpoch()})")
        settled.set(true)
        episodeJob?.cancel()
        clearStateAfterSettle()
    }
}
