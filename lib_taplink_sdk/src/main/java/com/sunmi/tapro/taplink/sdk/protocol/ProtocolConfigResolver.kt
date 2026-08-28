@file:Suppress("DEPRECATION")

package com.sunmi.tapro.taplink.sdk.protocol

import android.content.Context
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.PinpadStrategy
import com.sunmi.tapro.taplink.sdk.persistence.ConnectionPersistence
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager

/**
 * Protocol Config Resolver
 *
 * Responsible for parsing and selecting appropriate protocol based on ConnectionConfig.
 * Cable AUTO mode tries the inserted cable's protocol first (via [InsertedCableClassifier]), then
 * retries the remaining protocols in base order (VSP -> RS232 -> AOA) in ConnectionManager.
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

        // Secondary Display mode: always use VSP protocol
        if (connectionConfig.connectionMode == ConnectionMode.SECONDARY_DISPLAY) {
            return Pair(ProtocolManager.buildVspProtocol(), ConnectionMode.SECONDARY_DISPLAY.name)
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
     *
     * AUTO mode ordering (plug-and-play):
     * 1. **Inserted-cable first** — [InsertedCableClassifier] inspects the USB subsystem; if a real
     *    cable is plugged in (USB-serial bridge -> RS232, USB accessory -> AOA), that protocol is
     *    tried first so the matching cable connects immediately.
     * 2. **Cached protocol** — when no external cable signal is present (e.g. on-board VSP UART),
     *    the last-successful protocol is used as a hint to order the base list.
     * 3. **Base order** — VSP -> RS232 -> AOA for the remaining protocols.
     *
     * Trying an actual connection per protocol is more reliable than pure device detection, so the
     * classifier only reorders the try-list; every protocol is still retried until one succeeds.
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

        // Secondary Display mode uses VSP directly, no try-order needed
        if (connectionConfig?.connectionMode == ConnectionMode.SECONDARY_DISPLAY) return null

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

        return buildCableTryOrder(context)
    }

    /**
     * Builds the reordered cable protocol try-list (VSP -> RS232 -> AOA base order, with the
     * physically-inserted cable's protocol — or the last-successful cached protocol — moved first).
     * Shared by [getCableProtocolsToTry] and [getPinpadAutoCandidates].
     */
    private fun buildCableTryOrder(context: Context): List<Pair<String, String>> {
        // Base order: VSP, RS232, AOA. Prefer serial when peer enumerates as CDC; AOA last for stale-enum cases.
        val baseOrder = listOf(
            Pair(ProtocolManager.buildVspProtocol(), CableProtocol.USB_VSP.name),
            Pair(ProtocolManager.buildRs232Protocol(), CableProtocol.RS232.name),
            Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
        )

        // 1) Prioritize the physically-inserted cable's protocol (plug-and-play).
        val preferredName = when (InsertedCableClassifier.classify(context)) {
            CableProtocol.RS232 -> CableProtocol.RS232.name
            CableProtocol.USB_AOA -> CableProtocol.USB_AOA.name
            else -> {
                // 2) No external cable signal: fall back to last-successful cached protocol as a hint.
                val cached = ConnectionPersistence(context).getDetectedCableProtocol()
                if (cached != null && cached != CableProtocol.AUTO) cached.name else null
            }
        }

        if (preferredName != null) {
            val preferredPair = baseOrder.find { it.second == preferredName }
            if (preferredPair != null) {
                return listOf(preferredPair) + baseOrder.filter { it != preferredPair }
            }
        }
        return baseOrder
    }

    /**
     * Get the ordered transport candidates for a PinPad AUTO session.
     *
     * Only applies when [ConnectionConfig.pinpadAuto] is true. Iterates
     * [ConnectionConfig.autoPriority] and expands each strategy into concrete attempts:
     * - [PinpadStrategy.LAN] → a single LAN candidate (requires a non-blank host)
     * - [PinpadStrategy.CABLE] → the cable try-order (VSP/RS232/AOA, reordered by inserted cable)
     *
     * The connection layer ([com.sunmi.tapro.taplink.sdk.manager.ConnectionManager]) tries the
     * returned candidates in order, falling back to the next on connection failure/timeout.
     *
     * @return ordered (protocol, connectionMode) candidates, or null when not a PINPAD AUTO session
     */
    fun getPinpadAutoCandidates(
        connectionConfig: ConnectionConfig?,
        context: Context?
    ): List<Pair<String, String>>? {
        if (connectionConfig == null || !connectionConfig.pinpadAuto) return null

        val candidates = mutableListOf<Pair<String, String>>()
        for (strategy in connectionConfig.autoPriority) {
            when (strategy) {
                PinpadStrategy.LAN -> {
                    val host = connectionConfig.host
                    if (!host.isNullOrBlank()) {
                        val port = connectionConfig.port ?: 8443
                        candidates.add(
                            Pair(
                                ProtocolManager.buildLanProtocol(host, port, secure = false),
                                ConnectionMode.LAN.name
                            )
                        )
                    }
                }
                PinpadStrategy.CABLE -> {
                    if (context != null) {
                        candidates.addAll(buildCableTryOrder(context))
                    }
                }
                PinpadStrategy.AUTO -> {
                    // AUTO is not a concrete transport within a priority list; ignore.
                }
            }
        }
        return candidates.distinct().ifEmpty { null }
    }

    /**
     * Check if ConnectionConfig is empty (no meaningful parameters set)
     */
    private fun isEmptyConnectionConfig(config: ConnectionConfig): Boolean {
        return config.connectionMode == null &&
                config.cableProtocol == null &&
                config.host.isNullOrBlank() &&
                config.port == null
    }
}
