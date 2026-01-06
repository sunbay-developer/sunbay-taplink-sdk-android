# AOA重复权限广播问题修复

## 问题描述

在USB AOA连接过程中，发现同一个设备的权限授权会收到两次广播回调：

```
11:33:35.010 - 第一次广播: Device: /dev/bus/usb/001/014, Pending device: null
11:33:35.019 - 第二次广播: Device: /dev/bus/usb/001/014, Pending device: /dev/bus/usb/001/014
```

第一次广播时 `pendingDevice` 为 null，第二次才是正确的设备。

## 问题分析

### 可能的原因

1. **Android系统行为**: 某些Android版本可能会发送重复的权限广播
2. **PendingIntent重用**: 使用 `FLAG_UPDATE_CURRENT` 可能导致同一权限请求触发多次广播
3. **时序竞态**: 权限请求和广播接收之间的时序问题
4. **广播接收器重复注册**: 可能存在多个相同的广播接收器实例

### 日志分析

从日志可以看出：
- 同一个设备 `/dev/bus/usb/001/014` 收到两次权限广播
- 第一次: `Pending device: null` - 表明 `pendingDevice` 已被清空
- 第二次: `Pending device: /dev/bus/usb/001/014` - 正确的待处理设备
- 两次广播间隔很短（约9ms）

## 解决方案

### 1. 防抖处理

添加时间戳防抖机制，忽略短时间内的重复广播：

```kotlin
private var lastPermissionBroadcastTime = 0L
private val PERMISSION_BROADCAST_DEBOUNCE_MS = 100L

// 在广播接收器中
val currentTime = System.currentTimeMillis()
if (currentTime - lastPermissionBroadcastTime < PERMISSION_BROADCAST_DEBOUNCE_MS) {
    LogUtil.w(TAG, "Ignoring duplicate permission broadcast within ${PERMISSION_BROADCAST_DEBOUNCE_MS}ms")
    return
}
lastPermissionBroadcastTime = currentTime
```

### 2. 改进权限状态检查

增强对权限广播的验证：

```kotlin
// 检查设备是否为空
if (device == null) {
    LogUtil.w(TAG, "Received permission broadcast with null device, ignoring")
    return
}

// 更详细的设备匹配检查
if (device == pendingDevice) {
    // 正常处理
} else if (pendingDevice == null) {
    LogUtil.w(TAG, "Permission granted but no pending device (possible duplicate broadcast)")
} else {
    LogUtil.w(TAG, "Permission granted for different device: expected ${pendingDevice?.deviceName}, got ${device.deviceName}")
}
```

### 3. 同步保护

确保权限处理的原子性：

```kotlin
synchronized(this@BaseUsbHostKernel) {
    // 权限处理逻辑
    val callback = currentConnectionCallback
    pendingDevice = null // 立即清空，防止重复处理
    
    if (callback != null) {
        scope.launch {
            onPermissionGranted(device, callback)
        }
    }
}
```

## 修复效果

修复后的行为：
1. **防抖保护**: 100ms内的重复广播会被忽略
2. **状态验证**: 更严格的设备和状态检查
3. **原子操作**: 权限处理过程中的状态变更是原子的
4. **详细日志**: 提供更多调试信息

## 测试验证

修复后需要验证：
1. 正常的权限请求流程不受影响
2. 重复广播被正确过滤
3. AOA协议的两次权限请求都能正常工作
4. 错误情况下的异常处理正确

## 相关文件

- `BaseUsbHostKernel.kt`: 权限广播接收器实现
- `UsbStandardHostKernel.kt`: AOA协议权限请求逻辑

## 注意事项

1. **防抖时间**: 100ms的防抖时间需要根据实际测试调整
2. **AOA兼容性**: 确保修复不影响AOA协议的正常流程
3. **Android版本**: 不同Android版本的行为可能不同，需要广泛测试
4. **性能影响**: 防抖机制的性能开销很小，可以忽略

## 后续优化

1. 考虑使用更精确的设备标识符（如序列号）进行匹配
2. 添加权限请求超时机制
3. 优化PendingIntent的创建和管理
4. 考虑使用不同的requestCode来区分不同的权限请求