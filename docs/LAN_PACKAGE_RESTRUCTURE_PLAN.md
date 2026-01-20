# LAN包重新划分方案

## 当前问题分析

### 现有结构问题
```
lan/
├── client/                                     
│   ├── WebSocketClientKernel.kt              
│   ├── connection/                            
│   │   ├── ConnectionManager.kt              
│   │   └── impl/DefaultConnectionManager.kt   
│   ├── discovery/                             
│   │   ├── ServiceDiscoveryManager.kt        
│   │   └── impl/ 
│   └── heartbeat/                             
│       ├── HeartbeatManager.kt               
│       └── impl/ 
└── model/                                      
    └── ServiceInfo.kt                         
```

**问题**：
1. **client/** 目录命名不准确 - WebSocket不仅仅是客户端
2. **功能模块分散** - connection、discovery、heartbeat分散在不同目录
3. **层次过深** - 三层嵌套导致包名过长
4. **命名不一致** - 有些用Manager，有些没有统一规范

## 重新划分方案

### 方案1：按功能模块划分（推荐）
```
lan/
├── websocket/                    # WebSocket相关
│   ├── WebSocketClientKernel.kt
│   ├── connection/              # 连接管理
│   │   ├── ConnectionManager.kt
│   │   └── DefaultConnectionManager.kt
│   └── heartbeat/               # 心跳管理
│       └── HeartbeatManager.kt
├── discovery/                   # 服务发现（独立模块）
│   ├── ServiceDiscoveryManager.kt
│   └── NsdServiceDiscoveryManager.kt
└── model/                       # 数据模型
    └── ServiceInfo.kt
```

### 方案2：扁平化结构
```
lan/
├── WebSocketClientKernel.kt     # 主要内核
├── ConnectionManager.kt          # 连接管理接口
├── DefaultConnectionManager.kt  # 连接管理实现
├── ServiceDiscoveryManager.kt   # 服务发现接口
├── NsdServiceDiscoveryManager.kt # 服务发现实现
├── HeartbeatManager.kt          # 心跳管理
└── ServiceInfo.kt               # 数据模型
```

### 方案3：按职责层次划分
```
lan/
├── kernel/                      # 内核层
│   └── WebSocketClientKernel.kt
├── manager/                     # 管理器层
│   ├── ConnectionManager.kt
│   ├── ServiceDiscoveryManager.kt
│   └── HeartbeatManager.kt
├── impl/                        # 实现层
│   ├── DefaultConnectionManager.kt
│   └── NsdServiceDiscoveryManager.kt
└── model/                       # 模型层
    └── ServiceInfo.kt
```

## 推荐方案：方案1（按功能模块划分）

### 优势
1. **功能聚合**: 相关功能集中在一起
2. **层次合理**: 两层结构，不会过深
3. **扩展性好**: 可以独立添加新的功能模块
4. **命名清晰**: 每个模块职责明确

### 详细结构
```
com.sunmi.tapro.taplink.communication.lan/
├── websocket/                              # WebSocket功能模块
│   ├── WebSocketClientKernel.kt          # WebSocket客户端内核
│   ├── connection/                        # 连接管理子模块
│   │   ├── ConnectionManager.kt          # 连接管理接口
│   │   └── DefaultConnectionManager.kt   # 默认连接管理实现
│   └── heartbeat/                         # 心跳管理子模块
│       └── HeartbeatManager.kt           # 心跳管理器
├── discovery/                             # 服务发现模块
│   ├── ServiceDiscoveryManager.kt        # 服务发现接口
│   └── NsdServiceDiscoveryManager.kt     # NSD服务发现实现
└── model/                                 # 数据模型模块
    └── ServiceInfo.kt                     # 服务信息模型
```

## 命名规范

### 1. 包命名规范
- **websocket** - WebSocket相关功能
- **discovery** - 服务发现功能
- **model** - 数据模型
- **connection** - 连接管理（websocket子模块）
- **heartbeat** - 心跳管理（websocket子模块）

### 2. 类命名规范
- **Kernel** - 内核类，如 `WebSocketClientKernel`
- **Manager** - 管理器类，如 `ConnectionManager`、`HeartbeatManager`
- **Default前缀** - 默认实现，如 `DefaultConnectionManager`
- **具体技术前缀** - 特定技术实现，如 `NsdServiceDiscoveryManager`

### 3. 接口vs实现
- **接口**: 直接使用功能名，如 `ConnectionManager`
- **默认实现**: `Default` + 接口名，如 `DefaultConnectionManager`
- **特定实现**: 技术名 + 接口名，如 `NsdServiceDiscoveryManager`

## 迁移计划

### 第1步：创建新的目录结构
```bash
mkdir -p websocket/connection
mkdir -p websocket/heartbeat
mkdir -p discovery
# model目录已存在
```

### 第2步：移动和重命名文件
```
client/WebSocketClientKernel.kt → websocket/WebSocketClientKernel.kt
client/connection/ConnectionManager.kt → websocket/connection/ConnectionManager.kt
client/connection/impl/DefaultConnectionManager.kt → websocket/connection/DefaultConnectionManager.kt
client/discovery/ServiceDiscoveryManager.kt → discovery/ServiceDiscoveryManager.kt
client/heartbeat/HeartbeatManager.kt → websocket/heartbeat/HeartbeatManager.kt
model/ServiceInfo.kt → model/ServiceInfo.kt (保持不变)
```

### 第3步：更新包声明和导入
- 更新所有文件的package声明
- 更新import语句
- 更新类引用

### 第4步：清理旧目录
- 删除client目录及其子目录
- 确保没有遗留文件

## 预期效果

### 1. 包名简化
**之前**:
```kotlin
com.sunmi.tapro.taplink.communication.lan.client.connection.impl.DefaultConnectionManager
```

**之后**:
```kotlin
com.sunmi.tapro.taplink.communication.lan.websocket.connection.DefaultConnectionManager
```

### 2. 导入简化
**之前**:
```kotlin
import com.sunmi.tapro.taplink.communication.lan.client.connection.ConnectionManager
import com.sunmi.tapro.taplink.communication.lan.client.heartbeat.HeartbeatManager
import com.sunmi.tapro.taplink.communication.lan.client.discovery.ServiceDiscoveryManager
```

**之后**:
```kotlin
import com.sunmi.tapro.taplink.communication.lan.websocket.connection.ConnectionManager
import com.sunmi.tapro.taplink.communication.lan.websocket.heartbeat.HeartbeatManager
import com.sunmi.tapro.taplink.communication.lan.discovery.ServiceDiscoveryManager
```

### 3. 结构更清晰
- **websocket/** - 所有WebSocket相关功能
- **discovery/** - 独立的服务发现功能
- **model/** - 共享的数据模型

## 扩展性考虑

### 未来可能的扩展
```
lan/
├── websocket/                    # WebSocket功能模块
│   ├── WebSocketClientKernel.kt
│   ├── WebSocketServerKernel.kt  # 未来可能的服务端
│   ├── connection/
│   └── heartbeat/
├── discovery/                    # 服务发现模块
│   ├── ServiceDiscoveryManager.kt
│   ├── NsdServiceDiscoveryManager.kt
│   └── BonjourServiceDiscoveryManager.kt  # iOS支持
├── protocol/                     # 协议模块（未来）
│   ├── LanProtocolParser.kt
│   └── WebSocketProtocolHandler.kt
└── model/                        # 数据模型模块
    ├── ServiceInfo.kt
    └── ConnectionInfo.kt         # 未来可能的模型
```

## 总结

这个重新划分方案：

1. **功能聚合**: WebSocket相关功能集中在websocket包下
2. **层次合理**: 避免过深的嵌套结构
3. **命名清晰**: 包名和类名都有明确的含义
4. **扩展友好**: 可以方便地添加新的功能模块
5. **维护简单**: 相关功能集中，便于维护

这是一个更加合理和可维护的包结构设计。