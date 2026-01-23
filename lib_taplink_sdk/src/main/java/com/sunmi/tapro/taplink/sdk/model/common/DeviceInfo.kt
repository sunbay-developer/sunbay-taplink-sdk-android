package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Device information.
 * 
 * Used to describe details of the device initiating the transaction.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class DeviceInfo(
    /**
     * Device type (optional).
     * Values: PC, LAPTOP, TABLET, MOBILE, POS, KIOSK, HANDHELD, OTHER
     */
    val deviceType: String? = null,
    
    /**
     * Operating system type (optional).
     * Values: ANDROID, IOS, WINDOWS, LINUX, MACOS
     */
    val osType: String? = null,
    
    /**
     * Operating system version (optional, e.g., "13.0", "14.2").
     */
    val osVersion: String? = null,
    
    /**
     * Application version (optional, e.g., "1.0.0", "2.3.1").
     */
    val appVersion: String? = null,
    
    /**
     * SDK version (optional, e.g., "1.0.0").
     */
    val sdkVersion: String? = null,
    
    /**
     * User agent (optional, web applications only).
     */
    val userAgent: String? = null,
    
    /**
     * Device vendor (optional, e.g., "Samsung", "Apple", "Sunmi").
     */
    val deviceVendor: String? = null,
    
    /**
     * Device model (optional, e.g., "T2", "P2", "iPhone 15").
     */
    val deviceModel: String? = null,
    
    /**
     * Device IP address (optional, supports IPv4/IPv6).
     */
    val ip: String? = null,
    
    /**
     * Latitude (optional, decimal degrees).
     */
    val latitude: String? = null,
    
    /**
     * Longitude (optional, decimal degrees).
     */
    val longitude: String? = null,
    
    /**
     * Locale (optional, format: language_region, e.g., "en_US", "zh_CN").
     */
    val locale: String? = null,
    
    /**
     * Timezone (optional, UTC offset or timezone name, e.g., "+08:00", "America/New_York").
     */
    val timezone: String? = null
) {
    /**
     * Sets the device type.
     *
     * @param deviceType the device type
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setDeviceType(deviceType: String): DeviceInfo = copy(deviceType = deviceType)
    
    /**
     * Sets the operating system type.
     *
     * @param osType the OS type
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setOsType(osType: String): DeviceInfo = copy(osType = osType)
    
    /**
     * Sets the operating system version.
     *
     * @param osVersion the OS version
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setOsVersion(osVersion: String): DeviceInfo = copy(osVersion = osVersion)
    
    /**
     * Sets the application version.
     *
     * @param appVersion the app version
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setAppVersion(appVersion: String): DeviceInfo = copy(appVersion = appVersion)
    
    /**
     * Sets the SDK version.
     *
     * @param sdkVersion the SDK version
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setSdkVersion(sdkVersion: String): DeviceInfo = copy(sdkVersion = sdkVersion)
    
    /**
     * Sets the user agent.
     *
     * @param userAgent the user agent string
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setUserAgent(userAgent: String): DeviceInfo = copy(userAgent = userAgent)
    
    /**
     * Sets the device vendor.
     *
     * @param deviceVendor the device vendor
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setDeviceVendor(deviceVendor: String): DeviceInfo = copy(deviceVendor = deviceVendor)
    
    /**
     * Sets the device model.
     *
     * @param deviceModel the device model
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setDeviceModel(deviceModel: String): DeviceInfo = copy(deviceModel = deviceModel)
    
    /**
     * Sets the device IP address.
     *
     * @param ip the IP address
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setIp(ip: String): DeviceInfo = copy(ip = ip)
    
    /**
     * Sets the latitude.
     *
     * @param latitude the latitude
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setLatitude(latitude: String): DeviceInfo = copy(latitude = latitude)
    
    /**
     * Sets the longitude.
     *
     * @param longitude the longitude
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setLongitude(longitude: String): DeviceInfo = copy(longitude = longitude)
    
    /**
     * Sets the locale.
     *
     * @param locale the locale
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setLocale(locale: String): DeviceInfo = copy(locale = locale)
    
    /**
     * Sets the timezone.
     *
     * @param timezone the timezone
     * @return the updated DeviceInfo instance for method chaining
     */
    fun setTimezone(timezone: String): DeviceInfo = copy(timezone = timezone)
}

