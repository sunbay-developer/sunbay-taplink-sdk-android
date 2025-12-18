package com.sunmi.tapro.taplink.communication.lan

import android.content.Context
import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.util.ProtocolParser
import com.sunmi.tapro.taplink.communication.util.ProtocolParseResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetAddress
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket Client Service Kernel Class
 *
 * Features:
 * 1. WebSocket client connection
 * 2. Heartbeat mechanism to keep connection alive
 * 3. mDNS service discovery (automatically find devices when connection fails)
 * 4. Thread-safe data sending
 *
 * Note: Reconnection logic is uniformly managed by SDK layer (ReconnectManager), Service layer only handles underlying communication
 *
 * @param appId Application ID
 * @param appSecretKey Application secret key
 * @param context Android context, used for mDNS service
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class WebSocketClientKernel constructor(
    private val appId: String,
    private val appSecretKey: String,
    private val context: Context
) : BaseServiceKernel() {

    companion object {
        private const val TAG = "WebSocketClientKernel"
        
        // Heartbeat configuration
        private const val HEARTBEAT_INTERVAL = 30_000L // 30 seconds heartbeat interval
        private const val HEARTBEAT_TIMEOUT = 10_000L // 10 seconds heartbeat timeout
        private const val HEARTBEAT_MESSAGE = "{\"type\":\"ping\"}"
        
        // NSD configuration
        private const val NSD_SERVICE_TYPE = "_taplink._tcp" // Taplink service type
        private const val NSD_DISCOVERY_TIMEOUT = 10_000L // NSD discovery timeout 10 seconds
    }

    override fun getTag(): String = TAG

    /**
     * Coroutine scope
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Send data mutex lock
     */
    private val sendMutex = Mutex()

    /**
     * Current WebSocket client connection instance
     */
    private var client: TaplinkWebSocketClient? = null

    /**
     * Currently connected service URI
     */
    private var currentUri: URI? = null

    /**
     * Current connection callback
     */
    private var currentConnectionCallback: ConnectionCallback? = null

    /**
     * Heartbeat job
     */
    private var heartbeatJob: Job? = null

    /**
     * Last heartbeat response time
     */
    private var lastHeartbeatResponse = 0L

    /**
     * NSD Manager instance
     */
    private var nsdManager: NsdManager? = null

    /**
     * NSD discovery listener
     */
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * NSD discovered services list
     */
    private val discoveredServices = mutableListOf<ServiceInfo>()

    /**
     * Implement IServiceKernel interface: Connect to WebSocket server
     *
     * @param protocol Protocol string, supports ws://, wss://, and lan:// formats
     * @param connectionCallback Connection callback
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "WebSocket client is already connected or connecting. Current status: $currentConnectionStatus",
                connectionCallback,
                InnerErrorCode.C33.code
            )
            return
        }

        try {
            LogUtil.d(TAG, "Starting connection with protocol: $protocol")
            updateStatus(ConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()

            // 无需重置状态，由 SDK 层管理

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
     * Connect with protocol string
     */
    private fun connectWithProtocol(protocol: String, connectionCallback: ConnectionCallback) {
        scope.launch {
            try {
                // 解析协议字符串
                val parseResult = ProtocolParser.parse(protocol)
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

                val uri = when (parseResult) {
                    is ProtocolParseResult.LanProtocol -> {
                        // 将 lan:// 转换为 ws://
                        val scheme = if (parseResult.port == 443) "wss" else "ws"
                        URI("$scheme://${parseResult.ip}:${parseResult.port}")
                    }
                    else -> {
                        handleConnectionError(
                            "Invalid protocol", connectionCallback,
                            InnerErrorCode.C41.code
                        )
                        return@launch
                    }
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
     * Connect to specified URI
     * 
     * Communication Flow:
     * 1. Save current URI for future reference
     * 2. Close any existing WebSocket connection if present
     * 3. Create new WebSocket client instance with the URI
     * 4. Initiate asynchronous connection
     * 5. Wait up to 5 seconds for connection status change
     * 6. Return connection result
     * 
     * @param uri WebSocket server URI to connect to
     * @param connectionCallback Callback to notify connection status changes
     * @return Boolean True if connection successful, false otherwise
     */
    private suspend fun connectToUri(uri: URI, connectionCallback: ConnectionCallback): Boolean {
        return try {
            LogUtil.d(TAG, "Attempting to connect to: $uri")
            
            // Save current URI
            currentUri = uri

            // If there's an existing connection, close it first
            client?.let { 
                LogUtil.d(TAG, "Closing existing connection")
                it.close(1000, "Reconnecting")
                client = null
            }

            // Create new WebSocket client
            client = TaplinkWebSocketClient(uri, connectionCallback)
            
            // Connect (this is asynchronous, results will be returned via callback)
            client?.connect()
            
            // Wait for connection result (judged by status change)
            var attempts = 0
            while (attempts < 50 && currentConnectionStatus == ConnectionStatus.CONNECTING) { // Wait up to 5 seconds
                delay(100)
                attempts++
            }
            
            currentConnectionStatus == ConnectionStatus.CONNECTED
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to $uri: ${e.message}")
            false
        }
    }

    /**
     * NSD service discovery and connection
     */
    private suspend fun discoverAndConnect(connectionCallback: ConnectionCallback) {
        try {
            LogUtil.d(TAG, "Starting NSD service discovery")
            
            // 初始化 NSD Manager
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            discoveredServices.clear()
            
            // 创建发现监听器
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    LogUtil.e(TAG, "NSD discovery start failed: $serviceType, errorCode: $errorCode")
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
                    
                    // 解析服务信息
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            LogUtil.e(TAG, "NSD resolve failed: ${serviceInfo.serviceName}, errorCode: $errorCode")
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
                                discoveredServices.add(service)
                                LogUtil.d(TAG, "Added discovered service: $service")
                            }
                        }
                    })
                }
                
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    LogUtil.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")
                }
            }
            
            // 开始服务发现
            nsdManager?.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            
            // 等待发现服务
            delay(NSD_DISCOVERY_TIMEOUT)
            
            // 停止服务发现
            try {
                discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error stopping NSD discovery: ${e.message}")
            }
            
            // 尝试连接发现的服务
            for (service in discoveredServices) {
                if (service.host.isNotEmpty() && service.port > 0) {
                    val uri = URI("ws://${service.host}:${service.port}")
                    LogUtil.d(TAG, "Trying discovered service: $uri")
                    
                    if (connectToUri(uri, connectionCallback)) {
                        LogUtil.d(TAG, "Successfully connected to discovered service: $uri")
                        return
                    }
                }
            }
            
            // 没有找到可用的服务
            handleConnectionError(
                "No available Taplink services found via NSD",
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
     * Implement IServiceKernel interface: Send data
     * 
     * Communication Flow:
     * 1. Check if data can be sent (connection status is CONNECTED)
     * 2. Verify WebSocket client is initialized
     * 3. Execute in coroutine with thread-safe Mutex lock
     * 4. Log data sending information with traceId for debugging
     * 5. Send data asynchronously via WebSocket
     * 6. Notify callback of send result (success/failure)
     * 
     * @param traceId Unique identifier for request tracing
     * @param data Data to be sent via WebSocket
     * @param callback Callback to notify send result
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.d(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "WebSocket client not connected")
            return
        }

        if (client == null) {
            LogUtil.e(TAG, "WebSocket client is null, cannot send data")
            callback?.onError("WEBSOCKET_NULL", "WebSocket client interface is null")
            return
        }

        // Execute in coroutine, use Mutex to ensure thread safety
        scope.launch {
            sendMutex.withLock {
                try {
                    LogUtil.d(TAG, "Sending data via WebSocket: traceId=$traceId, dataSize=${data.size} bytes")

                    // Send data
                    client?.send(data)
                    // WebSocket send is asynchronous, assuming success here
                    if (client != null) {
                        LogUtil.d(TAG, "Data sent via WebSocket successfully: traceId=$traceId")
                        callback?.onResponse("SUCCESS")
                    } else {
                        LogUtil.e(TAG, "Failed to send data via WebSocket: traceId=$traceId")
                        callback?.onError("SEND_FAILED", "Failed to send data via WebSocket")
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to send data via WebSocket: traceId=$traceId, error=${e.message}")
                    callback?.onError("SEND_FAILED", e.message ?: "Unknown error")
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
            LogUtil.d(TAG, "Disconnect requested")
            
            // 停止心跳
            stopHeartbeat()
            
            // 断开WebSocket连接
            disconnectInternal()
            
            // 清理资源
            cleanupResources()
            
            updateStatus(ConnectionStatus.DISCONNECTED)
            LogUtil.d(TAG, "WebSocket client disconnected manually")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during manual disconnect: ${e.message}")
            updateStatus(ConnectionStatus.ERROR)
        }
    }

    /**
     * Disconnect (internal method)
     */
    private fun disconnectInternal() {
        try {
            client?.close(1000, "Normal closure")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error closing WebSocket client: ${e.message}")
        }
    }

    /**
     * Clean up resources
     */
    private fun cleanupResources() {
        client = null
        currentUri = null
        currentConnectionCallback = null
        dataReceiver = null
        
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
     * Start heartbeat mechanism
     * 
     * Communication Flow:
     * 1. Stop any existing heartbeat to avoid conflicts
     * 2. Initialize heartbeat job in coroutine scope
     * 3. Set initial heartbeat response timestamp
     * 4. Enter periodic heartbeat loop:
     *    a. Wait for heartbeat interval (30 seconds)
     *    b. Check if last heartbeat response timed out (40 seconds total)
     *    c. If timed out, notify connection lost and break loop
     *    d. If still connected, send heartbeat message
     *    e. Log heartbeat sending status
     * 5. Handle any exceptions during heartbeat process
     * 
     * Heartbeat Protocol:
     * - Send: {"type":"ping"} every 30 seconds
     * - Expect: Server should respond with pong message
     * - Timeout: 40 seconds without pong response triggers reconnection
     */
    private fun startHeartbeat() {
        stopHeartbeat() // Stop existing heartbeat first to avoid conflicts
        
        heartbeatJob = scope.launch {
            LogUtil.d(TAG, "Starting heartbeat mechanism")
            lastHeartbeatResponse = System.currentTimeMillis()
            
            while (isActive && currentConnectionStatus == ConnectionStatus.CONNECTED) {
                try {
                    delay(HEARTBEAT_INTERVAL)
                    
                    // Check last heartbeat response time
                    val now = System.currentTimeMillis()
                    if (now - lastHeartbeatResponse > HEARTBEAT_INTERVAL + HEARTBEAT_TIMEOUT) {
                        LogUtil.w(TAG, "Heartbeat timeout detected, connection may be lost")
                        handleConnectionLost()
                        break
                    }
                    
                    // Send heartbeat
                    client?.send(HEARTBEAT_MESSAGE)
                    // WebSocket send is asynchronous, assuming success here
                    if (client != null) {
                        LogUtil.d(TAG, "Heartbeat sent")
                    } else {
                        LogUtil.w(TAG, "Failed to send heartbeat")
                        handleConnectionLost()
                        break
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
     * Stop heartbeat mechanism
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        LogUtil.d(TAG, "Heartbeat stopped")
    }

    /**
     * Handle connection lost
     * 
     * Note: Reconnection is not handled in Service layer, it's uniformly managed by SDK layer
     */
    private fun handleConnectionLost() {
        LogUtil.w(TAG, "Connection lost, notifying upper layer")
        updateStatus(ConnectionStatus.ERROR)
        
        // 通知上层连接丢失，由 SDK 层决定是否重连
        currentConnectionCallback?.onDisconnected(
            InnerErrorCode.C35.code,
            "Connection lost"
        )
    }

    /**
     * Handle heartbeat response
     */
    private fun handleHeartbeatResponse() {
        lastHeartbeatResponse = System.currentTimeMillis()
        LogUtil.d(TAG, "Heartbeat response received")
    }

    /**
     * Inner WebSocketClient implementation class
     */
    private inner class TaplinkWebSocketClient(
        serverUri: URI,
        private val connectionCallback: ConnectionCallback
    ) : WebSocketClient(serverUri) {

        override fun onOpen(handshakedata: ServerHandshake) {
            LogUtil.d(TAG, "WebSocket connected to server: $uri")
            
            // 启动心跳
            startHeartbeat()
            
            // 通知连接成功
            handleConnectionSuccess(connectionCallback, mapOf(
                "deviceId" to "websocket_device",
                "taproVersion" to "1.0.0"
            ))
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            LogUtil.d(TAG, "WebSocket closed: code=$code, reason=$reason, remote=$remote")
            
            // 停止心跳
            stopHeartbeat()
            
            // 通知上层连接断开，由 SDK 层判断是主动还是被动
            updateStatus(ConnectionStatus.DISCONNECTED)
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
            
            if (currentConnectionStatus == ConnectionStatus.CONNECTING) {
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
     * Service information data class
     */
    private data class ServiceInfo(
        val name: String,
        val host: String,
        val port: Int
    )
}
