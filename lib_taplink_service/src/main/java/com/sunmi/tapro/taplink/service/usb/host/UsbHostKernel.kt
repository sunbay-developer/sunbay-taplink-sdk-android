package com.sunmi.tapro.taplink.service.usb.host

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
import com.sunmi.tapro.log.CLog
import com.sunmi.tapro.taplink.service.enums.ConnectionStatus
import com.sunmi.tapro.taplink.service.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.service.interfaces.InnerCallback
import com.sunmi.tapro.taplink.service.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.service.util.ProtocolParser
import com.sunmi.tapro.taplink.service.util.ProtocolParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * USB Host模式内核类
 * 
 * 负责处理USB Host模式的所有逻辑：
 * - 设备查找和连接
 * - 权限请求
 * - 数据传输（发送和接收）
 * - 设备插拔监听
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class UsbHostKernel(
    private val context: Context,
    private val permissionAction: String,
    private val permissionPendingIntent: PendingIntent
) : BaseServiceKernel() {
    private val TAG = "UsbHostKernel"
    
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 连接相关
    private var connectedDevice: UsbDevice? = null
    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpointIn: UsbEndpoint? = null
    private var usbEndpointOut: UsbEndpoint? = null
    
    // 数据接收任务
    private var dataReceiveJob: Job? = null
    
    // 权限请求相关
    private var pendingConnectionCallback: ConnectionCallback? = null
    private var pendingDevice: UsbDevice? = null
    
    // 权限请求广播接收器
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (permissionAction == intent.action) {
                synchronized(this@UsbHostKernel) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // 权限授予
                        if (device != null && device == pendingDevice) {
                            CLog.d(TAG, "USB device permission granted: ${device.deviceName}")
                            pendingDevice = null
                            val callback = pendingConnectionCallback
                            pendingConnectionCallback = null
                            
                            if (callback != null) {
                                scope.launch {
                                    val success = connectDevice(device)
                                    if (success) {
                                        handleConnectionSuccess(callback, null)
                                    } else {
                                        handleConnectionError("Failed to connect USB device: ${device.deviceName}", callback)
                                    }
                                }
                            }
                        }
                    } else {
                        // 权限拒绝
                        CLog.d(TAG, "USB permission denied")
                        val callback = pendingConnectionCallback
                        pendingConnectionCallback = null
                        pendingDevice = null
                        
                        callback?.let {
                            handleConnectionError("USB permission denied", it)
                        }
                    }
                }
            }
        }
    }
    
    // USB设备插拔广播接收器
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        CLog.d(TAG, "USB device attached: ${it.deviceName}")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        CLog.d(TAG, "USB device detached: ${it.deviceName}")
                        if (it == connectedDevice) {
                            disconnectInternal()
                            updateStatus(ConnectionStatus.DISCONNECTED)
                        }
                        // 如果断开的是待处理的设备，清除待处理状态
                        if (it == pendingDevice) {
                            pendingDevice = null
                            pendingConnectionCallback?.let { callback ->
                                handleConnectionError("USB device detached before connection", callback)
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
            CLog.d(TAG, "USB permission receiver registered")
        } catch (e: Exception) {
            CLog.e(TAG, "Failed to register USB permission receiver: ${e.message}")
        }
        
        // 注册USB设备插拔广播接收器
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            context.registerReceiver(usbReceiver, filter)
            CLog.d(TAG, "USB broadcast receiver registered")
        } catch (e: Exception) {
            CLog.e(TAG, "Failed to register USB receiver: ${e.message}")
        }
    }
    
    /**
     * 实现IServiceKernel接口：连接USB设备（Host模式）
     * 
     * @param protocol 协议字符串
     * @param connectionCallback 连接回调
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "USB device is already connected or connecting. Current status: $currentConnectionStatus",
                connectionCallback
            )
            return
        }
        
        scope.launch {
            try {
                updateStatus(ConnectionStatus.CONNECTING)
                connectionCallback.onWaitingConnect()
                
                // 解析协议字符串
                val parseResult = ProtocolParser.parse(protocol)
                if (parseResult is ProtocolParseResult.Error) {
                    handleConnectionError(parseResult.message, connectionCallback)
                    return@launch
                }
                
                if (parseResult !is ProtocolParseResult.UsbProtocol) {
                    handleConnectionError("Protocol type mismatch, expected usb protocol", connectionCallback)
                    return@launch
                }
                
                // 从已连接的设备中查找匹配的设备
                val devices = usbManager.deviceList
                val device = when {
                    parseResult.deviceName != null -> {
                        devices.values.firstOrNull { it.deviceName == parseResult.deviceName }
                    }
                    parseResult.deviceId != null -> {
                        devices.values.firstOrNull { it.deviceId.toString() == parseResult.deviceId }
                    }
                    else -> {
                        devices.values.firstOrNull()
                    }
                }
                
                if (device == null) {
                    handleConnectionError("USB device not found", connectionCallback)
                    return@launch
                }
                
                // 检查权限
                if (usbManager.hasPermission(device)) {
                    // 已有权限，直接连接
                    val success = connectDevice(device)
                    if (success) {
                        handleConnectionSuccess(connectionCallback, null)
                    } else {
                        handleConnectionError("Failed to connect USB device: ${device.deviceName}", connectionCallback)
                    }
                } else {
                    // 没有权限，请求权限
                    CLog.d(TAG, "Requesting permission for USB device: ${device.deviceName}")
                    requestPermission(device, connectionCallback)
                }
            } catch (e: Exception) {
                CLog.e(TAG, "Failed to connect USB host device: ${e.message}")
                handleConnectionError(e.message ?: "Unknown error", connectionCallback)
            }
        }
    }
    
    /**
     * 请求设备权限
     */
    private fun requestPermission(device: UsbDevice, connectionCallback: ConnectionCallback) {
        synchronized(this) {
            pendingDevice = device
            pendingConnectionCallback = connectionCallback
            
            try {
                usbManager.requestPermission(device, permissionPendingIntent)
                CLog.d(TAG, "USB permission request sent for device: ${device.deviceName}")
            } catch (e: Exception) {
                CLog.e(TAG, "Failed to request USB permission: ${e.message}")
                pendingDevice = null
                pendingConnectionCallback = null
                handleConnectionError("Failed to request USB permission: ${e.message}", connectionCallback)
            }
        }
    }
    
    /**
     * 连接设备
     */
    private fun connectDevice(device: UsbDevice): Boolean {
        return try {
            if (!usbManager.hasPermission(device)) {
                CLog.e(TAG, "No permission for USB device: ${device.deviceName}")
                return false
            }
            
            // 打开设备连接
            val connection = usbManager.openDevice(device) ?: run {
                CLog.e(TAG, "Failed to open USB device")
                return false
            }
            
            // 查找可用的接口和端点
            var interfaceFound = false
            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                
                // 查找批量传输端点
                var endpointIn: UsbEndpoint? = null
                var endpointOut: UsbEndpoint? = null
                
                for (j in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(j)
                    when {
                        endpoint.direction == UsbConstants.USB_DIR_IN -> {
                            endpointIn = endpoint
                        }
                        endpoint.direction == UsbConstants.USB_DIR_OUT -> {
                            endpointOut = endpoint
                        }
                    }
                }
                
                if (endpointIn != null && endpointOut != null) {
                    // 声明接口
                    if (connection.claimInterface(usbInterface, true)) {
                        this.usbDeviceConnection = connection
                        this.usbInterface = usbInterface
                        this.usbEndpointIn = endpointIn
                        this.usbEndpointOut = endpointOut
                        this.connectedDevice = device
                        interfaceFound = true
                        
                        // 启动数据接收
                        startDataReceive()
                        
                        CLog.d(TAG, "USB host device connected: ${device.deviceName}")
                        break
                    }
                }
            }
            
            if (!interfaceFound) {
                connection.close()
                CLog.e(TAG, "No suitable USB interface found")
                return false
            }
            
            true
        } catch (e: Exception) {
            CLog.e(TAG, "Failed to connect USB host device: ${e.message}")
            false
        }
    }
    
    /**
     * 启动数据接收
     */
    private fun startDataReceive() {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch {
            val buffer = ByteArray(16384) // 16KB buffer
            
            while (isActive && currentConnectionStatus == ConnectionStatus.CONNECTED &&
                   usbDeviceConnection != null && 
                   usbEndpointIn != null) {
                try {
                    val bytesRead = usbDeviceConnection?.bulkTransfer(
                        usbEndpointIn,
                        buffer,
                        buffer.size,
                        1000 // 1秒超时
                    ) ?: -1
                    
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        CLog.d(TAG, "Host data received: $bytesRead bytes")
                        dataReceiver?.invoke(data)
                    } else if (bytesRead == -1) {
                        // 超时或错误，继续循环
                    }
                } catch (e: Exception) {
                    CLog.e(TAG, "Error receiving host data: ${e.message}")
                    if (currentConnectionStatus == ConnectionStatus.CONNECTED) {
                        updateStatus(ConnectionStatus.ERROR)
                    }
                    break
                }
            }
        }
    }
    
    /**
     * Host模式发送数据
     */
    private fun sendHostData(data: ByteArray): Boolean {
        return try {
            if (usbDeviceConnection == null || usbEndpointOut == null) {
                CLog.e(TAG, "USB host connection not established")
                return false
            }
            
            val bytesWritten = usbDeviceConnection?.bulkTransfer(
                usbEndpointOut,
                data,
                data.size,
                1000 // 1秒超时
            ) ?: -1
            
            if (bytesWritten == data.size) {
                CLog.d(TAG, "Host data sent: ${data.size} bytes")
                true
            } else {
                CLog.e(TAG, "Failed to send host data: bytesWritten=$bytesWritten, expected=${data.size}")
                false
            }
        } catch (e: Exception) {
            CLog.e(TAG, "Error sending host data: ${e.message}")
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
            usbInterface?.let { interface_ ->
                usbDeviceConnection?.releaseInterface(interface_)
            }
            usbDeviceConnection?.close()
            usbDeviceConnection = null
            usbInterface = null
            usbEndpointIn = null
            usbEndpointOut = null
            connectedDevice = null
            
            CLog.d(TAG, "USB host disconnected")
        } catch (e: Exception) {
            CLog.e(TAG, "Error during disconnect: ${e.message}")
        }
    }
    
    /**
     * 清理资源
     */
    fun release() {
        disconnect()
        
        // 清理待处理的状态
        synchronized(this) {
            pendingDevice = null
            pendingConnectionCallback = null
        }
        
        try {
            context.unregisterReceiver(permissionReceiver)
            CLog.d(TAG, "USB permission receiver unregistered")
        } catch (e: Exception) {
            CLog.e(TAG, "Error unregistering USB permission receiver: ${e.message}")
        }
        
        try {
            context.unregisterReceiver(usbReceiver)
            CLog.d(TAG, "USB broadcast receiver unregistered")
        } catch (e: Exception) {
            CLog.e(TAG, "Error unregistering USB receiver: ${e.message}")
        }
        
        scope.cancel()
    }
    
    // ==================== 公共方法 ====================
    
    /**
     * 获取所有连接的USB设备
     */
    fun getConnectedDevices(): Map<String, UsbDevice> {
        return usbManager.deviceList
    }
    
    /**
     * 检查USB设备权限
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }
    
    /**
     * 获取当前连接的设备
     */
    fun getConnectedDevice(): UsbDevice? {
        return connectedDevice
    }
    
    /**
     * 实现IServiceKernel接口：发送数据
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            CLog.d(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "Service not connected")
            return
        }
        
        scope.launch {
            try {
                val success = sendHostData(data)
                if (success) {
                    callback?.onResponse("SUCCESS")
                } else {
                    updateStatus(ConnectionStatus.ERROR)
                    callback?.onError("SEND_FAILED", "Failed to send data via USB Host")
                }
            } catch (e: Exception) {
                CLog.e(TAG, "Failed to send data: ${e.message}")
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

