package com.sunmi.tapro.taplink.communication.cable.aoa.accessory

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.HexFrameBuffer
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.InnerUtil
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch

/**
 * 简化的 AOA Accessory Kernel
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
class CableAoaAccessoryKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private val TAG = "SimplifiedAoaAccessoryKernel"
    
    // ============ 状态定义 ============
    
    /**
     * 连接状态枚举
     */
    private enum class ConnectionState {
        IDLE,                    // 空闲，未连接
        REQUESTING_PERMISSION,   // 正在请求权限
        CONNECTING,             // 正在建立连接（open accessory）
        CONNECTED,              // 已连接
        DISCONNECTING,          // 正在断开
        ERROR                   // 错误状态
    }
    
    // 当前状态（唯一的真相来源）
    @Volatile
    private var state: ConnectionState = ConnectionState.IDLE
    
    // 状态锁（保护状态转换）
    private val stateLock = Any()
    
    // 是否应该继续等待连接（用于区分主动/被动断开）
    @Volatile
    private var shouldKeepWaiting: Boolean = false
    
    // USB Manager
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    // ============ 连接资源 ============
    
    @Volatile
    private var currentAccessory: UsbAccessory? = null
    
    @Volatile
    private var fileDescriptor: ParcelFileDescriptor? = null
    
    @Volatile
    private var inputStream: FileInputStream? = null
    
    @Volatile
    private var outputStream: FileOutputStream? = null
    
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
    private var pendingPermissionAccessory: UsbAccessory? = null
    
    // ============ 公共 API ============
    
    override fun getServiceType(): String = "SimplifiedAoaAccessory"
    
    override fun getTag(): String = TAG
    
    override fun getExpectedProtocolType(): String = "USB Protocol"
    
    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.UsbProtocol
    }
    
    /**
     * 连接到 Accessory（唯一的连接入口）
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
            
            // 设置为应该继续等待（用户主动调用 connect）
            shouldKeepWaiting = true
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
     * 
     * 注意：Accessory 模式的特殊性
     * - 如果是主动断开（用户操作），状态变为 DISCONNECTED，停止等待
     * - 如果是被动断开（Host 断开/拔线），状态变为 WAITING_CONNECT，继续等待
     * - 只有在 release() 时才真正停止
     */
    override fun performDisconnect() {
        performDisconnect(isUserInitiated = true)
    }
    
    /**
     * 断开连接（内部方法，支持区分主动/被动断开）
     * 
     * @param isUserInitiated true=用户主动断开，false=Host断开/拔线
     */
    private fun performDisconnect(isUserInitiated: Boolean) {
        LogUtil.i(TAG, "=== Disconnect requested, current state: $state, userInitiated: $isUserInitiated ===")
        
        synchronized(stateLock) {
            when (state) {
                ConnectionState.IDLE -> {
                    LogUtil.i(TAG, "Already disconnected")
                    
                    // 如果是主动断开，停止等待
                    if (isUserInitiated) {
                        shouldKeepWaiting = false
                        LogUtil.i(TAG, "User initiated disconnect, stopping wait loop")
                    }
                    return
                }
                ConnectionState.DISCONNECTING -> {
                    LogUtil.i(TAG, "Already disconnecting, ignoring")
                    return
                }
                else -> {
                    LogUtil.i(TAG, "Starting disconnect from state: $state")
                    state = ConnectionState.DISCONNECTING
                    
                    // 如果是主动断开，停止等待
                    if (isUserInitiated) {
                        shouldKeepWaiting = false
                        LogUtil.i(TAG, "User initiated disconnect, stopping wait loop")
                    }
                }
            }
        }
        
        // 清理资源（不在锁内执行，避免阻塞）
        cleanupResources()
        
        synchronized(stateLock) {
            state = ConnectionState.IDLE
            
            if (isUserInitiated) {
                // 用户主动断开：状态变为 DISCONNECTED，停止等待
                updateStatus(InnerConnectionStatus.DISCONNECTED)
                LogUtil.i(TAG, "User initiated disconnect, status set to DISCONNECTED")
            } else {
                // Host 断开/拔线：状态变为 WAITING_CONNECT，继续等待（如果 shouldKeepWaiting=true）
                if (shouldKeepWaiting) {
                    updateStatus(InnerConnectionStatus.WAITING_CONNECT)
                    LogUtil.i(TAG, "Host disconnected, status set to WAITING_CONNECT, will continue waiting")
                    
                    // 重新启动等待流程
                    val callback = currentCallback
                    if (callback != null) {
                        scope.launch {
                            startConnection(callback)
                        }
                    }
                } else {
                    updateStatus(InnerConnectionStatus.DISCONNECTED)
                    LogUtil.i(TAG, "Host disconnected but shouldKeepWaiting=false, status set to DISCONNECTED")
                }
            }
        }
        
        // 通知应用层断开
        val message = if (isUserInitiated) {
            "User disconnected"
        } else {
            if (shouldKeepWaiting) {
                "Host disconnected, waiting for next connection"
            } else {
                "Host disconnected"
            }
        }
        
        notifyConnectionDisconnected(
            InnerErrorCode.E212.code,
            message
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
        
        // 获取输出流的快照
        val output = outputStream
        
        if (output == null) {
            LogUtil.w(TAG, "Send data failed: output stream is null")
            callback?.onError(InnerErrorCode.E304.code, "Output stream not available")
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
            LogUtil.d(TAG, "📤 [Accessory] Sending ${data.size} bytes")
            LogUtil.d(TAG, "  Hex: $hexStr")
            LogUtil.d(TAG, "  Text: $textStr")
            
            output.write(data)
            output.flush()
            LogUtil.d(TAG, "✅ Data sent successfully: ${data.size} bytes")
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
        
        // 停止等待
        shouldKeepWaiting = false
        
        // 取消所有协程
        scope.coroutineContext.cancelChildren()
        
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
            context.unregisterReceiver(accessoryReceiver)
            LogUtil.d(TAG, "Accessory receiver unregistered")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to unregister accessory receiver: ${e.message}")
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
            // 设置为等待连接状态
            updateStatus(InnerConnectionStatus.WAITING_CONNECT)
            
            // 通知应用层正在等待连接
            callback.onWaitingConnect()
            
            // 1. 持续查找 Accessory，直到找到或被取消
            val accessory = waitForAccessory()
            if (accessory == null) {
                // 被取消或超时
                LogUtil.i(TAG, "Accessory wait cancelled or timed out")
                return
            }
            
            LogUtil.i(TAG, "Found accessory: ${getAccessoryDisplayKey(accessory)}")
            
            // 2. 请求权限（如果需要）
            if (!usbManager.hasPermission(accessory)) {
                synchronized(stateLock) {
                    state = ConnectionState.REQUESTING_PERMISSION
                }
                LogUtil.i(TAG, "Requesting permission for accessory")
                requestPermissionAndWait(accessory)
                LogUtil.i(TAG, "Permission granted")
            }
            
            // 3. 连接到 Accessory
            connectToAccessory(accessory, callback)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Connection failed: ${e.message}")
            handleConnectionError("Connection failed: ${e.message}", callback, InnerErrorCode.E212.code)
        }
    }
    
    /**
     * 等待 Accessory 出现
     * 
     * 持续查找 Accessory，直到找到或状态改变或用户主动断开
     * 
     * @return 找到的 Accessory，如果被取消则返回 null
     */
    private suspend fun waitForAccessory(): UsbAccessory? {
        val checkInterval = 500L  // 每 500ms 检查一次
        
        LogUtil.i(TAG, "Waiting for USB accessory to appear...")
        
        while (state == ConnectionState.IDLE && shouldKeepWaiting) {
            // 检查是否有 Accessory
            val accessory = findAccessory()
            if (accessory != null) {
                LogUtil.i(TAG, "Accessory found: ${getAccessoryDisplayKey(accessory)}")
                return accessory
            }
            
            // 等待一段时间后再次检查
            delay(checkInterval)
        }
        
        // 状态改变或用户主动断开，返回 null
        if (!shouldKeepWaiting) {
            LogUtil.i(TAG, "Accessory wait cancelled by user (shouldKeepWaiting=false)")
        } else {
            LogUtil.i(TAG, "Accessory wait cancelled, state changed to: $state")
        }
        return null
    }
    
    /**
     * 连接到 Accessory
     */
    private suspend fun connectToAccessory(accessory: UsbAccessory, callback: ConnectionCallback) {
        synchronized(stateLock) {
            // 幂等检查：如果已连接，直接返回
            if (state == ConnectionState.CONNECTED) {
                LogUtil.i(TAG, "Already connected in connectToAccessory")
                handleConnectionSuccess(callback, null)
                return
            }
            
            state = ConnectionState.CONNECTING
        }
        
        try {
            LogUtil.i(TAG, "Connecting to accessory: ${getAccessoryDisplayKey(accessory)}")
            
            // 1. 打开 Accessory
            val fd = usbManager.openAccessory(accessory)
                ?: throw Exception("Failed to open accessory")
            
            LogUtil.d(TAG, "Accessory opened successfully")
            
            // 2. 创建输入输出流
            val input = FileInputStream(fd.fileDescriptor)
            val output = FileOutputStream(fd.fileDescriptor)
            
            LogUtil.d(TAG, "Input/Output streams created")
            
            // 3. 保存资源
            synchronized(stateLock) {
                currentAccessory = accessory
                fileDescriptor = fd
                inputStream = input
                outputStream = output
                state = ConnectionState.CONNECTED
                updateStatus(InnerConnectionStatus.CONNECTED)
            }
            
            LogUtil.i(TAG, "Connection resources saved, state=CONNECTED")
            
            // 4. 初始化 HexFrame 缓冲区
            hexFrameBuffer = HexFrameBuffer(
                scope = scope,
                onFrameReceived = { frame ->
                    dataReceiver?.invoke(frame)
                }
            )
            
            LogUtil.i(TAG, "📡 About to start receive loop...")
            
            // 5. 启动接收
            startReceive()
            
            LogUtil.i(TAG, "📡 Receive loop started, receiveJob=${receiveJob != null}")
            
            // 6. 回调成功
            LogUtil.i(TAG, "=== Connection established successfully ===")
            handleConnectionSuccess(callback, null)
            
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to accessory: ${e.message}")
            cleanupResources()
            handleConnectionError("Failed to connect: ${e.message}", callback, InnerErrorCode.E212.code)
        }
    }
    
    /**
     * 清理资源
     */
    private fun cleanupResources() {
        LogUtil.d(TAG, "Cleaning up resources...")
        
        // 1. 先清空引用（防止新的操作使用旧资源）
        val oldInputStream = inputStream
        val oldOutputStream = outputStream
        val oldFileDescriptor = fileDescriptor
        val oldReceiveJob = receiveJob
        
        // 立即清空引用
        currentAccessory = null
        fileDescriptor = null
        inputStream = null
        outputStream = null
        
        // 2. 停止接收协程（使用本地引用）
        oldReceiveJob?.cancel()
        receiveJob = null
        
        // 等待接收协程完全停止（最多 500ms）
        val startTime = System.currentTimeMillis()
        while (oldReceiveJob?.isActive == true && System.currentTimeMillis() - startTime < 500) {
            Thread.sleep(10)
        }
        
        if (oldReceiveJob?.isActive == true) {
            LogUtil.w(TAG, "Receive job still active after 500ms, forcing cleanup")
        } else {
            LogUtil.d(TAG, "Receive job stopped successfully")
        }
        
        // 3. 停止 HexFrame 缓冲区
        hexFrameBuffer?.stop()
        hexFrameBuffer = null
        
        // 4. 关闭输入流（使用本地引用）
        oldInputStream?.let { stream ->
            try {
                stream.close()
                LogUtil.d(TAG, "Input stream closed")
            } catch (e: Exception) {
                LogUtil.w(TAG, "Failed to close input stream: ${e.message}")
            }
        }
        
        // 5. 关闭输出流（使用本地引用）
        oldOutputStream?.let { stream ->
            try {
                stream.close()
                LogUtil.d(TAG, "Output stream closed")
            } catch (e: Exception) {
                LogUtil.w(TAG, "Failed to close output stream: ${e.message}")
            }
        }
        
        // 6. 关闭文件描述符（使用本地引用）
        oldFileDescriptor?.let { fd ->
            try {
                fd.close()
                LogUtil.d(TAG, "File descriptor closed")
            } catch (e: Exception) {
                LogUtil.w(TAG, "Failed to close file descriptor: ${e.message}")
            }
        }
        
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
    private suspend fun requestPermissionAndWait(accessory: UsbAccessory) {
        permissionLatch = CountDownLatch(1)
        permissionDenied = false
        pendingPermissionAccessory = accessory
        
        val intent = createPermissionIntent(accessory)
        usbManager.requestPermission(accessory, intent)
        
        LogUtil.i(TAG, "Permission request sent, waiting for response...")
        
        // 等待权限响应（最多 30 秒）
        val granted = withTimeoutOrNull(30000) {
            permissionLatch?.await()
            !permissionDenied
        }
        
        pendingPermissionAccessory = null
        
        if (granted != true) {
            throw Exception("Permission denied or timeout")
        }
        
        LogUtil.i(TAG, "Permission granted")
    }
    
    /**
     * 创建权限请求 Intent
     */
    private fun createPermissionIntent(accessory: UsbAccessory): PendingIntent {
        val requestCode = accessory.hashCode() and 0x7FFFFFFF
        val intent = Intent("android.hardware.usb.action.USB_ACCESSORY_PERMISSION").apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
    
    // ============ Accessory 查找 ============
    
    /**
     * 查找 Accessory
     */
    private fun findAccessory(): UsbAccessory? {
        val accessories = usbManager.accessoryList
        
        if (accessories == null || accessories.isEmpty()) {
            LogUtil.w(TAG, "No USB accessories found")
            return null
        }
        
        LogUtil.d(TAG, "Found ${accessories.size} USB accessory(ies)")
        accessories.forEach { accessory ->
            LogUtil.d(TAG, "  - ${getAccessoryDisplayKey(accessory)}")
        }
        
        // 返回第一个 Accessory
        return accessories.firstOrNull()
    }
    
    // ============ 数据接收 ============
    
    /**
     * 启动数据接收
     */
    private fun startReceive() {
        // 取消旧的接收协程
        val oldJob = receiveJob
        receiveJob = null
        oldJob?.cancel()
        
        // 获取当前连接资源的快照（在启动新协程前）
        val currentInput = inputStream
        val currentAccessory = currentAccessory
        
        // 验证资源有效性
        if (currentInput == null || currentAccessory == null) {
            LogUtil.w(TAG, "Cannot start receive: resources not ready")
            return
        }
        
        // 生成连接ID（用于验证资源是否过期）
        val connectionId = System.currentTimeMillis()
        LogUtil.i(TAG, "📡 Starting receive loop with connectionId=$connectionId")
        
        receiveJob = scope.launch(Dispatchers.IO) {  // 使用 IO 调度器，避免阻塞主线程
            val buffer = ByteArray(16384)
            
            LogUtil.i(TAG, "📡 Data receive loop started (Accessory mode) on thread: ${Thread.currentThread().name}")
            LogUtil.i(TAG, "📡 InputStream available: ${currentInput != null}, State: $state")
            LogUtil.i(TAG, "📡 Accessory: ${getAccessoryDisplayKey(currentAccessory)}, ConnectionId: $connectionId")
            
            var loopCount = 0
            var consecutiveErrors = 0
            
            while (isActive && state == ConnectionState.CONNECTED) {
                loopCount++
                
                // 每次循环都验证资源是否仍然有效（防止使用过期资源）
                if (inputStream != currentInput || this@CableAoaAccessoryKernel.currentAccessory != currentAccessory) {
                    LogUtil.w(TAG, "⚠️ Connection resources changed, stopping stale receive loop (connectionId=$connectionId)")
                    break
                }
                
                try {
                    // 每 10 次循环打印一次心跳日志
                    if (loopCount % 10 == 1) {
                        LogUtil.d(TAG, "💓 Receive loop heartbeat #$loopCount (connectionId=$connectionId)")
                    }
                    
                    // 使用带超时的阻塞读取
                    // withTimeoutOrNull 会在超时后返回 null，这样我们可以定期检查状态
                    val bytesRead = withTimeoutOrNull(500) {
                        currentInput.read(buffer)
                    }
                    
                    when {
                        bytesRead == null -> {
                            // 超时，重置错误计数
                            consecutiveErrors = 0
                            delay(10)
                        }
                        bytesRead > 0 -> {
                            // 成功接收数据，重置错误计数
                            consecutiveErrors = 0
                            
                            val data = buffer.copyOf(bytesRead)
                            val hexStr = InnerUtil.bytes2HexStr(data)
                            val textStr = try {
                                String(data, Charsets.UTF_8)
                            } catch (e: Exception) {
                                "[non-UTF8 data]"
                            }
                            
                            LogUtil.i(TAG, "📥 [Accessory] Received $bytesRead bytes (loop #$loopCount, connectionId=$connectionId)")
                            LogUtil.d(TAG, "  Hex: $hexStr")
                            LogUtil.d(TAG, "  Text: $textStr")
                            
                            // 直接调用 dataReceiver，不通过 HexFrameBuffer（用于测试纯文本数据）
                            dataReceiver?.invoke(data)
                            
                            LogUtil.d(TAG, "✅ Data delivered to receiver")
                        }
                        else -> {
                            // bytesRead < 0: 流结束
                            consecutiveErrors++
                            LogUtil.w(TAG, "❌ Input stream ended (bytesRead=$bytesRead, consecutive errors: $consecutiveErrors, connectionId=$connectionId)")
                            
                            // 如果连续错误超过 3 次，停止接收
                            if (consecutiveErrors >= 3) {
                                LogUtil.w(TAG, "❌ Too many consecutive stream end errors, stopping receive")
                                break
                            }
                            delay(100)
                        }
                    }
                } catch (e: java.io.InterruptedIOException) {
                    LogUtil.w(TAG, "⚠️ Read interrupted: ${e.message} (connectionId=$connectionId)")
                    if (!isActive || state != ConnectionState.CONNECTED) {
                        break
                    }
                } catch (e: java.io.IOException) {
                    if (isActive && state == ConnectionState.CONNECTED) {
                        consecutiveErrors++
                        LogUtil.e(TAG, "❌ IO error during receive: ${e.message} (consecutive errors: $consecutiveErrors, connectionId=$connectionId)")
                        e.printStackTrace()
                        
                        // 如果连续错误超过 5 次，停止接收
                        if (consecutiveErrors >= 5) {
                            LogUtil.w(TAG, "❌ Too many consecutive IO errors, stopping receive loop")
                            break
                        }
                        
                        delay(100)
                    } else {
                        LogUtil.i(TAG, "IO error after disconnect, stopping receive (connectionId=$connectionId)")
                        break
                    }
                } catch (e: Exception) {
                    if (isActive && state == ConnectionState.CONNECTED) {
                        consecutiveErrors++
                        LogUtil.e(TAG, "❌ Unexpected error during receive: ${e.message} (consecutive errors: $consecutiveErrors, connectionId=$connectionId)")
                        e.printStackTrace()
                        
                        // 如果连续错误超过 5 次，停止接收
                        if (consecutiveErrors >= 5) {
                            LogUtil.w(TAG, "❌ Too many consecutive exceptions, stopping receive loop")
                            break
                        }
                        
                        delay(100)
                    } else {
                        break
                    }
                }
            }
            
            LogUtil.i(TAG, "📡 Data receive loop stopped (Accessory mode) after $loopCount iterations (connectionId=$connectionId)")
        }
    }
    
    // ============ 广播接收器 ============
    
    /**
     * 权限广播接收器
     */
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if ("android.hardware.usb.action.USB_ACCESSORY_PERMISSION" != intent.action) {
                return
            }
            
            val accessory: UsbAccessory? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
            }
            
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            
            LogUtil.i(TAG, "Permission broadcast: accessory=${accessory?.toString()}, granted=$granted")
            
            // 检查是否是我们请求的 Accessory
            if (accessory != null && isSameAccessory(accessory, pendingPermissionAccessory)) {
                permissionDenied = !granted
                permissionLatch?.countDown()
            }
        }
    }
    
    /**
     * Accessory 插拔广播接收器
     */
    private val accessoryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                    val accessory: UsbAccessory? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    }
                    
                    if (accessory != null) {
                        LogUtil.i(TAG, "Accessory attached via broadcast: ${getAccessoryDisplayKey(accessory)}")
                        
                        // 如果正在等待连接（IDLE 状态 + shouldKeepWaiting=true），尝试连接
                        if (state == ConnectionState.IDLE && 
                            shouldKeepWaiting &&
                            currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) {
                            LogUtil.i(TAG, "Currently waiting for accessory, attempting to connect...")
                            
                            val callback = currentCallback
                            if (callback != null) {
                                scope.launch {
                                    try {
                                        // 检查权限
                                        if (!usbManager.hasPermission(accessory)) {
                                            synchronized(stateLock) {
                                                state = ConnectionState.REQUESTING_PERMISSION
                                            }
                                            LogUtil.i(TAG, "Requesting permission for attached accessory")
                                            requestPermissionAndWait(accessory)
                                            LogUtil.i(TAG, "Permission granted for attached accessory")
                                        }
                                        
                                        // 连接到 Accessory
                                        connectToAccessory(accessory, callback)
                                    } catch (e: Exception) {
                                        LogUtil.e(TAG, "Failed to connect to attached accessory: ${e.message}")
                                        // 继续等待其他 accessory（如果 shouldKeepWaiting=true）
                                    }
                                }
                            }
                        }
                    }
                }
                
                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    val accessory: UsbAccessory? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    }
                    
                    if (accessory != null) {
                        LogUtil.i(TAG, "Accessory detached: ${getAccessoryDisplayKey(accessory)}")
                        
                        // 如果是当前连接的 Accessory，触发被动断开
                        if (isSameAccessory(accessory, currentAccessory) && state == ConnectionState.CONNECTED) {
                            LogUtil.w(TAG, "Host disconnected (accessory detached), triggering passive disconnect")
                            scope.launch {
                                performDisconnect(isUserInitiated = false)  // 被动断开
                            }
                        }
                    }
                }
            }
        }
    }
    
    // ============ 辅助方法 ============
    
    /**
     * 获取 Accessory 的显示标识
     */
    private fun getAccessoryDisplayKey(accessory: UsbAccessory): String {
        return "${accessory.manufacturer}/${accessory.model}/${accessory.version}"
    }
    
    /**
     * 比较两个 Accessory 是否相同
     */
    private fun isSameAccessory(accessory1: UsbAccessory?, accessory2: UsbAccessory?): Boolean {
        if (accessory1 == null || accessory2 == null) {
            return accessory1 == accessory2
        }
        return accessory1.manufacturer == accessory2.manufacturer &&
                accessory1.model == accessory2.model &&
                accessory1.version == accessory2.version
    }
    
    // ============ 初始化 ============
    
    init {
        // 注册权限广播接收器
        val permissionFilter = IntentFilter().apply {
            addAction("android.hardware.usb.action.USB_ACCESSORY_PERMISSION")
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
        
        // 注册 Accessory 插拔广播接收器
        val accessoryFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                accessoryReceiver,
                accessoryFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            LogUtil.i(TAG, "Accessory receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register accessory receiver: ${e.message}")
        }
        
        LogUtil.i(TAG, "SimplifiedAoaAccessoryKernel initialized")
    }
}
