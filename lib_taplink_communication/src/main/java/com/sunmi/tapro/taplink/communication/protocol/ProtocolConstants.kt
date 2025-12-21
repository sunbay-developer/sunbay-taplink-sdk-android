package com.sunmi.tapro.taplink.communication.protocol

/**
 * 协议常量定义
 * 
 * 统一定义所有支持的协议格式，供 SDK 层和 Communication 层共同使用
 * 
 * @author TaPro Team
 * @since 2025-12-17
 */
object ProtocolConstants {
    
    // ==================== 协议前缀 ====================
    
    /**
     * 本地服务协议前缀
     * 格式：local://packageName/action
     * 示例：local://com.sunmi.tapro/com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION
     */
    const val LOCAL_PROTOCOL_PREFIX = "local://"
    
    /**
     * 局域网协议前缀（WebSocket Secure）
     * 格式：wss://host:port
     * 示例：wss://192.168.1.100:8443
     */
    const val LAN_PROTOCOL_PREFIX = "wss://"
    
    /**
     * 局域网协议前缀（WebSocket）
     * 格式：ws://host:port
     * 示例：ws://192.168.1.100:8080
     */
    const val LAN_WS_PROTOCOL_PREFIX = "ws://"
    
    /**
     * USB 协议前缀
     * 格式：usb://mode/device 或 usb://device
     * 示例：
     * - usb://host/deviceName
     * - usb://accessory
     * - usb://
     */
    const val USB_PROTOCOL_PREFIX = "usb://"
    
    /**
     * 虚拟串口协议前缀
     * 格式：vsp://baudRate/dataBits/parity/stopBits
     * 示例：vsp://115200/8/N/1
     */
    const val VSP_PROTOCOL_PREFIX = "vsp://"
    
    /**
     * 串口协议前缀
     * 格式：serial://devicePath:baudRate
     * 示例：serial:///dev/ttyUSB0:115200
     */
    const val SERIAL_PROTOCOL_PREFIX = "serial://"
    
    /**
     * RS232 协议前缀（串口的别名）
     * 格式：rs232://devicePath:baudRate
     * 示例：rs232:///dev/ttyS0:9600
     */
    const val RS232_PROTOCOL_PREFIX = "rs232://"
    
    /**
     * 自动检测协议前缀
     * 格式：auto://
     * 系统会自动检测可用的连接方式
     */
    const val AUTO_PROTOCOL_PREFIX = "auto://"
    
    // ==================== 完整协议格式 ====================
    
    /**
     * 默认本地服务协议
     */
    const val DEFAULT_LOCAL_PROTOCOL = LOCAL_PROTOCOL_PREFIX
    
    /**
     * 默认 USB Host 协议
     */
    const val DEFAULT_USB_HOST_PROTOCOL = "${USB_PROTOCOL_PREFIX}host"
    
    /**
     * USB Accessory 协议
     */
    const val USB_ACCESSORY_PROTOCOL = "${USB_PROTOCOL_PREFIX}accessory"
    
    /**
     * 默认虚拟串口协议（115200, 8, N, 1）
     */
    const val DEFAULT_VSP_PROTOCOL = "${VSP_PROTOCOL_PREFIX}115200/8/N/1"
    
    /**
     * 自动检测协议
     */
    const val AUTO_DETECT_PROTOCOL = AUTO_PROTOCOL_PREFIX
    
    // ==================== 协议构建器 ====================
    
    /**
     * 构建本地服务协议
     * 
     * @param packageName 包名
     * @param action 服务 Action（可选）
     * @return 协议字符串
     */
    fun buildLocalProtocol(packageName: String, action: String? = null): String {
        return if (action != null) {
            "$LOCAL_PROTOCOL_PREFIX$packageName/$action"
        } else {
            "$LOCAL_PROTOCOL_PREFIX$packageName"
        }
    }
    
    /**
     * 构建局域网协议
     * 
     * @param host 主机地址
     * @param port 端口号
     * @param secure 是否使用 WSS（默认 true）
     * @return 协议字符串
     */
    fun buildLanProtocol(host: String, port: Int, secure: Boolean = true): String {
        val prefix = if (secure) LAN_PROTOCOL_PREFIX else LAN_WS_PROTOCOL_PREFIX
        return "$prefix$host:$port"
    }
    
    /**
     * 构建 USB Host 协议
     * 
     * @param deviceName 设备名称（可选）
     * @param deviceId 设备 ID（可选）
     * @return 协议字符串
     */
    fun buildUsbHostProtocol(deviceName: String? = null, deviceId: String? = null): String {
        return when {
            deviceName != null -> "${USB_PROTOCOL_PREFIX}host/$deviceName"
            deviceId != null -> "${USB_PROTOCOL_PREFIX}host/$deviceId"
            else -> DEFAULT_USB_HOST_PROTOCOL
        }
    }
    
    /**
     * 构建 USB Accessory 协议
     * 
     * @return 协议字符串
     */
    fun buildUsbAccessoryProtocol(): String {
        return USB_ACCESSORY_PROTOCOL
    }
    
    /**
     * 构建虚拟串口协议
     * 
     * @param baudRate 波特率
     * @param dataBits 数据位
     * @param parity 校验位
     * @param stopBits 停止位
     * @return 协议字符串
     */
    fun buildVspProtocol(
        baudRate: Int = 115200,
        dataBits: Int = 8,
        parity: String = "N",
        stopBits: Int = 1
    ): String {
        return "$VSP_PROTOCOL_PREFIX$baudRate/$dataBits/$parity/$stopBits"
    }
    
    /**
     * 构建串口协议
     * 
     * @param devicePath 设备路径
     * @param baudRate 波特率
     * @return 协议字符串
     */
    fun buildSerialProtocol(devicePath: String, baudRate: Int): String {
        return "$SERIAL_PROTOCOL_PREFIX$devicePath:$baudRate"
    }
    
    /**
     * 构建 RS232 协议
     * 
     * @param devicePath 设备路径
     * @param baudRate 波特率
     * @return 协议字符串
     */
    fun buildRs232Protocol(devicePath: String, baudRate: Int): String {
        return "$RS232_PROTOCOL_PREFIX$devicePath:$baudRate"
    }
    
    // ==================== 协议检测 ====================
    
    /**
     * 检测协议类型
     * 
     * @param protocol 协议字符串
     * @return 协议类型
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
     * 协议类型枚举
     */
    enum class ProtocolType {
        LOCAL,      // 本地服务
        LAN_WSS,    // 局域网 WSS
        LAN_WS,     // 局域网 WS
        USB,        // USB
        VSP,        // 虚拟串口
        SERIAL,     // 串口
        RS232,      // RS232
        AUTO,       // 自动检测
        UNKNOWN     // 未知
    }
}