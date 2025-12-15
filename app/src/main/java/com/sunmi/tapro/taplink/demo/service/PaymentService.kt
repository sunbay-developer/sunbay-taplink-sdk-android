package com.sunmi.tapro.taplink.demo.service

import android.content.Context

/**
 * Payment Service Interface
 *
 * Defines core payment-related functionalities, including initialization, connection management and transaction execution
 */
interface PaymentService {

    /**
     * Initialize SDK
     *
     * @param context Application context
     * @param appId Application ID
     * @param merchantId Merchant ID
     * @param secretKey Signature key
     * @return Initialization result, returns true on success, false on failure
     */
    fun initialize(
        context: Context,
        appId: String,
        merchantId: String,
        secretKey: String
    ): Boolean

    /**
     * Connect to payment terminal
     *
     * @param listener Connection status listener
     */
    fun connect(listener: ConnectionListener)

    /**
     * Disconnect
     */
    fun disconnect()

    /**
     * Check connection status
     *
     * @return Whether connected
     */
    fun isConnected(): Boolean

    /**
     * Check if connecting
     *
     * @return Whether connecting
     */
    fun isConnecting(): Boolean

    /**
     * Get connected device ID
     *
     * @return Device ID, returns null if not connected
     */
    fun getConnectedDeviceId(): String?

    /**
     * Get Tapro version
     *
     * @return Tapro version, returns null if not connected
     */
    fun getTaproVersion(): String?

    /**
     * Execute SALE transaction
     *
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param amount Transaction amount
     * @param currency Currency type
     * @param description Transaction description
     * @param surchargeAmount Surcharge amount (optional)
     * @param tipAmount Tip amount (optional)
     * @param cashbackAmount Cashback amount (optional)
     * @param callback Payment callback
     */
    fun executeSale(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: Double,
        currency: String,
        description: String,
        surchargeAmount: Double? = null,
        tipAmount: Double? = null,
        taxAmount: Double? = null,
        cashbackAmount: Double? = null,
        serviceFee: Double? = null,
        callback: PaymentCallback
    )

    /**
     * Execute AUTH transaction (Pre-authorization)
     *
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param amount Transaction amount
     * @param currency Currency type
     * @param description Transaction description
     * @param callback Payment callback
     */
    fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: Double,
        currency: String,
        description: String,
        callback: PaymentCallback
    )

    /**
     * Execute FORCED_AUTH transaction (Forced authorization)
     *
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param amount Transaction amount
     * @param currency Currency type
     * @param authCode Authorization code
     * @param description Transaction description
     * @param callback Payment callback
     */
    fun executeForcedAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: Double,
        currency: String,
        authCode: String,
        description: String,
        tipAmount: Double? = null,
        taxAmount: Double? = null,
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
        amount: Double,
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
        amount: Double,
        currency: String,
        description: String,
        surchargeAmount: Double? = null,
        tipAmount: Double? = null,
        taxAmount: Double? = null,
        cashbackAmount: Double? = null,
        serviceFee: Double? = null,
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
        amount: Double,
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
        tipAmount: Double,
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
     * @param referenceOrderId Reference order ID
     * @param transactionRequestId Transaction request ID
     * @param description Transaction description
     * @param callback Payment callback
     */
    fun executeBatchClose(
        referenceOrderId: String,
        transactionRequestId: String,
        description: String,
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
    val tipAmount: Double?,
    val totalAuthorizedAmount: Double?,
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
    val transAmount: Double?,
    val orderAmount: Double?,
    val taxAmount: Double?,
    val serviceFee: Double?,
    val surchargeAmount: Double?,
    val tipAmount: Double?,
    val cashbackAmount: Double?
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
    val totalAmount: Double,
    val totalTip: Double,
    val totalTax: Double,
    val totalSurchargeAmount: Double,
    val totalServiceFee: Double,
    val cashDiscount: Double,
    val closeTime: String
)
