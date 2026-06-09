package com.sunmi.tapro.taplink.sdk.error

/**
 * Connection error.
 *
 * Returned in [com.sunmi.tapro.taplink.sdk.callback.ConnectionListener.onError] when
 * a connection attempt fails or the active connection is lost unexpectedly.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ConnectionError(
    /**
     * Error code.
     * Common ranges: 201-214 (initialization / connection setup), 231-255 (mode-specific).
     * Legacy T-series codes (T01, T02, …) may also appear.
     */
    val code: String,
    
    /** Human-readable description of the error. */
    val message: String,
    
    /** Device ID, if the error occurred after partial device identification. */
    val deviceId: String? = null,
    
    /**
     * Remediation hint.
     * Contains a human-readable suggestion for how to recover from this error,
     * e.g., "Check USB cable connection" or "Ensure the payment terminal is powered on".
     * May be null if no specific suggestion is available.
     */
    val suggestion: String? = null
)



