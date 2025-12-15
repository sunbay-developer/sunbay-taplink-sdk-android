package com.sunmi.tapro.taplink.demo.service

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import java.math.BigDecimal
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener as SdkConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback as SdkPaymentCallback
import com.sunmi.tapro.taplink.sdk.error.ConnectionError as SdkConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError as SdkPaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent as SdkPaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult as SdkPaymentResult

/**
 * App-to-App mode payment service implementation
 * 
 * Implements PaymentService interface, encapsulates Taplink SDK calling logic
 */
class AppToAppPaymentService : PaymentService {
    
    companion object {
        private const val TAG = "AppToAppPaymentService"
        
        // Singleton instance
        @Volatile
        private var instance: AppToAppPaymentService? = null
        
        /**
         * Get singleton instance
         */
        fun getInstance(): AppToAppPaymentService {
            return instance ?: synchronized(this) {
                instance ?: AppToAppPaymentService().also { instance = it }
            }
        }
    }
    
    // Connection Status
    private var connected = false
    private var connecting = false
    
    // Connected Device Information
    private var connectedDeviceId: String? = null
    private var taproVersion: String? = null
    
    // Connection Listener
    private var connectionListener: ConnectionListener? = null
    
    /**
     * Initialize SDK
     * Note: SDK has been initialized in TaplinkDemoApplication, this method is not needed for App-to-App mode
     * App-to-App mode uses global SDK initialization, individual service initialization is not required
     */
    override fun initialize(
        context: Context,
        appId: String,
        merchantId: String,
        secretKey: String
    ): Boolean {
        Log.w(TAG, "=== SDK Initialize Called (Not Required) ===")
        Log.w(TAG, "App-to-App mode uses global SDK initialization in TaplinkDemoApplication")
        Log.w(TAG, "This method call is ignored - SDK already initialized globally")
        Log.w(TAG, "Use connect() method directly to establish connection")
        return true
    }
    
    /**
     * Connect to payment terminal
     * SDK has been initialized in TaplinkDemoApplication, only need to call connect here
     */
    override fun connect(listener: ConnectionListener) {
        this.connectionListener = listener
        
        Log.d(TAG, "=== Taplink SDK Connection Started ===")

        // Set connecting status
        connecting = true

        val connectionConfig = ConnectionConfig()
        
        Log.d(TAG, "=== Connection Request Parameters ===")
        Log.d(TAG, "Connection Config: $connectionConfig")
        
        Log.d(TAG, "=== Calling TaplinkSDK.connect() ===")
        
        // App-to-App mode: Pass null, SDK will use APP_TO_APP mode specified during initialization
        TaplinkSDK.connect(null, object : SdkConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "=== SDK Connection Callback: onConnected ===")
                Log.d(TAG, "Device ID: $deviceId")
                Log.d(TAG, "Tapro Version: $taproVersion")
                // Reset connecting status
                connecting = false
                handleConnected(deviceId, taproVersion)
            }
            
            override fun onDisconnected(reason: String) {
                Log.d(TAG, "=== SDK Connection Callback: onDisconnected ===")
                Log.d(TAG, "Disconnect Reason: $reason")
                // Reset connecting status
                connecting = false
                handleDisconnected(reason)
            }
            
            override fun onError(error: SdkConnectionError) {
                Log.e(TAG, "=== SDK Connection Callback: onError ===")
                Log.e(TAG, "Error Code: ${error.code}")
                Log.e(TAG, "Error Message: ${error.message}")
                Log.e(TAG, "Full Error Object: $error")
                // Reset connecting status
                connecting = false
                handleConnectionError(error.code, error.message)
            }
        })
        
        Log.d(TAG, "=== TaplinkSDK.connect() Called Successfully ===")
        Log.d(TAG, "Waiting for connection callbacks...")
    }
    
    /**
     * Handle connection success
     */
    private fun handleConnected(deviceId: String, version: String) {
        connected = true
        connectedDeviceId = deviceId
        taproVersion = version
        
        Log.d(TAG, "=== Connection Established Successfully ===")
        Log.d(TAG, "Connected Device ID: $deviceId")
        Log.d(TAG, "Tapro Version: $version")
        Log.d(TAG, "Connection Status: CONNECTED")
        Log.d(TAG, "Service Ready for Payment Operations")
        
        connectionListener?.onConnected(deviceId, version)
    }
    
    /**
     * Handle connection disconnected
     */
    private fun handleDisconnected(reason: String) {
        connected = false
        connectedDeviceId = null
        taproVersion = null
        
        Log.d(TAG, "=== Connection Disconnected ===")
        Log.d(TAG, "Disconnect Reason: $reason")
        Log.d(TAG, "Connection Status: DISCONNECTED")
        Log.d(TAG, "Device ID: Cleared")
        Log.d(TAG, "Tapro Version: Cleared")
        
        connectionListener?.onDisconnected(reason)
    }
    
    /**
     * Handle connection error
     */
    private fun handleConnectionError(code: String, message: String) {
        connected = false
        connectedDeviceId = null
        taproVersion = null
        
        Log.e(TAG, "=== Connection Error ===")
        Log.e(TAG, "Error Code: $code")
        Log.e(TAG, "Error Message: $message")
        Log.e(TAG, "Connection Status: FAILED")
        Log.e(TAG, "Device ID: Cleared")
        Log.e(TAG, "Tapro Version: Cleared")
        
        connectionListener?.onError(code, message)
    }
    
    /**
     * Handle payment failure result, log complete error information
     */
    private fun handlePaymentFailure(transactionType: String, error: SdkPaymentError, callback: PaymentCallback) {
        // Log complete error response
        Log.e(TAG, "=== $transactionType Response (Failure) ===")
        Log.e(TAG, "Error Code: ${error.code}")
        Log.e(TAG, "Error Message: ${error.message}")
        Log.e(TAG, "Full Error Object: $error")
        
        callback.onFailure(error.code, error.message)
    }
    
    /**
     * Handle payment result, check actual transaction status and route to success or failure
     */
    private fun handlePaymentResult(sdkResult: SdkPaymentResult, callback: PaymentCallback) {
        // Log complete response first
        Log.d(TAG, "=== Transaction Response (Raw) ===")
        Log.d(TAG, "Response Code: ${sdkResult.code}")
        Log.d(TAG, "Response Message: ${sdkResult.message}")
        Log.d(TAG, "Transaction ID: ${sdkResult.transactionId}")
        Log.d(TAG, "Transaction Request ID: ${sdkResult.transactionRequestId}")
        Log.d(TAG, "Transaction Status: ${sdkResult.transactionStatus}")
        Log.d(TAG, "Transaction Type: ${sdkResult.transactionType}")
        Log.d(TAG, "Transaction Result Code: ${sdkResult.transactionResultCode}")
        Log.d(TAG, "Transaction Result Message: ${sdkResult.transactionResultMsg}")
        Log.d(TAG, "Full Response Object: $sdkResult")
        
        // Check if transaction actually succeeded
        // Even if SDK calls onSuccess, we need to check transactionResultCode
        val isActuallySuccessful = isTransactionSuccessful(sdkResult)
        
        val result = PaymentResult(
            code = sdkResult.code,
            message = sdkResult.message ?: "Success",
            traceId = sdkResult.traceId,
            transactionId = sdkResult.transactionId,
            referenceOrderId = sdkResult.referenceOrderId,
            transactionRequestId = sdkResult.transactionRequestId,
            transactionStatus = sdkResult.transactionStatus,
            transactionType = sdkResult.transactionType,
            amount = sdkResult.amount?.let { amt ->
                TransactionAmount(
                    priceCurrency = amt.priceCurrency,
                    transAmount = amt.transAmount?.toDouble(),
                    orderAmount = amt.orderAmount?.toDouble(),
                    taxAmount = amt.taxAmount?.toDouble(),
                    serviceFee = amt.serviceFee?.toDouble(),
                    surchargeAmount = amt.surchargeAmount?.toDouble(),
                    tipAmount = amt.tipAmount?.toDouble(),
                    cashbackAmount = amt.cashbackAmount?.toDouble()
                )
            },
            createTime = sdkResult.createTime,
            completeTime = sdkResult.completeTime,
            cardInfo = sdkResult.cardInfo?.let { card ->
                CardInfo(
                    maskedPan = card.maskedPan,
                    cardNetworkType = card.cardNetworkType,
                    paymentMethodId = card.paymentMethodId,
                    subPaymentMethodId = card.subPaymentMethodId,
                    entryMode = card.entryMode,
                    authenticationMethod = card.authenticationMethod,
                    cardholderName = card.cardholderName,
                    expiryDate = card.expiryDate,
                    issuerBank = card.issuerBank,
                    cardBrand = card.cardBrand
                )
            },
            batchNo = sdkResult.batchNo,
            voucherNo = sdkResult.voucherNo,
            stan = sdkResult.stan,
            rrn = sdkResult.rrn,
            authCode = sdkResult.authCode,
            transactionResultCode = sdkResult.transactionResultCode,
            transactionResultMsg = sdkResult.transactionResultMsg,
            description = sdkResult.description,
            attach = sdkResult.attach,
            tipAmount = sdkResult.tipAmount?.toDouble(),
            totalAuthorizedAmount = sdkResult.totalAuthorizedAmount?.toDouble(),
            merchantRefundNo = sdkResult.merchantRefundNo,
            originalTransactionId = sdkResult.originalTransactionId,
            originalTransactionRequestId = sdkResult.originalTransactionRequestId,
            batchCloseInfo = sdkResult.batchCloseInfo?.let { bci ->
                BatchCloseInfo(
                    totalCount = bci.totalCount ?: 0,
                    totalAmount = bci.totalAmount?.toDouble() ?: 0.0,
                    totalTip = bci.totalTip?.toDouble() ?: 0.0,
                    totalTax = bci.totalTax?.toDouble() ?: 0.0,
                    totalSurchargeAmount = bci.totalSurchargeAmount?.toDouble() ?: 0.0,
                    totalServiceFee = bci.totalServiceFee?.toDouble() ?: 0.0,
                    cashDiscount = bci.cashDiscount?.toDouble() ?: 0.0,
                    closeTime = bci.closeTime ?: ""
                )
            }
        )
        
        if (isActuallySuccessful) {
            Log.d(TAG, "=== Transaction Actually Successful ===")
            callback.onSuccess(result)
        } else {
            Log.e(TAG, "=== Transaction Actually Failed (despite onSuccess callback) ===")
            Log.e(TAG, "Failure Reason: ${sdkResult.transactionResultMsg}")
            // Route to failure callback with the actual error details
            callback.onFailure(
                sdkResult.transactionResultCode ?: "UNKNOWN_ERROR",
                sdkResult.transactionResultMsg ?: "Transaction failed"
            )
        }
    }
    
    /**
     * Check if transaction is actually successful based on result codes
     * According to user feedback: Success transactions only return resultCode == "000"
     */
    private fun isTransactionSuccessful(sdkResult: SdkPaymentResult): Boolean {
        Log.d(TAG, "=== Checking Transaction Success ===")
        Log.d(TAG, "transactionStatus: ${sdkResult.transactionStatus}")
        Log.d(TAG, "transactionResultCode: ${sdkResult.transactionResultCode}")
        Log.d(TAG, "transactionResultMsg: ${sdkResult.transactionResultMsg}")
        Log.d(TAG, "code: ${sdkResult.code}")
        
        // According to user requirement: Success transactions return resultCode == "000" or "0"
        // Check transactionResultCode first (most reliable indicator)
        sdkResult.transactionResultCode?.let { resultCode ->
            Log.d(TAG, "Checking transactionResultCode: $resultCode")
            if (resultCode == "000" || resultCode == "0") {
                Log.d(TAG, "SUCCESS by transactionResultCode: $resultCode")
                return true
            } else {
                Log.d(TAG, "FAILED by transactionResultCode: $resultCode (not 000 or 0)")
                return false
            }
        }
        
        // If transactionResultCode is null, check transactionStatus as fallback
        when (sdkResult.transactionStatus) {
            "SUCCESS" -> {
                Log.d(TAG, "SUCCESS by transactionStatus (fallback)")
                return true
            }
            "FAILED" -> {
                Log.d(TAG, "FAILED by transactionStatus (fallback)")
                return false
            }
        }
        
        // Final fallback: check main response code
        val finalResult = sdkResult.code == "0"
        Log.d(TAG, "Final result by code check (fallback): $finalResult (code: ${sdkResult.code})")
        return finalResult
    }
    
    /**
     * Disconnect
     */
    override fun disconnect() {
        Log.d(TAG, "Disconnecting connection...")
        
        TaplinkSDK.disconnect()
        
        handleDisconnected("User initiated disconnection")
    }
    
    /**
     * Check connection status
     */
    override fun isConnected(): Boolean {
        return connected
    }
    
    /**
     * Check if connecting
     */
    override fun isConnecting(): Boolean {
        return connecting
    }
    
    /**
     * Get connected device ID
     */
    override fun getConnectedDeviceId(): String? {
        return connectedDeviceId
    }
    
    /**
     * Get Tapro version
     */
    override fun getTaproVersion(): String? {
        return taproVersion
    }
    
    /**
     * Execute SALE transaction
     */
    override fun executeSale(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: Double,
        currency: String,
        description: String,
        surchargeAmount: Double?,
        tipAmount: Double?,
        taxAmount: Double?,
        cashbackAmount: Double?,
        serviceFee: Double?,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing SALE transaction - OrderId: $referenceOrderId, Amount: $amount $currency")
        
        // Create AmountInfo with all amounts
        val amountInfo = AmountInfo(
            orderAmount = BigDecimal.valueOf(amount),
            pricingCurrency = currency,
            tipAmount = tipAmount?.let { BigDecimal.valueOf(it) },
            taxAmount = taxAmount?.let { BigDecimal.valueOf(it) },
            surchargeAmount = surchargeAmount?.let { BigDecimal.valueOf(it) },
            cashbackAmount = cashbackAmount?.let { BigDecimal.valueOf(it) },
            serviceFee = serviceFee?.let { BigDecimal.valueOf(it) }
        )
        
        val request = PaymentRequest("SALE")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setAmount(amountInfo)
            .setDescription(description)

        Log.d(TAG, "=== SALE Request ===")
        Log.d(TAG, "Request: $request")
        
        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("SALE", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "SALE transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "SALE transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
    
    /**
     * Execute AUTH transaction (pre-authorization)
     */
    override fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: Double,
        currency: String,
        description: String,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing AUTH transaction - OrderId: $referenceOrderId, Amount: $amount $currency")
        
        val request = PaymentRequest("AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setAmount(AmountInfo(BigDecimal.valueOf(amount), currency))
            .setDescription(description)
        
        Log.d(TAG, "=== AUTH Request ===")
        Log.d(TAG, "Request: $request")
        
        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("AUTH", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "AUTH transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "AUTH transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }

    /**
     * Execute FORCED_AUTH transaction (forced authorization)
     */
    override fun executeForcedAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: Double,
        currency: String,
        authCode: String,
        description: String,
        tipAmount: Double?,
        taxAmount: Double?,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }

        Log.d(TAG, "Executing FORCED_AUTH transaction - OrderId: $referenceOrderId, AuthCode: $authCode")

        // Create AmountInfo with all amounts
        val amountInfo = AmountInfo(
            orderAmount = BigDecimal.valueOf(amount),
            pricingCurrency = currency,
            tipAmount = tipAmount?.let { BigDecimal.valueOf(it) },
            taxAmount = taxAmount?.let { BigDecimal.valueOf(it) }
        )

        val request = PaymentRequest("FORCED_AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setAmount(amountInfo)
            .setForcedAuthCode(authCode)
            .setDescription(description)

        Log.d(TAG, "=== FORCED_AUTH Request ===")
        Log.d(TAG, "Request: $request")

        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }

            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("FORCED_AUTH", error, callback)
            }

            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "FORCED_AUTH transaction progress - Event: $eventStr")

                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"

                    else -> "FORCED_AUTH transaction processing..."
                }

                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
    
    /**
     * Execute REFUND transaction (refund)
     */
    override fun executeRefund(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: Double,
        currency: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing REFUND transaction - OriginalTxnId: $originalTransactionId, Amount: $amount $currency")
        
        val request = PaymentRequest("REFUND")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setAmount(AmountInfo(BigDecimal.valueOf(amount), currency))
            .setDescription(description)
        
        // Only set originalTransactionId if it's not empty (for reference-based refunds)
        if (originalTransactionId.isNotEmpty()) {
            request.setOriginalTransactionId(originalTransactionId)
        }
        
        reason?.let { request.setReason(it) }
        
        Log.d(TAG, "=== REFUND Request ===")
        Log.d(TAG, "Request: $request")
        
        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("REFUND", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "REFUND transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "REFUND transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
    
    /**
     * Execute VOID transaction (void)
     */
    override fun executeVoid(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing VOID transaction - OriginalTxnId: $originalTransactionId")
        
        val request = PaymentRequest("VOID")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setOriginalTransactionId(originalTransactionId)
            .setDescription(description)
        
        reason?.let { request.setReason(it) }
        
        Log.d(TAG, "=== VOID Request ===")
        Log.d(TAG, "Request: $request")
        
        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("VOID", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "VOID transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "VOID transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
    
    /**
     * Execute POST_AUTH transaction (post-auth)
     */
    override fun executePostAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: Double,
        currency: String,
        description: String,
        surchargeAmount: Double?,
        tipAmount: Double?,
        taxAmount: Double?,
        cashbackAmount: Double?,
        serviceFee: Double?,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing POST_AUTH transaction - OriginalTxnId: $originalTransactionId, Amount: $amount $currency")
        
        // Create AmountInfo with all amounts using builder pattern
        var amountInfo = AmountInfo(orderAmount = BigDecimal.valueOf(amount), pricingCurrency = currency)
        
        // Set additional amounts if provided
        // surchargeAmount?.let { amountInfo = amountInfo.setSurchargeAmount(BigDecimal.valueOf(it)) }
        tipAmount?.let { amountInfo = amountInfo.setTipAmount(BigDecimal.valueOf(it)) }
        taxAmount?.let { amountInfo = amountInfo.setTaxAmount(BigDecimal.valueOf(it)) }
        // cashbackAmount?.let { amountInfo = amountInfo.setCashbackAmount(BigDecimal.valueOf(it)) }
        // serviceFee?.let { amountInfo = amountInfo.setServiceFee(BigDecimal.valueOf(it)) }
        
        val request = PaymentRequest("POST_AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setOriginalTransactionId(originalTransactionId)
            .setAmount(amountInfo)
            .setDescription(description)
        
        Log.d(TAG, "=== POST_AUTH Request ===")
        Log.d(TAG, "Request: $request")
        
        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("POST_AUTH", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "POST_AUTH transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "POST_AUTH transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
    
    /**
     * Execute INCREMENT_AUTH transaction (incremental auth)
     */
    override fun executeIncrementalAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: Double,
        currency: String,
        description: String,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing INCREMENT_AUTH transaction - OriginalTxnId: $originalTransactionId, Amount: $amount $currency")
        
        val request = PaymentRequest("INCREMENT_AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setOriginalTransactionId(originalTransactionId)
            .setAmount(AmountInfo(BigDecimal.valueOf(amount), currency))
            .setDescription(description)
        
        Log.d(TAG, "=== INCREMENT_AUTH Request ===")
        Log.d(TAG, "Request: $request")
        
        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("INCREMENT_AUTH", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "INCREMENT_AUTH transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "INCREMENT_AUTH transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
    
    /**
     * Execute TIP_ADJUST transaction (tip adjust)
     */
    override fun executeTipAdjust(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        tipAmount: Double,
        description: String,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing TIP_ADJUST transaction - OriginalTxnId: $originalTransactionId, TipAmount: $tipAmount")
        
        val request = PaymentRequest("TIP_ADJUST")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setOriginalTransactionId(originalTransactionId)
            .setTipAmount(BigDecimal.valueOf(tipAmount))
            .setDescription(description)
        
        Log.d(TAG, "=== TIP_ADJUST Request ===")
        Log.d(TAG, "Request: $request")
        
        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("TIP_ADJUST", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "TIP_ADJUST transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "TIP_ADJUST transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
    
    /**
     * Execute QUERY transaction (query) - using transaction request ID
     */
    override fun executeQuery(
        transactionRequestId: String,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing QUERY transaction - TransactionRequestId: $transactionRequestId")
        
        val query = QueryRequest()
            .setTransactionRequestId(transactionRequestId)
        
        Log.d(TAG, "=== QUERY Request (by RequestId) ===")
        Log.d(TAG, "Request: $query")
        
        TaplinkSDK.query(query, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("QUERY (by RequestId)", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "QUERY transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "QUERY transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
    
    /**
     * Execute QUERY transaction (query) - using transaction ID
     */
    override fun executeQueryByTransactionId(
        transactionId: String,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing QUERY transaction - TransactionId: $transactionId")
        
        val query = QueryRequest()
            .setTransactionId(transactionId)
        
        Log.d(TAG, "=== QUERY Request (by TransactionId) ===")
        Log.d(TAG, "Request: $query")
        
        TaplinkSDK.query(query, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("QUERY (by TransactionId)", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                callback.onProgress("PROCESSING", "QUERY transaction processing...")
            }
        })
    }
    
    /**
     * Execute BATCH_CLOSE transaction (batch close)
     */
    override fun executeBatchClose(
        referenceOrderId: String,
        transactionRequestId: String,
        description: String,
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }
        
        Log.d(TAG, "Executing BATCH_CLOSE transaction")
        
        val request = PaymentRequest("BATCH_CLOSE")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setDescription(description)
        
        Log.d(TAG, "=== BATCH_CLOSE Request ===")
        Log.d(TAG, "Request: $request")
        
        TaplinkSDK.execute(request, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }
            
            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("BATCH_CLOSE", error, callback)
            }
            
            override fun onProgress(event: SdkPaymentEvent) {
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "BATCH_CLOSE transaction progress - Event: $eventStr")
                
                // Provide specific progress feedback based on event type
                val progressMessage = when {
                    eventStr.contains("PROCESSING", ignoreCase = true) -> "Processing transaction"
                    eventStr.contains("WAITING_CARD", ignoreCase = true) -> "Please insert, swipe or tap card"
                    eventStr.contains("CARD_DETECTED", ignoreCase = true) -> "Card detected"
                    eventStr.contains("READING_CARD", ignoreCase = true) -> "Card information is being read"
                    eventStr.contains("WAITING_PIN", ignoreCase = true) -> "Please enter PIN on payment terminal"
                    eventStr.contains("WAITING_SIGNATURE", ignoreCase = true) -> "Please enter signature on payment terminal"
                    eventStr.contains("WAITING_RESPONSE", ignoreCase = true) -> "Waiting for payment gateway response"
                    eventStr.contains("PRINTING", ignoreCase = true) -> "Transaction is being printed"
                    eventStr.contains("COMPLETED", ignoreCase = true) -> "Transaction completed successfully"
                    eventStr.contains("CANCEL", ignoreCase = true) -> "Transaction cancelled"
                    
                    else -> "BATCH_CLOSE transaction processing..."
                }
                
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
}
