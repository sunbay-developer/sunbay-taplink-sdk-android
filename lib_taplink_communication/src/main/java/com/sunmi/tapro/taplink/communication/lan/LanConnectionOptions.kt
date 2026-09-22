package com.sunmi.tapro.taplink.communication.lan

/**
 * Internal options for an SDK-managed LAN connection.
 *
 * This type is intentionally kept in the communication implementation package so it is not part
 * of the public SDK configuration protocol.
 */
data class LanConnectionOptions(
    val allowServiceDiscoveryFallback: Boolean = true,
    val enableServiceMonitoring: Boolean = true
)
