package com.sunmi.tapro.taplink.sdk

import android.content.Context
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.impl.TaplinkApiImpl
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import kotlin.jvm.JvmStatic

/**
 * Taplink SDK main class
 *
 * Provides SDK basic functionality: initialization, connection management, version information, etc.
 * Main responsibilities:
 * - SDK initialization and configuration
 * - Connection management (connect, disconnect, status query)
 * - Version information management
 * - Device information management
 * - Provide TaplinkClient instance for transaction operations
 *
 * Recommended usage:
 * ```kotlin
 * // 1. Initialize SDK
 * val config = TaplinkConfig.create(appId, merchantId, secretKey)
 * TaplinkSDK.init(context, config)
 * 
 * // 2. Establish connection
 * TaplinkSDK.connect(connectionConfig) { connected ->
 *     if (connected) {
 *         // 3. Get client for transactions
 *         val client = TaplinkSDK.getClient()
 *         client.quickSale(
 *             totalAmount = BigDecimal("10.00"),
 *             orderId = "ORDER001",
 *             callback = paymentCallback
 *         )
 *     }
 * }
 * 
 * // 4. Disconnect
 * TaplinkSDK.disconnect()
 * ```
 * 
 * Responsibility division:
 * - **TaplinkSDK**: Initialization, connection, disconnect, status management, version information
 * - **TaplinkClient**: Focus on transaction operations (sale, refund, void, etc.)
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkSDK private constructor() : TaplinkApi {

    /**
     * Internal proxy class, implements specific interface functionality
     */
    private val apiImpl: TaplinkApiImpl = TaplinkApiImpl()
    
    /**
     * TaplinkClient instance (lazy loaded)
     */
    private val clientInstance: TaplinkClient by lazy { TaplinkClient(apiImpl) }

    companion object {
        @Volatile
        private var INSTANCE: TaplinkSDK? = null

        /**
         * Get TaplinkSDK singleton instance
         *
         * @return TaplinkSDK Singleton instance
         */
        @JvmStatic
        fun getInstance(): TaplinkSDK {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaplinkSDK().also { INSTANCE = it }
            }
        }

        // ==================== Initialization Functions ====================

        /**
         * Initialize SDK
         *
         * Initialize Taplink SDK, configure application information and connection parameters. Must be called before using other interfaces.
         *
         * @param context Application or Activity context
         * @param config SDK configuration object
         */
        @JvmStatic
        @JvmName("initSDK")
        fun init(context: Context, config: TaplinkConfig) {
            getInstance().init(context, config)
        }

        // ==================== Connection Management Functions ====================

        /**
         * Establish connection
         *
         * Supports all connection modes:
         * - App-to-app mode: Use empty config, SDK auto-detects
         * - Cable mode: Use empty config for auto-detection, or specify protocol type
         * - LAN mode: Specify IP and port (first time), or use empty config (subsequent auto-connect)
         *
         * @param config Connection configuration object (can be null, uses default config)
         * @param listener Connection listener
         */
        @JvmStatic
        @JvmName("connectSDK")
        fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
            getInstance().connect(config, listener)
        }

        /**
         * Disconnect
         *
         * Disconnect from payment terminal, release resources.
         */
        @JvmStatic
        @JvmName("disconnectSDK")
        fun disconnect() {
            getInstance().disconnect()
        }

        /**
         * Check connection status
         *
         * @return true if connected, false otherwise
         */
        @JvmStatic
        @JvmName("isSDKConnected")
        fun isConnected(): Boolean {
            return getInstance().isConnected()
        }

        /**
         * Set connection status listener
         *
         * Set connection status listener to monitor connection status changes, reconnection events, etc. in real-time.
         *
         * @param listener Connection status listener, null means remove listener
         */
        @JvmStatic
        @JvmName("setSDKConnectionListener")
        fun setConnectionListener(listener: ConnectionListener?) {
            getInstance().setConnectionListener(listener)
        }

        /**
         * Remove connection status listener
         *
         * Remove previously set connection status listener.
         */
        @JvmStatic
        @JvmName("removeSDKConnectionListener")
        fun removeConnectionListener() {
            getInstance().removeConnectionListener()
        }

        // ==================== Device and Version Information ====================

        /**
         * Get connected device ID
         *
         * @return String Device ID, returns null if not connected
         */
        @JvmStatic
        @JvmName("getSDKConnectedDeviceId")
        fun getConnectedDeviceId(): String? {
            return getInstance().getConnectedDeviceId()
        }

        /**
         * Get connection mode
         *
         * @return ConnectionMode Current connection mode, returns null if not connected
         */
        @JvmStatic
        @JvmName("getSDKConnectionMode")
        fun getConnectionMode(): String? {
            return getInstance().getConnectionMode()
        }

        /**
         * Get Tapro version number
         *
         * Get Tapro version number of connected payment terminal.
         * Version number can only be obtained after successful connection.
         *
         * @return String Tapro version number, returns null if not connected or version not obtained
         */
        @JvmStatic
        @JvmName("getSDKTaproVersion")
        fun getTaproVersion(): String? {
            return getInstance().getTaproVersion()
        }

        /**
         * Get SDK version
         *
         * @return String SDK version number, format: x.y.z
         */
        @JvmStatic
        @JvmName("getSDKVersion")
        fun getVersion(): String {
            return getInstance().getVersion()
        }

        /**
         * Clear cached device information
         *
         * Clear locally cached device connection information, need to re-pair on next connection.
         */
        @JvmStatic
        @JvmName("clearSDKDeviceCache")
        fun clearDeviceCache() {
            getInstance().clearDeviceCache()
        }

        // ==================== Get Transaction Client ====================

        /**
         * Get TaplinkClient instance
         * 
         * Used to execute transaction operations, need to establish connection first
         *
         * @return TaplinkClient Transaction client instance
         */
        @JvmStatic
        fun getClient(): TaplinkClient {
            return getInstance().clientInstance
        }

        // ==================== Backward Compatible Interfaces (Deprecated) ====================

        /**
         * Execute transaction asynchronously (recommended)
         *
         * @deprecated Recommend using specific transaction methods of TaplinkSDK.getClient() instead
         */
        @Deprecated(
            message = "Recommend using specific transaction methods of TaplinkSDK.getClient() instead",
            replaceWith = ReplaceWith("TaplinkSDK.getClient().sale() or other specific transaction methods")
        )
        @JvmStatic
        @JvmName("executeSDK")
        fun execute(request: PaymentRequest, callback: PaymentCallback) {
            getInstance().execute(request, callback)
        }

        /**
         * Query transaction status asynchronously
         *
         * @deprecated Recommend using TaplinkSDK.getClient().query() instead
         */
        @Deprecated(
            message = "Recommend using TaplinkSDK.getClient().query() instead",
            replaceWith = ReplaceWith("TaplinkSDK.getClient().query(request, callback)")
        )
        @JvmStatic
        @JvmName("querySDK")
        fun query(request: QueryRequest, callback: PaymentCallback) {
            getInstance().query(request, callback)
        }
    }

    // ==================== Initialization Functions (Instance Methods) ====================

    override fun init(context: Context, config: TaplinkConfig) {
        apiImpl.init(context, config)
    }

    // ==================== Connection Management Functions (Instance Methods) ====================

    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        apiImpl.connect(config, listener)
    }

    override fun disconnect() {
        apiImpl.disconnect()
    }

    override fun isConnected(): Boolean {
        return apiImpl.isConnected()
    }

    override fun setConnectionListener(listener: ConnectionListener?) {
        apiImpl.setConnectionListener(listener)
    }

    override fun removeConnectionListener() {
        apiImpl.removeConnectionListener()
    }

    // ==================== Device and Version Information (Instance Methods) ====================

    override fun getConnectedDeviceId(): String? {
        return apiImpl.getConnectedDeviceId()
    }

    override fun getConnectionMode(): String? {
        return apiImpl.getConnectionMode()
    }

    override fun getTaproVersion(): String? {
        return apiImpl.getTaproVersion()
    }

    override fun getVersion(): String {
        return apiImpl.getVersion()
    }

    override fun clearDeviceCache() {
        apiImpl.clearDeviceCache()
    }

    override fun getConnectionConfig(): ConnectionConfig? {
        return apiImpl.getConnectionConfig()
    }

    // ==================== Backward Compatible Interfaces (Instance Methods, Deprecated) ====================

    override fun execute(request: PaymentRequest, callback: PaymentCallback) {
        apiImpl.execute(request, callback)
    }

    override fun query(request: QueryRequest, callback: PaymentCallback) {
        apiImpl.query(request, callback)
    }
}
