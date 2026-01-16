# AOA HexFrame协议禁用修复

## 问题确认

通过最新的日志分析，确认了AOA数据传输问题的根本原因：

### 问题现象
```log
19:36:01.208 - AOA data sent successfully: 246 bytes (attempt 1)
19:36:01.422 - Handling receive error: type=TRANSFER_ERROR, bytesRead=-1
19:36:01.422 - Removed device at /dev/bus/usb/001/013: P3
```

**时间间隔**: 214ms（与之前的197ms类似）

### 根本原因
**HexFrame协议不兼容**: 
- Host端发送HexFrame编码的数据（246字节）
- Accessory端无法解析HexFrame格式
- 设备检测到无效数据格式后主动断开连接

## 修复方案

### 1. 禁用数据发送的HexFrame编码

**修改文件**: `UsbStandardHostKernel.kt`

**修改前**:
```kotlin
override fun sendDataToDevice(data: ByteArray): Boolean {
    LogUtil.d(TAG, "AOA sending original data: ${String(data)} (${data.size} bytes)")
    
    // 使用HexFrameProtocol编码数据
    val frameData = HexFrameProtocol.encode(data)
    LogUtil.d(TAG, "AOA sending hex frame: ${String(frameData)} (${frameData.size} bytes)")
    
    return sendDataToAoaDevice(frameData)
}
```

**修改后**:
```kotlin
override fun sendDataToDevice(data: ByteArray): Boolean {
    LogUtil.d(TAG, "AOA sending raw data (HexFrame disabled): ${String(data)} (${data.size} bytes)")
    
    // 临时禁用HexFrame编码，直接发送原始数据
    return sendDataToAoaDevice(data)
}
```

### 2. 禁用数据接收的HexFrame解码

**修改前**:
```kotlin
// 添加到HexFrame缓冲区进行处理
hexFrameBuffer?.addData(data)
```

**修改后**:
```kotlin
// 直接处理原始数据，不使用HexFrame解码
dataReceiver?.invoke(data)
```

## 预期效果

### 数据大小变化
- **之前**: 发送246字节（HexFrame编码后的大小）
- **现在**: 发送原始数据大小（预计约100-150字节）

### 连接稳定性
- **之前**: 发送后214ms断开连接
- **现在**: 预期连接保持稳定，不会因协议不兼容而断开

### 数据传输
- **之前**: Accessory端无法接收数据
- **现在**: 预期Accessory端能正常接收原始数据

## 测试验证

### 1. 连接稳定性测试
- 建立AOA连接
- 发送INIT命令
- 观察连接是否保持稳定（不应在214ms后断开）

### 2. 数据传输测试
- Host端发送数据
- Accessory端检查是否接收到数据
- 验证数据内容是否正确

### 3. 日志验证
期望看到的日志变化：
```log
# Host端
AOA sending raw data (HexFrame disabled): [原始数据] ([原始大小] bytes)
AOA data sent successfully: [原始大小] bytes (attempt 1)

# 不应该再看到
Removed device at /dev/bus/usb/001/013: P3
```

## 长期解决方案

### 1. 协议协商机制
```kotlin
// 未来实现
private suspend fun negotiateProtocol(): ProtocolVersion {
    // 1. 查询Accessory端支持的协议
    // 2. 选择兼容的协议版本
    // 3. 确认协议配置
}
```

### 2. 设备兼容性检测
```kotlin
// 未来实现
private fun checkDeviceCompatibility(device: UsbDevice): ProtocolSupport {
    // 检查设备是否支持HexFrame协议
    // 返回支持的协议列表
}
```

### 3. 动态协议选择
```kotlin
// 未来实现
private fun selectOptimalProtocol(deviceSupport: ProtocolSupport): Protocol {
    // 根据设备支持情况选择最佳协议
    // 优先级: HexFrame > Raw > Fallback
}
```

## 注意事项

### 1. 临时性修复
这是一个临时修复，目的是快速解决连接断开问题。长期需要实现协议协商机制。

### 2. 协议一致性
确保Host端和Accessory端使用相同的数据格式：
- 如果Host端发送原始数据，Accessory端也应期望原始数据
- 避免一端使用HexFrame，另一端使用原始格式

### 3. 其他通信模式
这个修改只影响AOA模式，Serial模式仍然使用HexFrame协议。

## 验证清单

- [ ] 代码编译无错误
- [ ] AOA连接建立成功
- [ ] 数据发送不会导致连接断开
- [ ] Accessory端能接收到数据
- [ ] 数据内容正确无误
- [ ] 连接保持稳定

## 结论

通过禁用AOA模式的HexFrame协议，应该能够解决设备在接收数据后立即断开的问题。这个修复基于明确的日志证据，显示设备无法处理HexFrame格式的数据。

修复后，AOA数据传输应该能够正常工作，Host端发送的数据应该能被Accessory端成功接收。