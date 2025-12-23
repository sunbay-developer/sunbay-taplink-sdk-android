package com.sunmi.tapro.taplink.sdk.impl

import android.content.Context
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.reconnect.ReconnectManager
import com.sunmi.tapro.taplink.sdk.util.ProtocolBuilder
import com.sunmi.tapro.taplink.sdk.util.RequestConverter
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback as ServiceConnectionCallback
import com.google.gson.Gson
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode

/**
 * Connection management class
 *
 * Handles all connection-related operations including:
 * - Connection establishment and teardown
 * - Reconnection management
 * - Connection status tracking
 * - INIT command handling
 *
 * @author TaPro Team
 * @since 2025-12-22
 */
class ConnectionManager(
    private val config: TaplinkConfig,
    private val context: Context
) {
    private val TAG = "ConnectionManager"

    companion object {
        private const val SUCCESS_CODE = "0"
    }

    /**
     * Connection status
     */
    private var connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    /**
     * Connection listener
     */
    private var connectionListener: ConnectionListener? = null

    /**
     * Current connection mode
     */
    private var currentConnectionMode: String? = null

    /**
     * Connected device information
     */
    private var connectedDeviceId: String? = null
    private var connectedTaproVersion: String? = null

    /**
     * Reconnect manager
     */
    private var reconnectManager: ReconnectManager? = null

    /**
     * Gson instance for JSON serialization
     */
    private val gson = Gson()

    /**
     * Data receiver callback
     */
    private var dataReceiver: ((ByteArray) -> Unit)? = null

    /**
     * Callback manager for managing INIT command callbacks
     */
    private val callbackManager =
        com.sunmi.tapro.taplink.communication.util.LocalCallbackManager<com.sunmi.tapro.taplink.communication.interfaces.InnerCallback?>(
            timeoutMillis = 30_000L, // 30 seconds timeout for INIT command
            tag = TAG
        )

    /**
     * Response processor for handling INIT command responses
     */
    private val responseProcessor = ResponseProcessor()

    init {
        LogUtil.d(TAG, "ConnectionManager initialized")

        // Initialize reconnect manager
        reconnectManager = ReconnectManager(context, config) { connectionConfig, listener ->
            connectInternal(connectionConfig, listener)
        }
    }

    /**
     * Connect to device
     */
    fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        // If no config provided, try to get from ReconnectManager or create default
        var actualConfig = config ?: reconnectManager?.getLastConnectionConfig()

        // If still no config, create default based on TaplinkConfig connection mode
        if (actualConfig == null) {
            actualConfig = createDefaultConnectionConfig()
            LogUtil.d(
                TAG,
                "No connection config provided, created default config based on connection mode: ${this.config.connectionMode}"
            )
        } else {
            LogUtil.d(TAG, "Using connection config: ${if (config != null) "provided" else "previous"}")
        }

        // Prepare reconnect manager
        reconnectManager?.prepareConnect(actualConfig, listener)

        // Execute actual connection
        connectInternal(actualConfig, listener)
    }

    /**
     * Internal connection method
     */
    private fun connectInternal(config: ConnectionConfig?, listener: ConnectionListener) {
        LogUtil.d(TAG, "=== APP TO APP Connection Debug ===")
        LogUtil.d(
            TAG,
            "Connecting with config: cableProtocol=${config?.cableProtocol}, host=${config?.host}, port=${config?.port}"
        )
        LogUtil.d(TAG, "Connection mode: ${this.config.connectionMode}")
        LogUtil.d(TAG, "AppId: ${this.config.appId}")
        LogUtil.d(TAG, "SecretKey length: ${this.config.secretKey.length}")

        if (this.config.appId.isBlank() || this.config.secretKey.isBlank()) {
            listener.onError(
                ConnectionError(
                    "C20",
                    "SDK not initialized, please call init() first"
                )
            )
            return
        }

        // Build protocol string based on ConnectionConfig
        val (protocol, connectionMode) = ProtocolBuilder.buildProtocol(
            config,
            this.config,
            reconnectManager?.getContext()
        )
        currentConnectionMode = connectionMode
        LogUtil.d(TAG, "Built protocol string: $protocol, connectionMode: $connectionMode")

        // Validate protocol format
        if (!ProtocolManager.isValidProtocol(protocol)) {
            listener.onError(
                ConnectionError(
                    "C41",
                    "Invalid protocol format: $protocol"
                )
            )
            return
        }

        // Connect through ServiceKernel
        TaplinkServiceKernel.getInstance()
            ?.connect(
                protocol,
                this.config.appId,
                this.config.secretKey,
                object : ServiceConnectionCallback {
                    override fun onConnected(extraInfoMap: Map<String, String?>?) {
                        LogUtil.d(TAG, "=== Service Connection Success ===")
                        LogUtil.d(TAG, "onConnected, extraInfoMap=${extraInfoMap.toString()}")
                        LogUtil.d(TAG, "Connection status before INIT: $connectionStatus")

                        // Notify reconnect manager of successful connection
                        reconnectManager?.onConnected(connectedDeviceId)

                        // Register data receiver
                        registerDataReceiver()

                        LogUtil.d(TAG, "Starting INIT command to initialize tapro application...")
                        // Send INIT command to initialize tapro application
                        sendInitCommand { success, errorCode, errorMsg, deviceId, taproVersion ->
                            LogUtil.d(TAG, "=== INIT Command Result ===")
                            LogUtil.d(TAG, "INIT success: $success")
                            LogUtil.d(TAG, "INIT errorCode: $errorCode")
                            LogUtil.d(TAG, "INIT errorMsg: $errorMsg")
                            LogUtil.d(TAG, "INIT deviceId: $deviceId")
                            LogUtil.d(TAG, "INIT taproVersion: $taproVersion")

                            if (success) {
                                LogUtil.d(TAG, "INIT command completed successfully, notifying connection listeners")

                                // Get device info from INIT response
                                connectedDeviceId = deviceId ?: "unknown"
                                connectedTaproVersion = taproVersion ?: "unknown"
                                LogUtil.d(
                                    TAG,
                                    "Device info from INIT response: deviceId=$connectedDeviceId, taproVersion=${connectedTaproVersion}"
                                )
                                LogUtil.d(TAG, "=== Calling onConnected Callbacks ===")
                                updateConnectionStatus(
                                    ConnectionStatus.CONNECTED,
                                    deviceId = connectedDeviceId,
                                    taproVersion = connectedTaproVersion,
                                    listener = listener
                                )
                                LogUtil.d(TAG, "=== All onConnected Callbacks Completed ===")
                            } else {
                                LogUtil.e(TAG, "INIT command failed: code=$errorCode, msg=$errorMsg")
                                val error = ConnectionError(
                                    errorCode ?: "INIT_FAILED",
                                    errorMsg ?: "Failed to initialize tapro application"
                                )
                                LogUtil.d(TAG, "=== Calling onError Callbacks ===")
                                updateConnectionStatus(
                                    ConnectionStatus.ERROR,
                                    null,
                                    null,
                                    error,
                                    null,
                                    listener
                                )
                                LogUtil.d(TAG, "=== All onError Callbacks Completed ===")
                            }
                        }
                    }

                    override fun onWaitingConnect() {
                        updateConnectionStatus(
                            ConnectionStatus.WAIT_CONNECTING, listener = listener
                        )
                    }

                    override fun onDisconnected(code: String, msg: String) {
                        LogUtil.d(TAG, "=== Service Connection Disconnected ===")
                        LogUtil.d(TAG, "Disconnect code: $code")
                        LogUtil.d(TAG, "Disconnect message: $msg")
                        LogUtil.d(TAG, "Connection status before handling: $connectionStatus")

                        val reason = "Code: $code, Message: $msg"
                        LogUtil.d(TAG, "ConnectionManager:onDisconnected:code$code,msg:$msg")

                        // 判断是连接错误还是正常断开
                        val isConnectionError = isConnectionError(code, msg)

                        if (isConnectionError) {
                            LogUtil.e(TAG, "Treating as connection error: $reason")

                            // 创建连接错误对象
                            val connectionError = ConnectionError(code, msg)
                            LogUtil.d(TAG, "=== Calling onError Callbacks ===")
                            updateConnectionStatus(
                                ConnectionStatus.ERROR,
                                errorCode = connectionError,
                                listener = listener
                            )
                            LogUtil.d(TAG, "=== All onError Callbacks Completed ===")

                            // 清理资源
                            connectedDeviceId = null
                            connectedTaproVersion = null
                            currentConnectionMode = null
                        } else {
                            LogUtil.d(TAG, "Treating as normal disconnection: $reason")
                            updateConnectionStatus(
                                ConnectionStatus.DISCONNECTED,
                                reason = reason,
                                listener = listener
                            )

                            // Try auto-reconnect
                            val reconnected = reconnectManager?.onDisconnected(listener) ?: false

                            if (!reconnected) {
                                // Not reconnecting, notify application layer and clean up resources
                                LogUtil.d(TAG, "=== Calling onDisconnected Callbacks ===")
                                listener.onDisconnected(reason)
                                connectionListener?.onDisconnected(reason)
                                LogUtil.d(TAG, "=== All onDisconnected Callbacks Completed ===")

                                // Clean up resources (only when not reconnecting)
                                connectedDeviceId = null
                                connectedTaproVersion = null
                                currentConnectionMode = null
                            }
                            // If reconnecting, preserve device info and connection mode
                        }
                    }

                    override fun onServiceAddressChanged(
                        newServiceName: String,
                        newHost: String,
                        newPort: Int,
                        oldHost: String,
                        oldPort: Int
                    ): Boolean {
                        LogUtil.i(TAG, "Service address changed detected: $oldHost:$oldPort -> $newHost:$newPort")
                        val sameDevice =
                            (connectedDeviceId.isNullOrEmpty()) || (connectedDeviceId != null && newServiceName.endsWith(
                                connectedDeviceId!!
                            ))

                        if (!sameDevice) {
                            return false
                        } else {
                            // Create new connection configuration
                            val newConnectionConfig = ConnectionConfig().apply {
                                setHost(newHost)
                                setPort(newPort)
                            }

                            LogUtil.d(TAG, "Triggering reconnection with new address: $newHost:$newPort")

                            // Notify reconnect manager of address change, trigger reconnection
                            reconnectManager?.onAddressChanged(newConnectionConfig, listener)
                            return true
                        }
                    }
                })
    }

    /**
     * Disconnect from device
     */
    fun disconnect() {
        LogUtil.d(TAG, "Manual disconnect")

        // Notify reconnect manager of manual disconnect
        reconnectManager?.disconnect()

        updateConnectionStatus(ConnectionStatus.DISCONNECTED, reason = "Manual disconnect")
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.disconnect()
        connectedDeviceId = null
        connectedTaproVersion = null
        currentConnectionMode = null

        LogUtil.d(TAG, "Connection config will be cleared by ReconnectManager")
    }

    /**
     * Check if connected
     */
    fun isConnected(): Boolean {
        return connectionStatus == ConnectionStatus.CONNECTED
    }

    /**
     * Get connected device ID
     */
    fun getConnectedDeviceId(): String? {
        return connectedDeviceId
    }

    /**
     * Get connection mode
     */
    fun getConnectionMode(): String? {
        return currentConnectionMode
    }

    /**
     * Get TaPro version
     */
    fun getTaproVersion(): String? {
        return connectedTaproVersion
    }

    /**
     * Set connection listener
     */
    fun setConnectionListener(listener: ConnectionListener?) {
        LogUtil.d(TAG, "Setting connection listener")
        this.connectionListener = listener
    }

    /**
     * Remove connection listener
     */
    fun removeConnectionListener() {
        LogUtil.d(TAG, "Removing connection listener")
        this.connectionListener = null
    }

    /**
     * Set data receiver callback
     */
    fun setDataReceiver(receiver: (ByteArray) -> Unit) {
        this.dataReceiver = receiver
    }

    /**
     * Process received response data for INIT commands
     */
    private fun processReceivedResponse(responseJson: String) {
        try {
            if (responseJson.isBlank()) {
                return
            }

            LogUtil.d(TAG, "Processing received response: $responseJson")

            // Use ResponseProcessor to handle the response
            responseProcessor.processResponse(responseJson, callbackManager)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to process received response: ${e.message}")
        }
    }

    /**
     * Create default connection configuration based on TaplinkConfig connection mode
     */
    private fun createDefaultConnectionConfig(): ConnectionConfig {
        return when (config.connectionMode) {
            ConnectionMode.APP_TO_APP -> {
                LogUtil.d(TAG, "Creating default APP_TO_APP connection config")
                ConnectionConfig.createDefault()
            }

            ConnectionMode.CABLE -> {
                LogUtil.d(TAG, "Creating default CABLE connection config with AUTO protocol")
                ConnectionConfig().setCableProtocol(CableProtocol.AUTO)
            }

            ConnectionMode.LAN -> {
                LogUtil.d(TAG, "Creating default LAN connection config (requires manual host/port configuration)")
                ConnectionConfig.createDefault()
            }

            else -> {
                LogUtil.w(TAG, "Connection mode is NONE, creating default APP_TO_APP config")
                ConnectionConfig.createDefault()
            }
        }
    }

    /**
     * Send INIT command to initialize tapro application
     */
    private fun sendInitCommand(onComplete: (Boolean, String?, String?, String?, String?) -> Unit) {
        try {
            LogUtil.d(TAG, "Sending INIT command to initialize tapro application")

            // Build INIT request
            val initRequest = PaymentRequest(
                action = TransactionAction.INIT.value
            )

            // Convert PaymentRequest to BasicRequest
            val basicRequest = RequestConverter.convertToBasicRequest(
                request = initRequest,
                version = config.version,
                config.appId,
                secretKey = config.secretKey
            )

            // Convert BasicRequest to JSON string
            val requestJson = gson.toJson(basicRequest)
            LogUtil.d(TAG, "INIT command prepared: traceId=${basicRequest.traceId}")

            // Create internal callback for receiving INIT response
            val initCallback = object : InnerCallback {
                override fun onError(code: String, msg: String) {
                    LogUtil.e(TAG, "INIT command failed: code=$code, msg=$msg")
                    onComplete(false, code, msg, null, null)
                }

                override fun onResponse(responseData: String) {
                    LogUtil.d(TAG, "INIT command response received: $responseData")
                    try {
                        // Parse response to determine success
                        val basicResponse =
                            gson.fromJson(responseData, com.sunmi.tapro.taplink.sdk.model.base.BasicResponse::class.java)

                        // Parse device info directly from bizData
                        var code = "ERROR"
                        var message: String? = basicResponse.event.eventMsg
                        var deviceId: String? = null
                        var taproVersion: String? = null

                        if (basicResponse.bizData != null) {
                            try {
                                val bizDataJson = gson.fromJson(basicResponse.bizData, com.google.gson.JsonObject::class.java)
                                code = bizDataJson.get("code")?.asString ?: "ERROR"
                                message = bizDataJson.get("message")?.asString
                                deviceId = bizDataJson.get("deviceId")?.asString
                                taproVersion = bizDataJson.get("taproVersion")?.asString
                            } catch (e: Exception) {
                                LogUtil.w(TAG, "Failed to parse bizData as JSON: ${e.message}")
                            }
                        }

                        // Determine success
                        if (code == SUCCESS_CODE && basicResponse.event is com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent.Completed) {
                            LogUtil.d(TAG, "INIT command completed successfully")
                            LogUtil.d(TAG, "Device info: deviceId=$deviceId, taproVersion=$taproVersion")
                            onComplete(true, null, null, deviceId, taproVersion)
                        } else {
                            LogUtil.e(TAG, "INIT command failed: code=$code, message=$message")
                            onComplete(false, code, message, null, null)
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Failed to parse INIT response: ${e.message}")
                        onComplete(false, "PARSE_ERROR", "Failed to parse INIT response: ${e.message}", null, null)
                    }
                }
            }

            // Add callback to callbackManager BEFORE sending
            val added = callbackManager.addCallbackByTraceId(basicRequest.traceId, initCallback)
            if (!added) {
                LogUtil.w(TAG, "Failed to add INIT callback to manager, but continuing...")
            }
            LogUtil.d(TAG, "INIT callback registered with traceId: ${basicRequest.traceId}")

            // Send INIT command
            val serviceKernel = TaplinkServiceKernel.getInstance()
            if (serviceKernel == null) {
                LogUtil.e(TAG, "Service kernel not available")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                onComplete(false, "SERVICE_UNAVAILABLE", "Service kernel not available", null, null)
                return
            }

            try {
                serviceKernel.sendData(
                    basicRequest.traceId,
                    requestJson.toByteArray(Charsets.UTF_8),
                    initCallback // This callback is now managed by callbackManager
                )
                LogUtil.d(TAG, "INIT command sent successfully")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to send INIT command: ${e.message}")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                onComplete(false, "SEND_ERROR", "Failed to send INIT command: ${e.message}", null, null)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to prepare INIT command: ${e.message}")
            onComplete(false, "PREPARE_ERROR", "Failed to prepare INIT command: ${e.message}", null, null)
        }
    }

    /**
     * Register data receiver for receiving responses
     */
    private fun registerDataReceiver() {
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.registerDataReceiver { data ->
            try {
                if (data != null && data.isNotEmpty()) {
                    val responseJson = String(data, Charsets.UTF_8)
                    LogUtil.d(TAG, "Received data: $responseJson")

                    // First try to process as INIT command response
                    processReceivedResponse(responseJson)

                    // Also forward to PaymentManager's data receiver if set
                    dataReceiver?.invoke(data)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to process received data: ${e.message}")
            }
        }
    }

    /**
     * Update connection status
     */
    private fun updateConnectionStatus(
        newStatus: ConnectionStatus,
        deviceId: String? = null,
        taproVersion: String? = null,
        errorCode: ConnectionError? = null,
        reason: String? = null,
        listener: ConnectionListener? = null
    ) {
        if (connectionStatus != newStatus) {
            val oldStatus = connectionStatus
            connectionStatus = newStatus
            LogUtil.d(TAG, "Connection status changed: $oldStatus -> $newStatus")
        }

        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> {
                listener?.onConnected(deviceId!!, taproVersion!!)
                connectionListener?.onConnected(deviceId!!, taproVersion!!)
            }

            ConnectionStatus.ERROR -> {
                errorCode?.let {
                    listener?.onError(it)
                    connectionListener?.onError(it)
                }
            }

            ConnectionStatus.DISCONNECTED -> {
                reason?.let {
                    listener?.onDisconnected(it)
                    connectionListener?.onDisconnected(it)
                }
            }

            else -> {
                LogUtil.i(TAG, "do not need callback")
            }
        }
    }

    /**
     * Check if auto-connect should be performed
     */
    fun shouldAutoConnect(): Boolean {
        return reconnectManager?.shouldAutoConnect() ?: false
    }

    /**
     * Get auto-connect configuration
     */
    fun getAutoConnectConfig(): ConnectionConfig? {
        return reconnectManager?.getAutoConnectConfig()
    }

    /**
     * 判断是否为连接错误
     *
     * 根据错误码和消息判断是连接错误还是正常断开
     *
     * @param code 错误码
     * @param msg 错误消息
     * @return Boolean true表示连接错误，false表示正常断开
     */
    private fun isConnectionError(code: String, msg: String): Boolean {
        // 连接阶段的错误码
        val connectionErrorCodes = setOf(
            "C20", "C22", "C25", "C33", "C35", "C36", "C41", // 通用连接错误
            "-1", // BaseServiceKernel 默认错误码
            "1006", "1002", "1015", // WebSocket 连接错误码
            "CONNECTION_FAILED", "WEBSOCKET_NULL", "SEND_FAILED", // 自定义错误码
            "SERVICE_UNAVAILABLE", "PARSE_ERROR", "PREPARE_ERROR" // INIT 相关错误码
        )

        // 正常断开的错误码
        val normalDisconnectCodes = setOf(
            "1000", // WebSocket 正常关闭
            "1001", // WebSocket 端点离开
            "MANUAL_DISCONNECT", // 手动断开
            "HEARTBEAT_TIMEOUT" // 心跳超时（可能是网络问题，但不是连接错误）
        )

        // 首先检查是否是正常断开
        if (normalDisconnectCodes.contains(code)) {
            return false
        }

        // 然后检查是否是明确的连接错误
        if (connectionErrorCodes.contains(code)) {
            return true
        }

        // 根据消息内容判断
        val errorKeywords = listOf(
            "connection", "connect", "bind", "service", "protocol",
            "timeout", "failed", "error", "unavailable", "not found"
        )

        val lowerMsg = msg.lowercase()
        val hasErrorKeyword = errorKeywords.any { keyword ->
            lowerMsg.contains(keyword)
        }

        // 如果消息包含错误关键词，且当前状态是连接中，则认为是连接错误
        if (hasErrorKeyword && connectionStatus == ConnectionStatus.CONNECTING) {
            return true
        }

        // 默认情况下，如果是在连接阶段发生的断开，认为是连接错误
        return connectionStatus == ConnectionStatus.CONNECTING || connectionStatus == ConnectionStatus.WAIT_CONNECTING
    }
}