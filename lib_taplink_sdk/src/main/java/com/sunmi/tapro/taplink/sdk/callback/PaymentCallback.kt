package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * 支付回调接口
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface PaymentCallback {
    
    /**
     * 交易进度更新
     * 
     * @param event 进度事件
     */
    fun onProgress(event: PaymentEvent)
    
    /**
     * 交易成功
     * 
     * @param result 交易结果
     */
    fun onSuccess(result: PaymentResult)
    
    /**
     * 交易失败
     * 
     * @param error 错误信息
     */
    fun onFailure(error: PaymentError)
    
}



