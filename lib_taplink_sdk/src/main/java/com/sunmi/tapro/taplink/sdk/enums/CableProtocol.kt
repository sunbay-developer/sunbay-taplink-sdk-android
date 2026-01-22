package com.sunmi.tapro.taplink.sdk.enums

/**
 * Cable protocol enumeration
 * 
 * Used to specify the protocol type for cable connections
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class CableProtocol {
    /** Auto-detection (default) */
    AUTO,
    
    /** USB Android Open Accessory 2.0 */
    USB_AOA,
    
    /** USB Virtual Serial Port (CDC-ACM) */
    USB_VSP,
    
    /** Standard RS232 serial communication */
    RS232
}












