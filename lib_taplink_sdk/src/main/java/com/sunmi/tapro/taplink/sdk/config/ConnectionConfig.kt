package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.enums.CableProtocol

/**
 * 连接配置类
 * 
 * 用于配置不同连接模式的参数
 * 支持应用间模式、线缆模式和局域网模式
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ConnectionConfig {
    
    /**
     * 线缆协议类型（可选）
     * 用于线缆模式时指定协议类型
     */
    var cableProtocol: CableProtocol? = null
        private set
    
    /**
     * 主机地址（可选）
     * 用于局域网模式时指定支付终端的IP地址
     */
    var host: String? = null
        private set
    
    /**
     * 端口号（可选）
     * 用于局域网模式时指定支付终端的端口（8443-8453）
     */
    var port: Int? = null
        private set
    
    /**
     * 设置线缆协议
     * 
     * @param protocol 线缆协议类型
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setCableProtocol(protocol: CableProtocol): ConnectionConfig {
        this.cableProtocol = protocol
        return this
    }
    
    /**
     * 设置主机地址
     * 
     * @param host 主机IP地址
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setHost(host: String): ConnectionConfig {
        this.host = host
        return this
    }
    
    /**
     * 设置端口号
     * 
     * @param port 端口号（8443-8453）
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setPort(port: Int): ConnectionConfig {
        this.port = port
        return this
    }
    
    companion object {
        /**
         * 创建默认连接配置
         * 
         * 空配置，SDK会自动检测连接模式
         * 适用于应用间模式、线缆模式（自动检测）和局域网模式（使用缓存设备）
         * 
         * @return ConnectionConfig 默认配置对象
         */
        fun createDefault(): ConnectionConfig {
            return ConnectionConfig()
        }
    }
}













