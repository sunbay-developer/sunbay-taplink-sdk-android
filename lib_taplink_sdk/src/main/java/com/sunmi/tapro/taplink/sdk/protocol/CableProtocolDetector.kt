package com.sunmi.tapro.taplink.sdk.protocol

import android.content.Context
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Cable protocol detector
 * 
 * Automatically detects available cable connection protocols in the following order:
 * 1. USB AOA (Android Open Accessory) - Default and preferred
 * 2. USB VSP (Virtual Serial Port)
 * 3. RS232 (Serial communication)
 * 
 * @author TaPro Team
 * @since 2025-12-22
 */
class CableProtocolDetector(private val context: Context) {
    
    private val TAG = "CableProtocolDetector"
    
    companion object {
        private const val DETECTION_TIMEOUT_MS = 5000L
        
        // Common RS232 device paths
        private val RS232_DEVICE_PATHS = arrayOf(
            "/dev/ttyS0",
            "/dev/ttyS1", 
            "/dev/ttyS2",
            "/dev/ttyS3",
            "/dev/ttyUSB0",
            "/dev/ttyUSB1",
            "/dev/ttyACM0",
            "/dev/ttyACM1"
        )
    }
    
    /**
     * Detection result
     */
    data class DetectionResult(
        val protocol: CableProtocol,
        val deviceInfo: String? = null,
        val confidence: Float = 1.0f
    )
    
    /**
     * Detect available cable protocol with timeout
     * 
     * @return DetectionResult with the best available protocol
     */
    suspend fun detectProtocol(): DetectionResult {
        return withTimeoutOrNull(DETECTION_TIMEOUT_MS) {
            detectProtocolInternal()
        } ?: DetectionResult(CableProtocol.USB_AOA, "Detection timeout, using default")
    }
    
    /**
     * Internal protocol detection logic
     */
    private suspend fun detectProtocolInternal(): DetectionResult = withContext(Dispatchers.IO) {
        LogUtil.d(TAG, "Starting cable protocol detection...")
        
        // 1. Check USB AOA (highest priority)
        val aoaResult = checkUsbAoa()
        if (aoaResult != null) {
            LogUtil.i(TAG, "USB AOA detected: ${aoaResult.deviceInfo}")
            return@withContext aoaResult
        }
        
        // 2. Check USB VSP (Virtual Serial Port)
        val vspResult = checkUsbVsp()
        if (vspResult != null) {
            LogUtil.i(TAG, "USB VSP detected: ${vspResult.deviceInfo}")
            return@withContext vspResult
        }
        
        // 3. Check RS232 (lowest priority)
        val rs232Result = checkRs232()
        if (rs232Result != null) {
            LogUtil.i(TAG, "RS232 detected: ${rs232Result.deviceInfo}")
            return@withContext rs232Result
        }
        
        // 4. Default to USB AOA if nothing detected
        LogUtil.w(TAG, "No cable protocol detected, defaulting to USB AOA")
        return@withContext DetectionResult(
            protocol = CableProtocol.USB_AOA,
            deviceInfo = "No device detected, using default",
            confidence = 0.1f
        )
    }
    
    /**
     * Check for USB AOA (Android Open Accessory) devices
     */
    private fun checkUsbAoa(): DetectionResult? {
        return try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            
            // Check for USB Accessories (AOA mode)
            val accessories = usbManager.accessoryList
            if (!accessories.isNullOrEmpty()) {
                val accessory = accessories[0]
                LogUtil.d(TAG, "Found USB Accessory: ${accessory.manufacturer} ${accessory.model}")
                return DetectionResult(
                    protocol = CableProtocol.USB_AOA,
                    deviceInfo = "${accessory.manufacturer} ${accessory.model}",
                    confidence = 0.9f
                )
            }
            
            // Check for USB Host devices that might support AOA
            val devices = usbManager.deviceList
            for ((_, device) in devices) {
                if (isAoaCompatibleDevice(device)) {
                    LogUtil.d(TAG, "Found AOA compatible USB device: ${device.deviceName}")
                    return DetectionResult(
                        protocol = CableProtocol.USB_AOA,
                        deviceInfo = "USB Device: ${device.deviceName}",
                        confidence = 0.8f
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error checking USB AOA: ${e.message}")
            null
        }
    }
    
    /**
     * Check for USB VSP (Virtual Serial Port) devices
     */
    private fun checkUsbVsp(): DetectionResult? {
        return try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val devices = usbManager.deviceList
            
            for ((_, device) in devices) {
                if (isVspCompatibleDevice(device)) {
                    LogUtil.d(TAG, "Found VSP compatible device: ${device.deviceName}")
                    return DetectionResult(
                        protocol = CableProtocol.USB_VSP,
                        deviceInfo = "VSP Device: ${device.deviceName}",
                        confidence = 0.7f
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error checking USB VSP: ${e.message}")
            null
        }
    }
    
    /**
     * Check for RS232 serial devices
     */
    private fun checkRs232(): DetectionResult? {
        return try {
            for (devicePath in RS232_DEVICE_PATHS) {
                val deviceFile = File(devicePath)
                if (deviceFile.exists() && deviceFile.canRead() && deviceFile.canWrite()) {
                    LogUtil.d(TAG, "Found RS232 device: $devicePath")
                    return DetectionResult(
                        protocol = CableProtocol.RS232,
                        deviceInfo = "Serial Device: $devicePath",
                        confidence = 0.6f
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error checking RS232: ${e.message}")
            null
        }
    }
    
    /**
     * Check if USB device is AOA compatible
     */
    private fun isAoaCompatibleDevice(device: UsbDevice): Boolean {
        // AOA compatible devices typically have specific vendor/product IDs
        // or support the AOA protocol
        
        // Google's AOA vendor ID
        if (device.vendorId == 0x18D1) {
            return true
        }
        
        // Check for common payment terminal vendors
        val paymentTerminalVendors = setOf(
            0x0403, // FTDI
            0x067B, // Prolific
            0x10C4, // Silicon Labs
            0x1A86, // QinHeng Electronics
            0x0525  // Netchip Technology
        )
        
        return paymentTerminalVendors.contains(device.vendorId)
    }
    
    /**
     * Check if USB device is VSP compatible
     */
    private fun isVspCompatibleDevice(device: UsbDevice): Boolean {
        // VSP devices typically use CDC-ACM class
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            
            // CDC-ACM class (Communications Device Class - Abstract Control Model)
            if (usbInterface.interfaceClass == 2 && usbInterface.interfaceSubclass == 2) {
                return true
            }
            
            // FTDI devices
            if (device.vendorId == 0x0403) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Get detailed device information for debugging
     */
    fun getDeviceInfo(): String {
        val info = StringBuilder()
        
        try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            
            // USB Accessories
            val accessories = usbManager.accessoryList
            if (!accessories.isNullOrEmpty()) {
                info.append("USB Accessories:\n")
                accessories.forEach { accessory ->
                    info.append("  - ${accessory.manufacturer} ${accessory.model} (${accessory.version})\n")
                }
            }
            
            // USB Devices
            val devices = usbManager.deviceList
            if (devices.isNotEmpty()) {
                info.append("USB Devices:\n")
                devices.values.forEach { device ->
                    info.append("  - ${device.deviceName} (VID:${String.format("%04X", device.vendorId)}, PID:${String.format("%04X", device.productId)})\n")
                }
            }
            
            // Serial Devices
            val serialDevices = RS232_DEVICE_PATHS.filter { File(it).exists() }
            if (serialDevices.isNotEmpty()) {
                info.append("Serial Devices:\n")
                serialDevices.forEach { path ->
                    info.append("  - $path\n")
                }
            }
            
        } catch (e: Exception) {
            info.append("Error getting device info: ${e.message}")
        }
        
        return info.toString()
    }
}