package com.sunmi.tapro.taplink.sdk.protocol

import android.content.Context
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.persistence.ConnectionPersistence
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager

/**
 * Protocol Config Resolver
 *
 * Responsible for parsing and selecting appropriate protocol based on ConnectionConfig.
 * Cable AUTO mode uses try-order (AOA -> VSP -> RS232) in ConnectionManager, not detection.
 *
 * @author TaPro Team
 * @since 2025-12-17
 */
object ProtocolConfigResolver {

    /**
     * Build protocol string based on configuration
     *
     * @param connectionConfig Connection configuration
     * @param context Android context (required for cable protocol detection)
     * @return Pair of protocol string and connection mode name
     */
    fun buildProtocol(
        connectionConfig: ConnectionConfig?,
        context: Context? = null
    ): Pair<String, String?> {
        // Check if connectionConfig is null or empty
        if (connectionConfig == null || isEmptyConnectionConfig(connectionConfig)) {
            // Use default auto-detection when no config provided
            return buildProtocolFromConnectionMode(null, context)
        }

        // Cable mode with specific protocol
        connectionConfig.cableProtocol?.let { protocol ->
            return buildProtocolFromCableProtocol(protocol, context)
        }

        // LAN mode
        connectionConfig.host?.let { host ->
            val port = connectionConfig.port ?: 8443
            val protocolString = ProtocolManager.buildLanProtocol(host, port, secure = false
            )
            return Pair(protocolString, ConnectionMode.LAN.name)
        }

        // Default: APP_TO_APP mode
        return Pair(ProtocolManager.buildLocalProtocol(), ConnectionMode.APP_TO_APP.name)
    }

    /**
     * Build protocol from ConnectionMode
     */
    private fun buildProtocolFromConnectionMode(
        mode: ConnectionMode?,
        context: Context?
    ): Pair<String, String?> {
        return when (mode) {
            ConnectionMode.CABLE -> {
                // Cable mode: try-order in ConnectionManager handles AUTO. Fallback when context is null.
                Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
            }

            ConnectionMode.LAN -> {
                // LAN mode needs host and port, return default placeholder
                Pair("ws://", ConnectionMode.LAN.name)
            }

            ConnectionMode.APP_TO_APP -> {
                Pair(ProtocolManager.buildLocalProtocol(), ConnectionMode.APP_TO_APP.name)
            }

            else -> {
                // Default to APP_TO_APP
                Pair(ProtocolManager.buildLocalProtocol(), ConnectionMode.APP_TO_APP.name)
            }
        }
    }

    /**
     * Build protocol from CableProtocol
     */
    private fun buildProtocolFromCableProtocol(
        protocol: CableProtocol,
        context: Context?
    ): Pair<String, String?> {
        return when (protocol) {
            CableProtocol.AUTO -> {
                // AUTO: try-order in ConnectionManager handles this. Fallback when context is null.
                Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
            }

            CableProtocol.USB_AOA -> {
                Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
            }

            CableProtocol.USB_VSP -> {
                Pair(ProtocolManager.buildVspProtocol(), CableProtocol.USB_VSP.name)
            }

            CableProtocol.RS232 -> {
                Pair(ProtocolManager.buildRs232Protocol(), CableProtocol.RS232.name)
            }
        }
    }

    /**
     * Get cable protocols to try in order for connect-by-attempt strategy.
     * Returns protocols in priority order: AOA, VSP, RS232.
     * More accurate than device detection - tries actual connection.
     *
     * @param connectionConfig Connection configuration
     * @param context Android context
     * @return List of (protocol, connectionMode) to try in order, or null if not cable auto mode
     */
    fun getCableProtocolsToTry(
        connectionConfig: ConnectionConfig?,
        context: Context?
    ): List<Pair<String, String>>? {
        if (context == null) return null

        // Use try-order when we would have used detection (cable mode with AUTO or unspecified protocol)
        val isCableTryOrder = when {
            connectionConfig == null -> false
            connectionConfig.host?.isNotBlank() == true -> false  // LAN mode
            connectionConfig.connectionMode == ConnectionMode.CABLE -> {
                connectionConfig.cableProtocol == null || connectionConfig.cableProtocol == CableProtocol.AUTO
            }
            connectionConfig.cableProtocol == CableProtocol.AUTO -> true
            else -> false
        }

        if (!isCableTryOrder) return null

        // Base order: AOA, VSP, RS232. Put cached protocol first if recent.
        val baseOrder = listOf(
            Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name),
            Pair(ProtocolManager.buildVspProtocol(), CableProtocol.USB_VSP.name),
            Pair(ProtocolManager.buildRs232Protocol(), CableProtocol.RS232.name)
        )
        val cached = ConnectionPersistence(context).getDetectedCableProtocol()
        if (cached != null && cached != CableProtocol.AUTO) {
            val cachedPair = baseOrder.find { it.second == cached.name }
            if (cachedPair != null) {
                return listOf(cachedPair) + baseOrder.filter { it != cachedPair }
            }
        }
        return baseOrder
    }

    /**
     * Check if ConnectionConfig is empty
     */
    private fun isEmptyConnectionConfig(config: ConnectionConfig): Boolean {
        return config.cableProtocol == null &&
                config.host.isNullOrBlank() &&
                config.port == null
    }
}
