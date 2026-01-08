package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * 强制预授权交易请求
 *
 * 用于发起强制预授权交易，通常用于离线场景或特殊授权情况
 *
 * @param referenceOrderId 参考订单号（必需，6-32字符）
 * @param transactionRequestId 交易请求ID（必需）
 * @param amount 预授权金额信息（必需，只包含金额和币种）
 * @param description 交易描述（可选，最多128字符）
 * @param paymentMethod 支付方式信息（可选）
 * @param attach 附加信息（可选）
 * @param notifyUrl 通知URL（可选）
 * @param requestTimeout 请求超时时间（可选，单位：秒）
 * @param staffInfo 员工信息（可选）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ForcedAuthRequest(
    val referenceOrderId: String,
    val transactionRequestId: String,
    val amount: AuthAmountInfo,
    val description: String? = null,
    val paymentMethod: PaymentMethodInfo? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val requestTimeout: Long? = null,
    val staffInfo: StaffInfo? = null
) : BaseTransactionRequest() {

    override fun validate(): ValidationResult {
        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateReferenceOrderId(referenceOrderId),
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            validateAuthAmount(amount)
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
        private var requestTimeout: Long? = null
        private var staffInfo: StaffInfo? = null

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
        fun setDescription(description: String?): Builder {
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
         * 设置请求超时时间
         */
        fun setRequestTimeout(requestTimeout: Long): Builder {
            this.requestTimeout = requestTimeout
            return this
        }

        /**
         * 设置员工信息
         */
        fun setStaffInfo(staffInfo: StaffInfo): Builder {
            this.staffInfo = staffInfo
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
                description = description,
                paymentMethod = paymentMethod,
                attach = attach,
                notifyUrl = notifyUrl,
                requestTimeout = requestTimeout,
                staffInfo = staffInfo
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}