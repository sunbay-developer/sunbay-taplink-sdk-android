package com.sunmi.tapro.taplink.communication.cable.serial

import android.content.Context
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * RS232串口服务内核类
 *
 * 提供RS232物理串口通信功能，只支持物理串口设备
 * 协议格式：serial://devicePath:baudRate 或 rs232://devicePath:baudRate
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class SerialServiceKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private val TAG = "SerialServiceKernel"

    private var serialPort: File? = null
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var dataReceiveJob: Job? = null

    // ==================== AsyncServiceKernel 抽象方法实现 ====================

    override fun getServiceType(): String = "RS232 Serial"

    override fun getExpectedProtocolType(): String = "serial protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.SerialProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        val serialProtocol = parseResult as ProtocolParseResult.SerialProtocol
        val devicePath = serialProtocol.devicePath
        val baudRate = serialProtocol.baudRate

        LogUtil.d(TAG, "Connecting to RS232 serial port: $devicePath, baudRate: $baudRate")

        scope.launch {
            try {
                val success = openSerialPort(devicePath, baudRate)
                if (success) {
                    notifyConnectionSuccess(
                        mapOf(
                            "devicePath" to devicePath,
                            "baudRate" to baudRate.toString()
                        )
                    )
                } else {
                    notifyConnectionError("Failed to open serial port: $devicePath", InnerErrorCode.C25)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect serial port: ${e.message}")
                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.C25)
            }
        }
    }

    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        try {
            if (outputStream == null) {
                LogUtil.e(TAG, "Serial port output stream is null")
                callback?.onError("NOT_CONNECTED", "Serial port not connected")
                return
            }

            outputStream?.write(data)
            outputStream?.flush()
            LogUtil.d(TAG, "RS232 data sent: ${data.size} bytes")
            callback?.onResponse("SUCCESS")
        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to send RS232 data: ${e.message}")
            callback?.onError("SEND_FAILED", e.message ?: "Unknown error")
        }
    }

    override fun performDisconnect() {
        try {
            // 停止数据接收
            dataReceiveJob?.cancel()
            dataReceiveJob = null

            // 关闭流
            inputStream?.close()
            outputStream?.close()
            inputStream = null
            outputStream = null
            serialPort = null

            LogUtil.d(TAG, "RS232 serial port disconnected")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during serial disconnect: ${e.message}")
        }
    }

    // ==================== 串口操作 ====================

    /**
     * 打开RS232串口
     */
    private suspend fun openSerialPort(devicePath: String, baudRate: Int): Boolean {
        return try {
            val device = File(devicePath)
            if (!device.exists()) {
                LogUtil.e(TAG, "RS232 device not found: $devicePath")
                return false
            }

            // 注意：实际串口操作需要使用JNI或第三方库（如usb-serial-for-android）
            // 对于物理串口设备，可能需要使用 JNI 调用底层 API
            // 这里提供框架，实际实现需要根据具体硬件平台

            serialPort = device
            LogUtil.d(TAG, "RS232 serial port opened: $devicePath, baudRate: $baudRate")

            // TODO: 实际实现需要使用串口库打开设备
            // 例如使用 JNI 或 usb-serial-for-android（如果是 USB 转串口）
            // 这里暂时使用文件流作为示例
            // inputStream = FileInputStream(device)
            // outputStream = FileOutputStream(device)

            // 启动数据读取
            startDataReceive()

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to open RS232 serial port: ${e.message}")
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

            while (isActive && currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED &&
                   inputStream != null) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1

                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        LogUtil.d(TAG, "RS232 data received: $bytesRead bytes")
                        dataReceiver?.invoke(data)
                    } else if (bytesRead == -1) {
                        // 流结束
                        LogUtil.d(TAG, "RS232 input stream closed")
                        break
                    }
                } catch (e: IOException) {
                    LogUtil.e(TAG, "Error receiving RS232 data: ${e.message}")
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        updateStatus(InnerConnectionStatus.ERROR)
                    }
                    break
                }
            }
        }
    }

    /**
     * 清理资源
     */
    override fun cleanupCommonResources() {
        super.cleanupCommonResources()
        performDisconnect()
    }

    override fun getTag(): String = TAG
}