package com.sunmi.tapro.taplink.sdk.api

import android.content.Context
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.error.TaplinkException
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * Taplink SDK API Interface
 *
 * Complete API interface defined according to Taplink SDK for Android Integration Guide documentation
 * Reference document: Taplink SDK for Android - Integration Guide.md
 *
 * Key features:
 * - Unified use of PaymentRequest to handle all transaction types (distinguished by action field)
 * - Supports both synchronous and asynchronous calling methods
 * - Uses ConnectionConfig to uniformly manage all connection modes
 * - Method signatures correspond to TaplinkSDK static methods in documentation
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface TaplinkApi {

    // ==================== Initialization ====================

    /**
     * Initialize SDK
     *
     * Initialize Taplink SDK, configure application information and connection parameters. Must be called before using other interfaces.
     *
     * @param context Application or Activity context
     * @param config SDK configuration object
     */
    fun init(context: Context, config: TaplinkConfig)

    // ==================== Connection Management ====================

    /**
     * Establish connection (unified connection method)
     *
     * Supports all connection modes:
     * - App-to-app mode: Use empty configuration, SDK auto-detects
     * - Cable mode: Use empty configuration for auto-detection, or specify protocol type
     * - LAN mode: Specify IP and port (first time), or use empty configuration (subsequent auto-connection)
     *
     * @param config Connection configuration object (can be null, uses default configuration)
     * @param listener Connection listener
     */
    fun connect(config: ConnectionConfig?, listener: ConnectionListener)

    /**
     * Disconnect
     *
     * Disconnect from payment terminal and release resources.
     */
    fun disconnect()

    /**
     * Check connection status
     *
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean

    /**
     * Get connected device ID
     *
     * @return String device ID, returns null if not connected
     */
    fun getConnectedDeviceId(): String?

    /**
     * Get connection mode
     *
     * @return ConnectionMode current connection mode, returns null if not connected
     */
    fun getConnectionMode(): ConnectionMode?

    /**
     * Get Tapro version number
     *
     * Get the Tapro version number of the connected payment terminal.
     * Version number can only be obtained after successful connection.
     *
     * @return String Tapro version number, returns null if not connected or version not obtained
     */
    fun getTaproVersion(): String?

    // ==================== Transaction Execution ====================

    /**
     * Execute transaction asynchronously (recommended)
     *
     * Non-blocking call, returns immediately. Transaction results are passed through callback methods.
     * Recommended for cross-device scenarios and scenarios requiring progress feedback.
     *
     * @param request Payment request object
     * @param callback Payment callback interface
     */
    fun execute(request: PaymentRequest, callback: PaymentCallback)

    // ==================== Transaction Query ====================

    /**
     * Query transaction status asynchronously
     *
     * Query transaction status, particularly suitable for timeout scenarios.
     * Can query by transaction ID or transaction request ID.
     *
     * @param request Query request object
     * @param callback Payment callback interface
     */
    fun query(request: QueryRequest, callback: PaymentCallback)

    // ==================== Listener Management ====================

    /**
     * Set connection status listener
     *
     * Set connection status listener to monitor connection status changes, disconnection reconnection and other events in real time.
     *
     * @param listener Connection status listener, null means remove listener
     */
    fun setConnectionListener(listener: ConnectionListener?)

    /**
     * Remove connection status listener
     *
     * Remove previously set connection status listener.
     */
    fun removeConnectionListener()

    // ==================== Utility Methods ====================

    /**
     * Get SDK version
     *
     * @return String SDK version number, format: x.y.z
     */
    fun getVersion(): String

    /**
     * Clear cached device information
     *
     * Clear locally cached device connection information, requiring re-pairing on next connection.
     */
    fun clearDeviceCache()
}

