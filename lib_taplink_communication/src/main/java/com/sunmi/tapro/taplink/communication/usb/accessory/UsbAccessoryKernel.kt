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
 * USB Accessory Mode Kernel Class
 *
 * Responsible for handling all USB Accessory mode logic:
 * - Accessory discovery and connection
 * - Permission request
 * - Data transfer (send and receive)
 * - Accessory plug/unplug monitoring
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

    // Connection related
    private var connectedAccessory: UsbAccessory? = null
    private var accessoryFileDescriptor: ParcelFileDescriptor? = null
    private var accessoryInputStream: FileInputStream? = null
    private var accessoryOutputStream: FileOutputStream? = null

    // Data receive job
    private var dataReceiveJob: Job? = null

    // Permission request related
    private var pendingConnectionCallback: ConnectionCallback? = null
    private var pendingAccessory: UsbAccessory? = null

    // Permission request broadcast receiver
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (permissionAction == intent.action) {
                synchronized(this@UsbAccessoryKernel) {
                    val accessory: UsbAccessory? = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // Permission granted
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
                        // Permission denied
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

    // USB Accessory plug/unplug broadcast receiver
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
                        // If detached accessory is pending, clear pending state
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
        // Register permission request broadcast receiver
        val permissionFilter = IntentFilter(permissionAction)
        try {
            context.registerReceiver(permissionReceiver, permissionFilter)
            LogUtil.d(TAG, "USB permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB permission receiver: ${e.message}")
        }

        // Register USB Accessory attach/detach broadcast receiver
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
     * Connect to USB Accessory
     *
     * @param protocol Protocol string
     * @param connectionCallback Connection callback
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

                // Get connected Accessory
                val accessories = usbManager.accessoryList
                val accessory = accessories?.firstOrNull()

                if (accessory == null) {
                    handleConnectionError("USB accessory not found", connectionCallback)
                    return@launch
                }

                // Check permission (Accessory mode usually auto-granted via Intent Filter, but can be manually checked)
                if (usbManager.hasPermission(accessory)) {
                    // Permission already granted, connect directly
                    val success = connectAccessory(accessory)
                    if (success) {
                        handleConnectionSuccess(connectionCallback, null)
                    } else {
                        handleConnectionError("Failed to connect USB accessory", connectionCallback)
                    }
                } else {
                    // Permission not granted, request permission (Accessory usually auto-granted, but manual request supported for completeness)
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
     * Request accessory permission
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
     * Connect to accessory
     */
    private fun connectAccessory(accessory: UsbAccessory): Boolean {
        return try {
            // Open Accessory connection
            val fileDescriptor = usbManager.openAccessory(accessory) ?: run {
                LogUtil.e(TAG, "Failed to open USB accessory")
                return false
            }

            accessoryFileDescriptor = fileDescriptor
            connectedAccessory = accessory

            // Create input/output streams
            val fd = fileDescriptor.fileDescriptor
            accessoryInputStream = FileInputStream(fd)
            accessoryOutputStream = FileOutputStream(fd)

            // Start data receiving
            startDataReceive()

            LogUtil.d(TAG, "USB accessory connected: ${accessory.manufacturer} ${accessory.model}")
            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect USB accessory: ${e.message}")
            false
        }
    }

    /**
     * Start data receiving
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
                        // Stream ended
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
     * Send data in Accessory mode
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
     * Disconnect (internal method)
     */
    private fun disconnectInternal() {
        try {
            // Stop data receiving
            dataReceiveJob?.cancel()
            dataReceiveJob = null

            // Disconnect
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
     * Release resources
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

    // ==================== Public Methods ====================

    /**
     * Get all connected USB Accessories
     */
    fun getAccessoryList(): Array<UsbAccessory>? {
        return usbManager.accessoryList
    }

    /**
     * Get currently connected Accessory
     */
    fun getConnectedAccessory(): UsbAccessory? {
        return connectedAccessory
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
