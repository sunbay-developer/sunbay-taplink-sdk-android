package com.sunmi.tapro.taplink.communication.protocol


/**
 * USB AOA (Android Open Accessory) 协议常量
 *
 * 定义AOA协议的命令、常量等
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
object UsbStandardProtocol {
    /**
     * AOA协议版本
     */
    const val AOA_VERSION_1_0 = 1
    const val AOA_VERSION_2_0 = 2

    /**
     * AOA协议命令
     */
    const val AOA_GET_PROTOCOL = 51
    const val AOA_SEND_STRING = 52
    const val AOA_START = 53

    /**
     * AOA字符串索引
     */
    const val AOA_STRING_MANUFACTURER = 0
    const val AOA_STRING_MODEL = 1
    const val AOA_STRING_DESCRIPTION = 2
    const val AOA_STRING_VERSION = 3
    const val AOA_STRING_URI = 4
    const val AOA_STRING_SERIAL = 5

    /**
     * USB Vendor ID - Google
     */
    const val GOOGLE_VENDOR_ID = 0x18D1

    /**
     * USB Product ID - AOA
     */
    const val AOA_PRODUCT_ID_ACCESSORY = 0x2D00
    const val AOA_PRODUCT_ID_ACCESSORY_ADB = 0x2D01
    const val AOA_PRODUCT_ID_AUDIO = 0x2D02
    const val AOA_PRODUCT_ID_AUDIO_ADB = 0x2D03
    const val AOA_PRODUCT_ID_ACCESSORY_AUDIO = 0x2D04
    const val AOA_PRODUCT_ID_ACCESSORY_AUDIO_ADB = 0x2D05

    /**
     * USB控制传输请求类型
     */
    const val USB_DIR_OUT = 0x00
    const val USB_DIR_IN = 0x80
    const val USB_TYPE_VENDOR = 0x40
    const val USB_RECIPIENT_DEVICE = 0x00

    /**
     * USB控制传输请求类型组合
     */
    const val USB_REQUEST_TYPE_OUT = USB_DIR_OUT or USB_TYPE_VENDOR or USB_RECIPIENT_DEVICE
    const val USB_REQUEST_TYPE_IN = USB_DIR_IN or USB_TYPE_VENDOR or USB_RECIPIENT_DEVICE

    /**
     * 默认AOA字符串信息
     */
    data class UsbStandardInfo(
        val manufacturer: String = "SUNMI",
        val model: String = "TAPLINK_SDK",
        val description: String = "Taplink Communication SDK",
        val version: String = "1.0",
        val uri: String = "https://www.sunmi.com",
        val serial: String = "Taplink001"
    )

    /**
     * 构建AOA SEND_STRING命令数据
     */
    fun buildSendStringCommand(index: Int, string: String): ByteArray {
        // AOA协议中，SEND_STRING命令的数据就是字符串本身（以null结尾）
        // 索引通过控制传输的wIndex参数传递，不包含在数据中
        val stringBytes = string.toByteArray(Charsets.UTF_8)
        val data = ByteArray(stringBytes.size + 1) // +1 for null terminator
        System.arraycopy(stringBytes, 0, data, 0, stringBytes.size)
        data[stringBytes.size] = 0 // null terminator
        return data
    }

    /**
     * 检查设备是否支持AOA协议
     */
    fun isAoaCompatible(vendorId: Int, productId: Int): Boolean {
        return vendorId == GOOGLE_VENDOR_ID &&
                productId in AOA_PRODUCT_ID_ACCESSORY..AOA_PRODUCT_ID_ACCESSORY_AUDIO_ADB
    }
}

