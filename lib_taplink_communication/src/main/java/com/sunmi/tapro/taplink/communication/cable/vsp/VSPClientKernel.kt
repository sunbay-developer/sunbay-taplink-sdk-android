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
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.text.Charsets
import kotlin.time.Duration.Companion.milliseconds

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

    /**
     * True once this connection attempt has written its first [VspHandshake.REQ] to the port.
     *
     * The receive loop uses it to reject handshake frames that cannot be a reply to us — i.e.
     * bytes left over in the link FIFO by a previous session. Reset per connection attempt in
     * [awaitVspHandshake] and cleared in [performDisconnect].
     */
    private val hasSentHandshakeReq = AtomicBoolean(false)

    /**
     * Set while an active liveness probe ([checkLinkAlive]) is in flight. Completed by the receive
     * loop as soon as *any* inbound bytes arrive from the server, proving the peer is responsive.
     */
    @Volatile
    private var livenessWaiter: CompletableDeferred<Boolean>? = null

    // VSP configuration parameters
    private var baudRate: Int = 115200
    private var dataBits: Int = 8
    private var parity: Int = UsbSerialPort.PARITY_NONE
    private var stopBits: Int = UsbSerialPort.STOPBITS_1
    private var targetDeviceName: String? = null

    /**
     * Receive data buffer: temporarily stores incomplete JSON when USB CDC-ACM receives fragmented packets.
     * Complete JSON objects are extracted by [extractNextJsonObject] based on brace depth before passing to [dataReceiver].
     * It is only accessed within the dataReceiveJob coroutine, with no concurrent contention.
     */
    private val receiveBuffer = StringBuilder()

    /** Buffer size limit: clear when exceeded to prevent memory growth from abnormal data. */
    private val MAX_RECEIVE_BUFFER_SIZE = 512 * 1024 // 512 KB

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
        // Fail fast when no USB device is physically attached. Without this gate the kernel would
        // register receivers and start polling for a device that will never appear, producing
        // phantom UART activity and blocking transport switch (e.g. Cable -> LAN).
        if (usbManager.deviceList.isEmpty()) {
            LogUtil.w(TAG, "performConnect aborted: no USB device attached")
            notifyConnectionError("No USB device attached", InnerErrorCode.E251)
            return
        }

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

    /**
     * Active VSP liveness probe.
     *
     * A successful [usbSerialPort]?.write only proves the local USB port accepted the bytes, not
     * that TaPro (the server) is still processing them — if its UART receive loop is stuck
     * ("port timeout"), transactions would silently hang until the full request timeout. To fail
     * fast, we send a lightweight handshake REQ and wait for TaPro to reply (it answers REQ with an
     * ACK even during the data phase). Any inbound bytes complete [livenessWaiter].
     */
    override suspend fun checkLinkAlive(timeoutMs: Long): Boolean {
        if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED || usbSerialPort == null) {
            LogUtil.w(TAG, "checkLinkAlive: not connected (status=$currentInnerConnectionStatus)")
            return false
        }

        val waiter = CompletableDeferred<Boolean>()
        livenessWaiter = waiter
        return try {
            val sent = withSendLock {
                try {
                    usbSerialPort?.write(VspHandshake.REQ.toByteArray(Charsets.UTF_8), 1000)
                    true
                } catch (e: Exception) {
                    LogUtil.e(TAG, "checkLinkAlive: failed to send probe: ${e.message}")
                    false
                }
            }
            if (!sent) {
                return false
            }
            val alive = withTimeoutOrNull(timeoutMs) { waiter.await() } ?: false
            if (!alive) {
                LogUtil.w(TAG, "checkLinkAlive: no response within ${timeoutMs}ms, link presumed dead")
                // Tear down the dead link so upstream reconnect logic can re-establish it, instead
                // of every subsequent transaction paying the full probe timeout again.
                handleConnectionError()
            }
            alive
        } finally {
            if (livenessWaiter === waiter) {
                livenessWaiter = null
            }
        }
    }

    override fun performDisconnect() {
        LogUtil.d(TAG, "=== VSP Client Disconnect Started ===")

        handshakeWaiter?.cancel(CancellationException("VSP client disconnect"))
        handshakeWaiter = null
        hasSentHandshakeReq.set(false)

        livenessWaiter?.complete(false)
        livenessWaiter = null

        try {
            dataReceiveJob?.cancel()
            dataReceiveJob = null

            // Clear receive buffer to avoid reading residual data from the previous connection after reconnecting
            receiveBuffer.clear()

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
     * Determine if the USB device is a Sunmi VSP device.
     * Identification basis: interface with interfaceClass=10 and name "CDC ACM Data".
     */
    private fun isVspDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == 10 &&
                "CDC ACM Data".equals(iface.name, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Find the first Sunmi VSP device from connected USB devices (identify by interface name, not VID/PID).
     */
    private fun findVspUsbDevice(): UsbDevice? {
        for (device in usbManager.deviceList.values) {
            LogUtil.d(
                TAG, "USB device: ${device.deviceName} " +
                    "VID=0x${"%04X".format(device.vendorId)} " +
                    "PID=0x${"%04X".format(device.productId)} " +
                    "manufacturer=${device.manufacturerName}"
            )
            if (isVspDevice(device)) {
                LogUtil.i(TAG, "VSP device found: ${device.deviceName}")
                return device
            }
        }
        return null
    }

    /**
     * Find the VSP USB device, waiting up to [VSP_DEVICE_WAIT_TIMEOUT_MS] for it to enumerate.
     *
     * Fixes the "cannot connect" case where `connect()` is called right after the cable is plugged
     * in and the CDC-ACM interface has not finished enumerating yet. The poll is fully cancellable:
     * [delay] throws [CancellationException] when [disconnect] cancels the scope's children (manual
     * disconnect or a cable AUTO protocol switch), so it never blocks a teardown.
     */
    private suspend fun awaitVspUsbDevice(): UsbDevice? {
        findVspUsbDevice()?.let { return it }

        val deadline = System.currentTimeMillis() + VSP_DEVICE_WAIT_TIMEOUT_MS
        LogUtil.d(TAG, "VSP device not enumerated yet, waiting up to ${VSP_DEVICE_WAIT_TIMEOUT_MS}ms...")
        while (System.currentTimeMillis() < deadline) {
            delay(VSP_DEVICE_POLL_INTERVAL_MS)
            findVspUsbDevice()?.let {
                LogUtil.i(TAG, "VSP device appeared after wait: ${it.deviceName}")
                return it
            }
        }
        LogUtil.e(TAG, "No VSP device after waiting ${VSP_DEVICE_WAIT_TIMEOUT_MS}ms")
        return null
    }

    /**
     * Build a custom prober.
     *
     * Root cause: Sunmi VSP device VID/PID is not in the default ProbeTable;
     * [UsbSerialProber.getDefaultProber].findAllDrivers always returns an empty list.
     *
     * Fix strategy:
     * - Register known Sunmi VID/PID as fallback (using standard CdcAcmSerialDriver)
     * - For runtime-detected devices, register [VSPSerialDriver]:
     *   This driver selects the correct data interface by name "CDC ACM Data",
     *   while standard CdcAcmSerialDriver selects interfaces sequentially, which may fail on multi-interface devices.
     */
    private fun buildCustomProber(targetDevice: UsbDevice? = null): UsbSerialProber {
        val table = ProbeTable()
        // Known Sunmi VSP VID/PID fallback
        table.addProduct(0x16d0, 0x087e, CdcAcmSerialDriver::class.java)
        table.addProduct(0x067b, 0x23c3, CdcAcmSerialDriver::class.java)
        // Runtime devices use VSPSerialDriver to ensure selecting the correct "CDC ACM Data" interface
        if (targetDevice != null) {
            table.addProduct(targetDevice.vendorId, targetDevice.productId, VSPSerialDriver::class.java)
            LogUtil.d(
                TAG, "Custom prober: VID=0x${"%04X".format(targetDevice.vendorId)} " +
                    "PID=0x${"%04X".format(targetDevice.productId)} → VSPSerialDriver"
            )
        }
        return UsbSerialProber(table)
    }

    /**
     * Connect to the VSP device.
     *
     * Fix: Original implementation cannot find Sunmi devices using getDefaultProber().
     * Changed to enumerate devices by interface name, then open the correct interface using custom prober (with VSPSerialDriver).
     */
    private suspend fun connectToVspDevice(): Boolean {
        return try {
            val vspDevice = awaitVspUsbDevice()
            if (vspDevice == null) {
                LogUtil.e(TAG, "No VSP device found (no device with 'CDC ACM Data' interface)")
                return false
            }

            val targetDriver = buildCustomProber(vspDevice).probeDevice(vspDevice)
            if (targetDriver == null) {
                LogUtil.e(TAG, "Failed to probe VSP device: ${vspDevice.deviceName}")
                return false
            }
            LogUtil.d(TAG, "VSP driver: ${targetDriver.javaClass.simpleName}, ports=${targetDriver.ports.size}")

            if (!usbManager.hasPermission(vspDevice)) {
                LogUtil.d(TAG, "No permission for ${vspDevice.deviceName}, requesting...")
                synchronized(this@VSPClientKernel) { pendingDevice = vspDevice }
                try {
                    usbManager.requestPermission(vspDevice, createUsbPermissionPendingIntent(vspDevice))
                    LogUtil.d(TAG, "USB permission request sent for: ${vspDevice.deviceName}")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
                    synchronized(this@VSPClientKernel) { pendingDevice = null }
                    return false
                }
                // Wait for permission broadcast; continueConnection() will continue the subsequent flow
                return false
            }

            continueConnectionInternal(targetDriver)
        } catch (e: CancellationException) {
            // Disconnect / AUTO protocol switch cancelled the device wait — propagate, do not report error.
            throw e
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to VSP device: ${e.message}")
            false
        }
    }

    /**
     * Continue establishing connection after permission is granted.
     *
     * Fix: Original implementation also uses getDefaultProber(); changed to custom prober here as well.
     */
    private suspend fun continueConnection(device: UsbDevice, connectionCallback: ConnectionCallback) {
        try {
            val targetDriver = buildCustomProber(device).probeDevice(device)
            if (targetDriver == null) {
                LogUtil.e(TAG, "Driver not found for device: ${device.deviceName}")
                connectionCallback.onDisconnected("DRIVER_NOT_FOUND", "Driver not found after permission grant")
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

            // Discard bytes the peer left in the link FIFO during its previous session (typically
            // ##TAPLINK_HSK_REQ## re-sent every second by TaPro and never read). Handshake markers
            // carry no session id, so without this the receive loop would immediately read a stale
            // REQ and declare the handshake verified while the peer's port may still be closed —
            // the first business frame sent afterwards is then lost on the wire (TaPro shows
            // "connected" while the SDK reports 351 three seconds later).
            drainStaleRxBytes()

            LogUtil.d(TAG, "VSP client connected to: ${usbDevice.deviceName}")
            LogUtil.d(TAG, "Serial parameters: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to establish VSP connection: ${e.message}")
            false
        }
    }

    /**
     * Get the current list of available VSP devices (using custom prober to avoid default prober missing Sunmi devices).
     */
    fun getAvailableDevices(): List<Map<String, String>> {
        return try {
            usbManager.deviceList.values
                .filter { isVspDevice(it) }
                .map { device ->
                    val driver = buildCustomProber(device).probeDevice(device)
                    mapOf(
                        "deviceName" to device.deviceName,
                        "productName" to (device.productName ?: "Unknown"),
                        "vendorId" to "0x${"%04X".format(device.vendorId)}",
                        "productId" to "0x${"%04X".format(device.productId)}",
                        "driverClass" to (driver?.javaClass?.simpleName ?: "Unknown")
                    )
                }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to get available devices: ${e.message}")
            emptyList()
        }
    }

    /**
     * USB port is open: start the receive loop, actively drive [VspHandshake.REQ] and block until
     * the application-layer handshake completes, within [VspHandshake.DEFAULT_TIMEOUT_MS].
     *
     * The waiter is passed explicitly into [startDataReceive] so each receive-loop coroutine holds
     * a reference to its own handshake waiter (captured at launch time) rather than reading the
     * shared [handshakeWaiter] field at runtime. This prevents a stale receive loop from a
     * previous connection attempt from accidentally completing/failing the new handshake waiter
     * when its blocked [UsbSerialPort.read] throws IOException after the old port is closed.
     */
    private suspend fun awaitVspHandshake(): Boolean {
        val waiter = CompletableDeferred<Unit>()
        handshakeWaiter = waiter
        // Re-evaluate causality for every attempt; never inherit the previous connection's flag.
        hasSentHandshakeReq.set(false)
        var handshakeReqJob: Job? = null
        return try {
            startDataReceive(waiter)
            handshakeReqJob = startHandshakeRequests(waiter)
            withTimeoutOrNull(VspHandshake.DEFAULT_TIMEOUT_MS.milliseconds) {
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
            handshakeReqJob?.cancel()
            handshakeWaiter = null
            if (!waiter.isCompleted) {
                waiter.cancel(CancellationException("VSP handshake abandoned"))
            }
        }
    }

    /**
     * Actively drive the handshake instead of only waiting for the service side.
     *
     * TaPro only periodically sends [VspHandshake.REQ] in its own connect flow; when the cable is not physically disconnected
     * and TaPro is still CONNECTED (e.g., the integration app reconnects on its own), it won't send REQ again.
     * If the client only waits passively, handshake will timeout. TaPro immediately replies with ACK upon receiving REQ during the data phase,
     * so the client can periodically initiate REQ to cover both initial connection and reconnection scenarios.
     */
    private fun startHandshakeRequests(waiter: CompletableDeferred<Unit>): Job =
        scope.launch {
            while (isActive && !waiter.isCompleted) {
                sendHandshakeReq()
                delay(VspHandshake.DEFAULT_INTERVAL_MS)
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
     * Handshake: service side sends [VspHandshake.REQ], client replies [VspHandshake.ACK].
     * Non-handshake data is appended to [receiveBuffer]; [extractNextJsonObject] extracts complete JSON objects
     * based on brace depth and passes them to [dataReceiver], solving the JSON incompleteness problem caused by USB CDC-ACM fragmentation.
     *
     * @param handshakeWaiter The waiter for this specific connection attempt, captured by the
     *   coroutine closure at launch time. Using a parameter (rather than the shared field) prevents
     *   a stale receive loop from a previous connection from accidentally completing or failing the
     *   new connection's handshake waiter after its USB port is closed and an IOException fires.
     */
    private fun startDataReceive(handshakeWaiter: CompletableDeferred<Unit>?) {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch {
            receiveBuffer.clear()
            val buffer = ByteArray(16384)

            LogUtil.d(TAG, "Starting VSP client data receive loop...")
            
            while (isActive && isVspReceiveLoopActive()) {
                try {
                    val bytesRead = usbSerialPort?.read(buffer, 1000) ?: 0

                    if (bytesRead > 0) {
                        // Any inbound byte proves the server is alive; unblock a pending liveness probe.
                        livenessWaiter?.complete(true)
                        val data = buffer.copyOf(bytesRead)
                        val dataString = String(data, Charsets.UTF_8)
                        LogUtil.d(TAG, "VSP client raw received: ${data.size} bytes")

                        if (VspHandshake.isHandshakeMessage(dataString)) {
                            // Only handshake frames that arrive *after* we sent our own REQ count:
                            // that proves the peer has its port open and answered within this
                            // session, ruling out stale REQ/ACK left in the link. An inbound REQ is
                            // still always ACKed, preserving peer-initiated handshake compatibility.
                            if (VspHandshake.containsReq(dataString)) {
                                val acked = sendHandshakeAck()
                                if (acked && hasSentHandshakeReq.get()) {
                                    if (handshakeWaiter?.complete(Unit) == true) {
                                        LogUtil.i(TAG, "VSP client handshake verified with server")
                                    }
                                } else if (!hasSentHandshakeReq.get()) {
                                    LogUtil.d(TAG, "Ignoring handshake REQ received before our first REQ was sent")
                                }
                            } else if (VspHandshake.containsAck(dataString)) {
                                if (hasSentHandshakeReq.get()) {
                                    if (handshakeWaiter?.complete(Unit) == true) {
                                        LogUtil.i(TAG, "VSP client handshake verified with server")
                                    }
                                } else {
                                    LogUtil.d(TAG, "Ignoring handshake ACK received before our first REQ was sent")
                                }
                            }
                            // Handshake packet may carry subsequent payload (sent merged by the remote end)
                            val remaining = VspHandshake.stripHandshakeMarkers(dataString)
                            if (remaining != null) {
                                processIncomingPayload(remaining.toByteArray(Charsets.UTF_8))
                            }
                            continue
                        }

                        processIncomingPayload(data)
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
     * Append newly arrived bytes to [receiveBuffer] and trigger JSON object extraction.
     */
    private fun processIncomingPayload(data: ByteArray) {
        if (data.isNotEmpty()) {
            if (receiveBuffer.length + data.size > MAX_RECEIVE_BUFFER_SIZE) {
                LogUtil.w(TAG, "Receive buffer overflow (${receiveBuffer.length} chars), clearing")
                receiveBuffer.clear()
            }
            receiveBuffer.append(String(data, Charsets.UTF_8))
        }
        extractNextJsonObject()
    }

    /**
     * Extract complete JSON objects from [receiveBuffer] based on brace depth, dispatch to [dataReceiver],
     * then recursively process remaining content (to handle multiple messages in a single read).
     *
     * Algorithm matches Tapro VSPServiceKernel.unwrapVspFramedUartPayload:
     * '{' increments depth, '}' decrements depth; depth reaching zero indicates a complete JSON object.
     */
    private fun extractNextJsonObject() {
        val startIdx = receiveBuffer.indexOf('{')
        if (startIdx < 0) {
            if (receiveBuffer.isNotEmpty()) {
                LogUtil.d(TAG, "VSP buffer has no '{', clearing ${receiveBuffer.length} chars")
                receiveBuffer.clear()
            }
            return
        }
        if (startIdx > 0) {
            // Discard non-JSON prefix before '{' (e.g., UART frame header bytes)
            receiveBuffer.delete(0, startIdx)
        }

        var depth = 0
        for (i in receiveBuffer.indices) {
            when (receiveBuffer[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val jsonStr = receiveBuffer.substring(0, i + 1)
                        LogUtil.d(TAG, "VSP complete JSON extracted: ${jsonStr.length} chars")
                        dataReceiver?.invoke(jsonStr.toByteArray(Charsets.UTF_8))
                        receiveBuffer.delete(0, i + 1)
                        if (receiveBuffer.isNotEmpty()) extractNextJsonObject()
                        return
                    }
                }
            }
        }

        LogUtil.d(TAG, "VSP buffer: ${receiveBuffer.length} chars, waiting for more data")
    }

    /**
     * Discard bytes left in the link FIFO by the peer's previous session.
     *
     * The VSP handshake markers carry no session identifier, so a stale
     * [VspHandshake.REQ] is indistinguishable from a fresh one. Reading one before the peer has
     * reopened its port makes the client declare the handshake verified too early and lose the
     * first business frame it sends afterwards.
     *
     * Called once right after the port is opened and configured, before the receive loop starts.
     * Draining is safe: a live peer re-sends REQ every [VspHandshake.DEFAULT_INTERVAL_MS], and the
     * client drives its own REQ as well, so at most one round is delayed.
     */
    private fun drainStaleRxBytes() {
        val port = usbSerialPort ?: return
        val buffer = ByteArray(DRAIN_BUFFER_SIZE)
        var discarded = 0
        var rounds = 0
        try {
            while (rounds < MAX_DRAIN_ROUNDS) {
                rounds++
                val read = port.read(buffer, DRAIN_READ_TIMEOUT_MS)
                if (read <= 0) break
                discarded += read
            }
            if (rounds >= MAX_DRAIN_ROUNDS) {
                LogUtil.w(TAG, "Stale RX drain hit the round limit ($MAX_DRAIN_ROUNDS), discarded $discarded byte(s)")
            } else if (discarded > 0) {
                LogUtil.w(TAG, "Discarded $discarded stale RX byte(s) left by a previous VSP session")
            }
        } catch (e: IOException) {
            LogUtil.w(TAG, "Failed to drain stale RX bytes: ${e.message}")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Unexpected error draining stale RX bytes: ${e.message}")
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
     * Send a handshake [VspHandshake.REQ] to the service side.
     */
    private fun sendHandshakeReq() {
        try {
            val reqBytes = VspHandshake.REQ.toByteArray(Charsets.UTF_8)
            usbSerialPort?.write(reqBytes, 1000)
            // Set only after a successful write: an inbound handshake frame can be a reply to us
            // only if a REQ actually left this port.
            hasSentHandshakeReq.set(true)
            LogUtil.d(TAG, "Handshake REQ sent (client initiated)")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to send handshake REQ: ${e.message}")
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
         * Max time to wait for the VSP USB (CDC-ACM) device to appear/enumerate before giving up.
         *
         * A common "cannot connect" report is caused by calling `connect()` right after plugging the
         * cable: the Sunmi VSP CDC-ACM interface has not finished enumerating yet, so a single
         * [findVspUsbDevice] lookup returns null and the attempt fails immediately. Polling for a
         * bounded window makes the connection plug-and-play while still allowing cable AUTO mode to
         * fall through to the next protocol within an acceptable time.
         */
        private const val VSP_DEVICE_WAIT_TIMEOUT_MS = 6_000L

        /** Poll interval while waiting for the VSP USB device to enumerate. */
        private const val VSP_DEVICE_POLL_INTERVAL_MS = 500L

        /** Scratch buffer size for [drainStaleRxBytes]. */
        private const val DRAIN_BUFFER_SIZE = 4096

        /**
         * Read timeout while draining stale RX bytes. Kept short: the FIFO either already holds
         * leftover bytes or it is empty, so a long wait would only delay the handshake.
         */
        private const val DRAIN_READ_TIMEOUT_MS = 50

        /** Round limit for [drainStaleRxBytes], guarding against a peer that keeps flooding data. */
        private const val MAX_DRAIN_ROUNDS = 32

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