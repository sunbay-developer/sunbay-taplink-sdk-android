# USB Accessory-Only 模式设置指南

## 概述

将 Android 设备（Accessory 端）的 USB functions 设置为 `accessory-only` 模式（不带 adb），用于测试 USB AOA 协议在纯 Accessory 模式下的工作表现。

## 设置方法

### 方法 1: 通过 ADB 命令（推荐）

在设备上通过 ADB shell 执行以下命令：

```bash
# 1. 进入 root 权限（如果设备已 root）
adb shell
su

# 2. 设置 USB functions 为 accessory-only（临时，重启后恢复）
setprop sys.usb.config accessory

# 3. 或者设置为持久化（重启后仍然有效）
setprop persist.sys.usb.config accessory

# 4. 验证设置是否生效
getprop sys.usb.config
# 应该输出: accessory

getprop persist.sys.usb.config
# 应该输出: accessory
```

### 方法 2: 使用 svc 命令

```bash
adb shell
su
svc usb setFunctions accessory
```

### 方法 3: 通过系统设置（如果设备支持）

某些设备可能支持通过系统设置界面修改 USB 配置：
1. 进入 **设置** > **开发者选项** > **USB 配置**
2. 选择 **仅配件模式** 或 **Accessory only**

## 恢复 ADB 模式

测试完成后，如果需要恢复 ADB 功能：

```bash
adb shell
su

# 恢复为 adb 模式
setprop sys.usb.config adb
setprop persist.sys.usb.config adb

# 或者恢复为 adb + accessory 模式
setprop sys.usb.config adb,accessory
setprop persist.sys.usb.config adb,accessory
```

## 注意事项

1. **设置后 ADB 连接会断开**：设置为 `accessory-only` 后，ADB 功能会被禁用，无法通过 USB 进行 ADB 调试。
2. **需要其他方式连接**：如果设备支持，可以通过 WiFi ADB 或其他方式连接进行调试。
3. **重启后恢复**：如果只设置了 `sys.usb.config`（非持久化），设备重启后会恢复默认配置。
4. **权限要求**：修改 USB functions 通常需要 root 权限。

## 验证设置

设置完成后，可以通过以下方式验证：

1. **检查系统属性**：
   ```bash
   getprop sys.usb.config
   getprop persist.sys.usb.config
   ```

2. **检查 USB 设备列表**：
   ```bash
   lsusb  # 在 Host 端执行
   # 应该看到设备以 Accessory 模式出现
   ```

3. **检查日志**：
   ```bash
   adb logcat | grep -i usb
   # 查看 USB 相关的日志，确认模式切换
   ```

## 测试流程

1. **设置 Accessory 端为 accessory-only 模式**
2. **连接 USB 线缆**
3. **在 Host 端启动连接流程**
4. **观察日志和连接行为**
5. **测试完成后恢复 ADB 模式**

## 常见问题

### Q: 设置后无法通过 USB 连接 ADB？
A: 这是正常的，因为 `accessory-only` 模式禁用了 ADB 功能。可以通过 WiFi ADB 或其他方式连接。

### Q: 如何在不重启的情况下恢复 ADB？
A: 执行 `setprop sys.usb.config adb` 即可恢复，但需要 root 权限。

### Q: 设备不支持修改 USB functions？
A: 某些设备可能锁定了 USB functions 配置，需要检查设备是否支持开发者选项中的 USB 配置修改。

## 相关代码位置

- **Accessory 端实现**: `lib_taplink_service/src/main/java/com/sunmi/tapro/taplink/service/protocol/usb/UsbStandardAccessoryKernel.kt`
- **Host 端实现**: `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/cable/aoa/host/UsbStandardHostKernel.kt`


