package com.sunmi.tapro.taplink.communication.protocol

import com.sunmi.tapro.taplink.communication.enums.UsbMode
import com.sunmi.tapro.taplink.communication.local.constant.LocalServiceConstant
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * 协议解析结果数据类
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
sealed class ProtocolParseResult {
    /**
     * 本地服务协议解析结果
     */
    data class LocalProtocol(
        val packageName: String,
        val action: String?
    ) : ProtocolParseResult()

    /**
     * 局域网协议解析结果
     */
    data class LanProtocol(
        val ip: String,
        val port: Int
    ) : ProtocolParseResult()

    /**
     * 虚拟串口协议解析结果
     */
    data class VspProtocol(
        val baudRate: Int,
        val dataBits: Int,
        val parity: String,
        val stopBits: Int
    ) : ProtocolParseResult()

    /**
     * USB协议解析结果
     */
    data class UsbProtocol(
        val mode: UsbMode = UsbMode.HOST,  // USB模式：HOST 或 ACCESSORY
        val deviceName: String? = null,
        val deviceId: String? = null
    ) : ProtocolParseResult()

    /**
     * 串口协议解析结果（兼容旧格式）
     */
    data class SerialProtocol(
        val devicePath: String,
        val baudRate: Int
    ) : ProtocolParseResult()

    /**
     * 解析失败
     */
    data class Error(val message: String) : ProtocolParseResult()
}

/**
 * 协议解析工具类
 *
 * 支持解析以下协议格式：
 * - local://packageName/action - 本地服务
 * - lan://ip/port - 局域网连接
 * - vsp://baudRate/dataBits/parity/stopBits - 虚拟串口
 * - usb://host/deviceName 或 usb://host/deviceId - USB Host模式
 * - usb://accessory - USB Accessory模式
 * - usb://deviceName 或 usb://deviceId - USB设备（默认Host模式，兼容旧格式）
 * - serial://devicePath:baudRate - 串口（兼容格式）
 * - ws:// 或 wss:// - WebSocket
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ProtocolParser {
    private const val TAG = "ProtocolParser"

    /**
     * 解析协议字符串
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult 解析结果
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
            protocol.startsWith(ProtocolConstants.SERIAL_PROTOCOL_PREFIX) -> parseSerialProtocol(protocol)
            protocol.startsWith(ProtocolConstants.RS232_PROTOCOL_PREFIX) -> parseRs232Protocol(protocol)
            protocol.startsWith(ProtocolConstants.AUTO_PROTOCOL_PREFIX) -> parseAutoProtocol(protocol)
            else -> ProtocolParseResult.Error("Unsupported protocol format: $protocol")
        }
    }

    /**
     * 解析本地服务协议：local://packageName/action
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult.LocalProtocol 或 Error
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
     * 解析局域网协议：lan://ip/port
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult.LanProtocol 或 Error
     */
    private fun parseLanProtocol(protocol: String): ProtocolParseResult {
        try {
            // 支持 wss:// 和 ws:// 两种格式
            val content = when {
                protocol.startsWith(ProtocolConstants.LAN_PROTOCOL_PREFIX) -> 
                    protocol.removePrefix(ProtocolConstants.LAN_PROTOCOL_PREFIX)
                protocol.startsWith(ProtocolConstants.LAN_WS_PROTOCOL_PREFIX) -> 
                    protocol.removePrefix(ProtocolConstants.LAN_WS_PROTOCOL_PREFIX)
                else -> protocol
            }
            
            if (content.isEmpty()) {
                return ProtocolParseResult.Error("Lan protocol content is empty")
            }

            // 支持 host:port 格式
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

            return ProtocolParseResult.LanProtocol(ip, port)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse lan protocol: $protocol\n${e.stackTraceToString()}")
            return ProtocolParseResult.Error("Failed to parse lan protocol: ${e.message}")
        }
    }

    /**
     * 解析虚拟串口协议：vsp://baudRate/dataBits/parity/stopBits
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult.VspProtocol 或 Error
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
     * 解析USB协议
     *
     * 支持格式：
     * - usb://host/deviceName 或 usb://host/deviceId - Host模式
     * - usb://accessory - Accessory模式
     * - usb://deviceName 或 usb://deviceId - 默认Host模式（兼容旧格式）
     * - usb:// - 默认Host模式，使用第一个可用设备
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult.UsbProtocol 或 Error
     */
    private fun parseUsbProtocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix(ProtocolConstants.USB_PROTOCOL_PREFIX)

            // USB协议可以为空，表示使用默认设备（Host模式）
            if (content.isEmpty()) {
                return ProtocolParseResult.UsbProtocol(mode = UsbMode.HOST)
            }

            // 支持 usb://host/deviceName 或 usb://accessory 格式
            val parts = content.split("/")
            val firstPart = parts[0].lowercase()

            val mode = when {
                firstPart == "host" -> UsbMode.HOST
                firstPart == "accessory" -> UsbMode.ACCESSORY
                else -> UsbMode.HOST  // 默认Host模式（兼容旧格式）
            }

            if (mode == UsbMode.ACCESSORY) {
                // Accessory模式不需要设备标识
                return ProtocolParseResult.UsbProtocol(mode = UsbMode.ACCESSORY)
            }

            // Host模式：解析设备标识
            // 如果第一部分是"host"，则从第二部分获取设备标识；否则第一部分就是设备标识
            val deviceIdentifier = if (firstPart == "host" && parts.size > 1) {
                parts[1]
            } else if (firstPart != "host") {
                parts[0]
            } else {
                ""  // usb://host 没有指定设备
            }

            if (deviceIdentifier.isEmpty()) {
                return ProtocolParseResult.UsbProtocol(mode = UsbMode.HOST)
            }

            // 尝试解析为deviceId（数字）或deviceName（字符串）
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
     * 解析串口协议：serial://devicePath:baudRate（兼容旧格式）
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult.SerialProtocol 或 Error
     */
    private fun parseSerialProtocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix(ProtocolConstants.SERIAL_PROTOCOL_PREFIX)
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

    /**
     * 解析 RS232 协议：rs232://devicePath:baudRate
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult.SerialProtocol 或 Error
     */
    private fun parseRs232Protocol(protocol: String): ProtocolParseResult {
        try {
            val content = protocol.removePrefix(ProtocolConstants.RS232_PROTOCOL_PREFIX)
            if (content.isEmpty()) {
                return ProtocolParseResult.Error("RS232 protocol content is empty")
            }

            val parts = content.split(":")
            if (parts.size != 2) {
                return ProtocolParseResult.Error("Invalid RS232 protocol format, expected: rs232://devicePath:baudRate")
            }

            val devicePath = parts[0]
            val baudRate = parts[1].toIntOrNull()

            if (devicePath.isEmpty()) {
                return ProtocolParseResult.Error("Device path is empty in RS232 protocol")
            }

            if (baudRate == null || baudRate <= 0) {
                return ProtocolParseResult.Error("Invalid baud rate: ${parts[1]}")
            }

            return ProtocolParseResult.SerialProtocol(devicePath, baudRate)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to parse RS232 protocol: $protocol\n${e.stackTraceToString()}")
            return ProtocolParseResult.Error("Failed to parse RS232 protocol: ${e.message}")
        }
    }

    /**
     * 解析自动检测协议：auto://
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult.AutoProtocol 或 Error
     */
    private fun parseAutoProtocol(protocol: String): ProtocolParseResult {
        // 自动检测协议暂时返回错误，需要在上层实现自动检测逻辑
        return ProtocolParseResult.Error("Auto protocol detection not implemented yet")
    }
}


