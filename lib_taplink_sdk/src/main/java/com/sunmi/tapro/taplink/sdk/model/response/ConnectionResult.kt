package com.sunmi.tapro.taplink.sdk.model.response

/**
 * 连接结果
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ConnectionResult(
    /** 是否成功 */
    val success: Boolean,
    
    /** 设备标识 */
    val deviceId: String? = null,
    
    /** 设备名称 */
    val deviceName: String? = null,
    
    /** Tapro 版本号 */
    val taproVersion: String? = null,
    
    /** 错误码（失败时） */
    val errorCode: String? = null,
    
    /** 错误信息（失败时） */
    val errorMessage: String? = null
)



