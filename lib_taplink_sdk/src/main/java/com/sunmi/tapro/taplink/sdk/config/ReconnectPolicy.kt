package com.sunmi.tapro.taplink.sdk.config

/**
 * Reconnect Policy Configuration Object
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ReconnectPolicy(
    /** Whether to enable auto reconnect, default true */
    val enabled: Boolean = true,
    
    /** Maximum reconnect attempts, default 3 */
    val maxAttempts: Int = 3,
    
    /** Initial reconnect delay (milliseconds), default 0 (immediate) */
    val initialDelay: Long = 0,
    
    /** Reconnect interval (milliseconds), default 3000 */
    val retryDelay: Long = 3000,
    
    /** Whether to use exponential backoff, default true */
    val exponentialBackoff: Boolean = true,
    
    /** Maximum reconnect interval (milliseconds), default 10000 */
    val maxDelay: Long = 10000
)



