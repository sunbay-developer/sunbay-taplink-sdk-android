package com.sunmi.tapro.taplink.sdk.manager

import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.communication.interfaces.recovery.RecoveryPrereqResult
import com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic controller tests for [EstablishedRecoveryCoordinator], driven through the REAL
 * coordinator logic via a scripted [RecoveryHost] and a fake [RecoveryClock]. They cover the
 * single-owner / epoch / dedup / terminal-once / no-onError / success / failure / prereq semantics
 * without hardware. Mapped to SPEC-ESTABLISHED-RECOVERY-001 AC and TC-ER-001..015.
 *
 * The coordinator runs its episode on the injected scope; here it is an Unconfined scope so the
 * episode advances eagerly on the test thread. Tests await `!isRecovering()` with a wall-clock bound.
 */
class EstablishedRecoveryControllerTest {

    /** Fake monotonic clock controllable by tests. */
    private class FakeClock(var now: Long = 0L) : RecoveryClock {
        override fun elapsedRealtimeMs(): Long = now
    }

    /** Records terminal callbacks per observer identity. */
    private class RecordingListener : ConnectionListener {
        val connected = mutableListOf<String>()
        val disconnected = mutableListOf<String>()
        val errors = mutableListOf<String>()
        override fun onConnected(deviceId: String, taproVersion: String) { connected.add(deviceId) }
        override fun onDisconnected(reason: String) { disconnected.add(reason) }
        override fun onError(error: ConnectionError) { errors.add(error.code) }
    }

    /** Scriptable host exposing exactly the seams the coordinator drives. */
    private class FakeHost : RecoveryHost {
        var prereq: RecoveryPrereqResult = RecoveryPrereqResult.Available
        @Volatile var transportReady = true
        var identityValid = true
        var identity: TerminalIdentity? = TerminalIdentity("SERIAL-1")
        var observers = mutableListOf<ConnectionListener>()
        var config: ConnectionConfig? = ConnectionConfig.createDefault()

        val retireCount = AtomicInteger(0)
        val rebuildCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        val failureCleanupCount = AtomicInteger(0)
        val recoveryActiveSets = mutableListOf<Boolean>()
        val validateCount = AtomicInteger(0)

        override fun captureCurrentTerminalIdentity(): TerminalIdentity? = identity
        override fun currentConnectionConfigForRecovery(): ConnectionConfig? = config
        override fun recoveryObservers(): List<ConnectionListener> = observers.toList()
        override fun setEstablishedRecoveryActive(active: Boolean) { recoveryActiveSets.add(active) }
        override fun evaluateRecoveryPrerequisite(): RecoveryPrereqResult = prereq
        override fun retireForRecovery() { retireCount.incrementAndGet() }
        override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { rebuildCount.incrementAndGet() }
        override fun isRecoveryTransportReady(): Boolean = transportReady
        override suspend fun validateRecoveredIdentity(expected: TerminalIdentity): Boolean {
            validateCount.incrementAndGet()
            return identityValid
        }
        override fun onRecoverySuccess() { successCount.incrementAndGet() }
        override fun onRecoveryFailureCleanup(reason: String) { failureCleanupCount.incrementAndGet() }
        override fun getConnectedDeviceId(): String? = "device-1"
        override fun getTaproVersion(): String? = "1.0"
    }

    private fun newCoordinator(host: FakeHost, clock: RecoveryClock = FakeClock()):
        Pair<EstablishedRecoveryCoordinator, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return EstablishedRecoveryCoordinator(host, clock, scope) to scope
    }

    private fun fault(established: Boolean) =
        TransportFaultEvent(sessionId = 1, epoch = 0, code = "1006", msg = "drop", established = established)

    private suspend fun awaitSettled(coordinator: EstablishedRecoveryCoordinator) {
        withTimeout(5000) {
            while (coordinator.isRecovering()) delay(5)
        }
    }

    /** SPEC-ESTABLISHED-RECOVERY-001 §AC3 / TC-ER-003: first-connect fault is not owned by recovery. */
    @Test
    fun firstConnectFaultIsNotIntercepted() {
        val host = FakeHost()
        val (coordinator, _) = newCoordinator(host)
        assertFalse(coordinator.interceptIfEstablishedFault(fault(established = false)))
        assertFalse(coordinator.isRecovering())
        assertEquals(0, host.rebuildCount.get())
    }

    /** §AC6 / TC-ER-006: success -> exactly one onConnected per distinct observer, no onError/onDisconnected. */
    @Test
    fun successDeliversExactlyOneConnectedPerObserverNoError() = runBlocking {
        val host = FakeHost()
        val a = RecordingListener()
        val b = RecordingListener()
        // 'a' registered in two roles: must still be notified at most once.
        host.observers = mutableListOf(a, b, a)
        val (coordinator, _) = newCoordinator(host)
        assertTrue(coordinator.interceptIfEstablishedFault(fault(established = true)))
        awaitSettled(coordinator)
        assertEquals(listOf("device-1"), a.connected)
        assertEquals(listOf("device-1"), b.connected)
        assertTrue(a.errors.isEmpty() && b.errors.isEmpty())
        assertTrue(a.disconnected.isEmpty() && b.disconnected.isEmpty())
        assertEquals(1, host.successCount.get())
        assertEquals(0, host.failureCleanupCount.get())
    }

    /** §AC6/§AC10 / TC-ER-006, TC-ER-010: identity-unconfirmed failure -> one E213 onDisconnected, no onError,
     *  cleanup exactly once, never an early E306. */
    @Test
    fun identityUnconfirmedFailsWithSingleE213AndCleanupOnce() = runBlocking {
        val host = FakeHost().apply { identityValid = false }
        val a = RecordingListener()
        host.observers = mutableListOf(a)
        val (coordinator, _) = newCoordinator(host)
        coordinator.interceptIfEstablishedFault(fault(established = true))
        awaitSettled(coordinator)
        assertEquals(1, a.disconnected.size)
        assertTrue(a.disconnected.first().contains("213"))
        assertTrue(a.errors.isEmpty())
        assertTrue(a.connected.isEmpty())
        assertEquals(1, host.failureCleanupCount.get())
        assertEquals(0, host.successCount.get())
    }

    /** §AC4 / TC-ER-004: duplicate concurrent faults merge into ONE episode (one rebuild). */
    @Test
    fun duplicateFaultsMergeIntoOneEpisode() = runBlocking {
        // Hold the episode in the poll loop so the second fault lands while active.
        val host = FakeHost().apply { transportReady = false }
        val (coordinator, _) = newCoordinator(host)
        assertTrue(coordinator.interceptIfEstablishedFault(fault(established = true)))
        assertTrue(coordinator.interceptIfEstablishedFault(fault(established = true)))
        host.transportReady = true
        awaitSettled(coordinator)
        assertEquals(1, host.rebuildCount.get())
    }

    /** §AC12 / TC-ER-012: denied prerequisite ends the attempt with one failure, no rebuild. */
    @Test
    fun deniedPrerequisiteFailsWithoutRebuild() = runBlocking {
        val host = FakeHost().apply { prereq = RecoveryPrereqResult.Denied("auth required") }
        val a = RecordingListener()
        host.observers = mutableListOf(a)
        val (coordinator, _) = newCoordinator(host)
        coordinator.interceptIfEstablishedFault(fault(established = true))
        awaitSettled(coordinator)
        assertEquals(0, host.rebuildCount.get())
        assertEquals(1, a.disconnected.size)
        assertTrue(a.errors.isEmpty())
    }

    /** §AC9 / TC-ER-009: explicit detach/disconnect fences the episode; no later terminal reaches an observer. */
    @Test
    fun explicitDetachFencesEpisode() = runBlocking {
        val host = FakeHost().apply { transportReady = false }
        val a = RecordingListener()
        host.observers = mutableListOf(a)
        val (coordinator, _) = newCoordinator(host)
        coordinator.interceptIfEstablishedFault(fault(established = true))
        coordinator.onExplicitDetachOrDisconnect()
        assertFalse(coordinator.isRecovering())
        // Even if the transport later becomes ready, nothing is delivered.
        host.transportReady = true
        delay(50)
        assertTrue(a.connected.isEmpty())
        assertTrue(a.disconnected.isEmpty())
        assertTrue(a.errors.isEmpty())
    }

    /** §AC6 / TC-ER-009: a throwing observer does not block delivery to the others. */
    @Test
    fun throwingObserverDoesNotBlockOthers() = runBlocking {
        val host = FakeHost()
        val throwing = object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) { throw RuntimeException("boom") }
            override fun onDisconnected(reason: String) {}
            override fun onError(error: ConnectionError) {}
        }
        val good = RecordingListener()
        host.observers = mutableListOf(throwing, good)
        val (coordinator, _) = newCoordinator(host)
        coordinator.interceptIfEstablishedFault(fault(established = true))
        awaitSettled(coordinator)
        assertEquals(listOf("device-1"), good.connected)
    }

    /** §AC9 / TC-ER-009: a same-config caller joining during recovery receives the single terminal result. */
    @Test
    fun joinedCallerReceivesSingleTerminalOutcome() = runBlocking {
        val host = FakeHost().apply { transportReady = false }
        val (coordinator, _) = newCoordinator(host)
        coordinator.interceptIfEstablishedFault(fault(established = true))
        val joined = RecordingListener()
        assertTrue(coordinator.joinIfRecovering(joined))
        host.transportReady = true
        awaitSettled(coordinator)
        assertEquals(1, joined.connected.size)
        assertTrue(joined.errors.isEmpty())
    }

    /** §AC5/§AC11 / TC-ER-005, TC-ER-011: recovery toggles the PaymentManager gate active then inactive. */
    @Test
    fun recoveryTogglesPaymentGate() = runBlocking {
        val host = FakeHost()
        val (coordinator, _) = newCoordinator(host)
        coordinator.interceptIfEstablishedFault(fault(established = true))
        assertTrue(host.recoveryActiveSets.first())
        awaitSettled(coordinator)
        assertFalse(host.recoveryActiveSets.last())
    }

    /** §AC7 / TC-ER-007: retire runs before the rebuild (retire->open ordering). */
    @Test
    fun retireRunsBeforeRebuild() = runBlocking {
        val order = mutableListOf<String>()
        val host = object : RecoveryHost {
            override fun captureCurrentTerminalIdentity() = TerminalIdentity("S")
            override fun currentConnectionConfigForRecovery() = ConnectionConfig.createDefault()
            override fun recoveryObservers() = emptyList<ConnectionListener>()
            override fun setEstablishedRecoveryActive(active: Boolean) {}
            override fun evaluateRecoveryPrerequisite() = RecoveryPrereqResult.Available
            override fun retireForRecovery() { order.add("retire") }
            override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) { order.add("open") }
            override fun isRecoveryTransportReady() = true
            override suspend fun validateRecoveredIdentity(expected: TerminalIdentity) = true
            override fun onRecoverySuccess() {}
            override fun onRecoveryFailureCleanup(reason: String) {}
            override fun getConnectedDeviceId() = "d"
            override fun getTaproVersion() = "1"
        }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val coordinator = EstablishedRecoveryCoordinator(host, FakeClock(), scope)
        coordinator.interceptIfEstablishedFault(fault(established = true))
        awaitSettled(coordinator)
        assertEquals(listOf("retire", "open"), order)
    }
}
