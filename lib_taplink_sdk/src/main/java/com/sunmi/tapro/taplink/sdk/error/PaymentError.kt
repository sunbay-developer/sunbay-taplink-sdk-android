package com.sunmi.tapro.taplink.sdk.error

import com.sunmi.tapro.taplink.communication.enums.ErrorCategory

/**
 * 支付错误类
 * 
 * 包含结构化的错误信息和处理建议
 *
 * @author TaPro Team
 * @since 2025-01-14
 */
class PaymentError(
    /** 错误详情 */
    val detail: Detail,
    
    /** 追踪 ID，用于问题排查 */
    val traceId: String? = null,
    
    /** 商户订单号 */
    val referenceOrderId: String? = null,
    
    /** 交易 ID */
    val transactionId: String? = null,
    
    /** 交易请求 ID */
    val transactionRequestId: String? = null
) {
    // 向后兼容的便捷属性
    /** 错误码（等同于 detail.code） */
    val code: String get() = detail.code
    
    /** 错误描述（等同于 detail.message） */
    val message: String get() = detail.message
    
    /** 处理建议（等同于 detail.suggestion） */
    val suggestion: String get() = detail.suggestion
    
    /** 错误分类（等同于 detail.errorCategory） */
    val category: ErrorCategory get() = detail.errorCategory
    
    /** 是否可以使用相同的transactionRequestId重试（等同于 detail.canRetryWithSameId） */
    val canRetryWithSameId: Boolean get() = detail.canRetryWithSameId
    
    // 向后兼容：orderNo 映射到 referenceOrderId
    /** 订单号（等同于 referenceOrderId，向后兼容） */
    val orderNo: String? get() = referenceOrderId
    
    /**
     * 便捷构造函数：从错误码和消息创建 PaymentError（向后兼容）
     * 
     * @param code 错误码
     * @param message 错误描述
     * @param orderNo 订单号（可选，向后兼容）
     * @param transactionId 交易 ID（可选）
     * @param suggestion 处理建议（可选）
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
     * 错误详情类
     * 包含错误的完整信息和处理建议
     *
     * @author TaPro Team
     * @since 2025-01-14
     */
    data class Detail(
        /** 错误码（如 "306", "307", "201"） */
        val code: String,
        
        /** 错误描述（如 "响应超时", "交易被拒绝"） */
        val message: String,
        
        /** 处理建议（如 "先查询交易状态，确认后再重试"） */
        val suggestion: String,
        
        /** 错误分类 */
        val errorCategory: ErrorCategory,
        
        /** 是否可以使用相同的transactionRequestId重试 */
        val canRetryWithSameId: Boolean
    ) {
        companion object {
            /**
             * 判断是否可以使用相同的 transactionRequestId 重试
             * 
             * 根据集成指南：
             * - 306、307、308 不能使用相同ID（306需要先查询状态）
             * - 其他所有错误码都可以使用相同ID
             * 
             * @param code 错误码
             * @return true 表示可以使用相同ID重试，false 表示必须使用新ID
             */
            fun canRetryWithSameId(code: String): Boolean {
                // 306、307、308 不能使用相同ID（306需要先查询状态）
                return code !in listOf("306", "307", "308")
            }
        }
    }
}



