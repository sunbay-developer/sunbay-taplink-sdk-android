package com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement

import com.sunmi.tapro.taplink.sdk.model.request.transaction.BaseTransactionRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TransactionRequestValidator
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TransactionRequestValidationException
import com.sunmi.tapro.taplink.sdk.model.request.transaction.ValidationResult

/**
 * 批次关闭交易请求
 *
 * 用于关闭当前批次，完成结算流程
 *
 * @param transactionRequestId 交易请求ID（必需）
 * @param description 关闭描述（可选，最多128字符）
 * @param requestTimeout 请求超时时间（可选，单位：秒）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class BatchCloseRequest(
    val transactionRequestId: String,
    val description: String? = null,
    val requestTimeout: Long? = null
) : BaseTransactionRequest() {

    override fun validate(): ValidationResult {
        val descriptionValidation = if (description != null) {
            TransactionRequestValidator.validateDescription(description)
        } else {
            ValidationResult.success()
        }

        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            descriptionValidation
        )
    }

    companion object {
        /**
         * 创建BatchCloseRequest构建器
         */
        fun builder(): Builder = Builder()
    }

    /**
     * BatchCloseRequest构建器
     */
    class Builder {
        private var transactionRequestId: String? = null
        private var description: String? = null
        private var requestTimeout: Long? = null

        /**
         * 设置交易请求ID
         */
        fun setTransactionRequestId(transactionRequestId: String): Builder {
            this.transactionRequestId = transactionRequestId
            return this
        }

        /**
         * 设置关闭描述
         */
        fun setDescription(description: String?): Builder {
            this.description = description
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
         * 构建BatchCloseRequest实例
         * 
         * @throws TransactionRequestValidationException 如果验证失败
         */
        fun build(): BatchCloseRequest {
            val request = BatchCloseRequest(
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                description = description,
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