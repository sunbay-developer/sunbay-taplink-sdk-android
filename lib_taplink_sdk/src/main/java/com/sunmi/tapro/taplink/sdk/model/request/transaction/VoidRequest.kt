package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * Void Transaction Request
 *
 * Used to void completed transactions, does not include amount fields
 *
 * @param originalTransactionId Original transaction ID (either this or originalTransactionRequestId must be provided)
 * @param originalTransactionRequestId Original transaction request ID (either this or originalTransactionId must be provided)
 * @param transactionRequestId Transaction request ID (required)
 * @param description Transaction description (optional, up to 128 characters)
 * @param attach Additional information (optional)
 * @param notifyUrl Notification URL (optional)
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
         * Create VoidRequest builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * VoidRequest Builder
     */
    class Builder {
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var transactionRequestId: String? = null
        private var description: String? = null
        private var attach: String? = null
        private var notifyUrl: String? = null

        /**
         * Set original transaction ID
         */
        fun setOriginalTransactionId(originalTransactionId: String): Builder {
            this.originalTransactionId = originalTransactionId
            return this
        }

        /**
         * Set original transaction request ID
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
         * Build VoidRequest instance
         * 
         * @throws TransactionRequestValidationException If validation fails
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