package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.enums.CableProtocol

/**
 * Connection Configuration Class
 * 
 * Used to configure parameters for different connection modes
 * Supports app-to-app mode, cable mode, and LAN mode
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ConnectionConfig {
    
    /**
     * Cable protocol type (optional)
     * Used to specify protocol type for cable mode
     */
    var cableProtocol: CableProtocol? = null
        private set
    
    /**
     * Host address (optional)
     * Used to specify payment terminal IP address for LAN mode
     */
    var host: String? = null
        private set
    
    /**
     * Port number (optional)
     * Used to specify payment terminal port for LAN mode (8443-8453)
     */
    var port: Int? = null
        private set
    
    /**
     * Set cable protocol
     * 
     * @param protocol Cable protocol type
     * @return ConnectionConfig Current configuration object, supports chained calls
     */
    fun setCableProtocol(protocol: CableProtocol): ConnectionConfig {
        this.cableProtocol = protocol
        return this
    }
    
    /**
     * Set host address
     * 
     * @param host Host IP address
     * @return ConnectionConfig Current configuration object, supports chained calls
     */
    fun setHost(host: String): ConnectionConfig {
        this.host = host
        return this
    }
    
    /**
     * Set port number
     * 
     * @param port Port number (8443-8453)
     * @return ConnectionConfig Current configuration object, supports chained calls
     */
    fun setPort(port: Int): ConnectionConfig {
        this.port = port
        return this
    }
    
    companion object {
        /**
         * Create default connection configuration
         * 
         * Empty configuration, SDK will auto-detect connection mode
         * Suitable for app-to-app mode, cable mode (auto-detection), and LAN mode (using cached device)
         * 
         * @return ConnectionConfig Default configuration object
         */
        fun createDefault(): ConnectionConfig {
            return ConnectionConfig()
        }
    }
}













