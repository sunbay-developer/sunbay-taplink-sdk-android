package com.sunmi.tapro.taplink.communication.cable.vsp

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
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.text.Charsets

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
 * @since 2025-01-01
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

    /**
     * App-scoped action for [UsbManager.requestPermission] PendingIntent (same pattern as Android USB host samples).
     * Must match [IntentFilter] and the Intent passed to [PendingIntent.getBroadcast].
     */
    private val permissionAction = "com.sunmi.tapro.taplink.vsp.USB_PERMISSION"

    private var pendingDevice: UsbDevice? = null

    /**
     * [cleanupCommonResources] unregisters the USB permission receiver; [TaplinkServiceKernel] reuses this
     * kernel when status is DISCONNECTED, so [init] does not run again — must re-register on each connect.
     */
    @Volatile
    private var usbPermissionReceiverRegistered: Boolean = false

    /**
     * Set when the port is open and we are waiting for [VspHandshake] with the server (same as
     * [VSPServiceKernel.performHandshake] success on the service side).
     */
    @Volatile
    private var handshakeWaiter: CompletableDeferred<Unit>? = null

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
                    val device: UsbDevice? = readUsbDeviceFromPermissionIntent(intent)
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    LogUtil.d(TAG, "USB permission result:")
                    LogUtil.d(TAG, "  Device: ${device?.deviceName} VID=${device?.vendorId} PID=${device?.productId}")
                    LogUtil.d(TAG, "  Permission granted: $permissionGranted")
                    LogUtil.d(TAG, "  Pending device: ${pendingDevice?.deviceName} VID=${pendingDevice?.vendorId} PID=${pendingDevice?.productId}")
                    // Intent delivers a new UsbDevice instance — never use == pendingDevice
                    if (device != null && isSameUsbDevice(device, pendingDevice)) {
                        pendingDevice = null

                        if (permissionGranted) {
                            LogUtil.d(TAG, "USB permission granted for VSP device: ${device.deviceName}")

                            val callback = currentConnectionCallback
                            if (callback != null) {
                                scope.launch {
                                    continueConnection(device, callback)
                                }
                            } else {
                                LogUtil.e(TAG, "USB permission granted but currentConnectionCallback is null")
                                notifyConnectionError("Connection callback lost after permission grant", InnerErrorCode.E232)
                            }
                        } else {
                            LogUtil.e(TAG, "USB permission denied for VSP device: ${device.deviceName}")
                            notifyConnectionError("USB permission denied by user", InnerErrorCode.E252)
                        }
                    } else {
                        LogUtil.w(
                            TAG,
                            "Permission result for different or unknown device (sameDevice=${device != null && isSameUsbDevice(device, pendingDevice)})"
                        )
                    }
                }
            }
        }
    }

    // ==================== AsyncServiceKernel Abstract Method Implementation ====================

    override fun getServiceType(): String = "VSP Client"

    override fun getExpectedProtocolType(): String = "vsp client protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.VspProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        registerUsbPermissionReceiverIfNeeded()

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
                val success = connectToVspDevice()
                val waitingPermission = synchronized(this@VSPClientKernel) { pendingDevice != null }
                if (success) {
                    if (awaitVspHandshake()) {
                        notifyConnectionSuccess(
                            mapOf(
                                "mode" to "client",
                                "baudRate" to baudRate.toString(),
                                "dataBits" to dataBits.toString(),
                                "parity" to vspProtocol.parity,
                                "stopBits" to vspProtocol.stopBits.toString(),
                                "device" to (targetDeviceName ?: "auto-detected"),
                                "handshake" to "verified"
                            )
                        )
                    } else {
                        cleanupAfterHandshakeFailure()
                        notifyConnectionError("VSP handshake timed out", InnerErrorCode.E232)
                    }
                } else if (!waitingPermission) {
                    notifyConnectionError("Failed to connect to VSP device", InnerErrorCode.E232)
                }
                // success == false && waitingPermission: continueConnection() will finish after permission grant
            } catch (e: CancellationException) {
                throw e
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

        handshakeWaiter?.cancel(CancellationException("VSP client disconnect"))
        handshakeWaiter = null

        try {
            dataReceiveJob?.cancel()
            dataReceiveJob = null
            
            usbSerialPort?.close()
            usbSerialPort = null
            
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
     * Port is open and we may still be in CONNECTING (handshake not finished yet).
     * Receive loop must run during CONNECTING so handshake ACK/REQ can be processed.
     */
    private fun isVspReceiveLoopActive(): Boolean {
        val st = currentInnerConnectionStatus
        return usbSerialPort != null &&
            (st == InnerConnectionStatus.CONNECTING || st == InnerConnectionStatus.CONNECTED)
    }

    /**
     * Connect to VSP device
     */
    private suspend fun connectToVspDevice(): Boolean {
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
                    usbManager.requestPermission(usbDevice, createUsbPermissionPendingIntent(usbDevice))
                    LogUtil.d(TAG, "USB permission request sent for VSP device: ${usbDevice.deviceName}")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
                    synchronized(this@VSPClientKernel) {
                        pendingDevice = null
                    }
                    return false
                }
                
                // Permission request sent; continueConnection() runs from receiver — do not report success here
                return false
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
            val targetDriver = availableDrivers.find { isSameUsbDevice(it.device, device) }
            
            if (targetDriver == null) {
                LogUtil.e(TAG, "Driver not found for device: ${device.deviceName}")
                connectionCallback.onDisconnected("DRIVER_NOT_FOUND", "Driver not found for device")
                return
            }
            
            val success = continueConnectionInternal(targetDriver)
            if (success) {
                if (awaitVspHandshake()) {
                    handleConnectionSuccess(
                        connectionCallback,
                        mapOf(
                            "mode" to "client",
                            "baudRate" to baudRate.toString(),
                            "dataBits" to dataBits.toString(),
                            "parity" to parity.toString(),
                            "stopBits" to stopBits.toString(),
                            "device" to device.deviceName,
                            "handshake" to "verified"
                        )
                    )
                } else {
                    cleanupAfterHandshakeFailure()
                    handleConnectionError(
                        "VSP handshake timed out",
                        connectionCallback,
                        InnerErrorCode.E232.code
                    )
                }
            } else {
                connectionCallback.onDisconnected("CONNECTION_FAILED", "Failed to establish VSP connection")
            }
        } catch (e: CancellationException) {
            throw e
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
     * USB port is open: start receive loop and block until application-layer handshake completes
     * (service sends [VspHandshake.REQ], we send [VspHandshake.ACK]), same window as
     * [VspHandshake.DEFAULT_TIMEOUT_MS].
     */
    private suspend fun awaitVspHandshake(): Boolean {
        val waiter = CompletableDeferred<Unit>()
        handshakeWaiter = waiter
        return try {
            startDataReceive()
            withTimeoutOrNull(VspHandshake.DEFAULT_TIMEOUT_MS) {
                waiter.await()
            } != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            LogUtil.w(TAG, "VSP handshake aborted (IO): ${e.message}")
            false
        } catch (e: Exception) {
            LogUtil.w(TAG, "VSP handshake await failed: ${e.message}")
            false
        } finally {
            handshakeWaiter = null
            if (!waiter.isCompleted) {
                waiter.cancel(CancellationException("VSP handshake abandoned"))
            }
        }
    }

    private fun signalHandshakeSuccess() {
        val w = handshakeWaiter ?: return
        if (w.complete(Unit)) {
            LogUtil.i(TAG, "VSP client handshake verified with server")
        }
    }

    private fun cleanupAfterHandshakeFailure() {
        dataReceiveJob?.cancel()
        dataReceiveJob = null
        performDisconnect()
    }

    /**
     * Start data reception loop.
     *
     * Server initiates handshake with [VspHandshake.REQ]; client replies with [VspHandshake.ACK].
     * Any remaining payload in the same read is forwarded to [dataReceiver] as usual.
     */
    private fun startDataReceive() {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch {
            val buffer = ByteArray(16384)

            LogUtil.d(TAG, "Starting VSP client data receive loop...")
            
            while (isActive && isVspReceiveLoopActive()) {
                try {
                    val bytesRead = usbSerialPort?.read(buffer, 1000) ?: 0

                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        val dataString = String(data, Charsets.UTF_8)
                        LogUtil.d(TAG, "VSP client data received: $dataString (${data.size} bytes)")

                        if (VspHandshake.isHandshakeMessage(dataString)) {
                            if (VspHandshake.containsReq(dataString)) {
                                if (sendHandshakeAck()) {
                                    signalHandshakeSuccess()
                                }
                            } else if (VspHandshake.containsAck(dataString)) {
                                // Symmetric with service handling "mutual REQ" — link already verified
                                signalHandshakeSuccess()
                            }
                            val remaining = VspHandshake.stripHandshakeMarkers(dataString)
                            if (remaining != null) {
                                dataReceiver?.invoke(remaining.toByteArray(Charsets.UTF_8))
                            }
                            continue
                        }

                        dataReceiver?.invoke(data)
                    }
                } catch (e: IOException) {
                    LogUtil.e(TAG, "Error receiving VSP client data: ${e.message}")

                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING) {
                        handshakeWaiter?.completeExceptionally(e)
                    }
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        handleConnectionError()
                    }
                    break
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Unexpected error in VSP client receive loop: ${e.message}")

                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING) {
                        handshakeWaiter?.completeExceptionally(e)
                    }
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        handleConnectionError()
                    }
                    break
                }
            }
            
            LogUtil.d(TAG, "VSP client data receive loop ended")
        }
    }

    /**
     * Reply to handshake [VspHandshake.REQ] from server.
     * @return false if ACK could not be sent (handshake cannot complete).
     */
    private fun sendHandshakeAck(): Boolean {
        return try {
            val ackBytes = VspHandshake.ACK.toByteArray(Charsets.UTF_8)
            usbSerialPort?.write(ackBytes, 1000)
            LogUtil.d(TAG, "Handshake ACK sent (server initiated REQ)")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send handshake ACK: ${e.message}")
            false
        }
    }

    /**
     * Handle connection error
     */
    private fun handleConnectionError() {
        LogUtil.w(TAG, "VSP client connection error detected")
        
        if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
            updateStatus(InnerConnectionStatus.ERROR)
            notifyConnectionDisconnected(
                InnerErrorCode.E232.code,
                "VSP client connection error"
            )
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
        
        unregisterUsbPermissionReceiverIfNeeded()
    }

    override fun getTag(): String = TAG

    // ==================== Permission Management Methods ====================

    private fun registerUsbPermissionReceiverIfNeeded() {
        synchronized(this) {
            if (usbPermissionReceiverRegistered) return
            try {
                ContextCompat.registerReceiver(
                    context,
                    permissionReceiver,
                    IntentFilter(permissionAction),
                    ContextCompat.RECEIVER_EXPORTED
                )
                usbPermissionReceiverRegistered = true
                LogUtil.d(TAG, "VSP USB permission receiver registered")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to register VSP USB permission receiver: ${e.message}")
            }
        }
    }

    private fun unregisterUsbPermissionReceiverIfNeeded() {
        synchronized(this) {
            if (!usbPermissionReceiverRegistered) return
            try {
                context.unregisterReceiver(permissionReceiver)
                LogUtil.d(TAG, "VSP USB permission receiver unregistered")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error unregistering VSP USB permission receiver: ${e.message}")
            } finally {
                usbPermissionReceiverRegistered = false
            }
        }
    }

    /**
     * USB permission broadcast carries a freshly unmarshalled [UsbDevice]; reference equality with
     * [pendingDevice] is always false. Match by stable identity.
     */
    private fun isSameUsbDevice(a: UsbDevice?, b: UsbDevice?): Boolean {
        if (a == null || b == null) return false
        return a.deviceName == b.deviceName &&
            a.vendorId == b.vendorId &&
            a.productId == b.productId
    }

    private fun readUsbDeviceFromPermissionIntent(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    /**
     * PendingIntent for [UsbManager.requestPermission]. Action must equal [permissionAction] and the receiver filter.
     * Per-device [requestCode] avoids PendingIntent collisions between devices.
     */
    private fun createUsbPermissionPendingIntent(device: UsbDevice): PendingIntent {
        val requestCode = device.deviceName.hashCode() and 0x7FFFFFFF
        val intent = Intent(permissionAction).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
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
        registerUsbPermissionReceiverIfNeeded()
        synchronized(this) {
            pendingDevice = device
            // Update current connection callback, as this callback will be used after permission is granted
            currentConnectionCallback = callback
        }
        
        try {
            usbManager.requestPermission(device, createUsbPermissionPendingIntent(device))
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