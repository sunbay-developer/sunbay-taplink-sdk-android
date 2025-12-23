package com.sunmi.tapro.taplink.communication.lan

import android.content.Context
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.utils.SmartHeartbeatConfig
import com.sunmi.tapro.taplink.communication.utils.NetworkQualityMonitor
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.URI
import java.nio.ByteBuffer

/**
 * WebSocket 客户端服务内核类
 *
 * 功能特性：
 * 1. WebSocket 客户端连接
 * 2. 心跳机制保持连接活跃
 * 3. mDNS 服务发现（连接失败时自动查找设备）
 * 4. 线程安全的数据发送
 *
 * 注意：重连逻辑由 SDK 层（ReconnectManager）统一管理，Service 层只负责底层通信
 *
 * @param appId 应用ID
 * @param appSecretKey 应用密钥
 * @param context Android上下文，用于mDNS服务
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class WebSocketClientKernel constructor(
    private val appId: String,
    private val appSecretKey: String,
    private val context: Context
) : BaseServiceKernel(), 
    SmartHeartbeatConfig.HeartbeatConfigChangeListener,
    NetworkQualityMonitor.NetworkQualityChangeListener {

    companion object {
        private const val TAG = "WebSocketClientKernel"

        // 默认心跳配置（作为后备）
        private const val DEFAULT_HEARTBEAT_INTERVAL = 30_000L
        private const val DEFAULT_HEARTBEAT_TIMEOUT = 10_000L
        private const val HEARTBEAT_MESSAGE = "{\"type\":\"ping\"}"

        // NSD配置
        private const val NSD_SERVICE_TYPE = "_taplink._tcp"
        private const val NSD_DISCOVERY_TIMEOUT = 30_000L // 增加到30秒以提高发现成功率
        private const val NSD_RESOLUTION_TIMEOUT = 5_000L // 服务解析超时时间
    }

    override fun getTag(): String = TAG

    /**
     * 协程作用域
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 发送数据互斥锁
     */
    private val sendMutex = Mutex()

    /**
     * 智能心跳配置管理器
     */
    private val smartHeartbeatConfig = SmartHeartbeatConfig.getInstance()
    
    /**
     * 网络质量监测器
     */
    private val networkQualityMonitor = NetworkQualityMonitor.getInstance(context)

    /**
     * 当前WebSocket客户端连接实例
     */
    private var client: TaplinkWebSocketClient? = null

    /**
     * 当前连接的服务URI
     */
    private var currentUri: URI? = null

    /**
     * 当前连接回调
     */
    private var currentConnectionCallback: ConnectionCallback? = null

    /**
     * 心跳任务
     */
    private var heartbeatJob: Job? = null

    /**
     * 最后一次心跳响应时间
     */
    private var lastHeartbeatResponse = 0L
    
    /**
     * 心跳失败计数器
     */
    private var heartbeatFailureCount = 0

    /**
     * NSD Manager 实例
     */
    private var nsdManager: NsdManager? = null

    /**
     * NSD 发现监听器
     */
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * NSD 发现的服务列表
     */
    private val discoveredServices = mutableListOf<ServiceInfo>()

    /**
     * 服务解析计数器（用于同步）
     */
    private var pendingResolutions = 0
    private val resolutionMutex = Mutex()

    /**
     * 当前连接的服务信息
     */
    private var currentServiceInfo: ServiceInfo? = null

    /**
     * 是否启用服务监听（用于检测 IP/端口变化）
     */
    private var isServiceMonitoringEnabled = false

    /**
     * 实现IServiceKernel接口：连接WebSocket服务器
     *
     * @param protocol 协议字符串，支持 ws://、wss:// 和 lan:// 格式
     * @param connectionCallback 连接回调
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "WebSocket client is already connected or connecting. Current status: $currentInnerConnectionStatus",
                connectionCallback,
                InnerErrorCode.C33.code
            )
            return
        }

        try {
            LogUtil.d(TAG, "Starting connection with protocol: $protocol")
            
            // 初始化智能心跳系统
            initializeSmartHeartbeat()
            
            updateStatus(InnerConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()
            // 保存连接回调
            currentConnectionCallback = connectionCallback

            // 解析协议并连接
            connectWithProtocol(protocol, connectionCallback)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start connection: ${e.message}")
            handleConnectionError(
                "Failed to start connection: ${e.message}",
                connectionCallback,
                InnerErrorCode.C33.code
            )
        }
    }

    /**
     * 根据协议字符串进行连接
     */
    private fun connectWithProtocol(protocol: String, connectionCallback: ConnectionCallback) {
        scope.launch {
            try {
                // 使用统一的协议管理器解析协议字符串
                val parseResult = ProtocolManager.parseProtocol(protocol)
                if (parseResult is ProtocolParseResult.Error) {
                    handleConnectionError(
                        parseResult.message, connectionCallback,
                        InnerErrorCode.C41.code
                    )
                    return@launch
                }

                if (parseResult !is ProtocolParseResult.LanProtocol) {
                    handleConnectionError(
                        "Protocol type mismatch, expected websocket or lan protocol",
                        connectionCallback,
                        InnerErrorCode.C41.code
                    )
                    return@launch
                }

                // 使用统一的协议管理器转换为WebSocket URI
                val uri = ProtocolManager.lanProtocolToWebSocketUri(protocol)
                if (uri == null) {
                    handleConnectionError(
                        "Failed to convert protocol to WebSocket URI: $protocol",
                        connectionCallback,
                        InnerErrorCode.C41.code
                    )
                    return@launch
                }

                // 尝试直接连接
                if (connectToUri(uri, connectionCallback)) {
                    return@launch
                }

                // 直接连接失败，尝试 NSD 发现
                LogUtil.d(TAG, "Direct connection failed, starting NSD discovery")
                discoverAndConnect(connectionCallback)

            } catch (e: Exception) {
                LogUtil.e(TAG, "Error in connectWithProtocol: ${e.message}")
                handleConnectionError(
                    "Connection error: ${e.message}",
                    connectionCallback,
                    InnerErrorCode.C33.code
                )
            }
        }
    }

    /**
     * 连接到指定URI
     */
    private suspend fun connectToUri(uri: URI, connectionCallback: ConnectionCallback): Boolean {
        return try {
            LogUtil.d(TAG, "Attempting to connect to: $uri")

            // 保存当前URI
            currentUri = uri

            // 如果已有连接，先断开
            client?.let {
                LogUtil.d(TAG, "Closing existing connection")
                it.close(1000, "Reconnecting")
                client = null
            }

            // 创建新的WebSocket客户端
            client = TaplinkWebSocketClient(uri, connectionCallback)

            // 连接（这是异步的，结果会通过回调返回）
            client?.connect()

            // 等待连接结果（通过状态变化判断）
            var attempts = 0
            while (attempts < 50 && currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING) { // 最多等待5秒
                delay(100)
                attempts++
            }

            currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to $uri: ${e.message}")
            false
        }
    }

    /**
     * NSD 服务发现并连接
     */
    private suspend fun discoverAndConnect(connectionCallback: ConnectionCallback) {
        try {
            LogUtil.d(TAG, "Starting NSD service discovery")
            // 初始化 NSD Manager
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            discoveredServices.clear()
            pendingResolutions = 0

            // 创建发现监听器
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    LogUtil.e(TAG, "NSD discovery start failed: $serviceType, errorCode: $errorCode")
                    connectionCallback.onDisconnected(
                        InnerErrorCode.C35.code,
                        "${InnerErrorCode.C35.description}:(serviceType:$serviceType,errorCode:$errorCode)"
                    )
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    LogUtil.e(TAG, "NSD discovery stop failed: $serviceType, errorCode: $errorCode")
                }

                override fun onDiscoveryStarted(serviceType: String) {
                    LogUtil.d(TAG, "NSD discovery started: $serviceType")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    LogUtil.d(TAG, "NSD discovery stopped: $serviceType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    LogUtil.d(TAG, "NSD service found: ${serviceInfo.serviceName}")
                    
                    // 增加待解析计数
                    scope.launch {
                        resolutionMutex.withLock {
                            pendingResolutions++
                        }
                    }
                    
                    // 解析服务信息
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            LogUtil.e(TAG, "NSD resolve failed: ${serviceInfo.serviceName}, errorCode: $errorCode")
                            
                            // 减少待解析计数
                            scope.launch {
                                resolutionMutex.withLock {
                                    pendingResolutions--
                                }
                            }
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            LogUtil.d(TAG, "NSD service resolved: ${serviceInfo.serviceName}")
                            val host = serviceInfo.host?.hostAddress ?: ""
                            val port = serviceInfo.port

                            if (host.isNotEmpty() && port > 0) {
                                val service = ServiceInfo(
                                    name = serviceInfo.serviceName,
                                    host = host,
                                    port = port
                                )
                                
                                synchronized(discoveredServices) {
                                    discoveredServices.add(service)
                                }
                                
                                LogUtil.d(TAG, "Added discovered service: $service")
                                
                                // 记录服务属性（用于调试）
                                logServiceAttributes(serviceInfo)
                            }
                            
                            // 减少待解析计数
                            scope.launch {
                                resolutionMutex.withLock {
                                    pendingResolutions--
                                }
                            }
                        }
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    LogUtil.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")

                    // 从发现的服务列表中移除丢失的服务
                    val removedServices = synchronized(discoveredServices) {
                        discoveredServices.removeAll { service ->
                            service.name == serviceInfo.serviceName
                        }
                    }

                    if (removedServices) {
                        LogUtil.d(TAG, "Removed lost service from discovered list: ${serviceInfo.serviceName}")
                    }
                }
            }

            // 开始服务发现
            nsdManager?.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            // 等待发现服务，但要考虑服务解析时间
            val discoveryStartTime = System.currentTimeMillis()
            var discoveryComplete = false
            
            while (!discoveryComplete && (System.currentTimeMillis() - discoveryStartTime) < NSD_DISCOVERY_TIMEOUT) {
                delay(1000) // 每秒检查一次
                
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - discoveryStartTime
                
                // 检查是否有服务被发现
                val serviceCount = synchronized(discoveredServices) { discoveredServices.size }
                val pendingCount = resolutionMutex.withLock { pendingResolutions }
                
                LogUtil.d(TAG, "Discovery progress: ${elapsedTime}ms elapsed, $serviceCount services found, $pendingCount pending resolutions")
                
                // 如果发现了服务且没有待解析的服务，可以提前结束
                if (serviceCount > 0 && pendingCount == 0 && elapsedTime > 5000) { // 至少等待5秒
                    LogUtil.d(TAG, "Early discovery completion: found $serviceCount services")
                    discoveryComplete = true
                }
            }

            // 停止服务发现
            try {
                discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error stopping NSD discovery: ${e.message}")
            }

            // 等待剩余的服务解析完成
            val maxWaitForResolution = 5000L
            val resolutionStartTime = System.currentTimeMillis()
            while (resolutionMutex.withLock { pendingResolutions } > 0 && 
                   (System.currentTimeMillis() - resolutionStartTime) < maxWaitForResolution) {
                delay(100)
            }

            val finalServiceCount = synchronized(discoveredServices) { discoveredServices.size }
            LogUtil.d(TAG, "Discovery completed with $finalServiceCount services")

//            // 尝试连接发现的服务
            if (finalServiceCount > 0) {
                val servicesCopy = synchronized(discoveredServices) { discoveredServices.toList() }
//
//                for (service in servicesCopy) {
//                    if (service.host.isNotEmpty() && service.port > 0) {
//                        LogUtil.d(TAG, "Attempting to connect to discovered service: ${service.host}:${service.port}")
//
//                        val uri = URI("ws://${service.host}:${service.port}")
//                        if (connectToUri(uri, connectionCallback)) {
//                            LogUtil.d(TAG, "Successfully connected to discovered service: $uri")
//                            return
//                        } else {
//                            LogUtil.w(TAG, "Failed to connect to discovered service: $uri")
//                        }
//                    }
//                }
                
                // 如果直接连接都失败了，尝试通过回调通知上层
                LogUtil.d(TAG, "Direct connections failed, notifying upper layer via callback")
                for (service in servicesCopy) {
                    if (service.host.isNotEmpty() && service.port > 0) {
                        val result = connectionCallback.onServiceAddressChanged(
                            service.name,
                            service.host,
                            service.port,
                            currentServiceInfo?.host ?: "",
                            currentServiceInfo?.port ?: -1
                        )
                        LogUtil.d(
                            TAG, "Callback result for service ${service.name} (${service.host}:${service.port}): $result"
                        )
                        if (result) {
                            return
                        }
                    }
                }
            }

            // 没有找到可用的服务
            handleConnectionError(
                "No available Taplink services found via NSD (discovered: $finalServiceCount services)",
                connectionCallback,
                InnerErrorCode.C35.code
            )

        } catch (e: Exception) {
            LogUtil.e(TAG, "NSD discovery failed: ${e.message}")
            handleConnectionError(
                "NSD discovery failed: ${e.message}",
                connectionCallback,
                InnerErrorCode.C35.code
            )
        } finally {
            // 清理资源
            try {
                discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error cleaning up NSD: ${e.message}")
            }
            discoveryListener = null
            nsdManager = null
        }
    }

    /**
     * 实现IServiceKernel接口：发送数据
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.d(TAG, "Cannot send data, not connected. Status: $currentInnerConnectionStatus")
            callback?.onError("NOT_CONNECTED", "WebSocket client not connected")
            return
        }

        if (client == null) {
            LogUtil.e(TAG, "WebSocket client is null, cannot send data")
            callback?.onError("WEBSOCKET_NULL", "WebSocket client interface is null")
            return
        }

        // 在协程中执行，使用Mutex保证并发安全
        scope.launch {
            sendMutex.withLock {
                try {
                    LogUtil.d(TAG, "Sending data via WebSocket: traceId=$traceId, dataSize=${data.size} bytes")
                    // 发送数据
                    client?.send(data)
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to send data via WebSocket: traceId=$traceId, error=${e.message}")
                    callback?.onError("SEND_FAILED", e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * 实现IServiceKernel接口：断开连接
     */
    override fun disconnect() {
        if (currentInnerConnectionStatus == InnerConnectionStatus.DISCONNECTED) {
            return
        }

        try {
            LogUtil.d(TAG, "Disconnect requested")

            // 停止心跳
            stopHeartbeat()

            // 断开WebSocket连接
            disconnectInternal()

            // 清理资源
            cleanupResources()

            updateStatus(InnerConnectionStatus.DISCONNECTED)
            LogUtil.d(TAG, "WebSocket client disconnected manually")

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during manual disconnect: ${e.message}")
            updateStatus(InnerConnectionStatus.ERROR)
        }
    }

    /**
     * 断开连接（内部方法）
     */
    private fun disconnectInternal() {
        try {
            client?.close(1000, "Normal closure")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error closing WebSocket client: ${e.message}")
        }
    }

    /**
     * 清理资源
     */
    private fun cleanupResources() {
        client = null
        currentUri = null
        currentConnectionCallback = null
        dataReceiver = null

        // 停止服务监听
        stopServiceMonitoring()

        // 清理智能心跳系统
        cleanupSmartHeartbeat()

        // 清理服务信息
        currentServiceInfo = null

        // 清理 NSD
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            discoveryListener = null
            nsdManager = null
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error cleaning up NSD: ${e.message}")
        }

        discoveredServices.clear()
    }

    /**
     * 初始化智能心跳系统
     */
    private fun initializeSmartHeartbeat() {
        // 注册心跳配置变化监听
        smartHeartbeatConfig.addConfigChangeListener(this)
        
        // 启动网络质量监测
        networkQualityMonitor.addQualityChangeListener(this)
        networkQualityMonitor.startMonitoring()
        
        // 重置心跳失败计数
        heartbeatFailureCount = 0
        
        LogUtil.d(TAG, "Smart heartbeat system initialized")
    }
    
    /**
     * 清理智能心跳系统
     */
    private fun cleanupSmartHeartbeat() {
        smartHeartbeatConfig.removeConfigChangeListener(this)
        networkQualityMonitor.removeQualityChangeListener(this)
        networkQualityMonitor.stopMonitoring()
        
        LogUtil.d(TAG, "Smart heartbeat system cleaned up")
    }

    /**
     * 启动智能心跳机制
     */
    private fun startHeartbeat() {
        stopHeartbeat() // 先停止现有的心跳

        heartbeatJob = scope.launch {
            LogUtil.d(TAG, "Starting smart heartbeat mechanism")
            lastHeartbeatResponse = System.currentTimeMillis()
            heartbeatFailureCount = 0

            while (isActive && currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                try {
                    // 使用智能心跳配置获取当前间隔
                    val currentInterval = smartHeartbeatConfig.getCurrentInterval()
                    val currentTimeout = smartHeartbeatConfig.getCurrentConfig().timeout
                    val maxRetryCount = smartHeartbeatConfig.getCurrentConfig().maxRetryCount
                    
                    delay(currentInterval)

                    // 检查上次心跳响应时间
                    val now = System.currentTimeMillis()
                    if (now - lastHeartbeatResponse > currentInterval + currentTimeout) {
                        heartbeatFailureCount++
                        LogUtil.w(TAG, "Heartbeat timeout detected (${heartbeatFailureCount}/$maxRetryCount), connection may be lost")
                        
                        if (heartbeatFailureCount >= maxRetryCount) {
                            LogUtil.e(TAG, "Max heartbeat failures reached, treating as connection lost")
                            handleConnectionLost()
                            break
                        } else {
                            // 继续尝试，但记录失败次数
                            LogUtil.d(TAG, "Heartbeat timeout, but within retry limit, continuing...")
                        }
                    }

                    // 发送心跳
                    val heartbeatSent = sendHeartbeat()
                    if (!heartbeatSent) {
                        heartbeatFailureCount++
                        LogUtil.w(TAG, "Failed to send heartbeat (${heartbeatFailureCount}/$maxRetryCount)")
                        
                        if (heartbeatFailureCount >= maxRetryCount) {
                            LogUtil.e(TAG, "Max heartbeat send failures reached")
                            handleConnectionLost()
                            break
                        }
                    } else {
                        // 发送成功，重置失败计数
                        if (heartbeatFailureCount > 0) {
                            LogUtil.d(TAG, "Heartbeat sent successfully, reset failure count")
                            heartbeatFailureCount = 0
                        }
                    }

                } catch (e: Exception) {
                    LogUtil.e(TAG, "Heartbeat error: ${e.message}")
                    if (isActive) {
                        handleConnectionLost()
                    }
                    break
                }
            }
        }
    }
    
    /**
     * 发送心跳消息
     */
    private fun sendHeartbeat(): Boolean {
        return try {
            client?.send(HEARTBEAT_MESSAGE)
            val success = client != null
            if (success) {
                LogUtil.d(TAG, "Heartbeat sent (interval: ${smartHeartbeatConfig.getCurrentInterval()}ms)")
            }
            success
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending heartbeat: ${e.message}")
            false
        }
    }

    /**
     * 停止心跳机制
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        LogUtil.d(TAG, "Heartbeat stopped")
    }

    /**
     * 处理连接丢失
     *
     * 注意：不在 Service 层处理重连，由 SDK 层统一管理重连逻辑
     */
    private fun handleConnectionLost() {
        LogUtil.w(TAG, "Connection lost, notifying upper layer")
        updateStatus(InnerConnectionStatus.ERROR)

        // 通知上层连接丢失，由 SDK 层决定是否重连
        currentConnectionCallback?.onDisconnected(
            InnerErrorCode.C35.code,
            "Connection lost"
        )
    }

    /**
     * 处理心跳响应
     */
    private fun handleHeartbeatResponse() {
        lastHeartbeatResponse = System.currentTimeMillis()
        
        // 重置心跳失败计数
        if (heartbeatFailureCount > 0) {
            LogUtil.d(TAG, "Heartbeat response received, reset failure count from $heartbeatFailureCount to 0")
            heartbeatFailureCount = 0
        } else {
            LogUtil.d(TAG, "Heartbeat response received")
        }
    }
    
    // SmartHeartbeatConfig.HeartbeatConfigChangeListener 实现
    override fun onHeartbeatConfigChanged(newConfig: SmartHeartbeatConfig.HeartbeatConfig) {
        LogUtil.d(TAG, "Heartbeat config changed: interval=${smartHeartbeatConfig.getCurrentInterval()}ms, timeout=${newConfig.timeout}ms")
        
        // 如果心跳正在运行，重启以应用新配置
        if (heartbeatJob?.isActive == true && currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
            LogUtil.d(TAG, "Restarting heartbeat with new configuration")
            startHeartbeat()
        }
    }
    
    // NetworkQualityMonitor.NetworkQualityChangeListener 实现
    override fun onNetworkQualityChanged(
        oldQuality: SmartHeartbeatConfig.NetworkQuality,
        newQuality: SmartHeartbeatConfig.NetworkQuality
    ) {
        LogUtil.d(TAG, "Network quality changed: $oldQuality -> $newQuality")
        
        // 根据网络质量调整心跳配置
        smartHeartbeatConfig.adjustForNetworkQuality(newQuality)
    }

    /**
     * 内部 WebSocketClient 实现类
     */
    private inner class TaplinkWebSocketClient(
        serverUri: URI,
        private val connectionCallback: ConnectionCallback
    ) : WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake) {
            LogUtil.d(TAG, "WebSocket connected to server: $uri")

            // 保存当前连接的服务信息
            saveCurrentServiceInfo(uri)

            // 启动心跳
            startHeartbeat()

            // 启动服务监听（检测 IP/端口变化）
            startServiceMonitoring()

            // 通知连接成功
            handleConnectionSuccess(connectionCallback, null)
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            LogUtil.d(TAG, "WebSocket closed: code=$code, reason=$reason, remote=$remote")

            // 停止心跳
            stopHeartbeat()

            // 停止服务监听
            stopServiceMonitoring()

            // 通知上层连接断开，由 SDK 层判断是主动还是被动
            updateStatus(InnerConnectionStatus.DISCONNECTED)
            currentConnectionCallback?.onDisconnected("$code", reason)
        }

        override fun onMessage(message: String) {
            LogUtil.d(TAG, "Text message received: $message")
            // 检查是否是心跳响应
            if (message.contains("pong") || message.contains("ping")) {
                handleHeartbeatResponse()
                return
            }
            // 通过 dataReceiver 传递响应
            dataReceiver?.invoke(message.toByteArray(Charsets.UTF_8))
        }

        override fun onMessage(message: ByteBuffer) {
            val data = ByteArray(message.remaining())
            message.get(data)
            LogUtil.d(TAG, "Binary message received: ${data.size} bytes")

            // 通过 dataReceiver 传递响应
            dataReceiver?.invoke(data)
        }

        override fun onError(ex: Exception) {
            LogUtil.e(TAG, "WebSocket error: ${ex.message}")

            if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING) {
                // 连接阶段的错误
                handleConnectionError(
                    ex.message ?: "Connection error",
                    connectionCallback,
                    InnerErrorCode.C33.code
                )
            } else {
                // 运行时错误，通知上层
                handleConnectionLost()
            }
        }
    }

    /**
     * 保存当前连接的服务信息
     */
    private fun saveCurrentServiceInfo(uri: URI) {
        currentServiceInfo = ServiceInfo(
            name = "taplink-service", // 默认服务名
            host = uri.host,
            port = uri.port
        )
        LogUtil.d(TAG, "Saved current service info: ${currentServiceInfo}")
    }

    /**
     * 启动服务监听（检测 IP/端口变化）
     */
    private fun startServiceMonitoring() {
        if (isServiceMonitoringEnabled) {
            LogUtil.d(TAG, "Service monitoring already enabled")
            return
        }

        isServiceMonitoringEnabled = true
        LogUtil.d(TAG, "Starting service monitoring for IP/port changes")

        scope.launch {
            try {
                // 初始化 NSD Manager
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

                // 创建监听器
                val monitoringListener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        LogUtil.e(TAG, "Service monitoring start failed: $serviceType, errorCode: $errorCode")
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        LogUtil.e(TAG, "Service monitoring stop failed: $serviceType, errorCode: $errorCode")
                    }

                    override fun onDiscoveryStarted(serviceType: String) {
                        LogUtil.d(TAG, "Service monitoring started: $serviceType")
                    }

                    override fun onDiscoveryStopped(serviceType: String) {
                        LogUtil.d(TAG, "Service monitoring stopped: $serviceType")
                        isServiceMonitoringEnabled = false
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        LogUtil.d(TAG, "Service monitoring found: ${serviceInfo.serviceName}")

                        // 解析服务信息
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                LogUtil.e(TAG, "Service monitoring resolve failed: ${serviceInfo.serviceName}")
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val serviceName = serviceInfo.serviceName
                                val host = serviceInfo.host?.hostAddress ?: ""
                                val port = serviceInfo.port
                                LogUtil.d(TAG, "Service monitoring resolved: ${serviceInfo.serviceName}, $host:$port")

                                // 检查是否是当前连接的服务，且 IP/端口发生变化
                                checkAndHandleServiceChange(serviceName, host, port)
                            }
                        })
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        LogUtil.w(TAG, "Service monitoring lost: ${serviceInfo.serviceName}")

                        // 从发现的服务列表中移除丢失的服务
                        val removedServices = discoveredServices.removeAll { service ->
                            service.name == serviceInfo.serviceName
                        }

                        if (removedServices) {
                            LogUtil.d(TAG, "Removed lost service from monitoring list: ${serviceInfo.serviceName}")
                        }

                        // 检查是否是当前连接的服务丢失
                        val currentService = currentServiceInfo
                        if (currentService != null && currentService.name == serviceInfo.serviceName) {
                            LogUtil.w(TAG, "Current connected service lost: ${serviceInfo.serviceName}")
                            // 可以选择通知上层服务丢失，让SDK层决定是否需要重连
                            // currentConnectionCallback?.onDisconnected("SERVICE_LOST", "Connected service lost")
                        }
                    }
                }

                // 开始监听
                nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, monitoringListener)

            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to start service monitoring: ${e.message}")
                isServiceMonitoringEnabled = false
            }
        }
    }

    /**
     * 检查并处理服务变化
     */
    private fun checkAndHandleServiceChange(newServiceName: String, newHost: String, newPort: Int) {
        val currentService = currentServiceInfo
        if (currentService == null) {
            LogUtil.d(TAG, "No current service info, ignoring service change")
            return
        }

        // 检查 IP 或端口是否发生变化
        if (currentService.host != newHost || currentService.port != newPort) {
            LogUtil.i(TAG, "Service address changed: ${currentService.host}:${currentService.port} -> $newHost:$newPort")

            // 通知SDK层地址发生变更，让SDK层决定是否重连
            currentConnectionCallback?.onServiceAddressChanged(
                nameService = newServiceName,
                newHost = newHost,
                newPort = newPort,
                oldHost = currentService.host,
                oldPort = currentService.port
            )

            // 更新服务信息
            currentServiceInfo = ServiceInfo(
                name = currentService.name,
                host = newHost,
                port = newPort
            )
        }
    }


    /**
     * 停止服务监听
     */
    private fun stopServiceMonitoring() {
        if (!isServiceMonitoringEnabled) {
            return
        }

        LogUtil.d(TAG, "Stopping service monitoring")
        isServiceMonitoringEnabled = false

        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error stopping service monitoring: ${e.message}")
        }
    }

    /**
     * 记录服务属性（用于调试）
     */
    private fun logServiceAttributes(serviceInfo: NsdServiceInfo) {
        try {
            val attributes = serviceInfo.attributes
            if (attributes != null && attributes.isNotEmpty()) {
                LogUtil.d(TAG, "Service ${serviceInfo.serviceName} attributes:")
                for ((key, value) in attributes) {
                    val valueStr = if (value != null) String(value, Charsets.UTF_8) else "null"
                    LogUtil.d(TAG, "  $key = $valueStr")
                }
            } else {
                LogUtil.d(TAG, "Service ${serviceInfo.serviceName} has no attributes")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error logging service attributes: ${e.message}")
        }
    }

    /**
     * 服务信息数据类
     */
    private data class ServiceInfo(
        val name: String,
        val host: String,
        val port: Int
    )
}
