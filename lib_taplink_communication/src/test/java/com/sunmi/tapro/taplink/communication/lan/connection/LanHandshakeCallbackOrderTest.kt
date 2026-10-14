package com.sunmi.tapro.taplink.communication.lan.connection

import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.HandshakeImpl1Server
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/** SPEC-LAN-RECOVERY-001 §AC2/AC5/AC6: drive both asynchronous callback orders on a held handshake. */
class LanHandshakeCallbackOrderTest {
    private class Events : ConnectionManager.ConnectionListener {
        val events = CopyOnWriteArrayList<String>()
        override fun onConnected() { events.add("connected") }
        override fun onDisconnected(code: Int, reason: String, remote: Boolean) { events.add("disconnected") }
        override fun onError(exception: Exception) { events.add("error") }
    }

    private fun socket(manager: ConnectionManager): WebSocketClient =
        ConnectionManager::class.java.getDeclaredField("client").apply { isAccessible = true }.get(manager) as WebSocketClient

    @Test
    fun bothHandshakeErrorCloseOrdersAreDeferred() = runBlocking {
        for (errorFirst in listOf(true, false)) {
            val manager = ConnectionManager()
            val listener = Events()
            manager.setConnectionListener(listener)
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val connecting = async(Dispatchers.IO) {
                    manager.connect(URI("ws://127.0.0.1:${server.localPort}"), true)
                }
                withContext(Dispatchers.IO) { server.accept() }.use {
                    val client = socket(manager)
                    if (errorFirst) {
                        client.onError(Exception("refused handshake"))
                        client.onClose(-1, "refused handshake", false)
                    } else {
                        client.onClose(-1, "refused handshake", false)
                        client.onError(Exception("refused handshake"))
                    }
                    assertTrue(listener.events.isEmpty())
                    assertFalse(manager.isConnected())
                    connecting.cancelAndJoin()
                }
            }
            manager.disconnect()
        }
    }

    @Test
    fun retiredSocketCannotEmitCallbacksIntoItsSuccessor() = runBlocking {
        val manager = ConnectionManager()
        val oldEvents = Events()
        manager.setConnectionListener(oldEvents)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { oldServer ->
            val oldAttempt = async(Dispatchers.IO) { manager.connect(URI("ws://127.0.0.1:${oldServer.localPort}"), true) }
            withContext(Dispatchers.IO) { oldServer.accept() }.use {
                val oldSocket = socket(manager)
                oldAttempt.cancelAndJoin()
                val successorEvents = Events()
                manager.setConnectionListener(successorEvents)
                ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { successorServer ->
                    val next = async(Dispatchers.IO) { manager.connect(URI("ws://127.0.0.1:${successorServer.localPort}"), true) }
                    withContext(Dispatchers.IO) { successorServer.accept() }.use {
                        val nextUri = manager.getCurrentUri()
                        oldSocket.onError(Exception("late failure"))
                        oldSocket.onClose(-1, "late close", false)
                        oldSocket.onOpen(HandshakeImpl1Server())
                        assertTrue(oldEvents.events.isEmpty())
                        assertTrue(successorEvents.events.isEmpty())
                        assertEquals(nextUri, manager.getCurrentUri())
                        assertFalse(manager.isConnected())
                        next.cancelAndJoin()
                    }
                }
            }
        }
        manager.disconnect()
    }

    @Test
    fun deferredHandshakePolicyDoesNotHideEstablishedErrorsOrPeerInvalidation() = runBlocking {
        val manager = ConnectionManager()
        val events = Events()
        manager.setConnectionListener(events)
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val attempt = async(Dispatchers.IO) { manager.connect(URI("ws://127.0.0.1:${server.localPort}"), true) }
            withContext(Dispatchers.IO) { server.accept() }.use {
                val client = socket(manager)
                client.onOpen(HandshakeImpl1Server())
                client.onError(Exception("established link failure"))
                assertEquals(listOf("connected", "error"), events.events)
                manager.invalidate("peer restarted")
                client.onClose(1006, "late invalidation close", false)
                assertEquals(listOf("connected", "error", "disconnected"), events.events)
                attempt.cancelAndJoin()
            }
        }
        manager.disconnect()
    }
}
