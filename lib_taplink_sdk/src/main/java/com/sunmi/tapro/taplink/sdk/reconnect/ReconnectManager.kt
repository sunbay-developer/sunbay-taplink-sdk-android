package com.sunmi.tapro.taplink.sdk.reconnect

import android.os.Handler
import android.os.Looper
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Reconnection Manager
 * 
 * Responsible for managing automatic reconnection logic, including:
 * - Reconnection state management
 * - Retry counting
 * - Delayed reconnection scheduling
 * 
 * @author TaPro Team
 * @since 2025-12-11
 */
class ReconnectManager(
    private val config: TaplinkConfig,
    private val reconnectAction: (ConnectionConfig?, ConnectionListener) -> Unit
) {
    private val TAG = "ReconnectManager"
    
    /**
     * Whether it's a manual disconnection
     */
    private var isManualDisconnect = false
    
    /**
     * Current retry count
     */
    private var currentRetryCount = 0
    
    /**
     * Last connection configuration (for reconnection)
     */
    private var lastConnectionConfig: ConnectionConfig? = null
    
    /**
     * Last connection listener (for reconnection)
     */
    private var lastConnectionListener: ConnectionListener? = null
    
    /**
     * Reconnection handler
     */
    private val retryHandler = Handler(Looper.getMainLooper())
    
    /**
     * Prepare connection
     * 
     * Save connection parameters, reset reconnection state
     * 
     * @param config Connection configuration
     * @param listener Connection listener
     */
    fun prepareConnect(config: ConnectionConfig?, listener: ConnectionListener) {
        lastConnectionConfig = config
        lastConnectionListener = listener
        isManualDisconnect = false
        currentRetryCount = 0
    }
    
    /**
     * Connection successful
     * 
     * Reset retry count
     */
    fun onConnected() {
        currentRetryCount = 0
    }
    
    /**
     * Connection disconnected
     * 
     * Determine whether automatic reconnection is needed
     * 
     * @param listener Connection listener
     * @return Boolean Whether automatic reconnection was triggered
     */
    fun onDisconnected(listener: ConnectionListener): Boolean {
        val shouldAutoReconnect = config.autoReconnect && 
                                  !isManualDisconnect && 
                                  currentRetryCount < config.maxRetryCount
        
        if (shouldAutoReconnect) {
            currentRetryCount++
            LogUtil.d(TAG, "Connection lost, attempting reconnect ($currentRetryCount/${config.maxRetryCount})")
            
            // Notify listeners that reconnection is in progress
            listener.onReconnecting(currentRetryCount, config.maxRetryCount)
            lastConnectionListener?.onReconnecting(currentRetryCount, config.maxRetryCount)
            
            // Delayed reconnection
            retryHandler.postDelayed({
                attemptReconnect()
            }, config.retryDelayMs)
            
            return true
        }
        
        if (!isManualDisconnect && currentRetryCount >= config.maxRetryCount) {
            LogUtil.e(TAG, "Max retry attempts reached, giving up reconnection")
        }
        
        return false
    }
    
    /**
     * Manual disconnection
     * 
     * Mark as manual disconnection, cancel all retry tasks
     */
    fun disconnect() {
        LogUtil.d(TAG, "Manual disconnect")
        isManualDisconnect = true
        currentRetryCount = 0
        retryHandler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Attempt to reconnect
     * 
     * Reconnect using last saved connection configuration and listener
     */
    private fun attemptReconnect() {
        LogUtil.d(TAG, "Attempting to reconnect...")
        
        val config = lastConnectionConfig
        val listener = lastConnectionListener
        
        if (listener != null) {
            // Save current reconnection state
            val savedRetryCount = currentRetryCount
            val savedManualDisconnect = isManualDisconnect
            
            // Reconnect
            reconnectAction(config, listener)
            
            // Restore reconnection state (because reconnectAction might reset these values)
            currentRetryCount = savedRetryCount
            isManualDisconnect = savedManualDisconnect
        } else {
            LogUtil.e(TAG, "Cannot reconnect: no listener available")
        }
    }
    
    /**
     * Whether it's a manual disconnection
     */
    fun isManualDisconnect(): Boolean = isManualDisconnect
    
    /**
     * Get current retry count
     */
    fun getCurrentRetryCount(): Int = currentRetryCount
}
