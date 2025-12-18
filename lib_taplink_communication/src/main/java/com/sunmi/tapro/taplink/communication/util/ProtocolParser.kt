package com.sunmi.tapro.taplink.communication.util

import com.sunmi.tapro.taplink.communication.enums.UsbMode
import com.sunmi.tapro.taplink.communication.local.constant.LocalServiceConstant

/**
 * Protocol Parse Result Data Class
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
sealed class ProtocolParseResult {
    /**
     * Local service protocol parse result
     */
    data class LocalProtocol(
        val packageName: String,
        val action: String?
    ) : ProtocolParseResult()

    /**
     * LAN protocol parse result
     */
    data class LanProtocol(
        val ip: String,
        val port: Int
    ) : ProtocolParseResult()

    /**
     * Virtual serial port protocol parse result
     */
    data class VspProtocol(
        val baudRate: Int,
        val dataBits: Int,
        val parity: String,
        val stopBits: Int
    ) : ProtocolParseResult()

    /**
     * USB protocol parse result
     */
    data class UsbProtocol(
        val mode: UsbMode = UsbMode.HOST,  // USB mode: HOST or ACCESSORY
        val deviceName: String? = null,
        val deviceId: String? = null
    ) : ProtocolParseResult()

    /**
     * Serial protocol parse result (compatible with old format)
     */
    data class SerialProtocol(
        val devicePath: String,
        val baudRate: Int
    ) : ProtocolParseResult()

    /**
     * Parse error
     */
    data class Error(val message: String) : ProtocolParseResult()
}

/**
 * Protocol Parser Utility Class
 *
 * Supports parsing the following protocol formats:
 * - local://packageName/action - Local service
 * - lan://ip/port - LAN connection
 * - vsp://baudRate/dataBits/parity/stopBits - Virtual serial port
 * - usb://host/deviceName or usb://host/deviceId - USB Host mode
 * - usb://accessory - USB Accessory mode
 * - usb://deviceName or usb://deviceId - USB device (default Host mode, compatible with old format)
 * - serial://devicePath:baudRate - Serial port (compatible format)
 * - ws:// or wss:// - WebSocket
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ProtocolParser {
    private const val TAG = "ProtocolParser"

    /**
     * Parse protocol string
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult Parse result
     */
    fun parse(protocol: String): ProtocolParseResult {
        if (protocol.isEmpty()) {
            return ProtocolParseResult.Error("Protocol string is empty")
        }

        return when {
            protocol.startsWith("local://") -> parseLocalProtocol(protocol)
            protocol.startsWith("lan://") -> parseLanProtocol(protocol)
            protocol.startsWith("vsp://") -> parseVspProtocol(protocol)
            protocol.startsWith("usb://") -> parseUsbProtocol(protocol)
            protocol.startsWith("serial://") -> parseSerialProtocol(protocol)
            else -> ProtocolParseResult.Error("Unsupported protocol format: $protocol")
        }
    }

    /**
     * Parse local service protocol: local://packageName/action
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult.LocalProtocol or Error
     */
    private fun parseLocalProtocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix("local://")
            if (content.isEmpty()) {
                return ProtocolParseResult.LocalProtocol(
                    LocalServiceConstant.DEFAULT_PACKAGE_NAME,
                    LocalServiceConstant.DEFAULT_ACTION
                )
            }

            val parts = content.split("/")
            val packageName = parts[0]
            val action = if (parts.size > 1) parts[1] else null

            if (packageName.isEmpty()) {
                return ProtocolParseResult.Error("Package name is empty in local protocol")
            }

            return ProtocolParseResult.LocalProtocol(packageName, action)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse local protocol: $protocol\n${e.stackTraceToString()}")
            return ProtocolParseResult.Error("Failed to parse local protocol: ${e.message}")
        }
    }

    /**
     * Parse LAN protocol: lan://ip/port
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult.LanProtocol or Error
     */
    private fun parseLanProtocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix("lan://")
            if (content.isEmpty()) {
                return ProtocolParseResult.Error("Lan protocol content is empty")
            }

            val parts = content.split("/")
            if (parts.size < 2) {
                return ProtocolParseResult.Error("Invalid lan protocol format, expected: lan://ip/port")
            }

            val ip = parts[0]
            val port = parts[1].toIntOrNull()

            if (ip.isEmpty()) {
                return ProtocolParseResult.Error("IP address is empty in lan protocol")
            }

            if (port == null || port <= 0 || port > 65535) {
                return ProtocolParseResult.Error("Invalid port number: ${parts[1]}")
            }

            return ProtocolParseResult.LanProtocol(ip, port)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse lan protocol: $protocol\n${e.stackTraceToString()}")
            return ProtocolParseResult.Error("Failed to parse lan protocol: ${e.message}")
        }
    }

    /**
     * Parse virtual serial port protocol: vsp://baudRate/dataBits/parity/stopBits
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult.VspProtocol or Error
     */
    private fun parseVspProtocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix("vsp://")
            if (content.isEmpty()) {
                return ProtocolParseResult.Error("VSP protocol content is empty")
            }

            val parts = content.split("/")
            if (parts.size < 4) {
                return ProtocolParseResult.Error("Invalid vsp protocol format, expected: vsp://baudRate/dataBits/parity/stopBits")
            }

            val baudRate = parts[0].toIntOrNull()
            val dataBits = parts[1].toIntOrNull()
            val parity = parts[2]
            val stopBits = parts[3].toIntOrNull()

            if (baudRate == null || baudRate <= 0) {
                return ProtocolParseResult.Error("Invalid baud rate: ${parts[0]}")
            }

            if (dataBits == null || dataBits <= 0) {
                return ProtocolParseResult.Error("Invalid data bits: ${parts[1]}")
            }

            if (parity.isEmpty()) {
                return ProtocolParseResult.Error("Parity is empty")
            }

            if (stopBits == null || stopBits <= 0) {
                return ProtocolParseResult.Error("Invalid stop bits: ${parts[3]}")
            }

            return ProtocolParseResult.VspProtocol(baudRate, dataBits, parity, stopBits)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse vsp protocol: $protocol\n${e.stackTraceToString()}")
            return ProtocolParseResult.Error("Failed to parse vsp protocol: ${e.message}")
        }
    }

    /**
     * Parse USB protocol
     *
     * Supported formats:
     * - usb://host/deviceName or usb://host/deviceId - Host mode
     * - usb://accessory - Accessory mode
     * - usb://deviceName or usb://deviceId - Default Host mode (compatible with old format)
     * - usb:// - Default Host mode, use first available device
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult.UsbProtocol or Error
     */
    private fun parseUsbProtocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix("usb://")

            // USB protocol can be empty, indicating use default device (Host mode)
            if (content.isEmpty()) {
                return ProtocolParseResult.UsbProtocol(mode = UsbMode.HOST)
            }

            // Supports usb://host/deviceName or usb://accessory format
            val parts = content.split("/")
            val firstPart = parts[0].lowercase()

            val mode = when {
                firstPart == "host" -> UsbMode.HOST
                firstPart == "accessory" -> UsbMode.ACCESSORY
                else -> UsbMode.HOST  // Default Host mode (compatible with old format)
            }

            if (mode == UsbMode.ACCESSORY) {
                // Accessory mode doesn't require device identifier
                return ProtocolParseResult.UsbProtocol(mode = UsbMode.ACCESSORY)
            }

            // Host mode: Parse device identifier
            // If first part is "host", get device identifier from second part; otherwise first part is device identifier
            val deviceIdentifier = if (firstPart == "host" && parts.size > 1) {
                parts[1]
            } else if (firstPart != "host") {
                parts[0]
            } else {
                ""  // usb://host without device specified
            }

            if (deviceIdentifier.isEmpty()) {
                return ProtocolParseResult.UsbProtocol(mode = UsbMode.HOST)
            }

            // Try to parse as deviceId (number) or deviceName (string)
            val deviceId = deviceIdentifier.toIntOrNull()
            return if (deviceId != null) {
                ProtocolParseResult.UsbProtocol(mode = UsbMode.HOST, deviceId = deviceId.toString())
            } else {
                ProtocolParseResult.UsbProtocol(mode = UsbMode.HOST, deviceName = deviceIdentifier)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse usb protocol: $protocol\n${e.stackTraceToString()}")
            return ProtocolParseResult.Error("Failed to parse usb protocol: ${e.message}")
        }
    }

    /**
     * Parse serial protocol: serial://devicePath:baudRate (compatible with old format)
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult.SerialProtocol or Error
     */
    private fun parseSerialProtocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix("serial://")
            if (content.isEmpty()) {
                return ProtocolParseResult.Error("Serial protocol content is empty")
            }

            val parts = content.split(":")
            if (parts.size != 2) {
                return ProtocolParseResult.Error("Invalid serial protocol format, expected: serial://devicePath:baudRate")
            }

            val devicePath = parts[0]
            val baudRate = parts[1].toIntOrNull()

            if (devicePath.isEmpty()) {
                return ProtocolParseResult.Error("Device path is empty in serial protocol")
            }

            if (baudRate == null || baudRate <= 0) {
                return ProtocolParseResult.Error("Invalid baud rate: ${parts[1]}")
            }

            return ProtocolParseResult.SerialProtocol(devicePath, baudRate)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse serial protocol: $protocol\n${e.stackTraceToString()}")
            return ProtocolParseResult.Error("Failed to parse serial protocol: ${e.message}")
        }
    }
}


