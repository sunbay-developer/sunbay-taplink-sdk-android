package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * Validation Error Class
 *
 * Defines all possible validation error types
 *
 * @param message Error message
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
sealed class ValidationError(val message: String) {
    
    // Basic field validation errors
    object MissingTransactionRequestId : ValidationError("transactionRequestId is required")
    object MissingReferenceOrderId : ValidationError("referenceOrderId is required")
    object MissingAmount : ValidationError("amount is required")
    object MissingDescription : ValidationError("description is required")
    
    // Amount validation errors
    object InvalidAmount : ValidationError("amount must be positive")
    object InvalidTipAmount : ValidationError("tipAmount must be non-negative")
    
    // Original transaction reference validation errors
    object MissingOriginalTransaction : ValidationError("Either originalTransactionId or originalTransactionRequestId is required")
    
    // Refund mode validation errors
    object InvalidRefundMode : ValidationError("Refund must be either referenced or non-referenced, not both")
    
    // Custom validation errors
    data class CustomError(val customMessage: String) : ValidationError(customMessage)
}