package com.sunmi.tapro.taplink.communication.cable.aoa.accessory

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import com.sunmi.tapro.taplink.communication.cable.aoa.core.UsbAoaSession
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.service.protocol.usb.BaseUsbAccessoryKernel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * USB AOA Accessory模式内核类
 *
 * 负责处理USB AOA Accessory设备的连接和数据传输：
 * - 监听Accessory设备连接
 * - 连接Accessory设备
 * - 数据传输（发送和接收）
 *
 * 数据接收：
 * - 继承自 BaseUsbStandardKernel.startDataReceive()
 * - 在 connectToAccessory() 连接成功后自动启动
 * - 持续监听 accessoryInputStream，通过 dataReceiver 回调传递数据
 *
 * 数据发送：
 * - 继承自 BaseUsbStandardKernel.performSendData()
 * - 通过 sendData() 方法调用，写入 accessoryOutputStream
 *
 * 注意：此实现只负责连接Accessory设备，不包含监听/响应AOA协议命令的功能。
 * AOA协议命令（GET_PROTOCOL、SEND_STRING、START）应该由外部Host设备发送，
 * Android系统会自动处理这些命令并切换到Accessory模式。
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class UsbStandardAccessoryKernel(
    context: Context,
    usbStandardInfo: UsbStandardProtocol.UsbStandardInfo = UsbStandardProtocol.UsbStandardInfo(),
    permissionAction: String,
    permissionPendingIntent: PendingIntent,
) : BaseUsbAccessoryKernel("", "", context, usbStandardInfo, permissionAction, permissionPendingIntent) {

    override val TAG = "UsbAoaAccessoryKernel"
    private var currentResult: ProtocolParseResult.UsbProtocol? = null

    /**
     * Accessory连接时的回调
     */
    override fun onAccessoryAttached(accessory: UsbAccessory) {
        LogUtil.d(TAG, "System switched to Accessory mode: ${getAccessoryDisplayKey(accessory)}")

        // 检查是否是我们期望的Accessory
        val isExpectedAccessory = when {
            accessory.manufacturer == "SUNMI" && accessory.model == "TAPLINK_SDK" -> {
                LogUtil.d(TAG, "Exact match found for expected accessory")
                true
            }
            accessory.manufacturer?.contains("SUNMI", ignoreCase = true) == true -> {
                LogUtil.d(TAG, "Partial manufacturer match found: ${accessory.manufacturer}")
                true
            }
            accessory.model?.contains("TAPLINK", ignoreCase = true) == true -> {
                LogUtil.d(TAG, "Partial model match found: ${accessory.model}")
                true
            }
            else -> {
                LogUtil.w(TAG, "No exact match, but accepting accessory for debugging: ${getAccessoryDisplayKey(accessory)}")
                true
            }
        }

        if (isExpectedAccessory) {
            scope.launch {
                val callback = currentConnectionCallback
                if (callback != null) {
                    LogUtil.d(TAG, "Connecting to accessory...")
                    // 在 connectMutex 内执行连接建立
                    connectMutex.withLock {
                        val token = currentOpToken()
                        if (isTokenActive(token)) {
                            connectToAccessory(accessory, callback, token)
                        } else {
                            LogUtil.w(TAG, "onAccessoryAttached ignored: stale token=$token")
                        }
                    }
                } else {
                    LogUtil.w(TAG, "No connection callback available for attached accessory")
                }
            }
        } else {
            LogUtil.w(TAG, "Unexpected accessory attached: ${getAccessoryDisplayKey(accessory)}")
        }
    }

    /**
     * 权限授予后的回调
     */
    override suspend fun onPermissionGranted(accessory: UsbAccessory, callback: ConnectionCallback, token: Long) {
        // Token 检查：确保是当前连接操作的权限授予
        if (!isTokenActive(token)) {
            LogUtil.w(TAG, "onPermissionGranted ignored: stale token=$token")
            return
        }
        connectToAccessory(accessory, callback, token)
    }

    /**
     * 执行USB连接逻辑
     * Target设备等待Host发送AOA协议命令，然后系统自动切换到Accessory模式
     */
    override suspend fun performUsbConnect(
        parseResult: ProtocolParseResult.UsbProtocol,
        connectionCallback: ConnectionCallback,
        session: UsbAoaSession
    ) {
        val token = session.token
        currentResult = parseResult
        
        try {
            LogUtil.d(TAG, "Target device waiting for Host to initiate AOA protocol..., token=$token")

            // 在 connectMutex 内执行所有连接建立操作，确保原子性
            connectMutex.withLock {
                // Token 检查：确保连接操作未切换
                if (!isTokenActive(token)) {
                    LogUtil.w(TAG, "performUsbConnect ignored: token changed during lock acquisition, token=$token")
                    return@withLock
                }

                updateStatus(InnerConnectionStatus.CONNECTING)
                currentConnectionCallback = connectionCallback

                // 首先检查是否已经有Accessory存在
                val accessories = usbManager.accessoryList
                LogUtil.d(TAG, "Current accessory list size: ${accessories?.size ?: 0}")

                // 【修复】在 Accessory 模式下，如果当前没有 accessory，这是正常的
                // Android 设备需要等待 Host 端发送 AOA 协议来切换到 Accessory 模式
                // 所以应该进入等待状态，而不是立即返回错误
                if (accessories == null || accessories.isEmpty()) {
                    LogUtil.d(TAG, "No USB accessories found yet, waiting for Host to initiate AOA protocol...")
                    updateStatus(InnerConnectionStatus.WAITING_CONNECT)
                    // 不 return，继续执行等待逻辑
                } else {
                    accessories.forEachIndexed { index, acc ->
                        LogUtil.d(TAG, "  Existing Accessory[$index]: ${getAccessoryDisplayKey(acc)}")
                    }

                    val existingAccessory = accessories.find {
                        it.manufacturer == "SUNMI" && it.model == "TAPLINK_SDK"
                    }

                    if (existingAccessory != null) {
                        LogUtil.d(TAG, "Found existing Accessory: ${getAccessoryDisplayKey(existingAccessory)}")
                        connectToAccessory(existingAccessory, connectionCallback, token)
                        return@withLock
                    }

                    // 【修复 P0-2】有设备但不匹配，记录日志但继续等待（因为可能还未切换到 Accessory 模式）
                    LogUtil.d(TAG, "No matching Accessory found (${accessories.size} accessories exist but none match criteria), waiting for Host AOA protocol...")
                    updateStatus(InnerConnectionStatus.WAITING_CONNECT)
                }
            }

            // 等待系统切换到Accessory模式（在锁外等待，避免阻塞）
            // 无限等待，直到连接成功或用户主动取消
            val checkInterval = 500L
            var lastLogTime = System.currentTimeMillis()
            val logInterval = 5000L // 每5秒打印一次等待日志
            
            while (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING ||
                   currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) {
                // Token 检查：每次循环开始前检查
                if (!isTokenActive(token)) {
                    LogUtil.w(TAG, "performUsbConnect stopped: token changed, token=$token")
                    return
                }

                delay(checkInterval)

                // Token 检查：delay 后再次检查
                if (!isTokenActive(token)) {
                    LogUtil.w(TAG, "performUsbConnect stopped: token changed after delay, token=$token")
                    return
                }

                // 检查连接状态是否已改变（可能被用户取消）
                if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTING &&
                    currentInnerConnectionStatus != InnerConnectionStatus.WAITING_CONNECT) {
                    LogUtil.d(TAG, "Connection cancelled, stop waiting for Host AOA protocol")
                    return
                }

                // 检查是否有新的Accessory出现
                val currentAccessories = usbManager.accessoryList
                val matchingAccessory = currentAccessories?.find { acc ->
                    acc.manufacturer == "SUNMI" && acc.model == "TAPLINK_SDK"
                }

                if (matchingAccessory != null) {
                    LogUtil.d(TAG, "Found matching Accessory: ${getAccessoryDisplayKey(matchingAccessory)}")
                    // 在 connectMutex 内执行连接建立
                    connectMutex.withLock {
                        // Token 检查：进入锁后再次检查
                        if (!isTokenActive(token)) {
                            LogUtil.w(TAG, "performUsbConnect stopped: token changed in connectMutex, token=$token")
                            return@withLock
                        }
                        connectToAccessory(matchingAccessory, connectionCallback, token)
                    }
                    return
                }

                // 每5秒打印一次等待日志
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastLogTime >= logInterval) {
                    LogUtil.d(TAG, "Still waiting for Host AOA protocol... (token=$token)")
                    lastLogTime = currentTime
                }
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to wait for AOA protocol: ${e.message}")
            updateStatus(InnerConnectionStatus.DISCONNECTED)
            handleConnectionError(e.message ?: "Unknown error", connectionCallback, InnerErrorCode.E254.code)
        }
    }

    /**
     * 启动数据接收
     * 
     * @param session 连接会话
     * @param inputStream 输入流（Accessory 模式使用 FileInputStream，可能为 null）
     */
    override fun startDataReceive(session: UsbAoaSession, inputStream: java.io.InputStream?) {
        // 在 session.scope 内启动接收任务，确保 session 结束时自动取消
        session.scope.launch {
            // 优先使用传入的 inputStream，如果没有则使用 accessoryInputStream
            val stream = inputStream ?: accessoryInputStream
            if (stream == null) {
                LogUtil.w(TAG, "startDataReceive: No input stream available, cannot start receive loop")
                return@launch
            }
            
            val buffer = ByteArray(16384) // 16KB缓冲区
            LogUtil.d(TAG, "Starting AOA data receive loop (Accessory模式)..., session token=${session.token}")
            LogUtil.d(TAG, "Current status: $currentInnerConnectionStatus")

            while (isActive &&
                isSessionActive(session) &&
                (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED ||
                 currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) &&
                stream != null
            ) {
                try {
                    // 如果状态还是WAITING_CONNECT，先等待一下状态更新
                    if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED) {
                        LogUtil.d(TAG, "Waiting for connection status to become CONNECTED...")
                        delay(100)
                        continue
                    }

                    val bytesRead = stream.read(buffer)

                    when {
                        bytesRead == null -> {
                            // 流可能已关闭
                            LogUtil.w(TAG, "Input stream read returned null")
                            delay(100)
                            continue
                        }
                        bytesRead > 0 -> {
                            val data = buffer.copyOf(bytesRead)
                            LogUtil.d(TAG, "AOA data received (Accessory模式): $bytesRead bytes")
                            dataReceiver?.invoke(data)
                        }
                        bytesRead == -1 -> {
                            // 流结束，设备可能断开
                            LogUtil.w(TAG, "AOA input stream closed (EOF) - bytesRead=-1")
                            val isStillConnected = try {
                                val systemAccessories = usbManager.accessoryList
                                systemAccessories?.any { isSameAccessory(it, connectedAccessory) } ?: false
                            } catch (e: Exception) {
                                LogUtil.e(TAG, "Error checking accessory connection: ${e.message}")
                                false
                            }

                            if (!isStillConnected) {
                                LogUtil.w(TAG, "Accessory no longer in system list, disconnecting...")
                                updateStatus(InnerConnectionStatus.DISCONNECTED)
                                break
                            } else {
                                LogUtil.w(TAG, "Accessory still in system list, but stream returned EOF - possible stream issue")
                                delay(100)
                                continue
                            }
                        }
                        else -> {
                            // bytesRead == 0，继续读取
                            delay(100)
                        }
                    }
                } catch (ex: Exception) {
                    LogUtil.e(TAG, "Exception in AOA data receive: ${ex.message}")
                    ex.printStackTrace()
                    
                    // 检查是否是永久性断开错误
                    val errorMessage = ex.message?.lowercase() ?: ""
                    if (errorMessage.contains("no such device") ||
                        errorMessage.contains("broken pipe") ||
                        errorMessage.contains("device not found") ||
                        errorMessage.contains("connection reset")
                    ) {
                        LogUtil.w(TAG, "Permanent disconnection error detected, stopping receive")
                        updateStatus(InnerConnectionStatus.DISCONNECTED)
                        break
                    }
                    
                    // 临时错误，延迟后继续
                    delay(200)
                }
            }
            LogUtil.d(TAG, "AOA data receive loop ended (Accessory模式), session token=${session.token}")
            LogUtil.d(TAG, "Final status: $currentInnerConnectionStatus")
        }
    }

    companion object {
        const val ACTION_USB_STANDARD_ACCESSORY_KERNEL = "com.sunmi.tapro.taplink.accessory.USB_PERMISSION"
        const val REQUEST_CODE_FOR_USB_ACCESSORY_PERMISSION = 1

        fun createRequestPermissionPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_FOR_USB_ACCESSORY_PERMISSION,
                Intent(ACTION_USB_STANDARD_ACCESSORY_KERNEL)
                    .apply {
                        setPackage(context.packageName)
                    },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }
    }

}
