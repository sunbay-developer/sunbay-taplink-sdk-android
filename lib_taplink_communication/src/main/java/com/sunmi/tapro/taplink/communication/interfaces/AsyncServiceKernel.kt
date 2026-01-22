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
     * Coroutine exception handler
     * Used to uniformly log uncaught exceptions and avoid silent exception loss
     * SupervisorJob allows child coroutine failures to not affect sibling coroutines,
     * but without a handler, many exceptions will only be silently printed or handled by default, making troubleshooting difficult
     */
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LogUtil.e(getTag(), "Coroutine uncaught exception: ${throwable.message}")
        throwable.printStackTrace()
    }

    /**
     * Coroutine scope
     * 
     * Uses SupervisorJob() to allow child coroutine failures to not affect sibling coroutines
     * Uses CoroutineExceptionHandler to uniformly log uncaught exceptions
     */
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    /**
     * Send data mutex lock
     * 
     * Lock order constraint: sendMutex -> usbSendMutex (if subclass has one)
     * Never reverse this order (acquiring usbSendMutex before calling sendData/withSendLock), otherwise deadlock will occur
     */
    protected val sendMutex = Mutex()

    /**
     * Connection task Job
     * Used to manage connection coroutines, cancel old connections when new connection starts, cancel connection task when disconnecting
     */
    protected var connectJob: Job? = null

    /**
     * Current connection callback
     * 
     * Note: When connecting concurrently, callback may be overwritten, needs to be used with token mechanism
     */
    protected var currentConnectionCallback: ConnectionCallback? = null

    /**
     * Implement IServiceKernel interface: connect to service
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        LogUtil.i("tag", "=======AsyncServiceKernel:::connect:protocol:$protocol")
        if (!canConnect()) {
            handleConnectionError(
                InnerErrorCode.E211.description + ": Current status: $currentInnerConnectionStatus",
                connectionCallback,
                InnerErrorCode.E211.code
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
                    "${InnerErrorCode.E302.description}:${parseResult.message}", connectionCallback,
                    InnerErrorCode.E302.code
                )
                return
            }

            // Validate protocol type
            if (!isValidProtocolType(parseResult)) {
                handleConnectionError(
                    InnerErrorCode.E302.description + ", expected ${getExpectedProtocolType()}",
                    connectionCallback,
                    InnerErrorCode.E302.code
                )
                return
            }

            // Execute specific connection logic
            performConnect(parseResult, connectionCallback)

        } catch (e: Exception) {
            LogUtil.e(getTag(), "Failed to start connection: ${e.message}")
            handleConnectionError(
                "${InnerErrorCode.E212.description}: ${e.message}",
                connectionCallback,
                InnerErrorCode.E212.code
            )
        }
    }

    /**
     * Implement IServiceKernel interface: send data
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.d(getTag(), "Cannot send data, not connected. Status: $currentInnerConnectionStatus")
            callback?.onError(InnerErrorCode.E304.code, "${getServiceType()} ${InnerErrorCode.E304.description}")
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
                    callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
                }
            }
        }
    }

    /**
     * Implement IServiceKernel interface: disconnect
     * 
     * Optimized execution order (Option A: synchronous cleanup):
     * 1. First cancel connection tasks and child tasks (but keep the scope itself)
     * 2. Then synchronously release underlying resources (performDisconnect must be synchronous, don't use scope.launch)
     * 3. Clean up common resources
     * 4. Update status to DISCONNECTED (only update if resource release succeeds)
     * 
     * Note: performDisconnect() must complete resource release synchronously, cannot rely on scope.launch
     */
    override fun disconnect() {
        if (currentInnerConnectionStatus == InnerConnectionStatus.DISCONNECTED) {
            return
        }

        try {
            LogUtil.d(getTag(), "Disconnecting ${getServiceType()}")

            // 1. First cancel connection tasks and child tasks (but keep the scope itself)
            connectJob?.cancel()
            connectJob = null
            scope.coroutineContext.cancelChildren()

            // 2. Then synchronously release underlying resources (must be synchronous, don't use scope.launch)
            // Note: performDisconnect() must be synchronous, cannot rely on scope.launch
            val disconnectSuccess = runCatching {
                performDisconnect()
                true
            }.getOrElse { e ->
                LogUtil.e(getTag(), "Error during performDisconnect: ${e.message}")
                e.printStackTrace()
                false
            }

            // 3. Clean up common resources (clean up regardless of whether disconnect succeeded)
            cleanupCommonResources()

            // 4. Update status (only update to DISCONNECTED if resource release succeeded, otherwise ERROR)
            if (disconnectSuccess) {
                updateStatus(InnerConnectionStatus.DISCONNECTED)
                LogUtil.d(getTag(), "${getServiceType()} disconnected successfully")
            } else {
                updateStatus(InnerConnectionStatus.ERROR)
                LogUtil.e(getTag(), "${getServiceType()} disconnect completed with errors")
            }

        } catch (e: Exception) {
            LogUtil.e(getTag(), "Error during disconnect: ${e.message}")
            e.printStackTrace()
            // Even if error occurs, still clean up resources and update status
            cleanupCommonResources()
            updateStatus(InnerConnectionStatus.ERROR)
        }
    }

    /**
     * Clean up common resources
     * 
     * Note: Need to clean up connection-related jobs to avoid connection coroutines still alive during release/destruction
     */
    protected open fun cleanupCommonResources() {
        // Cancel connection task
        connectJob?.cancel()
        connectJob = null
        
        // Clean up callbacks and data receiver
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
    protected abstract suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?)

    /**
     * Execute specific disconnect logic
     */
    protected abstract fun performDisconnect()

    // ==================== Overridable methods - subclasses can customize ====================

    /**
     * Get error code when connection is busy
     */
    protected open fun getConnectionBusyErrorCode(): String = InnerErrorCode.E232.code

    /**
     * Get error code when connection fails
     */
    protected open fun getConnectionFailedErrorCode(): String = InnerErrorCode.E232.code

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
    protected fun notifyConnectionError(message: String, error: InnerErrorCode = InnerErrorCode.E232) {
        currentConnectionCallback?.let { callback ->
            handleConnectionError("${error.description}:${message}", callback, error.code)
        }
    }

    /**
     * Notify connection disconnected
     */
    protected fun notifyConnectionDisconnected(code: String, message: String) {
        currentConnectionCallback?.onDisconnected(code, message)
    }
}
