package com.sunmi.tapro.taplink.sdk.impl

import com.google.gson.Gson
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.TransactionAction
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.util.RequestConverter
import com.sunmi.tapro.taplink.communication.TaplinkServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.util.LocalCallbackManager
import com.sunmi.tapro.taplink.communication.util.LogUtil

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
    private val responseProcessor: ResponseProcessor
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
        timeoutMillis = (if (config.connectionTimeout <= 0) 60 else config.connectionTimeout) * 1000L, // 30 seconds timeout for INIT command
        tag = TAG
    )
    
    init {
        LogUtil.d(TAG, "PaymentManager initialized")
        
        // Set up data receiver for processing responses
        connectionManager.setDataReceiver { data ->
            processReceivedResponse(String(data, Charsets.UTF_8))
        }
    }
    
    /**
     * Execute payment request
     */
    fun execute(request: PaymentRequest, callback: PaymentCallback) {
        LogUtil.d(TAG, "Executing payment request: action=${request.action}, referenceOrderId=${request.referenceOrderId}")
        sendPaymentRequest(request, callback)
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
            
            // Call sendPaymentRequest to send request
            sendPaymentRequest(paymentRequest, callback)
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
     * Clear all pending callbacks
     */
    fun clearAllCallbacks() {
        callbackManager.clearAll()
    }
}