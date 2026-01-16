package com.sunmi.tapro.taplink.communication.cable.vsp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * VSP客户端内核类
 * 
 * 作为虚拟串口的客户端，主动连接到串口设备
 * 支持多种连接方式：
 * 1. USB转串口设备连接
 * 2. 蓝牙串口连接
 * 3. 网络串口连接
 * 
 * 协议格式：vsp://baudRate/dataBits/parity/stopBits?device=deviceName
 * 
 * @param appId 应用ID
 * @param appSecretKey 应用密钥
 * @param context Android上下文
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class VSPClientKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private val TAG = "VSPClientKernel"

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // USB串口相关
    private var usbSerialPort: UsbSerialPort? = null
    private var usbSerialDriver: UsbSerialDriver? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var dataReceiveJob: Job? = null

    // 权限请求相关
    private val permissionAction = "com.sunmi.tapro.taplink.vsp.USB_PERMISSION"
    private val permissionPendingIntent: PendingIntent = createPermissionPendingIntent()
    private var pendingDevice: UsbDevice? = null

    // VSP配置参数
    private var baudRate: Int = 115200
    private var dataBits: Int = 8
    private var parity: Int = UsbSerialPort.PARITY_NONE
    private var stopBits: Int = UsbSerialPort.STOPBITS_1
    private var targetDeviceName: String? = null

    // 权限请求广播接收器
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.d(TAG, "USB permission broadcast received: ${intent.action}")

            if (permissionAction == intent.action) {
                synchronized(this@VSPClientKernel) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    LogUtil.d(TAG, "USB permission result:")
                    LogUtil.d(TAG, "  Device: ${device?.deviceName}")
                    LogUtil.d(TAG, "  Permission granted: $permissionGranted")
                    LogUtil.d(TAG, "  Pending device: ${pendingDevice?.deviceName}")
                    if (device != null && device == pendingDevice) {
                        pendingDevice = null

                        if (permissionGranted) {
                            LogUtil.d(TAG, "USB permission granted for VSP device: ${device.deviceName}")
                            
                            // 使用父类callback
                            currentConnectionCallback?.let { callback ->
                                scope.launch {
                                    continueConnection(device, callback)
                                }
                            }
                        } else {
                            LogUtil.e(TAG, "USB permission denied for VSP device: ${device.deviceName}")
                            notifyConnectionError("USB permission denied by user", InnerErrorCode.E252)
                        }
                    } else {
                        LogUtil.w(TAG, "Permission result for different or unknown device")
                    }
                }
            }
        }
    }

    init {
        // 注册权限请求广播接收器
        val permissionFilter = IntentFilter(permissionAction)
        try {
            ContextCompat.registerReceiver(
                context,
                permissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_EXPORTED
            )
            LogUtil.d(TAG, "VSP USB permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register VSP USB permission receiver: ${e.message}")
        }
    }

    // ==================== AsyncServiceKernel 抽象方法实现 ====================

    override fun getServiceType(): String = "VSP Client"

    override fun getExpectedProtocolType(): String = "vsp client protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.VspProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        val vspProtocol = parseResult as ProtocolParseResult.VspProtocol
        
        // 解析协议参数
        baudRate = vspProtocol.baudRate
        dataBits = vspProtocol.dataBits
        parity = parseParity(vspProtocol.parity)
        stopBits = parseStopBits(vspProtocol.stopBits)
        
        // 解析目标设备名称（如果有）
        targetDeviceName = extractDeviceName(vspProtocol.toString())

        LogUtil.d(TAG, "Connecting to VSP device as client:")
        LogUtil.d(TAG, "  baudRate=$baudRate, dataBits=$dataBits")
        LogUtil.d(TAG, "  parity=${vspProtocol.parity}, stopBits=${vspProtocol.stopBits}")
        LogUtil.d(TAG, "  targetDevice=$targetDeviceName")

        scope.launch {
            try {
                val success = connectToVspDevice(connectionCallback)
                if (success) {
                    // 启动数据接收
                    startDataReceive()
                    
                    // 通知连接成功
                    notifyConnectionSuccess(
                        mapOf(
                            "mode" to "client",
                            "baudRate" to baudRate.toString(),
                            "dataBits" to dataBits.toString(),
                            "parity" to vspProtocol.parity,
                            "stopBits" to vspProtocol.stopBits.toString(),
                            "device" to (targetDeviceName ?: "auto-detected")
                        )
                    )
                } else {
                    notifyConnectionError("Failed to connect to VSP device", InnerErrorCode.E232)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect VSP client: ${e.message}")
                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.E232)
            }
        }
    }

    override suspend fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!isVspReady()) {
            LogUtil.e(TAG, "VSP client not ready")
            callback?.onError(InnerErrorCode.E202.code, InnerErrorCode.E202.description)
            return
        }

        try {
            val dataString = String(data)
            LogUtil.d(TAG, "VSP client sending data: $dataString (${data.size} bytes)")
            
            usbSerialPort?.write(data, 1000) // 1秒超时
            
            LogUtil.d(TAG, "VSP client data sent successfully: ${data.size} bytes")

        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to send VSP client data: ${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
            
            // 发送失败可能表示连接断开
            handleConnectionError()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Unexpected error sending VSP client data: ${e.message}")
            callback?.onError(InnerErrorCode.E304.code, e.message ?: InnerErrorCode.E304.description)
        }
    }

    override fun performDisconnect() {
        LogUtil.d(TAG, "=== VSP Client Disconnect Started ===")
        
        try {
            // 停止数据接收
            dataReceiveJob?.cancel()
            dataReceiveJob = null
            
            // 关闭串口连接
            usbSerialPort?.close()
            usbSerialPort = null
            
            // 关闭USB连接
            usbConnection?.close()
            usbConnection = null
            
            usbSerialDriver = null
            
            LogUtil.d(TAG, "VSP client disconnected successfully")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during VSP client disconnect: ${e.message}")
        }
        
        LogUtil.d(TAG, "=== VSP Client Disconnect Finished ===")
    }

    // ==================== VSP客户端操作方法 ====================

    /**
     * 检查VSP连接是否就绪（包括USB串口）
     */
    private fun isVspReady(): Boolean {
        return currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED && usbSerialPort != null
    }

    /**
     * 连接到VSP设备
     */
    private suspend fun connectToVspDevice(connectionCallback: ConnectionCallback): Boolean {
        return try {
            // 查找可用的USB串口设备
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                LogUtil.e(TAG, "No USB serial drivers found")
                return false
            }

            // 选择目标设备
            val targetDriver = selectTargetDevice(availableDrivers)
            if (targetDriver == null) {
                LogUtil.e(TAG, "Target VSP device not found")
                return false
            }

            val usbDevice = targetDriver.device

            // 检查权限
            if (!usbManager.hasPermission(usbDevice)) {
                LogUtil.d(TAG, "No permission for USB device: ${usbDevice.deviceName}, requesting permission...")
                
                // 请求权限
                synchronized(this@VSPClientKernel) {
                    pendingDevice = usbDevice
                }
                
                try {
                    usbManager.requestPermission(usbDevice, permissionPendingIntent)
                    LogUtil.d(TAG, "USB permission request sent for VSP device: ${usbDevice.deviceName}")
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
                    synchronized(this@VSPClientKernel) {
                        pendingDevice = null
                    }
                    return false
                }
                
                // 权限请求已发送，等待用户响应
                return true
            }

            // 已有权限，直接连接
            return continueConnectionInternal(targetDriver)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to VSP device: ${e.message}")
            false
        }
    }

    /**
     * 权限授予后继续连接
     */
    private suspend fun continueConnection(device: UsbDevice, connectionCallback: ConnectionCallback) {
        try {
            // 需要重新查找driver，因为设备可能已经改变
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            val targetDriver = availableDrivers.find { it.device == device }
            
            if (targetDriver == null) {
                LogUtil.e(TAG, "Driver not found for device: ${device.deviceName}")
                connectionCallback.onDisconnected("DRIVER_NOT_FOUND", "Driver not found for device")
                return
            }
            
            val success = continueConnectionInternal(targetDriver)
            if (success) {
                // 启动数据接收
                startDataReceive()
                
                // 通知连接成功
                connectionCallback.onConnected(
                    mapOf(
                        "mode" to "client",
                        "baudRate" to baudRate.toString(),
                        "dataBits" to dataBits.toString(),
                        "parity" to parity.toString(),
                        "stopBits" to stopBits.toString(),
                        "device" to device.deviceName
                    )
                )
            } else {
                connectionCallback.onDisconnected("CONNECTION_FAILED", "Failed to establish VSP connection")
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during VSP connection: ${e.message}")
            connectionCallback.onDisconnected("CONNECTION_ERROR", e.message ?: "Unknown error")
        }
    }

    /**
     * 执行实际的连接操作（已有权限）
     */
    private suspend fun continueConnectionInternal(targetDriver: UsbSerialDriver): Boolean {
        return try {
            val usbDevice = targetDriver.device
            // 打开USB设备连接
            val connection = usbManager.openDevice(usbDevice) ?: run {
                LogUtil.e(TAG, "Failed to open USB device")
                return false
            }

            // 获取串口（通常使用第一个端口）
            if (targetDriver.ports.isEmpty()) {
                LogUtil.e(TAG, "Driver has no ports available")
                connection.close()
                return false
            }
            
            val port = targetDriver.ports[0]

            // 打开串口
            port.open(connection)
            
            // 保存连接引用
            usbConnection = connection

            // 设置串口参数
            port.setParameters(baudRate, dataBits, stopBits, parity)

            usbSerialPort = port
            usbSerialDriver = targetDriver

            LogUtil.d(TAG, "VSP client connected to: ${usbDevice.deviceName}")
            LogUtil.d(TAG, "Serial parameters: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to establish VSP connection: ${e.message}")
            false
        }
    }

    /**
     * 选择目标设备
     */
    private fun selectTargetDevice(availableDrivers: List<UsbSerialDriver>): UsbSerialDriver? {
        return if (targetDeviceName != null) {
            // 根据设备名称查找
            availableDrivers.find { driver ->
                driver.device.deviceName.contains(targetDeviceName!!, ignoreCase = true) ||
                driver.device.productName?.contains(targetDeviceName!!, ignoreCase = true) == true
            }
        } else {
            // 使用第一个可用设备
            availableDrivers.firstOrNull()
        }?.also { driver ->
            LogUtil.d(TAG, "Selected VSP device: ${driver.device.deviceName}")
            LogUtil.d(TAG, "Device info: VID=${String.format("0x%04X", driver.device.vendorId)}, " +
                    "PID=${String.format("0x%04X", driver.device.productId)}")
        }
    }

    /**
     * 启动数据接收循环
     */
    private fun startDataReceive() {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch {
            val buffer = ByteArray(16384) // 16KB buffer

            LogUtil.d(TAG, "Starting VSP client data receive loop...")
            
            while (isActive && isVspReady()) {
                try {
                    val bytesRead = usbSerialPort?.read(buffer, 1000) ?: 0

                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        val dataString = String(data)
                        LogUtil.d(TAG, "VSP client data received: $dataString (${data.size} bytes)")
                        
                        // 通知数据接收器
                        dataReceiver?.invoke(data)
                    }
                } catch (e: IOException) {
                    LogUtil.e(TAG, "Error receiving VSP client data: ${e.message}")
                    
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        handleConnectionError()
                    }
                    break
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Unexpected error in VSP client receive loop: ${e.message}")
                    
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        updateStatus(InnerConnectionStatus.ERROR)
                    }
                    break
                }
            }
            
            LogUtil.d(TAG, "VSP client data receive loop ended")
        }
    }

    /**
     * 处理连接错误
     */
    private fun handleConnectionError() {
        LogUtil.w(TAG, "VSP client connection error detected")
        
        if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
            updateStatus(InnerConnectionStatus.ERROR)
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 从协议字符串中提取设备名称
     */
    private fun extractDeviceName(protocol: String): String? {
        return try {
            val uri = android.net.Uri.parse(protocol)
            uri.getQueryParameter("device")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to extract device name from protocol: $protocol")
            null
        }
    }

    /**
     * 解析校验位
     */
    private fun parseParity(parity: String): Int {
        return when (parity.uppercase()) {
            "N", "NONE" -> UsbSerialPort.PARITY_NONE
            "E", "EVEN" -> UsbSerialPort.PARITY_EVEN
            "O", "ODD" -> UsbSerialPort.PARITY_ODD
            "M", "MARK" -> UsbSerialPort.PARITY_MARK
            "S", "SPACE" -> UsbSerialPort.PARITY_SPACE
            else -> {
                LogUtil.w(TAG, "Unknown parity: $parity, using NONE")
                UsbSerialPort.PARITY_NONE
            }
        }
    }

    /**
     * 解析停止位
     */
    private fun parseStopBits(stopBits: Int): Int {
        return when (stopBits) {
            1 -> UsbSerialPort.STOPBITS_1
            2 -> UsbSerialPort.STOPBITS_2
            else -> {
                LogUtil.w(TAG, "Unknown stopBits: $stopBits, using 1")
                UsbSerialPort.STOPBITS_1
            }
        }
    }

    /**
     * 获取当前VSP配置
     */
    fun getCurrentConfig(): Map<String, Any> {
        return mapOf(
            "mode" to "client",
            "baudRate" to baudRate,
            "dataBits" to dataBits,
            "parity" to parity,
            "stopBits" to stopBits,
            "isConnected" to isVspReady(),
            "targetDevice" to (targetDeviceName ?: "auto")
        )
    }

    /**
     * 检查连接状态
     */
    fun isVspConnected(): Boolean = isVspReady()

    /**
     * 获取可用的VSP设备列表
     */
    fun getAvailableDevices(): List<Map<String, String>> {
        return try {
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            availableDrivers.map { driver ->
                mapOf(
                    "deviceName" to driver.device.deviceName,
                    "productName" to (driver.device.productName ?: "Unknown"),
                    "vendorId" to String.format("0x%04X", driver.device.vendorId),
                    "productId" to String.format("0x%04X", driver.device.productId),
                    "driverClass" to driver.javaClass.simpleName
                )
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to get available devices: ${e.message}")
            emptyList()
        }
    }

    /**
     * 清理资源
     */
    override fun cleanupCommonResources() {
        super.cleanupCommonResources()
        performDisconnect()
        
        // 清理权限相关状态
        synchronized(this) {
            pendingDevice = null
        }
        
        // 注销广播接收器
        try {
            context.unregisterReceiver(permissionReceiver)
            LogUtil.d(TAG, "VSP USB permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering VSP USB permission receiver: ${e.message}")
        }
    }

    override fun getTag(): String = TAG

    // ==================== 权限管理方法 ====================

    /**
     * 创建权限请求PendingIntent
     */
    private fun createPermissionPendingIntent(): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            0,
            Intent(permissionAction).apply {
                setPackage(context.packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    /**
     * 检查指定设备是否有权限
     */
    fun hasPermissionForDevice(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * 手动请求设备权限
     * 
     * 注意：此方法会更新当前的连接回调，如果正在进行连接流程，可能会影响当前连接
     */
    fun requestPermissionForDevice(device: UsbDevice, callback: ConnectionCallback) {
        synchronized(this) {
            pendingDevice = device
            // 更新当前连接回调，因为权限授予后会使用这个callback
            currentConnectionCallback = callback
        }
        
        try {
            usbManager.requestPermission(device, permissionPendingIntent)
            LogUtil.d(TAG, "Manual USB permission request sent for: ${device.deviceName}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to request USB permission manually: ${e.message}")
            synchronized(this) {
                pendingDevice = null
            }
            callback.onDisconnected("PERMISSION_REQUEST_FAILED", e.message ?: "Unknown error")
        }
    }

    companion object {
        /**
         * 检查系统是否支持USB Host模式
         */
        fun isUsbHostSupported(context: Context): Boolean {
            return context.packageManager.hasSystemFeature("android.hardware.usb.host")
        }

        /**
         * 获取所有可用的USB设备（不仅限于串口设备）
         */
        fun getAllUsbDevices(context: Context): List<UsbDevice> {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            return usbManager.deviceList.values.toList()
        }
    }
}