package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * Abort Transaction Request
 *
 * Used to abort an ongoing transaction, does not contain amount or order fields
 *
 * @param originalTransactionId Original transaction ID (either this or originalTransactionRequestId must be provided)
 * @param originalTransactionRequestId Original transaction request ID (either this or originalTransactionId must be provided)
 * @param description Abort reason description (optional, maximum 128 characters)
 * @param attach Additional information (optional)
 * @param requestTimeout Request timeout duration (optional, unit: seconds)
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class AbortRequest(
    val originalTransactionRequestId: String? = null,
    val description: String? = null,
    val attach: String? = null,
    val requestTimeout: Long? = null
) : BaseTransactionRequest() {

    init {
        require(originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
    }

    override fun validate(): ValidationResult {
        val originalTransactionValidation = TransactionRequestValidator.validateOriginalTransactionReference(
            originalTransactionRequestId
        )

        val descriptionValidation = if (description != null) {
            TransactionRequestValidator.validateDescription(description)
        } else {
            ValidationResult.success()
        }

        return TransactionRequestValidator.combineResults(
            originalTransactionValidation,
            descriptionValidation
        )
    }

    companion object {
        /**
         * Create AbortRequest builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * AbortRequest Builder
     */
    class Builder {
        private var originalTransactionRequestId: String? = null
        private var description: String? = null
        private var attach: String? = null
        private var requestTimeout: Long? = null

        /**
         * Set original transaction request ID
         */
        fun setOriginalTransactionRequestId(originalTransactionRequestId: String): Builder {
            this.originalTransactionRequestId = originalTransactionRequestId
            return this
        }

        /**
         * Set abort reason description
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
         * Set request timeout duration
         */
        fun setRequestTimeout(requestTimeout: Long): Builder {
            this.requestTimeout = requestTimeout
            return this
        }

        /**
         * Build AbortRequest instance
         *
         * @throws TransactionRequestValidationException If validation fails
         */
        fun build(): AbortRequest {
            val request = AbortRequest(
                originalTransactionRequestId = originalTransactionRequestId,
                description = description,
                attach = attach,
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