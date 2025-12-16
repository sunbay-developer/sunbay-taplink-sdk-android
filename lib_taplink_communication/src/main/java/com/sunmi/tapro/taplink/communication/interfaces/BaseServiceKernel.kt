package com.sunmi.tapro.taplink.communication.interfaces

import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * 服务内核抽象基类
 *
 * 提供统一的状态管理功能，减少代码重复
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class BaseServiceKernel : IServiceKernel {
    /**
     * 当前连接状态
     */
    @Volatile
    protected var currentConnectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED

    /**
     * 状态变化监听器
     */
    private var statusChangeListener: ((ConnectionStatus) -> Unit)? = null

    /**
     * 数据接收器
     * 用于接收从服务端推送的数据
     */
    protected var dataReceiver: ((ByteArray?) -> Unit)? = null

    /**
     * 获取当前连接状态
     *
     * @return ConnectionStatus 当前连接状态
     */
    override fun getConnectionStatus(): ConnectionStatus {
        return currentConnectionStatus
    }

    /**
     * 设置状态变化监听器
     *
     * @param listener 状态变化监听器
     */
    override fun setStatusChangeListener(listener: (ConnectionStatus) -> Unit) {
        statusChangeListener = listener
    }

    /**
     * 更新连接状态
     *
     * @param newStatus 新状态
     */
    protected fun updateStatus(newStatus: ConnectionStatus) {
        if (currentConnectionStatus != newStatus) {
            val oldStatus = currentConnectionStatus
            currentConnectionStatus = newStatus
            logStatusChange(oldStatus, newStatus)
            statusChangeListener?.invoke(newStatus)
        }
    }

    /**
     * 记录状态变化日志
     *
     * @param oldStatus 旧状态
     * @param newStatus 新状态
     */
    protected open fun logStatusChange(oldStatus: ConnectionStatus, newStatus: ConnectionStatus) {
        LogUtil.d(getTag(), "Status changed: $oldStatus -> $newStatus")
    }

    /**
     * 获取日志标签
     *
     * 默认使用类名
     *
     * @return String 日志标签
     */
    protected open fun getTag(): String = this::class.simpleName ?: "BaseServiceKernel"

    /**
     * 检查是否可以发送数据
     *
     * @return Boolean 是否可以发送数据
     */
    protected fun canSendData(): Boolean {
        return currentConnectionStatus == ConnectionStatus.CONNECTED
    }

    /**
     * 检查是否可以连接
     *
     * @return Boolean 是否可以连接
     */
    protected fun canConnect(): Boolean {
        return currentConnectionStatus == ConnectionStatus.DISCONNECTED ||
                currentConnectionStatus == ConnectionStatus.ERROR
    }

    /**
     * 处理连接错误
     *
     * @param errorMessage 错误信息
     * @param connectionCallback 连接回调
     * @param errorCode 错误码，默认为-1
     */
    protected fun handleConnectionError(
        errorMessage: String,
        connectionCallback: ConnectionCallback,
        errorCode: String ="-1"
    ) {
        updateStatus(ConnectionStatus.ERROR)
        connectionCallback.onDisconnected(errorCode, errorMessage)
    }

    /**
     * 处理连接成功
     *
     * @param connectionCallback 连接回调
     * @param taproVersion 版本信息，可为null
     */
    protected fun handleConnectionSuccess(
        connectionCallback: ConnectionCallback,
        extraInfoMap: Map<String, String?>? = null
    ) {
        updateStatus(ConnectionStatus.CONNECTED)
        connectionCallback.onConnected(extraInfoMap)
    }

    /**
     * 实现IServiceKernel接口：注册数据接收器
     *
     * @param receiver 数据接收回调
     */
    override fun registerDataReceiver(receiver: (dataArray: ByteArray?) -> Unit) {
        dataReceiver = receiver
        LogUtil.d(getTag(), "Data receiver registered")
    }

    /**
     * 实现IServiceKernel接口：接收数据
     *
     * 将接收到的数据传递给已注册的数据接收器
     *
     * @param data 接收到的数据
     */
    override fun receiveData(data: ByteArray) {
        if (dataReceiver != null) {
            LogUtil.d(getTag(), "Receiving data: ${data.size} bytes")
            dataReceiver?.invoke(data)
        } else {
            LogUtil.d(getTag(), "Data receiver not registered, ignoring received data: ${data.size} bytes")
        }
    }
}


