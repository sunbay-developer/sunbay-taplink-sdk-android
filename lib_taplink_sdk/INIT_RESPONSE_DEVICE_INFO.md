# INIT 响应设备信息获取优化

## 修改说明

### 问题

之前的实现从连接时的 `extraInfoMap` 获取设备信息：

```kotlin
// 旧的实现
connectedDeviceId = extraInfoMap?.getOrDefault("deviceId", "unknown")
taproVersion = extraInfoMap?.getOrDefault("taproVersion", "unknown")
```

### 解决方案

现在从 INIT 命令响应的 `bizData` 中获取设备信息：

```kotlin
// 新的实现
sendInitCommand { success, errorCode, errorMsg, deviceId, taproVersion ->
    if (success) {
        connectedDeviceId = deviceId ?: "unknown"
        this.taproVersion = taproVersion ?: "unknown"
        // ...
    }
}
```

## 修改内容

### 1. 直接解析 bizData JSON

不创建额外的数据类，直接从 `bizData` 的 JSON 对象中获取字段：

```kotlin
if (basicResponse.bizData != null) {
    val bizDataJson = gson.fromJson(basicResponse.bizData, JsonObject::class.java)
    code = bizDataJson.get("code")?.asString ?: "ERROR"
    message = bizDataJson.get("message")?.asString
    deviceId = bizDataJson.get("deviceId")?.asString
    taproVersion = bizDataJson.get("taproVersion")?.asString
}
```

### 2. 修改 sendInitCommand 方法

**方法签名变更：**

```kotlin
// 之前
private fun sendInitCommand(onComplete: (Boolean, String?, String?) -> Unit)

// 现在
private fun sendInitCommand(onComplete: (Boolean, String?, String?, String?, String?) -> Unit)
```

**响应解析变更：**

```kotlin
// 之前：使用 PaymentResult 解析
val paymentResult = gson.fromJson(basicResponse.bizData, PaymentResult::class.java)

// 现在：直接解析 JSON 对象
val bizDataJson = gson.fromJson(basicResponse.bizData, JsonObject::class.java)
deviceId = bizDataJson.get("deviceId")?.asString
taproVersion = bizDataJson.get("taproVersion")?.asString
```

### 3. 修改连接成功处理

```kotlin
sendInitCommand { success, errorCode, errorMsg, deviceId, taproVersion ->
    if (success) {
        // 从 INIT 响应中获取设备信息
        connectedDeviceId = deviceId ?: "unknown"
        this.taproVersion = taproVersion ?: "unknown"
        
        LogUtil.d(TAG, "Device info from INIT response: deviceId=$connectedDeviceId, taproVersion=${this.taproVersion}")
        
        // 通知监听器连接成功
        listener.onConnected(connectedDeviceId ?: "unknown", this.taproVersion ?: "unknown")
    }
}
```

## 优势

### 1. 数据来源更可靠

- ✅ **INIT 响应** - 来自服务端的正式响应，数据更准确
- ❌ **extraInfoMap** - 连接时的临时信息，可能不完整

### 2. 数据更丰富

`bizData` 可以包含更多设备信息：

- `deviceId` - 设备 ID
- `taproVersion` - TaPro 版本
- `deviceModel` - 设备型号
- `deviceSn` - 设备序列号
- `appVersion` - 应用版本
- `terminalId` - 终端 ID
- `merchantId` - 商户 ID

需要时可以轻松添加更多字段的解析。

### 3. 时机更合适

- INIT 命令完成后才获取设备信息
- 确保设备已经初始化完成
- 避免连接时信息不完整的问题

## INIT 响应格式

### 预期的 bizData 格式

```json
{
  "traceId": "xxx",
  "event": {
    "eventCode": "COMPLETED",
    "eventMsg": "Success"
  },
  "bizData": {
    "code": "0",
    "message": "Success",
    "deviceId": "DEVICE_12345",
    "taproVersion": "1.2.3",
    "deviceModel": "T2",
    "deviceSn": "SN123456789",
    "appVersion": "2.1.0",
    "terminalId": "TERM_001",
    "merchantId": "MERCHANT_123"
  }
}
```

### 服务端需要返回的字段

服务端在处理 INIT 命令时，需要在 `bizData` 中返回：

```kotlin
// 服务端示例
val initResponseData = mapOf(
    "code" to "0",
    "message" to "Success",
    "deviceId" to getDeviceId(),
    "taproVersion" to getTaproVersion(),
    "deviceModel" to getDeviceModel(),
    "deviceSn" to getDeviceSn(),
    "appVersion" to getAppVersion(),
    "terminalId" to getTerminalId(),
    "merchantId" to getMerchantId()
)

val bizData = gson.toJson(initResponseData)
```

## 兼容性

### 向后兼容

如果服务端暂时无法返回设备信息，客户端会：

1. 使用默认值 "unknown"
2. 记录日志便于调试
3. 不影响正常连接流程

### 错误处理

```kotlin
var deviceId: String? = null
var taproVersion: String? = null

if (basicResponse.bizData != null) {
    try {
        val bizDataJson = gson.fromJson(basicResponse.bizData, JsonObject::class.java)
        deviceId = bizDataJson.get("deviceId")?.asString
        taproVersion = bizDataJson.get("taproVersion")?.asString
    } catch (e: Exception) {
        LogUtil.w(TAG, "Failed to parse bizData as JSON: ${e.message}")
        // deviceId 和 taproVersion 保持为 null，后续使用默认值
    }
}

// 使用时提供默认值
connectedDeviceId = deviceId ?: "unknown"
this.taproVersion = taproVersion ?: "unknown"
```

## 测试验证

### 1. 检查日志

连接成功后查看日志：

```
D/TaplinkApiImpl: Device info from INIT response: deviceId=DEVICE_12345, taproVersion=1.2.3
```

### 2. 验证回调

```kotlin
TaplinkSDK.connect(config, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        Log.d(TAG, "Connected to device: $deviceId, version: $taproVersion")
        // 应该显示从 INIT 响应获取的信息
    }
})
```

### 3. 调试 INIT 响应

```kotlin
override fun onResponse(responseData: String) {
    LogUtil.d(TAG, "INIT command response received: $responseData")
    // 检查 bizData 中是否包含设备信息
}
```

## 迁移指南

### 对于客户端开发者

无需修改代码，设备信息获取方式的变更对外部 API 透明。

### 对于服务端开发者

需要在 INIT 命令的响应中添加设备信息：

```kotlin
// 处理 INIT 命令
fun handleInitCommand(request: InitRequest): InitResponse {
    // 执行初始化逻辑
    
    // 构建响应
    val initResult = InitResult(
        code = "0",
        message = "Success",
        deviceId = getCurrentDeviceId(),
        taproVersion = getCurrentTaproVersion()
        // 其他设备信息...
    )
    
    return InitResponse(
        event = PaymentEvent.Completed("Success"),
        bizData = gson.toJson(initResult)
    )
}
```

## 相关文件

- `lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/TaplinkApiImpl.kt` - 修改

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-19  
**作者**: TaPro Team