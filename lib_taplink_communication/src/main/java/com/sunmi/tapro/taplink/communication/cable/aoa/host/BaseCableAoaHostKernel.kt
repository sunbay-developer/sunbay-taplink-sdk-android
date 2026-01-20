package com.sunmi.tapro.taplink.communication.cable.aoa.host

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.cable.aoa.core.AoaSessionPhase
import com.sunmi.tapro.taplink.communication.cable.aoa.core.BaseUsbAoaKernel
import com.sunmi.tapro.taplink.communication.cable.aoa.manager.CableAoaErrorHandler
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
abstract class BaseCableAoaHostKernel(
    appId: String,
    appSecretKey: String,
    context: Context,
    usbStandardInfo: UsbStandardProtocol.UsbStandardInfo,
    permissionAction: String,
    permissionPendingIntent: PendingIntent
) : BaseUsbAoaKernel(appId, appSecretKey, context, usbStandardInfo, permissionAction, permissionPendingIntent) {

    /**
     * AOA错误处理器（重连由SDK层统一管理）
     * 
     * 放在 BaseUsbHostKernel 中，使得所有 Host 子类都可以访问
     */
    protected val cableAoaErrorHandler = CableAoaErrorHandler()

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
     * Device Permission 请求属于哪个操作（用于权限广播时验证）
     */
    @Volatile
    private var pendingDevicePermissionToken: Long = 0L

    /**
     * Accessory Permission 请求属于哪个操作（用于权限广播时验证）
     */
    @Volatile
    private var pendingAccessoryPermissionToken: Long = 0L

    /**
     * 重写父类的 createSession 方法，添加 Host 特有的逻辑：清除权限广播去重记录和原始设备引用
     * 
     * @param reason 操作原因（用于日志）
     * @return 新的会话
     */
    override fun createSession(reason: String): com.sunmi.tapro.taplink.communication.cable.aoa.core.UsbAoaSession {
        // Host 特有：清除权限广播去重记录，允许新连接处理权限广播
        lastProcessedDevicePermission = null
        lastProcessedAccessoryPermission = null
        // 清除原始设备引用
        originalDevice = null
        return super.createSession(reason)
    }

    /**
     * 设置原始设备（发送 AOA 协议前的设备）
     * 
     * 用于区分 AOA 切换期的 DETACHED 事件
     * 
     * @param device 原始设备
     */
    protected fun setOriginalDevice(device: UsbDevice?) {
        originalDevice = device
        LogUtil.d(TAG, "Original device set: ${getDeviceDisplayKey(device)}")
    }

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

    /**
     * 生成 Accessory 的稳定标识键（用于比对和去重）
     * 
     * 【修复 P2】不使用 serial，因为权限前后 serial 可能变化或抛异常
     * 只使用 manufacturer + model + version，这些字段在权限前后保持一致
     * 
     * @param accessory USB Accessory（可为 null）
     * @return Accessory 的稳定标识键，如果 accessory 为 null 则返回 "null"
     */
    protected fun getAccessoryKey(accessory: UsbAccessory?): String {
        return if (accessory == null) {
            "null"
        } else {
            // 【修复 P2】只使用 manufacturer + model + version，不包含 serial
            // serial 仅在 getAccessoryDisplayKey() 中用于日志显示
            "${accessory.manufacturer}_${accessory.model}_${accessory.version}"
        }
    }

    /**
     * 生成 Accessory 的稳定标识键（用于 PendingIntent data URI）
     * 
     * 使用 manufacturer|model|version|description 作为分隔符，确保权限前后一致
     * 不包含 serial，因为权限前后 serial 可能变化或抛异常
     * 
     * @param accessory USB Accessory
     * @return Accessory 的稳定标识键
     */
    private fun getAccessoryStableKey(accessory: UsbAccessory): String {
        // serial 不要参与：无权限时不可读，授权后可读，会导致 key 变化
        return listOf(
            accessory.manufacturer ?: "",
            accessory.model ?: "",
            accessory.version ?: "",
            accessory.description ?: ""
        ).joinToString("|")
    }

    /**
     * 获取 Accessory 的显示标识（用于日志）
     * 
     * 注意：在 Accessory detach 后，可能没有权限访问 serial，需要安全处理
     * serial 仅用于日志显示，不参与比对
     * 
     * @param accessory USB Accessory（可为 null）
     * @return Accessory 的显示标识
     */
    protected fun getAccessoryDisplayKey(accessory: UsbAccessory?): String {
        return if (accessory == null) {
            "null"
        } else {
            val serial = try {
                accessory.serial ?: "null"
            } catch (e: SecurityException) {
                // Accessory detach 后可能没有权限访问 serial
                "no_permission"
            }
            "${accessory.manufacturer}/${accessory.model} (v${accessory.version}, serial=$serial)"
        }
    }

    /**
     * 使用稳定的标识比对两个 USB Accessory 是否相同
     * 
     * 【修复 P2】使用 getAccessoryKey() 进行比对，不依赖 serial
     * 
     * @param accessory1 第一个 Accessory（可为 null）
     * @param accessory2 第二个 Accessory（可为 null）
     * @return 如果两个 Accessory 相同（基于稳定标识）返回 true，否则返回 false
     */
    private fun isSameAccessory(accessory1: UsbAccessory?, accessory2: UsbAccessory?): Boolean {
        if (accessory1 == null || accessory2 == null) {
            return accessory1 == accessory2
        }
        // getAccessoryKey() 现在只使用 manufacturer_model_version，不包含 serial
        return getAccessoryKey(accessory1) == getAccessoryKey(accessory2)
    }

    // USB设备连接相关
    var connectedDevice: UsbDevice? = null
    var usbDeviceConnection: UsbDeviceConnection? = null
    protected var usbInterface: UsbInterface? = null
    protected var usbEndpointIn: UsbEndpoint? = null
    var usbEndpointOut: UsbEndpoint? = null

    // USB Accessory 连接相关（AOA 设备可能以 Accessory 形式出现）
    var connectedAccessory: UsbAccessory? = null
    var accessoryFileDescriptor: ParcelFileDescriptor? = null

    // 原始设备（发送 AOA 协议前的设备）
    // 用于区分 AOA 切换期的 DETACHED 事件
    @Volatile
    private var originalDevice: UsbDevice? = null

    // 权限请求相关
    protected var pendingDevice: UsbDevice? = null
    protected var pendingAccessory: UsbAccessory? = null
    // 【修复 P0-4】权限广播去重：Device 和 Accessory 分别记录
    // Device 权限广播去重：使用 (token + deviceKey) 作为唯一标识
    private var lastProcessedDevicePermission: Pair<Long, String>? = null
    // Accessory 权限广播去重：使用 (token + accessoryKey) 作为唯一标识
    private var lastProcessedAccessoryPermission: Pair<Long, String>? = null

    // 权限请求广播接收器
    protected val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // 【诊断】第一行日志，确认 receiver 是否被调用
            LogUtil.i(TAG, "=== permissionReceiver.onReceive CALLED: action=${intent.action}, package=${intent.`package`} ===")
            LogUtil.i(TAG, "=== permissionReceiver: Intent extras keys=${intent.extras?.keySet()} ===")
            
            // 接受系统标准 action
            if ("android.hardware.usb.action.USB_PERMISSION" != intent.action) {
                LogUtil.i(TAG, "permissionReceiver: action mismatch, ignoring. Expected: android.hardware.usb.action.USB_PERMISSION, Got: ${intent.action}")
                return
            }
            
            LogUtil.i(TAG, "permissionReceiver: action matched")

            synchronized(this@BaseCableAoaHostKernel) {
                try {
                    // 【修复】使用 EXTRA_DEVICE 和 EXTRA_ACCESSORY 来识别请求类型
                    val hasDevice = intent.hasExtra(UsbManager.EXTRA_DEVICE)
                    val hasAccessory = intent.hasExtra(UsbManager.EXTRA_ACCESSORY)
                    
                    LogUtil.i(TAG, "permissionReceiver: hasDevice=$hasDevice, hasAccessory=$hasAccessory")

                    // 如果有 EXTRA_ACCESSORY，交给 accessoryPermissionReceiver 处理
                    if (hasAccessory) {
                        LogUtil.i(TAG, "permissionReceiver: Has EXTRA_ACCESSORY, delegating to accessoryPermissionReceiver")
                        return
                    }

                    // 获取 EXTRA_DEVICE
                    val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    LogUtil.i(TAG, "permissionReceiver: device=${device?.deviceName}, pendingDevice=${pendingDevice?.deviceName}")

                    // 如果没有 EXTRA_DEVICE，忽略
                    if (device == null) {
                        LogUtil.i(TAG, "permissionReceiver: device is null, ignoring")
                        return
                    }

                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    // 【修复 P0-4】使用独立的 Device token
                    val token = pendingDevicePermissionToken
                    LogUtil.i(TAG, "permissionReceiver: granted=$granted, token=$token, activeToken=${currentOpToken()}")

                    // Token 门禁：不是当前这次 connect 的 permission，直接忽略
                    if (!isTokenActive(token)) {
                        LogUtil.w(TAG, "Permission broadcast ignored: stale token=$token active=${currentOpToken()} device=${device.deviceName}")
                        return
                    }

                    // Device 门禁：设备不匹配，忽略
                    if (!isSameDevice(device, pendingDevice)) {
                        LogUtil.w(TAG, "Permission broadcast ignored: device mismatch expected=${pendingDevice?.deviceName} (VID=${pendingDevice?.vendorId}, PID=${pendingDevice?.productId}, ID=${pendingDevice?.deviceId}) got=${device.deviceName} (VID=${device.vendorId}, PID=${device.productId}, ID=${device.deviceId})")
                        return
                    }

                    // 【修复 P0-4】去重处理：使用独立的 Device 去重记录
                    val deviceKey = getDeviceKey(device)
                    val currentPermission = Pair(token, deviceKey)
                    if (lastProcessedDevicePermission == currentPermission) {
                        LogUtil.w(TAG, "Ignoring duplicate permission broadcast: token=$token deviceKey=$deviceKey")
                        return
                    }
                    lastProcessedDevicePermission = currentPermission

                    val callback = currentConnectionCallback
                    pendingDevice = null
                    // 【修复 P0-4】清除独立的 Device token
                    pendingDevicePermissionToken = 0L

                    if (granted) {
                        LogUtil.i(TAG, "=== USB device permission granted: ${getDeviceDisplayKey(device)}, token=$token ===")
                        if (callback != null) {
                            val session = currentSession()
                            LogUtil.i(TAG, "permissionReceiver: Launching onPermissionGranted, session=${session?.token}, token=$token")
                            session?.scope?.launch {
                                if (!isTokenActive(token)) {
                                    LogUtil.w(TAG, "onPermissionGranted skipped: stale token=$token")
                                    return@launch
                                }
                                LogUtil.i(TAG, "permissionReceiver: Acquiring connectMutex for onPermissionGranted, token=$token")
                                connectMutex.withLock {
                                    LogUtil.i(TAG, "permissionReceiver: connectMutex acquired, calling onPermissionGranted, token=$token")
                                    onPermissionGranted(device, callback)
                                    LogUtil.i(TAG, "permissionReceiver: onPermissionGranted completed, token=$token")
                                }
                            }
                        } else {
                            LogUtil.w(TAG, "Permission granted but no callback available")
                        }
                    } else {
                        LogUtil.i(TAG, "USB device permission denied by user: ${getDeviceDisplayKey(device)}")
                        callback?.let {
                            handleConnectionError(InnerErrorCode.E252.description, it, InnerErrorCode.E252.code)
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error in permissionReceiver: ${e.message}, exception: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    // USB设备插拔广播接收器
    protected val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.i(TAG, "=========onReceive Device========intent:${intent.action}")
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device == null) {
                        LogUtil.w(TAG, "USB_DEVICE_ATTACHED broadcast received but device is null")
                        return
                    }
                    LogUtil.d(TAG, "USB device attached: ${getDeviceDisplayKey(device)}")
                    onDeviceAttached(device)
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let { detached ->
                        LogUtil.d(TAG, "USB device detached: ${getDeviceDisplayKey(detached)}")
                        
                        val session = currentSession()
                        val currentPhase = session?.phase?.get() ?: AoaSessionPhase.IDLE
                        val isAoaDevice = UsbStandardProtocol.isAoaCompatible(detached.vendorId, detached.productId)
                        val isOriginalDevice = isSameDevice(detached, originalDevice)
                        
                        LogUtil.d(TAG, "Detach analysis: phase=$currentPhase, isAoaDevice=$isAoaDevice, isOriginalDevice=$isOriginalDevice")
                        
                        // 核心修复：根据 phase 和设备类型判断是否应该触发断线
                        when {
                            // 情况1：在 SENDING_AOA 或 WAIT_AOA_ATTACH 阶段，原始设备 detach 是正常的（AOA 切换过程）
                            (currentPhase == AoaSessionPhase.SENDING_AOA || currentPhase == AoaSessionPhase.WAIT_AOA_ATTACH) 
                                    && isOriginalDevice -> {
                                LogUtil.d(TAG, "Original device detached during AOA switch (phase=$currentPhase), this is normal, ignoring")
                                // 不触发断线，这是 AOA 切换的正常过程
                            }
                            
                            // 【修复 P1-3】情况2：在 REQUEST_AOA_PERMISSION 阶段，AOA 设备 detach 应触发连接失败
                            currentPhase == AoaSessionPhase.REQUEST_AOA_PERMISSION && isAoaDevice -> {
                                LogUtil.w(TAG, "AOA device detached during permission request (phase=$currentPhase)")
                                val tokenAtDetach = session?.token ?: 0L
                                if (isTokenActive(tokenAtDetach) && session != null) {
                                    session.scope.launch {
                                        disconnectInternal(tokenAtDetach, reason = "AOA_DEVICE_DETACHED_DURING_PERMISSION")
                                        updateStatus(InnerConnectionStatus.DISCONNECTED)
                                        currentConnectionCallback?.let { callback ->
                                            handleConnectionError(InnerErrorCode.E253.description, callback, InnerErrorCode.E253.code)
                                        }
                                    }
                                }
                            }
                            
                            // 情况3：在 RUNNING 阶段，AOA 设备 detach 才是真正的断线
                            currentPhase == AoaSessionPhase.RUNNING && isAoaDevice && isSameDevice(detached, connectedDevice) -> {
                                val tokenAtDetach = session?.token ?: 0L
                                LogUtil.w(TAG, "AOA device detached during RUNNING phase, starting delayed disconnect check (token: $tokenAtDetach, device: ${getDeviceDisplayKey(detached)})")
                                
                                // 延迟检查，避免因为临时USB信号问题导致的误断开
                                // 使用 session.scope，确保 session 结束时协程会被取消
                                session?.scope?.launch {
                                    delay(300) // 等待300ms
                                    
                                    // Token 门禁：如果这 300ms 内发生了新 connect，直接忽略旧 detach
                                    if (!isTokenActive(tokenAtDetach)) {
                                        LogUtil.w(TAG, "Detach check ignored: stale token=$tokenAtDetach")
                                        return@launch
                                    }
                                    
                                    // 设备门禁：如果 connectedDevice 已经变了，也忽略（使用稳定标识比对）
                                    if (!isSameDevice(detached, connectedDevice)) {
                                        LogUtil.w(TAG, "Detach check ignored: connectedDevice changed")
                                        return@launch
                                    }
                                    
                                    // 再次检查设备是否真的断开（使用稳定标识比对）
                                    val stillExists = try {
                                        val detachedKey = getDeviceKey(detached)
                                        usbManager.deviceList.values.any { existingDevice ->
                                            getDeviceKey(existingDevice) == detachedKey
                                        }
                                    } catch (_: Exception) {
                                        false
                                    }
                                    
                                    if (!stillExists) {
                                        LogUtil.d(TAG, "AOA device confirmed disconnected after delay, token=$tokenAtDetach, device=${getDeviceDisplayKey(detached)}")
                                        // disconnectInternal 现在是 suspend 函数，可以直接调用
                                        disconnectInternal(tokenAtDetach, reason = "AOA_DEVICE_DETACHED")
                                        updateStatus(InnerConnectionStatus.DISCONNECTED)
                                    } else {
                                        LogUtil.d(TAG, "AOA device still exists, ignoring detach event (possible temporary signal issue), device=${getDeviceDisplayKey(detached)}")
                                    }
                                }
                            }
                            
                            // 情况4：其他阶段的 detach，记录日志但不触发断线
                            else -> {
                                LogUtil.d(TAG, "Device detached but not in critical phase or not target device, ignoring (phase=$currentPhase, isAoaDevice=$isAoaDevice)")
                            }
                        }
                        
                        // 处理 pendingDevice 的 detach（正在请求权限时设备断开）
                        // 必须在 synchronized 块中操作，避免与 permissionReceiver 竞态
                        synchronized(this@BaseCableAoaHostKernel) {
                            // 使用稳定标识比对，避免对象引用不同导致的误判
                            if (isSameDevice(detached, pendingDevice)) {
                                pendingDevice = null
                                // 【修复 P0-4】清除独立的 Device token
                                pendingDevicePermissionToken = 0L
                                // 清除权限广播去重记录
                                lastProcessedDevicePermission = null
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

    // USB Accessory 权限请求广播接收器
    protected val accessoryPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.i(TAG, "=== accessoryPermissionReceiver received: action=${intent.action}, package=${intent.`package`} ===")
            
            // 接受系统标准 action
            if ("android.hardware.usb.action.USB_PERMISSION" != intent.action) {
                LogUtil.i(TAG, "accessoryPermissionReceiver: action mismatch, ignoring. Expected: android.hardware.usb.action.USB_PERMISSION, Got: ${intent.action}")
                return
            }
            
            LogUtil.i(TAG, "accessoryPermissionReceiver: action matched")

            synchronized(this@BaseCableAoaHostKernel) {
                try {
                    // 【修复】使用 EXTRA_DEVICE 和 EXTRA_ACCESSORY 来识别请求类型
                    val hasDevice = intent.hasExtra(UsbManager.EXTRA_DEVICE)
                    val hasAccessory = intent.hasExtra(UsbManager.EXTRA_ACCESSORY)

                    // 如果有 EXTRA_DEVICE，交给 permissionReceiver 处理
                    if (hasDevice) {
                        return
                    }

                    // 获取 EXTRA_ACCESSORY
                    val accessory: UsbAccessory? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    }

                    // 如果没有 EXTRA_ACCESSORY，忽略
                    if (accessory == null) {
                        return
                    }

                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    // 【修复 P0-4】使用独立的 Accessory token
                    val token = pendingAccessoryPermissionToken

                    // Token 门禁：不是当前这次 connect 的 permission，直接忽略
                    if (!isTokenActive(token)) {
                        LogUtil.w(TAG, "Accessory permission broadcast ignored: stale token=$token active=${currentOpToken()} accessory=${getAccessoryDisplayKey(accessory)}")
                        return
                    }

                    // Accessory 门禁：Accessory 不匹配，忽略
                    if (!isSameAccessory(accessory, pendingAccessory)) {
                        LogUtil.w(TAG, "Accessory permission broadcast ignored: accessory mismatch expected=${getAccessoryDisplayKey(pendingAccessory)} got=${getAccessoryDisplayKey(accessory)}")
                        return
                    }

                    // 【修复 P0-4】去重处理：使用独立的 Accessory 去重记录
                    val accessoryKey = getAccessoryKey(accessory)
                    val currentPermission = Pair(token, accessoryKey)
                    if (lastProcessedAccessoryPermission == currentPermission) {
                        LogUtil.w(TAG, "Ignoring duplicate accessory permission broadcast: token=$token accessoryKey=$accessoryKey")
                        return
                    }
                    lastProcessedAccessoryPermission = currentPermission

                    val callback = currentConnectionCallback
                    pendingAccessory = null
                    // 【修复 P0-4】清除独立的 Accessory token
                    pendingAccessoryPermissionToken = 0L

                    if (granted) {
                        LogUtil.i(TAG, "=== USB accessory permission granted: ${getAccessoryDisplayKey(accessory)}, token=$token ===")
                        if (callback != null) {
                            val session = currentSession()
                            LogUtil.i(TAG, "Launching onAccessoryPermissionGranted, session=${session?.token}, token=$token")
                            session?.scope?.launch {
                                if (!isTokenActive(token)) {
                                    LogUtil.w(TAG, "onAccessoryPermissionGranted skipped: stale token=$token")
                                    return@launch
                                }
                                LogUtil.i(TAG, "Acquiring connectMutex for onAccessoryPermissionGranted, token=$token")
                                connectMutex.withLock {
                                    LogUtil.i(TAG, "connectMutex acquired, calling onAccessoryPermissionGranted, token=$token")
                                    onAccessoryPermissionGranted(accessory, callback)
                                    LogUtil.i(TAG, "onAccessoryPermissionGranted completed, token=$token")
                                }
                            }
                        } else {
                            LogUtil.w(TAG, "Accessory permission granted but no callback available")
                        }
                    } else {
                        LogUtil.d(TAG, "USB accessory permission denied by user: ${getAccessoryDisplayKey(accessory)}")
                        callback?.let {
                            handleConnectionError(InnerErrorCode.E252.description, it, InnerErrorCode.E252.code)
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error in accessoryPermissionReceiver: ${e.message}, exception: ${e.javaClass.simpleName}")
                }
            }
        }
    }

    // USB Accessory 插拔广播接收器
    protected val accessoryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.i(TAG, "=========onReceive Accessory========intent:${intent.action}")
            when (intent.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                    val accessory: UsbAccessory? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    }
                    accessory?.let {
                        LogUtil.d(TAG, "USB accessory attached: ${getAccessoryDisplayKey(it)}")
                        onAccessoryAttached(it)
                    }
                }

                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    val accessory: UsbAccessory? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    }
                    accessory?.let { detached ->
                        LogUtil.d(TAG, "USB accessory detached: ${getAccessoryDisplayKey(detached)}")
                        
                        val session = currentSession()
                        val currentPhase = session?.phase?.get() ?: AoaSessionPhase.IDLE
                        
                        LogUtil.d(TAG, "Accessory detach analysis: phase=$currentPhase")
                        
                        // 在 RUNNING 阶段，Accessory detach 才是真正的断线
                        if (currentPhase == AoaSessionPhase.RUNNING && isSameAccessory(detached, connectedAccessory)) {
                            val tokenAtDetach = session?.token ?: 0L
                            LogUtil.w(TAG, "AOA accessory detached during RUNNING phase, starting delayed disconnect check (token: $tokenAtDetach, accessory: ${getAccessoryDisplayKey(detached)})")
                            
                            // 延迟检查，避免因为临时USB信号问题导致的误断开
                            // 使用 session.scope，确保 session 结束时协程会被取消
                            session?.scope?.launch {
                                delay(300) // 等待300ms
                                
                                // Token 门禁：如果这 300ms 内发生了新 connect，直接忽略旧 detach
                                if (!isTokenActive(tokenAtDetach)) {
                                    LogUtil.w(TAG, "Accessory detach check ignored: stale token=$tokenAtDetach")
                                    return@launch
                                }
                                
                                // Accessory 门禁：如果 connectedAccessory 已经变了，也忽略
                                if (!isSameAccessory(detached, connectedAccessory)) {
                                    LogUtil.w(TAG, "Accessory detach check ignored: connectedAccessory changed")
                                    return@launch
                                }
                                
                                // 再次检查 Accessory 是否真的断开
                                val stillExists = try {
                                    val detachedKey = getAccessoryKey(detached)
                                    usbManager.accessoryList.any { existingAccessory ->
                                        getAccessoryKey(existingAccessory) == detachedKey
                                    }
                                } catch (_: Exception) {
                                    false
                                }
                                
                                if (!stillExists) {
                                    LogUtil.d(TAG, "AOA accessory confirmed disconnected after delay, token=$tokenAtDetach, accessory=${getAccessoryDisplayKey(detached)}")
                                    disconnectInternal(tokenAtDetach, reason = "AOA_ACCESSORY_DETACHED")
                                    updateStatus(InnerConnectionStatus.DISCONNECTED)
                                } else {
                                    LogUtil.d(TAG, "AOA accessory still exists, ignoring detach event (possible temporary signal issue), accessory=${getAccessoryDisplayKey(detached)}")
                                }
                            }
                        }
                        
                        // 处理 pendingAccessory 的 detach（正在请求权限时 Accessory 断开）
                        synchronized(this@BaseCableAoaHostKernel) {
                            if (isSameAccessory(detached, pendingAccessory)) {
                                pendingAccessory = null
                                // 【修复 P0-4】清除独立的 Accessory token
                                pendingAccessoryPermissionToken = 0L
                                lastProcessedAccessoryPermission = null
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
        // 注册设备权限请求广播接收器
        // 注意：使用 NOT_EXPORTED 更安全，因为权限广播的 Intent 已设置 setPackage(context.packageName)，
        // 只有本应用能收到此广播，不需要导出给其他应用
        // 注意：系统可能会使用默认的 android.hardware.usb.action.USB_PERMISSION action
        // 而不是我们在 PendingIntent 中设置的自定义 action，所以需要同时监听两个 action
        val permissionFilter = IntentFilter().apply {
            // 监听自定义 action（如果系统使用我们设置的 action）
            addAction(permissionAction)
            // 同时监听系统默认的 USB 权限广播 action
            addAction("android.hardware.usb.action.USB_PERMISSION")
        }
        LogUtil.i(TAG, "=== Registering USB device permission receiver, permissionAction=$permissionAction ===")
        LogUtil.i(TAG, "=== IntentFilter: actions=[$permissionAction, android.hardware.usb.action.USB_PERMISSION] ===")
        try {
            ContextCompat.registerReceiver(
                context,
                permissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            LogUtil.i(TAG, "=== USB device permission receiver registered successfully ===")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB device permission receiver: ${e.message}, exception: ${e.javaClass.simpleName}")
            e.printStackTrace()
        }

        // 注册 Accessory 权限请求广播接收器（使用相同的 permissionAction）
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
        
        // 【诊断】添加诊断 receiver，捕获所有可能的 USB 权限广播
        val diagnosticFilter = IntentFilter().apply {
            addAction("android.hardware.usb.action.USB_PERMISSION")
            addAction(permissionAction)
            // 也捕获其他可能的 action
            addAction("com.android.systemui.usb.USB_PERMISSION")
        }
        val diagnosticReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                LogUtil.i(TAG, "=== DIAGNOSTIC RECEIVER: Received broadcast ===")
                LogUtil.i(TAG, "=== DIAGNOSTIC: action=${intent.action}, package=${intent.`package`}, data=${intent.data} ===")
                LogUtil.i(TAG, "=== DIAGNOSTIC: Intent extras keys=${intent.extras?.keySet()} ===")
                if (intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
                    val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    LogUtil.i(TAG, "=== DIAGNOSTIC: Device=${device?.deviceName}, granted=$granted ===")
                }
                if (intent.hasExtra(UsbManager.EXTRA_ACCESSORY)) {
                    val accessory = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    LogUtil.i(TAG, "=== DIAGNOSTIC: Accessory=${accessory?.manufacturer} ${accessory?.model}, granted=$granted ===")
                }
            }
        }
        try {
            ContextCompat.registerReceiver(
                context,
                diagnosticReceiver,
                diagnosticFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            LogUtil.i(TAG, "=== Diagnostic receiver registered successfully ===")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to register diagnostic receiver: ${e.message}")
        }

        // 注册USB设备插拔广播接收器
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
            LogUtil.d(TAG, "USB device receiver registered successfully (actions: ${deviceFilter.actionsIterator().asSequence().joinToString()})")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB device receiver: ${e.message}")
        }

        // 注册USB Accessory插拔广播接收器（AOA设备可能以Accessory形式出现）
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
            LogUtil.d(TAG, "USB accessory receiver registered successfully (actions: ${accessoryFilter.actionsIterator().asSequence().joinToString()})")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB accessory receiver: ${e.message}")
        }
    }

    /**
     * 权限授予后的回调（由子类实现）
     */
    protected abstract suspend fun onPermissionGranted(device: UsbDevice, callback: ConnectionCallback)

    /**
     * Accessory 权限授予后的回调（由子类实现）
     */
    protected abstract suspend fun onAccessoryPermissionGranted(accessory: UsbAccessory, callback: ConnectionCallback)

    /**
     * 设备连接时的回调（由子类实现）
     */
    protected open fun onDeviceAttached(device: UsbDevice) {}

    /**
     * 设备断开时的回调（由子类实现）
     */
    protected open fun onDeviceDetached(device: UsbDevice) {}

    /**
     * Accessory 连接时的回调（由子类实现）
     */
    protected open fun onAccessoryAttached(accessory: UsbAccessory) {}

    /**
     * Accessory 断开时的回调（由子类实现）
     */
    protected open fun onAccessoryDetached(accessory: UsbAccessory) {}

    /**
     * 为指定设备创建独立的 PendingIntent
     * 
     * 使用设备名称的哈希码作为请求码，确保每个设备有独立的 PendingIntent
     * 这样可以避免使用同一个 PendingIntent 导致获取到旧设备的问题
     * 
     * 注意：
     * - 不依赖 Intent 中的 extras 做校验（系统回调时可能不会保留自定义 extras）
     * - 设备校验依赖进程内的 token（pendingDevicePermissionToken）和稳定标识比对（isSameDevice）
     * - 系统回调时会自动填充 UsbManager.EXTRA_DEVICE，无需手动添加
     * 
     * 【修复】移除 data URI，使用系统标准 action
     * - 原因：Intent 有 data 时，IntentFilter 必须声明对应的 data scheme，否则无法匹配
     * - 使用系统标准 action 确保广播能被接收
     * - 通过 EXTRA_DEVICE 和 EXTRA_ACCESSORY 区分请求类型
     */
    private fun createDevicePendingIntent(device: UsbDevice): PendingIntent {
        val requestCode = device.deviceName.hashCode() and 0x7FFFFFFF
        val deviceKey = getDeviceKey(device)
        
        LogUtil.i(TAG, "createDevicePendingIntent: device=${device.deviceName}, requestCode=$requestCode, deviceKey=$deviceKey")
        
        // 【修复】使用系统标准 action，不添加 data URI
        val intent = Intent("android.hardware.usb.action.USB_PERMISSION").apply {
            setPackage(context.packageName)
            // 不添加 data，避免 IntentFilter 匹配失败
        }
        
        LogUtil.i(TAG, "createDevicePendingIntent: Intent action=${intent.action}, package=${intent.`package`}")
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        
        LogUtil.i(TAG, "createDevicePendingIntent: PendingIntent created successfully, requestCode=$requestCode")
        return pendingIntent
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
            // 【修复 P0-4】开始新的 permission 流程时清掉 Device 去重记录
            lastProcessedDevicePermission = null
            
            pendingDevice = device
            // 【修复 P0-4】绑定当前操作 token 到独立的 Device token
            pendingDevicePermissionToken = currentOpToken()
            val deviceKey = getDeviceKey(device)
            LogUtil.i(TAG, "=== Requesting permission for device: ${getDeviceDisplayKey(device)}, key=$deviceKey, binding token: $pendingDevicePermissionToken ===")

            try {
                LogUtil.i(TAG, "Creating device PendingIntent, token=$pendingDevicePermissionToken")
                val devicePendingIntent = createDevicePendingIntent(device)
                LogUtil.i(TAG, "Device PendingIntent created, calling usbManager.requestPermission, token=$pendingDevicePermissionToken")
                usbManager.requestPermission(device, devicePendingIntent)
                LogUtil.i(TAG, "=== USB permission request sent for device: ${getDeviceDisplayKey(device)}, key=$deviceKey, requestCode: ${device.deviceName.hashCode() and 0x7FFFFFFF}, token: $pendingDevicePermissionToken ===")
                
                // 【修复】启动权限超时检查（30秒）
                val permissionToken = pendingDevicePermissionToken
                val session = currentSession()
                session?.scope?.launch {
                    delay(30000) // 30秒超时
                    
                    // 检查是否仍在等待权限响应
                    synchronized(this@BaseCableAoaHostKernel) {
                        if (pendingDevicePermissionToken == permissionToken && 
                            pendingDevice != null && 
                            isSameDevice(pendingDevice, device)) {
                            LogUtil.w(TAG, "=== Permission request TIMEOUT after 30s, device: ${getDeviceDisplayKey(device)}, token=$permissionToken ===")
                            
                            // 清理状态
                            pendingDevice = null
                            pendingDevicePermissionToken = 0L
                            lastProcessedDevicePermission = null
                            
                            // 报告超时错误
                            handleConnectionError(
                                "USB权限请求超时（30秒无响应）",
                                connectionCallback,
                                InnerErrorCode.E254.code
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to request USB permission: ${e.message}, exception: ${e.javaClass.simpleName}")
                e.printStackTrace()
                pendingDevice = null
                // 【修复 P0-4】清除独立的 Device token
                pendingDevicePermissionToken = 0L
                handleConnectionError("USB permission request failed: ${e.message}", connectionCallback, InnerErrorCode.E252.code)
            }
        }
    }

    /**
     * 为指定 Accessory 创建独立的 PendingIntent
     * 
     * 使用 stableKey（不包含 serial）确保权限前后 key 不变
     * 
     * 注意：
     * - Accessory 校验依赖进程内的 token（pendingAccessoryPermissionToken）和稳定标识比对（isSameAccessory）
     * - 系统回调时会自动填充 UsbManager.EXTRA_ACCESSORY，无需手动添加
     * 
     * 【修复】移除 data URI，使用系统标准 action
     * - 原因：Intent 有 data 时，IntentFilter 必须声明对应的 data scheme，否则无法匹配
     * - 使用系统标准 action 确保广播能被接收
     * - 通过 EXTRA_DEVICE 和 EXTRA_ACCESSORY 区分请求类型
     */
    private fun createAccessoryPendingIntent(accessory: UsbAccessory): PendingIntent {
        val stableKey = getAccessoryStableKey(accessory) // 不要用 serial
        val requestCode = stableKey.hashCode() and 0x7FFFFFFF // 确保是正数

        LogUtil.d(TAG, "Creating Accessory PendingIntent: stableKey=$stableKey, requestCode=$requestCode")

        // 【修复】使用系统标准 action，不添加 data URI
        val intent = Intent("android.hardware.usb.action.USB_PERMISSION").apply {
            setPackage(context.packageName)
            // 不添加 data，避免 IntentFilter 匹配失败
        }

        return try {
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to create PendingIntent: ${e.message}, requestCode=$requestCode")
            throw e
        }
    }

    /**
     * 请求 Accessory 权限
     * 
     * 为每个 Accessory 动态创建独立的 PendingIntent，避免使用同一个 PendingIntent
     * 导致获取到旧 Accessory 的问题
     * 
     * 重要：绑定当前操作 token，避免旧的 permission 广播推进新连接
     */
    protected fun requestAccessoryPermission(accessory: UsbAccessory, connectionCallback: ConnectionCallback) {
        synchronized(this) {
            // 【修复 P0-4】开始新的 permission 流程时清掉 Accessory 去重记录
            lastProcessedAccessoryPermission = null
            
            pendingAccessory = accessory
            // 【修复 P0-4】绑定当前操作 token 到独立的 Accessory token
            pendingAccessoryPermissionToken = currentOpToken()
            val accessoryKey = getAccessoryKey(accessory)
            LogUtil.d(TAG, "Requesting permission for accessory: ${getAccessoryDisplayKey(accessory)}, key=$accessoryKey, binding token: $pendingAccessoryPermissionToken")

            try {
                val accessoryPendingIntent = createAccessoryPendingIntent(accessory)
                usbManager.requestPermission(accessory, accessoryPendingIntent)
                LogUtil.d(TAG, "USB accessory permission request sent for: ${getAccessoryDisplayKey(accessory)}, key=$accessoryKey, requestCode: ${accessoryKey.hashCode() and 0x7FFFFFFF}, token: $pendingAccessoryPermissionToken")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to request USB accessory permission: ${e.message}")
                pendingAccessory = null
                // 【修复 P0-4】清除独立的 Accessory token
                pendingAccessoryPermissionToken = 0L
                handleConnectionError("USB accessory permission request failed: ${e.message}", connectionCallback, InnerErrorCode.E252.code)
            }
        }
    }

    /**
     * 断开连接（内部实现，带 token 和 reason）
     * 
     * 重要：先完成资源清理，再结束 session，避免断开流程被自己的门禁拦截
     * 使用 disconnectMutex 防止并发断开
     * 
     * @param token 会话 token，如果不活跃则忽略断开操作
     * @param reason 断开原因（用于日志）
     */
    override suspend fun disconnectInternal(token: Long, reason: String) {
        // 使用 disconnectMutex 防止并发断开
        disconnectMutex.withLock {
            val isTokenActive = isTokenActive(token)
            
            // Token 校验：如果 token 不活跃，说明连接已切换
            // 但仍然需要清理资源（如果资源还存在），但跳过 session 相关操作
            if (!isTokenActive) {
                LogUtil.w(TAG, "disconnectInternal: stale token=$token active=${currentOpToken()} reason=$reason, but will still cleanup resources")
            } else {
                LogUtil.d(TAG, "=== USB Host Disconnect Internal Started token=$token reason=$reason ===")
            }
            
            // 重置错误处理器的连接时间
            cableAoaErrorHandler.resetConnectionTime()
            
            try {
                // 注意：dataReceiveJob 的清理已经在 BaseUsbAoaKernel.cleanupCommonResources() 中完成
                // 这里不需要重复清理，只需要清理 USB Host 特有的资源

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
                originalDevice = null

                // 清理 Accessory 资源
                accessoryFileDescriptor?.let { fd ->
                    try {
                        fd.close()
                        LogUtil.d(TAG, "USB accessory file descriptor closed successfully")
                    } catch (e: Exception) {
                        LogUtil.w(TAG, "Error closing USB accessory file descriptor: ${e.message}")
                    }
                }
                accessoryFileDescriptor = null
                
                connectedAccessory?.let { accessory ->
                    LogUtil.d(TAG, "Disconnected from USB accessory: ${getAccessoryDisplayKey(accessory)}")
                }
                connectedAccessory = null

                LogUtil.d(TAG, "AOA Host disconnected successfully")
                
                // 【修复】更新状态为 DISCONNECTED，确保下次连接时不会被 connectToAoaDeviceDirectly 的幂等门禁拦截
                updateStatus(InnerConnectionStatus.DISCONNECTED)
                
                // 只有在 token 活跃时才结束 session（避免影响新连接）
                if (isTokenActive) {
                    // 结束 session（会自动关闭 IO 资源和取消所有子任务）
                    endSession(token, reason)
                } else {
                    LogUtil.d(TAG, "Skipping endSession for stale token=$token")
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error during Host disconnect: ${e.message}")
                e.printStackTrace()
                // 只有在 token 活跃时才结束 session
                if (isTokenActive) {
                    endSession(token, "disconnect error: ${e.message}")
                }
            }
            
            if (isTokenActive) {
                LogUtil.d(TAG, "=== USB Host Disconnect Internal Finished ===")
            }
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
        val currentToken = currentOpToken()
        
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
                LogUtil.w(TAG, "Send data failed: operation token changed during lock acquisition (old: $currentToken, current: ${currentOpToken()})")
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
     * 2. 清理待处理的状态（pendingDevice、pendingAccessory、pendingDevicePermissionToken、pendingAccessoryPermissionToken 等）
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
                    // 注意：dataReceiveJob 的清理已经在 BaseUsbAoaKernel.cleanupCommonResources() 中完成
                    // 这里不需要重复清理，只需要清理 USB Host 特有的资源

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
                    originalDevice = null
                    
                    LogUtil.d(TAG, "AOA Host disconnected successfully in release")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error during disconnect in release: ${e.message}")
                    e.printStackTrace()
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
            pendingDevice = null
            pendingAccessory = null
            // 【修复 P0-4】清除所有独立的 token
            pendingDevicePermissionToken = 0L
            pendingAccessoryPermissionToken = 0L
            lastProcessedDevicePermission = null
            lastProcessedAccessoryPermission = null
        }
        
        // 4. 再 unregisterReceiver（避免广播回调落到已释放对象）
        // 注意：即使 disconnect() 已经执行，仍可能有延迟的广播在途中
        // 先 unregisterReceiver 可以确保后续的广播不会被处理
        try {
            context.unregisterReceiver(permissionReceiver)
            LogUtil.d(TAG, "USB device permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB device permission receiver: ${e.message}")
        }

        // 【修复 P0】注销 Accessory 权限广播接收器
        try {
            context.unregisterReceiver(accessoryPermissionReceiver)
            LogUtil.d(TAG, "USB accessory permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB accessory permission receiver: ${e.message}")
        }

        try {
            context.unregisterReceiver(deviceReceiver)
            LogUtil.d(TAG, "USB device receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB device receiver: ${e.message}")
        }

        // 【修复 P0】注销 Accessory 插拔广播接收器
        try {
            context.unregisterReceiver(accessoryReceiver)
            LogUtil.d(TAG, "USB accessory receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB accessory receiver: ${e.message}")
        }
        
        LogUtil.d(TAG, "=== USB Host Release Finished ===")
    }
}