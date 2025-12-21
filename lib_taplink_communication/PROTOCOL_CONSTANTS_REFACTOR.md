# 协议常量重构说明

## 问题背景

之前 SDK 层和 Communication 层使用的协议格式不一致，导致硬编码和维护困难：

### 旧的实现问题

**SDK 层 (TaplinkApiImpl.buildProtocolString):**
```kotlin
// 硬编码协议字符串
ConnectionMode.USB_AOA -> "usb_aoa://"
ConnectionMode.USB_VSP -> "usb_vsp://"
ConnectionMode.RS232 -> "rs232://"
ConnectionMode.LAN -> "wss://"
ConnectionMode.APP_TO_APP -> "local://"
```

**Communication 层 (ProtocolParser.parse):**
```kotlin
// 不一致的协议格式
protocol.startsWith("usb://") -> parseUsbProtocol(protocol)
protocol.startsWith("vsp://") -> parseVspProtocol(protocol)
protocol.startsWith("serial://") -> parseSerialProtocol(protocol)
protocol.startsWith("wss://") -> parseLanProtocol(protocol)
protocol.startsWith("local://") -> parseLocalProtocol(protocol)
```

**问题：**
- ❌ 协议格式不一致（`usb_aoa://` vs `usb://`）
- ❌ 硬编码字符串分散在多个文件
- ❌ 难以维护和扩展
- ❌ 容易出现拼写错误

---

## 解决方案

### 1. 创建统一的协议常量类

**文件：** `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/protocol/ProtocolConstants.kt`

```kotlin
object ProtocolConstants {
    // 协议前缀
    const val LOCAL_PROTOCOL_PREFIX = "local://"
    const val LAN_PROTOCOL_PREFIX = "wss://"
    const val LAN_WS_PROTOCOL_PREFIX = "ws://"
    const val USB_PROTOCOL_PREFIX = "usb://"
    const val VSP_PROTOCOL_PREFIX = "vsp://"
    const val SERIAL_PROTOCOL_PREFIX = "serial://"
    const val RS232_PROTOCOL_PREFIX = "rs232://"
    const val AUTO_PROTOCOL_PREFIX = "auto://"
    
    // 协议构建器
    fun buildLocalProtocol(packageName: String, action: String? = null): String
    fun buildLanProtocol(host: String, port: Int, secure: Boolean = true): String
    fun buildUsbHostProtocol(deviceName: String? = null, deviceId: String? = null): String
    fun buildUsbAccessoryProtocol(): String
    fun buildVspProtocol(baudRate: Int = 115200, dataBits: Int = 8, parity: String = "N", stopBits: Int = 1): String
    fun buildSerialProtocol(devicePath: String, baudRate: Int): String
    fun buildRs232Protocol(devicePath: String, baudRate: Int): String
    
    // 协议检测
    fun detectProtocolType(protocol: String): ProtocolType
}
```

### 2. 更新 ProtocolParser 使用常量

**修改前：**
```kotlin
return when {
    protocol.startsWith("local://") -> parseLocalProtocol(protocol)
    protocol.startsWith("wss://") -> parseLanProtocol(protocol)
    protocol.startsWith("vsp://") -> parseVspProtocol(protocol)
    protocol.startsWith("usb://") -> parseUsbProtocol(protocol)
    protocol.startsWith("serial://") -> parseSerialProtocol(protocol)
    else -> ProtocolParseResult.Error("Unsupported protocol format: $protocol")
}
```

**修改后：**
```kotlin
return when {
    protocol.startsWith(ProtocolConstants.LOCAL_PROTOCOL_PREFIX) -> parseLocalProtocol(protocol)
    protocol.startsWith(ProtocolConstants.LAN_PROTOCOL_PREFIX) -> parseLanProtocol(protocol)
    protocol.startsWith(ProtocolConstants.LAN_WS_PROTOCOL_PREFIX) -> parseLanProtocol(protocol)
    protocol.startsWith(ProtocolConstants.VSP_PROTOCOL_PREFIX) -> parseVspProtocol(protocol)
    protocol.startsWith(ProtocolConstants.USB_PROTOCOL_PREFIX) -> parseUsbProtocol(protocol)
    protocol.startsWith(ProtocolConstants.SERIAL_PROTOCOL_PREFIX) -> parseSerialProtocol(protocol)
    protocol.startsWith(ProtocolConstants.RS232_PROTOCOL_PREFIX) -> parseRs232Protocol(protocol)
    protocol.startsWith(ProtocolConstants.AUTO_PROTOCOL_PREFIX) -> parseAutoProtocol(protocol)
    else -> ProtocolParseResult.Error("Unsupported protocol format: $protocol")
}
```

### 3. 创建 SDK 层的协议构建器

**文件：** `lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/util/ProtocolBuilder.kt`

```kotlin
object ProtocolBuilder {
    fun buildProtocol(
        connectionConfig: ConnectionConfig?,
        taplinkConfig: TaplinkConfig
    ): Pair<String, ConnectionMode?> {
        // 统一的协议构建逻辑
        // 使用 ProtocolConstants 中的方法
    }
}
```

### 4. 更新 TaplinkApiImpl

**修改前：**
```kotlin
private fun buildProtocolString(config: ConnectionConfig?): String {
    // 大量硬编码的协议字符串
    return when (initConnectionMode) {
        ConnectionMode.USB_AOA -> "usb_aoa://"  // ❌ 硬编码
        ConnectionMode.USB_VSP -> "usb_vsp://"  // ❌ 硬编码
        // ...
    }
}
```

**修改后：**
```kotlin
// 使用 ProtocolBuilder
val (protocol, connectionMode) = ProtocolBuilder.buildProtocol(config, this.config!!)
currentConnectionMode = connectionMode
```

---

## 统一后的协议格式

### 支持的协议格式

| 协议类型 | 格式 | 示例 |
|---------|------|------|
| 本地服务 | `local://packageName/action` | `local://com.sunmi.tapro/ACTION_TRANSACTION` |
| 局域网 WSS | `wss://host:port` | `wss://192.168.1.100:8443` |
| 局域网 WS | `ws://host:port` | `ws://192.168.1.100:8080` |
| USB Host | `usb://host/device` | `usb://host/deviceName` |
| USB Accessory | `usb://accessory` | `usb://accessory` |
| 虚拟串口 | `vsp://baudRate/dataBits/parity/stopBits` | `vsp://115200/8/N/1` |
| 串口 | `serial://devicePath:baudRate` | `serial:///dev/ttyUSB0:115200` |
| RS232 | `rs232://devicePath:baudRate` | `rs232:///dev/ttyS0:9600` |
| 自动检测 | `auto://` | `auto://` |

### 协议映射关系

| ConnectionMode | 协议格式 | 说明 |
|---------------|---------|------|
| `APP_TO_APP` | `local://` | 应用间通信 |
| `LAN` | `wss://host:port` | 局域网连接 |
| `USB_AOA` | `usb://accessory` | USB Accessory 模式 |
| `USB_VSP` | `vsp://115200/8/N/1` | 虚拟串口 |
| `RS232` | `rs232:///dev/ttyS0:9600` | RS232 串口 |

---

## 使用示例

### 1. 构建协议字符串

```kotlin
// 本地服务
val localProtocol = ProtocolConstants.buildLocalProtocol(
    packageName = "com.sunmi.tapro",
    action = "ACTION_TRANSACTION"
)
// 结果: "local://com.sunmi.tapro/ACTION_TRANSACTION"

// 局域网
val lanProtocol = ProtocolConstants.buildLanProtocol(
    host = "192.168.1.100",
    port = 8443,
    secure = true
)
// 结果: "wss://192.168.1.100:8443"

// USB Host
val usbProtocol = ProtocolConstants.buildUsbHostProtocol(
    deviceName = "TapLink Device"
)
// 结果: "usb://host/TapLink Device"

// 虚拟串口
val vspProtocol = ProtocolConstants.buildVspProtocol(
    baudRate = 115200,
    dataBits = 8,
    parity = "N",
    stopBits = 1
)
// 结果: "vsp://115200/8/N/1"
```

### 2. 检测协议类型

```kotlin
val protocolType = ProtocolConstants.detectProtocolType("wss://192.168.1.100:8443")
// 结果: ProtocolType.LAN_WSS

when (protocolType) {
    ProtocolType.LOCAL -> // 本地服务
    ProtocolType.LAN_WSS -> // 局域网 WSS
    ProtocolType.USB -> // USB
    ProtocolType.VSP -> // 虚拟串口
    // ...
}
```

### 3. SDK 层使用

```kotlin
// 在 TaplinkApiImpl 中
val (protocol, connectionMode) = ProtocolBuilder.buildProtocol(
    connectionConfig = config,
    taplinkConfig = this.config!!
)

// 自动根据配置生成正确的协议字符串
LogUtil.d(TAG, "Protocol: $protocol, Mode: $connectionMode")
```

---

## 优势

### 1. 统一性
- ✅ SDK 层和 Communication 层使用相同的协议格式
- ✅ 所有协议字符串都在一个地方定义
- ✅ 避免不一致的问题

### 2. 可维护性
- ✅ 修改协议格式只需要在一个地方
- ✅ 新增协议类型很容易
- ✅ 减少硬编码

### 3. 可扩展性
- ✅ 提供协议构建器方法
- ✅ 支持协议检测
- ✅ 易于添加新的协议类型

### 4. 类型安全
- ✅ 使用枚举定义协议类型
- ✅ 编译时检查
- ✅ 避免拼写错误

---

## 迁移指南

### 对于现有代码

1. **不需要修改调用代码**
   - `TaplinkSDK.connect()` 的 API 保持不变
   - `ConnectionConfig` 的使用方式不变

2. **协议字符串会自动统一**
   - SDK 层会生成正确的协议格式
   - Communication 层会正确解析

3. **如果有自定义协议字符串**
   - 使用 `ProtocolConstants` 中的构建方法
   - 或者直接使用新的协议格式

### 示例迁移

**修改前：**
```kotlin
// 如果有硬编码的协议字符串
val protocol = "usb_aoa://"  // ❌ 旧格式
```

**修改后：**
```kotlin
// 使用协议构建器
val protocol = ProtocolConstants.buildUsbAccessoryProtocol()  // ✅ 新格式
// 或者直接使用新格式
val protocol = "usb://accessory"  // ✅ 新格式
```

---

## 相关文件

### 新增文件
- `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/protocol/ProtocolConstants.kt`
- `lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/util/ProtocolBuilder.kt`

### 修改文件
- `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/util/ProtocolParser.kt`
- `lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/TaplinkApiImpl.kt`

---

## 测试

### 协议构建测试

```kotlin
@Test
fun testProtocolBuilding() {
    // 测试本地协议
    val localProtocol = ProtocolConstants.buildLocalProtocol("com.test", "ACTION_TEST")
    assertEquals("local://com.test/ACTION_TEST", localProtocol)
    
    // 测试局域网协议
    val lanProtocol = ProtocolConstants.buildLanProtocol("192.168.1.1", 8443)
    assertEquals("wss://192.168.1.1:8443", lanProtocol)
    
    // 测试 USB 协议
    val usbProtocol = ProtocolConstants.buildUsbAccessoryProtocol()
    assertEquals("usb://accessory", usbProtocol)
}
```

### 协议解析测试

```kotlin
@Test
fun testProtocolParsing() {
    // 测试解析本地协议
    val result = ProtocolParser.parse("local://com.test/ACTION_TEST")
    assertTrue(result is ProtocolParseResult.LocalProtocol)
    
    // 测试解析局域网协议
    val lanResult = ProtocolParser.parse("wss://192.168.1.1:8443")
    assertTrue(lanResult is ProtocolParseResult.LanProtocol)
}
```

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team