package com.sunmi.tapro.taplink.communication.lan.model

/**
 * 服务信息数据类
 *
 * 用于表示通过mDNS发现的服务信息
 *
 * @param name 服务名称
 * @param type 服务类型
 * @param host 服务主机地址
 * @param port 服务端口
 * @param attributes 服务属性
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class ServiceInfo(
    var name: String,
    val type: String,
    val host: String,
    val port: Int,
    val attributes: Map<String, String> = emptyMap()
) {
    /**
     * 获取服务地址
     *
     * @return host:port 格式的地址
     */
    fun getAddress(): String = "$host:$port"

    /**
     * 检查服务是否有效
     *
     * @return true if valid, false otherwise
     */
    fun isValid(): Boolean = host.isNotEmpty() && port > 0

    /**
     * 获取服务的WebSocket URI
     *
     * @param secure 是否使用安全连接（wss）
     * @return WebSocket URI字符串
     */
    fun getWebSocketUri(secure: Boolean = false): String {
        val protocol = if (secure) "wss" else "ws"
        return "$protocol://$host:$port"
    }

    /**
     * 检查是否包含指定属性
     *
     * @param key 属性键
     * @return true if contains the attribute, false otherwise
     */
    fun hasAttribute(key: String): Boolean = attributes.containsKey(key)

    /**
     * 获取属性值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值或默认值
     */
    fun getAttribute(key: String, defaultValue: String = ""): String {
        return attributes[key] ?: defaultValue
    }

    override fun toString(): String {
        return "ServiceInfo(name='$name', address='${getAddress()}', attributes=${attributes.size})"
    }
}