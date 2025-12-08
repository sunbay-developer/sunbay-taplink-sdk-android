package com.sunmi.tapro.taplink.sdk.enums

/**
 * 线缆协议枚举
 * 
 * 用于指定线缆连接时使用的协议类型
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class CableProtocol {
    /** 自动检测（默认） */
    AUTO,
    
    /** USB Android Open Accessory 2.0 */
    USB_AOA,
    
    /** USB Virtual Serial Port (CDC-ACM) */
    USB_VSP,
    
    /** 标准 RS232 串行通信 */
    RS232
}












