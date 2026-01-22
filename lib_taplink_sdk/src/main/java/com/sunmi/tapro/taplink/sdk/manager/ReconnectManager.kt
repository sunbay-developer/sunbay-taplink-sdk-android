package com.sunmi.tapro.taplink.sdk.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.persistence.ConnectionPersistence
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Enhanced reconnect manager with persistence support
 *
 * Manages automatic reconnection logic with persistent connection configuration:
 * - Reconnection state management
 * - Retry counting
 * - Delayed reconnection scheduling
 * - Connection configuration persistence
 * - Device ID based auto-discovery
 *
 * @author TaPro Team
 * @since 2025-12-21
 */
class ReconnectManager(
    private val context: Context,
    private val reconnectAction: (ConnectionConfig?, ConnectionListener) -> Unit
) {
    private val TAG = "ReconnectManager"

    /**
     * Connection persistence manager
     */
    private val connectionPersistence = ConnectionPersistence(context)

    /**
     * Whether this is a manual disconnect
     */
    private var isManualDisconnect = false

    /**
     * Current retry count
     */
    private var currentRetryCount = 0

    /**
     * Whether currently reconnecting
     */
    private var isReconnecting = false

    /**
     * Last connection config (for reconnection)
     */
    private var lastConnectionConfig: ConnectionConfig? = null

    /**
     * Last connection listener (for reconnection)
     */
    private var lastConnectionListener: ConnectionListener? = null

    /**
     * List of connection listeners waiting for reconnection
     * Used when user calls connect() with same config while reconnecting
     */
    private val pendingReconnectionListeners = mutableListOf<ConnectionListener>()

    /**
     * Reconnection handler
     */
    private val retryHandler = Handler(Looper.getMainLooper())

    /**
     * Prepare connection
     *
     * Save connection parameters, reset reconnection state
     * When user manually calls connect(), reset all states to allow fresh connection
     *
     * @param config Connection configuration
     * @param listener Connection listener
     */
    fun prepareConnect(config: ConnectionConfig?, listener: ConnectionListener) {
        LogUtil.d(TAG, "Preparing connection, resetting reconnect state")
        lastConnectionConfig = config
        lastConnectionListener = listener
        isManualDisconnect = false
        currentRetryCount = 0
        isReconnecting = false

        // Clear pending listeners
        pendingReconnectionListeners.clear()

        // Cancel all pending reconnection tasks
        retryHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Connection successful
     *
     * Reset retry count and reconnection state
     * Save connection configuration for future auto-reconnection
     * Notify all pending listeners
     *
     * @param deviceId Connected device ID
     * @param taproVersion Tapro version (optional)
     */
    fun onConnected(deviceId: String? = null, taproVersion: String? = null) {
        currentRetryCount = 0
        isReconnecting = false
        LogUtil.d(TAG, "Connection established, reset reconnect state")

        // Save connection configuration for persistence
        if (lastConnectionConfig != null) {
            connectionPersistence.saveConnectionConfig(lastConnectionConfig, deviceId)
            LogUtil.d(TAG, "Saved connection config for future auto-reconnection")
        }

        // Enable auto-connect for future app launches
        connectionPersistence.setAutoConnectEnabled(true)

        // Notify all pending listeners
        if (pendingReconnectionListeners.isNotEmpty()) {
            val deviceIdStr = deviceId ?: "unknown"
            val taproVersionStr = taproVersion ?: "unknown"
            notifyPendingListenersSuccess(deviceIdStr, taproVersionStr)
        }
    }

    /**
     * Connection disconnected
     *
     * Determine whether to auto-reconnect
     *
     * @param listener Connection listener
     * @return Boolean whether auto-reconnection was triggered
     */
    fun onDisconnected(listener: ConnectionListener): Boolean {
        // Prevent duplicate reconnection triggers
        if (isReconnecting) {
            LogUtil.w(TAG, "Already reconnecting, ignoring duplicate onDisconnected call")
            return true // Return true to indicate reconnection is being handled
        }

        val shouldAutoReconnect = lastConnectionConfig?.autoReconnect == true &&
                !isManualDisconnect &&
                currentRetryCount < (lastConnectionConfig?.maxRetryCount ?: 3)

        if (shouldAutoReconnect) {
            isReconnecting = true
            currentRetryCount++
            LogUtil.d(
                TAG,
                "Connection lost, attempting reconnect ($currentRetryCount/${lastConnectionConfig?.maxRetryCount ?: 3})"
            )

            // Notify listener of reconnection attempt
            listener.onReconnecting(currentRetryCount, lastConnectionConfig?.maxRetryCount ?: 3)
            lastConnectionListener?.onReconnecting(currentRetryCount, lastConnectionConfig?.maxRetryCount ?: 3)

            // Delayed reconnection
            retryHandler.postDelayed({
                attemptReconnect()
            }, lastConnectionConfig?.retryDelayMs ?: 2000L)

            return true
        }

        // Retry limit reached or no auto-reconnect needed
        if (!isManualDisconnect && currentRetryCount >= (lastConnectionConfig?.maxRetryCount ?: 3)) {
            LogUtil.e(
                TAG,
                "Max retry attempts reached ($currentRetryCount/${lastConnectionConfig?.maxRetryCount ?: 3}), giving up auto-reconnection"
            )
            LogUtil.i(TAG, "User can manually call connect() to reconnect")
        }

        // Reset reconnection state, allow manual reconnection
        isReconnecting = false

        return false
    }

    /**
     * Manual disconnect
     *
     * Mark as manual disconnect, cancel all retry tasks
     */
    fun disconnect() {
        LogUtil.d(TAG, "Manual disconnect")
        isManualDisconnect = true
        isReconnecting = false
        currentRetryCount = 0
        retryHandler.removeCallbacksAndMessages(null)

        // Clear saved connection parameters (manual disconnect doesn't preserve)
        lastConnectionConfig = null
        lastConnectionListener = null

        // Clear pending listeners
        pendingReconnectionListeners.clear()

        // Clear persistent connection data
//        connectionPersistence.clearConnectionData()
        connectionPersistence.setAutoConnectEnabled(false)
        LogUtil.d(TAG, "Cleared saved connection config due to manual disconnect")
    }

    /**
     * Attempt reconnection
     *
     * Use last saved connection configuration and listener for reconnection
     */
    private fun attemptReconnect() {
        LogUtil.d(TAG, "Attempting to reconnect...")

        val config = lastConnectionConfig
        val listener = lastConnectionListener

        if (listener != null) {
            // Save current reconnection state
            val savedRetryCount = currentRetryCount
            val savedManualDisconnect = isManualDisconnect
            val savedReconnecting = isReconnecting

            // Reconnect
            reconnectAction(config, listener)

            // Restore reconnection state (because reconnectAction might reset these values)
            currentRetryCount = savedRetryCount
            isManualDisconnect = savedManualDisconnect
            isReconnecting = savedReconnecting
        } else {
            LogUtil.e(TAG, "Cannot reconnect: no listener available")
            isReconnecting = false
        }
    }

    /**
     * Whether this is a manual disconnect
     */
    fun isManualDisconnect(): Boolean = isManualDisconnect

    /**
     * Get current retry count
     */
    fun getCurrentRetryCount(): Int = currentRetryCount

    /**
     * Check if currently reconnecting
     *
     * @return Boolean true if currently reconnecting, false otherwise
     */
    fun isReconnecting(): Boolean {
        return isReconnecting
    }

    /**
     * Get the connection config being used for reconnection
     *
     * @return ConnectionConfig being used for reconnection, or null if not reconnecting
     */
    fun getReconnectingConfig(): ConnectionConfig? {
        return if (isReconnecting) {
            lastConnectionConfig
        } else {
            null
        }
    }

    /**
     * Add a connection listener to be notified when reconnection completes
     *
     * This is used when user calls connect() with same config while reconnecting
     *
     * @param listener Connection listener to add
     */
    fun addReconnectionListener(listener: ConnectionListener) {
        if (isReconnecting) {
            pendingReconnectionListeners.add(listener)
            LogUtil.d(TAG, "Added listener to pending reconnection listeners, total: ${pendingReconnectionListeners.size}")
        } else {
            LogUtil.w(TAG, "Not reconnecting, cannot add reconnection listener")
        }
    }

    /**
     * Notify all pending listeners of connection success
     *
     * @param deviceId Connected device ID
     * @param taproVersion Tapro version
     */
    private fun notifyPendingListenersSuccess(deviceId: String, taproVersion: String) {
        if (pendingReconnectionListeners.isEmpty()) {
            return
        }

        LogUtil.d(TAG, "Notifying ${pendingReconnectionListeners.size} pending listeners of connection success")
        val listeners = pendingReconnectionListeners.toList()
        pendingReconnectionListeners.clear()

        listeners.forEach { listener ->
            try {
                listener.onConnected(deviceId, taproVersion)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error notifying pending listener: ${e.message}")
            }
        }
    }

    /**
     * Notify all pending listeners of connection error
     *
     * @param error Connection error
     */
    fun notifyPendingListenersError(error: ConnectionError) {
        if (pendingReconnectionListeners.isEmpty()) {
            return
        }

        LogUtil.d(TAG, "Notifying ${pendingReconnectionListeners.size} pending listeners of connection error")
        val listeners = pendingReconnectionListeners.toList()
        pendingReconnectionListeners.clear()

        listeners.forEach { listener ->
            try {
                listener.onError(error)
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error notifying pending listener: ${e.message}")
            }
        }
    }

    /**
     * Get last connection configuration
     *
     * Used when no ConnectionConfig is passed to use last connection parameters
     * First tries in-memory config, then falls back to persistent storage
     *
     * @return Last connection configuration, or null if none available
     */
    fun getLastConnectionConfig(): ConnectionConfig? {
        // First try in-memory config
        if (lastConnectionConfig != null) {
            LogUtil.d(TAG, "Using in-memory connection config")
            return lastConnectionConfig
        }

        // Fall back to persistent storage
        val persistentConfig = connectionPersistence.getLastConnectionConfig()
        if (persistentConfig != null) {
            LogUtil.d(TAG, "Using persistent connection config")
            lastConnectionConfig = persistentConfig // Cache for future use
            return persistentConfig
        }

        LogUtil.d(TAG, "No connection config available")
        return null
    }

    /**
     * Handle service address change
     *
     * When mDNS detects service address change, update connection config and trigger reconnection
     *
     * @param newConnectionConfig New connection configuration
     * @param listener Connection listener
     */
    fun onAddressChanged(newConnectionConfig: ConnectionConfig, listener: ConnectionListener) {
        LogUtil.i(
            TAG,
            "Service address changed, updating connection config and reconnecting: $newConnectionConfig,$lastConnectionConfig"
        )

        // Update saved connection configuration
        lastConnectionConfig = newConnectionConfig

        // Update persistent storage
        val deviceId = connectionPersistence.getLastConnectedDeviceId()
        connectionPersistence.saveConnectionConfig(newConnectionConfig, deviceId)

        // Reset reconnection state, as this is address change reconnection, not failure retry
        currentRetryCount = 0
        isManualDisconnect = false
        isReconnecting = true

        // Cancel existing reconnection tasks
        retryHandler.removeCallbacksAndMessages(null)

        // Notify listener of reconnection (address change)
        listener.onReconnecting(0, lastConnectionConfig?.maxRetryCount ?: 3)
        lastConnectionListener?.onReconnecting(0, lastConnectionConfig?.maxRetryCount ?: 3)

        // Immediately attempt connection to new address (no delay)
        LogUtil.d(TAG, "Attempting to connect to new address immediately")
        reconnectAction(newConnectionConfig, listener)
    }

    /**
     * Check if auto-connect should be performed on app startup
     *
     * @return True if auto-connect should be performed
     */
    fun shouldAutoConnect(): Boolean {
        val enabled = connectionPersistence.isAutoConnectEnabled()
        val hasConfig = connectionPersistence.getLastConnectionConfig() != null

        LogUtil.d(TAG, "Auto-connect check: enabled=$enabled, hasConfig=$hasConfig")
        return enabled && hasConfig
    }

    /**
     * Get auto-connect configuration
     *
     * @return ConnectionConfig for auto-connect, or null if not available
     */
    fun getAutoConnectConfig(): ConnectionConfig? {
        return if (shouldAutoConnect()) {
            connectionPersistence.getLastConnectionConfig()
        } else {
            null
        }
    }

    /**
     * Get last connected device ID
     *
     * @return Last connected device ID, or null if not available
     */
    fun getLastConnectedDeviceId(): String? {
        return connectionPersistence.getLastConnectedDeviceId()
    }

    /**
     * Update device service information for mDNS discovery
     *
     * @param deviceId Device ID
     * @param serviceName Service name
     * @param host Host/IP address
     * @param port Port number
     */
    fun updateDeviceServiceInfo(deviceId: String, serviceName: String, host: String, port: Int) {
        val deviceServiceInfo = ConnectionPersistence.DeviceServiceInfo(
            deviceId = deviceId,
            serviceName = serviceName,
            host = host,
            port = port
        )
        connectionPersistence.saveDeviceServiceInfo(deviceServiceInfo)
        LogUtil.d(TAG, "Updated device service info: $deviceServiceInfo")
    }

    /**
     * Get device service information
     *
     * @param deviceId Device ID
     * @return Device service information, or null if not found
     */
    fun getDeviceServiceInfo(deviceId: String): ConnectionPersistence.DeviceServiceInfo? {
        return connectionPersistence.getDeviceServiceInfo(deviceId)
    }

    /**
     * Get Android context
     *
     * Used for cable protocol detection and other context-dependent operations
     *
     * @return Android context
     */
    fun getContext(): Context {
        return context
    }
}
