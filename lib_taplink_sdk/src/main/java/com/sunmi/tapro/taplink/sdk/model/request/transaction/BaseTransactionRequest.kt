package com.sunmi.tapro.taplink.sdk.model.request.transaction

/**
 * Base Transaction Request
 *
 * Abstract base class for all transaction request classes, providing common validation interface
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class BaseTransactionRequest {

    /**
     * Validate request parameters
     *
     * @return ValidationResult containing validation status and error list
     */
    abstract fun validate(): ValidationResult
}