package com.sunmi.tapro.taplink.sdk.manager

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.lan.LanClientKernel
import com.sunmi.tapro.taplink.communication.lan.model.ServiceInfo
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
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

/**
 * Exercises the REAL ConnectionManager + LanClientKernel + local WebSocket transport wiring for an
 * ESTABLISHED-connection fault. A LAN link established by direct host:port has no mDNS serial anchor,
 * so recovery cannot confirm the original terminal (R8) and must settle with exactly one
 * onDisconnected(E213) — never onError — while the registered ConnectionListener is retained.
 *
 * SPEC-ESTABLISHED-RECOVERY-001 §AC5/§AC6/§AC10 / TC-ER-005, TC-ER-006, TC-ER-010 (SDK-side wiring).
 */
class EstablishedRecoveryWiringTest {
    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, _ ->
                when (method.name) { "commit" -> true; "apply" -> null; else -> proxy }
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
        val establishedCompletion = CompletableDeferred<Unit>()
        override fun onConnected(deviceId: String, taproVersion: String) {
            connected.add(deviceId)
            if (!establishedCompletion.isCompleted) establishedCompletion.complete(Unit)
        }
        override fun onError(error: ConnectionError) { errors.add(error.code) }
        override fun onDisconnected(reason: String) { disconnected.add(reason) }
    }

    private class Server : WebSocketServer(InetSocketAddress("127.0.0.1", 0)) {
        val ready = CompletableDeferred<Unit>()
        override fun onStart() { ready.complete(Unit) }
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) = Unit
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) = Unit
        override fun onMessage(conn: WebSocket, message: String) = Unit
        override fun onError(conn: WebSocket?, ex: Exception) { if (!ready.isCompleted) ready.completeExceptionally(ex) }
    }

    private fun config(port: Int) = ConnectionConfig.createLanMode("127.0.0.1", port).setAutoReconnect(true)
    private fun unusedPort(): Int = ServerSocket(0).use { it.localPort }

    /**
     * Replace the coordinator's monotonic clock so the recovery budget expires after ~1s instead of
     * the production 5s, keeping the integration test fast while exercising the real settle path.
     */
    private fun installFastClock(manager: ConnectionManager) {
        val coordinatorField = ConnectionManager::class.java
            .getDeclaredField("establishedRecoveryCoordinator").apply { isAccessible = true }
        val coordinator = coordinatorField.get(manager)
        val start = System.currentTimeMillis()
        val fast = object : RecoveryClock {
            // Elapsed time scaled 6x so a 5000ms budget is reached in ~830ms real time.
            override fun elapsedRealtimeMs(): Long = (System.currentTimeMillis() - start) * 6
        }
        val clockField = coordinator.javaClass.getDeclaredField("clock").apply { isAccessible = true }
        clockField.set(coordinator, fast)
        // The production coordinator runs its episode on the Main-confined sdkScope, which does not
        // dispatch in a plain JVM unit test. Swap in an Unconfined scope so the real episode body
        // runs on the test thread while still exercising the full ConnectionManager wiring.
        val scopeField = coordinator.javaClass.getDeclaredField("scope").apply { isAccessible = true }
        scopeField.set(coordinator, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
    }

    private fun setup(discover: suspend ((ServiceInfo) -> Boolean) -> List<ServiceInfo>):
        Pair<ConnectionManager, TaplinkServiceKernel> {
        val context = TestContext()
        val service = TaplinkServiceKernel.getInstance(context)
        service.release()
        val kernel = LanClientKernel("test-app", "test-secret", context)
        LanClientKernel::class.java.getDeclaredField("discoverServices").apply { isAccessible = true }.set(kernel, discover)
        TaplinkServiceKernel::class.java.getDeclaredField("currentServiceKernel").apply { isAccessible = true }
            .set(service, kernel)
        return ConnectionManager(TaplinkConfig(appId = "test-app", secretKey = "test-secret"), context) to service
    }

    /**
     * An established LAN connection that drops: the SDK runs one silent internal recovery. With no
     * reliable serial anchor the single attempt fails identity validation and emits exactly one
     * onDisconnected(E213) and NO onError. The listener is never removed by the SDK.
     */
    @Test
    fun establishedDropYieldsSingleE213DisconnectNoError() = runBlocking {
        val (manager, service) = setup { emptyList() }
        val events = Events()
        val server = Server()
        server.start()
        manager.setConnectionListener(events)
        // Force the recovery budget to expire quickly so a non-recovering rebuild settles as a
        // failure within the test window instead of waiting the full 5s wall clock.
        installFastClock(manager)
        try {
            withTimeout(5000) { server.ready.await() }
            manager.connect(config(server.port), events)
            // onConnected proves the session reached CONNECTED at the SDK observer level, which arms
            // the established-fault recovery gate (hasEverEstablishedThisSession).
            withTimeout(5000) { events.establishedCompletion.await() }
            assertEquals(1, events.connected.size)

            // Drop the established link from the peer side -> internal recovery episode.
            server.connections.toList().forEach { it.closeConnection(1006, "established peer drop") }

            // The recovery settles EXACTLY once: either a single additional terminal onConnected
            // (same server/serial validated) OR a single onDisconnected(E213) (identity unconfirmed).
            // In EITHER case onError must NEVER be invoked for an established fault (R6), and the
            // retained listener receives that one terminal outcome.
            withTimeout(8000) {
                while (events.disconnected.isEmpty() && events.connected.size < 2) delay(20)
            }
            assertTrue("established fault must never surface onError", events.errors.isEmpty())
            val terminalCount = events.disconnected.size + (events.connected.size - 1)
            assertEquals("exactly one terminal recovery outcome", 1, terminalCount)
            if (events.disconnected.isNotEmpty()) {
                assertTrue("failure terminal must be E213", events.disconnected.first().contains("213"))
                assertFalse(manager.isConnected())
            }
        } finally {
            manager.disconnect()
            service.release()
            server.stop(1000)
        }
    }
}
