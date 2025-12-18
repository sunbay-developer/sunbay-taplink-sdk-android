package com.sunmi.tapro.taplink.sdk.model.response

/**
 * Connection Result
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ConnectionResult(
    /** Whether successful */
    val success: Boolean,
    
    /** Device identifier */
    val deviceId: String? = null,
    
    /** Device name */
    val deviceName: String? = null,
    
    /** Tapro version number */
    val taproVersion: String? = null,
    
    /** Error code (when failed) */
    val errorCode: String? = null,
    
    /** Error message (when failed) */
    val errorMessage: String? = null
)



