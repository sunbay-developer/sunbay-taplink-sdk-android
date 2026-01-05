package com.sunmi.tapro.taplink.sdk.impl

import android.content.Context
import com.google.gson.Gson
import com.sunmi.tapro.taplink.sdk.R
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.util.RequestConverter
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.util.LocalCallbackManager
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import java.math.BigDecimal

/**
 * Payment management class
 *
 * Handles all payment-related operations including:
 * - Payment request execution
 * - Transaction queries
 * - Request/response processing
 * - Callback management
 *
 * @author TaPro Team
 * @since 2025-12-22
 */
class PaymentManager(
    private val config: TaplinkConfig,
    private val connectionManager: ConnectionManager,
    private val responseProcessor: ResponseProcessor,
    private val context: Context
) {
    private val TAG = "PaymentManager"

    /**
     * Gson instance for JSON serialization
     */
    private val gson = Gson()

    /**
     * Callback manager for managing multiple async request callbacks
     */
    private val callbackManager = LocalCallbackManager<InnerCallback?>(
        defaultTimeoutMillis = Int.MAX_VALUE * 1L, // Use config timeout as default
        tag = TAG
    )

    /**
     * Flag to track if there's a pending transaction waiting for auto-connect
     * Used to distinguish between auto-connect from execute/query and normal reconnection
     */
    private var hasPendingAutoConnectTransaction = false

    /**
     * Pending transaction waiting for connection (only one at a time)
     * Used when connection is in progress but no auto-connect transaction is pending
     */
    private var pendingTransaction: Pair<PaymentRequest, PaymentCallback>? = null

    init {
        LogUtil.d(TAG, "PaymentManager initialized")

        // Set up data receiver for processing responses
        connectionManager.setDataReceiver { data ->
            processReceivedResponse(String(data, Charsets.UTF_8))
        }

        // Set up connection status listener to process pending transaction when connected
        connectionManager.setConnectionStatusListener {
            processPendingTransaction()
        }

        // Set up connection error listener to handle pending transaction when connection fails
        connectionManager.setConnectionErrorListener { error ->
            handlePendingTransactionError(error)
        }

        // Set up connection disconnected listener to clear auto-connect flag when disconnected
        connectionManager.setConnectionDisconnectedListener {
            if (hasPendingAutoConnectTransaction) {
                LogUtil.w(TAG, "Connection disconnected, clearing auto-connect flag")
                hasPendingAutoConnectTransaction = false
            }
        }
    }

    /**
     * Execute payment request
     */
    fun execute(request: PaymentRequest, callback: PaymentCallback) {
        LogUtil.d(TAG, "Executing payment request: action=${request.action}, referenceOrderId=${request.referenceOrderId}")

        // Check connection status
        if (connectionManager.isConnected()) {
            // Already connected, execute directly
            sendPaymentRequest(request, callback)
        } else if (connectionManager.isConnecting()) {
            // Connection in progress
            if (hasPendingAutoConnectTransaction) {
                // There's already a transaction waiting for auto-connect, reject this one
                LogUtil.w(TAG, "Connection in progress with pending transaction, rejecting new transaction")
                callback.onFailure(
                    PaymentError(
                        "CONNECTION_IN_PROGRESS",
                        "Connection is in progress with pending transaction, please wait for connection to complete"
                    )
                )
            } else {
                // No pending transaction, wait for connection to complete
                LogUtil.d(TAG, "Connection in progress, waiting for connection to complete before executing transaction")
                pendingTransaction = Pair(request, callback)
            }
        } else {
            // Not connected, auto-connect first
            LogUtil.d(TAG, "Not connected, attempting auto-connect before executing transaction")
            autoConnectAndExecute(request, callback)
        }
    }

    /**
     * Execute query request
     */
    fun query(request: QueryRequest, callback: PaymentCallback) {
        LogUtil.d(
            TAG,
            "Querying transaction: transactionId=${request.transactionId}, transactionRequestId=${request.transactionRequestId}"
        )

        try {
            // Convert QueryRequest to PaymentRequest
            val paymentRequest = convertQueryRequestToPaymentRequest(request)

            // Check connection status
            if (connectionManager.isConnected()) {
                // Already connected, execute directly
                sendPaymentRequest(paymentRequest, callback)
            } else if (connectionManager.isConnecting()) {
                // Connection in progress
                if (hasPendingAutoConnectTransaction) {
                    // There's already a transaction waiting for auto-connect, reject this one
                    LogUtil.w(TAG, "Connection in progress with pending transaction, rejecting new query")
                    callback.onFailure(
                        PaymentError(
                            "CONNECTION_IN_PROGRESS",
                            "Connection is in progress with pending transaction, please wait for connection to complete"
                        )
                    )
                } else {
                    // No pending transaction, wait for connection to complete
                    LogUtil.d(TAG, "Connection in progress, waiting for connection to complete before querying")
                    pendingTransaction = Pair(paymentRequest, callback)
                }
            } else {
                // Not connected, auto-connect first
                LogUtil.d(TAG, "Not connected, attempting auto-connect before querying transaction")
                autoConnectAndExecute(paymentRequest, callback)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error converting QueryRequest to PaymentRequest: ${e.message}")
            callback.onFailure(PaymentError("REQUEST_ERROR", e.message ?: "Failed to convert query request"))
        }
    }

    /**
     * Send payment request
     */
    private fun sendPaymentRequest(request: PaymentRequest, callback: PaymentCallback) {
        try {
            // Validate preconditions
            if (!validateConnection(callback)) return

            // Validate amount (must be integer, no decimal)
            if (!validateAmount(request, callback)) return

            // Convert PaymentRequest to BasicRequest
            val basicRequest = RequestConverter.convertToBasicRequest(
                request = request,
                version = config.version,
                config.appId,
                secretKey = config.secretKey
            )

            // Convert BasicRequest to JSON string
            val requestJson = gson.toJson(basicRequest)
            LogUtil.d(TAG, "Sending BasicRequest: action=${request.action}, requestSize=${requestJson.length} bytes")

            // Create internal callback for receiving raw response
            val internalCallback = createInternalCallback(callback, request, basicRequest.traceId)

            // Add callback to LocalCallbackManager before sending
            val isApp2App = connectionManager.getConnectionMode() == ConnectionMode.APP_TO_APP.name
            val added = when (request.action) {
                "QUERY" -> callbackManager.addQueryCallback(
                    basicRequest.traceId,
                    internalCallback,
                    isApp2App,
                    request.requestTimeout
                )

                else -> callbackManager.addTransactionCallback(
                    basicRequest.traceId,
                    internalCallback,
                    isApp2App,
                    request.requestTimeout
                )
            }

            if (!added) {
                LogUtil.w(TAG, "Failed to add callback to manager for traceId: ${basicRequest.traceId}")
            }
            LogUtil.d(TAG, "Callback registered with traceId: ${basicRequest.traceId}, action: ${request.action}")

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
                // Remove callback and notify error on send failure
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
     * Convert QueryRequest to PaymentRequest
     */
    private fun convertQueryRequestToPaymentRequest(queryRequest: QueryRequest): PaymentRequest {
        // Validate query request must have one query condition
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
        return PaymentRequest(
            action = TransactionAction.QUERY.value,
            transactionRequestId = transactionRequestId,
            description = description,
            // Query conditions: set based on fields in QueryRequest
            originalTransactionId = queryRequest.transactionId,
            originalTransactionRequestId = queryRequest.transactionRequestId,
            // Use default values from configuration
            staffInfo = config.defaultStaffInfo,
            deviceInfo = config.defaultDeviceInfo
        )
    }

    /**
     * Create internal callback object
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
                responseProcessor.handleResponse(responseData, callback, request)
            }
        }
    }

    /**
     * Process received response data
     */
    private fun processReceivedResponse(responseJson: String) {
        try {
            if (responseJson.isBlank()) {
                return
            }
            responseProcessor.processResponse(responseJson, callbackManager)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to process received response: ${e.message}")
        }
    }

    /**
     * Validate connection status
     */
    private fun validateConnection(callback: PaymentCallback): Boolean {
        if (!connectionManager.isConnected()) {
            LogUtil.e(TAG, "Not connected, please call connect() first")
            callback.onFailure(PaymentError("NOT_CONNECTED", "Not connected, please call connect() first"))
            return false
        }
        return true
    }

    /**
     * Validate amount fields - all amounts must be integers (no decimal)
     */
    private fun validateAmount(request: PaymentRequest, callback: PaymentCallback): Boolean {
        // Validate AmountInfo fields
        request.amount?.let { amountInfo ->
            if (!isInteger(amountInfo.orderAmount)) {
                LogUtil.e(TAG, "orderAmount must be an integer, got: ${amountInfo.orderAmount}")
                callback.onFailure(
                    PaymentError(
                        InnerErrorCode.C43.code,
                        "${InnerErrorCode.C43.description}:${context.getString(R.string.error_invalid_amount_order)}"
                    )
                )
                return false
            }

            amountInfo.tipAmount?.let {
                if (!isInteger(it)) {
                    LogUtil.e(TAG, "tipAmount must be an integer, got: $it")
                    callback.onFailure(
                        PaymentError(
                            InnerErrorCode.C43.code,
                            "${InnerErrorCode.C43.description}:${context.getString(R.string.error_invalid_amount_tip)}"
                        )
                    )
                    return false
                }
            }

            amountInfo.taxAmount?.let {
                if (!isInteger(it)) {
                    LogUtil.e(TAG, "taxAmount must be an integer, got: $it")
                    callback.onFailure(
                        PaymentError(
                            InnerErrorCode.C43.code,
                            "${InnerErrorCode.C43.description}:${context.getString(R.string.error_invalid_amount_tax)}"
                        )
                    )
                    return false
                }
            }

            amountInfo.surchargeAmount?.let {
                if (!isInteger(it)) {
                    LogUtil.e(TAG, "surchargeAmount must be an integer, got: $it")
                    callback.onFailure(
                        PaymentError(
                            InnerErrorCode.C43.code,
                            "${InnerErrorCode.C43.description}:${context.getString(R.string.error_invalid_amount_surcharge)}"
                        )
                    )
                    return false
                }
            }

            amountInfo.cashbackAmount?.let {
                if (!isInteger(it)) {
                    LogUtil.e(TAG, "cashbackAmount must be an integer, got: $it")
                    callback.onFailure(
                        PaymentError(
                            InnerErrorCode.C43.code,
                            "${InnerErrorCode.C43.description}:${context.getString(R.string.error_invalid_amount_cashback)}"
                        )
                    )
                    return false
                }
            }

            amountInfo.serviceFee?.let {
                if (!isInteger(it)) {
                    LogUtil.e(TAG, "serviceFee must be an integer, got: $it")
                    callback.onFailure(
                        PaymentError(
                            InnerErrorCode.C43.code,
                            "${InnerErrorCode.C43.description}:${context.getString(R.string.error_invalid_amount_service_fee)}"
                        )
                    )
                    return false
                }
            }
        }

        // Validate PaymentRequest tipAmount field
        request.tipAmount?.let {
            if (!isInteger(it)) {
                LogUtil.e(TAG, "PaymentRequest tipAmount must be an integer, got: $it")
                callback.onFailure(
                    PaymentError(
                        InnerErrorCode.C43.code,
                        "${InnerErrorCode.C43.description}:${context.getString(R.string.error_invalid_amount_tip)}"
                    )
                )
                return false
            }
        }

        return true
    }

    /**
     * Check if a BigDecimal value is an integer (no decimal part)
     */
    private fun isInteger(amount: BigDecimal): Boolean {
        // Check if the remainder when divided by 1 is zero
        return amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0
    }

    /**
     * Clear all pending callbacks
     */
    fun clearAllCallbacks() {
        callbackManager.clearAll()
    }

    /**
     * Immediately fail all pending transaction callbacks
     * Used when connection is lost or target process crashes
     *
     * @param errorCode Error code
     * @param errorMessage Error message
     */
    fun failAllPendingTransactions(errorCode: String, errorMessage: String) {
        LogUtil.w(TAG, "Failing all pending transactions: $errorCode - $errorMessage")
        // Clear auto-connect flag when connection is lost
        hasPendingAutoConnectTransaction = false
        callbackManager.failAllCallbacks(errorCode, errorMessage)
    }

    /**
     * Auto-connect and execute payment request
     *
     * If not connected, automatically connect first, then execute the transaction
     *
     * @param request Payment request
     * @param callback Payment callback
     */
    private fun autoConnectAndExecute(request: PaymentRequest, callback: PaymentCallback) {
        // Set flag to indicate there's a pending transaction waiting for auto-connect
        hasPendingAutoConnectTransaction = true

        val autoConnectListener = object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                LogUtil.d(TAG, "Auto-connect successful, executing payment request")
                // Clear flag
                hasPendingAutoConnectTransaction = false
                // Connection successful, execute transaction
                sendPaymentRequest(request, callback)
            }

            override fun onError(error: ConnectionError) {
                LogUtil.e(TAG, "Auto-connect failed: ${error.message}")
                // Clear flag
                hasPendingAutoConnectTransaction = false
                // Connection failed, notify callback
                callback.onFailure(
                    PaymentError(
                        error.code,
                        "Auto-connect failed: ${error.message}"
                    )
                )
            }

            override fun onDisconnected(reason: String) {
                // Clear flag if auto-connect was interrupted
                if (hasPendingAutoConnectTransaction) {
                    LogUtil.w(TAG, "Auto-connect disconnected: $reason")
                    hasPendingAutoConnectTransaction = false
                    // Notify callback of disconnection
                    callback.onFailure(
                        PaymentError(
                            "CONNECTION_DISCONNECTED",
                            "Auto-connect disconnected: $reason"
                        )
                    )
                }
            }

            override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
                LogUtil.d(TAG, "Auto-connect reconnecting: $currentAttempt/$maxAttempts")
            }
        }

        // Try to use saved connection config or default config
        val connectionConfig = connectionManager.getAutoConnectConfig()
        connectionManager.connect(connectionConfig, autoConnectListener)
    }

    /**
     * Process pending transaction after connection established
     * Called when connection is in progress (reconnecting) and no auto-connect transaction is pending
     */
    private fun processPendingTransaction() {
        val transaction = pendingTransaction
        if (transaction != null) {
            pendingTransaction = null
            val (request, callback) = transaction
            LogUtil.d(TAG, "Executing pending transaction after connection: action=${request.action}")
            sendPaymentRequest(request, callback)
        }
    }

    /**
     * Handle pending transaction error when connection fails
     * Called when connection fails (ERROR status) and there's a pending transaction
     */
    private fun handlePendingTransactionError(error: ConnectionError) {
        val transaction = pendingTransaction
        if (transaction != null) {
            pendingTransaction = null
            val (_, callback) = transaction
            LogUtil.e(TAG, "Connection failed, notifying pending transaction: ${error.message}")
            callback.onFailure(
                PaymentError(
                    error.code,
                    "Connection failed: ${error.message}"
                )
            )
        }
    }
}