package com.sunmi.tapro.taplink.communication.cable.aoa.host

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
import com.sunmi.tapro.taplink.communication.protocol.UsbStandardProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

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

    /**
     * 重写断开连接方法，添加Host特有的清理逻辑
     */
    override fun performDisconnect() {
        LogUtil.d(TAG, "=== AOA Host Disconnect Started ===")

        try {
            // 重置AOA连接标志
            aoaConnectionInProgress = false

            // 清理待处理的权限请求
            synchronized(this) {
                pendingDevice = null
            }

            // 调用基类的断开连接逻辑
            super.performDisconnect()

            LogUtil.d(TAG, "AOA Host disconnect completed successfully")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during AOA Host disconnect: ${e.message}")
        }

        LogUtil.d(TAG, "=== AOA Host Disconnect Finished ===")
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
        LogUtil.d(TAG, "=== USB DEVICE ATTACHED ===")
        LogUtil.d(TAG, "Device attached: ${device.deviceName}")
        LogUtil.d(TAG, "VID: ${String.format("0x%04X", device.vendorId)}, PID: ${String.format("0x%04X", device.productId)}")
        LogUtil.d(TAG, "Current status: $currentInnerConnectionStatus")

        // 检查是否是AOA设备
        if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
            LogUtil.d(TAG, "AOA device attached via broadcast: ${device.deviceName}")
            // 只在等待状态时尝试连接，避免与waitForAoaAccessory重复处理
            if (currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT ||
                currentInnerConnectionStatus == InnerConnectionStatus.CONNECTING
            ) {
                LogUtil.d(TAG, "Host is waiting for AOA device, but waitForAoaAccessory will handle this")
                // 不在这里处理连接，让waitForAoaAccessory方法来处理
                // 这样避免重复的权限请求
            } else {
                LogUtil.d(TAG, "AOA device attached but Host not waiting (current: $currentInnerConnectionStatus)")
            }
        } else {
            LogUtil.d(TAG, "Non-AOA device attached: ${device.deviceName}")
        }
        LogUtil.d(TAG, "=== END USB DEVICE ATTACHED ===")
    }

    /**
     * 设备断开时的回调
     */
    override fun onDeviceDetached(device: UsbDevice) {
        LogUtil.d(TAG, "=== USB DEVICE DETACHED ===")
        LogUtil.d(TAG, "Device detached: ${device.deviceName}")
        LogUtil.d(TAG, "VID: ${String.format("0x%04X", device.vendorId)}, PID: ${String.format("0x%04X", device.productId)}")

        // 基础的断开逻辑已经在BaseUsbHostKernel.deviceReceiver中处理了
        // 这里只需要添加AOA特定的日志和处理
        if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
            LogUtil.d(TAG, "AOA device detached")

            // 如果正在等待连接，记录状态
            if (currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) {
                LogUtil.w(TAG, "AOA device detached while waiting for connection")
            }
        } else {
            LogUtil.d(TAG, "Non-AOA device detached")
        }
        LogUtil.d(TAG, "=== END USB DEVICE DETACHED ===")
    }

    /**
     * 执行USB连接逻辑
     */
    override suspend fun performUsbConnect(
        parseResult: ProtocolParseResult.UsbProtocol,
        connectionCallback: ConnectionCallback
    ) {
        try {
            // 从已连接的设备中查找匹配的设备
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
                return
            }

            // 检查权限
            if (usbManager.hasPermission(device)) {
                // 已有权限，直接开始AOA协议
                startAoaProtocol(device, connectionCallback)
            } else {
                // 没有权限，请求权限
                LogUtil.d(TAG, "Requesting permission for USB device: ${device.deviceName}")
                requestPermission(device, connectionCallback)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect USB AOA host device: ${e.message}")
            handleConnectionError(e.message ?: "Unknown error", connectionCallback)
        }
    }

    /**
     * 开始AOA协议流程
     */
    private suspend fun startAoaProtocol(device: UsbDevice, connectionCallback: ConnectionCallback) {
        try {
            if (!usbManager.hasPermission(device)) {
                LogUtil.e(TAG, "No permission for USB device: ${device.deviceName}")
                handleConnectionError("No permission for USB device", connectionCallback)
                return
            }

            LogUtil.d(TAG, "=== Starting AOA Protocol ===")
            LogUtil.d(
                TAG,
                "Original device: ${device.deviceName} (VID: ${
                    String.format(
                        "0x%04X",
                        device.vendorId
                    )
                }, PID: ${String.format("0x%04X", device.productId)})"
            )

            // 预先检查是否已经有AOA设备存在（可能之前已经切换过）
            val existingAoaDevice = usbManager.deviceList.values.find { aoaDevice ->
                UsbStandardProtocol.isAoaCompatible(aoaDevice.vendorId, aoaDevice.productId)
            }

            if (existingAoaDevice != null) {
                LogUtil.d(TAG, "Found existing AOA device, connecting directly...")
                connectToAoaDevice(existingAoaDevice, connectionCallback)
                return
            }

            // 打开设备连接
            val connection = usbManager.openDevice(device) ?: run {
                LogUtil.e(TAG, "Failed to open USB device")
                handleConnectionError("Failed to open USB device", connectionCallback)
                return
            }

            LogUtil.d(TAG, "USB device opened: ${device.deviceName}")
            this.usbDeviceConnection = connection
            this.connectedDevice = device

            // 1. 发送GET_PROTOCOL命令
            val protocolVersion = sendGetProtocol(connection)
            if (protocolVersion <= 0) {
                LogUtil.e(TAG, "Device does not support AOA protocol (version: $protocolVersion)")
                disconnectInternal()
                handleConnectionError("Device does not support AOA protocol", connectionCallback)
                return
            }

            LogUtil.d(TAG, "AOA protocol version: $protocolVersion")

            // 2. 发送SEND_STRING命令
            val strings = listOf(
                Pair(UsbStandardProtocol.AOA_STRING_MANUFACTURER, usbStandardInfo.manufacturer),
                Pair(UsbStandardProtocol.AOA_STRING_MODEL, usbStandardInfo.model),
                Pair(UsbStandardProtocol.AOA_STRING_DESCRIPTION, usbStandardInfo.description),
                Pair(UsbStandardProtocol.AOA_STRING_VERSION, usbStandardInfo.version),
                Pair(UsbStandardProtocol.AOA_STRING_URI, usbStandardInfo.uri),
                Pair(UsbStandardProtocol.AOA_STRING_SERIAL, usbStandardInfo.serial)
            )

            for ((index, string) in strings) {
                if (!sendString(connection, index, string)) {
                    LogUtil.e(TAG, "Failed to send SEND_STRING[$index]: $string")
                    disconnectInternal()
                    handleConnectionError("Failed to send AOA string $index", connectionCallback)
                    return
                }
                delay(50)
            }

            // 3. 发送START命令
            if (!sendStart(connection)) {
                LogUtil.e(TAG, "Failed to send START command")
                disconnectInternal()
                handleConnectionError("Failed to send START command", connectionCallback)
                return
            }
            // 关闭设备连接
            connection.close()
            LogUtil.d(TAG, "AOA protocol completed successfully")
            LogUtil.d(TAG, "Device will now re-enumerate as AOA Accessory...")
            LogUtil.d(TAG, "Expected AOA device VID/PID: 0x18D1/0x2D00-0x2D05")
            LogUtil.i(TAG, "Note: A second permission dialog will appear for the AOA accessory device")


            this.usbDeviceConnection = null
            this.connectedDevice = null

            // 设置状态并等待Accessory
            updateStatus(InnerConnectionStatus.WAITING_CONNECT)
            currentConnectionCallback = connectionCallback

            // 等待AOA设备出现
            waitForAoaAccessory(connectionCallback)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to start AOA protocol: ${e.message}")
            disconnectInternal()
            handleConnectionError(e.message ?: "Unknown error", connectionCallback)
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
        handleConnectionError("AOA connection timeout", connectionCallback)
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
            LogUtil.d(TAG, "Connecting directly to AOA device: ${device.deviceName}")
            LogUtil.d(
                TAG,
                "Device info: VID=${String.format("0x%04X", device.vendorId)}, PID=${String.format("0x%04X", device.productId)}"
            )

            // 打开设备连接
            val connection = usbManager.openDevice(device) ?: run {
                throw Exception("Failed to open AOA device")
            }

            LogUtil.d(TAG, "Device opened successfully, interface count: ${device.interfaceCount}")

            // 查找合适的接口
            var aoaInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val usbInterface = device.getInterface(i)
                LogUtil.d(
                    TAG,
                    "Interface[$i]: class=${usbInterface.interfaceClass}, subclass=${usbInterface.interfaceSubclass}, protocol=${usbInterface.interfaceProtocol}"
                )
                LogUtil.d(TAG, "  Endpoint count: ${usbInterface.endpointCount}")

                // 打印所有端点信息
                for (j in 0 until usbInterface.endpointCount) {
                    val endpoint = usbInterface.getEndpoint(j)
                    LogUtil.d(
                        TAG, "    Endpoint[$j]: address=${String.format("0x%02X", endpoint.address)}, " +
                                "type=${endpoint.type}, direction=${if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"}, " +
                                "maxPacketSize=${endpoint.maxPacketSize}"
                    )
                }

                // AOA设备的接口通常是Vendor Specific类或者第一个接口
                if (usbInterface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC ||
                    (aoaInterface == null && usbInterface.endpointCount >= 2)
                ) {
                    aoaInterface = usbInterface
                    LogUtil.d(TAG, "Selected interface[$i] for AOA communication")
                }
            }

            if (aoaInterface == null) {
                connection.close()
                throw Exception("No suitable interface found on AOA device")
            }

            // 声明接口
            LogUtil.d(TAG, "Claiming interface...")
            if (!connection.claimInterface(aoaInterface, true)) {
                connection.close()
                throw Exception("Failed to claim AOA interface")
            }
            LogUtil.d(TAG, "Interface claimed successfully")

            // 查找端点
            var inEndpoint: UsbEndpoint? = null
            var outEndpoint: UsbEndpoint? = null

            for (i in 0 until aoaInterface.endpointCount) {
                val endpoint = aoaInterface.getEndpoint(i)
                LogUtil.d(
                    TAG, "Checking endpoint[$i]: address=${String.format("0x%02X", endpoint.address)}, " +
                            "type=${endpoint.type}, direction=${endpoint.direction}"
                )

                if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                        inEndpoint = endpoint
                        LogUtil.d(TAG, "Found IN endpoint: ${String.format("0x%02X", endpoint.address)}")
                    } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                        outEndpoint = endpoint
                        LogUtil.d(TAG, "Found OUT endpoint: ${String.format("0x%02X", endpoint.address)}")
                    }
                }
            }

            if (outEndpoint == null) {
                connection.releaseInterface(aoaInterface)
                connection.close()
                throw Exception("No bulk OUT endpoint found on AOA device")
            }

            // 保存连接信息
            this.usbDeviceConnection = connection
            this.usbInterface = aoaInterface
            this.usbEndpointIn = inEndpoint
            this.usbEndpointOut = outEndpoint
            this.connectedDevice = device

            LogUtil.d(TAG, "AOA device connected successfully")
            LogUtil.d(TAG, "Connection details:")
            LogUtil.d(TAG, "  Interface: class=${aoaInterface.interfaceClass}, id=${aoaInterface.id}")
            LogUtil.d(TAG, "  IN endpoint: ${if (inEndpoint != null) String.format("0x%02X", inEndpoint.address) else "none"}")
            LogUtil.d(
                TAG,
                "  OUT endpoint: ${String.format("0x%02X", outEndpoint.address)} (maxPacketSize: ${outEndpoint.maxPacketSize})"
            )

            // 启动数据接收（如果有输入端点）
            if (inEndpoint != null) {
                LogUtil.d(TAG, "Starting data receive...")
                startDataReceive()
            } else {
                LogUtil.w(TAG, "No IN endpoint found, data receive not available")
            }

            // 连接成功
            LogUtil.d(TAG, "Calling connection success callback...")
            aoaConnectionInProgress = false // 重置标志
            handleConnectionSuccess(connectionCallback, null)

        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to connect directly to AOA device: ${e.message}")
            LogUtil.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            aoaConnectionInProgress = false // 重置标志
            disconnectInternal()
            throw e
        }
    }

    /**
     * 重写数据接收方法 - Host端使用Bulk传输接收数据
     */
    override fun startDataReceive() {
        dataReceiveJob?.cancel()
        dataReceiveJob = scope.launch {
            val buffer = ByteArray(512) // 16KB buffer

            LogUtil.d(TAG, "Starting AOA data receive loop (Host端 Bulk传输)...")

            while (isActive && currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED &&
                usbDeviceConnection != null && usbEndpointIn != null
            ) {
                try {
                    val bytesRead = usbDeviceConnection!!.bulkTransfer(
                        usbEndpointIn, buffer, buffer.size, 1000 // 1秒超时
                    )

                    when {
                        bytesRead > 0 -> {
                            val data = buffer.copyOf(bytesRead)
                            LogUtil.d(TAG, "AOA data received (Host端): $bytesRead bytes")
                            dataReceiver?.invoke(data)
                        }

                        bytesRead == 0 -> {
                            // 超时，继续循环
                            continue
                        }

                        bytesRead == -1 -> {
                            // 传输错误，应该停止接收循环
                            LogUtil.w(TAG, "AOA bulk transfer error (result=-1), device may be disconnected")
                            LogUtil.w(TAG, "Stopping receive loop due to bulk transfer error")
                            
                            // 检查设备是否仍然连接
                            val isDeviceStillConnected = connectedDevice?.let { device ->
                                usbManager.deviceList.values.any { it.deviceName == device.deviceName }
                            } ?: false
                            
                            if (!isDeviceStillConnected) {
                                LogUtil.w(TAG, "AOA device is no longer in device list, device disconnected")
                                updateStatus(InnerConnectionStatus.DISCONNECTED)
                            } else {
                                LogUtil.w(TAG, "AOA device still in device list but bulk transfer failed")
                                updateStatus(InnerConnectionStatus.ERROR)
                            }
                            break
                        }

                        else -> {
                            LogUtil.w(TAG, "Unexpected bulk transfer result: $bytesRead")
                            // 对于其他异常结果，也应该停止循环
                            if (bytesRead < -1) {
                                LogUtil.w(TAG, "Negative bulk transfer result ($bytesRead), stopping receive loop")
                                updateStatus(InnerConnectionStatus.ERROR)
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.e(TAG, "Exception during AOA data receive (Host端): ${e.message}")
                    LogUtil.e(TAG, "Exception type: ${e.javaClass.simpleName}")

                    // 检查是否是因为断开连接导致的异常
                    if (currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED) {
                        LogUtil.w(TAG, "Data receive error while connected, updating status to ERROR")
                        updateStatus(InnerConnectionStatus.ERROR)
                    } else {
                        LogUtil.d(TAG, "Data receive error during disconnect, this is expected")
                    }
                    break
                }
            }
            LogUtil.d(TAG, "AOA data receive loop ended (Host端)")
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
                UsbStandardProtocol.USB_DIR_OUT or UsbStandardProtocol.USB_TYPE_VENDOR,
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
     * 实现Host端数据发送 - 使用Bulk传输发送数据到AOA设备
     */
    override fun sendDataToDevice(data: ByteArray): Boolean {
        return sendDataToAoaDevice(data)
    }

    /**
     * 发送数据到AOA设备（使用Bulk传输）
     */
    private fun sendDataToAoaDevice(data: ByteArray): Boolean {
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
            val isDeviceStillConnected = deviceList.values.any { it.deviceName == connectedDevice!!.deviceName }

            if (!isDeviceStillConnected) {
                LogUtil.e(TAG, "AOA device is no longer connected")
                return false
            }

            // 检查端点信息
            LogUtil.d(TAG, "Sending data to AOA device:")
            LogUtil.d(TAG, "  Device: ${connectedDevice!!.deviceName}")
            LogUtil.d(TAG, "  Data size: ${data.size} bytes")
            LogUtil.d(TAG, "  Endpoint: ${usbEndpointOut!!.address} (maxPacketSize: ${usbEndpointOut!!.maxPacketSize})")
            LogUtil.d(TAG, "  Interface: ${usbInterface?.id}")

            // 检查接口是否仍然被声明
            if (usbInterface != null) {
                val isInterfaceClaimed = try {
                    // 尝试重新声明接口（如果已声明会返回true）
                    usbDeviceConnection!!.claimInterface(usbInterface, true)
                } catch (e: Exception) {
                    LogUtil.w(TAG, "Failed to claim interface: ${e.message}")
                    false
                }

                if (!isInterfaceClaimed) {
                    LogUtil.e(TAG, "USB interface is not claimed")
                    return false
                }
            }

            // 执行bulk传输
            val result = usbDeviceConnection!!.bulkTransfer(
                usbEndpointOut, data, data.size, 5000 // 5秒超时
            )

            when {
                result == data.size -> {
                    LogUtil.d(TAG, "AOA data sent successfully: ${data.size} bytes")
                    true
                }

                result > 0 -> {
                    LogUtil.w(TAG, "AOA data partially sent: sent=$result, expected=${data.size}")
                    false
                }

                result == 0 -> {
                    LogUtil.e(TAG, "AOA data send timeout: no data sent")
                    false
                }

                result == -1 -> {
                    LogUtil.e(TAG, "AOA data send failed: USB transfer error")
                    LogUtil.e(TAG, "Possible causes:")
                    LogUtil.e(TAG, "  1. Device disconnected")
                    LogUtil.e(TAG, "  2. Interface not properly claimed")
                    LogUtil.e(TAG, "  3. Endpoint not available")
                    LogUtil.e(TAG, "  4. USB communication error")
                    false
                }

                else -> {
                    LogUtil.e(TAG, "AOA data send failed: unexpected result=$result")
                    false
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error sending data to AOA device: ${e.message}")
            LogUtil.e(TAG, "Exception type: ${e.javaClass.simpleName}")
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

            // 调用基类的清理逻辑
            super.release()

            LogUtil.d(TAG, "AOA Host resources released successfully")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error during AOA Host release: ${e.message}")
            e.printStackTrace()
        }

        LogUtil.d(TAG, "=== AOA Host Release Finished ===")
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