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
    
    /** Same device Intent */
    APP_TO_APP,

    /**
     * Cable mode
     */
    CABLE,
    
    /** WebSocket (LAN/WLAN) */
    LAN,

    /**
     * Secondary Display mode.
     *
     * Connects to a remote TaPro terminal via USB Virtual Serial Port (VSP) and
     * automatically opens the USB customer-facing screen player on the remote device.
     *
     * The connection is considered successful only when both the VSP transport is
     * established AND the secondary display player is launched on TaPro. If the screen
     * player fails to open, the connection is treated as failed (onError is delivered).
     *
     * Prerequisites:
     * - The remote device must have TaPro running with VSP service mode enabled.
     * - The two devices must be connected via a USB cable.
     *
     * After a successful connection, the full TapLink transaction API is available
     * (sale, refund, void, etc.) in addition to secondary display control.
     */
    SECONDARY_DISPLAY
}



