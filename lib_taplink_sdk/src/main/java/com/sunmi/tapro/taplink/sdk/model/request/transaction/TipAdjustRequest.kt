package com.sunmi.tapro.taplink.sdk.model.request.transaction

import java.math.BigDecimal

/**
 * Tip Adjustment Transaction Request
 *
 * Used to adjust the tip amount for a completed transaction, does not contain order-related fields
 *
 * @param originalTransactionId Original transaction ID (either this or originalTransactionRequestId must be provided)
 * @param originalTransactionRequestId Original transaction request ID (either this or originalTransactionId must be provided)
 * @param tipAmount Tip amount (required, non-negative, using basic currency unit)
 * @param attach Additional information (optional)
 * @param requestTimeout Request timeout duration (optional, unit: seconds)
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TipAdjustRequest(
    val originalTransactionId: String? = null,
    val originalTransactionRequestId: String? = null,
    val tipAmount: BigDecimal,
    val attach: String? = null,
    val requestTimeout: Long? = null
) : BaseTransactionRequest() {

    init {
        require(originalTransactionId != null || originalTransactionRequestId != null) {
            "Either originalTransactionId or originalTransactionRequestId must be provided"
        }
        require(tipAmount >= BigDecimal.ZERO) {
            "Tip amount must be non-negative"
        }
    }

    override fun validate(): ValidationResult {
        return TransactionRequestValidator.combineResults(
            TransactionRequestValidator.validateOriginalTransactionReference(
                originalTransactionId, 
                originalTransactionRequestId
            ),
            validateTipAmount(tipAmount)
        )
    }

    private fun validateTipAmount(tipAmount: BigDecimal): ValidationResult {
        return if (tipAmount < BigDecimal.ZERO) {
            ValidationResult.failure(ValidationError.InvalidTipAmount)
        } else {
            ValidationResult.success()
        }
    }

    companion object {
        /**
         * Create TipAdjustRequest builder
         */
        fun builder(): Builder = Builder()
    }

    /**
     * TipAdjustRequest Builder
     */
    class Builder {
        private var originalTransactionId: String? = null
        private var originalTransactionRequestId: String? = null
        private var tipAmount: BigDecimal? = null
        private var attach: String? = null
        private var requestTimeout: Long? = null

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
         * Set tip amount
         */
        fun setTipAmount(tipAmount: BigDecimal): Builder {
            this.tipAmount = tipAmount
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
         * Build TipAdjustRequest instance
         * 
         * @throws TransactionRequestValidationException If validation fails
         */
        fun build(): TipAdjustRequest {
            val request = TipAdjustRequest(
                originalTransactionId = originalTransactionId,
                originalTransactionRequestId = originalTransactionRequestId,
                tipAmount = requireNotNull(tipAmount) { "tipAmount is required" },
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