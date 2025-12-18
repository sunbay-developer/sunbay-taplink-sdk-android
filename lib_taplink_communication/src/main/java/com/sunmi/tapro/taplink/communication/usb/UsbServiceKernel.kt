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
 * USB Service Kernel Class
 * 
 * Serves as the unified entry point for USB services, automatically selects Host or Accessory mode based on protocol:
 * - Host mode: Android device acts as USB host, controlling external USB devices
 * - Accessory mode: Android device acts as USB accessory, controlled by external USB host
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class UsbServiceKernel(private val context: Context) : BaseServiceKernel() {
    private val TAG = "UsbServiceKernel"
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Permission request related
    private val USB_PERMISSION_ACTION = "com.sunmi.tapro.taplink.communication.USB_PERMISSION"
    private var permissionPendingIntent: PendingIntent? = null
    
    // Host and Accessory kernels
    private var hostKernel: UsbHostKernel? = null
    private var accessoryKernel: UsbAccessoryKernel? = null
    
    // Currently used kernel
    private var currentKernel: BaseServiceKernel? = null
    private var currentMode: UsbMode = UsbMode.HOST
    
    init {
        // Create PendingIntent for permission request
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
        
        // Initialize Host and Accessory kernels
        permissionPendingIntent?.let { pendingIntent ->
            hostKernel = UsbHostKernel(context, USB_PERMISSION_ACTION, pendingIntent)
            accessoryKernel = UsbAccessoryKernel(context, USB_PERMISSION_ACTION, pendingIntent)
        }
    }
    
    /**
     * Implement IServiceKernel interface: Connect to USB device
     * 
     * Communication Flow:
     * 1. Check if connection is allowed (not already connected/connecting)
     * 2. Update status to CONNECTING and notify callback
     * 3. Parse protocol string to determine USB mode and parameters
     * 4. Validate protocol type is USB protocol
     * 5. Set current USB mode based on protocol
     * 6. Disconnect any existing connection if present
     * 7. Select appropriate kernel based on USB mode:
     *    a. Host mode: Use UsbHostKernel
     *    b. Accessory mode: Use UsbAccessoryKernel
     * 8. Call connect method of selected kernel
     * 
     * @param protocol Protocol string
     *   - usb://host/deviceName or usb://host/deviceId - Host mode
     *   - usb://accessory - Accessory mode
     *   - usb://deviceName or usb://deviceId - Host mode (compatible with old format)
     * @param connectionCallback Connection callback to notify status changes
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
            
            // Parse protocol string to determine USB mode and parameters
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
            
            // Disconnect any existing connection
            currentKernel?.disconnect()
            currentKernel = null
            
            // Select corresponding kernel based on USB mode
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
     * Implement IServiceKernel interface: Send data
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
     * Implement IServiceKernel interface: Disconnect
     */
    override fun disconnect() {
        currentKernel?.disconnect()
        currentKernel = null
        updateStatus(ConnectionStatus.DISCONNECTED)
    }
    
    /**
     * Release resources
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
    
    // ==================== Host Mode Public Methods ====================
    
    /**
     * Get all connected USB devices (Host mode)
     */
    fun getConnectedDevices(): Map<String, android.hardware.usb.UsbDevice> {
        return hostKernel?.getConnectedDevices() ?: emptyMap()
    }
    
    /**
     * Check USB device permission (Host mode)
     */
    fun hasPermission(device: android.hardware.usb.UsbDevice): Boolean {
        return hostKernel?.hasPermission(device) ?: false
    }
    
    /**
     * Get currently connected device (Host mode)
     */
    fun getConnectedDevice(): android.hardware.usb.UsbDevice? {
        return hostKernel?.getConnectedDevice()
    }
    
    // ==================== Accessory Mode Public Methods ====================
    
    /**
     * Get all connected USB Accessories
     */
    fun getAccessoryList(): Array<android.hardware.usb.UsbAccessory>? {
        return accessoryKernel?.getAccessoryList()
    }
    
    /**
     * Get currently connected Accessory
     */
    fun getConnectedAccessory(): android.hardware.usb.UsbAccessory? {
        return accessoryKernel?.getConnectedAccessory()
    }
    
    /**
     * Get current USB mode
     */
    fun getCurrentMode(): UsbMode {
        return currentMode
    }
}
