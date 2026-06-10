# VSP Client端权限处理指南

## 概述

VSP Client端需要申请USB权限才能访问USB转串口设备。这是Android系统的安全机制，确保用户明确授权应用访问USB设备。

## 为什么需要权限

### 1. 系统安全要求
- Android系统将USB设备视为敏感资源
- 防止恶意应用未经授权访问USB设备
- 保护用户隐私和设备安全

### 2. USB Host模式特性
- VSP Client使用USB Host模式
- 需要访问USB转串口设备
- 每个USB设备都需要单独的权限

### 3. 权限作用域
- 权限是针对特定USB设备的
- 不同的USB设备需要分别申请权限
- 权限在应用重启后可能需要重新申请

## 权限处理流程

### 1. 自动权限处理（推荐）

```kotlin
val vspClient = VSPClientKernel(appId, appSecret, context)

// 连接时自动处理权限
vspClient.connect("vsp://115200/8/n/1", object : ConnectionCallback {
    override fun onWaitingConnect() {
        // 可能正在等待用户授权
        showMessage("正在请求USB设备权限...")
    }

    override fun onConnected(extraInfoMap: Map<String, String?>?) {
        // 权限已授予，连接成功
        showMessage("VSP设备连接成功")
    }

    override fun onError(code: String, message: String) {
        if (code.contains("PERMISSION")) {
            showMessage("用户拒绝了USB权限请求")
        } else {
            showMessage("连接失败: $message")
        }
    }
})
```

### 2. 手动权限处理

```kotlin
val vspClient = VSPClientKernel(appId, appSecret, context)

// 获取可用设备
val devices = vspClient.getAvailableDevices()

devices.forEach { deviceInfo ->
    val deviceName = deviceInfo["deviceName"]
    
    // 检查权限状态
    val hasPermission = vspClient.hasPermissionForDevice(device)
    
    if (!hasPermission) {
        // 手动请求权限
        vspClient.requestPermissionForDevice(device, permissionCallback)
    }
}
```

### 3. 预先申请权限

```kotlin
// 在应用启动时预先申请权限
fun preRequestUsbPermissions(context: Context) {
    val allDevices = VSPClientKernel.getAllUsbDevices(context)
    
    allDevices.forEach { device ->
        if (!usbManager.hasPermission(device)) {
            // 预先申请权限
            requestPermissionForDevice(device)
        }
    }
}
```

## 权限对话框处理

### 1. 用户体验优化

```kotlin
override fun onWaitingConnect() {
    // 显示友好的提示信息
    showPermissionDialog(
        title = "USB设备权限请求",
        message = "应用需要访问USB串口设备以建立通信连接，请在弹出的系统对话框中选择"允许"。",
        onCancel = {
            // 用户取消了权限请求
            vspClient.disconnect()
        }
    )
}
```

### 2. 权限被拒绝的处理

```kotlin
override fun onError(code: String, message: String) {
    when {
        code.contains("PERMISSION_DENIED") -> {
            showRetryDialog(
                message = "USB权限被拒绝，无法连接设备。是否重试？",
                onRetry = {
                    // 重新尝试连接（会再次请求权限）
                    vspClient.connect(protocol, this)
                },
                onCancel = {
                    // 用户放弃连接
                    finish()
                }
            )
        }
        else -> {
            showErrorDialog("连接失败: $message")
        }
    }
}
```

## 权限管理最佳实践

### 1. 检查系统支持

```kotlin
// 在使用VSP Client前检查系统支持
if (!VSPClientKernel.isUsbHostSupported(context)) {
    showError("设备不支持USB Host模式，无法使用VSP功能")
    return
}
```

### 2. 权限状态缓存

```kotlin
class VSPPermissionManager(private val context: Context) {
    private val permissionCache = mutableMapOf<String, Boolean>()
    
    fun isPermissionGranted(deviceName: String): Boolean {
        return permissionCache[deviceName] ?: false
    }
    
    fun cachePermissionResult(deviceName: String, granted: Boolean) {
        permissionCache[deviceName] = granted
    }
    
    fun clearCache() {
        permissionCache.clear()
    }
}
```

### 3. 批量权限处理

```kotlin
fun requestPermissionsForAllDevices(context: Context, callback: (Int, Int) -> Unit) {
    val vspClient = VSPClientKernel(appId, appSecret, context)
    val devices = vspClient.getAvailableDevices()
    
    var totalDevices = devices.size
    var grantedCount = 0
    
    devices.forEach { deviceInfo ->
        vspClient.requestPermissionForDevice(device, object : ConnectionCallback {
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                grantedCount++
                callback(grantedCount, totalDevices)
            }
            
            override fun onError(code: String, message: String) {
                callback(grantedCount, totalDevices)
            }
            
            // 其他回调方法...
        })
    }
}
```

## 常见问题和解决方案

### 1. 权限对话框不显示

**问题**: 调用权限请求后没有弹出系统对话框

**原因**:
- 设备不支持USB Host模式
- USB设备未正确连接
- 应用没有USB权限相关的权限声明

**解决方案**:
```xml
<!-- 在AndroidManifest.xml中添加 -->
<uses-feature android:name="android.hardware.usb.host" />
<uses-permission android:name="android.permission.USB_PERMISSION" />
```

### 2. 权限被拒绝后无法重新请求

**问题**: 用户拒绝权限后，再次请求时直接失败

**原因**: Android系统可能会记住用户的选择

**解决方案**:
```kotlin
// 提供手动重置权限的选项
fun resetUsbPermissions() {
    // 清理权限缓存
    permissionManager.clearCache()
    
    // 重新扫描设备
    scanDevicesAndRequestPermissions()
}
```

### 3. 多设备权限管理

**问题**: 连接多个USB设备时权限管理复杂

**解决方案**:
```kotlin
class MultiDeviceVSPManager {
    private val deviceClients = mutableMapOf<String, VSPClientKernel>()
    
    fun connectToDevice(deviceName: String, protocol: String) {
        val client = VSPClientKernel(appId, appSecret, context)
        deviceClients[deviceName] = client
        
        client.connect(protocol, createDeviceCallback(deviceName))
    }
    
    private fun createDeviceCallback(deviceName: String): ConnectionCallback {
        return object : ConnectionCallback {
            override fun onConnected(extraInfoMap: Map<String, String?>?) {
                LogUtil.d("MultiVSP", "Device $deviceName connected")
            }
            
            override fun onError(code: String, message: String) {
                LogUtil.e("MultiVSP", "Device $deviceName error: $message")
                deviceClients.remove(deviceName)
            }
        }
    }
}
```

## 权限相关的配置

### 1. AndroidManifest.xml配置

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- USB Host功能声明 -->
    <uses-feature 
        android:name="android.hardware.usb.host" 
        android:required="true" />
    
    <!-- USB权限 -->
    <uses-permission android:name="android.permission.USB_PERMISSION" />
    
    <application>
        <!-- USB设备过滤器（可选） -->
        <activity android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
            </intent-filter>
            
            <meta-data 
                android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
                android:resource="@xml/device_filter" />
        </activity>
    </application>
</manifest>
```

### 2. USB设备过滤器（res/xml/device_filter.xml）

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- FTDI设备 -->
    <usb-device vendor-id="1027" product-id="24577" />
    
    <!-- CH340设备 -->
    <usb-device vendor-id="6790" product-id="29987" />
    
    <!-- CP210x设备 -->
    <usb-device vendor-id="4292" />
    
    <!-- 或者使用class匹配所有串口设备 -->
    <usb-device class="255" subclass="255" protocol="255" />
</resources>
```

## 调试和测试

### 1. 权限状态检查

```kotlin
fun debugPermissionStatus(context: Context) {
    val vspClient = VSPClientKernel(appId, appSecret, context)
    val devices = vspClient.getAvailableDevices()
    
    LogUtil.d("VSPDebug", "=== USB Permission Status ===")
    devices.forEach { device ->
        val deviceName = device["deviceName"]
        val hasPermission = vspClient.hasPermissionForDevice(device)
        LogUtil.d("VSPDebug", "Device: $deviceName, Permission: $hasPermission")
    }
}
```

### 2. 权限请求日志

```kotlin
// 在VSPClientKernel中添加详细日志
private fun requestPermissionWithLogging(device: UsbDevice) {
    LogUtil.d(TAG, "=== USB Permission Request ===")
    LogUtil.d(TAG, "Device: ${device.deviceName}")
    LogUtil.d(TAG, "VID/PID: ${String.format("0x%04X/0x%04X", device.vendorId, device.productId)}")
    LogUtil.d(TAG, "Current permission: ${usbManager.hasPermission(device)}")
    
    usbManager.requestPermission(device, permissionPendingIntent)
    
    LogUtil.d(TAG, "Permission request sent")
}
```

## 总结

VSP Client端的权限处理是必需的，主要包括：

1. **自动权限处理**: 在连接时自动请求权限（推荐）
2. **手动权限管理**: 预先申请或批量处理权限
3. **用户体验优化**: 提供友好的权限请求提示
4. **错误处理**: 正确处理权限被拒绝的情况
5. **系统兼容性**: 检查设备是否支持USB Host模式

通过合理的权限处理机制，可以确保VSP Client端能够稳定、安全地访问USB串口设备。