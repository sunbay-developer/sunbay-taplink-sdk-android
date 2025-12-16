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
     * 
     * @param config 连接配置
     * @param listener 连接监听器
     */
    fun prepareConnect(config: ConnectionConfig?, listener: ConnectionListener) {
        lastConnectionConfig = config
        lastConnectionListener = listener
        isManualDisconnect = false
        currentRetryCount = 0
    }
    
    /**
     * 连接成功
     * 
     * 重置重试计数
     */
    fun onConnected() {
        currentRetryCount = 0
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
        val shouldAutoReconnect = config.autoReconnect && 
                                  !isManualDisconnect && 
                                  currentRetryCount < config.maxRetryCount
        
        if (shouldAutoReconnect) {
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
        
        if (!isManualDisconnect && currentRetryCount >= config.maxRetryCount) {
            LogUtil.e(TAG, "Max retry attempts reached, giving up reconnection")
        }
        
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
        currentRetryCount = 0
        retryHandler.removeCallbacksAndMessages(null)
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
            
            // 重新连接
            reconnectAction(config, listener)
            
            // 恢复重连状态（因为 reconnectAction 可能会重置这些值）
            currentRetryCount = savedRetryCount
            isManualDisconnect = savedManualDisconnect
        } else {
            LogUtil.e(TAG, "Cannot reconnect: no listener available")
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
}
