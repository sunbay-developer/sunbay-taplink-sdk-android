package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.enums.ReceiptType
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * Sale Transaction Request
 *
 * Request class for initiating sale transactions, containing all required and optional fields
 *
 * @param referenceOrderId Reference order ID (required, 6-32 characters)
 * @param transactionRequestId Transaction request ID (required)
 * @param amount Amount information (required)
 * @param description Transaction description (optional, maximum 128 characters)
 * @param paymentMethod Payment method information (optional)
 * @param attach Additional information (optional)
 * @param notifyUrl Notification URL (optional)
 * @param requestTimeout Request timeout duration (optional, unit: seconds)
 * @param staffInfo Staff information (optional)
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class SaleRequest(
    val referenceOrderId: String,
    val transactionRequestId: String,
    val amount: AmountInfo,
    val description: String? = null,
    val paymentMethod: PaymentMethodInfo? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val requestTimeout: Long? = null,
    val staffInfo: StaffInfo? = null,
    val receiptType: ReceiptType = ReceiptType.BOTH
) : BaseTransactionRequest() {

    override fun validate(): ValidationResult {
        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateReferenceOrderId(referenceOrderId),
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            TransactionRequestValidator.validateAmount(amount),
        )
    }

    companion object {
        /**
         * Create SaleRequest builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * SaleRequest Builder
     */
    class Builder {
        private var referenceOrderId: String? = null
        private var transactionRequestId: String? = null
        private var amount: AmountInfo? = null
        private var description: String? = null
        private var paymentMethod: PaymentMethodInfo? = null
        private var attach: String? = null
        private var notifyUrl: String? = null
        private var requestTimeout: Long? = null
        private var staffInfo: StaffInfo? = null
        private var receiptType: ReceiptType = ReceiptType.BOTH

        /**
         * Set reference order ID
         */
        fun setReferenceOrderId(referenceOrderId: String): Builder {
            this.referenceOrderId = referenceOrderId
            return this
        }

        /**
         * Set transaction request ID
         */
        fun setTransactionRequestId(transactionRequestId: String): Builder {
            this.transactionRequestId = transactionRequestId
            return this
        }

        /**
         * Set amount information
         */
        fun setAmount(amount: AmountInfo): Builder {
            this.amount = amount
            return this
        }

        /**
         * Set transaction description
         */
        fun setDescription(description: String?): Builder {
            this.description = description
            return this
        }

        /**
         * Set payment method information
         */
        fun setPaymentMethod(paymentMethod: PaymentMethodInfo): Builder {
            this.paymentMethod = paymentMethod
            return this
        }

        /**
         * Set additional information
         */
        fun setAttach(attach: String): Builder {
            this.attach = attach
            return this
        }

        /**
         * Set notification URL
         */
        fun setNotifyUrl(notifyUrl: String): Builder {
            this.notifyUrl = notifyUrl
            return this
        }

        /**
         * Set request timeout duration
         */
        fun setRequestTimeout(requestTimeout: Long): Builder {
            this.requestTimeout = requestTimeout
            return this
        }

        /**
         * Set staff information
         */
        fun setStaffInfo(staffInfo: StaffInfo): Builder {
            this.staffInfo = staffInfo
            return this
        }

        /**
         * Set receipt type
         */
        fun setReceiptType(receiptType: ReceiptType): Builder {
            this.receiptType = receiptType
            return this
        }

        /**
         * Build SaleRequest instance
         * 
         * @throws TransactionRequestValidationException If validation fails
         */
        fun build(): SaleRequest {
            val request = SaleRequest(
                referenceOrderId = requireNotNull(referenceOrderId) { "referenceOrderId is required" },
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                amount = requireNotNull(amount) { "amount is required" },
                description = description,
                paymentMethod = paymentMethod,
                attach = attach,
                notifyUrl = notifyUrl,
                requestTimeout = requestTimeout,
                staffInfo = staffInfo,
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