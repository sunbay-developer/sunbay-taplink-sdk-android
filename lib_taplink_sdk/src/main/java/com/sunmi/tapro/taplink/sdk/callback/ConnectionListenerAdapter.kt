package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.ConnectionError

/**
 * Abstract adapter for [ConnectionListener].
 *
 * Provides empty default implementations for all callback methods so that
 * integrators (especially Java callers) only need to override the methods
 * they care about.
 *
 * Kotlin usage:
 * ```kotlin
 * TaplinkSDK.connect(config, object : ConnectionListenerAdapter() {
 *     override fun onConnected(deviceId: String, taproVersion: String) {
 *         // start transactions
 *     }
 *     override fun onError(error: ConnectionError) {
 *         // show error dialog
 *     }
 * })
 * ```
 *
 * Java usage:
 * ```java
 * TaplinkSDK.connect(config, new ConnectionListenerAdapter() {
 *     @Override
 *     public void onConnected(String deviceId, String taproVersion) {
 *         // start transactions
 *     }
 * });
 * ```
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class ConnectionListenerAdapter : ConnectionListener {

    override fun onConnected(deviceId: String, taproVersion: String) {}

    override fun onDisconnected(reason: String) {}

    override fun onError(error: ConnectionError) {}

    override fun onReconnecting(attempt: Int, maxRetries: Int) {}
}
