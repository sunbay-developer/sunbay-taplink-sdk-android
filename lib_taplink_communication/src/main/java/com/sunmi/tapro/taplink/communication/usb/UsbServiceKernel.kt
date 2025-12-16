package com.sunmi.tapro.taplink.communication.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sunmi.tapro.taplink.communication.enums.ConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.UsbMode
import com.sunmi.tapro.taplink.communication.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.usb.accessory.UsbAccessoryKernel
import com.sunmi.tapro.taplink.communication.usb.host.UsbHostKernel
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.sunmi.tapro.taplink.communication.util.ProtocolParser
import com.sunmi.tapro.taplink.communication.util.ProtocolParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * USB服务内核类
 * 
 * 作为USB服务的统一入口，根据协议自动选择Host或Accessory模式：
 * - Host模式：Android设备作为USB主机，控制外部USB设备
 * - Accessory模式：Android设备作为USB配件，被外部USB主机控制
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class UsbServiceKernel(private val context: Context) : BaseServiceKernel() {
    private val TAG = "UsbServiceKernel"
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 权限请求相关
    private val USB_PERMISSION_ACTION = "com.sunmi.tapro.taplink.communication.USB_PERMISSION"
    private var permissionPendingIntent: PendingIntent? = null
    
    // Host和Accessory内核
    private var hostKernel: UsbHostKernel? = null
    private var accessoryKernel: UsbAccessoryKernel? = null
    
    // 当前使用的内核
    private var currentKernel: BaseServiceKernel? = null
    private var currentMode: UsbMode = UsbMode.HOST
    
    init {
        // 创建权限请求的PendingIntent
        val permissionIntent = Intent(USB_PERMISSION_ACTION).apply {
            setPackage(context.packageName)
        }
        permissionPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                0,
                permissionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getBroadcast(
                context,
                0,
                permissionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        
        // 初始化Host和Accessory内核
        permissionPendingIntent?.let { pendingIntent ->
            hostKernel = UsbHostKernel(context, USB_PERMISSION_ACTION, pendingIntent)
            accessoryKernel = UsbAccessoryKernel(context, USB_PERMISSION_ACTION, pendingIntent)
        }
    }
    
    /**
     * 实现IServiceKernel接口：连接USB设备
     * 
     * @param protocol 协议字符串
     *   - usb://host/deviceName 或 usb://host/deviceId - Host模式
     *   - usb://accessory - Accessory模式
     *   - usb://deviceName 或 usb://deviceId - Host模式（兼容旧格式）
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
        
        try {
            updateStatus(ConnectionStatus.CONNECTING)
            connectionCallback.onWaitingConnect()
            
            // 解析协议字符串
            val parseResult = ProtocolParser.parse(protocol)
            if (parseResult is ProtocolParseResult.Error) {
                handleConnectionError(parseResult.message, connectionCallback)
                return
            }
            
            if (parseResult !is ProtocolParseResult.UsbProtocol) {
                handleConnectionError("Protocol type mismatch, expected usb protocol", connectionCallback)
                return
            }
            
            currentMode = parseResult.mode
            
            // 断开之前的连接
            currentKernel?.disconnect()
            currentKernel = null
            
            // 根据模式选择对应的内核
            when (parseResult.mode) {
                UsbMode.HOST -> {
                    val kernel = hostKernel ?: run {
                        handleConnectionError("Host kernel not initialized", connectionCallback)
                        return
                    }
                    currentKernel = kernel
                    kernel.connect(protocol, connectionCallback)
                }
                UsbMode.ACCESSORY -> {
                    val kernel = accessoryKernel ?: run {
                        handleConnectionError("Accessory kernel not initialized", connectionCallback)
                        return
                    }
                    currentKernel = kernel
                    kernel.connect(protocol, connectionCallback)
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect USB: ${e.message}")
            handleConnectionError(e.message ?: "Unknown error", connectionCallback)
        }
    }
    
    /**
     * 实现IServiceKernel接口：发送数据
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        val kernel = currentKernel
        if (kernel == null) {
            LogUtil.d(TAG, "No active kernel, cannot send data")
            callback?.onError("NO_ACTIVE_KERNEL", "No active USB kernel")
            return
        }
        
        when (kernel) {
            is UsbHostKernel -> kernel.sendData(traceId, data, callback)
            is UsbAccessoryKernel -> kernel.sendData(traceId, data, callback)
            else -> {
                LogUtil.e(TAG, "Unknown kernel type")
                callback?.onError("UNKNOWN_KERNEL_TYPE", "Unknown USB kernel type")
            }
        }
    }
    
    /**
     * 实现IServiceKernel接口：断开连接
     */
    override fun disconnect() {
        currentKernel?.disconnect()
        currentKernel = null
        updateStatus(ConnectionStatus.DISCONNECTED)
    }
    
    /**
     * 清理资源
     */
    fun release() {
        disconnect()
        
        hostKernel?.release()
        accessoryKernel?.release()
        
        hostKernel = null
        accessoryKernel = null
        currentKernel = null
        
        scope.cancel()
    }
    
    // ==================== Host模式公共方法 ====================
    
    /**
     * 获取所有连接的USB设备（Host模式）
     */
    fun getConnectedDevices(): Map<String, android.hardware.usb.UsbDevice> {
        return hostKernel?.getConnectedDevices() ?: emptyMap()
    }
    
    /**
     * 检查USB设备权限（Host模式）
     */
    fun hasPermission(device: android.hardware.usb.UsbDevice): Boolean {
        return hostKernel?.hasPermission(device) ?: false
    }
    
    /**
     * 获取当前连接的设备（Host模式）
     */
    fun getConnectedDevice(): android.hardware.usb.UsbDevice? {
        return hostKernel?.getConnectedDevice()
    }
    
    // ==================== Accessory模式公共方法 ====================
    
    /**
     * 获取所有连接的USB Accessory
     */
    fun getAccessoryList(): Array<android.hardware.usb.UsbAccessory>? {
        return accessoryKernel?.getAccessoryList()
    }
    
    /**
     * 获取当前连接的Accessory
     */
    fun getConnectedAccessory(): android.hardware.usb.UsbAccessory? {
        return accessoryKernel?.getConnectedAccessory()
    }
    
    /**
     * 获取当前USB模式
     */
    fun getCurrentMode(): UsbMode {
        return currentMode
    }
}
