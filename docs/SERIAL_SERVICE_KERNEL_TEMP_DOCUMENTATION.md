# SerialServiceKernelTemp 文档

## 概述

SerialServiceKernelTemp 是基于 RS232Kernel 和 RS232NormalAdapter 功能，继承自 AsyncServiceKernel 的串口服务内核实现。它提供了完整的USB转串口设备支持，包括自动设备检测、权限管理和异步数据通信。

## 设计目标

1. **继承统一架构**：继承自AsyncServiceKernel，与TaplinkSDK架构完全兼容
2. **功能完整性**：保留RS232Kernel和RS232NormalAdapter的所有核心功能
3. **现代化实现**：使用Kotlin协程和现代Android开发模式
4. **易于使用**：提供简洁的API和完善的错误处理

## 核心特性

### 1. 设备支持
- **USB转串口芯片**：PL2303、CH340、FTDI、CP210x等
- **自动检测**：自动扫描和识别兼容设备
- **多端口支持**：支持多端口USB转串口设备
- **自定义设备**：支持SUNMI等特殊设备

### 2. 连接管理
- **权限处理**：自动USB权限请求和管理
- **连接重试**：智能连接重试机制
- **状态监控**：完整的连接状态管理
- **错误恢复**：自动错误检测和恢复

### 3. 数据通信
- **异步收发**：基于协程的异步数据处理
- **缓冲管理**：智能数据缓冲和流控制
- **错误处理**：完善的通信错误处理
- **超时控制**：可配置的发送超时

### 4. 配置灵活性
- **串口参数**：支持各种波特率、数据位、校验位、停止位
- **设备选择**：支持指定目标设备名称
- **协议解析**：完整的协议字符串解析

## 架构设计

### 类继承关系
```
AsyncServiceKernel (抽象基类)
    ↓
SerialServiceKernelTemp (具体实现)
```

### 核心组件
```
SerialServiceKernelTemp
├── USB管理器 (UsbManager)
├── 串口驱动 (UsbSerialDriver)
├── IO管理器 (SerialInputOutputManager)
├── 权限管理 (Permission Management)
└── 协程管理 (Coroutine Scope)
```

## API 参考

### 构造函数
```kotlin
SerialServiceKernelTemp(
    appId: String,           // 应用ID
    appSecretKey: String,    // 应用密钥
    context: Context         // Android上下文
)
```

### 主要方法

#### 连接管理
```kotlin
// 连接到串口设备
fun connect(protocol: String, connectionCallback: ConnectionCallback)

// 断开连接
fun disconnect()

// 获取连接状态
fun getConnectionStatus(): InnerConnectionStatus
```

#### 数据通信
```kotlin
// 发送数据
fun sendData(traceId: String, data: ByteArray, callback: InnerCallback?)

// 注册数据接收器
fun registerDataReceiver(receiver: (ByteArray?) -> Unit)
```

#### 设备管理
```kotlin
// 获取可用设备列表
fun getAvailableDevices(): List<Map<String, String>>

// 获取当前配置
fun getCurrentConfig(): Map<String, Any>

// 检查连接状态
fun isSerialConnected(): Boolean
```

#### 静态工具方法
```kotlin
// 检查USB Host支持
fun isUsbHostSupported(context: Context): Boolean

// 获取所有USB设备
fun getAllUsbDevices(context: Context): List<UsbDevice>
```

## 协议格式

### 基本格式
```
serial://baudRate/dataBits/parity/stopBits[?device=deviceName]
```

### 参数说明
- **baudRate**: 波特率 (9600, 19200, 38400, 57600, 115200等)
- **dataBits**: 数据位 (7, 8)
- **parity**: 校验位 (none, even, odd, mark, space)
- **stopBits**: 停止位 (1, 2)
- **device**: 可选，目标设备名称

### 协议示例
```kotlin
// 标准配置
"serial://115200/8/none/1"

// 指定设备
"serial://115200/8/none/1?device=PL2303"

// 偶校验
"serial://38400/8/even/1"

// 7位数据，奇校验
"serial://19200/7/odd/1"

// 2个停止位
"serial://57600/8/none/2"
```

## 使用示例

### 基本使用
```kotlin
// 1. 创建内核实例
val serialKernel = SerialServiceKernelTemp(
    appId = "your_app_id",
    appSecretKey = "your_secret",
    context = applicationContext
)

// 2. 注册数据接收器
serialKernel.registerDataReceiver { data ->
    if (data != null) {
        val message = String(data)
        Log.d("Serial", "Received: $message")
    }
}

// 3. 连接设备
serialKernel.connect("serial://115200/8/none/1", object : ConnectionCallback {
    override fun onConnected(extraInfoMap: Map<String, String?>?) {
        Log.d("Serial", "Connected successfully")
        
        // 发送测试数据
        val testData = "Hello Device!".toByteArray()
        serialKernel.sendData("test", testData) { response ->
            Log.d("Serial", "Data sent: $response")
        }
    }
    
    override fun onWaitingConnect() {
        Log.d("Serial", "Waiting for connection...")
    }
    
    override fun onDisconnected(code: String, msg: String) {
        Log.e("Serial", "Connection failed: $code - $msg")
    }
})
```

### 设备检测
```kotlin
// 检查系统支持
if (SerialServiceKernelTemp.isUsbHostSupported(context)) {
    // 获取可用设备
    val devices = serialKernel.getAvailableDevices()
    
    devices.forEach { device ->
        Log.d("Serial", "Found device: ${device["deviceName"]}")
        Log.d("Serial", "  Manufacturer: ${device["manufacturerName"]}")
        Log.d("Serial", "  Driver: ${device["driverClass"]}")
        Log.d("Serial", "  Ports: ${device["portCount"]}")
    }
}
```

### 状态监控
```kotlin
serialKernel.setStatusChangeListener { status ->
    when (status) {
        InnerConnectionStatus.DISCONNECTED -> {
            Log.d("Serial", "Device disconnected")
        }
        InnerConnectionStatus.CONNECTING -> {
            Log.d("Serial", "Connecting...")
        }
        InnerConnectionStatus.CONNECTED -> {
            Log.d("Serial", "Device connected")
            
            // 获取连接信息
            val config = serialKernel.getCurrentConfig()
            Log.d("Serial", "Config: $config")
        }
        InnerConnectionStatus.ERROR -> {
            Log.e("Serial", "Connection error")
        }
    }
}
```

## 权限管理

### USB权限
SerialServiceKernelTemp 自动处理USB设备权限：

1. **自动检测**：检查设备是否已有权限
2. **权限请求**：自动弹出权限请求对话框
3. **超时处理**：5秒权限请求超时
4. **自动授权**：尝试系统级自动授权（需要系统权限）

### 权限流程
```
设备检测 → 权限检查 → 权限请求 → 用户授权 → 建立连接
     ↓           ↓           ↓           ↓           ↓
   找到设备   → 无权限    → 弹出对话框  → 用户同意   → 连接成功
```

## 错误处理

### 常见错误码
- **NOT_CONNECTED**: 设备未连接
- **SEND_FAILED**: 数据发送失败
- **SEND_ERROR**: 发送过程中出现异常
- **PERMISSION_DENIED**: USB权限被拒绝
- **DEVICE_NOT_FOUND**: 未找到兼容设备
- **DRIVER_NOT_FOUND**: 未找到设备驱动
- **CONNECTION_TIMEOUT**: 连接超时

### 错误处理策略
```kotlin
serialKernel.sendData("test", data, object : InnerCallback {
    override fun onResponse(responseData: String) {
        // 发送成功
    }
    
    override fun onError(code: String, msg: String) {
        when (code) {
            "NOT_CONNECTED" -> {
                // 重新连接
                reconnectDevice()
            }
            "SEND_FAILED" -> {
                // 重试发送
                retrySendData()
            }
            "PERMISSION_DENIED" -> {
                // 提示用户授权
                showPermissionDialog()
            }
            else -> {
                // 其他错误处理
                handleGenericError(code, msg)
            }
        }
    }
})
```

## 性能优化

### 1. 连接优化
- **设备缓存**：缓存已检测的设备信息
- **快速重连**：保存连接参数，支持快速重连
- **并发检测**：并行检测多个设备

### 2. 数据传输优化
- **缓冲管理**：使用16KB接收缓冲区
- **异步处理**：所有IO操作都在后台线程
- **流控制**：智能流控制避免数据丢失

### 3. 内存优化
- **及时释放**：连接断开时及时释放资源
- **弱引用**：避免内存泄漏
- **对象池**：重用数据缓冲区

## 调试和日志

### 日志级别
```kotlin
// 启用详细日志
LogUtil.setLogLevel(LogLevel.DEBUG)

// 关键操作日志
LogUtil.d(TAG, "Device detection started")
LogUtil.d(TAG, "USB permission granted")
LogUtil.d(TAG, "Serial connection established")
LogUtil.d(TAG, "Data sent: ${data.size} bytes")
LogUtil.d(TAG, "Data received: ${data.size} bytes")
```

### 调试信息
```kotlin
// 获取调试信息
val debugInfo = mapOf(
    "connectionStatus" to serialKernel.getConnectionStatus(),
    "currentConfig" to serialKernel.getCurrentConfig(),
    "availableDevices" to serialKernel.getAvailableDevices(),
    "usbHostSupported" to SerialServiceKernelTemp.isUsbHostSupported(context)
)

Log.d("Debug", "Serial Debug Info: $debugInfo")
```

## 最佳实践

### 1. 初始化
```kotlin
// 在Application或Activity的onCreate中初始化
class MainActivity : AppCompatActivity() {
    private lateinit var serialKernel: SerialServiceKernelTemp
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查系统支持
        if (!SerialServiceKernelTemp.isUsbHostSupported(this)) {
            showError("Device does not support USB Host mode")
            return
        }
        
        // 初始化内核
        serialKernel = SerialServiceKernelTemp(
            appId = BuildConfig.APP_ID,
            appSecretKey = BuildConfig.APP_SECRET,
            context = applicationContext
        )
        
        setupSerialKernel()
    }
}
```

### 2. 生命周期管理
```kotlin
override fun onResume() {
    super.onResume()
    // 检查连接状态，必要时重连
    if (serialKernel.getConnectionStatus() == InnerConnectionStatus.DISCONNECTED) {
        connectToDevice()
    }
}

override fun onPause() {
    super.onPause()
    // 可选：暂停时断开连接以节省资源
    // serialKernel.disconnect()
}

override fun onDestroy() {
    super.onDestroy()
    // 确保断开连接和清理资源
    serialKernel.disconnect()
}
```

### 3. 错误恢复
```kotlin
private fun setupErrorRecovery() {
    serialKernel.setStatusChangeListener { status ->
        when (status) {
            InnerConnectionStatus.ERROR -> {
                // 延迟重连
                Handler(Looper.getMainLooper()).postDelayed({
                    if (serialKernel.getConnectionStatus() == InnerConnectionStatus.ERROR) {
                        reconnectWithBackoff()
                    }
                }, 3000)
            }
        }
    }
}

private fun reconnectWithBackoff() {
    // 实现指数退避重连策略
    val maxRetries = 5
    var retryCount = 0
    
    fun attemptReconnect() {
        if (retryCount < maxRetries) {
            val delay = (1000 * Math.pow(2.0, retryCount.toDouble())).toLong()
            Handler(Looper.getMainLooper()).postDelayed({
                connectToDevice()
                retryCount++
            }, delay)
        }
    }
    
    attemptReconnect()
}
```

## 与原始实现的对比

### RS232Kernel vs SerialServiceKernelTemp

| 特性 | RS232Kernel | SerialServiceKernelTemp |
|------|-------------|-------------------------|
| **架构** | 单例模式 | AsyncServiceKernel继承 |
| **语言** | Java | Kotlin |
| **并发** | 传统线程 | Kotlin协程 |
| **错误处理** | 回调接口 | 统一错误码 |
| **状态管理** | 外部状态机 | 内置状态管理 |
| **权限管理** | 手动处理 | 自动化处理 |
| **生命周期** | 手动管理 | 自动管理 |

### RS232NormalAdapter vs SerialServiceKernelTemp

| 特性 | RS232NormalAdapter | SerialServiceKernelTemp |
|------|-------------------|-------------------------|
| **设备检测** | 硬编码逻辑 | 通用检测算法 |
| **权限处理** | 广播接收器 | 集成权限管理 |
| **数据处理** | SerialInputOutputManager | 协程 + SerialInputOutputManager |
| **配置管理** | Bundle参数 | 协议字符串解析 |
| **错误恢复** | 基础重连 | 智能重连策略 |

## 迁移指南

### 从RS232Kernel迁移

1. **替换初始化代码**：
```kotlin
// 原来
val rs232Kernel = RS232Kernel.getInstance()
rs232Kernel.init(bundle)

// 现在
val serialKernel = SerialServiceKernelTemp(appId, appSecret, context)
serialKernel.connect(protocol, callback)
```

2. **更新数据发送**：
```kotlin
// 原来
rs232Kernel.send(bytes)

// 现在
serialKernel.sendData(traceId, bytes, callback)
```

3. **更新数据接收**：
```kotlin
// 原来 (通过DispenseEngine)
DispenseEngine.getInstance().onReceive(bytes)

// 现在
serialKernel.registerDataReceiver { bytes ->
    // 处理接收数据
}
```

### 配置迁移

```kotlin
// 原来的Bundle配置
val bundle = Bundle().apply {
    putInt("BAUD_RATE", 115200)
    putInt("DATA_BITS", 8)
    putInt("PARITY", 0) // NONE
    putInt("STOP_BITS", 1)
}

// 现在的协议字符串
val protocol = "serial://115200/8/none/1"
```

## 故障排除

### 常见问题

1. **设备未检测到**
   - 检查USB Host模式支持
   - 确认设备驱动兼容性
   - 检查USB连接

2. **权限请求失败**
   - 确认应用有USB权限声明
   - 检查系统权限设置
   - 尝试手动授权

3. **连接建立失败**
   - 检查串口参数配置
   - 确认设备未被其他应用占用
   - 检查硬件连接

4. **数据发送失败**
   - 检查连接状态
   - 确认数据格式正确
   - 检查发送超时设置

### 调试步骤

1. **启用详细日志**
2. **检查设备列表**
3. **验证权限状态**
4. **测试基本连接**
5. **验证数据传输**

## 版本历史

- **v1.0.0** (2025-01-02): 初始版本，基于RS232Kernel和RS232NormalAdapter实现

## 相关文档

- [AsyncServiceKernel API文档](AsyncServiceKernel.md)
- [TaplinkSDK集成指南](TaplinkSDK_Integration.md)
- [串口通信最佳实践](Serial_Communication_Best_Practices.md)