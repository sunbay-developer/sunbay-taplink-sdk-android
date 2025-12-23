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
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
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
     * Generate user-friendly progress message from SDK event
     * Prioritizes SDK-provided eventMsg over eventCode-based mapping
     */
    private fun getProgressMessage(event: SdkPaymentEvent, transactionType: String): String {
        val eventStr = event.eventMsg
        val eventMsg = event.eventMsg
        
        return when {
            // If SDK provides eventMsg, use it directly for better accuracy
            !eventMsg.isNullOrBlank() -> eventMsg
            
            // Otherwise use eventCode-based mapping
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
            else -> "$transactionType transaction processing..."
        }
    }

    /**
     * Re-initialize SDK for mode switching
     * This method should be called when switching between connection modes
     *
     * @param context Android Context
     * @param newMode New connection mode
     * @return Initialization result
     */
    fun reinitializeForMode(
        context: Context,
        newMode: ConnectionPreferences.ConnectionMode
    ): Boolean {
        Log.d(TAG, "=== SDK Re-initialization for Mode Switch ===")
        Log.d(TAG, "Current Mode: $currentMode")
        Log.d(TAG, "New Mode: $newMode")

        // Disconnect current connection if any
        if (connected || connecting) {
            Log.d(TAG, "Disconnecting current connection before re-initialization")
            disconnect()
        }

        // Save new mode to preferences
        ConnectionPreferences.saveConnectionMode(context, newMode)

        // Re-initialize with new mode
        return initialize(context, "", "", "")
    }

    /**
     * Initialize SDK with connection mode detection
     * Supports App-to-App, Cable, and LAN modes
     * Re-initializes SDK when switching between modes
     */
    override fun initialize(
        context: Context,
        appId: String,
        merchantId: String,
        secretKey: String
    ): Boolean {
        this.context = context

        // Get current connection mode from preferences (this is the authoritative source)
        currentMode = ConnectionPreferences.getConnectionMode(context)

        Log.d(TAG, "=== SDK Initialize Called ===")
        Log.d(TAG, "Current connection mode: $currentMode")

        // Read configuration parameters from resources (maintain consistency with existing approach)
        val actualAppId = context.getString(R.string.taplink_app_id)
        val actualMerchantId = context.getString(R.string.taplink_merchant_id)
        val actualSecretKey = context.getString(R.string.taplink_secret_key)

        Log.d(TAG, "=== SDK Init Parameters ===")
        Log.d(TAG, "App ID: $actualAppId")
        Log.d(TAG, "Merchant ID: $actualMerchantId")
        Log.d(TAG, "Secret Key: ${actualSecretKey.take(4)}****${actualSecretKey.takeLast(4)}")
        Log.d(TAG, "Connection Mode: $currentMode")

        // Validate configuration parameters
        if (actualAppId.isBlank() || actualMerchantId.isBlank() || actualSecretKey.isBlank()) {
            Log.e(TAG, "=== SDK Initialization Failed: Missing Configuration ===")
            Log.e(TAG, "App ID empty: ${actualAppId.isBlank()}")
            Log.e(TAG, "Merchant ID empty: ${actualMerchantId.isBlank()}")
            Log.e(TAG, "Secret Key empty: ${actualSecretKey.isBlank()}")
            return false
        }

        // Create SDK configuration based on connection mode (supports APP_TO_APP, CABLE, LAN)
        val sdkConnectionMode = when (currentMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                Log.d(TAG, "Configuring SDK for App-to-App mode")
                ConnectionMode.APP_TO_APP
            }

            ConnectionPreferences.ConnectionMode.CABLE -> {
                Log.d(TAG, "Configuring SDK for Cable mode")
                ConnectionMode.CABLE
            }

            ConnectionPreferences.ConnectionMode.LAN -> {
                Log.d(TAG, "Configuring SDK for LAN mode")
                ConnectionMode.LAN
            }

            else -> {
                Log.w(
                    TAG,
                    "Unsupported or uninitialized connection mode: $currentMode, using APP_TO_APP as default"
                )
                ConnectionMode.APP_TO_APP
            }
        }

        val config = TaplinkConfig(
            appId = actualAppId,
            merchantId = actualMerchantId,
            secretKey = actualSecretKey
        ).setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG)
            .setConnectionMode(sdkConnectionMode)

        return try {
            Log.d(TAG, "=== Calling TaplinkSDK.init() ===")
            TaplinkSDK.init(context, config)
            Log.d(TAG, "=== SDK Initialization Success ===")
            Log.d(TAG, "Mode: $currentMode")
            Log.d(TAG, "SDK ready for connection")
            true
        } catch (e: Exception) {
            Log.e(TAG, "=== SDK Initialization Failed ===")
            Log.e(TAG, "Mode: $currentMode")
            Log.e(TAG, "Error: ${e.message}", e)
            false
        }
    }

    /**
     * Connect to payment terminal based on current connection mode
     * Creates appropriate ConnectionConfig based on mode (App-to-App, Cable, LAN)
     * Supports first-time connection and subsequent auto-connection logic
     */
    override fun connect(listener: ConnectionListener) {
        this.connectionListener = listener

        Log.d(TAG, "=== Taplink SDK Connection Started ===")
        Log.d(TAG, "Connection Mode: $currentMode")

        // For LAN mode, check network connectivity first
        if (currentMode == ConnectionPreferences.ConnectionMode.LAN) {
            context?.let { ctx ->
                if (!NetworkUtils.isNetworkConnected(ctx)) {
                    Log.e(TAG, "Network not connected for LAN mode")
                    val networkType = NetworkUtils.getNetworkType(ctx)
                    Log.e(TAG, "Current network type: $networkType")
                    connectionListener?.onError(
                        "NETWORK_NOT_CONNECTED",
                        "Network is not connected. Please check your network connection and try again."
                    )
                    return
                }
                
                val networkType = NetworkUtils.getNetworkType(ctx)
                Log.d(TAG, "Network connected for LAN mode, type: $networkType")
            } ?: run {
                Log.e(TAG, "Context is null, cannot check network status")
                connectionListener?.onError(
                    "NO_CONTEXT",
                    "Cannot check network status"
                )
                return
            }
        }

        // Set connecting status
        connecting = true

        // Create connection configuration based on mode
        val connectionConfig = try {
            createConnectionConfig()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create connection config", e)
            connectionListener?.onError(
                "CONFIG_ERROR",
                "Failed to create connection configuration: ${e.message}"
            )
            connecting = false
            return
        }

        // For LAN mode, validate configuration requirements
        if (currentMode == ConnectionPreferences.ConnectionMode.LAN) {
            context?.let { ctx ->
                val ip = ConnectionPreferences.getLanIp(ctx)
                if (connectionConfig == null && (ip == null || ip.isEmpty())) {
                    Log.e(TAG, "LAN mode requires IP configuration")
                    connectionListener?.onError(
                        "LAN_CONFIG_REQUIRED",
                        "LAN mode requires IP address and port configuration"
                    )
                    connecting = false
                    return
                }
            }
        }

        Log.d(TAG, "=== Connection Request Parameters ===")
        Log.d(TAG, "Connection Config: $connectionConfig")

        Log.d(TAG, "=== Calling TaplinkSDK.connect() ===")

        // Call SDK connect method with appropriate configuration
        TaplinkSDK.connect(connectionConfig, object : SdkConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.d(TAG, "=== SDK Connection Callback: onConnected ===")
                Log.d(TAG, "Device ID: $deviceId")
                Log.d(TAG, "Tapro Version: $taproVersion")
                Log.d(TAG, "Connection Mode: $currentMode")
                // Reset connecting status
                connecting = false
                handleConnected(deviceId, taproVersion)
            }

            override fun onDisconnected(reason: String) {
                Log.d(TAG, "=== SDK Connection Callback: onDisconnected ===")
                Log.d(TAG, "Disconnect Reason: $reason")
                Log.d(TAG, "Connection Mode: $currentMode")
                // Reset connecting status
                connecting = false
                handleDisconnected(reason)
            }

            override fun onError(error: SdkConnectionError) {
                Log.e(TAG, "=== SDK Connection Callback: onError ===")
                Log.e(TAG, "Error Code: ${error.code}")
                Log.e(TAG, "Error Message: ${error.message}")
                Log.e(TAG, "Connection Mode: $currentMode")
                Log.e(TAG, "Full Error Object: $error")
                // Reset connecting status
                connecting = false
                handleConnectionError(error.code, error.message)
            }
        })

        Log.d(TAG, "=== TaplinkSDK.connect() Called Successfully ===")
        Log.d(TAG, "Connection Mode: $currentMode")
        Log.d(TAG, "Waiting for connection callbacks...")
    }

    /**
     * Create connection configuration based on current connection mode
     * Handles first-time connection and auto-connection logic
     *
     * @return ConnectionConfig for the current mode, or null for auto-detection/default behavior
     */
    private fun createConnectionConfig(): ConnectionConfig? {
        return when (currentMode) {
            ConnectionPreferences.ConnectionMode.LAN -> {
                createLanConnectionConfig()
            }

            ConnectionPreferences.ConnectionMode.CABLE -> {
                createCableConnectionConfig()
            }

            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                createAppToAppConnectionConfig()
            }

            null -> {
                Log.w(TAG, "Connection mode not initialized, using APP_TO_APP as default")
                createAppToAppConnectionConfig()
            }
        }
    }

    /**
     * Create LAN connection configuration
     *
     * Creates actual ConnectionConfig object with IP and port for LAN mode
     * Falls back to null for auto-connection if no configuration is available
     *
     * @return ConnectionConfig with IP/port or null for auto-connection
     */
    private fun createLanConnectionConfig(): ConnectionConfig? {
        val ctx = context ?: return null

        val ip = ConnectionPreferences.getLanIp(ctx)
        val port = ConnectionPreferences.getLanPort(ctx)

        if (ip != null && ip.isNotEmpty()) {
            // Validate configuration parameters
            Log.d(TAG, "=== LAN Connection Config (Creating) ===")
            Log.d(TAG, "IP: $ip")
            Log.d(TAG, "Port: $port")

            // Validate IP address format
            if (!NetworkUtils.isValidIpAddress(ip)) {
                Log.e(TAG, "Invalid IP address format: $ip")
                throw IllegalArgumentException("Invalid IP address format: $ip")
            }

            // Validate port range
            if (!NetworkUtils.isPortValid(port)) {
                Log.e(TAG, "Invalid port number: $port")
                throw IllegalArgumentException("Invalid port number: $port")
            }

            Log.d(TAG, "LAN configuration validated successfully")

            val connectionConfig =
                ConnectionConfig()
                    .setHost(ip)
                    .setPort(port)

            Log.d(TAG, "Created ConnectionConfig with host=$ip, port=$port")
            return connectionConfig
        } else {
            Log.d(TAG, "=== LAN Auto-Connect (using SDK cached device info) ===")
            Log.d(TAG, "No explicit IP configuration, attempting auto-connect")
            return null
        }
    }

    /**
     * Create Cable connection configuration
     * Uses auto-detection for cable type and protocol
     *
     * @return null to let SDK auto-detect cable type and protocol
     */
    private fun createCableConnectionConfig(): ConnectionConfig? {
        Log.d(TAG, "=== Cable Connection Config ===")
        Log.d(TAG, "Auto-detect cable type and protocol")
        Log.d(TAG, "Supported protocols: USB AOA, USB VSP, RS232")

        // For Cable mode, pass null to let SDK auto-detect cable type and protocol
        // SDK will automatically detect:
        // - USB AOA (Android Open Accessory)
        // - USB VSP (Virtual Serial Port)  
        // - RS232 serial connection
        return null
    }

    /**
     * Create App-to-App connection configuration
     * Uses default behavior for same-device communication
     *
     * @return null for default App-to-App behavior
     */
    private fun createAppToAppConnectionConfig(): ConnectionConfig? {
        Log.d(TAG, "=== App-to-App Connection Config ===")
        Log.d(TAG, "Using default same-device IPC communication")

        // For App-to-App mode, pass null for default behavior
        return null
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
     * Handle connection error with enhanced error mapping
     */
    private fun handleConnectionError(code: String, message: String) {
        connected = false
        connectedDeviceId = null
        taproVersion = null

        Log.e(TAG, "=== Connection Error ===")
        Log.e(TAG, "Error Code: $code")
        Log.e(TAG, "Error Message: $message")
        Log.e(TAG, "Connection Mode: $currentMode")
        Log.e(TAG, "Connection Status: FAILED")
        Log.e(TAG, "Device ID: Cleared")
        Log.e(TAG, "Tapro Version: Cleared")

        // Map error codes to user-friendly messages based on connection mode
        val (mappedCode, mappedMessage) = mapConnectionError(code, message)

        connectionListener?.onError(mappedCode, mappedMessage)
    }

    /**
     * Pass through SDK error codes and messages without additional mapping
     * SDK already provides appropriate error codes and user-friendly messages
     *
     * @param code Original error code from SDK
     * @param message Original error message from SDK
     * @return Pair of original error code and message
     */
    private fun mapConnectionError(code: String, message: String): Pair<String, String> {
        Log.d(TAG, "Connection error mapping - Mode: $currentMode, Code: $code, Message: $message")

        // Return SDK error information as-is
        // SDK ConnectionError already provides appropriate error codes and user-friendly messages
        return Pair(code, message)
    }

    /**
     * Handle payment failure result, log complete error information
     */
    private fun handlePaymentFailure(
        transactionType: String,
        error: SdkPaymentError,
        callback: PaymentCallback
    ) {
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
                    transAmount = amt.transAmount,
                    orderAmount = amt.orderAmount,
                    taxAmount = amt.taxAmount,
                    serviceFee = amt.serviceFee,
                    surchargeAmount = amt.surchargeAmount,
                    tipAmount = amt.tipAmount,
                    cashbackAmount = amt.cashbackAmount
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
            tipAmount = sdkResult.tipAmount,
            totalAuthorizedAmount = sdkResult.totalAuthorizedAmount,
            merchantRefundNo = sdkResult.merchantRefundNo,
            originalTransactionId = sdkResult.originalTransactionId,
            originalTransactionRequestId = sdkResult.originalTransactionRequestId,
            batchCloseInfo = sdkResult.batchCloseInfo?.let { bci ->
                BatchCloseInfo(
                    totalCount = bci.totalCount ?: 0,
                    totalAmount = bci.totalAmount ?: BigDecimal.ZERO,
                    totalTip = bci.totalTip ?: BigDecimal.ZERO,
                    totalTax = bci.totalTax ?: BigDecimal.ZERO,
                    totalSurchargeAmount = bci.totalSurchargeAmount ?: BigDecimal.ZERO,
                    totalServiceFee = bci.totalServiceFee ?: BigDecimal.ZERO,
                    cashDiscount = bci.cashDiscount ?: BigDecimal.ZERO,
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
     * Get current connection mode
     *
     * @return Current connection mode, or APP_TO_APP as default if not initialized
     */
    fun getCurrentConnectionMode(): ConnectionPreferences.ConnectionMode {
        return currentMode ?: ConnectionPreferences.ConnectionMode.APP_TO_APP
    }

    /**
     * Connect with explicit LAN configuration
     * Used for first-time LAN connection or when changing IP/port settings
     *
     * @param ip IP address of the Tapro device
     * @param port Port number (default 8443)
     * @param listener Connection status listener
     */
    fun connectWithLanConfig(
        ip: String,
        port: Int = 8443,
        listener: ConnectionListener
    ) {
        // Check network connectivity first for LAN mode
        context?.let { ctx ->
            if (!NetworkUtils.isNetworkConnected(ctx)) {
                Log.e(TAG, "Network not connected for LAN configuration")
                val networkType = NetworkUtils.getNetworkType(ctx)
                Log.e(TAG, "Current network type: $networkType")
                listener.onError(
                    "NETWORK_NOT_CONNECTED",
                    "Network is not connected. Please check your network connection and try again."
                )
                return
            }
            
            val networkType = NetworkUtils.getNetworkType(ctx)
            Log.d(TAG, "Network connected for LAN configuration, type: $networkType")
        } ?: run {
            Log.e(TAG, "Context is null, cannot check network status")
            listener.onError(
                "NO_CONTEXT",
                "Cannot check network status"
            )
            return
        }
        
        // Validate parameters
        if (!NetworkUtils.isValidIpAddress(ip)) {
            listener.onError("INVALID_IP", "Invalid IP address format: $ip")
            return
        }

        if (!NetworkUtils.isPortValid(port)) {
            listener.onError("INVALID_PORT", "Invalid port number: $port")
            return
        }

        // Save configuration for future use
        context?.let { ctx ->
            ConnectionPreferences.saveLanConfig(ctx, ip, port)
            Log.d(TAG, "LAN configuration saved: $ip:$port")
        }

        // Set connection mode to LAN if not already
        if (currentMode != ConnectionPreferences.ConnectionMode.LAN) {
            context?.let { ctx ->
                ConnectionPreferences.saveConnectionMode(
                    ctx,
                    ConnectionPreferences.ConnectionMode.LAN
                )
                currentMode = ConnectionPreferences.ConnectionMode.LAN
                Log.d(TAG, "Connection mode switched to LAN")
            }
        }

        // Proceed with normal connection
        connect(listener)
    }

    /**
     * Connect with Cable mode
     * Uses auto-detection for cable type and protocol
     *
     * @param listener Connection status listener
     */
    fun connectWithCableMode(listener: ConnectionListener) {
        // Set connection mode to Cable if not already
        if (currentMode != ConnectionPreferences.ConnectionMode.CABLE) {
            context?.let { ctx ->
                ConnectionPreferences.saveConnectionMode(
                    ctx,
                    ConnectionPreferences.ConnectionMode.CABLE
                )
                currentMode = ConnectionPreferences.ConnectionMode.CABLE
                Log.d(TAG, "Connection mode switched to Cable")
            }
        }

        // Proceed with normal connection
        connect(listener)
    }

    /**
     * Attempt auto-connection based on saved configuration and connection mode
     * Used for application startup or reconnection scenarios
     *
     * @param listener Connection status listener
     * @return true if auto-connection attempt was started, false if configuration is incomplete
     */
    fun attemptAutoConnect(listener: ConnectionListener): Boolean {
        Log.d(TAG, "=== Attempting Auto-Connect ===")
        Log.d(TAG, "Current Mode: $currentMode")

        return when (currentMode) {
            ConnectionPreferences.ConnectionMode.LAN -> {
                context?.let { ctx ->
                    val ip = ConnectionPreferences.getLanIp(ctx)
                    val port = ConnectionPreferences.getLanPort(ctx)
                    if (ip != null && ip.isNotEmpty()) {
                        Log.d(TAG, "Auto-connecting to LAN device: $ip:$port")
                    } else {
                        Log.d(TAG, "No LAN configuration available, attempting SDK auto-connect")
                    }
                    connect(listener)
                    true
                } ?: run {
                    Log.w(TAG, "Context is null, cannot auto-connect")
                    listener.onError("NO_CONTEXT", "Context is null, cannot auto-connect")
                    false
                }
            }

            ConnectionPreferences.ConnectionMode.CABLE -> {
                Log.d(TAG, "Auto-connecting with Cable mode")
                connect(listener)
                true
            }

            ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                Log.d(TAG, "Auto-connecting with App-to-App mode")
                connect(listener)
                true
            }

            null -> {
                Log.w(TAG, "Connection mode not initialized, cannot auto-connect")
                listener.onError("MODE_NOT_INITIALIZED", "Connection mode not initialized")
                false
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

        // Create AmountInfo with all amounts
        val amountInfo = AmountInfo(
            orderAmount = amount,
            pricingCurrency = currency,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            surchargeAmount = surchargeAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee
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
                // First check if we have eventMsg from SDK, use it if available
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

        val request = PaymentRequest("AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setAmount(AmountInfo(amount, currency))
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
                // First check if we have eventMsg from SDK, use it if available
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

        // Create AmountInfo with all amounts
        val amountInfo = AmountInfo(
            orderAmount = amount,
            pricingCurrency = currency,
            tipAmount = tipAmount,
            taxAmount = taxAmount
        )

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
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "FORCED_AUTH transaction progress - Event: $eventStr")

                // Provide specific progress feedback based on event type
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

        val request = if (originalTransactionId.isNotEmpty()) {
            Log.d(TAG, "Creating REFUND request with originalTransactionId: $originalTransactionId")
            PaymentRequest("REFUND")
                .setReferenceOrderId(referenceOrderId)
                .setTransactionRequestId(transactionRequestId)
                .setOriginalTransactionId(originalTransactionId)
                .setAmount(AmountInfo(amount, currency))
                .setDescription(description)
        } else {
            Log.d(TAG, "Creating standalone REFUND request without originalTransactionId")
            PaymentRequest("REFUND")
                .setReferenceOrderId(referenceOrderId)
                .setTransactionRequestId(transactionRequestId)
                .setAmount(AmountInfo(amount, currency))
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
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "REFUND transaction progress - Event: $eventStr")

                // Provide specific progress feedback based on event type
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
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "VOID transaction progress - Event: $eventStr")

                // Provide specific progress feedback based on event type
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

        // Create AmountInfo with all amounts using builder pattern
        var amountInfo = AmountInfo(orderAmount = amount, pricingCurrency = currency)

        // Set additional amounts if provided
        // surchargeAmount?.let { amountInfo = amountInfo.setSurchargeAmount(it) }
        tipAmount?.let { amountInfo = amountInfo.setTipAmount(it) }
        taxAmount?.let { amountInfo = amountInfo.setTaxAmount(it) }
        // cashbackAmount?.let { amountInfo = amountInfo.setCashbackAmount(it) }
        // serviceFee?.let { amountInfo = amountInfo.setServiceFee(it) }

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

        val request = PaymentRequest("INCREMENT_AUTH")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setOriginalTransactionId(originalTransactionId)
            .setAmount(AmountInfo(amount, currency))
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

        val request = PaymentRequest("TIP_ADJUST")
            .setReferenceOrderId(referenceOrderId)
            .setTransactionRequestId(transactionRequestId)
            .setOriginalTransactionId(originalTransactionId)
            .setTipAmount(tipAmount)
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
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "QUERY transaction progress - Event: $eventStr")

                // Provide specific progress feedback based on event type
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
                // Convert SDK returned status code to user-friendly message
                val eventStr = event.eventMsg
                Log.d(TAG, "BATCH_CLOSE transaction progress - Event: $eventStr")

                // Provide specific progress feedback based on event type
                val progressMessage = getProgressMessage(event, "BATCH_CLOSE")

                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
}
