# AOA连接故障排除步骤

## 当前状态分析

从Host端日志可以看出：
- ✅ AOA协议命令发送成功
- ✅ 设备正确断开并重新连接
- ❌ 重新连接后仍然是普通USB设备，不是Accessory

## 可能的原因

### 1. Target设备没有运行AOA代码
**检查**: Target设备上是否有对应的日志输出？
**预期日志**: 
```
Target device waiting for Host to initiate AOA protocol...
Current accessory list size: 0
```

### 2. Target设备不支持AOA协议
**检查**: Target设备的硬件和Android版本
**要求**: 
- Android 3.1+ (API 12+)
- 支持USB OTG功能
- 设备制造商支持AOA

### 3. AOA字符串不匹配或格式错误
**检查**: Host发送的字符串是否正确
**当前发送**:
- SUNMI (6 bytes) ✅
- TAPLINK_SDK (12 bytes) ✅ 
- Taplink Communication SDK (26 bytes) ✅
- 1.0 (4 bytes) ✅
- https://www.sunmi.com (22 bytes) ✅
- Taplink001 (11 bytes) ✅

### 4. 设备驱动或系统问题
**检查**: Android系统是否正确处理AOA协议
**可能原因**:
- USB驱动问题
- 系统权限问题
- 设备特定的AOA实现问题

## 故障排除步骤

### 步骤1: 确认Target设备状态
1. 检查Target设备是否运行了UsbStandardAccessoryKernel
2. 查看Target设备的日志输出
3. 确认Target设备的Android版本和AOA支持

### 步骤2: 使用标准AOA测试
1. 使用Google官方的AOA示例应用测试
2. 确认设备间的基本AOA连接可以工作
3. 排除硬件兼容性问题

### 步骤3: 检查设备信息
重新测试后，查看详细的设备信息：
```
Device[0]: /dev/bus/usb/001/017
  VID: 0x?????, PID: 0x?????
  Class: ?, Subclass: ?, Protocol: ?
  Interfaces: ?
```

### 步骤4: 验证AOA协议实现
1. 确认GET_PROTOCOL返回正确版本
2. 验证SEND_STRING命令格式
3. 检查START命令执行

## 调试命令

### 查看USB设备信息
```bash
adb shell lsusb
adb shell cat /proc/bus/usb/devices
```

### 监控USB事件
```bash
adb logcat | grep -E "(USB|AOA|Accessory)"
```

### 检查系统USB服务
```bash
adb shell dumpsys usb
```

## 可能的解决方案

### 解决方案1: 检查Target设备实现
确保Target设备正确运行了UsbStandardAccessoryKernel：
```kotlin
val accessoryKernel = UsbStandardAccessoryKernel(
    context = context,
    usbStandardInfo = UsbStandardProtocol.UsbStandardInfo(),
    permissionAction = UsbStandardAccessoryKernel.ACTION_USB_STANDARD_ACCESSORY_KERNEL,
    permissionPendingIntent = UsbStandardAccessoryKernel.createRequestPermissionPendingIntent(context)
)

accessoryKernel.connect("usb://") { success, error ->
    // 处理连接结果
}
```

### 解决方案2: 尝试不同的设备组合
- 使用已知支持AOA的设备进行测试
- 尝试不同的Android版本
- 检查设备制造商的AOA支持

### 解决方案3: 检查系统配置
- 确认USB调试已启用
- 检查开发者选项中的USB配置
- 验证USB连接模式

## 下一步行动

1. **获取Target设备日志**: 确认Target设备是否收到AOA协议
2. **检查设备详细信息**: 查看重新枚举后的设备VID/PID
3. **尝试标准AOA测试**: 使用官方示例验证硬件兼容性
4. **分析系统日志**: 查看Android系统级的USB事件

## 预期结果

如果一切正常，应该看到：
```
Host端:
- AOA START command sent successfully
- Device enumeration changed after START command
- Device[0]: VID: 0x18D1, PID: 0x2D00 (Google AOA)
- Accessory found: SUNMI TAPLINK_SDK

Target端:
- System switched to Accessory mode: SUNMI TAPLINK_SDK
- USB accessory connected: SUNMI TAPLINK_SDK
```