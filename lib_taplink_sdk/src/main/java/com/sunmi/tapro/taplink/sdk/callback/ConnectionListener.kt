package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.ConnectionError

/**
 * Connection status listener
 * 
 * Real-time monitoring of connection status changes, disconnection and reconnection events.
 * 
 * @author Tapro Team
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
     * Connection disconnected.
     *
     * When a connection that was ALREADY established drops abnormally, the SDK first runs exactly
     * one silent internal recovery attempt (same protocol, same terminal). No progress, error or
     * transient disconnect callback is delivered during that attempt. Only if recovery ultimately
     * fails is a single final [onDisconnected] delivered, with error code 213. If recovery succeeds,
     * [onConnected] is delivered instead and this method is not called for that fault. The SDK does
     * not remove a registered listener during recovery.
     *
     * Callbacks may run on a background thread; dispatch UI updates to the main thread.
     *
     * @param reason Disconnection reason
     */
    fun onDisconnected(reason: String)
    
    /**
     * Connection error.
     *
     * [onError] is scoped to an explicit [connect] attempt only. A fault on a connection that was
     * already established is NEVER reported via [onError]; the SDK absorbs it, runs one silent
     * recovery, and reports the outcome through [onConnected] (success) or a single
     * [onDisconnected] with code 213 (failure).
     *
     * During LAN handshake recovery with automatic reconnect enabled, a failed endpoint is reported
     * only if recovery ultimately fails. A recovered connection calls [onConnected] without an
     * intermediate error or disconnect notification.
     * Callbacks may run on a background thread; dispatch UI updates to the main thread.
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



