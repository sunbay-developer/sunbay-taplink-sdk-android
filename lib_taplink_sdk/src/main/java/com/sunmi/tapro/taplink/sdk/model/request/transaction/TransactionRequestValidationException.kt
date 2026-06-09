package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * Transaction Request Validation Exception
 *
 * Exception thrown when transaction request validation fails
 *
 * @param errors List of validation errors
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TransactionRequestValidationException(
    val errors: List<ValidationError>
) : IllegalArgumentException("Validation failed: ${errors.joinToString(", ") { it.message }}") {
    
    /**
     * Constructor that accepts a single validation error
     * 
     * @param error Validation error
     */
    constructor(error: ValidationError) : this(listOf(error))
    
    /**
     * Constructor that accepts a validation result
     * 
     * @param validationResult Validation result
     */
    constructor(validationResult: ValidationResult) : this(validationResult.errors) {
        require(!validationResult.isValid) { "Cannot create exception from successful validation result" }
    }
    
    /**
     * Get the first error message
     */
    fun getFirstErrorMessage(): String? = errors.firstOrNull()?.message
    
    /**
     * Get all error messages
     */
    fun getAllErrorMessages(): List<String> = errors.map { it.message }
}