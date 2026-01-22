package com.sunmi.tapro.taplink.communication.cable.aoa.util

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * AOA device utility class
 * 
 * Provides AOA device-related utility methods:
 * - Device search and filtering
 * - Interface and endpoint analysis
 * - Device information extraction
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
object CableAoaDeviceUtils {
    
    private const val TAG = "AoaDeviceUtils"
    
    /**
     * Endpoint information data class
     */
    data class EndpointInfo(
        val endpoint: UsbEndpoint,
        val direction: String,
        val type: String,
        val maxPacketSize: Int
    )
    
    /**
     * Interface information data class
     */
    data class InterfaceInfo(
        val usbInterface: UsbInterface,
        val interfaceClass: Int,
        val interfaceSubclass: Int,
        val interfaceProtocol: Int,
        val endpoints: List<EndpointInfo>
    )
    
    /**
     * Device analysis result data class
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
     * Find all AOA devices
     */
    fun findAoaDevices(usbManager: UsbManager): List<UsbDevice> {
        return usbManager.deviceList.values.filter { device ->
            UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
        }
    }
    
    /**
     * Find all original Android devices (non-AOA mode)
     */
    fun findOriginalAndroidDevices(usbManager: UsbManager): List<UsbDevice> {
        return usbManager.deviceList.values.filter { device ->
            !UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
        }
    }
    
    /**
     * Find device by device name
     */
    fun findDeviceByName(usbManager: UsbManager, deviceName: String): UsbDevice? {
        return usbManager.deviceList.values.find { device ->
            device.deviceName == deviceName
        }
    }
    
    /**
     * Find device by VID/PID
     */
    fun findDeviceByVidPid(usbManager: UsbManager, vendorId: Int, productId: Int): UsbDevice? {
        return usbManager.deviceList.values.find { device ->
            device.vendorId == vendorId && device.productId == productId
        }
    }
    
    /**
     * Analyze device interfaces and endpoints
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
            
            // Find suitable interface (prefer Vendor Specific class or interface with sufficient endpoints)
            if (suitableInterface == null) {
                if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC ||
                    (usbInterface.endpointCount >= 2 && endpoints.any { it.direction == "IN" } && endpoints.any { it.direction == "OUT" })
                ) {
                    suitableInterface = usbInterface
                    
                    // Find input and output endpoints
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
     * Analyze interface endpoints
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
     * Check if device is present in device list
     */
    fun isDevicePresent(usbManager: UsbManager, targetDevice: UsbDevice): Boolean {
        return usbManager.deviceList.values.any { device ->
            device.deviceName == targetDevice.deviceName &&
            device.vendorId == targetDevice.vendorId &&
            device.productId == targetDevice.productId
        }
    }
    
    /**
     * Get device detailed information string
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
     * Compare if two devices are the same
     */
    fun isSameDevice(device1: UsbDevice?, device2: UsbDevice?): Boolean {
        if (device1 == null || device2 == null) return false
        
        return device1.deviceName == device2.deviceName &&
               device1.vendorId == device2.vendorId &&
               device1.productId == device2.productId
    }
    
    /**
     * Get device short description
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
     * Check if interface is suitable for AOA communication
     */
    fun isInterfaceSuitableForAoa(usbInterface: UsbInterface): Boolean {
        // Check if has sufficient endpoints
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
        
        // At least need one output endpoint, input endpoint is optional
        return hasOutEndpoint
    }
}