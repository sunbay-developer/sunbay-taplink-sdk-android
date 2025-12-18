package com.sunmi.tapro.taplink.sdk.impl

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sunmi.tapro.taplink.sdk.BuildConfig
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.base.BasicResponse
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import com.sunmi.tapro.taplink.sdk.util.RequestConverter
import com.sunmi.tapro.taplink.sdk.reconnect.ReconnectManager
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.util.LocalCallbackManager
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback as ServiceConnectionCallback

/**
 * Taplink API Implementation Class
 *
 * Concrete implementation of all methods in TaplinkApi interface
 * This is a proxy class responsible for specific business logic implementation
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkApiImpl : TaplinkApi {

    private val TAG = "TaplinkApiImpl"

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 60
        private const val DEFAULT_CURRENCY = "USD"
        private const val ERROR_EVENT_CODE = "ERROR"
        private const val SUCCESS_CODE = "0"
    }

    /**
     * SDK configuration
     */
    private var config: TaplinkConfig? = null

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
    private var currentConnectionMode: ConnectionMode? = null

    /**
     * Connected device ID
     */
    private var connectedDeviceId: String? = null
    private var taproVersion: String? = null

    /**
     * Reconnection manager (lazy initialization)
     */
    private var reconnectManager: ReconnectManager? = null

    /**
     * Gson instance for JSON serialization
     */
    private val gson = Gson()

    /**
     * Callback manager
     * Used to manage callbacks for multiple asynchronous requests, supports finding and removing corresponding callbacks by traceId
     */
    private val callbackManager = LocalCallbackManager<InnerCallback?>(
        timeoutMillis = 60_000L, // Timeout 60 seconds
        tag = TAG
    )

    init {
        LogUtil.d(TAG, "TaplinkApiImpl initialized")
    }

    // ==================== Initialization ====================

    override fun init(context: Context, config: TaplinkConfig) {
        LogUtil.d(TAG, "Initializing SDK with appId: ${config.appId}")
        this.config = config
        
        // Initialize reconnection manager
        reconnectManager = ReconnectManager(config) { connectionConfig, listener ->
            connectInternal(connectionConfig, listener)
        }
        
        // Initialize ServiceKernel
        TaplinkServiceKernel.getInstance(context)
        
        // Configure log level
        if (config.logEnabled) {
            when (config.logLevel) {
                LogLevel.INFO -> LogUtil.setLevel(Log.INFO)
                LogLevel.WARN -> LogUtil.setLevel(Log.WARN)
                LogLevel.DEBUG -> LogUtil.setLevel(Log.DEBUG)
                LogLevel.ERROR -> LogUtil.setLevel(Log.ERROR)
            }
        } else {
            LogUtil.setLevel(9999)
        }
    }

    // ==================== Connection Management ====================

    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        // Prepare reconnection manager
        reconnectManager?.prepareConnect(config, listener)
        
        // Execute actual connection
        connectInternal(config, listener)
    }
    
    /**
     * Internal connection method
     * 
     * Actually executes connection logic, called by connect() and ReconnectManager
     */
    private fun connectInternal(config: ConnectionConfig?, listener: ConnectionListener) {
        LogUtil.d(
            TAG,
            "Connecting with config: cableProtocol=${config?.cableProtocol}, host=${config?.host}, port=${config?.port}"
        )
        
        if (this.config?.appId.isNullOrBlank() || this.config?.secretKey.isNullOrBlank()) {
            listener.onError(
                ConnectionError(
                    "C20",
                    "SDK not initialized, please call init() first"
                )
            )
            return
        }

        // Build protocol string based on ConnectionConfig
        val protocol = buildProtocolString(config)
        LogUtil.d(TAG, "Built protocol string: $protocol")

        // Connect through ServiceKernel
        TaplinkServiceKernel.getInstance()
            ?.connect(
                protocol,
                this.config!!.appId,
                this.config!!.secretKey,
                object : ServiceConnectionCallback {
                    override fun onConnected(extraInfoMap: Map<String, String?>?) {
                        LogUtil.d(TAG, "onConnected, extraInfoMap=${extraInfoMap.toString()}")
                        
                        // Notify reconnection manager of successful connection
                        reconnectManager?.onConnected()
                        
                        // Register data receiver for receiving responses
                        registerDataReceiver()
                        
                        // Send INIT command to initialize tapro application, wait for result before notifying connection success
                        sendInitCommand { success, errorCode, errorMsg ->
                            if (success) {
                                LogUtil.d(TAG, "INIT command completed successfully, notifying connection listeners")
                                updateConnectionStatus(ConnectionStatus.CONNECTED)
                                
                                // Get device information
                                connectedDeviceId = extraInfoMap?.getOrDefault("deviceId", "unknown")
                                taproVersion = extraInfoMap?.getOrDefault("taproVersion", "unknown")
                                
                                // Notify listeners of successful connection
                                listener.onConnected(connectedDeviceId ?: "unknown", taproVersion ?: "unknown")
                                connectionListener?.onConnected(connectedDeviceId ?: "unknown", taproVersion ?: "unknown")
                            } else {
                                LogUtil.e(TAG, "INIT command failed: code=$errorCode, msg=$errorMsg")
                                updateConnectionStatus(ConnectionStatus.DISCONNECTED)
                                
                                val error = ConnectionError(
                                    errorCode ?: "INIT_FAILED",
                                    errorMsg ?: "Failed to initialize tapro application"
                                )
                                listener.onError(error)
                                connectionListener?.onError(error)
                            }
                        }
                    }

                    override fun onWaitingConnect() {
                        updateConnectionStatus(ConnectionStatus.WAIT_CONNECTING)
                    }

                    override fun onDisconnected(code: String, msg: String) {
                        updateConnectionStatus(ConnectionStatus.DISCONNECTED)
                        val reason = "Code: $code, Message: $msg"
                        
                        // Attempt automatic reconnection
                        val reconnected = reconnectManager?.onDisconnected(listener) ?: false
                        
                        if (!reconnected) {
                            // No reconnection, notify application layer and clean up resources
                            listener.onDisconnected(reason)
                            connectionListener?.onDisconnected(reason)
                            
                            // Clean up resources (only clean up when not reconnecting)
                            connectedDeviceId = null
                            taproVersion = null
                            currentConnectionMode = null
                            callbackManager.clearAll()
                        }
                        // If reconnecting, keep device info and connection mode, don't clean callback manager
                    }
                })
    }

    override fun disconnect() {
        LogUtil.d(TAG, "Manual disconnect")
        
        // Notify reconnection manager of manual disconnection
        reconnectManager?.disconnect()
        
        updateConnectionStatus(ConnectionStatus.DISCONNECTED)
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.disconnect()
        connectedDeviceId = null
        taproVersion = null
        currentConnectionMode = null

        // Clean up all pending request callbacks
        callbackManager.clearAll()
    }

    override fun isConnected(): Boolean {
        return connectionStatus == ConnectionStatus.CONNECTED
    }

    override fun getConnectedDeviceId(): String? {
        return connectedDeviceId
    }

    override fun getConnectionMode(): ConnectionMode? {
        return currentConnectionMode
    }

    override fun getTaproVersion(): String? {
        return taproVersion
    }

    // ==================== Transaction Execution ====================

    override fun execute(request: PaymentRequest, callback: PaymentCallback) {
        LogUtil.d(TAG, "Executing payment request: action=${request.action}, referenceOrderId=${request.referenceOrderId}")
        sendPaymentRequest(request, callback)
    }

    // ==================== Transaction Query ====================

    /**
     * Query transaction status asynchronously
     *
     * **Implementation:** Convert QueryRequest to PaymentRequest (action="QUERY"), then call sendPaymentRequest
     * This allows reusing the same request sending logic, ensuring code consistency
     *
     * @param request Query request object
     * @param callback Payment callback interface
     */
    override fun query(request: QueryRequest, callback: PaymentCallback) {
        LogUtil.d(
            TAG,
            "Querying transaction: transactionId=${request.transactionId}, transactionRequestId=${request.transactionRequestId}"
        )

        try {
            // Convert QueryRequest to PaymentRequest
            val paymentRequest = convertQueryRequestToPaymentRequest(request)

            // Call sendPaymentRequest to send request
            sendPaymentRequest(paymentRequest, callback)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error converting QueryRequest to PaymentRequest: ${e.message}")
            callback.onFailure(PaymentError("REQUEST_ERROR", e.message ?: "Failed to convert query request"))
        }
    }

    // ==================== Listener Management ====================

    override fun setConnectionListener(listener: ConnectionListener?) {
        LogUtil.d(TAG, "Setting connection listener")
        this.connectionListener = listener
    }

    override fun removeConnectionListener() {
        LogUtil.d(TAG, "Removing connection listener")
        this.connectionListener = null
    }

    // ==================== Utility Methods ====================

    override fun getVersion(): String {
        return config?.version ?: BuildConfig.VERSION_NAME
    }

    override fun clearDeviceCache() {
        LogUtil.d(TAG, "Clearing device cache")
        connectedDeviceId = null
        taproVersion = null
        currentConnectionMode = null
        // TODO: Clear locally cached device information
    }

    // ==================== Private Helper Methods ====================

    /**
     * Build protocol string
     *
     * Build protocol string based on ConnectionConfig
     * If config is null or empty object, determine based on connectionMode from init
     * If connectionMode is also null, default to APP_TO_APP
     *
     * @param config Connection configuration
     * @return String protocol string
     */
    private fun buildProtocolString(config: ConnectionConfig?): String {
        // Check if config is null or empty object
        if (config == null || isEmptyConnectionConfig(config)) {
            LogUtil.d(TAG, "ConnectionConfig is null or empty, using TaplinkConfig.connectionMode")

            // Determine based on connectionMode from init
            val initConnectionMode = this.config?.connectionMode
            return when (initConnectionMode) {
                ConnectionMode.USB_AOA -> {
                    currentConnectionMode = ConnectionMode.USB_AOA
                    "usb_aoa://"
                }

                ConnectionMode.USB_VSP -> {
                    currentConnectionMode = ConnectionMode.USB_VSP
                    "usb_vsp://"
                }

                ConnectionMode.RS232 -> {
                    currentConnectionMode = ConnectionMode.RS232
                    "rs232://"
                }

                ConnectionMode.LAN -> {
                    currentConnectionMode = ConnectionMode.LAN
                    "wss://"
                }

                ConnectionMode.APP_TO_APP -> {
                    currentConnectionMode = ConnectionMode.APP_TO_APP
                    "local://"
                }

                else -> {
                    // connectionMode is also null, default to APP_TO_APP
                    LogUtil.d(TAG, "Both ConnectionConfig and connectionMode are null, using default APP_TO_APP")
                    currentConnectionMode = ConnectionMode.APP_TO_APP
                    "local://"
                }
            }
        }

        // Cable mode
        config.cableProtocol?.let { protocol ->
            currentConnectionMode = when (protocol) {
                com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_AOA -> ConnectionMode.USB_AOA
                com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_VSP -> ConnectionMode.USB_VSP
                com.sunmi.tapro.taplink.sdk.enums.CableProtocol.RS232 -> ConnectionMode.RS232
                com.sunmi.tapro.taplink.sdk.enums.CableProtocol.AUTO -> null
            }
            return when (protocol) {
                com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_AOA -> "usb_aoa://"
                com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_VSP -> "usb_vsp://"
                com.sunmi.tapro.taplink.sdk.enums.CableProtocol.RS232 -> "rs232://"
                com.sunmi.tapro.taplink.sdk.enums.CableProtocol.AUTO -> "auto://"
            }
        }

        // LAN mode
        config.host?.let { host ->
            val port = config.port ?: 8443
            currentConnectionMode = ConnectionMode.LAN
            return "ws://$host:$port"
        }

        // Default: app-to-app mode
        currentConnectionMode = ConnectionMode.APP_TO_APP
        return "local://"
    }

    /**
     * Check if ConnectionConfig is an empty object
     *
     * @param config Connection configuration
     * @return Boolean Whether it is an empty object
     */
    private fun isEmptyConnectionConfig(config: ConnectionConfig): Boolean {
        return config.cableProtocol == null &&
                config.host.isNullOrBlank() &&
                config.port == null
    }

    /**
     * Convert QueryRequest to PaymentRequest
     *
     * Query requests need to be converted to PaymentRequest format, with action set to "QUERY"
     * Other required fields use default values or are obtained from configuration
     *
     * @param queryRequest Query request object
     * @return PaymentRequest Converted payment request object
     */
    private fun convertQueryRequestToPaymentRequest(queryRequest: QueryRequest): PaymentRequest {
        val currentConfig = config
        if (currentConfig == null) {
            throw IllegalStateException("SDK not initialized")
        }

        // Validate that query request must have one query condition
        if (queryRequest.transactionId == null && queryRequest.transactionRequestId == null) {
            throw IllegalArgumentException("Either transactionId or transactionRequestId must be provided")
        }

        val transactionRequestId = queryRequest.transactionRequestId

        // Build query description
        val description = when {
            queryRequest.transactionId != null -> "Query transaction by transactionId: ${queryRequest.transactionId}"
            queryRequest.transactionRequestId != null -> "Query transaction by transactionRequestId: ${queryRequest.transactionRequestId}"
            else -> "Query transaction"
        }

        // Build PaymentRequest with action as QUERY
        // For queries, put query conditions in originalTransactionId or originalTransactionRequestId fields
        return PaymentRequest(
            action = TransactionAction.QUERY.value,
            transactionRequestId = transactionRequestId,
            description = description,
            // Query conditions: set according to fields in QueryRequest
            originalTransactionId = queryRequest.transactionId,
            originalTransactionRequestId = queryRequest.transactionRequestId,
            // Use default values from configuration
            staffInfo = currentConfig.defaultStaffInfo,
            deviceInfo = currentConfig.defaultDeviceInfo
        )
    }

    /**
     * Send payment request
     *
     * @param request Payment request
     * @param callback Payment callback
     */
    private fun sendPaymentRequest(request: PaymentRequest, callback: PaymentCallback) {
        try {
            // Validate preconditions
            val currentConfig = validateAndGetConfig(callback) ?: return
            if (!validateConnection(callback)) return

            // Convert PaymentRequest to BasicRequest
            val basicRequest = RequestConverter.convertToBasicRequest(
                request = request,
                version = currentConfig.version,
                secretKey = currentConfig.secretKey
            )

            // Convert BasicRequest to JSON string
            val requestJson = gson.toJson(basicRequest)
            LogUtil.d(TAG, "Sending BasicRequest: action=${request.action}, requestSize=${requestJson.length} bytes")

            // Create internal callback for receiving raw responses
            val internalCallback = createInternalCallback(callback, request, basicRequest.traceId)

            // Add callback to LocalCallbackManager before sending
            callbackManager.addCallbackByTraceId(basicRequest.traceId, internalCallback)
            LogUtil.d(TAG, "Callback registered with traceId: ${basicRequest.traceId}")

            // Send data through TaplinkServiceKernel
            val serviceKernel = TaplinkServiceKernel.getInstance()
            if (serviceKernel == null) {
                LogUtil.e(TAG, "Service kernel not available")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                callback.onFailure(PaymentError("NOT_CONNECTED", "Service kernel not available"))
                return
            }

            // Send data
            try {
                serviceKernel.sendData(
                    basicRequest.traceId,
                    requestJson.toByteArray(Charsets.UTF_8),
                    internalCallback // Callback is managed by LocalCallbackManager
                )
            } catch (e: Exception) {
                // When sending fails, remove callback and notify error
                LogUtil.e(TAG, "Failed to send data: traceId=${basicRequest.traceId}, error=${e.message}")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                callback.onFailure(PaymentError("SEND_ERROR", "Failed to send data: ${e.message}"))
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error processing request: action=${request.action}, error=${e.message}")
            callback.onFailure(PaymentError("REQUEST_ERROR", e.message ?: "Unknown error"))
        }
    }

    /**
     * Send INIT command to initialize tapro application
     *
     * @param onComplete Completion callback with parameters (success: Boolean, errorCode: String?, errorMsg: String?)
     */
    private fun sendInitCommand(onComplete: (Boolean, String?, String?) -> Unit) {
        try {
            LogUtil.d(TAG, "Sending INIT command to initialize tapro application")

            val currentConfig = config
            if (currentConfig == null) {
                LogUtil.e(TAG, "Cannot send INIT command: SDK not initialized")
                onComplete(false, "SDK_NOT_INITIALIZED", "SDK not initialized")
                return
            }

            // Build INIT request
            val initRequest = PaymentRequest(
                action = TransactionAction.INIT.value
            )

            // Convert PaymentRequest to BasicRequest
            val basicRequest = RequestConverter.convertToBasicRequest(
                request = initRequest,
                version = currentConfig.version,
                secretKey = currentConfig.secretKey
            )

            // Convert BasicRequest to JSON string
            val requestJson = gson.toJson(basicRequest)
            LogUtil.d(TAG, "INIT command prepared: traceId=${basicRequest.traceId}")

            // Create internal callback for receiving INIT response
            val initCallback = object : InnerCallback {
                override fun onError(code: String, msg: String) {
                    LogUtil.e(TAG, "INIT command failed: code=$code, msg=$msg")
                    onComplete(false, code, msg)
                }

                override fun onResponse(responseData: String) {
                    LogUtil.d(TAG, "INIT command response received: $responseData")
                    try {
                        // Parse response and determine if successful
                        val basicResponse = gson.fromJson(responseData, BasicResponse::class.java)

                        // Parse PaymentResult from bizData
                        val paymentResult = if (basicResponse.bizData != null) {
                            gson.fromJson(basicResponse.bizData, PaymentResult::class.java)
                        } else {
                            PaymentResult(code = "ERROR", message = basicResponse.event.eventMsg)
                        }

                        // Determine if successful
                        if (paymentResult.code == SUCCESS_CODE && basicResponse.event is PaymentEvent.Completed) {
                            LogUtil.d(TAG, "INIT command completed successfully")
                            onComplete(true, null, null)
                        } else {
                            LogUtil.e(TAG, "INIT command failed: code=${paymentResult.code}, message=${paymentResult.message}")
                            onComplete(false, paymentResult.code, paymentResult.message)
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Failed to parse INIT response: ${e.message}")
                        onComplete(false, "PARSE_ERROR", "Failed to parse INIT response: ${e.message}")
                    }
                }
            }

            // Register callback
            callbackManager.addCallbackByTraceId(basicRequest.traceId, initCallback)

            // Send INIT command
            val serviceKernel = TaplinkServiceKernel.getInstance()
            if (serviceKernel == null) {
                LogUtil.e(TAG, "Service kernel not available")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                onComplete(false, "SERVICE_UNAVAILABLE", "Service kernel not available")
                return
            }

            try {
                serviceKernel.sendData(
                    basicRequest.traceId,
                    requestJson.toByteArray(Charsets.UTF_8),
                    initCallback
                )
                LogUtil.d(TAG, "INIT command sent successfully")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to send INIT command: ${e.message}")
                callbackManager.removeCallbackByTraceId(basicRequest.traceId)
                onComplete(false, "SEND_ERROR", "Failed to send INIT command: ${e.message}")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to prepare INIT command: ${e.message}")
            onComplete(false, "PREPARE_ERROR", "Failed to prepare INIT command: ${e.message}")
        }
    }

    /**
     * Register data receiver for receiving responses
     */
    private fun registerDataReceiver() {
        TaplinkServiceKernel.getInstance()?.getCurrentServiceKernel()?.registerDataReceiver { data ->
            try {
                if (data == null || data.isEmpty()) {
                    return@registerDataReceiver
                }

                val responseJson = String(data, Charsets.UTF_8)
                processReceivedResponse(responseJson)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to process received data: ${e.message}")
            }
        }
    }

    /**
     * Process received response data
     *
     * @param responseJson Response JSON string
     */
    private fun processReceivedResponse(responseJson: String) {
        try {
            val jsonObject = JsonParser.parseString(responseJson).asJsonObject
            val traceId = jsonObject.get("traceId")?.asString
            val eventCode = jsonObject.get("eventCode")?.asString?.uppercase()

            if (traceId.isNullOrBlank()) {
                LogUtil.e(TAG, "Received data without traceId, ignoring: $responseJson")
                return
            }

            // Determine if it's an error response
            val isError = eventCode == ERROR_EVENT_CODE

            // Determine if callback should be removed
            val shouldRemove = shouldRemoveCallback(responseJson) || isError

            val callback = if (shouldRemove) {
                callbackManager.getAndRemoveCallbackByTraceId(traceId)
            } else {
                callbackManager.getCallbackByTraceId(traceId)
            }

            // Call corresponding callback method based on response type
            if (isError) {
                val errorCode = jsonObject.get("errorCode")?.asString ?: "UNKNOWN_ERROR"
                val errorMsg = jsonObject.get("eventMsg")?.asString ?: "Unknown error"
                callback?.onError(errorCode, errorMsg)
            } else {
                callback?.onResponse(responseJson)
            }
        } catch (e: Exception) {
            // If unable to parse as JSON, might be old format error response
            LogUtil.e(TAG, "Failed to parse response as JSON: $responseJson, error=${e.message}")
        }
    }

    /**
     * Create internal callback object
     *
     * @param callback Payment callback
     * @param request Payment request
     * @param traceId Trace ID
     * @return InnerCallback Internal callback object
     */
    private fun createInternalCallback(
        callback: PaymentCallback,
        request: PaymentRequest,
        traceId: String
    ): InnerCallback {
        return object : InnerCallback {
            override fun onError(code: String, msg: String) {
                LogUtil.e(TAG, "Received error: code=$code, msg=$msg, traceId=$traceId")
                callback.onFailure(PaymentError(code, msg))
            }

            override fun onResponse(responseData: String) {
                handleResponse(responseData, callback, request)
            }
        }
    }

    /**
     * Determine if callback should be removed
     *
     * @param responseJson Response JSON string
     * @return Boolean Whether callback should be removed
     */
    private fun shouldRemoveCallback(responseJson: String): Boolean {
        return try {
            val eventCode = extractEventCode(responseJson)
            if (eventCode == null) {
                return false
            }

            // Use PaymentEvent.fromEventCode to determine if it's Completed or Cancel
            val paymentEvent = PaymentEvent.fromEventCode(eventCode)
            when (paymentEvent) {
                is PaymentEvent.Completed -> true
                is PaymentEvent.Cancel -> true
                else -> false
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse responseJson for shouldRemoveCallback: ${e.message}")
            true // Default to remove when parsing fails
        }
    }

    /**
     * Extract eventCode from response JSON
     *
     * @param responseJson Response JSON string
     * @return String? eventCode, returns null if parsing fails
     */
    private fun extractEventCode(responseJson: String): String? {
        return try {
            val jsonObject = JsonParser.parseString(responseJson).asJsonObject
            val eventCodeElement = jsonObject.get("eventCode")
            when {
                eventCodeElement?.isJsonPrimitive == true -> {
                    val primitive = eventCodeElement.asJsonPrimitive
                    when {
                        primitive.isString -> primitive.asString
                        primitive.isNumber -> primitive.asString
                        else -> null
                    }
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Handle response data
     *
     * @param result Response JSON string
     * @param callback Payment callback
     * @param request Original payment request
     */
    private fun handleResponse(result: String, callback: PaymentCallback, request: PaymentRequest) {
        try {
            val basicResponse = gson.fromJson(result, BasicResponse::class.java)
            LogUtil.d(
                TAG,
                "Received BasicResponse: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}"
            )

            // Parse PaymentResult from bizData
            val paymentResult = if (basicResponse.bizData != null) {
                gson.fromJson(basicResponse.bizData, PaymentResult::class.java)
            } else {
                // If bizData is empty, create a default PaymentResult
                PaymentResult(
                    code = "ERROR",
                    message = basicResponse.event.eventMsg
                )
            }

            // First check the code
            if (paymentResult.code != SUCCESS_CODE) {
                // Code is not 0, call onFailure
                LogUtil.e(TAG, "Payment failed: code=${paymentResult.code}, message=${paymentResult.message}")
                callback.onFailure(
                    PaymentError(
                        paymentResult.code,
                        paymentResult.message ?: "unknown error"
                    )
                )
            } else {
                // Code is 0, determine based on PaymentEvent type
                when (basicResponse.event) {
                    is PaymentEvent.Completed -> {
                        // Event is Completed, call onSuccess
                        LogUtil.d(
                            TAG,
                            "Payment completed successfully: eventCode=${basicResponse.event.eventCode}"
                        )
                        callback.onSuccess(paymentResult)
                    }

                    is PaymentEvent.Cancel -> {
                        // Event is Cancel, call onFailure
                        LogUtil.e(
                            TAG,
                            "Payment cancelled: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}"
                        )
                        callback.onFailure(
                            PaymentError(
                                "CANCELLED",
                                basicResponse.event.eventMsg
                            )
                        )
                    }

                    else -> {
                        // Other event types, call onProgress
                        LogUtil.d(
                            TAG,
                            "Payment in progress: eventCode=${basicResponse.event.eventCode}, eventMsg=${basicResponse.event.eventMsg}, progress=${basicResponse.event.progress}"
                        )
                        callback.onProgress(basicResponse.event)
                    }
                }
            }
        } catch (e: Exception) {
            // Parsing failed, treat as send failure
            LogUtil.e(TAG, "Failed to parse response: action=${request.action}, result=$result, error=${e.message}")
            callback.onFailure(PaymentError("PARSE_ERROR", "Failed to parse response: ${e.message}"))
        }
    }

    /**
     * Validate and get configuration
     *
     * @param callback Payment callback for error notification
     * @return TaplinkConfig? Configuration object, returns null if validation fails
     */
    private fun validateAndGetConfig(callback: PaymentCallback): TaplinkConfig? {
        val currentConfig = config
        if (currentConfig == null) {
            LogUtil.e(TAG, "SDK not initialized, please call init() first")
            callback.onFailure(PaymentError("SDK_NOT_INITIALIZED", "SDK not initialized, please call init() first"))
            return null
        }
        return currentConfig
    }

    /**
     * Validate connection status
     *
     * @param callback Payment callback for error notification
     * @return Boolean Whether connected
     */
    private fun validateConnection(callback: PaymentCallback): Boolean {
        if (!isConnected()) {
            LogUtil.e(TAG, "Not connected, please call connect() first")
            callback.onFailure(PaymentError("NOT_CONNECTED", "Not connected, please call connect() first"))
            return false
        }
        return true
    }

    /**
     * Update connection status
     *
     * @param newStatus New status
     */
    private fun updateConnectionStatus(newStatus: ConnectionStatus) {
        if (connectionStatus != newStatus) {
            val oldStatus = connectionStatus
            connectionStatus = newStatus
            LogUtil.d(TAG, "Connection status changed: $oldStatus -> $newStatus")
            // Notify listeners
            connectionListener?.let { listener ->
                when (newStatus) {
                    ConnectionStatus.CONNECTED -> {
                        // When connection is successful, use actually obtained deviceId and taproVersion
                        listener.onConnected(connectedDeviceId ?: "unknown", taproVersion ?: "unknown")
                    }

                    ConnectionStatus.DISCONNECTED -> {
                        listener.onDisconnected("User disconnected")
                    }

                    ConnectionStatus.ERROR -> {
                        listener.onError(com.sunmi.tapro.taplink.sdk.error.ConnectionError("UNKNOWN", "Connection error"))
                    }

                    else -> {
                        // Other statuses
                    }
                }
            }
        }
    }
}
