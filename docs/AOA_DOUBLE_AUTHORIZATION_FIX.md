# AOA双重授权问题修复

## 问题描述

USB Host连接时弹出了两次授权框，用户体验不佳。

## 问题原因

AOA协议的工作流程导致需要两次权限请求：

1. **第一次授权**：对原始Android设备的权限
   - 用于发送AOA协议命令（GET_PROTOCOL、SEND_STRING、START）
   - 设备此时以普通Android设备身份出现

2. **第二次授权**：对AOA设备的权限  
   - 设备接收到START命令后重新枚举为AOA设备
   - VID/PID变为0x18D1/0x2D00-0x2D05
   - 系统将其视为新设备，需要重新授权

## 修复方案

### 1. 智能权限检查
- 在等待AOA设备时，先检查是否已有权限
- 如果已有权限，直接连接，避免重复请求

### 2. 权限请求优化
- 添加权限请求标记，避免重复请求
- 提供更清晰的日志说明为什么需要第二次授权

### 3. 预检查现有AOA设备
- 在开始AOA协议前，检查是否已有AOA设备存在
- 如果存在，直接连接，跳过协议流程

## 代码修改

### UsbStandardHostKernel.kt

1. **waitForAoaAccessory方法优化**：
```kotlin
private suspend fun waitForAoaAccessory(connectionCallback: ConnectionCallback) {
    var aoaPermissionRequested = false // 标记是否已经请求过AOA设备权限
    
    // 检查是否已有权限
    if (usbManager.hasPermission(aoaDevice)) {
        LogUtil.d(TAG, "AOA device already has permission, connecting directly...")
        connectToAoaDeviceDirectly(aoaDevice, connectionCallback)
        return
    } else if (!aoaPermissionRequested) {
        // 只请求一次AOA设备权限
        LogUtil.d(TAG, "Requesting permission for AOA device (this will show second authorization dialog)")
        aoaPermissionRequested = true
        connectToAoaDevice(aoaDevice, connectionCallback)
        return
    }
}
```

2. **分离直接连接方法**：
```kotlin
private suspend fun connectToAoaDeviceDirectly(device: UsbDevice, connectionCallback: ConnectionCallback) {
    // 直接连接，不检查权限（已确认有权限）
}
```

3. **预检查AOA设备**：
```kotlin
private suspend fun startAoaProtocol(device: UsbDevice, connectionCallback: ConnectionCallback) {
    // 预先检查是否已经有AOA设备存在
    val existingAoaDevice = usbManager.deviceList.values.find { aoaDevice ->
        UsbStandardProtocol.isAoaCompatible(aoaDevice.vendorId, aoaDevice.productId)
    }
    
    if (existingAoaDevice != null) {
        LogUtil.d(TAG, "Found existing AOA device, connecting directly...")
        connectToAoaDevice(existingAoaDevice, connectionCallback)
        return
    }
}
```

## 用户体验改进

### 授权提示优化
- 第一次授权：提示用于"初始化AOA连接"
- 第二次授权：提示用于"连接AOA设备"（如果需要）

### 日志改进
```kotlin
LogUtil.d(TAG, "=== Starting AOA Protocol ===")
LogUtil.d(TAG, "Original device: ${device.deviceName}")
LogUtil.d(TAG, "Expected AOA device VID/PID: 0x18D1/0x2D00-0x2D05")
LogUtil.d(TAG, "Requesting permission for AOA device (this will show second authorization dialog)")
```

## 测试验证

### 测试场景
1. **首次连接**：验证是否只显示必要的授权框
2. **重复连接**：验证是否能复用已有权限
3. **设备已在AOA模式**：验证是否跳过协议流程

### 预期结果
- 减少不必要的权限请求
- 提供清晰的授权说明
- 改善用户体验

## 注意事项

1. **Android系统限制**：
   - AOA协议要求设备重新枚举
   - 系统会将AOA设备视为新设备
   - 某些情况下双重授权不可避免

2. **权限缓存**：
   - Android可能缓存USB权限
   - 重启应用或系统可能清除缓存

3. **设备兼容性**：
   - 不同Android版本行为可能不同
   - 某些设备可能有特殊处理

## 总结

通过智能权限检查和预检查机制，可以显著减少双重授权的出现频率，提升用户体验。但由于AOA协议的特性，在某些情况下仍可能需要两次授权。