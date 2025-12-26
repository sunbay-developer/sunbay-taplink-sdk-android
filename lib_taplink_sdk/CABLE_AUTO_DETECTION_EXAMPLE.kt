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
 * Cable Protocol Auto-Detection Example
 * 
 * Demonstrates how to use automatic cable protocol detection
 * when connecting via cable without specifying the exact protocol.
 * 
 * @author TaPro Team
 * @since 2025-12-22
 */
class CableAutoDetectionExample(private val context: Context) {

    private val TAG = "CableAutoDetection"

    /**
     * Example 1: Basic auto-detection
     * 
     * Initialize SDK with Cable mode and connect without specifying protocol.
     * SDK will automatically detect USB_AOA, USB_VSP, or RS232.
     */
    fun basicAutoDetection() {
        // 1. Initialize SDK with Cable connection mode
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setConnectionMode(ConnectionMode.CABLE) // Cable mode without specific protocol
            .setLogEnabled(true)
            .setLogLevel(LogLevel.DEBUG) // Enable debug logs to see detection process

        TaplinkSDK.init(context, config)

        // 2. Connect with default config - auto-detection will occur
        val connectionConfig = ConnectionConfig.createDefault()
        
        TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.i(TAG, "✅ Connected successfully!")
                Log.i(TAG, "Device ID: $deviceId")
                Log.i(TAG, "TaPro Version: $taproVersion")
                Log.i(TAG, "Connection Mode: ${TaplinkSDK.getConnectionMode()}")
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "❌ Disconnected: $reason")
            }

            override fun onError(error: ConnectionError) {
                Log.e(TAG, "❌ Connection failed: ${error.message}")
                Log.e(TAG, "Error code: ${error.code}")
                
                // Handle specific error cases
                when (error.code) {
                    "C41" -> {
                        Log.e(TAG, "Protocol detection failed, try manual selection")
                        tryManualProtocolSelection()
                    }
                    else -> {
                        Log.e(TAG, "Other connection error: ${error.suggestion}")
                    }
                }
            }

            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                Log.i(TAG, "🔄 Reconnecting... ($attempt/$maxRetries)")
            }
        })
    }

    /**
     * Example 2: Explicit auto-detection
     * 
     * Explicitly request auto-detection by setting CableProtocol.AUTO
     */
    fun explicitAutoDetection() {
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )

        TaplinkSDK.init(context, config)

        // Explicitly request auto-detection
        val connectionConfig = ConnectionConfig()
            .setCableProtocol(CableProtocol.AUTO)

        TaplinkSDK.connect(connectionConfig, createConnectionListener())
    }

    /**
     * Example 3: Fallback to manual selection if auto-detection fails
     */
    fun autoDetectionWithFallback() {
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setConnectionMode(ConnectionMode.CABLE)

        TaplinkSDK.init(context, config)

        // First try auto-detection
        val autoConfig = ConnectionConfig.createDefault()
        
        TaplinkSDK.connect(autoConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.i(TAG, "✅ Auto-detection successful: $deviceId")
            }

            override fun onError(error: ConnectionError) {
                Log.w(TAG, "⚠️ Auto-detection failed: ${error.message}")
                Log.i(TAG, "🔄 Trying manual protocol selection...")
                
                // Fallback to manual protocol selection
                tryManualProtocolSelection()
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "❌ Disconnected: $reason")
            }

            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                Log.i(TAG, "🔄 Reconnecting... ($attempt/$maxRetries)")
            }
        })
    }

    /**
     * Try manual protocol selection in order of preference
     */
    private fun tryManualProtocolSelection() {
        val protocols = listOf(
            CableProtocol.USB_AOA,
            CableProtocol.USB_VSP,
            CableProtocol.RS232
        )

        tryProtocol(protocols, 0)
    }

    /**
     * Recursively try each protocol
     */
    private fun tryProtocol(protocols: List<CableProtocol>, index: Int) {
        if (index >= protocols.size) {
            Log.e(TAG, "❌ All manual protocols failed")
            return
        }

        val protocol = protocols[index]
        Log.i(TAG, "🔍 Trying manual protocol: $protocol")

        val connectionConfig = ConnectionConfig()
            .setCableProtocol(protocol)

        TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                Log.i(TAG, "✅ Manual protocol successful: $protocol")
                Log.i(TAG, "Device ID: $deviceId")
            }

            override fun onError(error: ConnectionError) {
                Log.w(TAG, "⚠️ Protocol $protocol failed: ${error.message}")
                
                // Try next protocol
                tryProtocol(protocols, index + 1)
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "❌ Disconnected: $reason")
            }

            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                Log.i(TAG, "🔄 Reconnecting... ($attempt/$maxRetries)")
            }
        })
    }

    /**
     * Example 4: Device-specific protocol override
     * 
     * Override auto-detection for known devices that require specific protocols
     */
    fun deviceSpecificProtocol() {
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )

        TaplinkSDK.init(context, config)

        // Determine protocol based on device characteristics
        val connectionConfig = when (getDeviceModel()) {
            "SpecificPaymentTerminal1" -> {
                Log.i(TAG, "🎯 Using USB VSP for SpecificPaymentTerminal1")
                ConnectionConfig().setCableProtocol(CableProtocol.USB_VSP)
            }
            "LegacySerialDevice" -> {
                Log.i(TAG, "🎯 Using RS232 for LegacySerialDevice")
                ConnectionConfig().setCableProtocol(CableProtocol.RS232)
            }
            else -> {
                Log.i(TAG, "🔍 Using auto-detection for unknown device")
                ConnectionConfig().setCableProtocol(CableProtocol.AUTO)
            }
        }

        TaplinkSDK.connect(connectionConfig, createConnectionListener())
    }

    /**
     * Example 5: Debug device information
     * 
     * Get detailed information about available devices for troubleshooting
     */
    fun debugDeviceInfo() {
        try {
            val detector = com.sunmi.tapro.taplink.sdk.util.CableProtocolDetector(context)
            val deviceInfo = detector.getDeviceInfo()
            
            Log.d(TAG, "📱 Available devices:")
            Log.d(TAG, deviceInfo)
            
            // You can also perform detection manually for debugging
            kotlinx.coroutines.runBlocking {
                val result = detector.detectProtocol()
                Log.d(TAG, "🔍 Detection result:")
                Log.d(TAG, "  Protocol: ${result.protocol}")
                Log.d(TAG, "  Confidence: ${result.confidence}")
                Log.d(TAG, "  Device Info: ${result.deviceInfo}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get device info: ${e.message}")
        }
    }

    /**
     * Create a standard connection listener with comprehensive logging
     */
    private fun createConnectionListener() = object : ConnectionListener {
        override fun onConnected(deviceId: String, taproVersion: String) {
            Log.i(TAG, "✅ Connection successful!")
            Log.i(TAG, "  Device ID: $deviceId")
            Log.i(TAG, "  TaPro Version: $taproVersion")
            Log.i(TAG, "  Connection Mode: ${TaplinkSDK.getConnectionMode()}")
        }

        override fun onDisconnected(reason: String) {
            Log.w(TAG, "❌ Disconnected: $reason")
        }

        override fun onError(error: ConnectionError) {
            Log.e(TAG, "❌ Connection error:")
            Log.e(TAG, "  Code: ${error.code}")
            Log.e(TAG, "  Message: ${error.message}")
            Log.e(TAG, "  Device ID: ${error.deviceId}")
            Log.e(TAG, "  Suggestion: ${error.suggestion}")
        }

        override fun onReconnecting(attempt: Int, maxRetries: Int) {
            Log.i(TAG, "🔄 Reconnecting... ($attempt/$maxRetries)")
        }
    }

    /**
     * Mock function to get device model
     * In real implementation, this might check USB device info, 
     * system properties, or user configuration
     */
    private fun getDeviceModel(): String {
        // This is a placeholder - implement based on your device detection logic
        return "UnknownDevice"
    }

    /**
     * Complete usage example
     */
    fun completeExample() {
        Log.i(TAG, "🚀 Starting Cable Auto-Detection Example")
        
        // Step 1: Debug available devices
        debugDeviceInfo()
        
        // Step 2: Try auto-detection with fallback
        autoDetectionWithFallback()
    }
}