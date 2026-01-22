package com.sunmi.tapro.taplink.communication.protocol

import com.sunmi.tapro.taplink.communication.enums.UsbMode
import com.sunmi.tapro.taplink.communication.local.constant.LocalServiceConstant
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Protocol parse result data class
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
        val port: Int,
        val secure: Boolean = false  // Whether to use secure connection (wss://), defaults to false (ws://)
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
     * Serial port protocol parse result
     */
    data class SerialProtocol(
        val baudRate: Int,
        val dataBits: Int,
        val parity: String,
        val stopBits: Int
    ) : ProtocolParseResult()

    /**
     * Parse failure
     */
    data class Error(val message: String) : ProtocolParseResult()
}

/**
 * Protocol parsing utility class
 *
 * Supports parsing the following protocol formats:
 * - local://packageName/action - Local service
 * - lan://ip/port - LAN connection
 * - vsp://baudRate/dataBits/parity/stopBits - Virtual serial port
 * - usb://host/deviceName or usb://host/deviceId - USB Host mode
 * - usb://accessory - USB Accessory mode
 * - usb://deviceName or usb://deviceId - USB device (default Host mode, backward compatible)
 * - serial://devicePath:baudRate - Serial port (compatible format)
 * - rs232://baudRate/dataBits/parity/stopBits - RS232 serial port (hex transmission)
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
            protocol.startsWith(ProtocolConstants.LOCAL_PROTOCOL_PREFIX) -> parseLocalProtocol(protocol)
            protocol.startsWith(ProtocolConstants.LAN_PROTOCOL_PREFIX) -> parseLanProtocol(protocol)
            protocol.startsWith(ProtocolConstants.LAN_WS_PROTOCOL_PREFIX) -> parseLanProtocol(protocol)
            protocol.startsWith(ProtocolConstants.VSP_PROTOCOL_PREFIX) -> parseVspProtocol(protocol)
            protocol.startsWith(ProtocolConstants.USB_PROTOCOL_PREFIX) -> parseUsbProtocol(protocol)
            protocol.startsWith(ProtocolConstants.SERIAL_PROTOCOL_PREFIX) -> parseVspProtocol(protocol)
            protocol.startsWith(ProtocolConstants.RS232_PROTOCOL_PREFIX) -> parseRs232Protocol(protocol)
            protocol.startsWith(ProtocolConstants.AUTO_PROTOCOL_PREFIX) -> parseAutoProtocol(protocol)
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
            val content = protocol.removePrefix(ProtocolConstants.LOCAL_PROTOCOL_PREFIX)
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
            // Support both wss:// and ws:// formats, and save secure connection information
            val (content, secure) = when {
                protocol.startsWith(ProtocolConstants.LAN_PROTOCOL_PREFIX) -> 
                    Pair(protocol.removePrefix(ProtocolConstants.LAN_PROTOCOL_PREFIX), true)  // wss://
                protocol.startsWith(ProtocolConstants.LAN_WS_PROTOCOL_PREFIX) -> 
                    Pair(protocol.removePrefix(ProtocolConstants.LAN_WS_PROTOCOL_PREFIX), false)  // ws://
                else -> Pair(protocol, false)  // Default non-secure connection
            }
            
            if (content.isEmpty()) {
                return ProtocolParseResult.Error("Lan protocol content is empty")
            }

            // Support host:port format
            val parts = if (content.contains(":")) {
                content.split(":")
            } else {
                content.split("/")
            }
            
            if (parts.size < 2) {
                return ProtocolParseResult.Error("Invalid lan protocol format, expected: wss://host:port or ws://host:port")
            }

            val ip = parts[0]
            val port = parts[1].toIntOrNull()

            if (ip.isEmpty()) {
                return ProtocolParseResult.Error("IP address is empty in lan protocol")
            }

            if (port == null || port <= 0 || port > 65535) {
                return ProtocolParseResult.Error("Invalid port number: ${parts[1]}")
            }

            return ProtocolParseResult.LanProtocol(ip, port, secure)
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
            val content = protocol.removePrefix(ProtocolConstants.VSP_PROTOCOL_PREFIX)
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
     * - usb://deviceName or usb://deviceId - Default Host mode (backward compatible)
     * - usb:// - Default Host mode, use first available device
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult.UsbProtocol or Error
     */
    private fun parseUsbProtocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix(ProtocolConstants.USB_PROTOCOL_PREFIX)

            // USB protocol can be empty, meaning use default device (Host mode)
            if (content.isEmpty()) {
                return ProtocolParseResult.UsbProtocol(mode = UsbMode.HOST)
            }

            // Support usb://host/deviceName or usb://accessory format
            val parts = content.split("/")
            val firstPart = parts[0].lowercase()

            val mode = when {
                firstPart == "host" -> UsbMode.HOST
                firstPart == "accessory" -> UsbMode.ACCESSORY
                else -> UsbMode.HOST  // Default Host mode (backward compatible)
            }

            if (mode == UsbMode.ACCESSORY) {
                // Accessory mode doesn't need device identifier
                return ProtocolParseResult.UsbProtocol(mode = UsbMode.ACCESSORY)
            }

            // Host mode: parse device identifier
            // If first part is "host", get device identifier from second part; otherwise first part is device identifier
            val deviceIdentifier = if (firstPart == "host" && parts.size > 1) {
                parts[1]
            } else if (firstPart != "host") {
                parts[0]
            } else {
                ""  // usb://host doesn't specify device
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
     * Parse RS232 protocol: rs232://baudRate/dataBits/parity/stopBits
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult.SerialProtocol or Error
     */
    private fun parseRs232Protocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix(ProtocolConstants.RS232_PROTOCOL_PREFIX)
            if (content.isEmpty()) {
                return ProtocolParseResult.Error("RS232 protocol content is empty")
            }

            // Separate main parameters and query parameters (ignore query parameters, because now unified use hex)
            val mainPart = if (content.contains("?")) {
                content.split("?", limit = 2)[0]
            } else {
                content
            }

            val parts = mainPart.split("/")
            if (parts.size < 4) {
                return ProtocolParseResult.Error("Invalid RS232 protocol format, expected: rs232://baudRate/dataBits/parity/stopBits")
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

            return ProtocolParseResult.SerialProtocol(baudRate, dataBits, parity, stopBits)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse RS232 protocol: $protocol\n${e.stackTraceToString()}")
            return ProtocolParseResult.Error("Failed to parse RS232 protocol: ${e.message}")
        }
    }

    /**
     * Parse auto detection protocol: auto://
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult.AutoProtocol or Error
     */
    private fun parseAutoProtocol(protocol: String): ProtocolParseResult {
        // Auto detection protocol temporarily returns error, need to implement auto detection logic in upper layer
        return ProtocolParseResult.Error("Auto protocol detection not implemented yet")
    }
}


