package com.sunmi.tapro.taplink.sdk.enums

/**
 * Connection mode enumeration
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class ConnectionMode {
    /** Not enabled */
    NONE,

    /**
     * On-device integration mode.
     *
     * The business app and TaPro run on the SAME terminal; the business app invokes
     * TaPro locally to complete the payment. This is the business-facing integration
     * mode; its current underlying implementation is App-to-App.
     *
     * Prefer this over the deprecated [APP_TO_APP] value: [ON_DEVICE] is normalized
     * onto the same App-to-App behavior internally, but future local mechanisms
     * (e.g. AIDL/Binder) can be added without changing the public mode.
     */
    ON_DEVICE,

    /**
     * Cross-Device integration mode.
     *
     * TaPro acts as a payment terminal driven by an external business system running on
     * another device, over a connection channel. The concrete connection strategy
     * (AUTO / LAN / CABLE) is NOT expressed as separate enum values; it is configured as a
     * detail via
     * [com.sunmi.tapro.taplink.sdk.config.ConnectionConfig.setCrossDeviceStrategy], exactly
     * like [CABLE] configures its protocol via
     * [com.sunmi.tapro.taplink.sdk.config.ConnectionConfig.setCableProtocol].
     *
     * CROSS_DEVICE is normalized internally onto classic LAN/CABLE behavior (or, for the AUTO
     * strategy, a cross-transport auto-selection with fallback), so existing resolver /
     * manager logic is reused unchanged.
     */
    CROSS_DEVICE,

    /** Same device Intent */
    @Deprecated(
        message = "Use ON_DEVICE instead. APP_TO_APP is retained for backward compatibility " +
            "and is normalized onto the same on-device behavior.",
        replaceWith = ReplaceWith("ON_DEVICE")
    )
    APP_TO_APP,

    /**
     * Cable mode
     */
    @Deprecated(
        message = "Use CROSS_DEVICE with ConnectionConfig.setCrossDeviceStrategy(CrossDeviceStrategy.CABLE) instead. " +
            "CABLE is retained for backward compatibility and remains fully functional."
    )
    CABLE,

    /** WebSocket (LAN/WLAN) */
    @Deprecated(
        message = "Use CROSS_DEVICE with ConnectionConfig.setCrossDeviceStrategy(CrossDeviceStrategy.LAN) instead. " +
            "LAN is retained for backward compatibility and remains fully functional."
    )
    LAN,

    /**
     * Sub_Screen mode.
     *
     * Connects to a remote TaPro terminal via USB Virtual Serial Port (VSP) and
     * automatically opens the USB customer-facing screen player on the remote device.
     *
     * The connection is considered successful only when both the VSP transport is
     * established AND the sub-screen player is launched on TaPro. If the screen
     * player fails to open, the connection is treated as failed (onError is delivered).
     *
     * Prerequisites:
     * - The remote device must have TaPro running with VSP service mode enabled.
     * - The two devices must be connected via a USB cable.
     *
     * After a successful connection, the full TapLink transaction API is available
     * (sale, refund, void, etc.) in addition to sub-screen control.
     */
    SUB_SCREEN
}



