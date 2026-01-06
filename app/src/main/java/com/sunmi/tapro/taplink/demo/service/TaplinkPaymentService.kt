package com.sunmi.tapro.taplink.demo.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import java.math.BigDecimal
import java.math.RoundingMode
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener as SdkConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback as SdkPaymentCallback
import com.sunmi.tapro.taplink.sdk.error.ConnectionError as SdkConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError as SdkPaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent as SdkPaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult as SdkPaymentResult

/**
 * Unified payment service implementation supporting multiple connection modes
 *
 * Supports App-to-App, Cable, and LAN connection modes
 * Implements PaymentService interface, encapsulates Taplink SDK calling logic
 */
class TaplinkPaymentService : PaymentService {

    companion object {
        private const val TAG = "TaplinkPaymentService"

        // Singleton instance
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TaplinkPaymentService? = null

        /**
         * Get singleton instance
         */
        fun getInstance(): TaplinkPaymentService {
            return instance ?: synchronized(this) {
                instance ?: TaplinkPaymentService().also { instance = it }
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

    // Context reference for accessing resources and preferences
    private var context: Context? = null

    /**
     * Get progress message from SDK event - directly use SDK message
     */
    private fun getProgressMessage(event: SdkPaymentEvent, transactionType: String): String {
        return event.eventMsg?.takeIf { it.isNotBlank() } 
            ?: "$transactionType transaction processing..."
    }

    /**
     * Initialize SDK with simplified logic
     * Only performs one-time SDK initialization - connection mode is handled in connect()
     */
    override fun initialize(
        context: Context,
        appId: String,
        merchantId: String,
        secretKey: String
    ): Boolean {
        this.context = context

        Log.d(TAG, "Initializing SDK")

        // Read configuration from resources
        val actualAppId = context.getString(R.string.taplink_app_id)
        val actualMerchantId = context.getString(R.string.taplink_merchant_id)
        val actualSecretKey = context.getString(R.string.taplink_secret_key)

        // Validate configuration
        if (actualAppId.isBlank() || actualMerchantId.isBlank() || actualSecretKey.isBlank()) {
            Log.e(TAG, "SDK initialization failed: Missing configuration")
            return false
        }

        // Create TaplinkConfig without ConnectionMode (ConnectionMode will be set in connect phase)
        val config = TaplinkConfig(
            appId = actualAppId,
            merchantId = actualMerchantId,
            secretKey = actualSecretKey
        ).setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG)

        return try {
            TaplinkSDK.init(context, config)
            Log.d(TAG, "SDK initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SDK initialization failed: ${e.message}", e)
            false
        }
    }

    /**
     * Connect to payment terminal with provided ConnectionConfig
     * Main connection method that accepts ConnectionConfig with ConnectionMode already set
     * 
     * Important: This method will always attempt to establish a new connection.
     * If already connected, it will replace the existing connection listener.
     */
    override fun connect(connectionConfig: ConnectionConfig, listener: ConnectionListener) {
        this.connectionListener = listener
        
        Log.d(TAG, "=== connect() called ===")
        Log.d(TAG, "ConnectionListener set: ${listener != null}")
        Log.d(TAG, "Current connection status: connected=$connected, connecting=$connecting")
        Log.d(TAG, "Connecting with provided ConnectionConfig: $connectionConfig")

        // If already connected, we still proceed to register the new listener
        // This allows UI components to receive connection status updates
        if (connected) {
            Log.d(TAG, "Already connected, but registering new listener")
            // Immediately notify the new listener of current connection status
            connectedDeviceId?.let { deviceId ->
                taproVersion?.let { version ->
                    listener.onConnected(deviceId, version)
                }
            }
            return
        }

        // If currently connecting, don't start another connection
        if (connecting) {
            Log.d(TAG, "Already connecting, just updating listener")
            return
        }

        connecting = true

        Log.d(TAG, "Calling TaplinkSDK.connect() with provided config: $connectionConfig")

        TaplinkSDK.connect(connectionConfig, object : SdkConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "Connected - Device: $deviceId, Version: $taproVersion")
                connecting = false
                handleConnected(deviceId, taproVersion)
            }

            override fun onDisconnected(reason: String) {
                Log.d(TAG, "Disconnected - Reason: $reason")
                connecting = false
                handleDisconnected(reason)
            }

            override fun onError(error: SdkConnectionError) {
                Log.e(TAG, "Connection error - Code: ${error.code}, Message: ${error.message}")
                connecting = false
                handleConnectionError(error.code, error.message)
            }
        })
    }



    /**
     * Handle connection success
     */
    private fun handleConnected(deviceId: String, version: String) {
        connected = true
        connectedDeviceId = deviceId
        taproVersion = version

        Log.d(TAG, "=== handleConnected called ===")
        Log.d(TAG, "Device ID: $deviceId, Version: $version")
        Log.d(TAG, "connectionListener is null: ${connectionListener == null}")
        Log.d(TAG, "Current thread: ${Thread.currentThread().name}")
        
        // Direct call without Handler to test
        Log.d(TAG, "About to call connectionListener.onConnected directly")
        Log.d(TAG, "connectionListener object: $connectionListener")
        
        try {
            connectionListener?.let { listener ->
                Log.d(TAG, "Calling listener.onConnected($deviceId, $version)")
                listener.onConnected(deviceId, version)
                Log.d(TAG, "listener.onConnected call completed successfully")
            } ?: run {
                Log.w(TAG, "connectionListener is null, cannot call onConnected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in connectionListener.onConnected", e)
        }
        
        Log.d(TAG, "=== handleConnected completed ===")
    }

    /**
     * Handle connection disconnected
     */
    private fun handleDisconnected(reason: String) {
        connected = false
        connectedDeviceId = null
        taproVersion = null

        Log.d(TAG, "=== handleDisconnected called ===")
        Log.d(TAG, "Disconnected - Reason: $reason")
        Log.d(TAG, "connectionListener is null: ${connectionListener == null}")
        
        try {
            connectionListener?.let { listener ->
                Log.d(TAG, "Calling listener.onDisconnected($reason)")
                listener.onDisconnected(reason)
                Log.d(TAG, "listener.onDisconnected call completed successfully")
            } ?: run {
                Log.w(TAG, "connectionListener is null, cannot call onDisconnected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in connectionListener.onDisconnected", e)
        }
        
        Log.d(TAG, "=== handleDisconnected completed ===")
    }

    /**
     * Handle connection error - directly forward SDK error
     */
    private fun handleConnectionError(code: String, message: String) {
        connected = false
        connectedDeviceId = null
        taproVersion = null

        Log.d(TAG, "=== handleConnectionError called ===")
        Log.e(TAG, "Connection error - Code: $code, Message: $message")
        Log.d(TAG, "connectionListener is null: ${connectionListener == null}")
        Log.d(TAG, "Current thread: ${Thread.currentThread().name}")
        
        // Direct call without Handler to test
        Log.d(TAG, "About to call connectionListener.onError directly")
        Log.d(TAG, "connectionListener object: $connectionListener")
        
        try {
            connectionListener?.let { listener ->
                Log.d(TAG, "Calling listener.onError($code, $message)")
                listener.onError(code, message)
                Log.d(TAG, "listener.onError call completed successfully")
            } ?: run {
                Log.w(TAG, "connectionListener is null, cannot call onError")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in connectionListener.onError", e)
        }
        
        Log.d(TAG, "=== handleConnectionError completed ===")
    }

    /**
     * Handle payment failure - directly forward SDK error
     * Also check if this is a connection-related error and update connection status
     */
    private fun handlePaymentFailure(
        transactionType: String,
        error: SdkPaymentError,
        callback: PaymentCallback
    ) {
        Log.e(TAG, "$transactionType failed - Code: ${error.code}, Message: ${error.message}")
        
        // Check if this is a connection-related error
        if (isConnectionRelatedError(error.code)) {
            Log.w(TAG, "Payment failure due to connection error: ${error.code}")
            // Update connection status immediately
            handleConnectionLost("Payment error: ${error.message}")
        }
        
        callback.onFailure(error.code, error.message)
    }
    
    /**
     * Handle connection lost - centralized connection state cleanup
     */
    private fun handleConnectionLost(reason: String) {
        if (connected) {
            Log.w(TAG, "Connection lost detected: $reason")
            connected = false
            connectedDeviceId = null
            taproVersion = null
            
            // Notify connection listener about the disconnection
            connectionListener?.onDisconnected(reason)
        }
    }
    
    /**
     * Check if error code indicates a connection problem
     */
    private fun isConnectionRelatedError(code: String): Boolean {
        return when (code) {
            "C36" -> true  // Target application crashed
            "C01" -> true  // Connection timeout
            "C02" -> true  // Connection failed
            "C03" -> true  // Connection lost
            "C04" -> true  // Service disconnected
            "C05" -> true  // Service binding failed
            else -> code.startsWith("C")  // Most C-codes are connection related
        }
    }

    /**
     * Handle payment result - converts SDK cents amounts back to dollars for UI display
     * Converts SDK result to internal PaymentResult format
     * Determines success based on transaction result code and status
     */
    private fun handlePaymentResult(sdkResult: SdkPaymentResult, callback: PaymentCallback) {
        Log.d(TAG, "Payment result - Code: ${sdkResult.code}, Status: ${sdkResult.transactionStatus}")

        // Convert cents amounts back to dollars for UI display
        fun toDollars(centsAmount: BigDecimal?): BigDecimal? {
            return centsAmount?.let { 
                it.divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            }
        }

        // Check if transaction is successful based on result code
        val isSuccessful = sdkResult.transactionResultCode == "000" || 
                          sdkResult.transactionResultCode == "0" ||
                          sdkResult.transactionStatus == "SUCCESS"

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
                // Convert cents to dollars
                TransactionAmount(
                    priceCurrency = amt.priceCurrency,
                    transAmount = toDollars(amt.transAmount), 
                    orderAmount = toDollars(amt.orderAmount), 
                    taxAmount = toDollars(amt.taxAmount), 
                    serviceFee = toDollars(amt.serviceFee), 
                    surchargeAmount = toDollars(amt.surchargeAmount), 
                    tipAmount = toDollars(amt.tipAmount), 
                    cashbackAmount = toDollars(amt.cashbackAmount) 
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
            tipAmount = toDollars(sdkResult.tipAmount), 
            totalAuthorizedAmount = toDollars(sdkResult.totalAuthorizedAmount), 
            merchantRefundNo = sdkResult.merchantRefundNo,
            originalTransactionId = sdkResult.originalTransactionId,
            originalTransactionRequestId = sdkResult.originalTransactionRequestId,
            batchCloseInfo = sdkResult.batchCloseInfo?.let { bci ->
                BatchCloseInfo(
                    totalCount = bci.totalCount ?: 0,
                    totalAmount = toDollars(bci.totalAmount) ?: BigDecimal.ZERO, 
                    totalTip = toDollars(bci.totalTip) ?: BigDecimal.ZERO, 
                    totalTax = toDollars(bci.totalTax) ?: BigDecimal.ZERO, 
                    totalSurchargeAmount = toDollars(bci.totalSurchargeAmount) ?: BigDecimal.ZERO, 
                    totalServiceFee = toDollars(bci.totalServiceFee) ?: BigDecimal.ZERO, 
                    cashDiscount = toDollars(bci.cashDiscount) ?: BigDecimal.ZERO, 
                    closeTime = bci.closeTime ?: ""
                )
            }
        )

        if (isSuccessful) {
            callback.onSuccess(result)
        } else {
            callback.onFailure(
                sdkResult.transactionResultCode ?: "UNKNOWN_ERROR",
                sdkResult.transactionResultMsg ?: "Transaction failed"
            )
        }
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
     * Check connection status - combines internal state with SDK state
     */
    override fun isConnected(): Boolean {
        // Always check both internal state and SDK state for accuracy
        val sdkConnected = try {
            TaplinkSDK.isConnected()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SDK connection status", e)
            false
        }
        
        // If SDK says disconnected but we think we're connected, update our state
        if (!sdkConnected && connected) {
            Log.w(TAG, "SDK reports disconnected but internal state was connected - updating")
            connected = false
            connectedDeviceId = null
            taproVersion = null
            
            // Notify listener about the disconnection
            connectionListener?.onDisconnected("SDK connection lost")
        }
        
        return connected && sdkConnected
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
        amount: BigDecimal,
        currency: String,
        description: String,
        surchargeAmount: BigDecimal?,
        tipAmount: BigDecimal?,
        taxAmount: BigDecimal?,
        cashbackAmount: BigDecimal?,
        serviceFee: BigDecimal?,
        staffInfo: StaffInfo?,
        callback: PaymentCallback
    ) {

        Log.d(
            TAG,
            "Executing SALE transaction - OrderId: $referenceOrderId, Amount: $amount $currency"
        )

        // Convert dollar amounts to cents for SDK
        fun toCents(dollarAmount: BigDecimal): BigDecimal {
            return (dollarAmount * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        }

        // Create AmountInfo with main amount converted to cents
        var amountInfo = AmountInfo(
            orderAmount = toCents(amount), // Convert main amount to cents
            pricingCurrency = currency
        )

        // Set additional amounts if provided, converting each to cents
        surchargeAmount?.let { 
            amountInfo = amountInfo.setSurchargeAmount(toCents(it))
        }
        tipAmount?.let { 
            amountInfo = amountInfo.setTipAmount(toCents(it))
        }
        taxAmount?.let { 
            amountInfo = amountInfo.setTaxAmount(toCents(it))
        }
        cashbackAmount?.let { 
            amountInfo = amountInfo.setCashbackAmount(toCents(it))
        }
        serviceFee?.let { 
            amountInfo = amountInfo.setServiceFee(toCents(it))
        }

        val staffInfo = StaffInfo(
            operatorId = "Harry",
            tipRecipientId = "Harry"
        )

        val request = PaymentRequest("SALE")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setAmount(amountInfo)
            .setDescription(description)
            .setStaffInfo(staffInfo)

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
                Log.d(TAG, "SALE progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "SALE")
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
        amount: BigDecimal,
        currency: String,
        description: String,
        callback: PaymentCallback
    ) {

        Log.d(
            TAG,
            "Executing AUTH transaction - OrderId: $referenceOrderId, Amount: $amount $currency"
        )

        // Convert dollar amount to cents for SDK
        val amountInCents = (amount * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)

        val request = PaymentRequest("AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setAmount(AmountInfo(amountInCents, currency))
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
                Log.d(TAG, "AUTH progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "AUTH")
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
        amount: BigDecimal,
        currency: String,
        description: String,
        tipAmount: BigDecimal?,
        taxAmount: BigDecimal?,
        callback: PaymentCallback
    ) {

        Log.d(
            TAG,
            "Executing FORCED_AUTH transaction - OrderId: $referenceOrderId"
        )

        // Convert dollar amounts to cents for SDK
        fun toCents(dollarAmount: BigDecimal): BigDecimal {
            return (dollarAmount * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        }

        // Create AmountInfo with all amounts converted to cents
        var amountInfo = AmountInfo(
            orderAmount = toCents(amount),
            pricingCurrency = currency
        )
        
        // Set additional amounts if provided, converting each to cents
        tipAmount?.let { 
            amountInfo = amountInfo.setTipAmount(toCents(it))
        }
        taxAmount?.let { 
            amountInfo = amountInfo.setTaxAmount(toCents(it))
        }

        val request = PaymentRequest("FORCED_AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setAmount(amountInfo)
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
                Log.d(TAG, "FORCED_AUTH progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "FORCED_AUTH")
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
        amount: BigDecimal,
        currency: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    ) {

        Log.d(
            TAG,
            "Executing REFUND transaction - OriginalTxnId: $originalTransactionId, Amount: $amount $currency"
        )

        // Convert dollar amount to cents for SDK
        val amountInCents = (amount * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)

        val request = if (originalTransactionId.isNotEmpty()) {
            Log.d(TAG, "Creating REFUND request with originalTransactionId: $originalTransactionId")
            PaymentRequest("REFUND")
                .setReferenceOrderId(referenceOrderId)
                .setTransactionRequestId(transactionRequestId)
                .setOriginalTransactionId(originalTransactionId)
                .setAmount(AmountInfo(amountInCents, currency))
                .setDescription(description)
        } else {
            Log.d(TAG, "Creating standalone REFUND request without originalTransactionId")
            PaymentRequest("REFUND")
                .setReferenceOrderId(referenceOrderId)
                .setTransactionRequestId(transactionRequestId)
                .setAmount(AmountInfo(amountInCents, currency))
                .setDescription(description)
        }

        // Set reason if provided
        reason?.let {
            Log.d(TAG, "Setting refund reason: $it")
            request.setReason(it)
        }

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
                Log.d(TAG, "REFUND progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "REFUND")
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
                Log.d(TAG, "VOID progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "VOID")
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
        amount: BigDecimal,
        currency: String,
        description: String,
        surchargeAmount: BigDecimal?,
        tipAmount: BigDecimal?,
        taxAmount: BigDecimal?,
        cashbackAmount: BigDecimal?,
        serviceFee: BigDecimal?,
        callback: PaymentCallback
    ) {

        Log.d(
            TAG,
            "Executing POST_AUTH transaction - OriginalTxnId: $originalTransactionId, Amount: $amount $currency"
        )

        // Convert dollar amounts to cents for SDK
        fun toCents(dollarAmount: BigDecimal): BigDecimal {
            return (dollarAmount * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        }

        // Create AmountInfo with all amounts converted to cents using builder pattern
        var amountInfo = AmountInfo(
            orderAmount = toCents(amount), 
            pricingCurrency = currency
        )

        // Set additional amounts if provided, converting each to cents
        // surchargeAmount?.let { amountInfo = amountInfo.setSurchargeAmount(toCents(it)) }
        tipAmount?.let { amountInfo = amountInfo.setTipAmount(toCents(it)) }
        taxAmount?.let { amountInfo = amountInfo.setTaxAmount(toCents(it)) }
        // cashbackAmount?.let { amountInfo = amountInfo.setCashbackAmount(toCents(it)) }
        // serviceFee?.let { amountInfo = amountInfo.setServiceFee(toCents(it)) }

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
                Log.d(TAG, "POST_AUTH progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "POST_AUTH")
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
        amount: BigDecimal,
        currency: String,
        description: String,
        callback: PaymentCallback
    ) {

        Log.d(
            TAG,
            "Executing INCREMENT_AUTH transaction - OriginalTxnId: $originalTransactionId, Amount: $amount $currency"
        )

        // Convert dollar amount to cents for SDK
        val amountInCents = (amount * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)

        val request = PaymentRequest("INCREMENT_AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setOriginalTransactionId(originalTransactionId)
            .setAmount(AmountInfo(amountInCents, currency))
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
                Log.d(TAG, "INCREMENT_AUTH progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "INCREMENT_AUTH")
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
        tipAmount: BigDecimal,
        description: String,
        callback: PaymentCallback
    ) {

        Log.d(
            TAG,
            "Executing TIP_ADJUST transaction - OriginalTxnId: $originalTransactionId, TipAmount: $tipAmount"
        )

        // Convert dollar tip amount to cents for SDK
        val tipAmountInCents = (tipAmount * BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)

        val request = PaymentRequest("TIP_ADJUST")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setOriginalTransactionId(originalTransactionId)
            .setTipAmount(tipAmountInCents)
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
                Log.d(TAG, "TIP_ADJUST progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "TIP_ADJUST")
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
                Log.d(TAG, "QUERY progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "QUERY")
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
        transactionRequestId: String,
        description: String,
        callback: PaymentCallback
    ) {

        Log.d(TAG, "Executing BATCH_CLOSE transaction")

        val request = PaymentRequest("BATCH_CLOSE")
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
                Log.d(TAG, "BATCH_CLOSE progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "BATCH_CLOSE")
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
}
