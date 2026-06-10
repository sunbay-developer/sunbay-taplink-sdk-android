//package com.sunmi.tapro.taplink.communication.protocol
//
//import android.content.Context
//import com.sunmi.tapro.taplink.communication.interfaces.AsyncServiceKernel
//import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
//import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
//import com.sunmi.tapro.taplink.communication.enums.InnerErrorCode
//import com.sunmi.tapro.taplink.communication.util.LogUtil
//
///**
// * 十六进制帧协议使用示例
// * 
// * 展示如何在不同的通信模式中使用 HexFrameProtocol 和 HexFrameBuffer
// * 
// * @author TaPro Team
// * @since 2025-01-06
// */
//
//// ==================== 示例1: 在USB AOA中使用 ====================
//
///**
// * USB AOA 内核示例（使用十六进制帧协议）
// */
//class UsbAoaHexKernelExample(
//    appId: String,
//    appSecretKey: String,
//    private val context: Context
//) : AsyncServiceKernel(appId, appSecretKey) {
//
//    private val TAG = "UsbAoaHexKernel"
//    
//    // 十六进制帧缓冲区
//    private var hexFrameBuffer: HexFrameBuffer? = null
//    
//    // USB相关变量（示例）
//    // private var usbConnection: UsbDeviceConnection? = null
//    // private var outEndpoint: UsbEndpoint? = null
//    // private var inEndpoint: UsbEndpoint? = null
//
//    init {
//        // 初始化十六进制帧缓冲区
//        hexFrameBuffer = HexFrameBuffer(
//            scope = scope,
//            onFrameReceived = { frame ->
//                // 接收到完整帧，发送给数据接收器
//                LogUtil.d(TAG, "Received complete frame: ${String(frame)}")
//                dataReceiver?.invoke(frame)
//            },
//            maxBufferSize = 128 * 1024,  // 128KB
//            messageTimeoutMs = 5000L      // 5秒超时
//        )
//    }
//
//    override fun getServiceType(): String = "USB AOA (Hex)"
//
//    override fun getExpectedProtocolType(): String = "usb aoa hex protocol"
//
//    override fun isValidProtocolType(parseResult: com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult): Boolean {
//        // 验证协议类型
//        return true
//    }
//
//    override fun performConnect(
//        parseResult: com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult,
//        connectionCallback: ConnectionCallback
//    ) {
//        LogUtil.d(TAG, "Connecting to USB AOA device with hex protocol")
//        
//        scope.launch {
//            try {
//                // 清空缓冲区
//                hexFrameBuffer?.clear()
//                
//                // 执行USB AOA连接逻辑
//                // val success = connectToUsbAoaDevice()
//                val success = true  // 示例
//                
//                if (success) {
//                    notifyConnectionSuccess(mapOf("mode" to "hex"))
//                } else {
//                    notifyConnectionError("Failed to connect", InnerErrorCode.C25)
//                }
//            } catch (e: Exception) {
//                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.C25)
//            }
//        }
//    }
//
//    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
//        try {
//            LogUtil.d(TAG, "Sending data: ${String(data)} (${data.size} bytes)")
//            
//            // 使用HexFrameProtocol编码数据
//            val frameData = HexFrameProtocol.encode(data)
//            
//            LogUtil.d(TAG, "Encoded frame: ${String(frameData)} (${frameData.size} bytes)")
//            
//            // 通过USB AOA发送
//            // usbConnection?.bulkTransfer(outEndpoint, frameData, frameData.size, 2000)
//            
//            LogUtil.d(TAG, "Data sent successfully")
//            
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to send data: ${e.message}")
//            callback?.onError(InnerErrorCode.C62.code, e.message ?: "Send failed")
//        }
//    }
//
//    override fun performDisconnect() {
//        LogUtil.d(TAG, "Disconnecting USB AOA device")
//        
//        // 停止缓冲区
//        hexFrameBuffer?.stop()
//        
//        // 关闭USB连接
//        // usbConnection?.close()
//    }
//
//    /**
//     * USB数据接收处理（在USB读取线程中调用）
//     */
//    private fun onUsbDataReceived(data: ByteArray) {
//        LogUtil.d(TAG, "USB data received: ${String(data)} (${data.size} bytes)")
//        
//        // 添加到缓冲区，自动提取完整帧
//        hexFrameBuffer?.addData(data)
//    }
//
//    override fun getTag(): String = TAG
//}
//
//// ==================== 示例2: 在WebSocket中使用 ====================
//
///**
// * WebSocket 内核示例（使用十六进制帧协议）
// */
//class WebSocketHexKernelExample(
//    appId: String,
//    appSecretKey: String
//) : AsyncServiceKernel(appId, appSecretKey) {
//
//    private val TAG = "WebSocketHexKernel"
//    
//    // 十六进制帧缓冲区
//    private var hexFrameBuffer: HexFrameBuffer? = null
//    
//    // WebSocket相关变量（示例）
//    // private var webSocket: WebSocket? = null
//
//    init {
//        hexFrameBuffer = HexFrameBuffer(
//            scope = scope,
//            onFrameReceived = { frame ->
//                LogUtil.d(TAG, "Received complete frame: ${String(frame)}")
//                dataReceiver?.invoke(frame)
//            }
//        )
//    }
//
//    override fun getServiceType(): String = "WebSocket (Hex)"
//
//    override fun getExpectedProtocolType(): String = "websocket hex protocol"
//
//    override fun isValidProtocolType(parseResult: com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult): Boolean {
//        return true
//    }
//
//    override fun performConnect(
//        parseResult: com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult,
//        connectionCallback: ConnectionCallback
//    ) {
//        LogUtil.d(TAG, "Connecting to WebSocket with hex protocol")
//        
//        scope.launch {
//            try {
//                hexFrameBuffer?.clear()
//                
//                // 执行WebSocket连接逻辑
//                // webSocket = createWebSocket()
//                
//                notifyConnectionSuccess(mapOf("mode" to "hex"))
//            } catch (e: Exception) {
//                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.C25)
//            }
//        }
//    }
//
//    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
//        try {
//            // 使用HexFrameProtocol编码
//            val frameData = HexFrameProtocol.encode(data)
//            
//            // 通过WebSocket发送（作为文本）
//            // webSocket?.send(String(frameData))
//            
//            LogUtil.d(TAG, "Data sent via WebSocket")
//            
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to send data: ${e.message}")
//            callback?.onError(InnerErrorCode.C62.code, e.message ?: "Send failed")
//        }
//    }
//
//    override fun performDisconnect() {
//        LogUtil.d(TAG, "Disconnecting WebSocket")
//        
//        hexFrameBuffer?.stop()
//        // webSocket?.close(1000, "Normal closure")
//    }
//
//    /**
//     * WebSocket消息监听（示例）
//     */
//    private fun onWebSocketMessage(text: String) {
//        LogUtil.d(TAG, "WebSocket message received: $text")
//        
//        // 添加到缓冲区
//        hexFrameBuffer?.addData(text.toByteArray())
//    }
//
//    override fun getTag(): String = TAG
//}
//
//// ==================== 示例3: 在蓝牙中使用 ====================
//
///**
// * 蓝牙内核示例（使用十六进制帧协议）
// */
//class BluetoothHexKernelExample(
//    appId: String,
//    appSecretKey: String,
//    private val context: Context
//) : AsyncServiceKernel(appId, appSecretKey) {
//
//    private val TAG = "BluetoothHexKernel"
//    
//    // 十六进制帧缓冲区
//    private var hexFrameBuffer: HexFrameBuffer? = null
//    
//    // 蓝牙相关变量（示例）
//    // private var bluetoothSocket: BluetoothSocket? = null
//    // private var receiveThread: Thread? = null
//
//    init {
//        hexFrameBuffer = HexFrameBuffer(
//            scope = scope,
//            onFrameReceived = { frame ->
//                LogUtil.d(TAG, "Received complete frame: ${String(frame)}")
//                dataReceiver?.invoke(frame)
//            }
//        )
//    }
//
//    override fun getServiceType(): String = "Bluetooth (Hex)"
//
//    override fun getExpectedProtocolType(): String = "bluetooth hex protocol"
//
//    override fun isValidProtocolType(parseResult: com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult): Boolean {
//        return true
//    }
//
//    override fun performConnect(
//        parseResult: com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult,
//        connectionCallback: ConnectionCallback
//    ) {
//        LogUtil.d(TAG, "Connecting to Bluetooth device with hex protocol")
//        
//        scope.launch {
//            try {
//                hexFrameBuffer?.clear()
//                
//                // 执行蓝牙连接逻辑
//                // bluetoothSocket = createBluetoothSocket()
//                // startReceiveThread()
//                
//                notifyConnectionSuccess(mapOf("mode" to "hex"))
//            } catch (e: Exception) {
//                notifyConnectionError(e.message ?: "Unknown error", InnerErrorCode.C25)
//            }
//        }
//    }
//
//    override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
//        try {
//            // 使用HexFrameProtocol编码
//            val frameData = HexFrameProtocol.encode(data)
//            
//            // 通过蓝牙发送
//            // bluetoothSocket?.outputStream?.write(frameData)
//            
//            LogUtil.d(TAG, "Data sent via Bluetooth")
//            
//        } catch (e: Exception) {
//            LogUtil.e(TAG, "Failed to send data: ${e.message}")
//            callback?.onError(InnerErrorCode.C62.code, e.message ?: "Send failed")
//        }
//    }
//
//    override fun performDisconnect() {
//        LogUtil.d(TAG, "Disconnecting Bluetooth")
//        
//        hexFrameBuffer?.stop()
//        
//        // 停止接收线程
//        // receiveThread?.interrupt()
//        
//        // 关闭蓝牙连接
//        // bluetoothSocket?.close()
//    }
//
//    /**
//     * 蓝牙数据接收线程（示例）
//     */
//    private fun startReceiveThread() {
//        // receiveThread = Thread {
//        //     val buffer = ByteArray(1024)
//        //     while (!Thread.currentThread().isInterrupted) {
//        //         try {
//        //             val bytes = bluetoothSocket?.inputStream?.read(buffer) ?: break
//        //             if (bytes > 0) {
//        //                 val data = buffer.copyOf(bytes)
//        //                 hexFrameBuffer?.addData(data)
//        //             }
//        //         } catch (e: Exception) {
//        //             break
//        //         }
//        //     }
//        // }
//        // receiveThread?.start()
//    }
//
//    override fun getTag(): String = TAG
//}
//
//// ==================== 示例4: 简单的点对点使用 ====================
//
///**
// * 简单的十六进制帧协议使用示例
// * 不依赖AsyncServiceKernel，可用于任何场景
// */
//class SimpleHexFrameExample {
//    
//    private val TAG = "SimpleHexFrame"
//    
//    /**
//     * 发送数据示例
//     */
//    fun sendData(data: String) {
//        // 1. 编码数据
//        val originalData = data.toByteArray()
//        val encodedFrame = HexFrameProtocol.encode(originalData)
//        
//        LogUtil.d(TAG, "Original: $data")
//        LogUtil.d(TAG, "Encoded: ${String(encodedFrame)}")
//        
//        // 2. 发送编码后的数据
//        // sendToDevice(encodedFrame)
//    }
//    
//    /**
//     * 接收数据示例
//     */
//    fun receiveData(receivedData: String) {
//        // 1. 解码数据
//        val result = HexFrameProtocol.decode(receivedData)
//        
//        // 2. 处理所有完整的帧
//        for (frame in result.frames) {
//            val decodedString = String(frame)
//            LogUtil.d(TAG, "Decoded: $decodedString")
//            
//            // 处理解码后的数据
//            processDecodedData(frame)
//        }
//    }
//    
//    private fun processDecodedData(data: ByteArray) {
//        // 处理数据
//        LogUtil.d(TAG, "Processing: ${String(data)}")
//    }
//}
