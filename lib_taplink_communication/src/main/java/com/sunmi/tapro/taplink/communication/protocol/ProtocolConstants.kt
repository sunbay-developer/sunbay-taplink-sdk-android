package com.sunmi.tapro.taplink.communication.protocol

/**
 * Protocol constants definition
 *
 * Unified definition of all supported protocol formats for use by both SDK layer and Communication layer
 *
 * @author TaPro Team
 * @since 2025-12-17
 */
object ProtocolConstants {

    // ==================== Protocol Prefixes ====================

    /**
     * Local service protocol prefix
     * Format: local://packageName/action
     * Example: local://com.sunmi.tapro/com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION
     */
    const val LOCAL_PROTOCOL_PREFIX = "local://"

    /**
     * LAN protocol prefix (WebSocket Secure)
     * Format: wss://host:port
     * Example: wss://192.168.1.100:8443
     */
    const val LAN_PROTOCOL_PREFIX = "wss://"

    /**
     * LAN protocol prefix (WebSocket)
     * Format: ws://host:port
     * Example: ws://192.168.1.100:8080
     */
    const val LAN_WS_PROTOCOL_PREFIX = "ws://"

    /**
     * USB protocol prefix
     * Format: usb://mode/device or usb://device
     * Examples:
     * - usb://host/deviceName
     * - usb://accessory
     * - usb://
     */
    const val USB_PROTOCOL_PREFIX = "usb://"

    /**
     * Virtual serial port protocol prefix
     * Format: vsp://baudRate/dataBits/parity/stopBits
     * Example: vsp://115200/8/N/1
     */
    const val VSP_PROTOCOL_PREFIX = "vsp://"

    /**
     * Serial port protocol prefix
     * Format: serial://devicePath:baudRate
     * Example: serial:///dev/ttyUSB0:115200
     */
    const val SERIAL_PROTOCOL_PREFIX = "serial://"

    /**
     * RS232 protocol prefix (alias for serial port)
     * Format: rs232://devicePath:baudRate
     * Example: rs232:///dev/ttyS0:9600
     */
    const val RS232_PROTOCOL_PREFIX = "rs232://"

    /**
     * Auto-detect protocol prefix
     * Format: auto://
     * System will automatically detect available connection methods
     */
    const val AUTO_PROTOCOL_PREFIX = "auto://"

    // ==================== Complete Protocol Formats ====================

    /**
     * Default local service protocol
     */
    const val DEFAULT_LOCAL_PROTOCOL = LOCAL_PROTOCOL_PREFIX

    /**
     * Default USB Host protocol
     */
    const val DEFAULT_USB_HOST_PROTOCOL = "${USB_PROTOCOL_PREFIX}host"

    /**
     * USB Accessory protocol
     */
    const val USB_ACCESSORY_PROTOCOL = "${USB_PROTOCOL_PREFIX}accessory"

    /**
     * Default virtual serial port protocol (115200, 8, N, 1)
     */
    const val DEFAULT_VSP_PROTOCOL = "${VSP_PROTOCOL_PREFIX}115200/8/N/1"

    /**
     * Auto-detect protocol
     */
    const val AUTO_DETECT_PROTOCOL = AUTO_PROTOCOL_PREFIX

    // ==================== Protocol Builders ====================

    /**
     * Build local service protocol
     *
     * @param packageName Package name
     * @param action Service Action (optional)
     * @return Protocol string
     */
    fun buildLocalProtocol(packageName: String, action: String? = null): String {
        return if (action != null) {
            "$LOCAL_PROTOCOL_PREFIX$packageName/$action"
        } else {
            "$LOCAL_PROTOCOL_PREFIX$packageName"
        }
    }

    /**
     * Build LAN protocol
     *
     * @param host Host address
     * @param port Port number
     * @param secure Whether to use WSS (default false, uses ws://)
     * @return Protocol string
     */
    fun buildLanProtocol(host: String, port: Int, secure: Boolean = false): String {
        val prefix = if (secure) LAN_PROTOCOL_PREFIX else LAN_WS_PROTOCOL_PREFIX
        return "$prefix$host:$port"
    }

    /**
     * Build USB Host protocol
     *
     * @param deviceName Device name (optional)
     * @param deviceId Device ID (optional)
     * @return Protocol string
     */
    fun buildUsbHostProtocol(deviceName: String? = null, deviceId: String? = null): String {
        return when {
            deviceName != null -> "${USB_PROTOCOL_PREFIX}host/$deviceName"
            deviceId != null -> "${USB_PROTOCOL_PREFIX}host/$deviceId"
            else -> DEFAULT_USB_HOST_PROTOCOL
        }
    }

    /**
     * Build USB Accessory protocol
     *
     * @return Protocol string
     */
    fun buildUsbAccessoryProtocol(): String {
        return USB_ACCESSORY_PROTOCOL
    }

    /**
     * Build virtual serial port protocol
     *
     * @param baudRate Baud rate
     * @param dataBits Data bits
     * @param parity Parity bit
     * @param stopBits Stop bits
     * @return Protocol string
     */
    fun buildVspProtocol(
        baudRate: Int = 115200,
        dataBits: Int = 8,
        parity: String = "n",
        stopBits: Int = 1
    ): String {
        return "$VSP_PROTOCOL_PREFIX$baudRate/$dataBits/$parity/$stopBits"
    }

    /**
     * Build RS232 protocol
     *
     * @param devicePath Device path
     * @param baudRate Baud rate
     * @return Protocol string
     */
    fun buildRs232Protocol(
        baudRate: Int = 115200,
        dataBits: Int = 8,
        parity: String = "n",
        stopBits: Int = 1
    ): String {
        return "$RS232_PROTOCOL_PREFIX$baudRate/$dataBits/$parity/$stopBits?mode=hex"
    }

    // ==================== Protocol Detection ====================

    /**
     * Detect protocol type
     *
     * @param protocol Protocol string
     * @return Protocol type
     */
    fun detectProtocolType(protocol: String): ProtocolType {
        return when {
            protocol.startsWith(LOCAL_PROTOCOL_PREFIX) -> ProtocolType.LOCAL
            protocol.startsWith(LAN_PROTOCOL_PREFIX) -> ProtocolType.LAN_WSS
            protocol.startsWith(LAN_WS_PROTOCOL_PREFIX) -> ProtocolType.LAN_WS
            protocol.startsWith(USB_PROTOCOL_PREFIX) -> ProtocolType.USB
            protocol.startsWith(VSP_PROTOCOL_PREFIX) -> ProtocolType.VSP
            protocol.startsWith(SERIAL_PROTOCOL_PREFIX) -> ProtocolType.SERIAL
            protocol.startsWith(RS232_PROTOCOL_PREFIX) -> ProtocolType.RS232
            protocol.startsWith(AUTO_PROTOCOL_PREFIX) -> ProtocolType.AUTO
            else -> ProtocolType.UNKNOWN
        }
    }

    /**
     * Protocol type enumeration
     */
    enum class ProtocolType {
        LOCAL,      // Local service
        LAN_WSS,    // LAN WSS
        LAN_WS,     // LAN WS
        USB,        // USB
        VSP,        // Virtual serial port
        SERIAL,     // Serial port
        RS232,      // RS232
        AUTO,       // Auto-detect
        UNKNOWN     // Unknown
    }
}