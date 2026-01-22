package com.sunmi.tapro.taplink.communication.lan.model

/**
 * Service information data class
 *
 * Used to represent service information discovered via mDNS
 *
 * @param name Service name
 * @param type Service type
 * @param host Service host address
 * @param port Service port
 * @param attributes Service attributes
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
     * Get service address
     *
     * @return Address in host:port format
     */
    fun getAddress(): String = "$host:$port"

    /**
     * Check if service is valid
     *
     * @return true if valid, false otherwise
     */
    fun isValid(): Boolean = host.isNotEmpty() && port > 0

    /**
     * Get service WebSocket URI
     *
     * @param secure Whether to use secure connection (wss)
     * @return WebSocket URI string
     */
    fun getWebSocketUri(secure: Boolean = false): String {
        val protocol = if (secure) "wss" else "ws"
        return "$protocol://$host:$port"
    }

    /**
     * Check if contains specified attribute
     *
     * @param key Attribute key
     * @return true if contains the attribute, false otherwise
     */
    fun hasAttribute(key: String): Boolean = attributes.containsKey(key)

    /**
     * Get attribute value
     *
     * @param key Attribute key
     * @param defaultValue Default value
     * @return Attribute value or default value
     */
    fun getAttribute(key: String, defaultValue: String = ""): String {
        return attributes[key] ?: defaultValue
    }

    override fun toString(): String {
        return "ServiceInfo(name='$name', address='${getAddress()}', attributes=${attributes.size})"
    }
}