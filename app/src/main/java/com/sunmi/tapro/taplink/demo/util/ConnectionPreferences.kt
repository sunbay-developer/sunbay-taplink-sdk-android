package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sunmi.tapro.taplink.demo.util.Constants

/**
 * Connection configuration management utility class
 * 
 * Responsible for saving and loading connection mode configurations, including:
 * - Connection mode (App-to-App, Cable, LAN, Cloud)
 * - LAN mode IP address and port configuration
 * 
 *
 * Uses SharedPreferences for persistent storage
 */
object ConnectionPreferences {
    
    private const val PREFS_NAME = "taplink_connection"
    private const val KEY_MODE = "connection_mode"
    private const val KEY_LAN_IP = "lan_ip"
    private const val KEY_LAN_PORT = "lan_port"
    private const val KEY_CABLE_PROTOCOL = "cable_protocol"
    
    // Default values
    private const val DEFAULT_MODE = "APP_TO_APP"
    private const val DEFAULT_LAN_PORT = Constants.DEFAULT_LAN_PORT
    private const val DEFAULT_CABLE_PROTOCOL = "AUTO"
    
    /**
     * Connection mode enumeration
     */
    enum class ConnectionMode {
        APP_TO_APP,    // Same-device integration (default)
        CABLE,         // Cross-device via cable
        LAN            // Local Area Network
    }
    
    /**
     * Cable protocol enumeration
     */
    enum class CableProtocol {
        AUTO,          // Auto-detect protocol (default)
        USB_AOA,       // USB Android Open Accessory 2.0
        USB_VSP,       // USB Virtual Serial Port
        RS232          // Standard RS232 serial communication
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
        getPreferences(context).edit {
            putString(KEY_MODE, mode.name)
        }
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
     */
    fun saveLanConfig(context: Context, ip: String, port: Int) {
        getPreferences(context).edit {
            putString(KEY_LAN_IP, ip)
            putInt(KEY_LAN_PORT, port)
        }
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
     * Get complete LAN configuration
     * 
     * @param context Android Context
     * @return Pair(IP address, Port number)
     */
    fun getLanConfig(context: Context): Pair<String?, Int> {
        val prefs = getPreferences(context)
        val ip = prefs.getString(KEY_LAN_IP, null)
        val port = prefs.getInt(KEY_LAN_PORT, DEFAULT_LAN_PORT)
        return Pair(ip, port)
    }
    
    /**
     * Save Cable protocol configuration
     * 
     * @param context Android Context
     * @param protocol Cable protocol
     */
    fun saveCableProtocol(context: Context, protocol: CableProtocol) {
        getPreferences(context).edit {
            putString(KEY_CABLE_PROTOCOL, protocol.name)
        }
    }
    
    /**
     * Get Cable protocol configuration
     * 
     * @param context Android Context
     * @return Cable protocol, default is AUTO
     */
    fun getCableProtocol(context: Context): CableProtocol {
        val prefs = getPreferences(context)
        val protocolName = prefs.getString(KEY_CABLE_PROTOCOL, DEFAULT_CABLE_PROTOCOL)
        return try {
            CableProtocol.valueOf(protocolName ?: DEFAULT_CABLE_PROTOCOL)
        } catch (e: IllegalArgumentException) {
            // Return default value if saved value is invalid
            CableProtocol.AUTO
        }
    }
}
