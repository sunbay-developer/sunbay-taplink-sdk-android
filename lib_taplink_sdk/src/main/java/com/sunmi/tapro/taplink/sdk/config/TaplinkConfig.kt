package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.BuildConfig
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.model.common.DeviceInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * Taplink SDK configuration class.
 *
 * Supports default values to simplify request parameters.
 * Uses method chaining for configuration.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TaplinkConfig(
    // ========== Required Configuration ==========

    /**
     * Application identifier (required).
     * Assigned by the SUNBAY platform.
     */
    val appId: String,

    /**
     * Merchant ID (required).
     * Unique identifier for the merchant on the SUNBAY platform.
     */
    val merchantId: String,

    /**
     * Signature secret key (required).
     * Used for HMAC-SHA256 signature verification.
     */
    val secretKey: String,


    // ========== Logging Configuration ==========

    /**
     * Whether logging is enabled (optional, default: false).
     */
    val logEnabled: Boolean = false,

    /**
     * Log level (optional, default: INFO).
     */
    val logLevel: LogLevel = LogLevel.INFO,

    // ========== Default Value Configuration (Simplifies Request Parameters) ==========

    /**
     * Default staff information (optional).
     * When set, PaymentRequest can omit the staffInfo parameter.
     */
    val defaultStaffInfo: StaffInfo? = null,

    /**
     * Default device information (optional).
     * When set, PaymentRequest can omit the deviceInfo parameter.
     */
    val defaultDeviceInfo: DeviceInfo? = null,

    /**
     * SDK version number in format x.y.z (default: 1.0.0).
     */
    val version: String = BuildConfig.VERSION_NAME,

    /**
     * Default timeout in seconds (default: 180).
     */
    val timeout: Int = 180
) {
    // ========== Required Configuration Methods ==========

    /**
     * Sets the application ID.
     *
     * @param appId the application identifier
     * @return the updated configuration instance for method chaining
     */
    fun setAppId(appId: String): TaplinkConfig = copy(appId = appId)

    /**
     * Sets the merchant ID.
     *
     * @param merchantId the merchant identifier
     * @return the updated configuration instance for method chaining
     */
    fun setMerchantId(merchantId: String): TaplinkConfig = copy(merchantId = merchantId)

    /**
     * Sets the signature secret key.
     *
     * @param secretKey the secret key for signature verification
     * @return the updated configuration instance for method chaining
     */
    fun setSecretKey(secretKey: String): TaplinkConfig = copy(secretKey = secretKey)


    // ========== Logging Configuration Methods ==========

    /**
     * Enables or disables logging.
     *
     * @param enabled whether logging is enabled
     * @return the updated configuration instance for method chaining
     */
    fun setLogEnabled(enabled: Boolean): TaplinkConfig = copy(logEnabled = enabled)

    /**
     * Sets the log level.
     *
     * @param level the log level
     * @return the updated configuration instance for method chaining
     */
    fun setLogLevel(level: LogLevel): TaplinkConfig = copy(logLevel = level)

    // ========== Default Value Configuration Methods ==========

    /**
     * Sets the default staff information.
     * When set, PaymentRequest can omit the staffInfo parameter.
     *
     * @param staffInfo the default staff information
     * @return the updated configuration instance for method chaining
     */
    fun setDefaultStaffInfo(staffInfo: StaffInfo): TaplinkConfig = copy(defaultStaffInfo = staffInfo)

    /**
     * Sets the default device information.
     * When set, PaymentRequest can omit the deviceInfo parameter.
     *
     * @param deviceInfo the default device information
     * @return the updated configuration instance for method chaining
     */
    fun setDefaultDeviceInfo(deviceInfo: DeviceInfo): TaplinkConfig = copy(defaultDeviceInfo = deviceInfo)

    companion object {
        /**
         * Creates a default configuration.
         * Requires the mandatory appId, merchantId, and secretKey.
         *
         * @param appId the application identifier
         * @param merchantId the merchant identifier
         * @param secretKey the signature secret key
         * @return the default configuration instance
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
