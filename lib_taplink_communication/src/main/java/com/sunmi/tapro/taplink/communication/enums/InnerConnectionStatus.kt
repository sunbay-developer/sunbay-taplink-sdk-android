package com.sunmi.tapro.taplink.communication.enums

/**
 * Connection status enumeration
 * 
 * Defines various states of service connection
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class InnerConnectionStatus {
    /**
     * Disconnected
     */
    DISCONNECTED,
    
    /**
     * Connecting
     */
    CONNECTING,
    
    /**
     * Connected
     */
    CONNECTED,

    /**
     * Waiting for connection
     */
    WAITING_CONNECT,
    
    /**
     * Reconnecting
     */
    RECONNECTING,
    
    /**
     * Connection error
     */
    ERROR
}













