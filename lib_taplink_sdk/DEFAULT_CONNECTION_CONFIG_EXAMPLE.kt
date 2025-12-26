package com.sunmi.tapro.taplink.sdk.example

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.error.ConnectionError

/**
 * Default Connection Configuration Example
 * 
 * Demonstrates how the SDK automatically creates appropriate default
 * connection configurations based on the TaplinkConfig connection mode
 * when no explicit ConnectionConfig is provided.
 * 
 * @author TaPro Team
 * @since 2025-12-22
 */
class DefaultConnectionConfigExample(private val context: Context) {

    private val TAG = "DefaultConfigExample"

    /**
     * Example 1: APP_TO_APP mode with no connection config
     * 
     * When connectionMode is APP_TO_APP and no ConnectionConfig is provided,
     * SDK creates a default empty config for local Intent-based connection.
     */
    fun appToAppModeWithoutConfig() {
        Log.i(TAG, "🚀 Example 1: APP_TO_APP mode without config")

        // Initialize SDK with APP_TO_APP mode
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setConnectionMode(ConnectionMode.APP_TO_APP) // Specify APP_TO_APP mode
            .setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG)

        TaplinkSDK.init(context, config)

        // Connect without providing ConnectionConfig
        // SDK will create default APP_TO_APP config automatically
        TaplinkSDK.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.i(TAG, "✅ APP_TO_APP connection successful!")
                Log.i(TAG, "Device ID: $deviceId")
                Log.i(TAG, "Connection Mode: ${TaplinkSDK.getConnectionMode()}")
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "❌ APP_TO_APP connection failed: ${error.message}")
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "❌ APP_TO_APP disconnected: $reason")
            }

            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                Log.i(TAG, "🔄 APP_TO_APP reconnecting... ($attempt/$maxRetries)")
            }
        })
    }

    /**
     * Example 2: CABLE mode with no connection config
     * 
     * When connectionMode is CABLE and no ConnectionConfig is provided,
     * SDK creates a config with CableProtocol.AUTO for automatic detection.
     */
    fun cableModeWithoutConfig() {
        Log.i(TAG, "🚀 Example 2: CABLE mode without config")

        // Initialize SDK with CABLE mode
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setConnectionMode(ConnectionMode.CABLE) // Specify CABLE mode
            .setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG)

        TaplinkSDK.init(context, config)

        // Connect without providing ConnectionConfig
        // SDK will create config with CableProtocol.AUTO automatically
        TaplinkSDK.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.i(TAG, "✅ CABLE connection successful with auto-detection!")
                Log.i(TAG, "Device ID: $deviceId")
                Log.i(TAG, "Connection Mode: ${TaplinkSDK.getConnectionMode()}")
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "❌ CABLE connection failed: ${error.message}")
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "❌ CABLE disconnected: $reason")
            }

            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                Log.i(TAG, "🔄 CABLE reconnecting... ($attempt/$maxRetries)")
            }
        })
    }

    /**
     * Example 3: LAN mode with no connection config
     * 
     * When connectionMode is LAN and no ConnectionConfig is provided,
     * SDK creates a default config, but LAN requires manual host/port setup.
     */
    fun lanModeWithoutConfig() {
        Log.i(TAG, "🚀 Example 3: LAN mode without config")

        // Initialize SDK with LAN mode
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setConnectionMode(ConnectionMode.LAN) // Specify LAN mode
            .setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG)

        TaplinkSDK.init(context, config)

        // Connect without providing ConnectionConfig
        // SDK will create default config, but LAN needs host/port
        TaplinkSDK.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.i(TAG, "✅ LAN connection successful!")
                Log.i(TAG, "Device ID: $deviceId")
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "❌ LAN connection failed: ${error.message}")
                Log.i(TAG, "💡 LAN mode requires explicit host/port configuration")
                
                // Try with explicit LAN configuration
                tryLanWithExplicitConfig()
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "❌ LAN disconnected: $reason")
            }

            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                Log.i(TAG, "🔄 LAN reconnecting... ($attempt/$maxRetries)")
            }
        })
    }

    /**
     * Example 4: Comparison of explicit vs automatic config creation
     */
    fun explicitVsAutomaticConfig() {
        Log.i(TAG, "🚀 Example 4: Explicit vs Automatic config")

        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setConnectionMode(ConnectionMode.CABLE)

        TaplinkSDK.init(context, config)

        // Method 1: Explicit config (traditional way)
        Log.i(TAG, "📝 Method 1: Explicit config")
        val explicitConfig = ConnectionConfig()
            .setCableProtocol(CableProtocol.AUTO)

        TaplinkSDK.connect(explicitConfig, createConnectionListener("Explicit"))

        // Wait a moment, then try automatic
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Method 2: Automatic config (new way)
            Log.i(TAG, "🤖 Method 2: Automatic config")
            TaplinkSDK.connect(null, createConnectionListener("Automatic"))
        }, 2000)
    }

    /**
     * Example 5: Override behavior - explicit config takes precedence
     */
    fun overrideBehavior() {
        Log.i(TAG, "🚀 Example 5: Override behavior")

        // Initialize with CABLE mode
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setConnectionMode(ConnectionMode.CABLE) // Default would be AUTO

        TaplinkSDK.init(context, config)

        // Override with specific protocol
        val overrideConfig = ConnectionConfig()
            .setCableProtocol(CableProtocol.USB_VSP) // Override AUTO with specific protocol

        TaplinkSDK.connect(overrideConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.i(TAG, "✅ Override successful - using USB_VSP instead of AUTO")
                Log.i(TAG, "Connection Mode: ${TaplinkSDK.getConnectionMode()}")
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "❌ Override failed: ${error.message}")
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "❌ Override disconnected: $reason")
            }

            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                Log.i(TAG, "🔄 Override reconnecting... ($attempt/$maxRetries)")
            }
        })
    }

    /**
     * Example 6: Previous connection config takes precedence
     */
    fun previousConnectionConfig() {
        Log.i(TAG, "🚀 Example 6: Previous connection config")

        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setConnectionMode(ConnectionMode.CABLE)

        TaplinkSDK.init(context, config)

        // First connection with explicit config
        val firstConfig = ConnectionConfig()
            .setCableProtocol(CableProtocol.USB_AOA)

        TaplinkSDK.connect(firstConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.i(TAG, "✅ First connection successful with USB_AOA")
                
                // Disconnect and reconnect without config
                TaplinkSDK.disconnect()
                
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    Log.i(TAG, "🔄 Reconnecting without config - should use previous USB_AOA")
                    
                    // This should use the previous successful config (USB_AOA)
                    TaplinkSDK.connect(null, createConnectionListener("Previous"))
                }, 1000)
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "❌ First connection failed: ${error.message}")
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "❌ First connection disconnected: $reason")
            }

            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                Log.i(TAG, "🔄 First connection reconnecting... ($attempt/$maxRetries)")
            }
        })
    }

    /**
     * Try LAN connection with explicit configuration
     */
    private fun tryLanWithExplicitConfig() {
        Log.i(TAG, "🔧 Trying LAN with explicit host/port configuration")

        val lanConfig = ConnectionConfig()
            .setHost("192.168.1.100") // Replace with actual device IP
            .setPort(8443)

        TaplinkSDK.connect(lanConfig, createConnectionListener("LAN-Explicit"))
    }

    /**
     * Create a connection listener with specified prefix
     */
    private fun createConnectionListener(prefix: String) = object : ConnectionListener {
        override fun onConnected(deviceId: String, taproVersion: String) {
            Log.i(TAG, "✅ [$prefix] Connection successful!")
            Log.i(TAG, "  Device ID: $deviceId")
            Log.i(TAG, "  TaPro Version: $taproVersion")
            Log.i(TAG, "  Connection Mode: ${TaplinkSDK.getConnectionMode()}")
        }

        override fun onDisconnected(reason: String) {
            Log.w(TAG, "❌ [$prefix] Disconnected: $reason")
        }

        override fun onError(error: ConnectionError) {
            Log.e(TAG, "❌ [$prefix] Connection error:")
            Log.e(TAG, "  Code: ${error.code}")
            Log.e(TAG, "  Message: ${error.message}")
        }

        override fun onReconnecting(attempt: Int, maxRetries: Int) {
            Log.i(TAG, "🔄 [$prefix] Reconnecting... ($attempt/$maxRetries)")
        }
    }

    /**
     * Complete demonstration of all scenarios
     */
    fun runAllExamples() {
        Log.i(TAG, "🎯 Running all default connection config examples...")

        // Run examples with delays to avoid conflicts
        appToAppModeWithoutConfig()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            cableModeWithoutConfig()
        }, 3000)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            explicitVsAutomaticConfig()
        }, 6000)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            overrideBehavior()
        }, 9000)
    }
}