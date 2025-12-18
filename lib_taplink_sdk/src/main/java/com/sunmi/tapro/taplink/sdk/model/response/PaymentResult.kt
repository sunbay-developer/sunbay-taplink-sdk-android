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
    // ========== Basic Response Fields ==========
    
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
    
    // ========== Transaction Identifier Fields ==========
    
    /**
     * Transaction ID (Nexus (SUNBAY payment gateway) transaction ID)
     */
    val transactionId: String? = null,
    
    /**
     * Merchant order number/reference order number
     */
    val referenceOrderId: String? = null,
    
    /**
     * Transaction request ID
     */
    val transactionRequestId: String? = null,
    
    // ========== Transaction Status Fields ==========
    
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
    
    // ========== Amount Information ==========
    
    /**
     * Transaction amount details
     */
    val amount: TransactionAmount? = null,
    
    // ========== Time Fields ==========
    
    /**
     * Transaction creation time (ISO 8601 format)
     */
    val createTime: String? = null,
    
    /**
     * Transaction completion time (ISO 8601 format)
     */
    val completeTime: String? = null,
    
    // ========== Card Information ==========
    
    /**
     * Card information (includes card number, card type, input method, etc.)
     */
    val cardInfo: CardInfo? = null,
    
    // ========== Transaction Voucher Information ==========
    
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
    
    // ========== Transaction Result Information ==========
    
    /**
     * Transaction result code
     */
    val transactionResultCode: String? = null,
    
    /**
     * Transaction result message
     */
    val transactionResultMsg: String? = null,
    
    // ========== Terminal and Description Information ==========
    
    /**
     * Product description
     */
    val description: String? = null,
    
    /**
     * Additional data (returned as-is)
     */
    val attach: String? = null,
    
    // ========== Batch Close Specific Fields ==========
    
    /**
     * Batch close information (batch close only)
     */
    val batchCloseInfo: BatchCloseInfo? = null,
    
    // ========== Tip Adjustment Specific Fields ==========
    
    /**
     * Tip amount (tip adjustment only, unit: base currency unit)
     */
    var tipAmount: BigDecimal? = null,
    
    // ========== Incremental Authorization Specific Fields ==========
    
    /**
     * Incremental amount (incremental authorization only, unit: base currency unit)
     */
    var incrementalAmount: BigDecimal? = null,
    
    /**
     * Total authorized amount (incremental authorization only, unit: base currency unit)
     */
    var totalAuthorizedAmount: BigDecimal? = null,
    
    // ========== Refund Specific Fields ==========
    
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
     * Check if transaction failed
     * 
     * @return true if transaction status is "FAILED" or response code is not "0"
     */
    fun isFailed(): Boolean {
        return "FAILED" == transactionStatus || "0" != code
    }
}
