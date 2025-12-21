package com.sunmi.tapro.taplink.sdk.enums

/**
 * 连接模式枚举
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class ConnectionMode {
    /** 不启用 */
    NONE,
    
    /** 同设备 Intent */
    APP_TO_APP,

    /**
     * 线缆模式
     */
    CABLE,
    
    /** WebSocket（LAN/WLAN） */
    LAN
}



