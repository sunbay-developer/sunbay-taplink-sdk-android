package com.sunmi.tapro.taplink.sdk

import android.content.Context
import com.sunmi.tapro.taplink.sdk.api.TaplinkApi
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.error.TaplinkException
import com.sunmi.tapro.taplink.sdk.impl.TaplinkApiImpl
import com.sunmi.tapro.taplink.sdk.model.request.PaymentRequest
import com.sunmi.tapro.taplink.sdk.model.request.QueryRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import kotlin.jvm.JvmStatic

/**
 * Taplink SDK Main Class
 *
 * Provides unified SDK entry point, implementing TaplinkApi interface
 * Internal implementation through TaplinkApiImpl proxy class
 *
 * Usage:
 * - Static method calls: TaplinkSDK.init(context, config)
 * - Singleton pattern: TaplinkSDK.getInstance().init(context, config)
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkSDK private constructor() : TaplinkApi {

    /**
     * Internal proxy class that implements specific interface functionality
     */
    private val apiImpl: TaplinkApiImpl = TaplinkApiImpl()

    companion object {
        @Volatile
        private var INSTANCE: TaplinkSDK? = null

        /**
         * Get TaplinkSDK singleton instance
         *
         * @return TaplinkSDK singleton instance
         */
        @JvmStatic
        fun getInstance(): TaplinkSDK {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TaplinkSDK().also { INSTANCE = it }
            }
        }

        // ==================== Initialization (Static Methods) ====================

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

        // ==================== Connection Management (Static Methods) ====================

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
        @JvmStatic
        @JvmName("connectSDK")
        fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
            getInstance().connect(config, listener)
        }

        /**
         * Disconnect
         *
         * Disconnect from payment terminal and release resources.
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
         * Get connected device ID
         *
         * @return String device ID, returns null if not connected
         */
        @JvmStatic
        @JvmName("getSDKConnectedDeviceId")
        fun getConnectedDeviceId(): String? {
            return getInstance().getConnectedDeviceId()
        }

        /**
         * Get connection mode
         *
         * @return ConnectionMode current connection mode, returns null if not connected
         */
        @JvmStatic
        @JvmName("getSDKConnectionMode")
        fun getConnectionMode(): ConnectionMode? {
            return getInstance().getConnectionMode()
        }

        /**
         * Get Tapro version number
         *
         * Get the Tapro version number of the connected payment terminal.
         * Version number can only be obtained after successful connection.
         *
         * @return String Tapro version number, returns null if not connected or version not obtained
         */
        @JvmStatic
        @JvmName("getSDKTaproVersion")
        fun getTaproVersion(): String? {
            return getInstance().getTaproVersion()
        }

        // ==================== Transaction Execution (Static Methods) ====================

        /**
         * Execute transaction asynchronously (recommended)
         *
         * Non-blocking call, returns immediately. Transaction results are passed through callback methods.
         * Recommended for cross-device scenarios and scenarios requiring progress feedback.
         *
         * @param request Payment request object
         * @param callback Payment callback interface
         */
        @JvmStatic
        @JvmName("executeSDK")
        fun execute(request: PaymentRequest, callback: PaymentCallback) {
            getInstance().execute(request, callback)
        }

        // ==================== Transaction Query (Static Methods) ====================

        /**
         * Query transaction status asynchronously
         *
         * Query transaction status, particularly suitable for timeout scenarios.
         * Can query by transaction ID or transaction request ID.
         *
         * @param request Query request object
         * @param callback Payment callback interface
         */
        @JvmStatic
        @JvmName("querySDK")
        fun query(request: QueryRequest, callback: PaymentCallback) {
            getInstance().query(request, callback)
        }

        // ==================== Listener Management (Static Methods) ====================

        /**
         * Set connection status listener
         *
         * Set connection status listener to monitor connection status changes, disconnection reconnection and other events in real time.
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

        // ==================== Utility Methods (Static Methods) ====================

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
         * Clear locally cached device connection information, requiring re-pairing on next connection.
         */
        @JvmStatic
        @JvmName("clearSDKDeviceCache")
        fun clearDeviceCache() {
            getInstance().clearDeviceCache()
        }
    }

    // ==================== Initialization (Instance Methods) ====================

    override fun init(context: Context, config: TaplinkConfig) {
        apiImpl.init(context, config)
    }

    // ==================== Connection Management (Instance Methods) ====================

    override fun connect(config: ConnectionConfig?, listener: ConnectionListener) {
        apiImpl.connect(config, listener)
    }

    override fun disconnect() {
        apiImpl.disconnect()
    }

    override fun isConnected(): Boolean {
        return apiImpl.isConnected()
    }

    override fun getConnectedDeviceId(): String? {
        return apiImpl.getConnectedDeviceId()
    }

    override fun getConnectionMode(): ConnectionMode? {
        return apiImpl.getConnectionMode()
    }

    override fun getTaproVersion(): String? {
        return apiImpl.getTaproVersion()
    }

    // ==================== Transaction Execution (Instance Methods) ====================

    override fun execute(request: PaymentRequest, callback: PaymentCallback) {
        apiImpl.execute(request, callback)
    }

    // ==================== Transaction Query (Instance Methods) ====================

    override fun query(request: QueryRequest, callback: PaymentCallback) {
        apiImpl.query(request, callback)
    }

    // ==================== Listener Management (Instance Methods) ====================

    override fun setConnectionListener(listener: ConnectionListener?) {
        apiImpl.setConnectionListener(listener)
    }

    override fun removeConnectionListener() {
        apiImpl.removeConnectionListener()
    }

    // ==================== Utility Methods (Instance Methods) ====================

    override fun getVersion(): String {
        return apiImpl.getVersion()
    }

    override fun clearDeviceCache() {
        apiImpl.clearDeviceCache()
    }
}
