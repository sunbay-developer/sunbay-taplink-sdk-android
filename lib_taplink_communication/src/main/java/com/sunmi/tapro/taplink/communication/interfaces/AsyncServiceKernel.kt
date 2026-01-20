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
     * 协程异常处理器
     * 用于统一记录"未捕获异常"，避免异常静默丢失
     * SupervisorJob 会让子协程失败不影响兄弟协程，但如果没有 handler，很多异常只会静默打印或被默认处理，排查困难
     */
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LogUtil.e(getTag(), "Coroutine uncaught exception: ${throwable.message}")
        throwable.printStackTrace()
    }

    /**
     * Coroutine scope
     * 
     * 使用 SupervisorJob() 让子协程失败不影响兄弟协程
     * 使用 CoroutineExceptionHandler 统一记录未捕获异常
     */
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler)

    /**
     * Send data mutex lock
     * 
     * 锁顺序约束：sendMutex -> usbSendMutex（如果子类有）
     * 任何地方都不要反过来（先拿 usbSendMutex 再调用 sendData/withSendLock），否则会死锁
     */
    protected val sendMutex = Mutex()

    /**
     * 连接任务 Job
     * 用于管理连接协程，新连接时取消旧连接，断开时取消连接任务
     */
    protected var connectJob: Job? = null

    /**
     * Current connection callback
     * 
     * 注意：并发 connect 时 callback 可能被覆盖，需要配合 token 机制使用
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
     * 优化后的执行顺序（方案 A：同步清理）：
     * 1. 先取消连接任务和子任务（但保留 scope 本身）
     * 2. 再同步释放底层资源（performDisconnect 必须是同步的，不要 scope.launch）
     * 3. 清理公共资源
     * 4. 更新状态为 DISCONNECTED（只有资源释放成功才更新）
     * 
     * 注意：performDisconnect() 必须是同步完成资源释放，不能依赖 scope.launch
     */
    override fun disconnect() {
        if (currentInnerConnectionStatus == InnerConnectionStatus.DISCONNECTED) {
            return
        }

        try {
            LogUtil.d(getTag(), "Disconnecting ${getServiceType()}")

            // 1. 先取消连接任务和子任务（但保留 scope 本身）
            connectJob?.cancel()
            connectJob = null
            scope.coroutineContext.cancelChildren()

            // 2. 再同步释放底层资源（必须同步，不要 scope.launch）
            // 注意：performDisconnect() 必须是同步的，不能依赖 scope.launch
            val disconnectSuccess = runCatching {
                performDisconnect()
                true
            }.getOrElse { e ->
                LogUtil.e(getTag(), "Error during performDisconnect: ${e.message}")
                e.printStackTrace()
                false
            }

            // 3. 清理公共资源（无论断开是否成功都要清理）
            cleanupCommonResources()

            // 4. 更新状态（只有资源释放成功才更新为 DISCONNECTED，否则为 ERROR）
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
            // 即使出错也要清理资源和更新状态
            cleanupCommonResources()
            updateStatus(InnerConnectionStatus.ERROR)
        }
    }

    /**
     * Clean up common resources
     * 
     * 注意：需要清理连接相关 job，避免 release/销毁时仍有连接协程活着
     */
    protected open fun cleanupCommonResources() {
        // 取消连接任务
        connectJob?.cancel()
        connectJob = null
        
        // 清理回调和数据接收器
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
