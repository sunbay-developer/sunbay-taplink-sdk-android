# AOA协议中Host和Target的角色区分

## 重要概念澄清

### Host端和Target端的不同职责

#### Host端（发送AOA协议的设备）
**职责**:
- 发送AOA协议命令（GET_PROTOCOL, SEND_STRING, START）
- 检测AOA设备的出现（VID: 0x18D1, PID: 0x2D00-0x2D05）
- **不能直接看到Accessory信息**

**能看到的**:
- 原始USB设备（发送AOA命令前）
- AOA设备（发送START命令后，设备重新枚举）

**看不到的**:
- Accessory信息（manufacturer, model等）
- Target端的应用状态

#### Target端（响应AOA协议的设备）
**职责**:
- 响应AOA协议命令（系统级处理）
- 切换到Accessory模式
- 检测并连接到Accessory
- **能看到完整的Accessory信息**

**能看到的**:
- Accessory信息（manufacturer, model, description等）
- 自己作为Accessory的状态

## 之前的错误逻辑

### Host端错误地尝试检查accessoryList
```kotlin
// ❌ 错误：Host端不应该能看到Accessory信息
val accessory = usbManager.accessoryList?.find { acc ->
    acc.manufacturer == usbStandardInfo.manufacturer && 
    acc.model == usbStandardInfo.model
}
```

**为什么错误**:
- Host端的`accessoryList`应该是空的
- Accessory信息只在Target端可见
- Host端只能通过VID/PID检测AOA设备

## 正确的逻辑

### Host端应该做的
```kotlin
// ✅ 正确：检测AOA设备的出现
val aoaDevice = usbManager.deviceList.values.find { device ->
    UsbStandardProtocol.isAoaCompatible(device.vendorId, device.productId)
}

if (aoaDevice != null) {
    // AOA设备已出现，等待广播接收器处理连接
    LogUtil.d(TAG, "AOA device detected, waiting for connection...")
}
```

### Target端应该做的
```kotlin
// ✅ 正确：检测并连接Accessory
val accessory = usbManager.accessoryList?.find { acc ->
    acc.manufacturer == "SUNMI" && acc.model == "TAPLINK_SDK"
}

if (accessory != null) {
    // 连接到Accessory
    connectToAccessory(accessory, callback)
}
```

## AOA连接的完整流程

### 1. Host端发送AOA协议
```
Host -> Target: GET_PROTOCOL
Host -> Target: SEND_STRING[0-5]
Host -> Target: START
```

### 2. Target设备重新枚举
```
Target设备: 断开 -> 重新枚举为AOA设备
```

### 3. Host端检测AOA设备
```
Host端: 检测到VID=0x18D1, PID=0x2D00的设备
```

### 4. Target端检测Accessory
```
Target端: 检测到Accessory信息并连接
```

### 5. 连接建立（可选）
```
如果需要Host端也连接：
Target端 -> Host端: 发送广播
Host端: 接收广播并连接
```

## 关键理解

### Host端的限制
- **只能看到设备级信息**（VID/PID）
- **不能直接访问Accessory信息**
- **依赖广播机制**进行连接

### Target端的能力
- **能看到完整Accessory信息**
- **直接连接Accessory**
- **控制连接状态**

### 连接方式
1. **单向连接**: 只有Target端连接（常见）
2. **双向连接**: Target端连接后，通过广播让Host端也连接

## 修复后的效果

现在Host端会：
1. ✅ 正确检测AOA设备出现
2. ✅ 等待广播接收器处理连接
3. ✅ 不依赖不存在的accessoryList
4. ✅ 提供准确的超时诊断

这样的逻辑更符合AOA协议的实际工作方式。