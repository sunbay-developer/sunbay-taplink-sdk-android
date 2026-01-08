package com.sunmi.tapro.taplink.communication.protocol

import com.sunmi.tapro.taplink.communication.enums.UsbMode
import com.sunmi.tapro.taplink.communication.local.constant.LocalServiceConstant
import com.sunmi.tapro.taplink.communication.util.LogUtil
import java.net.URI

/**
 * 统一协议管理工具类
 *
 * 整合协议构建和解析功能，避免在不同层次重复处理协议逻辑
 *
 * 功能特性：
 * 1. 协议字符串解析
 * 2. 协议字符串构建
 * 3. 协议格式验证
 * 4. 协议类型转换
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object ProtocolManager {

    private const val TAG = "ProtocolManager"

    // ==================== 协议解析 ====================

    /**
     * 解析协议字符串
     *
     * @param protocol 协议字符串
     * @return ProtocolParseResult 解析结果
     */
    fun parseProtocol(protocol: String): ProtocolParseResult {
        return ProtocolParser.parse(protocol)
    }

    /**
     * 验证协议格式是否有效
     *
     * @param protocol 协议字符串
     * @return Boolean 是否有效
     */
    fun isValidProtocol(protocol: String): Boolean {
        val result = parseProtocol(protocol)
        return result !is ProtocolParseResult.Error
    }

    /**
     * 获取协议类型
     *
     * @param protocol 协议字符串
     * @return String 协议类型名称
     */
    fun getProtocolType(protocol: String): String {
        return when (val result = parseProtocol(protocol)) {
            is ProtocolParseResult.LocalProtocol -> "LOCAL"
            is ProtocolParseResult.LanProtocol -> "LAN"
            is ProtocolParseResult.VspProtocol -> "VSP"
            is ProtocolParseResult.UsbProtocol -> "USB"
            is ProtocolParseResult.SerialProtocol -> "SERIAL"
            is ProtocolParseResult.Error -> "UNKNOWN"
        }
    }

    // ==================== 协议构建 ====================

    /**
     * 构建本地服务协议
     *
     * @param packageName 包名
     * @param action 动作（可选）
     * @return String 协议字符串
     */
    fun buildLocalProtocol(
        packageName: String = LocalServiceConstant.DEFAULT_PACKAGE_NAME,
        action: String? = LocalServiceConstant.DEFAULT_ACTION
    ): String {
        return ProtocolConstants.buildLocalProtocol(packageName, action)
    }

    /**
     * 构建局域网协议
     *
     * @param host 主机地址
     * @param port 端口号
     * @param secure 是否使用安全连接（wss）
     * @return String 协议字符串
     */
    fun buildLanProtocol(host: String, port: Int, secure: Boolean = false): String {
        return ProtocolConstants.buildLanProtocol(host, port, secure)
    }

    /**
     * 构建虚拟串口协议
     *
     * @param baudRate 波特率
     * @param dataBits 数据位
     * @param parity 校验位
     * @param stopBits 停止位
     * @return String 协议字符串
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
     * 构建USB Host协议
     *
     * @param deviceName 设备名称（可选）
     * @param deviceId 设备ID（可选）
     * @return String 协议字符串
     */
    fun buildUsbHostProtocol(deviceName: String? = null, deviceId: String? = null): String {
        return ProtocolConstants.buildUsbHostProtocol(deviceName, deviceId)
    }

    /**
     * 构建USB Accessory协议
     *
     * @return String 协议字符串
     */
    fun buildUsbAccessoryProtocol(): String {
        return ProtocolConstants.buildUsbAccessoryProtocol()
    }

    /**
     * 构建RS232协议
     *
     * @param devicePath 设备路径
     * @param baudRate 波特率
     * @return String 协议字符串
     */
    fun buildRs232Protocol(): String {
        return ProtocolConstants.buildRs232Protocol()
    }

    // ==================== 协议转换 ====================

    /**
     * 将局域网协议转换为WebSocket URI
     *
     * @param protocol 协议字符串
     * @return URI? WebSocket URI，转换失败返回null
     */
    fun lanProtocolToWebSocketUri(lanProtocol: ProtocolParseResult.LanProtocol): URI? {
        return try {
            val scheme = if (lanProtocol.port == 443) "wss" else "ws"
            URI("$scheme://${lanProtocol.ip}:${lanProtocol.port}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to create WebSocket URI: ${e.message}")
            null
        }
    }

    /**
     * 将WebSocket URI转换为局域网协议
     *
     * @param uri WebSocket URI
     * @return String? 协议字符串，转换失败返回null
     */
    fun webSocketUriToLanProtocol(uri: URI): String? {
        return try {
            val secure = uri.scheme == "wss"
            buildLanProtocol(uri.host, uri.port, secure)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert WebSocket URI to LAN protocol: ${e.message}")
            null
        }
    }

    /**
     * 检查协议是否支持指定的连接类型
     *
     * @param protocol 协议字符串
     * @param connectionType 连接类型
     * @return Boolean 是否支持
     */
    fun supportsConnectionType(protocol: String, connectionType: String): Boolean {
        val protocolType = getProtocolType(protocol)
        return when (connectionType.uppercase()) {
            "LOCAL" -> protocolType == "LOCAL"
            "LAN", "WEBSOCKET" -> protocolType == "LAN"
            "USB" -> protocolType == "USB"
            "SERIAL", "VSP" -> protocolType in listOf("SERIAL", "VSP")
            else -> false
        }
    }

    // ==================== 协议信息提取 ====================

    /**
     * 从协议中提取连接信息
     *
     * @param protocol 协议字符串
     * @return Map<String, Any> 连接信息
     */
    fun extractConnectionInfo(protocol: String): Map<String, Any> {
        val result = parseProtocol(protocol)
        return when (result) {
            is ProtocolParseResult.LocalProtocol -> mapOf(
                "type" to "LOCAL",
                "packageName" to result.packageName,
                "action" to (result.action ?: "")
            )

            is ProtocolParseResult.LanProtocol -> mapOf(
                "type" to "LAN",
                "host" to result.ip,
                "port" to result.port
            )

            is ProtocolParseResult.VspProtocol -> mapOf(
                "type" to "VSP",
                "baudRate" to result.baudRate,
                "dataBits" to result.dataBits,
                "parity" to result.parity,
                "stopBits" to result.stopBits
            )

            is ProtocolParseResult.UsbProtocol -> mapOf(
                "type" to "USB",
                "mode" to result.mode.name,
                "deviceName" to (result.deviceName ?: ""),
                "deviceId" to (result.deviceId ?: "")
            )

            is ProtocolParseResult.SerialProtocol -> mapOf(
                "type" to "SERIAL",
                "type" to "VSP",
                "baudRate" to result.baudRate,
                "dataBits" to result.dataBits,
                "parity" to result.parity,
                "stopBits" to result.stopBits
            )

            is ProtocolParseResult.Error -> mapOf(
                "type" to "ERROR",
                "error" to result.message
            )
        }
    }

    /**
     * 获取协议的显示名称
     *
     * @param protocol 协议字符串
     * @return String 显示名称
     */
    fun getProtocolDisplayName(protocol: String): String {
        val result = parseProtocol(protocol)
        return when (result) {
            is ProtocolParseResult.LocalProtocol -> "本地服务 (${result.packageName})"
            is ProtocolParseResult.LanProtocol -> "局域网 (${result.ip}:${result.port})"
            is ProtocolParseResult.VspProtocol -> "虚拟串口 (${result.baudRate})"
            is ProtocolParseResult.UsbProtocol -> "USB ${result.mode.name}"
            is ProtocolParseResult.SerialProtocol -> "串口 (${result.baudRate})"
            is ProtocolParseResult.Error -> "无效协议"
        }
    }

    // ==================== 协议验证 ====================

    /**
     * 验证局域网协议的主机和端口
     *
     * @param host 主机地址
     * @param port 端口号
     * @return Boolean 是否有效
     */
    fun isValidLanAddress(host: String, port: Int): Boolean {
        if (host.isBlank()) return false
        if (port <= 0 || port > 65535) return false

        // 简单的IP地址格式验证
        val ipPattern = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        val hostnamePattern = Regex("""^[a-zA-Z0-9.-]+$""")

        return ipPattern.matches(host) || hostnamePattern.matches(host)
    }

    /**
     * 验证USB设备标识
     *
     * @param deviceIdentifier 设备标识（名称或ID）
     * @return Boolean 是否有效
     */
    fun isValidUsbDeviceIdentifier(deviceIdentifier: String?): Boolean {
        if (deviceIdentifier.isNullOrBlank()) return true // 允许为空，表示使用默认设备

        // 检查是否为数字ID或有效的设备名称
        return deviceIdentifier.toIntOrNull() != null ||
                deviceIdentifier.matches(Regex("""^[a-zA-Z0-9_-]+$"""))
    }

    /**
     * 验证串口设备路径
     *
     * @param devicePath 设备路径
     * @return Boolean 是否有效
     */
    fun isValidSerialDevicePath(devicePath: String): Boolean {
        if (devicePath.isBlank()) return false

        // 常见的串口设备路径格式
        val patterns = listOf(
            Regex("""^/dev/tty[A-Z0-9]+$"""),  // Linux/Android
            Regex("""^COM\d+$"""),             // Windows
            Regex("""^/dev/cu\..+$""")         // macOS
        )

        return patterns.any { it.matches(devicePath) }
    }
}