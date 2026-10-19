package com.sunmi.tapro.taplink.sdk.manager

import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.communication.interfaces.recovery.RecoveryPrereqResult
import com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fake-clock budget/fence tests for [EstablishedRecoveryCoordinator].
 * SPEC-ESTABLISHED-RECOVERY-001 §AC4/§AC7 / TC-ER-004, TC-ER-007: single 5000ms budget, deadline
 * fence, no stacked loops, an expired attempt cannot later succeed.
 */
class EstablishedRecoveryBudgetTest {

    private class StepClock(private val seq: List<Long>) : RecoveryClock {
        private var i = 0
        override fun elapsedRealtimeMs(): Long {
            val v = seq[i.coerceAtMost(seq.size - 1)]
            if (i < seq.size - 1) i++
            return v
        }
    }

    // CopyOnWriteArrayList: executor/watchdog threads and the runBlocking test thread read/write
    // these concurrently in the D1/D2/cross-epoch/settling-window tests; a plain ArrayList does not
    // guarantee cross-thread visibility or structural safety.
    private class Rec : ConnectionListener {
        val connected = java.util.concurrent.CopyOnWriteArrayList<String>()
        val disconnected = java.util.concurrent.CopyOnWriteArrayList<String>()
        val errors = java.util.concurrent.CopyOnWriteArrayList<String>()
        override fun onConnected(deviceId: String, taproVersion: String) { connected.add(deviceId) }
        override fun onDisconnected(reason: String) { disconnected.add(reason) }
        override fun onError(error: ConnectionError) { errors.add(error.code) }
    }

    private fun host(ready: Boolean, obs: ConnectionListener, rebuilds: AtomicInteger) = object : RecoveryHost {
        override fun captureCurrentTerminalIdentity() = TerminalIdentity("S")
        override fun currentConnectionConfigForRecovery() = ConnectionConfig.createDefault()
        override fun recoveryObservers() = listOf(obs)
        override fun setEstablishedRecoveryActive(active: Boolean) {}
        override fun evaluateRecoveryPrerequisite() = RecoveryPrereqResult.Available
        override fun retireForRecovery() {}
        override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { rebuilds.incrementAndGet() }
        override fun isRecoveryTransportReady() = ready
        override suspend fun validateRecoveredIdentity(expected: TerminalIdentity) = true
        override fun onRecoverySuccess() {}
        override fun onRecoveryFailureCleanup(reason: String) {}
        override fun getConnectedDeviceId() = "d"
        override fun getTaproVersion() = "1"
    }

    private fun fault() =
        TransportFaultEvent(sessionId = 1, epoch = 0, code = "1006", msg = "drop", established = true)

    private suspend fun awaitSettled(c: EstablishedRecoveryCoordinator) {
        withTimeout(5000) { while (c.isRecovering()) delay(5) }
    }

    /** Deadline reached during polling -> single failure (E213), exactly one rebuild attempt. */
    @Test
    fun deadlineDuringPollingFailsOnceWithOneAttempt() = runBlocking {
        val rec = Rec()
        val rebuilds = AtomicInteger(0)
        // claim(0)->deadline=5000; after-retire(100,<5000); poll(200,<5000) opens; poll(6000)>=deadline fences.
        val clock = StepClock(listOf(0, 100, 200, 6000))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = EstablishedRecoveryCoordinator(host(ready = false, obs = rec, rebuilds = rebuilds), clock, scope)
        coordinator.interceptIfEstablishedFault(fault())
        awaitSettled(coordinator)
        assertEquals(1, rebuilds.get())
        assertEquals(1, rec.disconnected.size)
        assertTrue(rec.disconnected.first().contains("213"))
        assertTrue(rec.errors.isEmpty())
        assertTrue(rec.connected.isEmpty())
    }

    /** Deadline reached before the rebuild (stuck cleanup) -> fenced, no rebuild opened. */
    @Test
    fun deadlineBeforeRebuildFencesWithoutOpeningSuccessor() = runBlocking {
        val rec = Rec()
        val rebuilds = AtomicInteger(0)
        // claim(0) -> deadline=5000; after-retire check already past deadline(6000) -> fence.
        val clock = StepClock(listOf(0, 6000))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = EstablishedRecoveryCoordinator(host(ready = true, obs = rec, rebuilds = rebuilds), clock, scope)
        coordinator.interceptIfEstablishedFault(fault())
        awaitSettled(coordinator)
        assertEquals(0, rebuilds.get())
        assertEquals(1, rec.disconnected.size)
        assertTrue(rec.errors.isEmpty())
    }

    /**
     * D1 / TC-超时必达-Main受压: when the episode scope (Main-style) cannot dispatch the polling
     * continuation, the independent watchdog must still force a single terminal onDisconnected(E213)
     * once the budget deadline is reached. Proves the timeout no longer depends on the episode
     * scope's continuations being scheduled.
     *
     * SPEC-ESTABLISHED-RECOVERY-001 §AC4/§AC6.
     */
    @Test
    fun watchdogSettlesFailureWhenEpisodeScopeIsStarved() = runBlocking {
        val rec = Rec()
        val rebuilds = AtomicInteger(0)
        // A single-thread episode dispatcher that we deliberately keep busy forever, so the launched
        // runEpisode body (and any delay/poll continuation on it) never gets scheduled — this is the
        // JVM analogue of a starved Dispatchers.Main.
        val starvedExecutor = Executors.newSingleThreadExecutor()
        starvedExecutor.submit { try { Thread.sleep(60_000) } catch (_: InterruptedException) {} }
        val episodeScope = CoroutineScope(starvedExecutor.asCoroutineDispatcher())
        // claim reads 0 -> deadline=5000; watchdog reads 6000 >= deadline -> fires. The episode scope
        // never runs, so these clock reads are consumed only by claim + the watchdog.
        val clock = StepClock(listOf(0, 6000))
        val coordinator = EstablishedRecoveryCoordinator(
            host(ready = false, obs = rec, rebuilds = rebuilds),
            clock,
            episodeScope,
            // Drive the watchdog deterministically off an Unconfined dispatcher (independent of the
            // starved episode scope), exactly as the production default Dispatchers.Default is
            // independent of Main.
            Dispatchers.Unconfined
        )
        try {
            coordinator.interceptIfEstablishedFault(fault())
            withTimeout(5000) { while (rec.disconnected.isEmpty()) delay(5) }
            assertEquals("Watchdog must deliver exactly one terminal disconnect", 1, rec.disconnected.size)
            assertTrue(rec.disconnected.first().contains("213"))
            assertTrue(rec.errors.isEmpty())
            assertTrue(rec.connected.isEmpty())
            // The starved episode scope never opened the rebuild; the settle came from the watchdog.
            assertEquals(0, rebuilds.get())
        } finally {
            starvedExecutor.shutdownNow()
        }
    }

    /**
     * D2 / TC-非显式取消兜底: when the episode coroutine is cancelled by something OTHER than an
     * explicit detach/disconnect, the coordinator must still back-stop exactly one terminal
     * onDisconnected(E213) rather than go silent. The episode is cancelled by cancelling its parent
     * scope's children (a structured-concurrency cancel that is NOT onExplicitDetachOrDisconnect).
     *
     * SPEC-ESTABLISHED-RECOVERY-001 §AC6.
     */
    @Test
    fun nonExplicitCancellationBackStopsSingleFailure() = runBlocking {
        val rec = Rec()
        val rebuilds = AtomicInteger(0)
        // Keep the clock before the deadline forever so neither the poll loop nor the watchdog settles
        // on its own; the only settle must come from the non-explicit cancellation back-stop.
        val clock = object : RecoveryClock {
            override fun elapsedRealtimeMs(): Long = 0L
        }
        // A dedicated single-thread episode scope so the episode body actually suspends in its poll
        // loop (rather than running inline on Unconfined), leaving a live job to cancel.
        val episodeExecutor = Executors.newSingleThreadExecutor()
        val episodeScope = CoroutineScope(episodeExecutor.asCoroutineDispatcher())
        val coordinator = EstablishedRecoveryCoordinator(
            host(ready = false, obs = rec, rebuilds = rebuilds),
            clock,
            episodeScope,
            // A real-time watchdog that will never reach the (never-advancing) deadline within the
            // test window, so it cannot be the settle source.
            Dispatchers.Default
        )
        try {
            coordinator.interceptIfEstablishedFault(fault())
            // Let the episode reach the polling loop.
            withTimeout(5000) { while (!coordinator.isRecovering()) delay(5) }
            // Cancel the episode via its parent scope's children (NOT via onExplicitDetachOrDisconnect):
            // the D2 back-stop must still deliver one E213.
            episodeScope.coroutineContext[Job]!!.cancelChildren()
            withTimeout(5000) { while (rec.disconnected.isEmpty()) delay(5) }
            assertEquals("Non-explicit cancel must back-stop exactly one terminal disconnect", 1, rec.disconnected.size)
            assertTrue(rec.disconnected.first().contains("213"))
            assertTrue(rec.errors.isEmpty())
            assertTrue(rec.connected.isEmpty())
        } finally {
            episodeExecutor.shutdownNow()
        }
    }

    /**
     * D1 / TC-看门狗与正常结算互斥 (live watchdog, not eager-dispatched): the watchdog is genuinely
     * running and looping (its deadline not yet crossed) while the success settle commits on a
     * separate thread. Committing the success settle must, under the single synchronized settle
     * boundary, win the one-time gate AND cancel this episode's live watchdog job so the watchdog can
     * never deliver a second (failure) outcome — even after the clock is subsequently pushed past the
     * deadline. Exactly one onConnected and zero onDisconnected result.
     *
     * Unlike the earlier eager-Unconfined variant (which let the episode body settle synchronously
     * before the watchdog ever started, so the watchdog exited via the stale-episode fence without
     * ever reaching settleFailure), here the watchdog is demonstrably alive and in its loop when the
     * success settle happens, so this exercises the real settle-vs-live-watchdog mutual exclusion.
     *
     * SPEC-ESTABLISHED-RECOVERY-001 §AC4/§AC6.
     */
    @Test
    fun liveWatchdogIsCancelledAndSwallowedByNormalSuccessSettle() = runBlocking {
        val rec = Rec()
        val rebuilds = AtomicInteger(0)
        // The clock stays well before the deadline (100 < 5000) for every read the watchdog makes, so
        // the watchdog loops/sleeps alive and never fires on its own during the test window.
        val liveClock = object : RecoveryClock { override fun elapsedRealtimeMs(): Long = 100L }
        // Success blocks at readiness until we explicitly let it proceed, guaranteeing the watchdog is
        // already launched and looping before success settles.
        val letSuccessProceed = CountDownLatch(1)
        val readyProbed = CountDownLatch(1)
        val racingHost = object : RecoveryHost {
            override fun captureCurrentTerminalIdentity() = TerminalIdentity("S")
            override fun currentConnectionConfigForRecovery() = ConnectionConfig.createDefault()
            override fun recoveryObservers() = listOf<ConnectionListener>(rec)
            override fun setEstablishedRecoveryActive(active: Boolean) {}
            override fun evaluateRecoveryPrerequisite() = RecoveryPrereqResult.Available
            override fun retireForRecovery() {}
            override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { rebuilds.incrementAndGet() }
            override fun isRecoveryTransportReady(): Boolean {
                readyProbed.countDown()
                // Not ready until the test releases success, so the episode body stays in its poll
                // loop while the watchdog is confirmed alive.
                return letSuccessProceed.count == 0L
            }
            override suspend fun validateRecoveredIdentity(expected: TerminalIdentity) = true
            override fun onRecoverySuccess() {}
            override fun onRecoveryFailureCleanup(reason: String) {}
            override fun getConnectedDeviceId() = "d"
            override fun getTaproVersion() = "1"
        }
        val episodeExecutor = Executors.newSingleThreadExecutor()
        val watchdogExecutor = Executors.newSingleThreadExecutor()
        val episodeScope = CoroutineScope(episodeExecutor.asCoroutineDispatcher())
        val coordinator = EstablishedRecoveryCoordinator(
            racingHost, liveClock, episodeScope, watchdogExecutor.asCoroutineDispatcher()
        )
        try {
            coordinator.interceptIfEstablishedFault(fault())
            // The episode body entered polling and the watchdog is launched and alive (looping on the
            // < deadline clock). Now let success settle; it must cancel the live watchdog under lock.
            assertTrue(readyProbed.await(5, TimeUnit.SECONDS))
            letSuccessProceed.countDown()
            awaitSettled(coordinator)
            // Even if the watchdog were somehow still alive, give it extra ticks: it must stay dead.
            delay(150)
            assertEquals("Exactly one onConnected from the successful recovery", 1, rec.connected.size)
            assertTrue("A cancelled live watchdog must deliver no failure outcome", rec.disconnected.isEmpty())
            assertTrue(rec.errors.isEmpty())
            assertTrue(!coordinator.isRecovering())
        } finally {
            episodeExecutor.shutdownNow()
            watchdogExecutor.shutdownNow()
        }
    }

    /**
     * Review-B blocking (1) regression: the PaymentManager recovery gate and the owning generation
     * must be held until the Host-side teardown has fully run. On a failure settle the gate must NOT
     * be dropped (setEstablishedRecoveryActive(false)) before onRecoveryFailureCleanup() completes,
     * otherwise a new request could slip in on a dying link between the gate drop and
     * failAllPendingTransactions and be killed by this episode's own cleanup. The recording host
     * captures the order of the gate-release vs the failure cleanup.
     *
     * SPEC-ESTABLISHED-RECOVERY-001 §AC6/R10/R11.
     */
    @Test
    fun failureSettleKeepsRecoveryGateClosedUntilHostCleanupCompletes() = runBlocking {
        val rec = Rec()
        val rebuilds = AtomicInteger(0)
        val order = mutableListOf<String>()
        var gateClosedDuringCleanup = true
        val recordingHost = object : RecoveryHost {
            @Volatile var gateActive = false
            override fun captureCurrentTerminalIdentity() = TerminalIdentity("S")
            override fun currentConnectionConfigForRecovery() = ConnectionConfig.createDefault()
            override fun recoveryObservers() = listOf<ConnectionListener>(rec)
            override fun setEstablishedRecoveryActive(active: Boolean) {
                gateActive = active
                order.add(if (active) "gate-on" else "gate-off")
            }
            override fun evaluateRecoveryPrerequisite() = RecoveryPrereqResult.Available
            override fun retireForRecovery() {}
            override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { rebuilds.incrementAndGet() }
            override fun isRecoveryTransportReady() = false
            override suspend fun validateRecoveredIdentity(expected: TerminalIdentity) = true
            override fun onRecoverySuccess() {}
            override fun onRecoveryFailureCleanup(reason: String) {
                // The gate MUST still be closed (active) while cleanup runs.
                if (!gateActive) gateClosedDuringCleanup = false
                order.add("failure-cleanup")
            }
            override fun getConnectedDeviceId() = "d"
            override fun getTaproVersion() = "1"
        }
        // claim(0)->deadline=5000; after-retire(100); poll opens; poll(6000)>=deadline fences.
        val clock = StepClock(listOf(0, 100, 200, 6000))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = EstablishedRecoveryCoordinator(recordingHost, clock, scope)
        coordinator.interceptIfEstablishedFault(fault())
        awaitSettled(coordinator)
        assertEquals(1, rec.disconnected.size)
        assertTrue(rec.disconnected.first().contains("213"))
        assertTrue("Recovery gate must stay closed throughout host failure cleanup", gateClosedDuringCleanup)
        // Order must be: gate-on (claim) -> failure-cleanup (host teardown) -> gate-off (finalize).
        assertEquals(listOf("gate-on", "failure-cleanup", "gate-off"), order)
    }

    /**
     * Review-B blocking (1) regression (success path): the recovery gate and owning generation must be
     * held until onRecoverySuccess() (re-attach of the rebuilt receive route) completes, so no new
     * request can race ahead of the re-attach. onRecoverySuccess must observe the gate still closed,
     * and the gate drop (finalize) must come strictly after it.
     *
     * SPEC-ESTABLISHED-RECOVERY-001 §AC6/R10/R15.
     */
    @Test
    fun successSettleKeepsRecoveryGateClosedUntilHostReattachCompletes() = runBlocking {
        val rec = Rec()
        val rebuilds = AtomicInteger(0)
        val order = mutableListOf<String>()
        var gateClosedDuringReattach = true
        val recordingHost = object : RecoveryHost {
            @Volatile var gateActive = false
            override fun captureCurrentTerminalIdentity() = TerminalIdentity("S")
            override fun currentConnectionConfigForRecovery() = ConnectionConfig.createDefault()
            override fun recoveryObservers() = listOf<ConnectionListener>(rec)
            override fun setEstablishedRecoveryActive(active: Boolean) {
                gateActive = active
                order.add(if (active) "gate-on" else "gate-off")
            }
            override fun evaluateRecoveryPrerequisite() = RecoveryPrereqResult.Available
            override fun retireForRecovery() {}
            override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { rebuilds.incrementAndGet() }
            override fun isRecoveryTransportReady() = true
            override suspend fun validateRecoveredIdentity(expected: TerminalIdentity) = true
            override fun onRecoverySuccess() {
                if (!gateActive) gateClosedDuringReattach = false
                order.add("success-reattach")
            }
            override fun onRecoveryFailureCleanup(reason: String) {}
            override fun getConnectedDeviceId() = "d"
            override fun getTaproVersion() = "1"
        }
        val clock = StepClock(listOf(0, 100, 200))
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = EstablishedRecoveryCoordinator(recordingHost, clock, scope)
        coordinator.interceptIfEstablishedFault(fault())
        awaitSettled(coordinator)
        assertEquals(1, rec.connected.size)
        assertTrue(rec.disconnected.isEmpty())
        assertTrue("Recovery gate must stay closed throughout host re-attach", gateClosedDuringReattach)
        assertEquals(listOf("gate-on", "success-reattach", "gate-off"), order)
    }

    /**
     * Review iteration blocking (1)/(2) regression: a same-config connect() issued during the
     * SETTLING WINDOW — the terminal settle has already won its one-time CAS (jobs cancelled) but the
     * Host-side teardown has NOT yet run, so finalizeTerminalSettle has not taken/cleared the observer
     * snapshot — MUST still be joined to the current episode and receive the SAME single terminal
     * outcome. It must NOT be rejected.
     *
     * Rejecting it was the bug: the real ConnectionManager.connect() does not return on a false here,
     * it falls through to disconnect() -> onExplicitDetachOrDisconnect(), which clears the live
     * episodeObservers that the winning settle still has to notify — losing the ORIGINAL observer's
     * terminal E213. Admitting the joiner into the still-live observer set guarantees BOTH the
     * original observer and the late joiner get exactly one onDisconnected(E213) from finalize's
     * single snapshot.
     *
     * The host blocks failure cleanup on a latch so the join happens exactly in the
     * phase1(won)->host-teardown window while isRecovering() is still true and observers are not yet
     * cleared.
     *
     * RED on the previous implementation (join rejected => lateJoiner got zero callbacks and the test
     * below asserting lateJoiner gets exactly one E213 fails).
     *
     * SPEC-ESTABLISHED-RECOVERY-001 §AC6/R9.
     */
    @Test
    fun settlingWindowJoinIsAdmittedAndReceivesTheSingleTerminalOutcome() = runBlocking {
        val rec = Rec()
        val rebuilds = AtomicInteger(0)
        val inCleanup = CountDownLatch(1)
        val releaseCleanup = CountDownLatch(1)
        val blockingHost = object : RecoveryHost {
            override fun captureCurrentTerminalIdentity() = TerminalIdentity("S")
            override fun currentConnectionConfigForRecovery() = ConnectionConfig.createDefault()
            override fun recoveryObservers() = listOf<ConnectionListener>(rec)
            override fun setEstablishedRecoveryActive(active: Boolean) {}
            override fun evaluateRecoveryPrerequisite() = RecoveryPrereqResult.Available
            override fun retireForRecovery() {}
            override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { rebuilds.incrementAndGet() }
            override fun isRecoveryTransportReady() = false
            override suspend fun validateRecoveredIdentity(expected: TerminalIdentity) = true
            override fun onRecoverySuccess() {}
            override fun onRecoveryFailureCleanup(reason: String) {
                // The settle has already won its CAS (jobs cancelled) but the gate/currentControl are
                // still held and the observer snapshot has NOT been taken yet: this is exactly the
                // settling window in which a same-config joiner must be admitted (not rejected).
                inCleanup.countDown()
                releaseCleanup.await(10, TimeUnit.SECONDS)
            }
            override fun getConnectedDeviceId() = "d"
            override fun getTaproVersion() = "1"
        }
        // claim(0)->deadline=5000; after-retire(100); poll opens; poll(6000)>=deadline fences.
        val clock = StepClock(listOf(0, 100, 200, 6000))
        // Run the episode on its own thread so onRecoveryFailureCleanup blocking does not stall the
        // test thread issuing joinIfRecovering.
        val episodeExecutor = Executors.newSingleThreadExecutor()
        val episodeScope = CoroutineScope(episodeExecutor.asCoroutineDispatcher())
        val coordinator = EstablishedRecoveryCoordinator(blockingHost, clock, episodeScope, Dispatchers.Default)
        val lateJoiner = Rec()
        try {
            coordinator.interceptIfEstablishedFault(fault())
            // Wait until the settle has won and is parked inside host failure cleanup.
            assertTrue(inCleanup.await(5, TimeUnit.SECONDS))
            assertTrue("Coordinator is still 'recovering' during host teardown", coordinator.isRecovering())
            // A same-config join in this window MUST be admitted (observers not yet cleared).
            assertTrue(
                "settling-window join must be admitted so the late caller shares the terminal outcome",
                coordinator.joinIfRecovering(lateJoiner)
            )
        } finally {
            releaseCleanup.countDown()
            awaitSettled(coordinator)
            episodeExecutor.shutdownNow()
        }
        // Both the original observer and the late joiner got exactly one terminal E213.
        assertEquals(1, rec.disconnected.size)
        assertTrue(rec.disconnected.first().contains("213"))
        assertEquals("The settling-window joiner must receive exactly one terminal E213", 1, lateJoiner.disconnected.size)
        assertTrue(lateJoiner.disconnected.first().contains("213"))
        assertTrue(lateJoiner.connected.isEmpty())
        assertTrue(lateJoiner.errors.isEmpty())
    }

    /**
     * Cross-epoch regression (a): a Main-starved episode A is settled by its watchdog; A's body is
     * still mid-flight and only resumes AFTER a fresh episode B has claimed. A's late settle attempt
     * targets A's OWN (no-longer-current) control and MUST be rejected by the per-episode settle gate
     * without flipping B's token. B must still receive its own single terminal outcome.
     *
     * A's body is held deterministically at a host barrier (inside retireForRecovery) so the
     * interleaving is exact: watchdog settles A -> B claims -> A's body resumes and tries to settle B.
     *
     * SPEC-ESTABLISHED-RECOVERY-001 §AC4/§AC6 (two-generation isolation).
     */
    @Test
    fun staleEpisodeResumingAfterWatchdogSettleDoesNotPoisonNextEpisode() = runBlocking {
        val recA = Rec()
        val rebuildsA = AtomicInteger(0)
        val aInRetire = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        // A's clock: claim(0)->deadline=5000; the watchdog reads 6000 -> fires settleFailure for A.
        val clockA = StepClock(listOf(0, 6000))
        val hostA = object : RecoveryHost {
            override fun captureCurrentTerminalIdentity() = TerminalIdentity("S")
            override fun currentConnectionConfigForRecovery() = ConnectionConfig.createDefault()
            override fun recoveryObservers() = listOf<ConnectionListener>(recA)
            override fun setEstablishedRecoveryActive(active: Boolean) {}
            override fun evaluateRecoveryPrerequisite() = RecoveryPrereqResult.Available
            override fun retireForRecovery() {
                // Hold A's body here until the test releases it (after B has claimed).
                aInRetire.countDown()
                releaseA.await(10, TimeUnit.SECONDS)
            }
            override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { rebuildsA.incrementAndGet() }
            override fun isRecoveryTransportReady() = false
            override suspend fun validateRecoveredIdentity(expected: TerminalIdentity) = true
            override fun onRecoverySuccess() {}
            override fun onRecoveryFailureCleanup(reason: String) {}
            override fun getConnectedDeviceId() = "d"
            override fun getTaproVersion() = "1"
        }
        // A runs on its own single thread so retireForRecovery can block without stalling the watchdog.
        val episodeExecutorA = Executors.newSingleThreadExecutor()
        val episodeScopeA = CoroutineScope(episodeExecutorA.asCoroutineDispatcher())
        val coordinator = EstablishedRecoveryCoordinator(hostA, clockA, episodeScopeA, Dispatchers.Unconfined)
        try {
            coordinator.interceptIfEstablishedFault(fault())
            // A's body entered retire and is parked; the watchdog (clock=6000) settles A exactly once.
            assertTrue(aInRetire.await(5, TimeUnit.SECONDS))
            withTimeout(5000) { while (recA.disconnected.isEmpty()) delay(5) }
            assertEquals(1, recA.disconnected.size)
            assertTrue(!coordinator.isRecovering())

            // Claim fresh episode B on an Unconfined scope + never-advancing clock so B only settles
            // via its OWN non-explicit cancel.
            val recB = Rec()
            val rebuildsB = AtomicInteger(0)
            val zeroClock = object : RecoveryClock { override fun elapsedRealtimeMs() = 0L }
            coordinator.javaClass.getDeclaredField("clock").apply { isAccessible = true }.set(coordinator, zeroClock)
            val episodeScopeB = CoroutineScope(Dispatchers.Unconfined)
            coordinator.javaClass.getDeclaredField("scope").apply { isAccessible = true }.set(coordinator, episodeScopeB)
            EstablishedRecoveryCoordinator::class.java.getDeclaredField("connectionManager")
                .apply { isAccessible = true }.set(coordinator, host(ready = false, obs = recB, rebuilds = rebuildsB))
            coordinator.interceptIfEstablishedFault(fault())
            withTimeout(5000) { while (!coordinator.isRecovering()) delay(5) }

            // Release A's parked body: it resumes and (deadline long past) attempts settleFailure on
            // A's stale control. The per-episode gate must reject it without touching B.
            releaseA.countDown()
            delay(200)

            // B must still own its own terminal: cancel B non-explicitly.
            episodeScopeB.coroutineContext[Job]!!.cancelChildren()
            withTimeout(5000) { while (recB.disconnected.isEmpty()) delay(5) }
            assertEquals("B must receive its own single terminal disconnect", 1, recB.disconnected.size)
            assertTrue(recB.disconnected.first().contains("213"))
            assertTrue(recB.errors.isEmpty())
            assertEquals("A must not deliver a second terminal", 1, recA.disconnected.size)
        } finally {
            releaseA.countDown()
            episodeExecutorA.shutdownNow()
        }
    }

    /**
     * Cross-epoch regression (b): episode A is explicitly detached while its body is mid-flight; a new
     * episode B then claims; A's delayed CancellationException catch runs AFTER B claimed. It must
     * read A's OWN per-episode explicit-detach flag (staying silent) and must NOT settle against B's
     * gate. B still receives its own single terminal outcome.
     *
     * SPEC-ESTABLISHED-RECOVERY-001 §AC6 (two-generation isolation of the D2 flag).
     */
    @Test
    fun staleExplicitDetachCancellationDoesNotPoisonNextEpisode() = runBlocking {
        val recA = Rec()
        val rebuildsA = AtomicInteger(0)
        val zeroClock = object : RecoveryClock { override fun elapsedRealtimeMs() = 0L }
        val aInRetire = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val hostA = object : RecoveryHost {
            override fun captureCurrentTerminalIdentity() = TerminalIdentity("S")
            override fun currentConnectionConfigForRecovery() = ConnectionConfig.createDefault()
            override fun recoveryObservers() = listOf<ConnectionListener>(recA)
            override fun setEstablishedRecoveryActive(active: Boolean) {}
            override fun evaluateRecoveryPrerequisite() = RecoveryPrereqResult.Available
            override fun retireForRecovery() {
                aInRetire.countDown()
                releaseA.await(10, TimeUnit.SECONDS)
            }
            override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { rebuildsA.incrementAndGet() }
            override fun isRecoveryTransportReady() = false
            override suspend fun validateRecoveredIdentity(expected: TerminalIdentity) = true
            override fun onRecoverySuccess() {}
            override fun onRecoveryFailureCleanup(reason: String) {}
            override fun getConnectedDeviceId() = "d"
            override fun getTaproVersion() = "1"
        }
        val episodeExecutorA = Executors.newSingleThreadExecutor()
        val episodeScopeA = CoroutineScope(episodeExecutorA.asCoroutineDispatcher())
        val coordinator = EstablishedRecoveryCoordinator(hostA, zeroClock, episodeScopeA, Dispatchers.Default)
        try {
            coordinator.interceptIfEstablishedFault(fault())
            // A's body is parked in retire. Explicitly detach A: no callback, A's flag set as explicit,
            // A's episode job cancelled (its catch will run once retire unblocks).
            assertTrue(aInRetire.await(5, TimeUnit.SECONDS))
            coordinator.onExplicitDetachOrDisconnect()
            assertTrue("Explicit detach of A must not leave the coordinator recovering", !coordinator.isRecovering())
            assertTrue("Explicit detach must deliver no callback for A", recA.disconnected.isEmpty())

            // Claim B on an Unconfined scope; its terminal must come solely from B's own cancel.
            val recB = Rec()
            val rebuildsB = AtomicInteger(0)
            val episodeScopeB = CoroutineScope(Dispatchers.Unconfined)
            coordinator.javaClass.getDeclaredField("scope").apply { isAccessible = true }.set(coordinator, episodeScopeB)
            EstablishedRecoveryCoordinator::class.java.getDeclaredField("connectionManager")
                .apply { isAccessible = true }.set(coordinator, host(ready = false, obs = recB, rebuilds = rebuildsB))
            coordinator.interceptIfEstablishedFault(fault())
            withTimeout(5000) { while (!coordinator.isRecovering()) delay(5) }

            // Release A's parked body: its cancellation catch runs now (after B claimed). Reading A's
            // own explicit-detach flag, it must stay silent and never settle B.
            releaseA.countDown()
            delay(200)

            episodeScopeB.coroutineContext[Job]!!.cancelChildren()
            withTimeout(5000) { while (recB.disconnected.isEmpty()) delay(5) }
            assertEquals("B must receive its own single terminal disconnect", 1, recB.disconnected.size)
            assertTrue(recB.disconnected.first().contains("213"))
            assertTrue("A's stale explicit-detach cancel must never deliver a callback", recA.disconnected.isEmpty())
            assertTrue(recA.errors.isEmpty())
        } finally {
            releaseA.countDown()
            episodeExecutorA.shutdownNow()
        }
    }
}
