package com.sunmi.tapro.taplink.communication.lan

import android.content.Context
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.lan.connection.ConnectionManager
import com.sunmi.tapro.taplink.communication.lan.discovery.ServiceDiscoveryManager
import com.sunmi.tapro.taplink.communication.lan.model.ServiceInfo
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/**
 * LAN client service kernel class
 *
 * Refactored architecture:
 * - Correctly inherits AsyncServiceKernel, fully utilizes base functionality
 * - Uses manager pattern to decompose complex responsibilities
 * - Focuses on LAN network communication (mainly WebSocket)
 * - Fixes race condition issues, ensures operation mutex and callback correctness
 *
 * Manager components:
 * - ConnectionManager: WebSocket connection management
 * - ServiceDiscoveryManager: mDNS service discovery
 * - WebSocket callbacks: connection lifecycle management
 *
 * @param appId Application ID
 * @param appSecretKey Application secret key
 * @param context Android context
 *
 * @author TaPro Team
 * @since 2025-01-01
 */
class LanClientKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    companion object {
        private const val TAG = "LanClientKernel"
        private const val NSD_SERVICE_TYPE = "_taplink._tcp"

        // Service discovery retry configuration
        private const val SERVICE_DISCOVERY_MAX_RETRIES = 3  // Maximum retry count
        private const val SERVICE_DISCOVERY_RETRY_DELAY_MS = 1000L  // Initial retry delay (milliseconds)
        private const val SERVICE_DISCOVERY_RETRY_DELAY_MULTIPLIER = 2  // Retry delay multiplier (incremental delay)

        /**
         * Decide whether a newly advertised service represents a restart of the peer we are
         * already connected to.
         *
         * Tapro embeds a fresh timestamp in its mDNS instance name on every registration, so a
         * restart appears as a *different* instance name advertising the *same* endpoint. A
         * changed endpoint is an ordinary address change and is handled elsewhere.
         */
        internal fun isPeerRestart(
            previousName: String,
            previousHost: String,
            previousPort: Int,
            newName: String,
            newHost: String,
            newPort: Int
        ): Boolean {
            if (previousName == newName) return false
            // Same endpoint (host:port) is a necessary condition for a peer restart.
            if (previousHost != newHost || previousPort != newPort) return false
            // The mDNS instance name is "Taplink-Server-<timestamp>-<serial>". TaPro re-registers
            // its service with a FRESH timestamp periodically (and across network changes) WITHOUT
            // restarting — same device, same serial, only the timestamp differs. Treating that as a
            // peer restart would needlessly tear down a perfectly healthy socket (observed as a
            // ~5s "normal closure" loop). Only a genuinely DIFFERENT serial at the same endpoint
            // (i.e. a different device now answering there) counts as a peer restart.
            val previousSerial = parseServiceSerial(previousName)
            val newSerial = parseServiceSerial(newName)
            if (previousSerial != null && newSerial != null) {
                return previousSerial != newSerial
            }
            // Fall back to the old name-based heuristic only when a serial cannot be parsed.
            return true
        }

        /**
         * Parse the Tapro serial from a LAN mDNS service name.
         *
         * The service name is "Taplink-Server-<timestamp>-<serial>"; the serial is the trailing
         * '-'-delimited segment. Returns null for names that don't match the expected prefix or
         * carry no serial segment.
         *
         * Exposed so both the instance-level discovery binding and the SDK layer's device-identity
         * match rely on one canonical parse and cannot drift apart. Kept public (not internal)
         * because the SDK module (lib_taplink_sdk) is a separate Gradle module.
         */
        fun parseServiceSerial(serviceName: String): String? {
            if (!serviceName.startsWith("Taplink-Server-", ignoreCase = true)) return null
            return serviceName.substringAfterLast('-').takeIf { it.isNotEmpty() }
        }
    }

    override fun getTag(): String = TAG

    // ==================== Concurrency Control ====================

    /**
     * Operation mutex lock, ensures only one connection operation at a time
     */
    private val operationMutex = Mutex()

    /**
     * Status update mutex lock, protects status update operations
     */
    private val statusMutex = Mutex()

    /**
     * Current operation type being executed
     */
    private var currentOperation: String? = null

    /**
     * Operation identifier generator
     */
    private val operationIdGenerator = AtomicLong(0)

    /**
     * Current operation ID
     */
    private var currentOperationId: String? = null

    /**
     * Service address change listener (independent of connection callback, for persistent monitoring)
     */
    private var serviceAddressChangeListener: ServiceAddressChangeListener? = null

    /** Stable serial suffix of the Tapro mDNS service selected for this connection. */
    private var targetServiceSerial: String? = null

    /**
     * Service address change listener interface
     */
    interface ServiceAddressChangeListener {
        /**
         * Called when service address change is detected
         * @param serviceName Service name
         * @param newHost New host address
         * @param newPort New port number
         * @param oldHost Old host address
         * @param oldPort Old port number
         * @return Boolean - true: need to reconnect, false: no need to reconnect
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
     * Set service address change listener
     *
     * @param listener Listener, clears listener when null
     */
    fun setServiceAddressChangeListener(listener: ServiceAddressChangeListener?) {
        synchronized(this) {
            serviceAddressChangeListener = listener
            LogUtil.d(TAG, "Service address change listener ${if (listener != null) "set" else "cleared"}")
        }
    }

    fun getServiceAddressChangeListener(): ServiceAddressChangeListener? = serviceAddressChangeListener

    /**
     * The Tapro serial this LAN discovery is currently bound to, parsed from the mDNS service name
     * (e.g. "Taplink-Server-<ts>-<serial>" -> "<serial>"), or null if not yet bound.
     *
     * In LAN mode the serial is the only reliable device identity available at the transport layer
     * (the AIDL device-id / INIT-response device-id may be absent or already cleared on a passive
     * disconnect). The SDK layer uses it to attribute disconnect errors to the correct device and
     * to make reconnection decisions by exact device identity instead of a conservative fallback.
     */
    fun getTargetServiceSerial(): String? = targetServiceSerial

    private fun serviceSerial(serviceName: String): String? = parseServiceSerial(serviceName)

    private fun isTargetService(service: ServiceInfo): Boolean {
        val serial = serviceSerial(service.name)
        val target = targetServiceSerial

        // The endpoint our live socket is actually connected to is authoritative: a service that
        // advertises that exact host:port defines our bound identity, even if a stale serial is
        // still remembered. This is what lets a manual reconnect to a *different* device (new
        // ip/port that advertises a new serial) rebind discovery to the new serial, while never
        // allowing an unrelated device at some other address to preempt an established binding.
        if (isConnectedEndpoint(service)) return true

        if (target != null) return serial == target

        // For a direct first connection, bind identity to the service advertising
        // the endpoint that actually connected instead of selecting another device.
        val uri = currentUri
        return uri != null && service.host == uri.host && service.port == uri.port
    }

    /**
     * Whether [service] describes the exact endpoint our live WebSocket is currently connected to.
     *
     * Only the endpoint we truly connected to may (re)bind the discovery identity, so a manual
     * switch to another Tapro adopts the new serial while other devices on the subnet can never
     * hijack the connection.
     */
    private fun isConnectedEndpoint(service: ServiceInfo): Boolean {
        val uri = currentUri ?: return false
        return isWebSocketConnected() && service.host == uri.host && service.port == uri.port
    }

    private fun rememberTargetService(service: ServiceInfo) {
        serviceSerial(service.name)?.let {
            targetServiceSerial = it
            LogUtil.i(TAG, "Bound LAN discovery to Tapro serial: $it")
        }
    }

    // ==================== Manager Components ====================

    /**
     * WebSocket connection manager
     */
    private val connectionManager = ConnectionManager()

    /**
     * Service discovery manager
     */
    private val serviceDiscoveryManager = ServiceDiscoveryManager(context)

    // ==================== State Management ====================

    /**
     * Currently connected URI
     */
    private var currentUri: URI? = null

    /**
     * Currently connected service information
     */
    private var currentService: ServiceInfo? = null

    /**
     * Whether service monitoring is in progress
     */
    private var isServiceMonitoring = false

    /**
     * Whether it's a manual disconnect
     */
    private var isManualDisconnect = false

    /** Distinguishes a failed handshake from a drop of an established LAN link. */
    @Volatile
    private var hasEstablishedConnection = false

    /** Suppresses the close callback that follows a failed connection attempt. */
    @Volatile
    private var suppressNextDisconnect = false

    /**
     * Timestamp of last service address change trigger (for debouncing)
     */
    private var lastServiceAddressChangeTime: Long = 0

    /**
     * Debounce time interval (milliseconds), avoid repeated reconnection triggers in short time
     */
    private val SERVICE_ADDRESS_CHANGE_DEBOUNCE_MS = 2000L

    init {
        setupConnectionManager()
    }

    // ==================== Safe Operation Executor ====================

    /**
     * Safely execute connection operation, ensuring operation mutex
     */
    private suspend fun executeConnectionOperation(
        operationType: String,
        callback: ConnectionCallback,
        operation: suspend (String) -> Unit
    ) {
        // Check if another operation is in progress
        // Note: Even though TaplinkServiceKernel ensures disconnect() before connect(),
        // mutex protection is still needed here because:
        // 1. There may be concurrent calls from other threads
        // 2. There may be internal reconnection operations from service discovery in progress
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
            // Set current operation information
            currentOperation = operationType
            currentOperationId = operationId

            // Note: Callback is already managed by AsyncServiceKernel's currentConnectionCallback
            // No need for additional callback manager, because connection operations are mutually exclusive (protected by operationMutex)

            LogUtil.d(TAG, "Starting $operationType operation: $operationId")

            // Execute operation
            operation(operationId)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error in $operationType operation: ${e.message}")

            // Notify error: use base class's notifyConnectionError()
            notifyConnectionError("Operation failed: ${e.message}", InnerErrorCode.E241)

        } finally {
            // Note: Do not clear currentOperationId here, because connection success callback may be triggered asynchronously after finally
            // currentOperationId will be cleared in connection success/failure callback
            currentOperation = null
            // currentOperationId is retained until cleared in connection success/failure callback

            // Release mutex lock
            operationMutex.unlock()

            LogUtil.d(TAG, "Completed $operationType operation: $operationId")
        }
    }

    /**
     * Generate operation ID
     */
    private fun generateOperationId(): String {
        return "${System.currentTimeMillis()}_${operationIdGenerator.incrementAndGet()}"
    }

    /**
     * Check if operation is still valid
     */
    private fun isOperationValid(operationId: String): Boolean {
        return currentOperationId == operationId
    }

    /**
     * Safely update status
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
     * Check if WebSocket connection is established
     *
     * Prefer using ConnectionManager's actual status over currentInnerConnectionStatus
     * because ConnectionManager's status is more accurate (checks WebSocket's actual status)
     *
     * @return true if WebSocket connection is established, false otherwise
     */
    private fun isWebSocketConnected(): Boolean {
        return connectionManager.isConnected()
    }

    /**
     * Check if should skip service processing (already connected or connecting to same address)
     *
     * @param service Service to check
     * @return true if should skip, false otherwise
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

    // ==================== AsyncServiceKernel Abstract Method Implementation ====================

    override fun getServiceType(): String = "LAN"

    override fun getExpectedProtocolType(): String = "lan protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.LanProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        // Cancel old connection task to avoid concurrent connections
        connectJob?.cancel()
        connectJob = launchInScope {
            executeConnectionOperation("CONNECT", connectionCallback) { operationId ->
                try {
                    val lanResult = parseResult as ProtocolParseResult.LanProtocol
                    LogUtil.d(TAG, "Performing LAN connection to: ${lanResult.ip}:${lanResult.port} ($operationId)")

                    // Note: Do not check isOperationValid here, because mutex already protects operation mutex
                    // Normally there won't be multiple operations simultaneously

                    // Build WebSocket URI, use secure field from protocol parse result
                    val uri = try {
                        val scheme = if (lanResult.secure) "wss" else "ws"
                        URI("$scheme://${lanResult.ip}:${lanResult.port}")
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Invalid URI: ${lanResult.ip}:${lanResult.port}", e)
                    }

                    currentUri = uri

                    // Update status to connecting
                    updateStatusSafely(InnerConnectionStatus.CONNECTING)

                    // Note: Do not check isOperationValid here, because mutex already protects operation mutex

                    // Try direct connection
                    val result = connectionManager.connect(uri)
                    when (result) {
                        is ConnectionManager.ConnectionResult.Success -> {
                            LogUtil.d(TAG, "Direct connection successful ($operationId)")

                            // Note: Do not check isOperationValid here because:
                            // 1. onOpen callback is asynchronous, may execute before connect() returns
                            // 2. onConnected() callback will clear currentOperationId
                            // 3. If checked here, may misjudge operation as cancelled, causing connection to be disconnected
                            // 4. Connection success handling will be triggered through ConnectionManager's listener
                            // 5. If operation is really cancelled, onConnected() callback will handle disconnect logic

                            // Connection success handling will be triggered through ConnectionManager's listener
                            // Listener will call notifyConnectionSuccess() as unified connection success notification outlet
                        }

                        is ConnectionManager.ConnectionResult.Failure -> {
                            LogUtil.w(TAG, "Direct connection failed: ${result.error} ($operationId)")

                            // Note: Don't check isOperationValid here, because mutex already protects operation mutual exclusivity
                            // Service discovery is a long operation, will check isOperationValid in service discovery logic

                            // Try service discovery
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
     * Try service discovery for specific operation (with retry mechanism)
     */
    private suspend fun tryServiceDiscoveryForOperation(operationId: String) {
        var lastException: Exception? = null
        var retryCount = 0

        // Retry loop
        while (retryCount <= SERVICE_DISCOVERY_MAX_RETRIES) {
            try {
                // Check if operation is still valid
                if (!isOperationValid(operationId)) {
                    LogUtil.w(TAG, "Service discovery cancelled: $operationId")
                    return
                }

                if (retryCount > 0) {
                    // Calculate incremental delay: 1 second, 2 seconds, 4 seconds...
                    var delayMs = SERVICE_DISCOVERY_RETRY_DELAY_MS
                    repeat(retryCount - 1) {
                        delayMs *= SERVICE_DISCOVERY_RETRY_DELAY_MULTIPLIER
                    }
                    LogUtil.d(
                        TAG,
                        "Retrying service discovery (attempt ${retryCount + 1}/${SERVICE_DISCOVERY_MAX_RETRIES + 1}) after ${delayMs}ms delay ($operationId)"
                    )
                    delay(delayMs)
                    // Check again if operation is still valid after delay (mode may have switched during delay)
                    if (!isOperationValid(operationId)) {
                        LogUtil.w(TAG, "Service discovery cancelled after delay: $operationId")
                        return
                    }
                } else {
                    LogUtil.d(TAG, "Starting service discovery for operation: $operationId")
                }

                // Try service discovery
                val services = serviceDiscoveryManager.discoverServices(NSD_SERVICE_TYPE)

                if (services.isNotEmpty()) {
                    LogUtil.d(TAG, "Found ${services.size} services for operation: $operationId (attempt ${retryCount + 1})")

                    // Notify upper layer of discovered services, let upper layer decide if reconnection is needed
                    // Find first valid service and notify upper layer (using persistent listener)
                    var notified = false
                    for (service in services) {
                        if (service.isValid() && isTargetService(service)) {
                            rememberTargetService(service)
                            currentService = service
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
                                // Upper layer will handle reconnection, return directly here
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
                        // Continue retrying service discovery
                        if (retryCount < SERVICE_DISCOVERY_MAX_RETRIES) {
                            lastException = Exception("No valid services found (attempt ${retryCount + 1})")
                            retryCount++
                            continue
                        } else {
                            throw Exception("No valid services found after ${SERVICE_DISCOVERY_MAX_RETRIES + 1} attempts")
                        }
                    } else {
                        // Upper layer notified, but decided not to reconnect, service discovery completed
                        LogUtil.d(TAG, "Service discovery completed, upper layer will handle connection ($operationId)")
                        return
                    }
                } else {
                    // No services discovered
                    LogUtil.w(
                        TAG,
                        "No services found via service discovery (attempt ${retryCount + 1}/${SERVICE_DISCOVERY_MAX_RETRIES + 1}) ($operationId)"
                    )

                    // Check current connection status
                    // Use ConnectionManager's actual status, not currentInnerConnectionStatus
                    val isCurrentlyConnected = isWebSocketConnected()
                    val isCurrentlyConnecting = currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING

                    if (isCurrentlyConnected || isCurrentlyConnecting) {
                        LogUtil.d(TAG, "Service discovery found no services, but already connected/connecting ($operationId)")
                        return
                    }

                    // If there are still retry opportunities, continue retrying
                    if (retryCount < SERVICE_DISCOVERY_MAX_RETRIES) {
                        lastException = Exception("No services found (attempt ${retryCount + 1})")
                        retryCount++
                        continue
                    } else {
                        // All retries failed
                        throw Exception("No available services found after ${SERVICE_DISCOVERY_MAX_RETRIES + 1} attempts")
                    }
                }

            } catch (e: Exception) {
                lastException = e
                LogUtil.w(TAG, "Service discovery attempt ${retryCount + 1} failed: ${e.message} ($operationId)")

                // Check current connection status
                // Use ConnectionManager's actual status, not currentInnerConnectionStatus
                val isCurrentlyConnected = isWebSocketConnected()
                val isCurrentlyConnecting = currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING

                if (isCurrentlyConnected || isCurrentlyConnecting) {
                    LogUtil.d(TAG, "Service discovery failed, but already connected/connecting ($operationId)")
                    return
                }

                // Check if should retry
                if (retryCount < SERVICE_DISCOVERY_MAX_RETRIES) {
                    // Distinguish different types of errors
                    val isRetryableError = isRetryableServiceDiscoveryError(e)
                    if (isRetryableError) {
                        retryCount++
                        LogUtil.d(TAG, "Retryable error detected, will retry service discovery ($operationId)")
                        continue
                    } else {
                        // Non-retryable errors (such as operation cancelled), throw directly
                        LogUtil.w(TAG, "Non-retryable error detected, aborting service discovery ($operationId)")
                        throw e
                    }
                } else {
                    // All retries failed
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

        // If all retries failed, throw last exception
        throw lastException ?: Exception("Service discovery failed after ${SERVICE_DISCOVERY_MAX_RETRIES + 1} attempts")
    }

    /**
     * Determine if service discovery error is retryable
     *
     * @param e Exception
     * @return true if error is retryable, false otherwise
     */
    private fun isRetryableServiceDiscoveryError(e: Exception): Boolean {
        val errorMessage = e.message?.lowercase() ?: ""

        // Retryable error types
        val retryableErrors = listOf(
            "timeout",
            "network",
            "connection",
            "no available services",
            "discovery failed",
            "nsd",
            "service discovery"
        )

        // Non-retryable error types (such as operation cancelled)
        val nonRetryableErrors = listOf(
            "cancelled",
            "operation cancelled",
            "invalid"
        )

        // Check if it's a non-retryable error
        if (nonRetryableErrors.any { errorMessage.contains(it) }) {
            return false
        }

        // Check if it's a retryable error
        return retryableErrors.any { errorMessage.contains(it) }
    }

    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
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

            // Mark as manual disconnect
            isManualDisconnect = true

            // Stop service discovery (stop when manual disconnect)
            stopServiceDiscovery()

            // Disconnect WebSocket connection
            connectionManager.disconnect()

            // Clean up resources
            cleanupResources()

            LogUtil.d(TAG, "LAN disconnect completed")

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during disconnect: ${e.message}")
        }
    }

    // ==================== Manager Setup and Integration ====================

    /**
     * Setup WebSocket connection manager (updated version)
     */
    private fun setupConnectionManager() {
        // Set connection status listener
        connectionManager.setConnectionListener(object : ConnectionManager.ConnectionListener {
            override fun onConnected() {
                hasEstablishedConnection = true
                // Update status SYNCHRONOUSLY first so canSendData() is true
                // before any external callback fires. Notification
                // happen asynchronously afterwards.
                updateStatus(InnerConnectionStatus.CONNECTED)

                launchInScope {
                    LogUtil.d(TAG, "LAN connection established")

                    // If the endpoint we just connected to is a *different* address than the one we
                    // previously bound our discovery identity to, the user reconnected to another
                    // Tapro (manual ip/port change). Forget the stale serial so the connected
                    // endpoint rebinds discovery to whatever serial it advertises. For an ordinary
                    // reconnect/ip-follow to the same device the endpoint re-advertises the same
                    // serial, so this rebind is a harmless no-op; an unrelated device can never
                    // rebind because only the address we actually connected to is honored.
                    val connectedHost = currentUri?.host
                    val connectedPort = currentUri?.port ?: -1
                    val bound = currentService
                    if (bound != null && connectedHost != null &&
                        (bound.host != connectedHost || bound.port != connectedPort)
                    ) {
                        LogUtil.i(
                            TAG,
                            "Connected endpoint changed (${bound.host}:${bound.port} -> $connectedHost:$connectedPort); " +
                                    "forgetting stale serial ($targetServiceSerial) so discovery rebinds to the connected device"
                        )
                        targetServiceSerial = null
                        currentService = null
                    }

                    // Create service information (if needed)
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

                    // Start service monitoring
                    startServiceMonitoring()

                    // Unified connection success notification outlet: only use base class's notifyConnectionSuccess()
                    // Directly use base class's currentConnectionCallback, no need for additional callback manager
                    val connectionData = mapOf("uri" to currentUri?.toString())
                    val operationIdToClear = currentOperationId

                    // Clear current operation ID
                    if (operationIdToClear != null) {
                        currentOperationId = null
                    }

                    // Unified notification outlet: only use base class's notifyConnectionSuccess()
                    notifyConnectionSuccess(connectionData)
                }
            }

            override fun onDisconnected(code: Int, reason: String, remote: Boolean) {
                launchInScope {
                    LogUtil.d(
                        TAG,
                        "LAN connection closed: code=$code, reason=$reason, remote=$remote, isManual=$isManualDisconnect"
                    )

                    // Build disconnect information. Preserve the real underlying WebSocket close
                    // code so ConnectionManager can correctly distinguish manual disconnect /
                    // normal close / abnormal network close (e.g. 1006) instead of always
                    // reporting a generic E213, which previously masked the real disconnect
                    // reason and broke auto-reconnect classification.
                    val disconnectCode = if (isManualDisconnect) {
                        "MANUAL_DISCONNECT"
                    } else {
                        code.toString()
                    }
                    val disconnectMessage = if (isManualDisconnect) {
                        "${InnerErrorCode.E213.description}:Manual disconnect, $reason($code)"
                    } else {
                        "${InnerErrorCode.E213.description}:Passive disconnect, $reason($code)"
                    }

                    val wasManual = isManualDisconnect
                    isManualDisconnect = false

                    if (suppressNextDisconnect) {
                        suppressNextDisconnect = false
                        hasEstablishedConnection = false
                        updateStatusSafely(InnerConnectionStatus.DISCONNECTED)
                        LogUtil.d(TAG, "Suppressing disconnect callback for failed LAN handshake")
                        return@launchInScope
                    }
                    hasEstablishedConnection = false

                    // Determine if it's a connection error
                    val isError = code == 1006 || code >= 1002

                    // Safely update status
                    if (isError) {
                        updateStatusSafely(InnerConnectionStatus.ERROR)
                    } else {
                        updateStatusSafely(InnerConnectionStatus.DISCONNECTED)
                    }

                    // Notify connection disconnected: use base class's notifyConnectionDisconnected()
                    notifyConnectionDisconnected(disconnectCode, disconnectMessage)

                    // If not manual disconnect, and currently has connection callback, start service monitoring
                    // Use base class's currentConnectionCallback to determine if there's a pending connection
                    if (!wasManual && currentConnectionCallback != null) {
                        LogUtil.d(TAG, "Passive disconnect detected, starting service monitoring")
                        if (!isServiceMonitoring) {
                            startServiceMonitoring()
                        }
                    }
                }
            }

            override fun onError(exception: Exception) {
                val isHandshakeFailure = !hasEstablishedConnection && !isManualDisconnect
                if (isHandshakeFailure) {
                    suppressNextDisconnect = true
                }
                launchInScope {
                    LogUtil.e(TAG, "LAN connection error: ${exception.message}")

                    if (isHandshakeFailure) {
                        // A failed direct endpoint must be visible to the SDK caller even when
                        // service discovery fallback is still allowed by performConnect().
                        // The next close callback is suppressed separately to avoid delivering
                        // a duplicate disconnect notification for this same failed handshake.
                        updateStatusSafely(InnerConnectionStatus.ERROR)
                        notifyConnectionError(
                            "Connection error: ${exception.message}",
                            InnerErrorCode.E241
                        )
                        LogUtil.d(TAG, "LAN handshake failed; notifying caller and allowing service discovery fallback")
                        return@launchInScope
                    }

                    // Safely update status
                    updateStatusSafely(InnerConnectionStatus.ERROR)

                    // Notify connection error: use base class's notifyConnectionError()
                    notifyConnectionError("Connection error: ${exception.message}", InnerErrorCode.E241)
                }
            }
        })

        // Set message listener
        connectionManager.setMessageListener(object : ConnectionManager.MessageListener {
            override fun onTextMessage(message: String) {
                LogUtil.d(TAG, "Text message received: $message")

                // Pass to data receiver (provided by BaseServiceKernel)
                dataReceiver?.invoke(message.toByteArray(Charsets.UTF_8))
            }

            override fun onBinaryMessage(data: ByteArray) {
                LogUtil.d(TAG, "Binary message received: ${data.size} bytes:$dataReceiver")

                // Pass to data receiver (provided by BaseServiceKernel)
                dataReceiver?.invoke(data)
            }
        })
    }

    // ==================== Service Discovery Functionality ====================

    /**
     * Detect that the target Tapro restarted its LAN server and force the stale socket closed.
     *
     * Tapro embeds a fresh timestamp in its mDNS instance name every time it registers, so a
     * restart shows up as: old instance lost -> new instance found at the *same* host:port.
     * That case is invisible to [handleServiceAddressChange] (the address did not change) and
     * invisible to the WebSocket layer (a peer that restarts without a graceful close leaves the
     * client socket half-open, so no close frame ever arrives). The result is a connection that
     * reports CONNECTED forever while every request silently disappears.
     *
     * Closing the socket here converts the situation into an ordinary disconnect, letting the
     * existing reconnect machinery re-establish the link against the restarted peer.
     *
     * @return true when a restart was detected and handled, false otherwise.
     */
    private fun handleServiceInstanceRestart(service: ServiceInfo): Boolean {
        val previous = currentService ?: return false

        if (!isPeerRestart(
                previousName = previous.name,
                previousHost = previous.host,
                previousPort = previous.port,
                newName = service.name,
                newHost = service.host,
                newPort = service.port
            )
        ) {
            // Either the same instance, or a genuine address change that the regular
            // address-change path already handles.
            return false
        }

        LogUtil.w(
            TAG,
            "Target Tapro re-registered at the same address (${previous.name} -> ${service.name} " +
                    "at ${service.getAddress()}); treating existing link as stale"
        )

        // Adopt the new instance identity so subsequent lost/found events compare correctly.
        currentService = service
        rememberTargetService(service)

        if (!isWebSocketConnected()) {
            LogUtil.d(TAG, "No live WebSocket to invalidate after peer restart")
            return false
        }

        return try {
            // Not a user-initiated disconnect: the close must surface as a passive disconnect so
            // the SDK layer runs its auto-reconnect logic.
            isManualDisconnect = false
            connectionManager.invalidate("Peer re-registered mDNS service: ${service.name}")
            LogUtil.i(TAG, "Closed stale LAN socket after peer restart, awaiting reconnect")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to close stale LAN socket after peer restart: ${e.message}")
            false
        }
    }

    /**
     * Common method to handle service address changes
     *
     * @param service New service information
     * @param oldHost Old host address
     * @param oldPort Old port number
     * @param logPrefix Log prefix (used to distinguish between onServiceFound and onServiceUpdated)
     * @return Boolean - true: need to reconnect, false: no need to reconnect or processing skipped
     */
    private fun handleServiceAddressChange(
        service: ServiceInfo,
        oldHost: String,
        oldPort: Int,
        logPrefix: String = "Service address change"
    ): Boolean {
        // Check if should skip service processing (already connected or connecting to same address)
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

        // Debounce check: avoid repeated triggers in short time
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

        // Notify service address change, SDK layer will automatically handle reconnection decision and execution
        LogUtil.i(
            TAG,
            "Notifying service address changed: ${service.name}, " +
                    "old=$oldHost:$oldPort, " +
                    "new=${service.host}:${service.port}"
        )

        // Use persistent listener to notify service address change
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
     * Stop service discovery
     */
    private fun stopServiceDiscovery() {
        serviceDiscoveryManager.setServiceNameFilter(null)
        serviceDiscoveryManager.stopServiceMonitoring()
        isServiceMonitoring = false
        LogUtil.d(TAG, "Service discovery stopped")
    }

    /**
     * Start service monitoring (updated version)
     * Monitor service changes, handle server IP/port changes, etc.
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
                    if (!isTargetService(service)) {
                        LogUtil.d(TAG, "Ignoring unrelated Tapro service: ${service.name}")
                        return
                    }
                    if (targetServiceSerial == null) {
                        rememberTargetService(service)
                        currentService = service
                    } else {
                        val serial = serviceSerial(service.name)
                        if (serial != null && serial != targetServiceSerial && isConnectedEndpoint(service)) {
                            // The user manually reconnected to a *different* Tapro (new ip/port that
                            // now advertises a new serial at the endpoint we are connected to).
                            // Adopt the new serial as the discovery identity so subsequent ip
                            // changes follow this device instead of the previously bound one.
                            LogUtil.i(
                                TAG,
                                "Connected endpoint advertises a new serial ($targetServiceSerial -> $serial); rebinding LAN discovery"
                            )
                            rememberTargetService(service)
                            currentService = service
                        }
                    }

                    // Tapro re-registers its mDNS service with a fresh instance name on restart.
                    // If the target device now advertises a *different* instance name at the same
                    // address we were connected to, the peer restarted and our socket is half-open:
                    // the local endpoint still reports ESTABLISHED, so no close callback will ever
                    // fire and the address-change path below would be skipped as "same address".
                    // Force the socket closed so the normal disconnect/reconnect flow can run.
                    if (handleServiceInstanceRestart(service)) {
                        return
                    }

                    // Use current service as old address
                    handleServiceAddressChange(
                        service = service,
                        oldHost = currentService?.host ?: "",
                        oldPort = currentService?.port ?: -1,
                        logPrefix = "Service found"
                    )
                }

                override fun onServiceLost(service: ServiceInfo) {
                    if (!isTargetService(service)) {
                        LogUtil.d(TAG, "Ignoring lost event for unrelated Tapro service: ${service.name}")
                        return
                    }

                    LogUtil.w(TAG, "Service lost: ${service.name} at ${service.getAddress()}")

                    // A lost service is normally followed by the WebSocket close/error callback.
                    if (currentService != null && service.name == currentService?.name) {
                        LogUtil.w(TAG, "Current connected service is lost, waiting for WebSocket disconnect")
                    }
                }

                override fun onServiceUpdated(oldService: ServiceInfo, newService: ServiceInfo) {
                    LogUtil.i(
                        TAG,
                        "Service updated: ${newService.name}, ${oldService.getAddress()} -> ${newService.getAddress()}"
                    )

                    // Check if address really changed
                    val addressChanged = oldService.host != newService.host || oldService.port != newService.port
                    if (!addressChanged) {
                        LogUtil.d(TAG, "Service updated but address unchanged, ignoring")
                        return
                    }
                    if (!isTargetService(newService)) {
                        LogUtil.d(TAG, "Ignoring address change for unrelated Tapro service: ${newService.name}")
                        return
                    }
                    rememberTargetService(newService)

                    // Use old service as old address
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

            serviceDiscoveryManager.setServiceNameFilter { serviceName ->
                // Only resolve the terminal we are bound to. NsdManager allows a single in-flight
                // resolve, so resolving every Tapro on the subnet makes the calls we actually
                // need fail with FAILURE_ALREADY_ACTIVE (error 3).
                val target = targetServiceSerial
                target == null || serviceSerial(serviceName) == target
            }

            serviceDiscoveryManager.startServiceMonitoring(NSD_SERVICE_TYPE, listener)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start service monitoring: ${e.message}")
        }
    }


    // ==================== Resource Management ====================

    /**
     * Clean up resources (updated version)
     *
     * Note: This method is used to clean up all states and resources, ensure state is clean before reuse
     */
    private fun cleanupResources() {
        // Clean up callbacks
        // Reset state variables
        currentUri = null
        currentService = null
        targetServiceSerial = null
        isServiceMonitoring = false
        isManualDisconnect = false
        lastServiceAddressChangeTime = 0
        currentOperation = null
        currentOperationId = null
        hasEstablishedConnection = false
        suppressNextDisconnect = false

        // Ensure all manager components are completely cleaned up
        try {
            stopServiceDiscovery()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during cleanup: ${e.message}")
        }

        LogUtil.d(TAG, "Resources cleaned up")
    }

    // ==================== Override Base Class Methods (if needed) ====================

    override fun cleanupCommonResources() {
        // Execute own cleanup first
        cleanupResources()

        // Then call base class cleanup
        super.cleanupCommonResources()
    }

    // ==================== New Public Methods ====================

    /**
     * Get current operation status
     */
    fun getCurrentOperation(): String? = currentOperation

    /**
     * Get current operation ID
     */
    fun getCurrentOperationId(): String? = currentOperationId

    /**
     * Check if there's an operation in progress
     */
    fun hasOperationInProgress(): Boolean = currentOperation != null
}