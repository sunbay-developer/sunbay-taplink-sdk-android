package com.sunmi.tapro.taplink.communication.cable.aoa.host

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.HexFrameBuffer
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.cable.vsp.VspHandshake
import com.sunmi.tapro.taplink.communication.util.InnerUtil
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch

/**
 * Simplified AOA Host Kernel
 *
 * Core improvements:
 * 1. Remove session/token mechanism, replace with simple state machine
 * 2. Single connection entry point, all trigger paths enter through connect()
 * 3. Idempotent operations, repeated calls to connect() are safe
 * 4. State is truth, state variables directly reflect resource state
 *
 * @author TaPro Team
 * @since 2025-01-18
 */
class CableAoaHostKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private companion object {
        const val BULK_OUT_RETRY_DELAY_MS = 50L
        const val AOA_SWITCH_TIMEOUT_MS = 8000L
    }

    private val TAG = "SimplifiedAoaHostKernel"

    // ============ State Definition ============

    /**
     * Connection state enumeration
     *
     * State transition rules:
     * IDLE -> REQUESTING_PERMISSION -> SWITCHING_AOA -> CONNECTING -> CONNECTED
     *   |                                                                |
     *   +----------------------------------------------------------------+
     *                            (disconnect)
     */
    private enum class ConnectionState {
        IDLE,                    // Idle, not connected
        REQUESTING_PERMISSION,   // Requesting permission
        SWITCHING_AOA,          // Sending AOA protocol, waiting for device switch
        CONNECTING,             // Establishing connection (open device, claim interface)
        CONNECTED,              // Connected
        DISCONNECTING,          // Disconnecting
        ERROR                   // Error state
    }

    // Current state (single source of truth)
    @Volatile
    private var state: ConnectionState = ConnectionState.IDLE

    // State lock (protects state transitions)
    private val stateLock = Any()

    // USB Manager
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * [AsyncServiceKernel.disconnect] calls [kotlinx.coroutines.Job.cancelChildren] on [scope] first.
     * A [scope.launch] that invokes [disconnect] is itself a child of [scope] and can be cancelled
     * before [performDisconnect] completes. Use this scope for disconnect from receive/send paths.
     */
    private val deferredDisconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ============ Connection Resources ============

    @Volatile
    private var currentDevice: UsbDevice? = null

    @Volatile
    private var connection: UsbDeviceConnection? = null

    @Volatile
    private var interface_: UsbInterface? = null

    @Volatile
    private var endpointIn: UsbEndpoint? = null

    @Volatile
    private var endpointOut: UsbEndpoint? = null

    // Receive coroutine
    private var receiveJob: Job? = null

    // HexFrame buffer
    private var hexFrameBuffer: HexFrameBuffer? = null

    // Current connection callback
    @Volatile
    private var currentCallback: ConnectionCallback? = null

    // ============ Permission Wait Mechanism ============

    @Volatile
    private var permissionLatch: CountDownLatch? = null

    @Volatile
    private var permissionDenied: Boolean = false

    @Volatile
    private var pendingPermissionDevice: UsbDevice? = null

    @Volatile
    private var aoaDeviceLatch: CountDownLatch? = null

    @Volatile
    private var originalDevice: UsbDevice? = null

    // ============ Public API ============

    override fun getServiceType(): String = "SimplifiedAoaHost"

    override fun getTag(): String = TAG

    override fun getExpectedProtocolType(): String = "USB Protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.UsbProtocol
    }

    /**
     * Connect to device (single connection entry point)
     *
     * Idempotent operation:
     * - If already connected, return success directly
     * - If connecting, ignore duplicate calls
     * - If idle, start connection
     */
    override fun performConnect(
        parseResult: ProtocolParseResult,
        connectionCallback: ConnectionCallback
    ) {
        LogUtil.i(TAG, "=== Connect requested, current state: $state ===")

        synchronized(stateLock) {
            when (state) {
                ConnectionState.CONNECTED -> {
                    LogUtil.i(TAG, "Already connected, returning success")
                    handleConnectionSuccess(connectionCallback, null)
                    return
                }

                ConnectionState.REQUESTING_PERMISSION,
                ConnectionState.SWITCHING_AOA,
                ConnectionState.CONNECTING -> {
                    LogUtil.i(TAG, "Connection already in progress, state=$state, ignoring")
                    return
                }

                ConnectionState.DISCONNECTING -> {
                    LogUtil.i(TAG, "Currently disconnecting, will wait and retry")
                }

                ConnectionState.ERROR -> {
                    LogUtil.i(TAG, "In error state, resetting to idle")
                    resetToIdleUnsafe()
                }

                ConnectionState.IDLE -> {
                    LogUtil.i(TAG, "Idle, starting connection")
                }
            }
        }

        // Save callback
        currentCallback = connectionCallback

        // Start connection flow (execute in coroutine)
        scope.launch {
            // If disconnecting, wait for completion
            waitForState(ConnectionState.IDLE, timeout = 5000)

            startConnection(connectionCallback)
        }
    }


    /**
     * Disconnect (single disconnect entry point)
     */
    override fun performDisconnect() {
        LogUtil.i(TAG, "=== Disconnect requested, current state: $state ===")

        synchronized(stateLock) {
            when (state) {
                ConnectionState.IDLE -> {
                    LogUtil.i(TAG, "Already disconnected")
                    return
                }

                ConnectionState.DISCONNECTING -> {
                    LogUtil.i(TAG, "Already disconnecting, ignoring")
                    return
                }

                else -> {
                    LogUtil.i(TAG, "Starting disconnect from state: $state")
                    state = ConnectionState.DISCONNECTING
                }
            }
        }

        // Clean up resources (not executed in lock to avoid blocking)
        cleanupResources()

        synchronized(stateLock) {
            state = ConnectionState.IDLE
            updateStatus(InnerConnectionStatus.DISCONNECTED)
        }

        // Notify application layer of disconnection
        notifyConnectionDisconnected(
            InnerErrorCode.E212.code,
            "Device disconnected"
        )

        LogUtil.i(TAG, "=== Disconnect completed ===")
    }

    private fun scheduleKernelDisconnect(reason: String) {
        LogUtil.w(TAG, "Scheduling kernel disconnect: $reason")
        deferredDisconnectScope.launch {
            disconnect()
        }
    }

    /**
     * Send data
     *
     * Note: Parent class AsyncServiceKernel already uses sendMutex for locking, no need to add lock here
     */
    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        // Quick state check
        if (state != ConnectionState.CONNECTED) {
            LogUtil.w(TAG, "Send data failed: not connected, state=$state")
            callback?.onError(InnerErrorCode.E304.code, "Not connected")
            return
        }

        // Get snapshot of connection and endpoint
        val conn = connection
        val epOut = endpointOut

        if (conn == null || epOut == null) {
            LogUtil.w(TAG, "Send data failed: connection or endpoint is null")
            callback?.onError(InnerErrorCode.E304.code, "Connection not available")
            return
        }

        // Check state again (may have disconnected while waiting for parent class lock)
        if (state != ConnectionState.CONNECTED) {
            LogUtil.w(TAG, "Send data failed: disconnected while waiting for lock")
            callback?.onError(InnerErrorCode.E304.code, "Connection lost")
            return
        }

        try {
            val hexStr = InnerUtil.bytes2HexStr(data)
            val textStr = String(data, Charsets.UTF_8)
            LogUtil.d(TAG, "📤 [Host] Sending ${data.size} bytes")
            LogUtil.d(TAG, "  Hex: $hexStr")
            LogUtil.d(TAG, "  Text: $textStr")

            var result = conn.bulkTransfer(epOut, data, data.size, 3000)
            if (result < 0) {
                delay(BULK_OUT_RETRY_DELAY_MS)
                if (state == ConnectionState.CONNECTED && connection === conn && endpointOut === epOut) {
                    result = conn.bulkTransfer(epOut, data, data.size, 3000)
                }
            }
            if (result < 0) {
                LogUtil.e(
                    TAG,
                    "❌ Bulk OUT failed after retry ($result); peer may have closed link while USB still enumerated"
                )
                callback?.onError(InnerErrorCode.E304.code, "Bulk transfer failed")
                // Tapro app disconnect often leaves the USB device visible; receive loop only sees IN timeouts.
                scheduleKernelDisconnect("AOA bulk OUT failure")
            } else {
                LogUtil.d(TAG, "✅ Data sent successfully: $result bytes")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Send data exception: ${e.message}")
            e.printStackTrace()
            callback?.onError(InnerErrorCode.E304.code, e.message ?: "Send failed")
        }
    }


    /**
     * Release resources
     */
    fun release() {
        LogUtil.i(TAG, "=== Release started ===")

        deferredDisconnectScope.cancel()
        // Cancel all coroutines
        scope.cancel()

        // Clean up resources
        cleanupResources()

        // Unregister broadcast receivers
        try {
            context.unregisterReceiver(permissionReceiver)
            LogUtil.d(TAG, "Permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to unregister permission receiver: ${e.message}")
        }

        try {
            context.unregisterReceiver(deviceReceiver)
            LogUtil.d(TAG, "Device receiver unregistered")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to unregister device receiver: ${e.message}")
        }

        synchronized(stateLock) {
            state = ConnectionState.IDLE
        }

        LogUtil.i(TAG, "=== Release completed ===")
    }

    // ============ Internal Implementation ============

    /**
     * Start connection flow
     */
    private suspend fun startConnection(callback: ConnectionCallback) {
        try {
            updateStatus(InnerConnectionStatus.CONNECTING)

            // 1. Find device
            val device = findTargetDevice()
            if (device == null) {
                handleConnectionError("No USB device found", callback, InnerErrorCode.E212.code)
                return
            }

            LogUtil.i(TAG, "Found device: ${getDeviceDisplayKey(device)}")

            // 2. Check if already in AOA mode
            if (isAoaDevice(device)) {
                LogUtil.i(TAG, "Device already in AOA mode, connecting directly")
                connectToAoaDevice(device, callback)
            } else {
                LogUtil.i(TAG, "Device not in AOA mode, need to switch")
                switchToAoaMode(device, callback)
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Connection failed: ${e.message}")
            handleConnectionError("Connection failed: ${e.message}", callback, InnerErrorCode.E212.code)
        }
    }


    /**
     * Switch device to AOA mode
     */
    private suspend fun switchToAoaMode(device: UsbDevice, callback: ConnectionCallback) {
        try {
            // 1. Request permission (if needed)
            if (!usbManager.hasPermission(device)) {
                synchronized(stateLock) {
                    state = ConnectionState.REQUESTING_PERMISSION
                }
                LogUtil.i(TAG, "Requesting permission for device")
                requestPermissionAndWait(device)
                LogUtil.i(TAG, "Permission granted")
            }

            // 2. Save original device reference
            originalDevice = device

            // 3. Send AOA protocol
            synchronized(stateLock) {
                state = ConnectionState.SWITCHING_AOA
            }
            LogUtil.i(TAG, "Sending AOA protocol")
            sendAoaProtocol(device)
            LogUtil.i(TAG, "AOA protocol sent, waiting for device to switch")

            // 4. Wait for AOA device to appear
            val aoaDevice = waitForAoaDevice(timeout = AOA_SWITCH_TIMEOUT_MS)
            if (aoaDevice == null) {
                handleConnectionError(
                    "AOA device not found after switch (timeout ${AOA_SWITCH_TIMEOUT_MS / 1000}s), will try next protocol",
                    callback,
                    InnerErrorCode.E212.code
                )
                return
            }

            LogUtil.i(TAG, "AOA device appeared: ${getDeviceDisplayKey(aoaDevice)}")

            // 5. Connect to AOA device
            connectToAoaDevice(aoaDevice, callback)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to switch to AOA mode: ${e.message}")
            handleConnectionError("Failed to switch to AOA mode: ${e.message}", callback, InnerErrorCode.E212.code)
        }
    }

    /**
     * Connect to AOA device
     */
    private suspend fun connectToAoaDevice(device: UsbDevice, callback: ConnectionCallback) {
        synchronized(stateLock) {
            // Idempotent check: if already connected, return directly
            if (state == ConnectionState.CONNECTED) {
                LogUtil.i(TAG, "Already connected in connectToAoaDevice")
                handleConnectionSuccess(callback, null)
                return
            }

            state = ConnectionState.CONNECTING
        }

        try {
            LogUtil.i(TAG, "Connecting to AOA device: ${getDeviceDisplayKey(device)}")

            // 1. Request permission (if needed)
            if (!usbManager.hasPermission(device)) {
                LogUtil.i(TAG, "Requesting permission for AOA device")
                requestPermissionAndWait(device)
                LogUtil.i(TAG, "AOA device permission granted")
            }

            // 2. Open device
            val conn = usbManager.openDevice(device)
                ?: throw Exception("Failed to open AOA device")

            LogUtil.d(TAG, "Device opened successfully")

            // 3. Find interface and endpoints
            val interfaceData = findAoaInterface(device)
            if (interfaceData == null) {
                conn.close()
                throw Exception("No AOA interface found")
            }

            val (iface, epIn, epOut) = interfaceData
            LogUtil.d(TAG, "Found AOA interface with endpoints")

            // 4. Claim interface
            if (!conn.claimInterface(iface, true)) {
                conn.close()
                throw Exception("Failed to claim interface")
            }

            LogUtil.d(TAG, "Interface claimed successfully")


            // 5. Print endpoint information (for debugging)
            LogUtil.i(TAG, "=== Endpoint Information ===")
            LogUtil.i(TAG, "Endpoint IN:")
            if (epIn != null) {
                LogUtil.i(TAG, "  Address: 0x${Integer.toHexString(epIn.address.toInt() and 0xFF)}")
                LogUtil.i(TAG, "  Direction: ${epIn.direction} (USB_DIR_IN=${UsbConstants.USB_DIR_IN})")
                LogUtil.i(TAG, "  Max Packet Size: ${epIn.maxPacketSize}")
            } else {
                LogUtil.i(TAG, "  (null - optional)")
            }
            LogUtil.i(TAG, "Endpoint OUT:")
            LogUtil.i(TAG, "  Address: 0x${Integer.toHexString(epOut.address.toInt() and 0xFF)}")
            LogUtil.i(TAG, "  Direction: ${epOut.direction} (USB_DIR_OUT=0)")
            LogUtil.i(TAG, "  Max Packet Size: ${epOut.maxPacketSize}")
            LogUtil.i(TAG, "===========================")

            // 6. Flush endpoint buffer (important! prevent reading old data)
            // Note: Only flush IN endpoint (receive endpoint), don't flush OUT endpoint (send endpoint)
            if (epIn != null) {
                flushEndpoint(conn, epIn)
            }

            // 6b. Application-layer handshake (same REQ/ACK contract as VSP / RS232 Host)
            // Ensures the peer is actually serving Taplink on this bulk link before reporting CONNECTED.
            if (epIn == null) {
                LogUtil.w(TAG, "AOA: no IN endpoint — cannot run Taplink handshake")
                conn.releaseInterface(iface)
                conn.close()
                handleConnectionError(
                    "AOA device has no IN endpoint for handshake",
                    callback,
                    InnerErrorCode.E212.code
                )
                return
            }
            if (!performTaplinkAppHandshake(conn, epIn, epOut)) {
                LogUtil.w(TAG, "AOA Taplink handshake failed — peer may be VSP-only or not ready")
                try {
                    conn.releaseInterface(iface)
                } catch (e: Exception) {
                    LogUtil.w(TAG, "releaseInterface after handshake fail: ${e.message}")
                }
                try {
                    conn.close()
                } catch (e: Exception) {
                    LogUtil.w(TAG, "close after handshake fail: ${e.message}")
                }
                handleConnectionError(
                    "AOA Taplink handshake failed — peer not responding on AOA bulk (try VSP/RS232)",
                    callback,
                    InnerErrorCode.E212.code
                )
                return
            }

            // 7. Save resources
            synchronized(stateLock) {
                currentDevice = device
                connection = conn
                interface_ = iface
                endpointIn = epIn
                endpointOut = epOut
                state = ConnectionState.CONNECTED
                updateStatus(InnerConnectionStatus.CONNECTED)
            }

            LogUtil.i(TAG, "Connection resources saved, state=CONNECTED")

            // 8. Initialize HexFrame buffer
            hexFrameBuffer = HexFrameBuffer(
                scope = scope,
                onFrameReceived = { frame ->
                    dataReceiver?.invoke(frame)
                }
            )

            // 9. Start receiving
            startReceive()

            // 10. Callback success
            LogUtil.i(TAG, "=== Connection established successfully ===")
            handleConnectionSuccess(callback, null)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to AOA device: ${e.message}")
            cleanupResources()
            handleConnectionError("Failed to connect: ${e.message}", callback, InnerErrorCode.E212.code)
        }
    }

    /**
     * Flush USB endpoint buffer
     * 
     * Important: prevent reading old data left from previous connection
     */
    private fun flushEndpoint(connection: UsbDeviceConnection, endpoint: UsbEndpoint) {
        try {
            val buffer = ByteArray(endpoint.maxPacketSize)
            var flushedBytes = 0
            var iterations = 0
            val maxIterations = 10  // Maximum 10 attempts
            
            LogUtil.d(TAG, "Flushing endpoint 0x${Integer.toHexString(endpoint.address.toInt() and 0xFF)}...")
            
            // Continue reading until no data or max iterations reached
            while (iterations < maxIterations) {
                val bytesRead = connection.bulkTransfer(endpoint, buffer, buffer.size, 100)  // 100ms timeout
                if (bytesRead > 0) {
                    flushedBytes += bytesRead
                    iterations++
                } else {
                    break
                }
            }
            
            if (flushedBytes > 0) {
                LogUtil.i(TAG, "Flushed $flushedBytes bytes from endpoint 0x${Integer.toHexString(endpoint.address.toInt() and 0xFF)} in $iterations iterations")
            } else {
                LogUtil.d(TAG, "Endpoint 0x${Integer.toHexString(endpoint.address.toInt() and 0xFF)} is clean (no data to flush)")
            }
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to flush endpoint: ${e.message}")
        }
    }

    /**
     * After USB bulk endpoints are ready, verify the peer is serving Taplink on AOA (same markers as
     * [VspHandshake] used by VSP / RS232). Mirrors [SerialServiceKernel] active REQ probe + peer ACK/REQ.
     *
     * @return true if ACK seen, or peer REQ answered with ACK (mutual completion).
     */
    private suspend fun performTaplinkAppHandshake(
        conn: UsbDeviceConnection,
        epIn: UsbEndpoint,
        epOut: UsbEndpoint
    ): Boolean {
        val reqBytes = VspHandshake.REQ.toByteArray(Charsets.UTF_8)
        val ackMarker = VspHandshake.ACK
        val reqMarker = VspHandshake.REQ
        val buffer = ByteArray(epIn.maxPacketSize.coerceAtLeast(64))
        val acc = StringBuilder(256)

        LogUtil.i(
            TAG,
            "AOA Taplink handshake starting (timeout=${VspHandshake.DEFAULT_TIMEOUT_MS}ms, interval=${VspHandshake.DEFAULT_INTERVAL_MS}ms)"
        )

        val deadline = System.currentTimeMillis() + VspHandshake.DEFAULT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            var written = conn.bulkTransfer(epOut, reqBytes, reqBytes.size, 3000)
            if (written < 0) {
                LogUtil.w(TAG, "AOA handshake: bulk OUT failed ($written), retrying")
                delay(50)
                continue
            }

            val intervalEnd = System.currentTimeMillis() + VspHandshake.DEFAULT_INTERVAL_MS
            while (System.currentTimeMillis() < intervalEnd && System.currentTimeMillis() < deadline) {
                val n = conn.bulkTransfer(epIn, buffer, buffer.size, 150)
                if (n > 0) {
                    acc.append(String(buffer.copyOf(n), Charsets.UTF_8))
                    val s = acc.toString()
                    when {
                        s.contains(ackMarker) -> {
                            LogUtil.i(TAG, "AOA handshake: received ACK")
                            return true
                        }
                        s.contains(reqMarker) -> {
                            val ackBytes = ackMarker.toByteArray(Charsets.UTF_8)
                            val w2 = conn.bulkTransfer(epOut, ackBytes, ackBytes.size, 3000)
                            LogUtil.i(TAG, "AOA handshake: peer REQ seen, sent ACK (w=$w2)")
                            return true
                        }
                    }
                    if (acc.length > 4096) {
                        acc.setLength(0)
                    }
                }
                delay(VspHandshake.POLL_INTERVAL_MS)
            }
        }

        LogUtil.w(TAG, "AOA handshake: timeout waiting for ACK / peer REQ")
        return false
    }

    /**
     * Clean up resources
     */
    private fun cleanupResources() {
        LogUtil.d(TAG, "Cleaning up resources...")

        // 1. Clear references first (prevent new operations from using old resources)
        val oldConnection = connection
        val oldInterface = interface_
        val oldReceiveJob = receiveJob
        
        // Clear references immediately
        currentDevice = null
        connection = null
        interface_ = null
        endpointIn = null
        endpointOut = null
        originalDevice = null

        // 2. Stop receive coroutine (use local reference)
        oldReceiveJob?.cancel()
        receiveJob = null
        
        // Wait for receive coroutine to fully stop (max 500ms)
        val startTime = System.currentTimeMillis()
        while (oldReceiveJob?.isActive == true && System.currentTimeMillis() - startTime < 500) {
            Thread.sleep(10)
        }
        
        if (oldReceiveJob?.isActive == true) {
            LogUtil.w(TAG, "Receive job still active after 500ms, forcing cleanup")
        } else {
            LogUtil.d(TAG, "Receive job stopped successfully")
        }

        // 3. Stop HexFrame buffer
        hexFrameBuffer?.stop()
        hexFrameBuffer = null

        // 4. Release interface (use local reference)
        oldInterface?.let { iface ->
            try {
                oldConnection?.releaseInterface(iface)
                LogUtil.d(TAG, "Interface released")
            } catch (e: Exception) {
                LogUtil.w(TAG, "Failed to release interface: ${e.message}")
            }
        }

        // 5. Close connection (use local reference)
        oldConnection?.let { conn ->
            try {
                conn.close()
                LogUtil.d(TAG, "Connection closed")
            } catch (e: Exception) {
                LogUtil.w(TAG, "Failed to close connection: ${e.message}")
            }
        }

        // 6. Clean up common resources (callbacks, data receivers, etc.)
        // Note: This will clear dataReceiver, if need to keep dataReceiver, should re-register when reconnecting
        // cleanupCommonResources()

        LogUtil.d(TAG, "Resources cleaned up")
    }


    /**
     * Reset to idle state (non-locked version, caller must hold lock)
     */
    private fun resetToIdleUnsafe() {
        cleanupResources()
        state = ConnectionState.IDLE
        updateStatus(InnerConnectionStatus.DISCONNECTED)
    }


    /**
     * Wait for state to become specified value
     */
    private suspend fun waitForState(targetState: ConnectionState, timeout: Long) {
        val startTime = System.currentTimeMillis()
        while (state != targetState) {
            if (System.currentTimeMillis() - startTime > timeout) {
                LogUtil.w(TAG, "Timeout waiting for state $targetState, current=$state")
                break
            }
            delay(50)
        }
    }

    // ============ Permission Handling ============

    /**
     * Request permission and wait for result
     */
    private suspend fun requestPermissionAndWait(device: UsbDevice) {
        permissionLatch = CountDownLatch(1)
        permissionDenied = false
        pendingPermissionDevice = device

        val intent = createPermissionIntent(device)
        usbManager.requestPermission(device, intent)

        LogUtil.i(TAG, "Permission request sent, waiting for response...")

        // Wait for permission response (max 30 seconds)
        val granted = withTimeoutOrNull(30000) {
            permissionLatch?.await()
            !permissionDenied
        }

        pendingPermissionDevice = null

        if (granted != true) {
            throw Exception("Permission denied or timeout")
        }

        LogUtil.i(TAG, "Permission granted")
    }

    /**
     * Create permission request Intent
     */
    private fun createPermissionIntent(device: UsbDevice): PendingIntent {
        val requestCode = device.deviceName.hashCode() and 0x7FFFFFFF
        val intent = Intent("android.hardware.usb.action.USB_PERMISSION").apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }


    // ============ AOA Protocol Handling ============

    /**
     * Send AOA protocol
     */
    private fun sendAoaProtocol(device: UsbDevice) {
        val conn = usbManager.openDevice(device)
            ?: throw Exception("Failed to open device for AOA protocol")

        try {
            val aoaDeviceInfo = UsbStandardProtocol.createDefaultAoaDeviceInfo()

            if (!UsbStandardProtocol.sendAoaProtocolSequence(conn, aoaDeviceInfo)) {
                throw Exception("Failed to send AOA protocol sequence")
            }

            LogUtil.d(TAG, "AOA protocol sequence sent successfully")
        } finally {
            conn.close()
        }
    }

    /**
     * Wait for AOA device to appear
     */
    private suspend fun waitForAoaDevice(timeout: Long): UsbDevice? {
        aoaDeviceLatch = CountDownLatch(1)

        // Check if already exists first
        val existing = findAoaDevice()
        if (existing != null) {
            LogUtil.i(TAG, "AOA device already exists")
            return existing
        }

        LogUtil.i(TAG, "Waiting for AOA device to appear (timeout: ${timeout}ms)...")

        // Wait for device to appear. runInterruptible makes blocking await() respond to coroutine cancellation (timeout).
        val appeared = withTimeoutOrNull(timeout) {
            runInterruptible(Dispatchers.IO) {
                aoaDeviceLatch?.await()
            }
            true
        }

        if (appeared != true) {
            LogUtil.w(TAG, "Timeout waiting for AOA device (${timeout}ms) - device did not switch to AOA mode, will try next protocol")
            return null
        }

        return findAoaDevice()
    }

    // ============ Device Search ============

    /**
     * Find target device
     */
    private fun findTargetDevice(): UsbDevice? {
        val devices = usbManager.deviceList

        if (devices.isEmpty()) {
            LogUtil.w(TAG, "No USB devices found")
            return null
        }

        LogUtil.d(TAG, "Found ${devices.size} USB device(s)")
        devices.values.forEach { device ->
            LogUtil.d(TAG, "  - ${getDeviceDisplayKey(device)}")
        }

        // Prefer AOA device
        val aoaDevice = findAoaDevice()
        if (aoaDevice != null) {
            return aoaDevice
        }

        // Prefer Sunmi device, otherwise return first device
        return devices.values.firstOrNull { "Sunmi".equals(it.manufacturerName, ignoreCase = true) }
            ?: devices.values.firstOrNull()
    }

    /**
     * Find AOA device
     */
    private fun findAoaDevice(): UsbDevice? {
        return usbManager.deviceList.values.find { isAoaDevice(it) }
    }

    /**
     * Determine if device is AOA device
     */
    private fun isAoaDevice(device: UsbDevice): Boolean {
        return UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
    }


    /**
     * Find AOA interface and endpoints
     */
    private fun findAoaInterface(device: UsbDevice): Triple<UsbInterface, UsbEndpoint?, UsbEndpoint>? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)

            // Find AOA standard interface (class=255, subclass=66, protocol=1)
            if (iface.interfaceClass == 255 &&
                iface.interfaceSubclass == 66 &&
                iface.interfaceProtocol == 1 &&
                iface.endpointCount >= 2
            ) {

                val endpoints = findBulkEndpoints(iface)
                if (endpoints != null) {
                    LogUtil.d(TAG, "Found AOA interface[$i] with standard descriptor")
                    return Triple(iface, endpoints.first, endpoints.second)
                }
            }

            // Alternative: Vendor specific interface
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                iface.endpointCount >= 2
            ) {

                val endpoints = findBulkEndpoints(iface)
                if (endpoints != null) {
                    LogUtil.d(TAG, "Found AOA interface[$i] with vendor specific descriptor")
                    return Triple(iface, endpoints.first, endpoints.second)
                }
            }
        }

        return null
    }

    /**
     * Find Bulk endpoints
     */
    private fun findBulkEndpoints(iface: UsbInterface): Pair<UsbEndpoint?, UsbEndpoint>? {
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null

        for (i in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                    epIn = endpoint
                } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                    epOut = endpoint
                }
            }
        }

        // OUT endpoint is required, IN endpoint is optional
        return if (epOut != null) {
            Pair(epIn, epOut)
        } else {
            null
        }
    }

    // ============ Data Receiving ============

    /**
     * Start data receiving
     */
    private fun startReceive() {
        // Cancel old receive coroutine
        val oldJob = receiveJob
        receiveJob = null
        oldJob?.cancel()

        // Get snapshot of current connection resources (before starting new coroutine)
        val currentConn = connection
        val currentEpIn = endpointIn
        val currentDev = currentDevice
        
        // Validate resource validity
        if (currentConn == null || currentEpIn == null || currentDev == null) {
            LogUtil.w(TAG, "Cannot start receive: resources not ready")
            return
        }
        
        // Generate connection ID (for validating if resources are expired)
        val connectionId = System.currentTimeMillis()
        LogUtil.i(TAG, "📡 Starting receive loop with connectionId=$connectionId")

        receiveJob = scope.launch(Dispatchers.IO) {  // Use IO dispatcher
            val buffer = ByteArray(16384)

            LogUtil.i(TAG, "📡 Data receive loop started (Host mode) on thread: ${Thread.currentThread().name}")
            LogUtil.i(TAG, "📡 Connection: ${currentConn != null}, EndpointIn: ${currentEpIn != null}, State: $state")
            LogUtil.i(TAG, "📡 Device: ${getDeviceDisplayKey(currentDev)}, ConnectionId: $connectionId")

            var loopCount = 0
            
            while (isActive && state == ConnectionState.CONNECTED) {
                loopCount++
                
                // Validate resources are still valid on each loop (prevent using expired resources)
                if (connection != currentConn || endpointIn != currentEpIn || currentDevice != currentDev) {
                    LogUtil.w(TAG, "⚠️ Connection resources changed, stopping stale receive loop (connectionId=$connectionId)")
                    break
                }

                try {
                    // Print heartbeat log every 100 loops (reduce log volume)
                    if (loopCount % 100 == 1) {
                        LogUtil.d(TAG, "💓 Receive loop heartbeat #$loopCount (connectionId=$connectionId)")
                    }

                    val bytesRead = currentConn.bulkTransfer(currentEpIn, buffer, buffer.size, 500)

                    when {
                        bytesRead > 0 -> {
                            // Successfully received data
                            val data = buffer.copyOf(bytesRead)
                            val hexStr = InnerUtil.bytes2HexStr(data)
                            val textStr = try {
                                String(data, Charsets.UTF_8)
                            } catch (e: Exception) {
                                "[non-UTF8 data]"
                            }

                            LogUtil.i(TAG, "📥 [Host] Received $bytesRead bytes (loop #$loopCount, connectionId=$connectionId)")
                            LogUtil.d(TAG, "  Hex: $hexStr")
                            LogUtil.d(TAG, "  Text: $textStr")

                            // Directly call dataReceiver, not through HexFrameBuffer (for testing plain text data)
                            dataReceiver?.invoke(data)

                            LogUtil.d(TAG, "✅ Data delivered to receiver")
                        }

                        bytesRead < 0 -> {
                            // Transfer error or timeout
                            // Note: bulkTransfer timeout also returns -1, not necessarily a real error
                            
                            // Check if device is really disconnected
                            if (!isDeviceStillConnected()) {
                                LogUtil.w(TAG, "❌ Device disconnected, stopping receive loop")
                                scheduleKernelDisconnect("USB device removed (IN bulk timeout)")
                                break
                            }
                            
                            // Device still exists, may just be timeout or temporarily no data
                            // Continue receiving, don't print log (avoid too many logs)
                            delay(10)
                        }

                        else -> {
                            // bytesRead == 0: timeout, no data (theoretically shouldn't reach here, because timeout usually returns -1)
                            delay(10)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive && state == ConnectionState.CONNECTED) {
                        LogUtil.e(TAG, "❌ Receive error: ${e.message} (connectionId=$connectionId)")
                        e.printStackTrace()

                        // Check if exception is caused by device disconnection
                        if (!isDeviceStillConnected()) {
                            LogUtil.w(TAG, "❌ Device disconnected detected in exception handler, triggering disconnect")
                            scheduleKernelDisconnect("USB device removed (receive exception)")
                            break
                        }

                        // Device still exists, may be temporary exception, continue receiving
                        delay(100)
                    } else {
                        break
                    }
                }
            }

            LogUtil.i(TAG, "📡 Data receive loop stopped (Host mode) after $loopCount iterations (connectionId=$connectionId)")
        }
    }


    /**
     * Check if device is still connected
     */
    private fun isDeviceStillConnected(): Boolean {
        val device = currentDevice ?: return false
        return try {
            val deviceKey = getDeviceKey(device)
            usbManager.deviceList.values.any { getDeviceKey(it) == deviceKey }
        } catch (e: Exception) {
            false
        }
    }

    // ============ Broadcast Receivers ============

    /**
     * Permission broadcast receiver
     */
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("android.hardware.usb.action.USB_PERMISSION" != intent.action) {
                return
            }

            val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            LogUtil.i(TAG, "Permission broadcast: device=${device?.deviceName}, granted=$granted")

            // Check if it's the device we requested
            if (device != null && isSameDevice(device, pendingPermissionDevice)) {
                permissionDenied = !granted
                permissionLatch?.countDown()
            }
        }
    }

    /**
     * Device attach/detach broadcast receiver
     */
    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        LogUtil.i(TAG, "Device attached: ${getDeviceDisplayKey(device)}")

                        // If it's an AOA device and we're waiting, wake up the waiter
                        if (isAoaDevice(device) && state == ConnectionState.SWITCHING_AOA) {
                            LogUtil.i(TAG, "AOA device appeared, notifying waiter")
                            aoaDeviceLatch?.countDown()
                        }
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (device != null) {
                        LogUtil.i(TAG, "Device detached: ${getDeviceDisplayKey(device)}")

                        // If it's the currently connected device, trigger disconnect
                        if (isSameDevice(device, currentDevice) && state == ConnectionState.CONNECTED) {
                            LogUtil.w(TAG, "Current device detached, disconnecting")
                            scheduleKernelDisconnect("ACTION_USB_DEVICE_DETACHED")
                        }
                    }
                }
            }
        }
    }

    // ============ Helper Methods ============

    /**
     * Get stable identifier key for device
     */
    private fun getDeviceKey(device: UsbDevice): String {
        return "${device.deviceName}_${device.vendorId}_${device.productId}_${device.deviceId}"
    }

    /**
     * Get display identifier for device
     */
    private fun getDeviceDisplayKey(device: UsbDevice): String {
        return "${device.deviceName} (VID=${String.format("0x%04X", device.vendorId)}, PID=${
            String.format(
                "0x%04X",
                device.productId
            )
        },${device.manufacturerName})"
    }

    /**
     * Compare if two devices are the same
     */
    private fun isSameDevice(device1: UsbDevice?, device2: UsbDevice?): Boolean {
        if (device1 == null || device2 == null) {
            return device1 == device2
        }
        return getDeviceKey(device1) == getDeviceKey(device2)
    }

    // ============ Initialization ============

    init {
        // Register permission broadcast receiver
        val permissionFilter = IntentFilter().apply {
            addAction("android.hardware.usb.action.USB_PERMISSION")
        }
        try {
            ContextCompat.registerReceiver(
                context,
                permissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            LogUtil.i(TAG, "Permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register permission receiver: ${e.message}")
        }

        // Register device attach/detach broadcast receiver
        val deviceFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                deviceReceiver,
                deviceFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            LogUtil.i(TAG, "Device receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register device receiver: ${e.message}")
        }

        LogUtil.i(TAG, "SimplifiedAoaHostKernel initialized")
    }
}
