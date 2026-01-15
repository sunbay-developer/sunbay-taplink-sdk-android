package com.sunmi.tapro.taplink.communication.lan

import android.content.Context
import android.text.TextUtils
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.lan.connection.ConnectionManager
import com.sunmi.tapro.taplink.communication.lan.discovery.ServiceDiscoveryManager
import com.sunmi.tapro.taplink.communication.lan.heartbeat.HeartbeatManager
import com.sunmi.tapro.taplink.communication.lan.model.ServiceInfo
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/**
 * LAN客户端服务内核类
 *
 * 重组后的架构：
 * - 正确继承AsyncServiceKernel，充分利用基础功能
 * - 使用管理器模式分解复杂职责
 * - 专注于LAN网络通信（主要是WebSocket）
 * - 修复竞态条件问题，确保操作互斥和回调正确性
 *
 * 管理器组件：
 * - ConnectionManager: WebSocket连接管理
 * - ServiceDiscoveryManager: mDNS服务发现
 * - HeartbeatManager: 心跳机制管理
 *
 * @param appId 应用ID
 * @param appSecretKey 应用密钥
 * @param context Android上下文
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class LanClientKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    companion object {
        private const val TAG = "LanClientKernel"
        private const val NSD_SERVICE_TYPE = "_taplink._tcp"

        // 服务发现重试配置
        private const val SERVICE_DISCOVERY_MAX_RETRIES = 3  // 最大重试次数
        private const val SERVICE_DISCOVERY_RETRY_DELAY_MS = 1000L  // 初始重试延迟（毫秒）
        private const val SERVICE_DISCOVERY_RETRY_DELAY_MULTIPLIER = 2  // 重试延迟倍数（递增延迟）
    }

    override fun getTag(): String = TAG

    // ==================== 并发控制 ====================

    /**
     * 操作互斥锁，确保同时只有一个连接操作
     */
    private val operationMutex = Mutex()

    /**
     * 状态更新互斥锁，保护状态更新操作
     */
    private val statusMutex = Mutex()

    /**
     * 当前正在执行的操作类型
     */
    private var currentOperation: String? = null

    /**
     * 操作标识生成器
     */
    private val operationIdGenerator = AtomicLong(0)

    /**
     * 当前操作ID
     */
    private var currentOperationId: String? = null

    /**
     * 服务地址变化监听器（独立于连接回调，用于持久监听）
     */
    private var serviceAddressChangeListener: ServiceAddressChangeListener? = null

    /**
     * 服务地址变化监听器接口
     */
    interface ServiceAddressChangeListener {
        /**
         * 当检测到服务地址变更时调用
         * @param serviceName 服务名称
         * @param newHost 新的主机地址
         * @param newPort 新的端口号
         * @param oldHost 旧的主机地址
         * @param oldPort 旧的端口号
         * @return Boolean - true: 需要重连, false: 不需要重连
         */
        fun onServiceAddressChanged(
            serviceName: String,
            newHost: String,
            newPort: Int,
            oldHost: String,
            oldPort: Int
        ): Boolean
    }

    /**
     * 设置服务地址变化监听器
     *
     * @param listener 监听器，为 null 时清除监听器
     */
    fun setServiceAddressChangeListener(listener: ServiceAddressChangeListener?) {
        synchronized(this) {
            serviceAddressChangeListener = listener
            LogUtil.d(TAG, "Service address change listener ${if (listener != null) "set" else "cleared"}")
        }
    }

    fun getServiceAddressChangeListener(): ServiceAddressChangeListener? = serviceAddressChangeListener

    // ==================== 管理器组件 ====================

    /**
     * WebSocket连接管理器
     */
    private val connectionManager = ConnectionManager()

    /**
     * 服务发现管理器
     */
    private val serviceDiscoveryManager = ServiceDiscoveryManager(context)

    /**
     * 心跳管理器
     */
    private val heartbeatManager = HeartbeatManager()

    // ==================== 状态管理 ====================

    /**
     * 当前连接的URI
     */
    private var currentUri: URI? = null

    /**
     * 当前连接的服务信息
     */
    private var currentService: ServiceInfo? = null

    /**
     * 是否正在进行服务监听
     */
    private var isServiceMonitoring = false

    /**
     * 是否为主动断开
     */
    private var isManualDisconnect = false

    /**
     * 上次触发服务地址变化的时间戳（用于防抖）
     */
    private var lastServiceAddressChangeTime: Long = 0

    /**
     * 防抖时间间隔（毫秒），避免短时间内重复触发重连
     */
    private val SERVICE_ADDRESS_CHANGE_DEBOUNCE_MS = 2000L

    init {
        setupConnectionManager()
    }

    // ==================== 安全操作执行器 ====================

    /**
     * 安全执行连接操作，确保操作互斥
     */
    private suspend fun executeConnectionOperation(
        operationType: String,
        callback: ConnectionCallback,
        operation: suspend (String) -> Unit
    ) {
        // 检查是否已有操作在进行
        // 注意：即使 TaplinkServiceKernel 已经确保在 connect() 之前先 disconnect()，
        // 这里仍然需要互斥锁保护，因为：
        // 1. 可能有其他线程并发调用
        // 2. 可能有内部重连操作（服务发现、心跳失败等）在进行
        if (!operationMutex.tryLock()) {
            val currentOp = currentOperation
            LogUtil.w(TAG, "Another operation is in progress: $currentOp, rejecting $operationType")
            callback.onDisconnected(
                InnerErrorCode.E241.code,
                "Another connection operation is in progress: $currentOp"
            )
            return
        }

        val operationId = generateOperationId()

        try {
            // 设置当前操作信息
            currentOperation = operationType
            currentOperationId = operationId

            // 注意：回调已由基类 AsyncServiceKernel 的 currentConnectionCallback 管理
            // 不需要额外的回调管理器，因为连接操作是互斥的（operationMutex 保护）

            LogUtil.d(TAG, "Starting $operationType operation: $operationId")

            // 执行操作
            operation(operationId)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error in $operationType operation: ${e.message}")

            // 通知错误：使用基类的 notifyConnectionError()
            notifyConnectionError("Operation failed: ${e.message}", InnerErrorCode.E241)

        } finally {
            // 注意：不在这里清除 currentOperationId，因为连接成功回调可能在 finally 之后异步触发
            // currentOperationId 会在连接成功/失败回调中清除
            currentOperation = null
            // currentOperationId 保留，直到连接成功/失败回调中清除

            // 释放互斥锁
            operationMutex.unlock()

            LogUtil.d(TAG, "Completed $operationType operation: $operationId")
        }
    }

    /**
     * 生成操作ID
     */
    private fun generateOperationId(): String {
        return "${System.currentTimeMillis()}_${operationIdGenerator.incrementAndGet()}"
    }

    /**
     * 检查操作是否仍然有效
     */
    private fun isOperationValid(operationId: String): Boolean {
        return currentOperationId == operationId
    }

    /**
     * 安全更新状态
     */
    private suspend fun updateStatusSafely(newStatus: InnerConnectionStatus) {
        statusMutex.withLock {
            if (currentInnerConnectionStatus != newStatus) {
                LogUtil.d(TAG, "Status update: ${currentInnerConnectionStatus} -> $newStatus")
                updateStatus(newStatus)
            }
        }
    }

    /**
     * 检查 WebSocket 连接是否已建立
     *
     * 优先使用 ConnectionManager 的实际状态，而不是 currentInnerConnectionStatus
     * 因为 ConnectionManager 的状态更准确（检查了 WebSocket 的实际状态）
     *
     * @return true 如果 WebSocket 连接已建立，false 否则
     */
    private fun isWebSocketConnected(): Boolean {
        return connectionManager.isConnected()
    }

    /**
     * 检查是否应该跳过服务处理（已连接或正在连接到相同地址）
     *
     * @param service 要检查的服务
     * @return true 如果应该跳过，false 否则
     */
    private fun shouldSkipServiceProcessing(service: ServiceInfo): Boolean {
        val isSameAddress = currentService != null &&
                currentService?.host == service.host &&
                currentService?.port == service.port
        val isCurrentlyConnected = isWebSocketConnected()
        val isCurrentlyConnecting = currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING ||
                currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT

        return isSameAddress && (isCurrentlyConnected || isCurrentlyConnecting)
    }

    // ==================== AsyncServiceKernel 抽象方法实现 ====================

    override fun getServiceType(): String = "LAN"

    override fun getExpectedProtocolType(): String = "lan protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.LanProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        launchInScope {
            executeConnectionOperation("CONNECT", connectionCallback) { operationId ->
                try {
                    val lanResult = parseResult as ProtocolParseResult.LanProtocol
                    LogUtil.d(TAG, "Performing LAN connection to: ${lanResult.ip}:${lanResult.port} ($operationId)")

                    // 注意：不在这里检查 isOperationValid，因为互斥锁已经保护了操作互斥性
                    // 正常情况下不会有多个操作同时进行

                    // 构建WebSocket URI，使用协议解析结果中的 secure 字段
                    val uri = try {
                        val scheme = if (lanResult.secure) "wss" else "ws"
                        URI("$scheme://${lanResult.ip}:${lanResult.port}")
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid URI: ${lanResult.ip}:${lanResult.port}", e)
                    }

                    currentUri = uri

                    // 更新状态为连接中
                    updateStatusSafely(InnerConnectionStatus.CONNECTING)

                    // 注意：不在这里检查 isOperationValid，因为互斥锁已经保护了操作互斥性

                    // 尝试直接连接
                    val result = connectionManager.connect(uri)
                    when (result) {
                        is ConnectionManager.ConnectionResult.Success -> {
                            LogUtil.d(TAG, "Direct connection successful ($operationId)")

                            // 注意：不在这里检查 isOperationValid，因为：
                            // 1. onOpen 回调是异步的，可能在 connect() 返回之前就已经执行
                            // 2. onConnected() 回调会清除 currentOperationId
                            // 3. 如果在这里检查，可能会误判为操作被取消，导致连接被断开
                            // 4. 连接成功的处理会通过 ConnectionManager 的监听器触发
                            // 5. 如果操作真的被取消，onConnected() 回调中会处理断开逻辑

                            // 连接成功的处理会通过 ConnectionManager 的监听器触发
                            // 监听器会调用 notifyConnectionSuccess() 作为统一的连接成功通知出口
                        }

                        is ConnectionManager.ConnectionResult.Failure -> {
                            LogUtil.w(TAG, "Direct connection failed: ${result.error} ($operationId)")

                            // 注意：不在这里检查 isOperationValid，因为互斥锁已经保护了操作互斥性
                            // 服务发现是长时间操作，会在服务发现逻辑中检查 isOperationValid

                            // 尝试服务发现
                            tryServiceDiscoveryForOperation(operationId)
                        }
                    }

                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error in connect operation: ${e.message} ($operationId)")
                    throw e
                }
            }
        }
    }

    /**
     * 为特定操作尝试服务发现（带重试机制）
     */
    private suspend fun tryServiceDiscoveryForOperation(operationId: String) {
        var lastException: Exception? = null
        var retryCount = 0

        // 重试循环
        while (retryCount <= SERVICE_DISCOVERY_MAX_RETRIES) {
            try {
                // 检查操作是否仍然有效
                if (!isOperationValid(operationId)) {
                    LogUtil.w(TAG, "Service discovery cancelled: $operationId")
                    return
                }

                if (retryCount > 0) {
                    // 计算递增延迟：1秒、2秒、4秒...
                    var delayMs = SERVICE_DISCOVERY_RETRY_DELAY_MS
                    repeat(retryCount - 1) {
                        delayMs *= SERVICE_DISCOVERY_RETRY_DELAY_MULTIPLIER
                    }
                    LogUtil.d(
                        TAG,
                        "Retrying service discovery (attempt ${retryCount + 1}/${SERVICE_DISCOVERY_MAX_RETRIES + 1}) after ${delayMs}ms delay ($operationId)"
                    )
                    delay(delayMs)
                    // 延迟后再次检查操作是否仍然有效（可能在延迟期间模式已切换）
                    if (!isOperationValid(operationId)) {
                        LogUtil.w(TAG, "Service discovery cancelled after delay: $operationId")
                        return
                    }
                } else {
                    LogUtil.d(TAG, "Starting service discovery for operation: $operationId")
                }

                // 尝试服务发现
                val services = serviceDiscoveryManager.discoverServices(NSD_SERVICE_TYPE)

                if (services.isNotEmpty()) {
                    LogUtil.d(TAG, "Found ${services.size} services for operation: $operationId (attempt ${retryCount + 1})")

                    // 通知上层发现的服务，由上层决定是否需要重连
                    // 找到第一个有效服务并通知上层（使用持久监听器）
                    var notified = false
                    for (service in services) {
                        if (service.isValid()) {
                            LogUtil.d(
                                TAG,
                                "Notifying upper layer of discovered service: ${service.name} at ${service.getAddress()} ($operationId)"
                            )
                            val listener = serviceAddressChangeListener
                            val shouldReconnect = if (listener != null) {
                                try {
                                    listener.onServiceAddressChanged(
                                        service.name,
                                        service.host,
                                        service.port,
                                        currentService?.host ?: "",
                                        currentService?.port ?: -1
                                    )
                                } catch (e: Exception) {
                                    LogUtil.e(TAG, "Error in service address change listener: ${e.message}")
                                    false
                                }
                            } else {
                                LogUtil.w(TAG, "No service address change listener set, cannot notify upper layer")
                                false
                            }
                            notified = true
                            if (shouldReconnect) {
                                LogUtil.d(TAG, "Upper layer decided to reconnect, service discovery completed ($operationId)")
                                // 上层会处理重连，这里直接返回
                                return
                            } else {
                                LogUtil.d(
                                    TAG,
                                    "Upper layer decided not to reconnect for this service, trying next service ($operationId)"
                                )
                            }
                        }
                    }

                    if (!notified) {
                        LogUtil.w(TAG, "No valid services found in discovered services ($operationId)")
                        // 继续重试服务发现
                        if (retryCount < SERVICE_DISCOVERY_MAX_RETRIES) {
                            lastException = Exception("No valid services found (attempt ${retryCount + 1})")
                            retryCount++
                            continue
                        } else {
                            throw Exception("No valid services found after ${SERVICE_DISCOVERY_MAX_RETRIES + 1} attempts")
                        }
                    } else {
                        // 已通知上层，但上层决定不重连，服务发现完成
                        LogUtil.d(TAG, "Service discovery completed, upper layer will handle connection ($operationId)")
                        return
                    }
                } else {
                    // 没有发现服务
                    LogUtil.w(
                        TAG,
                        "No services found via service discovery (attempt ${retryCount + 1}/${SERVICE_DISCOVERY_MAX_RETRIES + 1}) ($operationId)"
                    )

                    // 检查当前连接状态
                    // 使用 ConnectionManager 的实际状态，而不是 currentInnerConnectionStatus
                    val isCurrentlyConnected = isWebSocketConnected()
                    val isCurrentlyConnecting = currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING

                    if (isCurrentlyConnected || isCurrentlyConnecting) {
                        LogUtil.d(TAG, "Service discovery found no services, but already connected/connecting ($operationId)")
                        return
                    }

                    // 如果还有重试机会，继续重试
                    if (retryCount < SERVICE_DISCOVERY_MAX_RETRIES) {
                        lastException = Exception("No services found (attempt ${retryCount + 1})")
                        retryCount++
                        continue
                    } else {
                        // 所有重试都失败
                        throw Exception("No available services found after ${SERVICE_DISCOVERY_MAX_RETRIES + 1} attempts")
                    }
                }

            } catch (e: Exception) {
                lastException = e
                LogUtil.w(TAG, "Service discovery attempt ${retryCount + 1} failed: ${e.message} ($operationId)")

                // 检查当前连接状态
                // 使用 ConnectionManager 的实际状态，而不是 currentInnerConnectionStatus
                val isCurrentlyConnected = isWebSocketConnected()
                val isCurrentlyConnecting = currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING

                if (isCurrentlyConnected || isCurrentlyConnecting) {
                    LogUtil.d(TAG, "Service discovery failed, but already connected/connecting ($operationId)")
                    return
                }

                // 检查是否应该重试
                if (retryCount < SERVICE_DISCOVERY_MAX_RETRIES) {
                    // 区分不同类型的错误
                    val isRetryableError = isRetryableServiceDiscoveryError(e)
                    if (isRetryableError) {
                        retryCount++
                        LogUtil.d(TAG, "Retryable error detected, will retry service discovery ($operationId)")
                        continue
                    } else {
                        // 不可重试的错误（如操作被取消），直接抛出
                        LogUtil.w(TAG, "Non-retryable error detected, aborting service discovery ($operationId)")
                        throw e
                    }
                } else {
                    // 所有重试都失败
                    LogUtil.e(
                        TAG,
                        "Service discovery failed after ${SERVICE_DISCOVERY_MAX_RETRIES + 1} attempts: ${e.message} ($operationId)"
                    )
                    throw Exception(
                        "Service discovery failed after ${SERVICE_DISCOVERY_MAX_RETRIES + 1} attempts: ${e.message}",
                        e
                    )
                }
            }
        }

        // 如果所有重试都失败，抛出最后一个异常
        throw lastException ?: Exception("Service discovery failed after ${SERVICE_DISCOVERY_MAX_RETRIES + 1} attempts")
    }

    /**
     * 判断服务发现错误是否可重试
     *
     * @param e 异常
     * @return true 如果错误可重试，false 否则
     */
    private fun isRetryableServiceDiscoveryError(e: Exception): Boolean {
        val errorMessage = e.message?.lowercase() ?: ""

        // 可重试的错误类型
        val retryableErrors = listOf(
            "timeout",
            "network",
            "connection",
            "no available services",
            "discovery failed",
            "nsd",
            "service discovery"
        )

        // 不可重试的错误类型（如操作被取消）
        val nonRetryableErrors = listOf(
            "cancelled",
            "operation cancelled",
            "invalid"
        )

        // 检查是否是不可重试的错误
        if (nonRetryableErrors.any { errorMessage.contains(it) }) {
            return false
        }

        // 检查是否是可重试的错误
        return retryableErrors.any { errorMessage.contains(it) }
    }

    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        try {
            LogUtil.d(TAG, "Sending data via LAN: traceId=$traceId, size=${data.size} bytes")

            val success = connectionManager.send(data)
            if (success) {
                LogUtil.d(TAG, "Data sent successfully: traceId=$traceId")
//                callback?.onResponse("Data sent successfully")
            } else {
                LogUtil.e(TAG, "Failed to send data: traceId=$traceId")
                callback?.onError(InnerErrorCode.E304.code, InnerErrorCode.E304.description)
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending data: traceId=$traceId, error=${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
        }
    }

    override fun performDisconnect() {
        try {
            LogUtil.d(TAG, "Performing LAN disconnect")

            // 标记为主动断开
            isManualDisconnect = true

            // 停止心跳
            stopHeartbeat()

            // 停止服务发现（主动断开时停止）
            stopServiceDiscovery()

            // 断开WebSocket连接
            connectionManager.disconnect()

            // 清理资源
            cleanupResources()

            LogUtil.d(TAG, "LAN disconnect completed")

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during disconnect: ${e.message}")
        }
    }

    /**
     * 心跳失败时的断开处理
     *
     * 心跳失败时只断开连接，不停止mDNS服务发现，以便在设备恢复后能够重新发现并连接
     */
    private fun disconnectOnHeartbeatFailure() {
        launchInScope {
            try {
                LogUtil.d(TAG, "Disconnecting due to heartbeat failure, keeping service discovery active")

                // 不标记为主动断开（因为是被动断开）
                // isManualDisconnect 保持为 false

                // 停止心跳
                stopHeartbeat()

                // 安全更新状态
                updateStatusSafely(InnerConnectionStatus.ERROR)

                // 通知连接错误：使用基类的 notifyConnectionError()
                notifyConnectionError("Connection lost due to heartbeat failure", InnerErrorCode.E213)

                // 不停止服务发现，保持mDNS服务发现运行，以便在设备恢复后能够重新发现

                // 断开WebSocket连接
                connectionManager.disconnect()

                // 清理连接相关资源，但保留服务发现相关资源
                currentUri = null
                currentService = null
                // 注意：不调用 cleanupResources()，因为它会清理所有资源
                // 也不调用 stopServiceDiscovery()，保持服务发现运行

                LogUtil.d(TAG, "Connection disconnected due to heartbeat failure, service discovery remains active")

            } catch (e: Exception) {
                LogUtil.e(TAG, "Error during heartbeat failure disconnect: ${e.message}")
            }
        }
    }

    // ==================== 管理器设置和集成 ====================

    /**
     * 设置WebSocket连接管理器（更新版本）
     */
    private fun setupConnectionManager() {
        // 设置连接状态监听器
        connectionManager.setConnectionListener(object : ConnectionManager.ConnectionListener {
            override fun onConnected() {
                launchInScope {
                    LogUtil.d(TAG, "LAN connection established")

                    // 安全更新状态
                    updateStatusSafely(InnerConnectionStatus.CONNECTED)

                    // 创建服务信息（如果需要）
                    if (currentService == null && currentUri != null) {
                        try {
                            val host = currentUri?.host ?: ""
                            val port = currentUri?.port ?: -1
                            if (host.isNotEmpty() && port > 0) {
                                currentService = ServiceInfo(
                                    name = "",
                                    type = NSD_SERVICE_TYPE,
                                    host = host,
                                    port = port,
                                    attributes = emptyMap()
                                )
                                LogUtil.d(TAG, "Created ServiceInfo for direct connection: ${currentService?.getAddress()}")
                            }
                        } catch (e: Exception) {
                            LogUtil.w(TAG, "Failed to create ServiceInfo from URI: ${e.message}")
                        }
                    }

                    // 启动心跳
                    startHeartbeat()

                    // 启动服务监听
                    startServiceMonitoring()

                    // 统一连接成功通知出口：只使用基类的 notifyConnectionSuccess()
                    // 直接使用基类的 currentConnectionCallback，不需要额外的回调管理器
                    val connectionData = mapOf("uri" to currentUri?.toString())
                    val operationIdToClear = currentOperationId

                    // 清除当前操作ID
                    if (operationIdToClear != null) {
                        currentOperationId = null
                    }

                    // 统一通知出口：只使用基类的 notifyConnectionSuccess()
                    notifyConnectionSuccess(connectionData)
                }
            }

            override fun onDisconnected(code: Int, reason: String, remote: Boolean) {
                launchInScope {
                    LogUtil.d(
                        TAG,
                        "LAN connection closed: code=$code, reason=$reason, remote=$remote, isManual=$isManualDisconnect"
                    )

                    // 停止心跳
                    stopHeartbeat()

                    // 构建断开信息
                    val disconnectCode = if (isManualDisconnect) {
                        InnerErrorCode.E213.code
                    } else {
                        InnerErrorCode.E213.code
                    }
                    val disconnectMessage = if (isManualDisconnect) {
                        "${InnerErrorCode.E213.description}:Manual disconnect, $reason($code)"
                    } else {
                        "${InnerErrorCode.E213.description}:Passive disconnect, $reason($code)"
                    }

                    val wasManual = isManualDisconnect
                    isManualDisconnect = false

                    // 判断是否是连接错误
                    val isError = code == 1006 || code >= 1002

                    // 安全更新状态
                    if (isError) {
                        updateStatusSafely(InnerConnectionStatus.ERROR)
                    } else {
                        updateStatusSafely(InnerConnectionStatus.DISCONNECTED)
                    }

                    // 通知连接断开：使用基类的 notifyConnectionDisconnected()
                    notifyConnectionDisconnected(disconnectCode, disconnectMessage)

                    // 如果不是主动断开，且当前有连接回调，启动服务监听
                    // 使用基类的 currentConnectionCallback 判断是否有待处理的连接
                    if (!wasManual && currentConnectionCallback != null) {
                        LogUtil.d(TAG, "Passive disconnect detected, starting service monitoring")
                        if (!isServiceMonitoring) {
                            startServiceMonitoring()
                        }
                    }
                }
            }

            override fun onError(exception: Exception) {
                launchInScope {
                    LogUtil.e(TAG, "LAN connection error: ${exception.message}")

                    // 安全更新状态
                    updateStatusSafely(InnerConnectionStatus.ERROR)

                    // 通知连接错误：使用基类的 notifyConnectionError()
                    notifyConnectionError("Connection error: ${exception.message}", InnerErrorCode.E241)
                }
            }
        })

        // 设置消息监听器
        connectionManager.setMessageListener(object : ConnectionManager.MessageListener {
            override fun onTextMessage(message: String) {
                LogUtil.d(TAG, "Text message received: $message")

                // 检查是否是心跳响应
                if (heartbeatManager.isHeartbeatResponse(message)) {
                    handleHeartbeatResponse()
                    return
                }

                // 传递给数据接收器（BaseServiceKernel提供）
                dataReceiver?.invoke(message.toByteArray(Charsets.UTF_8))
            }

            override fun onBinaryMessage(data: ByteArray) {
                LogUtil.d(TAG, "Binary message received: ${data.size} bytes:$dataReceiver")

                // 传递给数据接收器（BaseServiceKernel提供）
                dataReceiver?.invoke(data)
            }
        })
    }

    // ==================== 服务发现功能 ====================

    /**
     * 处理服务地址变化的通用方法
     *
     * @param service 新的服务信息
     * @param oldHost 旧的主机地址
     * @param oldPort 旧的端口号
     * @param logPrefix 日志前缀（用于区分是 onServiceFound 还是 onServiceUpdated）
     * @return Boolean - true: 需要重连, false: 不需要重连或处理被跳过
     */
    private fun handleServiceAddressChange(
        service: ServiceInfo,
        oldHost: String,
        oldPort: Int,
        logPrefix: String = "Service address change"
    ): Boolean {
        // 检查是否应该跳过服务处理（已连接或正在连接到相同地址）
        val shouldSkip = shouldSkipServiceProcessing(service)
        if (shouldSkip) {
            LogUtil.d(
                TAG,
                "$logPrefix but already connected/connecting to same address, ignoring: ${service.getAddress()}, " +
                        "currentService=${currentService?.getAddress()}, " +
                        "isWebSocketConnected=${isWebSocketConnected()}, " +
                        "currentStatus=${currentInnerConnectionStatus}"
            )
            return false
        }

        // 防抖检查：避免短时间内重复触发
        val currentTime = System.currentTimeMillis()
        val timeSinceLastChange = currentTime - lastServiceAddressChangeTime
        if (timeSinceLastChange < SERVICE_ADDRESS_CHANGE_DEBOUNCE_MS) {
            LogUtil.d(
                TAG,
                "$logPrefix debounced, ignoring: ${service.getAddress()}, " +
                        "timeSinceLastChange=${timeSinceLastChange}ms < ${SERVICE_ADDRESS_CHANGE_DEBOUNCE_MS}ms"
            )
            return false
        }
        lastServiceAddressChangeTime = currentTime

        // 通知服务地址变化，SDK层会自动处理重连决策和执行
        LogUtil.i(
            TAG,
            "Notifying service address changed: ${service.name}, " +
                    "old=$oldHost:$oldPort, " +
                    "new=${service.host}:${service.port}"
        )

        // 使用持久监听器通知服务地址变化
        val listener = serviceAddressChangeListener
        val shouldReconnect = if (listener != null) {
            try {
                listener.onServiceAddressChanged(
                    service.name,
                    service.host,
                    service.port,
                    oldHost,
                    oldPort
                )
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error in service address change listener: ${e.message}")
                false
            }
        } else {
            LogUtil.w(TAG, "No service address change listener set, cannot notify service address change")
            false
        }
        LogUtil.d(TAG, "Service address change notification result: $shouldReconnect")
        return shouldReconnect
    }

    /**
     * 停止服务发现
     */
    private fun stopServiceDiscovery() {
        serviceDiscoveryManager.stopServiceMonitoring()
        isServiceMonitoring = false
        LogUtil.d(TAG, "Service discovery stopped")
    }

    /**
     * 启动服务监听（更新版本）
     * 监听服务变化，处理服务器IP/端口变更等情况
     */
    private fun startServiceMonitoring() {
        if (isServiceMonitoring) {
            LogUtil.d(TAG, "Service monitoring already started")
            return
        }

        try {
            LogUtil.d(TAG, "Starting service monitoring")

            val listener = object : ServiceDiscoveryManager.ServiceChangeListener {
                override fun onServiceFound(service: ServiceInfo) {
                    LogUtil.i(TAG, "New service found: ${service.name} at ${service.getAddress()}")
                    // 使用当前服务作为旧地址
                    handleServiceAddressChange(
                        service = service,
                        oldHost = currentService?.host ?: "",
                        oldPort = currentService?.port ?: -1,
                        logPrefix = "Service found"
                    )
                }

                override fun onServiceLost(service: ServiceInfo) {
                    LogUtil.w(TAG, "Service lost: ${service.name} at ${service.getAddress()}")

                    // 如果丢失的是当前连接的服务，心跳失败会先检测到并触发断开
                    if (currentService != null && service.name == currentService?.name) {
                        LogUtil.w(TAG, "Current connected service is lost, heartbeat failure will handle reconnection")
                    }
                }

                override fun onServiceUpdated(oldService: ServiceInfo, newService: ServiceInfo) {
                    LogUtil.i(
                        TAG,
                        "Service updated: ${newService.name}, ${oldService.getAddress()} -> ${newService.getAddress()}"
                    )

                    // 检查地址是否真的发生了变化
                    val addressChanged = oldService.host != newService.host || oldService.port != newService.port
                    if (!addressChanged) {
                        LogUtil.d(TAG, "Service updated but address unchanged, ignoring")
                        return
                    }

                    // 使用旧服务作为旧地址
                    handleServiceAddressChange(
                        service = newService,
                        oldHost = oldService.host,
                        oldPort = oldService.port,
                        logPrefix = "Service updated"
                    )
                }

                override fun onDiscoveryStarted(serviceType: String) {
                    LogUtil.d(TAG, "Service monitoring started for: $serviceType")
                    isServiceMonitoring = true
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    LogUtil.d(TAG, "Service monitoring stopped for: $serviceType")
                    isServiceMonitoring = false
                }

                override fun onDiscoveryFailed(serviceType: String, errorCode: Int, error: String) {
                    LogUtil.e(TAG, "Service monitoring failed: $error")
                    isServiceMonitoring = false
                }
            }

            serviceDiscoveryManager.startServiceMonitoring(NSD_SERVICE_TYPE, listener)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start service monitoring: ${e.message}")
        }
    }


    // ==================== 心跳功能 ====================

    /**
     * 启动心跳
     */
    private fun startHeartbeat() {
        try {
            val config = HeartbeatManager.HeartbeatConfig()
            val sender = object : HeartbeatManager.HeartbeatSender {
                override suspend fun sendHeartbeat(message: String): Boolean {
                    // 使用带网络连通性检查的心跳发送方法，确保网络可达后再发送
                    return connectionManager.sendHeartbeatWithConnectivityCheck(message)
                }
            }

            // 设置心跳监听器
            heartbeatManager.setHeartbeatListener(object : HeartbeatManager.HeartbeatListener {
                override fun onHeartbeatSent(sentTime: Long) {
                    LogUtil.d(TAG, "Heartbeat sent at: $sentTime")
                }

                override fun onHeartbeatResponse(responseTime: Long, roundTripTime: Long) {
                    LogUtil.d(TAG, "Heartbeat response received, RTT: ${roundTripTime}ms")
                }

                override fun onHeartbeatDelayed(timeoutMs: Long, ratio: Double) {
                    LogUtil.w(
                        TAG,
                        "Heartbeat response delayed: ${(timeoutMs * ratio).toLong()}ms (${(ratio * 100).toInt()}% of timeout)"
                    )
                }

                override fun onHeartbeatTimeout(timeoutMs: Long, consecutiveFailures: Int) {
                    LogUtil.w(TAG, "Heartbeat timeout: ${timeoutMs}ms, consecutive failures: $consecutiveFailures")

                    // 如果连续失败次数过多，可以考虑重连
                    if (consecutiveFailures >= 2) {
                        LogUtil.w(TAG, "Multiple heartbeat timeouts, connection may be unstable")
                    }
                }

                override fun onHeartbeatFailed(error: String, consecutiveFailures: Int) {
                    LogUtil.e(TAG, "Heartbeat failed: $error, consecutive failures: $consecutiveFailures")

                    // 心跳连续失败，可能连接已断开
                    if (consecutiveFailures >= 2) { // 降低阈值，更快检测问题
                        LogUtil.e(TAG, "Too many heartbeat failures, disconnecting connection but keeping service discovery")
                        // 通知连接错误
                        notifyConnectionError("Heartbeat failed: $error", InnerErrorCode.E241)
                        // 心跳失败时断开连接，但不停止mDNS服务发现，以便在设备恢复后能够重新发现并连接
                        disconnectOnHeartbeatFailure()
                    }
                }

                override fun onConfigUpdated(
                    oldConfig: HeartbeatManager.HeartbeatConfig,
                    newConfig: HeartbeatManager.HeartbeatConfig
                ) {
                    LogUtil.d(TAG, "Heartbeat config updated: ${oldConfig.intervalMs}ms -> ${newConfig.intervalMs}ms")
                }
            })

            heartbeatManager.startHeartbeat(config, sender)
            LogUtil.d(TAG, "Heartbeat started")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start heartbeat: ${e.message}")
        }
    }

    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        try {
            heartbeatManager.stopHeartbeat()
            LogUtil.d(TAG, "Heartbeat stopped")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to stop heartbeat: ${e.message}")
        }
    }

    /**
     * 处理心跳响应
     */
    private fun handleHeartbeatResponse() {
        heartbeatManager.handleHeartbeatResponse()
        LogUtil.d(TAG, "Heartbeat response processed")
    }

    // ==================== 资源管理 ====================

    /**
     * 清理资源（更新版本）
     *
     * 注意：此方法用于清理所有状态和资源，确保复用前状态是干净的
     */
    private fun cleanupResources() {
        // 清理回调
        // 重置状态变量
        currentUri = null
        currentService = null
        isServiceMonitoring = false
        isManualDisconnect = false
        lastServiceAddressChangeTime = 0
        currentOperation = null
        currentOperationId = null

        // 确保所有管理器组件完全清理
        try {
            stopHeartbeat()
            stopServiceDiscovery()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during cleanup: ${e.message}")
        }

        LogUtil.d(TAG, "Resources cleaned up")
    }

    // ==================== 重写基类方法（如需要） ====================

    override fun cleanupCommonResources() {
        // 先执行自己的清理
        cleanupResources()

        // 再调用基类的清理
        super.cleanupCommonResources()
    }

    // ==================== 新增公共方法 ====================

    /**
     * 获取当前操作状态
     */
    fun getCurrentOperation(): String? = currentOperation

    /**
     * 获取当前操作ID
     */
    fun getCurrentOperationId(): String? = currentOperationId

    /**
     * 检查是否有操作在进行
     */
    fun hasOperationInProgress(): Boolean = currentOperation != null
}