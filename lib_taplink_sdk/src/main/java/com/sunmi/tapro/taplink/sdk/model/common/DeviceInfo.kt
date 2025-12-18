package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Device Information
 * 
 * Used to describe device details for initiating transactions
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class DeviceInfo(
    /**
     * Device type (optional)
     * Values: PC, LAPTOP, TABLET, MOBILE, POS, KIOSK, HANDHELD, OTHER
     */
    val deviceType: String? = null,
    
    /**
     * Operating system type (optional)
     * Values: ANDROID, IOS, WINDOWS, LINUX, MACOS
     */
    val osType: String? = null,
    
    /**
     * Operating system version (optional, e.g., "13.0", "14.2")
     */
    val osVersion: String? = null,
    
    /**
     * Application version (optional, e.g., "1.0.0", "2.3.1")
     */
    val appVersion: String? = null,
    
    /**
     * SDK version (optional, e.g., "1.0.0")
     */
    val sdkVersion: String? = null,
    
    /**
     * User agent (optional, web applications only)
     */
    val userAgent: String? = null,
    
    /**
     * Device vendor (optional, e.g., "Samsung", "Apple", "Sunmi")
     */
    val deviceVendor: String? = null,
    
    /**
     * Device model (optional, e.g., "T2", "P2", "iPhone 15")
     */
    val deviceModel: String? = null,
    
    /**
     * Device IP address (optional, supports IPv4/IPv6)
     */
    val ip: String? = null,
    
    /**
     * Latitude (optional, decimal degrees)
     */
    val latitude: String? = null,
    
    /**
     * Longitude (optional, decimal degrees)
     */
    val longitude: String? = null,
    
    /**
     * Locale (optional, format: language_region, e.g., "en_US", "zh_CN")
     */
    val locale: String? = null,
    
    /**
     * Timezone (optional, UTC offset or timezone name, e.g., "+08:00", "America/New_York")
     */
    val timezone: String? = null
) {
    /**
     * Chained call: Set device type
     */
    fun setDeviceType(deviceType: String): DeviceInfo = copy(deviceType = deviceType)
    
    /**
     * Chained call: Set operating system type
     */
    fun setOsType(osType: String): DeviceInfo = copy(osType = osType)
    
    /**
     * Chained call: Set operating system version
     */
    fun setOsVersion(osVersion: String): DeviceInfo = copy(osVersion = osVersion)
    
    /**
     * Chained call: Set application version
     */
    fun setAppVersion(appVersion: String): DeviceInfo = copy(appVersion = appVersion)
    
    /**
     * Chained call: Set SDK version
     */
    fun setSdkVersion(sdkVersion: String): DeviceInfo = copy(sdkVersion = sdkVersion)
    
    /**
     * Chained call: Set user agent
     */
    fun setUserAgent(userAgent: String): DeviceInfo = copy(userAgent = userAgent)
    
    /**
     * Chained call: Set device vendor
     */
    fun setDeviceVendor(deviceVendor: String): DeviceInfo = copy(deviceVendor = deviceVendor)
    
    /**
     * Chained call: Set device model
     */
    fun setDeviceModel(deviceModel: String): DeviceInfo = copy(deviceModel = deviceModel)
    
    /**
     * Chained call: Set device IP address
     */
    fun setIp(ip: String): DeviceInfo = copy(ip = ip)
    
    /**
     * Chained call: Set latitude
     */
    fun setLatitude(latitude: String): DeviceInfo = copy(latitude = latitude)
    
    /**
     * Chained call: Set longitude
     */
    fun setLongitude(longitude: String): DeviceInfo = copy(longitude = longitude)
    
    /**
     * Chained call: Set locale
     */
    fun setLocale(locale: String): DeviceInfo = copy(locale = locale)
    
    /**
     * Chained call: Set timezone
     */
    fun setTimezone(timezone: String): DeviceInfo = copy(timezone = timezone)
}

