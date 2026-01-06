# AOA数据传输错误修复

## 问题描述

Host端连接成功后，发送INIT命令时出现数据传输失败：

```
AOA data send failed: sent=-1, expected=244
INIT command failed: code=SEND_FAILED, msg=Failed to send data to AOA device
```

## 问题分析

`bulkTransfer`返回-1表示USB传输失败，可能的原因：

1. **USB端点状态不正确**
2. **设备连接状态异常**
3. **接口未正确声明**
4. **端点配置错误**
5. **设备已断开连接**
6. **Host端和Accessory端数据传输方式不同**

## 关键发现

Host端和Accessory端使用不同的数据传输方式：

- **Accessory端**：使用`FileInputStream/FileOutputStream`（基于文件描述符）
- **Host端**：使用`bulkTransfer`（直接USB传输）

BaseUsbStandardKernel中的`startDataReceive`方法只适用于Accessory端，Host端需要重写。

## 修复方案

### 1. 增强连接诊断

在`connectToAoaDeviceDirectly`方法中添加详细的设备和端点信息：

```kotlin
LogUtil.d(TAG, "Device info: VID=${String.format("0x%04X", device.vendorId)}, PID=${String.format("0x%04X", device.productId)}")
LogUtil.d(TAG, "Device opened successfully, interface count: ${device.interfaceCount}")

// 打印所有接口和端点信息
for (i in 0 until device.interfaceCount) {
    val usbInterface = device.getInterface(i)
    LogUtil.d(TAG, "Interface[$i]: class=${usbInterface.interfaceClass}, subclass=${usbInterface.interfaceSubclass}")
    
    for (j in 0 until usbInterface.endpointCount) {
        val endpoint = usbInterface.getEndpoint(j)
        LogUtil.d(TAG, "  Endpoint[$j]: address=${String.format("0x%02X", endpoint.address)}, " +
                "type=${endpoint.type}, direction=${if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"}")
    }
}
```

### 2. 改进端点选择逻辑

确保找到正确的Bulk OUT端点：

```kotlin
if (outEndpoint == null) {
    connection.releaseInterface(aoaInterface)
    connection.close()
    throw Exception("No bulk OUT endpoint found on AOA device")
}
```

### 3. 重写Host端数据接收方法

Host端需要使用bulkTransfer而不是FileInputStream：

```kotlin
/**
 * 重写数据接收方法 - Host端使用Bulk传输接收数据
 */
override fun startDataReceive() {
    dataReceiveJob?.cancel()
    dataReceiveJob = scope.launch {
        val buffer = ByteArray(16384)

        LogUtil.d(TAG, "Starting AOA data receive loop (Host端 Bulk传输)...")
        
        while (isActive && currentInnerConnectionStatus == InnerConnectionStatus.CONNECTED &&
            usbDeviceConnection != null && usbEndpointIn != null
        ) {
            try {
                val bytesRead = usbDeviceConnection!!.bulkTransfer(
                    usbEndpointIn, buffer, buffer.size, 1000
                )

                when {
                    bytesRead > 0 -> {
                        val data = buffer.copyOf(bytesRead)
                        LogUtil.d(TAG, "AOA data received (Host端): $bytesRead bytes")
                        dataReceiver?.invoke(data)
                    }
                    bytesRead == -1 -> {
                        LogUtil.w(TAG, "AOA bulk transfer error, stopping receive loop")
                        break
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error receiving AOA data (Host端): ${e.message}")
                break
            }
        }
    }
}
```

### 4. 增强数据发送诊断

在`sendDataToAoaDevice`方法中添加详细的状态检查：

```kotlin
// 检查设备连接状态
val deviceList = usbManager.deviceList
val isDeviceStillConnected = deviceList.values.any { it.deviceName == connectedDevice!!.deviceName }

if (!isDeviceStillConnected) {
    LogUtil.e(TAG, "AOA device is no longer connected")
    return false
}

// 检查接口是否仍然被声明
if (usbInterface != null) {
    val isInterfaceClaimed = try {
        usbDeviceConnection!!.claimInterface(usbInterface, true)
    } catch (e: Exception) {
        LogUtil.w(TAG, "Failed to claim interface: ${e.message}")
        false
    }
    
    if (!isInterfaceClaimed) {
        LogUtil.e(TAG, "USB interface is not claimed")
        return false
    }
}
```

### 5. 详细的错误分析

根据`bulkTransfer`返回值提供具体的错误信息：

```kotlin
when {
    result == data.size -> {
        LogUtil.d(TAG, "AOA data sent successfully: ${data.size} bytes")
        true
    }
    result > 0 -> {
        LogUtil.w(TAG, "AOA data partially sent: sent=$result, expected=${data.size}")
        false
    }
    result == 0 -> {
        LogUtil.e(TAG, "AOA data send timeout: no data sent")
        false
    }
    result == -1 -> {
        LogUtil.e(TAG, "AOA data send failed: USB transfer error")
        LogUtil.e(TAG, "Possible causes:")
        LogUtil.e(TAG, "  1. Device disconnected")
        LogUtil.e(TAG, "  2. Interface not properly claimed")
        LogUtil.e(TAG, "  3. Endpoint not available")
        LogUtil.e(TAG, "  4. USB communication error")
        false
    }
}
```

### 6. 修改BaseUsbStandardKernel

将startDataReceive方法改为可重写：

```kotlin
/**
 * 启动数据接收（可被子类重写）
 */
protected open fun startDataReceive() {
    // Accessory端实现（使用FileInputStream）
}
```

## 测试验证

### 预期日志输出

连接成功时应该看到：

```
Device info: VID=0x18D1, PID=0x2D00
Device opened successfully, interface count: 1
Interface[0]: class=255, subclass=255, protocol=0
  Endpoint[0]: address=0x81, type=2, direction=IN
  Endpoint[1]: address=0x02, type=2, direction=OUT
Selected interface[0] for AOA communication
Interface claimed successfully
Found IN endpoint: 0x81
Found OUT endpoint: 0x02
AOA device connected successfully
Starting AOA data receive loop (Host端 Bulk传输)...
```

数据发送成功时应该看到：

```
Sending data to AOA device:
  Device: /dev/bus/usb/001/002
  Data size: 244 bytes
  Endpoint: 2 (maxPacketSize: 512)
  Interface: 0
AOA data sent successfully: 244 bytes
```

### 故障排除

如果仍然出现传输失败：

1. **检查设备兼容性**：确认Target设备正确实现了AOA协议
2. **检查USB线缆**：使用支持数据传输的USB线缆
3. **检查权限**：确认Host端有正确的USB设备权限
4. **检查系统版本**：某些Android版本可能有AOA实现差异
5. **检查端点配置**：确认找到了正确的Bulk IN/OUT端点

## 总结

通过区分Host端和Accessory端的数据传输方式，重写Host端的数据接收方法，并增强诊断信息，可以更好地定位和解决AOA数据传输问题。关键是：

1. **Host端使用bulkTransfer**，Accessory端使用FileInputStream/OutputStream
2. 正确识别和配置USB端点
3. 接口被正确声明和维护
4. 设备连接状态实时监控
5. 提供详细的错误信息用于调试