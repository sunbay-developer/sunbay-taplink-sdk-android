package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.model.DiscoveredService

/**
 * LAN service discovery result listener.
 *
 * Used by [com.sunmi.tapro.taplink.sdk.api.TaplinkApi.discoverLanServices] to return
 * the discovered LAN Taplink services without establishing a connection. The caller
 * decides which service to connect to.
 *
 * @author TaPro Team
 */
interface DiscoveryListener {

    /**
     * Discovery finished.
     *
     * @param services All resolved LAN Taplink services (may be empty if none found).
     */
    fun onDiscovered(services: List<DiscoveredService>)

    /**
     * Discovery failed (e.g. SDK not initialized or an internal error occurred).
     *
     * @param error Error information
     */
    fun onError(error: ConnectionError)
}
