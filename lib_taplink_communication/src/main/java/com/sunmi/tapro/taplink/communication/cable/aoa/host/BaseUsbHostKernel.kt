package com.sunmi.tapro.taplink.communication.cable.aoa.host

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.cable.aoa.core.BaseUsbAoaKernel
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * USB AOA Host模式基类
 *
 * 提供Host模式特有的功能：
 * - USB设备操作
 * - 权限请求处理
 * - USB设备广播接收器
 * - bulkTransfer数据传输
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class BaseUsbHostKernel(
    appId: String,
    appSecretKey: String,
    context: Context,
    usbStandardInfo: UsbStandardProtocol.UsbStandardInfo,
    permissionAction: String,
    permissionPendingIntent: PendingIntent
) : BaseUsbAoaKernel(appId, appSecretKey, context, usbStandardInfo, permissionAction, permissionPendingIntent) {

    /**
     * USB bulk OUT 发送互斥锁
     * 
     * 重要：USB bulk OUT 不支持并发写，必须串行化。
     * 即使 AsyncServiceKernel.sendData() 已经使用了 sendMutex，这里使用独立的 mutex
     * 可以确保即使 sendDataToDevice() 被其他地方直接调用也能串行化。
     */
    private val usbSendMutex = Mutex()

    /**
     * 断开连接互斥锁
     * 
     * 用于防止并发断开操作，确保断开流程的原子性
     */
    private val disconnectMutex = Mutex()

    /**
     * 连接建立互斥锁
     * 
     * 用于防止并发连接操作，确保连接流程的原子性。
     * 所有连接建立操作（connect、onPermissionGranted、onDeviceAttached 触发的连接）
     * 以及子类中的 openDevice、claimInterface 都必须在 connectMutex.withLock { ... } 内执行。
     * 这样可以彻底消除"重复 claim / 重复 open / 状态错乱"。
     */
    protected val connectMutex = Mutex()

    /**
     * 操作序列号生成器（使用 AtomicLong 确保线程安全且唯一）
     */
    private val opSeq = AtomicLong(0L)

    /**
     * 当前活跃的操作令牌（连接代号）
     * 
     * 用于防止旧协程/旧广播误伤新连接：
     * - 每次连接时生成新的 token
     * - permissionReceiver、deviceReceiver、disconnectInternal、send 都检查 token
     * - 如果 token 不匹配，说明连接已切换，忽略旧操作
     */
    @Volatile
    private var activeOpToken: Long = 0L

    /**
     * Permission 请求属于哪个操作（用于权限广播时验证）
     */
    @Volatile
    private var pendingPermissionOpToken: Long = 0L

    /**
     * 开始一次新的连接/流程时调用
     * 
     * @param reason 操作原因（用于日志）
     * @return 新的操作令牌
     */
    protected fun beginNewOperation(reason: String): Long {
        val token = opSeq.incrementAndGet()
        activeOpToken = token
        // 清除权限广播去重记录，允许新连接处理权限广播
        lastProcessedPermission = null
        LogUtil.i(TAG, "beginNewOperation token=$token reason=$reason")
        return token
    }

    /**
     * 获取当前活跃的操作令牌
     */
    protected fun currentOpToken(): Long = activeOpToken

    /**
     * 检查操作令牌是否活跃（是否为当前连接）
     */
    protected fun isTokenActive(token: Long): Boolean = token == activeOpToken && token != 0L

    /**
     * 生成设备的稳定标识键（用于比对和日志）
     * 
     * 使用 deviceName + vendorId + productId + deviceId 生成唯一标识，
     * 避免对象引用不同导致的误判
     * 
     * @param device USB 设备（可为 null）
     * @return 设备的稳定标识键，如果 device 为 null 则返回 "null"
     */
    protected fun getDeviceKey(device: UsbDevice?): String {
        return if (device == null) {
            "null"
        } else {
            "${device.deviceName}_${device.vendorId}_${device.productId}_${device.deviceId}"
        }
    }

    /**
     * 获取设备的显示标识（用于日志）
     * 
     * @param device USB 设备（可为 null）
     * @return 设备的显示标识，格式：deviceName (VID=0xXXXX, PID=0xXXXX, ID=XXXX)
     */
    protected fun getDeviceDisplayKey(device: UsbDevice?): String {
        return if (device == null) {
            "null"
        } else {
            "${device.deviceName} (VID=${String.format("0x%04X", device.vendorId)}, PID=${String.format("0x%04X", device.productId)}, ID=${device.deviceId})"
        }
    }

    /**
     * 使用稳定的标识比对两个 USB 设备是否相同
     * 
     * 使用设备 key 进行比对，避免对象引用不同导致的误判
     * 
     * @param device1 第一个设备（可为 null）
     * @param device2 第二个设备（可为 null）
     * @return 如果两个设备相同（基于稳定标识）返回 true，否则返回 false
     */
    private fun isSameDevice(device1: UsbDevice?, device2: UsbDevice?): Boolean {
        if (device1 == null || device2 == null) {
            return device1 == device2 // 都为 null 时返回 true
        }
        return getDeviceKey(device1) == getDeviceKey(device2)
    }

    // USB设备连接相关
    var connectedDevice: UsbDevice? = null
    var usbDeviceConnection: UsbDeviceConnection? = null
    protected var usbInterface: UsbInterface? = null
    protected var usbEndpointIn: UsbEndpoint? = null
    var usbEndpointOut: UsbEndpoint? = null

    // 权限请求相关
    protected var pendingDevice: UsbDevice? = null
    // 权限广播去重：使用 (token + deviceKey) 作为唯一标识，避免重复处理
    // 格式：Pair<token, deviceKey>
    private var lastProcessedPermission: Pair<Long, String>? = null

    // 权限请求广播接收器
    protected val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (permissionAction != intent.action) return

            synchronized(this@BaseUsbHostKernel) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                val token = pendingPermissionOpToken
                if (device == null) {
                    LogUtil.w(TAG, "Received permission broadcast with null device, ignoring")
                    return
                }

                // Token 门禁：不是当前这次 connect 的 permission，直接忽略
                if (!isTokenActive(token)) {
                    LogUtil.w(TAG, "Permission broadcast ignored: stale token=$token active=$activeOpToken device=${device.deviceName}")
                    return
                }

                // Device 门禁：设备不匹配，忽略（使用稳定标识比对，避免对象引用不同导致的误判）
                if (!isSameDevice(device, pendingDevice)) {
                    LogUtil.w(TAG, "Permission broadcast ignored: device mismatch expected=${pendingDevice?.deviceName} (VID=${pendingDevice?.vendorId}, PID=${pendingDevice?.productId}, ID=${pendingDevice?.deviceId}) got=${device.deviceName} (VID=${device.vendorId}, PID=${device.productId}, ID=${device.deviceId})")
                    return
                }

                // 去重处理：按 (token + deviceKey) 去重，而不是按时间
                // 这样可以避免时间窗口去重可能吞掉真正有效的第二条广播
                val deviceKey = getDeviceKey(device)
                val currentPermission = Pair(token, deviceKey)
                if (lastProcessedPermission == currentPermission) {
                    LogUtil.w(TAG, "Ignoring duplicate permission broadcast: token=$token deviceKey=$deviceKey")
                    return
                }
                lastProcessedPermission = currentPermission

                val callback = currentConnectionCallback
                pendingDevice = null
                pendingPermissionOpToken = 0L
                // 注意：不清除 lastProcessedPermission，因为同一个 (token + deviceKey) 的广播应该只处理一次
                // 如果 token 变化（新连接），新的 (token + deviceKey) 组合会不同，可以正常处理

                if (granted) {
                    LogUtil.d(TAG, "USB device permission granted: ${getDeviceDisplayKey(device)}")
                    if (callback != null) {
                        scope.launch {
                            // 再次门禁，防止 launch 延迟后 token 已变
                            if (!isTokenActive(token)) {
                                LogUtil.w(TAG, "onPermissionGranted skipped: stale token=$token")
                                return@launch
                            }
                            // 在 connectMutex 内执行连接建立，避免并发连接
                            connectMutex.withLock {
                                onPermissionGranted(device, callback)
                            }
                        }
                    } else {
                        LogUtil.w(TAG, "Permission granted but no callback available")
                    }
                } else {
                    LogUtil.d(TAG, "USB device permission denied by user: ${getDeviceDisplayKey(device)}")
                    callback?.let {
                        handleConnectionError(InnerErrorCode.E252.description, it, InnerErrorCode.E252.code)
                    }
                }
            }
        }
    }

    // USB设备插拔广播接收器
    protected val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.i(TAG, "=========onReceive========intent:${intent.action}")
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        LogUtil.d(TAG, "USB device attached: ${getDeviceDisplayKey(it)}")
                        onDeviceAttached(it)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let { detached ->
                        LogUtil.d(TAG, "USB device detached: ${getDeviceDisplayKey(detached)}")
                        
                        // 捕获当时的 token 和设备对象，用于延迟检查时验证
                        val tokenAtDetach = currentOpToken()
                        val detachedAtTime = detached // 保存当时的设备对象，用于后续比对
                        val detachedKey = getDeviceKey(detached) // 保存设备 key，用于比对和日志
                        
                        // 添加智能断开检测，避免误判临时断开（使用稳定标识比对）
                        if (isSameDevice(detached, connectedDevice)) {
                            LogUtil.w(TAG, "Connected device detached, starting delayed disconnect check (token: $tokenAtDetach, device: ${getDeviceDisplayKey(detached)})")
                            
                            // 延迟检查，避免因为临时USB信号问题导致的误断开
                            scope.launch {
                                delay(300) // 等待300ms
                                
                                // Token 门禁：如果这 300ms 内发生了新 connect，直接忽略旧 detach
                                if (!isTokenActive(tokenAtDetach)) {
                                    LogUtil.w(TAG, "Detach check ignored: stale token=$tokenAtDetach")
                                    return@launch
                                }
                                
                                // 设备门禁：如果 connectedDevice 已经变了，也忽略（使用稳定标识比对）
                                if (!isSameDevice(detachedAtTime, connectedDevice)) {
                                    LogUtil.w(TAG, "Detach check ignored: connectedDevice changed")
                                    return@launch
                                }
                                
                                // 再次检查设备是否真的断开（使用稳定标识比对）
                                val stillExists = try {
                                    val currentConnectedKey = getDeviceKey(connectedDevice)
                                    currentConnectedKey == detachedKey && usbManager.deviceList.values.any { existingDevice ->
                                        getDeviceKey(existingDevice) == detachedKey
                                    }
                                } catch (_: Exception) {
                                    false
                                }
                                
                                if (!stillExists) {
                                    LogUtil.d(TAG, "Device confirmed disconnected after delay, token=$tokenAtDetach, device=${getDeviceDisplayKey(detachedAtTime)}")
                                    // disconnectInternal 现在是 suspend 函数，可以直接调用
                                    disconnectInternal(tokenAtDetach, reason = "DETACHED")
                                    updateStatus(InnerConnectionStatus.DISCONNECTED)
                                } else {
                                    LogUtil.d(TAG, "Device still exists, ignoring detach event (possible temporary signal issue), device=${getDeviceDisplayKey(detachedAtTime)}")
                                }
                            }
                        }
                        
                        // 处理 pendingDevice 的 detach（正在请求权限时设备断开）
                        // 必须在 synchronized 块中操作，避免与 permissionReceiver 竞态
                        synchronized(this@BaseUsbHostKernel) {
                            // 使用稳定标识比对，避免对象引用不同导致的误判
                            if (isSameDevice(detached, pendingDevice)) {
                                pendingDevice = null
                                pendingPermissionOpToken = 0L
                                // 清除权限广播去重记录
                                lastProcessedPermission = null
                                currentConnectionCallback?.let { callback ->
                                    handleConnectionError(InnerErrorCode.E253.description, callback, InnerErrorCode.E253.code)
                                }
                            }
                        }
                        onDeviceDetached(detached)
                    }
                }
            }
        }
    }

    init {
        // Host端不需要注册Accessory权限接收器，因为Host端使用设备权限
        // 只注册设备权限请求广播接收器
        // 注意：使用 NOT_EXPORTED 更安全，因为权限广播的 Intent 已设置 setPackage(context.packageName)，
        // 只有本应用能收到此广播，不需要导出给其他应用
        val permissionFilter = IntentFilter(permissionAction)
        try {
            ContextCompat.registerReceiver(
                context,
                permissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            LogUtil.d(TAG, "USB device permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB device permission receiver: ${e.message}")
        }

        // 注册USB设备插拔广播接收器
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            ContextCompat.registerReceiver(
                context,
                deviceReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            LogUtil.d(TAG, "USB device receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB device receiver: ${e.message}")
        }
    }

    /**
     * 权限授予后的回调（由子类实现）
     */
    protected abstract suspend fun onPermissionGranted(device: UsbDevice, callback: ConnectionCallback)

    /**
     * 设备连接时的回调（由子类实现）
     */
    protected open fun onDeviceAttached(device: UsbDevice) {}

    /**
     * 设备断开时的回调（由子类实现）
     */
    protected open fun onDeviceDetached(device: UsbDevice) {}

    /**
     * 为指定设备创建独立的 PendingIntent
     * 
     * 使用设备名称的哈希码作为请求码，确保每个设备有独立的 PendingIntent
     * 这样可以避免使用同一个 PendingIntent 导致获取到旧设备的问题
     * 
     * 注意：
     * - 不依赖 Intent 中的 extras 做校验（系统回调时可能不会保留自定义 extras）
     * - 设备校验依赖进程内的 token（pendingPermissionOpToken）和稳定标识比对（isSameDevice）
     * - 系统回调时会自动填充 UsbManager.EXTRA_DEVICE，无需手动添加
     */
    private fun createDevicePendingIntent(device: UsbDevice): PendingIntent {
        // 使用设备名称的哈希码作为请求码（设备名称是唯一的）
        val requestCode = device.deviceName.hashCode() and 0x7FFFFFFF // 确保是正数
        
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(permissionAction).apply {
                setPackage(context.packageName)
                // 注意：不在这里 putExtra(EXTRA_DEVICE)，因为：
                // 1. 系统回调时会自动填充 EXTRA_DEVICE
                // 2. 自定义 extras 可能不会被系统保留（很多 ROM/实现不会回传原 intent extras）
                // 3. 设备校验依赖进程内的 token 和稳定标识比对，更可靠
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * 请求设备权限
     * 
     * 为每个设备动态创建独立的 PendingIntent，避免使用同一个 PendingIntent
     * 导致获取到旧设备的问题
     * 
     * 重要：绑定当前操作 token，避免旧的 permission 广播推进新连接
     */
    protected fun requestPermission(device: UsbDevice, connectionCallback: ConnectionCallback) {
        synchronized(this) {
            pendingDevice = device
            // 绑定当前操作 token，用于权限广播时验证
            // 如果连接已切换（token 已变化），旧的 permission 广播会被忽略
            pendingPermissionOpToken = activeOpToken
            val deviceKey = getDeviceKey(device)
            LogUtil.d(TAG, "Requesting permission for device: ${getDeviceDisplayKey(device)}, key=$deviceKey, binding token: $pendingPermissionOpToken")
            // currentConnectionCallback 已在 AsyncServiceKernel.connect() 中设置，无需重复赋值

            try {
                // 为每个设备创建独立的 PendingIntent
                val devicePendingIntent = createDevicePendingIntent(device)
                usbManager.requestPermission(device, devicePendingIntent)
                LogUtil.d(TAG, "USB permission request sent for device: ${getDeviceDisplayKey(device)}, key=$deviceKey, requestCode: ${device.deviceName.hashCode() and 0x7FFFFFFF}, token: $pendingPermissionOpToken")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
                pendingDevice = null
                pendingPermissionOpToken = 0L
                handleConnectionError("USB permission request failed: ${e.message}", connectionCallback, InnerErrorCode.E252.code)
            }
        }
    }

    /**
     * 断开连接（内部方法）
     * 默认使用当前 token，避免旧流程断开新连接
     */
    override fun disconnectInternal() {
        // 默认用当前 token，在协程中执行断开操作
        scope.launch {
            disconnectInternal(currentOpToken(), reason = "disconnectInternal()")
        }
    }

    /**
     * 断开连接（内部实现，带 token 和 reason）
     * 
     * 重要：先完成资源清理，再清零 token，避免断开流程被自己的门禁拦截
     * 使用 disconnectMutex 防止并发断开
     * 
     * @param opToken 操作令牌，如果不活跃则忽略断开操作
     * @param reason 断开原因（用于日志）
     */
    protected suspend fun disconnectInternal(opToken: Long, reason: String) {
        // 使用 disconnectMutex 防止并发断开
        disconnectMutex.withLock {
            // Token 校验：如果 token 不活跃，说明连接已切换，忽略旧操作
            if (!isTokenActive(opToken)) {
                LogUtil.w(TAG, "disconnectInternal ignored: stale token=$opToken active=$activeOpToken reason=$reason")
                return@withLock
            }
            
            LogUtil.d(TAG, "=== USB Host Disconnect Internal Started token=$opToken reason=$reason ===")
            
            try {
                // 停止数据接收
                LogUtil.d(TAG, "Stopping data receive job...")
                dataReceiveJob?.cancel()
                dataReceiveJob = null

                // 断开设备连接
                LogUtil.d(TAG, "Releasing USB interface and connection...")
                usbInterface?.let { interface_ ->
                    try {
                        usbDeviceConnection?.releaseInterface(interface_)
                        LogUtil.d(TAG, "USB interface released successfully")
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Error releasing USB interface: ${e.message}")
                    }
                }
                
                usbDeviceConnection?.let { connection ->
                    try {
                        connection.close()
                        LogUtil.d(TAG, "USB device connection closed successfully")
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Error closing USB device connection: ${e.message}")
                    }
                }
                
                // 清理连接相关的变量
                usbDeviceConnection = null
                usbInterface = null
                usbEndpointIn = null
                usbEndpointOut = null
                
                connectedDevice?.let { device ->
                    LogUtil.d(TAG, "Disconnected from USB device: ${getDeviceDisplayKey(device)}")
                }
                connectedDevice = null

                LogUtil.d(TAG, "AOA Host disconnected successfully")
                
                // 重要：先完成资源清理，再清零 token，避免断开流程被自己的门禁拦截
                activeOpToken = 0L
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error during Host disconnect: ${e.message}")
                e.printStackTrace()
                // 即使出错也要清零 token
                activeOpToken = 0L
            }
            
            LogUtil.d(TAG, "=== USB Host Disconnect Internal Finished ===")
        }
    }

    /**
     * 执行具体的发送数据逻辑（Host模式使用bulkTransfer）
     * 注意：此方法为 suspend 函数，在协程中调用，可以直接调用 suspend 函数
     * 
     * 重要：USB bulk OUT 不支持并发写，必须串行化。
     * 使用独立的 usbSendMutex 确保 USB bulk OUT 操作串行化，即使 sendDataToDevice()
     * 被其他地方直接调用也能保证串行化。
     */
    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        // 保存当前 token，用于发送时验证
        val currentToken = activeOpToken
        
        // Token 校验：如果 token 无效，说明连接已断开或切换，直接失败
        if (!isTokenActive(currentToken)) {
            LogUtil.w(TAG, "Send data failed: operation token invalid (token: $currentToken), connection may be disconnected or switched")
            callback?.onError(InnerErrorCode.E304.code, "Connection not available")
            return
        }
        
        // 使用独立的 usbSendMutex 确保 USB bulk OUT 操作串行化
        // 这样可以确保即使 sendDataToDevice() 被其他地方直接调用也能串行化
        usbSendMutex.withLock {
            // 再次检查 token，确保连接未切换
            if (!isTokenActive(currentToken)) {
                LogUtil.w(TAG, "Send data failed: operation token changed during lock acquisition (old: $currentToken, current: $activeOpToken)")
                callback?.onError(InnerErrorCode.E304.code, "Connection switched during send")
                return@withLock
            }

            // 在锁内进行连接状态/端点快检查，并将连接字段读到局部变量
            // 这样可以避免在 sendDataToDevice 执行过程中，连接字段被 disconnect 清掉
            val conn = usbDeviceConnection
            val epOut = usbEndpointOut
            
            // 快检查：连接和端点必须存在
            if (conn == null || epOut == null) {
                LogUtil.w(TAG, "Send data failed: connection or endpoint is null (conn: $conn, epOut: $epOut)")
                callback?.onError(InnerErrorCode.E304.code, "Connection or endpoint not available")
                return@withLock
            }

            try {
                // 将连接字段作为参数传递，减少子类直接读共享字段
                val success = sendDataToDevice(conn, epOut, data, currentToken)
                if (!success) {
                    updateStatus(InnerConnectionStatus.ERROR)
                    callback?.onError(InnerErrorCode.E304.code, InnerErrorCode.E304.description)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to send data: ${e.message}")
                updateStatus(InnerConnectionStatus.ERROR)
                callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
            }
        }
    }

    /**
     * 发送数据到设备（使用bulkTransfer）
     * 注意：此方法为 suspend 函数，使用 delay() 而非 Thread.sleep() 以避免阻塞线程池
     * 
     * 重要：必须传入有效的 token，不能为 0，确保发送时进行 token 门禁校验
     * 
     * 注意：连接字段（conn, epOut）已在锁内读取到局部变量，避免在发送过程中被 disconnect 清掉。
     * 子类应该使用传入的参数，而不是直接访问共享字段（usbDeviceConnection, usbEndpointOut）。
     * 
     * @param conn USB 设备连接（已在锁内读取，保证在发送过程中不会被清空）
     * @param epOut USB 输出端点（已在锁内读取，保证在发送过程中不会被清空）
     * @param data 要发送的数据
     * @param expectedToken 期望的操作令牌，必须不为 0，用于验证连接未切换
     * @return true 如果发送成功，false 否则
     */
    protected abstract suspend fun sendDataToDevice(conn: UsbDeviceConnection, epOut: UsbEndpoint, data: ByteArray, expectedToken: Long): Boolean

    /**
     * 清理资源
     * 
     * 重要：先断开连接（取消 job、释放 USB），再 unregisterReceiver
     * 这样可以避免 release 过程中仍有协程在跑/仍有广播进来，导致回调落到已释放对象
     * 
     * 执行顺序：
     * 1. 断开连接：取消所有协程、停止数据接收、释放 USB 接口和连接
     * 2. 清理待处理的状态（pendingDevice、pendingPermissionOpToken 等）
     * 3. 取消注册接收器（unregisterReceiver）：避免广播回调落到已释放对象
     */
    override fun release() {
        LogUtil.d(TAG, "=== USB Host Release Started ===")
        
        try {
            // 1. 先断开连接（取消 job、释放 USB）
            // 使用 runBlocking 确保断开操作同步完成
            kotlinx.coroutines.runBlocking {
                // 取消所有协程
                scope.coroutineContext.cancelChildren()
                
                // 直接执行断开逻辑（同步方式，不使用 scope.launch）
                // 这样可以确保断开操作在 release 中完成
                try {
                    // 停止数据接收
                    LogUtil.d(TAG, "Stopping data receive job...")
                    dataReceiveJob?.cancel()
                    dataReceiveJob = null

                    // 断开设备连接
                    LogUtil.d(TAG, "Releasing USB interface and connection...")
                    usbInterface?.let { interface_ ->
                        try {
                            usbDeviceConnection?.releaseInterface(interface_)
                            LogUtil.d(TAG, "USB interface released successfully")
                        } catch (e: Exception) {
                            LogUtil.w(TAG, "Error releasing USB interface: ${e.message}")
                        }
                    }
                    
                    usbDeviceConnection?.let { connection ->
                        try {
                            connection.close()
                            LogUtil.d(TAG, "USB device connection closed successfully")
                        } catch (e: Exception) {
                            LogUtil.w(TAG, "Error closing USB device connection: ${e.message}")
                        }
                    }
                    
                    // 清理连接相关的变量
                    usbDeviceConnection = null
                    usbInterface = null
                    usbEndpointIn = null
                    usbEndpointOut = null
                    
                    connectedDevice?.let { device ->
                        LogUtil.d(TAG, "Disconnected from USB device: ${getDeviceDisplayKey(device)}")
                    }
                    connectedDevice = null
                    
                    // 清零 token
                    activeOpToken = 0L
                    
                    LogUtil.d(TAG, "AOA Host disconnected successfully in release")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error during disconnect in release: ${e.message}")
                    e.printStackTrace()
                    // 即使出错也要清理
                    activeOpToken = 0L
                }
            }
            
            LogUtil.d(TAG, "Disconnect completed")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during disconnect in release: ${e.message}")
        }
        
        // 2. 清理待处理的状态
        synchronized(this) {
            pendingDevice = null
            pendingPermissionOpToken = 0L
            lastProcessedPermission = null
        }
        
        // 3. 再 unregisterReceiver（避免广播回调落到已释放对象）
        // 注意：即使 disconnect() 已经执行，仍可能有延迟的广播在途中
        // 先 unregisterReceiver 可以确保后续的广播不会被处理
        try {
            context.unregisterReceiver(permissionReceiver)
            LogUtil.d(TAG, "USB device permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB device permission receiver: ${e.message}")
        }

        try {
            context.unregisterReceiver(deviceReceiver)
            LogUtil.d(TAG, "USB device receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB device receiver: ${e.message}")
        }
        
        LogUtil.d(TAG, "=== USB Host Release Finished ===")
    }
}