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
 * WebSocket连接管理器
 *
 * 职责：
 * - 管理WebSocket客户端连接
 * - 处理连接状态变化
 * - 处理消息发送和接收
 * - 集成网络连通性检查
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ConnectionManager {

    companion object {
        private const val TAG = "ConnectionManager"
        // wss 连接需要 TLS 握手，给更多时间
        // ws 连接不需要 TLS，时间可以短一些
        private const val CONNECTION_TIMEOUT_MS_WS = 15_000L  // ws:// 15秒
        private const val CONNECTION_TIMEOUT_MS_WSS = 45_000L  // wss:// 45秒（TLS握手需要更多时间）
    }
    
    /**
     * 根据 URI scheme 获取连接超时时间
     */
    private fun getConnectionTimeout(uri: URI): Long {
        return if (uri.scheme == "wss") {
            CONNECTION_TIMEOUT_MS_WSS
        } else {
            CONNECTION_TIMEOUT_MS_WS
        }
    }
    
    /**
     * 创建接受自签名证书的 SSL Socket Factory
     * 
     * 注意：这仅用于 LAN 环境下的自签名证书，生产环境应使用严格的证书验证
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
    
    // 网络连通性检查器
    private val connectivityChecker = NetworkConnectivityChecker()

    suspend fun connect(uri: URI): ConnectionResult {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Attempting to connect to: $uri")

                // 如果已连接到相同URI，检查实际连接状态
                if (isConnected.get() && currentUri == uri) {
                    // 检查 WebSocket 实际状态
                    if (client?.isOpen == true) {
                        LogUtil.d(TAG, "Already connected to $uri (WebSocket is open)")
                        return@withContext ConnectionResult.Success
                    } else {
                        // WebSocket 已关闭但标志位未更新，需要清理并重新连接
                        LogUtil.w(TAG, "URI matches but WebSocket is closed, cleaning up and reconnecting...")
                        cleanup()
                        // 继续执行连接逻辑
                    }
                } else if (isConnected.get() && currentUri != uri) {
                    // 连接到不同的 URI，需要先断开现有连接
                    LogUtil.d(TAG, "Disconnecting existing connection to different URI: $currentUri -> $uri")
                    disconnect()
                }

                // 创建新的WebSocket客户端
                client = InternalWebSocketClient(uri)
                currentUri = uri

                // 如果是 wss 连接，配置 SSL Socket Factory（接受自签名证书）
                if (uri.scheme == "wss") {
                    val sslSocketFactory = createTrustAllSSLSocketFactory()
                    if (sslSocketFactory != null) {
                        client?.setSocketFactory(sslSocketFactory)
                        LogUtil.d(TAG, "Configured SSL socket factory for wss connection")
                    } else {
                        LogUtil.w(TAG, "Failed to create SSL socket factory, wss connection may fail")
                    }
                }

                // 连接
                client?.connect()

                // 根据连接类型使用不同的超时时间
                val timeoutMs = getConnectionTimeout(uri)
                LogUtil.d(TAG, "Using connection timeout: ${timeoutMs}ms for ${uri.scheme}://${uri.host}:${uri.port}")

                // 等待连接结果
                val connectionResult = withTimeoutOrNull(timeoutMs) {
                    while (!isConnected.get() && client?.isOpen != true) {
                        delay(100)

                        // 检查是否连接失败
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

            // 检查WebSocket连接的实际状态
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
            // 发送失败时更新连接状态
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

            // 检查WebSocket连接的实际状态
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
            // 发送失败时更新连接状态
            isConnected.set(false)
            false
        }
    }
    
    /**
     * 简化的心跳发送方法
     * 
     * 移除额外的网络连通性检查，减少发送延迟
     * 网络问题会通过WebSocket的onError/onClose回调检测到
     * 
     * @param message 心跳消息
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

            // 直接发送心跳，不进行额外检查
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
     * 发送心跳消息（带网络连通性检查）
     * 
     * 注意：网络连通性检查仅使用ping，不会创建TCP连接，
     * 这样可以避免干扰现有的WebSocket连接（特别是当服务器只支持单客户端连接时）
     * 
     * @param message 心跳消息
     * @return true if sent successfully, false otherwise
     */
    suspend fun sendHeartbeatWithConnectivityCheck(message: String): Boolean {
        return try {
            if (!isConnected()) {
                LogUtil.w(TAG, "Cannot send heartbeat: not connected")
                return false
            }

            // 检查WebSocket连接的实际状态
            val client = this.client
            if (client == null || client.isClosed || client.isClosing) {
                LogUtil.w(TAG, "Cannot send heartbeat: WebSocket client is closed or closing")
                isConnected.set(false)
                return false
            }

            // 检查网络连通性（仅对心跳进行额外检查）
            val uri = currentUri
            if (uri != null) {
                val isReachable = connectivityChecker.isHostReachable(
                    host = uri.host,
                    port = uri.port,
                    timeoutMs = 2000 // 2秒超时
                )
                
                if (!isReachable) {
                    LogUtil.w(TAG, "Cannot send heartbeat: host ${uri.host}:${uri.port} is not reachable")
                    // 网络不通时，更新连接状态但不立即断开WebSocket
                    // 让WebSocket自己检测到网络问题并触发onError或onClose
                    return false
                }
            }

            // 发送心跳消息
            client.send(message)
            LogUtil.d(TAG, "Heartbeat message sent with connectivity check: $message")
            true
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send heartbeat message: ${e.message}")
            // 发送失败时更新连接状态
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
     * 清理资源
     */
    private fun cleanup() {
        isConnected.set(false)
        client = null
        currentUri = null
        LogUtil.d(TAG, "Resources cleaned up")
    }

    /**
     * 内部WebSocket客户端实现
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
     * 连接结果
     */
    sealed class ConnectionResult {
        object Success : ConnectionResult()
        data class Failure(val error: String, val exception: Exception? = null) : ConnectionResult()
    }

    /**
     * 消息监听器
     */
    interface MessageListener {
        /**
         * 接收到文本消息
         *
         * @param message 文本消息
         */
        fun onTextMessage(message: String)

        /**
         * 接收到二进制消息
         *
         * @param data 二进制数据
         */
        fun onBinaryMessage(data: ByteArray)
    }

    /**
     * 连接状态监听器
     */
    interface ConnectionListener {
        /**
         * 连接建立成功
         */
        fun onConnected()

        /**
         * 连接断开
         *
         * @param code 断开代码
         * @param reason 断开原因
         * @param remote 是否为远程断开
         */
        fun onDisconnected(code: Int, reason: String, remote: Boolean)

        /**
         * 连接发生错误
         *
         * @param exception 错误异常
         */
        fun onError(exception: Exception)
    }
}