package com.sunmi.tapro.taplink.service.lan

import com.sunmi.tapro.log.CLog
import com.sunmi.tapro.taplink.service.enums.ConnectionStatus
import com.sunmi.tapro.taplink.service.enums.InnerErrorCode
import com.sunmi.tapro.taplink.service.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.service.interfaces.InnerCallback
import com.sunmi.tapro.taplink.service.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.service.util.ProtocolParser
import com.sunmi.tapro.taplink.service.util.ProtocolParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * WebSocket服务内核类
 *
 * 提供WebSocket连接、发送、接收消息的功能，实现IServiceKernel接口
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class LanServiceKernel : BaseServiceKernel() {
    private val TAG = "WebSocketServiceKernel"

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var onMessageListener: ((String) -> Unit)? = null
    private var onErrorListener: ((Throwable) -> Unit)? = null
    private var onCloseListener: ((Int, String) -> Unit)? = null
    private var onOpenListener: (() -> Unit)? = null
    private var connectionCallback: ConnectionCallback? = null

    override fun getTag(): String = TAG

    /**
     * 实现IServiceKernel接口：连接WebSocket服务器
     *
     * @param protocol WebSocket服务器地址（如 "ws://example.com" 或 "wss://example.com"）
     * @param connectionCallback 连接回调
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "WebSocket is already connected or connecting. Current status: $currentConnectionStatus",
                connectionCallback,
                errorCode = InnerErrorCode.C33.code
            )
            return
        }

        this.connectionCallback = connectionCallback

        if (webSocket != null) {
            CLog.e(TAG, "WebSocket already exists, disconnect first")
            disconnectInternal()
        }

        try {
            updateStatus(ConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()

            // 解析协议字符串，支持 ws://、wss:// 和 lan://
            val parseResult = ProtocolParser.parse(protocol)
            val url = when (parseResult) {
                is ProtocolParseResult.WebSocketProtocol -> parseResult.url
                is ProtocolParseResult.LanProtocol -> {
                    // 将 lan:// 转换为 ws://
                    val scheme = if (parseResult.port == 443) "wss" else "ws"
                    "$scheme://${parseResult.ip}:${parseResult.port}"
                }

                is ProtocolParseResult.Error -> {
                    handleConnectionError(
                        parseResult.message, connectionCallback,
                        InnerErrorCode.C41.code
                    )
                    return
                }

                else -> {
                    handleConnectionError(
                        "Protocol type mismatch, expected websocket or lan protocol", connectionCallback,
                        InnerErrorCode.C41.code
                    )
                    return
                }
            }

            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    CLog.d(TAG, "WebSocket connected: $protocol")
                    onOpenListener?.invoke()
                    handleConnectionSuccess(connectionCallback, null)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    CLog.d(TAG, "WebSocket message received: $text")
                    onMessageListener?.invoke(text)
                    // 转换为ByteArray传递给dataReceiver
                    dataReceiver?.invoke(text.toByteArray(Charsets.UTF_8))
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val text = bytes.utf8()
                    CLog.d(TAG, "WebSocket message received (bytes): $text")
                    onMessageListener?.invoke(text)
                    dataReceiver?.invoke(bytes.toByteArray())
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    CLog.d(TAG, "WebSocket closing: $code - $reason")
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    CLog.d(TAG, "WebSocket closed: $code - $reason")
                    onCloseListener?.invoke(code, reason)
                    updateStatus(ConnectionStatus.DISCONNECTED)
                    connectionCallback?.onDisconnected("$code", reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    CLog.e(TAG, "WebSocket failure:${t.message}")
                    onErrorListener?.invoke(t)
                    handleConnectionError(
                        t.message ?: "Unknown error", connectionCallback,
                        InnerErrorCode.C33.code
                    )
                }
            })
        } catch (e: Exception) {
            CLog.e(TAG, "Failed to connect WebSocket:${e.message}")
            handleConnectionError(e.message ?: "Unknown error", connectionCallback, InnerErrorCode.C33.code)
        }
    }

    /**
     * 实现IServiceKernel接口：发送数据
     *
     * @param traceId 追踪ID
     * @param data 要发送的数据
     * @param sendCallback 发送结果回调
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            CLog.d(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "Service not connected")
            return
        }

        val result = send(data)
        if (!result) {
            updateStatus(ConnectionStatus.ERROR)
            callback?.onError("SEND_FAILED", "Failed to send data via WebSocket")
        } else {
            callback?.onResponse("SUCCESS")
        }
    }

    /**
     * 实现IServiceKernel接口：注册数据接收器
     *
     * @param receiver 数据接收回调
     */

    /**
     * 实现IServiceKernel接口：断开连接
     */
    override fun disconnect() {
        if (currentConnectionStatus == ConnectionStatus.DISCONNECTED) {
            return
        }

        updateStatus(ConnectionStatus.DISCONNECTED)
        disconnectInternal()
    }

    /**
     * 发送数据（ByteArray）
     *
     * @param data 要发送的数据
     * @return Boolean 是否发送成功
     */
    private fun send(data: ByteArray): Boolean {
        return webSocket?.send(okio.ByteString.of(*data)) ?: run {
            CLog.d(TAG, "WebSocket not connected, cannot send data")
            false
        }
    }

    /**
     * 发送消息（字符串，保留用于兼容性）
     *
     * @param message 消息内容
     * @return Boolean 是否发送成功
     */
    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: run {
            CLog.d(TAG, "WebSocket not connected, cannot send message")
            false
        }
    }

    /**
     * 断开连接（内部方法）
     */
    private fun disconnectInternal() {
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        CLog.d(TAG, "WebSocket disconnected")
    }

    /**
     * 设置消息监听器
     *
     * @param listener 消息监听器
     */
    fun setOnMessageListener(listener: (String) -> Unit) {
        onMessageListener = listener
    }

    /**
     * 设置错误监听器
     *
     * @param listener 错误监听器
     */
    fun setOnErrorListener(listener: (Throwable) -> Unit) {
        onErrorListener = listener
    }

    /**
     * 设置关闭监听器
     *
     * @param listener 关闭监听器
     */
    fun setOnCloseListener(listener: (Int, String) -> Unit) {
        onCloseListener = listener
    }

    /**
     * 设置打开监听器
     *
     * @param listener 打开监听器
     */
    fun setOnOpenListener(listener: () -> Unit) {
        onOpenListener = listener
    }

    /**
     * 检查是否已连接
     *
     * @return Boolean 是否已连接
     */
    fun isConnected(): Boolean {
        return webSocket != null
    }
}

