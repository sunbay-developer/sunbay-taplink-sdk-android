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
import com.sunmi.tapro.taplink.communication.cable.vsp.VspHandshake
import com.sunmi.tapro.taplink.communication.protocol.HexFrameBuffer
import com.sunmi.tapro.taplink.communication.protocol.HexFrameProtocol
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlin.jvm.Volatile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/** Interval for USB host presence check while RS232 (USB-serial) is connected. */
private const val SERIAL_LINK_CHECK_INTERVAL_MS = 5_000L

/** Same cadence as [VspHandshake.DEFAULT_INTERVAL_MS] — both ends send REQ so peer loss is detectable. */
private const val RS232_HSK_REQ_INTERVAL_MS = 1_000L

private const val RS232_HSK_SILENCE_CHECK_MS = 1_000L

/** No handshake or application payload from peer after we have seen at least one — likely far-end unplug or dead link. */
private const val RS232_HSK_PEER_SILENCE_MS = 5_000L

/**
 * USB layer outcome for [continueConnection] / [connectToSerialDevice].
 * [SerialReady] means the serial port is open; app-level "connected" still waits for [VspHandshake] from peer.
 */
private sealed class SerialUsbConnectResult {
    object Failed : SerialUsbConnectResult()
    object PermissionPending : SerialUsbConnectResult()
    object SerialReady : SerialUsbConnectResult()
}

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

    /** Preserved for [notifyConnectionSuccess] extra map (parity/stopBits as in [ProtocolParseResult.SerialProtocol]). */
    private var lastSerialParityLabel: String = ""
    private var lastSerialStopBitsProtocol: Int = 1

    /** Must match PendingIntent intent action and IntentFilter (Android USB permission pattern). */
    private val permissionAction = "com.sunmi.tapro.taplink.serial.USB_PERMISSION"

    private var pendingDevice: UsbDevice? = null
    private var isReceiverRegistered: Boolean = false

    // USB permission status
    private enum class UsbPermission {
        Unknown, Requested, Granted, Denied
    }

    private var usbPermission = UsbPermission.Unknown

    // Timeout management
    private var permissionTimeoutJob: Job? = null

    /**
     * Periodic USB presence check while CONNECTED.
     *
     * **VSP** uses application-level liveness: the peer sends `VspHandshake.REQ` and the client
     * replies with ACK from `VSPClientKernel.sendHandshakeAck` inside the read loop — not LAN
     * `HeartbeatManager`. I/O errors then go through `handleConnectionError`.
     *
     * **RS232 Hex** uses `SerialInputOutputManager` + hex framing, not the VSP text read loop, so the
     * same REQ/ACK path is not wired here; unplug often drops the device from `UsbManager.deviceList`
     * before `onRunError`. This job adds a **host-side attach check**, complementary to VSP's peer-driven
     * handshake rather than a duplicate of `sendHandshakeAck`.
     */
    private var serialLinkWatchdogJob: Job? = null

    private var serialHandshakeSendJob: Job? = null
    private var serialHandshakeSilenceJob: Job? = null

    /** True between USB serial open and first peer [VspHandshake] marker (REQ/ACK). */
    @Volatile
    private var awaitingPeerHandshake: Boolean = false

    private var peerHandshakeAwaitSendJob: Job? = null
    private var peerHandshakeAwaitTimeoutJob: Job? = null

    private val handshakeStreamLock = Any()
    private val handshakeStreamBuf = StringBuilder(256)

    @Volatile
    private var lastPeerHandshakeActivityMillis: Long = 0L

    /** Once true, missing peer handshake or application payload for [RS232_HSK_PEER_SILENCE_MS] is treated as disconnect. */
    @Volatile
    private var peerHandshakeEverSeen: Boolean = false

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

                // Retry connection — USB open then wait for peer handshake before reporting CONNECTED
                scope.launch {
                    when (continueConnection()) {
                        SerialUsbConnectResult.Failed -> {
                            if (usbPermission == UsbPermission.Denied) {
                                notifyConnectionError("USB permission denied", InnerErrorCode.E232)
                            } else {
                                notifyConnectionError(
                                    "Failed to open serial device after USB permission granted",
                                    InnerErrorCode.E232
                                )
                            }
                        }
                        SerialUsbConnectResult.PermissionPending -> Unit
                        SerialUsbConnectResult.SerialReady -> startPeerHandshakeAwait()
                    }
                }
            }
        }
    }

    init {
        registerPermissionReceiver()
    }

    /**
     * [HexFrameBuffer.stop] shuts down its single-thread executor and cannot be restarted.
     * After [performDisconnect] we null the reference; each new USB session gets a fresh buffer.
     */
    private fun prepareHexFrameBufferForConnection() {
        if (hexFrameBuffer == null) {
            hexFrameBuffer = HexFrameBuffer(
                scope = scope,
                onFrameReceived = { frame ->
                    dataReceiver?.invoke(frame)
                }
            )
        } else {
            hexFrameBuffer?.clear()
        }
    }

    // ==================== AsyncServiceKernel Abstract Method Implementation ====================

    override fun getServiceType(): String = "RS232 Serial USB (Hex)"

    override fun getExpectedProtocolType(): String = "rs232 hex protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.SerialProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        // Fail fast when no USB device is physically attached, so we don't register receivers or
        // start polling for a device that isn't there (avoids phantom UART activity / blocked switch).
        if (usbManager.deviceList.isEmpty()) {
            LogUtil.w(TAG, "performConnect aborted: no USB device attached")
            notifyConnectionError("No USB device attached", InnerErrorCode.E251)
            return
        }

        // After disconnect, kernel may be reused; cleanup unregisters USB permission receiver — re-register each connect
        registerPermissionReceiver()

        val serialProtocol = parseResult as ProtocolParseResult.SerialProtocol
        applySerialProtocolParams(serialProtocol)

        LogUtil.d(TAG, "Connecting to RS232 serial device (Hex mode):")
        LogUtil.d(TAG, "  baudRate=$baudRate, dataBits=$dataBits")
        LogUtil.d(TAG, "  parity=${serialProtocol.parity}, stopBits=${serialProtocol.stopBits}")
        LogUtil.d(TAG, "  targetDevice=$targetDeviceName")

        scope.launch {
            try {
                val ok = runSerialConnectLoop()
                if (!ok) {
                    notifyConnectionError("Failed to connect to serial device", InnerErrorCode.E232)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect serial device: ${e.message}")
                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.E232)
            }
        }
    }

    /**
     * Parses [ProtocolParseResult.SerialProtocol] into this kernel's baud/parity/stop bits and optional device filter.
     */
    private fun applySerialProtocolParams(serialProtocol: ProtocolParseResult.SerialProtocol) {
        baudRate = serialProtocol.baudRate
        dataBits = serialProtocol.dataBits
        parity = parseParityFromString(serialProtocol.parity)
        stopBits = parseStopBitsFromInt(serialProtocol.stopBits)
        targetDeviceName = extractDeviceName(serialProtocol.toString())
        lastSerialParityLabel = serialProtocol.parity
        lastSerialStopBitsProtocol = serialProtocol.stopBits
    }

    /**
     * One attempt to open USB serial and notify success; returns false without notifying on failure.
     * TaPro [TaproRS232ServiceKernel] uses this inside [runSerialConnectLoop] to poll until a cable is plugged in.
     */
    protected suspend fun connectSerialSingleAttempt(): Boolean {
        return try {
            prepareHexFrameBufferForConnection()
            clearHandshakeStreamBuffer()
            when (connectToSerialDevice()) {
                SerialUsbConnectResult.Failed -> false
                SerialUsbConnectResult.PermissionPending -> true
                SerialUsbConnectResult.SerialReady -> {
                    startPeerHandshakeAwait()
                    true
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "connectSerialSingleAttempt: ${e.message}")
            false
        }
    }

    /**
     * Default: single [connectSerialSingleAttempt] (no internal retry loop; caller triggers [performConnect]).
     * TaPro `TaproRS232ServiceKernel` (app `lib_taplink_service`) overrides to poll until the USB serial is ready.
     */
    protected open suspend fun runSerialConnectLoop(): Boolean {
        return connectSerialSingleAttempt()
    }

    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!isSerialReady()) {
            LogUtil.e(TAG, "Serial device not ready")
            callback?.onError(InnerErrorCode.E255.code, InnerErrorCode.E255.description)
            return
        }

        // sendData already holds sendMutex; do not nest withSendLock (Mutex is not reentrant).
        try {
            val originalString = String(data)
            LogUtil.d(TAG, "Serial sending original data: $originalString (${data.size} bytes)")

            val frameData = HexFrameProtocol.encode(data)
            LogUtil.d(TAG, "Serial sending hex frame: ${String(frameData)} (${frameData.size} bytes)")

            usbSerialPort?.write(frameData, 2000)
            LogUtil.d(TAG, "Serial hex data sent successfully: ${frameData.size} bytes")
        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to send serial hex data: ${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)

            // Send failure may indicate connection lost — must notify ConnectionCallback so SDK layer runs DISCONNECTED/ERROR flow
            notifySerialLinkLost("Serial send failed: ${e.message ?: "IOException"}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Unexpected error sending serial hex data: ${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
        }
    }

    override fun performDisconnect() {
        LogUtil.d(TAG, "=== Serial Hex Disconnect Started ===")

        try {
            stopPeerHandshakeAwait()
            stopSerialLinkWatchdog()
            stopRs232HandshakeKeepalive()
            peerHandshakeEverSeen = false
            lastPeerHandshakeActivityMillis = 0L
            clearHandshakeStreamBuffer()

            // Cancel permission timeout task
            permissionTimeoutJob?.cancel()
            permissionTimeoutJob = null

            // Stop reads before tearing down the hex buffer so late chunks are not dropped as "buffer stopped"
            usbIoManager?.setListener(null)
            usbIoManager?.stop()
            usbIoManager = null

            hexFrameBuffer?.stop()
            hexFrameBuffer = null

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
     * Connect to serial port device (USB open only; peer handshake is [startPeerHandshakeAwait]).
     */
    private suspend fun connectToSerialDevice(): SerialUsbConnectResult {
        usbPermission = UsbPermission.Unknown
        return continueConnection()
    }

    /**
     * Continue connection process.
     * Do not reset [usbPermission] here — [permissionReceiver] calls this after the user grants/denies.
     */
    private suspend fun continueConnection(): SerialUsbConnectResult {
        return try {
            if (usbPermission == UsbPermission.Denied) {
                LogUtil.e(TAG, "USB permission denied")
                return SerialUsbConnectResult.Failed
            }

            val device = findUsbDevice()
            if (device == null) {
                LogUtil.e(TAG, "No suitable USB serial device found")
                return SerialUsbConnectResult.Failed
            }

            val driver = getUsbSerialDriver(device)
            if (driver == null) {
                LogUtil.e(TAG, "No driver found for device: ${device.deviceName}")
                return SerialUsbConnectResult.Failed
            }

            if (driver.ports.isEmpty()) {
                LogUtil.e(TAG, "Device has no available ports")
                return SerialUsbConnectResult.Failed
            }

            val connection = openUsbConnection(device)
            if (connection == null) {
                if (usbPermission == UsbPermission.Unknown) {
                    requestUsbPermission(device)
                    return SerialUsbConnectResult.PermissionPending
                }
                return SerialUsbConnectResult.Failed
            }

            if (establishSerialConnection(driver, connection)) SerialUsbConnectResult.SerialReady
            else SerialUsbConnectResult.Failed
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to serial device: ${e.message}")
            SerialUsbConnectResult.Failed
        }
    }

    /**
     * After USB serial is open: **actively** send [VspHandshake.REQ] immediately, then on each
     * [RS232_HSK_REQ_INTERVAL_MS] until peer REQ/ACK or application payload is seen ([completePeerHandshake]), or until
     * [VspHandshake.DEFAULT_TIMEOUT_MS] elapses ([failPeerHandshakeAwait]).
     *
     * After [notifyConnectionSuccess], [startRs232HandshakeKeepalive] continues REQ/ACK liveness;
     * [serialHandshakeSilenceJob] treats no peer handshake or payload activity for [RS232_HSK_PEER_SILENCE_MS] as disconnect.
     */
    private fun startPeerHandshakeAwait() {
        if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTING) {
            LogUtil.w(TAG, "startPeerHandshakeAwait ignored: status=$currentInnerConnectionStatus")
            return
        }
        stopPeerHandshakeAwait()
        awaitingPeerHandshake = true
        peerHandshakeEverSeen = false
        lastPeerHandshakeActivityMillis = System.currentTimeMillis()

        peerHandshakeAwaitSendJob = scope.launch {
            try {
                var first = true
                while (isActive && awaitingPeerHandshake) {
                    try {
                        withSendLock {
                            val port = usbSerialPort ?: return@withSendLock
                            port.write(VspHandshake.REQ.toByteArray(Charsets.UTF_8), 2000)
                        }
                        if (first) {
                            first = false
                            LogUtil.d(TAG, "Peer handshake: first REQ sent immediately after serial open (active probe)")
                        }
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Peer handshake REQ send failed: ${e.message}")
                        failPeerHandshakeAwait("Peer handshake REQ failed: ${e.message ?: "IOException"}")
                        break
                    }
                    if (!awaitingPeerHandshake) break
                    delay(RS232_HSK_REQ_INTERVAL_MS)
                }
            } catch (_: CancellationException) {
                LogUtil.d(TAG, "Peer handshake await send job cancelled")
            }
        }

        peerHandshakeAwaitTimeoutJob = scope.launch {
            try {
                delay(VspHandshake.DEFAULT_TIMEOUT_MS)
                if (awaitingPeerHandshake) {
                    LogUtil.e(TAG, "Peer handshake timed out after ${VspHandshake.DEFAULT_TIMEOUT_MS}ms")
                    failPeerHandshakeAwait(
                        "Peer handshake timeout (${VspHandshake.DEFAULT_TIMEOUT_MS}ms) — no REQ/ACK from peer"
                    )
                }
            } catch (_: CancellationException) {
                LogUtil.d(TAG, "Peer handshake await timeout cancelled")
            }
        }

        LogUtil.d(
            TAG,
            "Waiting for peer VSP handshake (timeout=${VspHandshake.DEFAULT_TIMEOUT_MS}ms) before reporting CONNECTED"
        )
    }

    private fun stopPeerHandshakeAwait() {
        awaitingPeerHandshake = false
        peerHandshakeAwaitSendJob?.cancel()
        peerHandshakeAwaitSendJob = null
        peerHandshakeAwaitTimeoutJob?.cancel()
        peerHandshakeAwaitTimeoutJob = null
    }

    /**
     * First peer handshake seen — complete SDK "connected" and start post-connect jobs.
     */
    private fun completePeerHandshake() {
        if (!awaitingPeerHandshake) return
        stopPeerHandshakeAwait()

        notifyConnectionSuccess(
            mapOf(
                "baudRate" to baudRate.toString(),
                "dataBits" to dataBits.toString(),
                "parity" to lastSerialParityLabel,
                "stopBits" to lastSerialStopBitsProtocol.toString(),
                "device" to (getConnectedDeviceName()),
                "portNum" to portNum.toString(),
                "mode" to "hex"
            )
        )
        startSerialLinkWatchdog()
        startRs232HandshakeKeepalive()
        onPostSerialConnected()
        LogUtil.d(TAG, "Peer handshake OK — CONNECTED reported to SDK")
    }

    private fun failPeerHandshakeAwait(message: String) {
        if (!awaitingPeerHandshake) return
        stopPeerHandshakeAwait()
        performDisconnect()
        notifyConnectionError(message, InnerErrorCode.E232)
    }

    private fun canSendHandshakeAck(): Boolean {
        return when (currentInnerConnectionStatus) {
            InnerConnectionStatus.CONNECTED -> true
            InnerConnectionStatus.CONNECTING -> awaitingPeerHandshake
            else -> false
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
            usbManager.requestPermission(device, createUsbPermissionPendingIntent(device))

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
                LogUtil.d(TAG, "RS232 raw chunk (${data.size} bytes)")
                feedIncomingWithHandshakeSeparation(data)
            }
        }

        override fun onRunError(exception: Exception?) {
            LogUtil.e(TAG, "Serial hex IO error (read/thread): ${exception?.message}")
            LogUtil.e(TAG, "SerialInputOutputManager.onRunError — USB detach or serial close often surfaces here")
            exception?.printStackTrace()

            notifySerialLinkLost("Serial IO error: ${exception?.message ?: "onRunError"}")
        }
    }

    /**
     * USB unplug / read thread exit / send IOException: propagate to [ConnectionCallback.onDisconnected]
     * so ConnectionManager can treat as connection error (fail pending, ERROR/DISCONNECTED), not only kernel ERROR state.
     */
    private fun notifySerialLinkLost(
        detail: String,
        errorCode: String = InnerErrorCode.E232.code
    ) {
        if (awaitingPeerHandshake) {
            failPeerHandshakeAwait(detail)
            return
        }
        if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED) {
            return
        }
        stopSerialLinkWatchdog()
        stopRs232HandshakeKeepalive()
        LogUtil.w(TAG, "Serial link lost: $detail")
        val cb = currentConnectionCallback
        if (cb != null) {
            handleConnectionError(detail, cb, errorCode)
        } else {
            updateStatus(InnerConnectionStatus.ERROR)
        }
    }

    private fun startSerialLinkWatchdog() {
        stopSerialLinkWatchdog()
        serialLinkWatchdogJob = scope.launch {
            LogUtil.d(
                TAG,
                "Serial link watchdog started (interval=${SERIAL_LINK_CHECK_INTERVAL_MS}ms) — USB host presence check"
            )
            try {
                while (isActive) {
                    delay(SERIAL_LINK_CHECK_INTERVAL_MS)
                    if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED) break
                    if (!isUsbSerialDeviceStillAttached()) {
                        LogUtil.w(TAG, "Serial watchdog: USB serial device no longer listed by UsbManager (likely detached)")
                        notifySerialLinkLost(
                            "USB serial device detached (link watchdog)",
                            InnerErrorCode.E213.code
                        )
                        break
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                LogUtil.d(TAG, "Serial link watchdog cancelled")
            }
        }
    }

    private fun stopSerialLinkWatchdog() {
        serialLinkWatchdogJob?.cancel()
        serialLinkWatchdogJob = null
    }

    /** True if the connected [UsbDevice] still appears in the current USB device list. */
    private fun isUsbSerialDeviceStillAttached(): Boolean {
        val driver = usbSerialDriver ?: return false
        val device = driver.device
        return usbManager.deviceList.values.any { attached ->
            attached.deviceName == device.deviceName &&
                attached.vendorId == device.vendorId &&
                attached.productId == device.productId
        }
    }

    /**
     * Subclasses may override (e.g. extra logging). RS232 handshake keepalive is started in the base class.
     */
    protected open fun onPostSerialConnected() {
        // default no-op
    }

    /**
     * Master switch: when false, neither periodic REQ nor peer-silence watch is started.
     * USB watchdog + IO errors still apply.
     *
     * For finer control use [enableRs232HandshakePeriodicReqSend] and
     * [enableRs232HandshakePeerSilenceWatch] (both honored only when this is true).
     */
    protected open fun enableRs232HandshakeKeepalive(): Boolean = true

    /**
     * While CONNECTED, periodically send [VspHandshake.REQ] ([RS232_HSK_REQ_INTERVAL_MS]).
     * TaPro can return false and rely on [enableRs232HandshakePeerSilenceWatch] if Taplink keeps
     * sending REQ (and TaPro keeps replying ACK) so inbound handshake traffic continues.
     */
    protected open fun enableRs232HandshakePeriodicReqSend(): Boolean = true

    /**
     * While CONNECTED, if we have seen peer activity (handshake REQ/ACK or application payload
     * forwarded to the hex frame buffer) and none arrives for [RS232_HSK_PEER_SILENCE_MS],
     * treat the link as lost.
     */
    protected open fun enableRs232HandshakePeerSilenceWatch(): Boolean = true

    private fun clearHandshakeStreamBuffer() {
        synchronized(handshakeStreamLock) {
            handshakeStreamBuf.setLength(0)
        }
    }

    /**
     * Strip [VspHandshake] markers before hex framing; reply ACK to peer REQ (same contract as VSP client kernel).
     * Non-marker bytes forwarded to [HexFrameBuffer] count as peer application activity for handshake completion
     * and silence refresh (same as receiving REQ/ACK).
     */
    private fun feedIncomingWithHandshakeSeparation(chunk: ByteArray) {
        synchronized(handshakeStreamLock) {
            handshakeStreamBuf.append(String(chunk, Charsets.UTF_8))
            val req = VspHandshake.REQ
            val ack = VspHandshake.ACK
            val maxMarkerLen = maxOf(req.length, ack.length)
            val keepSuffix = maxMarkerLen - 1
            var guard = 0
            while (guard++ < 4096) {
                val s = handshakeStreamBuf.toString()
                val iReq = s.indexOf(req)
                val iAck = s.indexOf(ack)
                val nextReq = if (iReq >= 0) iReq else Int.MAX_VALUE
                val nextAck = if (iAck >= 0) iAck else Int.MAX_VALUE
                val i0 = minOf(nextReq, nextAck)
                if (i0 == Int.MAX_VALUE) {
                    if (s.length > keepSuffix) {
                        val flushLen = s.length - keepSuffix
                        addHexBufferDataRecordingPeerActivity(s.substring(0, flushLen).toByteArray(Charsets.UTF_8))
                        handshakeStreamBuf.setLength(0)
                        handshakeStreamBuf.append(s.substring(flushLen))
                    }
                    break
                }
                val marker = if (nextReq <= nextAck) req else ack
                if (i0 > 0) {
                    addHexBufferDataRecordingPeerActivity(s.substring(0, i0).toByteArray(Charsets.UTF_8))
                }
                onRemoteHandshakeMarker(marker)
                val rest = i0 + marker.length
                handshakeStreamBuf.setLength(0)
                handshakeStreamBuf.append(s.substring(rest))
            }
        }
    }

    /**
     * Forwards stripped payload to the hex decoder and treats non-empty payload as successful peer liveness:
     * completes pre-CONNECT handshake if needed, and refreshes [lastPeerHandshakeActivityMillis] while CONNECTED.
     */
    private fun addHexBufferDataRecordingPeerActivity(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        hexFrameBuffer?.addData(bytes)
        onPeerApplicationPayloadSeen()
    }

    private fun onPeerApplicationPayloadSeen() {
        peerHandshakeEverSeen = true
        lastPeerHandshakeActivityMillis = System.currentTimeMillis()
        if (awaitingPeerHandshake) {
            LogUtil.d(TAG, "Peer handshake: application payload received — treating as handshake success")
            completePeerHandshake()
        }
    }

    private fun onRemoteHandshakeMarker(marker: String) {
        peerHandshakeEverSeen = true
        lastPeerHandshakeActivityMillis = System.currentTimeMillis()
        if (marker == VspHandshake.REQ) {
            scheduleHandshakeAckSend()
        }
        if (awaitingPeerHandshake) {
            completePeerHandshake()
        }
    }

    private fun scheduleHandshakeAckSend() {
        scope.launch {
            try {
                withSendLock {
                    if (!canSendHandshakeAck()) return@withSendLock
                    val port = usbSerialPort ?: return@withSendLock
                    val ack = VspHandshake.ACK.toByteArray(Charsets.UTF_8)
                    port.write(ack, 1000)
                    LogUtil.d(TAG, "RS232 handshake ACK sent (peer REQ)")
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "RS232 handshake ACK failed: ${e.message}")
                notifySerialLinkLost("RS232 handshake ACK failed: ${e.message}")
            }
        }
    }

    private fun startRs232HandshakeKeepalive() {
        stopRs232HandshakeKeepalive()
        if (!enableRs232HandshakeKeepalive()) {
            LogUtil.d(TAG, "RS232 handshake keepalive disabled by subclass")
            return
        }
        val sendReq = enableRs232HandshakePeriodicReqSend()
        val watchSilence = enableRs232HandshakePeerSilenceWatch()
        if (!sendReq && !watchSilence) {
            LogUtil.d(TAG, "RS232 handshake: periodic REQ and peer silence watch both disabled, nothing to start")
            return
        }

        if (sendReq) {
            serialHandshakeSendJob = scope.launch {
                try {
                    while (isActive && currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        delay(RS232_HSK_REQ_INTERVAL_MS)
                        if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED) break
                        try {
                            withSendLock {
                                val port = usbSerialPort ?: return@withSendLock
                                port.write(VspHandshake.REQ.toByteArray(Charsets.UTF_8), 2000)
                            }
                        } catch (e: Exception) {
                            LogUtil.e(TAG, "RS232 handshake REQ send failed: ${e.message}")
                            notifySerialLinkLost("RS232 handshake REQ failed: ${e.message}")
                            break
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    LogUtil.d(TAG, "RS232 handshake send job cancelled")
                }
            }
        }

        if (watchSilence) {
            serialHandshakeSilenceJob = scope.launch {
                try {
                    while (isActive && currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        delay(RS232_HSK_SILENCE_CHECK_MS)
                        if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED) break
                        if (!peerHandshakeEverSeen) continue
                        val silent =
                            System.currentTimeMillis() - lastPeerHandshakeActivityMillis > RS232_HSK_PEER_SILENCE_MS
                        if (silent) {
                            LogUtil.w(TAG, "RS232 peer silent (no handshake or payload) for ${RS232_HSK_PEER_SILENCE_MS}ms")
                            notifySerialLinkLost(
                                "RS232 peer silent (no REQ/ACK or application data — far end may be unplugged)",
                                InnerErrorCode.E213.code
                            )
                            break
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    LogUtil.d(TAG, "RS232 handshake silence job cancelled")
                }
            }
        }
    }

    private fun stopRs232HandshakeKeepalive() {
        serialHandshakeSendJob?.cancel()
        serialHandshakeSendJob = null
        serialHandshakeSilenceJob?.cancel()
        serialHandshakeSilenceJob = null
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

    /** Action must match [permissionAction] and [IntentFilter] in [registerPermissionReceiver]. */
    private fun createUsbPermissionPendingIntent(device: UsbDevice): PendingIntent {
        val requestCode = device.deviceName.hashCode() and 0x7FFFFFFF
        val intent = Intent(permissionAction).apply {
            setPackage(context.packageName)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
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