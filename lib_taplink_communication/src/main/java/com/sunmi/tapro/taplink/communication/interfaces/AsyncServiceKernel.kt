package com.sunmi.tapro.taplink.communication.interfaces

import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.util.ProtocolParser
import com.sunmi.tapro.taplink.communication.util.ProtocolParseResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Abstract Async Service Kernel Class
 *
 * Provides common asynchronous operation functionality to reduce code duplication between LocalServiceKernel and WebSocketClientKernel
 * Includes coroutine management, concurrency control, protocol parsing, and other common functionalities
 *
 * @param appId Application ID
 * @param appSecretKey Application secret key
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class AsyncServiceKernel(
    protected val appId: String,
    protected val appSecretKey: String
) : BaseServiceKernel() {

    /**
     * Coroutine scope
     */
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Send data mutex lock
     */
    protected val sendMutex = Mutex()

    /**
     * Current connection callback
     */
    protected var currentConnectionCallback: ConnectionCallback? = null

    /**
     * Implement IServiceKernel interface: Connect to service
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "Service is already connected or connecting. Current status: $currentConnectionStatus",
                connectionCallback,
                getConnectionBusyErrorCode()
            )
            return
        }

        try {
            LogUtil.d(getTag(), "Starting connection with protocol: $protocol")
            updateStatus(ConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()

            // 保存连接回调
            currentConnectionCallback = connectionCallback

            // 解析协议
            val parseResult = ProtocolParser.parse(protocol)
            if (parseResult is ProtocolParseResult.Error) {
                handleConnectionError(
                    parseResult.message, connectionCallback,
                    InnerErrorCode.C41.code
                )
                return
            }

            // 验证协议类型
            if (!isValidProtocolType(parseResult)) {
                handleConnectionError(
                    "Protocol type mismatch, expected ${getExpectedProtocolType()}",
                    connectionCallback,
                    InnerErrorCode.C41.code
                )
                return
            }

            // 执行具体的连接逻辑
            performConnect(parseResult, connectionCallback)

        } catch (e: Exception) {
            LogUtil.e(getTag(), "Failed to start connection: ${e.message}")
            handleConnectionError(
                "Failed to start connection: ${e.message}",
                connectionCallback,
                getConnectionFailedErrorCode()
            )
        }
    }

    /**
     * Implement IServiceKernel interface: Send data
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.d(getTag(), "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "${getServiceType()} not connected")
            return
        }

        // 在协程中执行，使用Mutex保证并发安全
        scope.launch {
            sendMutex.withLock {
                try {
                    LogUtil.d(getTag(), "Sending data via ${getServiceType()}: traceId=$traceId, dataSize=${data.size} bytes")

                    // 执行具体的发送逻辑
                    performSendData(traceId, data, callback)

                } catch (e: Exception) {
                    LogUtil.e(getTag(), "Failed to send data: traceId=$traceId, error=${e.message}")
                    callback?.onError("SEND_ERROR", e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Implement IServiceKernel interface: Disconnect
     */
    override fun disconnect() {
        if (currentConnectionStatus == ConnectionStatus.DISCONNECTED) {
            return
        }

        try {
            LogUtil.d(getTag(), "Disconnecting ${getServiceType()}")

            // 取消所有协程
            scope.coroutineContext.cancelChildren()

            // 执行具体的断开逻辑
            performDisconnect()

            // 清理通用资源
            cleanupCommonResources()

            updateStatus(ConnectionStatus.DISCONNECTED)
            LogUtil.d(getTag(), "${getServiceType()} disconnected")

        } catch (e: Exception) {
            LogUtil.e(getTag(), "Error during disconnect: ${e.message}")
            updateStatus(ConnectionStatus.ERROR)
        }
    }

    /**
     * Clean up common resources
     */
    protected open fun cleanupCommonResources() {
        currentConnectionCallback = null
        dataReceiver = null
    }

    // ==================== Abstract methods - must be implemented by subclasses ====================

    /**
     * Get service type name (for logging)
     */
    protected abstract fun getServiceType(): String

    /**
     * Get expected protocol type description
     */
    protected abstract fun getExpectedProtocolType(): String

    /**
     * Validate if protocol type is valid
     */
    protected abstract fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean

    /**
     * Perform specific connection logic
     */
    protected abstract fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback)

    /**
     * Perform specific send data logic
     */
    protected abstract fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?)

    /**
     * Perform specific disconnect logic
     */
    protected abstract fun performDisconnect()

    // ==================== Overridable methods - can be customized by subclasses ====================

    /**
     * Get error code when connection is busy
     */
    protected open fun getConnectionBusyErrorCode(): String = InnerErrorCode.C25.code

    /**
     * Get error code when connection fails
     */
    protected open fun getConnectionFailedErrorCode(): String = InnerErrorCode.C25.code

    // ==================== Helper methods ====================

    /**
     * Execute operation in coroutine
     */
    protected fun launchInScope(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(block = block)
    }

    /**
     * Execute operation under mutex protection
     */
    protected suspend fun <T> withSendLock(block: suspend () -> T): T {
        return sendMutex.withLock { block() }
    }

    /**
     * Notify connection success
     */
    protected fun notifyConnectionSuccess(extraInfoMap: Map<String, String?>? = null) {
        currentConnectionCallback?.let { callback ->
            handleConnectionSuccess(callback, extraInfoMap)
        }
    }

    /**
     * Notify connection error
     */
    protected fun notifyConnectionError(message: String, error: InnerErrorCode = InnerErrorCode.C25) {
        currentConnectionCallback?.let { callback ->
            handleConnectionError("${error.description}(${message})", callback, error.code)
        }
    }

    /**
     * Notify connection disconnected
     */
    protected fun notifyConnectionDisconnected(code: String, message: String) {
        currentConnectionCallback?.onDisconnected(code, message)
    }
}