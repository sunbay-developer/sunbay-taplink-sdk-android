@file:Suppress("DEPRECATION")

package com.sunmi.tapro.taplink.sdk.manager

import android.content.Context
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.DiscoveryListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.AppToAppMode
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.model.DiscoveredService
import com.sunmi.tapro.taplink.sdk.persistence.ConnectionPersistence
import com.sunmi.tapro.taplink.sdk.protocol.ProtocolConfigResolver
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback as ServiceConnectionCallback
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.lan.LanClientKernel
import com.sunmi.tapro.taplink.communication.lan.discovery.ServiceDiscoveryManager
import com.sunmi.tapro.taplink.sdk.scanner.QrScanCoordinator
import kotlinx.coroutines.*
import kotlin.coroutines.resume

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
     * Internal fallback listener for reconnection flows.
     *
     * Used when app does not keep a persistent ConnectionListener reference.
     * This keeps transport reconnection functional without forcing SDK user callbacks.
     */
    private val internalReconnectListener = object : ConnectionListener {
        override fun onConnected(deviceId: String, taproVersion: String) {
            // no-op
        }

        override fun onDisconnected(reason: String) {
            // no-op
        }

        override fun onError(error: ConnectionError) {
            // no-op
        }
    }

    /**
     * Current connection mode
     */
    private var currentConnectionMode: String? = null

    /**
     * Current App-to-App transaction mode.
     * Only meaningful when current connection mode is APP_TO_APP.
     */
    private var currentAppToAppMode: AppToAppMode? = null

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

    /**
     * CROSS_DEVICE AUTO fallback state.
     *
     * [isAutoSession] is true only when the current connection was established from a CROSS_DEVICE
     * AUTO session (config.crossDeviceAuto). [autoCandidates] holds the ordered transport candidates
     * so that runtime fallback can rotate to the next transport when the active one becomes
     * unusable (repeated request timeouts / blocked transport). [autoTimeoutCount] counts
     * consecutive request timeouts since the last successful response.
     */
    private var isAutoSession: Boolean = false
    private var autoCandidates: List<Pair<String, String>>? = null
    private var autoTimeoutCount: Int = 0

    /**
     * Guards against re-entrant / rapidly-repeated transport switch attempts. A single physical
     * event can produce a burst of drop notifications; while a switch is in flight further attempts
     * are ignored. Internal only — not configurable by integrators.
     */
    private var transportSwitchInProgress: Boolean = false

    /**
     * Coroutine job for auto-discovery operation.
     * Cancelled on disconnect() to abort discovery.
     */
    private var discoveryJob: Job? = null

    /**
     * Coroutine scope for auto-discover and scan-and-connect operations.
     */
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
                val inProgressConfig = reconnectManager?.getLastConnectionConfig()
                if (inProgressConfig == null || actualConfig.isEquivalentTo(inProgressConfig)) {
                    // Same connection request already in flight: queue this listener.
                    LogUtil.w(TAG, "Connection already in progress (same config), adding listener to queue")
                    pendingConnectionListeners.add(listener)
                    LogUtil.d(TAG, "Added listener to pending connection queue, total: ${pendingConnectionListeners.size}")
                    return
                } else {
                    // A DIFFERENT config was requested (e.g. switching Cable -> LAN) while the
                    // previous attempt is still stuck in CONNECTING. Cancel the stuck attempt and
                    // start fresh; otherwise the new request would be queued behind an attempt that
                    // may never complete, and the mode switch would silently fail.
                    LogUtil.w(TAG, "Different config requested while a connection is in progress — cancelling stuck attempt and reconnecting")
                    disconnect()
                    reconnectManager?.prepareConnect(actualConfig, listener)
                    connectInternal(actualConfig, listener)
                    return
                }
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
        // Reset mode cache before this connection attempt computes the effective mode.
        currentAppToAppMode = null

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

        // CROSS_DEVICE AUTO mode: try an ordered list of transports (per config.autoPriority), spanning
        // LAN and CABLE. Reuses the same connect-by-attempt framework as Cable AUTO. Marks the
        // session as an AUTO session so runtime request-timeout fallback can rotate transports.
        val autoCandidateList = ProtocolConfigResolver.getCrossDeviceAutoCandidates(
            config,
            reconnectManager?.getContext()
        )
        if (!autoCandidateList.isNullOrEmpty()) {
            isAutoSession = true
            autoCandidates = autoCandidateList
            autoTimeoutCount = 0
            tryConnectWithProtocolList(autoCandidateList, connectionConfig, listener)
            return
        } else {
            // Not a CROSS_DEVICE AUTO session (or AUTO with no resolvable candidate): clear AUTO state.
            clearAutoFallbackState()
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
        currentAppToAppMode = if (connectionMode == ConnectionMode.APP_TO_APP.name) {
            val resolvedMode = connectionConfig?.appToAppMode ?: AppToAppMode.CUSTOM
            LogUtil.d(TAG, "Resolved App-to-App mode on connect: $resolvedMode")
            resolvedMode
        } else {
            null
        }

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

                    // Extract TaPro version and device ID from extraInfoMap if available.
                    // In APP_TO_APP mode, TaPro returns these via getExtraInfos() AIDL method
                    // at connection time. For LAN/Cable modes, extraInfoMap does not contain
                    // version info — it will be populated later from the first INIT response.
                    val deviceId = extraInfoMap?.get(EXTRA_KEY_DEVICE_ID) ?: "unknown"
                    val taproVersion = extraInfoMap?.get(EXTRA_KEY_TAPRO_VERSION) ?: "unknown"

                    connectedDeviceId = deviceId
                    connectedTaproVersion = taproVersion
                    currentConnectionConfig = connectionConfig

                    if (taproVersion != "unknown") {
                        LogUtil.d(TAG, "TaPro version obtained at connect: $taproVersion, deviceId: $deviceId")
                    } else {
                        LogUtil.d(TAG, "Physical connection established, TaPro version will be obtained on first transaction (INIT)")
                    }

                    updateConnectionStatus(
                        ConnectionStatus.CONNECTED,
                        deviceId = deviceId,
                        taproVersion = taproVersion,
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

                        // Attribute the error to the best-known device identity. In LAN mode the
                        // live/persisted device id is often absent, so fall back to the mDNS-bound
                        // serial (getReliableDeviceId) — resolved BEFORE the cleanup below clears
                        // connectedDeviceId — so integrators still learn which device dropped.
                        val errorDeviceId = getReliableDeviceId(
                            connectedDeviceId,
                            reconnectManager?.getLastConnectedDeviceId()
                        )

                        // Create connection error object
                        val connectionError = ConnectionError(code, msg, deviceId = errorDeviceId)
                        updateConnectionStatus(
                            ConnectionStatus.ERROR,
                            errorCode = connectionError,
                            listener = listener
                        )

                        // Clean up resources
                        connectedDeviceId = null
                        connectedTaproVersion = null
                        currentConnectionMode = null
                        currentAppToAppMode = null
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
                            currentAppToAppMode = null
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

        /**
         * Set true once ANY protocol in this list has reached CONNECTED. Distinguishes a
         * connection-establishment failure (try the next protocol) from a passive drop of an
         * already-established transport (AUTO drop-fallback rotates to the next candidate).
         */
        var connectedOnce = false

        fun tryNextProtocol() {
            if (tryIndex >= protocols.size) {
                LogUtil.w(TAG, "All cable protocols failed: VSP, RS232, AOA")
                transportSwitchInProgress = false
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
                        connectedOnce = true

                        // Save successful protocol for future reconnects
                        runCatching {
                            CableProtocol.valueOf(mode)
                        }.onSuccess { cableProtocol ->
                            connectionPersistence.saveDetectedCableProtocol(cableProtocol, null)
                        }

                        syncStatusFromKernel()
                        registerDataReceiver()

                        // Extract TaPro version and device ID if provided (typically not
                        // available in Cable mode, but handle it consistently).
                        val deviceId = extraInfoMap?.get(EXTRA_KEY_DEVICE_ID) ?: "unknown"
                        val taproVersion = extraInfoMap?.get(EXTRA_KEY_TAPRO_VERSION) ?: "unknown"

                        connectedDeviceId = deviceId
                        connectedTaproVersion = taproVersion
                        currentConnectionConfig = connectionConfig

                        if (taproVersion != "unknown") {
                            LogUtil.d(TAG, "TaPro version obtained at connect ($mode): $taproVersion, deviceId: $deviceId")
                        }

                        updateConnectionStatus(
                            ConnectionStatus.CONNECTED,
                            deviceId = deviceId,
                            taproVersion = taproVersion,
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

                        // Passive drop of an already-established AUTO transport: rotate to the next
                        // candidate (cable dropped → LAN; LAN dropped → cable). Default behavior when
                        // the AUTO session has 2+ candidates. Applies regardless of whether the drop
                        // is classified as an error or a normal disconnect.
                        if (connectedOnce && isAutoSession) {
                            if (isConnectionError) {
                                paymentManager?.failAllPendingTransactions(code, msg)
                            }
                            if (rotateAutoTransportAfterDrop()) {
                                syncStatusFromKernel()
                                return
                            }
                        }

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
                            currentAppToAppMode = null
                            currentConnectionConfig = null
                            // A fallback/hot-switch reconnect that ended in ERROR must not leave the
                            // guard set, or future drop-fallback / hot-switch attempts are blocked.
                            transportSwitchInProgress = false
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

        // Cancel any in-progress discovery or scan-and-connect
        discoveryJob?.cancel()
        discoveryJob = null
        QrScanCoordinator.cancelSession()

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
        currentAppToAppMode = null
        currentConnectionConfig = null
        clearAutoFallbackState()

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
     * Returns the current [ConnectionStatus].
     *
     * More granular than [isConnected]: distinguishes CONNECTING, WAIT_CONNECTING,
     * CONNECTED, DISCONNECTED, and ERROR states.
     */
    fun getConnectionStatus(): ConnectionStatus = connectionStatus

    /**
     * Actively probe whether the current link is alive.
     *
     * Unlike [isConnected] (which only reflects the cached kernel status), this performs a
     * best-effort round-trip check on the underlying transport. It lets callers fail fast when the
     * peer (TaPro) has stopped responding — e.g. a stuck cable/UART port — instead of waiting for a
     * full transaction timeout.
     *
     * Runs the (potentially blocking) probe on [Dispatchers.IO].
     *
     * @param timeoutMs Maximum time to wait for the peer to acknowledge the probe.
     * @return `true` if the link is confirmed alive, `false` otherwise.
     */
    suspend fun checkLinkAlive(timeoutMs: Long): Boolean {
        if (!isConnected()) {
            return false
        }
        val serviceKernel = TaplinkServiceKernel.getInstance() ?: return false
        return withContext(Dispatchers.IO) {
            serviceKernel.checkLinkAlive(timeoutMs)
        }
    }

    /**
     * Fire-and-forget variant of [checkLinkAlive] for non-suspend callers (e.g. PaymentManager).
     * [onResult] is delivered on the main thread.
     */
    fun checkLinkAliveAsync(timeoutMs: Long, onResult: (Boolean) -> Unit) {
        sdkScope.launch {
            val alive = try {
                checkLinkAlive(timeoutMs)
            } catch (e: Exception) {
                LogUtil.e(TAG, "checkLinkAliveAsync failed: ${e.message}")
                false
            }
            onResult(alive)
        }
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
     * Called by PaymentManager when a transaction request receives any response from the peer.
     * Resets the CROSS_DEVICE AUTO consecutive-timeout counter (the active transport is proven healthy).
     */
    fun onRequestResponded() {
        if (!isAutoSession) return
        if (autoTimeoutCount != 0) {
            LogUtil.d(TAG, "AUTO: request responded, resetting timeout counter")
        }
        autoTimeoutCount = 0
    }

    /**
     * Called by PaymentManager when a transaction request times out (no response from peer).
     *
     * For a CROSS_DEVICE AUTO session, a request timeout means the active transport may be connected at
     * the socket level but blocked at the application level. After [AUTO_FALLBACK_TIMEOUT_THRESHOLD]
     * consecutive timeouts, rotate to the next transport candidate ([maybeTriggerAutoFallback]).
     *
     * This only affects the CONNECTION layer; the single-transaction timeout semantics (E306 must
     * be resolved via query-first, never blind retry) are handled independently by PaymentManager.
     *
     * @param errorCode the inner error code of the timed-out request (e.g. E306)
     */
    fun onRequestTimedOut(errorCode: String?) {
        if (!isAutoSession) return
        autoTimeoutCount++
        // Transport switching is now performed ONLY after the active connection actually drops
        // (see rotateAutoTransportAfterDrop). A request timeout while the socket still appears
        // connected no longer proactively tears down and rotates the transport — doing so risked
        // interrupting an in-flight transaction and switching to a different device. The timeout is
        // recorded for diagnostics only; the single-transaction timeout rule (E306 → query-first)
        // is handled by PaymentManager.
        LogUtil.w(TAG, "AUTO: request timed out (code=$errorCode), consecutive=$autoTimeoutCount (no transport switch; waiting for real disconnect)")
    }

    /**
     * Handles a passive drop of the active transport in a CROSS_DEVICE AUTO session by rotating to
     * the next candidate and reconnecting — cable unplugged → try LAN; LAN dropped → try cable
     * (if present in the candidate list). This is the DEFAULT behavior whenever the AUTO session has
     * two or more candidates; no opt-in flag is required.
     *
     * The active transport has already gone away (this is called from the transport's onDisconnected),
     * so the current kernel is not torn down again here.
     *
     * @return true if a fallback reconnect was initiated, false otherwise (caller then applies its
     *   normal DISCONNECTED/ERROR handling).
     */
    private fun rotateAutoTransportAfterDrop(): Boolean {
        if (!isAutoSession) return false
        val candidates = autoCandidates
        if (candidates == null) {
            LogUtil.d(TAG, "AUTO drop-fallback skipped: no candidates")
            return false
        }
        if (transportSwitchInProgress) {
            LogUtil.d(TAG, "AUTO drop-fallback skipped: a switch is already in progress")
            return false
        }

        // Never switch transports while a transaction is in flight. Tearing the transport down
        // mid-transaction drops the response callback and cannot recover the in-flight transaction;
        // it also risks reconnecting to a DIFFERENT device. Let the normal disconnect path fail the
        // pending transaction and notify the integrator instead.
        if (paymentManager?.hasInFlightTransactions() == true) {
            LogUtil.w(TAG, "AUTO drop-fallback skipped: a transaction is in flight; not switching transport")
            return false
        }

        // Fallback is viable when there is more than one candidate to rotate through, OR when LAN is
        // an allowed transport that can still be discovered+injected via mDNS even though no LAN
        // candidate is currently in the list (e.g. cable-only candidates with no configured host).
        val cfg = currentConnectionConfig ?: reconnectManager?.getLastConnectionConfig()
        val lanInPriority = cfg?.autoPriority?.contains(
            com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy.LAN
        ) == true
        val hasLanCandidate = candidates.any { it.second == ConnectionMode.LAN.name }
        val canInjectLan = lanInPriority && !hasLanCandidate
        if (candidates.size < 2 && !canInjectLan) {
            LogUtil.d(TAG, "AUTO drop-fallback skipped: not enough candidates and no LAN to discover")
            return false
        }

        // If the transport that just dropped is LAN, only fall back to cable when a cable is
        // physically attached. Otherwise the only fallback target (cable) is guaranteed to fail
        // with "no USB device attached", and — combined with the LAN kernel's own mDNS
        // address-change reconnect churn — this produced a reconnect storm on LAN-only setups.
        // A LAN drop with no cable present is left to the LAN kernel's own reconnect/self-healing.
        val currentIsLan = currentConnectionMode == ConnectionMode.LAN.name
        if (currentIsLan) {
            val ctx = reconnectManager?.getContext() ?: context
            val cablePresent =
                com.sunmi.tapro.taplink.sdk.protocol.InsertedCableClassifier.classify(ctx) != null
            if (!cablePresent) {
                LogUtil.d(TAG, "AUTO drop-fallback skipped: LAN dropped and no physical cable attached; leaving LAN to self-reconnect")
                return false
            }
        }

        val rotated = rotateAfterCurrent(candidates, currentConnectionMode)
        LogUtil.w(TAG, "AUTO drop-fallback: transport '$currentConnectionMode' dropped, rotating to next candidate")
        autoTimeoutCount = 0
        reconnectWithRotatedCandidates(rotated, tearDownCurrent = false)
        return true
    }

    /**
     * Rotates [candidates] so that the candidate AFTER [current] comes first (current and anything
     * before it move to the end). When [current] is not found, the list is returned unchanged.
     */
    private fun rotateAfterCurrent(
        candidates: List<Pair<String, String>>,
        current: String?
    ): List<Pair<String, String>> {
        val idx = candidates.indexOfFirst { it.second == current }
        return if (idx >= 0) {
            candidates.drop(idx + 1) + candidates.take(idx + 1)
        } else {
            candidates
        }
    }

    /**
     * Shared reconnect path for AUTO transport rotation (timeout fallback, hot-switch, passive drop).
     *
     * Before reconnecting, any LAN candidate is resolved to the CURRENT service address of the SAME
     * TaPro via mDNS (see [resolveLanCandidatesViaMdns]) so a LAN fallback still reaches the right
     * device even if its IP changed; the configured host/port is used as a fallback when discovery
     * finds no matching service.
     *
     * @param rotated the rotated candidate list (next transport first)
     * @param tearDownCurrent whether to disconnect the current kernel before reconnecting (true for
     *   an active-but-blocked transport, false when the transport already dropped)
     */
    private fun reconnectWithRotatedCandidates(
        rotated: List<Pair<String, String>>,
        tearDownCurrent: Boolean
    ) {
        val listener = connectionListener
            ?: reconnectManager?.getLastConnectionListener()
            ?: internalReconnectListener
        val config = currentConnectionConfig ?: reconnectManager?.getLastConnectionConfig()

        transportSwitchInProgress = true
        autoCandidates = rotated

        // Move to CONNECTING immediately so integrators never observe a stale CONNECTED while the
        // active transport is gone and we are (asynchronously) discovering + reconnecting the next
        // candidate. Without this, a passive drop followed by a slow/empty mDNS discovery would
        // leave the SDK reporting CONNECTED even though TaPro is no longer reachable.
        updateConnectionStatus(ConnectionStatus.CONNECTING, listener = listener)

        sdkScope.launch {
            val resolved = resolveLanCandidatesViaMdns(rotated)
            autoCandidates = resolved

            // The mDNS resolution above blocks for up to DISCOVERY_TIMEOUT_MS. While it runs, the
            // ordinary candidate walk (tryConnectWithProtocolList, driven by the transport's
            // onDisconnected) may already have brought a candidate up, and the integrator may even
            // have started a transaction on that link. Continuing here would tear down a healthy
            // connection mid-transaction, so re-check the world before touching any kernel.
            if (abandonFallbackIfLinkRecovered(listener)) {
                return@launch
            }

            // If nothing is connectable (e.g. LAN candidate could not be resolved and there is no
            // usable fallback address, and no cable candidate remains), do not silently hang in
            // CONNECTING — surface a disconnect so the integrator can react.
            if (resolved.isEmpty()) {
                LogUtil.w(TAG, "AUTO fallback: no connectable candidate after resolution; reporting disconnected")
                transportSwitchInProgress = false
                clearAutoFallbackState()
                updateConnectionStatus(
                    ConnectionStatus.DISCONNECTED,
                    reason = "AUTO fallback: no reachable transport",
                    listener = listener
                )
                return@launch
            }

            if (tearDownCurrent) {
                TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.disconnect()
            }
            tryConnectWithProtocolList(resolved, config, listener)
        }
    }

    /**
     * Guard against an AUTO fallback that has become obsolete while it was waiting on mDNS
     * discovery.
     *
     * A single transport drop can start two recovery paths at once: the candidate walk driven by
     * the transport's `onDisconnected` (which reconnects immediately) and
     * [reconnectWithRotatedCandidates] (which first spends up to `DISCOVERY_TIMEOUT_MS` resolving
     * LAN addresses via mDNS). By the time the slow path resumes, the fast path has usually already
     * established a link — and the integrator may have started a transaction on it. Reconnecting
     * then kills a live link mid-transaction and loses the in-flight response.
     *
     * @return true when the fallback was abandoned and the caller must stop.
     */
    private fun abandonFallbackIfLinkRecovered(listener: ConnectionListener?): Boolean {
        val kernelStatus = TaplinkServiceKernel.getInstance()
            ?.getCurrentServiceKernel()
            ?.getConnectionStatus()
        val linkUp = kernelStatus == InnerConnectionStatus.CONNECTED
        val txInFlight = paymentManager?.hasInFlightTransactions() == true

        if (!linkUp && !txInFlight) return false

        LogUtil.w(
            TAG,
            "AUTO fallback abandoned after mDNS discovery: link already recovered " +
                "(kernelStatus=$kernelStatus, transactionInFlight=$txInFlight)"
        )
        transportSwitchInProgress = false

        if (linkUp) {
            // The fallback moved the SDK to CONNECTING before discovery; restore the real state so
            // the integrator is not left believing the session is still reconnecting.
            updateConnectionStatus(
                ConnectionStatus.CONNECTED,
                deviceId = connectedDeviceId,
                taproVersion = connectedTaproVersion,
                listener = listener
            )
        }
        return true
    }

    /**
     * Resolves the LAN transport of an AUTO session to the current mDNS-advertised address of the
     * SAME TaPro device, so a LAN fallback reaches the right terminal even if its IP changed.
     *
     * Triggering: runs whenever the session's `autoPriority` includes LAN OR [candidates] already
     * contains a LAN entry.
     *
     * Device matching (same TaPro): picks the discovered service whose parsed serial
     * ([LanClientKernel.parseServiceSerial]) matches the reliable target device id
     * ([getReliableDeviceId]). The target serial comes from the live/persisted device id or, most
     * importantly, the serial persisted from a prior LAN connection to this device
     * ([ConnectionPersistence.getLastConnectedSerial]) — this is why "connect once, then rediscover
     * the SAME device by serial next time" works.
     *
     * No reliable serial: the SDK does NOT connect an arbitrary discovered service (on a shared
     * network that is very likely a different terminal). Instead it keeps the configured LAN
     * candidate — AUTO sessions are required to configure a LAN `host` as the fallback target — so
     * the preconfigured address is used. If there is neither a serial match nor a configured LAN
     * candidate, the fallback reports DISCONNECTED rather than connecting the wrong device.
     *
     * Result:
     * - serial match found → the LAN candidate address is (re)built with the discovered host/port;
     * - no match → the existing (configured-host) LAN candidate is preserved as the fallback target.
     *
     * Non-LAN candidates pass through unchanged.
     */
    private suspend fun resolveLanCandidatesViaMdns(
        candidates: List<Pair<String, String>>
    ): List<Pair<String, String>> {
        val hasLanCandidate = candidates.any { it.second == ConnectionMode.LAN.name }
        val cfg = currentConnectionConfig ?: reconnectManager?.getLastConnectionConfig()
        val priority = cfg?.autoPriority ?: emptyList()
        val lanInPriority = priority.contains(com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy.LAN)

        // Only skip mDNS when LAN is neither a present candidate nor an allowed transport.
        if (!hasLanCandidate && !lanInPriority) return candidates

        // Prefer the serial the live LAN kernel is bound to. getReliableDeviceId falls back to the
        // serial persisted by a PREVIOUS session, which on a shared bench belongs to a different
        // terminal; matching on it would either find nothing or, worse, resolve another device.
        val targetSerial = resolveBoundLanSerial()?.takeIf { it.isNotEmpty() }
            ?: getReliableDeviceId(
                connectedDeviceId,
                reconnectManager?.getLastConnectedDeviceId()
            )

        val discovered: DiscoveredService? = try {
            val ctx = reconnectManager?.getContext() ?: context
            val discoveryManager = ServiceDiscoveryManager(ctx)
            val services = discoveryManager.discoverServicesIsolated(
                NSD_SERVICE_TYPE, DISCOVERY_TIMEOUT_MS
            ).filter { it.host.isNotEmpty() && it.port > 0 }

            if (services.isEmpty()) {
                null
            } else if (!targetSerial.isNullOrEmpty() && targetSerial != "unknown") {
                // Prefer the service whose serial matches the same TaPro device.
                val match = services.firstOrNull {
                    LanClientKernel.parseServiceSerial(it.name) == targetSerial
                }
                match?.let { DiscoveredService(it.name, it.host, it.port) }
                    ?: run {
                        LogUtil.w(TAG, "AUTO LAN fallback: no mDNS service matched serial=$targetSerial; keeping existing candidates")
                        null
                    }
            } else {
                // No reliable serial to match on. Do NOT blindly connect the first discovered
                // service — on a shared network that is very likely a DIFFERENT terminal. Only a
                // preconfigured host may be used (handled below by keeping existing candidates).
                LogUtil.w(TAG, "AUTO LAN fallback: no reliable target serial; refusing to connect an arbitrary discovered service")
                null
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "AUTO LAN fallback: mDNS discovery failed: ${e.message}; keeping existing candidates")
            null
        }

        if (discovered == null) return candidates

        val lanCandidate = Pair(
            ProtocolManager.buildLanProtocol(discovered.host, discovered.port, secure = false),
            ConnectionMode.LAN.name
        )
        LogUtil.d(TAG, "AUTO LAN fallback: resolved LAN target to ${discovered.host}:${discovered.port} (service=${discovered.name})")

        if (hasLanCandidate) {
            // Rebuild the existing LAN candidate's address in place.
            return candidates.map { if (it.second == ConnectionMode.LAN.name) lanCandidate else it }
        }

        // No LAN candidate existed (no configured host): inject one according to priority.
        val cableRank = priority.indexOf(com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy.CABLE)
        val lanRank = priority.indexOf(com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy.LAN)
        val lanFirst = lanRank in 0 until (if (cableRank < 0) Int.MAX_VALUE else cableRank)
        return if (lanFirst) listOf(lanCandidate) + candidates else candidates + lanCandidate
    }

    /**
     * Clears all CROSS_DEVICE AUTO fallback state.
     */
    private fun clearAutoFallbackState() {
        isAutoSession = false
        autoCandidates = null
        autoTimeoutCount = 0
        transportSwitchInProgress = false
    }

    /**
     * Get App-to-App transaction mode for current connection.
     */
    fun getAppToAppMode(): AppToAppMode? {
        return currentAppToAppMode
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

                // A new successful connection ends any in-flight transport switch.
                transportSwitchInProgress = false

                // Persist the LAN-bound Tapro serial (if this connection is LAN) so that a later
                // AUTO fallback to LAN can rediscover the SAME device via mDNS even after the LAN
                // kernel is torn down (transport switch / disconnect clears the live/bound serial).
                persistLanSerialIfAvailable()
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
            "1002", "1015", // WebSocket connection error codes
            "CONNECTION_FAILED", "WEBSOCKET_NULL", "SEND_FAILED", // Custom error codes
            "SERVICE_UNAVAILABLE", "PARSE_ERROR", "PREPARE_ERROR" // INIT related error codes
        )

        // Normal disconnect error codes. These represent a real, already-established
        // connection going away (network drop, remote reset, manual disconnect), and
        // should go through the DISCONNECTED + auto-reconnect path rather than being
        // treated as a permanent connection ERROR with no recovery attempt.
        // Note: "1006" (abnormal WebSocket closure, e.g. TCP reset / network drop) is
        // intentionally included here — it is the most common real-world disconnect
        // code once a LAN connection has already been established, and previously being
        // classified as a connection error meant the SDK would give up without ever
        // attempting to reconnect, leaving it stuck until a manual reconnect.
        val normalDisconnectCodes = setOf(
            "1000", // WebSocket normal close
            "1001", // WebSocket endpoint leaving
            "1006", // WebSocket abnormal close (no close frame received, e.g. network drop)
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

        // mDNS serial-segment exact match: the LAN service name is "Taplink-Server-<ts>-<serial>",
        // whose trailing '-'-delimited segment is the Tapro serial. Reuse LanClientKernel's
        // canonical parse so this same-device check stays in lockstep with the discovery binding,
        // and avoid false positives from a serial that merely appears as a substring elsewhere.
        val serialSegmentMatch =
            LanClientKernel.parseServiceSerial(newServiceName) == targetDeviceId
        if (serialSegmentMatch) {
            return DeviceMatchResult(
                matchType = DeviceMatchType.SAME_DEVICE,
                confidence = 1.0f,
                reason = "mDNS serial segment exact match"
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
     * Get the most reliable device ID.
     *
     * Priority: live connected id > persisted last connected id > LAN bound serial.
     *
     * The LAN serial fallback matters because in LAN mode the AIDL/INIT device id is often absent
     * or already cleared on a passive disconnect (e.g. peer mDNS re-register), yet the transport
     * layer has bound a target serial parsed from the mDNS service name. Using it keeps device
     * attribution and same-device reconnection decisions working across peer restarts.
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
                val lanSerial = resolveBoundLanSerial()
                if (!lanSerial.isNullOrEmpty()) {
                    LogUtil.d(TAG, "Using LAN bound serial as device ID: $lanSerial")
                    lanSerial
                } else {
                    // Last resort: the serial persisted from a previous LAN connection. This is what
                    // makes "cable dropped → discover the SAME TaPro's LAN service" work after the
                    // LAN kernel (and its bound serial) is already gone.
                    val persistedSerial = connectionPersistence.getLastConnectedSerial()
                    if (!persistedSerial.isNullOrEmpty()) {
                        LogUtil.d(TAG, "Using persisted last-connected serial as device ID: $persistedSerial")
                        persistedSerial
                    } else {
                        LogUtil.w(TAG, "No reliable device ID available")
                        null
                    }
                }
            }
        }
    }

    /**
     * Persists the Tapro serial the active LAN kernel is bound to, if any. Safe to call after every
     * successful connection; it is a no-op for non-LAN transports or when no serial is bound.
     */
    private fun persistLanSerialIfAvailable() {
        val serial = resolveBoundLanSerial()
        if (!serial.isNullOrEmpty()) {
            connectionPersistence.saveLastConnectedSerial(serial)
        }
    }

    /**
     * The Tapro serial the active LAN kernel is bound to (parsed from the mDNS service name),
     * or null when not in LAN mode / not yet bound.
     */
    private fun resolveBoundLanSerial(): String? {
        return try {
            val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
            (kernel as? LanClientKernel)?.getTargetServiceSerial()
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to resolve bound LAN serial: ${e.message}")
            null
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

        val normalizedServiceName = serviceName.lowercase()
        val isExpectedServiceName = normalizedServiceName.contains("tapro") ||
            normalizedServiceName.contains("taplink") ||
            normalizedServiceName.startsWith("taplink-server")

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

            val reconnectListener =
                connectionListener ?: reconnectManager?.getLastConnectionListener()
                    ?: internalReconnectListener
            LogUtil.d(
                TAG,
                "Executing reconnection to $newHost:$newPort, listener=$reconnectListener"
            )
            reconnectManager?.onAddressChanged(newConnectionConfig, reconnectListener)
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
                val previousStatus = connectionStatus
                LogUtil.d(
                    TAG,
                    "Kernel status changed: $kernelStatus -> syncing to ConnectionManager: $newStatus (was: $previousStatus)"
                )
                // Update status without triggering callbacks here (the primary notification path
                // is ConnectionCallback). We only additionally notify the integrator when the
                // kernel dropped to DISCONNECTED/ERROR while the SDK still believed it was
                // connected/connecting — otherwise that drop is only reflected in the internal
                // status field and the integrator (e.g. Demo ConnectionManager) stays stuck in
                // CONNECTING until the next connect()'s validateStatusConsistency reconciles it.
                synchronized(this) {
                    connectionStatus = newStatus
                }

                val droppedToInactive = newStatus == ConnectionStatus.DISCONNECTED ||
                        newStatus == ConnectionStatus.ERROR
                val wasActive = previousStatus == ConnectionStatus.CONNECTED ||
                        previousStatus == ConnectionStatus.CONNECTING ||
                        previousStatus == ConnectionStatus.WAIT_CONNECTING
                // Suppress during self-healing reconnect so a transient VSP/sub-screen kernel
                // bounce (which surfaces as RECONNECTING -> CONNECTING, not DISCONNECTED) or an
                // in-progress reconnect never raises a spurious onDisconnected to the integrator.
                val isSelfHealing = reconnectManager?.isReconnecting() == true

                if (droppedToInactive && wasActive && !isSelfHealing) {
                    // CROSS_DEVICE AUTO passive-drop fallback via the kernel status listener is
                    // restricted to CABLE transports. Cable kernels (notably AOA on device-detach)
                    // may only call updateStatus() without invoking the ConnectionCallback, so the
                    // status listener is the only reliable drop signal for them.
                    //
                    // LAN is intentionally EXCLUDED here: the LAN kernel has its own mDNS
                    // address-change reconnect / self-healing path that produces transient
                    // CONNECTED⇄DISCONNECTED churn (especially on multi-terminal networks). Letting
                    // that churn trigger an AUTO fallback caused a reconnect storm (LAN "dropped" →
                    // rotate → cable candidates all fail "no USB device" → LAN reconnect → repeat
                    // every few seconds). LAN drops are handled by the ConnectionCallback path and
                    // the LAN kernel's own reconnect instead.
                    val current = currentConnectionMode
                    val currentIsCable = current == CableProtocol.USB_AOA.name ||
                        current == CableProtocol.USB_VSP.name ||
                        current == CableProtocol.RS232.name

                    if (isAutoSession && currentIsCable && rotateAutoTransportAfterDrop()) {
                        LogUtil.w(
                            TAG,
                            "Kernel dropped to $newStatus on cable ($current); AUTO fallback initiated from status listener"
                        )
                        return@setStatusChangeListener
                    }

                    val reason = "Kernel status changed to $kernelStatus"
                    LogUtil.w(
                        TAG,
                        "Kernel dropped to $newStatus while SDK was $previousStatus and not reconnecting; " +
                            "notifying integrator onDisconnected to keep states in sync"
                    )
                    try {
                        connectionListener?.onDisconnected(reason)
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error notifying onDisconnected from kernel status sync: ${e.message}")
                    }
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

    // ==================== Auto-Discover & Scan-and-Connect ====================

    companion object {
        private const val NSD_SERVICE_TYPE = "_taplink._tcp"
        private const val DISCOVERY_TIMEOUT_MS = 15_000L

        /**
         * Consecutive request-timeout threshold before a CROSS_DEVICE AUTO session rotates to the next
         * transport candidate. Set to 1 so a single blocked request triggers fallback quickly.
         */
        private const val AUTO_FALLBACK_TIMEOUT_THRESHOLD = 1

        // Error codes for discovery and scan
        const val ERROR_NO_SERVICES = "E501"
        const val ERROR_ALL_FAILED = "E502"
        const val ERROR_OPERATION_IN_PROGRESS = "E503"
        const val ERROR_SCAN_CANCELLED = "E504"
        const val ERROR_CAMERA_PERMISSION_DENIED = "E505"
        const val ERROR_CAMERA_UNAVAILABLE = "E506"

        // Keys used by TaPro in extraInfoMap (returned via getExtraInfos in APP_TO_APP mode)
        private const val EXTRA_KEY_TAPRO_VERSION = "taproVersion"
        private const val EXTRA_KEY_DEVICE_ID = "deviceId"
    }

    /**
     * Discover LAN Taplink services via mDNS WITHOUT connecting.
     *
     * Runs a one-shot mDNS discovery and returns the resolved host/port list so the
     * caller can present or auto-fill the address and then call connect() with a LAN
     * config. Does not change connection status and does not connect.
     *
     * Call disconnect() to cancel an in-progress discovery.
     */
    fun discoverLanServices(listener: DiscoveryListener) {
        LogUtil.d(TAG, "discoverLanServices called")

        discoveryJob = sdkScope.launch {
            try {
                LogUtil.d(TAG, "Starting mDNS service discovery (discover-only)...")
                val discoveryManager = ServiceDiscoveryManager(context)
                val services = discoveryManager.discoverServicesIsolated(
                    NSD_SERVICE_TYPE, DISCOVERY_TIMEOUT_MS
                )

                val result = services
                    .filter { it.host.isNotEmpty() && it.port > 0 }
                    .map { DiscoveredService(it.name, it.host, it.port) }

                LogUtil.d(TAG, "discoverLanServices found ${result.size} valid services")
                listener.onDiscovered(result)
            } catch (e: CancellationException) {
                LogUtil.d(TAG, "discoverLanServices cancelled")
            } catch (e: Exception) {
                LogUtil.e(TAG, "discoverLanServices error: ${e.message}")
                listener.onError(
                    ConnectionError(ERROR_NO_SERVICES, "Discovery failed: ${e.message}")
                )
            } finally {
                discoveryJob = null
            }
        }
    }

    /**
     * Launch the QR scanner to read a lan:// code and return the parsed host/port
     * WITHOUT connecting.
     *
     * SDK opens its built-in camera scanner Activity and handles CAMERA permission.
     * Only lan://host/port QR codes are accepted. The resolved host/port is delivered
     * via [DiscoveryListener] (a single-element list) so the caller can auto-fill the
     * address and then call connect() with a LAN config.
     *
     * Call disconnect() to cancel an in-progress scan session.
     */
    fun scanLanQrCode(listener: DiscoveryListener) {
        LogUtil.d(TAG, "scanLanQrCode called")

        val started = QrScanCoordinator.startScanOnlySession(
            context = context,
            onResult = { service -> listener.onDiscovered(listOf(service)) },
            onError = { error -> listener.onError(error) }
        )

        if (!started) {
            listener.onError(
                ConnectionError(ERROR_OPERATION_IN_PROGRESS, "A scan session is already active")
            )
        }
    }

    /**
     * Auto-discover LAN services and connect to the first available one.
     *
     * Uses mDNS (Android NSD) to discover _taplink._tcp services, then tries
     * connecting to each in discovery order until one succeeds.
     *
     * Call disconnect() to cancel an in-progress discovery.
     */
    fun autoDiscoverAndConnect(listener: ConnectionListener) {
        LogUtil.d(TAG, "autoDiscoverAndConnect called")

        // Check if another operation is in progress
        if (connectionStatus == ConnectionStatus.CONNECTING ||
            connectionStatus == ConnectionStatus.WAIT_CONNECTING ||
            connectionStatus == ConnectionStatus.CONNECTED
        ) {
            LogUtil.w(TAG, "autoDiscoverAndConnect rejected: status=$connectionStatus")
            listener.onError(
                ConnectionError(ERROR_OPERATION_IN_PROGRESS, "Another connection operation is in progress")
            )
            return
        }

        updateConnectionStatus(ConnectionStatus.CONNECTING, listener = listener)
        connectionListener = listener

        discoveryJob = sdkScope.launch {
            try {
                LogUtil.d(TAG, "Starting mDNS service discovery...")
                val discoveryManager = ServiceDiscoveryManager(context)
                val services = discoveryManager.discoverServicesIsolated(
                    NSD_SERVICE_TYPE, DISCOVERY_TIMEOUT_MS
                )

                // Filter valid services
                val validServices = services.filter { it.host.isNotEmpty() && it.port > 0 }
                if (validServices.isEmpty()) {
                    LogUtil.w(TAG, "No valid services discovered")
                    updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
                    listener.onError(
                        ConnectionError(ERROR_NO_SERVICES, "No Taplink services found on the network")
                    )
                    return@launch
                }

                LogUtil.d(TAG, "Found ${validServices.size} valid services, trying sequentially")

                // Try each service sequentially
                for ((index, service) in validServices.withIndex()) {
                    ensureActive()
                    LogUtil.d(TAG, "Trying service ${index + 1}/${validServices.size}: ${service.host}:${service.port}")

                    val success = suspendCancellableCoroutine<Boolean> { cont ->
                        val config = ConnectionConfig.createLanMode(service.host, service.port)
                        val proxy = object : ConnectionListener {
                            override fun onConnected(deviceId: String, taproVersion: String) {
                                // Success — forward to original listener
                                listener.onConnected(deviceId, taproVersion)
                                if (cont.isActive) cont.resume(true)
                            }

                            override fun onDisconnected(reason: String) {
                                // Treat as failure for this candidate
                                if (cont.isActive) cont.resume(false)
                            }

                            override fun onError(error: ConnectionError) {
                                // This candidate failed, don't forward
                                LogUtil.w(TAG, "Candidate ${service.host}:${service.port} failed: ${error.message}")
                                if (cont.isActive) cont.resume(false)
                            }

                            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                                // Ignore reconnection during discovery
                            }
                        }

                        // Reset status so connect() doesn't reject
                        connectionStatus = ConnectionStatus.DISCONNECTED
                        connect(config, proxy)
                    }

                    if (success) {
                        LogUtil.d(TAG, "Successfully connected to ${service.host}:${service.port}")
                        return@launch
                    }
                }

                // All candidates failed
                ensureActive()
                LogUtil.w(TAG, "All ${validServices.size} discovered services failed")
                updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
                listener.onError(
                    ConnectionError(ERROR_ALL_FAILED, "All discovered services failed to connect")
                )

            } catch (e: CancellationException) {
                LogUtil.d(TAG, "autoDiscoverAndConnect cancelled")
                // No callback on cancellation (disconnect() was called)
            } catch (e: Exception) {
                LogUtil.e(TAG, "autoDiscoverAndConnect error: ${e.message}")
                updateConnectionStatus(ConnectionStatus.DISCONNECTED, listener = listener)
                listener.onError(
                    ConnectionError(ERROR_NO_SERVICES, "Discovery failed: ${e.message}")
                )
            } finally {
                discoveryJob = null
            }
        }
    }

    /**
     * Launch QR scanner and connect using scanned lan:// QR code.
     *
     * SDK opens its built-in camera scanner Activity. Only lan://host/port
     * QR codes are accepted. Invalid codes trigger a toast and continue scanning.
     *
     * Call disconnect() to cancel an in-progress scan session.
     */
    fun scanAndConnect(listener: ConnectionListener) {
        LogUtil.d(TAG, "scanAndConnect called")

        // Check if another operation is in progress
        if (connectionStatus == ConnectionStatus.CONNECTING ||
            connectionStatus == ConnectionStatus.WAIT_CONNECTING ||
            connectionStatus == ConnectionStatus.CONNECTED
        ) {
            LogUtil.w(TAG, "scanAndConnect rejected: status=$connectionStatus")
            listener.onError(
                ConnectionError(ERROR_OPERATION_IN_PROGRESS, "Another connection operation is in progress")
            )
            return
        }

        // Delegate to QrScanCoordinator which manages the scanner Activity lifecycle
        val started = QrScanCoordinator.startSession(
            context = context,
            listener = listener,
            connectAction = { config, proxyListener ->
                connect(config, proxyListener)
            }
        )

        if (!started) {
            listener.onError(
                ConnectionError(ERROR_OPERATION_IN_PROGRESS, "A scan session is already active")
            )
        }
    }
}