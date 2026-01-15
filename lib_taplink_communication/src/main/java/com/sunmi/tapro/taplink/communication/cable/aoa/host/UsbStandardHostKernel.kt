package com.sunmi.tapro.taplink.communication.cable.aoa.host

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.sunmi.tapro.taplink.communication.cable.aoa.manager.AoaErrorHandler
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.protocol.HexFrameBuffer
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.util.InnerUtil
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * USB AOA Host模式内核类
 *
 * 负责处理USB AOA Host模式的所有逻辑：
 * - 查找Android设备
 * - 发送AOA协议命令启动Accessory模式
 * - 连接Accessory设备
 * - 数据传输（发送和接收）
 *
 * AOA协议流程：
 * 1. 连接到Android设备（作为USB设备）
 * 2. 发送GET_PROTOCOL命令查询AOA协议版本
 * 3. 发送SEND_STRING命令发送Accessory信息
 * 4. 发送START命令启动Accessory模式
 * 5. 等待设备切换到Accessory模式后连接
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class UsbStandardHostKernel(
    appId: String,
    appSecretKey: String,
    context: Context,
    usbStandardInfo: UsbStandardProtocol.UsbStandardInfo,
    permissionAction: String,
    permissionPendingIntent: PendingIntent
) : BaseUsbHostKernel(appId, appSecretKey, context, usbStandardInfo, permissionAction, permissionPendingIntent) {

    override val TAG = "UsbAoaHostKernel"

    // 添加标志防止重复处理AOA设备连接
    private var aoaConnectionInProgress = false

    // 集成AOA错误处理器（重连由SDK层统一管理）
    private val aoaErrorHandler = AoaErrorHandler()

    // 十六进制帧缓冲区管理器
    private var hexFrameBuffer: HexFrameBuffer? = null

    /**
     * 重写断开连接方法，添加Host特有的清理逻辑
     */
    override fun performDisconnect() {
        LogUtil.d(TAG, "AOA Host disconnect started")

        try {
            aoaConnectionInProgress = false
            hexFrameBuffer?.stop()
            hexFrameBuffer = null
            synchronized(this) {
                pendingDevice = null
            }
            super.performDisconnect()
            LogUtil.d(TAG, "AOA Host disconnect completed")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during disconnect: ${e.message}")
        }
    }

    /**
     * 权限授予后的回调
     */
    override suspend fun onPermissionGranted(device: UsbDevice, callback: ConnectionCallback) {
        startAoaProtocol(device, callback)
    }

    /**
     * 设备连接时的回调
     */
    override fun onDeviceAttached(device: UsbDevice) {
        LogUtil.d(TAG, "Device attached: ${device.deviceName} (VID: ${String.format("0x%04X", device.vendorId)}, PID: ${String.format("0x%04X", device.productId)})")

        if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
            LogUtil.d(TAG, "AOA device attached, status: $currentInnerConnectionStatus")
        }
    }

    /**
     * 设备断开时的回调
     */
    override fun onDeviceDetached(device: UsbDevice) {
        LogUtil.d(TAG, "Device detached: ${device.deviceName} (VID: ${String.format("0x%04X", device.vendorId)}, PID: ${String.format("0x%04X", device.productId)})")

        if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId) &&
            currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) {
            LogUtil.w(TAG, "AOA device detached while waiting for connection")
        }
    }

    /**
     * 执行USB连接逻辑
     */
    override suspend fun performUsbConnect(
        parseResult: ProtocolParseResult.UsbProtocol,
        connectionCallback: ConnectionCallback
    ) {
        try {
            val devices = usbManager.deviceList
            LogUtil.d(TAG, "Starting USB connect, found ${devices.size} device(s)")
            
            // 检查是否存在AOA设备
            val aoaDevice = devices.values.find { device ->
                UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
            }
            
            if (aoaDevice != null) {
                LogUtil.d(TAG, "Found existing AOA device: ${aoaDevice.deviceName}")
                if (usbManager.hasPermission(aoaDevice)) {
                    connectToAoaDevice(aoaDevice, connectionCallback)
                } else {
                    requestPermission(aoaDevice, connectionCallback)
                }
                return
            }
            
            // 查找需要切换的Android设备
            val device = when {
                parseResult.deviceName != null -> devices.values.firstOrNull { it.deviceName == parseResult.deviceName }
                parseResult.deviceId != null -> devices.values.firstOrNull { it.deviceId.toString() == parseResult.deviceId }
                else -> devices.values.firstOrNull()
            }

            if (device == null) {
                LogUtil.e(TAG, "No suitable USB device found")
                handleConnectionError(InnerErrorCode.E252.description, connectionCallback, InnerErrorCode.E252.code)
                return
            }
            
            LogUtil.d(TAG, "Selected device: ${device.deviceName} (VID: ${String.format("0x%04X", device.vendorId)}, PID: ${String.format("0x%04X", device.productId)})")

            if (usbManager.hasPermission(device)) {
                startAoaProtocol(device, connectionCallback)
            } else {
                requestPermission(device, connectionCallback)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect: ${e.message}")
            handleConnectionError(
                "${InnerErrorCode.E212.description}:${e.message ?: "Unknown error"}",
                connectionCallback,
                InnerErrorCode.E212.code
            )
        }
    }

    /**
     * 开始AOA协议流程
     * 
     * 注意：调用此方法前必须确保已有USB设备权限
     */
    private suspend fun startAoaProtocol(device: UsbDevice, connectionCallback: ConnectionCallback) {
        try {
            LogUtil.d(TAG, "Starting AOA protocol for ${device.deviceName}")

            if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
                LogUtil.d(TAG, "Device already in AOA mode")
                connectToAoaDevice(device, connectionCallback)
                return
            }

            val connection = usbManager.openDevice(device) ?: run {
                LogUtil.e(TAG, "Failed to open USB device")
                handleConnectionError(InnerErrorCode.E252.description, connectionCallback, InnerErrorCode.E252.code)
                return
            }

            val aoaDeviceInfo = UsbStandardProtocol.createDefaultAoaDeviceInfo()

            if (!UsbStandardProtocol.sendAoaProtocolSequence(connection, aoaDeviceInfo)) {
                LogUtil.e(TAG, "Failed to send AOA protocol sequence")
                connection.close()
                handleConnectionError(InnerErrorCode.E254.description, connectionCallback, InnerErrorCode.E254.code)
                return
            }
            
            connection.close()
            LogUtil.d(TAG, "AOA protocol sent, waiting for device to switch")

            updateStatus(InnerConnectionStatus.WAITING_CONNECT)
            currentConnectionCallback = connectionCallback
            waitForAoaAccessory(connectionCallback)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start AOA protocol: ${e.message}")
            disconnectInternal()
            handleConnectionError(
                "${InnerErrorCode.E254.description}:${e.message ?: "Unknown error"}",
                connectionCallback,
                InnerErrorCode.E254.code
            )
        }
    }

    /**
     * 等待AOA Accessory出现并连接
     */
    private suspend fun waitForAoaAccessory(connectionCallback: ConnectionCallback) {
        val maxWaitTime = 15000L // 15秒
        val checkInterval = 200L
        val maxAttempts = (maxWaitTime / checkInterval).toInt()

        var attempts = 0
        var aoaPermissionRequested = false // 标记是否已经请求过AOA设备权限

        while (attempts < maxAttempts &&
            (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING ||
                    currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT)
        ) {
            delay(checkInterval)
            attempts++

            // 检查连接状态是否已改变（可能通过广播连接了）
            if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTING &&
                currentInnerConnectionStatus != InnerConnectionStatus.WAITING_CONNECT
            ) {
                LogUtil.d(TAG, "Connection established via broadcast")
                return
            }

            // 检查是否有AOA设备出现
            val aoaDevice = usbManager.deviceList.values.find { device ->
                UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
            }

            if (aoaDevice != null && !aoaConnectionInProgress) {
                LogUtil.d(TAG, "AOA device detected: ${aoaDevice.deviceName}")
                LogUtil.d(
                    TAG, "VID: ${String.format("0x%04X", aoaDevice.vendorId)}, " +
                            "PID: ${String.format("0x%04X", aoaDevice.productId)}"
                )

                // 设置标志防止重复处理
                aoaConnectionInProgress = true

                try {
                    // 检查是否已有权限
                    if (usbManager.hasPermission(aoaDevice)) {
                        LogUtil.d(TAG, "AOA device already has permission, connecting directly...")
                        connectToAoaDeviceDirectly(aoaDevice, connectionCallback)
                        return
                    } else if (!aoaPermissionRequested) {
                        // 只请求一次AOA设备权限
                        LogUtil.d(TAG, "Requesting permission for AOA device")
                        LogUtil.i(
                            TAG,
                            "Note: This is the second permission request - AOA protocol requires separate permissions for:"
                        )
                        LogUtil.i(TAG, "  1. Original device (for AOA protocol setup)")
                        LogUtil.i(TAG, "  2. AOA accessory device (for data communication)")
                        aoaPermissionRequested = true
                        connectToAoaDevice(aoaDevice, connectionCallback)
                        return
                    }
                } catch (e: Exception) {
                    LogUtil.w(TAG, "Failed to connect to AOA device: ${e.message}")
                    aoaConnectionInProgress = false // 重置标志
                }
            }

            // 每5秒打印一次状态
            if (attempts % 25 == 0) {
                LogUtil.d(TAG, "Waiting for AOA device... (${attempts * checkInterval}ms elapsed)")
                if (aoaPermissionRequested) {
                    LogUtil.d(TAG, "AOA device permission already requested, waiting for user response...")
                }
            }
        }

        // 超时处理
        aoaConnectionInProgress = false // 重置标志

        val finalAoaDevice = usbManager.deviceList.values.find { device ->
            UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
        }

        if (finalAoaDevice != null) {
            LogUtil.w(TAG, "AOA device found but connection failed after ${maxWaitTime}ms")
            LogUtil.w(
                TAG, "Device: ${finalAoaDevice.deviceName} " +
                        "(VID: ${String.format("0x%04X", finalAoaDevice.vendorId)}, " +
                        "PID: ${String.format("0x%04X", finalAoaDevice.productId)})"
            )
            if (!aoaPermissionRequested) {
                LogUtil.w(TAG, "Permission was never requested for AOA device")
            } else {
                LogUtil.w(TAG, "Permission was requested but user may have denied it or timeout occurred")
            }
        } else {
            LogUtil.w(TAG, "No AOA device found after ${maxWaitTime}ms")
            LogUtil.w(TAG, "Target device may not support AOA or failed to switch to Accessory mode")
        }

        updateStatus(InnerConnectionStatus.DISCONNECTED)
        handleConnectionError(InnerErrorCode.E213.description, connectionCallback, InnerErrorCode.E213.code)
    }

    /**
     * 连接到AOA设备
     */
    private suspend fun connectToAoaDevice(device: UsbDevice, connectionCallback: ConnectionCallback) {
        try {
            LogUtil.d(TAG, "Attempting to connect to AOA device: ${device.deviceName}")

            // 检查权限
            if (!usbManager.hasPermission(device)) {
                LogUtil.d(TAG, "Requesting permission for AOA device")
                requestPermission(device, connectionCallback)
                return
            }

            // 直接连接
            connectToAoaDeviceDirectly(device, connectionCallback)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect to AOA device: ${e.message}")
            disconnectInternal()
            throw e
        }
    }

    /**
     * 直接连接到AOA设备（已有权限）
     */
    private suspend fun connectToAoaDeviceDirectly(device: UsbDevice, connectionCallback: ConnectionCallback) {
        try {
            LogUtil.d(TAG, "Connecting to AOA device: ${device.deviceName}")

            val connection = usbManager.openDevice(device) ?: throw Exception("Failed to open AOA device")

            // 查找AOA标准接口 (class=255, subclass=66, protocol=1)
            var aoaInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                if (usbInterface.interfaceClass == 255 &&
                    usbInterface.interfaceSubclass == 66 &&
                    usbInterface.interfaceProtocol == 1 &&
                    usbInterface.endpointCount >= 2
                ) {
                    aoaInterface = usbInterface
                    LogUtil.d(TAG, "Found AOA interface[$i]")
                    break
                }

                if (aoaInterface == null &&
                    usbInterface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC &&
                    usbInterface.endpointCount >= 2
                ) {
                    aoaInterface = usbInterface
                }
            }

            if (aoaInterface == null) {
                connection.close()
                throw Exception("No suitable interface found")
            }

            if (!connection.claimInterface(aoaInterface, true)) {
                connection.close()
                throw Exception("Failed to claim interface")
            }

            // 查找端点
            var inEndpoint: UsbEndpoint? = null
            var outEndpoint: UsbEndpoint? = null

            for (i in 0 until aoaInterface.endpointCount) {
                val endpoint = aoaInterface.getEndpoint(i)
                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                        inEndpoint = endpoint
                    } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                        outEndpoint = endpoint
                    }
                }
            }

            if (outEndpoint == null) {
                connection.releaseInterface(aoaInterface)
                connection.close()
                throw Exception("No bulk OUT endpoint found")
            }

            // 保存连接信息
            this.usbDeviceConnection = connection
            this.usbInterface = aoaInterface
            this.usbEndpointIn = inEndpoint
            this.usbEndpointOut = outEndpoint
            this.connectedDevice = device

            // 初始化HexFrame缓冲区
            hexFrameBuffer = HexFrameBuffer(
                scope = scope,
                onFrameReceived = { frame ->
                    dataReceiver?.invoke(frame)
                }
            )

            LogUtil.d(TAG, "AOA device connected, IN: ${inEndpoint?.let { String.format("0x%02X", it.address) } ?: "none"}, OUT: ${String.format("0x%02X", outEndpoint.address)}")

            if (inEndpoint != null) {
                startDataReceive()
            }

            handleConnectionSuccess(connectionCallback, null)
            aoaConnectionInProgress = false


            // 后台验证连接
            scope.launch {
                delay(1000)
                if (!validateConnectionState()) {
                    LogUtil.w(TAG, "Connection validation failed")
                }
            }

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect: ${e.message}")
            aoaConnectionInProgress = false
            disconnectInternal()
            throw e
        }
    }

    /**
     * 验证连接状态
     */
    private fun validateConnectionState(): Boolean {
        return try {
            // 检查设备连接状态
            val isDeviceConnected = connectedDevice != null &&
                    usbDeviceConnection != null &&
                    usbInterface != null

            // 检查端点配置
            val isEndpointValid = usbEndpointIn != null && usbEndpointOut != null

            // 检查端点配置是否符合AOA标准
            val isEndpointConfigValid = validateEndpointConfiguration()

            LogUtil.d(TAG, "Connection validation:")
            LogUtil.d(TAG, "  Device connected: $isDeviceConnected")
            LogUtil.d(TAG, "  Endpoints valid: $isEndpointValid")
            LogUtil.d(TAG, "  Endpoint config valid: $isEndpointConfigValid")

            isDeviceConnected && isEndpointValid && isEndpointConfigValid
        } catch (e: Exception) {
            LogUtil.e(TAG, "Connection validation error: ${e.message}")
            false
        }
    }

    /**
     * 验证端点配置一致性
     */
    private fun validateEndpointConfiguration(): Boolean {
        val interface1 = usbInterface
        if (interface1 == null) {
            LogUtil.w(TAG, "USB interface is null")
            return false
        }

        LogUtil.d(TAG, "Validating endpoint configuration:")
        LogUtil.d(TAG, "  Interface class: ${interface1.interfaceClass} (expected: 255)")
        LogUtil.d(TAG, "  Interface subclass: ${interface1.interfaceSubclass} (expected: 66)")
        LogUtil.d(TAG, "  Interface protocol: ${interface1.interfaceProtocol} (expected: 1)")
        LogUtil.d(TAG, "  OUT endpoint: ${String.format("0x%02X", usbEndpointOut?.address ?: 0)} (expected: 0x02)")
        LogUtil.d(TAG, "  IN endpoint: ${String.format("0x%02X", usbEndpointIn?.address ?: 0)} (expected: 0x82)")

        // 验证接口配置 (AOA标准接口)
        val isInterfaceValid = interface1.interfaceClass == 255 &&
                interface1.interfaceSubclass == 66 &&
                interface1.interfaceProtocol == 1

        // 验证端点地址
        val isEndpointAddressValid = usbEndpointOut?.address == 0x02 &&
                usbEndpointIn?.address == 0x82

        return isInterfaceValid && isEndpointAddressValid
    }

    /**
     * 重写数据接收方法 - Host端使用Bulk传输接收数据
     * 集成AoaErrorHandler进行智能错误处理
     */
    override fun startDataReceive() {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch {
            val buffer = ByteArray(16384) // 增大缓冲区到16KB

            LogUtil.d(TAG, "Starting AOA data receive loop (Host端 Bulk传输)...")
            LogUtil.d(TAG, "Current status: $currentInnerConnectionStatus")

            while (isActive &&
                (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED ||
                        currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) &&
                usbDeviceConnection != null && usbEndpointIn != null
            ) {
                try {
                    // 如果状态还是WAITING_CONNECT，先等待一下状态更新
                    if (currentInnerConnectionStatus != InnerConnectionStatus.CONNECTED) {
                        LogUtil.d(TAG, "Waiting for connection status to become CONNECTED...")
                        delay(100) // 等待100ms让状态更新
                        continue
                    }

                    val bytesRead = usbDeviceConnection!!.bulkTransfer(
                        usbEndpointIn, buffer, buffer.size, 500 // 减少超时时间到500ms
                    )

                    when {
                        bytesRead > 0 -> {
                            // 成功接收数据，重置错误计数
                            aoaErrorHandler.resetErrorCount()
                            val data = buffer.copyOf(bytesRead)
                            LogUtil.d(TAG, "AOA raw data received (Host端): $bytesRead bytes,${InnerUtil.bytes2HexStr(data)}")

                            // 直接处理原始数据，不使用HexFrame解码
                            dataReceiver?.invoke(data)
                        }

                        bytesRead <= 0 -> {
                            // 使用AoaErrorHandler处理错误
                            val isDeviceStillConnected = connectedDevice?.let { device ->
                                usbManager.deviceList.values.any {
                                    it.deviceName == device.deviceName &&
                                            it.vendorId == device.vendorId &&
                                            it.productId == device.productId
                                }
                            } ?: false

                            val errorResult = aoaErrorHandler.handleReceiveError(
                                bytesRead, isDeviceStillConnected, currentInnerConnectionStatus
                            )

                            LogUtil.d(
                                TAG, "Error handling result: shouldContinue=${errorResult.shouldContinue}, " +
                                        "shouldBreak=${errorResult.shouldBreak}, delayMs=${errorResult.delayMs}"
                            )

                            // 根据错误处理结果更新状态
                            errorResult.newStatus?.let { newStatus ->
                                updateStatus(newStatus)
                            }

                            // 根据错误处理结果决定是否继续
                            if (errorResult.shouldBreak) {
                                LogUtil.w(TAG, "Breaking receive loop due to error handling result")
                                break
                            }

                            if (!errorResult.shouldContinue) {
                                LogUtil.w(TAG, "Stopping receive loop due to error handling result")
                                break
                            }

                            // 应用延迟
                            if (errorResult.delayMs > 0) {
                                delay(errorResult.delayMs)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 使用AoaErrorHandler处理异常
                    val isDeviceStillConnected = connectedDevice?.let { device ->
                        usbManager.deviceList.values.any {
                            it.deviceName == device.deviceName
                        }
                    } ?: false

                    val errorResult = aoaErrorHandler.handleException(
                        e, currentInnerConnectionStatus, isDeviceStillConnected
                    )

                    LogUtil.e(
                        TAG, "Exception handling result: shouldContinue=${errorResult.shouldContinue}, " +
                                "shouldBreak=${errorResult.shouldBreak}, delayMs=${errorResult.delayMs}"
                    )

                    // 根据异常处理结果更新状态
                    errorResult.newStatus?.let { newStatus ->
                        updateStatus(newStatus)
                    }

                    // 根据异常处理结果决定是否继续
                    if (errorResult.shouldBreak) {
                        LogUtil.w(TAG, "Breaking receive loop due to exception handling result")
                        break
                    }

                    if (!errorResult.shouldContinue) {
                        LogUtil.w(TAG, "Stopping receive loop due to exception handling result")
                        break
                    }

                    // 应用延迟
                    if (errorResult.delayMs > 0) {
                        delay(errorResult.delayMs)
                    }
                }
            }
            LogUtil.d(TAG, "AOA data receive loop ended (Host端)")
            LogUtil.d(TAG, "Final status: $currentInnerConnectionStatus")
            LogUtil.d(TAG, "Final error stats: ${aoaErrorHandler.getErrorStats()}")
        }
    }

    /**
     * 发送GET_PROTOCOL命令
     */
    private fun sendGetProtocol(connection: UsbDeviceConnection): Int {
        return try {
            val buffer = ByteArray(2)
            val result = connection.controlTransfer(
                UsbStandardProtocol.USB_DIR_IN or UsbStandardProtocol.USB_TYPE_VENDOR,
                UsbStandardProtocol.AOA_GET_PROTOCOL,
                0,
                0,
                buffer,
                buffer.size,
                1000
            )

            if (result == 2) {
                val version = (buffer[0].toInt() and 0xFF) or ((buffer[1].toInt() and 0xFF) shl 8)
                LogUtil.d(TAG, "GET_PROTOCOL response: $version (raw bytes: ${buffer[0]}, ${buffer[1]})")

                // 验证版本号是否合理
                if (version < 1 || version > 2) {
                    LogUtil.w(TAG, "Unusual AOA protocol version: $version")
                }

                version
            } else {
                LogUtil.e(TAG, "GET_PROTOCOL failed: result=$result, expected=2")
                if (result < 0) {
                    LogUtil.e(TAG, "Control transfer error (negative result), device may not support AOA")
                }
                0
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending GET_PROTOCOL: ${e.message}")
            0
        }
    }

    /**
     * 发送SEND_STRING命令
     */
    private fun sendString(connection: UsbDeviceConnection, index: Int, string: String): Boolean {
        return try {
            val data = UsbStandardProtocol.buildSendStringCommand(index, string)
            val result = connection.controlTransfer(
                UsbStandardProtocol.USB_TYPE_VENDOR,
                UsbStandardProtocol.AOA_SEND_STRING,
                0,
                index,
                data,
                data.size,
                1000
            )

            val success = result == data.size
            if (success) {
                LogUtil.d(TAG, "SEND_STRING[$index] sent: $string (${data.size} bytes)")
            } else {
                LogUtil.e(TAG, "SEND_STRING[$index] failed: result=$result, expected=${data.size}")
            }
            success
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending SEND_STRING[$index]: ${e.message}")
            false
        }
    }

    /**
     * 发送START命令
     */
    private fun sendStart(connection: UsbDeviceConnection): Boolean {
        return try {
            val result = connection.controlTransfer(
                UsbStandardProtocol.USB_DIR_OUT or UsbStandardProtocol.USB_TYPE_VENDOR,
                UsbStandardProtocol.AOA_START,
                0,
                0,
                null,
                0,
                1000
            )

            val success = result >= 0  // START命令成功返回值应该是>=0
            if (success) {
                LogUtil.d(TAG, "START command sent successfully (result=$result)")
            } else {
                LogUtil.e(TAG, "START command failed: result=$result")
            }
            success
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending START: ${e.message}")
            false
        }
    }

    /**
     * 实现Host端数据发送 - 临时移除HexFrame协议，直接发送原始数据
     */
    override fun sendDataToDevice(data: ByteArray): Boolean {
        LogUtil.d(TAG, "=== AOA Data Send Analysis ===")
        LogUtil.d(TAG, "AOA sending raw data (HexFrame disabled):")
        LogUtil.d(TAG, "  Size: ${data.size} bytes")

        val tempData = "FF123456"

        try {
            // 尝试作为UTF-8字符串解析
//            val dataAsString = String(data, Charsets.UTF_8)
            val dataAsString = tempData
            LogUtil.d(TAG, "  Content (UTF-8): $dataAsString")
        } catch (e: Exception) {
            LogUtil.d(TAG, "  Content (UTF-8): [Invalid UTF-8 data]")
        }

        // 显示前64字节的十六进制内容
//        val hexPreview = data.take(64).joinToString(" ") { "%02X".format(it) }
//        LogUtil.d(TAG, "  Content (Hex, first 64 bytes): $hexPreview")
//
//        if (data.size > 64) {
//            LogUtil.d(TAG, "  ... (${data.size - 64} more bytes)")
//        }
        LogUtil.d(TAG, "=== End AOA Data Analysis ===")

        // 临时禁用HexFrame编码，直接发送原始数据
        return sendDataToAoaDevice(hexToBytes(tempData))
    }

    fun hexToBytes(hex: String): ByteArray {
        var hex = hex
        hex = hex.replace("0x", "").replace("\\s+".toRegex(), "")
        require((hex.length and 1) != 1) { "hex length must be even" }

        val len = hex.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            out[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return out
    }

    /**
     * 检查连接健康状态
     */
    private fun isConnectionHealthy(): Boolean {
        return try {
            // 检查基本连接状态
            val basicCheck = connectedDevice != null &&
                    usbDeviceConnection != null &&
                    usbInterface != null &&
                    usbEndpointOut != null

            if (!basicCheck) {
                LogUtil.w(TAG, "Basic connection check failed")
                return false
            }

            // 检查设备是否仍在设备列表中
            val deviceExists = try {
                usbManager.deviceList.values.any { it.deviceName == connectedDevice!!.deviceName }
            } catch (e: Exception) {
                LogUtil.w(TAG, "Device list check failed: ${e.message}")
                false
            }

            LogUtil.d(TAG, "Connection health check: basic=$basicCheck, deviceExists=$deviceExists")
            return basicCheck && deviceExists
        } catch (e: Exception) {
            LogUtil.e(TAG, "Connection health check error: ${e.message}")
            false
        }
    }

    /**
     * 发送数据到AOA设备（使用Bulk传输）
     * 集成AoaErrorHandler进行智能错误处理和重试
     */
    private fun sendDataToAoaDevice(data: ByteArray): Boolean {
        // 修复：使用实际传入的数据，而不是硬编码的测试数据
        // 发送前检查连接健康状态
        if (!isConnectionHealthy()) {
            LogUtil.e(TAG, "Connection not healthy, aborting data send")
            return false
        }

        // 发送前添加短暂延迟，确保设备状态稳定
        // 这个延迟可以避免在设备状态不稳定时发送数据，导致USB模式冲突
        // 延迟时间设置为20ms，既能确保设备稳定，又不会显著影响性能
        try {
            Thread.sleep(20) // 20ms延迟，确保设备状态稳定
            LogUtil.d(TAG, "Pre-send stabilization delay completed (20ms)")
        } catch (e: InterruptedException) {
            LogUtil.w(TAG, "Pre-send stabilization delay interrupted: ${e.message}")
            Thread.currentThread().interrupt() // 恢复中断状态
        }
        
        // 重要：发送前再次检查设备是否仍然连接
        // 根据日志分析，设备可能在发送前就被系统移除了
        val deviceList = usbManager.deviceList
        val currentDevice = deviceList.values.find {
            it.deviceName == connectedDevice!!.deviceName &&
                    it.vendorId == connectedDevice!!.vendorId &&
                    it.productId == connectedDevice!!.productId
        }
        
        if (currentDevice == null) {
            LogUtil.e(TAG, "Device no longer in system list before send, aborting")
            LogUtil.e(TAG, "This may indicate the device was removed or USB state changed")
            return false
        }
        
        LogUtil.d(TAG, "Device still in system list, proceeding with data send")

        return try {
            // 详细检查连接状态
            if (usbDeviceConnection == null) {
                LogUtil.e(TAG, "USB device connection is null")
                return false
            }

            if (usbEndpointOut == null) {
                LogUtil.e(TAG, "USB output endpoint is null")
                return false
            }

            if (connectedDevice == null) {
                LogUtil.e(TAG, "Connected device is null")
                return false
            }

            // 检查设备连接状态
            val deviceList = usbManager.deviceList
            val currentDevice = deviceList.values.find {
                it.deviceName == connectedDevice!!.deviceName &&
                        it.vendorId == connectedDevice!!.vendorId &&
                        it.productId == connectedDevice!!.productId
            }

            if (currentDevice == null) {
                LogUtil.e(TAG, "AOA device is no longer connected")
                return false
            }

            // 检查USB设备权限（重要：确保有权限才能发送数据）
            if (!usbManager.hasPermission(currentDevice)) {
                LogUtil.e(TAG, "USB device permission lost, cannot send data")
                LogUtil.e(
                    TAG,
                    "Device: ${currentDevice.deviceName}, VID: ${
                        String.format(
                            "0x%04X",
                            currentDevice.vendorId
                        )
                    }, PID: ${String.format("0x%04X", currentDevice.productId)}"
                )
                updateStatus(InnerConnectionStatus.ERROR)
                return false
            }
            LogUtil.d(TAG, "USB device permission verified: ${currentDevice.deviceName}")

            // 检查端点信息
            LogUtil.d(TAG, "Sending data to AOA device:")
            LogUtil.d(TAG, "  Device: ${connectedDevice!!.deviceName}")
            LogUtil.d(TAG, "  Data size: ${data.size} bytes")
            LogUtil.d(
                TAG,
                "  Endpoint: ${
                    String.format(
                        "0x%02X",
                        usbEndpointOut!!.address
                    )
                } (maxPacketSize: ${usbEndpointOut!!.maxPacketSize})"
            )
            LogUtil.d(TAG, "  Interface: ${usbInterface?.id}")

            // 实验性改动：不再在发送时重新 claim 接口
            // 接口应该在连接时（connectToAoaDeviceDirectly）已经 claim 一次
            // 频繁 claim 可能导致系统断开设备连接
            // 只检查接口和连接是否存在，不进行 claim 操作
            if (usbInterface == null || usbDeviceConnection == null) {
                LogUtil.e(TAG, "USB interface or connection is null, cannot send data")
                return false
            }

            // 验证接口是否仍然有效（但不 claim）
            val interfaceId = usbInterface?.id
            LogUtil.d(
                TAG,
                "USB interface check passed (interface ID: $interfaceId), interface should already be claimed during connection"
            )

            // 使用AoaErrorHandler进行智能重试
            val maxRetries = 3

            repeat(maxRetries) { attempt ->
                try {
                    // 添加详细的发送前日志
                    LogUtil.d(TAG, "Attempting to send data (attempt ${attempt + 1}/$maxRetries):")
                    LogUtil.d(TAG, "  Data size: ${data.size} bytes")
                    LogUtil.d(TAG, "  Endpoint: ${String.format("0x%02X", usbEndpointOut!!.address)}")
                    LogUtil.d(TAG, "  Interface: ${usbInterface?.id}")
                    LogUtil.d(TAG, "  Device: ${connectedDevice!!.deviceName}")
                    LogUtil.d(TAG, "  Data preview (first 32 bytes): ${data.take(32).joinToString(" ") { "%02X".format(it) }}")
                    
                    // 验证接口是否仍然被claim（重要：确保接口没有被释放）
                    val interfaceId = usbInterface?.id
                    if (interfaceId == null) {
                        LogUtil.e(TAG, "USB interface is null, cannot send data")
                        return false
                    }
                    
                    // 检查接口状态（但不重新claim，避免干扰）
                    // 注意：在Android中，无法直接查询接口是否被claim，只能通过尝试使用来验证
                    LogUtil.d(TAG, "Verifying interface state before bulk transfer: interfaceId=$interfaceId")
                    LogUtil.d(TAG, "Connection state: connection=${usbDeviceConnection != null}, endpoint=${usbEndpointOut != null}")
                    
                    // 执行bulk传输
                    val startTime = System.currentTimeMillis()
                    val result = usbDeviceConnection!!.bulkTransfer(
                        usbEndpointOut, data, data.size, 5000 // 5秒超时
                    )
                    val transferTime = System.currentTimeMillis() - startTime

                    LogUtil.d(TAG, "Bulk transfer result: $result (expected: ${data.size}), transferTime=${transferTime}ms")
                    
                    // 如果返回负数，说明传输失败
                    if (result < 0) {
                        LogUtil.e(TAG, "Bulk transfer failed with error code: $result")
                        LogUtil.e(TAG, "Common error codes: -1=timeout, -2=pipe error, -3=no device")
                    }
                    
                    if (result == data.size) {
                        // 发送成功，重置错误计数
                        aoaErrorHandler.resetErrorCount()
                        LogUtil.d(TAG, "AOA data sent successfully: ${data.size} bytes (attempt ${attempt + 1})")

                        // 添加短暂的发送后延迟，确保Accessory端有时间处理数据
                        // 这个延迟可以避免在Accessory端还在处理数据时进行其他操作，导致USB模式冲突
                        // 延迟时间设置为50ms，既能给Accessory端足够时间，又不会显著影响性能
                        try {
                            Thread.sleep(50) // 50ms延迟，让Accessory端处理数据
                            LogUtil.d(TAG, "Post-send stabilization delay completed (50ms)")
                        } catch (e: InterruptedException) {
                            LogUtil.w(TAG, "Post-send stabilization delay interrupted: ${e.message}")
                            Thread.currentThread().interrupt() // 恢复中断状态
                        }

                        return true
                    } else {
                        // 使用AoaErrorHandler处理发送错误
                        val currentDeviceConnected = deviceList.values.any {
                            it.deviceName == connectedDevice!!.deviceName
                        }

                        val errorResult = aoaErrorHandler.handleSendError(
                            result, data.size, attempt, maxRetries, currentDeviceConnected
                        )

                        LogUtil.w(
                            TAG, "Send error handling result (attempt ${attempt + 1}): " +
                                    "shouldContinue=${errorResult.shouldContinue}, " +
                                    "shouldBreak=${errorResult.shouldBreak}, delayMs=${errorResult.delayMs}"
                        )

                        // 根据错误处理结果更新状态
                        errorResult.newStatus?.let { newStatus ->
                            updateStatus(newStatus)
                        }

                        // 根据错误处理结果决定是否继续重试
                        if (errorResult.shouldBreak) {
                            LogUtil.w(TAG, "Breaking send retry due to error handling result")
                            return false
                        }

                        if (!errorResult.shouldContinue) {
                            LogUtil.w(TAG, "Stopping send retry due to error handling result")
                            return false
                        }

                        // 应用延迟
                        if (errorResult.delayMs > 0) {
                            Thread.sleep(errorResult.delayMs)
                        }
                    }

                } catch (e: Exception) {
                    LogUtil.e(TAG, "Exception during data send (attempt ${attempt + 1}): ${e.message}")

                    // 最后一次尝试时抛出异常
                    if (attempt == maxRetries - 1) {
                        throw e
                    }

                    // 其他情况下短暂延迟后继续
                    Thread.sleep(100)
                }
            }

            LogUtil.e(TAG, "Failed to send data after $maxRetries attempts")
            LogUtil.e(TAG, "Final error stats: ${aoaErrorHandler.getErrorStats()}")
            false

        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending data to AOA device: ${e.message}")
            LogUtil.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            LogUtil.e(TAG, "Error stats: ${aoaErrorHandler.getErrorStats()}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 清理资源
     */
    override fun release() {
        LogUtil.d(TAG, "=== AOA Host Release Started ===")

        try {
            // 先断开连接
            disconnect()

            // 重置AOA连接标志
            aoaConnectionInProgress = false

            // 停止并清空HexFrame缓冲区
            hexFrameBuffer?.stop()
            hexFrameBuffer = null

            // 调用基类的清理逻辑
            super.release()

            LogUtil.d(TAG, "AOA Host resources released successfully")
            LogUtil.d(TAG, "Final error stats: ${aoaErrorHandler.getErrorStats()}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during AOA Host release: ${e.message}")
            e.printStackTrace()
        }

        LogUtil.d(TAG, "=== AOA Host Release Finished ===")
    }

    /**
     * 检查设备是否仍然连接
     */
    private fun isDeviceConnected(): Boolean {
        return try {
            connectedDevice?.let { device ->
                usbManager.deviceList.values.any {
                    it.deviceName == device.deviceName &&
                            it.vendorId == device.vendorId &&
                            it.productId == device.productId
                }
            } ?: false
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error checking device connection: ${e.message}")
            false
        }
    }

    companion object {
        const val ACTION_USB_STANDARD_HOST_KERNEL = "com.sunmi.tapro.taplink.host.USB_PERMISSION"
        const val REQUEST_CODE_FOR_USB_PERMISSION = 0

        fun createRequestPermissionPendingIntent(context: Context): PendingIntent {
            return PendingIntent.getBroadcast(
                context, REQUEST_CODE_FOR_USB_PERMISSION,
                Intent(ACTION_USB_STANDARD_HOST_KERNEL).apply {
                    setPackage(context.packageName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }
    }
}