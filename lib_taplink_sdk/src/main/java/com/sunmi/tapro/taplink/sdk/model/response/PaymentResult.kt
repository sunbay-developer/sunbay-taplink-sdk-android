package com.sunmi.tapro.taplink.sdk.model.response

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.BatchAmount
import com.sunmi.tapro.taplink.sdk.model.common.BatchCloseInfo
import com.sunmi.tapro.taplink.sdk.model.common.CardInfo
import com.sunmi.tapro.taplink.sdk.model.common.TransactionAmount
import java.math.BigDecimal

/**
 * Payment Result Class
 * 
 * Contains comprehensive transaction information
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentResult(
    // ========== Basic response fields ==========
    
    /**
     * Response code ("100" indicates success)
     */
    val code: String,
    
    /**
     * Response description
     */
    val message: String? = null,
    
    /**
     * Trace ID (for troubleshooting)
     */
    val traceId: String? = null,
    
    // ========== Transaction identifier fields ==========
    
    /**
     * Transaction ID (Nexus (SUNBAY payment gateway) transaction ID)
     */
    val transactionId: String? = null,
    
    /**
     * Merchant order ID / Reference order ID
     */
    val referenceOrderId: String? = null,
    
    /**
     * Transaction request ID
     */
    val transactionRequestId: String? = null,
    
    // ========== Transaction status fields ==========
    
    /**
     * Transaction status
     * Values: SUCCESS, PROCESSING, FAILED
     */
    val transactionStatus: String? = null,
    
    /**
     * Transaction type
     * Values: SALE, AUTH, FORCED_AUTH, INCREMENTAL, POST_AUTH, VOID, REFUND
     */
    val transactionType: String? = null,
    
    // ========== Amount information ==========
    
    /**
     * Transaction amount details
     */
    val amount: TransactionAmount? = null,
    
    // ========== Time fields ==========
    
    /**
     * Transaction creation time (ISO 8601 format)
     */
    val createTime: String? = null,
    
    /**
     * Transaction completion time (ISO 8601 format)
     */
    val completeTime: String? = null,
    
    // ========== Card information ==========
    
    /**
     * Card information (includes card number, card type, input method, etc.)
     */
    val cardInfo: CardInfo? = null,
    
    // ========== Transaction voucher information ==========
    
    /**
     * Batch number
     */
    val batchNo: Int? = null,
    
    /**
     * Voucher number
     */
    val voucherNo: String? = null,
    
    /**
     * System Trace Audit Number (STAN)
     */
    val stan: String? = null,
    
    /**
     * Retrieval Reference Number (RRN)
     */
    val rrn: String? = null,
    
    /**
     * Authorization code
     */
    val authCode: String? = null,
    
    // ========== Transaction result information ==========
    
    /**
     * Transaction result code
     */
    val transactionResultCode: String? = null,
    
    /**
     * Transaction result message
     */
    val transactionResultMsg: String? = null,
    
    // ========== Terminal and description information ==========
    
    /**
     * Product description
     */
    val description: String? = null,
    
    /**
     * Additional data (returned as-is)
     */
    val attach: String? = null,
    
    // ========== Receipt information ==========
    
    /**
     * Receipt JSON data
     * Contains the receipt information in JSON format
     */
    val receiptJson: String? = null,
    
    // ========== Batch close specific fields ==========
    
    /**
     * Batch close information (batch close only)
     */
    val batchCloseInfo: BatchCloseInfo? = null,
    
    // ========== Tip adjustment specific fields ==========
    
    /**
     * Tip amount (tip adjustment only, unit: smallest currency unit / cents)
     */
    val tipAmount: BigDecimal? = null,
    
    // ========== Incremental authorization specific fields ==========
    
    /**
     * Incremental amount (incremental authorization only, unit: smallest currency unit / cents)
     */
    val incrementalAmount: BigDecimal? = null,
    
    /**
     * Total authorized amount (incremental authorization only, unit: smallest currency unit / cents)
     */
    val totalAuthorizedAmount: BigDecimal? = null,
    
    // ========== Refund specific fields ==========
    
    /**
     * Merchant refund number (refund only)
     */
    val merchantRefundNo: String? = null,
    
    /**
     * Original transaction ID (refund/void/authorization completion)
     */
    val originalTransactionId: String? = null,
    
    /**
     * Original transaction request ID (refund/void/authorization completion)
     */
    val originalTransactionRequestId: String? = null
) {
    /**
     * Check if transaction is successful.
     *
     * @return true if response code is "100" (gateway success) and transaction status is "SUCCESS"
     */
    fun isSuccess(): Boolean {
        return "100" == code && "SUCCESS" == transactionStatus
    }

    /**
     * Check if transaction is processing (pending gateway decision).
     *
     * @return true if transaction status is "PROCESSING"
     */
    fun isProcessing(): Boolean {
        return "PROCESSING" == transactionStatus
    }

    /**
     * Check if transaction was declined by the issuer or terminal.
     *
     * Note: a failed transaction is still a final response — it arrives via [onSuccess].
     * Technical/communication errors arrive via [onFailure] instead.
     *
     * @return true if transaction status is "FAILED"
     */
    fun isFailed(): Boolean {
        return "FAILED" == transactionStatus
    }

    /**
     * Convert this result to a [PaymentError] for unified error display.
     *
     * Useful when migrating from SDK ≤ v1.0.6 where declined transactions were delivered
     * via `onFailure(PaymentError)`. Call this inside [onTransactionDeclined] or
     * inside an `onSuccess` handler for `isFailed()` results to reuse existing error-display
     * code without rewriting it.
     *
     * ```kotlin
     * override fun onTransactionDeclined(result: PaymentResult) {
     *     showError(result.toPaymentError())   // reuses legacy error UI
     * }
     * ```
     *
     * @return [PaymentError] populated from this result's decline details
     */
    fun toPaymentError(): PaymentError =
        PaymentError.create(
            code = code,
            message = transactionResultMsg ?: message ?: "Transaction declined",
            traceId = traceId,
            transactionId = transactionId,
            transactionRequestId = transactionRequestId
        )
}
