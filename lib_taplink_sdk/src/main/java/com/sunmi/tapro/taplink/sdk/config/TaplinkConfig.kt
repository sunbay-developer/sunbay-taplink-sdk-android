package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.Environment
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.model.common.DeviceInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * Taplink SDK Configuration Class
 * 
 * Supports default values to simplify request parameters.
 * Uses chained method calls to set configuration items.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TaplinkConfig(
    // ========== Required Configuration ==========
    
    /**
     * Application identifier (required)
     * Assigned by SUNBAY platform
     */
    val appId: String,
    
    /**
     * Merchant ID (required)
     * Unique identifier for merchant on SUNBAY platform
     */
    val merchantId: String,
    
    /**
     * Signature key (required)
     * Used for HMAC-SHA256 signature verification
     */
    val secretKey: String,
    
    // ========== Environment and Connection Configuration ==========
    
    /**
     * Environment configuration (optional, default PRODUCTION)
     */
    val environment: Environment = Environment.PRODUCTION,
    
    /**
     * Connection mode (optional)
     */
    val connectionMode: ConnectionMode? = null,
    
    /**
     * Connection timeout (optional, default 10 seconds)
     */
    val connectionTimeout: Int = 10,
    
    /**
     * Trust self-signed certificates (optional, default true)
     */
    val trustSelfSignedCert: Boolean = true,
    
    // ========== Log Configuration ==========
    
    /**
     * Whether to enable logging (optional, default false)
     */
    val logEnabled: Boolean = false,
    
    /**
     * Log level (optional, default INFO)
     */
    val logLevel: LogLevel = LogLevel.INFO,
    
    // ========== Default Value Configuration (Simplify Request Parameters) ==========
    
    /**
     * Default staff information (optional)
     * When set, PaymentRequest can omit staffInfo parameter
     */
    val defaultStaffInfo: StaffInfo? = null,
    
    /**
     * Default device information (optional)
     * When set, PaymentRequest can omit deviceInfo parameter
     */
    val defaultDeviceInfo: DeviceInfo? = null,
    
    /**
     * SDK version number, format: x.y.z, default 1.0.0
     */
    val version: String = "1.0.0",
    
    /**
     * Default timeout (seconds), default 60
     */
    val timeout: Int = 60,
    
    // ========== Auto Reconnect Configuration ==========
    
    /**
     * Whether to enable auto reconnect (optional, default true)
     * When connection is not actively disconnected, SDK will automatically attempt to reconnect
     */
    val autoReconnect: Boolean = true,
    
    /**
     * Maximum retry count (optional, default 3)
     */
    val maxRetryCount: Int = 3,
    
    /**
     * Retry delay time (milliseconds, default 2000ms)
     */
    val retryDelayMs: Long = 2000L
) {
    // ========== Chained Method Calls for Required Configuration ==========
    
    /**
     * Chained call: Set AppId
     */
    fun setAppId(appId: String): TaplinkConfig = copy(appId = appId)
    
    /**
     * Chained call: Set Merchant ID
     */
    fun setMerchantId(merchantId: String): TaplinkConfig = copy(merchantId = merchantId)
    
    /**
     * Chained call: Set signature key
     */
    fun setSecretKey(secretKey: String): TaplinkConfig = copy(secretKey = secretKey)
    
    // ========== Chained Method Calls for Environment and Connection Configuration ==========
    
    /**
     * Chained call: Set environment
     */
    fun setEnvironment(environment: Environment): TaplinkConfig = copy(environment = environment)
    
    /**
     * Chained call: Set connection mode
     */
    fun setConnectionMode(mode: ConnectionMode): TaplinkConfig = copy(connectionMode = mode)
    
    /**
     * Chained call: Set connection timeout
     */
    fun setConnectionTimeout(seconds: Int): TaplinkConfig = copy(connectionTimeout = seconds)
    
    /**
     * Chained call: Set whether to trust self-signed certificates
     */
    fun setTrustSelfSignedCert(trust: Boolean): TaplinkConfig = copy(trustSelfSignedCert = trust)
    
    // ========== Chained Method Calls for Log Configuration ==========
    
    /**
     * Chained call: Set whether to enable logging
     */
    fun setLogEnabled(enabled: Boolean): TaplinkConfig = copy(logEnabled = enabled)
    
    /**
     * Chained call: Set log level
     */
    fun setLogLevel(level: LogLevel): TaplinkConfig = copy(logLevel = level)
    
    // ========== Chained Method Calls for Default Value Configuration ==========
    
    /**
     * Chained call: Set default staff information
     * When set, PaymentRequest can omit staffInfo parameter
     */
    fun setDefaultStaffInfo(staffInfo: StaffInfo): TaplinkConfig = copy(defaultStaffInfo = staffInfo)
    
    /**
     * Chained call: Set default device information
     * When set, PaymentRequest can omit deviceInfo parameter
     */
    fun setDefaultDeviceInfo(deviceInfo: DeviceInfo): TaplinkConfig = copy(defaultDeviceInfo = deviceInfo)
    
    /**
     * Chained call: Set SDK version
     */
    fun setVersion(version: String): TaplinkConfig = copy(version = version)
    
    /**
     * Chained call: Set default timeout
     */
    fun setTimeout(timeout: Int): TaplinkConfig = copy(timeout = timeout)
    
    // ========== Chained Method Calls for Auto Reconnect Configuration ==========
    
    /**
     * Chained call: Set whether to enable auto reconnect
     */
    fun setAutoReconnect(enabled: Boolean): TaplinkConfig = copy(autoReconnect = enabled)
    
    /**
     * Chained call: Set retry policy
     * 
     * @param maxRetries Maximum retry count
     * @param delayMs Retry delay time (milliseconds)
     */
    fun setRetryPolicy(maxRetries: Int, delayMs: Long): TaplinkConfig = 
        copy(maxRetryCount = maxRetries, retryDelayMs = delayMs)
    
    companion object {
        /**
         * Create default configuration
         * Requires providing required appId, merchantId, secretKey
         */
        fun create(
            appId: String,
            merchantId: String,
            secretKey: String
        ): TaplinkConfig {
            return TaplinkConfig(
                appId = appId,
                merchantId = merchantId,
                secretKey = secretKey
            )
        }
    }
}
