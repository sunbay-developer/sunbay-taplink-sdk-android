package com.sunmi.tapro.taplink.communication.lan.connection

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocketFactory

/**
 * WebSocket connection manager
 *
 * Responsibilities:
 * - Manage WebSocket client connections
 * - Handle connection status changes
 * - Handle message sending and receiving
 * - Integrate network connectivity checking
 *
 * @author Tapro Team
 * @since 2025-01-01
 */
class ConnectionManager {

    companion object {
        private const val TAG = "ConnectionManager"
        // wss connections need TLS handshake, give more time
        // ws connections don't need TLS, time can be shorter
        private const val CONNECTION_TIMEOUT_MS_WS = 15_000L  // ws:// 15 seconds
        private const val CONNECTION_TIMEOUT_MS_WSS = 45_000L  // wss:// 45 seconds (TLS handshake needs more time)

        /**
         * Interval, in seconds, for the WebSocket protocol-level ping/pong keepalive.
         *
         * This is transport-level liveness detection built into RFC 6455, not an application
         * heartbeat: no business message is exchanged and Tapro answers pings automatically in
         * the WebSocket layer. It exists purely to detect a half-open socket — a peer that
         * disappeared without a close handshake — which otherwise stays "connected" forever and
         * silently swallows every request.
         *
         * The value is the maximum time a single ping may go un-ponged before the link is
         * declared lost. It must stay comfortably above the worst-case pong stall that a healthy
         * peer can exhibit, otherwise a live transaction is torn down by mistake. During a
         * transaction Tapro is foreground-busy driving the card reader, PIN pad and online
         * authorization; its automatic pong can legitimately be delayed for several seconds under
         * load or GC. A value that is too tight (e.g. 10s) drops the socket mid card-read/PIN and
         * the pending result can never be delivered — a dropped in-flight payment is far worse
         * than a dead link surfacing a little later. 60s (the library default) tolerates those
         * transient stalls while still detecting a genuinely vanished peer within a minute; the
         * link itself survives arbitrarily long idle periods because pings/pongs keep flowing.
         */
        private const val CONNECTION_LOST_TIMEOUT_SECONDS = 60
    }
    
    /**
     * Get connection timeout based on URI scheme
     */
    private fun getConnectionTimeout(uri: URI): Long {
        return if (uri.scheme == "wss") {
            CONNECTION_TIMEOUT_MS_WSS
        } else {
            CONNECTION_TIMEOUT_MS_WS
        }
    }
    
    /**
     * Create SSL Socket Factory that accepts self-signed certificates
     * 
     * Note: This is only for self-signed certificates in LAN environments, production should use strict certificate validation
     */
    private fun createTrustAllSSLSocketFactory(): SSLSocketFactory? {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            sslContext.socketFactory
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to create SSL socket factory: ${e.message}")
            null
        }
    }

    @Volatile
    private var client: InternalWebSocketClient? = null
    private var currentUri: URI? = null
    private var messageListener: MessageListener? = null
    private var connectionListener: ConnectionListener? = null

    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    suspend fun connect(uri: URI): ConnectionResult = connect(uri, deferHandshakeFailureCallbacks = false)

    internal suspend fun connect(uri: URI, deferHandshakeFailureCallbacks: Boolean): ConnectionResult {
        return withContext(Dispatchers.IO) {
            var attempt: InternalWebSocketClient? = null
            try {
                ensureActive()
                LogUtil.d(TAG, "Attempting to connect to: $uri")

                // If already connected to same URI, check actual connection status
                if (isConnected.get() && currentUri == uri) {
                    // Check WebSocket actual status
                    if (client?.isOpen == true) {
                        LogUtil.d(TAG, "Already connected to $uri (WebSocket is open)")
                        return@withContext ConnectionResult.Success
                    } else {
                        // WebSocket is closed but flag not updated, need to cleanup and reconnect
                        LogUtil.w(TAG, "URI matches but WebSocket is closed, cleaning up and reconnecting...")
                        cleanup()
                        // Continue with connection logic
                    }
                } else if (isConnected.get() && currentUri != uri) {
                    // Connecting to different URI, need to disconnect existing connection first
                    LogUtil.d(TAG, "Disconnecting existing connection to different URI: $currentUri -> $uri")
                    disconnect()
                }

                // Create new WebSocket client
                val newClient = InternalWebSocketClient(uri, deferHandshakeFailureCallbacks)
                attempt = newClient
                synchronized(this@ConnectionManager) {
                    client = newClient
                    currentUri = uri
                }

                // Enable protocol-level ping/pong so a vanished peer is detected promptly
                // instead of leaving a half-open socket that reports CONNECTED indefinitely.
                newClient.connectionLostTimeout = CONNECTION_LOST_TIMEOUT_SECONDS

                // If wss connection, configure SSL Socket Factory (accept self-signed certificates)
                if (uri.scheme == "wss") {
                    val sslSocketFactory = createTrustAllSSLSocketFactory()
                    if (sslSocketFactory != null) {
                        newClient.setSocketFactory(sslSocketFactory)
                        LogUtil.d(TAG, "Configured SSL socket factory for wss connection")
                    } else {
                        LogUtil.w(TAG, "Failed to create SSL socket factory, wss connection may fail")
                    }
                }

                // TLS setup may have overlapped cancellation; do not open a cancelled socket.
                ensureActive()
                newClient.connect()

                // Use different timeout based on connection type
                val timeoutMs = getConnectionTimeout(uri)
                LogUtil.d(TAG, "Using connection timeout: ${timeoutMs}ms for ${uri.scheme}://${uri.host}:${uri.port}")

                // Wait for connection result
                val connectionResult = withTimeoutOrNull(timeoutMs) {
                    while (!newClient.isOpen) {
                        delay(100)

                        // Check if connection failed
                        if (newClient.isClosed || client !== newClient) {
                            throw Exception("Connection closed during handshake")
                        }
                    }
                    true
                }

                if (connectionResult == true && client === newClient && isConnected.get()) {
                    LogUtil.d(TAG, "Successfully connected to $uri")
                    ConnectionResult.Success
                } else {
                    LogUtil.e(TAG, "Connection timeout or failed for $uri")
                    cleanup(newClient)
                    ConnectionResult.Failure("Connection timeout")
                }

            } catch (e: CancellationException) {
                attempt?.let { cleanup(it) }
                throw e
            } catch (e: Exception) {
                attempt?.let { cleanup(it) }
                ensureActive()
                LogUtil.e(TAG, "Failed to connect to $uri: ${e.message}")
                ConnectionResult.Failure(
                    "Connection failed: ${e.message}", e
                )
            }
        }
    }

    fun disconnect() {
        LogUtil.d(TAG, "Disconnecting WebSocket connection")
        val oldClient = retireClient()
        try {
            oldClient?.close(1000, "Normal closure")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error closing WebSocket client: ${e.message}")
        }
    }

    /**
     * Tear the connection down immediately without attempting a closing handshake.
     *
     * [disconnect] sends a close frame and waits for the peer to echo it back. When the peer has
     * vanished (process restarted, cable pulled, host rebooted) that reply never arrives and the
     * socket lingers in a half-open state. This variant drops the connection locally and reports
     * it as an abnormal close (1006) so the disconnect surfaces to listeners right away.
     *
     * @param reason Human readable reason, forwarded in the close event.
     */
    fun invalidate(reason: String) {
        LogUtil.w(TAG, "Invalidating WebSocket connection: $reason")
        val oldClient = retireClient() ?: return
        // Retiring the socket filters its asynchronous close; report this intentional
        // invalidation once so peer-restart recovery still follows the normal drop path.
        connectionListener?.onDisconnected(CloseFrame.ABNORMAL_CLOSE, reason, false)
        try {
            oldClient.closeConnection(CloseFrame.ABNORMAL_CLOSE, reason)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error invalidating WebSocket client: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return isConnected.get() && client?.isOpen == true
    }

    fun send(data: ByteArray): Boolean {
        return try {
            if (!isConnected()) {
                LogUtil.w(TAG, "Cannot send data: not connected")
                return false
            }

            // Check WebSocket connection actual status
            val client = this.client
            if (client == null || client.isClosed || client.isClosing) {
                LogUtil.w(TAG, "Cannot send data: WebSocket client is closed or closing")
                isConnected.set(false)
                return false
            }

            client.send(data)
            LogUtil.d(TAG, "Binary data sent: ${data.size} bytes")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send binary data: ${e.message}")
            // Update connection status when send fails
            isConnected.set(false)
            false
        }
    }

    fun send(message: String): Boolean {
        return try {
            if (!isConnected()) {
                LogUtil.w(TAG, "Cannot send message: not connected")
                return false
            }

            // Check WebSocket connection actual status
            val client = this.client
            if (client == null || client.isClosed || client.isClosing) {
                LogUtil.w(TAG, "Cannot send message: WebSocket client is closed or closing")
                isConnected.set(false)
                return false
            }

            client.send(message)
            LogUtil.d(TAG, "Text message sent: $message")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send text message: ${e.message}")
            // Update connection status when send fails
            isConnected.set(false)
            false
        }
    }
    
    fun setMessageListener(listener: MessageListener?) {
        this.messageListener = listener
        LogUtil.d(TAG, "Message listener ${if (listener != null) "set" else "removed"}")
    }

    fun setConnectionListener(listener: ConnectionListener?) {
        this.connectionListener = listener
        LogUtil.d(TAG, "Connection listener ${if (listener != null) "set" else "removed"}")
    }

    fun getCurrentUri(): URI? {
        return currentUri
    }

    /**
     * Clean up resources
     */
    private fun retireClient(expected: InternalWebSocketClient? = null): InternalWebSocketClient? =
        synchronized(this) {
            if (expected != null && client !== expected) return@synchronized null
            val oldClient = client
            isConnected.set(false)
            client = null
            currentUri = null
            oldClient
        }

    private fun cleanup(expected: InternalWebSocketClient? = null) {
        val oldClient = retireClient(expected)
        // A timed-out handshake may still be opening. Close the retired socket so it
        // cannot revive the cancelled attempt or leak its WebSocket worker threads.
        oldClient?.closeConnection(CloseFrame.NORMAL, "Connection attempt finished")
        LogUtil.d(TAG, "Resources cleaned up")
    }

    /** Each socket owns its handshake policy; retired sockets cannot mutate the next attempt. */
    private inner class InternalWebSocketClient(
        serverUri: URI,
        private val deferHandshakeFailureCallbacks: Boolean
    ) : WebSocketClient(serverUri) {
        private var opened = false

        override fun onOpen(handshakedata: ServerHandshake) = synchronized(this@ConnectionManager) {
            if (client !== this) {
                closeConnection(CloseFrame.NORMAL, "Obsolete connection attempt")
                return@synchronized
            }
            LogUtil.d(TAG, "WebSocket connection opened: $uri")
            opened = true
            isConnected.set(true)
            connectionListener?.onConnected()
            Unit
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) = synchronized(this@ConnectionManager) {
            if (client !== this) return@synchronized
            LogUtil.d(TAG, "WebSocket connection closed: code=$code, reason=$reason, remote=$remote")
            isConnected.set(false)
            if (!deferHandshakeFailureCallbacks || opened) {
                connectionListener?.onDisconnected(code, reason, remote)
            }
            Unit
        }

        override fun onMessage(message: String) = synchronized(this@ConnectionManager) {
            if (client !== this) return@synchronized
            LogUtil.d(TAG, "Text message received: $message")
            messageListener?.onTextMessage(message)
            Unit
        }

        override fun onMessage(message: ByteBuffer) = synchronized(this@ConnectionManager) {
            if (client !== this) return@synchronized
            val data = ByteArray(message.remaining())
            message.get(data)
            LogUtil.d(TAG, "Binary message received: ${data.size} bytes")
            messageListener?.onBinaryMessage(data)
            Unit
        }

        override fun onError(ex: Exception) = synchronized(this@ConnectionManager) {
            if (client !== this) return@synchronized
            LogUtil.e(TAG, "WebSocket error: ${ex.message}")
            isConnected.set(false)
            if (!deferHandshakeFailureCallbacks || opened) {
                connectionListener?.onError(ex)
            }
            Unit
        }
    }

    /**
     * Connection result
     */
    sealed class ConnectionResult {
        object Success : ConnectionResult()
        data class Failure(val error: String, val exception: Exception? = null) : ConnectionResult()
    }

    /**
     * Message listener
     */
    interface MessageListener {
        /**
         * Text message received
         *
         * @param message Text message
         */
        fun onTextMessage(message: String)

        /**
         * Binary message received
         *
         * @param data Binary data
         */
        fun onBinaryMessage(data: ByteArray)
    }

    /**
     * Connection status listener
     */
    interface ConnectionListener {
        /**
         * Connection established successfully
         */
        fun onConnected()

        /**
         * Connection disconnected
         *
         * @param code Disconnect code
         * @param reason Disconnect reason
         * @param remote Whether disconnected remotely
         */
        fun onDisconnected(code: Int, reason: String, remote: Boolean)

        /**
         * Connection error occurred
         *
         * @param exception Error exception
         */
        fun onError(exception: Exception)
    }
}