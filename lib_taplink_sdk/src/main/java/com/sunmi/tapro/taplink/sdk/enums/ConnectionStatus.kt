package com.sunmi.tapro.taplink.sdk.enums

/**
 * 连接状态枚举
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class ConnectionStatus {
    /** 未连接 */
    DISCONNECTED,
    
    /** 连接中 */
    CONNECTING,

    /**
     * 等待连接中
     */
    WAIT_CONNECTING,
    
    /** 已连接 */
    CONNECTED,
    
    /** 连接错误 */
    ERROR
}



