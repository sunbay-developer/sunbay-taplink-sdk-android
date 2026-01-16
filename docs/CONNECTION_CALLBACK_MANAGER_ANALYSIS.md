# ConnectionCallbackManager 必要性分析

## 一、连接流程分析

### 1.1 完整连接流程

```
TaplinkSDK.connect()
  └─> ConnectionManager.connectInternal()
      └─> TaplinkServiceKernel.connect()
          └─> LanClientKernel.connect()
              └─> AsyncServiceKernel.connect()
                  ├─> currentConnectionCallback = connectionCallback  // 基类保存回调
                  └─> LanClientKernel.performConnect()
                      └─> executeConnectionOperation()
                          ├─> callbackManager.addCallback()  // 添加到管理器
                          └─> ConnectionManager.connect(URI)
                              └─> onConnected()
                                  └─> notifyConnectionSuccess()  // 基类通知 currentConnectionCallback
```

### 1.2 错误/断开流程

```
连接错误/断开
  └─> callbackManager.notifyError()  // 通知所有回调
      └─> entry.callback.onDisconnected()
  └─> notifyConnectionError() / notifyConnectionDisconnected()  // 基类通知 currentConnectionCallback
      └─> currentConnectionCallback.onDisconnected()
```

## 二、ConnectionCallbackManager 使用场景分析

### 2.1 当前使用场景

| 方法 | 使用位置 | 用途 | 是否必要 |
|------|---------|------|---------|
| `addCallback()` | `executeConnectionOperation()` | 添加回调到管理器 | ❓ |
| `removeCallback()` | `onConnected()` | 移除当前操作的回调 | ❓ |
| `notifyError()` | 错误/断开时 | 通知所有回调 | ❓ |
| `hasCallbacks()` | 被动断开时 | 判断是否启动服务监听 | ❓ |
| `getCallbackCount()` | 调试/监控 | 获取回调数量 | ❌ |
| `clearExpiredCallbacks()` | 清理资源时 | 清理过期回调 | ❓ |

### 2.2 关键发现

#### 发现 1：连接操作是互斥的

```kotlin
// executeConnectionOperation() 使用互斥锁
if (!operationMutex.tryLock()) {
    // 拒绝新操作
    return
}
```

**结论**：由于 `operationMutex` 的保护，同一时间只能有一个连接操作，所以 `callbackManager` 中实际上同一时间只有一个回调。

#### 发现 2：基类已有回调管理

```kotlin
// AsyncServiceKernel 基类
protected var currentConnectionCallback: ConnectionCallback? = null

// 通知方法
protected fun notifyConnectionSuccess(...)
protected fun notifyConnectionError(...)
protected fun notifyConnectionDisconnected(...)
```

**结论**：基类已经提供了回调管理机制，`callbackManager` 是重复的。

#### 发现 3：其他 kernel 不使用回调管理器

- `VSPClientKernel`：直接使用基类的 `notifyConnectionSuccess()` 和 `notifyConnectionError()`
- `LocalServiceKernel`：直接使用基类的 `notifyConnectionSuccess()` 和 `notifyConnectionError()`
- `SerialServiceKernel`：直接使用基类的通知方法

**结论**：其他 kernel 都不需要额外的回调管理器，`LanClientKernel` 的特殊性值得怀疑。

#### 发现 4：被动断开时的服务监听逻辑

```kotlin
// 被动断开时
if (!wasManual && callbackManager.hasCallbacks()) {
    startServiceMonitoring()
}
```

**问题**：
- 这个逻辑的目的是什么？
- 如果连接已断开，为什么还需要服务监听？
- 服务监听已经通过 `serviceAddressChangeListener` 独立实现

## 三、问题分析

### 3.1 重复通知问题

当前实现中，错误/断开时会通知两次：
1. `callbackManager.notifyError()` - 通知所有回调
2. `notifyConnectionError()` / `notifyConnectionDisconnected()` - 基类通知 `currentConnectionCallback`

**影响**：可能导致 SDK 层的回调被调用两次。

### 3.2 设计不一致

- 其他 kernel 都直接使用基类的回调管理
- `LanClientKernel` 使用了额外的 `ConnectionCallbackManager`
- 这违反了设计一致性原则

### 3.3 过度设计

- 互斥锁已经保证了同一时间只有一个连接操作
- 基类已经提供了回调管理
- `callbackManager` 的"支持多个回调"的设计初衷实际上无法实现（因为互斥锁）

## 四、建议

### 4.1 可以移除 ConnectionCallbackManager

**理由**：
1. ✅ 连接操作是互斥的，同一时间只有一个回调
2. ✅ 基类已有 `currentConnectionCallback` 管理回调
3. ✅ 其他 kernel 都不使用回调管理器
4. ✅ 移除后可以简化代码，提高一致性

### 4.2 需要处理的点

#### 点 1：错误/断开通知

**当前**：
```kotlin
callbackManager.notifyError(...)  // 通知所有回调
notifyConnectionError(...)  // 基类通知 currentConnectionCallback
```

**修改后**：
```kotlin
// 只使用基类的通知方法
notifyConnectionError(...)
// 或
notifyConnectionDisconnected(...)
```

#### 点 2：被动断开时的服务监听

**当前**：
```kotlin
if (!wasManual && callbackManager.hasCallbacks()) {
    startServiceMonitoring()
}
```

**问题**：这个逻辑的目的是什么？如果连接已断开，为什么还需要服务监听？

**建议**：
- 如果服务监听应该始终运行（连接成功后），则不需要这个判断
- 如果服务监听只在有回调时运行，可以考虑使用 `currentConnectionCallback != null` 来判断

#### 点 3：操作 ID 管理

**当前**：
```kotlin
callbackManager.addCallback(operationId, operationType, callback)
callbackManager.removeCallback(operationId)
```

**修改后**：
- 操作 ID 仍然需要（用于 `isOperationValid()` 检查）
- 但不需要通过 `callbackManager` 管理
- 可以直接使用 `currentConnectionCallback` 和 `currentOperationId`

## 五、重构方案

### 5.1 简化方案

1. **移除 `ConnectionCallbackManager` 类**
2. **直接使用基类的 `currentConnectionCallback`**
3. **保留 `currentOperationId` 用于操作验证**
4. **统一使用基类的通知方法**

### 5.2 修改点

1. `executeConnectionOperation()` - 不再添加回调到管理器
2. `onConnected()` - 不再移除回调，直接使用基类通知
3. `onDisconnected()` / `onError()` - 只使用基类通知方法
4. 被动断开时的服务监听逻辑 - 使用 `currentConnectionCallback != null` 判断

## 六、结论

**ConnectionCallbackManager 可以移除**，原因：
1. 连接操作是互斥的，同一时间只有一个回调
2. 基类已有完整的回调管理机制
3. 其他 kernel 都不使用回调管理器
4. 移除后可以简化代码，提高一致性
5. 避免重复通知的问题

**建议**：移除 `ConnectionCallbackManager`，直接使用基类的 `currentConnectionCallback` 和通知方法。
