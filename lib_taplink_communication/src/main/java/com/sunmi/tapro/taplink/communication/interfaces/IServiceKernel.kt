package com.sunmi.tapro.taplink.communication.interfaces

import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus

/**
 * 服务内核接口
 * 
 * 定义所有服务内核必须实现的基本功能
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface IServiceKernel {
    
    /**
     * 连接服务
     * 
     * @param protocol 协议字符串，不同服务类型格式不同
     * @param connectionCallback 连接回调
     */
    fun connect(protocol: String, connectionCallback: ConnectionCallback)
    
    /**
     * 发送数据
     * 
     * @param traceId 追踪ID
     * @param data 要发送的数据
     * @param callback 发送结果回调，可为null
     */
    fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?)
    
    
    /**
     * 注册数据接收器
     * 
     * @param receiver 数据接收回调
     */
    fun registerDataReceiver(receiver: (dataArray: ByteArray?) -> Unit)
    
    /**
     * 接收数据
     * 
     * 当服务端主动推送数据时，调用此方法将数据传递给已注册的数据接收器
     * 
     * @param data 接收到的数据
     */
    fun receiveData(data: ByteArray)
    
    /**
     * 断开连接
     */
    fun disconnect()
    
    /**
     * 获取当前连接状态
     * 
     * @return ConnectionStatus 当前连接状态
     */
    fun getConnectionStatus(): InnerConnectionStatus
    
    /**
     * 设置状态变化监听器
     * 
     * @param listener 状态变化监听器
     */
    fun setStatusChangeListener(listener: (InnerConnectionStatus) -> Unit)
}