package com.sunmi.tapro.taplink.communication.examples

import android.content.Context
import com.sunmi.tapro.taplink.communication.enums.InnerConnectionStatus
import com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback
import com.sunmi.tapro.taplink.communication.interfaces.InnerCallback
import com.sunmi.tapro.taplink.communication.lan.*
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * LAN模式Kernel使用示例
 * 
 * 展示如何使用新创建的LAN模式下的各种通信内核：
 * 1. RS232 LAN Kernel - 串口通信
 * 2. USB LAN Kernel - USB通信
 * 3. VSP LAN Kernel - 虚拟串口通信
 * 4. LAN Mode Kernel Manager - 统一管理器
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
class LanModeKernelsUsageExample(private val context: Context) {

    companion object {
        private const val TAG = "LanModeKernelsExample"
        private const val APP_ID = "your_app_id"
        private const val APP_SECRET_KEY = "your_app_secret_key"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ==================== 1. RS232 LAN Kernel 使用示例 ====================

    /**
     * RS232 LAN模式连接示例
     */
    fun demonstrateRs232LanConnection() {
        LogUtil.d(TAG, "=== RS232 LAN Kernel Demo ===")
        
        val rs232Kernel = Rs232LanKernel(APP_ID, APP_SECRET_KEY, context)
        
        // 构建RS232协议
        val rs232Protocol = ProtocolManager.buildRs232Protocol("/dev/ttyS0", 115200)
        LogUtil.d(TAG, "RS232 Protocol: $rs232Protocol")
        
        // 设置数据接收器
        rs232Kernel.registerDataReceiver { data ->
            data?.let {
                val receivedText = String(it, Charsets.UTF_8)
                LogUtil.d(TAG, "RS232 Data received: $receivedText")
            }
        }
        
        // 设置状态监听器
        rs232Kernel.setStatusChangeListener { status ->
            LogUtil.d(TAG, "RS232 Status changed: $status")
        }
        
        // 连接
        rs232Kernel.connect(rs232Protocol, object : ConnectionCallback {
            override fun onWaitingConnect() {
                LogUtil.d(TAG, "RS232 Waiting for connection...")
            }
            
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                LogUtil.d(TAG, "RS232 Connected successfully!")
                extraInfoMap?.let { info ->
                    LogUtil.d(TAG, "RS232 Connection info: $info")
                }
                
                // 连接成功后发送测试数据
                sendRs232TestData(rs232Kernel)
            }
            
            override fun onDisconnected(code: String, message: String) {
                LogUtil.d(TAG, "RS232 Disconnected: $code - $message")
            }
            
            override fun onServiceAddressChanged(
                nameService: String,
                newHost: String,
                newPort: Int,
                oldHost: String,
                oldPort: Int
            ): Boolean {
                // RS232不涉及地址变更
                return false
            }
        })
    }
    
    private fun sendRs232TestData(kernel: Rs232LanKernel) {
        scope.launch {
            // 发送字符串数据
            kernel.sendString("test_trace_001", "Hello RS232!", object : InnerCallback {
                override fun onResponse(response: String) {
                    LogUtil.d(TAG, "RS232 Send success: $response")
                }
                
                override fun onError(code: String, message: String) {
                    LogUtil.e(TAG, "RS232 Send failed: $code - $message")
                }
            })
            
            // 发送二进制数据
            val binaryData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
            kernel.sendData("test_trace_002", binaryData, object : InnerCallback {
                override fun onResponse(response: String) {
                    LogUtil.d(TAG, "RS232 Binary send success: $response")
                }
                
                override fun onError(code: String, message: String) {
                    LogUtil.e(TAG, "RS232 Binary send failed: $code - $message")
                }
            })
        }
    }

    // ==================== 2. USB LAN Kernel 使用示例 ====================

    /**
     * USB LAN模式连接示例
     */
    fun demonstrateUsbLanConnection() {
        LogUtil.d(TAG, "=== USB LAN Kernel Demo ===")
        
        val usbKernel = UsbLanKernel(APP_ID, APP_SECRET_KEY, context)
        
        // 检查可用设备
        val availableDevices = usbKernel.getConnectedDevices()
        LogUtil.d(TAG, "Available USB devices: ${availableDevices.size}")
        
        availableDevices.forEach { (name, device) ->
            val deviceInfo = usbKernel.getDeviceInfo(device)
            LogUtil.d(TAG, "USB Device: $name, Info: $deviceInfo")
        }
        
        // 构建USB Host协议（使用第一个可用设备）
        val usbProtocol = if (availableDevices.isNotEmpty()) {
            val firstDevice = availableDevices.values.first()
            ProtocolManager.buildUsbHostProtocol(firstDevice.deviceName)
        } else {
            ProtocolManager.buildUsbHostProtocol() // 使用默认设备
        }
        
        LogUtil.d(TAG, "USB Protocol: $usbProtocol")
        
        // 设置数据接收器
        usbKernel.registerDataReceiver { data ->
            data?.let {
                LogUtil.d(TAG, "USB Data received: ${it.size} bytes")
            }
        }
        
        // 连接
        usbKernel.connect(usbProtocol, object : ConnectionCallback {
            override fun onWaitingConnect() {
                LogUtil.d(TAG, "USB Waiting for connection...")
            }
            
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                LogUtil.d(TAG, "USB Connected successfully!")
                extraInfoMap?.let { info ->
                    LogUtil.d(TAG, "USB Connection info: $info")
                }
                
                // 连接成功后发送测试数据
                sendUsbTestData(usbKernel)
            }
            
            override fun onDisconnected(code: String, message: String) {
                LogUtil.d(TAG, "USB Disconnected: $code - $message")
            }
            
            override fun onServiceAddressChanged(
                nameService: String,
                newHost: String,
                newPort: Int,
                oldHost: String,
                oldPort: Int
            ): Boolean {
                // USB不涉及地址变更
                return false
            }
        })
    }
    
    private fun sendUsbTestData(kernel: UsbLanKernel) {
        scope.launch {
            val testData = "USB Test Data".toByteArray(Charsets.UTF_8)
            kernel.sendData("usb_trace_001", testData, object : InnerCallback {
                override fun onResponse(response: String) {
                    LogUtil.d(TAG, "USB Send success: $response")
                }
                
                override fun onError(code: String, message: String) {
                    LogUtil.e(TAG, "USB Send failed: $code - $message")
                }
            })
        }
    }

    // ==================== 3. VSP LAN Kernel 使用示例 ====================

    /**
     * VSP LAN模式连接示例
     */
    fun demonstrateVspLanConnection() {
        LogUtil.d(TAG, "=== VSP LAN Kernel Demo ===")
        
        val vspKernel = VspLanKernel(APP_ID, APP_SECRET_KEY, context)
        
        // 构建VSP协议（115200, 8, N, 1）
        val vspProtocol = ProtocolManager.buildVspProtocol(115200, 8, "N", 1)
        LogUtil.d(TAG, "VSP Protocol: $vspProtocol")
        
        // 设置数据接收器
        vspKernel.registerDataReceiver { data ->
            data?.let {
                val receivedText = String(it, Charsets.UTF_8)
                LogUtil.d(TAG, "VSP Data received: $receivedText")
            }
        }
        
        // 连接
        vspKernel.connect(vspProtocol, object : ConnectionCallback {
            override fun onWaitingConnect() {
                LogUtil.d(TAG, "VSP Waiting for connection...")
            }
            
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                LogUtil.d(TAG, "VSP Connected successfully!")
                extraInfoMap?.let { info ->
                    LogUtil.d(TAG, "VSP Connection info: $info")
                }
                
                // 显示当前配置
                val currentConfig = vspKernel.getCurrentConfig()
                LogUtil.d(TAG, "VSP Current config: $currentConfig")
                
                // 连接成功后发送测试数据
                sendVspTestData(vspKernel)
            }
            
            override fun onDisconnected(code: String, message: String) {
                LogUtil.d(TAG, "VSP Disconnected: $code - $message")
            }
            
            override fun onServiceAddressChanged(
                nameService: String,
                newHost: String,
                newPort: Int,
                oldHost: String,
                oldPort: Int
            ): Boolean {
                // VSP不涉及地址变更
                return false
            }
        })
    }
    
    private fun sendVspTestData(kernel: VspLanKernel) {
        scope.launch {
            // 发送字符串数据
            kernel.sendString("vsp_trace_001", "Hello VSP!", object : InnerCallback {
                override fun onResponse(response: String) {
                    LogUtil.d(TAG, "VSP Send success: $response")
                }
                
                override fun onError(code: String, message: String) {
                    LogUtil.e(TAG, "VSP Send failed: $code - $message")
                }
            })
        }
    }

    // ==================== 4. LAN Mode Kernel Manager 使用示例 ====================

    /**
     * LAN模式内核管理器使用示例
     */
    fun demonstrateLanModeKernelManager() {
        LogUtil.d(TAG, "=== LAN Mode Kernel Manager Demo ===")
        
        val kernelManager = LanModeKernelManager(APP_ID, APP_SECRET_KEY, context)
        
        // 检查支持的协议类型
        val supportedTypes = kernelManager.getSupportedProtocolTypes()
        LogUtil.d(TAG, "Supported protocol types: $supportedTypes")
        
        // 设置全局数据接收器
        kernelManager.registerDataReceiver { data ->
            data?.let {
                LogUtil.d(TAG, "Manager received data: ${it.size} bytes")
            }
        }
        
        // 设置状态监听器
        kernelManager.setStatusChangeListener { status ->
            LogUtil.d(TAG, "Manager status changed: $status")
            
            if (status == InnerConnectionStatus.CONNECTED) {
                val kernelInfo = kernelManager.getCurrentKernelInfo()
                LogUtil.d(TAG, "Current kernel info: $kernelInfo")
            }
        }
        
        // 演示多种协议连接
        demonstrateMultipleProtocolConnections(kernelManager)
    }
    
    private fun demonstrateMultipleProtocolConnections(manager: LanModeKernelManager) {
        scope.launch {
            val protocols = listOf(
                ProtocolManager.buildLanProtocol("192.168.1.100", 8080, false), // WebSocket
                ProtocolManager.buildRs232Protocol("/dev/ttyS0", 115200),        // RS232
                ProtocolManager.buildUsbHostProtocol(),                          // USB Host
                ProtocolManager.buildVspProtocol(115200, 8, "N", 1)             // VSP
            )
            
            protocols.forEach { protocol ->
                LogUtil.d(TAG, "Testing protocol: $protocol")
                
                // 检查协议是否支持
                if (manager.isProtocolSupported(protocol)) {
                    LogUtil.d(TAG, "Protocol supported: $protocol")
                    
                    // 连接测试
                    manager.connect(protocol, object : ConnectionCallback {
                        override fun onWaitingConnect() {
                            LogUtil.d(TAG, "Manager waiting for connection: $protocol")
                        }
                        
                        override fun onConnected(extraInfoMap: Map<String, String?>?) {
                            LogUtil.d(TAG, "Manager connected: $protocol")
                            
                            // 发送测试数据
                            val testData = "Test from manager".toByteArray(Charsets.UTF_8)
                            manager.sendData("manager_trace", testData, object : InnerCallback {
                                override fun onResponse(response: String) {
                                    LogUtil.d(TAG, "Manager send success: $response")
                                }
                                
                                override fun onError(code: String, message: String) {
                                    LogUtil.e(TAG, "Manager send failed: $code - $message")
                                }
                            })
                            
                            // 短暂延迟后断开，测试下一个协议
                            scope.launch {
                                kotlinx.coroutines.delay(2000)
                                manager.disconnect()
                            }
                        }
                        
                        override fun onDisconnected(code: String, message: String) {
                            LogUtil.d(TAG, "Manager disconnected: $protocol - $code - $message")
                        }
                        
                        override fun onServiceAddressChanged(
                            nameService: String,
                            newHost: String,
                            newPort: Int,
                            oldHost: String,
                            oldPort: Int
                        ): Boolean {
                            return false
                        }
                    })
                    
                    // 等待连接完成
                    kotlinx.coroutines.delay(3000)
                } else {
                    LogUtil.w(TAG, "Protocol not supported: $protocol")
                }
            }
            
            // 清理资源
            manager.release()
        }
    }

    // ==================== 5. 综合使用示例 ====================

    /**
     * 综合使用示例：根据设备环境自动选择最佳连接方式
     */
    fun demonstrateSmartConnection() {
        LogUtil.d(TAG, "=== Smart Connection Demo ===")
        
        val manager = LanModeKernelManager(APP_ID, APP_SECRET_KEY, context)
        
        scope.launch {
            // 按优先级尝试不同的连接方式
            val connectionStrategies = listOf(
                ConnectionStrategy("WebSocket", ProtocolManager.buildLanProtocol("192.168.1.100", 8080)),
                ConnectionStrategy("USB", ProtocolManager.buildUsbHostProtocol()),
                ConnectionStrategy("VSP", ProtocolManager.buildVspProtocol()),
                ConnectionStrategy("RS232", ProtocolManager.buildRs232Protocol("/dev/ttyS0", 115200))
            )
            
            var connected = false
            
            for (strategy in connectionStrategies) {
                if (connected) break
                
                LogUtil.d(TAG, "Trying connection strategy: ${strategy.name}")
                
                if (manager.isProtocolSupported(strategy.protocol)) {
                    try {
                        manager.connect(strategy.protocol, object : ConnectionCallback {
                            override fun onWaitingConnect() {
                                LogUtil.d(TAG, "Smart connection waiting: ${strategy.name}")
                            }
                            
                            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                                LogUtil.d(TAG, "Smart connection success: ${strategy.name}")
                                connected = true
                                
                                // 连接成功，开始正常通信
                                startNormalCommunication(manager)
                            }
                            
                            override fun onDisconnected(code: String, message: String) {
                                LogUtil.d(TAG, "Smart connection failed: ${strategy.name} - $code")
                                connected = false
                            }
                            
                            override fun onServiceAddressChanged(
                                nameService: String,
                                newHost: String,
                                newPort: Int,
                                oldHost: String,
                                oldPort: Int
                            ): Boolean {
                                return false
                            }
                        })
                        
                        // 等待连接结果
                        kotlinx.coroutines.delay(3000)
                        
                        if (manager.hasActiveConnection()) {
                            LogUtil.d(TAG, "Connection established with: ${strategy.name}")
                            break
                        } else {
                            LogUtil.d(TAG, "Connection failed with: ${strategy.name}, trying next...")
                            manager.disconnect()
                        }
                        
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Error with strategy ${strategy.name}: ${e.message}")
                    }
                } else {
                    LogUtil.d(TAG, "Strategy not supported: ${strategy.name}")
                }
            }
            
            if (!connected) {
                LogUtil.e(TAG, "All connection strategies failed")
            }
        }
    }
    
    private fun startNormalCommunication(manager: LanModeKernelManager) {
        LogUtil.d(TAG, "Starting normal communication...")
        
        // 设置数据接收处理
        manager.registerDataReceiver { data ->
            data?.let {
                // 处理接收到的数据
                processReceivedData(it)
            }
        }
        
        // 定期发送心跳或数据
        scope.launch {
            repeat(5) { index ->
                val heartbeat = "Heartbeat #$index".toByteArray(Charsets.UTF_8)
                manager.sendData("heartbeat_$index", heartbeat, object : InnerCallback {
                    override fun onResponse(response: String) {
                        LogUtil.d(TAG, "Heartbeat sent successfully: #$index")
                    }
                    
                    override fun onError(code: String, message: String) {
                        LogUtil.e(TAG, "Heartbeat failed: #$index - $code")
                    }
                })
                
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    
    private fun processReceivedData(data: ByteArray) {
        try {
            val text = String(data, Charsets.UTF_8)
            LogUtil.d(TAG, "Processing received data: $text")
            
            // 根据数据内容进行相应处理
            when {
                text.contains("ping") -> {
                    LogUtil.d(TAG, "Received ping, should send pong")
                }
                text.contains("command") -> {
                    LogUtil.d(TAG, "Received command, processing...")
                }
                else -> {
                    LogUtil.d(TAG, "Received general data: ${data.size} bytes")
                }
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error processing received data: ${e.message}")
        }
    }
    
    // ==================== 数据类 ====================
    
    data class ConnectionStrategy(
        val name: String,
        val protocol: String
    )
    
    // ==================== 公共方法 ====================
    
    /**
     * 运行所有示例
     */
    fun runAllExamples() {
        LogUtil.d(TAG, "Running all LAN Mode Kernel examples...")
        
        // 依次运行各个示例（实际使用时可能需要根据设备环境选择性运行）
        demonstrateRs232LanConnection()
        
        scope.launch {
            kotlinx.coroutines.delay(5000)
            demonstrateUsbLanConnection()
            
            kotlinx.coroutines.delay(5000)
            demonstrateVspLanConnection()
            
            kotlinx.coroutines.delay(5000)
            demonstrateLanModeKernelManager()
            
            kotlinx.coroutines.delay(10000)
            demonstrateSmartConnection()
        }
    }
}