package com.sunmi.tapro.taplink.sdk.enums

/**
 * Connection Mode Enum
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class ConnectionMode {
    /** Not enabled */
    NONE,
    
    /** Same device Intent */
    APP_TO_APP,
    
    /** USB AOA protocol */
    USB_AOA,
    
    /** USB virtual serial port */
    USB_VSP,
    
    /** RS232 serial port */
    RS232,
    
    /** WebSocket (LAN/WLAN) */
    LAN
}



