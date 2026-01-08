package com.sunmi.tapro.taplink.communication.lan.connection

import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket连接管理器
 *
 * 职责：
 * - 管理WebSocket客户端连接
 * - 处理连接状态变化
 * - 处理消息发送和接收
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ConnectionManager {

    companion object {
        private const val TAG = "ConnectionManager"
        private const val CONNECTION_TIMEOUT_MS = 10_000L
    }

    private var client: InternalWebSocketClient? = null
    private var currentUri: URI? = null
    private var messageListener: MessageListener? = null
    private var connectionListener: ConnectionListener? = null

    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun connect(uri: URI): ConnectionResult {
        return withContext(Dispatchers.IO) {
            try {
                LogUtil.d(TAG, "Attempting to connect to: $uri")

                // 如果已连接到相同URI，直接返回成功
                if (isConnected.get() && currentUri == uri) {
                    LogUtil.d(TAG, "Already connected to $uri")
                    return@withContext ConnectionResult.Success
                }

                // 断开现有连接
                if (isConnected.get()) {
                    LogUtil.d(TAG, "Disconnecting existing connection")
                    disconnect()
                }

                // 创建新的WebSocket客户端
                client = InternalWebSocketClient(uri)
                currentUri = uri

                // 连接
                client?.connect()

                // 等待连接结果
                val connectionResult = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
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

            client?.send(data)
            LogUtil.d(TAG, "Binary data sent: ${data.size} bytes")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send binary data: ${e.message}")
            false
        }
    }

    fun send(message: String): Boolean {
        return try {
            if (!isConnected()) {
                LogUtil.w(TAG, "Cannot send message: not connected")
                return false
            }

            client?.send(message)
            LogUtil.d(TAG, "Text message sent: $message")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send text message: ${e.message}")
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