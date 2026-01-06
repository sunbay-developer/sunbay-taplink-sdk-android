# AOA双重授权权限冲突修复

## 问题描述

Host端连接时出现两次授权弹框，第二次用户拒绝后连接失败：

```
2025-12-30 16:35:31.325 USB device permission granted: /dev/bus/usb/001/100
2025-12-30 16:35:38.065 Permission granted: false (第二次授权被拒绝)
2025-12-30 16:35:38.075 Status changed: WAITING_CONNECT -> ERROR
```

## 根本原因

Host端继承了BaseUsbStandardKernel，导致同时注册了两种权限接收器：

1. **BaseUsbHostKernel.permissionReceiver** - 处理USB设备权限（正确）
2. **BaseUsbStandardKernel.accessoryPermissionReceiver** - 处理Accessory权限（错误）

两个接收器使用相同的`permissionAction`，导致权限处理冲突：

- 第一次授权：原始Android设备权限 (VID: 0x0E8D, PID: 0x201C) - 成功
- 第二次授权：AOA设备权限 (VID: 0x18D1, PID: 0x2D01) - 被错误的接收器处理

## 问题分析

### 权限接收器冲突

```kotlin
// BaseUsbHostKernel - 正确的设备权限处理
protected val permissionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        // 处理UsbDevice权限
    }
}

// BaseUsbStandardKernel - 错误的Accessory权限处理（Host端不需要）
protected val accessoryPermissionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val accessory: UsbAccessory? = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY)
        // 期望UsbAccessory但收到的是UsbDevice权限广播
    }
}
```

### 日志分析

```
USB accessory permission result:
  Accessory: null null  // 期望UsbAccessory但实际是UsbDevice
  Permission granted: false
  Pending accessory: null null
```

## 修复方案

### 1. 添加Host模式标志

在BaseUsbStandardKernel中添加`isHostMode`参数：

```kotlin
abstract class BaseUsbStandardKernel(
    // ... 其他参数
    private val isHostMode: Boolean = false  // 添加Host模式标志
) : AsyncServiceKernel(appId, appSecretKey) {
```

### 2. 条件注册权限接收器

只有非Host模式才注册Accessory权限接收器：

```kotlin
init {
    // 只有非Host模式才注册Accessory权限请求广播接收器
    if (!isHostMode) {
        val permissionFilter = IntentFilter(permissionAction)
        try {
            ContextCompat.registerReceiver(
                context,
                accessoryPermissionReceiver,
                permissionFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            LogUtil.d(TAG, "USB accessory permission receiver registered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB accessory permission receiver: ${e.message}")
        }
    } else {
        LogUtil.d(TAG, "Host mode: skipping accessory permission receiver registration")
    }
}
```

### 3. 更新构造函数调用

```kotlin
// BaseUsbHostKernel
abstract class BaseUsbHostKernel(
    // ... 参数
) : BaseUsbStandardKernel(appId, appSecretKey, context, usbStandardInfo, permissionAction, permissionPendingIntent, true) {  // isHostMode=true

// UsbStandardAccessoryKernel  
class UsbStandardAccessoryKernel(
    // ... 参数
) : BaseUsbStandardKernel("", "", context, usbStandardInfo, permissionAction, permissionPendingIntent, false) {  // isHostMode=false
```

### 4. 改进权限处理日志

添加更详细的权限处理日志：

```kotlin
LogUtil.d(TAG, "USB device permission result:")
LogUtil.d(TAG, "  Device: ${device?.deviceName}")
LogUtil.d(TAG, "  Permission granted: $permissionGranted")
LogUtil.d(TAG, "  Pending device: ${pendingDevice?.deviceName}")
```

### 5. 条件清理资源

在release方法中也要条件性地清理：

```kotlin
open fun release() {
    // 只有非Host模式才注销Accessory权限接收器
    if (!isHostMode) {
        try {
            context.unregisterReceiver(accessoryPermissionReceiver)
            LogUtil.d(TAG, "USB accessory permission receiver unregistered")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Error unregistering USB accessory permission receiver: ${e.message}")
        }
    }
}
```

## 预期效果

修复后，Host端只会有一次授权弹框：

1. **第一次授权**：原始Android设备权限 - 用于发送AOA协议命令
2. **AOA设备权限**：自动继承或无需额外授权

### 预期日志

```
Host mode: skipping accessory permission receiver registration
USB device permission receiver registered
USB device permission granted: /dev/bus/usb/001/100
AOA protocol completed successfully
AOA device detected: /dev/bus/usb/001/101
AOA device already has permission, connecting directly...
AOA device connected successfully
```

## 测试验证

### 测试场景

1. **首次连接**：验证只显示一次授权框
2. **重复连接**：验证权限复用
3. **权限拒绝**：验证错误处理

### 验证要点

- Host端不注册Accessory权限接收器
- 只有设备权限接收器处理权限广播
- AOA设备权限自动继承或无需额外授权
- 错误日志清晰明确

## 总结

通过区分Host模式和Accessory模式的权限处理需求，避免了权限接收器冲突，解决了双重授权问题。关键改进：

1. **模式区分**：Host模式不注册Accessory权限接收器
2. **权限隔离**：设备权限和Accessory权限分别处理
3. **日志改进**：提供清晰的权限处理信息
4. **资源管理**：条件性注册和清理接收器

这样可以显著改善用户体验，避免不必要的权限请求。