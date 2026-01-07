package com.sunmi.tapro.taplink.demo.service

import android.content.Context
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import java.math.BigDecimal

/**
 * Payment Service Interface
 *
 * Defines the contract for payment-related operations in the Taplink Demo application.
 * This interface abstracts the underlying SDK implementation and provides a clean API
 * for payment operations, connection management, and transaction processing.
 * 
 * The interface supports multiple connection modes (App-to-App, Cable, LAN) and
 * various transaction types (SALE, AUTH, REFUND, etc.) while maintaining consistent
 * error handling and callback patterns.
 */
interface PaymentService {

    /**
     * Initialize the payment SDK with merchant credentials
     *
     * This method performs one-time SDK initialization using the provided credentials.
     * It should be called once during application startup before any other operations.
     *
     * @param context Application context for SDK initialization
     * @param appId Unique application identifier provided by SUNMI
     * @param merchantId Merchant identifier for transaction processing
     * @param secretKey Secret key for secure communication with payment platform
     * @return true if initialization succeeds, false if it fails due to invalid credentials or other errors
     */
    fun initialize(
        context: Context,
        appId: String,
        merchantId: String,
        secretKey: String
    ): Boolean

    /**
     * Establish connection to payment terminal using specified configuration
     *
     * This method attempts to connect to the payment terminal using the provided
     * ConnectionConfig. The connection mode and parameters are determined by the
     * configuration object, allowing for flexible connection management.
     *
     * @param connectionConfig Configuration object containing connection mode and parameters
     * @param listener Callback interface for connection status updates
     */
    fun connect(connectionConfig: ConnectionConfig, listener: ConnectionListener)

    /**
     * Disconnect from the payment terminal
     * 
     * Cleanly terminates the connection to the payment terminal and releases
     * any associated resources. This should be called when the connection is
     * no longer needed or when switching connection modes.
     */
    fun disconnect()

    /**
     * Check current connection status
     *
     * @return true if currently connected to a payment terminal, false otherwise
     */
    fun isConnected(): Boolean

    /**
     * Get the identifier of the currently connected device
     *
     * @return Device identifier string, or null if not connected
     */
    fun getConnectedDeviceId(): String?

    /**
     * Get the version of the connected Tapro application
     *
     * @return Tapro version string, or null if not connected or version unavailable
     */
    fun getTaproVersion(): String?

    /**
     * Execute a SALE transaction
     *
     * Processes a complete sale transaction with optional additional amounts.
     * This is the most common transaction type for point-of-sale operations.
     *
     * @param referenceOrderId Unique order identifier from merchant system
     * @param transactionRequestId Unique transaction request identifier for idempotency
     * @param amount Base transaction amount (order total)
     * @param currency Currency code (e.g., "USD", "EUR")
     * @param description Human-readable transaction description
     * @param surchargeAmount Optional surcharge amount (e.g., convenience fee)
     * @param tipAmount Optional tip amount
     * @param taxAmount Optional tax amount
     * @param cashbackAmount Optional cashback amount
     * @param serviceFee Optional service fee amount
     * @param staffInfo Optional staff information for transaction tracking
     * @param callback Callback interface for transaction result handling
     */
    fun executeSale(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        surchargeAmount: BigDecimal? = null,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        staffInfo: StaffInfo? = null,
        callback: PaymentCallback
    )

    /**
     * Execute an AUTH (pre-authorization) transaction
     *
     * Reserves funds on the customer's payment method without completing the sale.
     * The reserved amount can later be captured using POST_AUTH or released automatically.
     * Commonly used in scenarios where the final amount may change (e.g., hotel reservations).
     *
     * @param referenceOrderId Unique order identifier from merchant system
     * @param transactionRequestId Unique transaction request identifier for idempotency
     * @param amount Amount to pre-authorize
     * @param currency Currency code (e.g., "USD", "EUR")
     * @param description Human-readable transaction description
     * @param callback Callback interface for transaction result handling
     */
    fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        callback: PaymentCallback
    )

    /**
     * Execute a FORCED_AUTH (forced authorization) transaction
     *
     * Processes an authorization using a pre-obtained authorization code.
     * This is typically used for phone orders or when the merchant has already
     * received authorization through an alternative channel.
     *
     * @param referenceOrderId Unique order identifier from merchant system
     * @param transactionRequestId Unique transaction request identifier for idempotency
     * @param amount Transaction amount to authorize
     * @param currency Currency code (e.g., "USD", "EUR")
     * @param description Human-readable transaction description
     * @param tipAmount Optional tip amount to include in authorization
     * @param taxAmount Optional tax amount to include in authorization
     * @param callback Callback interface for transaction result handling
     */
    fun executeForcedAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        callback: PaymentCallback
    )

    /**
     * Execute REFUND transaction (Refund)
     *
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param originalTransactionId Original transaction ID
     * @param amount Refund amount
     * @param currency Currency type
     * @param description Transaction description
     * @param reason Refund reason
     * @param callback Payment callback
     */
    fun executeRefund(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    )

    /**
     * Execute VOID transaction (Void)
     *
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param originalTransactionId Original transaction ID
     * @param description Transaction description
     * @param reason Void reason
     * @param callback Payment callback
     */
    fun executeVoid(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    )

    /**
     * Execute POST_AUTH transaction (Pre-authorization completion)
     *
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param originalTransactionId Original transaction ID
     * @param amount Completion amount
     * @param currency Currency type
     * @param description Transaction description
     * @param surchargeAmount Surcharge amount (optional)
     * @param tipAmount Tip amount (optional)
     * @param cashbackAmount Cashback amount (optional)
     * @param callback Payment callback
     */
    fun executePostAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        surchargeAmount: BigDecimal? = null,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        callback: PaymentCallback
    )

    /**
     * Execute INCREMENTAL_AUTH transaction (Incremental authorization)
     *
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param originalTransactionId Original transaction ID
     * @param amount Incremental amount
     * @param currency Currency type
     * @param description Transaction description
     * @param callback Payment callback
     */
    fun executeIncrementalAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        callback: PaymentCallback
    )

    /**
     * Execute TIP_ADJUST transaction (Tip adjustment)
     *
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param originalTransactionId Original transaction ID
     * @param tipAmount Tip amount
     * @param description Transaction description
     * @param callback Payment callback
     */
    fun executeTipAdjust(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        tipAmount: BigDecimal,
        description: String,
        callback: PaymentCallback
    )

    /**
     * Execute QUERY transaction (Inquiry) - Using transaction request ID
     *
     * @param transactionRequestId Transaction request ID
     * @param callback Payment callback
     */
    fun executeQuery(
        transactionRequestId: String,
        callback: PaymentCallback
    )

    /**
     * Execute QUERY transaction (Inquiry) - Using transaction ID
     *
     * @param transactionId Transaction ID
     * @param callback Payment callback
     */
    fun executeQueryByTransactionId(
        transactionId: String,
        callback: PaymentCallback
    )

    /**
     * Execute BATCH_CLOSE transaction (Batch close)
     *
     * @param transactionRequestId Transaction request ID
     * @param description Transaction description
     * @param callback Payment callback
     */
    fun executeBatchClose(
        transactionRequestId: String,
        description: String,
        callback: PaymentCallback
    )

    /**
     * Execute ABORT transaction (Cancel/Abort ongoing transaction)
     *
     * @param originalTransactionId Original transaction ID to abort
     * @param originalTransactionRequestId Original transaction request ID to abort
     * @param description Description/reason for aborting the transaction
     * @param callback Payment callback
     */
    fun executeAbort(
        originalTransactionId: String?,
        originalTransactionRequestId: String?,
        description: String?,
        callback: PaymentCallback
    )
}

/**
 * Connection status listener
 */
interface ConnectionListener {
    /**
     * Connection successful
     *
     * @param deviceId Device ID
     * @param taproVersion Tapro version
     */
    fun onConnected(deviceId: String, taproVersion: String)

    /**
     * Connection disconnected
     *
     * @param reason Disconnect reason
     */
    fun onDisconnected(reason: String)

    /**
     * Connection error
     *
     * @param code Error code
     * @param message Error message
     */
    fun onError(code: String, message: String)
}

/**
 * Payment callback
 */
interface PaymentCallback {
    /**
     * Payment successful
     *
     * @param result Payment result
     */
    fun onSuccess(result: PaymentResult)

    /**
     * Payment failed
     *
     * @param code Error code
     * @param message Error message
     */
    fun onFailure(code: String, message: String)

    /**
     * Payment progress
     *
     * @param status Status code
     * @param message Status description
     */
    fun onProgress(status: String, message: String) {}
}

/**
 * Payment result
 */
data class PaymentResult(
    val code: String,
    val message: String,
    val traceId: String?,
    val transactionId: String?,
    val referenceOrderId: String?,
    val transactionRequestId: String?,
    val transactionStatus: String?,
    val transactionType: String?,
    val amount: TransactionAmount?,
    val createTime: String?,
    val completeTime: String?,
    val cardInfo: CardInfo?,
    val batchNo: Int?,
    val voucherNo: String?,
    val stan: String?,
    val rrn: String?,
    val authCode: String?,
    val transactionResultCode: String?,
    val transactionResultMsg: String?,
    val description: String?,
    val attach: String?,
    val batchCloseInfo: BatchCloseInfo?,
    val tipAmount: BigDecimal?,
    val totalAuthorizedAmount: BigDecimal?,
    val merchantRefundNo: String?,
    val originalTransactionId: String?,
    val originalTransactionRequestId: String?
) {
    /**
     * Check if transaction is successful
     */
    fun isSuccess(): Boolean = transactionStatus == "SUCCESS"

    /**
     * Check if transaction is processing
     */
    fun isProcessing(): Boolean = transactionStatus == "PROCESSING"

    /**
     * Check if transaction failed
     */
    fun isFailed(): Boolean = transactionStatus == "FAILED"
}

/**
 * Transaction amount details
 */
data class TransactionAmount(
    val priceCurrency: String?,
    val transAmount: BigDecimal?,
    val orderAmount: BigDecimal?,
    val taxAmount: BigDecimal?,
    val serviceFee: BigDecimal?,
    val surchargeAmount: BigDecimal?,
    val tipAmount: BigDecimal?,
    val cashbackAmount: BigDecimal?
)

/**
 * Card information
 */
data class CardInfo(
    val maskedPan: String?,
    val cardNetworkType: String?,
    val paymentMethodId: String?,
    val subPaymentMethodId: String?,
    val entryMode: String?,
    val authenticationMethod: String?,
    val cardholderName: String?,
    val expiryDate: String?,
    val issuerBank: String?,
    val cardBrand: String?
)

/**
 * Batch close info
 */
data class BatchCloseInfo(
    val totalCount: Int,
    val totalAmount: BigDecimal,
    val totalTip: BigDecimal,
    val totalTax: BigDecimal,
    val totalSurchargeAmount: BigDecimal,
    val totalServiceFee: BigDecimal,
    val cashDiscount: BigDecimal,
    val closeTime: String
)
