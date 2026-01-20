package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * 中止交易请求
 *
 * 用于中止正在进行的交易，不包含金额或订单字段
 *
 * @param originalTransactionId 原始交易ID（与originalTransactionRequestId二选一）
 * @param originalTransactionRequestId 原始交易请求ID（与originalTransactionId二选一）
 * @param description 中止原因描述（可选，最多128字符）
 * @param attach 附加信息（可选）
 * @param requestTimeout 请求超时时间（可选，单位：秒）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class AbortRequest(
    val originalTransactionRequestId: String? = null,
    val description: String? = null,
    val attach: String? = null,
    val requestTimeout: Long? = null
) : BaseTransactionRequest() {

    init {
        require(originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
    }

    override fun validate(): ValidationResult {
        val originalTransactionValidation = TransactionRequestValidator.validateOriginalTransactionReference(
            originalTransactionRequestId
        )

        val descriptionValidation = if (description != null) {
            TransactionRequestValidator.validateDescription(description)
        } else {
            ValidationResult.success()
        }

        return TransactionRequestValidator.combineResults(
            originalTransactionValidation,
            descriptionValidation
        )
    }

    companion object {
        /**
         * 创建AbortRequest构建器
         */
        fun builder(): Builder = Builder()
    }

    /**
     * AbortRequest构建器
     */
    class Builder {
        private var originalTransactionRequestId: String? = null
        private var description: String? = null
        private var attach: String? = null
        private var requestTimeout: Long? = null

        /**
         * 设置原始交易请求ID
         */
        fun setOriginalTransactionRequestId(originalTransactionRequestId: String): Builder {
            this.originalTransactionRequestId = originalTransactionRequestId
            return this
        }

        /**
         * 设置中止原因描述
         */
        fun setDescription(description: String?): Builder {
            this.description = description
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
         * 构建AbortRequest实例
         *
         * @throws TransactionRequestValidationException 如果验证失败
         */
        fun build(): AbortRequest {
            val request = AbortRequest(
                originalTransactionRequestId = originalTransactionRequestId,
                description = description,
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