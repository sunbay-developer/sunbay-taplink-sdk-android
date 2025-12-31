package com.sunmi.tapro.taplink.sdk.config

import com.sunmi.tapro.taplink.sdk.enums.ConnectionMode
import com.sunmi.tapro.taplink.sdk.enums.Environment
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

    // ========== 环境和连接配置 ==========

    /**
     * 环境配置（可选，默认 PRODUCTION）
     */
    val environment: Environment = Environment.PRODUCTION,

    /**
     * 连接模式（可选）
     */
    val connectionMode: ConnectionMode? = null,

    /**
     * 连接超时时间（可选，默认 10 秒）
     */
    val connectionTimeout: Int = 60,

    /**
     * 信任自签名证书（可选，默认 true）
     */
    val trustSelfSignedCert: Boolean = true,

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
    val version: String = "1.0.0",

    /**
     * 默认超时时间（秒），默认 60
     */
    val timeout: Int = 180,

    // ========== 自动重连配置 ==========

    /**
     * 是否启用自动重连（可选，默认 true）
     * 当连接非主动断开时，SDK 会自动尝试重连
     */
    val autoReconnect: Boolean = false,

    /**
     * 最大重试次数（可选，默认 3）
     */
    val maxRetryCount: Int = 3,

    /**
     * 重试延迟时间（毫秒，默认 2000ms）
     */
    val retryDelayMs: Long = 2000L
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

    // ========== 环境和连接配置的链式调用方法 ==========

    /**
     * 链式调用：设置环境
     */
    fun setEnvironment(environment: Environment): TaplinkConfig = copy(environment = environment)

    /**
     * 链式调用：设置连接模式
     */
    fun setConnectionMode(mode: ConnectionMode): TaplinkConfig = copy(connectionMode = mode)

    /**
     * 链式调用：设置连接超时
     */
    fun setConnectionTimeout(seconds: Int): TaplinkConfig = copy(connectionTimeout = seconds)

    /**
     * 链式调用：设置是否信任自签名证书
     */
    fun setTrustSelfSignedCert(trust: Boolean): TaplinkConfig = copy(trustSelfSignedCert = trust)

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

    /**
     * 链式调用：设置 SDK 版本
     */
    fun setVersion(version: String): TaplinkConfig = copy(version = version)

    /**
     * 链式调用：设置默认超时时间
     */
    fun setTimeout(timeout: Int): TaplinkConfig = copy(timeout = timeout)

    // ========== 自动重连配置的链式调用方法 ==========

    /**
     * 链式调用：设置是否启用自动重连
     */
    fun setAutoReconnect(enabled: Boolean): TaplinkConfig = copy(autoReconnect = enabled)

    /**
     * 链式调用：设置重试策略
     *
     * @param maxRetries 最大重试次数
     * @param delayMs 重试延迟时间（毫秒）
     */
    fun setRetryPolicy(maxRetries: Int, delayMs: Long): TaplinkConfig =
        copy(maxRetryCount = maxRetries, retryDelayMs = delayMs)

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
