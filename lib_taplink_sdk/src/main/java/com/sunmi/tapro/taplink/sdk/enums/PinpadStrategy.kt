package com.sunmi.tapro.taplink.sdk.enums

/**
 * PinPad connection strategy.
 *
 * A configuration detail for [ConnectionMode.PINPAD] — analogous to how [CableProtocol]
 * configures [ConnectionMode.CABLE]. It selects HOW the PinPad integration mode connects,
 * without introducing separate top-level [ConnectionMode] values.
 *
 * Set via [com.sunmi.tapro.taplink.sdk.config.ConnectionConfig.setPinpadStrategy].
 */
enum class PinpadStrategy {
    /**
     * Automatically select an available connection.
     *
     * When both LAN and CABLE conditions exist, the SDK picks one according to the
     * configured priority (see
     * [com.sunmi.tapro.taplink.sdk.config.ConnectionConfig.setAutoPriority]); if the
     * active transport becomes unavailable / times out, it falls back to the other.
     */
    AUTO,

    /** Connect the PinPad over LAN (requires host + port). */
    LAN,

    /** Connect the PinPad over a cable (auto-detects VSP / RS232 / AOA by default). */
    CABLE
}
