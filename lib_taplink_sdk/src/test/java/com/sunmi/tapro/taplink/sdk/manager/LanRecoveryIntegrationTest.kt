package com.sunmi.tapro.taplink.sdk.manager

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.lan.LanClientKernel
import com.sunmi.tapro.taplink.communication.lan.model.ServiceInfo
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import kotlinx.coroutines.*
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/** Runs the actual SDK/kernel/transport handoff with local sockets and fake Android NSD/storage.
 * SPEC-LAN-RECOVERY-001 §AC1/AC2/AC3/AC4/AC5/AC6.
 */
class LanRecoveryIntegrationTest {
    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, _ ->
                when (method.name) {
                    "commit" -> true
                    "apply" -> null
                    else -> proxy
                }
            }
            return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
                when (method.name) {
                    "edit" -> editor
                    "getAll" -> emptyMap<String, Any>()
                    "contains" -> false
                    else -> args?.getOrNull(1)
                }
            } as SharedPreferences
        }
    }

    private class Events : ConnectionListener {
        val connected = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<String>()
        val disconnected = CopyOnWriteArrayList<String>()
        val completion = CompletableDeferred<Unit>()
        override fun onConnected(deviceId: String, taproVersion: String) {
            connected.add(deviceId)
            completion.complete(Unit)
        }
        override fun onError(error: ConnectionError) {
            errors.add(error.code)
            completion.complete(Unit)
        }
        override fun onDisconnected(reason: String) { disconnected.add(reason) }
    }

    private class Server : WebSocketServer(InetSocketAddress("127.0.0.1", 0)) {
        val ready = CompletableDeferred<Unit>()
        override fun onStart() { ready.complete(Unit) }
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) = Unit
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) = Unit
        override fun onMessage(conn: WebSocket, message: String) = Unit
        override fun onError(conn: WebSocket?, ex: Exception) { ready.completeExceptionally(ex) }
    }

    private fun config(port: Int, autoReconnect: Boolean = true) = ConnectionConfig.createLanMode("127.0.0.1", port)
        .setAutoReconnect(autoReconnect)

    private fun unusedPort(): Int = ServerSocket(0).use { it.localPort }

    /**
     * Replace the recovery coordinator's Main-confined scope with an Unconfined one and shorten its
     * budget so the real recovery episode settles within a JVM test.
     */
    private fun installRecoveryTestScope(manager: ConnectionManager) {
        val coordinator = ConnectionManager::class.java
            .getDeclaredField("establishedRecoveryCoordinator").apply { isAccessible = true }.get(manager)
        coordinator.javaClass.getDeclaredField("scope").apply { isAccessible = true }
            .set(coordinator, CoroutineScope(Dispatchers.Unconfined))
        val start = System.currentTimeMillis()
        val fast = object : RecoveryClock {
            override fun elapsedRealtimeMs(): Long = (System.currentTimeMillis() - start) * 6
        }
        coordinator.javaClass.getDeclaredField("clock").apply { isAccessible = true }.set(coordinator, fast)
    }

    private fun setup(discover: suspend ((ServiceInfo) -> Boolean) -> List<ServiceInfo>): Pair<ConnectionManager, TaplinkServiceKernel> {
        val context = TestContext()
        val service = TaplinkServiceKernel.getInstance(context)
        service.release()
        val kernel = LanClientKernel("test-app", "test-secret", context)
        LanClientKernel::class.java.getDeclaredField("discoverServices").apply { isAccessible = true }.set(kernel, discover)
        TaplinkServiceKernel::class.java.getDeclaredField("currentServiceKernel").apply { isAccessible = true }.set(service, kernel)
        return ConnectionManager(TaplinkConfig(appId = "test-app", secretKey = "test-secret"), context) to service
    }

    @Test
    fun newPortSuccessNotifiesOriginalCallerObserverAndQueuedCallerWithoutTransientFailure() = runBlocking {
        val server = Server()
        server.start()
        withTimeout(5000) { server.ready.await() }
        val discoveryEntered = CompletableDeferred<Unit>()
        val releaseDiscovery = CompletableDeferred<Unit>()
        val (manager, service) = setup {
            discoveryEntered.complete(Unit)
            releaseDiscovery.await()
            listOf(ServiceInfo("Taplink-Server-1-TESTSERIAL", "_taplink._tcp", "127.0.0.1", server.port))
        }
        val primary = Events()
        val observer = Events()
        val queued = Events()
        manager.setConnectionListener(observer)
        try {
            val stale = config(unusedPort())
            manager.connect(stale, primary)
            withTimeout(5000) { discoveryEntered.await() }
            assertTrue(manager.isConnecting())
            manager.connect(stale, queued)
            releaseDiscovery.complete(Unit)
            withTimeout(5000) {
                primary.completion.await()
                observer.completion.await()
                queued.completion.await()
            }
            listOf(primary, observer, queued).forEach {
                assertEquals(1, it.connected.size)
                assertTrue(it.errors.isEmpty())
                assertTrue(it.disconnected.isEmpty())
            }
            assertTrue(manager.isConnected())
        } finally {
            manager.disconnect()
            service.release()
            server.stop(1000)
        }
    }

    @Test
    fun discoveryFailureDeduplicatesSharedObserverAndQueuedCallersAndAllowsFreshConnect() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val (manager, service) = setup {
            entered.complete(Unit)
            release.await()
            throw IllegalStateException("invalid discovery configuration")
        }
        val shared = Events()
        val queued = Events()
        manager.setConnectionListener(shared)
        try {
            val stale = config(unusedPort())
            manager.connect(stale, shared)
            withTimeout(5000) { entered.await() }
            manager.connect(stale, shared)
            manager.connect(stale, queued)
            release.complete(Unit)
            withTimeout(5000) { shared.completion.await(); queued.completion.await() }
            assertEquals(listOf("241"), shared.errors)
            assertEquals(listOf("241"), queued.errors)
            assertTrue(shared.disconnected.isEmpty())
            assertTrue(queued.disconnected.isEmpty())
            assertFalse(manager.isConnecting())
            val server = Server()
            server.start()
            try {
                withTimeout(5000) { server.ready.await() }
                val fresh = Events()
                manager.connect(config(server.port), fresh)
                withTimeout(5000) { fresh.completion.await() }
                assertTrue(fresh.errors.isEmpty())
                assertEquals(1, fresh.connected.size)
            } finally {
                manager.disconnect()
                server.stop(1000)
            }
        } finally {
            manager.disconnect()
            service.release()
        }
    }

    @Test
    fun disabledAutoReconnectDoesNotDiscover() = runBlocking {
        var discoveries = 0
        val (manager, service) = setup { discoveries++; emptyList() }
        val events = Events()
        try {
            manager.connect(config(unusedPort(), false), events)
            withTimeout(5000) { events.completion.await() }
            assertTrue(events.errors.isNotEmpty())
            assertEquals(0, discoveries)
            assertFalse(manager.isConnecting())
        } finally {
            manager.disconnect()
            service.release()
        }
    }

    @Test
    fun cancelledDiscoveryCannotReconnectOrReportAnOldFailure() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val (manager, service) = setup {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        val events = Events()
        try {
            manager.connect(config(unusedPort()), events)
            withTimeout(5000) { entered.await() }
            manager.disconnect()
            withTimeout(5000) { cancelled.await() }
            assertTrue(events.errors.isEmpty())
            assertTrue(events.connected.isEmpty())
            assertEquals(ConnectionStatus.DISCONNECTED, manager.getConnectionStatus())
        } finally {
            service.release()
        }
    }
    @Test
    fun switchingToNewExplicitConnectionCancelsDiscoveryWithoutAnObsoleteResult() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val (manager, service) = setup {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        val old = Events()
        val fresh = Events()
        val server = Server()
        server.start()
        try {
            withTimeout(5000) { server.ready.await() }
            manager.connect(config(unusedPort()), old)
            withTimeout(5000) { entered.await() }
            manager.connect(config(server.port), fresh)
            withTimeout(5000) { cancelled.await(); fresh.completion.await() }
            assertTrue(old.errors.isEmpty())
            assertTrue(old.connected.isEmpty())
            assertEquals(1, fresh.connected.size)
            assertTrue(fresh.errors.isEmpty())
            assertTrue(manager.isConnected())
        } finally {
            manager.disconnect()
            service.release()
            server.stop(1000)
        }
    }

    /**
     * SPEC-ESTABLISHED-RECOVERY-001 R6: after this change an established link drop runs one silent
     * internal recovery before the single terminal callback. The observer is STILL notified — now
     * exactly once, and never via onError. (The coordinator episode runs on the Main-confined
     * sdkScope, which does not dispatch in a plain JVM test, so an Unconfined scope + fast clock are
     * injected to let the real episode settle.)
     */
    @Test
    fun establishedLinkDropStillNotifiesTheObserver() = runBlocking {
        val (manager, service) = setup { emptyList() }
        val events = Events()
        val server = Server()
        server.start()
        manager.setConnectionListener(events)
        installRecoveryTestScope(manager)
        try {
            withTimeout(5000) { server.ready.await() }
            manager.connect(config(server.port), events)
            withTimeout(5000) { events.completion.await() }
            server.connections.toList().forEach { it.closeConnection(1006, "test peer drop") }
            withTimeout(8000) {
                while (events.disconnected.isEmpty() && events.connected.size < 2) delay(10)
            }
            // The observer is notified exactly once by the recovery outcome; never via onError.
            assertTrue(events.errors.isEmpty())
            val terminalCount = events.disconnected.size + (events.connected.size - 1)
            assertEquals(1, terminalCount)
        } finally {
            manager.disconnect()
            service.release()
            server.stop(1000)
        }
    }

    @Test
    fun transactionFastRecoveryRetainsImmediateHandshakeFailurePath() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val (manager, service) = setup { entered.complete(Unit); awaitCancellation() }
        val events = Events()
        ConnectionManager::class.java.getDeclaredField("isFastRecovering").apply { isAccessible = true }.set(manager, true)
        try {
            manager.connect(config(unusedPort()), events)
            withTimeout(5000) { entered.await() }
            assertTrue("Existing fast recovery error path must remain visible", events.errors.isNotEmpty())
        } finally {
            manager.disconnect()
            service.release()
        }
    }

    @Test
    fun replacementFailureReleasesReconnectQueueWithOneErrorPerObserver() = runBlocking {
        val replacementPort = unusedPort()
        val (manager, service) = setup {
            listOf(ServiceInfo("Taplink-Server-1-TESTSERIAL", "_taplink._tcp", "127.0.0.1", replacementPort))
        }
        val shared = Events()
        val queued = Events()
        manager.setConnectionListener(shared)
        try {
            manager.connect(config(unusedPort()), shared)
            val reconnect = ConnectionManager::class.java.getDeclaredField("reconnectManager").apply { isAccessible = true }
                .get(manager) as ReconnectManager
            withTimeout(5000) {
                while (!reconnect.isReconnecting()) delay(10)
            }
            manager.connect(config(replacementPort), shared)
            manager.connect(config(replacementPort), queued)
            // The replacement uses the platform discovery adapter, which returns no services
            // in this fake Context. Its existing four discovery attempts take 1+2+4 seconds.
            withTimeout(12000) { shared.completion.await(); queued.completion.await() }
            assertEquals(listOf("241"), shared.errors)
            assertEquals(listOf("241"), queued.errors)
            assertTrue(shared.disconnected.isEmpty())
            assertTrue(queued.disconnected.isEmpty())
            assertFalse(reconnect.isReconnecting())
            assertFalse(manager.isConnecting())
        } finally {
            manager.disconnect()
            service.release()
        }
    }

    /** Wires the ConnectionManager's kernel status listener and drives a kernel status change,
     * exercising the real setupKernelStatusListener -> isSelfHealing -> connectionDisconnectedListener
     * path. Returns whether connectionDisconnectedListener fired for a CONNECTED -> DISCONNECTED drop
     * given the supplied fast-recovery flag. */
    private fun observeDisconnectNotification(manager: ConnectionManager, fastRecovering: Boolean): Boolean {
        val mgrClass = ConnectionManager::class.java
        // The internal cached status must be an active one for the drop to count as a real drop.
        mgrClass.getDeclaredField("connectionStatus").apply { isAccessible = true }
            .set(manager, ConnectionStatus.CONNECTED)
        mgrClass.getDeclaredField("isFastRecovering").apply { isAccessible = true }.set(manager, fastRecovering)
        var disconnectedNotified = false
        manager.setConnectionDisconnectedListener { disconnectedNotified = true }
        // Register the real kernel status listener (same call the connect flow makes).
        mgrClass.getDeclaredMethod("setupKernelStatusListener", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(manager, false)
        // Fire the kernel's status change listener directly with the mid-recovery DISCONNECTED.
        // statusChangeListener lives on BaseServiceKernel, several levels up the hierarchy.
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()!!
        var cls: Class<*>? = kernel.javaClass
        var listenerField: java.lang.reflect.Field? = null
        while (cls != null && listenerField == null) {
            listenerField = runCatching { cls!!.getDeclaredField("statusChangeListener") }.getOrNull()
            cls = cls.superclass
        }
        @Suppress("UNCHECKED_CAST")
        val kernelListener = listenerField!!.apply { isAccessible = true }
            .get(kernel) as (InnerConnectionStatus) -> Unit
        kernelListener.invoke(InnerConnectionStatus.DISCONNECTED)
        return disconnectedNotified
    }

    /** Forces the live kernel's reported connection status, driving ConnectionManager's
     * getActualConnectionStatus(). currentInnerConnectionStatus lives on BaseServiceKernel several
     * levels up the hierarchy. */
    private fun forceKernelStatus(kernel: LanClientKernel, status: InnerConnectionStatus) {
        var cls: Class<*>? = kernel.javaClass
        var field: java.lang.reflect.Field? = null
        while (cls != null && field == null) {
            field = runCatching { cls!!.getDeclaredField("currentInnerConnectionStatus") }.getOrNull()
            cls = cls.superclass
        }
        field!!.apply { isAccessible = true }.set(kernel, status)
    }

    /** Invokes the private analyzeAddressChange(newHost,newPort,oldHost,oldPort) and returns its
     * shouldReconnect decision. */
    private fun analyzeAddressChange(
        manager: ConnectionManager,
        newHost: String,
        newPort: Int,
        oldHost: String,
        oldPort: Int
    ): Boolean {
        val method = ConnectionManager::class.java.getDeclaredMethod(
            "analyzeAddressChange",
            String::class.java,
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType
        ).apply { isAccessible = true }
        val decision = method.invoke(manager, newHost, newPort, oldHost, oldPort)
        val field = decision.javaClass.getDeclaredField("shouldReconnect").apply { isAccessible = true }
        return field.getBoolean(decision)
    }

    /**
     * SPEC-LAN-RECOVERY-001 §AC1/AC3 (Fix A). After a peer-side network drop the kernel can retain a
     * stale CONNECTING while the old socket is dead. A same-address discovery in that transitional
     * state must be judged "reconnection needed" (shouldReconnect=true) so the attempt recovers,
     * instead of being wrongly treated as "already connected" (the pre-fix bug that let the handoff
     * be mislabelled fatal). WAIT_CONNECTING is covered as another transitional residue.
     */
    @Test
    fun staleTransitionalStateSameAddressIsJudgedAsReconnectNeeded() {
        val (manager, service) = setup { emptyList() }
        try {
            val kernel = service.getCurrentServiceKernel() as LanClientKernel
            listOf(InnerConnectionStatus.CONNECTING, InnerConnectionStatus.WAITING_CONNECT).forEach { stale ->
                forceKernelStatus(kernel, stale)
                assertTrue(
                    "Stale $stale on same address must be judged as reconnect-needed",
                    analyzeAddressChange(manager, "127.0.0.1", 8443, "127.0.0.1", 8443)
                )
            }
        } finally {
            manager.disconnect()
            service.release()
        }
    }

    /**
     * SPEC-LAN-RECOVERY-001 §4 (Fix A regression guard). When the link is GENUINELY established
     * (kernel CONNECTED) a same-address discovery must NOT trigger a reconnect, preventing thrashing a
     * live connection. Both decision guards (analyzeAddressChange and analyzeConnectionState) must
     * agree on "no reconnection needed" for a live same-address link.
     */
    @Test
    fun genuinelyConnectedSameAddressDoesNotReconnect() {
        val (manager, service) = setup { emptyList() }
        try {
            val kernel = service.getCurrentServiceKernel() as LanClientKernel
            forceKernelStatus(kernel, InnerConnectionStatus.CONNECTED)
            assertFalse(
                "A genuinely CONNECTED same-address link must not reconnect",
                analyzeAddressChange(manager, "127.0.0.1", 8443, "127.0.0.1", 8443)
            )
            // Second guard must agree once a replacement config for the same address is recorded.
            val reconnect = ConnectionManager::class.java.getDeclaredField("reconnectManager")
                .apply { isAccessible = true }.get(manager) as ReconnectManager
            ReconnectManager::class.java.getDeclaredField("lastConnectionConfig")
                .apply { isAccessible = true }.set(reconnect, config(8443))
            val stateMethod = ConnectionManager::class.java.getDeclaredMethod(
                "analyzeConnectionState", String::class.java, Int::class.javaPrimitiveType
            ).apply { isAccessible = true }
            val stateDecision = stateMethod.invoke(manager, "127.0.0.1", 8443)
            val shouldReconnect = stateDecision.javaClass.getDeclaredField("shouldReconnect")
                .apply { isAccessible = true }.getBoolean(stateDecision)
            assertFalse("analyzeConnectionState must also suppress reconnect for a live same-address link", shouldReconnect)
        } finally {
            manager.disconnect()
            service.release()
        }
    }

    @Test
    fun fastRecoveryWindowSuppressesFalseDisconnectNotification() {
        val (manager, service) = setup { emptyList() }
        try {
            // isFastRecovering=true and reconnectManager unused (isReconnecting()==false): the
            // transient mid-recovery DISCONNECTED must be treated as self-healing and NOT surface
            // onDisconnected (which would wrongly clear the processed-terminal-event dedup set).
            assertFalse(
                "Fast-recovery window must suppress the false onDisconnected",
                observeDisconnectNotification(manager, fastRecovering = true)
            )
        } finally {
            service.release()
        }
    }

    @Test
    fun genuineDropOutsideFastRecoveryStillNotifiesDisconnect() {
        val (manager, service) = setup { emptyList() }
        try {
            // Control case: without fast recovery (and no reconnect in progress) a real
            // CONNECTED -> DISCONNECTED drop must still notify, proving the suppression is scoped
            // to the fast-recovery window and did not silence genuine disconnects.
            assertTrue(
                "A genuine drop outside the fast-recovery window must still notify",
                observeDisconnectNotification(manager, fastRecovering = false)
            )
        } finally {
            service.release()
        }
    }

}
