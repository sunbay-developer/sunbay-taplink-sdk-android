package com.sunmi.tapro.taplink.sdk.enums

/**
 * Connection Status Enum
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class ConnectionStatus {
    /** Disconnected */
    DISCONNECTED,
    
    /** Connecting */
    CONNECTING,

    /**
     * Waiting for connection
     */
    WAIT_CONNECTING,
    
    /** Connected */
    CONNECTED,
    
    /** Connection error */
    ERROR
}



