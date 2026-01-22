package com.sunmi.tapro.taplink.communication.interfaces

/**
 * Internal callback interface
 *
 * Used for callback communication between internal service layer and SDK layer
 * Provides both success response and error response callback methods
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface InnerCallback {
    /**
     * Error callback
     *
     * @param code Error code
     * @param msg Error message
     */
    fun onError(code: String, msg: String)

    /**
     * Response callback
     *
     * @param responseData Response data (JSON string)
     */
    fun onResponse(responseData: String)
}



