# 架构流程分析

## 用户观察总结

用户提出了几个重要的架构观察：

1. **SDK层绕不开TaplinkServiceKernel**
2. **handleServiceAddressChange 抛给SDK层处理，SDK层调用TaplinkServiceKernel**
3. **disconnectOnHeartbeatFailure 等待下一次发现，也是抛给SDK层**

## 架构流程分析

### 1. 服务地址变化处理流程

```
服务发现检测到地址变化
  └─> LanClientKernel.callbackManager.notifyServiceAddressChanged()
      └─> ConnectionCallback.onServiceAddressChanged() (SDK ConnectionManager实现)
          └─> ConnectionManager.onServiceAddressChanged()
              ├─> 设备身份验证
              ├─> 地址变化分析
              ├─> 连接状态分析
              └─> executeReconnection()
                  └─> ReconnectManager.onAddressChanged()
                      └─> reconnectAction(config, listener)
                          └─> ConnectionManager.connectInternal()
                              └─> TaplinkServiceKernel.connect()
                                  └─> LanClientKernel.connect()
```

**关键点**：
- ✅ `notifyServiceAddressChanged()` 确实抛给SDK层处理
- ✅ SDK层的 `onServiceAddressChanged()` 实现复杂的决策逻辑
- ✅ 最终通过 `reconnectAction` 调用 `ConnectionManager.connectInternal()`
- ✅ `connectInternal()` 调用 `TaplinkServiceKernel.connect()`
- ✅ **SDK层确实绕不开TaplinkServiceKernel**

### 2. 心跳失败处理流程

```
心跳连续失败
  └─> HeartbeatManager.onHeartbeatFailed()
      └─> LanClientKernel.disconnectOnHeartbeatFailure()
          ├─> 停止心跳
          ├─> 更新状态为 ERROR
          ├─> 通知回调管理器连接错误
          ├─> 断开WebSocket连接
          └─> **保持服务发现运行**
          
服务发现检测到新服务
  └─> LanClientKernel.onServiceFound()
      └─> callbackManager.notifyServiceAddressChanged()
          └─> ConnectionManager.onServiceAddressChanged()
              └─> (同上，最终调用TaplinkServiceKernel.connect())
```

**关键点**：
- ✅ `disconnectOnHeartbeatFailure()` 只断开连接，**不停止服务发现**
- ✅ 等待下一次服务发现触发重连
- ✅ 服务发现触发后，通过 `notifyServiceAddressChanged()` 抛给SDK层
- ✅ SDK层通过 `TaplinkServiceKernel.connect()` 重新连接
- ✅ **确实是通过SDK层处理，绕不开TaplinkServiceKernel**

### 3. reconnectAction 的实现

```kotlin
// ConnectionManager.kt
reconnectManager = ReconnectManager(
    context,
    reconnectAction = { config, listener ->
        // 这个 lambda 就是 reconnectAction
        connectInternal(config, listener)
    }
)

// connectInternal 最终会调用
TaplinkServiceKernel.getInstance()?.connect(protocol, appId, appSecretKey, connectionCallback)
```

**关键点**：
- ✅ `reconnectAction` 是 `ConnectionManager.connectInternal()` 的引用
- ✅ `connectInternal()` 最终调用 `TaplinkServiceKernel.connect()`
- ✅ **所有重连都通过TaplinkServiceKernel**

## 架构设计分析

### 为什么SDK层绕不开TaplinkServiceKernel？

1. **统一接口**：
   - `TaplinkServiceKernel` 提供统一的连接接口
   - 支持多种连接方式（LAN、USB、Serial、VSP、Local）
   - SDK层不需要关心底层实现细节

2. **状态管理**：
   - `TaplinkServiceKernel` 管理当前活跃的 kernel
   - 确保同时只有一个连接
   - 处理 kernel 切换逻辑

3. **协议解析**：
   - `TaplinkServiceKernel` 负责协议解析
   - 根据协议类型选择合适的 kernel
   - SDK层只需要提供协议字符串

### 为什么服务地址变化和心跳失败都抛给SDK层？

1. **业务决策**：
   - SDK层有更完整的上下文信息（设备ID、连接配置等）
   - 可以做出更智能的重连决策
   - 考虑用户配置、重连策略等

2. **状态一致性**：
   - SDK层的 `ConnectionManager` 维护连接状态
   - 需要统一管理连接、重连、断开等操作
   - 避免状态不一致

3. **用户体验**：
   - SDK层可以通知应用层连接状态变化
   - 提供重连进度、错误信息等
   - 支持重连配置和策略

## 架构优势

### 1. 职责分离
- **Communication层**：负责底层通信（WebSocket、USB等）
- **SDK层**：负责业务逻辑和决策
- **TaplinkServiceKernel**：作为中间层，统一接口

### 2. 可扩展性
- 新增连接方式只需实现 `IServiceKernel`
- SDK层无需修改
- 通过 `TaplinkServiceKernel` 统一管理

### 3. 状态一致性
- 所有连接操作都通过 `TaplinkServiceKernel`
- 确保状态同步
- 避免并发问题

## 潜在优化点

### 1. 减少回调层级
当前回调层级较深：
```
LanClientKernel -> ConnectionCallback -> ConnectionManager -> ReconnectManager -> TaplinkServiceKernel -> LanClientKernel
```

可以考虑：
- 直接通知 `TaplinkServiceKernel`，减少中间层
- 但会破坏职责分离，需要权衡

### 2. 心跳失败后的重连策略
当前策略：
- 断开连接，保持服务发现
- 等待服务发现触发重连

可以考虑：
- 心跳失败后立即尝试重连（如果知道地址）
- 但需要权衡网络开销和成功率

## 总结

用户的观察是**完全正确**的：

1. ✅ **SDK层确实绕不开TaplinkServiceKernel**
   - 所有连接操作都通过 `TaplinkServiceKernel`
   - 这是架构设计的核心

2. ✅ **服务地址变化抛给SDK层处理**
   - `notifyServiceAddressChanged()` 触发SDK层的决策
   - 最终通过 `TaplinkServiceKernel.connect()` 重连

3. ✅ **心跳失败等待下一次发现**
   - `disconnectOnHeartbeatFailure()` 保持服务发现运行
   - 服务发现触发后，通过SDK层重连
   - 最终也通过 `TaplinkServiceKernel.connect()`

这种设计是**合理的**，因为：
- 保持了职责分离
- 统一了连接管理
- 提供了灵活的决策空间
