package com.sunmi.tapro.taplink.service.serial

import android.content.Context
import com.sunmi.tapro.log.CLog
import com.sunmi.tapro.taplink.service.enums.ConnectionStatus
import com.sunmi.tapro.taplink.service.interfaces.BaseServiceKernel
import com.sunmi.tapro.taplink.service.interfaces.InnerCallback
import com.sunmi.tapro.taplink.service.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.service.util.ProtocolParser
import com.sunmi.tapro.taplink.service.util.ProtocolParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * 串口服务内核类
 * 
 * 提供串口通信的功能，实现IServiceKernel接口
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class SerialServiceKernel(private val context: Context) : BaseServiceKernel() {
    private val TAG = "SerialServiceKernel"
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var serialPort: File? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    
    private var onDataReceivedListener: ((ByteArray) -> Unit)? = null
    private var isReading = false
    
    /**
     * 实现IServiceKernel接口：连接串口
     * 
     * @param protocol 协议字符串，格式：devicePath:baudRate（如 "/dev/ttyS1:9600"）
     * @param connectionCallback 连接回调
     */
    override fun connect(protocol: String, connectionCallback: ConnectionCallback) {
        if (!canConnect()) {
            handleConnectionError(
                "Serial port is already connected or connecting. Current status: $currentConnectionStatus",
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
            
            // 支持两种协议格式：serial:// 和 vsp://
            when (parseResult) {
                is ProtocolParseResult.SerialProtocol -> {
                    val success = open(parseResult.devicePath, parseResult.baudRate)
                    if (success) {
                        handleConnectionSuccess(connectionCallback, null)
                    } else {
                        handleConnectionError("Failed to open serial port: ${parseResult.devicePath}", connectionCallback)
                    }
                }
                is ProtocolParseResult.VspProtocol -> {
                    // VSP虚拟串口连接逻辑
                    val success = openVsp(parseResult.baudRate, parseResult.dataBits, parseResult.parity, parseResult.stopBits)
                    if (success) {
                        handleConnectionSuccess(connectionCallback, null)
                    } else {
                        handleConnectionError("Failed to open VSP port", connectionCallback)
                    }
                }
                else -> {
                    handleConnectionError("Protocol type mismatch, expected serial or vsp protocol", connectionCallback)
                }
            }
        } catch (e: Exception) {
            CLog.e(TAG, "Failed to connect serial port:${e.message}")
            handleConnectionError(e.message ?: "Unknown error", connectionCallback)
        }
    }
    
    /**
     * 实现IServiceKernel接口：发送数据
     * 
     * @param traceId 追踪ID
     * @param data 要发送的数据
     * @param sendCallback 发送结果回调
     */
    override fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        if (!canSendData()) {
            CLog.e(TAG, "Cannot send data, not connected. Status: $currentConnectionStatus")
            callback?.onError("NOT_CONNECTED", "Service not connected")
            return
        }
        
        val result = send(data)
        if (!result) {
            updateStatus(ConnectionStatus.ERROR)
            callback?.onError("SEND_FAILED", "Failed to send data via serial port")
        } else {
            callback?.onResponse("SUCCESS")
        }
    }
    
    /**
     * 实现IServiceKernel接口：注册数据接收器
     * 
     * @param receiver 数据接收回调
     */
    override fun registerDataReceiver(receiver: (dataArray: ByteArray?) -> Unit) {
        // 调用基类方法注册接收器
        super.registerDataReceiver(receiver)
        // 同时设置原有的监听器，确保兼容性
        setOnDataReceivedListener { data ->
            receiver(data)
        }
    }
    
    /**
     * 实现IServiceKernel接口：断开连接
     */
    override fun disconnect() {
        if (currentConnectionStatus == ConnectionStatus.DISCONNECTED) {
            return
        }
        
        updateStatus(ConnectionStatus.DISCONNECTED)
        disconnectInternal()
    }
    
    /**
     * 打开VSP虚拟串口
     * 
     * @param baudRate 波特率
     * @param dataBits 数据位
     * @param parity 校验位
     * @param stopBits 停止位
     * @return Boolean 是否打开成功
     */
    private fun openVsp(baudRate: Int, dataBits: Int, parity: String, stopBits: Int): Boolean {
        return try {
            // VSP虚拟串口连接逻辑
            // 注意：实际串口操作需要使用JNI或第三方库（如usb-serial-for-android）
            // 这里只是示例框架，实际实现需要根据具体需求使用相应的库
            CLog.d(TAG, "Opening VSP port: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")
            
            // 启动数据读取
            startReading()
            
            true
        } catch (e: Exception) {
            CLog.e(TAG, "Failed to open VSP port:${e.message}")
            false
        }
    }
    
    /**
     * 打开串口
     * 
     * @param devicePath 串口设备路径（如 /dev/ttyS1）
     * @param baudRate 波特率
     * @return Boolean 是否打开成功
     */
    private fun open(devicePath: String, baudRate: Int): Boolean {
        return try {
            val device = File(devicePath)
            if (!device.exists()) {
                CLog.e(TAG, "Serial device not found: $devicePath")
                return false
            }
            
            // 注意：实际串口操作需要使用JNI或第三方库（如usb-serial-for-android）
            // 这里只是示例框架，实际实现需要根据具体需求使用相应的库
            
            serialPort = device
            CLog.d(TAG, "Serial port opened: $devicePath, baudRate: $baudRate")
            
            // 启动数据读取
            startReading()
            
            true
        } catch (e: Exception) {
            CLog.e(TAG, "Failed to open serial port:${e.message}")
            false
        }
    }
    
    /**
     * 关闭串口（内部实现）
     */
    private fun disconnectInternal() {
        try {
            isReading = false
            inputStream?.close()
            outputStream?.close()
            inputStream = null
            outputStream = null
            serialPort = null
            CLog.d(TAG, "Serial port closed")
        } catch (e: Exception) {
            CLog.e(TAG, "Failed to close serial port:${e.message}")
        }
    }
    
    /**
     * 发送数据
     * 
     * @param data 要发送的数据
     * @return Boolean 是否发送成功
     */
    private fun send(data: ByteArray): Boolean {
        return try {
            outputStream?.write(data)
            outputStream?.flush()
            CLog.d(TAG, "Data sent: ${data.size} bytes")
            true
        } catch (e: IOException) {
            CLog.e(TAG, "Failed to send data:${e.message}")
            false
        }
    }
    
    /**
     * 发送字符串
     * 
     * @param text 要发送的文本
     * @return Boolean 是否发送成功
     */
    fun send(text: String): Boolean {
        return send(text.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * 启动数据读取
     */
    private fun startReading() {
        if (isReading) return
        
        isReading = true
        scope.launch {
            val buffer = ByteArray(1024)
            while (isReading && inputStream != null) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        CLog.d(TAG, "Data received: $bytesRead bytes")
                        onDataReceivedListener?.invoke(data)
                        dataReceiver?.invoke(data)
                    }
                } catch (e: IOException) {
                    if (isReading) {
                        CLog.e(TAG, "Error reading serial data:${e.message}")
                        updateStatus(ConnectionStatus.ERROR)
                    }
                    break
                }
            }
        }
    }
    
    /**
     * 设置数据接收监听器
     * 
     * @param listener 监听器
     */
    fun setOnDataReceivedListener(listener: (ByteArray) -> Unit) {
        onDataReceivedListener = listener
    }
    
    /**
     * 检查是否已连接
     * 
     * @return Boolean 是否已连接
     */
    fun isConnected(): Boolean {
        return serialPort != null && inputStream != null && outputStream != null
    }
}


