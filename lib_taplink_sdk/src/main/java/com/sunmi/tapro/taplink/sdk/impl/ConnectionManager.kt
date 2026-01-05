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
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent

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
     * Payment manager reference (optional, set after initialization)
     * Used to notify pending transactions when connection is lost
     */
    private var paymentManager: PaymentManager? = null

    /**
     * Connection status change listener for PaymentManager
     * Called when connection status changes to CONNECTED
     */
    private var connectionStatusListener: (() -> Unit)? = null

    /**
     * Connection error listener for PaymentManager
     * Called when connection fails (ERROR status)
     */
    private var connectionErrorListener: ((ConnectionError) -> Unit)? = null

    /**
     * Connection disconnected listener for PaymentManager
     * Called when connection status changes to DISCONNECTED
     */
    private var connectionDisconnectedListener: (() -> Unit)? = null

    /**
     * Gson instance for JSON serialization
     */
    private val gson = Gson()

    /**
     * Data receiver callback
     */
    private var dataReceiver: ((ByteArray) -> Unit)? = null

    /**
     * List of connection listeners waiting for connection completion
     * Used when user calls connect() while connection is already in progress
     */
    private val pendingConnectionListeners = mutableListOf<ConnectionListener>()

    /**
     * Callback manager for managing INIT command callbacks
     */
    private val callbackManager =
        com.sunmi.tapro.taplink.communication.util.LocalCallbackManager<InnerCallback?>(
            defaultTimeoutMillis = 60 * 1000L, // 60 seconds default timeout
            tag = TAG
        )

    /**
     * Response processor for handling INIT command responses
     */
    private val responseProcessor = ResponseProcessor()

    init {
        LogUtil.d(TAG, "ConnectionManager initialized")

        // Initialize reconnect manager
        reconnectManager = ReconnectManager(context) { connectionConfig, listener ->
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
        }

        // Check if currently reconnecting
        val reconnectManager = this.reconnectManager
        if (reconnectManager?.isReconnecting() == true) {
            val reconnectingConfig = reconnectManager.getReconnectingConfig()

            if (reconnectingConfig == null || !actualConfig.isEquivalentTo(reconnectingConfig)) {
                if (connectionStatus == ConnectionStatus.CONNECTING ||
                    connectionStatus == ConnectionStatus.WAIT_CONNECTING ||
                    connectionStatus == ConnectionStatus.CONNECTED
                ) {
                    disconnect()
                }
                reconnectManager.prepareConnect(actualConfig, listener)
            } else {
                reconnectManager.addReconnectionListener(listener)
                return
            }
        } else {
            // Not reconnecting, check if already connecting
            if (connectionStatus == ConnectionStatus.CONNECTING ||
                connectionStatus == ConnectionStatus.WAIT_CONNECTING
            ) {
                LogUtil.w(TAG, "Connection already in progress, adding listener to queue")
                pendingConnectionListeners.add(listener)
                LogUtil.d(TAG, "Added listener to pending connection queue, total: ${pendingConnectionListeners.size}")
                return
            }
            // Prepare reconnect manager for new connection
            reconnectManager?.prepareConnect(actualConfig, listener)
        }

        // Execute actual connection
        connectInternal(actualConfig, listener)
    }

    /**
     * Internal connection method
     */
    private fun connectInternal(config: ConnectionConfig?, listener: ConnectionListener) {
        // Set connection status immediately to prevent concurrent connections
        if (connectionStatus != ConnectionStatus.CONNECTING &&
            connectionStatus != ConnectionStatus.WAIT_CONNECTING
        ) {
            updateConnectionStatus(ConnectionStatus.CONNECTING, listener = listener)
        }

        if (this.config.appId.isBlank() || this.config.secretKey.isBlank()) {
            // Reset status on error
            updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
            listener.onError(
                ConnectionError(
                    InnerErrorCode.C20.code,
                    InnerErrorCode.C20.description
                )
            )
            return
        }

        // Build protocol string based on ConnectionConfig
        val (protocol, connectionMode) = ProtocolBuilder.buildProtocol(
            config,
            reconnectManager?.getContext()
        )
        currentConnectionMode = connectionMode

        // Validate protocol format
        if (!ProtocolManager.isValidProtocol(protocol)) {
            // Reset status on error
            updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
            listener.onError(
                ConnectionError(
                    InnerErrorCode.C41.code,
                    "${InnerErrorCode.C41.description}:$protocol"
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
                        // Register data receiver
                        registerDataReceiver()

                        // Send INIT command to initialize tapro application
                        sendInitCommand { success, errorCode, errorMsg, deviceId, taproVersion ->
                            if (success) {
                                // Get device info from INIT response
                                connectedDeviceId = deviceId ?: "unknown"
                                connectedTaproVersion = taproVersion ?: "unknown"

                                // 在 INIT 成功后保存连接信息（包含正确的 deviceId）
                                reconnectManager?.onConnected(connectedDeviceId, connectedTaproVersion)
                                updateConnectionStatus(
                                    ConnectionStatus.CONNECTED,
                                    deviceId = connectedDeviceId,
                                    taproVersion = connectedTaproVersion,
                                    listener = listener
                                )
                            } else {
                                val error = ConnectionError(
                                    errorCode ?: InnerErrorCode.C44.code,
                                    errorMsg ?: InnerErrorCode.C44.description
                                )

                                // Notify pending reconnection listeners
                                reconnectManager?.notifyPendingListenersError(error)

                                updateConnectionStatus(
                                    ConnectionStatus.ERROR,
                                    null,
                                    null,
                                    error,
                                    null,
                                    listener
                                )
                            }
                        }
                    }

                    override fun onWaitingConnect() {
                        updateConnectionStatus(
                            ConnectionStatus.WAIT_CONNECTING, listener = listener
                        )
                    }

                    override fun onDisconnected(code: String, msg: String) {
                        val reason = "Code: $code, Message: $msg"

                        // 判断是连接错误还是正常断开
                        val isConnectionError = isConnectionError(code, msg)

                        if (isConnectionError) {
                            // 立即触发所有待处理的交易回调（目标进程崩溃时）
                            paymentManager?.failAllPendingTransactions(code, msg)

                            // 创建连接错误对象
                            val connectionError = ConnectionError(code, msg)
                            updateConnectionStatus(
                                ConnectionStatus.ERROR,
                                errorCode = connectionError,
                                listener = listener
                            )

                            // 清理资源
                            connectedDeviceId = null
                            connectedTaproVersion = null
                            currentConnectionMode = null
                        } else {
                            updateConnectionStatus(
                                ConnectionStatus.DISCONNECTED,
                                reason = reason,
                                listener = listener
                            )

                            // Try auto-reconnect
                            val reconnected = reconnectManager?.onDisconnected(listener) ?: false

                            if (!reconnected) {
                                // Not reconnecting, notify application layer and clean up resources
                                listener.onDisconnected(reason)
                                connectionListener?.onDisconnected(reason)

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

        // Clear pending connection listeners
        if (pendingConnectionListeners.isNotEmpty()) {
            val error = ConnectionError(
                InnerErrorCode.C45.code,
                InnerErrorCode.C45.description
            )
            notifyPendingConnectionListenersError(error)
        }

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
     * Check if connection is in progress (connecting or reconnecting)
     */
    fun isConnecting(): Boolean {
        return connectionStatus == ConnectionStatus.CONNECTING ||
                connectionStatus == ConnectionStatus.WAIT_CONNECTING ||
                (reconnectManager?.isReconnecting() == true)
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
        // Since connectionMode was moved to ConnectionConfig, create a default config
        // The SDK will auto-detect the connection mode
        LogUtil.d(TAG, "Creating default connection config with auto-detection")
        return ConnectionConfig.createDefault()
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
                        var transactionResultCode: String? = null

                        if (basicResponse.bizData != null) {
                            try {
                                val bizDataJson = gson.fromJson(basicResponse.bizData, com.google.gson.JsonObject::class.java)
                                code = bizDataJson.get("code")?.asString ?: "ERROR"
                                message = bizDataJson.get("message")?.asString
                                deviceId = bizDataJson.get("deviceId")?.asString
                                taproVersion = bizDataJson.get("taproVersion")?.asString
                                transactionResultCode = bizDataJson.get("transactionResultCode")?.asString
                            } catch (e: Exception) {
                                LogUtil.w(TAG, "Failed to parse bizData as JSON: ${e.message}")
                            }
                        }

                        // Determine success
                        if (code == SUCCESS_CODE && basicResponse.event is PaymentEvent.Completed
                            && (transactionResultCode == "0" || transactionResultCode == "000")
                        ) {
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
            val added = callbackManager.addInitCallback(
                basicRequest.traceId, initCallback,
                app2app =
                    getConnectionMode() == ConnectionMode.APP_TO_APP.name,
                initRequest.requestTimeout
            )
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
                // Notify connection status listener (for PaymentManager to process pending transaction)
                connectionStatusListener?.invoke()
                listener?.onConnected(deviceId!!, taproVersion!!)
                connectionListener?.onConnected(deviceId!!, taproVersion!!)

                // Notify all pending connection listeners
                notifyPendingConnectionListenersSuccess(deviceId!!, taproVersion!!)
            }

            ConnectionStatus.ERROR -> {
                errorCode?.let {
                    // Notify connection error listener (for PaymentManager to handle pending transaction)
                    connectionErrorListener?.invoke(it)
                    listener?.onError(it)
                    connectionListener?.onError(it)

                    // Notify all pending connection listeners
                    notifyPendingConnectionListenersError(it)
                }
            }

            ConnectionStatus.DISCONNECTED -> {
                reason?.let {
                    // Notify connection disconnected listener (for PaymentManager to clear auto-connect flag)
                    connectionDisconnectedListener?.invoke()
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
     * Notify all pending connection listeners of connection success
     *
     * @param deviceId Connected device ID
     * @param taproVersion Tapro version
     */
    private fun notifyPendingConnectionListenersSuccess(deviceId: String, taproVersion: String) {
        if (pendingConnectionListeners.isEmpty()) {
            return
        }

        LogUtil.d(TAG, "Notifying ${pendingConnectionListeners.size} pending connection listeners of connection success")
        val listeners = pendingConnectionListeners.toList()
        pendingConnectionListeners.clear()

        listeners.forEach { listener ->
            try {
                listener.onConnected(deviceId, taproVersion)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error notifying pending connection listener: ${e.message}")
            }
        }
    }

    /**
     * Notify all pending connection listeners of connection error
     *
     * @param error Connection error
     */
    private fun notifyPendingConnectionListenersError(error: ConnectionError) {
        if (pendingConnectionListeners.isEmpty()) {
            return
        }

        LogUtil.d(TAG, "Notifying ${pendingConnectionListeners.size} pending connection listeners of connection error")
        val listeners = pendingConnectionListeners.toList()
        pendingConnectionListeners.clear()

        listeners.forEach { listener ->
            try {
                listener.onError(error)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error notifying pending connection listener: ${e.message}")
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
     * Set payment manager reference
     * Called after PaymentManager is created to enable connection loss notifications
     */
    fun setPaymentManager(paymentManager: PaymentManager) {
        this.paymentManager = paymentManager
    }

    /**
     * Set connection status listener
     * Called when connection status changes to CONNECTED
     */
    fun setConnectionStatusListener(listener: (() -> Unit)?) {
        this.connectionStatusListener = listener
    }

    /**
     * Set connection error listener
     * Called when connection fails (ERROR status)
     */
    fun setConnectionErrorListener(listener: ((ConnectionError) -> Unit)?) {
        this.connectionErrorListener = listener
    }

    /**
     * Set connection disconnected listener
     * Called when connection status changes to DISCONNECTED
     */
    fun setConnectionDisconnectedListener(listener: (() -> Unit)?) {
        this.connectionDisconnectedListener = listener
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