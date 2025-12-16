package com.sunmi.tapro.taplink.communication.enums

/**
 * 连接状态枚举
 * 
 * 定义服务连接的各种状态
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class ConnectionStatus {
    /**
     * 未连接
     */
    DISCONNECTED,
    
    /**
     * 连接中
     */
    CONNECTING,
    
    /**
     * 已连接
     */
    CONNECTED,
    
    /**
     * 重连中
     */
    RECONNECTING,
    
    /**
     * 连接错误
     */
    ERROR
}













