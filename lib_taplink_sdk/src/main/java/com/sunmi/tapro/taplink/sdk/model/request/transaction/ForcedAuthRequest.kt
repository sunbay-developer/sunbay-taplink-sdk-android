package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo

/**
 * 强制预授权交易请求
 *
 * 用于发起强制预授权交易，通常用于离线场景或特殊授权情况
 *
 * @param referenceOrderId 参考订单号（必需，6-32字符）
 * @param transactionRequestId 交易请求ID（必需）
 * @param amount 预授权金额信息（必需，只包含金额和币种）
 * @param description 交易描述（必需，最多128字符）
 * @param paymentMethod 支付方式信息（可选）
 * @param attach 附加信息（可选）
 * @param notifyUrl 通知URL（可选）
 * @param timeExpire 过期时间（可选）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ForcedAuthRequest(
    val referenceOrderId: String,
    val transactionRequestId: String,
    val amount: AuthAmountInfo,
    val description: String,
    val paymentMethod: PaymentMethodInfo? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val timeExpire: String? = null
) : BaseTransactionRequest() {

    override fun validate(): ValidationResult {
        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateReferenceOrderId(referenceOrderId),
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            validateAuthAmount(amount),
            TransactionRequestValidator.validateDescription(description)
        )
    }

    private fun validateAuthAmount(amount: AuthAmountInfo?): ValidationResult {
        return if (amount == null) {
            ValidationResult.failure(ValidationError.MissingAmount)
        } else {
            ValidationResult.success()
        }
    }

    companion object {
        /**
         * 创建ForcedAuthRequest构建器
         */
        fun builder(): Builder = Builder()
    }

    /**
     * ForcedAuthRequest构建器
     */
    class Builder {
        private var referenceOrderId: String? = null
        private var transactionRequestId: String? = null
        private var amount: AuthAmountInfo? = null
        private var description: String? = null
        private var paymentMethod: PaymentMethodInfo? = null
        private var attach: String? = null
        private var notifyUrl: String? = null
        private var timeExpire: String? = null

        /**
         * 设置参考订单号
         */
        fun setReferenceOrderId(referenceOrderId: String): Builder {
            this.referenceOrderId = referenceOrderId
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
         * 设置预授权金额信息
         */
        fun setAmount(amount: AuthAmountInfo): Builder {
            this.amount = amount
            return this
        }

        /**
         * 设置交易描述
         */
        fun setDescription(description: String): Builder {
            this.description = description
            return this
        }

        /**
         * 设置支付方式信息
         */
        fun setPaymentMethod(paymentMethod: PaymentMethodInfo): Builder {
            this.paymentMethod = paymentMethod
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
         * 设置过期时间
         */
        fun setTimeExpire(timeExpire: String): Builder {
            this.timeExpire = timeExpire
            return this
        }

        /**
         * 构建ForcedAuthRequest实例
         * 
         * @throws TransactionRequestValidationException 如果验证失败
         */
        fun build(): ForcedAuthRequest {
            val request = ForcedAuthRequest(
                referenceOrderId = requireNotNull(referenceOrderId) { "referenceOrderId is required" },
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                amount = requireNotNull(amount) { "amount is required" },
                description = requireNotNull(description) { "description is required" },
                paymentMethod = paymentMethod,
                attach = attach,
                notifyUrl = notifyUrl,
                timeExpire = timeExpire
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}