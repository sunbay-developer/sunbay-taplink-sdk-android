package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.ConnectionError

/**
 * 连接状态监听器
 * 
 * 实时监听连接状态变化、断线重连等事件。
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface ConnectionListener {
    
    /**
     * 连接成功
     * 
     * @param deviceId 设备标识
     * @param taproVersion Tapro 版本号
     */
    fun onConnected(deviceId: String, taproVersion: String)
    
    /**
     * 连接断开
     *
     * @param reason 断开原因
     */
    fun onDisconnected(reason: String)
    
    /**
     * 连接错误
     * 
     * @param error 错误信息
     */
    fun onError(error: ConnectionError)
    
    /**
     * 正在重连（可选实现）
     * 
     * 当启用自动重连时，SDK 会在重连前调用此方法
     * 
     * @param attempt 当前重试次数
     * @param maxRetries 最大重试次数
     */
    fun onReconnecting(attempt: Int, maxRetries: Int) {
        // 默认空实现，子类可选择性覆盖
    }
    
}



