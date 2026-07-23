# Taplink SDK Demo App

USB AOA (Android Open Accessory) 协议测试应用，包含 Host 和 Accessory 两种模式的测试 Demo。

## 功能说明

### 主界面 (MainActivity)
- 选择 USB Host 模式测试
- 选择 USB Accessory 模式测试

### USB Host 模式 (UsbHostDemoActivity)
作为 USB Host 端，主动连接 USB Accessory 设备。

**实现方式：** 使用 `TaplinkSDK` (TaplinkClient)

**功能：**
- 连接到 USB Accessory 设备
- 发送测试数据
- 接收数据
- 断开连接
- 实时日志显示

**使用场景：**
- Android 设备作为主机（Host）
- 连接到其他 Android 设备（Accessory 模式）
- 或连接到支持 AOA 协议的硬件设备

### USB Accessory 模式 (UsbAccessoryDemoActivity)
作为 USB Accessory 端，等待 USB Host 设备连接。

**实现方式：** 直接使用 `UsbStandardAccessoryKernel`

**功能：**
- 等待 Host 设备连接
- 发送测试数据
- 接收数据
- 断开连接
- 实时日志显示

**使用场景：**
- Android 设备作为配件（Accessory）
- 等待其他 Android 设备（Host 模式）连接
- 或等待支持 AOA 协议的主机设备连接

## 测试步骤

### 准备工作
1. 准备两台 Android 设备（或一台 Android 设备 + 一台支持 AOA 的主机）
2. 准备 USB OTG 线缆
3. 在两台设备上分别安装此 Demo 应用

### Host 端测试
1. 在设备 A 上启动应用
2. 选择 "USB Host 模式测试"
3. 连接 USB 线缆到 Accessory 设备
4. 点击 "连接" 按钮
5. 授权 USB 权限
6. 连接成功后可以发送测试数据

### Accessory 端测试
1. 在设备 B 上启动应用
2. 选择 "USB Accessory 模式测试"
3. 点击 "连接" 按钮（开始监听）
4. 连接 USB 线缆到 Host 设备
5. 授权 USB 权限
6. 连接成功后可以发送测试数据

## 配置说明

### Host 模式初始化（使用 TaplinkSDK）
```kotlin
// 创建 TaplinkConfig
val config = TaplinkConfig.create(
    context = this,
    appId = "demo_app_id",
    appSecretKey = "demo_secret_key"
)

// 创建 TaplinkClient
val taplinkClient = TaplinkClient(config)

// 设置连接监听器
taplinkClient.setConnectionListener(object : ConnectionListener {
    override fun onStatusChanged(status: ConnectionStatus) {
        // 处理连接状态变化
    }
    override fun onError(errorCode: String, errorMessage: String) {
        // 处理连接错误
    }
})

// 设置数据接收回调
taplinkClient.setDataReceiver { data ->
    // 处理接收到的数据
}
```

### Accessory 模式初始化
```kotlin
// 创建 PendingIntent
val pendingIntent = UsbStandardAccessoryKernel.createRequestPermissionPendingIntent(this)

// 创建 USB 标准信息
val usbStandardInfo = UsbStandardProtocol.UsbStandardInfo(
    manufacturer = "SUNMI",
    model = "TAPLINK_SDK",
    description = "Taplink SDK Demo",
    version = "1.0",
    uri = "https://www.sunmi.com",
    serial = "12345"
)

// 创建 Accessory Kernel
val accessoryKernel = UsbStandardAccessoryKernel(
    context = this,
    usbStandardInfo = usbStandardInfo,
    permissionAction = UsbStandardAccessoryKernel.ACTION_USB_STANDARD_ACCESSORY_KERNEL,
    permissionPendingIntent = pendingIntent
)

// 设置数据接收回调
accessoryKernel.setDataReceiver { data ->
    // 处理接收到的数据
}
```

### 连接和数据传输

#### Host 模式（使用 TaplinkSDK）
```kotlin
// 创建连接配置
val connectionConfig = ConnectionConfig(
    cableProtocol = CableProtocol.USB_AOA
)

// 开始连接
taplinkClient.connect(connectionConfig)

// 发送数据
taplinkClient.sendRawData(data) { success, errorCode, errorMessage ->
    if (success) {
        // 发送成功
    } else {
        // 发送失败
    }
}

// 断开连接
taplinkClient.disconnect()

// 释放资源
taplinkClient.release()
```

#### Accessory 模式（使用 Kernel）
```kotlin
// 创建协议解析结果
val parseResult = ProtocolParseResult.UsbProtocol()

// 创建连接回调
val connectionCallback = object : ConnectionCallback {
    override fun onConnected(extraInfoMap: Map<String, String?>?) {
        // 连接成功
    }
    override fun onWaitingConnect() {
        // 等待连接
    }
    override fun onDisconnected(code: String, msg: String) {
        // 连接断开
    }
}

// 开始连接
accessoryKernel.connect(parseResult, connectionCallback)

// 发送数据
accessoryKernel.sendData(data, callback)

// 断开连接
accessoryKernel.disconnect()
```

**注意：** 
- Host 和 Accessory 的 USB 标准信息必须匹配
- Host 端发送的 AOA 协议信息必须与 Accessory 端配置一致

### AndroidManifest 配置
- USB 权限：`android.permission.USB_PERMISSION`
- USB 特性：`android.hardware.usb.accessory`
- Accessory 模式需要响应 `USB_ACCESSORY_ATTACHED` Intent

### USB Accessory 过滤器
位置：`app/src/main/res/xml/accessory_filter.xml`

```xml
<usb-accessory
    manufacturer="Sunmi"
    model="TaplinkDemo"
    version="1.0" />
```

**注意：** Host 端发送的 AOA 协议信息必须与此配置匹配。

## 日志说明

Demo 应用会实时显示以下日志：
- 连接状态变化
- 权限请求和授权
- 数据发送和接收
- 错误信息
- 断开连接事件

日志格式：`[HH:mm:ss.SSS] 日志内容`

## 常见问题

### 1. 连接失败
- 检查 USB 线缆是否正常
- 检查是否授权了 USB 权限
- 检查 Accessory 过滤器配置是否匹配
- 查看日志中的错误码和错误信息

### 2. 权限被拒绝
- 重新连接并授权
- 检查系统设置中的 USB 权限

### 3. 设备未检测到
- 确认设备支持 USB OTG
- 尝试更换 USB 线缆
- 重启应用或设备

## 错误码参考

- E251: 未找到 USB 设备
- E252: USB 权限被拒绝
- E253: 设备在权限请求期间断开
- E213: 连接超时（Accessory 模式）
- E304: 数据发送失败

## 技术架构

### 依赖模块
- `lib_taplink_sdk`: SDK 模块（Host 模式使用）
- `lib_taplink_communication`: 通信层模块（Accessory 模式使用）
- `lib_log`: 日志模块

### 核心类

#### Host 模式（SDK 层）
- `TaplinkClient`: SDK 客户端
- `TaplinkConfig`: SDK 配置
- `ConnectionConfig`: 连接配置
- `ConnectionListener`: 连接状态监听器
- `CableProtocol`: 线缆协议枚举

#### Accessory 模式（Kernel 层）
- `UsbStandardAccessoryKernel`: USB Accessory 模式内核
- `UsbStandardProtocol`: USB 协议工具类
- `ConnectionCallback`: 连接状态回调接口
- `InnerCallback`: 数据发送回调接口

### 使用流程

#### Host 模式（SDK 层）
```kotlin
// 1. 创建 TaplinkClient
val config = TaplinkConfig.create(context, appId, appSecretKey)
val taplinkClient = TaplinkClient(config)

// 2. 设置监听器
taplinkClient.setConnectionListener(...)
taplinkClient.setDataReceiver { data -> ... }

// 3. 连接
val connectionConfig = ConnectionConfig(cableProtocol = CableProtocol.USB_AOA)
taplinkClient.connect(connectionConfig)

// 4. 发送数据
taplinkClient.sendRawData(data) { success, errorCode, errorMessage -> ... }

// 5. 断开连接
taplinkClient.disconnect()

// 6. 释放资源
taplinkClient.release()
```

#### Accessory 模式（Kernel 层）
```kotlin
// 1. 创建 Kernel 实例
val kernel = UsbStandardAccessoryKernel(...)

// 2. 设置数据接收回调
kernel.registerDataReceiver { data -> ... }

// 3. 创建协议解析结果和连接回调
val parseResult = ProtocolParseResult.UsbProtocol()
val connectionCallback = object : ConnectionCallback { ... }

// 4. 开始连接
kernel.connect(parseResult, connectionCallback)

// 5. 发送数据
kernel.sendData(data, callback)

// 6. 断开连接
kernel.disconnect()

// 7. 释放资源
kernel.release()
```

### 关键特性
- **Host 模式使用 SDK 层**：通过 TaplinkClient 进行连接和数据传输
- **Accessory 模式使用 Kernel 层**：直接使用底层 Kernel 进行测试
- **完整的生命周期管理**：Session、Token、Mutex 等机制
- **实时数据传输**：支持双向数据收发
- **详细的日志输出**：便于调试和问题定位

## 开发说明

### 添加新功能
1. 在对应的 Activity 中添加 UI 控件
2. 在布局文件中添加控件定义
3. 实现业务逻辑
4. 更新日志输出

### 调试建议
- 使用 `adb logcat` 查看详细日志
- 启用 USB 调试模式
- 使用两台设备进行端到端测试

## 版本信息

- 版本：1.0
- 最低 SDK：25 (Android 7.1)
- 目标 SDK：36 (Android 14)
- Kotlin 版本：1.9+

## 许可证

Copyright © 2025 Sunmi TaPro Team
