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
     * Merchant ID (optional).
     * When provided, it is validated for legacy/cloud compatibility use cases.
     * It is not required for local SDK initialization.
     */
    val merchantId: String? = null,

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
    val timeout: Int = 180,

    /**
     * Width ratio for TaPro application execution (optional, default: null).
     * Specifies the width ratio that the TaPro app occupies on the screen.
     * Value should be between 0.0 and 1.0 (e.g., 0.5 means 50% of screen width).
     */
    val taproAppWidthRatio: Float? = null
) {
    init {
        validateOptionalMerchantId(merchantId)
    }

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
    fun setMerchantId(merchantId: String?): TaplinkConfig = copy(merchantId = merchantId)

    /**
     * Clears the merchant ID.
     *
     * @return the updated configuration instance for method chaining
     */
    fun clearMerchantId(): TaplinkConfig = copy(merchantId = null)

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

    /**
     * Sets the width ratio for TaPro application execution.
     *
     * @param taproAppWidthRatio the width ratio (0.0 to 1.0) for the TaPro app
     * @return the updated configuration instance for method chaining
     */
    fun setTaproAppWidthRatio(taproAppWidthRatio: Float?): TaplinkConfig = copy(taproAppWidthRatio = taproAppWidthRatio)

    companion object {
        private const val MAX_MERCHANT_ID_LENGTH = 32
        private val MERCHANT_ID_REGEX = Regex("^[A-Za-z0-9_-]+$")

        private fun validateOptionalMerchantId(merchantId: String?) {
            val normalizedMerchantId = merchantId?.trim()
            if (normalizedMerchantId.isNullOrEmpty()) {
                return
            }

            require(normalizedMerchantId.length <= MAX_MERCHANT_ID_LENGTH) {
                "merchantId must be at most $MAX_MERCHANT_ID_LENGTH characters when provided"
            }
            require(MERCHANT_ID_REGEX.matches(normalizedMerchantId)) {
                "merchantId can only contain letters, digits, underscores, and hyphens"
            }
        }

        /**
         * Creates a default configuration.
         * Requires the mandatory appId and secretKey.
         *
         * @param appId the application identifier
         * @param secretKey the signature secret key
         * @return the default configuration instance
         */
        @JvmStatic
        fun create(
            appId: String,
            secretKey: String
        ): TaplinkConfig {
            return TaplinkConfig(
                appId = appId,
                secretKey = secretKey
            )
        }

        /**
         * Creates a default configuration with an optional merchant identifier.
         *
         * @param appId the application identifier
         * @param merchantId the merchant identifier (optional)
         * @param secretKey the signature secret key
         * @return the default configuration instance
         */
        @JvmStatic
        fun create(
            appId: String,
            merchantId: String?,
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
