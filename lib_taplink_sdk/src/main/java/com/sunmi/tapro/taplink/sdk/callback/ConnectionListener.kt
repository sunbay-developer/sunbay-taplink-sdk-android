package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.ConnectionError

/**
 * Connection status listener
 * 
 * Real-time monitoring of connection status changes, disconnection and reconnection events.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface ConnectionListener {
    
    /**
     * Connection successful
     *
     * Called when the physical transport is established and the SDK is ready to
     * accept transactions.
     *
     * **Important:** [deviceId] and [taproVersion] are placeholder values (`"unknown"`)
     * at this point. The real device ID and Tapro version are exchanged during INIT,
     * which Tapro performs on the first transaction. To read the actual values, call
     * [TaplinkSDK.getConnectedDeviceId] and [TaplinkSDK.getTaproVersion] after the
     * first transaction completes.
     *
     * @param deviceId Device identifier — placeholder until first transaction
     * @param taproVersion Tapro version number — placeholder until first transaction
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
     * When auto-reconnection is enabled, SDK will call this method before reconnecting
     * 
     * @param attempt Current retry attempt number
     * @param maxRetries Maximum retry attempts
     */
    fun onReconnecting(attempt: Int, maxRetries: Int) {
        // Default empty implementation, subclasses can optionally override
    }
    
}



