package com.sunmi.tapro.taplink.demo.service

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.util.Constants
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AbortRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthAmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.ForcedAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.IncrementalAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.PostAuthRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.RefundRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TipAdjustRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.VoidRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement.BatchCloseRequest
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
 * This service has been updated to use the new type-safe Taplink SDK API, replacing
 * the legacy unified execute() method with dedicated transaction-specific methods.
 * 
 * Key API Changes:
 * - PaymentRequest("ACTION") -> Dedicated request objects (SaleRequest, AuthRequest, etc.)
 * - TaplinkSDK.execute() -> TaplinkSDK.getClient().transactionMethod()
 * - Improved type safety and parameter validation
 * - Better error handling and debugging capabilities
 * 
 * Supports App-to-App, Cable, and LAN connection modes
 * Implements PaymentService interface, encapsulates Taplink SDK calling logic
 * Maintains full backward compatibility at the interface level
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
     * Get TaplinkClient instance for new API calls
     * 
     * This method provides access to the new type-safe transaction methods introduced
     * in the updated SDK. The TaplinkClient offers dedicated methods for each transaction
     * type (sale, auth, refund, etc.) replacing the legacy unified execute() approach.
     * 
     * Benefits of the new API:
     * - Type safety: Each transaction type has its own request object
     * - Better validation: Request objects validate parameters at compile time
     * - Cleaner code: Dedicated methods eliminate action string constants
     * - Improved maintainability: Clear separation of transaction types
     * 
     * @return TaplinkClient instance for executing type-safe transactions
     */
    private fun getClient(): com.sunmi.tapro.taplink.sdk.TaplinkClient {
        return TaplinkSDK.getClient()
    }

    /**
     * Build AmountInfo object with proper dollar-to-cents conversion
     * 
     * This method handles all amount conversions consistently across all transaction types
     * in the new API structure. It ensures proper formatting and conversion of monetary
     * values from user-friendly dollar amounts to SDK-required cent amounts.
     * 
     * Key Features:
     * - Consistent dollar-to-cents conversion using proper rounding
     * - Support for all additional amount types (surcharge, tip, tax, cashback, service fee)
     * - Null-safe handling of optional amounts
     * - Centralized amount processing for all transaction types
     * 
     * This method is used by all new API transaction methods to ensure consistent
     * amount handling across SALE, REFUND, POST_AUTH, and other transaction types.
     * 
     * @param amount Main transaction amount in dollars
     * @param currency Currency code (e.g., "USD")
     * @param surchargeAmount Optional surcharge amount in dollars
     * @param tipAmount Optional tip amount in dollars
     * @param taxAmount Optional tax amount in dollars
     * @param cashbackAmount Optional cashback amount in dollars
     * @param serviceFee Optional service fee in dollars
     * @return AmountInfo object with all amounts converted to cents
     */
    private fun buildAmountInfo(
        amount: BigDecimal,
        currency: String,
        surchargeAmount: BigDecimal? = null,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null
    ): AmountInfo {
        // Convert dollar amounts to cents for SDK
        fun toCents(dollarAmount: BigDecimal): BigDecimal {
            return (dollarAmount * BigDecimal(Constants.CENTS_TO_DOLLARS_MULTIPLIER)).setScale(0, RoundingMode.HALF_UP)
        }
        
        // Create base AmountInfo with main amount
        var amountInfo = AmountInfo(
            orderAmount = toCents(amount),
            pricingCurrency = currency
        )
        
        // Set additional amounts if provided, converting each to cents
        surchargeAmount?.let { amountInfo = amountInfo.setSurchargeAmount(toCents(it)) }
        tipAmount?.let { amountInfo = amountInfo.setTipAmount(toCents(it)) }
        taxAmount?.let { amountInfo = amountInfo.setTaxAmount(toCents(it)) }
        cashbackAmount?.let { amountInfo = amountInfo.setCashbackAmount(toCents(it)) }
        serviceFee?.let { amountInfo = amountInfo.setServiceFee(toCents(it)) }
        
        return amountInfo
    }

    /**
     * Build AuthAmountInfo object with proper dollar-to-cents conversion for AUTH transactions
     * 
     * This method creates AuthAmountInfo objects specifically for AUTH-type transactions
     * (AUTH, FORCED_AUTH, INCREMENT_AUTH) in the new API structure. AuthAmountInfo is
     * a specialized amount object that provides the specific fields required for
     * authorization transactions.
     * 
     * Key Differences from AmountInfo:
     * - Designed specifically for authorization transactions
     * - Simplified structure focused on core authorization amounts
     * - Used by AUTH, FORCED_AUTH, and INCREMENT_AUTH transaction types
     * 
     * This method ensures consistent amount handling across all authorization-type
     * transactions in the new API.
     * 
     * @param amount Main transaction amount in dollars
     * @param currency Currency code (e.g., "USD")
     * @return AuthAmountInfo object with amount converted to cents
     */
    private fun buildAuthAmountInfo(
        amount: BigDecimal,
        currency: String
    ): AuthAmountInfo {
        // Convert dollar amounts to cents for SDK
        fun toCents(dollarAmount: BigDecimal): BigDecimal {
            return (dollarAmount * BigDecimal(Constants.CENTS_TO_DOLLARS_MULTIPLIER)).setScale(0, RoundingMode.HALF_UP)
        }
        
        return AuthAmountInfo(
            orderAmount = toCents(amount),
            pricingCurrency = currency
        )
    }

    /**
     * Get progress message from SDK event - directly use SDK message
     */
    private fun getProgressMessage(event: SdkPaymentEvent, transactionType: String): String {
        return event.eventMsg.takeIf { it.isNotBlank() }
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
            Log.e(TAG, Constants.SdkMessages.SDK_INITIALIZATION_FAILED_MISSING_CONFIG)
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
            Log.d(TAG, Constants.SdkMessages.SDK_INITIALIZED_SUCCESSFULLY)
            true
        } catch (e: Exception) {
            Log.e(TAG, String.format(Constants.SdkMessages.SDK_INITIALIZATION_FAILED, e.message), e)
            false
        }
    }

    /**
     * Connect to payment terminal with comprehensive connection management
     * 
     * This method implements the core connection logic for the Taplink SDK with
     * intelligent state management:
     * 
     * 1. Connection State Handling:
     *    - If already connected: Register new listener and notify immediately
     *    - If connecting: Update listener without starting new connection
     *    - If disconnected: Initiate new connection attempt
     * 
     * 2. Listener Management:
     *    - Always updates the connection listener reference for status updates
     *    - Provides immediate callback for already-connected scenarios
     *    - Ensures UI components receive real-time connection status
     * 
     * 3. SDK Integration:
     *    - Uses provided ConnectionConfig with pre-configured connection mode
     *    - Handles all SDK callback scenarios (success, failure, disconnection)
     *    - Maintains internal state consistency with SDK state
     * 
     * This approach ensures optimal user experience by avoiding unnecessary
     * reconnection attempts while maintaining robust status monitoring.
     */
    override fun connect(connectionConfig: ConnectionConfig, listener: ConnectionListener) {
        this.connectionListener = listener
        
        Log.d(TAG, "=== connect() called ===")
        Log.d(TAG, "Current connection status: connected=$connected, connecting=$connecting")
        Log.d(TAG, "Connecting with provided ConnectionConfig: $connectionConfig")

        // Handle already-connected scenario: register new listener and provide immediate feedback
        // This is important for UI components that need current connection status
        if (connected) {
            Log.d(TAG, "Already connected, but registering new listener")
            // Immediately notify the new listener of current connection status
            // This ensures UI components get updated even when connection is already established
            connectedDeviceId?.let { deviceId ->
                taproVersion?.let { version ->
                    listener.onConnected(deviceId, version)
                }
            }
            return
        }

        // Prevent multiple simultaneous connection attempts which could cause conflicts
        if (connecting) {
            Log.d(TAG, "Already connecting, just updating listener")
            return
        }

        connecting = true

        Log.d(TAG, "Calling TaplinkSDK.connect() with provided config: $connectionConfig")

        // Initiate SDK connection with comprehensive callback handling
        // The SDK will handle the actual connection establishment based on the provided config
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
            Constants.ConnectionErrorCodes.TARGET_APP_CRASHED -> true
            Constants.ConnectionErrorCodes.CONNECTION_TIMEOUT -> true
            Constants.ConnectionErrorCodes.CONNECTION_FAILED -> true
            Constants.ConnectionErrorCodes.CONNECTION_LOST -> true
            Constants.ConnectionErrorCodes.SERVICE_DISCONNECTED -> true
            Constants.ConnectionErrorCodes.SERVICE_BINDING_FAILED -> true
            else -> code.startsWith("C")  // Most C-codes are connection related
        }
    }

    /**
     * Handle payment result with comprehensive amount conversion and status determination
     * 
     * This method performs critical business logic for payment result processing:
     * 
     * 1. Amount Conversion:
     *    - SDK returns amounts in cents (integer values)
     *    - UI expects amounts in dollars (decimal values)
     *    - Conversion uses proper rounding to avoid precision errors
     * 
     * 2. Success Determination:
     *    - Checks both transaction result code and status
     *    - Handles multiple success indicators from different SDK versions
     *    - Ensures consistent success/failure classification
     * 
     * 3. Data Mapping:
     *    - Converts SDK result format to internal PaymentResult format
     *    - Preserves all transaction metadata for audit and display
     *    - Handles optional fields gracefully (null-safe operations)
     * 
     * This conversion layer isolates the UI from SDK-specific data formats
     * and ensures consistent behavior across different SDK versions.
     */
    private fun handlePaymentResult(sdkResult: SdkPaymentResult, callback: PaymentCallback) {
        Log.d(TAG, "Payment result - Code: ${sdkResult.code}, Status: ${sdkResult.transactionStatus}")

        // Convert cents amounts back to dollars for UI display and business logic
        // The SDK uses integer cents to avoid floating-point precision issues
        // We convert back to decimal dollars for user-friendly display
        fun toDollars(centsAmount: BigDecimal?): BigDecimal? {
            return centsAmount?.divide(BigDecimal(Constants.CENTS_TO_DOLLARS_MULTIPLIER), Constants.AMOUNT_DECIMAL_PLACES, RoundingMode.HALF_UP)
        }

        // Determine transaction success using multiple criteria for robustness
        // Different SDK versions may use different success indicators
        val isSuccessful = sdkResult.transactionResultCode == Constants.TransactionResultCodes.SUCCESS ||
                          sdkResult.transactionStatus == Constants.TransactionStatus.SUCCESS

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
     * Execute SALE transaction using new sale API
     * 
     * This method has been updated to use the new type-safe SaleRequest API
     * instead of the legacy PaymentRequest("SALE") approach. The new API provides:
     * - Better type safety with dedicated SaleRequest object
     * - Cleaner parameter handling through structured request objects
     * - Consistent error handling across all transaction types
     * 
     * API Migration: PaymentRequest("SALE") + TaplinkSDK.execute() -> SaleRequest + TaplinkSDK.getClient().sale()
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

        // Build AmountInfo using the existing buildAmountInfo method
        val amountInfo = buildAmountInfo(
            amount = amount,
            currency = currency,
            surchargeAmount = surchargeAmount,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee
        )

        // Create SaleRequest using the new API structure
        val saleRequest = SaleRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = amountInfo,
            description = description
        )

        Log.d(TAG, "=== SALE Request (New API) ===")
        Log.d(TAG, "SaleRequest: $saleRequest")

        // Use new sale API
        getClient().sale(saleRequest, object : SdkPaymentCallback {
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
     * Execute AUTH transaction using new auth API
     * 
     * This method has been updated to use the new type-safe AuthRequest API
     * instead of the legacy PaymentRequest("AUTH") approach. Key changes:
     * - Uses AuthAmountInfo instead of generic AmountInfo for AUTH-specific requirements
     * - Dedicated AuthRequest object provides better parameter validation
     * - Direct API call through TaplinkSDK.getClient().auth() for improved performance
     * 
     * API Migration: PaymentRequest("AUTH") + TaplinkSDK.execute() -> AuthRequest + TaplinkSDK.getClient().auth()
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

        // Build AuthAmountInfo using the new method
        val authAmountInfo = buildAuthAmountInfo(
            amount = amount,
            currency = currency
        )

        // Create AuthRequest using the new API structure
        val authRequest = AuthRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = authAmountInfo,
            description = description
        )

        Log.d(TAG, "=== AUTH Request (New API) ===")
        Log.d(TAG, "AuthRequest: $authRequest")

        // Use new auth API
        getClient().auth(authRequest, object : SdkPaymentCallback {
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
     * Execute FORCED_AUTH transaction using new forcedAuth API
     * 
     * This method has been updated to use the new type-safe ForcedAuthRequest API
     * instead of the legacy PaymentRequest("FORCED_AUTH") approach. The new implementation:
     * - Uses AuthAmountInfo for consistent amount handling with other AUTH operations
     * - Provides dedicated ForcedAuthRequest object for better type safety
     * - Maintains compatibility with existing tip and tax amount parameters
     * 
     * API Migration: PaymentRequest("FORCED_AUTH") + TaplinkSDK.execute() -> ForcedAuthRequest + TaplinkSDK.getClient().forcedAuth()
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
            "Executing FORCED_AUTH transaction - OrderId: $referenceOrderId, Amount: $amount $currency"
        )

        // Build AuthAmountInfo for ForcedAuth (similar to Auth transaction)
        val authAmountInfo = buildAuthAmountInfo(
            amount = amount,
            currency = currency
        )

        // Create ForcedAuthRequest directly in the method using the new API structure
        val forcedAuthRequest = ForcedAuthRequest(
            referenceOrderId = referenceOrderId,
            transactionRequestId = transactionRequestId,
            amount = authAmountInfo,
            description = description
        )

        Log.d(TAG, "=== FORCED_AUTH Request (New API) ===")
        Log.d(TAG, "ForcedAuthRequest: $forcedAuthRequest")

        // Use new forcedAuth API
        getClient().forcedAuth(forcedAuthRequest, object : SdkPaymentCallback {
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
     * Execute REFUND transaction using new refund API
     * 
     * This method has been updated to use the new type-safe RefundRequest API
     * instead of the legacy PaymentRequest("REFUND") approach. Key improvements:
     * - Dedicated RefundRequest object with proper field validation
     * - Flexible original transaction reference (by ID or order ID)
     * - Note: The 'reason' parameter is logged but not supported in the new API structure
     * 
     * API Migration: PaymentRequest("REFUND") + TaplinkSDK.execute() -> RefundRequest + TaplinkSDK.getClient().refund()
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

        // Incorporate reason into description field when provided
        val finalDescription = if (reason != null) {
            if (description.isNotEmpty()) "$description (Reason: $reason)" else "Reason: $reason"
        } else {
            description
        }

        // Build AmountInfo using the existing buildAmountInfo method
        val amountInfo = buildAmountInfo(
            amount = amount,
            currency = currency
        )

        // Create RefundRequest
        val refundRequest = RefundRequest(
            transactionRequestId = transactionRequestId,
            amount = amountInfo,
            description = finalDescription,
            originalTransactionId = originalTransactionId.takeIf { it.isNotEmpty() },
            referenceOrderId = referenceOrderId.takeIf { originalTransactionId.isEmpty() }
        )

        Log.d(TAG, "=== REFUND Request (New API) ===")
        Log.d(TAG, "RefundRequest: $refundRequest")

        // Use new refund API
        getClient().refund(refundRequest, object : SdkPaymentCallback {
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
     * Execute VOID transaction using new void API
     * 
     * This method has been updated to use the new type-safe VoidRequest API
     * instead of the legacy PaymentRequest("VOID") approach. The new implementation:
     * - Uses dedicated VoidRequest object for better parameter validation
     * - Supports both originalTransactionId and originalTransactionRequestId references
     * - Incorporates reason into description field when provided
     * 
     * API Migration: PaymentRequest("VOID") + TaplinkSDK.execute() -> VoidRequest + TaplinkSDK.getClient().void()
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

        // Note: reason parameter is not directly supported in VoidRequest, but can be included in description
        val finalDescription = if (reason != null) {
            if (description.isNotEmpty()) "$description (Reason: $reason)" else "Reason: $reason"
        } else {
            description
        }

        // Create VoidRequest directly in the method using the correct structure
        // VoidRequest requires either originalTransactionId or originalTransactionRequestId
        val voidRequest = VoidRequest(
            originalTransactionId = originalTransactionId.takeIf { it.isNotEmpty() },
            originalTransactionRequestId = null,
            transactionRequestId = transactionRequestId,
            description = finalDescription.takeIf { it.isNotEmpty() },
            attach = null, // Optional field, not provided in current interface
            notifyUrl = null // Optional field, not provided in current interface
        )

        Log.d(TAG, "=== VOID Request (New API) ===")
        Log.d(TAG, "VoidRequest: $voidRequest")

        // Use new void API
        getClient().void(voidRequest, object : SdkPaymentCallback {
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
     * Execute POST_AUTH transaction using new postAuth API
     * 
     * This method has been updated to use the new type-safe PostAuthRequest API
     * instead of the legacy PaymentRequest("POST_AUTH") approach. Key features:
     * - Dedicated PostAuthRequest object with proper field structure
     * - Full support for additional amounts (surcharge, tip, tax, cashback, service fee)
     * - Direct reference to original transaction ID for completion processing
     * 
     * API Migration: PaymentRequest("POST_AUTH") + TaplinkSDK.execute() -> PostAuthRequest + TaplinkSDK.getClient().postAuth()
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

        // Build AmountInfo using the existing buildAmountInfo method
        val amountInfo = buildAmountInfo(
            amount = amount,
            currency = currency,
            surchargeAmount = surchargeAmount,
            tipAmount = tipAmount,
            taxAmount = taxAmount,
            cashbackAmount = cashbackAmount,
            serviceFee = serviceFee
        )

        // Create PostAuthRequest directly in the method using the new API structure
        // Note: PostAuthRequest doesn't have referenceOrderId field, only originalTransactionId and transactionRequestId
        val postAuthRequest = PostAuthRequest(
            originalTransactionId = originalTransactionId,
            transactionRequestId = transactionRequestId,
            amount = amountInfo,
            description = description
        )

        Log.d(TAG, "=== POST_AUTH Request (New API) ===")
        Log.d(TAG, "PostAuthRequest: $postAuthRequest")

        // Use new postAuth API
        getClient().postAuth(postAuthRequest, object : SdkPaymentCallback {
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
     * Execute INCREMENT_AUTH transaction using new incrementalAuth API
     * 
     * This method has been updated to use the new type-safe IncrementalAuthRequest API
     * instead of the legacy PaymentRequest("INCREMENT_AUTH") approach. The new implementation:
     * - Uses AuthAmountInfo for consistent amount handling with other AUTH operations
     * - Dedicated IncrementalAuthRequest object for better type safety
     * - Direct reference to original transaction for incremental authorization
     * 
     * API Migration: PaymentRequest("INCREMENT_AUTH") + TaplinkSDK.execute() -> IncrementalAuthRequest + TaplinkSDK.getClient().incrementalAuth()
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

        // Build AuthAmountInfo using the existing buildAuthAmountInfo method (similar to executeAuth)
        val authAmountInfo = buildAuthAmountInfo(
            amount = amount,
            currency = currency
        )

        // Create IncrementalAuthRequest directly in the method using the new API structure
        // Note: IncrementalAuthRequest doesn't have referenceOrderId field, only originalTransactionId and transactionRequestId
        val incrementalAuthRequest = IncrementalAuthRequest(
            originalTransactionId = originalTransactionId,
            transactionRequestId = transactionRequestId,
            amount = authAmountInfo,
            description = description
        )

        Log.d(TAG, "=== INCREMENT_AUTH Request (New API) ===")
        Log.d(TAG, "IncrementalAuthRequest: $incrementalAuthRequest")

        // Use new incrementalAuth API
        getClient().incrementalAuth(incrementalAuthRequest, object : SdkPaymentCallback {
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
     * Execute TIP_ADJUST transaction using new tipAdjust API
     * 
     * This method has been updated to use the new type-safe TipAdjustRequest API
     * instead of the legacy PaymentRequest("TIP_ADJUST") approach. Key changes:
     * - Dedicated TipAdjustRequest object with tip-specific field validation
     * - Proper dollar-to-cents conversion for tip amounts
     * - Description parameter mapped to attach field for additional context
     * 
     * API Migration: PaymentRequest("TIP_ADJUST") + TaplinkSDK.execute() -> TipAdjustRequest + TaplinkSDK.getClient().tipAdjust()
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
        val tipAmountInCents = (tipAmount * BigDecimal(Constants.CENTS_TO_DOLLARS_MULTIPLIER)).setScale(0, RoundingMode.HALF_UP)

        // Create TipAdjustRequest directly in the method using the new API structure
        // Based on the actual TipAdjustRequest structure, it uses originalTransactionId and tipAmount
        // The description parameter is not directly supported, but can be passed as attach
        val tipAdjustRequest = TipAdjustRequest(
            originalTransactionId = originalTransactionId.takeIf { it.isNotEmpty() },
            originalTransactionRequestId = transactionRequestId.takeIf { originalTransactionId.isEmpty() },
            tipAmount = tipAmountInCents,
            attach = description.takeIf { it.isNotEmpty() }
        )

        Log.d(TAG, "=== TIP_ADJUST Request (New API) ===")
        Log.d(TAG, "TipAdjustRequest: $tipAdjustRequest")

        // Use new tipAdjust API
        getClient().tipAdjust(tipAdjustRequest, object : SdkPaymentCallback {
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
     * Execute QUERY transaction using new query API - using transaction request ID
     */
    override fun executeQuery(
        transactionRequestId: String,
        callback: PaymentCallback
    ) {
        Log.d(TAG, "Executing QUERY transaction - TransactionRequestId: $transactionRequestId")

        // Create QueryRequest using the actual API structure with chain calls
        val queryRequest = QueryRequest()
            .setTransactionRequestId(transactionRequestId)

        Log.d(TAG, "=== QUERY Request (New API - by RequestId) ===")
        Log.d(TAG, "QueryRequest: $queryRequest")

        // Use new query API
        getClient().query(queryRequest, object : SdkPaymentCallback {
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
     * Execute QUERY transaction using new query API - using transaction ID
     */
    override fun executeQueryByTransactionId(
        transactionId: String,
        callback: PaymentCallback
    ) {
        Log.d(TAG, "Executing QUERY transaction - TransactionId: $transactionId")

        // Create QueryRequest using the actual API structure with chain calls
        val queryRequest = QueryRequest()
            .setTransactionId(transactionId)

        Log.d(TAG, "=== QUERY Request (New API - by TransactionId) ===")
        Log.d(TAG, "QueryRequest: $queryRequest")

        // Use new query API
        getClient().query(queryRequest, object : SdkPaymentCallback {
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
     * Execute BATCH_CLOSE transaction using new batchClose API
     */
    override fun executeBatchClose(
        transactionRequestId: String,
        description: String,
        callback: PaymentCallback
    ) {

        Log.d(TAG, "Executing BATCH_CLOSE transaction")

        // Create BatchCloseRequest using the new API structure
        val batchCloseRequest = BatchCloseRequest(
            transactionRequestId = transactionRequestId,
            description = description
        )

        Log.d(TAG, "=== BATCH_CLOSE Request (New API) ===")
        Log.d(TAG, "BatchCloseRequest: $batchCloseRequest")

        // Use new batchClose API
        getClient().batchClose(batchCloseRequest, object : SdkPaymentCallback {
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

    /**
     * Execute ABORT transaction using new abort API
     * 
     * This method provides the ability to abort/cancel an ongoing transaction.
     * It uses the new AbortRequest API for type-safe transaction cancellation.
     */
    override fun executeAbort(
        originalTransactionId: String?,
        originalTransactionRequestId: String?,
        description: String?,
        callback: PaymentCallback
    ) {

        Log.d(TAG, "Executing ABORT transaction - OriginalTxnId: $originalTransactionId, OriginalRequestId: $originalTransactionRequestId")

        // Create AbortRequest using the new API structure
        // Based on the actual AbortRequest structure, it uses description field for the abort reason
        val abortRequest = AbortRequest(
            originalTransactionId = originalTransactionId,
            originalTransactionRequestId = originalTransactionRequestId,
            description = description,
            attach = null // Optional attach field, not used in this implementation
        )

        Log.d(TAG, "=== ABORT Request (New API) ===")
        Log.d(TAG, "AbortRequest: $abortRequest")

        // Use new abort API
        getClient().abort(abortRequest, object : SdkPaymentCallback {
            override fun onSuccess(result: SdkPaymentResult) {
                handlePaymentResult(result, callback)
            }

            override fun onFailure(error: SdkPaymentError) {
                handlePaymentFailure("ABORT", error, callback)
            }

            override fun onProgress(event: SdkPaymentEvent) {
                Log.d(TAG, "ABORT progress: ${event.eventMsg}")
                val progressMessage = getProgressMessage(event, "ABORT")
                callback.onProgress("PROCESSING", progressMessage)
            }
        })
    }
}
