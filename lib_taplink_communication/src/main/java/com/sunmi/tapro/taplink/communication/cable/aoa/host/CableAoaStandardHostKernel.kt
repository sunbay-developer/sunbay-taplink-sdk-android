//package com.sunmi.tapro.taplink.communication.cable.aoa.host
//
//import android.app.PendingIntent
//import android.content.Context
//import android.content.Intent
//import android.hardware.usb.UsbAccessory
//import android.hardware.usb.UsbConstants
//import android.hardware.usb.UsbDevice
//import android.hardware.usb.UsbDeviceConnection
//import android.hardware.usb.UsbEndpoint
//import android.hardware.usb.UsbInterface
//import com.sunmi.tapro.taplink.communication.cable.aoa.core.AoaIoBundle
//import com.sunmi.tapro.taplink.communication.cable.aoa.core.AoaSessionPhase
//import com.sunmi.tapro.taplink.communication.cable.aoa.core.UsbAoaSession
//import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
//import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
//import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
//import com.sunmi.tapro.taplink.communication.protocol.HexFrameBuffer
//import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
//import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
//import com.sunmi.tapro.taplink.communication.util.InnerUtil
//import com.sunmi.tapro.taplink.communication.util.LogUtil
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.sync.withLock
//import java.io.InputStream
//
///**
// * USB AOA Host模式内核类
// *
// * 负责处理USB AOA Host模式的所有逻辑：
// * - 查找Android设备
// * - 发送AOA协议命令启动Accessory模式
// * - 连接Accessory设备
// * - 数据传输（发送和接收）
// *
// * AOA协议流程：
// * 1. 连接到Android设备（作为USB设备）
// * 2. 发送GET_PROTOCOL命令查询AOA协议版本
// * 3. 发送SEND_STRING命令发送Accessory信息
// * 4. 发送START命令启动Accessory模式
// * 5. 等待设备切换到Accessory模式后连接
// *
// * @author TaPro Team
// * @since 2025-01-XX
// */
//class CableAoaStandardHostKernel(
//    appId: String,
//    appSecretKey: String,
//    context: Context,
//    usbStandardInfo: UsbStandardProtocol.UsbStandardInfo,
//    permissionAction: String,
//    permissionPendingIntent: PendingIntent
//) : BaseCableAoaHostKernel(appId, appSecretKey, context, usbStandardInfo, permissionAction, permissionPendingIntent) {
//
//    override val TAG = "UsbAoaHostKernel"
//
//    // 十六进制帧缓冲区管理器
//    private var hexFrameBuffer: HexFrameBuffer? = null
//
//    /**
//     * 重写断开连接方法，添加Host特有的清理逻辑
//     */
//    override fun performDisconnect() {
//        LogUtil.d(TAG, "AOA Host disconnect started")
//
//        try {
//            hexFrameBuffer?.stop()
//            hexFrameBuffer = null
//            synchronized(this) {
//                pendingDevice = null
//            }
//            super.performDisconnect()
//            LogUtil.d(TAG, "AOA Host disconnect completed")
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Error during disconnect: ${e.message}")
//        }
//    }
//
//    /**
//     * 权限授予后的回调
//     */
//    override suspend fun onPermissionGranted(device: UsbDevice, callback: ConnectionCallback) {
//        // 获取当前 session，确保是当前连接操作的权限授予
//        val session = currentSession()
//        val currentToken = currentOpToken()
//        LogUtil.i(TAG, "=== onPermissionGranted called, device=${device.deviceName} (VID=0x${String.format("%04X", device.vendorId)}, PID=0x${String.format("%04X", device.productId)}), session.token=${session?.token}, currentToken=$currentToken, isSessionActive=${isSessionActive(session)} ===")
//
//        if (session == null || !isSessionActive(session)) {
//            LogUtil.e(TAG, "onPermissionGranted failed: no active session (session=${session?.token}, currentToken=$currentToken), reporting error to user")
//            // 【修复】关键修复：必须给用户错误回调，避免用户点击"允许"后没有任何反应
//            handleConnectionError(
//                "${InnerErrorCode.E254.description}: Session expired during permission grant",
//                callback,
//                InnerErrorCode.E254.code
//            )
//            return
//        }
//
//        LogUtil.i(TAG, "Calling startAoaProtocol, token=${session.token}")
//        startAoaProtocol(session, device, callback)
//        LogUtil.i(TAG, "startAoaProtocol returned, token=${session.token}")
//    }
//
//    /**
//     * Accessory 权限授予后的回调
//     */
//    override suspend fun onAccessoryPermissionGranted(accessory: UsbAccessory, callback: ConnectionCallback) {
//        // 获取当前 session，确保是当前连接操作的权限授予
//        val session = currentSession()
//        val currentToken = currentOpToken()
//        LogUtil.i(TAG, "=== onAccessoryPermissionGranted called, accessory=${getAccessoryDisplayKey(accessory)}, session.token=${session?.token}, currentToken=$currentToken, isSessionActive=${isSessionActive(session)} ===")
//
//        if (session == null || !isSessionActive(session)) {
//            LogUtil.e(TAG, "onAccessoryPermissionGranted failed: no active session (session=${session?.token}, currentToken=$currentToken), reporting error to user")
//            // 【修复】关键修复：必须给用户错误回调，避免用户点击"允许"后没有任何反应
//            handleConnectionError(
//                "${InnerErrorCode.E254.description}: Session expired during accessory permission grant",
//                callback,
//                InnerErrorCode.E254.code
//            )
//            return
//        }
//
//        LogUtil.i(TAG, "Calling connectToAoaAccessory, token=${session.token}")
//        connectToAoaAccessory(session, accessory, callback)
//        LogUtil.i(TAG, "connectToAoaAccessory returned, token=${session.token}")
//    }
//
//    /**
//     * Accessory 连接时的回调
//     */
//    override fun onAccessoryAttached(accessory: UsbAccessory) {
//        LogUtil.d(TAG, "AOA accessory attached: ${getAccessoryDisplayKey(accessory)}")
//
//        // 允许在 CONNECTING 或 WAITING_CONNECT 状态下处理 attach 事件
//        when {
//            currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT ||
//            currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING -> {
//                val callback = currentConnectionCallback
//                LogUtil.d(TAG, "AOA accessory detected via broadcast, callback=${if (callback != null) "available" else "null"}")
//
//                if (callback == null) {
//                    LogUtil.w(TAG, "AOA accessory attached but no connection callback available, status: $currentInnerConnectionStatus")
//                    return
//                }
//
//                scope.launch {
//                    val session = currentSession()
//                    if (session == null || !isSessionActive(session)) {
//                        LogUtil.w(TAG, "onAccessoryAttached ignored: no active session")
//                        return@launch
//                    }
//
//                    connectMutex.withLock {
//                        // 再次检查 session 是否仍然活跃
//                        if (!isSessionActive(session)) {
//                            LogUtil.w(TAG, "onAccessoryAttached ignored: session no longer active")
//                            return@withLock
//                        }
//
//                        // 检查状态是否仍然是 WAITING_CONNECT 或 CONNECTING
//                        if (currentInnerConnectionStatus != InnerConnectionStatus.WAITING_CONNECT &&
//                            currentInnerConnectionStatus != InnerConnectionStatus.CONNECTING) {
//                            LogUtil.d(TAG, "onAccessoryAttached ignored: status changed to $currentInnerConnectionStatus")
//                            return@withLock
//                        }
//
//                        LogUtil.d(TAG, "AOA accessory detected via broadcast, attempting to connect...")
//
//                        try {
//                            // 检查是否已有权限
//                            if (usbManager.hasPermission(accessory)) {
//                                LogUtil.d(TAG, "AOA accessory already has permission, connecting directly...")
//                                connectToAoaAccessoryDirectly(session, accessory, callback)
//                            } else {
//                                LogUtil.d(TAG, "Requesting permission for AOA accessory...")
//                                requestAccessoryPermission(accessory, callback)
//                            }
//                        } catch (e: Exception) {
//                            LogUtil.e(TAG, "Failed to connect to AOA accessory in onAccessoryAttached: ${e.message}")
//                        }
//                    }
//                }
//            }
//            currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED -> {
//                LogUtil.d(TAG, "AOA accessory attached but already connected, ignoring")
//            }
//            else -> {
//                LogUtil.d(TAG, "AOA accessory attached but current status is $currentInnerConnectionStatus, not in connection state")
//            }
//        }
//    }
//
//    /**
//     * 设备连接时的回调
//     *
//     * 【修复 P1-4】优化状态检查，移除 aoaConnectionInProgress 标志
//     * - 允许在 CONNECTING 或 WAITING_CONNECT 状态下处理 attach 事件
//     * - 使用 connectMutex 保护并发，不需要额外的标志
//     * - 确保第一次 attach 广播就能被正确处理
//     */
//    override fun onDeviceAttached(device: UsbDevice) {
//        LogUtil.d(
//            TAG,
//            "Device attached: ${device.deviceName} (VID: ${
//                String.format(
//                    "0x%04X",
//                    device.vendorId
//                )
//            }, PID: ${String.format("0x%04X", device.productId)})"
//        )
//
//        val isAoaDevice = UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
//
//        if (isAoaDevice) {
//            LogUtil.d(TAG, "AOA device attached, status: $currentInnerConnectionStatus")
//
//            // 【修复 P1-4】允许在 CONNECTING 或 WAITING_CONNECT 状态下处理 attach 事件
//            when {
//                currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT ||
//                currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING -> {
//                    val callback = currentConnectionCallback
//                    LogUtil.d(TAG, "AOA device detected via broadcast, callback=${if (callback != null) "available" else "null"}")
//
//                    if (callback == null) {
//                        LogUtil.w(TAG, "AOA device attached but no connection callback available, status: $currentInnerConnectionStatus")
//                        return
//                    }
//
//                    scope.launch {
//                        val session = currentSession()
//                        LogUtil.d(TAG, "onDeviceAttached: current session=${session?.token}, isSessionActive=${isSessionActive(session)}")
//
//                        if (session == null || !isSessionActive(session)) {
//                            LogUtil.w(TAG, "onDeviceAttached ignored: no active session")
//                            return@launch
//                        }
//
//                        connectMutex.withLock {
//                            // Session 检查：进入锁后再次检查
//                            if (!isSessionActive(session)) {
//                                LogUtil.w(TAG, "onDeviceAttached ignored: session no longer active")
//                                return@withLock
//                            }
//
//                            // 【修复 P1-4】检查状态是否仍然是 WAITING_CONNECT 或 CONNECTING
//                            if (currentInnerConnectionStatus != InnerConnectionStatus.WAITING_CONNECT &&
//                                currentInnerConnectionStatus != InnerConnectionStatus.CONNECTING) {
//                                LogUtil.d(TAG, "onDeviceAttached ignored: status changed to $currentInnerConnectionStatus")
//                                return@withLock
//                            }
//
//                            // 【修复 P1-4】移除 aoaConnectionInProgress 检查，connectMutex 已经保护了并发
//
//                            LogUtil.d(TAG, "AOA device detected via broadcast, attempting to connect...")
//
//                            try {
//                                // 检查是否已有权限
//                                if (usbManager.hasPermission(device)) {
//                                    LogUtil.d(TAG, "AOA device has permission, connecting directly...")
//                                    connectToAoaDeviceDirectly(session, device, callback)
//                                } else {
//                                    LogUtil.d(TAG, "Requesting permission for AOA device...")
//                                    connectToAoaDevice(session, device, callback)
//                                }
//                            } catch (e: Exception) {
//                                LogUtil.e(TAG, "Failed to connect to AOA device in onDeviceAttached: ${e.message}")
//                            }
//                        }
//                    }
//                }
//                currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED -> {
//                    LogUtil.d(TAG, "AOA device attached but already connected, ignoring")
//                }
//                else -> {
//                    LogUtil.d(TAG, "AOA device attached but current status is $currentInnerConnectionStatus, not in connection state")
//                }
//            }
//        } else {
//            // 非 AOA 设备
//            when {
//                currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT ||
//                currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING -> {
//                    LogUtil.d(TAG, "Non-AOA device attached while waiting for AOA device, ignoring (not target device)")
//                }
//                else -> {
//                    LogUtil.d(TAG, "Non-AOA device attached, status: $currentInnerConnectionStatus")
//                }
//            }
//        }
//    }
//
//    /**
//     * 设备断开时的回调
//     */
//    override fun onDeviceDetached(device: UsbDevice) {
//        LogUtil.d(
//            TAG,
//            "Device detached: ${device.deviceName} (VID: ${
//                String.format(
//                    "0x%04X",
//                    device.vendorId
//                )
//            }, PID: ${String.format("0x%04X", device.productId)})"
//        )
//
//        if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId) &&
//            currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT
//        ) {
//            LogUtil.w(TAG, "AOA device detached while waiting for connection")
//        }
//    }
//
//    /**
//     * 执行USB连接逻辑
//     *
//     * @param parseResult USB协议解析结果
//     * @param connectionCallback 连接回调
//     * @param session 连接会话，包含 token、scope、phase、io 等
//     */
//    override suspend fun performUsbConnect(
//        parseResult: ProtocolParseResult.UsbProtocol,
//        connectionCallback: ConnectionCallback,
//        session: UsbAoaSession
//    ) {
//        val token = session.token
//        LogUtil.i(TAG, "=== USB connect started with token=$token ===")
//
//        // 更新 phase 为 REQUEST_ORIGINAL_PERMISSION
//        session.phase.set(AoaSessionPhase.REQUEST_ORIGINAL_PERMISSION)
//
//        // 在 connectMutex 内执行所有连接建立操作，确保原子性
//        LogUtil.i(TAG, "Acquiring connectMutex, token=$token")
//        connectMutex.withLock {
//            LogUtil.i(TAG, "connectMutex acquired, token=$token")
//            // Token 检查：确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "USB connect ignored: token changed during lock acquisition, token=$token")
//                return@withLock
//            }
//
//            try {
//                val devices = usbManager.deviceList
//                LogUtil.i(TAG, "Starting USB connect, found ${devices.size} device(s), token=$token")
//
//                // 【修复 P0-2】区分"没有设备"和"设备不匹配"
//                if (devices.isEmpty()) {
//                    LogUtil.e(TAG, "No USB devices found in system")
//                    handleConnectionError(InnerErrorCode.E251.description, connectionCallback, InnerErrorCode.E251.code)
//                    return@withLock
//                }
//
//                // 检查是否存在AOA设备
//                LogUtil.i(TAG, "Checking for AOA devices, token=$token")
//                devices.values.forEach { device ->
//                    val isAoa = UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
//                    LogUtil.i(TAG, "Device: ${device.deviceName} (VID=0x${String.format("%04X", device.vendorId)}, PID=0x${String.format("%04X", device.productId)}), isAOA=$isAoa, token=$token")
//                }
//
//                val aoaDevice = devices.values.find { device ->
//                    UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
//                }
//
//                if (aoaDevice != null) {
//                    LogUtil.i(TAG, "Found existing AOA device: ${aoaDevice.deviceName}, token=$token")
//                    val hasPermission = usbManager.hasPermission(aoaDevice)
//                    LogUtil.i(TAG, "AOA device permission check: hasPermission=$hasPermission, token=$token")
//                    if (hasPermission) {
//                        LogUtil.i(TAG, "AOA device already has permission, connecting directly, token=$token")
//                        // 【修复】设备已在 AOA 模式且有权限，直接连接
//                        // 注意：不要在这里设置 phase，让 connectToAoaDeviceDirectly 内部设置
//                        connectToAoaDeviceDirectly(session, aoaDevice, connectionCallback)
//                    } else {
//                        LogUtil.i(TAG, "Requesting permission for AOA device, token=$token")
//                        requestPermission(aoaDevice, connectionCallback)
//                    }
//                    return@withLock
//                }
//
//                // 查找需要切换的Android设备
//                LogUtil.i(TAG, "No AOA device found, looking for Android device to switch, token=$token")
//                val device = when {
//                    parseResult.deviceName != null -> {
//                        LogUtil.i(TAG, "Looking for device by name: ${parseResult.deviceName}, token=$token")
//                        devices.values.firstOrNull { it.deviceName == parseResult.deviceName }
//                    }
//                    parseResult.deviceId != null -> {
//                        LogUtil.i(TAG, "Looking for device by ID: ${parseResult.deviceId}, token=$token")
//                        devices.values.firstOrNull { it.deviceId.toString() == parseResult.deviceId }
//                    }
//                    else -> {
//                        LogUtil.i(TAG, "No specific device criteria, using first device, token=$token")
//                        devices.values.firstOrNull()
//                    }
//                }
//
//                // 【修复 P0-2】区分"设备不匹配"和"权限错误"
//                if (device == null) {
//                    LogUtil.e(TAG, "No suitable USB device found (devices exist but none match criteria), token=$token")
//                    handleConnectionError(InnerErrorCode.E251.description, connectionCallback, InnerErrorCode.E251.code)
//                    return@withLock
//                }
//
//                LogUtil.i(
//                    TAG,
//                    "Selected device: ${device.deviceName} (VID: ${
//                        String.format(
//                            "0x%04X",
//                            device.vendorId
//                        )
//                    }, PID: ${String.format("0x%04X", device.productId)}), token=$token"
//                )
//
//                val hasPermission = usbManager.hasPermission(device)
//                LogUtil.i(TAG, "Device permission check: hasPermission=$hasPermission, token=$token")
//                if (hasPermission) {
//                    LogUtil.i(TAG, "Calling startAoaProtocol, token=$token")
//                    startAoaProtocol(session, device, connectionCallback)
//                } else {
//                    LogUtil.i(TAG, "Requesting permission for device, token=$token")
//                    requestPermission(device, connectionCallback)
//                }
//            } catch (e: Exception) {
//                LogUtil.e(TAG, "Failed to connect: ${e.message}")
//                handleConnectionError(
//                    "${InnerErrorCode.E212.description}:${e.message ?: "Unknown error"}",
//                    connectionCallback,
//                    InnerErrorCode.E212.code
//                )
//            }
//        }
//    }
//
//    /**
//     * 开始AOA协议流程
//     *
//     * 注意：调用此方法前必须确保已有USB设备权限
//     * 注意：此方法应在 connectMutex.withLock { ... } 内调用，确保连接建立的原子性
//     *
//     * @param session 连接会话
//     * @param device 原始设备
//     * @param connectionCallback 连接回调
//     */
//    private suspend fun startAoaProtocol(session: UsbAoaSession, device: UsbDevice, connectionCallback: ConnectionCallback) {
//        val token = session.token
//        // Token 检查：确保连接操作未切换
//        if (!isTokenActive(token)) {
//            LogUtil.w(TAG, "startAoaProtocol ignored: stale token=$token")
//            return
//        }
//
//        try {
//            LogUtil.d(TAG, "Starting AOA protocol for ${device.deviceName}, token=$token")
//
//            // 【修复】先检查设备是否已经在 AOA 模式，避免对 AOA 设备再次发送协议
//            if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
//                LogUtil.i(TAG, "Device already in AOA mode, connecting directly, token=$token")
//                // 设备已经在 AOA 模式，直接连接（不需要 AOA 协议切换）
//                // 注意：不要在这里设置 phase，让 connectToAoaDeviceDirectly 内部设置
//                connectToAoaDeviceDirectly(session, device, connectionCallback)
//                return
//            }
//
//            // 保存原始设备引用，用于区分 AOA 切换期的 DETACHED 事件
//            setOriginalDevice(device)
//
//            // 更新 phase 为 SENDING_AOA（只有需要发送 AOA 协议时才设置此 phase）
//            session.phase.set(AoaSessionPhase.SENDING_AOA)
//
//            // Token 检查：确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "startAoaProtocol ignored: token changed before openDevice, token=$token")
//                return
//            }
//
//            // openDevice 必须在 connectMutex 内执行（调用方已保证）
//            val connection = usbManager.openDevice(device) ?: run {
//                LogUtil.e(TAG, "Failed to open USB device")
//                handleConnectionError(InnerErrorCode.E252.description, connectionCallback, InnerErrorCode.E252.code)
//                return
//            }
//
//            val aoaDeviceInfo = UsbStandardProtocol.createDefaultAoaDeviceInfo()
//
//            if (!UsbStandardProtocol.sendAoaProtocolSequence(connection, aoaDeviceInfo)) {
//                LogUtil.e(TAG, "Failed to send AOA protocol sequence")
//                connection.close()
//                handleConnectionError(InnerErrorCode.E254.description, connectionCallback, InnerErrorCode.E254.code)
//                return
//            }
//
//            connection.close()
//            LogUtil.d(TAG, "AOA protocol sent, waiting for device to switch, token=$token")
//
//            // Token 检查：确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "startAoaProtocol ignored: token changed after sending AOA protocol, token=$token")
//                return
//            }
//
//            // 更新 phase 为 WAIT_AOA_ATTACH
//            session.phase.set(AoaSessionPhase.WAIT_AOA_ATTACH)
//            updateStatus(InnerConnectionStatus.WAITING_CONNECT)
//            // currentConnectionCallback 已在 AsyncServiceKernel.connect() 中设置，无需重复赋值
//            waitForAoaAccessory(session, connectionCallback)
//
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to start AOA protocol: ${e.message}")
//            disconnectInternal(token, "startAoaProtocol failed: ${e.message}")
//            handleConnectionError(
//                "${InnerErrorCode.E254.description}:${e.message ?: "Unknown error"}",
//                connectionCallback,
//                InnerErrorCode.E254.code
//            )
//        }
//    }
//
//    /**
//     * 等待AOA Accessory出现并连接
//     *
//     * @param session 连接会话
//     * @param connectionCallback 连接回调
//     */
//    private suspend fun waitForAoaAccessory(session: UsbAoaSession, connectionCallback: ConnectionCallback) {
//        val token = session.token
//        val maxWaitTime = 15000L // 15秒
//        val checkInterval = 200L
//        val maxAttempts = (maxWaitTime / checkInterval).toInt()
//
//        var attempts = 0
//        var aoaPermissionRequested = false // 标记是否已经请求过AOA设备权限
//
//        LogUtil.d(TAG, "Waiting for AOA accessory, token=$token")
//
//        while (attempts < maxAttempts &&
//            (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING ||
//                    currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT)
//        ) {
//            // Token 检查：每次循环开始前检查，确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed, token=$token")
//                return
//            }
//
//            delay(checkInterval)
//            attempts++
//
//            // Token 检查：delay 后再次检查，确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed after delay, token=$token")
//                return
//            }
//
//            // 检查连接状态是否已改变（可能通过广播连接了）
//            if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTING &&
//                currentInnerConnectionStatus != InnerConnectionStatus.WAITING_CONNECT
//            ) {
//                LogUtil.d(TAG, "Connection established via broadcast, token=$token")
//                return
//            }
//
//            // Token 检查：确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed before device check, token=$token")
//                return
//            }
//
//            // 检查是否有AOA设备出现（可能以 Device 或 Accessory 形式出现）
//            val aoaDevice = usbManager.deviceList.values.find { device ->
//                UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
//            }
//            val aoaAccessory = usbManager.accessoryList?.firstOrNull()
//
//            // 【修复 P1-4】移除 aoaConnectionInProgress 检查，使用 connectMutex 保护并发
//            // 优先处理 Accessory（AOA 设备通常以 Accessory 形式出现）
//            if (aoaAccessory != null) {
//                // Token 检查：发现 Accessory 后再次检查，确保连接操作未切换
//                if (!isTokenActive(token)) {
//                    LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed after finding AOA accessory, token=$token")
//                    return
//                }
//
//                LogUtil.d(TAG, "AOA accessory detected: ${getAccessoryDisplayKey(aoaAccessory)}, token=$token")
//
//                try {
//                    // Token 检查：连接前再次检查，确保连接操作未切换
//                    if (!isTokenActive(token)) {
//                        LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed before connecting accessory, token=$token")
//                        return
//                    }
//
//                    // 检查是否已有权限
//                    if (usbManager.hasPermission(aoaAccessory)) {
//                        LogUtil.d(TAG, "AOA accessory already has permission, connecting directly..., token=$token")
//                        // 【修复死锁】不需要再次获取 connectMutex，因为调用方（onPermissionGranted）已经持有了锁
//                        connectToAoaAccessoryDirectly(session, aoaAccessory, connectionCallback)
//                        return
//                    } else if (!aoaPermissionRequested) {
//                        // 只请求一次AOA Accessory权限
//                        LogUtil.d(TAG, "Requesting permission for AOA accessory, token=$token")
//                        LogUtil.i(
//                            TAG,
//                            "Note: This is the second permission request - AOA protocol requires separate permissions for:"
//                        )
//                        LogUtil.i(TAG, "  1. Original device (for AOA protocol setup)")
//                        LogUtil.i(TAG, "  2. AOA accessory device (for data communication)")
//                        LogUtil.d(TAG, "Setting aoaPermissionRequested=true, token=$token")
//                        aoaPermissionRequested = true
//                        // 更新 phase 为 REQUEST_AOA_PERMISSION
//                        LogUtil.d(TAG, "Setting phase to REQUEST_AOA_PERMISSION, token=$token")
//                        session.phase.set(AoaSessionPhase.REQUEST_AOA_PERMISSION)
//                        LogUtil.d(TAG, "Phase set completed, token=$token")
//                        // 【修复死锁】不需要再次获取 connectMutex，因为调用方（onPermissionGranted）已经持有了锁
//                        LogUtil.d(TAG, "Calling requestAccessoryPermission for AOA accessory (already in connectMutex), token=$token")
//                        try {
//                            requestAccessoryPermission(aoaAccessory, connectionCallback)
//                            LogUtil.d(TAG, "requestAccessoryPermission completed, token=$token")
//                        } catch (e: Exception) {
//                            LogUtil.e(TAG, "Exception in requestAccessoryPermission: ${e.message}, token=$token")
//                            throw e
//                        }
//                        return
//                    }
//                } catch (e: Exception) {
//                    LogUtil.w(TAG, "Failed to connect to AOA accessory: ${e.message}, token=$token")
//                }
//            } else if (aoaDevice != null) {
//                // Token 检查：发现设备后再次检查，确保连接操作未切换
//                if (!isTokenActive(token)) {
//                    LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed after finding AOA device, token=$token")
//                    return
//                }
//
//                LogUtil.d(TAG, "AOA device detected: ${aoaDevice.deviceName}, token=$token")
//                LogUtil.d(
//                    TAG, "VID: ${String.format("0x%04X", aoaDevice.vendorId)}, " +
//                            "PID: ${String.format("0x%04X", aoaDevice.productId)}"
//                )
//
//                try {
//                    // Token 检查：连接前再次检查，确保连接操作未切换
//                    if (!isTokenActive(token)) {
//                        LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed before connecting, token=$token")
//                        return
//                    }
//
//                    // 检查是否已有权限
//                    if (usbManager.hasPermission(aoaDevice)) {
//                        LogUtil.d(TAG, "AOA device already has permission, connecting directly..., token=$token")
//                        // 【修复死锁】不需要再次获取 connectMutex，因为调用方（onPermissionGranted）已经持有了锁
//                        connectToAoaDeviceDirectly(session, aoaDevice, connectionCallback)
//                        return
//                    } else if (!aoaPermissionRequested) {
//                        // 只请求一次AOA设备权限
//                        LogUtil.d(TAG, "Requesting permission for AOA device, token=$token")
//                        LogUtil.i(
//                            TAG,
//                            "Note: This is the second permission request - AOA protocol requires separate permissions for:"
//                        )
//                        LogUtil.i(TAG, "  1. Original device (for AOA protocol setup)")
//                        LogUtil.i(TAG, "  2. AOA accessory device (for data communication)")
//                        LogUtil.d(TAG, "Setting aoaPermissionRequested=true, token=$token")
//                        aoaPermissionRequested = true
//                        // 更新 phase 为 REQUEST_AOA_PERMISSION
//                        LogUtil.d(TAG, "Setting phase to REQUEST_AOA_PERMISSION, token=$token")
//                        session.phase.set(AoaSessionPhase.REQUEST_AOA_PERMISSION)
//                        LogUtil.d(TAG, "Phase set completed, token=$token")
//                        // 【修复死锁】不需要再次获取 connectMutex，因为调用方（onPermissionGranted）已经持有了锁
//                        LogUtil.d(TAG, "Calling connectToAoaDevice for AOA device (already in connectMutex), token=$token")
//                        try {
//                            connectToAoaDevice(session, aoaDevice, connectionCallback)
//                            LogUtil.d(TAG, "connectToAoaDevice completed, token=$token")
//                        } catch (e: Exception) {
//                            LogUtil.e(TAG, "Exception in connectToAoaDevice: ${e.message}, token=$token")
//                        }
//                        return
//                    }
//                } catch (e: Exception) {
//                    LogUtil.w(TAG, "Failed to connect to AOA device: ${e.message}, token=$token")
//                }
//            }
//
//            // 每5秒打印一次状态
//            if (attempts % 25 == 0) {
//                LogUtil.d(TAG, "Waiting for AOA device... (${attempts * checkInterval}ms elapsed, token=$token)")
//                if (aoaPermissionRequested) {
//                    LogUtil.d(TAG, "AOA device permission already requested, waiting for user response...")
//                }
//            }
//        }
//
//        // 超时处理
//        // Token 检查：超时后再次检查，确保连接操作未切换
//        if (!isTokenActive(token)) {
//            LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed after loop timeout, token=$token")
//            return
//        }
//
//        // 检查是否有 AOA 设备或 Accessory 出现
//        val finalAoaDevice = usbManager.deviceList.values.find { device ->
//            UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
//        }
//        val finalAoaAccessory = usbManager.accessoryList?.firstOrNull()
//
//        if (finalAoaAccessory != null) {
//            // Token 检查：发现 Accessory 但连接失败，确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed after finding final accessory, token=$token")
//                return
//            }
//            LogUtil.w(TAG, "AOA accessory found but connection failed after ${maxWaitTime}ms, token=$token")
//            LogUtil.w(TAG, "Accessory: ${getAccessoryDisplayKey(finalAoaAccessory)}")
//            if (!aoaPermissionRequested) {
//                LogUtil.w(TAG, "Permission was never requested for AOA accessory")
//            } else {
//                LogUtil.w(TAG, "Permission was requested but user may have denied it or timeout occurred")
//            }
//        } else if (finalAoaDevice != null) {
//            // Token 检查：发现设备但连接失败，确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed after finding final device, token=$token")
//                return
//            }
//            LogUtil.w(TAG, "AOA device found but connection failed after ${maxWaitTime}ms, token=$token")
//            LogUtil.w(
//                TAG, "Device: ${finalAoaDevice.deviceName} " +
//                        "(VID: ${String.format("0x%04X", finalAoaDevice.vendorId)}, " +
//                        "PID: ${String.format("0x%04X", finalAoaDevice.productId)})"
//            )
//            if (!aoaPermissionRequested) {
//                LogUtil.w(TAG, "Permission was never requested for AOA device")
//            } else {
//                LogUtil.w(TAG, "Permission was requested but user may have denied it or timeout occurred")
//            }
//        } else {
//            // Token 检查：超时前再次检查，确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed after timeout, token=$token")
//                return
//            }
//            LogUtil.w(TAG, "No AOA device or accessory found after ${maxWaitTime}ms, token=$token")
//            LogUtil.w(TAG, "Target device may not support AOA or failed to switch to Accessory mode")
//        }
//
//        // Token 检查：最终检查，确保连接操作未切换
//        if (!isTokenActive(token)) {
//            LogUtil.w(TAG, "waitForAoaAccessory stopped: token changed before error handling, token=$token")
//            return
//        }
//
//        updateStatus(InnerConnectionStatus.DISCONNECTED)
//        handleConnectionError(InnerErrorCode.E213.description, connectionCallback, InnerErrorCode.E213.code)
//    }
//
//    /**
//     * 连接到AOA设备
//     *
//     * 注意：此方法应在 connectMutex.withLock { ... } 内调用，确保连接建立的原子性
//     *
//     * @param session 连接会话
//     * @param device AOA设备
//     * @param connectionCallback 连接回调
//     */
//    private suspend fun connectToAoaDevice(session: UsbAoaSession, device: UsbDevice, connectionCallback: ConnectionCallback) {
//        val token = session.token
//        LogUtil.d(TAG, "connectToAoaDevice called, device=${device.deviceName}, token=$token")
//        // Token 检查：确保连接操作未切换
//        if (!isTokenActive(token)) {
//            LogUtil.w(TAG, "connectToAoaDevice ignored: stale token=$token")
//            return
//        }
//
//        try {
//            LogUtil.d(TAG, "Attempting to connect to AOA device: ${device.deviceName}, token=$token")
//
//            // 检查权限
//            val hasPermission = usbManager.hasPermission(device)
//            LogUtil.d(TAG, "AOA device permission check: hasPermission=$hasPermission, token=$token")
//            if (!hasPermission) {
//                LogUtil.d(TAG, "Requesting permission for AOA device, token=$token")
//                // 更新 phase 为 REQUEST_AOA_PERMISSION
//                LogUtil.d(TAG, "Setting phase to REQUEST_AOA_PERMISSION in connectToAoaDevice, token=$token")
//                session.phase.set(AoaSessionPhase.REQUEST_AOA_PERMISSION)
//                LogUtil.d(TAG, "Phase set completed in connectToAoaDevice, token=$token")
//                LogUtil.d(TAG, "Calling requestPermission for AOA device, token=$token")
//                requestPermission(device, connectionCallback)
//                LogUtil.d(TAG, "requestPermission completed in connectToAoaDevice, token=$token")
//                return
//            }
//
//            // Token 检查：确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "connectToAoaDevice ignored: token changed before direct connect, token=$token")
//                return
//            }
//
//            // 直接连接
//            LogUtil.d(TAG, "AOA device has permission, calling connectToAoaDeviceDirectly, token=$token")
//            connectToAoaDeviceDirectly(session, device, connectionCallback)
//            LogUtil.d(TAG, "connectToAoaDeviceDirectly completed in connectToAoaDevice, token=$token")
//
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to connect to AOA device: ${e.message}, token=$token")
//            disconnectInternal(token, "connectToAoaDevice failed: ${e.message}")
//            throw e
//        }
//    }
//
//    /**
//     * 直接连接到AOA设备（已有权限）
//     *
//     * 注意：此方法应在 connectMutex.withLock { ... } 内调用，确保连接建立的原子性
//     *
//     * @param session 连接会话
//     * @param device AOA设备
//     * @param connectionCallback 连接回调
//     */
//    private suspend fun connectToAoaDeviceDirectly(session: UsbAoaSession, device: UsbDevice, connectionCallback: ConnectionCallback) {
//        val token = session.token
//
//        // 【修复 P1】幂等门禁：如果已经连接成功，直接返回
//        // 避免多条路径（轮询、广播、权限）重复触发连接，导致重复 open/close/claim
//        if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED && isSessionActive(session)) {
//            LogUtil.d(TAG, "connectToAoaDeviceDirectly ignored: already connected, token=$token")
//            return
//        }
//
//        // 【修复 P1】幂等门禁：如果 phase 已经是 RUNNING 或更高，说明连接已建立，直接返回
//        val currentPhase = session.phase.get()
//        if (currentPhase >= AoaSessionPhase.RUNNING) {
//            LogUtil.d(TAG, "connectToAoaDeviceDirectly ignored: phase already $currentPhase, token=$token")
//            return
//        }
//
//        // Token 检查：确保连接操作未切换
//        if (!isTokenActive(token)) {
//            LogUtil.w(TAG, "connectToAoaDeviceDirectly ignored: stale token=$token")
//            return
//        }
//
//        try {
//            LogUtil.d(TAG, "Connecting to AOA device: ${device.deviceName}, token=$token")
//
//            // Token 检查：openDevice 前再次检查
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "connectToAoaDeviceDirectly ignored: token changed before openDevice, token=$token")
//                return
//            }
//
//            val connection = usbManager.openDevice(device) ?: throw Exception("Failed to open AOA device")
//
//            // 查找AOA标准接口 (class=255, subclass=66, protocol=1)
//            var aoaInterface: UsbInterface? = null
//            for (i in 0 until device.interfaceCount) {
//                val usbInterface = device.getInterface(i)
//                if (usbInterface.interfaceClass == 255 &&
//                    usbInterface.interfaceSubclass == 66 &&
//                    usbInterface.interfaceProtocol == 1 &&
//                    usbInterface.endpointCount >= 2
//                ) {
//                    aoaInterface = usbInterface
//                    LogUtil.d(TAG, "Found AOA interface[$i]")
//                    break
//                }
//
//                if (aoaInterface == null &&
//                    usbInterface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
//                    usbInterface.endpointCount >= 2
//                ) {
//                    aoaInterface = usbInterface
//                }
//            }
//
//            // Token 检查：openDevice 后再次检查
//            if (!isTokenActive(token)) {
//                connection.close()
//                LogUtil.w(TAG, "connectToAoaDeviceDirectly ignored: token changed after openDevice, token=$token")
//                return
//            }
//
//            if (aoaInterface == null) {
//                connection.close()
//                throw Exception("No suitable interface found")
//            }
//
//            // Token 检查：claimInterface 前再次检查
//            if (!isTokenActive(token)) {
//                connection.close()
//                LogUtil.w(TAG, "connectToAoaDeviceDirectly ignored: token changed before claimInterface, token=$token")
//                return
//            }
//
//            // claimInterface 必须在 connectMutex 内执行（调用方已保证）
//            if (!connection.claimInterface(aoaInterface, true)) {
//                connection.close()
//                throw Exception("Failed to claim interface")
//            }
//
//            // Token 检查：claimInterface 后再次检查
//            if (!isTokenActive(token)) {
//                connection.releaseInterface(aoaInterface)
//                connection.close()
//                LogUtil.w(TAG, "connectToAoaDeviceDirectly ignored: token changed after claimInterface, token=$token")
//                return
//            }
//
//            // 查找端点
//            var inEndpoint: UsbEndpoint? = null
//            var outEndpoint: UsbEndpoint? = null
//
//            for (i in 0 until aoaInterface.endpointCount) {
//                val endpoint = aoaInterface.getEndpoint(i)
//                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
//                    if (endpoint.direction == UsbConstants.USB_DIR_IN) {
//                        inEndpoint = endpoint
//                    } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
//                        outEndpoint = endpoint
//                    }
//                }
//            }
//
//            if (outEndpoint == null) {
//                connection.releaseInterface(aoaInterface)
//                connection.close()
//                throw Exception("No bulk OUT endpoint found")
//            }
//
//            // Token 检查：保存连接信息前再次检查，这是最后的检查点
//            if (!isTokenActive(token)) {
//                connection.releaseInterface(aoaInterface)
//                connection.close()
//                LogUtil.w(TAG, "connectToAoaDeviceDirectly ignored: token changed before saving connection, token=$token")
//                return
//            }
//
//            // 保存连接信息
//            this.usbDeviceConnection = connection
//            this.usbInterface = aoaInterface
//            this.usbEndpointIn = inEndpoint
//            this.usbEndpointOut = outEndpoint
//            this.connectedDevice = device
//
//            // 初始化HexFrame缓冲区
//            hexFrameBuffer = HexFrameBuffer(
//                scope = scope,
//                onFrameReceived = { frame ->
//                    dataReceiver?.invoke(frame)
//                }
//            )
//
//            LogUtil.d(
//                TAG,
//                "AOA device connected, IN: ${
//                    inEndpoint?.let {
//                        String.format(
//                            "0x%02X",
//                            it.address
//                        )
//                    } ?: "none"
//                }, OUT: ${String.format("0x%02X", outEndpoint.address)}, token=$token")
//
//            // 更新 phase 为 OPENING_IO
//            session.phase.set(AoaSessionPhase.OPENING_IO)
//
//            // 【修复 P0-2】Host 模式使用 bulkTransfer，不需要真正的 InputStream/OutputStream
//            // 使用 null 避免创建假流导致误判 EOF
//            val ioBundle = AoaIoBundle(connection, null, null)
//            session.io.set(ioBundle)
//
//            // 更新 phase 为 RUNNING
//            session.phase.set(AoaSessionPhase.RUNNING)
//
//            // 重要：先更新状态为 CONNECTED，确保接收循环能立即开始读取数据
//            updateStatus(InnerConnectionStatus.CONNECTED)
//
//            // 标记连接已建立（用于错误处理的grace period）
//            cableAoaErrorHandler.markConnectionEstablished()
//
//            // 启动数据接收（Host 模式使用 bulkTransfer，不依赖 inputStream）
//            if (inEndpoint != null) {
//                startDataReceive(session, null) // 传入 null，因为 Host 模式不使用 stream
//            }
//
//            // 通知连接成功（handleConnectionSuccess 内部也会更新状态，但我们已经提前更新了）
//            handleConnectionSuccess(connectionCallback, null)
//
//            // 后台验证连接
//            scope.launch {
//                delay(1000)
//                if (!validateConnectionState()) {
//                    LogUtil.w(TAG, "Connection validation failed")
//                }
//            }
//
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to connect: ${e.message}")
//            disconnectInternal(token, "connectToAoaDeviceDirectly failed: ${e.message}")
//            throw e
//        }
//    }
//
//    /**
//     * 连接到AOA Accessory
//     *
//     * 注意：此方法应在 connectMutex.withLock { ... } 内调用，确保连接建立的原子性
//     *
//     * @param session 连接会话
//     * @param accessory AOA Accessory
//     * @param connectionCallback 连接回调
//     */
//    private suspend fun connectToAoaAccessory(session: UsbAoaSession, accessory: UsbAccessory, connectionCallback: ConnectionCallback) {
//        val token = session.token
//        LogUtil.i(TAG, "=== connectToAoaAccessory started, accessory=${getAccessoryDisplayKey(accessory)}, token=$token ===")
//        // Token 检查：确保连接操作未切换
//        if (!isTokenActive(token)) {
//            LogUtil.w(TAG, "connectToAoaAccessory ignored: stale token=$token")
//            return
//        }
//
//        try {
//            LogUtil.i(TAG, "Attempting to connect to AOA accessory: ${getAccessoryDisplayKey(accessory)}, token=$token")
//
//            // 检查权限
//            val hasPermission = usbManager.hasPermission(accessory)
//            LogUtil.i(TAG, "Accessory permission check: hasPermission=$hasPermission, token=$token")
//            if (!hasPermission) {
//                LogUtil.w(TAG, "Requesting permission for AOA accessory, token=$token")
//                // 更新 phase 为 REQUEST_AOA_PERMISSION
//                session.phase.set(AoaSessionPhase.REQUEST_AOA_PERMISSION)
//                requestAccessoryPermission(accessory, connectionCallback)
//                return
//            }
//
//            // Token 检查：确保连接操作未切换
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "connectToAoaAccessory ignored: token changed before direct connect, token=$token")
//                return
//            }
//
//            // 直接连接
//            LogUtil.i(TAG, "Calling connectToAoaAccessoryDirectly, token=$token")
//            connectToAoaAccessoryDirectly(session, accessory, connectionCallback)
//            LogUtil.i(TAG, "connectToAoaAccessoryDirectly returned, token=$token")
//
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to connect to AOA accessory: ${e.message}, token=$token")
//            disconnectInternal(token, "connectToAoaAccessory failed: ${e.message}")
//            throw e
//        }
//    }
//
//    /**
//     * 直接连接到AOA Accessory（已有权限）
//     *
//     * 注意：此方法应在 connectMutex.withLock { ... } 内调用，确保连接建立的原子性
//     *
//     * @param session 连接会话
//     * @param accessory AOA Accessory
//     * @param connectionCallback 连接回调
//     */
//    private suspend fun connectToAoaAccessoryDirectly(session: UsbAoaSession, accessory: UsbAccessory, connectionCallback: ConnectionCallback) {
//        val token = session.token
//        LogUtil.i(TAG, "=== connectToAoaAccessoryDirectly started, accessory=${getAccessoryDisplayKey(accessory)}, token=$token ===")
//
//        // 【修复 P1】幂等门禁：如果已经连接成功，直接返回
//        // 避免多条路径（轮询、广播、权限）重复触发连接，导致重复 open/close
//        if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED && isSessionActive(session)) {
//            LogUtil.i(TAG, "connectToAoaAccessoryDirectly ignored: already connected, token=$token")
//            return
//        }
//
//        // 【修复 P1】幂等门禁：如果 phase 已经是 RUNNING 或更高，说明连接已建立，直接返回
//        val currentPhase = session.phase.get()
//        if (currentPhase >= AoaSessionPhase.RUNNING) {
//            LogUtil.i(TAG, "connectToAoaAccessoryDirectly ignored: phase already $currentPhase, token=$token")
//            return
//        }
//
//        // Token 检查：确保连接操作未切换
//        if (!isTokenActive(token)) {
//            LogUtil.w(TAG, "connectToAoaAccessoryDirectly ignored: stale token=$token")
//            return
//        }
//
//        try {
//            LogUtil.i(TAG, "Connecting to AOA accessory: ${getAccessoryDisplayKey(accessory)}, token=$token")
//
//            // Token 检查：openAccessory 前再次检查
//            if (!isTokenActive(token)) {
//                LogUtil.w(TAG, "connectToAoaAccessoryDirectly ignored: token changed before openAccessory, token=$token")
//                return
//            }
//
//            // 打开 Accessory 连接
//            val fileDescriptor = usbManager.openAccessory(accessory) ?: throw Exception("Failed to open AOA accessory")
//
//            // Token 检查：openAccessory 后再次检查
//            if (!isTokenActive(token)) {
//                fileDescriptor.close()
//                LogUtil.w(TAG, "connectToAoaAccessoryDirectly ignored: token changed after openAccessory, token=$token")
//                return
//            }
//
//            // 创建输入输出流
//            val fd = fileDescriptor.fileDescriptor
//            val inputStream = java.io.FileInputStream(fd)
//            val outputStream = java.io.FileOutputStream(fd)
//
//            // Token 检查：保存连接信息前再次检查，这是最后的检查点
//            if (!isTokenActive(token)) {
//                inputStream.close()
//                outputStream.close()
//                fileDescriptor.close()
//                LogUtil.w(TAG, "connectToAoaAccessoryDirectly ignored: token changed before saving connection, token=$token")
//                return
//            }
//
//            // 保存连接信息
//            this.accessoryFileDescriptor = fileDescriptor
//            this.connectedAccessory = accessory
//
//            // 初始化HexFrame缓冲区
//            hexFrameBuffer = HexFrameBuffer(
//                scope = scope,
//                onFrameReceived = { frame ->
//                    dataReceiver?.invoke(frame)
//                }
//            )
//
//            LogUtil.d(TAG, "AOA accessory connected, token=$token")
//
//            // 更新 phase 为 OPENING_IO
//            session.phase.set(AoaSessionPhase.OPENING_IO)
//
//            // 创建 IO Bundle
//            val ioBundle = AoaIoBundle(null, inputStream, outputStream) // Accessory 模式不需要 UsbDeviceConnection
//            session.io.set(ioBundle)
//
//            // 更新 phase 为 RUNNING
//            session.phase.set(AoaSessionPhase.RUNNING)
//
//            // 重要：先更新状态为 CONNECTED，确保接收循环能立即开始读取数据
//            updateStatus(InnerConnectionStatus.CONNECTED)
//
//            // 启动数据接收
//            startDataReceive(session, inputStream)
//
//            // 通知连接成功
//            handleConnectionSuccess(connectionCallback, null)
//
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to connect: ${e.message}")
//            disconnectInternal(token, "connectToAoaAccessoryDirectly failed: ${e.message}")
//            throw e
//        }
//    }
//
//    /**
//     * 验证连接状态
//     */
//    private fun validateConnectionState(): Boolean {
//        return try {
//            // 检查设备连接状态
//            val isDeviceConnected = connectedDevice != null &&
//                    usbDeviceConnection != null &&
//                    usbInterface != null
//
//            // 检查端点配置
//            val isEndpointValid = usbEndpointIn != null && usbEndpointOut != null
//
//            // 检查端点配置是否符合AOA标准
//            val isEndpointConfigValid = validateEndpointConfiguration()
//
//            LogUtil.d(TAG, "Connection validation:")
//            LogUtil.d(TAG, "  Device connected: $isDeviceConnected")
//            LogUtil.d(TAG, "  Endpoints valid: $isEndpointValid")
//            LogUtil.d(TAG, "  Endpoint config valid: $isEndpointConfigValid")
//
//            isDeviceConnected && isEndpointValid && isEndpointConfigValid
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Connection validation error: ${e.message}")
//            false
//        }
//    }
//
//    /**
//     * 验证端点配置一致性
//     */
//    private fun validateEndpointConfiguration(): Boolean {
//        val interface1 = usbInterface
//        if (interface1 == null) {
//            LogUtil.w(TAG, "USB interface is null")
//            return false
//        }
//
//        LogUtil.d(TAG, "Validating endpoint configuration:")
//        LogUtil.d(TAG, "  Interface class: ${interface1.interfaceClass} (expected: 255)")
//        LogUtil.d(TAG, "  Interface subclass: ${interface1.interfaceSubclass} (expected: 66)")
//        LogUtil.d(TAG, "  Interface protocol: ${interface1.interfaceProtocol} (expected: 1)")
//        LogUtil.d(TAG, "  OUT endpoint: ${String.format("0x%02X", usbEndpointOut?.address ?: 0)}")
//        LogUtil.d(TAG, "  IN endpoint: ${String.format("0x%02X", usbEndpointIn?.address ?: 0)}")
//
//        // 验证接口配置 (AOA标准接口)
//        val isInterfaceValid = interface1.interfaceClass == 255 &&
//                interface1.interfaceSubclass == 66 &&
//                interface1.interfaceProtocol == 1
//
//        // 【修复 P0-1】只验证端点类型和方向，不验证具体地址
//        // 端点地址可能因设备实现而异，只要类型正确即可
//        val isEndpointTypeValid = usbEndpointOut != null &&
//                usbEndpointOut!!.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
//                usbEndpointOut!!.direction == UsbConstants.USB_DIR_OUT &&
//                usbEndpointIn != null &&
//                usbEndpointIn!!.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
//                usbEndpointIn!!.direction == UsbConstants.USB_DIR_IN
//
//        // 端点地址仅作为日志提示，不作为验证条件
//        if (usbEndpointOut?.address != 0x02 || usbEndpointIn?.address != 0x82) {
//            LogUtil.w(TAG, "Endpoint addresses differ from common AOA values (0x02/0x82), but this is acceptable")
//        }
//
//        return isInterfaceValid && isEndpointTypeValid
//    }
//
//    /**
//     * 重写数据接收方法 - Host端使用Bulk传输接收数据
//     * 集成AoaErrorHandler进行智能错误处理
//     *
//     * 注意：USB Host 模式使用 bulkTransfer，不通过 InputStream
//     * 但为了兼容接口，我们接收 inputStream 参数（实际不使用）
//     */
//    /**
//     * 启动数据接收循环
//     *
//     * 【修复 P0-5】根据连接类型分支处理：
//     * - Accessory 模式：使用 InputStream 读取
//     * - Host Bulk 模式：使用 bulkTransfer 读取
//     */
//    override fun startDataReceive(session: UsbAoaSession, inputStream: InputStream?) {
//        session.scope.launch {
//            // 【修复 P0-5】根据连接类型选择接收方式
//            val io = session.io.get()
//            val isAccessoryMode = io?.connection == null && inputStream != null
//            val isHostBulkMode = usbDeviceConnection != null && usbEndpointIn != null
//
//            if (isAccessoryMode && inputStream != null) {
//                // Accessory 模式：使用 InputStream 读取
//                LogUtil.d(TAG, "Starting AOA data receive loop (Accessory模式 Stream读取)...")
//                val buffer = ByteArray(16384)
//
//                while (isActive && isSessionActive(session) &&
//                    currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED
//                ) {
//                    try {
//                        val bytesRead = inputStream.read(buffer)
//                        when {
//                            bytesRead > 0 -> {
//                                val data = buffer.copyOf(bytesRead)
//                                LogUtil.d(TAG, "AOA raw data received (Accessory端): $bytesRead bytes, ${InnerUtil.bytes2HexStr(data)}")
//                                dataReceiver?.invoke(data)
//                            }
//                            bytesRead == -1 -> {
//                                LogUtil.w(TAG, "InputStream reached EOF, breaking receive loop")
//                                break
//                            }
//                            else -> {
//                                delay(10) // 短暂延迟避免 CPU 占用过高
//                            }
//                        }
//                    } catch (e: Exception) {
//                        if (isSessionActive(session)) {
//                            LogUtil.e(TAG, "Error reading from Accessory stream: ${e.message}, exception: ${e.javaClass.simpleName}")
//                            // 检查是否是真正的断开
//                            if (e is java.io.IOException) {
//                                val isDeviceStillConnected = try {
//                                    usbManager.accessoryList?.isNotEmpty() == true
//                                } catch (e: Exception) {
//                                    false
//                                }
//                                if (!isDeviceStillConnected) {
//                                    LogUtil.w(TAG, "Accessory disconnected, breaking receive loop")
//                                    break
//                                }
//                            }
//                            delay(100) // 错误后短暂延迟
//                        } else {
//                            break
//                        }
//                    }
//                }
//                LogUtil.d(TAG, "Accessory receive loop ended")
//            } else if (isHostBulkMode) {
//                // Host Bulk 模式：使用 bulkTransfer 读取（原有逻辑）
//                val buffer = ByteArray(16384)
//
//                LogUtil.d(TAG, "Starting AOA data receive loop (Host端 Bulk传输)...")
//                LogUtil.d(TAG, "Current status: $currentInnerConnectionStatus, session token=${session.token}")
//
//                while (isActive && isSessionActive(session) &&
//                    currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED &&
//                    usbDeviceConnection != null && usbEndpointIn != null
//                ) {
//                    try {
//                        val bytesRead = usbDeviceConnection!!.bulkTransfer(
//                            usbEndpointIn, buffer, buffer.size, 500
//                        )
//
//                        when {
//                            bytesRead > 0 -> {
//                                cableAoaErrorHandler.resetErrorCount()
//                                val data = buffer.copyOf(bytesRead)
//                                LogUtil.d(TAG, "AOA raw data received (Host端): $bytesRead bytes, ${InnerUtil.bytes2HexStr(data)}")
//                                dataReceiver?.invoke(data)
//                            }
//
//                            bytesRead <= 0 -> {
//                                val isDeviceStillConnected = isDeviceStillInSystemList()
//                                val errorResult = cableAoaErrorHandler.handleReceiveError(
//                                    bytesRead, isDeviceStillConnected, currentInnerConnectionStatus, session.phase.get()
//                                )
//
//                                LogUtil.d(
//                                    TAG, "Error handling result: shouldContinue=${errorResult.shouldContinue}, " +
//                                            "shouldBreak=${errorResult.shouldBreak}, delayMs=${errorResult.delayMs}"
//                                )
//
//                                errorResult.newStatus?.let { newStatus ->
//                                    updateStatus(newStatus)
//                                }
//
//                                if (errorResult.shouldBreak) {
//                                    LogUtil.w(TAG, "Breaking receive loop due to error handling result")
//                                    break
//                                }
//
//                                if (!errorResult.shouldContinue) {
//                                    LogUtil.w(TAG, "Stopping receive loop due to error handling result")
//                                    break
//                                }
//
//                                if (errorResult.delayMs > 0) {
//                                    delay(errorResult.delayMs)
//                                }
//                            }
//                        }
//                    } catch (e: Exception) {
//                        val isDeviceStillConnected = isDeviceStillInSystemList()
//                        val errorResult = cableAoaErrorHandler.handleException(
//                            e, currentInnerConnectionStatus, isDeviceStillConnected
//                        )
//
//                        LogUtil.e(
//                            TAG, "Exception in receive loop: ${e.message}, " +
//                                    "shouldContinue=${errorResult.shouldContinue}, shouldBreak=${errorResult.shouldBreak}"
//                        )
//
//                        errorResult.newStatus?.let { newStatus ->
//                            updateStatus(newStatus)
//                        }
//
//                        if (errorResult.shouldBreak) {
//                            LogUtil.w(TAG, "Breaking receive loop due to exception handling result")
//                            break
//                        }
//
//                        if (!errorResult.shouldContinue) {
//                            LogUtil.w(TAG, "Stopping receive loop due to exception handling result")
//                            break
//                        }
//
//                        if (errorResult.delayMs > 0) {
//                            delay(errorResult.delayMs)
//                        }
//                    }
//                }
//                LogUtil.d(TAG, "Host bulk receive loop ended")
//            } else {
//                LogUtil.w(TAG, "startDataReceive: Unknown connection mode, cannot start receive loop. " +
//                        "isAccessoryMode=$isAccessoryMode, isHostBulkMode=$isHostBulkMode, " +
//                        "inputStream=${inputStream != null}, usbDeviceConnection=${usbDeviceConnection != null}, " +
//                        "usbEndpointIn=${usbEndpointIn != null}")
//            }
//        }
//    }
//
//    /**
//     * 实现Host端数据发送 - 临时移除HexFrame协议，直接发送原始数据
//     *
//     * 注意：conn 和 epOut 已在锁内读取，保证在发送过程中不会被 disconnect 清掉。
//     * 使用传入的参数，而不是直接访问共享字段。
//     */
//    override suspend fun sendDataToDevice(
//        conn: UsbDeviceConnection,
//        epOut: UsbEndpoint,
//        data: ByteArray,
//        expectedToken: Long
//    ): Boolean {
//        LogUtil.d(TAG, "Sending raw data: size=${data.size}, token: $expectedToken")
//        return sendDataToAoaDevice(conn, epOut, data, expectedToken)
//    }
//
//    /**
//     * 发送数据到AOA设备（使用Bulk传输）
//     * 集成AoaErrorHandler进行智能错误处理和重试
//     *
//     * 优化策略：快路径 + 失败再判定
//     * - 快路径：直接尝试 bulkTransfer（连接字段已在锁内检查并传递）
//     * - 失败判定：如果 bulkTransfer 返回负数，再做详细的 deviceList/permission 检查
//     *
//     * 注意：conn 和 epOut 已在锁内读取并传递，保证在发送过程中不会被 disconnect 清掉。
//     * 不再需要检查这些字段是否为 null，直接使用即可。
//     *
//     * @param conn USB 设备连接（已在锁内读取，保证在发送过程中不会被清空）
//     * @param epOut USB 输出端点（已在锁内读取，保证在发送过程中不会被清空）
//     * @param data 要发送的数据
//     * @param expectedToken 期望的操作令牌，用于验证连接未切换
//     */
//    private suspend fun sendDataToAoaDevice(
//        conn: UsbDeviceConnection,
//        epOut: UsbEndpoint,
//        data: ByteArray,
//        expectedToken: Long
//    ): Boolean {
//        // Token 校验：如果 token 不匹配，说明连接已切换，直接失败
//        if (!isTokenActive(expectedToken)) {
//            LogUtil.w(
//                TAG,
//                "Send data failed: operation token invalid (expected: $expectedToken, current: ${currentOpToken()}), connection may be switched"
//            )
//            return false
//        }
//
//        // 注意：conn 和 epOut 已在锁内检查并传递，不需要再次检查 null
//
//        // 发送前添加短暂延迟，确保设备状态稳定
//        delay(20) // 20ms延迟，确保设备状态稳定
//
//        // 使用AoaErrorHandler进行智能重试
//        val maxRetries = 3
//
//        repeat(maxRetries) { attempt ->
//            try {
//                LogUtil.d(TAG, "Attempting to send data (attempt ${attempt + 1}/$maxRetries): size=${data.size} bytes")
//
//                // 再次检查 token，确保连接未切换
//                if (!isTokenActive(expectedToken)) {
//                    LogUtil.w(
//                        TAG,
//                        "Send data failed: operation token changed during send (expected: $expectedToken, current: ${currentOpToken()}), connection switched"
//                    )
//                    return false
//                }
//
//                // 快路径：直接尝试 bulkTransfer（使用传入的参数，而不是共享字段）
//                val startTime = System.currentTimeMillis()
//                val result = conn.bulkTransfer(
//                    epOut, data, data.size, 5000 // 5秒超时
//                )
//                val transferTime = System.currentTimeMillis() - startTime
//
//                LogUtil.d(TAG, "Bulk transfer result: $result (expected: ${data.size}), transferTime=${transferTime}ms")
//
//                if (result == data.size) {
//                    // 发送成功，重置错误计数
//                    cableAoaErrorHandler.resetErrorCount()
//                    LogUtil.d(TAG, "AOA data sent successfully: ${data.size} bytes (attempt ${attempt + 1})")
//
//                    // 添加短暂的发送后延迟，确保Accessory端有时间处理数据
//                    delay(50) // 50ms延迟，让Accessory端处理数据
//                    return true
//                } else {
//                    // 发送失败：执行慢路径检查（失败再判定）
//                    LogUtil.w(TAG, "Bulk transfer failed with error code: $result")
//                    LogUtil.w(TAG, "Common error codes: -1=timeout, -2=pipe error, -3=no device")
//
//                    // 慢路径：检查设备连接状态和权限
//                    val deviceStillConnected = checkDeviceConnectionAndPermission()
//
//                    // 使用AoaErrorHandler处理发送错误
//                    val errorResult = cableAoaErrorHandler.handleSendError(
//                        result, data.size, attempt, maxRetries, deviceStillConnected
//                    )
//
//                    LogUtil.w(
//                        TAG, "Send error handling result (attempt ${attempt + 1}): " +
//                                "shouldContinue=${errorResult.shouldContinue}, " +
//                                "shouldBreak=${errorResult.shouldBreak}, delayMs=${errorResult.delayMs}, " +
//                                "deviceStillConnected=$deviceStillConnected"
//                    )
//
//                    // 根据错误处理结果更新状态
//                    errorResult.newStatus?.let { newStatus ->
//                        updateStatus(newStatus)
//                    }
//
//                    // 根据错误处理结果决定是否继续重试
//                    if (errorResult.shouldBreak) {
//                        LogUtil.w(TAG, "Breaking send retry due to error handling result")
//                        return false
//                    }
//
//                    if (!errorResult.shouldContinue) {
//                        LogUtil.w(TAG, "Stopping send retry due to error handling result")
//                        return false
//                    }
//
//                    // 应用延迟
//                    if (errorResult.delayMs > 0) {
//                        delay(errorResult.delayMs.toLong())
//                    }
//                }
//
//            } catch (e: Exception) {
//                LogUtil.e(TAG, "Exception during data send (attempt ${attempt + 1}): ${e.message}")
//
//                // 最后一次尝试时抛出异常
//                if (attempt == maxRetries - 1) {
//                    throw e
//                }
//
//                // 其他情况下短暂延迟后继续
//                delay(100)
//            }
//        }
//
//        LogUtil.e(TAG, "Failed to send data after $maxRetries attempts")
//        LogUtil.e(TAG, "Final error stats: ${cableAoaErrorHandler.getErrorStats()}")
//        return false
//    }
//
//
//    /**
//     * 检查设备是否仍在系统设备列表中
//     *
//     * 使用父类的 getDeviceKey() 方法进行稳定标识比对
//     *
//     * @return true 如果设备仍在系统列表中，false 否则
//     */
//    private fun isDeviceStillInSystemList(): Boolean {
//        return try {
//            val device = connectedDevice ?: return false
//            val deviceKey = getDeviceKey(device)
//            usbManager.deviceList.values.any { getDeviceKey(it) == deviceKey }
//        } catch (e: Exception) {
//            LogUtil.w(TAG, "Error checking device in system list: ${e.message}")
//            false
//        }
//    }
//
//    /**
//     * 慢路径：检查设备连接状态和权限（仅在发送失败时调用）
//     *
//     * @return true 如果设备仍然连接且有权限，false 否则
//     */
//    private fun checkDeviceConnectionAndPermission(): Boolean {
//        return try {
//            val device = connectedDevice
//            if (device == null) {
//                LogUtil.w(TAG, "Connected device is null")
//                return false
//            }
//
//            // 检查设备是否仍在设备列表中（使用稳定标识比对）
//            val deviceKey = getDeviceKey(device)
//            val currentDevice = usbManager.deviceList.values.find {
//                getDeviceKey(it) == deviceKey
//            }
//
//            if (currentDevice == null) {
//                LogUtil.w(TAG, "Device no longer in system list")
//                return false
//            }
//
//            // 检查USB设备权限
//            val hasPermission = usbManager.hasPermission(currentDevice)
//            if (!hasPermission) {
//                LogUtil.w(TAG, "USB device permission lost: ${getDeviceDisplayKey(currentDevice)}")
//            }
//
//            hasPermission
//        } catch (e: Exception) {
//            LogUtil.w(TAG, "Error checking device connection and permission: ${e.message}")
//            false
//        }
//    }
//
//    /**
//     * 清理资源
//     */
//    override fun release() {
//        LogUtil.d(TAG, "=== AOA Host Release Started ===")
//
//        try {
//            // 先断开连接
//            disconnect()
//
//            // 停止并清空HexFrame缓冲区
//            hexFrameBuffer?.stop()
//            hexFrameBuffer = null
//
//            // 调用基类的清理逻辑
//            super.release()
//
//            LogUtil.d(TAG, "AOA Host resources released successfully")
//            LogUtil.d(TAG, "Final error stats: ${cableAoaErrorHandler.getErrorStats()}")
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Error during AOA Host release: ${e.message}")
//            e.printStackTrace()
//        }
//
//        LogUtil.d(TAG, "=== AOA Host Release Finished ===")
//    }
//
//    companion object {
//        const val ACTION_USB_STANDARD_HOST_KERNEL = "com.sunmi.tapro.taplink.host.USB_PERMISSION"
//        const val REQUEST_CODE_FOR_USB_PERMISSION = 0
//
//        fun createRequestPermissionPendingIntent(context: Context): PendingIntent {
//            return PendingIntent.getBroadcast(
//                context, REQUEST_CODE_FOR_USB_PERMISSION,
//                Intent(ACTION_USB_STANDARD_HOST_KERNEL).apply {
//                    setPackage(context.packageName)
//                },
//                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
//            )
//        }
//    }
//}