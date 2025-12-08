package com.sunmi.tapro.taplink.sdk.config

/**
 * 重连策略配置对象
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ReconnectPolicy(
    /** 是否启用自动重连，默认 true */
    val enabled: Boolean = true,
    
    /** 最大重连次数，默认 3 */
    val maxAttempts: Int = 3,
    
    /** 首次重连延迟（毫秒），默认 0（立即） */
    val initialDelay: Long = 0,
    
    /** 重连间隔（毫秒），默认 3000 */
    val retryDelay: Long = 3000,
    
    /** 是否使用指数退避，默认 true */
    val exponentialBackoff: Boolean = true,
    
    /** 最大重连间隔（毫秒），默认 10000 */
    val maxDelay: Long = 10000
)



