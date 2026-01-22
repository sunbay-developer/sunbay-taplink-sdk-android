package com.sunmi.tapro.taplink.communication.cable.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.HexFrameBuffer
import com.sunmi.tapro.taplink.communication.protocol.HexFrameProtocol
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * RS232 serial port service kernel implementation class
 *
 * References RS232Kernel and RS232NormalAdapter implementations, inherits from AsyncServiceKernel
 * Supports automatic detection and connection of USB-to-serial devices, uses hexadecimal transmission protocol
 *
 * Protocol format: rs232://baudRate/dataBits/parity/stopBits?device=deviceName
 *
 * Features:
 * - Supports multiple USB-to-serial chips (PL2303, CH340, FTDI, etc.)
 * - Automatic device detection and driver matching
 * - USB permission management
 * - Asynchronous data transmission and reception
 * - Hexadecimal data transmission (high-performance mode)
 * - Complete lifecycle management
 *
 * Hexadecimal transmission protocol:
 * - Frame structure: FF + Length(4 bits) + Data(hex) + Checksum(2 bits) + FE
 * - Advantages: Fixed frame boundaries, checksum verification, high transmission efficiency
 * - Solves JSON fragmentation and data corruption issues
 *
 * @param appId Application ID
 * @param appSecretKey Application secret key
 * @param context Android context
 *
 * @author TaPro Team
 * @since 2025-01-03
 */
open class SerialServiceKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private val TAG = "SerialServiceKernel"

    // USB manager
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // USB serial port related
    private var usbSerialPort: UsbSerialPort? = null
    private var usbSerialDriver: UsbSerialDriver? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbIoManager: SerialInputOutputManager? = null

    // Serial port configuration parameters
    private var baudRate: Int = 115200
    private var dataBits: Int = 8
    private var parity: Int = UsbSerialPort.PARITY_NONE
    private var stopBits: Int = UsbSerialPort.STOPBITS_1
    private var targetDeviceName: String? = null
    private var portNum: Int = 0

    // Permission management
    private val permissionAction = "com.sunmi.tapro.taplink.serial.USB_PERMISSION"
    private val permissionPendingIntent: PendingIntent = createPermissionPendingIntent()
    private var pendingDevice: UsbDevice? = null
    private var isReceiverRegistered: Boolean = false

    // USB permission status
    private enum class UsbPermission {
        Unknown, Requested, Granted, Denied
    }

    private var usbPermission = UsbPermission.Unknown

    // Timeout management
    private var permissionTimeoutJob: Job? = null

    // ==================== Hexadecimal Data Processing (High-Performance Transmission Mode) ====================
    
    /**
     * Hexadecimal frame buffer manager
     */
    private var hexFrameBuffer: HexFrameBuffer? = null

    // USB prober
    private val usbDefaultProber = UsbSerialProber.getDefaultProber()
    private var usbCustomProber: UsbSerialProber? = null

    // Permission request broadcast receiver
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.d(TAG, "USB permission broadcast received: ${intent.action}")

            if (permissionAction == intent.action) {
                // Cancel timeout task
                permissionTimeoutJob?.cancel()
                permissionTimeoutJob = null

                val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                usbPermission = if (permissionGranted) UsbPermission.Granted else UsbPermission.Denied

                LogUtil.d(TAG, "USB permission result: $permissionGranted")

                // Retry connection
                scope.launch {
                    continueConnection()
                }
            }
        }
    }

    init {
        registerPermissionReceiver()
        
        // Initialize hexadecimal frame buffer
        hexFrameBuffer = HexFrameBuffer(
            scope = scope,
            onFrameReceived = { frame ->
                // Send to data receiver
                dataReceiver?.invoke(frame)
            }
        )
    }

    // ==================== AsyncServiceKernel Abstract Method Implementation ====================

    override fun getServiceType(): String = "RS232 Serial USB (Hex)"

    override fun getExpectedProtocolType(): String = "rs232 hex protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.SerialProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        val serialProtocol = parseResult as ProtocolParseResult.SerialProtocol

        // Parse protocol parameters
        baudRate = serialProtocol.baudRate
        dataBits = serialProtocol.dataBits
        parity = parseParityFromString(serialProtocol.parity)
        stopBits = parseStopBitsFromInt(serialProtocol.stopBits)

        // Parse target device name (if any)
        targetDeviceName = extractDeviceName(serialProtocol.toString())

        LogUtil.d(TAG, "Connecting to RS232 serial device (Hex mode):")
        LogUtil.d(TAG, "  baudRate=$baudRate, dataBits=$dataBits")
        LogUtil.d(TAG, "  parity=${serialProtocol.parity}, stopBits=${serialProtocol.stopBits}")
        LogUtil.d(TAG, "  targetDevice=$targetDeviceName")

        scope.launch {
            try {
                // Clear buffer
                hexFrameBuffer?.clear()
                
                val success = connectToSerialDevice()
                if (success) {
                    // Notify connection success
                    notifyConnectionSuccess(
                        mapOf(
                            "baudRate" to baudRate.toString(),
                            "dataBits" to dataBits.toString(),
                            "parity" to (serialProtocol.parity),
                            "stopBits" to (serialProtocol.stopBits).toString(),
                            "device" to (getConnectedDeviceName()),
                            "portNum" to portNum.toString(),
                            "mode" to "hex"
                        )
                    )
                } else {
                    notifyConnectionError("Failed to connect to serial device", InnerErrorCode.E232)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect serial device: ${e.message}")
                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.E232)
            }
        }
    }

    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!isSerialReady()) {
            LogUtil.e(TAG, "Serial device not ready")
            callback?.onError(InnerErrorCode.E255.code, InnerErrorCode.E255.description)
            return
        }

        try {
            val originalString = String(data)
            LogUtil.d(TAG, "Serial sending original data: $originalString (${data.size} bytes)")

            // Encode data using HexFrameProtocol
            val frameData = HexFrameProtocol.encode(data)
            
            LogUtil.d(TAG, "Serial sending hex frame: ${String(frameData)} (${frameData.size} bytes)")

            // Send data
            usbSerialPort?.write(frameData, 2000)

            LogUtil.d(TAG, "Serial hex data sent successfully: ${frameData.size} bytes")

        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to send serial hex data: ${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)

            // Send failure may indicate connection lost
            handleConnectionError()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Unexpected error sending serial hex data: ${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
        }
    }

    override fun performDisconnect() {
        LogUtil.d(TAG, "=== Serial Hex Disconnect Started ===")

        try {
            // Cancel permission timeout task
            permissionTimeoutJob?.cancel()
            permissionTimeoutJob = null

            // Stop and clear buffer
            hexFrameBuffer?.stop()

            // Stop IO manager
            usbIoManager?.setListener(null)
            usbIoManager?.stop()
            usbIoManager = null

            // Close serial port
            usbSerialPort?.close()
            usbSerialPort = null

            // Close USB connection
            usbConnection?.close()
            usbConnection = null

            usbSerialDriver = null

            LogUtil.d(TAG, "Serial hex device disconnected successfully")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during serial hex disconnect: ${e.message}")
        }

        LogUtil.d(TAG, "=== Serial Hex Disconnect Finished ===")
    }

    // ==================== Serial Port Connection Operation Methods ====================

    /**
     * Check if serial port connection is ready
     */
    private fun isSerialReady(): Boolean {
        return currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED &&
                usbSerialPort != null && usbIoManager != null
    }

    /**
     * Connect to serial port device
     */
    private suspend fun connectToSerialDevice(): Boolean {
        // Reset permission status
        usbPermission = UsbPermission.Unknown

        return continueConnection()
    }

    /**
     * Continue connection process
     */
    private suspend fun continueConnection(): Boolean {
        return try {
            // Check permission status
            if (usbPermission == UsbPermission.Denied) {
                LogUtil.e(TAG, "USB permission denied")
                return false
            }

            // Find USB device
            val device = findUsbDevice()
            if (device == null) {
                LogUtil.e(TAG, "No suitable USB serial device found")
                return false
            }

            // Get driver
            val driver = getUsbSerialDriver(device)
            if (driver == null) {
                LogUtil.e(TAG, "No driver found for device: ${device.deviceName}")
                return false
            }

            // Check ports
            if (driver.ports.isEmpty()) {
                LogUtil.e(TAG, "Device has no available ports")
                return false
            }

            // Open USB connection
            val connection = openUsbConnection(device)
            if (connection == null) {
                // May need to request permission
                if (usbPermission == UsbPermission.Unknown) {
                    requestUsbPermission(device)
                    return true // Permission request sent, waiting for result
                }
                return false
            }

            // Establish serial port connection
            return establishSerialConnection(driver, connection)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to serial device: ${e.message}")
            false
        }
    }

    /**
     * Find USB device (references RS232NormalAdapter.findUsbDevice)
     */
    private fun findUsbDevice(): UsbDevice? {
        val usbDeviceList = usbManager.deviceList.values

        LogUtil.d(TAG, "Scanning ${usbDeviceList.size} USB devices...")

        // Reference RS232NormalAdapter device search logic
        for (device in usbDeviceList) {
            val manufacturerName = device.manufacturerName
            val productId = device.productId
            val vendorId = device.vendorId
            val productIdString = String.format("%04X", productId)

            LogUtil.d(TAG, "productId: $productIdString vendorId: $vendorId")
            LogUtil.d(TAG, "manufacturerName: $manufacturerName")

            // First try default prober
            var driver = usbDefaultProber.probeDevice(device)
            if (driver == null) {
                // Try custom prober
                driver = usbCustomProber?.probeDevice(device)
            }

            if (driver != null) {
                portNum = 0
                LogUtil.d(TAG, "Found compatible device: ${device.deviceName}, Driver: ${driver.javaClass.simpleName}")
                return device
            }

            // Check if it's a SUNMI device (reference RS232NormalAdapter logic)
            val isSunmiDevice = manufacturerName?.contains("SUNMI", ignoreCase = true) == true
            if (isSunmiDevice) {
                LogUtil.d(TAG, "Found SUNMI device, creating custom prober")
                usbCustomProber = createCustomProber(vendorId, productId)
                driver = usbCustomProber?.probeDevice(device)
                if (driver != null) {
                    // Find available ports
                    for (port in 0 until driver.ports.size) {
                        LogUtil.d(TAG, "The VSP port: $port driver: $driver")
                        portNum = port
                    }
                    return device
                }
            }

            // If target device name is specified, perform matching
            if (targetDeviceName != null) {
                val deviceMatches = device.deviceName.contains(targetDeviceName!!, ignoreCase = true) ||
                        (manufacturerName?.contains(targetDeviceName!!, ignoreCase = true) == true)
                if (deviceMatches && driver != null) {
                    LogUtil.d(TAG, "Device name matches target: $targetDeviceName")
                    return device
                }
            }
        }

        return null
    }

    /**
     * Get USB serial driver (references RS232NormalAdapter.getUsbSerialDriver)
     */
    private fun getUsbSerialDriver(device: UsbDevice): UsbSerialDriver? {
        var driver = usbDefaultProber.probeDevice(device)
        if (driver == null) {
            driver = usbCustomProber?.probeDevice(device)
        }
        return driver
    }

    /**
     * Create custom prober (supports special devices)
     * References custom prober creation in RS232NormalAdapter
     */
    private fun createCustomProber(vendorId: Int, productId: Int): UsbSerialProber? {
        // Can add support for special devices here
        // For example SUNMI devices or other custom USB-to-serial devices
        // Reference RS232CustomProbe.getCustomProbe(vendorId, productId)
        return null
    }

    /**
     * Open USB device connection (references RS232NormalAdapter.openUsbConnection)
     */
    private fun openUsbConnection(device: UsbDevice): UsbDeviceConnection? {
        var hasPermission = usbManager.hasPermission(device)

        if (!hasPermission) {
            // Try auto-grant permission (references RS232NormalAdapter.grantUsbDevicePermission)
            hasPermission = grantUsbDevicePermission(device)
        }

        return if (hasPermission) {
            usbManager.openDevice(device)
        } else {
            null
        }
    }

    /**
     * Request USB permission
     */
    private fun requestUsbPermission(device: UsbDevice) {
        LogUtil.d(TAG, "Requesting USB permission for: ${device.deviceName}")

        usbPermission = UsbPermission.Requested
        pendingDevice = device

        try {
            usbManager.requestPermission(device, permissionPendingIntent)

            // Start timeout task
            permissionTimeoutJob = scope.launch {
                delay(5000) // 5 second timeout
                if (usbPermission == UsbPermission.Requested) {
                    LogUtil.e(TAG, "USB permission request timeout")
                    usbPermission = UsbPermission.Denied
                    notifyConnectionError("USB permission request timeout", InnerErrorCode.E232)
                }
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
            usbPermission = UsbPermission.Denied
        }
    }

    /**
     * Try to auto-grant USB device permission (references RS232NormalAdapter.grantUsbDevicePermission)
     */
    private fun grantUsbDevicePermission(device: UsbDevice): Boolean {
        return try {
            val clazz = Class.forName("android.hardware.usb.UsbManager")
            val method = clazz.getDeclaredMethod("grantPermission", UsbDevice::class.java, String::class.java)
            method.invoke(usbManager, device, context.packageName)
            usbManager.hasPermission(device)
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to auto-grant USB permission: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Establish serial port connection (references RS232NormalAdapter connection establishment process)
     */
    private fun establishSerialConnection(driver: UsbSerialDriver, connection: UsbDeviceConnection): Boolean {
        return try {
            // Use specified port number (references RS232NormalAdapter)
            val port = driver.ports[portNum]

            // Open serial port
            port.open(connection)

            // Set serial port parameters
            port.setParameters(baudRate, dataBits, stopBits, parity)

            // Save references
            usbSerialPort = port
            usbSerialDriver = driver
            usbConnection = connection

            // Start IO manager
            usbIoManager = SerialInputOutputManager(port, serialInputOutputListener)
            usbIoManager?.start()

            LogUtil.d(TAG, "Serial connection established successfully")
            LogUtil.d(TAG, "Device: ${driver.device.deviceName}")
            LogUtil.d(TAG, "Parameters: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")
            LogUtil.d(TAG, "Port: $portNum")

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to establish serial connection: ${e.message}")
            false
        }
    }

    /**
     * Serial port data listener - hexadecimal version
     * Uses HexFrameBuffer for data processing
     */
    private val serialInputOutputListener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray?) {
            if (data != null && data.isNotEmpty()) {
                // Reference RS232NormalAdapter logging method
                val dataString = String(data)
                LogUtil.d(TAG, "The RS232 hex receive: $dataString")
                
                // Add to buffer for processing
                hexFrameBuffer?.addData(data)
            }
        }

        override fun onRunError(exception: Exception?) {
            LogUtil.e(TAG, "Serial hex IO error: ${exception?.message}")
            LogUtil.e(TAG, "onRunError()")
            exception?.printStackTrace()

            if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                handleConnectionError()
            }
        }
    }

    /**
     * Handle connection error
     */
    private fun handleConnectionError() {
        LogUtil.w(TAG, "Serial hex connection error detected")

        if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
            updateStatus(InnerConnectionStatus.ERROR)
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Extract device name from protocol string
     */
    private fun extractDeviceName(protocol: String): String? {
        return try {
            val uri = android.net.Uri.parse(protocol)
            uri.getQueryParameter("device")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to extract device name from protocol: $protocol")
            null
        }
    }

    /**
     * Parse parity string
     */
    private fun parseParityFromString(parity: String): Int {
        return when (parity.uppercase()) {
            "NONE", "N" -> UsbSerialPort.PARITY_NONE
            "EVEN", "E" -> UsbSerialPort.PARITY_EVEN
            "ODD", "O" -> UsbSerialPort.PARITY_ODD
            "MARK", "M" -> UsbSerialPort.PARITY_MARK
            "SPACE", "S" -> UsbSerialPort.PARITY_SPACE
            else -> {
                LogUtil.w(TAG, "Unknown parity: $parity, using NONE")
                UsbSerialPort.PARITY_NONE
            }
        }
    }

    /**
     * Parse stop bits
     */
    private fun parseStopBitsFromInt(stopBits: Int): Int {
        return when (stopBits) {
            1 -> UsbSerialPort.STOPBITS_1
            2 -> UsbSerialPort.STOPBITS_2
            else -> {
                LogUtil.w(TAG, "Unknown stopBits: $stopBits, using 1")
                UsbSerialPort.STOPBITS_1
            }
        }
    }

    /**
     * Get currently connected device name
     */
    private fun getConnectedDeviceName(): String? {
        return usbSerialDriver?.device?.deviceName
    }

    /**
     * Get current serial port configuration
     */
    fun getCurrentConfig(): Map<String, Any> {
        return mapOf(
            "baudRate" to baudRate,
            "dataBits" to dataBits,
            "parity" to parity,
            "stopBits" to stopBits,
            "portNum" to portNum,
            "isConnected" to isSerialReady(),
            "device" to (getConnectedDeviceName() ?: "none"),
            "targetDevice" to (targetDeviceName ?: "auto"),
            "bufferSize" to (hexFrameBuffer?.getBufferSize() ?: 0),
            "lastDataTime" to (hexFrameBuffer?.getLastDataTimestamp() ?: 0L),
            "mode" to "hex"
        )
    }

    /**
     * Check serial port connection status
     */
    fun isSerialConnected(): Boolean = isSerialReady()

    /**
     * Get list of available serial port devices
     */
    fun getAvailableDevices(): List<Map<String, String>> {
        return try {
            val devices = mutableListOf<Map<String, String>>()

            for (device in usbManager.deviceList.values) {
                val driver = getUsbSerialDriver(device)
                if (driver != null) {
                    devices.add(
                        mapOf(
                            "deviceName" to device.deviceName,
                            "productName" to (device.productName ?: "Unknown"),
                            "manufacturerName" to (device.manufacturerName ?: "Unknown"),
                            "vendorId" to String.format("0x%04X", device.vendorId),
                            "productId" to String.format("0x%04X", device.productId),
                            "driverClass" to driver.javaClass.simpleName,
                            "portCount" to driver.ports.size.toString()
                        )
                    )
                }
            }

            devices
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to get available devices: ${e.message}")
            emptyList()
        }
    }

    // ==================== Permission Management ====================

    /**
     * Register permission request broadcast receiver
     */
    private fun registerPermissionReceiver() {
        if (!isReceiverRegistered) {
            try {
                val intentFilter = IntentFilter(permissionAction)
                ContextCompat.registerReceiver(
                    context,
                    permissionReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
                LogUtil.d(TAG, "USB permission receiver registered")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to register USB permission receiver: ${e.message}")
            }
        }
    }

    /**
     * Unregister permission request broadcast receiver
     */
    private fun unregisterPermissionReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(permissionReceiver)
                isReceiverRegistered = false
                LogUtil.d(TAG, "USB permission receiver unregistered")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error unregistering USB permission receiver: ${e.message}")
            }
        }
    }

    /**
     * Create permission request PendingIntent
     */
    private fun createPermissionPendingIntent(): PendingIntent {
        val intent = Intent(permissionAction).apply {
            setPackage(context.packageName)
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }

        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /**
     * Clean up resources
     */
    override fun cleanupCommonResources() {
        super.cleanupCommonResources()
        performDisconnect()

        // Stop buffer
        hexFrameBuffer?.stop()
        hexFrameBuffer = null

        // Clean up permission-related state
        pendingDevice = null
        usbPermission = UsbPermission.Unknown

        // Unregister broadcast receiver
        unregisterPermissionReceiver()
    }

    override fun getTag(): String = TAG

    companion object {
        /**
         * Check if system supports USB Host mode
         */
        fun isUsbHostSupported(context: Context): Boolean {
            return context.packageManager.hasSystemFeature("android.hardware.usb.host")
        }

        /**
         * Get all available USB devices (not limited to serial port devices)
         */
        fun getAllUsbDevices(context: Context): List<UsbDevice> {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return usbManager.deviceList.values.toList()
        }
    }
}