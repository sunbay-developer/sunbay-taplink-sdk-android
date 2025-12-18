package com.sunmi.tapro.taplink.sdk

import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import org.junit.Test
import org.junit.Assert.*

/**
 * Auto Reconnect Feature Tests
 * 
 * Test auto reconnect configuration and behavior
 */
class AutoReconnectTest {
    
    @Test
    fun testDefaultAutoReconnectConfig() {
        // Test default configuration
        val config = TaplinkConfig(
            appId = "test_app_id",
            merchantId = "test_merchant_id",
            secretKey = "test_secret_key"
        )
        
        // Verify default values
        assertTrue("Auto reconnect should be enabled by default", config.autoReconnect)
        assertEquals("Default max retry count should be 3", 3, config.maxRetryCount)
        assertEquals("Default retry delay should be 2000ms", 2000L, config.retryDelayMs)
    }
    
    @Test
    fun testCustomAutoReconnectConfig() {
        // Test custom configuration
        val config = TaplinkConfig(
            appId = "test_app_id",
            merchantId = "test_merchant_id",
            secretKey = "test_secret_key"
        )
            .setAutoReconnect(false)
            .setRetryPolicy(maxRetries = 5, delayMs = 3000L)
        
        // Verify custom values
        assertFalse("Auto reconnect should be disabled", config.autoReconnect)
        assertEquals("Max retry count should be 5", 5, config.maxRetryCount)
        assertEquals("Retry delay should be 3000ms", 3000L, config.retryDelayMs)
    }
    
    @Test
    fun testAutoReconnectWithDifferentConnectionModes() {
        // Test reconnect configuration for different connection modes
        val modes = listOf(
            ConnectionMode.APP_TO_APP,
            ConnectionMode.CABLE,
            ConnectionMode.LAN
        )
        
        modes.forEach { mode ->
            val config = TaplinkConfig(
                appId = "test_app_id",
                merchantId = "test_merchant_id",
                secretKey = "test_secret_key"
            )
                .setConnectionMode(mode)
                .setAutoReconnect(true)
            
            assertTrue("Auto reconnect should be enabled for $mode", config.autoReconnect)
            assertEquals("Connection mode should be $mode", mode, config.connectionMode)
        }
    }
    
    @Test
    fun testConnectionListenerWithReconnecting() {
        // Test ConnectionListener's onReconnecting callback
        var reconnectingCalled = false
        var reconnectAttempt = 0
        var reconnectMaxRetries = 0
        
        val listener = object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                // Not tested here
            }
            
            override fun onDisconnected(reason: String) {
                // Not tested here
            }
            
            override fun onError(error: ConnectionError) {
                // Not tested here
            }
            
            override fun onReconnecting(attempt: Int, maxRetries: Int) {
                reconnectingCalled = true
                reconnectAttempt = attempt
                reconnectMaxRetries = maxRetries
            }
        }
        
        // Simulate calling onReconnecting
        listener.onReconnecting(2, 5)
        
        // Verify callback was called
        assertTrue("onReconnecting should be called", reconnectingCalled)
        assertEquals("Attempt should be 2", 2, reconnectAttempt)
        assertEquals("Max retries should be 5", 5, reconnectMaxRetries)
    }
    
    @Test
    fun testRecommendedRetryPolicyForLAN() {
        // Test recommended retry policy for LAN mode
        val config = TaplinkConfig(
            appId = "test_app_id",
            merchantId = "test_merchant_id",
            secretKey = "test_secret_key"
        )
            .setConnectionMode(ConnectionMode.LAN)
            .setRetryPolicy(maxRetries = 5, delayMs = 3000L)
        
        assertEquals("LAN mode should have 5 retries", 5, config.maxRetryCount)
        assertEquals("LAN mode should have 3000ms delay", 3000L, config.retryDelayMs)
    }
    
    @Test
    fun testRecommendedRetryPolicyForAppToApp() {
        // Test recommended retry policy for app-to-app mode
        val config = TaplinkConfig(
            appId = "test_app_id",
            merchantId = "test_merchant_id",
            secretKey = "test_secret_key"
        )
            .setConnectionMode(ConnectionMode.APP_TO_APP)
            .setRetryPolicy(maxRetries = 3, delayMs = 1000L)
        
        assertEquals("APP_TO_APP mode should have 3 retries", 3, config.maxRetryCount)
        assertEquals("APP_TO_APP mode should have 1000ms delay", 1000L, config.retryDelayMs)
    }
    
    @Test
    fun testDisableAutoReconnect() {
        // Test disabling auto reconnect
        val config = TaplinkConfig(
            appId = "test_app_id",
            merchantId = "test_merchant_id",
            secretKey = "test_secret_key"
        )
            .setAutoReconnect(false)
        
        assertFalse("Auto reconnect should be disabled", config.autoReconnect)
    }
    
    @Test
    fun testChainedConfiguration() {
        // Test chained configuration
        val config = TaplinkConfig(
            appId = "test_app_id",
            merchantId = "test_merchant_id",
            secretKey = "test_secret_key"
        )
            .setConnectionMode(ConnectionMode.LAN)
            .setAutoReconnect(true)
            .setRetryPolicy(maxRetries = 5, delayMs = 3000L)
            .setLogEnabled(true)
        
        assertEquals("Connection mode should be LAN", ConnectionMode.LAN, config.connectionMode)
        assertTrue("Auto reconnect should be enabled", config.autoReconnect)
        assertEquals("Max retry count should be 5", 5, config.maxRetryCount)
        assertEquals("Retry delay should be 3000ms", 3000L, config.retryDelayMs)
        assertTrue("Log should be enabled", config.logEnabled)
    }
}
