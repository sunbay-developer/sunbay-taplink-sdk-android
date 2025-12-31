package com.sunmi.tapro.taplink.demo.model

import java.math.BigDecimal

/**
 * Transaction data class
 * 
 * Stores complete information for a single transaction, including transaction identifiers, amount, status, etc.
 * 
 * @property transactionRequestId Transaction request ID (locally generated for idempotency control)
 * @property transactionId Nexus transaction serial number (returned by platform, may be empty)
 * @property referenceOrderId Reference order ID (unique order identifier in merchant system)
 * @property type Transaction type
 * @property amount Transaction base amount (order amount)
 * @property totalAmount Total transaction amount including all fees (transAmount from SDK)
 * @property currency Currency type
 * @property status Transaction status
 * @property timestamp Creation timestamp (milliseconds)
 * @property authCode Authorization code (returned when transaction is successful)
 * @property errorCode Error code (returned when transaction fails)
 * @property errorMessage Error message (returned when transaction fails)
 * @property originalTransactionId Original transaction ID (used for REFUND, VOID and other follow-up operations)
 * @property surchargeAmount Surcharge amount (optional)
 * @property tipAmount Tip amount (optional)
 * @property cashbackAmount Cashback amount (optional)
 * @property serviceFee Service fee amount (optional)
 * @property batchNo Batch number (for BATCH_CLOSE transactions)
 * @property batchCloseInfo Batch close information (for BATCH_CLOSE transactions)
 */
data class Transaction(
    val transactionRequestId: String,
    val transactionId: String? = null,
    val referenceOrderId: String? = null,
    val type: TransactionType,
    val amount: BigDecimal,
    val totalAmount: BigDecimal? = null,
    val currency: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val authCode: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val originalTransactionId: String? = null,
    val surchargeAmount: BigDecimal? = null,
    val tipAmount: BigDecimal? = null,
    val taxAmount: BigDecimal? = null,
    val cashbackAmount: BigDecimal? = null,
    val serviceFee: BigDecimal? = null,
    val batchNo: Int? = null,
    val batchCloseInfo: BatchCloseInfo? = null
) {
    /**
     * Check if transaction is successful
     */
    fun isSuccess(): Boolean = status == TransactionStatus.SUCCESS
    
    /**
     * Check if transaction failed
     */
    fun isFailed(): Boolean = status == TransactionStatus.FAILED
    
    /**
     * Check if transaction can be refunded
     * Only successful transactions of type SALE and POST_AUTH can be refunded
     */
    fun canRefund(): Boolean {
        return isSuccess() && (
            type == TransactionType.SALE ||
            type == TransactionType.POST_AUTH
        )
    }
    
    /**
     * Check if transaction can be voided
     * Only successful transactions of type SALE, AUTH, FORCED_AUTH and POST_AUTH can be voided
     */
    fun canVoid(): Boolean {
        return isSuccess() && (
            type == TransactionType.SALE ||
            type == TransactionType.AUTH ||
            type == TransactionType.REFUND ||
            type == TransactionType.POST_AUTH
        )
    }
    
    /**
     * Check if transaction can be tip adjusted
     * Only successful transactions of type SALE and POST_AUTH can be tip adjusted
     */
    fun canAdjustTip(): Boolean {
        return isSuccess() && (
            type == TransactionType.SALE ||
            type == TransactionType.POST_AUTH
        )
    }
    
    /**
     * Check if transaction can be incremental authorized
     * Only successful transactions of type AUTH and FORCED_AUTH can be incremental authorized
     */
    fun canIncrementalAuth(): Boolean {
        return isSuccess() && (
            type == TransactionType.AUTH
        )
    }
    
    /**
     * Check if transaction can be post authorized
     * Only successful transactions of type AUTH and FORCED_AUTH can be post authorized
     */
    fun canPostAuth(): Boolean {
        return isSuccess() && (
            type == TransactionType.AUTH
        )
    }
    
    /**
     * Get transaction display name
     */
    fun getDisplayName(): String {
        return when (type) {
            TransactionType.SALE -> "SALE"
            TransactionType.AUTH -> "AUTH"
            TransactionType.INCREMENT_AUTH -> "INCREMENT AUTH"
            TransactionType.FORCED_AUTH -> "FORCED_AUTH"
            TransactionType.POST_AUTH -> "POST AUTH"
            TransactionType.REFUND -> "REFUND"
            TransactionType.VOID -> "VOID"
            TransactionType.TIP_ADJUST -> "TIP ADJUST"
            TransactionType.QUERY -> "QUERY"
            TransactionType.BATCH_CLOSE -> "BATCH CLOSE"
        }
    }
    
    /**
     * Get status display name
     */
    fun getStatusDisplayName(): String {
        return when (status) {
            TransactionStatus.PENDING -> "Pending"
            TransactionStatus.PROCESSING -> "Processing"
            TransactionStatus.SUCCESS -> "Success"
            TransactionStatus.FAILED -> "Failed"
            TransactionStatus.CANCELLED -> "Cancelled"
        }
    }
}

/**
 * Batch close information
 */
data class BatchCloseInfo(
    val totalCount: Int,
    val totalAmount: BigDecimal,
    val totalTip: BigDecimal,
    val totalTax: BigDecimal,
    val totalSurchargeAmount: BigDecimal,
    val cashDiscount: BigDecimal,
    val closeTime: String
)