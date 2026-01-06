# AOA Host端设备事件处理修复

## 问题描述

UsbStandardHostKernel没有实现`onDeviceAttached`和`onDeviceDetached`方法，导致Host端无法正确响应USB设备的插拔事件。

## 问题分析

### 1. 缺失的方法实现
BaseUsbHostKernel中定义了设备事件回调方法：
```kotlin
protected open fun onDeviceAttached(device: UsbDevice) {}
protected open fun onDeviceDetached(device: UsbDevice) {}
```

但UsbStandardHostKernel没有重写这些方法。

### 2. 错误的事件处理
UsbStandardHostKernel中有一个`onAccessoryAttached`方法，但：
- 它不是override的，不会被调用
- Host端不应该处理Accessory事件，应该处理Device事件

### 3. 设备插拔逻辑缺失
Host端需要能够：
- 检测AOA设备的连接
- 在等待状态时自动连接新插入的AOA设备
- 处理已连接设备的断开

### 4. 避免重复逻辑
BaseUsbHostKernel的`deviceReceiver`已经处理了设备断开的核心逻辑：
- 检查是否是当前连接的设备
- 调用`disconnectInternal()`和`updateStatus()`
- 处理待处理设备的清理

子类只需要添加特定的处理逻辑，避免重复。

## 修复实现

### 1. 删除错误的Accessory事件处理

删除了UsbStandardHostKernel中的`onAccessoryAttached`方法：
```kotlin
// 删除这个方法
fun onAccessoryAttached(accessory: UsbAccessory) {
    // Host端不应该处理Accessory事件
}
```

### 2. 实现设备连接处理

添加了`onDeviceAttached`方法：
```kotlin
override fun onDeviceAttached(device: UsbDevice) {
    LogUtil.d(TAG, "=== USB DEVICE ATTACHED ===")
    LogUtil.d(TAG, "Device attached: ${device.deviceName}")
    LogUtil.d(TAG, "VID: ${String.format("0x%04X", device.vendorId)}, PID: ${String.format("0x%04X", device.productId)}")
    
    // 检查是否是AOA设备
    if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
        LogUtil.d(TAG, "AOA device attached via broadcast: ${device.deviceName}")
        
        // 如果正在等待AOA设备，尝试连接
        if (currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) {
            LogUtil.d(TAG, "Host is waiting for AOA device, attempting connection...")
            scope.launch {
                val callback = currentConnectionCallback
                if (callback != null) {
                    try {
                        connectToAoaDevice(device, callback)
                    } catch (e: Exception) {
                        LogUtil.e(TAG, "Failed to connect to AOA device from broadcast: ${e.message}")
                    }
                } else {
                    LogUtil.w(TAG, "No connection callback available for AOA device")
                }
            }
        }
    }
}
```

### 3. 实现设备断开处理（避免重复逻辑）

添加了简化的`onDeviceDetached`方法：
```kotlin
override fun onDeviceDetached(device: UsbDevice) {
    LogUtil.d(TAG, "=== USB DEVICE DETACHED ===")
    LogUtil.d(TAG, "Device detached: ${device.deviceName}")
    LogUtil.d(TAG, "VID: ${String.format("0x%04X", device.vendorId)}, PID: ${String.format("0x%04X", device.productId)}")
    
    // 基础的断开逻辑已经在BaseUsbHostKernel.deviceReceiver中处理了
    // 这里只需要添加AOA特定的日志和处理
    if (UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)) {
        LogUtil.d(TAG, "AOA device detached")
        
        // 如果正在等待连接，记录状态
        if (currentInnerConnectionStatus == InnerConnectionStatus.WAITING_CONNECT) {
            LogUtil.w(TAG, "AOA device detached while waiting for connection")
        }
    } else {
        LogUtil.d(TAG, "Non-AOA device detached")
    }
}
```

## 架构设计

### 事件处理层次
```
BaseUsbHostKernel.deviceReceiver (广播接收器)
├── 处理核心逻辑：断开连接、状态更新、资源清理
└── 调用子类回调：onDeviceAttached() / onDeviceDetached()
    └── UsbStandardHostKernel (子类实现)
        ├── AOA设备检测和分类
        ├── 状态感知的连接处理
        └── 详细的事件日志
```

### 职责分离
- **BaseUsbHostKernel**：处理通用的设备管理逻辑
- **UsbStandardHostKernel**：处理AOA特定的逻辑和日志

## 功能特性

### 1. AOA设备自动检测
- 检查设备的VID/PID是否符合AOA规范
- 区分AOA设备和普通USB设备

### 2. 状态感知连接
- 只在`WAITING_CONNECT`状态时尝试连接新设备
- 避免在不合适的时机进行连接

### 3. 避免重复处理
- 核心断开逻辑由父类处理
- 子类只添加特定的处理和日志

### 4. 详细日志记录
- 记录设备插拔事件
- 显示设备VID/PID信息
- 跟踪连接状态变化

## 预期效果

### 1. 改善连接体验
- AOA设备插入时自动检测和连接
- 设备断开时及时响应（由父类处理）

### 2. 更好的错误处理
- 连接失败时提供详细错误信息
- 状态不匹配时给出明确提示

### 3. 增强调试能力
- 详细的设备事件日志
- 清晰的状态跟踪信息

### 4. 代码清晰性
- 避免重复逻辑
- 职责分离明确

## 测试验证

### 测试场景
1. **设备插入**：在等待状态时插入AOA设备
2. **设备拔出**：在连接状态时拔出AOA设备
3. **非AOA设备**：插入普通USB设备
4. **状态不匹配**：在非等待状态时插入AOA设备

### 预期日志
```
=== USB DEVICE ATTACHED ===
Device attached: /dev/bus/usb/001/101
VID: 0x18D1, PID: 0x2D01
AOA device attached via broadcast: /dev/bus/usb/001/101
Host is waiting for AOA device, attempting connection...
AOA device connected successfully
=== END USB DEVICE ATTACHED ===

=== USB DEVICE DETACHED ===
Device detached: /dev/bus/usb/001/101
VID: 0x18D1, PID: 0x2D01
AOA device detached
=== END USB DEVICE DETACHED ===
```

## 总结

通过实现正确的设备事件处理方法，并避免与父类的重复逻辑，Host端现在能够：

1. **正确响应设备插拔事件**
2. **自动检测和连接AOA设备**
3. **避免重复的断开处理逻辑**
4. **提供详细的事件日志**

这个修复完善了Host端的设备管理功能，同时保持了代码的清晰性和可维护性。