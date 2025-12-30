package com.sunmi.tapro.taplink.demo.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.NetworkUtils
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
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

    // Current connection mode (will be initialized from preferences in initialize method)
    private var currentMode: ConnectionPreferences.ConnectionMode? = null

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
     * Supports App-to-App, Cable, and LAN modes
     */
    override fun initialize(
        context: Context,
        appId: String,
        merchantId: String,
        secretKey: String
    ): Boolean {
        this.context = context
        currentMode = ConnectionPreferences.getConnectionMode(context)

        Log.d(TAG, "Initializing SDK for mode: $currentMode")

        // Read configuration from resources
        val actualAppId = context.getString(R.string.taplink_app_id)
        val actualMerchantId = context.getString(R.string.taplink_merchant_id)
        val actualSecretKey = context.getString(R.string.taplink_secret_key)

        // Validate configuration
        if (actualAppId.isBlank() || actualMerchantId.isBlank() || actualSecretKey.isBlank()) {
            Log.e(TAG, "SDK initialization failed: Missing configuration")
            return false
        }

        // Map connection mode
        val sdkConnectionMode = when (currentMode) {
            ConnectionPreferences.ConnectionMode.CABLE -> ConnectionMode.CABLE
            ConnectionPreferences.ConnectionMode.LAN -> ConnectionMode.LAN
            else -> ConnectionMode.APP_TO_APP
        }

        val config = TaplinkConfig(
            appId = actualAppId,
            merchantId = actualMerchantId,
            secretKey = actualSecretKey
        ).setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG)
            .setConnectionMode(sdkConnectionMode)

        return try {
            TaplinkSDK.init(context, config)
            Log.d(TAG, "SDK initialized successfully for mode: $currentMode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SDK initialization failed: ${e.message}", e)
            false
        }
    }

    /**
     * Connect to payment terminal based on current connection mode
     * Simplified connection logic with unified configuration handling
     */
    override fun connect(listener: ConnectionListener) {
        this.connectionListener = listener
        
        Log.d(TAG, "=== connect() called ===")
        Log.d(TAG, "ConnectionListener set: ${listener != null}")
        Log.d(TAG, "Connecting with mode: $currentMode")

        // Check network for LAN mode
        if (currentMode == ConnectionPreferences.ConnectionMode.LAN) {
            context?.let { ctx ->
                if (!NetworkUtils.isNetworkConnected(ctx)) {
                    Log.e(TAG, "Network not connected for LAN mode")
                    connectionListener?.onError(
                        "NETWORK_NOT_CONNECTED",
                        "Network is not connected. Please check your network connection and try again."
                    )
                    return
                }
            }
        }

        connecting = true

        // Create connection configuration
        val connectionConfig = createConnectionConfig()

        Log.d(TAG, "Calling TaplinkSDK.connect() with config: $connectionConfig")

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
     * Create connection configuration based on current connection mode
     * Unified configuration handling for all modes
     * 
     * Returns appropriate ConnectionConfig for LAN and Cable modes,
     * or null for App-to-App mode (uses SDK default behavior)
     *
     * @return ConnectionConfig for the current mode, or null for auto-detection
     */
    private fun createConnectionConfig(): ConnectionConfig? {
        return when (currentMode) {
            ConnectionPreferences.ConnectionMode.LAN -> createLanConfig()
            ConnectionPreferences.ConnectionMode.CABLE -> createCableConfig()
            else -> null // App-to-App uses default behavior
        }
    }

    /**
     * Create LAN connection configuration
     * Validates IP address and port from preferences
     * Throws IllegalArgumentException for invalid network parameters
     */
    private fun createLanConfig(): ConnectionConfig? {
        val ctx = context ?: return null
        val ip = ConnectionPreferences.getLanIp(ctx)
        val port = ConnectionPreferences.getLanPort(ctx)

        if (ip != null && ip.isNotEmpty()) {
            Log.d(TAG, "LAN config - IP: $ip, Port: $port")
            
            if (!NetworkUtils.isValidIpAddress(ip)) {
                throw IllegalArgumentException("Invalid IP address format: $ip")
            }
            if (!NetworkUtils.isPortValid(port)) {
                throw IllegalArgumentException("Invalid port number: $port")
            }

            return ConnectionConfig().setHost(ip).setPort(port)
        } else {
            Log.d(TAG, "No LAN IP configured, using auto-connect")
            return null
        }
    }

    /**
     * Create Cable connection configuration with protocol support
     * Supports AUTO, USB_AOA, USB_VSP, RS232 protocols
     */
    private fun createCableConfig(): ConnectionConfig? {
        val ctx = context ?: return null
        val protocol = ConnectionPreferences.getCableProtocol(ctx)
        
        Log.d(TAG, "Cable config - Protocol: $protocol")
        
        return when (protocol) {
            ConnectionPreferences.CableProtocol.AUTO -> null // Let SDK auto-detect
            ConnectionPreferences.CableProtocol.USB_AOA -> ConnectionConfig().setCableProtocol(CableProtocol.USB_AOA)
            ConnectionPreferences.CableProtocol.USB_VSP -> ConnectionConfig().setCableProtocol(CableProtocol.USB_VSP)
            ConnectionPreferences.CableProtocol.RS232 -> ConnectionConfig().setCableProtocol(CableProtocol.RS232)
        }
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

        Log.d(TAG, "Disconnected - Reason: $reason")
        connectionListener?.onDisconnected(reason)
    }

    /**
     * Handle connection error - directly forward SDK error
     */
    private fun handleConnectionError(code: String, message: String) {
        connected = false
        connectedDeviceId = null
        taproVersion = null

        Log.e(TAG, "Connection error - Code: $code, Message: $message")
        connectionListener?.onError(code, message)
    }

    /**
     * Handle payment failure - directly forward SDK error
     */
    private fun handlePaymentFailure(
        transactionType: String,
        error: SdkPaymentError,
        callback: PaymentCallback
    ) {
        Log.e(TAG, "$transactionType failed - Code: ${error.code}, Message: ${error.message}")
        callback.onFailure(error.code, error.message)
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
     * Get current connection mode
     *
     * @return Current connection mode, or APP_TO_APP as default if not initialized
     */
    fun getCurrentConnectionMode(): ConnectionPreferences.ConnectionMode {
        return currentMode ?: ConnectionPreferences.ConnectionMode.APP_TO_APP
    }

    /**
     * Connect with LAN configuration - simplified
     */
    fun connectWithLanConfig(
        ip: String,
        port: Int = 8443,
        listener: ConnectionListener
    ) {
        // Basic validation
        if (!NetworkUtils.isValidIpAddress(ip)) {
            listener.onError("INVALID_IP", "Invalid IP address format: $ip")
            return
        }
        if (!NetworkUtils.isPortValid(port)) {
            listener.onError("INVALID_PORT", "Invalid port number: $port")
            return
        }

        // Save configuration and switch mode
        context?.let { ctx ->
            ConnectionPreferences.saveLanConfig(ctx, ip, port)
            ConnectionPreferences.saveConnectionMode(ctx, ConnectionPreferences.ConnectionMode.LAN)
            currentMode = ConnectionPreferences.ConnectionMode.LAN
            Log.d(TAG, "LAN config saved: $ip:$port")
        }

        connect(listener)
    }

    /**
     * Connect with Cable mode - simplified
     */
    fun connectWithCableMode(listener: ConnectionListener) {
        context?.let { ctx ->
            ConnectionPreferences.saveConnectionMode(ctx, ConnectionPreferences.ConnectionMode.CABLE)
            currentMode = ConnectionPreferences.ConnectionMode.CABLE
            Log.d(TAG, "Switched to Cable mode")
        }
        connect(listener)
    }

    /**
     * Attempt auto-connection - simplified
     */
    fun attemptAutoConnect(listener: ConnectionListener): Boolean {
        Log.d(TAG, "Attempting auto-connect with mode: $currentMode")
        
        return when (currentMode) {
            null -> {
                listener.onError("MODE_NOT_INITIALIZED", "Connection mode not initialized")
                false
            }
            else -> {
                connect(listener)
                true
            }
        }
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
        callback: PaymentCallback
    ) {
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }

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
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }

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
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }

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
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }

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
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }

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
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }

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
        if (!connected) {
            callback.onFailure("C30", "Tapro payment terminal not connected")
            return
        }

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
                Log.d(TAG, "BATCH_CLOSE progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "BATCH_CLOSE")
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
}
