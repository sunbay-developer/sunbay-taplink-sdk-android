package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * 退款交易请求
 *
 * 支持两种退款模式：
 * 1. 引用退款：基于原始交易ID或交易请求ID进行退款
 * 2. 非引用退款：基于参考订单号进行独立退款
 *
 * @param transactionRequestId 交易请求ID（必需）
 * @param amount 退款金额信息（必需）
 * @param description 交易描述（可选，最多128字符）
 * @param originalTransactionId 原始交易ID（引用退款时使用）
 * @param originalTransactionRequestId 原始交易请求ID（引用退款时使用）
 * @param referenceOrderId 参考订单号（非引用退款时使用，6-32字符）
 * @param paymentMethod 支付方式信息（非引用退款时可选）
 * @param attach 附加信息（可选）
 * @param notifyUrl 通知URL（可选）
 * @param requestTimeout 请求超时时间（可选，单位：秒）
 * @param staffInfo 员工信息（可选）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class RefundRequest(
    val transactionRequestId: String,
    val amount: AmountInfo,
    val description: String? = null,
    // 引用退款字段
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    // 非引用退款字段
    val referenceOrderId: String? = null,
    val paymentMethod: PaymentMethodInfo? = null,
    // 通用可选字段
    val attach: String? = null,
    val notifyUrl: String? = null,
    val requestTimeout: Long? = null,
    val staffInfo: StaffInfo? = null
) : BaseTransactionRequest() {

    init {
        require(isReferencedRefund() || isNonReferencedRefund()) {
            "Refund must be either referenced (with originalTransactionId/originalTransactionRequestId) or non-referenced (with referenceOrderId)"
        }
        require(!(isReferencedRefund() && isNonReferencedRefund())) {
            "Refund cannot be both referenced and non-referenced"
        }
    }

    /**
     * 判断是否为引用退款
     */
    fun isReferencedRefund(): Boolean = 
        originalTransactionId != null || originalTransactionRequestId != null

    /**
     * 判断是否为非引用退款
     */
    fun isNonReferencedRefund(): Boolean = 
        referenceOrderId != null

    override fun validate(): ValidationResult {
        val baseValidation = TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            TransactionRequestValidator.validateAmount(amount)
        )

        val modeValidation = if (isReferencedRefund()) {
            // 引用退款验证
            ValidationResult.success()
        } else {
            // 非引用退款验证
            TransactionRequestValidator.validateReferenceOrderId(referenceOrderId)
        }

        return TransactionRequestValidator.combineResults(baseValidation, modeValidation)
    }

    companion object {
        /**
         * 创建引用退款构建器
         */
        fun referencedBuilder(): ReferencedBuilder = ReferencedBuilder()

        /**
         * 创建非引用退款构建器
         */
        fun nonReferencedBuilder(): NonReferencedBuilder = NonReferencedBuilder()
    }

    /**
     * 引用退款构建器
     */
    class ReferencedBuilder {
        private var transactionRequestId: String? = null
        private var amount: AmountInfo? = null
        private var description: String? = null
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var attach: String? = null
        private var notifyUrl: String? = null
        private var requestTimeout: Long? = null
        private var staffInfo: StaffInfo? = null

        fun setTransactionRequestId(transactionRequestId: String): ReferencedBuilder {
            this.transactionRequestId = transactionRequestId
            return this
        }

        fun setAmount(amount: AmountInfo): ReferencedBuilder {
            this.amount = amount
            return this
        }

        fun setDescription(description: String?): ReferencedBuilder {
            this.description = description
            return this
        }

        fun setOriginalTransactionId(originalTransactionId: String): ReferencedBuilder {
            this.originalTransactionId = originalTransactionId
            return this
        }

        fun setOriginalTransactionRequestId(originalTransactionRequestId: String): ReferencedBuilder {
            this.originalTransactionRequestId = originalTransactionRequestId
            return this
        }

        fun setAttach(attach: String): ReferencedBuilder {
            this.attach = attach
            return this
        }

        fun setNotifyUrl(notifyUrl: String): ReferencedBuilder {
            this.notifyUrl = notifyUrl
            return this
        }

        fun setRequestTimeout(requestTimeout: Long): ReferencedBuilder {
            this.requestTimeout = requestTimeout
            return this
        }

        fun build(): RefundRequest {
            require(originalTransactionId != null || originalTransactionRequestId != null) {
                "Either originalTransactionId or originalTransactionRequestId must be provided for referenced refund"
            }

            val request = RefundRequest(
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                amount = requireNotNull(amount) { "amount is required" },
                description = description,
                originalTransactionId = originalTransactionId,
                originalTransactionRequestId = originalTransactionRequestId,
                attach = attach,
                notifyUrl = notifyUrl,
                requestTimeout = requestTimeout
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }

    /**
     * 非引用退款构建器
     */
    class NonReferencedBuilder {
        private var transactionRequestId: String? = null
        private var amount: AmountInfo? = null
        private var description: String? = null
        private var referenceOrderId: String? = null
        private var paymentMethod: PaymentMethodInfo? = null
        private var attach: String? = null
        private var notifyUrl: String? = null
        private var requestTimeout: Long? = null

        fun setTransactionRequestId(transactionRequestId: String): NonReferencedBuilder {
            this.transactionRequestId = transactionRequestId
            return this
        }

        fun setAmount(amount: AmountInfo): NonReferencedBuilder {
            this.amount = amount
            return this
        }

        fun setDescription(description: String?): NonReferencedBuilder {
            this.description = description
            return this
        }

        fun setReferenceOrderId(referenceOrderId: String): NonReferencedBuilder {
            this.referenceOrderId = referenceOrderId
            return this
        }

        fun setPaymentMethod(paymentMethod: PaymentMethodInfo): NonReferencedBuilder {
            this.paymentMethod = paymentMethod
            return this
        }

        fun setAttach(attach: String): NonReferencedBuilder {
            this.attach = attach
            return this
        }

        fun setNotifyUrl(notifyUrl: String): NonReferencedBuilder {
            this.notifyUrl = notifyUrl
            return this
        }

        fun setRequestTimeout(requestTimeout: Long): NonReferencedBuilder {
            this.requestTimeout = requestTimeout
            return this
        }

        fun build(): RefundRequest {
            val request = RefundRequest(
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                amount = requireNotNull(amount) { "amount is required" },
                description = description,
                referenceOrderId = requireNotNull(referenceOrderId) { "referenceOrderId is required for non-referenced refund" },
                paymentMethod = paymentMethod,
                attach = attach,
                notifyUrl = notifyUrl,
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