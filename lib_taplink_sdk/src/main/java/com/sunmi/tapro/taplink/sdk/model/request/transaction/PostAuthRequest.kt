package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * Post-Authorization Transaction Request
 *
 * Used to complete a pre-authorization transaction, converting the authorized amount to actual deduction
 *
 * @param originalTransactionId Original pre-authorization transaction ID (either this or originalTransactionRequestId must be provided)
 * @param originalTransactionRequestId Original pre-authorization transaction request ID (either this or originalTransactionId must be provided)
 * @param transactionRequestId Transaction request ID (required)
 * @param amount Completion amount information (required, can be less than or equal to the authorized amount)
 * @param description Transaction description (optional, maximum 128 characters)
 * @param attach Additional information (optional)
 * @param notifyUrl Notification URL (optional)
 * @param requestTimeout Request timeout duration (optional, unit: seconds)
 * @param staffInfo Staff information (optional)
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PostAuthRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val transactionRequestId: String,
    val amount: AmountInfo,
    val description: String? = null,
    val attach: String? = null,
    val notifyUrl: String? = null,
    val requestTimeout: Long? = null,
    val staffInfo: StaffInfo? = null,
    val printReceipt: PrintReceipt = PrintReceipt.NONE,
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
            TransactionRequestValidator.validateTransactionRequestId(transactionRequestId),
            TransactionRequestValidator.validateAmount(amount)
        )
    }

    companion object {
        /**
         * Create PostAuthRequest builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * PostAuthRequest Builder
     */
    class Builder {
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var transactionRequestId: String? = null
        private var amount: AmountInfo? = null
        private var description: String? = null
        private var attach: String? = null
        private var notifyUrl: String? = null
        private var requestTimeout: Long? = null
        private var staffInfo: StaffInfo? = null
        private var printReceipt: PrintReceipt = PrintReceipt.NONE

        /**
         * Set original pre-authorization transaction ID
         */
        fun setOriginalTransactionId(originalTransactionId: String): Builder {
            this.originalTransactionId = originalTransactionId
            return this
        }

        /**
         * Set original pre-authorization transaction request ID
         */
        fun setOriginalTransactionRequestId(originalTransactionRequestId: String): Builder {
            this.originalTransactionRequestId = originalTransactionRequestId
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
         * Set completion amount information
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
         * Build PostAuthRequest instance
         * 
         * @throws TransactionRequestValidationException If validation fails
         */
        fun build(): PostAuthRequest {
            val request = PostAuthRequest(
                originalTransactionId = originalTransactionId,
                originalTransactionRequestId = originalTransactionRequestId,
                transactionRequestId = requireNotNull(transactionRequestId) { "transactionRequestId is required" },
                amount = requireNotNull(amount) { "amount is required" },
                description = description,
                attach = attach,
                notifyUrl = notifyUrl,
                requestTimeout = requestTimeout,
                staffInfo = staffInfo,
                printReceipt = printReceipt,
            )

            val validationResult = request.validate()
            if (!validationResult.isValid) {
                throw TransactionRequestValidationException(validationResult.errors)
            }

            return request
        }
    }
}