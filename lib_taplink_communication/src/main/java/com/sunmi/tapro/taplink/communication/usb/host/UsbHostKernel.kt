package com.sunmi.tapro.taplink.communication.usb.host

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
import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.util.ProtocolParser
import com.sunmi.tapro.taplink.communication.util.ProtocolParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * USB Host Mode Kernel Class
 * 
 * Responsible for handling all USB Host mode logic:
 * - Device discovery and connection
 * - Permission request
 * - Data transfer (send and receive)
 * - Device plug/unplug monitoring
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
    
    // Connection related
    private var connectedDevice: UsbDevice? = null
    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpointIn: UsbEndpoint? = null
    private var usbEndpointOut: UsbEndpoint? = null
    
    // Data receive job
    private var dataReceiveJob: Job? = null
    
    // Permission request related
    private var pendingConnectionCallback: ConnectionCallback? = null
    private var pendingDevice: UsbDevice? = null
    
    // Permission request broadcast receiver
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (permissionAction == intent.action) {
                synchronized(this@UsbHostKernel) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // Permission granted
                        if (device != null && device == pendingDevice) {
                            LogUtil.d(TAG, "USB device permission granted: ${device.deviceName}")
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
                        // Permission denied
                        LogUtil.d(TAG, "USB permission denied")
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
    
    // USB device plug/unplug broadcast receiver
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        LogUtil.d(TAG, "USB device attached: ${it.deviceName}")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        LogUtil.d(TAG, "USB device detached: ${it.deviceName}")
                        if (it == connectedDevice) {
                            disconnectInternal()
                            updateStatus(ConnectionStatus.DISCONNECTED)
                        }
                        // If disconnected device is pending, clear pending state
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
        // Register permission request broadcast receiver
        val permissionFilter = IntentFilter(permissionAction)
        try {
            context.registerReceiver(permissionReceiver, permissionFilter)
            LogUtil.d(TAG, "USB permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB permission receiver: ${e.message}")
        }
        
        // Register USB device attach/detach broadcast receiver
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        try {
            context.registerReceiver(usbReceiver, filter)
            LogUtil.d(TAG, "USB broadcast receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB receiver: ${e.message}")
        }
    }
    
    /**
     * Implement IServiceKernel interface: Connect to USB device (Host mode)
     * 
     * @param protocol Protocol string
     * @param connectionCallback Connection callback
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
                
                // Parse protocol string
                val parseResult = ProtocolParser.parse(protocol)
                if (parseResult is ProtocolParseResult.Error) {
                    handleConnectionError(parseResult.message, connectionCallback)
                    return@launch
                }
                
                if (parseResult !is ProtocolParseResult.UsbProtocol) {
                    handleConnectionError("Protocol type mismatch, expected usb protocol", connectionCallback)
                    return@launch
                }
                
                // Find matching device from connected devices
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
                
                // Check permission
                if (usbManager.hasPermission(device)) {
                    // Permission already granted, connect directly
                    val success = connectDevice(device)
                    if (success) {
                        handleConnectionSuccess(connectionCallback, null)
                    } else {
                        handleConnectionError("Failed to connect USB device: ${device.deviceName}", connectionCallback)
                    }
                } else {
                    // Permission not granted, request permission
                    LogUtil.d(TAG, "Requesting permission for USB device: ${device.deviceName}")
                    requestPermission(device, connectionCallback)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect USB host device: ${e.message}")
                handleConnectionError(e.message ?: "Unknown error", connectionCallback)
            }
        }
    }
    
    /**
     * Request device permission
     */
    private fun requestPermission(device: UsbDevice, connectionCallback: ConnectionCallback) {
        synchronized(this) {
            pendingDevice = device
            pendingConnectionCallback = connectionCallback
            
            try {
                usbManager.requestPermission(device, permissionPendingIntent)
                LogUtil.d(TAG, "USB permission request sent for device: ${device.deviceName}")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
                pendingDevice = null
                pendingConnectionCallback = null
                handleConnectionError("Failed to request USB permission: ${e.message}", connectionCallback)
            }
        }
    }
    
    /**
     * Connect to device
     */
    private fun connectDevice(device: UsbDevice): Boolean {
        return try {
            if (!usbManager.hasPermission(device)) {
                LogUtil.e(TAG, "No permission for USB device: ${device.deviceName}")
                return false
            }
            
            // Open device connection
            val connection = usbManager.openDevice(device) ?: run {
                LogUtil.e(TAG, "Failed to open USB device")
                return false
            }
            
            // Find available interfaces and endpoints
            var interfaceFound = false
            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                
                // Find bulk transfer endpoints
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
                    // Claim interface
                    if (connection.claimInterface(usbInterface, true)) {
                        this.usbDeviceConnection = connection
                        this.usbInterface = usbInterface
                        this.usbEndpointIn = endpointIn
                        this.usbEndpointOut = endpointOut
                        this.connectedDevice = device
                        interfaceFound = true
                        
                        // Start data receiving
                        startDataReceive()

                        LogUtil.d(TAG, "USB host device connected: ${device.deviceName}")
                        break
                    }
                }
            }
            
            if (!interfaceFound) {
                connection.close()
                LogUtil.e(TAG, "No suitable USB interface found")
                return false
            }
            
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect USB host device: ${e.message}")
            false
        }
    }
    
    /**
     * Start data receiving
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
                        LogUtil.d(TAG, "Host data received: $bytesRead bytes")
                        dataReceiver?.invoke(data)
                    } else if (bytesRead == -1) {
                        // Timeout or error, continue loop
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error receiving host data: ${e.message}")
                    if (currentConnectionStatus == ConnectionStatus.CONNECTED) {
                        updateStatus(ConnectionStatus.ERROR)
                    }
                    break
                }
            }
        }
    }
    
    /**
     * Send data in Host mode
     */
    private fun sendHostData(data: ByteArray): Boolean {
        return try {
            if (usbDeviceConnection == null || usbEndpointOut == null) {
                LogUtil.e(TAG, "USB host connection not established")
                return false
            }
            
            val bytesWritten = usbDeviceConnection?.bulkTransfer(
                usbEndpointOut,
                data,
                data.size,
                1000 // 1秒超时
            ) ?: -1
            
            if (bytesWritten == data.size) {
                LogUtil.d(TAG, "Host data sent: ${data.size} bytes")
                true
            } else {
                LogUtil.e(TAG, "Failed to send host data: bytesWritten=$bytesWritten, expected=${data.size}")
                false
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending host data: ${e.message}")
            false
        }
    }
    
    /**
     * Disconnect (internal method)
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

            LogUtil.d(TAG, "USB host disconnected")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during disconnect: ${e.message}")
        }
    }
    
    /**
     * Release resources
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
    
    // ==================== Public Methods ====================
    
    /**
     * Get all connected USB devices
     */
    fun getConnectedDevices(): Map<String, UsbDevice> {
        return usbManager.deviceList
    }
    
    /**
     * Check USB device permission
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }
    
    /**
     * Get currently connected device
     */
    fun getConnectedDevice(): UsbDevice? {
        return connectedDevice
    }
    
    /**
     * Implement IServiceKernel interface: Send data
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            LogUtil.d(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
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
                LogUtil.e(TAG, "Failed to send data: ${e.message}")
                updateStatus(ConnectionStatus.ERROR)
                callback?.onError("SEND_ERROR", e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Implement IServiceKernel interface: Disconnect
     */
    override fun disconnect() {
        if (currentConnectionStatus == ConnectionStatus.DISCONNECTED) {
            return
        }
        
        disconnectInternal()
        updateStatus(ConnectionStatus.DISCONNECTED)
    }
}

