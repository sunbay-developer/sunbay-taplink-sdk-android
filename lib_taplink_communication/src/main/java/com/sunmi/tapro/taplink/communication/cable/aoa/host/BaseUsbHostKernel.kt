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

    // USB设备连接相关
    var connectedDevice: UsbDevice? = null
    var usbDeviceConnection: UsbDeviceConnection? = null
    protected var usbInterface: UsbInterface? = null
    protected var usbEndpointIn: UsbEndpoint? = null
    var usbEndpointOut: UsbEndpoint? = null

    // 权限请求相关
    protected var pendingDevice: UsbDevice? = null
    private var lastPermissionBroadcastTime = 0L
    private val PERMISSION_BROADCAST_DEBOUNCE_MS = 100L // 100ms内的重复广播将被忽略

    // 权限请求广播接收器
    protected val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.d(TAG, "USB device permission broadcast received: ${intent.action}")

            if (permissionAction == intent.action) {
                synchronized(this@BaseUsbHostKernel) {
                    val currentTime = System.currentTimeMillis()
                    
                    // 防抖处理：如果在很短时间内收到重复广播，忽略后续的
                    if (currentTime - lastPermissionBroadcastTime < PERMISSION_BROADCAST_DEBOUNCE_MS) {
                        LogUtil.w(TAG, "Ignoring duplicate permission broadcast within ${PERMISSION_BROADCAST_DEBOUNCE_MS}ms")
                        return
                    }
                    lastPermissionBroadcastTime = currentTime

                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    LogUtil.d(TAG, "USB device permission result:")
                    LogUtil.d(TAG, "  Device: ${device?.deviceName}")
                    LogUtil.d(TAG, "  Permission granted: $permissionGranted")
                    LogUtil.d(TAG, "  Pending device: ${pendingDevice?.deviceName}")

                    // 检查是否是重复的广播
                    if (device == null) {
                        LogUtil.w(TAG, "Received permission broadcast with null device, ignoring")
                        return
                    }

                    if (permissionGranted) {
                        // 权限授予
                        if (device == pendingDevice) {
                            LogUtil.d(TAG, "USB device permission granted: ${device.deviceName}")
                            val callback = currentConnectionCallback
                            
                            // 清空待处理设备，防止重复处理
                            pendingDevice = null

                            if (callback != null) {
                                scope.launch {
                                    onPermissionGranted(device, callback)
                                }
                            } else {
                                LogUtil.w(TAG, "Permission granted but no callback available")
                            }
                        } else if (pendingDevice == null) {
                            LogUtil.w(TAG, "Permission granted but no pending device (possible duplicate broadcast)")
                        } else {
                            LogUtil.w(TAG, "Permission granted for different device: expected ${pendingDevice?.deviceName}, got ${device.deviceName}")
                        }
                    } else {
                        // 权限拒绝
                        if (device == pendingDevice) {
                            LogUtil.d(TAG, "USB device permission denied by user: ${device.deviceName}")
                            val callback = currentConnectionCallback
                            pendingDevice = null

                            callback?.let {
                                handleConnectionError(InnerErrorCode.C49.description, it, InnerErrorCode.C49.code)
                            }
                        } else {
                            LogUtil.w(TAG, "Permission denied for non-pending device: ${device.deviceName}")
                        }
                    }
                }
            } else {
                LogUtil.d(TAG, "Received broadcast for different action: ${intent.action}")
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
                        LogUtil.d(TAG, "USB device attached: ${it.deviceName}")
                        onDeviceAttached(it)
                    }
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        LogUtil.d(TAG, "USB device detached: ${it.deviceName}")
                        
                        // 添加智能断开检测，避免误判临时断开
                        if (it == connectedDevice) {
                            LogUtil.w(TAG, "Connected device detached, starting delayed disconnect check")
                            
                            // 延迟检查，避免因为临时USB信号问题导致的误断开
                            scope.launch {
                                delay(300) // 等待300ms
                                
                                // 再次检查设备是否真的断开
                                val stillExists = try {
                                    usbManager.deviceList.values.any { dev ->
                                        dev.deviceName == it.deviceName
                                    }
                                } catch (e: Exception) {
                                    false
                                }
                                
                                if (!stillExists) {
                                    LogUtil.d(TAG, "Device confirmed disconnected after delay check")
                                    disconnectInternal()
                                    updateStatus(InnerConnectionStatus.DISCONNECTED)
                                } else {
                                    LogUtil.d(TAG, "Device still exists, ignoring detach event (possible temporary signal issue)")
                                }
                            }
                        }
                        
                        if (it == pendingDevice) {
                            pendingDevice = null
                            currentConnectionCallback?.let { callback ->
                                handleConnectionError(InnerErrorCode.C50.description, callback, InnerErrorCode.C50.code)
                            }
                        }
                        onDeviceDetached(it)
                    }
                }
            }
        }
    }

    init {
        // Host端不需要注册Accessory权限接收器，因为Host端使用设备权限
        // 只注册设备权限请求广播接收器
        val permissionFilter = IntentFilter(permissionAction)
        try {
            ContextCompat.registerReceiver(
                context,
                permissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_EXPORTED
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
     * 请求设备权限
     */
    protected fun requestPermission(device: UsbDevice, connectionCallback: ConnectionCallback) {
        synchronized(this) {
            pendingDevice = device
            currentConnectionCallback = connectionCallback

            try {
                usbManager.requestPermission(device, permissionPendingIntent)
                LogUtil.d(TAG, "USB permission request sent for device: ${device.deviceName}")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
                pendingDevice = null
                handleConnectionError("${InnerErrorCode.C51.description}: ${e.message}", connectionCallback, InnerErrorCode.C51.code)
            }
        }
    }

    /**
     * 断开连接（内部方法）
     */
    override fun disconnectInternal() {
        LogUtil.d(TAG, "=== USB Host Disconnect Internal Started ===")
        
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
            usbEndpointIn
            
            // 清理连接相关的变量
            usbDeviceConnection = null
            usbInterface = null
            usbEndpointIn = null
            usbEndpointOut = null
            
            connectedDevice?.let { device ->
                LogUtil.d(TAG, "Disconnected from USB device: ${device.deviceName}")
            }
            connectedDevice = null

            LogUtil.d(TAG, "AOA Host disconnected successfully")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during Host disconnect: ${e.message}")
            e.printStackTrace()
        }
        
        LogUtil.d(TAG, "=== USB Host Disconnect Internal Finished ===")
    }

    /**
     * 执行具体的发送数据逻辑（Host模式使用bulkTransfer）
     */
    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        try {
            val success = sendDataToDevice(data)
            if (!success) {
                updateStatus(InnerConnectionStatus.ERROR)
                callback?.onError(InnerErrorCode.C62.code, InnerErrorCode.C62.description)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to send data: ${e.message}")
            updateStatus(InnerConnectionStatus.ERROR)
            callback?.onError(InnerErrorCode.C63.code, e.message ?: InnerErrorCode.C63.description)
        }
    }

    /**
     * 发送数据到设备（使用bulkTransfer）
     */
    protected abstract fun sendDataToDevice(data: ByteArray): Boolean

    /**
     * 清理资源
     */
    override fun release() {
        // 清理待处理的状态
        synchronized(this) {
            pendingDevice = null
        }

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
    }
}