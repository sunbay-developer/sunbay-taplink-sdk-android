@file:Suppress("DEPRECATION")

package com.sunmi.tapro.taplink.sdk.protocol

import android.content.Context
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy
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

        // Sub_Screen mode: always use VSP protocol
        if (connectionConfig.connectionMode == ConnectionMode.SUB_SCREEN) {
            return Pair(ProtocolManager.buildVspProtocol(), ConnectionMode.SUB_SCREEN.name)
        }

        // LAN mode takes precedence over any cableProtocol still present on the config: an
        // explicitly selected LAN transport must never be resolved to a cable protocol.
        if (connectionConfig.connectionMode == ConnectionMode.LAN &&
            !connectionConfig.host.isNullOrBlank()
        ) {
            val port = connectionConfig.port ?: 8443
            return Pair(
                ProtocolManager.buildLanProtocol(connectionConfig.host!!, port, secure = false),
                ConnectionMode.LAN.name
            )
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

        // Sub_Screen mode uses VSP directly, no try-order needed
        if (connectionConfig?.connectionMode == ConnectionMode.SUB_SCREEN) return null

        // Use try-order when we would have used detection (cable mode with AUTO or unspecified protocol)
        val isCableTryOrder = when {
            connectionConfig == null -> false
            connectionConfig.connectionMode == ConnectionMode.LAN -> false  // LAN never falls back to cable
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
     * Expands the CABLE strategy of a Cross-Device AUTO session into concrete candidates.
     *
     * An explicitly configured sub-protocol must win over the AUTO try-order: callers that pass
     * `cableProtocol = USB_AOA` expect the peer to be driven into accessory mode, but the plain
     * try-order starts at VSP and would claim the port first, so the AOA switch never happens.
     * Only [CableProtocol.AUTO]/unset falls back to the reordered VSP → RS232 → AOA list.
     *
     * Resolving an explicit protocol needs no [Context]; only the try-order does, so a null
     * context yields an empty cable candidate list rather than silently dropping an explicit choice.
     */
    private fun buildCableCandidates(
        connectionConfig: ConnectionConfig,
        context: Context?
    ): List<Pair<String, String>> {
        val explicit = connectionConfig.cableProtocol
        if (explicit != null && explicit != CableProtocol.AUTO) {
            val (protocol, mode) = buildProtocolFromCableProtocol(explicit, context)
            return listOf(Pair(protocol, mode ?: explicit.name))
        }
        if (context == null) return emptyList()
        return buildCableTryOrder(context)
    }

    /**
     * Builds the reordered cable protocol try-list (VSP -> RS232 -> AOA base order, with the
     * physically-inserted cable's protocol — or the last-successful cached protocol — moved first).
     * Shared by [getCableProtocolsToTry] and [getCrossDeviceAutoCandidates].
     */
    private fun buildCableTryOrder(context: Context): List<Pair<String, String>> {
        // Base order: VSP, RS232, AOA. Prefer serial when peer enumerates as CDC; AOA last for stale-enum cases.
        val baseOrder = listOf(
            Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name),
            Pair(ProtocolManager.buildVspProtocol(), CableProtocol.USB_VSP.name),
            Pair(ProtocolManager.buildRs232Protocol(), CableProtocol.RS232.name)
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
     * Get the ordered transport candidates for a Cross-Device AUTO session.
     *
     * Only applies when [ConnectionConfig.crossDeviceAuto] is true. Iterates
     * [ConnectionConfig.autoPriority] and expands each strategy into concrete attempts:
     * - [CrossDeviceStrategy.LAN] → a single LAN candidate (requires a non-blank host)
     * - [CrossDeviceStrategy.CABLE] → the explicitly configured cable sub-protocol when one is set,
     *   otherwise the cable try-order (VSP/RS232/AOA, reordered by inserted cable)
     *
     * The connection layer ([com.sunmi.tapro.taplink.sdk.manager.ConnectionManager]) tries the
     * returned candidates in order, falling back to the next on connection failure/timeout.
     *
     * @return ordered (protocol, connectionMode) candidates, or null when not a CROSS_DEVICE AUTO session
     */
    fun getCrossDeviceAutoCandidates(
        connectionConfig: ConnectionConfig?,
        context: Context?
    ): List<Pair<String, String>>? {
        if (connectionConfig == null || !connectionConfig.crossDeviceAuto) return null

        val candidates = mutableListOf<Pair<String, String>>()
        for (strategy in connectionConfig.autoPriority) {
            when (strategy) {
                CrossDeviceStrategy.LAN -> {
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
                CrossDeviceStrategy.CABLE -> {
                    candidates.addAll(buildCableCandidates(connectionConfig, context))
                }
                CrossDeviceStrategy.AUTO -> {
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
