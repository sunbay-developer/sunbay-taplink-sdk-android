package com.sunmi.tapro.taplink.communication.enums

/**
 * USB mode enumeration
 * 
 * Used to distinguish between two modes of USB AOA protocol:
 * - HOST: Android device acts as USB host, controlling external USB devices
 * - ACCESSORY: Android device acts as USB accessory, controlled by external USB host
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class UsbMode {
    /** USB Host mode: Android device acts as USB host */
    HOST,
    
    /** USB Accessory mode: Android device acts as USB accessory */
    ACCESSORY
}












