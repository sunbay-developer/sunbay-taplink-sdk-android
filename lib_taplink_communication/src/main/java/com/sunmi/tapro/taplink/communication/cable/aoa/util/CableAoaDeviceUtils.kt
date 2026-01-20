package com.sunmi.tapro.taplink.communication.cable.aoa.util

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * AOA设备工具类
 * 
 * 提供AOA设备相关的工具方法：
 * - 设备查找和过滤
 * - 接口和端点分析
 * - 设备信息提取
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
object CableAoaDeviceUtils {
    
    private const val TAG = "AoaDeviceUtils"
    
    /**
     * 端点信息数据类
     */
    data class EndpointInfo(
        val endpoint: UsbEndpoint,
        val direction: String,
        val type: String,
        val maxPacketSize: Int
    )
    
    /**
     * 接口信息数据类
     */
    data class InterfaceInfo(
        val usbInterface: UsbInterface,
        val interfaceClass: Int,
        val interfaceSubclass: Int,
        val interfaceProtocol: Int,
        val endpoints: List<EndpointInfo>
    )
    
    /**
     * 设备分析结果数据类
     */
    data class DeviceAnalysis(
        val device: UsbDevice,
        val isAoaDevice: Boolean,
        val interfaces: List<InterfaceInfo>,
        val suitableInterface: UsbInterface?,
        val inEndpoint: UsbEndpoint?,
        val outEndpoint: UsbEndpoint?
    )
    
    /**
     * 查找所有AOA设备
     */
    fun findAoaDevices(usbManager: UsbManager): List<UsbDevice> {
        return usbManager.deviceList.values.filter { device ->
            UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
        }
    }
    
    /**
     * 查找所有原始Android设备（非AOA模式）
     */
    fun findOriginalAndroidDevices(usbManager: UsbManager): List<UsbDevice> {
        return usbManager.deviceList.values.filter { device ->
            !UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
        }
    }
    
    /**
     * 根据设备名称查找设备
     */
    fun findDeviceByName(usbManager: UsbManager, deviceName: String): UsbDevice? {
        return usbManager.deviceList.values.find { device ->
            device.deviceName == deviceName
        }
    }
    
    /**
     * 根据VID/PID查找设备
     */
    fun findDeviceByVidPid(usbManager: UsbManager, vendorId: Int, productId: Int): UsbDevice? {
        return usbManager.deviceList.values.find { device ->
            device.vendorId == vendorId && device.productId == productId
        }
    }
    
    /**
     * 分析设备接口和端点
     */
    fun analyzeDevice(device: UsbDevice): DeviceAnalysis {
        LogUtil.d(TAG, "Analyzing device: ${device.deviceName}")
        LogUtil.d(TAG, "VID: ${String.format("0x%04X", device.vendorId)}, PID: ${String.format("0x%04X", device.productId)}")
        LogUtil.d(TAG, "Interface count: ${device.interfaceCount}")
        
        val interfaces = mutableListOf<InterfaceInfo>()
        var suitableInterface: UsbInterface? = null
        var inEndpoint: UsbEndpoint? = null
        var outEndpoint: UsbEndpoint? = null
        
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            val endpoints = analyzeInterfaceEndpoints(usbInterface)
            
            val interfaceInfo = InterfaceInfo(
                usbInterface = usbInterface,
                interfaceClass = usbInterface.interfaceClass,
                interfaceSubclass = usbInterface.interfaceSubclass,
                interfaceProtocol = usbInterface.interfaceProtocol,
                endpoints = endpoints
            )
            interfaces.add(interfaceInfo)
            
            LogUtil.d(TAG, "Interface[$i]: class=${usbInterface.interfaceClass}, " +
                    "subclass=${usbInterface.interfaceSubclass}, protocol=${usbInterface.interfaceProtocol}")
            LogUtil.d(TAG, "  Endpoint count: ${usbInterface.endpointCount}")
            
            // 查找合适的接口（优先选择Vendor Specific类或有足够端点的接口）
            if (suitableInterface == null) {
                if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC ||
                    (usbInterface.endpointCount >= 2 && endpoints.any { it.direction == "IN" } && endpoints.any { it.direction == "OUT" })
                ) {
                    suitableInterface = usbInterface
                    
                    // 查找输入和输出端点
                    for (endpointInfo in endpoints) {
                        if (endpointInfo.type == "BULK") {
                            when (endpointInfo.direction) {
                                "IN" -> if (inEndpoint == null) inEndpoint = endpointInfo.endpoint
                                "OUT" -> if (outEndpoint == null) outEndpoint = endpointInfo.endpoint
                            }
                        }
                    }
                    
                    LogUtil.d(TAG, "Selected interface[$i] for communication")
                    LogUtil.d(TAG, "  IN endpoint: ${if (inEndpoint != null) String.format("0x%02X", inEndpoint.address) else "none"}")
                    LogUtil.d(TAG, "  OUT endpoint: ${if (outEndpoint != null) String.format("0x%02X", outEndpoint.address) else "none"}")
                }
            }
        }
        
        val isAoaDevice = UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
        
        return DeviceAnalysis(
            device = device,
            isAoaDevice = isAoaDevice,
            interfaces = interfaces,
            suitableInterface = suitableInterface,
            inEndpoint = inEndpoint,
            outEndpoint = outEndpoint
        )
    }
    
    /**
     * 分析接口端点
     */
    private fun analyzeInterfaceEndpoints(usbInterface: UsbInterface): List<EndpointInfo> {
        val endpoints = mutableListOf<EndpointInfo>()
        
        for (j in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(j)
            val direction = if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
            val type = when (endpoint.type) {
                UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
                UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISOCHRONOUS"
                UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
                else -> "UNKNOWN"
            }
            
            val endpointInfo = EndpointInfo(
                endpoint = endpoint,
                direction = direction,
                type = type,
                maxPacketSize = endpoint.maxPacketSize
            )
            endpoints.add(endpointInfo)
            
            LogUtil.d(TAG, "    Endpoint[$j]: address=${String.format("0x%02X", endpoint.address)}, " +
                    "type=$type, direction=$direction, maxPacketSize=${endpoint.maxPacketSize}")
        }
        
        return endpoints
    }
    
    /**
     * 检查设备是否在设备列表中
     */
    fun isDevicePresent(usbManager: UsbManager, targetDevice: UsbDevice): Boolean {
        return usbManager.deviceList.values.any { device ->
            device.deviceName == targetDevice.deviceName &&
            device.vendorId == targetDevice.vendorId &&
            device.productId == targetDevice.productId
        }
    }
    
    /**
     * 获取设备的详细信息字符串
     */
    fun getDeviceDetailInfo(device: UsbDevice): String {
        val analysis = analyzeDevice(device)
        val sb = StringBuilder()
        
        sb.appendLine("Device: ${device.deviceName}")
        sb.appendLine("VID: ${String.format("0x%04X", device.vendorId)}")
        sb.appendLine("PID: ${String.format("0x%04X", device.productId)}")
        sb.appendLine("Device ID: ${device.deviceId}")
        sb.appendLine("Device Protocol: ${device.deviceProtocol}")
        sb.appendLine("Device Class: ${device.deviceClass}")
        sb.appendLine("Device Subclass: ${device.deviceSubclass}")
        sb.appendLine("Is AOA Device: ${analysis.isAoaDevice}")
        sb.appendLine("Interface Count: ${device.interfaceCount}")
        
        if (analysis.suitableInterface != null) {
            sb.appendLine("Suitable Interface: Found")
            sb.appendLine("  Class: ${analysis.suitableInterface.interfaceClass}")
            sb.appendLine("  Subclass: ${analysis.suitableInterface.interfaceSubclass}")
            sb.appendLine("  Protocol: ${analysis.suitableInterface.interfaceProtocol}")
            sb.appendLine("  IN Endpoint: ${if (analysis.inEndpoint != null) String.format("0x%02X", analysis.inEndpoint.address) else "None"}")
            sb.appendLine("  OUT Endpoint: ${if (analysis.outEndpoint != null) String.format("0x%02X", analysis.outEndpoint.address) else "None"}")
        } else {
            sb.appendLine("Suitable Interface: Not Found")
        }
        
        return sb.toString()
    }
    
    /**
     * 比较两个设备是否相同
     */
    fun isSameDevice(device1: UsbDevice?, device2: UsbDevice?): Boolean {
        if (device1 == null || device2 == null) return false
        
        return device1.deviceName == device2.deviceName &&
               device1.vendorId == device2.vendorId &&
               device1.productId == device2.productId
    }
    
    /**
     * 获取设备的简短描述
     */
    fun getDeviceShortDescription(device: UsbDevice): String {
        val deviceType = if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
            "AOA Device"
        } else {
            "Android Device"
        }
        
        return "$deviceType (${String.format("0x%04X:0x%04X", device.vendorId, device.productId)})"
    }
    
    /**
     * 检查接口是否适合AOA通信
     */
    fun isInterfaceSuitableForAoa(usbInterface: UsbInterface): Boolean {
        // 检查是否有足够的端点
        if (usbInterface.endpointCount < 2) {
            return false
        }
        
        var hasInEndpoint = false
        var hasOutEndpoint = false
        
        for (i in 0 until usbInterface.endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                    hasInEndpoint = true
                } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                    hasOutEndpoint = true
                }
            }
        }
        
        // 至少需要一个输出端点，输入端点可选
        return hasOutEndpoint
    }
}