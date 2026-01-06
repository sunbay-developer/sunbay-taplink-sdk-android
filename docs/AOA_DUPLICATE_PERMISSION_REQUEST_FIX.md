# AOA重复权限请求修复

## 问题描述

Host端在连接AOA设备时出现重复的权限请求，导致用户需要授权两次相同的设备。

## 问题分析

从日志可以看出，有两个地方同时在处理AOA设备连接：

### 1. 重复处理源头

```
19:02:42.844 AOA device detected: /dev/bus/usb/001/069  (waitForAoaAccessory)
19:02:42.844 AOA device attached via broadcast: /dev/bus/001/069  (onDeviceAttached)
19:02:42.846 Requesting permission for AOA device (第一次)
19:02:42.847 Attempting to connect to AOA device: /dev/bus/usb/001/069 (第二次)
19:02:42.849 Requesting permission for AOA device (第三次)
```

### 2. 处理流程冲突

1. **waitForAoaAccessory方法**：轮询检测AOA设备出现
2. **onDeviceAttached回调**：响应设备插入广播
3. **两者同时检测到同一个AOA设备**
4. **都调用connectToAoaDevice方法**
5. **导致重复的权限请求**

### 3. 广播重复

```
19:02:42.818 USB_DEVICE_ATTACHED (第一次广播)
19:02:42.829 USB_DEVICE_ATTACHED (第二次广播)  
19:02:42.839 USB_DEVICE_ATTACHED (第三次广播)
```

系统发送了多次相同的设备连接广播，每次都触发处理逻辑。

## 修复方案

### 1. 避免onDeviceAttached重复处理

修改`onDeviceAttached`方法，不在其中处理AOA设备连接：

```kotlin
override fun onDeviceAttached(device: UsbDevice) {
    // 检查是否是AOA设备
    if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
        LogUtil.d(TAG, "AOA device attached via broadcast: ${device.deviceName}")
        
        // 只在等待状态时尝试连接，避免与waitForAoaAccessory重复处理
        if (currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) {
            LogUtil.d(TAG, "Host is waiting for AOA device, but waitForAoaAccessory will handle this")
            // 不在这里处理连接，让waitForAoaAccessory方法来处理
            // 这样避免重复的权限请求
        }
    }
}
```

### 2. 添加连接进度标志

在类中添加标志防止重复处理：

```kotlin
class UsbStandardHostKernel {
    // 添加标志防止重复处理AOA设备连接
    private var aoaConnectionInProgress = false
}
```

### 3. 修改waitForAoaAccessory逻辑

```kotlin
private suspend fun waitForAoaAccessory(connectionCallback: ConnectionCallback) {
    // ...
    
    if (aoaDevice != null && !aoaConnectionInProgress) {
        LogUtil.d(TAG, "AOA device detected: ${aoaDevice.deviceName}")
        
        // 设置标志防止重复处理
        aoaConnectionInProgress = true
        
        try {
            // 检查权限和连接逻辑
            if (usbManager.hasPermission(aoaDevice)) {
                connectToAoaDeviceDirectly(aoaDevice, connectionCallback)
                return
            } else if (!aoaPermissionRequested) {
                aoaPermissionRequested = true
                connectToAoaDevice(aoaDevice, connectionCallback)
                return
            }
        } catch (e: Exception) {
            aoaConnectionInProgress = false // 重置标志
        }
    }
}
```

### 4. 在关键点重置标志

```kotlin
// 连接成功时
handleConnectionSuccess(connectionCallback, null)
aoaConnectionInProgress = false // 重置标志

// 连接失败时
catch (e: Exception) {
    aoaConnectionInProgress = false // 重置标志
    throw e
}

// 超时时
// 超时处理
aoaConnectionInProgress = false // 重置标志
```

## 修复效果

### 1. 避免重复处理
- `onDeviceAttached`不再处理AOA设备连接
- 只有`waitForAoaAccessory`负责AOA设备连接
- 消除了处理逻辑冲突

### 2. 防止并发访问
- `aoaConnectionInProgress`标志防止同时处理
- 确保同一时间只有一个连接尝试
- 在成功、失败、超时时都会重置标志

### 3. 单一职责
- `waitForAoaAccessory`：专门负责AOA设备连接
- `onDeviceAttached`：只负责日志记录和状态检查
- 职责分离，逻辑清晰

## 预期日志

修复后的正常流程：

```
=== Starting AOA Protocol ===
AOA protocol completed successfully
Device will now re-enumerate as AOA Accessory...
=== USB DEVICE DETACHED === (原设备断开)
=== USB DEVICE ATTACHED === (AOA设备连接)
AOA device attached via broadcast: /dev/bus/usb/001/069
Host is waiting for AOA device, but waitForAoaAccessory will handle this
AOA device detected: /dev/bus/usb/001/069 (waitForAoaAccessory处理)
Requesting permission for AOA device (只有一次权限请求)
AOA device connected successfully
```

## 测试验证

### 测试场景
1. **正常连接**：验证只有一次权限请求
2. **重复广播**：验证多次广播不会重复处理
3. **并发处理**：验证标志机制有效
4. **异常恢复**：验证异常时标志正确重置

### 验证要点
- 只显示一次权限授权对话框
- 连接成功率不受影响
- 异常情况下能正确恢复
- 日志清晰显示处理流程

## 总结

通过以下修复措施解决了重复权限请求问题：

1. **职责分离**：明确各方法的职责范围
2. **并发控制**：使用标志防止重复处理
3. **状态管理**：在关键点正确重置状态
4. **逻辑优化**：避免多个地方处理同一事件

修复后，用户只需要授权一次AOA设备权限，显著改善了用户体验。