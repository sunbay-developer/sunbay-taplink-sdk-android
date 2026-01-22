package com.sunmi.tapro.taplink.communication.protocol

import com.sunmi.tapro.taplink.communication.enums.UsbMode
import com.sunmi.tapro.taplink.communication.local.constant.LocalServiceConstant
import com.sunmi.tapro.taplink.communication.util.LogUtil
import java.net.URI

/**
 * Unified protocol management utility class
 *
 * Integrates protocol building and parsing functionality to avoid duplicate protocol logic handling across different layers
 *
 * Features:
 * 1. Protocol string parsing
 * 2. Protocol string building
 * 3. Protocol format validation
 * 4. Protocol type conversion
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ProtocolManager {

    private const val TAG = "ProtocolManager"

    // ==================== Protocol Parsing ====================

    /**
     * Parse protocol string
     *
     * @param protocol Protocol string
     * @return ProtocolParseResult Parse result
     */
    fun parseProtocol(protocol: String): ProtocolParseResult {
        return ProtocolParser.parse(protocol)
    }

    /**
     * Validate if protocol format is valid
     *
     * @param protocol Protocol string
     * @return Boolean Whether valid
     */
    fun isValidProtocol(protocol: String): Boolean {
        val result = parseProtocol(protocol)
        return result !is ProtocolParseResult.Error
    }

    // ==================== Protocol Building ====================

    /**
     * Build local service protocol
     *
     * @param packageName Package name
     * @param action Action (optional)
     * @return String Protocol string
     */
    fun buildLocalProtocol(
        packageName: String = LocalServiceConstant.DEFAULT_PACKAGE_NAME,
        action: String? = LocalServiceConstant.DEFAULT_ACTION
    ): String {
        return ProtocolConstants.buildLocalProtocol(packageName, action)
    }

    /**
     * Build LAN protocol
     *
     * @param host Host address
     * @param port Port number
     * @param secure Whether to use secure connection (wss)
     * @return String Protocol string
     */
    fun buildLanProtocol(host: String, port: Int, secure: Boolean = false): String {
        return ProtocolConstants.buildLanProtocol(host, port, secure)
    }

    /**
     * Build virtual serial port protocol
     *
     * @param baudRate Baud rate
     * @param dataBits Data bits
     * @param parity Parity bit
     * @param stopBits Stop bits
     * @return String Protocol string
     */
    fun buildVspProtocol(
        baudRate: Int = 115200,
        dataBits: Int = 8,
        parity: String = "n",
        stopBits: Int = 1
    ): String {
        return ProtocolConstants.buildVspProtocol(baudRate, dataBits, parity, stopBits)
    }

    /**
     * Build USB Host protocol
     *
     * @param deviceName Device name (optional)
     * @param deviceId Device ID (optional)
     * @return String Protocol string
     */
    fun buildUsbHostProtocol(deviceName: String? = null, deviceId: String? = null): String {
        return ProtocolConstants.buildUsbHostProtocol(deviceName, deviceId)
    }

    /**
     * Build USB Accessory protocol
     *
     * @return String Protocol string
     */
    fun buildUsbAccessoryProtocol(): String {
        return ProtocolConstants.buildUsbAccessoryProtocol()
    }

    /**
     * Build RS232 protocol
     *
     * @param devicePath Device path
     * @param baudRate Baud rate
     * @return String Protocol string
     */
    fun buildRs232Protocol(): String {
        return ProtocolConstants.buildRs232Protocol()
    }
}