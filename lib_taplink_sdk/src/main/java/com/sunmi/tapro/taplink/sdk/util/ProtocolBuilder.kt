package com.sunmi.tapro.taplink.sdk.util

import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager

/**
 * 协议构建工具类
 *
 * 负责根据 ConnectionConfig 和 TaplinkConfig 构建协议字符串
 *
 * @author TaPro Team
 * @since 2025-12-17
 */
object ProtocolBuilder {

    /**
     * 根据配置构建协议字符串
     *
     * @param connectionConfig 连接配置
     * @param taplinkConfig Taplink 配置
     * @return 协议字符串
     */
    fun buildProtocol(
        connectionConfig: ConnectionConfig?,
        taplinkConfig: TaplinkConfig
    ): Pair<String, String?> {
        // 检查 connectionConfig 是否为空或空对象
        if (connectionConfig == null || isEmptyConnectionConfig(connectionConfig)) {
            // 使用 TaplinkConfig 中的 connectionMode
            return buildProtocolFromConnectionMode(taplinkConfig.connectionMode)
        }

        // 线缆模式
        connectionConfig.cableProtocol?.let { protocol ->
            return buildProtocolFromCableProtocol(protocol)
        }

        // 局域网模式
        connectionConfig.host?.let { host ->
            val port = connectionConfig.port ?: 8443
            val protocolString = ProtocolManager.buildLanProtocol(host, port, secure = true)
            return Pair(protocolString, ConnectionMode.LAN.name)
        }

        // 默认：应用间模式
        return Pair(ProtocolManager.buildLocalProtocol(), ConnectionMode.APP_TO_APP.name)
    }

    /**
     * 根据 ConnectionMode 构建协议
     */
    private fun buildProtocolFromConnectionMode(mode: ConnectionMode?): Pair<String, String?> {
        return when (mode) {
            ConnectionMode.CABLE -> {
                //TODO 需要检查
                Pair(ProtocolManager.buildUsbAccessoryProtocol(), CableProtocol.USB_AOA.name)

            }

            ConnectionMode.LAN -> {
                // 局域网模式需要 host 和 port，这里返回默认值
                Pair("wss://", ConnectionMode.LAN.name)
            }

            ConnectionMode.APP_TO_APP -> {
                Pair(ProtocolManager.buildLocalProtocol(), ConnectionMode.APP_TO_APP.name)
            }

            else -> {
                // 默认为 APP_TO_APP
                Pair(ProtocolManager.buildLocalProtocol(), ConnectionMode.APP_TO_APP.name)
            }
        }
    }

    /**
     * 根据 CableProtocol 构建协议
     */
    private fun buildProtocolFromCableProtocol(protocol: CableProtocol): Pair<String, String?> {
        return when (protocol) {
            CableProtocol.USB_AOA -> {
                Pair(ProtocolManager.buildUsbAccessoryProtocol(), CableProtocol.USB_AOA.name)
            }

            CableProtocol.USB_VSP -> {
                Pair(ProtocolManager.buildVspProtocol(), CableProtocol.USB_VSP.name)
            }

            CableProtocol.RS232 -> {
                Pair(ProtocolManager.buildRs232Protocol("/dev/ttyS0", 9600), CableProtocol.RS232.name)
            }

            CableProtocol.AUTO -> {
                Pair("auto://", null)
            }
        }
    }

    /**
     * 判断 ConnectionConfig 是否为空对象
     */
    private fun isEmptyConnectionConfig(config: ConnectionConfig): Boolean {
        return config.cableProtocol == null &&
                config.host.isNullOrBlank() &&
                config.port == null
    }
}
