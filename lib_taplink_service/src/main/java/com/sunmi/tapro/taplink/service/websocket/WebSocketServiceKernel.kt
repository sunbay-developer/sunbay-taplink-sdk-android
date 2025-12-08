package com.sunmi.tapro.taplink.service.websocket

import com.sunmi.tapro.log.CLog
import com.sunmi.tapro.taplink.service.enums.ConnectionStatus
import com.sunmi.tapro.taplink.service.enums.InnerErrorCode
import com.sunmi.tapro.taplink.service.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.service.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.service.interfaces.InnerCallback
import com.sunmi.tapro.taplink.service.util.ProtocolParser
import com.sunmi.tapro.taplink.service.util.ProtocolParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.cancelChildren
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
 * 参考LocalServiceKernel的结构和模式实现
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class WebSocketServiceKernel constructor(
    val appId: String,
    val appSecretKey: String
) : BaseServiceKernel() {
    private val TAG = "WebSocketServiceKernel"

    /**
     * 协程作用域
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 并发控制互斥锁
     * 用于保证WebSocket操作的线程安全
     */
    private val websocketMutex = Mutex()

    /**
     * OkHttpClient实例，用于WebSocket连接
     */
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 当前WebSocket连接实例
     */
    private var webSocket: WebSocket? = null

    /**
     * 当前连接回调
     */
    private var currentConnectionCallback: ConnectionCallback? = null

    /**
     * WebSocket连接URL
     */
    private var currentUrl: String? = null

    /**
     * 实现IServiceKernel接口：连接WebSocket服务
     *
     * @param protocol 协议字符串，格式：ws://host:port/path 或 wss://host:port/path
     * @param connectionCallback 连接回调
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "WebSocket is already connected or connecting. Current status: $currentConnectionStatus",
                connectionCallback,
                InnerErrorCode.C33.code
            )
            return
        }

        try {
            updateStatus(ConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()

            // 解析协议字符串
            val parseResult = ProtocolParser.parse(protocol)
            if (parseResult is ProtocolParseResult.Error) {
                handleConnectionError(
                    parseResult.message, connectionCallback,
                    InnerErrorCode.C41.code
                )
                return
            }

            if (parseResult !is ProtocolParseResult.WebSocketProtocol) {
                handleConnectionError(
                    "Protocol type mismatch, expected websocket protocol", connectionCallback,
                    InnerErrorCode.C41.code
                )
                return
            }

            val url = parseResult.url
            CLog.d(TAG, "Connecting to WebSocket: $url")

            // 保存当前URL和回调
            currentUrl = url
            currentConnectionCallback = connectionCallback

            // 如果已有WebSocket连接，先断开
            if (webSocket != null) {
                CLog.d(TAG, "Disconnecting existing WebSocket before connecting new one")
                disconnectInternal()
            }

            // 创建WebSocket请求
            val request = Request.Builder()
                .url(url)
                .addHeader("X-App-Id", appId)
                .addHeader("X-App-Secret-Key", appSecretKey)
                .build()

            // 在协程中执行连接，使用Mutex保证并发安全
            scope.launch {
                websocketMutex.withLock {
                    try {
                        webSocket = client.newWebSocket(request, createWebSocketListener(connectionCallback))
                        CLog.d(TAG, "WebSocket connection initiated: $url")
                    } catch (e: Exception) {
                        CLog.e(TAG, "Failed to create WebSocket connection: ${e.message}")
                        handleConnectionError(
                            "Failed to create WebSocket connection: ${e.message}",
                            connectionCallback,
                            InnerErrorCode.C33.code
                        )
                    }
                }
            }

        } catch (e: Exception) {
            CLog.e(TAG, "Failed to connect WebSocket\n${e.stackTraceToString()}")
            handleConnectionError(e.message ?: "Unknown error", connectionCallback, InnerErrorCode.C33.code)
        }
    }

    /**
     * 实现IServiceKernel接口：发送数据
     *
     * 使用并发控制确保WebSocket发送的线程安全
     *
     * @param traceId 追踪ID
     * @param data 要发送的数据
     * @param callback 发送结果回调
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            CLog.d(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "WebSocket not connected")
            return
        }

        if (webSocket == null) {
            CLog.e(TAG, "WebSocket is null, cannot send data")
            callback?.onError("WEBSOCKET_NULL", "WebSocket interface is null")
            return
        }

        // 在协程中执行，使用Mutex保证并发安全
        scope.launch {
            websocketMutex.withLock {
                try {
                    CLog.d(TAG, "Sending data via WebSocket: traceId=$traceId, dataSize=${data.size} bytes")

                    // 发送ByteArray数据
                    val result = webSocket?.send(ByteString.of(*data)) ?: false

                    if (result) {
                        CLog.d(TAG, "Data sent via WebSocket successfully: traceId=$traceId")
                        callback?.onResponse("SUCCESS")
                    } else {
                        CLog.e(TAG, "Failed to send data via WebSocket: traceId=$traceId")
                        updateStatus(ConnectionStatus.ERROR)
                        callback?.onError("SEND_FAILED", "Failed to send data via WebSocket")
                    }
                } catch (e: Exception) {
                    CLog.e(TAG, "Failed to send data via WebSocket: traceId=$traceId, error=${e.stackTraceToString()}")
                    updateStatus(ConnectionStatus.ERROR)
                    callback?.onError("SEND_ERROR", e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * 实现IServiceKernel接口：断开连接
     */
    override fun disconnect() {
        if (currentConnectionStatus == ConnectionStatus.DISCONNECTED) {
            return
        }

        try {
            // 取消所有协程
            scope.coroutineContext.cancelChildren()

            // 断开WebSocket连接
            disconnectInternal()

            // 清理资源
            currentUrl = null
            currentConnectionCallback = null

            updateStatus(ConnectionStatus.DISCONNECTED)
            CLog.d(TAG, "WebSocket disconnected")
        } catch (e: Exception) {
            CLog.e(TAG, "Error during disconnect: ${e.stackTraceToString()}")
            updateStatus(ConnectionStatus.ERROR)
        }
    }

    /**
     * 创建WebSocket监听器
     *
     * @param connectionCallback 连接回调
     * @return WebSocketListener WebSocket监听器
     */
    private fun createWebSocketListener(connectionCallback: ConnectionCallback): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                CLog.d(TAG, "WebSocket connected: ${currentUrl}")
                
                // 构建额外信息Map（类似LocalServiceKernel的getExtraInfos）
                val extraInfoMap = mapOf(
                    "url" to (currentUrl ?: ""),
                    "protocol" to "websocket",
                    "statusCode" to response.code.toString()
                )

                handleConnectionSuccess(connectionCallback, extraInfoMap)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                CLog.d(TAG, "WebSocket message received (text): $text")
                // 转换为ByteArray传递给dataReceiver
                dataReceiver?.invoke(text.toByteArray(Charsets.UTF_8))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                CLog.d(TAG, "WebSocket message received (bytes): $text")
                // 直接传递ByteArray
                dataReceiver?.invoke(bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                CLog.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                CLog.d(TAG, "WebSocket closed: $code - $reason")
                updateStatus(ConnectionStatus.DISCONNECTED)
                currentConnectionCallback?.onDisconnected(code.toString(), reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                CLog.e(TAG, "WebSocket failure: ${t.message}")
                handleConnectionError(
                    t.message ?: "Unknown error",
                    connectionCallback,
                    InnerErrorCode.C33.code
                )
            }
        }
    }

    /**
     * 断开连接（内部方法）
     */
    private fun disconnectInternal() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            CLog.e(TAG, "Error closing WebSocket: ${e.message}")
        }
        webSocket = null
    }
}

