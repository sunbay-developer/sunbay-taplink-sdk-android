@file:Suppress("DEPRECATION")

package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy
import com.sunmi.tapro.taplink.sdk.enums.AppToAppMode

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
     * App-to-App transaction mode (optional, default: CUSTOM).
     * Only used when [connectionMode] is [ConnectionMode.APP_TO_APP].
     */
    var appToAppMode: AppToAppMode = AppToAppMode.CUSTOM
        private set

    /**
     * Cable protocol type (optional).
     * Specifies the protocol type to be used when operating in Cable mode.
     */
    var cableProtocol: CableProtocol? = null
        private set

    /**
     * Cross-Device connection strategy (optional).
     *
     * Only meaningful when the public mode is [ConnectionMode.CROSS_DEVICE]. Analogous to
     * [cableProtocol] for [ConnectionMode.CABLE]: it configures HOW the Cross-Device peer
     * connects (AUTO / LAN / CABLE) without exposing separate top-level [ConnectionMode] values.
     */
    var crossDeviceStrategy: CrossDeviceStrategy? = null
        private set

    /**
     * Whether this configuration resolves to the Cross-Device AUTO strategy (cross-transport
     * auto-selection with fallback). Derived internally from [ConnectionMode.CROSS_DEVICE] +
     * [CrossDeviceStrategy.AUTO]; when true, [connectionMode] is left null and the connection
     * layer selects a transport according to [autoPriority].
     */
    var crossDeviceAuto: Boolean = false
        private set

    /**
     * Priority order used by the Cross-Device AUTO strategy to decide which transport to try
     * first when multiple are viable. Only [CrossDeviceStrategy.LAN] / [CrossDeviceStrategy.CABLE]
     * are meaningful here. Defaults to [DEFAULT_AUTO_PRIORITY] (LAN first, then CABLE).
     */
    var autoPriority: List<CrossDeviceStrategy> = DEFAULT_AUTO_PRIORITY
        private set

    /** Internal flag: the public mode selected was [ConnectionMode.CROSS_DEVICE]. */
    private var isCrossDeviceMode: Boolean = false

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
     * New business-facing modes are normalized onto classic behavior so existing
     * resolver/manager logic is reused unchanged:
     * - [ConnectionMode.ON_DEVICE] → [ConnectionMode.APP_TO_APP]
     * - [ConnectionMode.CROSS_DEVICE] → LAN / CABLE / AUTO depending on [crossDeviceStrategy]
     *   (defaults to [CrossDeviceStrategy.AUTO] when unset), via [applyCrossDeviceNormalization].
     *
     * @param mode the connection mode
     * @return the current configuration instance for method chaining
     */
    fun setConnectionMode(mode: ConnectionMode): ConnectionConfig {
        when (mode) {
            ConnectionMode.CROSS_DEVICE -> {
                isCrossDeviceMode = true
                if (crossDeviceStrategy == null) crossDeviceStrategy = CrossDeviceStrategy.AUTO
                applyCrossDeviceNormalization()
            }
            ConnectionMode.ON_DEVICE -> {
                clearCrossDeviceState()
                this.connectionMode = ConnectionMode.APP_TO_APP
            }
            else -> {
                clearCrossDeviceState()
                this.connectionMode = mode
            }
        }
        return this
    }

    /**
     * Sets the Cross-Device connection strategy (only meaningful together with
     * [ConnectionMode.CROSS_DEVICE]). Can be called before or after [setConnectionMode];
     * normalization is re-applied when the mode is CROSS_DEVICE.
     *
     * @param strategy the Cross-Device connection strategy
     * @return the current configuration instance for method chaining
     */
    fun setCrossDeviceStrategy(strategy: CrossDeviceStrategy): ConnectionConfig {
        this.crossDeviceStrategy = strategy
        if (isCrossDeviceMode) applyCrossDeviceNormalization()
        return this
    }

    /**
     * Sets the Cross-Device AUTO priority order. Only [CrossDeviceStrategy.LAN]/[CrossDeviceStrategy.CABLE]
     * entries are honored; an empty/invalid list falls back to [DEFAULT_AUTO_PRIORITY].
     *
     * @param priority ordered transports to try first under AUTO
     * @return the current configuration instance for method chaining
     */
    fun setAutoPriority(priority: List<CrossDeviceStrategy>): ConnectionConfig {
        val filtered = priority.filter { it == CrossDeviceStrategy.LAN || it == CrossDeviceStrategy.CABLE }
        this.autoPriority = filtered.distinct().ifEmpty { DEFAULT_AUTO_PRIORITY }
        return this
    }

    /**
     * Convenience overload: sets [preferred] first and the other transport as fallback.
     *
     * @param preferred the transport to try first ([CrossDeviceStrategy.LAN] or [CrossDeviceStrategy.CABLE])
     * @return the current configuration instance for method chaining
     */
    fun setAutoPriority(preferred: CrossDeviceStrategy): ConnectionConfig {
        val other = if (preferred == CrossDeviceStrategy.CABLE) CrossDeviceStrategy.LAN else CrossDeviceStrategy.CABLE
        return setAutoPriority(listOf(preferred, other))
    }

    /**
     * Normalizes a CROSS_DEVICE selection onto classic connection behavior based on
     * [crossDeviceStrategy]. Idempotent and call-order independent.
     */
    private fun applyCrossDeviceNormalization() {
        when (crossDeviceStrategy ?: CrossDeviceStrategy.AUTO) {
            CrossDeviceStrategy.LAN -> {
                this.connectionMode = ConnectionMode.LAN
                this.crossDeviceAuto = false
                // A cable protocol is meaningless for a LAN-only Cross-Device session; clearing it
                // prevents a residual value (callers commonly pass a non-null cableProtocol
                // regardless of strategy) from steering protocol resolution to a cable transport.
                this.cableProtocol = null
            }
            CrossDeviceStrategy.CABLE -> {
                this.connectionMode = ConnectionMode.CABLE
                this.crossDeviceAuto = false
                // CROSS_DEVICE + CABLE defaults to cable AUTO try-order (VSP/RS232/AOA) unless
                // an explicit protocol was chosen — no need to pin a specific protocol.
                if (this.cableProtocol == null) this.cableProtocol = CableProtocol.AUTO
            }
            CrossDeviceStrategy.AUTO -> {
                // AUTO selects a transport at the connection layer; leave connectionMode null.
                this.connectionMode = null
                this.crossDeviceAuto = true
            }
        }
    }

    /** Clears any Cross-Device-derived state when a non-CROSS_DEVICE mode is selected. */
    private fun clearCrossDeviceState() {
        isCrossDeviceMode = false
        crossDeviceStrategy = null
        crossDeviceAuto = false
    }

    /**
     * Sets App-to-App transaction mode.
     *
     * If not set, SDK defaults to [AppToAppMode.CUSTOM].
     *
     * @param mode App-to-App mode
     * @return the current configuration instance for method chaining
     */
    fun setAppToAppMode(mode: AppToAppMode): ConnectionConfig {
        this.appToAppMode = mode
        return this
    }

    /**
     * Sets the cable protocol.
     *
     * @param protocol the cable protocol type
     * @return the current configuration instance for method chaining
     */
    fun setCableProtocol(protocol: CableProtocol?): ConnectionConfig {
        this.cableProtocol = protocol ?: CableProtocol.AUTO
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
                this.appToAppMode == other.appToAppMode &&
               this.cableProtocol == other.cableProtocol &&
               this.crossDeviceAuto == other.crossDeviceAuto &&
               this.crossDeviceStrategy == other.crossDeviceStrategy &&
               this.autoPriority == other.autoPriority &&
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
                "appToAppMode=$appToAppMode, " +
                "cableProtocol=$cableProtocol, " +
                "crossDeviceStrategy=$crossDeviceStrategy, " +
                "crossDeviceAuto=$crossDeviceAuto, " +
                "autoPriority=$autoPriority, " +
                "host=$host, " +
                "port=$port, " +
                "connectionTimeout=${connectionTimeout}s, " +
                "autoReconnect=$autoReconnect, " +
                "maxRetryCount=$maxRetryCount, " +
                "retryDelayMs=${retryDelayMs}ms" +
                ")"
    }
    
    companion object {

        /** Default Cross-Device AUTO priority: try LAN first, then CABLE. */
        val DEFAULT_AUTO_PRIORITY: List<CrossDeviceStrategy> =
            listOf(CrossDeviceStrategy.LAN, CrossDeviceStrategy.CABLE)

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
        @Deprecated(
            message = "Use createOnDeviceMode() instead. App-to-App is the current underlying " +
                "implementation of the ON_DEVICE integration mode.",
            replaceWith = ReplaceWith("ConnectionConfig.createOnDeviceMode()")
        )
        fun createAppMode(mode: AppToAppMode = AppToAppMode.CUSTOM): ConnectionConfig {
            return ConnectionConfig()
                .setConnectionMode(ConnectionMode.APP_TO_APP)
                .setAppToAppMode(mode)
        }

        /**
         * Creates an on-device (ON_DEVICE) integration mode configuration.
         *
         * The business app and TaPro run on the same terminal. Normalized internally onto
         * the App-to-App implementation.
         *
         * @return the on-device mode configuration
         */
        fun createOnDeviceMode(mode: AppToAppMode = AppToAppMode.CUSTOM): ConnectionConfig {
            return ConnectionConfig()
                .setAppToAppMode(mode)
                .setConnectionMode(ConnectionMode.ON_DEVICE)
        }

        /**
         * Creates a Cable mode configuration.
         *
         * @param protocol the cable protocol type (optional)
         * @return the Cable mode configuration
         */
        @Deprecated(
            message = "Use createCrossDeviceMode(CrossDeviceStrategy.CABLE) instead. CABLE remains fully functional.",
            replaceWith = ReplaceWith("ConnectionConfig.createCrossDeviceMode(CrossDeviceStrategy.CABLE, cableProtocol = protocol)")
        )
        fun createCableMode(protocol: CableProtocol = CableProtocol.AUTO): ConnectionConfig {
            return ConnectionConfig()
                .setConnectionMode(ConnectionMode.CABLE)
                .setCableProtocol(protocol)
        }

        /**
         * Creates a Local Area Network (LAN) mode configuration.
         *
         * @param host the host address (optional)
         * @param port the port number (optional)
         * @return the LAN mode configuration
         */
        @Deprecated(
            message = "Use createCrossDeviceMode(CrossDeviceStrategy.LAN, host, port) instead. LAN remains fully functional.",
            replaceWith = ReplaceWith("ConnectionConfig.createCrossDeviceMode(CrossDeviceStrategy.LAN, host, port)")
        )
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
         * Creates a Cross-Device integration mode configuration.
         *
         * The concrete connection is configured via [strategy] (AUTO / LAN / CABLE), mirroring
         * how [createCableMode] configures its protocol. AUTO selects a transport per
         * [autoPriority] with runtime fallback; CABLE defaults to the cable AUTO try-order
         * (VSP/RS232/AOA) unless [cableProtocol] is given; LAN requires [host] (+ optional [port]).
         *
         * @param strategy Cross-Device connection strategy (default [CrossDeviceStrategy.AUTO])
         * @param host LAN host (for LAN / AUTO)
         * @param port LAN port (for LAN / AUTO)
         * @param cableProtocol explicit cable protocol (for CABLE); defaults to AUTO try-order when null.
         *   Ignored when [strategy] is [CrossDeviceStrategy.LAN].
         * @param autoPriority optional AUTO priority override
         * @return the Cross-Device mode configuration
         */
        fun createCrossDeviceMode(
            strategy: CrossDeviceStrategy = CrossDeviceStrategy.AUTO,
            host: String? = null,
            port: Int? = null,
            cableProtocol: CableProtocol? = null,
            autoPriority: List<CrossDeviceStrategy>? = null
        ): ConnectionConfig {
            val config = ConnectionConfig()
            if (host != null) config.setHost(host)
            if (port != null) config.setPort(port)
            if (cableProtocol != null) config.setCableProtocol(cableProtocol)
            if (autoPriority != null) config.setAutoPriority(autoPriority)
            config.setCrossDeviceStrategy(strategy)
            config.setConnectionMode(ConnectionMode.CROSS_DEVICE)
            return config
        }

        /**
         * Creates a Sub_Screen mode configuration.
         *
         * Connects via USB Virtual Serial Port (VSP) to a remote TaPro terminal and
         * automatically opens the USB customer-facing screen player. The connection
         * is only considered successful when both the VSP link is up AND the screen
         * player launches on the remote TaPro device.
         *
         * @return the Sub_Screen mode configuration
         */
        fun createSubScreenMode(): ConnectionConfig {
            return ConnectionConfig()
                .setConnectionMode(ConnectionMode.SUB_SCREEN)
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











