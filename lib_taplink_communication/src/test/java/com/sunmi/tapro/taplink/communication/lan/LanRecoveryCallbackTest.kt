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
    @Test
    fun rejectedOrUnownedDiscoveryTargetTerminatesTheAttempt() = runBlocking {
        for (installRejectingListener in listOf(false, true)) {
            val endpoint = refusedEndpoint()
            val port = java.net.URI(endpoint).port
            val kernel = LanClientKernel("test", "test", ContextWrapper(null)) {
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
                withTimeout(5000) { events.done.await() }
                assertEquals(listOf("241"), events.failures)
                assertEquals(InnerConnectionStatus.ERROR, kernel.getConnectionStatus())
            } finally {
                kernel.disconnect()
            }
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
