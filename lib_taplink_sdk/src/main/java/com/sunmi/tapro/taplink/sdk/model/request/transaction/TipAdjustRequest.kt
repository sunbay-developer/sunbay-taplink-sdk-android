package com.sunmi.tapro.taplink.sdk.model.request.transaction

import java.math.BigDecimal

/**
 * 小费调整交易请求
 *
 * 用于调整已完成交易的小费金额，不包含订单相关字段
 *
 * @param originalTransactionId 原始交易ID（与originalTransactionRequestId二选一）
 * @param originalTransactionRequestId 原始交易请求ID（与originalTransactionId二选一）
 * @param tipAmount 小费金额（必需，非负数，使用基本货币单位）
 * @param attach 附加信息（可选）
 * @param requestTimeout 请求超时时间（可选，单位：秒）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TipAdjustRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val tipAmount: BigDecimal,
    val attach: String? = null,
    val requestTimeout: Long? = null
) : BaseTransactionRequest() {

    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
        require(tipAmount >= BigDecimal.ZERO) {
            "Tip amount must be non-negative"
        }
    }

    override fun validate(): ValidationResult {
        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateOriginalTransactionReference(
                originalTransactionId, 
                originalTransactionRequestId
            ),
            validateTipAmount(tipAmount)
        )
    }

    private fun validateTipAmount(tipAmount: BigDecimal): ValidationResult {
        return if (tipAmount < BigDecimal.ZERO) {
            ValidationResult.failure(ValidationError.InvalidTipAmount)
        } else {
            ValidationResult.success()
        }
    }

    companion object {
        /**
         * 创建TipAdjustRequest构建器
         */
        fun builder(): Builder = Builder()
    }

    /**
     * TipAdjustRequest构建器
     */
    class Builder {
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var tipAmount: BigDecimal? = null
        private var attach: String? = null
        private var requestTimeout: Long? = null

        /**
         * 设置原始交易ID
         */
        fun setOriginalTransactionId(originalTransactionId: String): Builder {
            this.originalTransactionId = originalTransactionId
            return this
        }

        /**
         * 设置原始交易请求ID
         */
        fun setOriginalTransactionRequestId(originalTransactionRequestId: String): Builder {
            this.originalTransactionRequestId = originalTransactionRequestId
            return this
        }

        /**
         * 设置小费金额
         */
        fun setTipAmount(tipAmount: BigDecimal): Builder {
            this.tipAmount = tipAmount
            return this
        }

        /**
         * 设置附加信息
         */
        fun setAttach(attach: String): Builder {
            this.attach = attach
            return this
        }

        /**
         * 设置请求超时时间
         */
        fun setRequestTimeout(requestTimeout: Long): Builder {
            this.requestTimeout = requestTimeout
            return this
        }

        /**
         * 构建TipAdjustRequest实例
         * 
         * @throws TransactionRequestValidationException 如果验证失败
         */
        fun build(): TipAdjustRequest {
            val request = TipAdjustRequest(
                originalTransactionId = originalTransactionId,
                originalTransactionRequestId = originalTransactionRequestId,
                tipAmount = requireNotNull(tipAmount) { "tipAmount is required" },
                attach = attach,
                requestTimeout = requestTimeout
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}