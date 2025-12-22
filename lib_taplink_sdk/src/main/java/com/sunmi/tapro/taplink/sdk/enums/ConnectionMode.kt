package com.sunmi.tapro.taplink.sdk.enums

/**
 * Connection mode enumeration
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class ConnectionMode {
    /** Not enabled */
    NONE,
    
    /** Same device Intent */
    APP_TO_APP,

    /**
     * Cable mode
     */
    CABLE,
    
    /** WebSocket (LAN/WLAN) */
    LAN
}



