package com.sunmi.tapro.taplink.service.enums

/**
 * USB模式枚举
 * 
 * 用于区分USB AOA协议的两种模式：
 * - HOST: Android设备作为USB主机，控制外部USB设备
 * - ACCESSORY: Android设备作为USB配件，被外部USB主机控制
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class UsbMode {
    /** USB Host模式：Android设备作为USB主机 */
    HOST,
    
    /** USB Accessory模式：Android设备作为USB配件 */
    ACCESSORY
}












