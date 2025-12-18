package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Connection configuration management utility class
 * 
 * Responsible for saving and loading connection mode configurations, including:
 * - Connection mode (App-to-App, Cable, LAN, Cloud)
 * - LAN mode IP address and port configuration
 * 
 * Uses SharedPreferences for persistent storage
 */
object ConnectionPreferences {
    
    private const val PREFS_NAME = "taplink_connection"
    private const val KEY_MODE = "connection_mode"
    private const val KEY_LAN_IP = "lan_ip"
    private const val KEY_LAN_PORT = "lan_port"
    private const val KEY_LAN_TLS_ENABLED = "lan_tls_enabled"
    
    // Default values
    private const val DEFAULT_MODE = "APP_TO_APP"
    private const val DEFAULT_LAN_PORT = 8443
    private const val DEFAULT_LAN_TLS_ENABLED = true
    
    /**
     * Connection mode enumeration
     */
    enum class ConnectionMode {
        APP_TO_APP,    // Same-device integration (default)
        CABLE,         // Cross-device via cable
        LAN            // Local Area Network
    }
    
    /**
     * Get SharedPreferences instance
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save connection mode
     * 
     * @param context Android Context
     * @param mode Connection mode
     */
    fun saveConnectionMode(context: Context, mode: ConnectionMode) {
        getPreferences(context)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
    
    /**
     * Get saved connection mode
     * 
     * @param context Android Context
     * @return Connection mode, default is APP_TO_APP
     */
    fun getConnectionMode(context: Context): ConnectionMode {
        val prefs = getPreferences(context)
        val modeName = prefs.getString(KEY_MODE, DEFAULT_MODE)
        return try {
            ConnectionMode.valueOf(modeName ?: DEFAULT_MODE)
        } catch (e: IllegalArgumentException) {
            // Return default value if saved value is invalid
            ConnectionMode.APP_TO_APP
        }
    }
    
    /**
     * Save LAN configuration
     * 
     * @param context Android Context
     * @param ip IP address
     * @param port Port number
     * @param tlsEnabled Whether TLS is enabled
     */
    fun saveLanConfig(context: Context, ip: String, port: Int, tlsEnabled: Boolean = true) {
        getPreferences(context)
            .edit()
            .putString(KEY_LAN_IP, ip)
            .putInt(KEY_LAN_PORT, port)
            .putBoolean(KEY_LAN_TLS_ENABLED, tlsEnabled)
            .apply()
    }
    
    /**
     * Get LAN IP address
     * 
     * @param context Android Context
     * @return IP address, null if not configured
     */
    fun getLanIp(context: Context): String? {
        return getPreferences(context).getString(KEY_LAN_IP, null)
    }
    
    /**
     * Get LAN port number
     * 
     * @param context Android Context
     * @return Port number, default is 8443
     */
    fun getLanPort(context: Context): Int {
        return getPreferences(context).getInt(KEY_LAN_PORT, DEFAULT_LAN_PORT)
    }
    
    /**
     * Get LAN TLS enabled status
     * 
     * @param context Android Context
     * @return Whether TLS is enabled, default is true
     */
    fun getLanTlsEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_LAN_TLS_ENABLED, DEFAULT_LAN_TLS_ENABLED)
    }
    
    /**
     * Get complete LAN configuration
     * 
     * @param context Android Context
     * @return Triple(IP address, Port number, TLS enabled status)
     */
    fun getLanConfig(context: Context): Triple<String?, Int, Boolean> {
        val prefs = getPreferences(context)
        val ip = prefs.getString(KEY_LAN_IP, null)
        val port = prefs.getInt(KEY_LAN_PORT, DEFAULT_LAN_PORT)
        val tlsEnabled = prefs.getBoolean(KEY_LAN_TLS_ENABLED, DEFAULT_LAN_TLS_ENABLED)
        return Triple(ip, port, tlsEnabled)
    }
    
    /**
     * Validate if LAN configuration is complete
     * 
     * @param context Android Context
     * @return Whether configuration is complete (IP address is not empty)
     */
    fun isLanConfigComplete(context: Context): Boolean {
        val ip = getLanIp(context)
        return !ip.isNullOrBlank()
    }
    
    /**
     * Clear all connection configurations
     * 
     * @param context Android Context
     */
    fun clearAll(context: Context) {
        getPreferences(context)
            .edit()
            .clear()
            .apply()
    }
    
    /**
     * Clear LAN configuration
     * 
     * @param context Android Context
     */
    fun clearLanConfig(context: Context) {
        getPreferences(context)
            .edit()
            .remove(KEY_LAN_IP)
            .remove(KEY_LAN_PORT)
            .remove(KEY_LAN_TLS_ENABLED)
            .apply()
    }
}
