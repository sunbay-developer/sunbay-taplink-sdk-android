package com.sunmi.tapro.taplink.sdk.error

/**
 * Connection error
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ConnectionError(
    /** Error code */
    val code: String,
    
    /** Error description */
    val message: String,
    
    /** Device ID (if available) */
    val deviceId: String? = null,
    
    /** Handling suggestion */
    val suggestion: String? = null
)



