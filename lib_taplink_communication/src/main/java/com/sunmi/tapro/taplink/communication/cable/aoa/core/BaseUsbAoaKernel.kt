package com.sunmi.tapro.taplink.communication.cable.aoa.core

import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbManager
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * USB AOA内核共同基类
 *
 * 提供Host和Accessory模式的共同功能：
 * - USB管理器初始化
 * - 协议验证
 * - 基础连接管理
 * - 数据接收任务管理
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class BaseUsbAoaKernel(
    appId: String,
    appSecretKey: String,
    protected val context: Context,
    protected val usbStandardInfo: UsbStandardProtocol.UsbStandardInfo,
    protected val permissionAction: String,
    protected val permissionPendingIntent: PendingIntent,
) : AsyncServiceKernel(appId, appSecretKey) {

    protected open val TAG = this::class.simpleName ?: "BaseUsbAoaKernel"

    protected val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // 数据接收任务
    protected var dataReceiveJob: Job? = null

    // ==================== AsyncServiceKernel抽象方法实现 ====================

    /**
     * 获取服务类型名称（用于日志）
     */
    override fun getServiceType(): String {
        return "USB AOA"
    }

    /**
     * 获取期望的协议类型描述
     */
    override fun getExpectedProtocolType(): String {
        return "USB"
    }

    /**
     * 验证协议类型是否有效
     */
    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.UsbProtocol
    }

    /**
     * 执行具体的连接逻辑
     */
    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        if (parseResult !is ProtocolParseResult.UsbProtocol) {
            handleConnectionError(InnerErrorCode.C48.description, connectionCallback, InnerErrorCode.C48.code)
            return
        }

        scope.launch {
            performUsbConnect(parseResult, connectionCallback)
        }
    }

    /**
     * 执行USB连接逻辑（由子类实现）
     */
    protected abstract suspend fun performUsbConnect(
        parseResult: ProtocolParseResult.UsbProtocol,
        connectionCallback: ConnectionCallback
    )

    /**
     * 启动数据接收（由子类实现）
     */
    protected abstract fun startDataReceive()

    /**
     * 断开连接（内部方法，由子类实现）
     */
    protected abstract fun disconnectInternal()

    /**
     * 执行具体的断开连接逻辑
     */
    override fun performDisconnect() {
        disconnectInternal()
    }

    /**
     * 清理公共资源
     */
    override fun cleanupCommonResources() {
        super.cleanupCommonResources()

        // 停止数据接收
        dataReceiveJob?.cancel()
        dataReceiveJob = null
    }

    /**
     * 清理资源（由子类实现）
     */
    abstract fun release()
}