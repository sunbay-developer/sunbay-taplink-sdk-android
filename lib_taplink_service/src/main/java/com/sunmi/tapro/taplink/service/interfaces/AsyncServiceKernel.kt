package com.sunmi.tapro.taplink.service.interfaces

import com.sunmi.tapro.taplink.service.enums.ConnectionStatus
import com.sunmi.tapro.taplink.service.enums.InnerErrorCode
import com.sunmi.tapro.taplink.service.util.LogUtil
import com.sunmi.tapro.taplink.service.util.ProtocolParser
import com.sunmi.tapro.taplink.service.util.ProtocolParseResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 异步服务内核抽象基类
 *
 * 提供通用的异步操作功能，减少 LocalServiceKernel 和 WebSocketClientKernel 的重复代码
 * 包含协程管理、并发控制、协议解析等通用功能
 *
 * @param appId 应用ID
 * @param appSecretKey 应用密钥
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class AsyncServiceKernel(
    protected val appId: String,
    protected val appSecretKey: String
) : BaseServiceKernel() {

    /**
     * 协程作用域
     */
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 发送数据互斥锁
     */
    protected val sendMutex = Mutex()

    /**
     * 当前连接回调
     */
    protected var currentConnectionCallback: ConnectionCallback? = null

    /**
     * 实现IServiceKernel接口：连接服务
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
     * 实现IServiceKernel接口：发送数据
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
     * 实现IServiceKernel接口：断开连接
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
     * 清理通用资源
     */
    protected open fun cleanupCommonResources() {
        currentConnectionCallback = null
        dataReceiver = null
    }

    // ==================== 抽象方法 - 子类必须实现 ====================

    /**
     * 获取服务类型名称（用于日志）
     */
    protected abstract fun getServiceType(): String

    /**
     * 获取期望的协议类型描述
     */
    protected abstract fun getExpectedProtocolType(): String

    /**
     * 验证协议类型是否有效
     */
    protected abstract fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean

    /**
     * 执行具体的连接逻辑
     */
    protected abstract fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback)

    /**
     * 执行具体的发送数据逻辑
     */
    protected abstract fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?)

    /**
     * 执行具体的断开连接逻辑
     */
    protected abstract fun performDisconnect()

    // ==================== 可重写的方法 - 子类可以自定义 ====================

    /**
     * 获取连接繁忙时的错误码
     */
    protected open fun getConnectionBusyErrorCode(): String = InnerErrorCode.C25.code

    /**
     * 获取连接失败时的错误码
     */
    protected open fun getConnectionFailedErrorCode(): String = InnerErrorCode.C25.code

    // ==================== 辅助方法 ====================

    /**
     * 在协程中执行操作
     */
    protected fun launchInScope(block: suspend CoroutineScope.() -> Unit): Job {
        return scope.launch(block = block)
    }

    /**
     * 在互斥锁保护下执行操作
     */
    protected suspend fun <T> withSendLock(block: suspend () -> T): T {
        return sendMutex.withLock { block() }
    }

    /**
     * 通知连接成功
     */
    protected fun notifyConnectionSuccess(extraInfoMap: Map<String, String?>? = null) {
        currentConnectionCallback?.let { callback ->
            handleConnectionSuccess(callback, extraInfoMap)
        }
    }

    /**
     * 通知连接失败
     */
    protected fun notifyConnectionError(message: String, error: InnerErrorCode = InnerErrorCode.C25) {
        currentConnectionCallback?.let { callback ->
            handleConnectionError("${error.description}(${message})", callback, error.code)
        }
    }

    /**
     * 通知连接断开
     */
    protected fun notifyConnectionDisconnected(code: String, message: String) {
        currentConnectionCallback?.onDisconnected(code, message)
    }
}