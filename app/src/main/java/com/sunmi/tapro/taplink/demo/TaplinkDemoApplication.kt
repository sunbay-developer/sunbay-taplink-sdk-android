package com.sunmi.tapro.taplink.demo

import android.app.Application
import android.util.Log
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.LogLevel

/**
 * Taplink Demo Application Class
 * Responsible for global application initialization, including Taplink SDK initialization
 */
class TaplinkDemoApplication : Application() {

    companion object {
        private const val TAG = "TaplinkDemoApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Taplink SDK
        initializeTaplinkSDK()
    }

    /**
     * Initialize Taplink SDK
     * Initialize SDK based on user's saved connection mode preference
     * This ensures the SDK starts with the correct mode after app restart
     */
    private fun initializeTaplinkSDK() {
        try {
            Log.d(TAG, "=== Taplink SDK Initialization Started ===")

            // Read configuration from resource files
            val appId = getString(R.string.taplink_app_id)
            val merchantId = getString(R.string.taplink_merchant_id)
            val secretKey = getString(R.string.taplink_secret_key)

            // Get user's saved connection mode preference
            val savedMode = com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.getConnectionMode(this)
            
            // Map to SDK ConnectionMode
            val sdkConnectionMode = when (savedMode) {
                com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.APP_TO_APP -> {
                    Log.d(TAG, "Using saved mode: App-to-App")
                    ConnectionMode.APP_TO_APP
                }
                com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.CABLE -> {
                    Log.d(TAG, "Using saved mode: Cable")
                    ConnectionMode.USB_AOA
                }
                com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.LAN -> {
                    Log.d(TAG, "Using saved mode: LAN")
                    ConnectionMode.LAN
                }
                else -> {
                    Log.d(TAG, "No saved mode or unsupported mode, defaulting to App-to-App")
                    ConnectionMode.APP_TO_APP
                }
            }

            // Log configuration parameters (mask sensitive data)
            Log.d(TAG, "=== SDK Init Request Parameters ===")
            Log.d(TAG, "App ID: $appId")
            Log.d(TAG, "Merchant ID: $merchantId")
            Log.d(TAG, "Secret Key: ${secretKey.take(4)}****${secretKey.takeLast(4)}")
            Log.d(TAG, "Connection Mode: $sdkConnectionMode")
            Log.d(TAG, "Log Level: DEBUG")
            Log.d(TAG, "Log Enabled: true")

            // Create SDK configuration
            val config = TaplinkConfig(
                appId = appId,
                merchantId = merchantId,
                secretKey = secretKey
            ).setLogEnabled(true).setLogLevel(LogLevel.DEBUG).setConnectionMode(sdkConnectionMode)

            Log.d(TAG, "=== SDK Config Object Created ===")
            Log.d(TAG, "Config: $config")

            // Initialize SDK
            Log.d(TAG, "=== Calling TaplinkSDK.init() ===")
            TaplinkSDK.init(this, config)

            Log.d(TAG, "=== Taplink SDK Initialization Response ===")
            Log.d(TAG, "Status: SUCCESS")
            Log.d(TAG, "Mode: $sdkConnectionMode")
            Log.d(TAG, "SDK Ready for connection")

        } catch (e: Exception) {
            Log.e(TAG, "=== Taplink SDK Initialization Response ===")
            Log.e(TAG, "Status: FAILURE")
            Log.e(TAG, "Error Type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error Message: ${e.message}")
            Log.e(TAG, "Full Exception: ", e)
        }
    }
}
