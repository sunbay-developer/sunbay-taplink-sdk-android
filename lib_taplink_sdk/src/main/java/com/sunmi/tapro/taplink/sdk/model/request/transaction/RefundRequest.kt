package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.enums.ReceiptType
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * Refund Transaction Request
 *
 * Supports two refund modes:
 * 1. Referenced refund: Based on original transaction ID or transaction request ID
 * 2. Non-referenced refund: Independent refund based on reference order ID
 *
 * @param transactionRequestId Transaction request ID (required)
 * @param amount Refund amount information (required)
 * @param description Transaction description (optional, maximum 128 characters)
 * @param originalTransactionId Original transaction ID (used for referenced refund)
 * @param originalTransactionRequestId Original transaction request ID (used for referenced refund)
 * @param referenceOrderId Reference order ID (used for non-referenced refund, 6-32 characters)
 * @param paymentMethod Payment method information (optional for non-referenced refund)
 * @param attach Additional information (optional)
 * @param notifyUrl Notification URL (optional)
 * @param requestTimeout Request timeout duration (optional, unit: seconds)
 * @param staffInfo Staff information (optional)
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
    val staffInfo: StaffInfo? = null,
    val receiptType: ReceiptType = ReceiptType.BOTH
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
     * Check if this is a referenced refund
     */
    fun isReferencedRefund(): Boolean = 
        originalTransactionId != null || originalTransactionRequestId != null

    /**
     * Check if this is a non-referenced refund
     */
    fun isNonReferencedRefund(): Boolean = 
        referenceOrderId != null

    override fun validate(): ValidationResult {
        val baseValidation = TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            TransactionRequestValidator.validateAmount(amount)
        )

        val modeValidation = if (isReferencedRefund()) {
            // Referenced refund validation
            ValidationResult.success()
        } else {
            // Non-referenced refund validation
            TransactionRequestValidator.validateReferenceOrderId(referenceOrderId)
        }

        return TransactionRequestValidator.combineResults(baseValidation, modeValidation)
    }

    companion object {
        /**
         * Create referenced refund builder
         */
        fun referencedBuilder(): ReferencedBuilder = ReferencedBuilder()

        /**
         * Create non-referenced refund builder
         */
        fun nonReferencedBuilder(): NonReferencedBuilder = NonReferencedBuilder()
    }

    /**
     * Referenced Refund Builder
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
        private var receiptType: ReceiptType = ReceiptType.BOTH

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

        fun setReceiptType(receiptType: ReceiptType): ReferencedBuilder {
            this.receiptType = receiptType
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
                requestTimeout = requestTimeout,
                receiptType = receiptType
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }

    /**
     * Non-referenced Refund Builder
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
        private var receiptType: ReceiptType = ReceiptType.BOTH

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

        fun setReceiptType(receiptType: ReceiptType): NonReferencedBuilder {
            this.receiptType = receiptType
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
                requestTimeout = requestTimeout,
                receiptType = receiptType
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}