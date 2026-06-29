package com.sunmi.tapro.taplink.sdk.enums

/**
 * App-to-App transaction processing mode.
 *
 * CUSTOM keeps the full foreground UI flow on Tapro.
 * HEADLESS runs the transaction with minimal UI and progress callbacks.
 */
enum class AppToAppMode {
    CUSTOM,
    HEADLESS
}

