package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.ConnectionError

/**
 * Connection Status Listener
 * 
 * Real-time monitoring of connection status changes, disconnection reconnection and other events.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface ConnectionListener {
    
    /**
     * Connection successful
     * 
     * @param deviceId Device identifier
     * @param taproVersion Tapro version number
     */
    fun onConnected(deviceId: String, taproVersion: String)
    
    /**
     * Connection disconnected
     *
     * @param reason Disconnection reason
     */
    fun onDisconnected(reason: String)
    
    /**
     * Connection error
     * 
     * @param error Error information
     */
    fun onError(error: ConnectionError)
    
    /**
     * Reconnecting (optional implementation)
     * 
     * When auto-reconnect is enabled, SDK will call this method before reconnecting
     * 
     * @param attempt Current retry count
     * @param maxRetries Maximum retry count
     */
    fun onReconnecting(attempt: Int, maxRetries: Int) {
        // Default empty implementation, subclasses can optionally override
    }
    
}



