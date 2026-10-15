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
import org.junit.Assert.assertTrue
import org.junit.Test
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

    private class Rec : ConnectionListener {
        val connected = mutableListOf<String>()
        val disconnected = mutableListOf<String>()
        val errors = mutableListOf<String>()
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
}
