# Host端Accessory广播接收测试

## 问题分析

Host和Target在不同设备上，但Host端没有收到Accessory连接的广播。

## 可能的原因

### 1. 广播接收器导出设置错误 ✅ 已修复
**问题**: 使用了`RECEIVER_NOT_EXPORTED`，可能阻止接收系统广播
**修复**: 改为`RECEIVER_EXPORTED`以接收系统USB广播

### 2. 广播接收器没有正确注册
**检查**: 查看注册日志是否成功

### 3. 时序问题
**现象**: Target端在Host端轮询结束后才连接
**解决**: 增加了详细的广播日志来跟踪

## 修复内容

### 1. 增强广播接收器日志
```kotlin
LogUtil.d(TAG, "=== ACCESSORY BROADCAST RECEIVED ===")
LogUtil.d(TAG, "Action: ${intent.action}")
LogUtil.d(TAG, "Processing USB_ACCESSORY_ATTACHED broadcast")
```

### 2. 修复广播接收器导出设置
```kotlin
ContextCompat.registerReceiver(
    context,
    accessoryReceiver,
    filter,
    ContextCompat.RECEIVER_EXPORTED  // 改为EXPORTED
)
```

### 3. 增强Host端回调日志
```kotlin
LogUtil.d(TAG, "=== HOST RECEIVED ACCESSORY ATTACHED BROADCAST ===")
LogUtil.d(TAG, "Host is in correct state for Accessory connection")
```

## 测试步骤

### 1. 重新测试连接
运行Host和Target应用，观察日志输出

### 2. 检查广播接收器注册
Host端应该看到：
```
USB accessory receiver registered (EXPORTED)
```

### 3. 监控广播接收
当Target端连接成功时，Host端应该看到：
```
=== ACCESSORY BROADCAST RECEIVED ===
Action: android.hardware.usb.action.USB_ACCESSORY_ATTACHED
Processing USB_ACCESSORY_ATTACHED broadcast
USB accessory attached via broadcast: SUNMI TAPLINK_SDK
=== HOST RECEIVED ACCESSORY ATTACHED BROADCAST ===
```

### 4. 验证连接建立
Host端应该最终看到：
```
Host is in correct state for Accessory connection
Proceeding to connect to Accessory from Host side...
USB accessory connected: SUNMI TAPLINK_SDK
Status changed: WAITING_CONNECT -> CONNECTED
```

## 预期结果

修复后的完整流程：

### Host端日志：
```
1. AOA协议发送成功
2. 设备重新枚举
3. 开始轮询Accessory
4. 收到ACCESSORY_ATTACHED广播 ← 新增
5. 连接到Accessory成功 ← 新增
6. 状态变为CONNECTED ← 新增
```

### Target端日志：
```
1. 检测到Accessory
2. 请求权限成功
3. 连接建立成功
4. 状态变为CONNECTED
```

## 如果仍然有问题

### 检查项目：
1. **权限问题**: 确认应用有USB权限
2. **系统版本**: 确认Android版本支持AOA
3. **硬件支持**: 确认设备支持USB AOA协议
4. **USB连接**: 确认USB线连接正常

### 调试命令：
```bash
# 监控系统USB事件
adb logcat | grep -E "(USB|AOA|Accessory|android.hardware.usb)"

# 检查USB设备状态
adb shell dumpsys usb

# 查看USB设备列表
adb shell lsusb
```

## 关键修复点

最重要的修复是将广播接收器改为`RECEIVER_EXPORTED`，这允许接收系统级的USB广播。之前使用`RECEIVER_NOT_EXPORTED`可能阻止了Host端接收`ACTION_USB_ACCESSORY_ATTACHED`广播。

现在重新测试应该能看到Host端正确接收广播并建立连接。