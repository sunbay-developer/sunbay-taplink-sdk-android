package com.sunmi.tapro.taplink.sdk.model.common

/**
 * 设备信息
 * 
 * 用于描述发起交易的设备详情
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class DeviceInfo(
    /**
     * 设备类型（可选）
     * 值：PC, LAPTOP, TABLET, MOBILE, POS, KIOSK, HANDHELD, OTHER
     */
    val deviceType: String? = null,
    
    /**
     * 操作系统类型（可选）
     * 值：ANDROID, IOS, WINDOWS, LINUX, MACOS
     */
    val osType: String? = null,
    
    /**
     * 操作系统版本（可选，如 "13.0", "14.2"）
     */
    val osVersion: String? = null,
    
    /**
     * 应用版本（可选，如 "1.0.0", "2.3.1"）
     */
    val appVersion: String? = null,
    
    /**
     * SDK 版本（可选，如 "1.0.0"）
     */
    val sdkVersion: String? = null,
    
    /**
     * 用户代理（可选，仅 Web 应用）
     */
    val userAgent: String? = null,
    
    /**
     * 设备供应商（可选，如 "Samsung", "Apple", "Sunmi"）
     */
    val deviceVendor: String? = null,
    
    /**
     * 设备型号（可选，如 "T2", "P2", "iPhone 15"）
     */
    val deviceModel: String? = null,
    
    /**
     * 设备 IP 地址（可选，支持 IPv4/IPv6）
     */
    val ip: String? = null,
    
    /**
     * 纬度（可选，十进制度数）
     */
    val latitude: String? = null,
    
    /**
     * 经度（可选，十进制度数）
     */
    val longitude: String? = null,
    
    /**
     * 语言环境（可选，格式：语言_地区，如 "en_US", "zh_CN"）
     */
    val locale: String? = null,
    
    /**
     * 时区（可选，UTC 偏移或时区名称，如 "+08:00", "America/New_York"）
     */
    val timezone: String? = null
) {
    /**
     * 链式调用：设置设备类型
     */
    fun setDeviceType(deviceType: String): DeviceInfo = copy(deviceType = deviceType)
    
    /**
     * 链式调用：设置操作系统类型
     */
    fun setOsType(osType: String): DeviceInfo = copy(osType = osType)
    
    /**
     * 链式调用：设置操作系统版本
     */
    fun setOsVersion(osVersion: String): DeviceInfo = copy(osVersion = osVersion)
    
    /**
     * 链式调用：设置应用版本
     */
    fun setAppVersion(appVersion: String): DeviceInfo = copy(appVersion = appVersion)
    
    /**
     * 链式调用：设置 SDK 版本
     */
    fun setSdkVersion(sdkVersion: String): DeviceInfo = copy(sdkVersion = sdkVersion)
    
    /**
     * 链式调用：设置用户代理
     */
    fun setUserAgent(userAgent: String): DeviceInfo = copy(userAgent = userAgent)
    
    /**
     * 链式调用：设置设备供应商
     */
    fun setDeviceVendor(deviceVendor: String): DeviceInfo = copy(deviceVendor = deviceVendor)
    
    /**
     * 链式调用：设置设备型号
     */
    fun setDeviceModel(deviceModel: String): DeviceInfo = copy(deviceModel = deviceModel)
    
    /**
     * 链式调用：设置设备 IP 地址
     */
    fun setIp(ip: String): DeviceInfo = copy(ip = ip)
    
    /**
     * 链式调用：设置纬度
     */
    fun setLatitude(latitude: String): DeviceInfo = copy(latitude = latitude)
    
    /**
     * 链式调用：设置经度
     */
    fun setLongitude(longitude: String): DeviceInfo = copy(longitude = longitude)
    
    /**
     * 链式调用：设置语言环境
     */
    fun setLocale(locale: String): DeviceInfo = copy(locale = locale)
    
    /**
     * 链式调用：设置时区
     */
    fun setTimezone(timezone: String): DeviceInfo = copy(timezone = timezone)
}

