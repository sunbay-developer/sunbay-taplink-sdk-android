package com.sunmi.tapro.taplink.sdk.protocol

import android.content.Context
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.persistence.ConnectionPersistence
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.runBlocking

/**
 * 协议配置解析器
 *
 * 负责根据 ConnectionConfig 解析和选择合适的协议
 * 支持自动线缆协议检测和协议字符串构建
 *
 * @author TaPro Team
 * @since 2025-12-17
 */
object ProtocolConfigResolver {

    private val TAG = "ProtocolConfigResolver"

    /**
     * 根据配置构建协议字符串
     *
     * @param connectionConfig 连接配置
     * @param context Android 上下文 (线缆协议检测需要)
     * @return 协议字符串和连接模式名称的配对
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
                // Cable mode without specific protocol - need auto-detection
                if (context != null) {
                    detectAndBuildCableProtocol(context)
                } else {
                    // Fallback to default AOA if no context available
                    LogUtil.w(TAG, "No context available for cable protocol detection, using default AOA")
                    Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
                }
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
                // Auto-detection mode
                if (context != null) {
                    detectAndBuildCableProtocol(context)
                } else {
                    // Fallback to default AOA if no context available
                    LogUtil.w(TAG, "No context available for cable protocol detection, using default AOA")
                    Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
                }
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
     * Detect cable protocol and build appropriate protocol string
     */
    private fun detectAndBuildCableProtocol(context: Context): Pair<String, String?> {
        return try {
            LogUtil.i(TAG, "Starting cable protocol auto-detection...")

            val persistence = ConnectionPersistence(context)

            // Check if we have a recent detection result
            val cachedProtocol = persistence.getDetectedCableProtocol()
            if (cachedProtocol != null && cachedProtocol != CableProtocol.AUTO) {
                LogUtil.i(TAG, "Using cached cable protocol detection: $cachedProtocol")
                return buildProtocolFromCableProtocol(cachedProtocol, context)
            }

            // Check if we have a previous successful connection configuration
            val lastConfig = persistence.getLastConnectionConfig()
            if (lastConfig?.cableProtocol != null && lastConfig.cableProtocol != CableProtocol.AUTO) {
                LogUtil.i(TAG, "Using previous successful cable protocol: ${lastConfig.cableProtocol}")
                return buildProtocolFromCableProtocol(lastConfig.cableProtocol!!, context)
            }

            // Perform auto-detection
            val detector = CableProtocolDetector(context)
            val detectionResult = runBlocking {
                detector.detectProtocol()
            }

            LogUtil.i(
                TAG,
                "Cable protocol detection completed: ${detectionResult.protocol} (confidence: ${detectionResult.confidence})"
            )
            LogUtil.d(TAG, "Detection details: ${detectionResult.deviceInfo}")

            // Save detection result for future use
            persistence.saveDetectedCableProtocol(detectionResult.protocol, detectionResult.deviceInfo)

            // Build protocol based on detection result
            when (detectionResult.protocol) {
                CableProtocol.USB_AOA -> {
                    Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
                }

                CableProtocol.USB_VSP -> {
                    Pair(ProtocolManager.buildVspProtocol(), CableProtocol.USB_VSP.name)
                }

                CableProtocol.RS232 -> {
                    Pair(ProtocolManager.buildRs232Protocol(), CableProtocol.RS232.name)
                }

                CableProtocol.AUTO -> {
                    // Should not happen, but fallback to AOA
                    LogUtil.w(TAG, "Detection returned AUTO, falling back to USB AOA")
                    Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
                }
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Cable protocol detection failed: ${e.message}")
            LogUtil.w(TAG, "Falling back to default USB AOA protocol")

            // Fallback to default AOA
            Pair(ProtocolManager.buildUsbHostProtocol(), CableProtocol.USB_AOA.name)
        }
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
