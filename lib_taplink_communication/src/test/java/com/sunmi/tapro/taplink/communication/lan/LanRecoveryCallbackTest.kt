package com.sunmi.tapro.taplink.communication.lan

import android.content.ContextWrapper
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.lan.model.ServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/** Regression evidence for SPEC-LAN-RECOVERY-001 §AC2/AC3. */
class LanRecoveryCallbackTest {
    private class Events : ConnectionCallback {
        val failures = CopyOnWriteArrayList<String>()
        val done = CompletableDeferred<Unit>()
        override fun onWaitingConnect() = Unit
        override fun onConnected(extraInfoMap: Map<String, String?>?) { done.complete(Unit) }
        override fun onDisconnected(code: String, msg: String) {
            failures.add(code)
            done.complete(Unit)
        }
    }

    private fun refusedEndpoint(): String = ServerSocket(0).use { "ws://127.0.0.1:${it.localPort}" }

    @Test
    fun handshakeFailureRemainsConnectingWhileDiscoveryIsPending() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<List<ServiceInfo>>()
        val kernel = LanClientKernel("test", "test", ContextWrapper(null)) {
            entered.complete(Unit)
            release.await()
        }
        val events = Events()
        val states = CopyOnWriteArrayList<InnerConnectionStatus>()
        kernel.configureConnectionOptions(LanConnectionOptions(enableServiceMonitoring = false))
        kernel.setStatusChangeListener { states.add(it) }
        try {
            kernel.connect(refusedEndpoint(), events)
            withTimeout(5000) { entered.await() }
            assertEquals("Intermediate failure must not notify the caller", emptyList<String>(), events.failures)
            assertEquals(InnerConnectionStatus.CONNECTING, kernel.getConnectionStatus())
            assertFalse(states.contains(InnerConnectionStatus.ERROR))
            assertFalse(states.contains(InnerConnectionStatus.DISCONNECTED))
        } finally {
            kernel.disconnect()
        }
    }

    @Test
    fun finalDiscoveryFailureReportsOnlyOneTerminalError() = runBlocking {
        val kernel = LanClientKernel("test", "test", ContextWrapper(null)) {
            throw IllegalStateException("invalid discovery configuration")
        }
        val events = Events()
        kernel.configureConnectionOptions(LanConnectionOptions(enableServiceMonitoring = false))
        try {
            kernel.connect(refusedEndpoint(), events)
            withTimeout(5000) { events.done.await() }
            // Wait for the operation to exit so the assertion observes both original failure paths.
            withTimeout(5000) {
                while (kernel.getCurrentOperation() != null) kotlinx.coroutines.delay(10)
            }
            assertEquals(listOf("241"), events.failures)
            assertEquals(InnerConnectionStatus.ERROR, kernel.getConnectionStatus())
        } finally {
            kernel.disconnect()
        }
    }
    /**
     * SPEC-LAN-RECOVERY-001 §AC3 (Fix B). An upper layer that rejects every discovered target (or no
     * owning listener at all) must NOT be treated as a one-shot non-retryable abort. Per R3 the kernel
     * converges within the existing bounded discovery budget (4 attempts) and only then reports exactly
     * one terminal 241 ending in ERROR — never a silent CONNECTING stall, never an immediate abort.
     */
    @Test
    fun rejectedOrUnownedDiscoveryTargetTerminatesTheAttempt() = runBlocking {
        for (installRejectingListener in listOf(false, true)) {
            val endpoint = refusedEndpoint()
            val port = java.net.URI(endpoint).port
            var discoveries = 0
            val kernel = LanClientKernel("test", "test", ContextWrapper(null)) {
                discoveries++
                listOf(ServiceInfo("Taplink-Server-1-TESTSERIAL", "_taplink._tcp", "127.0.0.1", port))
            }
            if (installRejectingListener) {
                kernel.setServiceAddressChangeListener(object : LanClientKernel.ServiceAddressChangeListener {
                    override fun onServiceAddressChanged(serviceName: String, newHost: String, newPort: Int, oldHost: String, oldPort: Int) = false
                })
            }
            val events = Events()
            try {
                kernel.connect(endpoint, events)
                withTimeout(12000) { events.done.await() }
                // Converged across the full bounded budget (not a single-shot abort) before terminating.
                assertEquals(4, discoveries)
                assertEquals(listOf("241"), events.failures)
                assertEquals(InnerConnectionStatus.ERROR, kernel.getConnectionStatus())
            } finally {
                kernel.disconnect()
            }
        }
    }

    /**
     * SPEC-LAN-RECOVERY-001 §AC3 (Fix B regression guard). Explicitly proves "upper layer declined" is
     * not conflated with a fatal recovery handoff: after the FIRST round's rejection the attempt stays
     * CONNECTING with no failure reported, and only once the bounded budget is exhausted does a single
     * terminal 241 appear. This is the direct counter-case to the f935c54 regression where a first-round
     * rejection raised an "invalid recovery handoff" and was aborted as non-retryable.
     */
    @Test
    fun upperLayerRejectionIsNotTreatedAsNonRetryableAbort() = runBlocking {
        val endpoint = refusedEndpoint()
        val port = java.net.URI(endpoint).port
        val firstRound = CompletableDeferred<Unit>()
        var discoveries = 0
        val kernel = LanClientKernel("test", "test", ContextWrapper(null)) {
            discoveries++
            if (discoveries == 1) firstRound.complete(Unit)
            listOf(ServiceInfo("Taplink-Server-1-TESTSERIAL", "_taplink._tcp", "127.0.0.1", port))
        }
        kernel.setServiceAddressChangeListener(object : LanClientKernel.ServiceAddressChangeListener {
            override fun onServiceAddressChanged(serviceName: String, newHost: String, newPort: Int, oldHost: String, oldPort: Int) = false
        })
        val events = Events()
        try {
            kernel.connect(endpoint, events)
            // After the first rejection the attempt must still be converging, not aborted.
            withTimeout(5000) { firstRound.await() }
            assertEquals("First-round rejection must not emit a terminal failure", emptyList<String>(), events.failures)
            assertEquals(InnerConnectionStatus.CONNECTING, kernel.getConnectionStatus())
            // Budget then converges to exactly one terminal error.
            withTimeout(12000) { events.done.await() }
            assertEquals(4, discoveries)
            assertEquals(listOf("241"), events.failures)
            assertEquals(InnerConnectionStatus.ERROR, kernel.getConnectionStatus())
        } finally {
            kernel.disconnect()
        }
    }

    /**
     * SPEC-LAN-RECOVERY-001 §AC6 (Fix B regression guard). Fast transaction recovery opts out of deferral
     * (deferHandshakeFailureCallbacks=false) and must retain its EXISTING immediate return path, untouched
     * by Fix B. The guard here is that it does NOT enter the deferred budget-convergence logic added for
     * the main recovery path: it takes a single discovery round and returns immediately rather than looping
     * the full 4-attempt budget. The existing immediate error callback behaviour of that branch is
     * preserved verbatim (Fix B only changed the deferHandshakeFailureCallbacks==true branch).
     */
    @Test
    fun fastRecoveryRejectionRetainsImmediateReturnPath() = runBlocking {
        val endpoint = refusedEndpoint()
        val port = java.net.URI(endpoint).port
        var discoveries = 0
        val kernel = LanClientKernel("test", "test", ContextWrapper(null)) {
            discoveries++
            listOf(ServiceInfo("Taplink-Server-1-TESTSERIAL", "_taplink._tcp", "127.0.0.1", port))
        }
        kernel.configureConnectionOptions(
            LanConnectionOptions(enableServiceMonitoring = false, deferHandshakeFailureCallbacks = false)
        )
        kernel.setServiceAddressChangeListener(object : LanClientKernel.ServiceAddressChangeListener {
            override fun onServiceAddressChanged(serviceName: String, newHost: String, newPort: Int, oldHost: String, oldPort: Int) = false
        })
        val events = Events()
        try {
            kernel.connect(endpoint, events)
            // Let the operation finish (immediate return path, no deferred budget retries).
            withTimeout(5000) {
                while (kernel.getCurrentOperation() != null) kotlinx.coroutines.delay(10)
            }
            // The decisive invariant: the rejection branch returned after a SINGLE discovery round and did
            // NOT loop the deferred 4-attempt recovery budget introduced by Fix B. This proves fast
            // recovery still takes its own immediate path.
            assertEquals("Fast recovery must not loop the deferred recovery budget", 1, discoveries)
        } finally {
            kernel.disconnect()
        }
    }

    @Test
    fun noDiscoveryTargetExhaustsExistingBoundedRetriesAndReportsFinalFailure() = runBlocking {
        var discoveries = 0
        val kernel = LanClientKernel("test", "test", ContextWrapper(null)) {
            discoveries++
            emptyList()
        }
        val events = Events()
        try {
            kernel.connect(refusedEndpoint(), events)
            withTimeout(12000) { events.done.await() }
            assertEquals(4, discoveries)
            assertEquals(listOf("241"), events.failures)
            assertEquals(InnerConnectionStatus.ERROR, kernel.getConnectionStatus())
        } finally {
            kernel.disconnect()
        }
    }

}
