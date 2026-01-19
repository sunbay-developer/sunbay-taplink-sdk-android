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
import com.sunmi.tapro.taplink.communication.util.InnerUtil
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch

/**
 * 简化的 AOA Host Kernel
 * 
 * 核心改进：
 * 1. 移除 session/token 机制，用简单的状态机替代
 * 2. 单一连接入口，所有触发路径都通过 connect() 进入
 * 3. 幂等操作，重复调用 connect() 是安全的
 * 4. 状态即真相，状态变量直接反映资源状态
 * 
 * @author TaPro Team
 * @since 2025-01-18
 */
class CableAoaHostKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private val TAG = "SimplifiedAoaHostKernel"
    
    // ============ 状态定义 ============
    
    /**
     * 连接状态枚举
     * 
     * 状态转换规则：
     * IDLE -> REQUESTING_PERMISSION -> SWITCHING_AOA -> CONNECTING -> CONNECTED
     *   |                                                                |
     *   +----------------------------------------------------------------+
     *                            (disconnect)
     */
    private enum class ConnectionState {
        IDLE,                    // 空闲，未连接
        REQUESTING_PERMISSION,   // 正在请求权限
        SWITCHING_AOA,          // 正在发送 AOA 协议，等待设备切换
        CONNECTING,             // 正在建立连接（open device, claim interface）
        CONNECTED,              // 已连接
        DISCONNECTING,          // 正在断开
        ERROR                   // 错误状态
    }
    
    // 当前状态（唯一的真相来源）
    @Volatile
    private var state: ConnectionState = ConnectionState.IDLE
    
    // 状态锁（保护状态转换）
    private val stateLock = Any()
    
    // USB Manager
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    // ============ 连接资源 ============
    
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
    
    // 接收协程
    private var receiveJob: Job? = null
    
    // HexFrame 缓冲区
    private var hexFrameBuffer: HexFrameBuffer? = null
    
    // 当前连接回调
    @Volatile
    private var currentCallback: ConnectionCallback? = null
    
    // ============ 权限等待机制 ============
    
    @Volatile
    private var permissionLatch: CountDownLatch? = null
    
    @Volatile
    private var permissionDenied: Boolean = false
    
    @Volatile
    private var pendingPermissionDevice: UsbDevice? = null
    
    // ============ AOA 设备等待机制 ============
    
    @Volatile
    private var aoaDeviceLatch: CountDownLatch? = null
    
    @Volatile
    private var originalDevice: UsbDevice? = null
    
    // ============ 公共 API ============
    
    override fun getServiceType(): String = "SimplifiedAoaHost"
    
    override fun getTag(): String = TAG
    
    override fun getExpectedProtocolType(): String = "USB Protocol"
    
    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.UsbProtocol
    }
    
    /**
     * 连接到设备（唯一的连接入口）
     * 
     * 幂等操作：
     * - 如果已连接，直接返回成功
     * - 如果正在连接，忽略重复调用
     * - 如果空闲，开始连接
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
        
        // 保存回调
        currentCallback = connectionCallback
        
        // 开始连接流程（在协程中执行）
        scope.launch {
            // 如果正在断开，等待完成
            waitForState(ConnectionState.IDLE, timeout = 5000)
            
            startConnection(connectionCallback)
        }
    }

    
    /**
     * 断开连接（唯一的断开入口）
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
        
        // 清理资源（不在锁内执行，避免阻塞）
        cleanupResources()
        
        synchronized(stateLock) {
            state = ConnectionState.IDLE
            updateStatus(InnerConnectionStatus.DISCONNECTED)
        }
        
        // 通知应用层断开
        notifyConnectionDisconnected(
            InnerErrorCode.E212.code,
            "Device disconnected"
        )
        
        LogUtil.i(TAG, "=== Disconnect completed ===")
    }
    
    /**
     * 发送数据
     * 
     * 注意：父类 AsyncServiceKernel 已经使用 sendMutex 加锁，这里不需要再加锁
     */
    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        // 快速检查状态
        if (state != ConnectionState.CONNECTED) {
            LogUtil.w(TAG, "Send data failed: not connected, state=$state")
            callback?.onError(InnerErrorCode.E304.code, "Not connected")
            return
        }
        
        // 获取连接和端点的快照
        val conn = connection
        val epOut = endpointOut
        
        if (conn == null || epOut == null) {
            LogUtil.w(TAG, "Send data failed: connection or endpoint is null")
            callback?.onError(InnerErrorCode.E304.code, "Connection not available")
            return
        }
        
        // 再次检查状态（可能在等待父类锁期间断开了）
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
            
            val result = conn.bulkTransfer(epOut, data, data.size, 3000)
            if (result < 0) {
                LogUtil.e(TAG, "❌ Bulk transfer failed: $result")
                callback?.onError(InnerErrorCode.E304.code, "Bulk transfer failed")
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
     * 释放资源
     */
    fun release() {
        LogUtil.i(TAG, "=== Release started ===")
        
        // 取消所有协程
        scope.cancel()
        
        // 清理资源
        cleanupResources()
        
        // 注销广播接收器
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
    
    // ============ 内部实现 ============
    
    /**
     * 开始连接流程
     */
    private suspend fun startConnection(callback: ConnectionCallback) {
        try {
            updateStatus(InnerConnectionStatus.CONNECTING)
            
            // 1. 查找设备
            val device = findTargetDevice()
            if (device == null) {
                handleConnectionError("No USB device found", callback, InnerErrorCode.E212.code)
                return
            }
            
            LogUtil.i(TAG, "Found device: ${getDeviceDisplayKey(device)}")
            
            // 2. 检查是否已在 AOA 模式
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
     * 切换设备到 AOA 模式
     */
    private suspend fun switchToAoaMode(device: UsbDevice, callback: ConnectionCallback) {
        try {
            // 1. 请求权限（如果需要）
            if (!usbManager.hasPermission(device)) {
                synchronized(stateLock) {
                    state = ConnectionState.REQUESTING_PERMISSION
                }
                LogUtil.i(TAG, "Requesting permission for device")
                requestPermissionAndWait(device)
                LogUtil.i(TAG, "Permission granted")
            }
            
            // 2. 保存原始设备引用
            originalDevice = device
            
            // 3. 发送 AOA 协议
            synchronized(stateLock) {
                state = ConnectionState.SWITCHING_AOA
            }
            LogUtil.i(TAG, "Sending AOA protocol")
            sendAoaProtocol(device)
            LogUtil.i(TAG, "AOA protocol sent, waiting for device to switch")
            
            // 4. 等待 AOA 设备出现
            val aoaDevice = waitForAoaDevice(timeout = 15000)
            if (aoaDevice == null) {
                handleConnectionError("AOA device not found after switch (timeout 15s)", callback, InnerErrorCode.E212.code)
                return
            }
            
            LogUtil.i(TAG, "AOA device appeared: ${getDeviceDisplayKey(aoaDevice)}")
            
            // 5. 连接到 AOA 设备
            connectToAoaDevice(aoaDevice, callback)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to switch to AOA mode: ${e.message}")
            handleConnectionError("Failed to switch to AOA mode: ${e.message}", callback, InnerErrorCode.E212.code)
        }
    }
    
    /**
     * 连接到 AOA 设备
     */
    private suspend fun connectToAoaDevice(device: UsbDevice, callback: ConnectionCallback) {
        synchronized(stateLock) {
            // 幂等检查：如果已连接，直接返回
            if (state == ConnectionState.CONNECTED) {
                LogUtil.i(TAG, "Already connected in connectToAoaDevice")
                handleConnectionSuccess(callback, null)
                return
            }
            
            state = ConnectionState.CONNECTING
        }
        
        try {
            LogUtil.i(TAG, "Connecting to AOA device: ${getDeviceDisplayKey(device)}")
            
            // 1. 请求权限（如果需要）
            if (!usbManager.hasPermission(device)) {
                LogUtil.i(TAG, "Requesting permission for AOA device")
                requestPermissionAndWait(device)
                LogUtil.i(TAG, "AOA device permission granted")
            }
            
            // 2. 打开设备
            val conn = usbManager.openDevice(device) 
                ?: throw Exception("Failed to open AOA device")
            
            LogUtil.d(TAG, "Device opened successfully")
            
            // 3. 查找接口和端点
            val interfaceData = findAoaInterface(device)
            if (interfaceData == null) {
                conn.close()
                throw Exception("No AOA interface found")
            }
            
            val (iface, epIn, epOut) = interfaceData
            LogUtil.d(TAG, "Found AOA interface with endpoints")
            
            // 4. Claim 接口
            if (!conn.claimInterface(iface, true)) {
                conn.close()
                throw Exception("Failed to claim interface")
            }
            
            LogUtil.d(TAG, "Interface claimed successfully")

            
            // 5. 打印端点信息（调试用）
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
            
            // 6. 保存资源
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
            
            // 6. 初始化 HexFrame 缓冲区
            hexFrameBuffer = HexFrameBuffer(
                scope = scope,
                onFrameReceived = { frame ->
                    dataReceiver?.invoke(frame)
                }
            )
            
            // 7. 启动接收
            startReceive()
            
            // 8. 回调成功
            LogUtil.i(TAG, "=== Connection established successfully ===")
            handleConnectionSuccess(callback, null)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to AOA device: ${e.message}")
            cleanupResources()
            handleConnectionError("Failed to connect: ${e.message}", callback, InnerErrorCode.E212.code)
        }
    }
    
    /**
     * 清理资源
     */
    private fun cleanupResources() {
        LogUtil.d(TAG, "Cleaning up resources...")
        
        // 停止接收
        receiveJob?.cancel()
        receiveJob = null
        
        // 停止 HexFrame 缓冲区
        hexFrameBuffer?.stop()
        hexFrameBuffer = null
        
        // 释放接口
        interface_?.let { iface ->
            try {
                connection?.releaseInterface(iface)
                LogUtil.d(TAG, "Interface released")
            } catch (e: Exception) {
                LogUtil.w(TAG, "Failed to release interface: ${e.message}")
            }
        }
        
        // 关闭连接
        connection?.let { conn ->
            try {
                conn.close()
                LogUtil.d(TAG, "Connection closed")
            } catch (e: Exception) {
                LogUtil.w(TAG, "Failed to close connection: ${e.message}")
            }
        }
        
        // 清空引用
        currentDevice = null
        connection = null
        interface_ = null
        endpointIn = null
        endpointOut = null
        originalDevice = null
        
        LogUtil.d(TAG, "Resources cleaned up")
    }

    
    /**
     * 重置到空闲状态（不加锁版本，调用方需持有锁）
     */
    private fun resetToIdleUnsafe() {
        cleanupResources()
        state = ConnectionState.IDLE
        updateStatus(InnerConnectionStatus.DISCONNECTED)
    }
    

    
    /**
     * 等待状态变为指定值
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
    
    // ============ 权限处理 ============
    
    /**
     * 请求权限并等待结果
     */
    private suspend fun requestPermissionAndWait(device: UsbDevice) {
        permissionLatch = CountDownLatch(1)
        permissionDenied = false
        pendingPermissionDevice = device
        
        val intent = createPermissionIntent(device)
        usbManager.requestPermission(device, intent)
        
        LogUtil.i(TAG, "Permission request sent, waiting for response...")
        
        // 等待权限响应（最多 30 秒）
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
     * 创建权限请求 Intent
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

    
    // ============ AOA 协议处理 ============
    
    /**
     * 发送 AOA 协议
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
     * 等待 AOA 设备出现
     */
    private suspend fun waitForAoaDevice(timeout: Long): UsbDevice? {
        aoaDeviceLatch = CountDownLatch(1)
        
        // 先检查是否已经存在
        val existing = findAoaDevice()
        if (existing != null) {
            LogUtil.i(TAG, "AOA device already exists")
            return existing
        }
        
        LogUtil.i(TAG, "Waiting for AOA device to appear (timeout: ${timeout}ms)...")
        
        // 等待设备出现
        val appeared = withTimeoutOrNull(timeout) {
            aoaDeviceLatch?.await()
            true
        }
        
        if (appeared != true) {
            LogUtil.w(TAG, "Timeout waiting for AOA device")
            return null
        }
        
        return findAoaDevice()
    }
    
    // ============ 设备查找 ============
    
    /**
     * 查找目标设备
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
        
        // 优先查找 AOA 设备
        val aoaDevice = findAoaDevice()
        if (aoaDevice != null) {
            return aoaDevice
        }
        
        // 否则返回第一个设备
        return devices.values.firstOrNull()
    }
    
    /**
     * 查找 AOA 设备
     */
    private fun findAoaDevice(): UsbDevice? {
        return usbManager.deviceList.values.find { isAoaDevice(it) }
    }
    
    /**
     * 判断是否是 AOA 设备
     */
    private fun isAoaDevice(device: UsbDevice): Boolean {
        return UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
    }

    
    /**
     * 查找 AOA 接口和端点
     */
    private fun findAoaInterface(device: UsbDevice): Triple<UsbInterface, UsbEndpoint?, UsbEndpoint>? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            
            // 查找 AOA 标准接口 (class=255, subclass=66, protocol=1)
            if (iface.interfaceClass == 255 &&
                iface.interfaceSubclass == 66 &&
                iface.interfaceProtocol == 1 &&
                iface.endpointCount >= 2) {
                
                val endpoints = findBulkEndpoints(iface)
                if (endpoints != null) {
                    LogUtil.d(TAG, "Found AOA interface[$i] with standard descriptor")
                    return Triple(iface, endpoints.first, endpoints.second)
                }
            }
            
            // 备选：Vendor specific 接口
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                iface.endpointCount >= 2) {
                
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
     * 查找 Bulk 端点
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
        
        // OUT 端点是必须的，IN 端点可选
        return if (epOut != null) {
            Pair(epIn, epOut)
        } else {
            null
        }
    }
    
    // ============ 数据接收 ============
    
    /**
     * 启动数据接收
     */
    private fun startReceive() {
        receiveJob?.cancel()
        
        receiveJob = scope.launch(Dispatchers.IO) {  // 使用 IO 调度器
            val buffer = ByteArray(16384)
            
            LogUtil.i(TAG, "📡 Data receive loop started (Host mode) on thread: ${Thread.currentThread().name}")
            LogUtil.i(TAG, "📡 Connection: ${connection != null}, EndpointIn: ${endpointIn != null}, State: $state")
            
            var loopCount = 0
            while (isActive && state == ConnectionState.CONNECTED) {
                loopCount++
                val conn = connection
                val epIn = endpointIn
                
                if (conn == null || epIn == null) {
                    LogUtil.w(TAG, "Connection or endpoint is null, stopping receive")
                    break
                }
                
                try {
                    // 每 10 次循环打印一次心跳日志
                    if (loopCount % 10 == 1) {
                        LogUtil.d(TAG, "💓 Receive loop heartbeat #$loopCount")
                    }
                    
                    val bytesRead = conn.bulkTransfer(epIn, buffer, buffer.size, 500)
                    
                    when {
                        bytesRead > 0 -> {
                            val data = buffer.copyOf(bytesRead)
                            val hexStr = InnerUtil.bytes2HexStr(data)
                            val textStr = try {
                                String(data, Charsets.UTF_8)
                            } catch (e: Exception) {
                                "[non-UTF8 data]"
                            }
                            
                            LogUtil.i(TAG, "📥 [Host] Received $bytesRead bytes (loop #$loopCount)")
                            LogUtil.d(TAG, "  Hex: $hexStr")
                            LogUtil.d(TAG, "  Text: $textStr")
                            
                            // 直接调用 dataReceiver，不通过 HexFrameBuffer（用于测试纯文本数据）
                            dataReceiver?.invoke(data)
                            
                            LogUtil.d(TAG, "✅ Data delivered to receiver")
                        }
                        bytesRead < 0 -> {
                            // 传输错误，检查设备是否还在
                            LogUtil.w(TAG, "Bulk transfer error: $bytesRead")
                            
                            if (!isDeviceStillConnected()) {
                                LogUtil.w(TAG, "❌ Device disconnected detected, triggering disconnect")
                                // 触发断开
                                scope.launch {
                                    disconnect()
                                }
                                break
                            }
                            delay(100)
                        }
                        else -> {
                            // 超时，继续
                            delay(10)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive && state == ConnectionState.CONNECTED) {
                        LogUtil.e(TAG, "❌ Receive error: ${e.message}")
                        e.printStackTrace()
                        
                        // 检查是否是设备断开导致的异常
                        if (!isDeviceStillConnected()) {
                            LogUtil.w(TAG, "❌ Device disconnected detected in exception handler, triggering disconnect")
                            scope.launch {
                                disconnect()
                            }
                            break
                        }
                        
                        delay(100)
                    } else {
                        break
                    }
                }
            }
            
            LogUtil.i(TAG, "📡 Data receive loop stopped (Host mode) after $loopCount iterations")
        }
    }

    
    /**
     * 检查设备是否仍然连接
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
    
    // ============ 广播接收器 ============
    
    /**
     * 权限广播接收器
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
            
            // 检查是否是我们请求的设备
            if (device != null && isSameDevice(device, pendingPermissionDevice)) {
                permissionDenied = !granted
                permissionLatch?.countDown()
            }
        }
    }
    
    /**
     * 设备插拔广播接收器
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
                        
                        // 如果是 AOA 设备且正在等待，唤醒等待
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
                        
                        // 如果是当前连接的设备，触发断开
                        if (isSameDevice(device, currentDevice) && state == ConnectionState.CONNECTED) {
                            LogUtil.w(TAG, "Current device detached, disconnecting")
                            scope.launch {
                                disconnect()
                            }
                        }
                    }
                }
            }
        }
    }
    
    // ============ 辅助方法 ============
    
    /**
     * 获取设备的稳定标识键
     */
    private fun getDeviceKey(device: UsbDevice): String {
        return "${device.deviceName}_${device.vendorId}_${device.productId}_${device.deviceId}"
    }
    
    /**
     * 获取设备的显示标识
     */
    private fun getDeviceDisplayKey(device: UsbDevice): String {
        return "${device.deviceName} (VID=${String.format("0x%04X", device.vendorId)}, PID=${String.format("0x%04X", device.productId)})"
    }
    
    /**
     * 比较两个设备是否相同
     */
    private fun isSameDevice(device1: UsbDevice?, device2: UsbDevice?): Boolean {
        if (device1 == null || device2 == null) {
            return device1 == device2
        }
        return getDeviceKey(device1) == getDeviceKey(device2)
    }
    
    // ============ 初始化 ============
    
    init {
        // 注册权限广播接收器
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
        
        // 注册设备插拔广播接收器
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
