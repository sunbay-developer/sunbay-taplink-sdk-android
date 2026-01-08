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
import java.net.URI

/**
 * LAN客户端服务内核类
 *
 * 重组后的架构：
 * - 正确继承AsyncServiceKernel，充分利用基础功能
 * - 使用管理器模式分解复杂职责
 * - 专注于LAN网络通信（主要是WebSocket）
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
    }

    override fun getTag(): String = TAG

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

    // ==================== AsyncServiceKernel 抽象方法实现 ====================

    override fun getServiceType(): String = "LAN"

    override fun getExpectedProtocolType(): String = "lan protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.LanProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        // 如果是复用场景（状态是 DISCONNECTED），确保状态是干净的
        // 这可以避免复用时的状态残留问题
        if (currentInnerConnectionStatus == InnerConnectionStatus.DISCONNECTED) {
            LogUtil.d(TAG, "Reusing kernel, ensuring clean state before connect")
            cleanupResources()
        }

        val lanResult = parseResult as ProtocolParseResult.LanProtocol

        launchInScope {
            try {
                LogUtil.d(TAG, "Performing LAN connection to: ${lanResult.ip}:${lanResult.port}")

                // 构建WebSocket URI
                val uri = try {
                    URI("ws://${lanResult.ip}:${lanResult.port}")
                } catch (e: Exception) {
                    notifyConnectionError(
                        "Invalid URI: ${lanResult.ip}:${lanResult.port}",
                        InnerErrorCode.C59
                    )
                    return@launchInScope
                }

                currentUri = uri

                // 尝试直接连接
                val result = connectionManager.connect(uri)
                when (result) {
                    is ConnectionManager.ConnectionResult.Success -> {
                        LogUtil.d(TAG, "Direct connection successful")
                        // 连接成功会通过connectionManager的监听器处理
                        // ConnectionManager.connect() 会等待 isConnected.get() 为 true 后才返回 Success
                        // isConnected 是在 onOpen() 回调中设置为 true 的
                        // onOpen() 回调会调用 connectionListener.onConnected()
                        // onConnected() 会调用 notifyConnectionSuccess()，它会更新状态为 CONNECTED
                        // 
                        // 所以理论上，当 ConnectionResult.Success 返回时，状态应该已经更新为 CONNECTED
                        // 但为了确保状态一致性，这里检查状态，如果状态不是 CONNECTED，说明 onOpen 回调可能还没完成
                        // 等待一小段时间让回调完成，然后再次检查
                        if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED) {
                            LogUtil.d(TAG, "Connection successful but status not updated yet (current: $currentInnerConnectionStatus), waiting for onOpen callback")
                            // 等待一小段时间，让 onOpen 回调完成（虽然理论上应该已经完成了）
                            delay(50)
                            // 再次检查状态
                            if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED) {
                                LogUtil.w(TAG, "Status still not updated after wait (current: $currentInnerConnectionStatus), onOpen callback may not have been triggered properly")
                                // 如果状态仍然没有更新，可能是 onOpen 回调没有触发或执行失败
                                // 这种情况下，应该手动更新状态，确保状态一致性
                                // 因为连接已经成功（ConnectionResult.Success），状态应该是 CONNECTED
                                updateStatus(InnerConnectionStatus.CONNECTED)
                                LogUtil.d(TAG, "Manually updated status to CONNECTED to ensure consistency")
                            }
                        } else {
                            LogUtil.d(TAG, "Connection successful and status already updated to CONNECTED")
                        }
                    }

                    is ConnectionManager.ConnectionResult.Failure -> {
                        LogUtil.w(TAG, "Direct connection failed: ${result.error}")
                        // 尝试服务发现
                        tryServiceDiscovery(connectionCallback)
                    }
                }

            } catch (e: Exception) {
                LogUtil.e(TAG, "Error in performConnect: ${e.message}")
                notifyConnectionError("Connection failed: ${e.message}")
            }
        }
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
                callback?.onError(InnerErrorCode.C62.code, InnerErrorCode.C62.description)
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending data: traceId=$traceId, error=${e.message}")
            callback?.onError(InnerErrorCode.C62.code, e.message ?: InnerErrorCode.C62.description)
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
        try {
            LogUtil.d(TAG, "Disconnecting due to heartbeat failure, keeping service discovery active")

            // 不标记为主动断开（因为是被动断开）
            // isManualDisconnect 保持为 false

            // 停止心跳
            stopHeartbeat()

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

    // ==================== 管理器设置和集成 ====================

    /**
     * 设置WebSocket连接管理器
     */
    private fun setupConnectionManager() {
        // 设置连接状态监听器
        connectionManager.setConnectionListener(object : ConnectionManager.ConnectionListener {
            override fun onConnected() {
                LogUtil.d(TAG, "LAN connection established")

                // 如果currentService为空，根据currentUri创建ServiceInfo
                // 这样可以确保即使是通过直接连接（IP:Port）方式连接成功，也能有ServiceInfo
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

                // 启动服务监听（监控服务变化）
                startServiceMonitoring()

                // 通知连接成功（使用AsyncServiceKernel提供的方法）
                notifyConnectionSuccess(mapOf("uri" to currentUri?.toString()))
            }

            override fun onDisconnected(code: Int, reason: String, remote: Boolean) {
                LogUtil.d(TAG, "LAN connection closed: code=$code, reason=$reason, remote=$remote, isManual=$isManualDisconnect")

                // 停止心跳
                stopHeartbeat()

                // 构建断开信息，包含是否为主动断开的信息
                val disconnectCode = if (isManualDisconnect) {
                    InnerErrorCode.C45.code
                } else {
                    InnerErrorCode.C36.code
                }
                val disconnectMessage = if (isManualDisconnect) {
                    "${InnerErrorCode.C45.description}:Manual disconnect, $reason($code)"
                } else {
                    "${InnerErrorCode.C36.description}:Passive disconnect, $reason($code)"
                }

                // 保存是否为主动断开的标志
                val wasManual = isManualDisconnect

                // 重置标志（为下次连接做准备）
                isManualDisconnect = false

                // 判断是否是连接错误（code 1006 通常是异常断开）
                val isError = code == 1006 || code >= 1002

                // 更新 Kernel 状态（必须在通知断开之前更新，确保状态同步正确）
                if (isError) {
                    updateStatus(InnerConnectionStatus.ERROR)
                } else {
                    updateStatus(InnerConnectionStatus.DISCONNECTED)
                }

                // 通知连接断开（使用AsyncServiceKernel提供的方法）
                notifyConnectionDisconnected(disconnectCode, disconnectMessage)

                // 如果不是主动断开，启动服务监听以尝试重新连接
                // 注意：不调用 tryServiceDiscovery()，因为它是一次性发现，可能在重连时被取消
                // 而是启动持续的服务监听，通过 onServiceFound 回调触发重连
                if (!wasManual && currentConnectionCallback != null) {
                    LogUtil.d(TAG, "Passive disconnect detected, starting service monitoring to find available services")
                    // 如果服务监听未启动，则启动它
                    // 如果已经启动，则保持运行状态（不断开时不会停止服务监听）
                    if (!isServiceMonitoring) {
                        startServiceMonitoring()
                    } else {
                        LogUtil.d(TAG, "Service monitoring already running, will trigger reconnection when service is found")
                    }
                }
            }

            override fun onError(exception: Exception) {
                LogUtil.e(TAG, "LAN connection error: ${exception.message}")

                // 通知连接错误（使用AsyncServiceKernel提供的方法）
                notifyConnectionError("Connection error: ${exception.message}")
            }
        })

        // 设置消息监听器
        connectionManager.setMessageListener(object : ConnectionManager.MessageListener {
            override fun onTextMessage(message: String) {
                LogUtil.d(TAG, "Text message received: $message")

                // 检查是否是心跳响应
                if (isHeartbeatResponse(message)) {
                    handleHeartbeatResponse()
                    return
                }

                // 传递给数据接收器（BaseServiceKernel提供）
                dataReceiver?.invoke(message.toByteArray(Charsets.UTF_8))
            }

            override fun onBinaryMessage(data: ByteArray) {
                LogUtil.d(TAG, "Binary message received: ${data.size} bytes")

                // 传递给数据接收器（BaseServiceKernel提供）
                dataReceiver?.invoke(data)
            }
        })
    }

    // ==================== 服务发现功能 ====================

    /**
     * 尝试服务发现
     */
    private suspend fun tryServiceDiscovery(connectionCallback: ConnectionCallback) {
        try {
            LogUtil.d(TAG, "Starting service discovery")

            val services = serviceDiscoveryManager.discoverServices(NSD_SERVICE_TYPE)
            if (services.isNotEmpty()) {
                LogUtil.d(TAG, "Found ${services.size} services, trying to connect")

                var hasValidService = false
                for (service in services) {
                    if (service.isValid()) {
                        hasValidService = true
                        // 通知服务地址变化，由 onServiceAddressChanged 判断是否需要重连
                        val shouldReconnect = connectionCallback.onServiceAddressChanged(
                            service.name,
                            service.host, service.port,
                            currentService?.host ?: "", currentService?.port ?: -1
                        )

                        if (shouldReconnect) {
                            LogUtil.d(TAG, "Service address changed, reconnection will be handled by ConnectionManager")
                            // onServiceAddressChanged 返回 true 表示会触发重连，由 ReconnectManager 处理
                            return
                        } else {
                            LogUtil.d(
                                TAG,
                                "Service address change ignored (same device/address or different device): ${service.getAddress()}"
                            )
                            // 如果不需要重连，可能是已经连接到相同的服务，这是正常情况，不应该报告错误
                            // 直接返回，不继续处理
                            return
                        }
                    }
                }

                // 如果找到了服务，但没有有效的服务，不应该报告错误
                // 因为可能是服务正在解析中，或者服务无效
                if (hasValidService) {
                    LogUtil.d(TAG, "Found services but no reconnection needed, connection may already be established")
                    return
                }
            }

            // 只有在真正没有找到任何服务时，才报告错误
            // 注意：如果当前已经连接，不应该调用此方法，或者应该检查当前连接状态
            val currentStatus = currentInnerConnectionStatus
            if (currentStatus == InnerConnectionStatus.CONNECTED ||
                currentStatus == InnerConnectionStatus.CONNECTING
            ) {
                LogUtil.d(TAG, "Service discovery found no services, but already connected/connecting, ignoring error")
                return
            }

            LogUtil.w(TAG, "No available services found via service discovery")
            notifyConnectionError("No available services found", InnerErrorCode.C60)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Service discovery failed: ${e.message}")
            // 如果当前已经连接，不应该因为服务发现失败而报告错误
            val currentStatus = currentInnerConnectionStatus
            if (currentStatus == InnerConnectionStatus.CONNECTED ||
                currentStatus == InnerConnectionStatus.CONNECTING
            ) {
                LogUtil.d(TAG, "Service discovery failed, but already connected/connecting, ignoring error")
                return
            }
            notifyConnectionError("Service discovery failed: ${e.message}", InnerErrorCode.C35)
        }
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
     * 启动服务监听
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
                    LogUtil.d(TAG, "New service found: ${service.name} at ${service.getAddress()}")

                    // 检查当前连接状态，如果已连接或正在连接，且地址相同，则不触发重连
                    val currentStatus = currentInnerConnectionStatus
                    val isSameAddress = currentService != null &&
                            currentService?.host == service.host &&
                            currentService?.port == service.port

                    if (isSameAddress &&
                        (currentStatus == InnerConnectionStatus.CONNECTED ||
                                currentStatus == InnerConnectionStatus.CONNECTING ||
                                currentStatus == InnerConnectionStatus.WAITING_CONNECT)
                    ) {
                        LogUtil.d(
                            TAG,
                            "Service found but already connected/connecting to same address, ignoring: ${service.getAddress()}"
                        )
                        return
                    }

                    // 防抖检查：避免短时间内重复触发
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastServiceAddressChangeTime < SERVICE_ADDRESS_CHANGE_DEBOUNCE_MS) {
                        LogUtil.d(TAG, "Service address change debounced, ignoring: ${service.getAddress()}")
                        return
                    }
                    lastServiceAddressChangeTime = currentTime

                    // 通知服务地址变化，由 onServiceAddressChanged 判断是否需要重连
                    currentConnectionCallback?.onServiceAddressChanged(
                        service.name,
                        service.host,
                        service.port,
                        currentService?.host ?: "",
                        currentService?.port ?: -1
                    )
                }

                override fun onServiceLost(service: ServiceInfo) {
                    LogUtil.w(TAG, "Service lost: ${service.name} at ${service.getAddress()}")

                    // 如果丢失的是当前连接的服务，需要处理
                    if (currentService != null && service.name == currentService?.name) {
                        LogUtil.w(TAG, "Current connected service is lost, may need to reconnect")
                        // 这里可以触发重连逻辑，但通常心跳失败会先检测到
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

                    // 检查当前连接状态，如果已连接或正在连接，且新地址与当前地址相同，则不触发重连
                    val currentStatus = currentInnerConnectionStatus
                    val isSameAddress = currentService != null &&
                            currentService?.host == newService.host &&
                            currentService?.port == newService.port

                    if (isSameAddress &&
                        (currentStatus == InnerConnectionStatus.CONNECTED ||
                                currentStatus == InnerConnectionStatus.CONNECTING ||
                                currentStatus == InnerConnectionStatus.WAITING_CONNECT)
                    ) {
                        LogUtil.d(
                            TAG,
                            "Service updated but already connected/connecting to new address, ignoring: ${newService.getAddress()}"
                        )
                        return
                    }

                    // 防抖检查：避免短时间内重复触发
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastServiceAddressChangeTime < SERVICE_ADDRESS_CHANGE_DEBOUNCE_MS) {
                        LogUtil.d(TAG, "Service address change debounced, ignoring: ${newService.getAddress()}")
                        return
                    }
                    lastServiceAddressChangeTime = currentTime

                    // 如果更新的是当前连接的服务，且地址发生变化
                    currentConnectionCallback?.onServiceAddressChanged(
                        newService.name,
                        newService.host,
                        newService.port,
                        oldService.host,
                        oldService.port
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

    /**
     * 处理服务地址变更
     * 当检测到当前连接的服务地址发生变化时，尝试重新连接
     */
    private fun handleServiceAddressChange(newService: ServiceInfo) {
        launchInScope {
            try {
                LogUtil.i(TAG, "Handling service address change to: ${newService.getAddress()}")

                // 构建新的URI
                val newUri = URI(newService.getWebSocketUri())

                // 如果新地址与当前连接相同，无需处理
                if (newUri == currentUri) {
                    LogUtil.d(TAG, "Service address unchanged, no action needed")
                    return@launchInScope
                }

                // 尝试连接到新地址
                val result = connectionManager.connect(newUri)
                when (result) {
                    is ConnectionManager.ConnectionResult.Success -> {
                        LogUtil.i(TAG, "Successfully reconnected to updated service: $newUri")
                        currentUri = newUri
                        currentService = newService

                        // 重新启动心跳（连接管理器会自动处理）
                        // 连接成功的处理会通过connectionListener触发
                    }

                    is ConnectionManager.ConnectionResult.Failure -> {
                        LogUtil.w(TAG, "Failed to reconnect to updated service: ${result.error}")
                        // 保持当前连接，等待心跳检测到问题
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error handling service address change: ${e.message}")
            }
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
                override fun sendHeartbeat(message: String): Boolean {
                    return connectionManager.send(message)
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
                    if (consecutiveFailures >= 3) {
                        LogUtil.e(TAG, "Too many heartbeat failures, disconnecting connection but keeping service discovery")
                        // 通知连接错误
                        notifyConnectionError("Heartbeat failed: $error")
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
     * 检查是否是心跳响应
     */
    private fun isHeartbeatResponse(message: String): Boolean {
        return message.contains("pong") || message.contains("ping")
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
     * 清理资源
     *
     * 注意：此方法用于清理所有状态和资源，确保复用前状态是干净的
     */
    private fun cleanupResources() {
        // 重置状态变量
        currentUri = null
        currentService = null
        isServiceMonitoring = false
        isManualDisconnect = false  // 重置主动断开标志，避免复用后状态不正确
        lastServiceAddressChangeTime = 0  // 重置防抖时间戳，避免复用后防抖逻辑失效

        // 确保所有管理器组件完全清理
        try {
            stopHeartbeat()  // 确保心跳停止，避免资源泄漏
            stopServiceDiscovery()  // 确保服务发现停止，避免状态不一致
            // 注意：不在这里调用 connectionManager.disconnect()，因为 disconnect() 会触发回调
            // 应该在 performDisconnect() 中调用
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
}