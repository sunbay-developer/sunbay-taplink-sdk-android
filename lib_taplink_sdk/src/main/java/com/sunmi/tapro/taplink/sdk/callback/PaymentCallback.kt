package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * Payment callback interface
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface PaymentCallback {
    
    /**
     * Transaction progress update
     * 
     * @param event Progress event
     */
    fun onProgress(event: PaymentEvent)
    
    /**
     * Transaction success
     * 
     * @param result Transaction result
     */
    fun onSuccess(result: PaymentResult)
    
    /**
     * Transaction failure
     * 
     * @param error Error information
     */
    fun onFailure(error: PaymentError)
    
}



