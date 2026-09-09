@file:Suppress("DEPRECATION")

package com.sunmi.tapro.taplink.sdk.protocol

import android.content.Context
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager

/**
 * Protocol Config Resolver
 *
 * Responsible for parsing and selecting the appropriate protocol based on ConnectionConfig.
 *
 * The connection mode and (for cable) the concrete cable protocol must always be specified
 * explicitly by the integrator. There is no automatic transport/protocol selection: no
 * Cross-Device AUTO (LAN/CABLE auto-selection) and no Cable AUTO (VSP/RS232/AOA try-order).
 *
 * @author TaPro Team
 * @since 2025-12-17
 */
object ProtocolConfigResolver {

    /**
     * Build protocol string based on configuration.
     *
     * @param connectionConfig Connection configuration
     * @param context Android context (unused; kept for signature stability)
     * @return Pair of protocol string and connection mode name
     */
    fun buildProtocol(
        connectionConfig: ConnectionConfig?,
        context: Context? = null
    ): Pair<String, String?> {
        // Check if connectionConfig is null or empty
        if (connectionConfig == null || isEmptyConnectionConfig(connectionConfig)) {
            // No usable config: fall back to On-Device (App-to-App) local connection.
            return Pair(ProtocolManager.buildLocalProtocol(), ConnectionMode.APP_TO_APP.name)
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

        // Cable mode with an explicit protocol (VSP / RS232 / AOA).
        connectionConfig.cableProtocol?.let { protocol ->
            return buildProtocolFromCableProtocol(protocol)
        }

        // LAN mode (host provided without an explicit LAN connection mode).
        connectionConfig.host?.let { host ->
            val port = connectionConfig.port ?: 8443
            val protocolString = ProtocolManager.buildLanProtocol(host, port, secure = false)
            return Pair(protocolString, ConnectionMode.LAN.name)
        }

        // Default: APP_TO_APP (On-Device) mode.
        return Pair(ProtocolManager.buildLocalProtocol(), ConnectionMode.APP_TO_APP.name)
    }

    /**
     * Build protocol from an explicit [CableProtocol].
     */
    private fun buildProtocolFromCableProtocol(
        protocol: CableProtocol
    ): Pair<String, String?> {
        return when (protocol) {
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
     * Check if ConnectionConfig is empty (no meaningful parameters set)
     */
    private fun isEmptyConnectionConfig(config: ConnectionConfig): Boolean {
        return config.connectionMode == null &&
                config.cableProtocol == null &&
                config.host.isNullOrBlank() &&
                config.port == null
    }
}
