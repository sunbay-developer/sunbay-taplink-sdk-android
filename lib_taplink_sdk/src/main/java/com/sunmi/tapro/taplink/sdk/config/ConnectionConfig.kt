package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode

/**
 * 连接配置类
 * 
 * 用于配置不同连接模式的参数和连接行为
 * 支持应用间模式、线缆模式和局域网模式
 * 包含连接超时、自动重连等配置
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class ConnectionConfig {
    
    // ========== 连接模式配置 ==========
    
    /**
     * 连接模式（可选）
     * 指定使用哪种连接模式：应用间、线缆或局域网
     */
    var connectionMode: ConnectionMode? = null
        private set
    
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
    
    // ========== 连接超时配置 ==========
    
    /**
     * 连接超时时间（秒，默认 60 秒）
     * 建立连接的最大等待时间
     */
    var connectionTimeout: Int = 60
        private set
    
    // ========== 自动重连配置 ==========
    
    /**
     * 是否启用自动重连（默认 false）
     * 当连接非主动断开时，SDK 会自动尝试重连
     */
    var autoReconnect: Boolean = false
        private set
    
    /**
     * 最大重试次数（默认 3）
     * 自动重连的最大尝试次数
     */
    var maxRetryCount: Int = 3
        private set
    
    /**
     * 重试延迟时间（毫秒，默认 2000ms）
     * 每次重连尝试之间的等待时间
     */
    var retryDelayMs: Long = 2000L
        private set
    
    // ========== 连接模式配置方法 ==========
    
    /**
     * 设置连接模式
     * 
     * @param mode 连接模式
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setConnectionMode(mode: ConnectionMode): ConnectionConfig {
        this.connectionMode = mode
        return this
    }
    
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
    
    // ========== 连接超时配置方法 ==========
    
    /**
     * 设置连接超时时间
     * 
     * @param seconds 超时时间（秒）
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setConnectionTimeout(seconds: Int): ConnectionConfig {
        this.connectionTimeout = seconds
        return this
    }
    
    // ========== 自动重连配置方法 ==========
    
    /**
     * 设置是否启用自动重连
     * 
     * @param enabled 是否启用自动重连
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setAutoReconnect(enabled: Boolean): ConnectionConfig {
        this.autoReconnect = enabled
        return this
    }
    
    /**
     * 设置重试策略
     * 
     * @param maxRetries 最大重试次数
     * @param delayMs 重试延迟时间（毫秒）
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setRetryPolicy(maxRetries: Int, delayMs: Long): ConnectionConfig {
        this.maxRetryCount = maxRetries
        this.retryDelayMs = delayMs
        return this
    }
    
    /**
     * 设置最大重试次数
     * 
     * @param maxRetries 最大重试次数
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setMaxRetryCount(maxRetries: Int): ConnectionConfig {
        this.maxRetryCount = maxRetries
        return this
    }
    
    /**
     * 设置重试延迟时间
     * 
     * @param delayMs 重试延迟时间（毫秒）
     * @return ConnectionConfig 当前配置对象，支持链式调用
     */
    fun setRetryDelayMs(delayMs: Long): ConnectionConfig {
        this.retryDelayMs = delayMs
        return this
    }
    
    /**
     * Check if this config is equivalent to another config
     * 
     * Two configs are considered equivalent if they have the same connection parameters
     * 
     * @param other The other config to compare
     * @return Boolean true if configs are equivalent, false otherwise
     */
    fun isEquivalentTo(other: ConnectionConfig?): Boolean {
        if (other == null) return false
        
        return this.connectionMode == other.connectionMode &&
               this.cableProtocol == other.cableProtocol &&
               this.host == other.host &&
               this.port == other.port &&
               this.connectionTimeout == other.connectionTimeout &&
               this.autoReconnect == other.autoReconnect &&
               this.maxRetryCount == other.maxRetryCount &&
               this.retryDelayMs == other.retryDelayMs
    }
    
    /**
     * 获取配置摘要信息
     * 
     * @return String 配置的字符串表示
     */
    override fun toString(): String {
        return "ConnectionConfig(" +
                "connectionMode=$connectionMode, " +
                "cableProtocol=$cableProtocol, " +
                "host=$host, " +
                "port=$port, " +
                "connectionTimeout=${connectionTimeout}s, " +
                "autoReconnect=$autoReconnect, " +
                "maxRetryCount=$maxRetryCount, " +
                "retryDelayMs=${retryDelayMs}ms" +
                ")"
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
        
        /**
         * 创建应用间模式配置
         * 
         * @return ConnectionConfig 应用间模式配置
         */
        fun createAppMode(): ConnectionConfig {
            return ConnectionConfig().setConnectionMode(ConnectionMode.APP_TO_APP)
        }
        
        /**
         * 创建线缆模式配置
         * 
         * @param protocol 线缆协议类型（可选）
         * @return ConnectionConfig 线缆模式配置
         */
        fun createCableMode(protocol: CableProtocol? = null): ConnectionConfig {
            val config = ConnectionConfig().setConnectionMode(ConnectionMode.CABLE)
            return if (protocol != null) {
                config.setCableProtocol(protocol)
            } else {
                config
            }
        }
        
        /**
         * 创建局域网模式配置
         * 
         * @param host 主机地址（可选）
         * @param port 端口号（可选）
         * @return ConnectionConfig 局域网模式配置
         */
        fun createLanMode(host: String? = null, port: Int? = null): ConnectionConfig {
            val config = ConnectionConfig().setConnectionMode(ConnectionMode.LAN)
            if (host != null) {
                config.setHost(host)
            }
            if (port != null) {
                config.setPort(port)
            }
            return config
        }
        
        /**
         * 创建高可靠性配置
         * 启用自动重连，增加重试次数和延迟
         * 
         * @return ConnectionConfig 高可靠性配置
         */
        fun createHighReliabilityConfig(): ConnectionConfig {
            return ConnectionConfig()
                .setAutoReconnect(true)
                .setRetryPolicy(maxRetries = 5, delayMs = 3000L)
                .setConnectionTimeout(90)
        }
        
        /**
         * 创建快速连接配置
         * 减少超时时间，禁用自动重连
         * 
         * @return ConnectionConfig 快速连接配置
         */
        fun createFastConnectionConfig(): ConnectionConfig {
            return ConnectionConfig()
                .setAutoReconnect(false)
                .setConnectionTimeout(30)
        }
    }
}













