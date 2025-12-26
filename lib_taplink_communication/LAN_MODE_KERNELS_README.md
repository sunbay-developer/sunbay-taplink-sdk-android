# LAN模式通信内核 (LAN Mode Communication Kernels)

本文档介绍了为`lib_taplink_communication`模块新增的LAN模式通信内核，包括RS232、USB、VSP三种通信方式的实现。

## 概述

在原有的WebSocket LAN通信基础上，新增了以下三种LAN模式通信内核：

1. **Rs232LanKernel** - RS232串口通信内核
2. **UsbLanKernel** - USB通信内核  
3. **VspLanKernel** - 虚拟串口(VSP)通信内核
4. **LanModeKernelManager** - LAN模式内核统一管理器

## 架构设计

### 继承关系
```
AsyncServiceKernel (抽象基类)
├── WebSocketClientKernel (现有)
├── Rs232LanKernel (新增)
├── UsbLanKernel (新增)
└── VspLanKernel (新增)
```

### 核心特性
- **统一接口**: 所有内核都实现`AsyncServiceKernel`接口
- **异步操作**: 基于协程的异步数据收发
- **状态管理**: 统一的连接状态管理和监控
- **错误处理**: 完善的错误处理和重连机制
- **协议解析**: 集成统一的协议管理系统

## 内核详细说明

### 1. Rs232LanKernel - RS232串口通信内核

#### 功能特性
- RS232串口设备连接和通信
- 可配置的波特率和串口参数
- 异步数据读写
- 设备权限检查和错误处理

#### 支持的协议格式
```kotlin
// RS232协议格式
"rs232://devicePath:baudRate"

// 示例
"rs232:///dev/ttyS0:115200"
"rs232:///dev/ttyUSB0:9600"
```

#### 使用示例
```kotlin
val rs232Kernel = Rs232LanKernel(appId, appSecretKey, context)

// 构建协议
val protocol = ProtocolManager.buildRs232Protocol("/dev/ttyS0", 115200)

// 连接
rs232Kernel.connect(protocol, connectionCallback)

// 发送数据
rs232Kernel.sendString("trace_id", "Hello RS232!")
```

### 2. UsbLanKernel - USB通信内核

#### 功能特性
- 支持USB Host和Accessory模式
- 自动设备检测和权限管理
- 设备信息查询和状态监控
- 多设备支持

#### 支持的协议格式
```kotlin
// USB Host模式
"usb://host/deviceName"
"usb://host/deviceId"
"usb://host"  // 使用第一个可用设备

// USB Accessory模式
"usb://accessory"
```

#### 使用示例
```kotlin
val usbKernel = UsbLanKernel(appId, appSecretKey, context)

// 检查可用设备
val devices = usbKernel.getConnectedDevices()

// 构建协议
val protocol = ProtocolManager.buildUsbHostProtocol("deviceName")

// 连接
usbKernel.connect(protocol, connectionCallback)
```

### 3. VspLanKernel - 虚拟串口通信内核

#### 功能特性
- 虚拟串口连接和配置
- 可配置的串口参数（波特率、数据位、校验位、停止位）
- 参数验证和配置管理
- 异步数据传输

#### 支持的协议格式
```kotlin
// VSP协议格式
"vsp://baudRate/dataBits/parity/stopBits"

// 示例
"vsp://115200/8/N/1"
"vsp://9600/8/E/1"
```

#### 使用示例
```kotlin
val vspKernel = VspLanKernel(appId, appSecretKey, context)

// 构建协议
val protocol = ProtocolManager.buildVspProtocol(115200, 8, "N", 1)

// 连接
vspKernel.connect(protocol, connectionCallback)

// 获取当前配置
val config = vspKernel.getCurrentConfig()
```

### 4. LanModeKernelManager - 统一管理器

#### 功能特性
- 统一管理所有LAN模式内核
- 根据协议自动选择合适的内核
- 内核间的无缝切换
- 资源管理和清理

#### 使用示例
```kotlin
val manager = LanModeKernelManager(appId, appSecretKey, context)

// 检查支持的协议类型
val supportedTypes = manager.getSupportedProtocolTypes()

// 连接（自动选择内核）
manager.connect(protocol, connectionCallback)

// 发送数据
manager.sendData("trace_id", data, callback)

// 获取当前内核信息
val kernelInfo = manager.getCurrentKernelInfo()
```

## 协议管理

### 协议构建方法
```kotlin
// RS232协议
ProtocolManager.buildRs232Protocol("/dev/ttyS0", 115200)

// USB Host协议
ProtocolManager.buildUsbHostProtocol("deviceName")
ProtocolManager.buildUsbAccessoryProtocol()

// VSP协议
ProtocolManager.buildVspProtocol(115200, 8, "N", 1)
```

### 协议解析
```kotlin
val parseResult = ProtocolManager.parseProtocol(protocol)
when (parseResult) {
    is ProtocolParseResult.SerialProtocol -> {
        // RS232协议
        val devicePath = parseResult.devicePath
        val baudRate = parseResult.baudRate
    }
    is ProtocolParseResult.UsbProtocol -> {
        // USB协议
        val mode = parseResult.mode
        val deviceName = parseResult.deviceName
    }
    is ProtocolParseResult.VspProtocol -> {
        // VSP协议
        val baudRate = parseResult.baudRate
        val dataBits = parseResult.dataBits
    }
}
```

## 使用场景

### 1. 设备间直连通信
```kotlin
// 通过RS232串口连接外部设备
val rs232Protocol = "rs232:///dev/ttyS0:115200"
manager.connect(rs232Protocol, callback)
```

### 2. USB设备通信
```kotlin
// 连接USB设备进行数据交换
val usbProtocol = "usb://host/myDevice"
manager.connect(usbProtocol, callback)
```

### 3. 虚拟串口通信
```kotlin
// 通过虚拟串口与应用程序通信
val vspProtocol = "vsp://115200/8/N/1"
manager.connect(vspProtocol, callback)
```

### 4. 智能连接选择
```kotlin
// 按优先级尝试多种连接方式
val strategies = listOf(
    "ws://192.168.1.100:8080",    // 优先WebSocket
    "usb://host",                 // 其次USB
    "vsp://115200/8/N/1",        // 然后VSP
    "rs232:///dev/ttyS0:115200"  // 最后RS232
)

for (protocol in strategies) {
    if (manager.isProtocolSupported(protocol)) {
        manager.connect(protocol, callback)
        if (manager.hasActiveConnection()) break
    }
}
```

## 错误处理

### 常见错误码
- `C33` - 连接失败
- `C35` - 连接丢失
- `C41` - 协议错误
- `SEND_FAILED` - 数据发送失败
- `NO_ACTIVE_KERNEL` - 没有活跃的内核

### 错误处理示例
```kotlin
kernel.connect(protocol, object : ConnectionCallback {
    override fun onDisconnected(code: String, message: String) {
        when (code) {
            "C33" -> {
                // 连接失败，可能需要检查设备或权限
                LogUtil.e(TAG, "Connection failed: $message")
            }
            "C35" -> {
                // 连接丢失，可能需要重连
                LogUtil.w(TAG, "Connection lost: $message")
            }
            "C41" -> {
                // 协议错误，检查协议格式
                LogUtil.e(TAG, "Protocol error: $message")
            }
        }
    }
})
```

## 注意事项

### 1. 权限要求
- **RS232**: 需要串口设备访问权限
- **USB**: 需要USB设备访问权限，可能需要用户授权
- **VSP**: 需要虚拟串口驱动支持

### 2. 设备依赖
- **RS232**: 需要物理串口设备或USB转串口适配器
- **USB**: 需要USB OTG支持和相应的USB设备
- **VSP**: 需要虚拟串口软件或驱动

### 3. 实现说明
当前实现提供了完整的框架和接口，但某些底层操作（如实际的串口读写、USB通信）需要集成相应的第三方库：

- **RS232/VSP**: 可集成`usb-serial-for-android`等串口库
- **USB**: 使用Android原生USB API
- **设备驱动**: 可能需要特定的设备驱动支持

## 扩展性

### 添加新的通信方式
1. 继承`AsyncServiceKernel`基类
2. 实现必要的抽象方法
3. 在`LanModeKernelManager`中注册新内核
4. 在`ProtocolManager`中添加协议支持

### 自定义协议
```kotlin
// 在ProtocolConstants中添加新协议前缀
const val CUSTOM_PROTOCOL_PREFIX = "custom://"

// 在ProtocolParser中添加解析逻辑
private fun parseCustomProtocol(protocol: String): ProtocolParseResult {
    // 自定义解析逻辑
}
```

## 测试和调试

### 日志输出
所有内核都使用统一的日志系统，可通过以下标签过滤日志：
- `Rs232LanKernel` - RS232相关日志
- `UsbLanKernel` - USB相关日志  
- `VspLanKernel` - VSP相关日志
- `LanModeKernelManager` - 管理器日志

### 调试建议
1. 检查设备连接状态
2. 验证协议格式正确性
3. 确认必要权限已获取
4. 查看详细错误日志
5. 测试设备兼容性

## 总结

新增的LAN模式通信内核为Taplink SDK提供了更丰富的设备连接选项，支持RS232、USB、VSP等多种通信方式。通过统一的接口设计和管理器模式，开发者可以轻松地在不同通信方式间切换，实现更灵活的设备通信方案。