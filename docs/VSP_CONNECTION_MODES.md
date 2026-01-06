# VSP虚拟串口连接模式说明

## 概述

虚拟串口(VSP)支持多种连接模式，根据设备角色和连接方式的不同，可以分为服务端模式和客户端模式。

## 连接模式对比

| 模式 | 实现类 | 角色 | 连接方式 | 适用场景 |
|------|--------|------|----------|----------|
| 服务端模式 | VSPServiceKernelTemp | 被动等待连接 | SUNMI UART API | SUNMI设备作为服务端 |
| 服务端模式 | VspServiceKernel | 被动等待连接 | USB Serial库 | Android设备作为USB Host |
| 客户端模式 | VSPClientKernel | 主动发起连接 | USB Serial库 | Android设备连接外部串口设备 |

## 1. VSP服务端模式

### 1.1 VSPServiceKernelTemp (SUNMI设备服务端)

**特点**:
- 使用SUNMI设备的原生UART接口
- 设备作为服务端，等待外部设备连接
- 适用于SUNMI POS机等设备

**连接流程**:
```
1. SUNMI设备启动VSP服务
2. 配置UART参数（波特率、数据位等）
3. 等待外部设备（如收银机）通过串口连接
4. 建立双向通信
```

**使用示例**:
```kotlin
val vspServer = VSPServiceKernelTemp(appId, appSecret, context)
vspServer.connect("vsp://115200/8/n/1", connectionCallback)
```

### 1.2 VspServiceKernel (USB Host服务端)

**特点**:
- 使用usb-serial-for-android库
- Android设备作为USB Host
- 连接USB转串口设备

**连接流程**:
```
1. Android设备检测USB转串口设备
2. 请求USB权限
3. 打开USB串口设备
4. 配置串口参数
5. 建立通信
```

**使用示例**:
```kotlin
val vspServer = VspServiceKernel(appId, appSecret, context)
vspServer.connect("vsp://115200/8/n/1", connectionCallback)
```

## 2. VSP客户端模式

### 2.1 VSPClientKernel (主动连接客户端)

**特点**:
- 主动扫描和连接VSP设备
- 支持指定目标设备
- 适用于需要连接多个串口设备的场景

**连接流程**:
```
1. 扫描可用的USB串口设备
2. 选择目标设备（自动或手动指定）
3. 请求USB权限
4. 建立连接并配置参数
5. 开始数据通信
```

**使用示例**:
```kotlin
// 自动连接第一个可用设备
val vspClient = VSPClientKernel(appId, appSecret, context)
vspClient.connect("vsp://115200/8/n/1", connectionCallback)

// 连接指定设备
vspClient.connect("vsp://115200/8/n/1?device=FTDI", connectionCallback)
```

## 3. 协议格式详解

### 基本格式
```
vsp://baudRate/dataBits/parity/stopBits[?parameters]
```

### 参数说明
- **baudRate**: 波特率 (9600, 19200, 38400, 57600, 115200等)
- **dataBits**: 数据位 (7, 8)
- **parity**: 校验位 (n=none, e=even, o=odd, m=mark, s=space)
- **stopBits**: 停止位 (1, 2)
- **parameters**: 可选参数

### 可选参数
- **device**: 指定目标设备名称
- **timeout**: 连接超时时间
- **mode**: 连接模式 (client/server)

### 协议示例
```
vsp://115200/8/n/1                    # 基本配置
vsp://9600/8/e/2?device=FTDI          # 指定FTDI设备
vsp://38400/8/n/1?timeout=5000        # 设置超时时间
vsp://115200/8/n/1?mode=client        # 明确指定客户端模式
```

## 4. 设备类型支持

### 4.1 USB转串口芯片支持

| 芯片类型 | 厂商 | 特点 | 驱动支持 |
|----------|------|------|----------|
| FTDI | FTDI | 稳定性好，兼容性强 | ✅ 完全支持 |
| CH340/CH341 | WCH | 成本低，国产芯片 | ✅ 完全支持 |
| CP210x | Silicon Labs | 功耗低，集成度高 | ✅ 完全支持 |
| PL2303 | Prolific | 老牌芯片，兼容性好 | ✅ 完全支持 |

### 4.2 设备识别

**自动识别**:
```kotlin
val devices = vspClient.getAvailableDevices()
devices.forEach { device ->
    println("Device: ${device["deviceName"]}")
    println("VID/PID: ${device["vendorId"]}/${device["productId"]}")
}
```

**手动指定**:
```kotlin
// 通过设备名称
vspClient.connect("vsp://115200/8/n/1?device=FTDI")

// 通过产品名称
vspClient.connect("vsp://115200/8/n/1?device=USB-SERIAL")
```

## 5. 连接场景示例

### 5.1 POS机与收银系统通信

**场景**: SUNMI POS机作为服务端，收银系统作为客户端

```kotlin
// POS机端 (服务端)
val posServer = VSPServiceKernelTemp(appId, appSecret, context)
posServer.connect("vsp://115200/8/n/1", serverCallback)

// 收银系统端 (客户端)
val cashierClient = VSPClientKernel(appId, appSecret, context)
cashierClient.connect("vsp://115200/8/n/1?device=POS", clientCallback)
```

### 5.2 多设备管理

**场景**: 一个Android设备管理多个串口设备

```kotlin
// 连接打印机
val printerClient = VSPClientKernel(appId, appSecret, context)
printerClient.connect("vsp://9600/8/n/1?device=PRINTER")

// 连接扫码枪
val scannerClient = VSPClientKernel(appId, appSecret, context)
scannerClient.connect("vsp://115200/8/n/1?device=SCANNER")

// 连接称重设备
val scaleClient = VSPClientKernel(appId, appSecret, context)
scaleClient.connect("vsp://38400/8/e/1?device=SCALE")
```

### 5.3 调试和测试

**场景**: 使用VSP进行设备调试

```kotlin
// 扫描所有可用设备
val debugClient = VSPClientKernel(appId, appSecret, context)
val devices = debugClient.getAvailableDevices()

// 逐个测试连接
devices.forEach { device ->
    val deviceName = device["deviceName"]
    debugClient.connect("vsp://115200/8/n/1?device=$deviceName", testCallback)
}
```

## 6. 错误处理和故障排除

### 6.1 常见错误

| 错误类型 | 原因 | 解决方案 |
|----------|------|----------|
| 设备未找到 | USB设备未连接或不支持 | 检查设备连接和驱动支持 |
| 权限被拒绝 | 用户拒绝USB权限 | 重新请求权限或检查权限设置 |
| 连接超时 | 设备响应慢或参数不匹配 | 调整超时时间或检查串口参数 |
| 数据传输失败 | 串口参数不匹配 | 确认波特率、数据位等参数 |

### 6.2 调试技巧

**启用详细日志**:
```kotlin
// 在连接前启用调试日志
LogUtil.setLogLevel(LogUtil.DEBUG)
```

**测试连接参数**:
```kotlin
// 尝试不同的波特率
val baudRates = listOf(9600, 19200, 38400, 57600, 115200)
baudRates.forEach { rate ->
    vspClient.connect("vsp://$rate/8/n/1", testCallback)
}
```

**检查设备状态**:
```kotlin
// 定期检查连接状态
val config = vspClient.getCurrentConfig()
val isConnected = vspClient.isVspConnected()
```

## 7. 性能优化

### 7.1 连接优化
- 缓存设备列表，避免重复扫描
- 使用合适的超时时间
- 实现连接池管理多个设备

### 7.2 数据传输优化
- 使用合适的缓冲区大小
- 批量发送数据减少系统调用
- 实现数据压缩（如果需要）

### 7.3 资源管理
- 及时释放不使用的连接
- 正确处理设备断开事件
- 避免内存泄漏

## 8. 最佳实践

1. **权限管理**: 在应用启动时预先请求USB权限
2. **错误恢复**: 实现自动重连机制
3. **状态监控**: 定期检查连接状态
4. **参数配置**: 根据设备类型选择合适的串口参数
5. **日志记录**: 记录详细的连接和通信日志
6. **用户体验**: 提供清晰的连接状态提示

## 相关文件

- `VSPServiceKernelTemp.kt`: SUNMI UART服务端实现
- `VspServiceKernel.kt`: USB Serial服务端实现
- `VSPClientKernel.kt`: VSP客户端实现
- `VSP_CLIENT_USAGE_EXAMPLE.kt`: 客户端使用示例