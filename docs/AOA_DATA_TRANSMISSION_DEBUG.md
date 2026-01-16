# AOA数据传输调试指南

## 问题现象

Host端发送数据（包括硬编码数据），但Accessory端没有收到，接收数据的长度一直为-1。

## 可能的原因

### 1. 时序问题
- **问题**: Host端在Accessory端接收循环启动之前就发送了数据
- **症状**: Host端显示发送成功，但Accessory端没有收到
- **解决方案**: 确保Accessory端在连接成功后立即启动接收循环

### 2. 端点地址不匹配
- **问题**: Host端使用的OUT端点地址与Accessory端期望的地址不匹配
- **症状**: 数据发送到错误的端点
- **解决方案**: 验证端点地址（Host OUT=0x02, Accessory IN=0x82）

### 3. 接口未正确Claim
- **问题**: USB接口在发送时没有被正确claim
- **症状**: bulkTransfer返回错误或超时
- **解决方案**: 确保接口在连接时claim，发送时不再重新claim

### 4. 数据流向问题
- **问题**: 对USB AOA协议的数据流向理解错误
- **症状**: 数据发送到错误的方向
- **解决方案**: 
  - Host端：使用OUT端点（0x02）发送数据
  - Accessory端：使用FileInputStream接收数据

## 诊断步骤

### 1. 检查Host端发送日志

查看以下关键日志：
```
Attempting to send data (attempt 1/3):
  Data size: X bytes
  Endpoint: 0x02
  Interface: X
  Device: /dev/bus/usb/XXX/XXX
  Data preview (first 32 bytes): XX XX XX ...
Bulk transfer result: X (expected: X)
```

**关键检查点**:
- `Bulk transfer result` 是否等于数据大小？
- 如果返回负数，说明传输失败（-1=超时, -2=管道错误, -3=设备不存在）

### 2. 检查Accessory端接收日志

查看以下关键日志：
```
=== Starting AOA data receive (Accessory模式) ===
Receive loop configuration:
  Buffer size: 16384 bytes
  Read timeout: 2000ms
  Max consecutive errors: 5
First read attempt: using ExecutorService with timeout: 2000ms
Calling inputStream.read() in separate thread with timeout: 2000ms, fd=XX
First read result: bytesRead=XX
```

**关键检查点**:
- 接收循环是否已启动？
- `First read result` 的值是什么？
  - `> 0`: 成功接收到数据
  - `-1`: 流结束（EOF），可能表示连接断开
  - `null`: 读取超时（正常，继续等待）

### 3. 检查连接建立时序

**Host端连接流程**:
1. 打开设备连接
2. Claim接口
3. 查找端点
4. 更新状态为CONNECTED
5. 调用连接成功回调
6. **此时可以发送数据**

**Accessory端连接流程**:
1. 打开Accessory连接
2. 创建FileInputStream/FileOutputStream
3. 更新状态为CONNECTED
4. **立即启动接收循环**
5. 调用连接成功回调

**关键检查点**:
- Accessory端的接收循环是否在Host端发送数据之前启动？
- 两个端的CONNECTED状态是否都已设置？

## 调试代码

### Host端发送前检查

```kotlin
// 验证接口是否仍然被claim
val interfaceId = usbInterface?.id
if (interfaceId == null) {
    LogUtil.e(TAG, "USB interface is null, cannot send data")
    return false
}

// 检查连接状态
LogUtil.d(TAG, "Connection state: connection=${usbDeviceConnection != null}, endpoint=${usbEndpointOut != null}")

// 执行bulk传输
val result = usbDeviceConnection!!.bulkTransfer(
    usbEndpointOut, data, data.size, 5000
)

// 检查结果
if (result < 0) {
    LogUtil.e(TAG, "Bulk transfer failed with error code: $result")
    // -1 = timeout
    // -2 = pipe error
    // -3 = no device
}
```

### Accessory端接收检查

```kotlin
// 检查接收循环状态
LogUtil.d(TAG, "Receive loop state:")
LogUtil.d(TAG, "  isActive=$isActive")
LogUtil.d(TAG, "  status=$currentInnerConnectionStatus")
LogUtil.d(TAG, "  inputStream=${accessoryInputStream != null}")
LogUtil.d(TAG, "  fileDescriptor=${accessoryFileDescriptor?.fileDescriptor}")

// 检查第一次读取结果
if (readCount == 1L) {
    LogUtil.d(TAG, "First read result: bytesRead=$bytesRead")
    if (bytesRead == -1) {
        LogUtil.w(TAG, "Stream returned EOF immediately - possible connection issue")
    }
}
```

## 常见问题排查

### Q1: Host端显示发送成功，但Accessory端没有收到

**可能原因**:
1. Accessory端接收循环未启动
2. 端点地址不匹配
3. 接口未正确claim

**排查步骤**:
1. 检查Accessory端日志，确认接收循环已启动
2. 检查端点地址（Host OUT=0x02, Accessory IN=0x82）
3. 检查接口ID是否一致

### Q2: Accessory端一直返回-1（EOF）

**可能原因**:
1. 连接在接收循环启动前就断开了
2. FileInputStream在创建时就有问题
3. 设备在连接后立即断开

**排查步骤**:
1. 检查Accessory连接是否仍然有效
2. 检查系统Accessory列表
3. 检查文件描述符是否有效

### Q3: Accessory端读取一直超时（返回null）

**可能原因**:
1. Host端没有发送数据
2. 数据发送到错误的端点
3. 数据在传输过程中丢失

**排查步骤**:
1. 检查Host端发送日志，确认数据已发送
2. 检查端点地址是否正确
3. 尝试发送硬编码的测试数据

## 测试建议

1. **发送硬编码数据测试**:
   ```kotlin
   val testData = byteArrayOf(0xEE, 0xEE) // 简单的测试数据
   sendDataToAoaDevice(testData)
   ```

2. **检查时序**:
   - 在Host端发送前，等待1秒确保Accessory端接收循环已启动
   - 或者添加同步机制，确保Accessory端准备好后再发送

3. **验证端点**:
   - 打印所有端点的地址和方向
   - 确认使用的端点符合AOA标准

4. **监控连接状态**:
   - 在发送和接收时都检查连接状态
   - 如果连接断开，立即停止操作

## 修复后的预期行为

1. **Host端**:
   - 发送前验证接口和端点状态
   - 发送后检查返回值
   - 记录详细的发送日志

2. **Accessory端**:
   - 连接后立即启动接收循环
   - 记录详细的接收日志
   - 正确处理EOF和超时情况

3. **数据流**:
   - Host端通过OUT端点（0x02）发送数据
   - Accessory端通过FileInputStream接收数据
   - 数据成功传输并触发dataReceiver回调


