package com.sunmi.tapro.taplink.sdk.reconnect

import android.os.Handler
import android.os.Looper
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * 重连管理器
 *
 * 负责管理自动重连逻辑，包括：
 * - 重连状态管理
 * - 重试计数
 * - 延迟重连调度
 *
 * @author TaPro Team
 * @since 2025-12-11
 */
class ReconnectManager(
    private val config: TaplinkConfig,
    private val reconnectAction: (ConnectionConfig?, ConnectionListener) -> Unit
) {
    private val TAG = "ReconnectManager"

    /**
     * 是否为主动断开连接
     */
    private var isManualDisconnect = false

    /**
     * 当前重试次数
     */
    private var currentRetryCount = 0

    /**
     * 是否正在重连中
     */
    private var isReconnecting = false

    /**
     * 上次连接配置（用于重连）
     */
    private var lastConnectionConfig: ConnectionConfig? = null

    /**
     * 上次连接监听器（用于重连）
     */
    private var lastConnectionListener: ConnectionListener? = null

    /**
     * 重连处理器
     */
    private val retryHandler = Handler(Looper.getMainLooper())

    /**
     * 准备连接
     *
     * 保存连接参数，重置重连状态
     * 当用户手动调用 connect() 时，会重置所有状态，允许重新开始连接
     *
     * @param config 连接配置
     * @param listener 连接监听器
     */
    fun prepareConnect(config: ConnectionConfig?, listener: ConnectionListener) {
        LogUtil.d(TAG, "Preparing connection, resetting reconnect state")
        lastConnectionConfig = config
        lastConnectionListener = listener
        isManualDisconnect = false
        currentRetryCount = 0
        isReconnecting = false

        // 取消所有待处理的重连任务
        retryHandler.removeCallbacksAndMessages(null)
    }

    /**
     * 连接成功
     *
     * 重置重试计数和重连状态
     */
    fun onConnected() {
        currentRetryCount = 0
        isReconnecting = false
        LogUtil.d(TAG, "Connection established, reset reconnect state")
    }

    /**
     * 连接断开
     *
     * 判断是否需要自动重连
     *
     * @param listener 连接监听器
     * @return Boolean 是否触发了自动重连
     */
    fun onDisconnected(listener: ConnectionListener): Boolean {
        // 防止重复触发重连
        if (isReconnecting) {
            LogUtil.w(TAG, "Already reconnecting, ignoring duplicate onDisconnected call")
            return true // 返回 true 表示正在处理重连，不需要通知应用层
        }

        val shouldAutoReconnect = config.autoReconnect &&
                !isManualDisconnect &&
                currentRetryCount < config.maxRetryCount

        if (shouldAutoReconnect) {
            isReconnecting = true
            currentRetryCount++
            LogUtil.d(TAG, "Connection lost, attempting reconnect ($currentRetryCount/${config.maxRetryCount})")

            // 通知监听器正在重连
            listener.onReconnecting(currentRetryCount, config.maxRetryCount)
            lastConnectionListener?.onReconnecting(currentRetryCount, config.maxRetryCount)

            // 延迟重连
            retryHandler.postDelayed({
                attemptReconnect()
            }, config.retryDelayMs)

            return true
        }

        // 重连次数用完或不需要重连
        if (!isManualDisconnect && currentRetryCount >= config.maxRetryCount) {
            LogUtil.e(TAG, "Max retry attempts reached ($currentRetryCount/${config.maxRetryCount}), giving up auto-reconnection")
            LogUtil.i(TAG, "User can manually call connect() to reconnect")
        }

        // 重置重连状态，允许用户手动重连
        isReconnecting = false

        return false
    }

    /**
     * 主动断开
     *
     * 标记为主动断开，取消所有重试任务
     */
    fun disconnect() {
        LogUtil.d(TAG, "Manual disconnect")
        isManualDisconnect = true
        isReconnecting = false
        currentRetryCount = 0
        retryHandler.removeCallbacksAndMessages(null)

        // 清空保存的连接参数（主动断开时不保留）
        lastConnectionConfig = null
        lastConnectionListener = null
        LogUtil.d(TAG, "Cleared saved connection config due to manual disconnect")
    }

    /**
     * 尝试重新连接
     *
     * 使用上次保存的连接配置和监听器进行重连
     */
    private fun attemptReconnect() {
        LogUtil.d(TAG, "Attempting to reconnect...")

        val config = lastConnectionConfig
        val listener = lastConnectionListener

        if (listener != null) {
            // 保存当前重连状态
            val savedRetryCount = currentRetryCount
            val savedManualDisconnect = isManualDisconnect
            val savedReconnecting = isReconnecting

            // 重新连接
            reconnectAction(config, listener)

            // 恢复重连状态（因为 reconnectAction 可能会重置这些值）
            currentRetryCount = savedRetryCount
            isManualDisconnect = savedManualDisconnect
            isReconnecting = savedReconnecting
        } else {
            LogUtil.e(TAG, "Cannot reconnect: no listener available")
            isReconnecting = false
        }
    }

    /**
     * 是否为主动断开
     */
    fun isManualDisconnect(): Boolean = isManualDisconnect

    /**
     * 获取当前重试次数
     */
    fun getCurrentRetryCount(): Int = currentRetryCount

    /**
     * 获取上次的连接配置
     *
     * 用于在没有传递 ConnectionConfig 时使用上次的连接参数
     *
     * @return 上次的连接配置，如果没有则返回 null
     */
    fun getLastConnectionConfig(): ConnectionConfig? = lastConnectionConfig

    /**
     * 处理服务地址变更
     *
     * 当mDNS检测到服务地址变更时，更新连接配置并触发重连
     *
     * @param newConnectionConfig 新的连接配置
     * @param listener 连接监听器
     */
    fun onAddressChanged(newConnectionConfig: ConnectionConfig, listener: ConnectionListener) {
        LogUtil.i(TAG, "Service address changed, updating connection config and reconnecting:config::$newConnectionConfig")

        // 更新保存的连接配置
        lastConnectionConfig = newConnectionConfig

        // 重置重连状态，因为这是地址变更导致的重连，不是失败重试
        currentRetryCount = 0
        isManualDisconnect = false
        isReconnecting = true

        // 取消现有的重连任务
        retryHandler.removeCallbacksAndMessages(null)

        // 通知监听器正在重连（地址变更）
        listener.onReconnecting(0, config.maxRetryCount)
        lastConnectionListener?.onReconnecting(0, config.maxRetryCount)

        // 立即尝试连接到新地址（不延迟）
        LogUtil.d(TAG, "Attempting to connect to new address immediately")
        reconnectAction(newConnectionConfig, listener)
    }
}
