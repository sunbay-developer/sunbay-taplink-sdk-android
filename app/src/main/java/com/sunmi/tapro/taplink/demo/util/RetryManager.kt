package com.sunmi.tapro.taplink.demo.util

/**
 * Simple retry manager for error classification and retry strategy determination
 * 
 * Provides basic error classification for retry decisions:
 * - Temporary errors: Can retry with same transactionRequestId
 * - Definite failures: Need new transactionRequestId to retry
 * - Configuration errors: No retry, require manual intervention
 * - Connection errors: Need reconnection
 */
object RetryManager {
    
    /**
     * Retry strategy enumeration
     */
    enum class RetryStrategy {
        SAME_ID,    // Use same transactionRequestId to retry
        NEW_ID,     // Generate new transactionRequestId to retry
        NO_RETRY    // Do not retry, manual intervention required
    }
    
    // Temporary errors - can retry with same ID
    private val TEMPORARY_ERROR_CODES = setOf(
        "E02",  // Network error
        "996",  // System timeout
        "E01",  // System busy
        "C08",  // Transaction locked
        "E10"   // Transaction timeout
    )
    
    // Definite failure errors - need new ID to retry
    private val DEFINITE_FAILURE_CODES = setOf(
//        "00V",
        "051",  // Insufficient funds
        "055",  // Incorrect PIN
        "054",  // Expired card
        "057",  // Transaction not permitted
        "091",  // Issuer unavailable
        "100"   // Declined
    )
    
    // Configuration errors - no retry
    private val CONFIG_ERROR_CODES = setOf(
        "S03",  // Signature verification failed
        "C20",  // SDK not initialized
        "M02",  // App not found
        "M03",  // App merchant mismatch
        "S06"   // Permission denied
    )
    
    // Connection errors - retry after reconnection
    private val CONNECTION_ERROR_CODES = setOf(
        "C22",  // Tapro not installed
        "C23",  // Cable not connected
        "C30",  // LAN not connected
        "C24"   // Cable protocol error
    )
    
    /**
     * Check if error code represents a temporary error
     */
    fun isTemporaryError(errorCode: String): Boolean {
        return errorCode in TEMPORARY_ERROR_CODES
    }
    
    /**
     * Check if error code represents a definite failure
     */
    fun isDefiniteFailure(errorCode: String): Boolean {
        return errorCode in DEFINITE_FAILURE_CODES
    }
    
    /**
     * Check if error code represents a configuration error
     */
    fun isConfigurationError(errorCode: String): Boolean {
        return errorCode in CONFIG_ERROR_CODES
    }
    
    /**
     * Check if error code represents a connection error
     */
    fun isConnectionError(errorCode: String): Boolean {
        return errorCode in CONNECTION_ERROR_CODES
    }
    
    /**
     * Determine retry strategy based on error code
     */
    fun getRetryStrategy(errorCode: String): RetryStrategy {
        return when {
            isTemporaryError(errorCode) -> RetryStrategy.SAME_ID
            isDefiniteFailure(errorCode) -> RetryStrategy.NEW_ID
            isConfigurationError(errorCode) -> RetryStrategy.NO_RETRY
            isConnectionError(errorCode) -> RetryStrategy.NO_RETRY
            else -> RetryStrategy.NEW_ID // Default strategy for unknown errors
        }
    }
    
    /**
     * Check if the error is retryable (either SAME_ID or NEW_ID)
     */
    fun isRetryable(errorCode: String): Boolean {
        val strategy = getRetryStrategy(errorCode)
        return strategy == RetryStrategy.SAME_ID || strategy == RetryStrategy.NEW_ID
    }
    
    /**
     * Get user-friendly error description
     */
    fun getErrorDescription(errorCode: String): String {
        return when {
            isTemporaryError(errorCode) -> "Temporary network or system error. Please try again."
            isDefiniteFailure(errorCode) -> when (errorCode) {
                "051" -> "Insufficient funds. Please check card balance."
                "055" -> "Incorrect PIN. Please try again."
                "054" -> "Card has expired. Please use a different card."
                "057" -> "Transaction not permitted. Please contact your bank."
                "091" -> "Bank system unavailable. Please try again later."
                "100" -> "Transaction declined. Please contact your bank."
                else -> "Transaction failed. Please try again."
            }
            isConfigurationError(errorCode) -> when (errorCode) {
                "S03" -> "Configuration error. Please check app settings."
                "C20" -> "System not initialized. Please restart the app."
                "M02" -> "App configuration error. Please contact support."
                "M03" -> "Merchant configuration error. Please contact support."
                "S06" -> "Permission denied. Please contact administrator."
                else -> "Configuration error. Please contact support."
            }
            isConnectionError(errorCode) -> when (errorCode) {
                "C22" -> "Payment app not installed. Please install Tapro."
                "C23" -> "Cable not connected. Please check connection."
                "C30" -> "Network not connected. Please check network."
                "C24" -> "Connection error. Please check cable."
                else -> "Connection error. Please check connection."
            }
            else -> "Unknown error: $errorCode"
        }
    }
}