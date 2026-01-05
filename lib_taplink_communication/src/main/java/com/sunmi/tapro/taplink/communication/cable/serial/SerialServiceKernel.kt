package com.sunmi.tapro.taplink.communication.cable.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
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
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * RS232串口服务内核实现类
 *
 * 参考RS232Kernel和RS232NormalAdapter的实现，继承自AsyncServiceKernel
 * 支持USB转串口设备的自动检测和连接，使用十六进制传输协议
 *
 * 协议格式：rs232://baudRate/dataBits/parity/stopBits?device=deviceName
 *
 * 功能特性：
 * - 支持多种USB转串口芯片（PL2303、CH340、FTDI等）
 * - 自动设备检测和驱动匹配
 * - USB权限管理
 * - 异步数据收发
 * - 十六进制数据传输（高性能模式）
 * - 完整的生命周期管理
 *
 * 十六进制传输协议：
 * - 帧结构：FF + 长度(4位) + 数据(十六进制) + 校验和(2位) + FE
 * - 优势：固定帧边界、校验和验证、高传输效率
 * - 解决了JSON分片和数据错乱问题
 *
 * @param appId 应用ID
 * @param appSecretKey 应用密钥
 * @param context Android上下文
 *
 * @author TaPro Team
 * @since 2025-01-03
 */
open class SerialServiceKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private val TAG = "SerialServiceKernel"

    // USB管理器
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // USB串口相关
    private var usbSerialPort: UsbSerialPort? = null
    private var usbSerialDriver: UsbSerialDriver? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbIoManager: SerialInputOutputManager? = null

    // 串口配置参数
    private var baudRate: Int = 115200
    private var dataBits: Int = 8
    private var parity: Int = UsbSerialPort.PARITY_NONE
    private var stopBits: Int = UsbSerialPort.STOPBITS_1
    private var targetDeviceName: String? = null
    private var portNum: Int = 0

    // 权限管理
    private val permissionAction = "com.sunmi.tapro.taplink.serial.USB_PERMISSION"
    private val permissionPendingIntent: PendingIntent = createPermissionPendingIntent()
    private var pendingDevice: UsbDevice? = null
    private var isReceiverRegistered: Boolean = false

    // USB权限状态
    private enum class UsbPermission {
        Unknown, Requested, Granted, Denied
    }

    private var usbPermission = UsbPermission.Unknown

    // 超时管理
    private var permissionTimeoutJob: Job? = null

    // ==================== 十六进制数据处理 (高性能传输模式) ====================
    
    /**
     * 单线程数据处理执行器，确保数据按接收顺序处理
     */
    private val dataProcessingExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SerialHexDataProcessor").apply {
            isDaemon = true
        }
    }
    
    /**
     * 数据队列，保证FIFO顺序处理
     */
    private val dataQueue = LinkedBlockingQueue<ByteArray>()
    
    /**
     * 十六进制数据缓冲区
     */
    private val hexBuffer = StringBuilder()
    
    /**
     * 最大缓冲区大小
     */
    private val maxBufferSize = 128 * 1024 // 128KB (十六进制需要更大空间)
    
    /**
     * 消息超时时间（毫秒）
     */
    private val messageTimeoutMs = 5000L
    
    /**
     * 最后接收数据的时间戳
     */
    private var lastDataTimestamp = 0L

    // USB探测器
    private val usbDefaultProber = UsbSerialProber.getDefaultProber()
    private var usbCustomProber: UsbSerialProber? = null

    // 权限请求广播接收器
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LogUtil.d(TAG, "USB permission broadcast received: ${intent.action}")

            if (permissionAction == intent.action) {
                // 取消超时任务
                permissionTimeoutJob?.cancel()
                permissionTimeoutJob = null

                val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                usbPermission = if (permissionGranted) UsbPermission.Granted else UsbPermission.Denied

                LogUtil.d(TAG, "USB permission result: $permissionGranted")

                // 重新尝试连接
                scope.launch {
                    continueConnection()
                }
            }
        }
    }

    init {
        registerPermissionReceiver()
        startBufferTimeoutChecker()
        startSequentialDataProcessor()
    }

    // ==================== 十六进制编码工具方法 ====================

    /**
     * 字节数组转十六进制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    /**
     * 十六进制字符串转字节数组
     */
    private fun hexToBytes(hex: String): ByteArray {
        return try {
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to convert hex to bytes: $hex")
            byteArrayOf()
        }
    }

    /**
     * 计算校验和
     */
    private fun calculateChecksum(data: ByteArray): Byte {
        return (data.sum() and 0xFF).toByte()
    }

    // ==================== AsyncServiceKernel 抽象方法实现 ====================

    override fun getServiceType(): String = "RS232 Serial USB (Hex)"

    override fun getExpectedProtocolType(): String = "rs232 hex protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.SerialProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        val serialProtocol = parseResult as ProtocolParseResult.SerialProtocol

        // 解析协议参数
        baudRate = serialProtocol.baudRate
        dataBits = serialProtocol.dataBits
        parity = parseParityFromString(serialProtocol.parity)
        stopBits = parseStopBitsFromInt(serialProtocol.stopBits)

        // 解析目标设备名称（如果有）
        targetDeviceName = extractDeviceName(serialProtocol.toString())

        LogUtil.d(TAG, "Connecting to RS232 serial device (Hex mode):")
        LogUtil.d(TAG, "  baudRate=$baudRate, dataBits=$dataBits")
        LogUtil.d(TAG, "  parity=${serialProtocol.parity}, stopBits=${serialProtocol.stopBits}")
        LogUtil.d(TAG, "  targetDevice=$targetDeviceName")

        scope.launch {
            try {
                // 清空缓冲区
                clearHexBuffer()
                
                val success = connectToSerialDevice()
                if (success) {
                    // 通知连接成功
                    notifyConnectionSuccess(
                        mapOf(
                            "baudRate" to baudRate.toString(),
                            "dataBits" to dataBits.toString(),
                            "parity" to (serialProtocol.parity),
                            "stopBits" to (serialProtocol.stopBits).toString(),
                            "device" to (getConnectedDeviceName()),
                            "portNum" to portNum.toString(),
                            "mode" to "hex"
                        )
                    )
                } else {
                    notifyConnectionError("Failed to connect to serial device", InnerErrorCode.C25)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect serial device: ${e.message}")
                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.C25)
            }
        }
    }

    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!isSerialReady()) {
            LogUtil.e(TAG, "Serial device not ready")
            callback?.onError(InnerErrorCode.C65.code, InnerErrorCode.C65.description)
            return
        }

        try {
            val originalString = String(data)
            LogUtil.d(TAG, "Serial sending original data: $originalString (${data.size} bytes)")

            // 转换为十六进制
            val hexString = bytesToHex(data)
            
            // 计算校验和
            val checksum = calculateChecksum(data)
            val checksumHex = String.format("%02X", checksum.toInt() and 0xFF)
            
            // 构建帧：FF + 长度(4位) + 数据(十六进制) + 校验和(2位) + FE
            val frameLength = String.format("%04X", data.size)
            val frameData = "FF${frameLength}${hexString}${checksumHex}FE".toByteArray()
            
            LogUtil.d(TAG, "Serial sending hex frame: ${String(frameData)} (${frameData.size} bytes)")
            LogUtil.d(TAG, "  Original size: ${data.size}, Hex size: ${hexString.length}, Frame size: ${frameData.size}")

            // 发送数据
            usbSerialPort?.write(frameData, 2000)

            LogUtil.d(TAG, "Serial hex data sent successfully: ${frameData.size} bytes")

        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to send serial hex data: ${e.message}")
            callback?.onError(InnerErrorCode.C62.code, e.message ?: InnerErrorCode.C62.description)

            // 发送失败可能表示连接断开
            handleConnectionError()
        } catch (e: Exception) {
            LogUtil.e(TAG, "Unexpected error sending serial hex data: ${e.message}")
            callback?.onError(InnerErrorCode.C63.code, e.message ?: InnerErrorCode.C63.description)
        }
    }

    override fun performDisconnect() {
        LogUtil.d(TAG, "=== Serial Hex Disconnect Started ===")

        try {
            // 取消权限超时任务
            permissionTimeoutJob?.cancel()
            permissionTimeoutJob = null

            // 清空缓冲区
            clearHexBuffer()

            // 停止IO管理器
            usbIoManager?.setListener(null)
            usbIoManager?.stop()
            usbIoManager = null

            // 关闭串口
            usbSerialPort?.close()
            usbSerialPort = null

            // 关闭USB连接
            usbConnection?.close()
            usbConnection = null

            usbSerialDriver = null

            LogUtil.d(TAG, "Serial hex device disconnected successfully")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during serial hex disconnect: ${e.message}")
        }

        LogUtil.d(TAG, "=== Serial Hex Disconnect Finished ===")
    }

    // ==================== JSON数据缓冲和重组方法 (参考RS232NormalAdapter数据处理) ====================

    // ==================== 十六进制数据处理方法 ====================

    /**
     * 启动顺序数据处理器
     * 使用单线程执行器确保数据按接收顺序处理，解决并发导致的数据错乱问题
     */
    private fun startSequentialDataProcessor() {
        dataProcessingExecutor.execute {
            LogUtil.d(TAG, "Sequential hex data processor started")
            
            while (!Thread.currentThread().isInterrupted) {
                try {
                    // 阻塞等待数据，保证FIFO顺序
                    val data = dataQueue.take()
                    processHexDataInOrder(data)
                } catch (e: InterruptedException) {
                    LogUtil.d(TAG, "Hex data processor interrupted")
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Error in sequential hex data processing: ${e.message}")
                }
            }
            
            LogUtil.d(TAG, "Sequential hex data processor stopped")
        }
    }

    /**
     * 按顺序处理接收到的十六进制数据片段
     * 替代原来的协程并发处理，确保数据顺序正确
     */
    private fun processHexDataInOrder(data: ByteArray) {
        val dataString = String(data, Charsets.UTF_8)
        LogUtil.d(TAG, "Processing hex data in order: $dataString (${data.size} bytes)")

        synchronized(hexBuffer) {
            // 更新时间戳
            lastDataTimestamp = System.currentTimeMillis()

            // 检查缓冲区大小
            if (hexBuffer.length + dataString.length > maxBufferSize) {
                LogUtil.w(TAG, "Hex buffer overflow, clearing buffer")
                hexBuffer.clear()
            }

            // 添加到缓冲区
            hexBuffer.append(dataString)

            // 尝试提取完整的十六进制帧
            extractCompleteHexFrames()
        }
    }

    /**
     * 从缓冲区提取完整的十六进制帧
     */
    private fun extractCompleteHexFrames() {
        val bufferContent = hexBuffer.toString()
        var startIndex = 0

        while (true) {
            // 查找帧开始标记 FF
            val frameStart = bufferContent.indexOf("FF", startIndex)
            if (frameStart == -1) break

            // 检查是否有足够的数据读取长度
            if (frameStart + 6 > bufferContent.length) break

            try {
                // 读取数据长度（4位十六进制）
                val lengthHex = bufferContent.substring(frameStart + 2, frameStart + 6)
                val dataLength = lengthHex.toInt(16)
                
                LogUtil.d(TAG, "Found frame start at $frameStart, data length: $dataLength")

                // 计算完整帧的长度：FF(2) + 长度(4) + 数据(dataLength*2) + 校验和(2) + FE(2)
                val totalFrameLength = 2 + 4 + (dataLength * 2) + 2 + 2

                if (frameStart + totalFrameLength > bufferContent.length) {
                    LogUtil.d(TAG, "Frame incomplete, waiting for more data")
                    break // 帧不完整
                }

                // 检查帧尾标记 FE
                val frameEndPos = frameStart + totalFrameLength - 2
                if (!bufferContent.substring(frameEndPos, frameEndPos + 2).equals("FE", ignoreCase = true)) {
                    LogUtil.w(TAG, "Invalid frame end marker at position $frameEndPos")
                    startIndex = frameStart + 2
                    continue
                }

                // 提取十六进制数据和校验和
                val hexDataStart = frameStart + 6
                val hexDataEnd = frameEndPos - 2
                val hexData = bufferContent.substring(hexDataStart, hexDataEnd)
                val checksumHex = bufferContent.substring(hexDataEnd, frameEndPos)

                LogUtil.d(TAG, "Extracted hex data: $hexData, checksum: $checksumHex")

                // 转换为原始数据
                val originalData = hexToBytes(hexData)
                if (originalData.isEmpty()) {
                    LogUtil.w(TAG, "Failed to decode hex data: $hexData")
                    startIndex = frameStart + totalFrameLength
                    continue
                }

                // 验证校验和
                val calculatedChecksum = calculateChecksum(originalData)
                val expectedChecksum = checksumHex.toInt(16).toByte()
                
                if (calculatedChecksum != expectedChecksum) {
                    LogUtil.w(TAG, "Checksum mismatch: calculated=${String.format("%02X", calculatedChecksum.toInt() and 0xFF)}, expected=$checksumHex")
                    startIndex = frameStart + totalFrameLength
                    continue
                }

                LogUtil.d(TAG, "Complete hex frame decoded successfully: ${String(originalData)} (${originalData.size} bytes)")

                // 发送给数据接收器
                dataReceiver?.invoke(originalData)

                startIndex = frameStart + totalFrameLength

            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to decode hex frame: ${e.message}")
                startIndex = frameStart + 2
            }
        }

        // 清理已处理的数据
        if (startIndex > 0) {
            hexBuffer.delete(0, startIndex)
            LogUtil.d(TAG, "Hex buffer cleaned, remaining: ${hexBuffer.length} chars")
        }
    }

    /**
     * 清空十六进制缓冲区
     */
    private fun clearHexBuffer() {
        synchronized(hexBuffer) {
            hexBuffer.clear()
            lastDataTimestamp = 0L
            LogUtil.d(TAG, "Hex buffer cleared")
        }
    }

    /**
     * 启动缓冲区超时检查器
     */
    private fun startBufferTimeoutChecker() {
        scope.launch {
            while (true) {
                delay(1000) // 每秒检查一次

                synchronized(hexBuffer) {
                    val currentTime = System.currentTimeMillis()
                    
                    // 检查是否有超时的不完整消息
                    if (hexBuffer.isNotEmpty() && 
                        lastDataTimestamp > 0 && 
                        currentTime - lastDataTimestamp > messageTimeoutMs) {
                        
                        LogUtil.w(TAG, "Hex message timeout, clearing buffer: ${hexBuffer.toString()}")
                        hexBuffer.clear()
                        lastDataTimestamp = 0L
                    }
                }
            }
        }
    }

    // ==================== 串口连接操作方法 ====================

    /**
     * 检查串口连接是否就绪
     */
    private fun isSerialReady(): Boolean {
        return currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED &&
                usbSerialPort != null && usbIoManager != null
    }

    /**
     * 连接到串口设备
     */
    private suspend fun connectToSerialDevice(): Boolean {
        // 重置权限状态
        usbPermission = UsbPermission.Unknown

        return continueConnection()
    }

    /**
     * 继续连接流程
     */
    private suspend fun continueConnection(): Boolean {
        return try {
            // 检查权限状态
            if (usbPermission == UsbPermission.Denied) {
                LogUtil.e(TAG, "USB permission denied")
                return false
            }

            // 查找USB设备
            val device = findUsbDevice()
            if (device == null) {
                LogUtil.e(TAG, "No suitable USB serial device found")
                return false
            }

            // 获取驱动
            val driver = getUsbSerialDriver(device)
            if (driver == null) {
                LogUtil.e(TAG, "No driver found for device: ${device.deviceName}")
                return false
            }

            // 检查端口
            if (driver.ports.isEmpty()) {
                LogUtil.e(TAG, "Device has no available ports")
                return false
            }

            // 打开USB连接
            val connection = openUsbConnection(device)
            if (connection == null) {
                // 可能需要请求权限
                if (usbPermission == UsbPermission.Unknown) {
                    requestUsbPermission(device)
                    return true // 权限请求已发送，等待结果
                }
                return false
            }

            // 建立串口连接
            return establishSerialConnection(driver, connection)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to serial device: ${e.message}")
            false
        }
    }

    /**
     * 查找USB设备 (参考RS232NormalAdapter.findUsbDevice)
     */
    private fun findUsbDevice(): UsbDevice? {
        val usbDeviceList = usbManager.deviceList.values

        LogUtil.d(TAG, "Scanning ${usbDeviceList.size} USB devices...")

        // 参考RS232NormalAdapter的设备查找逻辑
        for (device in usbDeviceList) {
            val manufacturerName = device.manufacturerName
            val productId = device.productId
            val vendorId = device.vendorId
            val productIdString = String.format("%04X", productId)

            LogUtil.d(TAG, "productId: $productIdString vendorId: $vendorId")
            LogUtil.d(TAG, "manufacturerName: $manufacturerName")

            // 首先尝试默认探测器
            var driver = usbDefaultProber.probeDevice(device)
            if (driver == null) {
                // 尝试自定义探测器
                driver = usbCustomProber?.probeDevice(device)
            }

            if (driver != null) {
                portNum = 0
                LogUtil.d(TAG, "Found compatible device: ${device.deviceName}, Driver: ${driver.javaClass.simpleName}")
                return device
            }

            // 检查是否是SUNMI设备（参考RS232NormalAdapter逻辑）
            val isSunmiDevice = manufacturerName?.contains("SUNMI", ignoreCase = true) == true
            if (isSunmiDevice) {
                LogUtil.d(TAG, "Found SUNMI device, creating custom prober")
                usbCustomProber = createCustomProber(vendorId, productId)
                driver = usbCustomProber?.probeDevice(device)
                if (driver != null) {
                    // 查找可用端口
                    for (port in 0 until driver.ports.size) {
                        LogUtil.d(TAG, "The VSP port: $port driver: $driver")
                        portNum = port
                    }
                    return device
                }
            }

            // 如果指定了目标设备名称，进行匹配
            if (targetDeviceName != null) {
                val deviceMatches = device.deviceName.contains(targetDeviceName!!, ignoreCase = true) ||
                        (manufacturerName?.contains(targetDeviceName!!, ignoreCase = true) == true)
                if (deviceMatches && driver != null) {
                    LogUtil.d(TAG, "Device name matches target: $targetDeviceName")
                    return device
                }
            }
        }

        return null
    }

    /**
     * 获取USB串口驱动 (参考RS232NormalAdapter.getUsbSerialDriver)
     */
    private fun getUsbSerialDriver(device: UsbDevice): UsbSerialDriver? {
        var driver = usbDefaultProber.probeDevice(device)
        if (driver == null) {
            driver = usbCustomProber?.probeDevice(device)
        }
        return driver
    }

    /**
     * 创建自定义探测器（支持特殊设备）
     * 参考RS232NormalAdapter中的自定义探测器创建
     */
    private fun createCustomProber(vendorId: Int, productId: Int): UsbSerialProber? {
        // 这里可以添加对特殊设备的支持
        // 例如SUNMI设备或其他自定义USB转串口设备
        // 参考RS232CustomProbe.getCustomProbe(vendorId, productId)
        return null
    }

    /**
     * 打开USB设备连接 (参考RS232NormalAdapter.openUsbConnection)
     */
    private fun openUsbConnection(device: UsbDevice): UsbDeviceConnection? {
        var hasPermission = usbManager.hasPermission(device)

        if (!hasPermission) {
            // 尝试自动授权（参考RS232NormalAdapter.grantUsbDevicePermission）
            hasPermission = grantUsbDevicePermission(device)
        }

        return if (hasPermission) {
            usbManager.openDevice(device)
        } else {
            null
        }
    }

    /**
     * 请求USB权限
     */
    private fun requestUsbPermission(device: UsbDevice) {
        LogUtil.d(TAG, "Requesting USB permission for: ${device.deviceName}")

        usbPermission = UsbPermission.Requested
        pendingDevice = device

        try {
            usbManager.requestPermission(device, permissionPendingIntent)

            // 启动超时任务
            permissionTimeoutJob = scope.launch {
                delay(5000) // 5秒超时
                if (usbPermission == UsbPermission.Requested) {
                    LogUtil.e(TAG, "USB permission request timeout")
                    usbPermission = UsbPermission.Denied
                    notifyConnectionError("USB permission request timeout", InnerErrorCode.C25)
                }
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to request USB permission: ${e.message}")
            usbPermission = UsbPermission.Denied
        }
    }

    /**
     * 尝试自动授权USB设备权限 (参考RS232NormalAdapter.grantUsbDevicePermission)
     */
    private fun grantUsbDevicePermission(device: UsbDevice): Boolean {
        return try {
            val clazz = Class.forName("android.hardware.usb.UsbManager")
            val method = clazz.getDeclaredMethod("grantPermission", UsbDevice::class.java, String::class.java)
            method.invoke(usbManager, device, context.packageName)
            usbManager.hasPermission(device)
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to auto-grant USB permission: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 建立串口连接 (参考RS232NormalAdapter的连接建立过程)
     */
    private fun establishSerialConnection(driver: UsbSerialDriver, connection: UsbDeviceConnection): Boolean {
        return try {
            // 使用指定的端口号（参考RS232NormalAdapter）
            val port = driver.ports[portNum]

            // 打开串口
            port.open(connection)

            // 设置串口参数
            port.setParameters(baudRate, dataBits, stopBits, parity)

            // 保存引用
            usbSerialPort = port
            usbSerialDriver = driver
            usbConnection = connection

            // 启动IO管理器
            usbIoManager = SerialInputOutputManager(port, serialInputOutputListener)
            usbIoManager?.start()

            LogUtil.d(TAG, "Serial connection established successfully")
            LogUtil.d(TAG, "Device: ${driver.device.deviceName}")
            LogUtil.d(TAG, "Parameters: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")
            LogUtil.d(TAG, "Port: $portNum")

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to establish serial connection: ${e.message}")
            false
        }
    }

    /**
     * 串口数据监听器 - 十六进制版本
     * 修复：使用队列顺序处理替代协程并发处理，解决数据错乱问题
     */
    private val serialInputOutputListener = object : SerialInputOutputManager.Listener {
        override fun onNewData(data: ByteArray?) {
            if (data != null && data.isNotEmpty()) {
                // 参考RS232NormalAdapter的日志记录方式
                val dataString = String(data)
                LogUtil.d(TAG, "The RS232 hex receive: $dataString")
                
                // 直接加入队列，保证FIFO顺序，不使用协程避免并发错乱
                val success = dataQueue.offer(data)
                if (!success) {
                    LogUtil.w(TAG, "Hex data queue is full, dropping data: $dataString")
                }
            }
        }

        override fun onRunError(exception: Exception?) {
            LogUtil.e(TAG, "Serial hex IO error: ${exception?.message}")
            LogUtil.e(TAG, "onRunError()")
            exception?.printStackTrace()

            if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                handleConnectionError()
            }
        }
    }

    /**
     * 处理连接错误
     */
    private fun handleConnectionError() {
        LogUtil.w(TAG, "Serial hex connection error detected")

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
     * 解析校验位字符串
     */
    private fun parseParityFromString(parity: String): Int {
        return when (parity.uppercase()) {
            "NONE", "N" -> UsbSerialPort.PARITY_NONE
            "EVEN", "E" -> UsbSerialPort.PARITY_EVEN
            "ODD", "O" -> UsbSerialPort.PARITY_ODD
            "MARK", "M" -> UsbSerialPort.PARITY_MARK
            "SPACE", "S" -> UsbSerialPort.PARITY_SPACE
            else -> {
                LogUtil.w(TAG, "Unknown parity: $parity, using NONE")
                UsbSerialPort.PARITY_NONE
            }
        }
    }

    /**
     * 解析停止位
     */
    private fun parseStopBitsFromInt(stopBits: Int): Int {
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
     * 获取当前连接的设备名称
     */
    private fun getConnectedDeviceName(): String? {
        return usbSerialDriver?.device?.deviceName
    }

    /**
     * 获取当前串口配置
     */
    fun getCurrentConfig(): Map<String, Any> {
        return mapOf(
            "baudRate" to baudRate,
            "dataBits" to dataBits,
            "parity" to parity,
            "stopBits" to stopBits,
            "portNum" to portNum,
            "isConnected" to isSerialReady(),
            "device" to (getConnectedDeviceName() ?: "none"),
            "targetDevice" to (targetDeviceName ?: "auto"),
            "bufferSize" to hexBuffer.length,
            "lastDataTime" to lastDataTimestamp,
            "mode" to "hex"
        )
    }

    /**
     * 检查串口连接状态
     */
    fun isSerialConnected(): Boolean = isSerialReady()

    /**
     * 获取可用的串口设备列表
     */
    fun getAvailableDevices(): List<Map<String, String>> {
        return try {
            val devices = mutableListOf<Map<String, String>>()

            for (device in usbManager.deviceList.values) {
                val driver = getUsbSerialDriver(device)
                if (driver != null) {
                    devices.add(
                        mapOf(
                            "deviceName" to device.deviceName,
                            "productName" to (device.productName ?: "Unknown"),
                            "manufacturerName" to (device.manufacturerName ?: "Unknown"),
                            "vendorId" to String.format("0x%04X", device.vendorId),
                            "productId" to String.format("0x%04X", device.productId),
                            "driverClass" to driver.javaClass.simpleName,
                            "portCount" to driver.ports.size.toString()
                        )
                    )
                }
            }

            devices
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to get available devices: ${e.message}")
            emptyList()
        }
    }

    // ==================== 权限管理 ====================

    /**
     * 注册权限请求广播接收器
     */
    private fun registerPermissionReceiver() {
        if (!isReceiverRegistered) {
            try {
                val intentFilter = IntentFilter(permissionAction)
                ContextCompat.registerReceiver(
                    context,
                    permissionReceiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
                LogUtil.d(TAG, "USB permission receiver registered")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to register USB permission receiver: ${e.message}")
            }
        }
    }

    /**
     * 注销权限请求广播接收器
     */
    private fun unregisterPermissionReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(permissionReceiver)
                isReceiverRegistered = false
                LogUtil.d(TAG, "USB permission receiver unregistered")
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error unregistering USB permission receiver: ${e.message}")
            }
        }
    }

    /**
     * 创建权限请求PendingIntent
     */
    private fun createPermissionPendingIntent(): PendingIntent {
        val intent = Intent(permissionAction).apply {
            setPackage(context.packageName)
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or PendingIntent.FLAG_MUTABLE
        }

        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /**
     * 清理资源
     */
    override fun cleanupCommonResources() {
        super.cleanupCommonResources()
        performDisconnect()

        // 停止数据处理器
        dataProcessingExecutor.shutdown()
        
        // 清空数据队列
        dataQueue.clear()

        // 清理权限相关状态
        pendingDevice = null
        usbPermission = UsbPermission.Unknown

        // 注销广播接收器
        unregisterPermissionReceiver()
    }

    override fun getTag(): String = TAG

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