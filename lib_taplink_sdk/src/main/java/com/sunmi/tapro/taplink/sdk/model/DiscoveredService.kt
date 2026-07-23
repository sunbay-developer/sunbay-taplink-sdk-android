package com.sunmi.tapro.taplink.sdk.model

/**
 * A LAN Taplink service discovered via mDNS (Android NSD).
 *
 * Returned by [com.sunmi.tapro.taplink.sdk.api.TaplinkApi.discoverLanServices] so the
 * caller can present or auto-fill the address and then establish a LAN connection.
 *
 * @property name mDNS service instance name
 * @property host Resolved host / IP address (e.g. "192.168.1.100")
 * @property port Service port (e.g. 8443)
 */
data class DiscoveredService(
    val name: String,
    val host: String,
    val port: Int
)
