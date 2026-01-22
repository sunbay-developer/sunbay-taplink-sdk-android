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
 * - HeartbeatManager: Heartbeat mechanism management
 *
 * @param appId Application ID
 * @param appSecretKey Application secret key
 * @param context Android context
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

        // Service discovery retry configuration
        private const val SERVICE_DISCOVERY_MAX_RETRIES = 3  // Maximum retry count
        private const val SERVICE_DISCOVERY_RETRY_DELAY_MS = 1000L  // Initial retry delay (milliseconds)
        private const val SERVICE_DISCOVERY_RETRY_DELAY_MULTIPLIER = 2  // Retry delay multiplier (incremental delay)
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

    // ==================== Manager Components ====================

    /**
     * WebSocket connection manager
     */
    private val connectionManager = ConnectionManager()

    /**
     * Service discovery manager
     */
    private val serviceDiscoveryManager = ServiceDiscoveryManager(context)

    /**
     * Heartbeat manager
     */
    private val heartbeatManager = HeartbeatManager()

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
        // 2. There may be internal reconnection operations (service discovery, heartbeat failure, etc.) in progress
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

            // Stop heartbeat
            stopHeartbeat()

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

    /**
     * Disconnect handling when heartbeat fails
     *
     * When heartbeat fails, only disconnect connection, do not stop mDNS service discovery,
     * so that device can be rediscovered and reconnected after recovery
     */
    private fun disconnectOnHeartbeatFailure() {
        launchInScope {
            try {
                LogUtil.d(TAG, "Disconnecting due to heartbeat failure, keeping service discovery active")

                // Do not mark as manual disconnect (because it's passive disconnect)
                // isManualDisconnect remains false

                // Stop heartbeat
                stopHeartbeat()

                // Safely update status
                updateStatusSafely(InnerConnectionStatus.ERROR)

                // Notify connection error: use base class's notifyConnectionError()
                notifyConnectionError("Connection lost due to heartbeat failure", InnerErrorCode.E213)

                // Do not stop service discovery, keep mDNS service discovery running,
                // so that device can be rediscovered after recovery

                // Disconnect WebSocket connection
                connectionManager.disconnect()

                // Clean up connection-related resources, but keep service discovery-related resources
                currentUri = null
                currentService = null
                // Note: Do not call cleanupResources(), because it will clean up all resources
                // Also do not call stopServiceDiscovery(), keep service discovery running

                LogUtil.d(TAG, "Connection disconnected due to heartbeat failure, service discovery remains active")

            } catch (e: Exception) {
                LogUtil.e(TAG, "Error during heartbeat failure disconnect: ${e.message}")
            }
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
                launchInScope {
                    LogUtil.d(TAG, "LAN connection established")

                    // Safely update status
                    updateStatusSafely(InnerConnectionStatus.CONNECTED)

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

                    // Start heartbeat
                    startHeartbeat()

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

                    // Stop heartbeat
                    stopHeartbeat()

                    // Build disconnect information
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
                launchInScope {
                    LogUtil.e(TAG, "LAN connection error: ${exception.message}")

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

                // Check if it's a heartbeat response
                if (heartbeatManager.isHeartbeatResponse(message)) {
                    handleHeartbeatResponse()
                    return
                }

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
                    // Use current service as old address
                    handleServiceAddressChange(
                        service = service,
                        oldHost = currentService?.host ?: "",
                        oldPort = currentService?.port ?: -1,
                        logPrefix = "Service found"
                    )
                }

                override fun onServiceLost(service: ServiceInfo) {
                    LogUtil.w(TAG, "Service lost: ${service.name} at ${service.getAddress()}")

                    // If lost service is currently connected service, heartbeat failure will detect first and trigger disconnect
                    if (currentService != null && service.name == currentService?.name) {
                        LogUtil.w(TAG, "Current connected service is lost, heartbeat failure will handle reconnection")
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

            serviceDiscoveryManager.startServiceMonitoring(NSD_SERVICE_TYPE, listener)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start service monitoring: ${e.message}")
        }
    }


    // ==================== Heartbeat Functionality ====================

    /**
     * Start heartbeat
     */
    private fun startHeartbeat() {
        try {
            val config = HeartbeatManager.HeartbeatConfig()
            val sender = object : HeartbeatManager.HeartbeatSender {
                override suspend fun sendHeartbeat(message: String): Boolean {
                    // Use heartbeat send method with network connectivity check, ensure network is reachable before sending
                    return connectionManager.sendHeartbeatWithConnectivityCheck(message)
                }
            }

            // Set heartbeat listener
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

                    // If consecutive failures too many, can consider reconnection
                    if (consecutiveFailures >= 2) {
                        LogUtil.w(TAG, "Multiple heartbeat timeouts, connection may be unstable")
                    }
                }

                override fun onHeartbeatFailed(error: String, consecutiveFailures: Int) {
                    LogUtil.e(TAG, "Heartbeat failed: $error, consecutive failures: $consecutiveFailures")

                    // Heartbeat consecutive failures, connection may be disconnected
                    if (consecutiveFailures >= 2) { // Lower threshold, detect problems faster
                        LogUtil.e(TAG, "Too many heartbeat failures, disconnecting connection but keeping service discovery")
                        // Notify connection error
                        notifyConnectionError("Heartbeat failed: $error", InnerErrorCode.E241)
                        // Disconnect on heartbeat failure, but don't stop mDNS service discovery, so can rediscover and reconnect after device recovery
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
     * Stop heartbeat
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
     * Handle heartbeat response
     */
    private fun handleHeartbeatResponse() {
        heartbeatManager.handleHeartbeatResponse()
        LogUtil.d(TAG, "Heartbeat response processed")
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
        isServiceMonitoring = false
        isManualDisconnect = false
        lastServiceAddressChangeTime = 0
        currentOperation = null
        currentOperationId = null

        // Ensure all manager components are completely cleaned up
        try {
            stopHeartbeat()
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