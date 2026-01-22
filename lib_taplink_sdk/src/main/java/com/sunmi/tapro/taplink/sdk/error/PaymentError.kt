package com.sunmi.tapro.taplink.sdk.error

import com.sunmi.tapro.taplink.communication.enums.ErrorCategory

/**
 * Payment error class
 * 
 * Contains structured error information and handling suggestions
 *
 * @author TaPro Team
 * @since 2025-01-14
 */
class PaymentError(
    /** Error details */
    val detail: Detail,
    
    /** Trace ID for troubleshooting */
    val traceId: String? = null,
    
    /** Merchant order number */
    val referenceOrderId: String? = null,
    
    /** Transaction ID */
    val transactionId: String? = null,
    
    /** Transaction request ID */
    val transactionRequestId: String? = null
) {
    // Backward compatible convenience properties
    /** Error code (equivalent to detail.code) */
    val code: String get() = detail.code
    
    /** Error description (equivalent to detail.message) */
    val message: String get() = detail.message
    
    /** Handling suggestion (equivalent to detail.suggestion) */
    val suggestion: String get() = detail.suggestion
    
    /** Error category (equivalent to detail.errorCategory) */
    val category: ErrorCategory get() = detail.errorCategory
    
    /** Whether can retry with the same transactionRequestId (equivalent to detail.canRetryWithSameId) */
    val canRetryWithSameId: Boolean get() = detail.canRetryWithSameId
    
    // Backward compatibility: orderNo maps to referenceOrderId
    /** Order number (equivalent to referenceOrderId, backward compatible) */
    val orderNo: String? get() = referenceOrderId
    
    /**
     * Convenience constructor: Create PaymentError from error code and message (backward compatible)
     * 
     * @param code Error code
     * @param message Error description
     * @param orderNo Order number (optional, backward compatible)
     * @param transactionId Transaction ID (optional)
     * @param suggestion Handling suggestion (optional)
     */
    constructor(
        code: String,
        message: String,
        orderNo: String? = null,
        transactionId: String? = null,
        suggestion: String? = null
    ) : this(
        detail = Detail(
            code = code,
            message = message,
            suggestion = suggestion ?: "",
            errorCategory = ErrorCategory.fromCode(code),
            canRetryWithSameId = Detail.canRetryWithSameId(code)
        ),
        referenceOrderId = orderNo,
        transactionId = transactionId
    )
    
    companion object {
        /**
         * 便捷构造函数：从错误码和消息创建 PaymentError
         * 
         * 用于向后兼容，自动创建 Detail
         * 
         * @param code 错误码
         * @param message 错误描述
         * @param suggestion 处理建议（可选，默认为空字符串）
         * @param traceId 追踪 ID（可选）
         * @param referenceOrderId 商户订单号（可选）
         * @param transactionId 交易 ID（可选）
         * @param transactionRequestId 交易请求 ID（可选）
         */
        @JvmStatic
        fun create(
            code: String,
            message: String,
            suggestion: String = "",
            traceId: String? = null,
            referenceOrderId: String? = null,
            transactionId: String? = null,
            transactionRequestId: String? = null
        ): PaymentError {
            val category = ErrorCategory.fromCode(code)
            val canRetry = Detail.canRetryWithSameId(code)
            val detail = Detail(
                code = code,
                message = message,
                suggestion = suggestion,
                errorCategory = category,
                canRetryWithSameId = canRetry
            )
            return PaymentError(
                detail = detail,
                traceId = traceId,
                referenceOrderId = referenceOrderId,
                transactionId = transactionId,
                transactionRequestId = transactionRequestId
            )
        }
    }
    
    /**
     * Error detail class
     * Contains complete error information and handling suggestions
     *
     * @author TaPro Team
     * @since 2025-01-14
     */
    data class Detail(
        /** Error code (e.g., "306", "307", "201") */
        val code: String,
        
        /** Error description (e.g., "Response timeout", "Transaction rejected") */
        val message: String,
        
        /** Handling suggestion (e.g., "Query transaction status first, then retry after confirmation") */
        val suggestion: String,
        
        /** Error category */
        val errorCategory: ErrorCategory,
        
        /** Whether can retry with the same transactionRequestId */
        val canRetryWithSameId: Boolean
    ) {
        companion object {
            /**
             * Determine whether can retry with the same transactionRequestId
             * 
             * According to integration guide:
             * - 306, 307, 308 cannot use the same ID (306 needs to query status first)
             * - All other error codes can use the same ID
             * 
             * @param code Error code
             * @return true means can retry with same ID, false means must use new ID
             */
            fun canRetryWithSameId(code: String): Boolean {
                // 306, 307, 308 cannot use the same ID (306 needs to query status first)
                return code !in listOf("306", "307", "308")
            }
        }
    }
}



