package com.sunmi.tapro.taplink.communication.lan.connection

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
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
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ConnectionManager {

    companion object {
        private const val TAG = "ConnectionManager"
        // wss connections need TLS handshake, give more time
        // ws connections don't need TLS, time can be shorter
        private const val CONNECTION_TIMEOUT_MS_WS = 15_000L  // ws:// 15 seconds
        private const val CONNECTION_TIMEOUT_MS_WSS = 45_000L  // wss:// 45 seconds (TLS handshake needs more time)
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

    private var client: InternalWebSocketClient? = null
    private var currentUri: URI? = null
    private var messageListener: MessageListener? = null
    private var connectionListener: ConnectionListener? = null

    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Network connectivity checker
    private val connectivityChecker = NetworkConnectivityChecker()

    suspend fun connect(uri: URI): ConnectionResult {
        return withContext(Dispatchers.IO) {
            try {
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
                client = InternalWebSocketClient(uri)
                currentUri = uri

                // If wss connection, configure SSL Socket Factory (accept self-signed certificates)
                if (uri.scheme == "wss") {
                    val sslSocketFactory = createTrustAllSSLSocketFactory()
                    if (sslSocketFactory != null) {
                        client?.setSocketFactory(sslSocketFactory)
                        LogUtil.d(TAG, "Configured SSL socket factory for wss connection")
                    } else {
                        LogUtil.w(TAG, "Failed to create SSL socket factory, wss connection may fail")
                    }
                }

                // Connect
                client?.connect()

                // Use different timeout based on connection type
                val timeoutMs = getConnectionTimeout(uri)
                LogUtil.d(TAG, "Using connection timeout: ${timeoutMs}ms for ${uri.scheme}://${uri.host}:${uri.port}")

                // Wait for connection result
                val connectionResult = withTimeoutOrNull(timeoutMs) {
                    while (!isConnected.get() && client?.isOpen != true) {
                        delay(100)

                        // Check if connection failed
                        client?.let { c ->
                            if (c.isClosed) {
                                throw Exception("Connection closed during handshake")
                            }
                        }
                    }
                    true
                }

                if (connectionResult == true && isConnected.get()) {
                    LogUtil.d(TAG, "Successfully connected to $uri")
                    ConnectionResult.Success
                } else {
                    LogUtil.e(TAG, "Connection timeout or failed for $uri")
                    cleanup()
                    ConnectionResult.Failure("Connection timeout")
                }

            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect to $uri: ${e.message}")
                cleanup()
                ConnectionResult.Failure(
                    "Connection failed: ${e.message}", e
                )
            }
        }
    }

    fun disconnect() {
        LogUtil.d(TAG, "Disconnecting WebSocket connection")
        try {
            client?.close(1000, "Normal closure")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error closing WebSocket client: ${e.message}")
        }

        cleanup()
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
    
    /**
     * Simplified heartbeat send method
     * 
     * Removes additional network connectivity checks to reduce send delay
     * Network issues will be detected through WebSocket's onError/onClose callbacks
     * 
     * @param message Heartbeat message
     * @return true if sent successfully, false otherwise
     */
    suspend fun sendHeartbeat(message: String): Boolean {
        return try {
            val client = this.client
            if (client == null || client.isClosed || client.isClosing) {
                LogUtil.w(TAG, "Cannot send heartbeat: WebSocket not available")
                isConnected.set(false)
                return false
            }

            // Send heartbeat directly, no additional checks
            client.send(message)
            LogUtil.d(TAG, "Heartbeat sent: $message")
            true
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send heartbeat: ${e.message}")
            isConnected.set(false)
            false
        }
    }
    
    /**
     * Send heartbeat message (with network connectivity check)
     * 
     * Note: Network connectivity check only uses ping, does not create TCP connection,
     * this avoids interfering with existing WebSocket connection (especially when server only supports single client connection)
     * 
     * @param message Heartbeat message
     * @return true if sent successfully, false otherwise
     */
    suspend fun sendHeartbeatWithConnectivityCheck(message: String): Boolean {
        return try {
            if (!isConnected()) {
                LogUtil.w(TAG, "Cannot send heartbeat: not connected")
                return false
            }

            // Check WebSocket connection actual status
            val client = this.client
            if (client == null || client.isClosed || client.isClosing) {
                LogUtil.w(TAG, "Cannot send heartbeat: WebSocket client is closed or closing")
                isConnected.set(false)
                return false
            }

            // Check network connectivity (only additional check for heartbeat)
            val uri = currentUri
            if (uri != null) {
                val isReachable = connectivityChecker.isHostReachable(
                    host = uri.host,
                    port = uri.port,
                    timeoutMs = 2000 // 2 second timeout
                )
                
                if (!isReachable) {
                    LogUtil.w(TAG, "Cannot send heartbeat: host ${uri.host}:${uri.port} is not reachable")
                    // When network is unreachable, update connection status but don't immediately disconnect WebSocket
                    // Let WebSocket detect network issues itself and trigger onError or onClose
                    return false
                }
            }

            // Send heartbeat message
            client.send(message)
            LogUtil.d(TAG, "Heartbeat message sent with connectivity check: $message")
            true
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send heartbeat message: ${e.message}")
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
    private fun cleanup() {
        isConnected.set(false)
        client = null
        currentUri = null
        LogUtil.d(TAG, "Resources cleaned up")
    }

    /**
     * Internal WebSocket client implementation
     */
    private inner class InternalWebSocketClient(serverUri: URI) : WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake) {
            LogUtil.d(TAG, "WebSocket connection opened: $uri")
            isConnected.set(true)
            connectionListener?.onConnected()
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            LogUtil.d(TAG, "WebSocket connection closed: code=$code, reason=$reason, remote=$remote")
            isConnected.set(false)
            connectionListener?.onDisconnected(code, reason, remote)
        }

        override fun onMessage(message: String) {
            LogUtil.d(TAG, "Text message received: $message")
            messageListener?.onTextMessage(message)
        }

        override fun onMessage(message: ByteBuffer) {
            val data = ByteArray(message.remaining())
            message.get(data)
            LogUtil.d(TAG, "Binary message received: ${data.size} bytes")
            messageListener?.onBinaryMessage(data)
        }

        override fun onError(ex: Exception) {
            LogUtil.e(TAG, "WebSocket error: ${ex.message}")
            isConnected.set(false)
            connectionListener?.onError(ex)
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