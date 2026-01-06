# VSPClientKernel Callback重复分析

## 问题描述

VSPClientKernel中存在两个ConnectionCallback引用：
1. 父类的 `currentConnectionCallback` (AsyncServiceKernel)
2. 子类的 `pendingConnectionCallback` (VSPClientKernel)

需要分析是否存在重复，以及是否可以简化。

## 当前使用场景分析

### 父类 currentConnectionCallback

**保存时机：**
```kotlin
// AsyncServiceKernel.connect()
currentConnectionCallback = connectionCallback
```

**使用场景：**
```kotlin
// 通知连接成功
protected fun notifyConnectionSuccess(extraInfoMap: Map<String, String?>? = null) {
    currentConnectionCallback?.let { callback ->
        handleConnectionSuccess(callback, extraInfoMap)
    }
}

// 通知连接错误
protected fun notifyConnectionError(message: String, error: InnerErrorCode = InnerErrorCode.C25) {
    currentConnectionCallback?.let { callback ->
        handleConnectionError("${error.description}(${message})", callback, error.code)
    }
}

// 通知连接断开
protected fun notifyConnectionDisconnected(code: String, message: String) {
    currentConnectionCallback?.onDisconnected(code, message)
}
```

**清理时机：**
```kotlin
// AsyncServiceKernel.cleanupCommonResources()
currentConnectionCallback = null
```

### 子类 pendingConnectionCallback

**保存时机：**
```kotlin
// 权限请求时
synchronized(this@VSPClientKernel) {
    pendingDevice = usbDevice
    pendingConnectionCallback = connectionCallback  // 保存callback
}

// 手动权限请求时
synchronized(this) {
    pendingDevice = device
    pendingConnectionCallback = callback  // 保存callback
}
```

**使用场景：**
```kotlin
// 权限广播接收器中
if (device != null && device == pendingDevice) {
    pendingDevice = null
    val callback = pendingConnectionCallback  // 取出callback
    pendingConnectionCallback = null

    if (permissionGranted) {
        // 使用callback继续连接
        if (callback != null) {
            scope.launch {
                continueConnection(device, callback)
            }
        }
    } else {
        // 使用notifyConnectionError (父类方法)
        callback?.let {
            notifyConnectionError("USB permission denied by user", InnerErrorCode.C25)
        }
    }
}
```

**清理时机：**
```kotlin
// 权限请求失败时
synchronized(this@VSPClientKernel) {
    pendingDevice = null
    pendingConnectionCallback = null
}

// cleanupCommonResources()中
synchronized(this) {
    pendingDevice = null
    pendingConnectionCallback = null
}
```

## 生命周期对比

### 正常连接流程（无需权限）
```
1. connect() 调用
   ├─ currentConnectionCallback = connectionCallback (父类保存)
   └─ connectToVspDevice(connectionCallback) (直接传递)
       └─ 连接成功 → notifyConnectionSuccess() (使用父类callback)
```

### 权限请求流程
```
1. connect() 调用
   ├─ currentConnectionCallback = connectionCallback (父类保存)
   └─ connectToVspDevice(connectionCallback)
       └─ 需要权限
           ├─ pendingConnectionCallback = connectionCallback (子类保存)
           └─ 等待权限广播
               └─ 权限授予 → continueConnection(device, pendingConnectionCallback)
```

## 问题分析

### ❌ 存在重复保存

**问题1：同一个callback被保存两次**
```kotlin
// 父类保存
currentConnectionCallback = connectionCallback

// 子类也保存（权限请求时）
pendingConnectionCallback = connectionCallback

// 结果：currentConnectionCallback == pendingConnectionCallback
```

**问题2：清理逻辑重复**
```kotlin
// 父类清理
currentConnectionCallback = null

// 子类也清理
pendingConnectionCallback = null
```

**问题3：使用混乱**
```kotlin
// 有时使用父类callback
notifyConnectionError("USB permission denied by user", InnerErrorCode.C25)

// 有时直接使用子类callback
callback?.let {
    notifyConnectionError("USB permission denied by user", InnerErrorCode.C25)
}
```

## 解决方案

### 方案1：移除pendingConnectionCallback（推荐）

```kotlin
// 删除
// private var pendingConnectionCallback: ConnectionCallback? = null

// 权限请求时不保存callback，直接使用父类的
synchronized(this@VSPClientKernel) {
    pendingDevice = usbDevice
    // 不需要保存callback，使用currentConnectionCallback
}

// 权限广播接收器中
if (device != null && device == pendingDevice) {
    pendingDevice = null
    
    if (permissionGranted) {
        LogUtil.d(TAG, "USB permission granted for VSP device: ${device.deviceName}")
        
        // 直接使用父类callback
        currentConnectionCallback?.let { callback ->
            scope.launch {
                continueConnection(device, callback)
            }
        }
    } else {
        LogUtil.e(TAG, "USB permission denied for VSP device: ${device.deviceName}")
        notifyConnectionError("USB permission denied by user", InnerErrorCode.C25)
    }
}
```

### 方案2：保留但统一使用

```kotlin
// 保留pendingConnectionCallback，但确保与父类同步
private fun setPendingCallback(callback: ConnectionCallback?) {
    pendingConnectionCallback = callback
    // 不修改currentConnectionCallback，让父类管理
}

// 使用时优先使用pendingConnectionCallback
private fun getCurrentCallback(): ConnectionCallback? {
    return pendingConnectionCallback ?: currentConnectionCallback
}
```

## 推荐修改

### 1. 移除pendingConnectionCallback字段
```kotlin
// 删除这行
// private var pendingConnectionCallback: ConnectionCallback? = null
```

### 2. 修改权限请求逻辑
```kotlin
// connectToVspDevice()中
synchronized(this@VSPClientKernel) {
    pendingDevice = usbDevice
    // 移除：pendingConnectionCallback = connectionCallback
}

// 权限请求失败时
synchronized(this@VSPClientKernel) {
    pendingDevice = null
    // 移除：pendingConnectionCallback = null
}
```

### 3. 修改权限广播接收器
```kotlin
if (device != null && device == pendingDevice) {
    pendingDevice = null
    // 移除：val callback = pendingConnectionCallback
    // 移除：pendingConnectionCallback = null

    if (permissionGranted) {
        LogUtil.d(TAG, "USB permission granted for VSP device: ${device.deviceName}")
        
        // 使用父类callback
        currentConnectionCallback?.let { callback ->
            scope.launch {
                continueConnection(device, callback)
            }
        }
    } else {
        LogUtil.e(TAG, "USB permission denied for VSP device: ${device.deviceName}")
        notifyConnectionError("USB permission denied by user", InnerErrorCode.C25)
    }
}
```

### 4. 修改手动权限请求方法
```kotlin
fun requestPermissionForDevice(device: UsbDevice, callback: ConnectionCallback) {
    synchronized(this) {
        pendingDevice = device
        // 移除：pendingConnectionCallback = callback
        // 注意：这里的callback参数可能与currentConnectionCallback不同
        // 需要考虑是否需要更新currentConnectionCallback
    }
    
    try {
        usbManager.requestPermission(device, permissionPendingIntent)
        LogUtil.d(TAG, "Manual USB permission request sent for: ${device.deviceName}")
    } catch (e: Exception) {
        LogUtil.e(TAG, "Failed to request USB permission manually: ${e.message}")
        synchronized(this) {
            pendingDevice = null
        }
        callback.onDisconnected("PERMISSION_REQUEST_FAILED", e.message ?: "Unknown error")
    }
}
```

### 5. 更新cleanupCommonResources
```kotlin
override fun cleanupCommonResources() {
    super.cleanupCommonResources()
    performDisconnect()
    
    // 清理权限相关状态
    synchronized(this) {
        pendingDevice = null
        // 移除：pendingConnectionCallback = null
    }
    
    // 注销广播接收器
    try {
        context.unregisterReceiver(permissionReceiver)
        LogUtil.d(TAG, "VSP USB permission receiver unregistered")
    } catch (e: Exception) {
        LogUtil.e(TAG, "Error unregistering VSP USB permission receiver: ${e.message}")
    }
}
```

## 特殊情况考虑

### 手动权限请求的callback参数

`requestPermissionForDevice(device: UsbDevice, callback: ConnectionCallback)`方法接收一个callback参数，这个callback可能与`currentConnectionCallback`不同。

**解决方案：**
1. 如果这是一个独立的权限请求（不在connect流程中），应该更新`currentConnectionCallback`
2. 或者重新设计这个方法，让它只在connect流程中使用

## 结论

`pendingConnectionCallback`确实是重复的，可以安全移除：

1. **生命周期重复**：与`currentConnectionCallback`保存相同的callback实例
2. **功能重复**：都是为了在异步操作完成后回调
3. **清理重复**：两个字段都需要在相同时机清理
4. **使用混乱**：有时用父类方法，有时直接调用

移除`pendingConnectionCallback`可以：
- ✅ 简化代码逻辑
- ✅ 减少状态管理复杂度
- ✅ 统一callback使用方式
- ✅ 避免潜在的不一致问题

唯一需要注意的是`requestPermissionForDevice`方法的callback参数处理。