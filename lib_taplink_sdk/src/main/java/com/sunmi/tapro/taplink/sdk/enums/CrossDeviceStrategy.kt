package com.sunmi.tapro.taplink.sdk.enums

/**
 * Cross-Device connection strategy.
 *
 * A configuration detail for [ConnectionMode.CROSS_DEVICE] — analogous to how [CableProtocol]
 * configures [ConnectionMode.CABLE]. It selects HOW the Cross-Device integration mode connects,
 * without introducing separate top-level [ConnectionMode] values.
 *
 * Set via [com.sunmi.tapro.taplink.sdk.config.ConnectionConfig.setCrossDeviceStrategy].
 *
 * Note: Cloud is a supported Cross-Device transport at the TaPro display/status level, but it is
 * NOT a value of this enum. Cloud transactions are dispatched to TaPro directly by the cloud
 * backend; the SDK never establishes, authenticates, or drives a Cloud connection, so there is no
 * SDK-selectable [CrossDeviceStrategy.CLOUD] strategy.
 */
enum class CrossDeviceStrategy {
    /**
     * Automatically select an available connection.
     *
     * When both LAN and CABLE conditions exist, the SDK picks one according to the
     * configured priority (see
     * [com.sunmi.tapro.taplink.sdk.config.ConnectionConfig.setAutoPriority]); if the
     * active transport becomes unavailable / times out, it falls back to the other.
     */
    AUTO,

    /** Connect the Cross-Device peer over LAN (requires host + port). */
    LAN,

    /** Connect the Cross-Device peer over a cable (auto-detects VSP / RS232 / AOA by default). */
    CABLE
}
