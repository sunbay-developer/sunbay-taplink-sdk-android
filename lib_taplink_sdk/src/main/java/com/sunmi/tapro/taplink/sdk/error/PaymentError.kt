package com.sunmi.tapro.taplink.sdk.error

/**
 * 交易错误
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentError(
    /** 错误码（如：E01, E10, E30） */
    val code: String,
    
    /** 错误描述 */
    val message: String,
    
    /** 订单号 */
    val orderNo: String? = null,
    
    /** 交易 ID（如果有） */
    val transactionId: String? = null,
    
    /** 处理建议 */
    val suggestion: String? = null
)



