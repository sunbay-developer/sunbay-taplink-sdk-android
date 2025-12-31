package com.sunmi.tapro.taplink.communication.cable.vsp

import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
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
 * 虚拟串口(VSP)服务内核类
 *
 * 提供虚拟串口通信功能，支持USB转串口等虚拟串口设备
 * 协议格式：vsp://baudRate/dataBits/parity/stopBits
 *
 * 使用 usb-serial-for-android 库实现虚拟串口通信
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class VspServiceKernel(
    appId: String,
    appSecretKey: String,
    private val context: Context
) : AsyncServiceKernel(appId, appSecretKey) {

    private val TAG = "VspServiceKernel"

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private var usbSerialPort: UsbSerialPort? = null
    private var usbSerialDriver: UsbSerialDriver? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var dataReceiveJob: Job? = null

    // VSP配置参数
    private var baudRate: Int = 115200
    private var dataBits: Int = 8
    private var parity: Int = UsbSerialPort.PARITY_NONE
    private var stopBits: Int = UsbSerialPort.STOPBITS_1

    // ==================== AsyncServiceKernel 抽象方法实现 ====================

    override fun getServiceType(): String = "VSP Virtual Serial"

    override fun getExpectedProtocolType(): String = "vsp protocol"

    override fun isValidProtocolType(parseResult: ProtocolParseResult): Boolean {
        return parseResult is ProtocolParseResult.VspProtocol
    }

    override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
        val vspProtocol = parseResult as ProtocolParseResult.VspProtocol
        baudRate = vspProtocol.baudRate
        dataBits = vspProtocol.dataBits
        parity = parseParity(vspProtocol.parity)
        stopBits = parseStopBits(vspProtocol.stopBits)

        LogUtil.d(TAG, "Connecting to VSP: baudRate=$baudRate, dataBits=$dataBits, parity=${vspProtocol.parity}, stopBits=${vspProtocol.stopBits}")

        scope.launch {
            try {
                val success = openVspPort()
                if (success) {
                    notifyConnectionSuccess(
                        mapOf(
                            "baudRate" to baudRate.toString(),
                            "dataBits" to dataBits.toString(),
                            "parity" to vspProtocol.parity,
                            "stopBits" to vspProtocol.stopBits.toString()
                        )
                    )
                } else {
                    notifyConnectionError("Failed to open VSP port", InnerErrorCode.C25)
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Failed to connect VSP port: ${e.message}")
                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.C25)
            }
        }
    }

    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
        try {
            if (usbSerialPort == null) {
                LogUtil.e(TAG, "VSP port is null")
                callback?.onError("NOT_CONNECTED", "VSP port not connected")
                return
            }

            usbSerialPort?.write(data, 1000) // 1秒超时
            LogUtil.d(TAG, "VSP data sent: ${data.size} bytes")
            callback?.onResponse("SUCCESS")
        } catch (e: IOException) {
            LogUtil.e(TAG, "Failed to send VSP data: ${e.message}")
            callback?.onError("SEND_FAILED", e.message ?: "Unknown error")
        }
    }

    override fun performDisconnect() {
        try {
            // 停止数据接收
            dataReceiveJob?.cancel()
            dataReceiveJob = null

            // 关闭串口
            usbSerialPort?.close()
            usbSerialPort = null
            
            // 关闭USB连接
            usbConnection?.close()
            usbConnection = null
            
            usbSerialDriver = null

            LogUtil.d(TAG, "VSP port disconnected")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during VSP disconnect: ${e.message}")
        }
    }

    // ==================== VSP操作 ====================

    /**
     * 打开虚拟串口
     */
    private suspend fun openVspPort(): Boolean {
        return try {

            // 查找可用的USB串口设备
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

            if (availableDrivers.isEmpty()) {
                LogUtil.e(TAG, "No USB serial drivers found")
                return false
            }

            // 使用第一个可用的驱动
            val driver = availableDrivers[0]
            val usbDevice = driver.device

            // 检查权限
            if (!usbManager.hasPermission(usbDevice)) {
                LogUtil.e(TAG, "No permission for USB device: ${usbDevice.deviceName}")
                return false
            }

            // 打开USB设备连接
            val connection = usbManager.openDevice(usbDevice) ?: run {
                LogUtil.e(TAG, "Failed to open USB device")
                return false
            }

            // 获取串口（通常使用第一个端口）
            if (driver.ports.isEmpty()) {
                LogUtil.e(TAG, "Driver has no ports available")
                connection.close()
                return false
            }
            
            val port = driver.ports[0]

            // 打开串口
            port.open(connection)
            
            // 保存连接引用，以便在断开时正确关闭
            usbConnection = connection

            // 设置串口参数
            port.setParameters(baudRate, dataBits, stopBits, parity)

            usbSerialPort = port
            usbSerialDriver = driver

            LogUtil.d(TAG, "VSP port opened: baudRate=$baudRate, dataBits=$dataBits, parity=$parity, stopBits=$stopBits")

            // 启动数据读取
            startDataReceive()

            true
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to open VSP port: ${e.message}")
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
                   usbSerialPort != null) {
                try {
                    val bytesRead = usbSerialPort?.read(buffer, 1000) ?: 0

                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        LogUtil.d(TAG, "VSP data received: $bytesRead bytes")
                        dataReceiver?.invoke(data)
                    }
                } catch (e: IOException) {
                    LogUtil.e(TAG, "Error receiving VSP data: ${e.message}")
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        updateStatus(InnerConnectionStatus.ERROR)
                    }
                    break
                }
            }
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
            "baudRate" to baudRate,
            "dataBits" to dataBits,
            "parity" to parity,
            "stopBits" to stopBits
        )
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