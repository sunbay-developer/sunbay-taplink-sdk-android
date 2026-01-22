package com.sunmi.tapro.taplink.communication.enums

import com.sunmi.tapro.taplink.communication.util.ErrorStringHelper

/**
 * Error code category enumeration
 *
 * Classifies errors based on error code ranges for easier error handling and statistics
 *
 * Category descriptions:
 * - INITIALIZATION: 20x - SDK initialization related errors
 * - CONNECTION: 21x, 23x, 24x, 25x - Connection status and connection failure errors
 * - AUTHENTICATION: 221 - Authentication failure errors
 * - TRANSACTION: 30x - Transaction processing related errors
 *
 * @author TaPro Team
 * @since 2025-01-14
 */
enum class ErrorCategory {
    /** Initialization error */
    INITIALIZATION,
    
    /** Connection error */
    CONNECTION,
    
    /** Authentication error */
    AUTHENTICATION,
    
    /** Transaction error */
    TRANSACTION,
    
    /** Unknown error */
    UNKNOWN;
    
    /**
     * Get category display name (retrieved from resource files)
     */
    val displayName: String
        get() = ErrorStringHelper.getCategoryDisplayName(name)
    
    /**
     * Get category description (retrieved from resource files)
     */
    val description: String
        get() = ErrorStringHelper.getCategoryDescription(name)
    
    companion object {
        /**
         * Get the corresponding error category based on error code
         *
         * @param code Error code (string format, e.g., "201")
         * @return Error category
         */
        fun fromCode(code: String?): ErrorCategory {
            if (code.isNullOrBlank()) {
                return UNKNOWN
            }
            
            // Process numeric error codes
            val codeInt = code.toIntOrNull() ?: return UNKNOWN
            
            // Determine category based on error code range
            return when {
                // Initialization errors (20x, excluding 204 and 209, which have been moved to cable mode)
                codeInt in 201..203 -> INITIALIZATION
                
                // Connection errors (21x, 23x, 24x, 25x, 27x, 28x)
                codeInt in 211..220 -> CONNECTION  // General connection management errors
                codeInt in 231..250 -> CONNECTION  // APP_TO_APP mode errors
                codeInt in 251..280 -> CONNECTION  // LAN mode errors
                codeInt in 281..310 -> CONNECTION  // CABLE mode errors
                
                // Authentication errors (221)
                codeInt == 221 -> AUTHENTICATION
                
                // Transaction errors (30x)
                codeInt in 301..399 -> TRANSACTION
                
                // Other cases
                else -> UNKNOWN
            }
        }
    }
}
