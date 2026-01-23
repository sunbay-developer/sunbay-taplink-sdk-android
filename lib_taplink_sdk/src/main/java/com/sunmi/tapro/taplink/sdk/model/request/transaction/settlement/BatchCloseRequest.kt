package com.sunmi.tapro.taplink.sdk.model.request.transaction.settlement

import com.sunmi.tapro.taplink.sdk.model.request.transaction.BaseTransactionRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TransactionRequestValidator
import com.sunmi.tapro.taplink.sdk.model.request.transaction.TransactionRequestValidationException
import com.sunmi.tapro.taplink.sdk.model.request.transaction.ValidationResult

/**
 * Batch close transaction request
 *
 * Used to close the current batch and complete the settlement process
 *
 * @param transactionRequestId Transaction request ID (required)
 * @param description Close description (optional, max 128 characters)
 * @param requestTimeout Request timeout (optional, in seconds)
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
         * Create BatchCloseRequest builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * BatchCloseRequest builder
     */
    class Builder {
        private var transactionRequestId: String? = null
        private var description: String? = null
        private var requestTimeout: Long? = null

        /**
         * Set transaction request ID
         */
        fun setTransactionRequestId(transactionRequestId: String): Builder {
            this.transactionRequestId = transactionRequestId
            return this
        }

        /**
         * Set close description
         */
        fun setDescription(description: String?): Builder {
            this.description = description
            return this
        }

        /**
         * Set request timeout
         */
        fun setRequestTimeout(requestTimeout: Long): Builder {
            this.requestTimeout = requestTimeout
            return this
        }

        /**
         * Build BatchCloseRequest instance
         * 
         * @throws TransactionRequestValidationException if validation fails
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