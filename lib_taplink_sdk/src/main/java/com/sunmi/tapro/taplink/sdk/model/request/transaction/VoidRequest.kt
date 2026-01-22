package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * 撤销交易请求
 *
 * 用于撤销已完成的交易，不包含金额字段
 *
 * @param originalTransactionId 原始交易ID（与originalTransactionRequestId二选一）
 * @param originalTransactionRequestId 原始交易请求ID（与originalTransactionId二选一）
 * @param transactionRequestId 交易请求ID（必需）
 * @param description 交易描述（可选，最多128字符）
 * @param attach 附加信息（可选）
 * @param notifyUrl 通知URL（可选）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class VoidRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val transactionRequestId: String,
    val description: String? = null,
    val attach: String? = null,
    val notifyUrl: String? = null
) : BaseTransactionRequest() {

    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
    }

    override fun validate(): ValidationResult {
        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateOriginalTransactionReference(
                originalTransactionId, 
                originalTransactionRequestId
            ),
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId)
        )
    }

    companion object {
        /**
         * 创建VoidRequest构建器
         */
        fun builder(): Builder = Builder()
    }

    /**
     * VoidRequest构建器
     */
    class Builder {
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var transactionRequestId: String? = null
        private var description: String? = null
        private var attach: String? = null
        private var notifyUrl: String? = null

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
         * 设置交易请求ID
         */
        fun setTransactionRequestId(transactionRequestId: String): Builder {
            this.transactionRequestId = transactionRequestId
            return this
        }

        /**
         * 设置交易描述
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
         * 设置通知URL
         */
        fun setNotifyUrl(notifyUrl: String): Builder {
            this.notifyUrl = notifyUrl
            return this
        }

        /**
         * 构建VoidRequest实例
         * 
         * @throws TransactionRequestValidationException 如果验证失败
         */
        fun build(): VoidRequest {
            val request = VoidRequest(
                originalTransactionId = originalTransactionId,
                originalTransactionRequestId = originalTransactionRequestId,
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                description = description,
                attach = attach,
                notifyUrl = notifyUrl
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}