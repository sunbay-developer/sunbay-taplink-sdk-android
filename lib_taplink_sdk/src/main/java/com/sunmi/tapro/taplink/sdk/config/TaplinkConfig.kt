package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.BuildConfig
import com.sunmi.tapro.taplink.sdk.enums.LogLevel
import com.sunmi.tapro.taplink.sdk.model.common.DeviceInfo
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

/**
 * Taplink SDK 配置类
 *
 * 支持默认值以简化请求参数。
 * 使用链式调用方式设置配置项。
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TaplinkConfig(
    // ========== 必需配置 ==========

    /**
     * 应用标识（必需）
     * 由 SUNBAY 平台分配
     */
    val appId: String,

    /**
     * 商户 ID（必需）
     * 商户在 SUNBAY 平台的唯一标识
     */
    val merchantId: String,

    /**
     * 签名密钥（必需）
     * 用于 HMAC-SHA256 签名验证
     */
    val secretKey: String,


    // ========== 日志配置 ==========

    /**
     * 是否启用日志（可选，默认 false）
     */
    val logEnabled: Boolean = false,

    /**
     * 日志级别（可选，默认 INFO）
     */
    val logLevel: LogLevel = LogLevel.INFO,

    // ========== 默认值配置（简化请求参数）==========

    /**
     * 默认员工信息（可选）
     * 设置后，PaymentRequest 可以省略 staffInfo 参数
     */
    val defaultStaffInfo: StaffInfo? = null,

    /**
     * 默认设备信息（可选）
     * 设置后，PaymentRequest 可以省略 deviceInfo 参数
     */
    val defaultDeviceInfo: DeviceInfo? = null,

    /**
     * SDK版本号，格式：x.y.z，默认 1.0.0
     */
    val version: String = BuildConfig.VERSION_NAME,

    /**
     * 默认超时时间（秒），默认 180
     */
    val timeout: Int = 180
) {
    // ========== 必需配置的链式调用方法 ==========

    /**
     * 链式调用：设置 AppId
     */
    fun setAppId(appId: String): TaplinkConfig = copy(appId = appId)

    /**
     * 链式调用：设置商户 ID
     */
    fun setMerchantId(merchantId: String): TaplinkConfig = copy(merchantId = merchantId)

    /**
     * 链式调用：设置签名密钥
     */
    fun setSecretKey(secretKey: String): TaplinkConfig = copy(secretKey = secretKey)


    // ========== 日志配置的链式调用方法 ==========

    /**
     * 链式调用：设置是否启用日志
     */
    fun setLogEnabled(enabled: Boolean): TaplinkConfig = copy(logEnabled = enabled)

    /**
     * 链式调用：设置日志级别
     */
    fun setLogLevel(level: LogLevel): TaplinkConfig = copy(logLevel = level)

    // ========== 默认值配置的链式调用方法 ==========

    /**
     * 链式调用：设置默认员工信息
     * 设置后，PaymentRequest 可以省略 staffInfo 参数
     */
    fun setDefaultStaffInfo(staffInfo: StaffInfo): TaplinkConfig = copy(defaultStaffInfo = staffInfo)

    /**
     * 链式调用：设置默认设备信息
     * 设置后，PaymentRequest 可以省略 deviceInfo 参数
     */
    fun setDefaultDeviceInfo(deviceInfo: DeviceInfo): TaplinkConfig = copy(defaultDeviceInfo = deviceInfo)

    companion object {
        /**
         * 创建默认配置
         * 需要提供必需的 appId, merchantId, secretKey
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
