# LAN包结构重组分析

## 当前问题分析

### 散乱的类结构
当前lan包下的类分布混乱，存在以下问题：

1. **多个重复的WebSocketClientKernel实现**
   - `WebSocketClientKernel.kt` - 原始实现
   - `RefactoredWebSocketClientKernel.kt` - 错误的重构版本
   - `CorrectlyRefactoredWebSocketClientKernel.kt` - 正确的重构版本

2. **管理器类分散**
   - `manager/` 目录下有接口定义
   - `manager/impl/` 目录下有实现类
   - 但缺乏统一的组织结构

3. **缺乏清晰的职责划分**
   - 没有明确的客户端/服务端分离
   - 没有按功能模块组织
   - 测试类与实现类混在一起

## 重组方案

### 目标结构
```
com.sunmi.tapro.taplink.communication.lan/
├── client/                    # WebSocket客户端相关
│   ├── WebSocketClientKernel.kt
│   ├── connection/           # 连接管理
│   │   ├── ConnectionManager.kt
│   │   └── impl/
│   │       └── DefaultConnectionManager.kt
│   ├── discovery/            # 服务发现
│   │   ├── ServiceDiscoveryManager.kt
│   │   └── impl/
│   │       └── NsdServiceDiscoveryManager.kt
│   └── heartbeat/            # 心跳管理
│       ├── HeartbeatManager.kt
│       └── impl/
│           └── SmartHeartbeatManager.kt
├── server/                   # WebSocket服务端相关（如果有）
│   └── WebSocketServerKernel.kt
├── protocol/                 # 协议相关
│   ├── WebSocketProtocol.kt
│   └── LanProtocolConverter.kt
├── model/                    # 数据模型
│   ├── ServiceInfo.kt
│   ├── HeartbeatConfig.kt
│   └── ConnectionState.kt
└── util/                     # 工具类
    ├── NetworkQualityMonitor.kt
    └── WebSocketUtils.kt
```

### 重组原则

1. **按功能模块分组**
   - client/ - 客户端功能
   - server/ - 服务端功能
   - protocol/ - 协议处理
   - model/ - 数据模型
   - util/ - 工具类

2. **接口与实现分离**
   - 接口直接放在功能目录下
   - 实现放在impl/子目录下

3. **单一职责原则**
   - 每个类只负责一个明确的功能
   - 避免功能重叠和职责混乱

4. **命名规范**
   - Manager后缀用于管理器类
   - Kernel后缀用于内核类
   - 实现类使用Default前缀

## 具体重组步骤

### 第1步：清理重复类
1. **保留正确的实现**
   - 保留：`CorrectlyRefactoredWebSocketClientKernel.kt`
   - 删除：`RefactoredWebSocketClientKernel.kt`
   - 重命名：`WebSocketClientKernel.kt` → `LegacyWebSocketClientKernel.kt`（作为备份）

2. **重命名正确的实现**
   - `CorrectlyRefactoredWebSocketClientKernel.kt` → `WebSocketClientKernel.kt`

### 第2步：创建功能目录
```bash
mkdir -p client/connection/impl
mkdir -p client/discovery/impl  
mkdir -p client/heartbeat/impl
mkdir -p server
mkdir -p protocol
mkdir -p model
mkdir -p util
```

### 第3步：移动和重组类

#### 连接管理
```
manager/WebSocketConnectionManager.kt → client/connection/ConnectionManager.kt
manager/impl/DefaultWebSocketConnectionManager.kt → client/connection/impl/DefaultConnectionManager.kt
```

#### 服务发现
```
manager/ServiceDiscoveryManager.kt → client/discovery/ServiceDiscoveryManager.kt
# 创建新的实现类
client/discovery/impl/NsdServiceDiscoveryManager.kt
```

#### 心跳管理
```
manager/HeartbeatManager.kt → client/heartbeat/HeartbeatManager.kt
# 创建新的实现类
client/heartbeat/impl/SmartHeartbeatManager.kt
```

#### 数据传输
```
manager/DataTransmissionHandler.kt → client/transmission/DataTransmissionHandler.kt
# 或者合并到ConnectionManager中
```

### 第4步：更新包名和引用
1. 更新所有类的package声明
2. 更新import语句
3. 更新类名引用

### 第5步：创建统一的入口
```kotlin
// client/WebSocketClientKernel.kt
class WebSocketClientKernel : AsyncServiceKernel {
    private val connectionManager: ConnectionManager
    private val discoveryManager: ServiceDiscoveryManager  
    private val heartbeatManager: HeartbeatManager
    // ...
}
```

## 重组后的优势

### 1. 结构清晰
- 按功能模块组织，职责明确
- 客户端/服务端分离
- 接口与实现分离

### 2. 易于维护
- 相关功能集中在一起
- 减少类之间的耦合
- 便于定位和修改

### 3. 易于扩展
- 新功能可以独立添加
- 不影响现有模块
- 支持插件化扩展

### 4. 易于测试
- 每个模块可以独立测试
- 模拟依赖更简单
- 测试覆盖更全面

## 迁移风险控制

### 1. 分阶段迁移
- 先创建新结构，保留旧结构
- 逐步迁移功能模块
- 充分测试后删除旧结构

### 2. 向后兼容
- 保持公共接口不变
- 提供迁移适配器
- 逐步废弃旧接口

### 3. 文档更新
- 更新架构文档
- 提供迁移指南
- 更新使用示例

## 实施时间表

### 第1周：准备阶段
- 创建新的目录结构
- 分析现有类的依赖关系
- 制定详细的迁移计划

### 第2周：核心迁移
- 迁移管理器接口和实现
- 更新WebSocketClientKernel
- 基础功能测试

### 第3周：完善和测试
- 完善所有功能模块
- 全面测试验证
- 性能测试和优化

### 第4周：清理和文档
- 删除旧的重复类
- 更新相关文档
- 代码审查和优化

这个重组方案将显著改善lan包的结构，使其更加清晰、易维护和易扩展。