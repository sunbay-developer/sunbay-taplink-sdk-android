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
import com.sunmi.tapro.taplink.communication.lan.LanConnectionOptions
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
 * @authorTapro Team
 * @since 2025-12-22
 */
class ConnectionManager(
    private val config: TaplinkConfig,
    private val context: Context
) : RecoveryHost {
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
     * OPT-R1: 是否正在执行「交易中单次快速恢复」。用于防止快速恢复与常规重连
     * （ReconnectManager）叠加触发双重重连，以及防止恢复期间的重入。
     */
    @Volatile
    private var isFastRecovering = false

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
     * Coroutine job for auto-discovery operation.
     * Cancelled on disconnect() to abort discovery.
     */
    private var discoveryJob: Job? = null

    /**
     * Coroutine scope for auto-discover and scan-and-connect operations.
     */
    private val sdkScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * SPEC-ESTABLISHED-RECOVERY-001 R3 hard gate: whether this logical session has ever reached
     * CONNECTED. Only an established session can be recovered; a never-established fault keeps the
     * existing first-connect strategy. Reset to false on explicit disconnect() / new-config connect().
     */
    @Volatile
    private var hasEverEstablishedThisSession = false

    /** Monotonic logical-session id, bumped when a fresh session begins (new-config connect/disconnect). */
    private val sessionIdCounter = java.util.concurrent.atomic.AtomicLong(0)

    @Volatile
    private var currentSessionId: Long = 0

    /**
     * SPEC-ESTABLISHED-RECOVERY-001 R8: trusted identity anchor captured when the connection first
     * reached CONNECTED, used to validate that a recovery rebuild reconnected to the SAME terminal.
     */
    @Volatile
    private var establishedTerminalIdentity:
        com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity? = null

    /**
     * The single owner of established-connection fault recovery (SPEC-ESTABLISHED-RECOVERY-001).
     * Lazily held so tests and the kernel wiring can observe recovery state.
     */
    private val establishedRecoveryCoordinator: EstablishedRecoveryCoordinator =
        EstablishedRecoveryCoordinator(this, SystemRecoveryClock(), sdkScope)

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

        // SPEC-ESTABLISHED-RECOVERY-001 R9: a connect() issued while an established-fault recovery is
        // active and asking for the SAME config joins the current episode's single terminal outcome
        // rather than opening a second transport. A DIFFERENT config supersedes recovery (fence it)
        // and starts a fresh session below.
        if (establishedRecoveryCoordinator.isRecovering()) {
            val sameConfig = currentConnectionConfig?.isEquivalentTo(actualConfig) == true
            if (sameConfig) {
                if (establishedRecoveryCoordinator.joinIfRecovering(listener)) {
                    return
                }
            } else {
                establishedRecoveryCoordinator.onExplicitDetachOrDisconnect()
                hasEverEstablishedThisSession = false
                establishedTerminalIdentity = null
                currentSessionId = sessionIdCounter.incrementAndGet()
            }
        }

        // Self-heal a STALE CONNECTING/WAIT_CONNECTING state before deciding how to connect.
        //
        // If the SDK still believes it is (WAIT_)CONNECTING but the underlying kernel is no longer
        // actually connecting/connected (it dropped to DISCONNECTED/ERROR, or there is no kernel at
        // all), the previous attempt died without ever driving the SDK status back. In that case a
        // fresh connect() with the same/absent config would be queued into pendingConnectionListeners
        // and hang forever, because that queue is only drained by a kernel callback that will never
        // come. Force a clean disconnect here so the request below takes the normal fresh-connect
        // path instead of the "already in progress" branch. A genuinely in-flight attempt is left
        // untouched (kernel is still CONNECTING/WAIT_CONNECTING/CONNECTED).
        if (connectionStatus == ConnectionStatus.CONNECTING ||
            connectionStatus == ConnectionStatus.WAIT_CONNECTING
        ) {
            val kernelActual = getActualConnectionStatus()
            val kernelStillActive = kernelActual == ConnectionStatus.CONNECTING ||
                kernelActual == ConnectionStatus.WAIT_CONNECTING ||
                kernelActual == ConnectionStatus.CONNECTED
            if (!kernelStillActive) {
                LogUtil.w(
                    TAG,
                    "Stale $connectionStatus detected (kernel actual=$kernelActual); resetting before reconnect"
                )
                disconnect()
            }
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
                    synchronized(pendingConnectionListeners) { pendingConnectionListeners.add(listener) }
                    LogUtil.d(TAG, "Added listener to pending connection queue, total: ${pendingConnectionListeners.size}")
                    return
                } else {
                    // A DIFFERENT config was requested (e.g. switching Cable -> LAN) while the
                    // previous attempt is still stuck in CONNECTING. Cancel the stuck attempt and
                    // start fresh; otherwise the new request would be queued behind an attempt that
                    // may never complete, and the mode switch would silently fail.
                    LogUtil.w(TAG, "Different config requested while a connection is in progress — cancelling stuck attempt and reconnecting")
                    disconnect()
                    reconnectManager.prepareConnect(actualConfig, listener)
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

        // Build protocol string based on ConnectionConfig (single, explicit protocol).
        // There is no AUTO transport/protocol selection: the integrator must specify the mode
        // (and, for cable, the concrete cable protocol) explicitly.
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

        val deferLanHandshakeFailure = connectionMode == ConnectionMode.LAN.name &&
            connectionConfig?.autoReconnect != false && !isFastRecovering
        val connectionEstablished = java.util.concurrent.atomic.AtomicBoolean(false)

        // Connect first, kernel will be created inside TaplinkServiceKernel.connect()
        serviceKernel?.connect(
            protocol,
            this.config.appId,
            this.config.secretKey,
            this.config.taproAppWidthRatio,
            lanConnectionOptions = if (connectionMode == ConnectionMode.LAN.name) {
                LanConnectionOptions(
                    allowServiceDiscoveryFallback = connectionConfig?.autoReconnect != false,
                    enableServiceMonitoring = connectionConfig?.autoReconnect != false,
                    deferHandshakeFailureCallbacks = deferLanHandshakeFailure
                )
            } else {
                null
            },
            connectionCallback = object : ServiceConnectionCallback {
                override fun onConnected(extraInfoMap: Map<String, String?>?) {
                    connectionEstablished.set(true)
                    // Sync status after connection success
                    syncStatusFromKernel()

                    // Register data receiver
                    registerDataReceiver()

                    // ExtractTapro version and device ID from extraInfoMap if available.
                    // In APP_TO_APP mode,Tapro returns these via getExtraInfos() AIDL method
                    // at connection time. For LAN/Cable modes, extraInfoMap does not contain
                    // version info — it will be populated later from the first INIT response.
                    val deviceId = extraInfoMap?.get(EXTRA_KEY_DEVICE_ID) ?: "unknown"
                    val taproVersion = extraInfoMap?.get(EXTRA_KEY_TAPRO_VERSION) ?: "unknown"

                    connectedDeviceId = deviceId
                    connectedTaproVersion = taproVersion
                    currentConnectionConfig = connectionConfig

                    if (taproVersion != "unknown") {
                        LogUtil.d(TAG, "Tapro version obtained at connect: $taproVersion, deviceId: $deviceId")
                    } else {
                        LogUtil.d(TAG, "Physical connection established, Tapro version will be obtained on first transaction (INIT)")
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
                    setupKernelStatusListener(deferLanHandshakeFailure)

                    updateConnectionStatus(
                        ConnectionStatus.WAIT_CONNECTING,
                        reason = "Waiting for connection",
                        listener = listener
                    )
                }

                override fun onDisconnected(code: String, msg: String) {
                    val reason = "Code: $code, Message: $msg"

                    // SPEC-ESTABLISHED-RECOVERY-001 R5/R6: an established-connection fault is owned by
                    // the recovery coordinator. Claiming the epoch is synchronous and happens BEFORE
                    // any classification, public callback, payment cleanup or ReconnectManager entry.
                    // On an established fault we early-return: no onError, no transient onDisconnected,
                    // no failAllPendingTransactions, no fast-recovery, no updateConnectionStatus here.
                    if (establishedRecoveryCoordinator.interceptIfEstablishedFault(
                            TransportFaultEvent(
                                sessionId = currentSessionId,
                                epoch = establishedRecoveryCoordinator.currentEpoch(),
                                code = code,
                                msg = msg,
                                established = hasEverEstablishedThisSession
                            )
                        )
                    ) {
                        syncStatusFromKernel()
                        return
                    }

                    // Determine if this is a connection error or normal disconnect
                    val isConnectionError = isConnectionError(code, msg)

                    if (isConnectionError) {
                        // Immediately trigger all pending transaction callbacks (when target process crashes)
                        paymentManager?.failAllPendingTransactions(code, msg)

                        // Attribute the error to the ACTIVE connection's device identity only.
                        // In LAN mode the live device id may be absent, so fall back to the
                        // mDNS-bound serial of the current link (getReliableDeviceId) — resolved
                        // BEFORE the cleanup below clears connectedDeviceId. It deliberately does
                        // NOT use any cross-session persisted id, so a failure is never
                        // misattributed to a previously connected device.
                        val errorDeviceId = getReliableDeviceId(connectedDeviceId)

                        // Create connection error object
                        val connectionError = ConnectionError(code, msg, deviceId = errorDeviceId)
                        // Address-change and delayed reconnect attempts enter ReconnectManager's
                        // reconnecting state before the transport is started. A connection-phase
                        // error reaches this branch instead of the normal disconnect branch, so
                        // explicitly terminate that state and fail listeners queued during the
                        // attempt. Otherwise a later connect() with the same config is accepted
                        // into the pending queue forever.
                        if (deferLanHandshakeFailure && !connectionEstablished.get()) {
                            notifyLanRecoveryFailure(connectionError, listener)
                        } else {
                            reconnectManager?.onReconnectFailed(connectionError)
                            updateConnectionStatus(
                                ConnectionStatus.ERROR,
                                errorCode = connectionError,
                                listener = listener
                            )
                        }

                        // Clean up resources
                        connectedDeviceId = null
                        connectedTaproVersion = null
                        currentConnectionMode = null
                        currentAppToAppMode = null
                        currentConnectionConfig = null
                    } else {
                        // OPT-R1: 交易进行中发生被动断开时，先尝试一次「原连接配置快速重连」，
                        // 不走 mDNS 发现、预算 5s。适用于所有连接模式（LAN / Cable 三子协议 /
                        // App-to-App / Cloud），不再以连接模式门控——Cable 拔线/VSP 自愈同样需要
                        // 走快恢路径，才能让 isSelfHealing 守卫生效（快恢期间不清交易回调、不清终态
                        // 去重集），使 Host 侧 traceId 锚点重指的续推事件能被 SDK 回调命中。触发条件：
                        //   1) autoReconnect 已开启（按产品要求，仅在开启时做交易中快速恢复）；
                        //   2) 确有在途交易；
                        //   3) 非用户主动断开；
                        //   4) 未在快速恢复 / 常规重连中；
                        //   5) 有可复用的连接配置。
                        // 本分支（normalDisconnect）本身即「已建立连接后的被动/正常断开」，涵盖
                        // 1006 等真实掉线码。成功则维持连接、不进入 ReconnectManager；失败则直接
                        // 向用户报「连接断开」(E213)，不再把底层 1006 透传给客户。
                        val autoReconnectEnabled = currentConnectionConfig?.autoReconnect == true
                        val isManual = reconnectManager?.isManualDisconnect() == true
                        val hasInFlight = paymentManager?.hasInFlightTransactions() == true
                        // SPEC-ESTABLISHED-RECOVERY-001 R4/AC4: when the coordinator owns an episode,
                        // never spawn the legacy fast-recovery coroutine — it would stack a second
                        // 5000ms budget and fail in-flight tx with an early E306. The coordinator does
                        // the single bounded rebuild and clears tx once as E213 on failure.
                        val canFastRecover = !establishedRecoveryCoordinator.isRecovering() &&
                            computeCanFastRecover(
                                autoReconnectEnabled = autoReconnectEnabled,
                                isManual = isManual,
                                hasInFlight = hasInFlight,
                                isFastRecovering = isFastRecovering,
                                isReconnecting = reconnectManager?.isReconnecting() == true,
                                hasConnectionConfig = currentConnectionConfig != null
                            )
                        if (canFastRecover) {
                            LogUtil.w(
                                TAG,
                                "OPT-R1: in-flight transaction + passive drop (code=$code); attempting single fast recovery"
                            )
                            tryFastRecovery(listener)
                            // 快速恢复在其协程内自行收口（成功维持连接 / 失败报 E213 回落），
                            // 这里直接返回，避免与 ReconnectManager 形成双重重连。
                            syncStatusFromKernel()
                            return
                        }

                        updateConnectionStatus(
                            ConnectionStatus.DISCONNECTED,
                            reason = reason,
                            listener = listener
                        )

                        // Try auto-reconnect
                        val reconnected = reconnectManager?.onDisconnected(listener) ?: false

                        if (!reconnected) {
                            // The primary/global listeners were notified by updateConnectionStatus.
                            // Only a terminal drop releases queued callers; an active retry owns them.
                            val pendingListeners = takePendingConnectionListeners()
                            notifyDistinctConnectionDisconnects(
                                pendingListeners,
                                reason,
                                alreadyNotified = listOfNotNull(listener, connectionListener)
                            ) { exception ->
                                LogUtil.e(TAG, "Error notifying pending disconnect listener: ${exception.message}")
                            }

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
     * OPT-R1 单次快速恢复的进入条件判定（模式无关）。
     *
     * 抽成纯函数以便单测直接断言：原先条件写死 `isLanMode`，只有 LAN 能进快恢；现放开为所有连接
     * 模式（LAN / Cable 的 USB_VSP·RS232·USB_AOA / App-to-App / Cloud）均可进，由在途交易 + 被动
     * 断开 + autoReconnect 等运行态条件共同约束，连接模式不再参与判定。
     *
     * @return 仅当「autoReconnect 开启 且 非主动断开 且 有在途交易 且 未在快恢中 且 未在常规重连中
     *         且 有可复用连接配置」全部满足时为 true。
     */
    internal fun computeCanFastRecover(
        autoReconnectEnabled: Boolean,
        isManual: Boolean,
        hasInFlight: Boolean,
        isFastRecovering: Boolean,
        isReconnecting: Boolean,
        hasConnectionConfig: Boolean
    ): Boolean {
        return autoReconnectEnabled &&
            !isManual &&
            hasInFlight &&
            !isFastRecovering &&
            !isReconnecting &&
            hasConnectionConfig
    }

    /** Final failure for one LAN recovery attempt, including callers queued during reconnect. */
    private fun notifyLanRecoveryFailure(error: ConnectionError, listener: ConnectionListener) {
        val reconnectListeners = reconnectManager?.finishFailedReconnect().orEmpty()
        val pendingListeners = synchronized(pendingConnectionListeners) {
            pendingConnectionListeners.toList().also { pendingConnectionListeners.clear() }
        }
        connectionStatus = ConnectionStatus.ERROR
        connectionErrorListener?.invoke(error)
        notifyDistinctConnectionErrors(
            listOfNotNull(listener, connectionListener) + pendingListeners + reconnectListeners,
            error
        ) { exception -> LogUtil.e(TAG, "Error notifying LAN recovery listener: ${exception.message}") }
    }

    /**
     * OPT-R1 + OPT-R2: 交易中 LAN 被动断开后的「单次原 IP 快速重连」。
     *
     * 行为约定：
     * - 用上次成功连接的配置（currentConnectionConfig，含原 IP/端口）发起**一次**快速重连，
     *   不走 mDNS 发现、总预算 [FAST_RECOVERY_BUDGET_MS]。
     * - 恢复成功：维持连接，不进入 ReconnectManager。在途交易回调由 LocalCallbackManager 的
     *   交易超时（默认 180s）继续承载，远大于本恢复窗口，故无需额外冻结倒计时。
     * - 恢复失败/超时：立即对所有在途交易以 E306 收口，并向用户上报「连接断开」(E213)。
     *   **不再把底层 1006 透传给客户**。
     *
     * 注意：本方法只负责「单次快速恢复」，不自旋、不与 ReconnectManager 并发。调用方必须在
     * 调用前确保 isFastRecovering 为 false 且 ReconnectManager 未在重连。
     */
    private fun tryFastRecovery(listener: ConnectionListener) {
        val config = currentConnectionConfig
        if (config == null) {
            LogUtil.w(TAG, "OPT-R1: no connection config for fast recovery; report disconnected")
            fastRecoveryFallback(listener)
            return
        }
        isFastRecovering = true
        sdkScope.launch {
            try {
                LogUtil.d(TAG, "OPT-R1: fast recovery started (budget=${FAST_RECOVERY_BUDGET_MS}ms)")
                // 发起一次快速重连（复用常规连接入口，原 IP 来自 config）。
                connectInternal(config, internalReconnectListener)

                // 在预算内轮询内核真实连接状态。
                val recovered = withTimeoutOrNull(FAST_RECOVERY_BUDGET_MS) {
                    while (isActive && !isConnected()) {
                        delay(FAST_RECOVERY_POLL_MS)
                    }
                    isConnected()
                } ?: false

                if (recovered) {
                    LogUtil.i(TAG, "OPT-R1: fast recovery SUCCESS; connection restored, keeping in-flight transaction")
                    // 维持连接，不触发 ReconnectManager；在途交易回调继续等待响应。
                } else {
                    LogUtil.w(TAG, "OPT-R1: fast recovery FAILED within budget; failing in-flight (E306) and reporting disconnected (E213)")
                    // 提前收口在途交易，不再干等到交易超时。
                    paymentManager?.failAllPendingTransactions(
                        InnerErrorCode.E306.code,
                        InnerErrorCode.E306.description
                    )
                    fastRecoveryFallback(listener)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "OPT-R1: fast recovery exception: ${e.message}")
                paymentManager?.failAllPendingTransactions(
                    InnerErrorCode.E306.code,
                    InnerErrorCode.E306.description
                )
                fastRecoveryFallback(listener)
            } finally {
                isFastRecovering = false
            }
        }
    }

    /**
     * 快速恢复失败后的回落：向用户上报「连接断开」(E213)、清理资源。不再尝试 ReconnectManager，
     * 避免双重重连；也不把底层 1006 透传给客户。
     */
    private fun fastRecoveryFallback(listener: ConnectionListener) {
        val disconnectReason = "Code: ${InnerErrorCode.E213.code}, Message: ${InnerErrorCode.E213.description}"
        updateConnectionStatus(
            ConnectionStatus.DISCONNECTED,
            reason = disconnectReason,
            listener = listener
        )
        try {
            listener.onDisconnected(disconnectReason)
            connectionListener?.onDisconnected(disconnectReason)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error notifying onDisconnected after fast recovery fallback: ${e.message}")
        }
        connectedDeviceId = null
        connectedTaproVersion = null
        currentConnectionMode = null
        currentAppToAppMode = null
        currentConnectionConfig = null
        syncStatusFromKernel()
    }

    /**
     * Disconnect from device
     */
    fun disconnect() {
        LogUtil.d(TAG, "Manual disconnect")

        // R9: an explicit disconnect fences any active recovery episode (no later old success/error),
        // and starts a fresh logical session so a subsequent connect is treated as a first-connect.
        establishedRecoveryCoordinator.onExplicitDetachOrDisconnect()
        hasEverEstablishedThisSession = false
        establishedTerminalIdentity = null
        currentSessionId = sessionIdCounter.incrementAndGet()

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
        // SPEC-ESTABLISHED-RECOVERY-001 R5/AC5: during recovery the logical projection is
        // "connecting", so isConnected()==false without rewriting connectionStatus or syncing from
        // the (ERROR/DISCONNECTED jittering) kernel.
        if (establishedRecoveryCoordinator.isRecovering()) {
            return false
        }
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
     * peer (Tapro) has stopped responding — e.g. a stuck cable/UART port — instead of waiting for a
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
        // SPEC-ESTABLISHED-RECOVERY-001 R5/AC5: recovery is a non-terminal "connecting" phase.
        if (establishedRecoveryCoordinator.isRecovering()) {
            return true
        }
        return connectionStatus == ConnectionStatus.CONNECTING ||
                connectionStatus == ConnectionStatus.WAIT_CONNECTING ||
                (reconnectManager?.isReconnecting() == true)
    }

    /**
     * Get connected device ID
     */
    override fun getConnectedDeviceId(): String? {
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
     * Get App-to-App transaction mode for current connection.
     */
    fun getAppToAppMode(): AppToAppMode? {
        return currentAppToAppMode
    }

    /**
     * Get Tapro version
     */
    override fun getTaproVersion(): String? {
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
        // R9: the only caller-initiated detach also fences any active recovery episode so no later
        // old success/error reaches a listener after it is explicitly removed. The coordinator never
        // removes a listener itself.
        establishedRecoveryCoordinator.onExplicitDetachOrDisconnect()
        this.connectionListener = null
    }

    // ---------------------------------------------------------------------------------------------
    // SPEC-ESTABLISHED-RECOVERY-001 — internal hooks consumed by EstablishedRecoveryCoordinator.
    // These are internal to the SDK (manager package) and never exported to the public API.
    // ---------------------------------------------------------------------------------------------

    /** @return `true` while an established-fault recovery episode is active. */
    internal fun isEstablishedRecoveryActive(): Boolean = establishedRecoveryCoordinator.isRecovering()

    /** Set the PaymentManager-visible gate so new requests are rejected before send during recovery. */
    override fun setEstablishedRecoveryActive(active: Boolean) {
        paymentManager?.setEstablishedRecoveryActive(active)
    }

    /** The original connection config to reuse for the single same-protocol rebuild (read-only). */
    override fun currentConnectionConfigForRecovery(): ConnectionConfig? = currentConnectionConfig

    /** Transfer queued callers to the recovery episode that now owns their terminal outcome. */
    override fun recoveryObservers(): List<ConnectionListener> =
        listOfNotNull(connectionListener, reconnectManager?.getLastConnectionListener()) +
            takePendingConnectionListeners()

    private fun takePendingConnectionListeners(): List<ConnectionListener> =
        synchronized(pendingConnectionListeners) {
            pendingConnectionListeners.toList().also { pendingConnectionListeners.clear() }
        }

    /** Capture the current terminal identity anchor for later same-terminal validation (R8). */
    override fun captureCurrentTerminalIdentity():
        com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity? {
        establishedTerminalIdentity?.let { return it }
        return TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.captureTerminalIdentity()
    }

    /** Read-only, non-interactive recovery prerequisite evaluation delegated to the active kernel (R14). */
    override fun evaluateRecoveryPrerequisite():
        com.sunmi.tapro.taplink.communication.interfaces.recovery.RecoveryPrereqResult {
        return TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.evaluateRecoveryPrerequisite()
            ?: com.sunmi.tapro.taplink.communication.interfaces.recovery.RecoveryPrereqResult.Unsupported
    }

    /**
     * Retire the pre-fault resources before opening a successor (R7). Drives the current kernel's
     * disconnect so stale read/write/status/close callbacks cannot pollute the rebuilt link.
     */
    override fun retireForRecovery() {
        LogUtil.d(TAG, "Recovery: retiring old transport before rebuild")
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.disconnect()
    }

    /** Open EXACTLY ONE same-protocol rebuild using the original config with an internal listener (R4). */
    override fun openRecoveryRebuild(originalConfig: ConnectionConfig?) {
        LogUtil.d(TAG, "Recovery: opening single same-protocol rebuild")
        connectInternal(originalConfig, internalReconnectListener)
    }

    /** @return `true` when the rebuilt transport reports CONNECTED at the kernel level. */
    override fun isRecoveryTransportReady(): Boolean {
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel() ?: return false
        return kernel.getConnectionStatus() == InnerConnectionStatus.CONNECTED
    }

    /** Validate the rebuilt link points at the original terminal (R8); SUB_SCREEN needs screen init. */
    override suspend fun validateRecoveredIdentity(
        expected: com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity
    ): Boolean {
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel() ?: return false
        return kernel.validateRecoveredIdentity(expected)
    }

    /**
     * Successful recovery (R10/R15): re-attach the rebuilt kernel's receive route to the SAME
     * callback/traceId mapping and restore CONNECTED projection WITHOUT replaying or swapping
     * callbacks. The traceId->callback mapping lives in LocalCallbackManager and is not cleared, so
     * only the data receiver needs re-wiring to the new kernel.
     */
    override fun onRecoverySuccess() {
        registerDataReceiver()
        setupKernelStatusListener()
        connectionStatus = ConnectionStatus.CONNECTED
        syncStatusFromKernel()
    }

    /**
     * Failed recovery (R10/Q4): on the single terminal disconnect, clear/fail in-flight transaction
     * callbacks via the existing failAllPendingTransactions path exactly once, alongside the one
     * onDisconnected(E213) the coordinator emits. No synthetic early E306.
     */
    override fun onRecoveryFailureCleanup(reason: String) {
        paymentManager?.failAllPendingTransactions(
            InnerErrorCode.E213.code,
            InnerErrorCode.E213.description
        )
        connectionStatus = ConnectionStatus.DISCONNECTED
        connectedDeviceId = null
        connectedTaproVersion = null
        currentConnectionMode = null
        currentAppToAppMode = null
        currentConnectionConfig = null
        establishedTerminalIdentity = null
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.disconnect()
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
        // SPEC-ESTABLISHED-RECOVERY-001 R5/R6: while a recovery episode is active the rebuild's own
        // status transitions must neither flip the public projection to a terminal state nor surface
        // callbacks to public observers — the coordinator owns the single terminal outcome and the
        // final projection (via onRecoverySuccess/onRecoveryFailureCleanup). Record established state
        // only; do not mutate connectionStatus or notify here.
        if (establishedRecoveryCoordinator.isRecovering()) {
            if (newStatus == ConnectionStatus.CONNECTED) {
                hasEverEstablishedThisSession = true
            }
            return
        }

        if (connectionStatus != newStatus) {
            val oldStatus = connectionStatus
            connectionStatus = newStatus

            LogUtil.d(TAG, "Connection status changed: $oldStatus -> $newStatus")
        }

        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> {
                // R3: this logical session has now been established; established faults become
                // eligible for the one-shot recovery. R8: capture the terminal identity anchor.
                hasEverEstablishedThisSession = true
                establishedTerminalIdentity =
                    TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.captureTerminalIdentity()

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

                // Persist the LAN-bound Tapro serial (if this connection is LAN) so that a later
                // LAN reconnect can rediscover the SAME device via mDNS (same-session IP-follow)
                // even after the LAN kernel is torn down.
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
                notifyDistinctConnectionDisconnects(
                    listOfNotNull(listener, connectionListener), disconnectionReason
                ) { exception ->
                    LogUtil.e(TAG, "Error notifying disconnect listener: ${exception.message}")
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
     * @return List of listeners that were notified (for deduplication)
     */
    private fun notifyPendingConnectionListenersSuccess(deviceId: String, taproVersion: String): List<ConnectionListener> {
        if (pendingConnectionListeners.isEmpty()) {
            return emptyList()
        }

        LogUtil.d(TAG, "Notifying ${pendingConnectionListeners.size} pending connection listeners of connection success")
        val listeners = synchronized(pendingConnectionListeners) {
            pendingConnectionListeners.toList().also { pendingConnectionListeners.clear() }
        }

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
        val listeners = synchronized(pendingConnectionListeners) {
            pendingConnectionListeners.toList().also { pendingConnectionListeners.clear() }
        }

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

        // 1. Enhanced device identity verification (active connection only)
        val deviceMatchResult = analyzeDeviceIdentity(
            newServiceName = newServiceName,
            currentDeviceId = connectedDeviceId
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
        currentDeviceId: String?
    ): DeviceMatchResult {
        // Get the most reliable device ID (active connection only)
        val targetDeviceId = getReliableDeviceId(currentDeviceId)

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
     * Priority: live connected id > LAN bound serial of the ACTIVE connection.
     *
     * Only identities from the CURRENT (still-live) connection are used:
     * - [currentDeviceId] is the in-memory device id of the active connection (cleared on
     *   disconnect), and
     * - [resolveBoundLanSerial] is the serial the live LAN kernel is bound to, parsed from the mDNS
     *   service name.
     *
     * This intentionally does NOT fall back to any cross-session persisted device id / serial.
     * Persisted identities belong to a PREVIOUS connection (possibly a different terminal), so using
     * them misattributes a fresh connection failure to the old device — e.g. after switching from
     * one device to another over VSP/Cable, the failure of the new attempt would still report the
     * first device's id. Same-session LAN IP-follow is unaffected: it relies on the live
     * [currentDeviceId] / bound serial above, both of which remain available for the active link.
     */
    private fun getReliableDeviceId(
        currentDeviceId: String?
    ): String? {
        return when {
            !currentDeviceId.isNullOrEmpty() && currentDeviceId != "unknown" -> {
                LogUtil.d(TAG, "Using current connected device ID: $currentDeviceId")
                currentDeviceId
            }

            else -> {
                val lanSerial = resolveBoundLanSerial()
                if (!lanSerial.isNullOrEmpty()) {
                    LogUtil.d(TAG, "Using LAN bound serial as device ID: $lanSerial")
                    lanSerial
                } else {
                    LogUtil.w(TAG, "No reliable device ID available")
                    null
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
            // Only a genuinely established, live link (kernel CONNECTED, i.e. WebSocket actually
            // connected) may short-circuit as "already connected, no reconnection needed". Any
            // transitional state (CONNECTING / WAIT_CONNECTING) is NOT proof of a live link: after a
            // peer-side network drop the kernel can retain a stale CONNECTING while the old socket is
            // already dead. In that case the discovered same-address target is a valid recovery
            // handoff and we MUST reconnect. Treating stale CONNECTING as "connected" is exactly the
            // misjudgement that let a drop-and-reconnect be mislabelled as a fatal recovery failure.
            return if (actualStatus == ConnectionStatus.CONNECTED) {
                ReconnectionDecision(
                    shouldReconnect = false,
                    reason = "Same address and link is genuinely established (status: $actualStatus), no reconnection needed"
                )
            } else {
                ReconnectionDecision(
                    shouldReconnect = true,
                    reason = "Same address but link not established (status: $actualStatus), reconnection needed"
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

        // Keep this second guard aligned with analyzeAddressChange: only a genuinely established link
        // (kernel CONNECTED) to the same address may short-circuit as "no reconnection needed" to
        // avoid thrashing a live connection. A transitional CONNECTING / WAIT_CONNECTING is a stale
        // residue, not a live link, so it must fall through to reconnect — otherwise a drop-and-
        // reconnect to the same address would be wrongly suppressed here.
        if (actualStatus == ConnectionStatus.CONNECTED) {
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
        if (reconnectManager?.getLastConnectionConfig()?.autoReconnect == false) {
            return
        }
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
        if (reconnectManager?.getLastConnectionConfig()?.autoReconnect == false) {
            LogUtil.d(TAG, "Ignoring LAN address change because autoReconnect is disabled")
            return false
        }
        return try {
            val newConnectionConfig = reconnectManager?.getLastConnectionConfig()
                ?.copyWithLanEndpoint(newHost, newPort)
                ?: ConnectionConfig()
                    .setConnectionMode(ConnectionMode.LAN)
                    .setHost(newHost)
                    .setPort(newPort)

            // A handshake handoff continues the original connect call. Keep its listener
            // as the primary callback; the persistent observer is notified separately.
            val reconnectListener = if (connectionStatus == ConnectionStatus.CONNECTING ||
                connectionStatus == ConnectionStatus.WAIT_CONNECTING
            ) {
                reconnectManager?.getLastConnectionListener() ?: connectionListener ?: internalReconnectListener
            } else {
                connectionListener ?: reconnectManager?.getLastConnectionListener() ?: internalReconnectListener
            }
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
    private fun setupKernelStatusListener(deferLanHandshakeFailure: Boolean = false) {
        val kernel = TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()
        if (kernel == null) {
            LogUtil.d(TAG, "Cannot setup kernel status listener: kernel not available yet")
            return
        }

        kernel.setStatusChangeListener { kernelStatus ->
            if (TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel() !== kernel) {
                return@setStatusChangeListener
            }
            val newStatus = convertToConnectionStatus(kernelStatus)

            // SPEC-ESTABLISHED-RECOVERY-001 R5/R6: an established link dropping to DISCONNECTED/ERROR
            // via the kernel status listener is an established fault — claim the recovery epoch
            // synchronously BEFORE any field mutation or public notification. On an established fault
            // the claim returns true; the coordinator then owns the single terminal outcome and this
            // listener invocation must do nothing further (status projection stays non-terminal).
            val kernelDroppedToInactive = newStatus == ConnectionStatus.DISCONNECTED ||
                newStatus == ConnectionStatus.ERROR
            val sdkWasActive = connectionStatus == ConnectionStatus.CONNECTED ||
                connectionStatus == ConnectionStatus.CONNECTING ||
                connectionStatus == ConnectionStatus.WAIT_CONNECTING
            if (kernelDroppedToInactive && sdkWasActive &&
                !establishedRecoveryCoordinator.isRecovering() &&
                reconnectManager?.isReconnecting() != true &&
                !isFastRecovering
            ) {
                if (establishedRecoveryCoordinator.interceptIfEstablishedFault(
                        TransportFaultEvent(
                            sessionId = currentSessionId,
                            epoch = establishedRecoveryCoordinator.currentEpoch(),
                            code = "1006",
                            msg = "Kernel status changed to $kernelStatus",
                            established = hasEverEstablishedThisSession
                        )
                    )
                ) {
                    return@setStatusChangeListener
                }
            }

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
                // SPEC-ESTABLISHED-RECOVERY-001 R5/AC5: during recovery keep the logical projection
                // non-terminal (isConnecting=true/isConnected=false). The self-healing guard below
                // only controls whether we SURFACE onDisconnected; it does not protect the status
                // FIELD. So we must not overwrite connectionStatus to DISCONNECTED/ERROR mid-recovery
                // — kernel ERROR->DISCONNECTED jitter must not flip the projection.
                if (!establishedRecoveryCoordinator.isRecovering()) {
                    synchronized(this) {
                        connectionStatus = newStatus
                    }
                }

                val droppedToInactive = newStatus == ConnectionStatus.DISCONNECTED ||
                        newStatus == ConnectionStatus.ERROR
                val wasActive = previousStatus == ConnectionStatus.CONNECTED ||
                        previousStatus == ConnectionStatus.CONNECTING ||
                        previousStatus == ConnectionStatus.WAIT_CONNECTING
                // Suppress during self-healing reconnect so a transient VSP/sub-screen kernel
                // bounce (which surfaces as RECONNECTING -> CONNECTING, not DISCONNECTED) or an
                // in-progress reconnect never raises a spurious onDisconnected to the integrator.
                // OPT-R1: the single in-transaction fast-recovery window (isFastRecovering) is
                // also self-healing — its mid-recovery kernel DISCONNECTED is transient and must
                // not surface a false onDisconnected nor clear the processed-terminal-event set.
                // SPEC-ESTABLISHED-RECOVERY-001 R5/R6: an active established-recovery episode is also
                // self-healing — its mid-recovery kernel DISCONNECTED/ERROR must not surface a false
                // onDisconnected nor invoke connectionDisconnectedListener (which would clear the
                // processed-terminal-event dedup set).
                val isSelfHealing = reconnectManager?.isReconnecting() == true ||
                    isFastRecovering ||
                    establishedRecoveryCoordinator.isRecovering()

                // A recovering LAN handshake has one terminal owner: ConnectionCallback.
                // Kernel ERROR is also set by the final failure path; do not synthesize a
                // second onDisconnected before that path delivers onError.
                val handshakeFailureOwnedByCallback = deferLanHandshakeFailure &&
                    previousStatus in listOf(ConnectionStatus.CONNECTING, ConnectionStatus.WAIT_CONNECTING)
                if (droppedToInactive && wasActive && !isSelfHealing && !handshakeFailureOwnedByCallback) {
                    val reason = "Kernel status changed to $kernelStatus"
                    LogUtil.w(
                        TAG,
                        "Kernel dropped to $newStatus while SDK was $previousStatus and not reconnecting; " +
                            "notifying integrator onDisconnected to keep states in sync"
                    )

                    // The listener passed to connect() is the primary lifecycle callback for most
                    // integrations. setConnectionListener() is optional, so notifying only the
                    // global listener leaves callers stuck in CONNECTED after a transport drop.
                    connectionDisconnectedListener?.invoke()
                    val listenersToNotify = linkedSetOf<ConnectionListener>()
                    reconnectManager?.getLastConnectionListener()?.let(listenersToNotify::add)
                    connectionListener?.let(listenersToNotify::add)
                    listenersToNotify.forEach { listener ->
                        try {
                            listener.onDisconnected(reason)
                        } catch (e: Exception) {
                            LogUtil.e(
                                TAG,
                                "Error notifying onDisconnected from kernel status sync: ${e.message}"
                            )
                        }
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

        // Keys used by Tapro in extraInfoMap (returned via getExtraInfos in APP_TO_APP mode)
        private const val EXTRA_KEY_TAPRO_VERSION = "taproVersion"
        private const val EXTRA_KEY_DEVICE_ID = "deviceId"

        // OPT-R1: 交易中单次快速恢复的总预算与轮询间隔（毫秒）。
        // 预算取 5s（含握手），远小于交易回调超时（默认 180s），确保恢复窗口不与交易超时冲突。
        private const val FAST_RECOVERY_BUDGET_MS = 5_000L
        private const val FAST_RECOVERY_POLL_MS = 200L
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

        if (connectionStatus == ConnectionStatus.CONNECTING ||
            connectionStatus == ConnectionStatus.WAIT_CONNECTING ||
            connectionStatus == ConnectionStatus.CONNECTED
        ) {
            LogUtil.w(TAG, "discoverLanServices rejected: status=$connectionStatus")
            listener.onError(
                ConnectionError(ERROR_OPERATION_IN_PROGRESS, "Service discovery requires a disconnected state")
            )
            return
        }

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
                        // Explicit auto-discovery keeps its historical discovery/recovery behavior
                        // independent of the caller's persisted ConnectionConfig policy.
                        val config = ConnectionConfig.createLanMode(service.host, service.port)
                            .setAutoReconnect(true)
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
