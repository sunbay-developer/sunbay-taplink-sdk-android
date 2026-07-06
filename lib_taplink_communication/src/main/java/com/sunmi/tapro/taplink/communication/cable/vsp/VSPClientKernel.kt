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

    /**
     * 接收数据缓冲区：USB CDC-ACM 分包到达时暂存不完整的 JSON，
     * 由 [extractNextJsonObject] 按大括号深度提取完整对象后再交给 [dataReceiver]。
     * 仅在 dataReceiveJob 协程内访问，无并发争用。
     */
    private val receiveBuffer = StringBuilder()

    /** 缓冲区上限：超限清空，防止异常数据导致内存持续增长 */
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

            // 清空接收缓冲，避免重连后读到上次的残留数据
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
     * 判断 USB 设备是否为商米 VSP 设备。
     * 识别依据：存在 interfaceClass=10 且名称为 "CDC ACM Data" 的接口。
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
     * 在已连接的 USB 设备中找到第一个商米 VSP 设备（按接口名识别，不依赖 VID/PID）。
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
     * 构建自定义探针。
     *
     * 根本原因：商米 VSP 设备 VID/PID 不在默认 ProbeTable 中，
     * [UsbSerialProber.getDefaultProber] 的 findAllDrivers 始终返回空列表。
     *
     * 修复策略：
     * - 注册已知商米 VID/PID 作为兜底（使用标准 CdcAcmSerialDriver）
     * - 对运行时检测到的设备注册 [VSPSerialDriver]：
     *   该驱动按接口名 "CDC ACM Data" 选择正确的数据接口，
     *   标准 CdcAcmSerialDriver 按顺序选接口，在多接口设备上会选错导致无法收发数据
     */
    private fun buildCustomProber(targetDevice: UsbDevice? = null): UsbSerialProber {
        val table = ProbeTable()
        // 已知商米 VSP VID/PID 兜底
        table.addProduct(0x16d0, 0x087e, CdcAcmSerialDriver::class.java)
        table.addProduct(0x067b, 0x23c3, CdcAcmSerialDriver::class.java)
        // 运行时设备使用 VSPSerialDriver，确保选到正确的 "CDC ACM Data" 接口
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
     * 连接到 VSP 设备。
     *
     * 修复：原实现用 getDefaultProber() 找不到商米设备。
     * 改为按接口名枚举设备，再用自定义探针（含 VSPSerialDriver）打开正确接口。
     */
    private suspend fun connectToVspDevice(): Boolean {
        return try {
            val vspDevice = findVspUsbDevice()
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
                // 等待权限广播，由 continueConnection() 继续后续流程
                return false
            }

            continueConnectionInternal(targetDriver)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to VSP device: ${e.message}")
            false
        }
    }

    /**
     * 权限授予后继续建立连接。
     *
     * 修复：原实现同样使用 getDefaultProber()，此处同样改为自定义探针。
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

            LogUtil.d(TAG, "VSP client connected to: ${usbDevice.deviceName}")
            LogUtil.d(TAG, "Serial parameters: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to establish VSP connection: ${e.message}")
            false
        }
    }

    /**
     * 获取当前可用的 VSP 设备列表（使用自定义探针，避免默认探针遗漏商米设备）。
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
     * 握手：服务端发 [VspHandshake.REQ]，客户端回 [VspHandshake.ACK]。
     * 非握手数据追加到 [receiveBuffer]，由 [extractNextJsonObject] 按大括号深度
     * 提取完整 JSON 后再交给 [dataReceiver]，解决 USB CDC-ACM 分包导致的 JSON 残缺问题。
     */
    private fun startDataReceive() {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch {
            receiveBuffer.clear()
            val buffer = ByteArray(16384)

            LogUtil.d(TAG, "Starting VSP client data receive loop...")
            
            while (isActive && isVspReceiveLoopActive()) {
                try {
                    val bytesRead = usbSerialPort?.read(buffer, 1000) ?: 0

                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        val dataString = String(data, Charsets.UTF_8)
                        LogUtil.d(TAG, "VSP client raw received: ${data.size} bytes")

                        if (VspHandshake.isHandshakeMessage(dataString)) {
                            if (VspHandshake.containsReq(dataString)) {
                                if (sendHandshakeAck()) {
                                    signalHandshakeSuccess()
                                }
                            } else if (VspHandshake.containsAck(dataString)) {
                                signalHandshakeSuccess()
                            }
                            // 握手包可能携带后续 payload（对端合并发送）
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
     * 将新到字节追加到 [receiveBuffer]，触发 JSON 对象提取。
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
     * 按大括号深度从 [receiveBuffer] 提取完整 JSON 对象，分发给 [dataReceiver]，
     * 然后递归处理剩余内容（应对单次 read 包含多条消息的情况）。
     *
     * 与 Tapro VSPServiceKernel.unwrapVspFramedUartPayload 算法一致：
     * '{' 深度+1，'}' 深度-1，归零即为完整 JSON。
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
            // 丢弃 '{' 之前的非 JSON 前缀（如 UART 帧头字节）
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