package com.sunmi.tapro.taplink.sdk.model.request.transaction

import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import java.math.BigDecimal

/**
 * Transaction Request Validator
 *
 * Provides common validation methods for validating various fields of transaction requests
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object TransactionRequestValidator {

    /**
     * Validate transaction request ID
     *
     * @param transactionRequestId Transaction request ID
     * @return ValidationResult validation result
     */
    fun validateTransactionRequestId(transactionRequestId: String?): ValidationResult {
        return if (transactionRequestId.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingTransactionRequestId)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * Validate reference order ID
     *
     * @param referenceOrderId Reference order ID
     * @return ValidationResult validation result
     */
    fun validateReferenceOrderId(referenceOrderId: String?): ValidationResult {
        return if (referenceOrderId.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingReferenceOrderId)
        } else if (referenceOrderId.length < 6 || referenceOrderId.length > 32) {
            ValidationResult.failure(ValidationError.CustomError("referenceOrderId must be 6-32 characters"))
        } else if (!referenceOrderId.matches(Regex("^[a-zA-Z0-9_\\-|*]+$"))) {
            ValidationResult.failure(ValidationError.CustomError("referenceOrderId can only contain letters, numbers, _, -, |, *"))
        } else {
            ValidationResult.success()
        }
    }

    /**
     * Validate amount information
     *
     * @param amount Amount information
     * @return ValidationResult validation result
     */
    fun validateAmount(amount: AmountInfo?): ValidationResult {
        return if (amount == null) {
            ValidationResult.failure(ValidationError.MissingAmount)
        } else if (amount.orderAmount <= BigDecimal.ZERO) {
            ValidationResult.failure(ValidationError.InvalidAmount)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * Validate description information
     *
     * @param description Description information
     * @return ValidationResult validation result
     */
    fun validateDescription(description: String?): ValidationResult {
        return if (description.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingDescription)
        } else if (description.length > 128) {
            ValidationResult.failure(ValidationError.CustomError("description must not exceed 128 characters"))
        } else {
            ValidationResult.success()
        }
    }

    /**
     * Validate original transaction reference
     *
     * @param originalTransactionId Original transaction ID
     * @param originalTransactionRequestId Original transaction request ID
     * @return ValidationResult validation result
     */
    fun validateOriginalTransactionReference(
        originalTransactionId: String?,
        originalTransactionRequestId: String?
    ): ValidationResult {
        return if (originalTransactionId.isNullOrBlank() && originalTransactionRequestId.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingOriginalTransaction)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * Validate original transaction reference
     *
     * @param originalTransactionRequestId Original transaction request ID
     * @return ValidationResult validation result
     */
    fun validateOriginalTransactionReference(
        originalTransactionRequestId: String?
    ): ValidationResult {
        return if (originalTransactionRequestId.isNullOrBlank()) {
            ValidationResult.failure(ValidationError.MissingOriginalTransaction)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * Validate tip amount
     *
     * @param tipAmount Tip amount
     * @return ValidationResult validation result
     */
    fun validateTipAmount(tipAmount: Long): ValidationResult {
        return if (tipAmount < 0) {
            ValidationResult.failure(ValidationError.InvalidTipAmount)
        } else {
            ValidationResult.success()
        }
    }

    /**
     * Combine multiple validation results
     *
     * @param results Validation result list
     * @return ValidationResult combined validation result
     */
    fun combineResults(vararg results: ValidationResult): ValidationResult {
        val allErrors = results.flatMap { it.errors }
        return if (allErrors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(allErrors)
        }
    }
}