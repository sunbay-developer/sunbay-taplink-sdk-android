package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * Validation Result Class
 *
 * Contains whether validation succeeded and specific error information
 *
 * @param isValid Whether validation passed
 * @param errors Error list, containing specific error information when validation fails
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError> = emptyList()
) {
    companion object {
        /**
         * Create successful validation result
         */
        fun success(): ValidationResult = ValidationResult(true)
        
        /**
         * Create failed validation result
         * 
         * @param errors Error list
         */
        fun failure(vararg errors: ValidationError): ValidationResult = 
            ValidationResult(false, errors.toList())
        
        /**
         * Create failed validation result
         * 
         * @param errors Error list
         */
        fun failure(errors: List<ValidationError>): ValidationResult = 
            ValidationResult(false, errors)
    }
    
    /**
     * Get error message string
     */
    fun getErrorMessage(): String = errors.joinToString(", ") { it.message }
}