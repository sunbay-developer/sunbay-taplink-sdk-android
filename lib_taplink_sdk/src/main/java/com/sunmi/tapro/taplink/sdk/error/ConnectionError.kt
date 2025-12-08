package com.sunmi.tapro.taplink.sdk.error

/**
 * 连接错误
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ConnectionError(
    /** 错误码 */
    val code: String,
    
    /** 错误描述 */
    val message: String,
    
    /** 设备 ID（如果有） */
    val deviceId: String? = null,
    
    /** 处理建议 */
    val suggestion: String? = null
)



