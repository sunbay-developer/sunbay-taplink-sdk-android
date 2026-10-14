package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.BuildConfig
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.model.common.DeviceInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * Taplink SDK configuration class.
 *
 * Supports default values to simplify request parameters.
 * Setters update this instance and return it for method chaining.
 * Finish configuring before SDK initialization; the SDK takes a shallow snapshot.
 * Use [copy] when an independent configuration is needed. Do not mutate configurations
 * used as hash collection keys. Serialize configuration writes before initialization.
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class TaplinkConfig(
    // ========== Required Configuration ==========

    /**
     * Application identifier (required).
     * Assigned by the SUNBAY platform.
     */
    appId: String,

    /**
     * Merchant ID (optional).
     * When provided, it is validated for legacy/cloud compatibility use cases.
     * It is not required for local SDK initialization.
     */
    merchantId: String? = null,

    /**
     * Signature secret key (required).
     * Used for HMAC-SHA256 signature verification.
     */
    secretKey: String,


    // ========== Logging Configuration ==========

    /**
     * Whether logging is enabled (optional, default: false).
     */
    logEnabled: Boolean = false,

    /**
     * Log level (optional, default: INFO).
     */
    logLevel: LogLevel = LogLevel.INFO,

    // ========== Default Value Configuration (Simplifies Request Parameters) ==========

    /**
     * Default staff information (optional).
     * When set, PaymentRequest can omit the staffInfo parameter.
     */
    defaultStaffInfo: StaffInfo? = null,

    /**
     * Default device information (optional).
     * When set, PaymentRequest can omit the deviceInfo parameter.
     */
    defaultDeviceInfo: DeviceInfo? = null,

    /**
     * SDK version number in format x.y.z (default: 1.0.0).
     */
    version: String = BuildConfig.VERSION_NAME,

    /**
     * Default timeout in seconds (default: 180).
     */
    timeout: Int = 180,

    /**
     * Width ratio for Tapro application execution (optional, default: null).
     * Specifies the width ratio that the Tapro app occupies on the screen.
     * Value should be between 0.0 and 1.0 (e.g., 0.5 means 50% of screen width).
     */
    taproAppWidthRatio: Float? = null,

    /**
     * (Deprecated behavior) Previously auto-opened the Tapro USB secondary-screen player right
     * after the transport was established, before [ConnectionListener.onConnected] was delivered.
     *
     * As of the Unreleased version this flag no longer triggers auto-open for LAN / Cable / AUTO /
     * On-Device connections. Only [com.sunmi.tapro.taplink.sdk.enums.ConnectionMode.SUB_SCREEN]
     * opens the screen player automatically as part of connecting. For other modes, call
     * [com.sunmi.tapro.taplink.sdk.api.TaplinkApi.openUsbScreenPlayer] explicitly after
     * `onConnected` when you need the customer-facing display.
     *
     * Retained for source/binary compatibility; the value is currently ignored.
     */
    autoOpenUsbScreenPlayer: Boolean = false
) {
    var appId: String = appId
        private set

    var merchantId: String? = merchantId
        private set

    var secretKey: String = secretKey
        private set

    var logEnabled: Boolean = logEnabled
        private set

    var logLevel: LogLevel = logLevel
        private set

    var defaultStaffInfo: StaffInfo? = defaultStaffInfo
        private set

    var defaultDeviceInfo: DeviceInfo? = defaultDeviceInfo
        private set

    val version: String = version

    var timeout: Int = timeout
        private set

    var taproAppWidthRatio: Float? = taproAppWidthRatio
        private set

    var autoOpenUsbScreenPlayer: Boolean = autoOpenUsbScreenPlayer
        private set

    init {
        validateOptionalMerchantId(merchantId)
    }

    // ========== Required Configuration Methods ==========

    /**
     * Sets the application ID.
     *
     * @param appId the application identifier
     * @return this configuration instance for method chaining
     */
    fun setAppId(appId: String): TaplinkConfig = apply { this.appId = appId }

    /**
     * Sets the merchant ID.
     *
     * @param merchantId the merchant identifier
     * @return this configuration instance for method chaining
     */
    fun setMerchantId(merchantId: String?): TaplinkConfig = apply {
        validateOptionalMerchantId(merchantId)
        this.merchantId = merchantId
    }

    /**
     * Clears the merchant ID.
     *
     * @return this configuration instance for method chaining
     */
    fun clearMerchantId(): TaplinkConfig = apply { merchantId = null }

    /**
     * Sets the signature secret key.
     *
     * @param secretKey the secret key for signature verification
     * @return this configuration instance for method chaining
     */
    fun setSecretKey(secretKey: String): TaplinkConfig = apply { this.secretKey = secretKey }


    // ========== Logging Configuration Methods ==========

    /**
     * Enables or disables logging.
     *
     * @param enabled whether logging is enabled
     * @return this configuration instance for method chaining
     */
    fun setLogEnabled(enabled: Boolean): TaplinkConfig = apply { this.logEnabled = enabled }

    /**
     * Sets the log level.
     *
     * @param level the log level
     * @return this configuration instance for method chaining
     */
    fun setLogLevel(level: LogLevel): TaplinkConfig = apply { this.logLevel = level }

    // ========== Default Value Configuration Methods ==========

    /**
     * Sets the default staff information.
     * When set, PaymentRequest can omit the staffInfo parameter.
     *
     * @param staffInfo the default staff information
     * @return this configuration instance for method chaining
     */
    fun setDefaultStaffInfo(staffInfo: StaffInfo): TaplinkConfig = apply { this.defaultStaffInfo = staffInfo }

    /**
     * Sets the default device information.
     * When set, PaymentRequest can omit the deviceInfo parameter.
     *
     * @param deviceInfo the default device information
     * @return this configuration instance for method chaining
     */
    fun setDefaultDeviceInfo(deviceInfo: DeviceInfo): TaplinkConfig = apply { this.defaultDeviceInfo = deviceInfo }

    /**
     * Sets the default transaction timeout in seconds.
     * Updates this instance. Set before SDK initialization; initialized SDKs use a snapshot.
     *
     * @param timeout default timeout in seconds
     * @return this configuration instance for method chaining
     */
    fun setTimeout(timeout: Int): TaplinkConfig = apply { this.timeout = timeout }

    /**
     * Sets the width ratio for Tapro application execution.
     *
     * @param taproAppWidthRatio the width ratio (0.0 to 1.0) for the Tapro app
     * @return this configuration instance for method chaining
     */
    fun setTaproAppWidthRatio(taproAppWidthRatio: Float?): TaplinkConfig = apply { this.taproAppWidthRatio = taproAppWidthRatio }

    /**
     * Enables or disables automatically opening the Tapro USB secondary-screen player on connect.
     *
     * @param enabled whether the screen player is opened before `onConnected` is delivered
     * @return this configuration instance for method chaining
     */
    fun setAutoOpenUsbScreenPlayer(enabled: Boolean): TaplinkConfig = apply { this.autoOpenUsbScreenPlayer = enabled }

    /** Creates an independent shallow copy, preserving the original data-class API. */
    fun copy(
        appId: String = this.appId,
        merchantId: String? = this.merchantId,
        secretKey: String = this.secretKey,
        logEnabled: Boolean = this.logEnabled,
        logLevel: LogLevel = this.logLevel,
        defaultStaffInfo: StaffInfo? = this.defaultStaffInfo,
        defaultDeviceInfo: DeviceInfo? = this.defaultDeviceInfo,
        version: String = this.version,
        timeout: Int = this.timeout,
        taproAppWidthRatio: Float? = this.taproAppWidthRatio,
        autoOpenUsbScreenPlayer: Boolean = this.autoOpenUsbScreenPlayer
    ): TaplinkConfig = TaplinkConfig(
        appId = appId,
        merchantId = merchantId,
        secretKey = secretKey,
        logEnabled = logEnabled,
        logLevel = logLevel,
        defaultStaffInfo = defaultStaffInfo,
        defaultDeviceInfo = defaultDeviceInfo,
        version = version,
        timeout = timeout,
        taproAppWidthRatio = taproAppWidthRatio,
        autoOpenUsbScreenPlayer = autoOpenUsbScreenPlayer
    )

    operator fun component1(): String = appId
    operator fun component2(): String? = merchantId
    operator fun component3(): String = secretKey
    operator fun component4(): Boolean = logEnabled
    operator fun component5(): LogLevel = logLevel
    operator fun component6(): StaffInfo? = defaultStaffInfo
    operator fun component7(): DeviceInfo? = defaultDeviceInfo
    operator fun component8(): String = version
    operator fun component9(): Int = timeout
    operator fun component10(): Float? = taproAppWidthRatio
    operator fun component11(): Boolean = autoOpenUsbScreenPlayer

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TaplinkConfig) return false
        return appId == other.appId &&
            merchantId == other.merchantId &&
            secretKey == other.secretKey &&
            logEnabled == other.logEnabled &&
            logLevel == other.logLevel &&
            defaultStaffInfo == other.defaultStaffInfo &&
            defaultDeviceInfo == other.defaultDeviceInfo &&
            version == other.version &&
            timeout == other.timeout &&
            (taproAppWidthRatio as Any?) == (other.taproAppWidthRatio as Any?) &&
            autoOpenUsbScreenPlayer == other.autoOpenUsbScreenPlayer
    }

    override fun hashCode(): Int {
        var result = appId.hashCode()
        result = 31 * result + (merchantId?.hashCode() ?: 0)
        result = 31 * result + secretKey.hashCode()
        result = 31 * result + (if (logEnabled) 1 else 0)
        result = 31 * result + logLevel.hashCode()
        result = 31 * result + (defaultStaffInfo?.hashCode() ?: 0)
        result = 31 * result + (defaultDeviceInfo?.hashCode() ?: 0)
        result = 31 * result + version.hashCode()
        result = 31 * result + timeout.hashCode()
        result = 31 * result + (taproAppWidthRatio?.hashCode() ?: 0)
        result = 31 * result + (if (autoOpenUsbScreenPlayer) 1 else 0)
        return result
    }

    override fun toString(): String =
        "TaplinkConfig(appId=$appId, merchantId=$merchantId, secretKey=$secretKey, logEnabled=$logEnabled, logLevel=$logLevel, defaultStaffInfo=$defaultStaffInfo, defaultDeviceInfo=$defaultDeviceInfo, version=$version, timeout=$timeout, taproAppWidthRatio=$taproAppWidthRatio, autoOpenUsbScreenPlayer=$autoOpenUsbScreenPlayer)"

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
