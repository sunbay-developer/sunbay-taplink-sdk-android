# Protocol Package

协议处理相关类的统一包，包含所有协议解析、构建和管理功能。

## 包结构

```
com.sunmi.tapro.taplink.communication.protocol/
├── ProtocolConstants.kt    # 协议常量定义和基础构建方法
├── ProtocolParser.kt       # 协议解析器和解析结果定义
├── ProtocolManager.kt      # 统一协议管理器（推荐使用）
└── README.md              # 本说明文档
```

## 类说明

### ProtocolConstants.kt
- **功能**：定义所有支持的协议前缀和格式常量
- **包含**：协议前缀常量、默认协议字符串、基础协议构建方法
- **使用场景**：需要协议常量或基础构建方法时

### ProtocolParser.kt
- **功能**：协议字符串解析和解析结果定义
- **包含**：`ProtocolParseResult` 密封类、`ProtocolParser` 解析器
- **使用场景**：需要解析协议字符串时（建议使用 ProtocolManager）

### ProtocolManager.kt ⭐️
- **功能**：统一的协议管理器，整合解析和构建功能
- **包含**：协议解析、构建、验证、转换、信息提取等全部功能
- **使用场景**：所有协议相关操作（推荐统一使用此类）

## 使用建议

### 推荐方式
```kotlin
// 使用统一的 ProtocolManager
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager

// 解析协议
val result = ProtocolManager.parseProtocol("wss://192.168.1.100:8443")

// 构建协议
val protocol = ProtocolManager.buildLanProtocol("192.168.1.100", 8443, secure = true)

// 验证协议
val isValid = ProtocolManager.isValidProtocol(protocol)
```

### 兼容方式
```kotlin
// 直接使用解析器（兼容旧代码）
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParser

val result = ProtocolParser.parse("wss://192.168.1.100:8443")
```

## 支持的协议格式

- **本地服务**：`local://packageName/action`
- **局域网**：`wss://host:port` 或 `ws://host:port`
- **USB**：`usb://host/device` 或 `usb://accessory`
- **虚拟串口**：`vsp://baudRate/dataBits/parity/stopBits`
- **串口**：`serial://devicePath:baudRate`
- **RS232**：`rs232://devicePath:baudRate`
- **自动检测**：`auto://`

## 迁移指南

如果您的代码之前使用了 `util` 包中的协议类，请更新导入语句：

```kotlin
// 旧导入
import com.sunmi.tapro.taplink.communication.util.ProtocolManager
import com.sunmi.tapro.taplink.communication.util.ProtocolParser
import com.sunmi.tapro.taplink.communication.util.ProtocolParseResult

// 新导入
import com.sunmi.tapro.taplink.communication.protocol.ProtocolManager
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParser
import com.sunmi.tapro.taplink.communication.protocol.ProtocolParseResult
```

## 设计原则

1. **统一入口**：ProtocolManager 作为协议操作的统一入口
2. **功能完整**：涵盖协议的解析、构建、验证、转换等所有需求
3. **向后兼容**：保持现有 API 的兼容性
4. **易于扩展**：新增协议类型时只需修改此包中的类
5. **类型安全**：使用密封类确保协议解析结果的类型安全