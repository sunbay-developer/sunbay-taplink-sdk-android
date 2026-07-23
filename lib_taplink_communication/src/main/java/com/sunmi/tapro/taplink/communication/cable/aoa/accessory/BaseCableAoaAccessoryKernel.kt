package com.sunmi.tapro.taplink.service.protocol.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.cable.aoa.core.BaseUsbAoaKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * USB AOA Accessory模式基类
 *
 * 提供Accessory模式特有的功能：
 * - Accessory权限管理
 * - Accessory连接管理
 * - FileInputStream/FileOutputStream数据传输
 * - Accessory广播接收器
 * - Token 生命周期管理（防止旧协程/旧广播误伤新连接）
 * - 连接建立互斥锁（确保连接建立的原子性）
 * - 断开连接互斥锁（防止并发断开）
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class BaseCableAoaAccessoryKernel(
    appId: String,
    appSecretKey: String,
    context: Context,
    usbStandardInfo: UsbStandardProtocol.UsbStandardInfo,
    permissionAction: String,
    permissionPendingIntent: PendingIntent,
) : BaseUsbAoaKernel(appId, appSecretKey, context, usbStandardInfo, permissionAction, permissionPendingIntent) {

    override val TAG = "BaseUsbAccessoryKernel"

    // ==================== 互斥锁 ====================
    
    /**
     * 连接建立互斥锁
     * 
     * 用于防止并发连接操作，确保连接流程的原子性。
     * 所有连接建立操作（connect、onPermissionGranted、onAccessoryAttached 触发的连接）
     * 都必须在 connectMutex.withLock { ... } 内执行。
     */
    protected val connectMutex = Mutex()

    /**
     * 断开连接互斥锁
     * 
     * 用于防止并发断开操作，确保断开流程的原子性
     */
    private val disconnectMutex = Mutex()

    // ==================== Accessory连接相关 ====================
    
    protected var connectedAccessory: UsbAccessory? = null
    protected var accessoryFileDescriptor: ParcelFileDescriptor? = null
    protected var accessoryInputStream: FileInputStream? = null
    protected var accessoryOutputStream: FileOutputStream? = null

    // ==================== Accessory权限请求相关 ====================
    
    /**
     * Permission 请求属于哪个操作（用于权限广播时验证）
     */
    @Volatile
    private var pendingPermissionOpToken: Long = 0L

    protected var pendingAccessory: UsbAccessory? = null
    
    // 权限广播去重：使用 (token + accessoryKey) 作为唯一标识，避免重复处理
    // 格式：Pair<token, accessoryKey>
    private var lastProcessedPermission: Pair<Long, String>? = null

    /**
     * 重写父类的 createSession 方法，添加 Accessory 特有的逻辑：清除权限广播去重记录
     * 
     * @param reason 操作原因（用于日志）
     * @return 新的会话
     */
    override fun createSession(reason: String): com.sunmi.tapro.taplink.communication.cable.aoa.core.UsbAoaSession {
        // Accessory 特有：清除权限广播去重记录，允许新连接处理权限广播
        lastProcessedPermission = null
        return super.createSession(reason)
    }

    /**
     * 生成Accessory的稳定标识键（用于比对和日志）
     * 
     * 使用 manufacturer + model + version + serial 生成唯一标识，
     * 避免对象引用不同导致的误判
     * 
     * 注意：在 Accessory detach 后，可能没有权限访问 serial，需要安全处理
     * 
     * @param accessory USB Accessory（可为 null）
     * @return Accessory的稳定标识键，如果 accessory 为 null 则返回 "null"
     */
    protected fun getAccessoryKey(accessory: UsbAccessory?): String {
        return if (accessory == null) {
            "null"
        } else {
            // 安全获取 serial，避免 SecurityException
            val serial = try {
                accessory.serial ?: "null"
            } catch (e: SecurityException) {
                // Accessory detach 后可能没有权限访问 serial
                "no_permission"
            }
            "${accessory.manufacturer}_${accessory.model}_${accessory.version}_$serial"
        }
    }

    /**
     * 获取Accessory的显示标识（用于日志）
     * 
     * 注意：在 Accessory detach 后，可能没有权限访问 serial，需要安全处理
     * 
     * @param accessory USB Accessory（可为 null）
     * @return Accessory的显示标识
     */
    protected fun getAccessoryDisplayKey(accessory: UsbAccessory?): String {
        return if (accessory == null) {
            "null"
        } else {
            // 安全获取 serial，避免 SecurityException
            val serial = try {
                accessory.serial ?: "null"
            } catch (e: SecurityException) {
                // Accessory detach 后可能没有权限访问 serial
                "no_permission"
            }
            "${accessory.manufacturer} ${accessory.model} (version=${accessory.version}, serial=$serial)"
        }
    }

    // ==================== 权限广播接收器 ====================
    
    protected val accessoryPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (permissionAction != intent.action) return

            synchronized(this@BaseCableAoaAccessoryKernel) {
                // 使用类型安全的 API 获取 Parcelable（Android 13+ 兼容）
                val accessory: UsbAccessory? =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    }
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                val token = pendingPermissionOpToken
                if (accessory == null) {
                    LogUtil.w(TAG, "Received permission broadcast with null accessory, ignoring")
                    return
                }

                // Token 门禁：不是当前这次 connect 的 permission，直接忽略
                if (!isTokenActive(token)) {
                    LogUtil.w(TAG, "Permission broadcast ignored: stale token=$token active=${currentOpToken()} accessory=${getAccessoryDisplayKey(accessory)}")
                    return
                }

                // Accessory 门禁：设备不匹配，忽略（使用稳定标识比对）
                if (!isSameAccessory(accessory, pendingAccessory)) {
                    LogUtil.w(TAG, "Permission broadcast ignored: accessory mismatch expected=${getAccessoryDisplayKey(pendingAccessory)} got=${getAccessoryDisplayKey(accessory)}")
                    return
                }

                // 去重处理：按 (token + accessoryKey) 去重
                val accessoryKey = getAccessoryKey(accessory)
                val currentPermission = Pair(token, accessoryKey)
                if (lastProcessedPermission == currentPermission) {
                    LogUtil.w(TAG, "Ignoring duplicate permission broadcast: token=$token accessoryKey=$accessoryKey")
                    return
                }
                lastProcessedPermission = currentPermission

                val callback = currentConnectionCallback
                pendingAccessory = null
                pendingPermissionOpToken = 0L

                if (granted) {
                    LogUtil.d(TAG, "USB accessory permission granted: ${getAccessoryDisplayKey(accessory)}")
                    if (callback != null) {
                        scope.launch {
                            // 再次门禁，防止 launch 延迟后 token 已变
                            if (!isTokenActive(token)) {
                                LogUtil.w(TAG, "onPermissionGranted skipped: stale token=$token")
                                return@launch
                            }
                            // 在 connectMutex 内执行连接建立，避免并发连接
                            connectMutex.withLock {
                                onPermissionGranted(accessory, callback, token)
                            }
                        }
                    } else {
                        LogUtil.w(TAG, "Permission granted but no callback available")
                    }
                } else {
                    LogUtil.d(TAG, "USB accessory permission denied by user: ${getAccessoryDisplayKey(accessory)}")
                    callback?.let {
                        handleConnectionError(InnerErrorCode.E252.description, it, InnerErrorCode.E252.code)
                    }
                }
            }
        }
    }

    // ==================== Accessory广播接收器 ====================
    
    protected val accessoryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.d(TAG, "=== ACCESSORY BROADCAST RECEIVED === Action: ${intent.action}")

            when (intent.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                    LogUtil.d(TAG, "Processing USB_ACCESSORY_ATTACHED broadcast")
                    val accessory: UsbAccessory? =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                        }
                    accessory?.let {
                        LogUtil.d(TAG, "USB accessory attached via broadcast: ${getAccessoryDisplayKey(it)}")
                        onAccessoryAttached(it)
                    } ?: run {
                        LogUtil.w(TAG, "USB_ACCESSORY_ATTACHED broadcast received but no accessory in intent")
                    }
                }

                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    LogUtil.d(TAG, "Processing USB_ACCESSORY_DETACHED broadcast")
                    val accessory: UsbAccessory? =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                        }
                    accessory?.let { detached ->
                        LogUtil.d(TAG, "USB accessory detached: ${getAccessoryDisplayKey(detached)}")
                        
                        // 捕获当时的 token 和设备对象，用于延迟检查时验证
                        val tokenAtDetach = currentOpToken()
                        val detachedAtTime = detached
                        val detachedKey = getAccessoryKey(detached)
                        
                        // 添加智能断开检测，避免误判临时断开
                        if (isSameAccessory(detached, connectedAccessory)) {
                            LogUtil.w(TAG, "Connected accessory detached, starting delayed disconnect check (token: $tokenAtDetach, accessory: ${getAccessoryDisplayKey(detached)})")
                            
                            // 延迟检查，避免因为临时USB信号问题导致的误断开
                            scope.launch {
                                delay(300) // 等待300ms
                                
                                // Token 门禁：如果这 300ms 内发生了新 connect，直接忽略旧 detach
                                if (!isTokenActive(tokenAtDetach)) {
                                    LogUtil.w(TAG, "Detach check ignored: stale token=$tokenAtDetach")
                                    return@launch
                                }
                                
                                // 设备门禁：如果 connectedAccessory 已经变了，也忽略
                                if (!isSameAccessory(detachedAtTime, connectedAccessory)) {
                                    LogUtil.w(TAG, "Detach check ignored: connectedAccessory changed")
                                    return@launch
                                }
                                
                                // 再次检查设备是否真的断开
                                val stillExists = try {
                                    val currentConnectedKey = getAccessoryKey(connectedAccessory)
                                    currentConnectedKey == detachedKey && usbManager.accessoryList?.any { existingAccessory ->
                                        getAccessoryKey(existingAccessory) == detachedKey
                                    } == true
                                } catch (_: Exception) {
                                    false
                                }
                                
                                if (!stillExists) {
                                    LogUtil.d(TAG, "Accessory confirmed disconnected after delay, token=$tokenAtDetach, accessory=${getAccessoryDisplayKey(detachedAtTime)}")
                                    disconnectInternal(tokenAtDetach, reason = "DETACHED")
                                    updateStatus(InnerConnectionStatus.DISCONNECTED)
                                } else {
                                    LogUtil.d(TAG, "Accessory still exists, ignoring detach event (possible temporary signal issue), accessory=${getAccessoryDisplayKey(detachedAtTime)}")
                                }
                            }
                        }
                        
                        // 处理 pendingAccessory 的 detach
                        synchronized(this@BaseCableAoaAccessoryKernel) {
                            if (isSameAccessory(detached, pendingAccessory)) {
                                pendingAccessory = null
                                pendingPermissionOpToken = 0L
                                lastProcessedPermission = null
                                currentConnectionCallback?.let { callback ->
                                    handleConnectionError(InnerErrorCode.E253.description, callback, InnerErrorCode.E253.code)
                                }
                            }
                        }
                        onAccessoryDetached(detached)
                    }
                }
            }
        }
    }

    init {
        // 注册Accessory权限请求广播接收器
        val permissionFilter = IntentFilter(permissionAction)
        try {
            ContextCompat.registerReceiver(
                context,
                accessoryPermissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            LogUtil.d(TAG, "USB accessory permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB accessory permission receiver: ${e.message}")
        }

        // 注册Accessory广播接收器
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                accessoryReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            LogUtil.d(TAG, "USB accessory receiver registered (EXPORTED)")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB accessory receiver: ${e.message}")
        }
    }

    /**
     * 使用稳定的标识比对两个 USB Accessory 是否相同
     * 
     * 使用 Accessory key 进行比对，避免对象引用不同导致的误判
     * 
     * @param accessory1 第一个 Accessory（可为 null）
     * @param accessory2 第二个 Accessory（可为 null）
     * @return 如果两个 Accessory 相同（基于稳定标识）返回 true，否则返回 false
     */
    protected fun isSameAccessory(accessory1: UsbAccessory?, accessory2: UsbAccessory?): Boolean {
        if (accessory1 == null || accessory2 == null) {
            return accessory1 == accessory2 // 都为 null 时返回 true
        }
        return getAccessoryKey(accessory1) == getAccessoryKey(accessory2)
    }

    /**
     * 权限授予后的回调（由子类实现）
     * 
     * @param token 操作令牌，用于验证连接未切换
     */
    protected abstract suspend fun onPermissionGranted(accessory: UsbAccessory, callback: ConnectionCallback, token: Long)

    /**
     * Accessory连接时的回调（由子类实现）
     */
    protected abstract fun onAccessoryAttached(accessory: UsbAccessory)

    /**
     * Accessory断开时的回调（由子类实现）
     */
    protected open fun onAccessoryDetached(accessory: UsbAccessory) {}

    /**
     * 请求Accessory权限
     * 
     * 重要：绑定当前操作 token，避免旧的 permission 广播推进新连接
     */
    protected fun requestAccessoryPermission(accessory: UsbAccessory, connectionCallback: ConnectionCallback) {
        synchronized(this) {
            if (pendingAccessory != null) {
                LogUtil.w(TAG, "Permission request already in progress for: ${getAccessoryDisplayKey(pendingAccessory)}")
                return
            }

            pendingAccessory = accessory
            // 绑定当前操作 token，用于权限广播时验证
            pendingPermissionOpToken = currentOpToken()
            val accessoryKey = getAccessoryKey(accessory)
            LogUtil.d(TAG, "Requesting permission for accessory: ${getAccessoryDisplayKey(accessory)}, key=$accessoryKey, binding token: $pendingPermissionOpToken")

            try {
                usbManager.requestPermission(accessory, permissionPendingIntent)
                LogUtil.d(TAG, "USB accessory permission request sent successfully")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to request USB accessory permission: ${e.message}")
                pendingAccessory = null
                pendingPermissionOpToken = 0L
                handleConnectionError("USB permission request failed: ${e.message}", connectionCallback, InnerErrorCode.E252.code)
            }
        }
    }

    /**
     * 连接到Accessory设备
     * 
     * 注意：此方法应在 connectMutex.withLock { ... } 内调用，确保连接建立的原子性
     * 
     * @param token 操作令牌，用于验证连接未切换
     */
    protected suspend fun connectToAccessory(
        accessory: UsbAccessory,
        connectionCallback: ConnectionCallback? = null,
        token: Long
    ) {
        // Token 检查：确保连接操作未切换
        if (!isTokenActive(token)) {
            LogUtil.w(TAG, "connectToAccessory ignored: stale token=$token")
            return
        }
        
        try {
            LogUtil.d(TAG, "Connecting to accessory: ${getAccessoryDisplayKey(accessory)}, token=$token")

            // 重要：在创建新连接之前，先清理旧的资源
            dataReceiveJob?.cancel()
            dataReceiveJob = null

            try {
                accessoryInputStream?.close()
            } catch (e: Exception) {
                LogUtil.w(TAG, "Error closing old input stream: ${e.message}")
            }

            try {
                accessoryOutputStream?.close()
            } catch (e: Exception) {
                LogUtil.w(TAG, "Error closing old output stream: ${e.message}")
            }

            try {
                accessoryFileDescriptor?.close()
            } catch (e: Exception) {
                LogUtil.w(TAG, "Error closing old file descriptor: ${e.message}")
            }

            accessoryInputStream = null
            accessoryOutputStream = null
            accessoryFileDescriptor = null
            connectedAccessory = null

            // Token 检查：确保连接操作未切换
            if (!isTokenActive(token)) {
                LogUtil.w(TAG, "connectToAccessory ignored: token changed before permission check, token=$token")
                return
            }

            // 检查权限
            if (!usbManager.hasPermission(accessory)) {
                LogUtil.d(TAG, "Requesting permission for accessory: ${getAccessoryDisplayKey(accessory)}")
                if (connectionCallback != null) {
                    requestAccessoryPermission(accessory, connectionCallback)
                }
                return
            }

            // Token 检查：确保连接操作未切换
            if (!isTokenActive(token)) {
                LogUtil.w(TAG, "connectToAccessory ignored: token changed before openAccessory, token=$token")
                return
            }

            // 打开Accessory连接
            val fileDescriptor = usbManager.openAccessory(accessory) ?: run {
                LogUtil.e(TAG, "Failed to open USB accessory")
                if (connectionCallback != null) {
                    handleConnectionError("Failed to open USB accessory", connectionCallback, InnerErrorCode.E252.code)
                }
                return
            }

            // Token 检查：确保连接操作未切换
            if (!isTokenActive(token)) {
                fileDescriptor.close()
                LogUtil.w(TAG, "connectToAccessory ignored: token changed after openAccessory, token=$token")
                return
            }

            accessoryFileDescriptor = fileDescriptor
            connectedAccessory = accessory

            // 创建输入输出流
            val fd = fileDescriptor.fileDescriptor
            accessoryInputStream = FileInputStream(fd)
            accessoryOutputStream = FileOutputStream(fd)

            LogUtil.d(TAG, "Accessory connection established, token=$token")

            // 获取当前 session
            val session = currentSession()
            if (session == null || !isSessionActive(session)) {
                LogUtil.w(TAG, "connectToAccessory: no active session after connection")
                fileDescriptor.close()
                return
            }

            // 先更新状态为 CONNECTED，确保接收循环能立即开始读取数据
            updateStatus(InnerConnectionStatus.CONNECTED)

            // 启动数据接收（传入 session 和 inputStream）
            startDataReceive(session, accessoryInputStream!!)

            LogUtil.d(TAG, "USB accessory connected: ${getAccessoryDisplayKey(accessory)}")

            if (connectionCallback != null) {
                handleConnectionSuccess(connectionCallback, null)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect accessory: ${e.message}")
            if (connectionCallback != null) {
                handleConnectionError(e.message ?: "Unknown error", connectionCallback, InnerErrorCode.E252.code)
            }
        }
    }

    /**
     * 启动数据接收（Accessory模式使用FileInputStream）
     * 由子类实现具体的数据接收逻辑
     * 
     * @param session 连接会话
     * @param inputStream 输入流（Accessory 模式使用 FileInputStream）
     */
    abstract override fun startDataReceive(session: com.sunmi.tapro.taplink.communication.cable.aoa.core.UsbAoaSession, inputStream: java.io.InputStream?)

    /**
     * 发送数据到Accessory（带重试机制）
     */
    protected suspend fun sendAccessoryData(data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        // 保存当前 token，用于发送时验证
        val currentToken = currentOpToken()
        
        // Token 校验：如果 token 无效，说明连接已断开或切换，直接失败
        if (!isTokenActive(currentToken)) {
            LogUtil.w(TAG, "Send data failed: operation token invalid (token: $currentToken), connection may be disconnected or switched")
            return@withContext false
        }
        
        val maxRetries = 3
        repeat(maxRetries) { attempt ->
            try {
                val outputStream = accessoryOutputStream
                if (outputStream == null) {
                    LogUtil.e(TAG, "USB accessory output stream not available")
                    return@withContext false
                }

                val accessory = connectedAccessory
                if (accessory == null) {
                    LogUtil.e(TAG, "Connected accessory is null")
                    return@withContext false
                }

                // 再次检查 token
                if (!isTokenActive(currentToken)) {
                    LogUtil.w(TAG, "Send data failed: operation token changed during send (old: $currentToken, current: ${currentOpToken()})")
                    return@withContext false
                }

                if (!usbManager.hasPermission(accessory)) {
                    LogUtil.e(TAG, "No permission for accessory")
                    return@withContext false
                }

                LogUtil.d(TAG, "Attempting to send AOA data: ${data.size} bytes (attempt ${attempt + 1})")
                outputStream.write(data)
                outputStream.flush()
                LogUtil.d(TAG, "AOA data sent successfully: ${data.size} bytes (attempt ${attempt + 1})")
                return@withContext true

            } catch (e: IOException) {
                LogUtil.w(TAG, "Send failed (attempt ${attempt + 1}): ${e.message}")
                val errorMessage = e.message?.lowercase() ?: ""
                if (errorMessage.contains("eio") ||
                    errorMessage.contains("no such device") ||
                    errorMessage.contains("broken pipe")
                ) {
                    LogUtil.e(TAG, "Device disconnected during send")
                    return@withContext false
                }

                if (attempt == maxRetries - 1) {
                    LogUtil.e(TAG, "Send failed after $maxRetries attempts: ${e.message}")
                    return@withContext false
                }

                delay(100L * (attempt + 1))
            } catch (e: Exception) {
                LogUtil.e(TAG, "Unexpected error during send (attempt ${attempt + 1}): ${e.message}")
                if (attempt == maxRetries - 1) {
                    return@withContext false
                }
                delay(100L * (attempt + 1))
            }
        }
        false
    }

    /**
     * 断开连接（内部实现，带 token 和 reason）
     * 
     * 重要：先完成资源清理，再结束 session，避免断开流程被自己的门禁拦截
     * 使用 disconnectMutex 防止并发断开
     * 
     * @param opToken 操作令牌，如果不活跃则忽略断开操作
     * @param reason 断开原因（用于日志）
     */
    override suspend fun disconnectInternal(opToken: Long, reason: String) {
        disconnectMutex.withLock {
            val isTokenActive = isTokenActive(opToken)
            
            // Token 校验：如果 token 不活跃，说明连接已切换
            // 但仍然需要清理资源（如果资源还存在），但跳过 session 相关操作
            if (!isTokenActive) {
                LogUtil.w(TAG, "disconnectInternal: stale token=$opToken active=${currentOpToken()} reason=$reason, but will still cleanup resources")
            } else {
                LogUtil.d(TAG, "=== USB Accessory Disconnect Internal Started token=$opToken reason=$reason ===")
            }
            
            try {
                // 停止数据接收
                dataReceiveJob?.cancel()
                dataReceiveJob = null

                // 断开Accessory连接
                accessoryInputStream?.close()
                accessoryOutputStream?.close()
                accessoryFileDescriptor?.close()
                accessoryInputStream = null
                accessoryOutputStream = null
                accessoryFileDescriptor = null
                connectedAccessory = null

                LogUtil.d(TAG, "AOA Accessory disconnected successfully")
                
                // 只有在 token 活跃时才结束 session（避免影响新连接）
                if (isTokenActive) {
                    // 结束 session（会自动关闭 IO 资源和取消所有子任务）
                    endSession(opToken, reason)
                } else {
                    LogUtil.d(TAG, "Skipping endSession for stale token=$opToken")
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error during Accessory disconnect: ${e.message}")
                e.printStackTrace()
                // 只有在 token 活跃时才结束 session
                if (isTokenActive) {
                    endSession(opToken, "disconnect error: ${e.message}")
                }
            }
            
            if (isTokenActive) {
                LogUtil.d(TAG, "=== USB Accessory Disconnect Internal Finished ===")
            }
        }
    }

    /**
     * 执行具体的发送数据逻辑
     */
    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        try {
            val success = sendAccessoryData(data)
            if (success) {
                callback?.onResponse("SUCCESS")
            } else {
                updateStatus(InnerConnectionStatus.ERROR)
                callback?.onError(InnerErrorCode.E304.code, InnerErrorCode.E304.description)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send data: ${e.message}")
            updateStatus(InnerConnectionStatus.ERROR)
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
        }
    }

    /**
     * 清理资源
     * 
     * 重要：先断开连接，再 unregisterReceiver
     */
    override fun release() {
        LogUtil.d(TAG, "=== USB Accessory Release Started ===")
        
        try {
            // 1. 先断开连接
            kotlinx.coroutines.runBlocking {
                scope.coroutineContext.cancelChildren()
                try {
                    dataReceiveJob?.cancel()
                    dataReceiveJob = null
                    accessoryInputStream?.close()
                    accessoryOutputStream?.close()
                    accessoryFileDescriptor?.close()
                    accessoryInputStream = null
                    accessoryOutputStream = null
                    accessoryFileDescriptor = null
                    connectedAccessory = null
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error during disconnect in release: ${e.message}")
                }
            }
            
            LogUtil.d(TAG, "Disconnect completed")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during disconnect in release: ${e.message}")
        }
        
        // 2. 清理公共资源（包括 session）
        cleanupCommonResources()
        
        // 3. 清理待处理的状态
        synchronized(this) {
            pendingAccessory = null
            pendingPermissionOpToken = 0L
            lastProcessedPermission = null
        }
        
        // 4. 再 unregisterReceiver
        try {
            context.unregisterReceiver(accessoryPermissionReceiver)
            LogUtil.d(TAG, "USB accessory permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB accessory permission receiver: ${e.message}")
        }

        try {
            context.unregisterReceiver(accessoryReceiver)
            LogUtil.d(TAG, "USB accessory receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB accessory receiver: ${e.message}")
        }
        
        LogUtil.d(TAG, "=== USB Accessory Release Finished ===")
    }
}