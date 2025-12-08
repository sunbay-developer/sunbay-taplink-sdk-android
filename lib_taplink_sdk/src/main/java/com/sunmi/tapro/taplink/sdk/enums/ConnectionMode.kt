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
    
    /** USB AOA 协议 */
    USB_AOA,
    
    /** USB 虚拟串口 */
    USB_VSP,
    
    /** RS232 串口 */
    RS232,
    
    /** WebSocket（LAN/WLAN） */
    WEBSOCKET
}



