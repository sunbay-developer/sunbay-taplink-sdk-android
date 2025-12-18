package com.sunmi.tapro.taplink.communication.interfaces

/**
 * Inner Callback Interface
 *
 * Used for callback communication between internal service layer and SDK layer
 * Provides two callback methods: success response and error response
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



