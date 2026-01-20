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
}