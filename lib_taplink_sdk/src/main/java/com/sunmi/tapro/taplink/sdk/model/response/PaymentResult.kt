package com.sunmi.tapro.taplink.sdk.model.response

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
     * Response code ("0" indicates success)
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
    
    // ========== Batch close specific fields ==========
    
    /**
     * Batch close information (batch close only)
     */
    val batchCloseInfo: BatchCloseInfo? = null,
    
    // ========== Tip adjustment specific fields ==========
    
    /**
     * Tip amount (tip adjustment only, unit: base currency unit)
     */
    var tipAmount: BigDecimal? = null,
    
    // ========== Incremental authorization specific fields ==========
    
    /**
     * Incremental amount (incremental authorization only, unit: base currency unit)
     */
    var incrementalAmount: BigDecimal? = null,
    
    /**
     * Total authorized amount (incremental authorization only, unit: base currency unit)
     */
    var totalAuthorizedAmount: BigDecimal? = null,
    
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
     * Check if transaction is successful
     * 
     * @return true if response code is "0" and transaction status is "SUCCESS"
     */
    fun isSuccess(): Boolean {
        return "0" == code && "SUCCESS" == transactionStatus
    }
    
    /**
     * Check if transaction is processing
     * 
     * @return true if transaction status is "PROCESSING"
     */
    fun isProcessing(): Boolean {
        return "PROCESSING" == transactionStatus
    }
    
    /**
     * Check if transaction is failed
     * 
     * @return true if transaction status is "FAILED" or response code is not "0"
     */
    fun isFailed(): Boolean {
        return "FAILED" == transactionStatus || "0" != code
    }
}
