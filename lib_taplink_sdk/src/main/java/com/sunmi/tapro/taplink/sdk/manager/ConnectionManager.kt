package com.sunmi.tapro.taplink.sdk.manager

import android.content.Context
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.persistence.ConnectionPersistence
import com.sunmi.tapro.taplink.sdk.protocol.ProtocolConfigResolver
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback as ServiceConnectionCallback
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.lan.LanClientKernel

/**
 * Connection management class
 *
 * Handles all connection-related operations including:
 * - Connection establishment and teardown
 * - Reconnection management
 * - Connection status tracking
 * - Device info (updated by PaymentManager when INIT succeeds)
 *
 * @author TaPro Team
 * @since 2025-12-22
 */
class ConnectionManager(
    private val config: TaplinkConfig,
    private val context: Context
) {
    private val TAG = "ConnectionManager"

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
     * Current connection configuration
     * Used to check if already connected with same config
     */
    private var currentConnectionConfig: ConnectionConfig? = null


    /**
     * Reconnect manager
     */
    private var reconnectManager: ReconnectManager? = null

    /**
     * Connection persistence for saving successful cable protocol
     */
    private val connectionPersistence = ConnectionPersistence(context)

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
     * Data receiver callback
     */
    private var dataReceiver: ((ByteArray) -> Unit)? = null

    /**
     * List of connection listeners waiting for connection completion
     * Used when user calls connect() while connection is already in progress
     */
    private val pendingConnectionListeners = mutableListOf<ConnectionListener>()

    init {
        LogUtil.d(TAG, "ConnectionManager initialized")

        // Initialize reconnect manager
        reconnectManager = ReconnectManager(context) { connectionConfig, listener ->
            connectInternal(connectionConfig, listener)
        }

        // Note: Kernel status listener will be setup when kernel is created during connection
        // Cannot setup here because kernel doesn't exist yet
    }

    /**
     * Connect to device
     */
    fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        // If no config provided, try to get from ReconnectManager or create default
        var actualConfig = config ?: reconnectManager?.getLastConnectionConfig()
        LogUtil.d(TAG, "connect: actualConfig=$actualConfig, lastConfig=${reconnectManager?.getLastConnectionConfig()}")

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
            // Not reconnecting, check connection status
            if (connectionStatus == ConnectionStatus.CONNECTING ||
                connectionStatus == ConnectionStatus.WAIT_CONNECTING
            ) {
                LogUtil.w(TAG, "Connection already in progress, adding listener to queue")
                pendingConnectionListeners.add(listener)
                LogUtil.d(TAG, "Added listener to pending connection queue, total: ${pendingConnectionListeners.size}")
                return
            }

            // Check if already connected with same config
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                val isSameConfig = currentConnectionConfig?.isEquivalentTo(actualConfig) == true
                val isActuallyConnected = isConnected()

                if (isSameConfig && isActuallyConnected) {
                    // Already connected with same config, notify listener immediately
                    LogUtil.d(TAG, "Already connected with same config, notifying listener immediately")
                    val deviceId = connectedDeviceId ?: "unknown"
                    val taproVersion = connectedTaproVersion ?: "unknown"
                    listener.onConnected(deviceId, taproVersion)
                    return
                } else if (!isSameConfig) {
                    // Different config, need to disconnect first
                    LogUtil.d(TAG, "Different config detected, disconnecting current connection before reconnecting")
                    disconnect()
                } else if (!isActuallyConnected) {
                    // Status says CONNECTED but actually not connected, need to reconnect
                    LogUtil.w(TAG, "Status is CONNECTED but actually not connected, reconnecting...")
                    disconnect()
                }
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
        // Save config for connection state tracking
        val connectionConfig = config

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
                    InnerErrorCode.E201.code,
                    InnerErrorCode.E201.description
                )
            )
            return
        }

        // Cable AUTO mode: try connect in protocol order (VSP -> RS232 -> AOA)
        val protocolsToTry = ProtocolConfigResolver.getCableProtocolsToTry(
            config,
            reconnectManager?.getContext()
        )
        if (!protocolsToTry.isNullOrEmpty()) {
            tryConnectWithProtocolList(protocolsToTry, connectionConfig, listener)
            return
        }

        // Build protocol string based on ConnectionConfig (single protocol)
        val (protocol, connectionMode) = ProtocolConfigResolver.buildProtocol(
            config,
            reconnectManager?.getContext()
        )
        currentConnectionMode = connectionMode

        // If not in LAN mode, clear any existing LAN address listener
        if (connectionMode != ConnectionMode.LAN.name) {
            clearServiceAddressChangeListener()
        }

        // Validate protocol format
        if (!ProtocolManager.isValidProtocol(protocol)) {
            // Reset status on error
            updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
            listener.onError(
                ConnectionError(
                    InnerErrorCode.E302.code,
                    "${InnerErrorCode.E302.description}:$protocol"
                )
            )
            return
        }

        // Validate status consistency before connecting
        validateStatusConsistency()

        // Connect through ServiceKernel
        val serviceKernel = TaplinkServiceKernel.getInstance()

        // Connect first, kernel will be created inside TaplinkServiceKernel.connect()
        serviceKernel?.connect(
            protocol,
            this.config.appId,
            this.config.secretKey,
            this.config.taproAppWidthRatio,
            object : ServiceConnectionCallback {
                override fun onConnected(extraInfoMap: Map<String, String?>?) {
                    // Sync status after connection success
                    syncStatusFromKernel()

                    // Register data receiver
                    registerDataReceiver()

                    // Physical connection established. INIT is deferred to first transaction execution.
                    connectedDeviceId = "unknown"
                    connectedTaproVersion = "unknown"
                    currentConnectionConfig = connectionConfig

                    LogUtil.d(TAG, "Physical connection established, INIT will be performed on first transaction")

                    updateConnectionStatus(
                        ConnectionStatus.CONNECTED,
                        deviceId = "unknown",
                        taproVersion = "unknown",
                        listener = listener
                    )
                }

                override fun onWaitingConnect() {
                    // Setup service address change listener during connection waiting phase
                    // At this point LanClientKernel has been created and assigned to currentServiceKernel
                    // This allows earlier detection of service address changes, even during connection process
                    setupServiceAddressChangeListener()

                    // Setup status listener as soon as kernel is created
                    // Kernel is created in TaplinkServiceKernel.connect() before this callback
                    setupKernelStatusListener()

                    updateConnectionStatus(
                        ConnectionStatus.WAIT_CONNECTING,
                        reason = "Waiting for connection",
                        listener = listener
                    )
                }

                override fun onDisconnected(code: String, msg: String) {
                    val reason = "Code: $code, Message: $msg"

                    // Determine if this is a connection error or normal disconnect
                    val isConnectionError = isConnectionError(code, msg)

                    if (isConnectionError) {
                        // Immediately trigger all pending transaction callbacks (when target process crashes)
                        paymentManager?.failAllPendingTransactions(code, msg)

                        // Create connection error object
                        val connectionError = ConnectionError(code, msg)
                        updateConnectionStatus(
                            ConnectionStatus.ERROR,
                            errorCode = connectionError,
                            listener = listener
                        )

                        // Clean up resources
                        connectedDeviceId = null
                        connectedTaproVersion = null
                        currentConnectionMode = null
                        currentConnectionConfig = null
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
                            currentConnectionConfig = null
                        }
                        // If reconnecting, preserve device info and connection mode
                    }

                    // Sync status from kernel after updating ConnectionManager status
                    // Kernel should have updated its status in onDisconnected callback
                    // This ensures both states are consistent
                    syncStatusFromKernel()
                }
            })
    }

    /**
     * Try connect with cable protocols in order (VSP -> RS232 -> AOA).
     * More accurate than device detection - uses actual connection attempt.
     */
    private fun tryConnectWithProtocolList(
        protocols: List<Pair<String, String>>,
        connectionConfig: ConnectionConfig?,
        listener: ConnectionListener
    ) {
        if (protocols.isEmpty()) {
            updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
            listener.onError(
                ConnectionError(
                    InnerErrorCode.E302.code,
                    "${InnerErrorCode.E302.description}: No cable protocols to try"
                )
            )
            return
        }

        clearServiceAddressChangeListener()
        validateStatusConsistency()

        val serviceKernel = TaplinkServiceKernel.getInstance() ?: run {
            updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
            listener.onError(
                ConnectionError(InnerErrorCode.E201.code, InnerErrorCode.E201.description)
            )
            return
        }

        var tryIndex = 0

        /**
         * Incremented on every [tryNextProtocol] entry. Each protocol attempt captures its value in
         * the [ServiceConnectionCallback] so a late [onDisconnected] from a previous kernel (e.g. AOA
         * finishing disconnect after we already moved to VSP) cannot advance [tryIndex] again.
         */
        var cableAttemptEpoch = 0

        fun tryNextProtocol() {
            if (tryIndex >= protocols.size) {
                LogUtil.w(TAG, "All cable protocols failed: VSP, RS232, AOA")
                updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
                listener.onError(
                    ConnectionError(
                        InnerErrorCode.E214.code,
                        "Cable connection failed: tried VSP, RS232, AOA - none succeeded"
                    )
                )
                return
            }

            cableAttemptEpoch++
            val epochForThisAttempt = cableAttemptEpoch

            val (protocol, connectionMode) = protocols[tryIndex]
            currentConnectionMode = connectionMode
            LogUtil.d(TAG, "Trying cable protocol ${tryIndex + 1}/${protocols.size}: $connectionMode")

            if (!ProtocolManager.isValidProtocol(protocol)) {
                LogUtil.w(TAG, "Invalid protocol for $connectionMode, trying next")
                tryIndex++
                tryNextProtocol()
                return
            }

            serviceKernel.connect(
                protocol,
                this.config.appId,
                this.config.secretKey,
                this.config.taproAppWidthRatio,
                object : ServiceConnectionCallback {
                    override fun onConnected(extraInfoMap: Map<String, String?>?) {
                        if (epochForThisAttempt != cableAttemptEpoch) {
                            LogUtil.d(TAG, "Ignoring stale cable onConnected for $connectionMode (epoch=$epochForThisAttempt, current=$cableAttemptEpoch)")
                            return
                        }

                        val (_, mode) = protocols[tryIndex]
                        LogUtil.d(TAG, "Cable connection succeeded with $mode")

                        // Save successful protocol for future reconnects
                        runCatching {
                            CableProtocol.valueOf(mode)
                        }.onSuccess { cableProtocol ->
                            connectionPersistence.saveDetectedCableProtocol(cableProtocol, null)
                        }

                        syncStatusFromKernel()
                        registerDataReceiver()

                        connectedDeviceId = "unknown"
                        connectedTaproVersion = "unknown"
                        currentConnectionConfig = connectionConfig

                        updateConnectionStatus(
                            ConnectionStatus.CONNECTED,
                            deviceId = "unknown",
                            taproVersion = "unknown",
                            listener = listener
                        )
                    }

                    override fun onWaitingConnect() {
                        if (epochForThisAttempt != cableAttemptEpoch) {
                            LogUtil.d(TAG, "Ignoring stale cable onWaitingConnect for $connectionMode (epoch=$epochForThisAttempt, current=$cableAttemptEpoch)")
                            return
                        }

                        setupServiceAddressChangeListener()
                        setupKernelStatusListener()
                        updateConnectionStatus(
                            ConnectionStatus.WAIT_CONNECTING,
                            reason = "Waiting for connection ($connectionMode)",
                            listener = listener
                        )
                    }

                    override fun onDisconnected(code: String, msg: String) {
                        if (epochForThisAttempt != cableAttemptEpoch) {
                            LogUtil.d(
                                TAG,
                                "Ignoring stale cable disconnect for $connectionMode " +
                                    "(epoch=$epochForThisAttempt, current=$cableAttemptEpoch): code=$code, msg=$msg"
                            )
                            return
                        }

                        val isConnectionError = isConnectionError(code, msg)
                        LogUtil.d(TAG, "Cable $connectionMode failed: code=$code, msg=$msg, isConnectionError=$isConnectionError")

                        if (isConnectionError && tryIndex < protocols.size - 1) {
                            tryIndex++
                            tryNextProtocol()
                        } else {
                            if (isConnectionError) {
                                paymentManager?.failAllPendingTransactions(code, msg)
                            }
                            val connectionError = ConnectionError(code, msg)
                            updateConnectionStatus(
                                ConnectionStatus.ERROR,
                                errorCode = connectionError,
                                listener = listener
                            )
                            connectedDeviceId = null
                            connectedTaproVersion = null
                            currentConnectionMode = null
                            currentConnectionConfig = null
                        }
                        syncStatusFromKernel()
                    }
                }
            )
        }

        tryNextProtocol()
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
                InnerErrorCode.E213.code,
                InnerErrorCode.E213.description
            )
            notifyPendingConnectionListenersError(error)
        }

        updateConnectionStatus(ConnectionStatus.DISCONNECTED, reason = "Manual disconnect")
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.disconnect()
        connectedDeviceId = null
        connectedTaproVersion = null
        currentConnectionMode = null
        currentConnectionConfig = null

        // Clear service address change listener (clear when disconnecting)
        clearServiceAddressChangeListener()

        // Sync status after disconnect to ensure consistency
        syncStatusFromKernel()

        LogUtil.d(TAG, "Connection config will be cleared by ReconnectManager")
    }

    /**
     * Check if connected
     *
     * Priority: Kernel actual status > ConnectionManager cached status
     *
     * Note: This method performs automatic status synchronization if inconsistency is detected
     */
    fun isConnected(): Boolean {
        // First check kernel actual status
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
        val kernelStatus = kernel?.getConnectionStatus()

        if (kernelStatus != null) {
            val isKernelConnected = kernelStatus == InnerConnectionStatus.CONNECTED
            val expectedStatus = convertToConnectionStatus(kernelStatus)

            // Sync status if inconsistent (but respect INIT failure scenario)
            if (expectedStatus != connectionStatus) {
                // Special case: INIT failure scenario - don't sync if ConnectionManager is ERROR
                val isInitFailureScenario = connectionStatus == ConnectionStatus.ERROR &&
                        expectedStatus == ConnectionStatus.CONNECTED

                if (!isInitFailureScenario) {
                    LogUtil.w(
                        TAG,
                        "Status inconsistency detected: Kernel=$kernelStatus (expected: $expectedStatus), ConnectionManager=$connectionStatus, syncing..."
                    )
                    syncStatusFromKernel()
                }
            }

            return isKernelConnected
        }

        // Fallback to cached status if kernel is not available
        return connectionStatus == ConnectionStatus.CONNECTED
    }

    /**
     * Update device info when INIT succeeds.
     * Called by PaymentManager when INIT command completes successfully.
     *
     * @param deviceId Device ID from INIT response
     * @param taproVersion Tapro version from INIT response
     */
    fun updateDeviceInfo(deviceId: String, taproVersion: String) {
        connectedDeviceId = deviceId
        connectedTaproVersion = taproVersion
        reconnectManager?.onConnected(deviceId, taproVersion)
        LogUtil.d(TAG, "Device info updated: deviceId=$deviceId, taproVersion=$taproVersion")
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
     * Get last connected device ID
     *
     * Get the device ID of the last successful connection, even if currently disconnected.
     * Used for auto-reconnection, device identification, etc.
     *
     * @return Last connected device ID, or null if not available
     */
    fun getLastConnectedDeviceId(): String? {
        // First try current connected device ID
        if (connectedDeviceId != null) {
            return connectedDeviceId
        }
        // Fall back to persistent storage
        return reconnectManager?.getLastConnectedDeviceId()
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
     * Create default connection configuration based on TaplinkConfig connection mode
     */
    private fun createDefaultConnectionConfig(): ConnectionConfig {
        // Since connectionMode was moved to ConnectionConfig, create a default config
        // The SDK will auto-detect the connection mode
        LogUtil.d(TAG, "Creating default connection config with auto-detection")
        return ConnectionConfig.createDefault()
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

                    // Forward to PaymentManager for processing (INIT and transaction responses)
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
    fun updateConnectionStatus(
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

                // First, notify all pending connection listeners (they will be removed from the list)
                val notifiedPendingListeners = notifyPendingConnectionListenersSuccess(deviceId!!, taproVersion!!)

                // Collect all unique listeners to avoid duplicate callbacks
                val listenersToNotify = mutableSetOf<ConnectionListener>()

                // Add the listener parameter if provided and not already notified as pending listener
                listener?.let {
                    if (!notifiedPendingListeners.contains(it)) {
                        listenersToNotify.add(it)
                    }
                }

                // Add the global connection listener if set and different from listener parameter and not already notified
                connectionListener?.let {
                    if (it != listener && !notifiedPendingListeners.contains(it)) {
                        listenersToNotify.add(it)
                    }
                }

                // Now notify all unique listeners that haven't been notified yet
                listenersToNotify.forEach { l ->
                    try {
                        l.onConnected(deviceId!!, taproVersion!!)
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error notifying connection listener: ${e.message}")
                    }
                }
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
                // Always notify PaymentManager: reason was optional and DISCONNECTED without reason
                // skipped the listener, leaving INIT flags stale after link drop / mode switch.
                connectionDisconnectedListener?.invoke()
                val disconnectionReason = reason ?: "Disconnected"
                listener?.onDisconnected(disconnectionReason)
                connectionListener?.onDisconnected(disconnectionReason)
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
     * @return List of listeners that were notified (for deduplication)
     */
    private fun notifyPendingConnectionListenersSuccess(deviceId: String, taproVersion: String): List<ConnectionListener> {
        if (pendingConnectionListeners.isEmpty()) {
            return emptyList()
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

        return listeners
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
     * Clears all persisted device and connection cache data.
     *
     * Resets cached device identity and version info, then delegates to
     * [ConnectionPersistence] to wipe stored connection state (cable protocol,
     * last-connected device id, etc.).  Call this when you want the next
     * [connect] invocation to treat the device as completely new.
     */
    fun clearDeviceCache() {
        LogUtil.d(TAG, "Clearing device cache")
        connectedDeviceId = null
        connectedTaproVersion = null
        connectionPersistence.clearConnectionData()
    }

    /**
     * Determines if this is a connection error.
     *
     * Determines whether it's a connection error or normal disconnect based on error code and message.
     *
     * @param code error code
     * @param msg error message
     * @return true if connection error, false if normal disconnect
     */
    private fun isConnectionError(code: String, msg: String): Boolean {
        // Connection phase error codes (includes new error codes and backward compatible T-series codes)
        val connectionErrorCodes = setOf(
            // New error codes
            "201", "202", "203", // Initialization errors
            "211", "212", "213", "214", "221", // Connection status/failure
            "231", "232", // APP_TO_APP connection errors
            "241", "242", // LAN connection errors
            "251", "252", "253", "254", "255", // USB mode connection errors
            // Backward compatible: T-series error codes
            "T01", "T02", "T04", "T05", "T06", "T07", "T08", "T09", "T10", "T11", "T12", "T17",
            // Other error codes
            "-1", // BaseServiceKernel default error code
            "1006", "1002", "1015", // WebSocket connection error codes
            "CONNECTION_FAILED", "WEBSOCKET_NULL", "SEND_FAILED", // Custom error codes
            "SERVICE_UNAVAILABLE", "PARSE_ERROR", "PREPARE_ERROR" // INIT related error codes
        )

        // Normal disconnect error codes
        val normalDisconnectCodes = setOf(
            "1000", // WebSocket normal close
            "1001", // WebSocket endpoint leaving
            "MANUAL_DISCONNECT", // Manual disconnect
            "HEARTBEAT_TIMEOUT" // Heartbeat timeout (may be network issue, but not connection error)
        )

        // First check if it's a normal disconnect
        if (normalDisconnectCodes.contains(code)) {
            return false
        }

        // Then check if it's a clear connection error
        if (connectionErrorCodes.contains(code)) {
            return true
        }

        // Determine based on message content
        val errorKeywords = listOf(
            "connection", "connect", "bind", "service", "protocol",
            "timeout", "failed", "error", "unavailable", "not found"
        )

        val lowerMsg = msg.lowercase()
        val hasErrorKeyword = errorKeywords.any { keyword ->
            lowerMsg.contains(keyword)
        }

        // If message contains error keywords and current status is connecting, consider it a connection error
        if (hasErrorKeyword && connectionStatus == ConnectionStatus.CONNECTING) {
            return true
        }

        // By default, if disconnect occurs during connection phase, consider it a connection error
        return connectionStatus == ConnectionStatus.CONNECTING || connectionStatus == ConnectionStatus.WAIT_CONNECTING
    }

    // ==================== Status Synchronization Methods ====================

    /**
     * Device match result
     */
    private data class DeviceMatchResult(
        val matchType: DeviceMatchType,
        val confidence: Float,
        val reason: String
    )

    /**
     * Device match type
     */
    private enum class DeviceMatchType {
        SAME_DEVICE,
        DIFFERENT_DEVICE,
        UNKNOWN_DEVICE
    }

    /**
     * Reconnection decision result
     */
    private data class ReconnectionDecision(
        val shouldReconnect: Boolean,
        val reason: String
    )

    /**
     * Handle service address change (unified entry point).
     *
     * This method can be called by callbacks or listeners.
     */
    private fun handleServiceAddressChanged(
        newServiceName: String,
        newHost: String,
        newPort: Int,
        oldHost: String,
        oldPort: Int
    ): Boolean {
        LogUtil.i(
            TAG,
            "Service address changed detected: $newServiceName -> $newHost:$newPort (old: $oldHost:$oldPort)"
        )

        // 1. Enhanced device identity verification
        val deviceMatchResult = analyzeDeviceIdentity(
            newServiceName = newServiceName,
            currentDeviceId = connectedDeviceId,
            lastConnectedDeviceId = reconnectManager?.getLastConnectedDeviceId()
        )

        when (deviceMatchResult.matchType) {
            DeviceMatchType.DIFFERENT_DEVICE -> {
                LogUtil.d(TAG, "Service from different device, ignoring reconnection")
                return false
            }

            DeviceMatchType.UNKNOWN_DEVICE -> {
                LogUtil.w(TAG, "Cannot determine device identity, applying conservative policy")
                if (!shouldReconnectToUnknownDevice(newServiceName, newHost, newPort)) {
                    return false
                }
            }

            DeviceMatchType.SAME_DEVICE -> {
                LogUtil.d(TAG, "Service from same device (confidence: ${deviceMatchResult.confidence})")
                if (deviceMatchResult.confidence < 0.7f) {
                    LogUtil.d(
                        TAG,
                        "Device match confidence too low (${deviceMatchResult.confidence}), ignoring reconnection"
                    )
                    return false
                }
            }
        }

        // 2. Address change analysis
        val addressChangeResult = analyzeAddressChange(newHost, newPort, oldHost, oldPort)
        if (!addressChangeResult.shouldReconnect) {
            LogUtil.d(TAG, "Address change analysis: ${addressChangeResult.reason}")
            return false
        }

        // 3. Connection state analysis
        val connectionStateResult = analyzeConnectionState(newHost, newPort)
        if (!connectionStateResult.shouldReconnect) {
            LogUtil.d(TAG, "Connection state analysis: ${connectionStateResult.reason}")
            return false
        }

        // 4. Execute reconnection
        LogUtil.i(TAG, "All checks passed, triggering reconnection to $newHost:$newPort")
        return executeReconnection(newHost, newPort)
    }

    /**
     * Analyze device identity
     */
    private fun analyzeDeviceIdentity(
        newServiceName: String,
        currentDeviceId: String?,
        lastConnectedDeviceId: String?
    ): DeviceMatchResult {
        // Get the most reliable device ID
        val targetDeviceId = getReliableDeviceId(currentDeviceId, lastConnectedDeviceId)

        if (targetDeviceId.isNullOrEmpty() || targetDeviceId == "unknown") {
            return DeviceMatchResult(
                matchType = DeviceMatchType.UNKNOWN_DEVICE,
                confidence = 0.0f,
                reason = "No reliable device ID available"
            )
        }

        // Multiple matching strategies
        val exactMatch = newServiceName == "TaproService_$targetDeviceId"
        if (exactMatch) {
            return DeviceMatchResult(
                matchType = DeviceMatchType.SAME_DEVICE,
                confidence = 1.0f,
                reason = "Exact service name match"
            )
        }

        val suffixMatch = newServiceName.endsWith("_$targetDeviceId") || newServiceName.endsWith(targetDeviceId)
        if (suffixMatch) {
            return DeviceMatchResult(
                matchType = DeviceMatchType.SAME_DEVICE,
                confidence = 0.8f,
                reason = "Service name suffix match"
            )
        }

        val patternMatch = newServiceName.contains(targetDeviceId) &&
                newServiceName.contains("Tapro", ignoreCase = true)
        if (patternMatch) {
            return DeviceMatchResult(
                matchType = DeviceMatchType.SAME_DEVICE,
                confidence = 0.6f,
                reason = "Service name pattern match"
            )
        }

        return DeviceMatchResult(
            matchType = DeviceMatchType.DIFFERENT_DEVICE,
            confidence = 0.0f,
            reason = "No device ID match found"
        )
    }

    /**
     * Get the most reliable device ID
     */
    private fun getReliableDeviceId(
        currentDeviceId: String?,
        lastConnectedDeviceId: String?
    ): String? {
        return when {
            !currentDeviceId.isNullOrEmpty() && currentDeviceId != "unknown" -> {
                LogUtil.d(TAG, "Using current connected device ID: $currentDeviceId")
                currentDeviceId
            }

            !lastConnectedDeviceId.isNullOrEmpty() && lastConnectedDeviceId != "unknown" -> {
                LogUtil.d(TAG, "Using last connected device ID: $lastConnectedDeviceId")
                lastConnectedDeviceId
            }

            else -> {
                LogUtil.w(TAG, "No reliable device ID available")
                null
            }
        }
    }

    /**
     * Handle reconnection policy for unknown devices
     */
    private fun shouldReconnectToUnknownDevice(
        serviceName: String,
        newHost: String,
        newPort: Int
    ): Boolean {
        // Conservative policy: only reconnect under specific conditions
        val isCurrentlyDisconnected = getActualConnectionStatus() in listOf(
            ConnectionStatus.DISCONNECTED,
            ConnectionStatus.ERROR
        )

        val isExpectedServiceName = serviceName.contains("Tapro", ignoreCase = true)

        val shouldReconnect = isCurrentlyDisconnected && isExpectedServiceName

        LogUtil.d(
            TAG,
            "Unknown device policy: disconnected=$isCurrentlyDisconnected, expectedService=$isExpectedServiceName, shouldReconnect=$shouldReconnect"
        )

        return shouldReconnect
    }

    /**
     * Analyze address change
     */
    private fun analyzeAddressChange(
        newHost: String,
        newPort: Int,
        oldHost: String,
        oldPort: Int
    ): ReconnectionDecision {
        // Check if address actually changed
        val isSameAddress = oldHost.isNotEmpty() &&
                oldPort > 0 &&
                newHost == oldHost &&
                newPort == oldPort

        if (isSameAddress) {
            val actualStatus = getActualConnectionStatus()
            return if (actualStatus in listOf(ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR)) {
                ReconnectionDecision(
                    shouldReconnect = true,
                    reason = "Same address but currently disconnected, reconnection needed"
                )
            } else {
                ReconnectionDecision(
                    shouldReconnect = false,
                    reason = "Same address and already connected (status: $actualStatus), no reconnection needed"
                )
            }
        }

        return ReconnectionDecision(
            shouldReconnect = true,
            reason = "Address changed from $oldHost:$oldPort to $newHost:$newPort"
        )
    }

    /**
     * Analyze connection state
     */
    private fun analyzeConnectionState(newHost: String, newPort: Int): ReconnectionDecision {
        val actualStatus = getActualConnectionStatus()

        // If already connected, check if connected to same address
        if (actualStatus !in listOf(ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR)) {
            val currentConfig = reconnectManager?.getLastConnectionConfig()
            if (currentConfig != null &&
                currentConfig.host == newHost &&
                currentConfig.port == newPort
            ) {
                return ReconnectionDecision(
                    shouldReconnect = false,
                    reason = "Already connected to the same address $newHost:$newPort"
                )
            }
        }

        return ReconnectionDecision(
            shouldReconnect = true,
            reason = "Connection state allows reconnection (current status: $actualStatus)"
        )
    }

    /**
     * Setup service address change listener (for LAN connection only).
     *
     * Set during connection waiting phase (onWaitingConnect), when LanClientKernel is already created.
     * This allows earlier detection of service address changes, even during connection process.
     */
    private fun setupServiceAddressChangeListener() {
        try {
            val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
            if (kernel is LanClientKernel) {
                if (kernel.getServiceAddressChangeListener() != null) {
                    LogUtil.d(TAG, "exists address change listener for LanClientKernel")
                    return
                }
                LogUtil.d(TAG, "Setting up service address change listener for LanClientKernel")

                kernel.setServiceAddressChangeListener(object : LanClientKernel.ServiceAddressChangeListener {
                    override fun onServiceAddressChanged(
                        serviceName: String,
                        newHost: String,
                        newPort: Int,
                        oldHost: String,
                        oldPort: Int
                    ): Boolean {
                        // Directly call unified service address change handler
                        return this@ConnectionManager.handleServiceAddressChanged(
                            serviceName,
                            newHost,
                            newPort,
                            oldHost,
                            oldPort
                        )
                    }
                })
                LogUtil.d(TAG, "Service address change listener set successfully")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to setup service address change listener: ${e.message}")
        }
    }

    /**
     * Clear service address change listener.
     *
     * Clear previously set listener when switching to non-LAN mode or disconnecting.
     */
    private fun clearServiceAddressChangeListener() {
        try {
            val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
            if (kernel is LanClientKernel) {
                LogUtil.d(TAG, "Clearing service address change listener for LanClientKernel")
                kernel.setServiceAddressChangeListener(null)
                LogUtil.d(TAG, "Service address change listener cleared successfully")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to clear service address change listener: ${e.message}")
        }
    }

    /**
     * Execute reconnection
     */
    private fun executeReconnection(newHost: String, newPort: Int): Boolean {
        return try {
            val newConnectionConfig = ConnectionConfig().apply {
                setHost(newHost)
                setPort(newPort)
            }

            LogUtil.d(TAG, "Executing reconnection to $newHost:$newPort,$connectionListener")
            connectionListener?.let {
                reconnectManager?.onAddressChanged(newConnectionConfig, it)
            }

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to execute reconnection: ${e.message}")
            false
        }
    }

    /**
     * Setup kernel status listener for automatic synchronization
     *
     * When kernel status changes, automatically sync to ConnectionManager
     *
     * Note: This should be called after kernel is created (during connection process)
     * Called in onWaitingConnect() which is always called before onConnected()
     */
    private fun setupKernelStatusListener() {
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
        if (kernel == null) {
            LogUtil.d(TAG, "Cannot setup kernel status listener: kernel not available yet")
            return
        }

        kernel.setStatusChangeListener { kernelStatus ->
            val newStatus = convertToConnectionStatus(kernelStatus)
            if (connectionStatus != newStatus) {
                LogUtil.d(
                    TAG,
                    "Kernel status changed: $kernelStatus -> syncing to ConnectionManager: $newStatus (was: $connectionStatus)"
                )
                // Update status without triggering callbacks (to avoid duplicate notifications)
                // The callbacks should be triggered by ConnectionCallback, not by status sync
                synchronized(this) {
                    connectionStatus = newStatus
                }
            }
        }
        LogUtil.d(TAG, "Kernel status listener setup successfully")
    }

    /**
     * Convert InnerConnectionStatus to ConnectionStatus
     */
    private fun convertToConnectionStatus(kernelStatus: InnerConnectionStatus): ConnectionStatus {
        return when (kernelStatus) {
            InnerConnectionStatus.CONNECTED -> ConnectionStatus.CONNECTED
            InnerConnectionStatus.CONNECTING -> ConnectionStatus.CONNECTING
            InnerConnectionStatus.WAITING_CONNECT -> ConnectionStatus.WAIT_CONNECTING
            InnerConnectionStatus.ERROR -> ConnectionStatus.ERROR
            InnerConnectionStatus.DISCONNECTED -> ConnectionStatus.DISCONNECTED
            InnerConnectionStatus.RECONNECTING -> ConnectionStatus.CONNECTING // Map RECONNECTING to CONNECTING
        }
    }

    /**
     * Get actual connection status from kernel
     *
     * Priority: Kernel status > ConnectionManager cached status
     */
    private fun getActualConnectionStatus(): ConnectionStatus {
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
        val kernelStatus = kernel?.getConnectionStatus()

        return if (kernelStatus != null) {
            convertToConnectionStatus(kernelStatus)
        } else {
            // Fallback to cached status if kernel is not available
            connectionStatus
        }
    }

    /**
     * Sync status from kernel to ConnectionManager
     *
     * This method updates ConnectionManager's cached status based on kernel's actual status
     *
     * Note: In disconnect scenarios, Kernel should update its status before calling onDisconnected callback,
     * so this sync should align both states correctly. However, we add protection to prevent overwriting
     * correct disconnect states with stale CONNECTED state from kernel.
     *
     * Special handling for INIT failure scenario:
     * - If ConnectionManager is ERROR but Kernel is CONNECTED, this indicates INIT failure
     * - In this case, we should trust ConnectionManager's ERROR state and disconnect kernel
     */
    private fun syncStatusFromKernel() {
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
        val kernelStatus = kernel?.getConnectionStatus()

        if (kernelStatus != null) {
            val expectedStatus = convertToConnectionStatus(kernelStatus)
            if (connectionStatus != expectedStatus) {
                // Special case: INIT failure scenario
                // ConnectionManager is ERROR but Kernel is still CONNECTED
                // This happens when WebSocket connects but INIT command fails
                val isInitFailureScenario = connectionStatus == ConnectionStatus.ERROR &&
                        expectedStatus == ConnectionStatus.CONNECTED

                if (isInitFailureScenario) {
                    LogUtil.w(
                        TAG,
                        "INIT failure scenario detected: ConnectionManager=ERROR, Kernel=CONNECTED. " +
                                "This indicates INIT command failed after WebSocket connection. " +
                                "Kernel state will be corrected by disconnect operation."
                    )
                    // Don't sync in this case - ConnectionManager's ERROR state is correct
                    // The kernel should be disconnected (which should have been done in INIT failure handler)
                    return
                }

                // Protection: Don't sync from CONNECTED to ERROR/DISCONNECTED if we're already in disconnect state
                // This prevents overwriting correct disconnect states with stale CONNECTED state from kernel
                val isDisconnectState = connectionStatus == ConnectionStatus.DISCONNECTED ||
                        connectionStatus == ConnectionStatus.ERROR
                val kernelIsDisconnectState = expectedStatus == ConnectionStatus.DISCONNECTED ||
                        expectedStatus == ConnectionStatus.ERROR

                if (!isDisconnectState || kernelIsDisconnectState) {
                    LogUtil.d(TAG, "Syncing status from kernel: $kernelStatus -> $expectedStatus (was: $connectionStatus)")
                    synchronized(this) {
                        connectionStatus = expectedStatus
                    }
                } else {
                    LogUtil.w(
                        TAG,
                        "Skipping status sync: ConnectionManager=$connectionStatus (disconnect state), Kernel=$kernelStatus (still CONNECTED), kernel state may be stale"
                    )
                }
            }
        }
    }

    /**
     * Validate status consistency between Kernel and ConnectionManager
     *
     * If inconsistency is detected, automatically sync from kernel
     *
     * @return Boolean true if status is consistent, false if inconsistency was detected and fixed
     */
    /**
     * Validate status consistency between Kernel and ConnectionManager
     *
     * If inconsistency is detected, automatically sync from kernel
     *
     * Special handling for INIT failure scenario:
     * - If ConnectionManager is ERROR but Kernel is CONNECTED, this indicates INIT failure
     * - In this case, we should trust ConnectionManager's ERROR state and disconnect kernel
     *
     * @return Boolean true if status is consistent, false if inconsistency was detected and fixed
     */
    private fun validateStatusConsistency(): Boolean {
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
        val kernelStatus = kernel?.getConnectionStatus()

        if (kernelStatus == null) {
            // Kernel not available, cannot validate
            return true
        }

        val expectedStatus = convertToConnectionStatus(kernelStatus)

        if (connectionStatus != expectedStatus) {
            // Special case: INIT failure scenario
            // ConnectionManager is ERROR but Kernel is still CONNECTED
            // This happens when WebSocket connects but INIT command fails
            val isInitFailureScenario = connectionStatus == ConnectionStatus.ERROR &&
                    expectedStatus == ConnectionStatus.CONNECTED

            if (isInitFailureScenario) {
                LogUtil.w(
                    TAG,
                    "INIT failure scenario detected during validation: ConnectionManager=ERROR, Kernel=CONNECTED. " +
                            "This indicates INIT command failed after WebSocket connection. " +
                            "Kernel should be disconnected to ensure state consistency."
                )
                // Don't sync in this case - ConnectionManager's ERROR state is correct
                // The kernel should be disconnected (which should have been done in INIT failure handler)
                // If kernel is still connected, disconnect it now
                if (kernelStatus == InnerConnectionStatus.CONNECTED) {
                    LogUtil.w(TAG, "Kernel is still CONNECTED after INIT failure, disconnecting now")
                    try {
                        kernel.disconnect()
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error disconnecting kernel during validation: ${e.message}")
                    }
                }
                return false  // Inconsistency detected and handled
            }

            LogUtil.w(
                TAG,
                "Status inconsistency detected: ConnectionManager=$connectionStatus, Kernel=$kernelStatus (expected: $expectedStatus), auto-fixing..."
            )
            synchronized(this) {
                connectionStatus = expectedStatus
            }
            return false
        }

        return true
    }
}