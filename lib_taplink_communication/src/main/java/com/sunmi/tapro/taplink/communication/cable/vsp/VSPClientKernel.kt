package com.sunmi.tapro.taplink.communication.cable.vsp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * VSP client kernel class
 * 
 * Acts as a virtual serial port client, actively connects to serial port devices
 * Supports multiple connection methods:
 * 1. USB-to-serial device connection
 * 2. Bluetooth serial connection
 * 3. Network serial connection
 * 
 * Protocol format: vsp://baudRate/dataBits/parity/stopBits?device=deviceName
 * 
 * @param appId Application ID
 * @param appSecretKey Application secret key
 * @param context Android context
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class VSPClientKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private val TAG = "VSPClientKernel"

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // USB serial port related
    private var usbSerialPort: UsbSerialPort? = null
    private var usbSerialDriver: UsbSerialDriver? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var dataReceiveJob: Job? = null

    // Permission request related
    private val permissionAction = "com.sunmi.tapro.taplink.vsp.USB_PERMISSION"
    private val permissionPendingIntent: PendingIntent = createPermissionPendingIntent()
    private var pendingDevice: UsbDevice? = null

    // VSP configuration parameters
    private var baudRate: Int = 115200
    private var dataBits: Int = 8
    private var parity: Int = UsbSerialPort.PARITY_NONE
    private var stopBits: Int = UsbSerialPort.STOPBITS_1
    private var targetDeviceName: String? = null

    // Permission request broadcast receiver
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.d(TAG, "USB permission broadcast received: ${intent.action}")

            if (permissionAction == intent.action) {
                synchronized(this@VSPClientKernel) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    LogUtil.d(TAG, "USB permission result:")
                    LogUtil.d(TAG, "  Device: ${device?.deviceName}")
                    LogUtil.d(TAG, "  Permission granted: $permissionGranted")
                    LogUtil.d(TAG, "  Pending device: ${pendingDevice?.deviceName}")
                    if (device != null && device == pendingDevice) {
                        pendingDevice = null

                        if (permissionGranted) {
                            LogUtil.d(TAG, "USB permission granted for VSP device: ${device.deviceName}")
                            
                            // Use parent class callback
                            currentConnectionCallback?.let { callback ->
                                scope.launch {
                                    continueConnection(device, callback)
                                }
                            }
                        } else {
                            LogUtil.e(TAG, "USB permission denied for VSP device: ${device.deviceName}")
                            notifyConnectionError("USB permission denied by user", InnerErrorCode.E252)
                        }
                    } else {
                        LogUtil.w(TAG, "Permission result for different or unknown device")
                    }
                }
            }
        }
    }

    init {
        // Register permission request broadcast receiver
        val permissionFilter = IntentFilter(permissionAction)
        try {
            ContextCompat.registerReceiver(
                context,
                permissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            LogUtil.d(TAG, "VSP USB permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register VSP USB permission receiver: ${e.message}")
        }
    }

    // ==================== AsyncServiceKernel Abstract Method Implementation ====================

    override fun getServiceType(): String = "VSP Client"

    override fun getExpectedProtocolType(): String = "vsp client protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.VspProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        val vspProtocol = parseResult as ProtocolParseResult.VspProtocol
        
        // Parse protocol parameters
        baudRate = vspProtocol.baudRate
        dataBits = vspProtocol.dataBits
        parity = parseParity(vspProtocol.parity)
        stopBits = parseStopBits(vspProtocol.stopBits)
        
        // Parse target device name (if any)
        targetDeviceName = extractDeviceName(vspProtocol.toString())

        LogUtil.d(TAG, "Connecting to VSP device as client:")
        LogUtil.d(TAG, "  baudRate=$baudRate, dataBits=$dataBits")
        LogUtil.d(TAG, "  parity=${vspProtocol.parity}, stopBits=${vspProtocol.stopBits}")
        LogUtil.d(TAG, "  targetDevice=$targetDeviceName")

        scope.launch {
            try {
                val success = connectToVspDevice(connectionCallback)
                if (success) {
                    // Start data reception
                    startDataReceive()
                    
                    // Notify connection success
                    notifyConnectionSuccess(
                        mapOf(
                            "mode" to "client",
                            "baudRate" to baudRate.toString(),
                            "dataBits" to dataBits.toString(),
                            "parity" to vspProtocol.parity,
                            "stopBits" to vspProtocol.stopBits.toString(),
                            "device" to (targetDeviceName ?: "auto-detected")
                        )
                    )
                } else {
                    notifyConnectionError("Failed to connect to VSP device", InnerErrorCode.E232)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect VSP client: ${e.message}")
                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.E232)
            }
        }
    }

    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!isVspReady()) {
            LogUtil.e(TAG, "VSP client not ready")
            callback?.onError(InnerErrorCode.E202.code, InnerErrorCode.E202.description)
            return
        }

        try {
            val dataString = String(data)
            LogUtil.d(TAG, "VSP client sending data: $dataString (${data.size} bytes)")
            
            usbSerialPort?.write(data, 1000) // 1 second timeout
            
            LogUtil.d(TAG, "VSP client data sent successfully: ${data.size} bytes")

        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to send VSP client data: ${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
            
            // Send failure may indicate connection lost
            handleConnectionError()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Unexpected error sending VSP client data: ${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
        }
    }

    override fun performDisconnect() {
        LogUtil.d(TAG, "=== VSP Client Disconnect Started ===")
        
        try {
            // Stop data reception
            dataReceiveJob?.cancel()
            dataReceiveJob = null
            
            // Close serial port connection
            usbSerialPort?.close()
            usbSerialPort = null
            
            // Close USB connection
            usbConnection?.close()
            usbConnection = null
            
            usbSerialDriver = null
            
            LogUtil.d(TAG, "VSP client disconnected successfully")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during VSP client disconnect: ${e.message}")
        }
        
        LogUtil.d(TAG, "=== VSP Client Disconnect Finished ===")
    }

    // ==================== VSP Client Operation Methods ====================

    /**
     * Check if VSP connection is ready (including USB serial port)
     */
    private fun isVspReady(): Boolean {
        return currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED && usbSerialPort != null
    }

    /**
     * Connect to VSP device
     */
    private suspend fun connectToVspDevice(connectionCallback: ConnectionCallback): Boolean {
        return try {
            // Find available USB serial port devices
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                LogUtil.e(TAG, "No USB serial drivers found")
                return false
            }

            // Select target device
            val targetDriver = selectTargetDevice(availableDrivers)
            if (targetDriver == null) {
                LogUtil.e(TAG, "Target VSP device not found")
                return false
            }

            val usbDevice = targetDriver.device

            // Check permission
            if (!usbManager.hasPermission(usbDevice)) {
                LogUtil.d(TAG, "No permission for USB device: ${usbDevice.deviceName}, requesting permission...")
                
                // Request permission
                synchronized(this@VSPClientKernel) {
                    pendingDevice = usbDevice
                }
                
                try {
                    usbManager.requestPermission(usbDevice, permissionPendingIntent)
                    LogUtil.d(TAG, "USB permission request sent for VSP device: ${usbDevice.deviceName}")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
                    synchronized(this@VSPClientKernel) {
                        pendingDevice = null
                    }
                    return false
                }
                
                // Permission request sent, waiting for user response
                return true
            }

            // Already have permission, connect directly
            return continueConnectionInternal(targetDriver)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to VSP device: ${e.message}")
            false
        }
    }

    /**
     * Continue connection after permission granted
     */
    private suspend fun continueConnection(device: UsbDevice, connectionCallback: ConnectionCallback) {
        try {
            // Need to re-find driver, as device may have changed
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val targetDriver = availableDrivers.find { it.device == device }
            
            if (targetDriver == null) {
                LogUtil.e(TAG, "Driver not found for device: ${device.deviceName}")
                connectionCallback.onDisconnected("DRIVER_NOT_FOUND", "Driver not found for device")
                return
            }
            
            val success = continueConnectionInternal(targetDriver)
            if (success) {
                // Start data reception
                startDataReceive()
                
                // Notify connection success
                connectionCallback.onConnected(
                    mapOf(
                        "mode" to "client",
                        "baudRate" to baudRate.toString(),
                        "dataBits" to dataBits.toString(),
                        "parity" to parity.toString(),
                        "stopBits" to stopBits.toString(),
                        "device" to device.deviceName
                    )
                )
            } else {
                connectionCallback.onDisconnected("CONNECTION_FAILED", "Failed to establish VSP connection")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during VSP connection: ${e.message}")
            connectionCallback.onDisconnected("CONNECTION_ERROR", e.message ?: "Unknown error")
        }
    }

    /**
     * Execute actual connection operation (already have permission)
     */
    private suspend fun continueConnectionInternal(targetDriver: UsbSerialDriver): Boolean {
        return try {
            val usbDevice = targetDriver.device
            // Open USB device connection
            val connection = usbManager.openDevice(usbDevice) ?: run {
                LogUtil.e(TAG, "Failed to open USB device")
                return false
            }

            // Get serial port (usually use first port)
            if (targetDriver.ports.isEmpty()) {
                LogUtil.e(TAG, "Driver has no ports available")
                connection.close()
                return false
            }
            
            val port = targetDriver.ports[0]

            // Open serial port
            port.open(connection)
            
            // Save connection reference
            usbConnection = connection

            // Set serial port parameters
            port.setParameters(baudRate, dataBits, stopBits, parity)

            usbSerialPort = port
            usbSerialDriver = targetDriver

            LogUtil.d(TAG, "VSP client connected to: ${usbDevice.deviceName}")
            LogUtil.d(TAG, "Serial parameters: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to establish VSP connection: ${e.message}")
            false
        }
    }

    /**
     * Select target device
     */
    private fun selectTargetDevice(availableDrivers: List<UsbSerialDriver>): UsbSerialDriver? {
        return if (targetDeviceName != null) {
            // Find by device name
            availableDrivers.find { driver ->
                driver.device.deviceName.contains(targetDeviceName!!, ignoreCase = true) ||
                driver.device.productName?.contains(targetDeviceName!!, ignoreCase = true) == true
            }
        } else {
            // Use first available device
            availableDrivers.firstOrNull()
        }?.also { driver ->
            LogUtil.d(TAG, "Selected VSP device: ${driver.device.deviceName}")
            LogUtil.d(TAG, "Device info: VID=${String.format("0x%04X", driver.device.vendorId)}, " +
                    "PID=${String.format("0x%04X", driver.device.productId)}")
        }
    }

    /**
     * Start data reception loop
     */
    private fun startDataReceive() {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch {
            val buffer = ByteArray(16384) // 16KB buffer

            LogUtil.d(TAG, "Starting VSP client data receive loop...")
            
            while (isActive && isVspReady()) {
                try {
                    val bytesRead = usbSerialPort?.read(buffer, 1000) ?: 0

                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        val dataString = String(data)
                        LogUtil.d(TAG, "VSP client data received: $dataString (${data.size} bytes)")
                        
                        // Notify data receiver
                        dataReceiver?.invoke(data)
                    }
                } catch (e: IOException) {
                    LogUtil.e(TAG, "Error receiving VSP client data: ${e.message}")
                    
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        handleConnectionError()
                    }
                    break
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Unexpected error in VSP client receive loop: ${e.message}")
                    
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        updateStatus(InnerConnectionStatus.ERROR)
                    }
                    break
                }
            }
            
            LogUtil.d(TAG, "VSP client data receive loop ended")
        }
    }

    /**
     * Handle connection error
     */
    private fun handleConnectionError() {
        LogUtil.w(TAG, "VSP client connection error detected")
        
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
     * Parse parity
     */
    private fun parseParity(parity: String): Int {
        return when (parity.uppercase()) {
            "N", "NONE" -> UsbSerialPort.PARITY_NONE
            "E", "EVEN" -> UsbSerialPort.PARITY_EVEN
            "O", "ODD" -> UsbSerialPort.PARITY_ODD
            "M", "MARK" -> UsbSerialPort.PARITY_MARK
            "S", "SPACE" -> UsbSerialPort.PARITY_SPACE
            else -> {
                LogUtil.w(TAG, "Unknown parity: $parity, using NONE")
                UsbSerialPort.PARITY_NONE
            }
        }
    }

    /**
     * Parse stop bits
     */
    private fun parseStopBits(stopBits: Int): Int {
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
     * Get current VSP configuration
     */
    fun getCurrentConfig(): Map<String, Any> {
        return mapOf(
            "mode" to "client",
            "baudRate" to baudRate,
            "dataBits" to dataBits,
            "parity" to parity,
            "stopBits" to stopBits,
            "isConnected" to isVspReady(),
            "targetDevice" to (targetDeviceName ?: "auto")
        )
    }

    /**
     * Check connection status
     */
    fun isVspConnected(): Boolean = isVspReady()

    /**
     * Get list of available VSP devices
     */
    fun getAvailableDevices(): List<Map<String, String>> {
        return try {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            availableDrivers.map { driver ->
                mapOf(
                    "deviceName" to driver.device.deviceName,
                    "productName" to (driver.device.productName ?: "Unknown"),
                    "vendorId" to String.format("0x%04X", driver.device.vendorId),
                    "productId" to String.format("0x%04X", driver.device.productId),
                    "driverClass" to driver.javaClass.simpleName
                )
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to get available devices: ${e.message}")
            emptyList()
        }
    }

    /**
     * Clean up resources
     */
    override fun cleanupCommonResources() {
        super.cleanupCommonResources()
        performDisconnect()
        
        // Clean up permission-related state
        synchronized(this) {
            pendingDevice = null
        }
        
        // Unregister broadcast receiver
        try {
            context.unregisterReceiver(permissionReceiver)
            LogUtil.d(TAG, "VSP USB permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering VSP USB permission receiver: ${e.message}")
        }
    }

    override fun getTag(): String = TAG

    // ==================== Permission Management Methods ====================

    /**
     * Create permission request PendingIntent
     */
    private fun createPermissionPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            0,
            Intent(permissionAction).apply {
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * Check if specified device has permission
     */
    fun hasPermissionForDevice(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * Manually request device permission
     * 
     * Note: This method will update the current connection callback, if a connection process is in progress, it may affect the current connection
     */
    fun requestPermissionForDevice(device: UsbDevice, callback: ConnectionCallback) {
        synchronized(this) {
            pendingDevice = device
            // Update current connection callback, as this callback will be used after permission is granted
            currentConnectionCallback = callback
        }
        
        try {
            usbManager.requestPermission(device, permissionPendingIntent)
            LogUtil.d(TAG, "Manual USB permission request sent for: ${device.deviceName}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to request USB permission manually: ${e.message}")
            synchronized(this) {
                pendingDevice = null
            }
            callback.onDisconnected("PERMISSION_REQUEST_FAILED", e.message ?: "Unknown error")
        }
    }

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