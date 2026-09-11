package com.sunmi.tapro.taplink.sdk.persistence

import android.content.Context
import android.content.SharedPreferences
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.sdk.enums.AppToAppMode
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy
import com.sunmi.tapro.taplink.sdk.util.TaplinkJsonBridge
import org.json.JSONObject

/**
 * Connection configuration persistence manager
 * 
 * Handles saving and loading connection configurations for automatic reconnection
 * Supports device ID based discovery and connection parameter persistence
 * 
 * @author TaPro Team
 * @since 2025-12-21
 */
class ConnectionPersistence(context: Context) {
    
    private val TAG = "ConnectionPersistence"
    
    companion object {
        private const val PREFS_NAME = "taplink_connection_prefs"
        private const val KEY_LAST_CONNECTION_CONFIG = "last_connection_config"
        private const val KEY_CONNECTED_DEVICE_ID = "connected_device_id"
        private const val KEY_LAST_CONNECTED_SERIAL = "last_connected_serial"
        private const val KEY_DEVICE_SERVICE_INFO = "device_service_info_"
        private const val KEY_AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                val configJson = TaplinkJsonBridge.toJsonString(config)
                editor.putString(KEY_LAST_CONNECTION_CONFIG, configJson)
                LogUtil.d(TAG, "Saved connection config: $configJson")
            }

            // Save connected device ID. Never overwrite a previously stored, valid id with a
            // blank/"unknown" value — LAN connections deliver deviceId="unknown" at onConnected
            // (the real serial only arrives via INIT or is parsed from the mDNS service name), and
            // clobbering the stored id would break same-device mDNS discovery on a later fallback.
            if (!deviceId.isNullOrEmpty() && deviceId != "unknown") {
                editor.putString(KEY_CONNECTED_DEVICE_ID, deviceId)
                LogUtil.d(TAG, "Saved connected device ID: $deviceId")
            } else {
                LogUtil.d(TAG, "Skip saving connected device ID (blank/unknown): $deviceId")
            }

            editor.apply()
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to save connection config: ${e.message}")
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
                val config = parseConnectionConfig(configJson)
                LogUtil.d(TAG, "Retrieved last connection config: $configJson")
                config
            } else {
                LogUtil.d(TAG, "No saved connection config found")
                null
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to retrieve connection config: ${e.message}")
            prefs.edit().remove(KEY_LAST_CONNECTION_CONFIG).apply()
            LogUtil.w(TAG, "Removed invalid cached connection config")
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
     * Persist the Tapro serial of the most recent LAN connection (parsed from the mDNS service
     * name). LAN connections do not deliver a real deviceId at `onConnected` (it is "unknown"), and
     * the transport-bound serial is lost once the LAN kernel is torn down (transport switch /
     * disconnect). Persisting it here keeps same-device mDNS discovery working when an AUTO session
     * later falls back to LAN after the cable drops.
     *
     * @param serial the Tapro serial, ignored when null/blank/"unknown"
     */
    fun saveLastConnectedSerial(serial: String?) {
        if (serial.isNullOrEmpty() || serial == "unknown") return
        try {
            prefs.edit().putString(KEY_LAST_CONNECTED_SERIAL, serial).apply()
            LogUtil.d(TAG, "Saved last connected serial: $serial")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to save last connected serial: ${e.message}")
        }
    }

    /**
     * The Tapro serial of the most recent connection that exposed one (see [saveLastConnectedSerial]),
     * or null if none is stored.
     */
    fun getLastConnectedSerial(): String? {
        return try {
            prefs.getString(KEY_LAST_CONNECTED_SERIAL, null)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to retrieve last connected serial: ${e.message}")
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
            val serviceInfoJson = TaplinkJsonBridge.toJsonString(deviceServiceInfo)
            
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
                val serviceInfo = parseDeviceServiceInfo(serviceInfoJson)
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
            editor.remove(KEY_LAST_CONNECTED_SERIAL)
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
                        val serviceInfo = parseDeviceServiceInfo(value)
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

    /** Reads both Jackson-era cache JSON and JSON written by the current bridge. */
    private fun parseConnectionConfig(json: String): ConnectionConfig {
        val source = JSONObject(json)
        val config = ConnectionConfig()

        enumValueOrNull<CableProtocol>(source.optNullableString("cableProtocol"))
            ?.let(config::setCableProtocol)
        val strategy = enumValueOrNull<CrossDeviceStrategy>(
            source.optNullableString("crossDeviceStrategy")
        )
        strategy?.let(config::setCrossDeviceStrategy)

        if (strategy != null) {
            config.setConnectionMode(ConnectionMode.CROSS_DEVICE)
        } else {
            enumValueOrNull<ConnectionMode>(source.optNullableString("connectionMode"))
                ?.let(config::setConnectionMode)
        }

        enumValueOrNull<AppToAppMode>(source.optNullableString("appToAppMode"))
            ?.let(config::setAppToAppMode)
        source.optNullableString("host")?.let(config::setHost)
        if (source.has("port") && !source.isNull("port")) config.setPort(source.getInt("port"))
        if (source.has("connectionTimeout")) {
            config.setConnectionTimeout(source.getInt("connectionTimeout"))
        }
        if (source.has("autoReconnect")) config.setAutoReconnect(source.getBoolean("autoReconnect"))
        config.setRetryPolicy(
            source.optInt("maxRetryCount", config.maxRetryCount),
            source.optLong("retryDelayMs", config.retryDelayMs)
        )
        return config
    }

    private fun parseDeviceServiceInfo(json: String): DeviceServiceInfo {
        val source = JSONObject(json)
        return DeviceServiceInfo(
            deviceId = source.getString("deviceId"),
            serviceName = source.getString("serviceName"),
            host = source.getString("host"),
            port = source.getInt("port"),
            lastSeen = source.optLong("lastSeen", System.currentTimeMillis())
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
