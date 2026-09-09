package com.sunmi.tapro.taplink.sdk.enums

/**
 * Cross-Device connection strategy.
 *
 * A configuration detail for [ConnectionMode.CROSS_DEVICE] — analogous to how [CableProtocol]
 * configures [ConnectionMode.CABLE]. It selects HOW the Cross-Device integration mode connects,
 * without introducing separate top-level [ConnectionMode] values. The strategy must always be
 * specified explicitly (LAN or CABLE); there is no automatic transport selection.
 *
 * Set via [com.sunmi.tapro.taplink.sdk.config.ConnectionConfig.setCrossDeviceStrategy].
 *
 * Note: Cloud is a supported Cross-Device transport at the TaPro display/status level, but it is
 * NOT a value of this enum. Cloud transactions are dispatched to TaPro directly by the cloud
 * backend; the SDK never establishes, authenticates, or drives a Cloud connection, so there is no
 * SDK-selectable [CrossDeviceStrategy.CLOUD] strategy.
 */
enum class CrossDeviceStrategy {
    /** Connect the Cross-Device peer over LAN (requires host + port). */
    LAN,

    /**
     * Connect the Cross-Device peer over a cable.
     *
     * The specific cable protocol (VSP / RS232 / AOA) must be specified explicitly via
     * [com.sunmi.tapro.taplink.sdk.config.ConnectionConfig.setCableProtocol]; there is no
     * automatic cable-protocol detection.
     */
    CABLE
}
