package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode

/**
 * Connection configuration class.
 *
 * Used to define parameters and connection behaviors for different connection modes.
 * Supports App-to-App mode, Cable mode, and Local Area Network (LAN) mode.
 * Includes configuration options such as connection timeout and automatic reconnection.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ConnectionConfig {

    // ========== Connection Mode Configuration ==========

    /**
     * Connection mode (optional).
     * Specifies which connection mode to use: App-to-App, Cable, or Local Area Network (LAN).
     */
    var connectionMode: ConnectionMode? = null
        private set

    /**
     * Cable protocol type (optional).
     * Specifies the protocol type to be used when operating in Cable mode.
     */
    var cableProtocol: CableProtocol? = null
        private set

    /**
     * Host address (optional).
     * Specifies the IP address of the payment terminal when operating in Local Area Network (LAN) mode.
     */
    var host: String? = null
        private set

    /**
     * Port number (optional).
     * Specifies the port of the payment terminal when operating in Local Area Network (LAN) mode (8443–8453).
     */
    var port: Int? = null
        private set

    // ========== Connection Timeout Configuration ==========

    /**
     * Connection timeout (in seconds, default: 60).
     * The maximum amount of time to wait for a connection to be established.
     */
    var connectionTimeout: Int = 60
        private set

    // ========== Automatic Reconnection Configuration ==========

    /**
     * Whether automatic reconnection is enabled (default: false).
     * When the connection is interrupted unexpectedly, the SDK will automatically attempt to reconnect.
     */
    var autoReconnect: Boolean = false
        private set

    /**
     * Maximum retry attempts (default: 3).
     * The maximum number of attempts for automatic reconnection.
     */
    var maxRetryCount: Int = 3
        private set

    /**
     * Retry delay (in milliseconds, default: 2000 ms).
     * The wait time between consecutive reconnection attempts.
     */
    var retryDelayMs: Long = 2000L
        private set

    // ========== Connection Mode Configuration Methods ==========

    /**
     * Sets the connection mode.
     *
     * @param mode the connection mode
     * @return the current configuration instance for method chaining
     */
    fun setConnectionMode(mode: ConnectionMode): ConnectionConfig {
        this.connectionMode = mode
        return this
    }

    /**
     * Sets the cable protocol.
     *
     * @param protocol the cable protocol type
     * @return the current configuration instance for method chaining
     */
    fun setCableProtocol(protocol: CableProtocol): ConnectionConfig {
        this.cableProtocol = protocol
        return this
    }

    /**
     * Sets the host address.
     *
     * @param host the host IP address
     * @return the current configuration instance for method chaining
     */
    fun setHost(host: String): ConnectionConfig {
        this.host = host
        return this
    }

    /**
     * Sets the port number.
     *
     * @param port the port number (8443–8453)
     * @return the current configuration instance for method chaining
     */
    fun setPort(port: Int): ConnectionConfig {
        this.port = port
        return this
    }

    // ========== Connection Timeout Configuration Methods ==========

    /**
     * Sets the connection timeout.
     *
     * @param seconds the timeout duration in seconds
     * @return the current configuration instance for method chaining
     */
    fun setConnectionTimeout(seconds: Int): ConnectionConfig {
        this.connectionTimeout = seconds
        return this
    }

    // ========== Automatic Reconnection Configuration Methods ==========

    /**
     * Enables or disables automatic reconnection.
     *
     * @param enabled whether automatic reconnection is enabled
     * @return the current configuration instance for method chaining
     */
    fun setAutoReconnect(enabled: Boolean): ConnectionConfig {
        this.autoReconnect = enabled
        return this
    }
    
    /**
     * Sets the retry strategy.
     *
     * @param maxRetries the maximum number of retry attempts
     * @param delayMs the retry delay in milliseconds
     * @return the current configuration instance for method chaining
     */
    fun setRetryPolicy(maxRetries: Int, delayMs: Long): ConnectionConfig {
        this.maxRetryCount = maxRetries
        this.retryDelayMs = delayMs
        return this
    }

    /**
     * Sets the maximum number of retry attempts.
     *
     * @param maxRetries the maximum number of retry attempts
     * @return the current configuration instance for method chaining
     */
    fun setMaxRetryCount(maxRetries: Int): ConnectionConfig {
        this.maxRetryCount = maxRetries
        return this
    }

    /**
     * Sets the retry delay.
     *
     * @param delayMs the retry delay in milliseconds
     * @return the current configuration instance for method chaining
     */
    fun setRetryDelayMs(delayMs: Long): ConnectionConfig {
        this.retryDelayMs = delayMs
        return this
    }
    
    /**
     * Checks if this configuration is equivalent to another configuration.
     * 
     * Two configurations are considered equivalent if they have the same connection parameters.
     * 
     * @param other the configuration to compare with
     * @return true if configurations are equivalent, false otherwise
     */
    fun isEquivalentTo(other: ConnectionConfig?): Boolean {
        if (other == null) return false
        
        return this.connectionMode == other.connectionMode &&
               this.cableProtocol == other.cableProtocol &&
               this.host == other.host &&
               this.port == other.port &&
               this.connectionTimeout == other.connectionTimeout &&
               this.autoReconnect == other.autoReconnect &&
               this.maxRetryCount == other.maxRetryCount &&
               this.retryDelayMs == other.retryDelayMs
    }

    /**
     * Returns a string representation of the current configuration.
     *
     * @return string representation of the configuration
     */
    override fun toString(): String {
        return "ConnectionConfig(" +
                "connectionMode=$connectionMode, " +
                "cableProtocol=$cableProtocol, " +
                "host=$host, " +
                "port=$port, " +
                "connectionTimeout=${connectionTimeout}s, " +
                "autoReconnect=$autoReconnect, " +
                "maxRetryCount=$maxRetryCount, " +
                "retryDelayMs=${retryDelayMs}ms" +
                ")"
    }
    
    companion object {

        /**
         * Creates a default connection configuration.
         *
         * An empty configuration that allows the SDK to automatically determine
         * the appropriate connection mode. Applicable to App-to-App mode,
         * Cable mode (auto-detected), and LAN mode (using cached device information).
         *
         * @return the default configuration instance
         */
        fun createDefault(): ConnectionConfig {
            return ConnectionConfig()
        }

        /**
         * Creates an App-to-App mode configuration.
         *
         * @return the App-to-App mode configuration
         */
        fun createAppMode(): ConnectionConfig {
            return ConnectionConfig().setConnectionMode(ConnectionMode.APP_TO_APP)
        }

        /**
         * Creates a Cable mode configuration.
         *
         * @param protocol the cable protocol type (optional)
         * @return the Cable mode configuration
         */
        fun createCableMode(protocol: CableProtocol? = null): ConnectionConfig {
            val config = ConnectionConfig().setConnectionMode(ConnectionMode.CABLE)
            return if (protocol != null) {
                config.setCableProtocol(protocol)
            } else {
                config
            }
        }

        /**
         * Creates a Local Area Network (LAN) mode configuration.
         *
         * @param host the host address (optional)
         * @param port the port number (optional)
         * @return the LAN mode configuration
         */
        fun createLanMode(host: String? = null, port: Int? = null): ConnectionConfig {
            val config = ConnectionConfig().setConnectionMode(ConnectionMode.LAN)
            if (host != null) {
                config.setHost(host)
            }
            if (port != null) {
                config.setPort(port)
            }
            return config
        }

        /**
         * Creates a high-reliability configuration.
         *
         * Enables automatic reconnection and increases retry attempts and delays.
         *
         * @return the high-reliability configuration
         */
        fun createHighReliabilityConfig(): ConnectionConfig {
            return ConnectionConfig()
                .setAutoReconnect(true)
                .setRetryPolicy(maxRetries = 5, delayMs = 3000L)
                .setConnectionTimeout(90)
        }

        /**
         * Creates a fast-connection configuration.
         *
         * Reduces connection timeout and disables automatic reconnection.
         *
         * @return the fast-connection configuration
         */
        fun createFastConnectionConfig(): ConnectionConfig {
            return ConnectionConfig()
                .setAutoReconnect(false)
                .setConnectionTimeout(30)
        }
    }
}













