package com.sunmi.tapro.taplink.communication.interfaces

import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Async service kernel abstract base class
 *
 * Provides common async operation functionality, reducing duplicate code in LocalServiceKernel and WebSocketClientKernel
 * Includes coroutine management, concurrency control, protocol parsing and other common features
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
     * Implement IServiceKernel interface: connect to service
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "Service is already connected or connecting. Current status: $currentInnerConnectionStatus",
                connectionCallback,
                getConnectionBusyErrorCode()
            )
            return
        }

        try {
            LogUtil.d(getTag(), "Starting connection with protocol: $protocol")
            updateStatus(InnerConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()

            // Save connection callback
            currentConnectionCallback = connectionCallback

            // Parse protocol
            val parseResult = ProtocolManager.parseProtocol(protocol)
            if (parseResult is ProtocolParseResult.Error) {
                handleConnectionError(
                    parseResult.message, connectionCallback,
                    InnerErrorCode.C41.code
                )
                return
            }

            // Validate protocol type
            if (!isValidProtocolType(parseResult)) {
                handleConnectionError(
                    "Protocol type mismatch, expected ${getExpectedProtocolType()}",
                    connectionCallback,
                    InnerErrorCode.C41.code
                )
                return
            }

            // Execute specific connection logic
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
     * Implement IServiceKernel interface: send data
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.d(getTag(), "Cannot send data, not connected. Status: $currentInnerConnectionStatus")
            callback?.onError("NOT_CONNECTED", "${getServiceType()} not connected")
            return
        }

        // Execute in coroutine, use Mutex to ensure concurrency safety
        scope.launch {
            sendMutex.withLock {
                try {
                    LogUtil.d(getTag(), "Sending data via ${getServiceType()}: traceId=$traceId, dataSize=${data.size} bytes")

                    // Execute specific send logic
                    performSendData(traceId, data, callback)

                } catch (e: Exception) {
                    LogUtil.e(getTag(), "Failed to send data: traceId=$traceId, error=${e.message}")
                    callback?.onError("SEND_ERROR", e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Implement IServiceKernel interface: disconnect
     */
    override fun disconnect() {
        if (currentInnerConnectionStatus == InnerConnectionStatus.DISCONNECTED) {
            return
        }

        try {
            LogUtil.d(getTag(), "Disconnecting ${getServiceType()}")

            // Cancel all coroutines
            scope.coroutineContext.cancelChildren()

            // Execute specific disconnect logic
            performDisconnect()

            // Clean up common resources
            cleanupCommonResources()

            updateStatus(InnerConnectionStatus.DISCONNECTED)
            LogUtil.d(getTag(), "${getServiceType()} disconnected")

        } catch (e: Exception) {
            LogUtil.e(getTag(), "Error during disconnect: ${e.message}")
            updateStatus(InnerConnectionStatus.ERROR)
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
     * Execute specific connection logic
     */
    protected abstract fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback)

    /**
     * Execute specific send data logic
     */
    protected abstract fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?)

    /**
     * Execute specific disconnect logic
     */
    protected abstract fun performDisconnect()

    // ==================== Overridable methods - subclasses can customize ====================

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
     * Execute operation in coroutine scope
     */
    protected fun launchInScope(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(block = block)
    }

    /**
     * Execute operation under mutex lock protection
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
     * Notify connection failure
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