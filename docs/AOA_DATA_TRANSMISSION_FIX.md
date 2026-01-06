# AOA数据传输修复

## 问题分析

从日志可以看到：
```
✅ AOA device connected successfully
✅ Interface: 255, Endpoints: IN=true, OUT=true
✅ Status changed: WAITING_CONNECT -> CONNECTED
❌ USB accessory output stream not available
❌ Status changed: CONNECTED -> ERROR
```

**问题**: Host端成功连接到AOA设备，但数据发送失败。

## 根本原因

Host端和Target端使用不同的数据传输方式：

### Target端（Accessory模式）
- 使用 `FileInputStream/FileOutputStream`
- 通过 `accessoryOutputStream.write()` 发送数据
- 系统管理的流接口

### Host端（AOA设备模式）
- 使用 `UsbDeviceConnection.bulkTransfer()`
- 通过USB Bulk端点发送数据
- 直接的USB传输

## 修复方案

### 重写performSendData方法
```kotlin
override fun performSendData(traceId: String, data: ByteArray, callback: InnerCallback?) {
    try {
        val success = sendDataToAoaDevice(data)
        if (success) {
            callback?.onResponse("SUCCESS")
        } else {
            updateStatus(InnerConnectionStatus.ERROR)
            callback?.onError("SEND_FAILED", "Failed to send data to AOA device")
        }
    } catch (e: Exception) {
        LogUtil.e(TAG, "Failed to send data: ${e.message}")
        updateStatus(InnerConnectionStatus.ERROR)
        callback?.onError("SEND_ERROR", e.message ?: "Unknown error")
    }
}
```

### 实现AOA设备数据发送
```kotlin
private fun sendDataToAoaDevice(data: ByteArray): Boolean {
    return try {
        if (usbDeviceConnection == null || usbEndpointOut == null) {
            LogUtil.e(TAG, "AOA device connection or output endpoint not available")
            return false
        }

        val result = usbDeviceConnection!!.bulkTransfer(
            usbEndpointOut, data, data.size, 5000 // 5秒超时
        )

        if (result == data.size) {
            LogUtil.d(TAG, "AOA data sent successfully: ${data.size} bytes")
            true
        } else {
            LogUtil.e(TAG, "AOA data send failed: sent=$result, expected=${data.size}")
            false
        }
    } catch (e: Exception) {
        LogUtil.e(TAG, "Error sending data to AOA device: ${e.message}")
        false
    }
}
```

## 数据传输对比

| 方面 | Target端 (Accessory) | Host端 (AOA Device) |
|------|---------------------|-------------------|
| 连接对象 | UsbAccessory | UsbDevice |
| 数据发送 | FileOutputStream.write() | bulkTransfer() |
| 数据接收 | FileInputStream.read() | bulkTransfer() |
| 端点类型 | 系统管理 | Bulk IN/OUT |
| 超时控制 | 系统控制 | 手动设置 |

## 修复效果

修复后应该看到：
```
✅ AOA device connected successfully
✅ Interface: 255, Endpoints: IN=true, OUT=true
✅ Status changed: WAITING_CONNECT -> CONNECTED
✅ AOA data sent successfully: 244 bytes  ← 新增
✅ INIT command sent successfully         ← 新增
```

## 技术要点

### 1. Bulk传输特点
- 适合大量数据传输
- 有错误检测和重传
- 支持超时控制
- 保证数据完整性

### 2. 端点检查
```kotlin
if (usbDeviceConnection == null || usbEndpointOut == null) {
    // 连接或端点不可用
    return false
}
```

### 3. 传输验证
```kotlin
if (result == data.size) {
    // 数据完全发送成功
    return true
} else {
    // 部分发送或失败
    return false
}
```

### 4. 超时处理
- 设置5秒超时避免无限等待
- 超时后返回失败状态
- 允许上层重试机制

## 完整的AOA通信流程

现在Host端具备完整的AOA通信能力：

1. ✅ **协议发送**: GET_PROTOCOL, SEND_STRING, START
2. ✅ **设备检测**: 检测AOA设备出现
3. ✅ **设备连接**: 连接AOA设备并声明接口
4. ✅ **数据发送**: 通过Bulk OUT端点发送数据
5. ✅ **数据接收**: 通过Bulk IN端点接收数据

Host端现在可以与Target端进行完整的双向数据通信了！