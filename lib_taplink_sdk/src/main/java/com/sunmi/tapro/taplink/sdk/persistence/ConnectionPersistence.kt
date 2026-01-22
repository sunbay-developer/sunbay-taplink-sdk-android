package com.sunmi.tapro.taplink.sdk.persistence

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Connection configuration persistence manager
 * 
 * Handles saving and loading connection configurations for automatic reconnection
 * Supports device ID based discovery and connection parameter persistence
 * 
 * @author TaPro Team
 * @since 2025-12-21
 */
class ConnectionPersistence(private val context: Context) {
    
    private val TAG = "ConnectionPersistence"
    
    companion object {
        private const val PREFS_NAME = "taplink_connection_prefs"
        private const val KEY_LAST_CONNECTION_CONFIG = "last_connection_config"
        private const val KEY_CONNECTED_DEVICE_ID = "connected_device_id"
        private const val KEY_DEVICE_SERVICE_INFO = "device_service_info_"
        private const val KEY_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Device service information for mDNS discovery
     */
    data class DeviceServiceInfo(
        val deviceId: String,
        val serviceName: String,
        val host: String,
        val port: Int,
        val lastSeen: Long = System.currentTimeMillis()
    )
    
    /**
     * Save connection configuration after successful connection
     * 
     * @param config Connection configuration to save
     * @param deviceId Connected device ID
     */
    fun saveConnectionConfig(config: ConnectionConfig?, deviceId: String?) {
        try {
            LogUtil.d(TAG, "Saving connection config for deviceId: $deviceId")
            
            val editor = prefs.edit()
            
            // Save connection config
            if (config != null) {
                val configJson = gson.toJson(config)
                editor.putString(KEY_LAST_CONNECTION_CONFIG, configJson)
                LogUtil.d(TAG, "Saved connection config: $configJson")
            }
            
            // Save connected device ID
            if (deviceId != null) {
                editor.putString(KEY_CONNECTED_DEVICE_ID, deviceId)
                LogUtil.d(TAG, "Saved connected device ID: $deviceId")
            }
            
            editor.apply()
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to save connection config: ${e.message}")
        }
    }

    /**
     * Save detected cable protocol for future use
     * 
     * @param protocol Detected cable protocol
     * @param deviceInfo Device information from detection
     */
    fun saveDetectedCableProtocol(protocol: com.sunmi.tapro.taplink.sdk.enums.CableProtocol, deviceInfo: String?) {
        try {
            val editor = prefs.edit()
            editor.putString("detected_cable_protocol", protocol.name)
            if (deviceInfo != null) {
                editor.putString("detected_cable_device_info", deviceInfo)
            }
            editor.putLong("detected_cable_protocol_timestamp", System.currentTimeMillis())
            editor.apply()
            
            LogUtil.d(TAG, "Saved detected cable protocol: $protocol, deviceInfo: $deviceInfo")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to save detected cable protocol: ${e.message}")
        }
    }

    /**
     * Get last detected cable protocol
     * 
     * @return Detected cable protocol or null if not found or expired
     */
    fun getDetectedCableProtocol(): com.sunmi.tapro.taplink.sdk.enums.CableProtocol? {
        return try {
            val protocolName = prefs.getString("detected_cable_protocol", null)
            val timestamp = prefs.getLong("detected_cable_protocol_timestamp", 0)
            
            // Check if detection is recent (within 5 minutes)
            val isRecent = (System.currentTimeMillis() - timestamp) < 5 * 60 * 1000
            
            if (protocolName != null && isRecent) {
                val protocol = com.sunmi.tapro.taplink.sdk.enums.CableProtocol.valueOf(protocolName)
                LogUtil.d(TAG, "Retrieved detected cable protocol: $protocol")
                protocol
            } else {
                LogUtil.d(TAG, "No recent cable protocol detection found")
                null
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to retrieve detected cable protocol: ${e.message}")
            null
        }
    }
    
    /**
     * Get last connection configuration
     * 
     * @return Last saved connection config or null if not found
     */
    fun getLastConnectionConfig(): ConnectionConfig? {
        return try {
            val configJson = prefs.getString(KEY_LAST_CONNECTION_CONFIG, null)
            if (configJson != null) {
                val config = gson.fromJson(configJson, ConnectionConfig::class.java)
                LogUtil.d(TAG, "Retrieved last connection config: $configJson")
                config
            } else {
                LogUtil.d(TAG, "No saved connection config found")
                null
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to retrieve connection config: ${e.message}")
            null
        }
    }
    
    /**
     * Get last connected device ID
     * 
     * @return Last connected device ID or null if not found
     */
    fun getLastConnectedDeviceId(): String? {
        return try {
            val deviceId = prefs.getString(KEY_CONNECTED_DEVICE_ID, null)
            LogUtil.d(TAG, "Retrieved last connected device ID: $deviceId")
            deviceId
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to retrieve device ID: ${e.message}")
            null
        }
    }
    
    /**
     * Save device service information for mDNS discovery
     * 
     * @param deviceServiceInfo Device service information
     */
    fun saveDeviceServiceInfo(deviceServiceInfo: DeviceServiceInfo) {
        try {
            val key = KEY_DEVICE_SERVICE_INFO + deviceServiceInfo.deviceId
            val serviceInfoJson = gson.toJson(deviceServiceInfo)
            
            prefs.edit()
                .putString(key, serviceInfoJson)
                .apply()
                
            LogUtil.d(TAG, "Saved device service info: $serviceInfoJson")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to save device service info: ${e.message}")
        }
    }
    
    /**
     * Get device service information by device ID
     * 
     * @param deviceId Device ID to look up
     * @return Device service information or null if not found
     */
    fun getDeviceServiceInfo(deviceId: String): DeviceServiceInfo? {
        return try {
            val key = KEY_DEVICE_SERVICE_INFO + deviceId
            val serviceInfoJson = prefs.getString(key, null)
            
            if (serviceInfoJson != null) {
                val serviceInfo = gson.fromJson(serviceInfoJson, DeviceServiceInfo::class.java)
                LogUtil.d(TAG, "Retrieved device service info for $deviceId: $serviceInfoJson")
                serviceInfo
            } else {
                LogUtil.d(TAG, "No service info found for device ID: $deviceId")
                null
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to retrieve device service info: ${e.message}")
            null
        }
    }
    
    /**
     * Update device service information (IP/port change)
     * 
     * @param deviceId Device ID
     * @param newHost New host/IP address
     * @param newPort New port
     */
    fun updateDeviceServiceInfo(deviceId: String, newHost: String, newPort: Int) {
        try {
            val existingInfo = getDeviceServiceInfo(deviceId)
            if (existingInfo != null) {
                val updatedInfo = existingInfo.copy(
                    host = newHost,
                    port = newPort,
                    lastSeen = System.currentTimeMillis()
                )
                saveDeviceServiceInfo(updatedInfo)
                LogUtil.i(TAG, "Updated device service info for $deviceId: $newHost:$newPort")
            } else {
                LogUtil.w(TAG, "Cannot update service info: device $deviceId not found")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to update device service info: ${e.message}")
        }
    }
    
    /**
     * Set auto-connect enabled state
     * 
     * @param enabled Whether auto-connect is enabled
     */
    fun setAutoConnectEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_CONNECT_ENABLED, enabled)
            .apply()
        LogUtil.d(TAG, "Set auto-connect enabled: $enabled")
    }
    
    /**
     * Check if auto-connect is enabled
     * 
     * @return True if auto-connect is enabled
     */
    fun isAutoConnectEnabled(): Boolean {
        val enabled = prefs.getBoolean(KEY_AUTO_CONNECT_ENABLED, false)
        LogUtil.d(TAG, "Auto-connect enabled: $enabled")
        return enabled
    }
    
    /**
     * Clear all saved connection data (on manual disconnect)
     */
    fun clearConnectionData() {
        try {
            val editor = prefs.edit()
            editor.remove(KEY_LAST_CONNECTION_CONFIG)
            editor.remove(KEY_CONNECTED_DEVICE_ID)
            editor.apply()
            
            LogUtil.d(TAG, "Cleared connection data")
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to clear connection data: ${e.message}")
        }
    }
    
    /**
     * Clear device service information
     * 
     * @param deviceId Device ID to clear, or null to clear all
     */
    fun clearDeviceServiceInfo(deviceId: String? = null) {
        try {
            val editor = prefs.edit()
            
            if (deviceId != null) {
                val key = KEY_DEVICE_SERVICE_INFO + deviceId
                editor.remove(key)
                LogUtil.d(TAG, "Cleared service info for device: $deviceId")
            } else {
                // Clear all device service info
                val allKeys = prefs.all.keys
                for (key in allKeys) {
                    if (key.startsWith(KEY_DEVICE_SERVICE_INFO)) {
                        editor.remove(key)
                    }
                }
                LogUtil.d(TAG, "Cleared all device service info")
            }
            
            editor.apply()
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to clear device service info: ${e.message}")
        }
    }
    
    /**
     * Get all saved device service information
     * 
     * @return List of all saved device service info
     */
    fun getAllDeviceServiceInfo(): List<DeviceServiceInfo> {
        return try {
            val result = mutableListOf<DeviceServiceInfo>()
            val allPrefs = prefs.all
            
            for ((key, value) in allPrefs) {
                if (key.startsWith(KEY_DEVICE_SERVICE_INFO) && value is String) {
                    try {
                        val serviceInfo = gson.fromJson(value, DeviceServiceInfo::class.java)
                        result.add(serviceInfo)
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Failed to parse device service info: $key")
                    }
                }
            }
            
            LogUtil.d(TAG, "Retrieved ${result.size} device service info entries")
            result
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to get all device service info: ${e.message}")
            emptyList()
        }
    }
}