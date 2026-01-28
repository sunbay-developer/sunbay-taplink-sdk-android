package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * Forced Authorization Transaction Request
 *
 * Used to initiate forced authorization transactions, typically for offline scenarios or special authorization cases
 *
 * @param referenceOrderId Reference order ID (required, 6-32 characters)
 * @param transactionRequestId Transaction request ID (required)
 * @param amount Authorization amount information (required, contains only amount and currency)
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
data class ForcedAuthRequest(
    val referenceOrderId: String,
    val transactionRequestId: String,
    val amount: AuthAmountInfo,
    val description: String? = null,
    val paymentMethod: PaymentMethodInfo? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val requestTimeout: Long? = null,
    val staffInfo: StaffInfo? = null,
    val printReceipt: PrintReceipt = PrintReceipt.NONE
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
         * Create ForcedAuthRequest builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * ForcedAuthRequest Builder
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
        private var printReceipt: PrintReceipt = PrintReceipt.NONE

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
         * Set authorization amount information
         */
        fun setAmount(amount: AuthAmountInfo): Builder {
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
         * Set print receipt
         */
        fun setPrintReceipt(printReceipt: PrintReceipt): Builder {
            this.printReceipt = printReceipt
            return this
        }

        /**
         * Build ForcedAuthRequest instance
         * 
         * @throws TransactionRequestValidationException If validation fails
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
                staffInfo = staffInfo,
                printReceipt = printReceipt
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}