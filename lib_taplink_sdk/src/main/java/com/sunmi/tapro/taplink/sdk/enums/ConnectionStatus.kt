package com.sunmi.tapro.taplink.sdk.enums

/**
 * Connection status enumeration
 * 
 * @author Tapro Team
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



