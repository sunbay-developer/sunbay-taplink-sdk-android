package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Device information.
 * 
 * Used to describe details of the device initiating the transaction.
 * 
 * Field setters update this instance and return it for chaining. Use [copy] for
 * independent top-level values, and finish updates before request submission.
 * Serialize writes and do not mutate instances used as hash collection keys.
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class DeviceInfo(
    /**
     * Device type (optional).
     * Values: PC, LAPTOP, TABLET, MOBILE, POS, KIOSK, HANDHELD, OTHER
     */
    deviceType: String? = null,
    
    /**
     * Operating system type (optional).
     * Values: ANDROID, IOS, WINDOWS, LINUX, MACOS
     */
    osType: String? = null,
    
    /**
     * Operating system version (optional, e.g., "13.0", "14.2").
     */
    osVersion: String? = null,
    
    /**
     * Application version (optional, e.g., "1.0.0", "2.3.1").
     */
    appVersion: String? = null,
    
    /**
     * SDK version (optional, e.g., "1.0.0").
     */
    sdkVersion: String? = null,
    
    /**
     * User agent (optional, web applications only).
     */
    userAgent: String? = null,
    
    /**
     * Device vendor (optional, e.g., "Samsung", "Apple", "Sunmi").
     */
    deviceVendor: String? = null,
    
    /**
     * Device model (optional, e.g., "T2", "P2", "iPhone 15").
     */
    deviceModel: String? = null,
    
    /**
     * Device IP address (optional, supports IPv4/IPv6).
     */
    ip: String? = null,
    
    /**
     * Latitude (optional, decimal degrees).
     */
    latitude: String? = null,
    
    /**
     * Longitude (optional, decimal degrees).
     */
    longitude: String? = null,
    
    /**
     * Locale (optional, format: language_region, e.g., "en_US", "zh_CN").
     */
    locale: String? = null,
    
    /**
     * Timezone (optional, UTC offset or timezone name, e.g., "+08:00", "America/New_York").
     */
    timezone: String? = null
) {
    var deviceType: String? = deviceType
        private set

    var osType: String? = osType
        private set

    var osVersion: String? = osVersion
        private set

    var appVersion: String? = appVersion
        private set

    var sdkVersion: String? = sdkVersion
        private set

    var userAgent: String? = userAgent
        private set

    var deviceVendor: String? = deviceVendor
        private set

    var deviceModel: String? = deviceModel
        private set

    var ip: String? = ip
        private set

    var latitude: String? = latitude
        private set

    var longitude: String? = longitude
        private set

    var locale: String? = locale
        private set

    var timezone: String? = timezone
        private set

    /**
     * Sets the device type.
     *
     * @param deviceType the device type
     * @return this DeviceInfo instance for method chaining
     */
    fun setDeviceType(deviceType: String): DeviceInfo = apply { this.deviceType = deviceType }
    
    /**
     * Sets the operating system type.
     *
     * @param osType the OS type
     * @return this DeviceInfo instance for method chaining
     */
    fun setOsType(osType: String): DeviceInfo = apply { this.osType = osType }
    
    /**
     * Sets the operating system version.
     *
     * @param osVersion the OS version
     * @return this DeviceInfo instance for method chaining
     */
    fun setOsVersion(osVersion: String): DeviceInfo = apply { this.osVersion = osVersion }
    
    /**
     * Sets the application version.
     *
     * @param appVersion the app version
     * @return this DeviceInfo instance for method chaining
     */
    fun setAppVersion(appVersion: String): DeviceInfo = apply { this.appVersion = appVersion }
    
    /**
     * Sets the SDK version.
     *
     * @param sdkVersion the SDK version
     * @return this DeviceInfo instance for method chaining
     */
    fun setSdkVersion(sdkVersion: String): DeviceInfo = apply { this.sdkVersion = sdkVersion }
    
    /**
     * Sets the user agent.
     *
     * @param userAgent the user agent string
     * @return this DeviceInfo instance for method chaining
     */
    fun setUserAgent(userAgent: String): DeviceInfo = apply { this.userAgent = userAgent }
    
    /**
     * Sets the device vendor.
     *
     * @param deviceVendor the device vendor
     * @return this DeviceInfo instance for method chaining
     */
    fun setDeviceVendor(deviceVendor: String): DeviceInfo = apply { this.deviceVendor = deviceVendor }
    
    /**
     * Sets the device model.
     *
     * @param deviceModel the device model
     * @return this DeviceInfo instance for method chaining
     */
    fun setDeviceModel(deviceModel: String): DeviceInfo = apply { this.deviceModel = deviceModel }
    
    /**
     * Sets the device IP address.
     *
     * @param ip the IP address
     * @return this DeviceInfo instance for method chaining
     */
    fun setIp(ip: String): DeviceInfo = apply { this.ip = ip }
    
    /**
     * Sets the latitude.
     *
     * @param latitude the latitude
     * @return this DeviceInfo instance for method chaining
     */
    fun setLatitude(latitude: String): DeviceInfo = apply { this.latitude = latitude }
    
    /**
     * Sets the longitude.
     *
     * @param longitude the longitude
     * @return this DeviceInfo instance for method chaining
     */
    fun setLongitude(longitude: String): DeviceInfo = apply { this.longitude = longitude }
    
    /**
     * Sets the locale.
     *
     * @param locale the locale
     * @return this DeviceInfo instance for method chaining
     */
    fun setLocale(locale: String): DeviceInfo = apply { this.locale = locale }
    
    /**
     * Sets the timezone.
     *
     * @param timezone the timezone
     * @return this DeviceInfo instance for method chaining
     */
    fun setTimezone(timezone: String): DeviceInfo = apply { this.timezone = timezone }

    /** Creates an independent shallow copy; setters mutate this instance. */
    fun copy(
        deviceType: String? = this.deviceType,
        osType: String? = this.osType,
        osVersion: String? = this.osVersion,
        appVersion: String? = this.appVersion,
        sdkVersion: String? = this.sdkVersion,
        userAgent: String? = this.userAgent,
        deviceVendor: String? = this.deviceVendor,
        deviceModel: String? = this.deviceModel,
        ip: String? = this.ip,
        latitude: String? = this.latitude,
        longitude: String? = this.longitude,
        locale: String? = this.locale,
        timezone: String? = this.timezone
    ): DeviceInfo = DeviceInfo(
        deviceType = deviceType,
        osType = osType,
        osVersion = osVersion,
        appVersion = appVersion,
        sdkVersion = sdkVersion,
        userAgent = userAgent,
        deviceVendor = deviceVendor,
        deviceModel = deviceModel,
        ip = ip,
        latitude = latitude,
        longitude = longitude,
        locale = locale,
        timezone = timezone
    )

    operator fun component1(): String? = deviceType
    operator fun component2(): String? = osType
    operator fun component3(): String? = osVersion
    operator fun component4(): String? = appVersion
    operator fun component5(): String? = sdkVersion
    operator fun component6(): String? = userAgent
    operator fun component7(): String? = deviceVendor
    operator fun component8(): String? = deviceModel
    operator fun component9(): String? = ip
    operator fun component10(): String? = latitude
    operator fun component11(): String? = longitude
    operator fun component12(): String? = locale
    operator fun component13(): String? = timezone

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceInfo) return false
        return deviceType == other.deviceType &&
            osType == other.osType &&
            osVersion == other.osVersion &&
            appVersion == other.appVersion &&
            sdkVersion == other.sdkVersion &&
            userAgent == other.userAgent &&
            deviceVendor == other.deviceVendor &&
            deviceModel == other.deviceModel &&
            ip == other.ip &&
            latitude == other.latitude &&
            longitude == other.longitude &&
            locale == other.locale &&
            timezone == other.timezone
    }

    override fun hashCode(): Int {
        var result = (deviceType?.hashCode() ?: 0)
        result = 31 * result + (osType?.hashCode() ?: 0)
        result = 31 * result + (osVersion?.hashCode() ?: 0)
        result = 31 * result + (appVersion?.hashCode() ?: 0)
        result = 31 * result + (sdkVersion?.hashCode() ?: 0)
        result = 31 * result + (userAgent?.hashCode() ?: 0)
        result = 31 * result + (deviceVendor?.hashCode() ?: 0)
        result = 31 * result + (deviceModel?.hashCode() ?: 0)
        result = 31 * result + (ip?.hashCode() ?: 0)
        result = 31 * result + (latitude?.hashCode() ?: 0)
        result = 31 * result + (longitude?.hashCode() ?: 0)
        result = 31 * result + (locale?.hashCode() ?: 0)
        result = 31 * result + (timezone?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "DeviceInfo(deviceType=$deviceType, osType=$osType, osVersion=$osVersion, appVersion=$appVersion, sdkVersion=$sdkVersion, userAgent=$userAgent, deviceVendor=$deviceVendor, deviceModel=$deviceModel, ip=$ip, latitude=$latitude, longitude=$longitude, locale=$locale, timezone=$timezone)"

}

