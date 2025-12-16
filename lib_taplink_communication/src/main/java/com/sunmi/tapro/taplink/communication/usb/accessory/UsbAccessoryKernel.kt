package com.sunmi.tapro.taplink.communication.usb.accessory

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * USB Accessory模式内核类
 *
 * 负责处理USB Accessory模式的所有逻辑：
 * - Accessory查找和连接
 * - 权限请求
 * - 数据传输（发送和接收）
 * - Accessory插拔监听
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class UsbAccessoryKernel(
    private val context: Context,
    private val permissionAction: String,
    private val permissionPendingIntent: PendingIntent
) : BaseServiceKernel() {
    private val TAG = "UsbAccessoryKernel"

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 连接相关
    private var connectedAccessory: UsbAccessory? = null
    private var accessoryFileDescriptor: ParcelFileDescriptor? = null
    private var accessoryInputStream: FileInputStream? = null
    private var accessoryOutputStream: FileOutputStream? = null

    // 数据接收任务
    private var dataReceiveJob: Job? = null

    // 权限请求相关
    private var pendingConnectionCallback: ConnectionCallback? = null
    private var pendingAccessory: UsbAccessory? = null

    // 权限请求广播接收器
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (permissionAction == intent.action) {
                synchronized(this@UsbAccessoryKernel) {
                    val accessory: UsbAccessory? = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // 权限授予
                        if (accessory != null && accessory == pendingAccessory) {
                            LogUtil.d(TAG, "USB accessory permission granted: ${accessory.manufacturer} ${accessory.model}")
                            pendingAccessory = null
                            val callback = pendingConnectionCallback
                            pendingConnectionCallback = null

                            if (callback != null) {
                                scope.launch {
                                    val success = connectAccessory(accessory)
                                    if (success) {
                                        handleConnectionSuccess(callback, null)
                                    } else {
                                        handleConnectionError("Failed to connect USB accessory", callback)
                                    }
                                }
                            }
                        }
                    } else {
                        // 权限拒绝
                        LogUtil.d(TAG, "USB accessory permission denied")
                        val callback = pendingConnectionCallback
                        pendingConnectionCallback = null
                        pendingAccessory = null

                        callback?.let {
                            handleConnectionError("USB accessory permission denied", it)
                        }
                    }
                }
            }
        }
    }

    // USB Accessory插拔广播接收器
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                    val accessory: UsbAccessory? = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    accessory?.let {
                        LogUtil.d(TAG, "USB accessory attached: ${it.manufacturer} ${it.model}")
                    }
                }

                UsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                    val accessory: UsbAccessory? = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
                    accessory?.let {
                        LogUtil.d(TAG, "USB accessory detached: ${it.manufacturer} ${it.model}")
                        if (it == connectedAccessory) {
                            disconnectInternal()
                            updateStatus(ConnectionStatus.DISCONNECTED)
                        }
                        // 如果断开的是待处理的Accessory，清除待处理状态
                        if (it == pendingAccessory) {
                            pendingAccessory = null
                            pendingConnectionCallback?.let { callback ->
                                handleConnectionError("USB accessory detached before connection", callback)
                            }
                            pendingConnectionCallback = null
                        }
                    }
                }
            }
        }
    }

    init {
        // 注册权限请求广播接收器
        val permissionFilter = IntentFilter(permissionAction)
        try {
            context.registerReceiver(permissionReceiver, permissionFilter)
            LogUtil.d(TAG, "USB permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB permission receiver: ${e.message}")
        }

        // 注册USB Accessory插拔广播接收器
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
        }
        try {
            context.registerReceiver(usbReceiver, filter)
            LogUtil.d(TAG, "USB broadcast receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB receiver: ${e.message}")
        }
    }

    /**
     * 连接USB Accessory
     *
     * @param connectionCallback 连接回调
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "USB accessory is already connected or connecting. Current status: $currentConnectionStatus",
                connectionCallback
            )
            return
        }

        scope.launch {
            try {
                updateStatus(ConnectionStatus.CONNECTING)
                connectionCallback.onWaitingConnect()

                // 获取已连接的Accessory
                val accessories = usbManager.accessoryList
                val accessory = accessories?.firstOrNull()

                if (accessory == null) {
                    handleConnectionError("USB accessory not found", connectionCallback)
                    return@launch
                }

                // 检查权限（Accessory模式通常通过Intent Filter自动授权，但也可以手动检查）
                if (usbManager.hasPermission(accessory)) {
                    // 已有权限，直接连接
                    val success = connectAccessory(accessory)
                    if (success) {
                        handleConnectionSuccess(connectionCallback, null)
                    } else {
                        handleConnectionError("Failed to connect USB accessory", connectionCallback)
                    }
                } else {
                    // 没有权限，请求权限（虽然Accessory通常自动授权，但为了完整性也支持手动请求）
                    LogUtil.d(TAG, "Requesting permission for USB accessory: ${accessory.manufacturer} ${accessory.model}")
                    requestPermission(accessory, connectionCallback)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect USB accessory: ${e.message}")
                handleConnectionError(e.message ?: "Unknown error", connectionCallback)
            }
        }
    }

    /**
     * 请求Accessory权限
     */
    private fun requestPermission(accessory: UsbAccessory, connectionCallback: ConnectionCallback) {
        synchronized(this) {
            pendingAccessory = accessory
            pendingConnectionCallback = connectionCallback

            try {
                usbManager.requestPermission(accessory, permissionPendingIntent)
                LogUtil.d(TAG, "USB permission request sent for accessory: ${accessory.manufacturer} ${accessory.model}")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to request USB accessory permission: ${e.message}")
                pendingAccessory = null
                pendingConnectionCallback = null
                handleConnectionError("Failed to request USB accessory permission: ${e.message}", connectionCallback)
            }
        }
    }

    /**
     * 连接Accessory
     */
    private fun connectAccessory(accessory: UsbAccessory): Boolean {
        return try {
            // 打开Accessory连接
            val fileDescriptor = usbManager.openAccessory(accessory) ?: run {
                LogUtil.e(TAG, "Failed to open USB accessory")
                return false
            }

            accessoryFileDescriptor = fileDescriptor
            connectedAccessory = accessory

            // 创建输入输出流
            val fd = fileDescriptor.fileDescriptor
            accessoryInputStream = FileInputStream(fd)
            accessoryOutputStream = FileOutputStream(fd)

            // 启动数据接收
            startDataReceive()

            LogUtil.d(TAG, "USB accessory connected: ${accessory.manufacturer} ${accessory.model}")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect USB accessory: ${e.message}")
            false
        }
    }

    /**
     * 启动数据接收
     */
    private fun startDataReceive() {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(16384) // 16KB buffer

            while (isActive && currentConnectionStatus == ConnectionStatus.CONNECTED &&
                accessoryInputStream != null
            ) {
                try {
                    val bytesRead = accessoryInputStream?.read(buffer) ?: -1

                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        LogUtil.d(TAG, "Accessory data received: $bytesRead bytes")
                        dataReceiver?.invoke(data)
                    } else if (bytesRead == -1) {
                        // 流结束
                        LogUtil.d(TAG, "Accessory input stream closed")
                        break
                    }
                } catch (e: IOException) {
                    LogUtil.e(TAG, "Error receiving accessory data: ${e.message}")
                    if (currentConnectionStatus == ConnectionStatus.CONNECTED) {
                        updateStatus(ConnectionStatus.ERROR)
                    }
                    break
                }
            }
        }
    }

    /**
     * Accessory模式发送数据
     */
    private fun sendAccessoryData(data: ByteArray): Boolean {
        return try {
            if (accessoryOutputStream == null) {
                LogUtil.e(TAG, "USB accessory output stream not available")
                return false
            }

            accessoryOutputStream?.write(data)
            accessoryOutputStream?.flush()
            LogUtil.d(TAG, "Accessory data sent: ${data.size} bytes")
            true
        } catch (e: IOException) {
            LogUtil.e(TAG, "Error sending accessory data: ${e.message}")
            false
        }
    }

    /**
     * 断开连接（内部方法）
     */
    private fun disconnectInternal() {
        try {
            // 停止数据接收
            dataReceiveJob?.cancel()
            dataReceiveJob = null

            // 断开连接
            accessoryInputStream?.close()
            accessoryOutputStream?.close()
            accessoryFileDescriptor?.close()
            accessoryInputStream = null
            accessoryOutputStream = null
            accessoryFileDescriptor = null
            connectedAccessory = null

            LogUtil.d(TAG, "USB accessory disconnected")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during disconnect: ${e.message}")
        }
    }

    /**
     * 清理资源
     */
    fun release() {
        disconnect()

        // 清理待处理的状态
        synchronized(this) {
            pendingAccessory = null
            pendingConnectionCallback = null
        }

        try {
            context.unregisterReceiver(permissionReceiver)
            LogUtil.d(TAG, "USB permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB permission receiver: ${e.message}")
        }

        try {
            context.unregisterReceiver(usbReceiver)
            LogUtil.d(TAG, "USB broadcast receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB receiver: ${e.message}")
        }

        scope.cancel()
    }

    // ==================== 公共方法 ====================

    /**
     * 获取所有连接的USB Accessory
     */
    fun getAccessoryList(): Array<UsbAccessory>? {
        return usbManager.accessoryList
    }

    /**
     * 获取当前连接的Accessory
     */
    fun getConnectedAccessory(): UsbAccessory? {
        return connectedAccessory
    }

    /**
     * 实现IServiceKernel接口：发送数据
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.d(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "Service not connected")
            return
        }

        scope.launch {
            try {
                val success = sendAccessoryData(data)
                if (success) {
                    callback?.onResponse("SUCCESS")
                } else {
                    updateStatus(ConnectionStatus.ERROR)
                    callback?.onError("SEND_FAILED", "Failed to send data via USB Accessory")
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to send data: ${e.message}")
                updateStatus(ConnectionStatus.ERROR)
                callback?.onError("SEND_ERROR", e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 实现IServiceKernel接口：断开连接
     */
    override fun disconnect() {
        if (currentConnectionStatus == ConnectionStatus.DISCONNECTED) {
            return
        }

        disconnectInternal()
        updateStatus(ConnectionStatus.DISCONNECTED)
    }
}
