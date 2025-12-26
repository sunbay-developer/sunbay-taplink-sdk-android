# 智能心跳机制使用指南

## 概述

智能心跳机制根据应用状态和网络质量动态调整心跳参数，提供更稳定和高效的连接保持策略。

## 核心特性

### 1. 应用状态感知
- **前台状态**：正常心跳间隔（30秒）
- **后台状态**：延长心跳间隔（60秒）
- **低内存状态**：进一步延长间隔（120秒）

### 2. 网络质量自适应
- **优秀网络**：使用标准间隔
- **良好网络**：间隔增加20%
- **一般网络**：间隔增加50%
- **较差网络**：间隔增加100%
- **很差网络**：间隔增加200%

### 3. 智能重试机制
- 心跳失败时不立即断开连接
- 支持可配置的重试次数（默认3次）
- 失败计数器在成功后自动重置

## 使用方法

### 1. 应用初始化时设置

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化应用状态监听器
        AppStateMonitor.getInstance().initialize(this)
        
        // 可选：自定义心跳配置
        SmartHeartbeatConfig.getInstance().setCustomConfig(
            foregroundInterval = 25_000L,    // 前台25秒
            backgroundInterval = 90_000L,    // 后台90秒
            lowMemoryInterval = 180_000L,    // 低内存3分钟
            timeout = 20_000L,               // 超时20秒
            maxRetryCount = 5                // 最大重试5次
        )
    }
}
```

### 2. 在 Activity 中优化连接管理

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onResume() {
        super.onResume()
        // 应用回到前台时，心跳会自动调整为前台模式
        LogUtil.d("MainActivity", "App resumed, heartbeat will adjust to foreground mode")
    }
    
    override fun onPause() {
        super.onPause()
        // 应用进入后台时，心跳会自动调整为后台模式
        LogUtil.d("MainActivity", "App paused, heartbeat will adjust to background mode")
    }
}
```

### 3. 监听心跳配置变化（可选）

```kotlin
class ConnectionService : SmartHeartbeatConfig.HeartbeatConfigChangeListener {
    
    init {
        SmartHeartbeatConfig.getInstance().addConfigChangeListener(this)
    }
    
    override fun onHeartbeatConfigChanged(newConfig: SmartHeartbeatConfig.HeartbeatConfig) {
        LogUtil.d("ConnectionService", "Heartbeat config changed: ${newConfig}")
        // 可以在这里记录配置变化或执行其他逻辑
    }
    
    fun cleanup() {
        SmartHeartbeatConfig.getInstance().removeConfigChangeListener(this)
    }
}
```

### 4. 监听网络质量变化（可选）

```kotlin
class NetworkManager : NetworkQualityMonitor.NetworkQualityChangeListener {
    
    init {
        NetworkQualityMonitor.getInstance(context).addQualityChangeListener(this)
    }
    
    override fun onNetworkQualityChanged(
        oldQuality: SmartHeartbeatConfig.NetworkQuality,
        newQuality: SmartHeartbeatConfig.NetworkQuality
    ) {
        LogUtil.d("NetworkManager", "Network quality: $oldQuality -> $newQuality")
        
        // 可以根据网络质量变化执行其他优化
        when (newQuality) {
            SmartHeartbeatConfig.NetworkQuality.VERY_POOR -> {
                // 网络很差时的处理逻辑
                showNetworkWarning()
            }
            SmartHeartbeatConfig.NetworkQuality.EXCELLENT -> {
                // 网络优秀时的处理逻辑
                hideNetworkWarning()
            }
            else -> {
                // 其他情况
            }
        }
    }
}
```

## 配置参数说明

### 心跳间隔配置
- `foregroundInterval`: 前台心跳间隔（默认30秒）
- `backgroundInterval`: 后台心跳间隔（默认60秒）
- `lowMemoryInterval`: 低内存心跳间隔（默认120秒）

### 超时和重试配置
- `timeout`: 心跳超时时间（默认15秒）
- `maxRetryCount`: 最大重试次数（默认3次）

### 网络质量阈值
- 优秀网络：延迟 ≤ 50ms
- 良好网络：延迟 ≤ 100ms
- 一般网络：延迟 ≤ 200ms
- 较差网络：延迟 ≤ 500ms
- 很差网络：延迟 > 500ms

## 最佳实践

### 1. 根据业务场景调整配置
```kotlin
// 对于实时性要求高的应用
SmartHeartbeatConfig.getInstance().setCustomConfig(
    foregroundInterval = 15_000L,  // 更频繁的前台心跳
    maxRetryCount = 2              // 更快的失败检测
)

// 对于省电要求高的应用
SmartHeartbeatConfig.getInstance().setCustomConfig(
    backgroundInterval = 120_000L, // 更长的后台间隔
    lowMemoryInterval = 300_000L   // 低内存时5分钟间隔
)
```

### 2. 在关键业务操作前检查连接
```kotlin
fun performCriticalOperation() {
    // 在关键操作前手动检查网络质量
    NetworkQualityMonitor.getInstance(context).checkQualityNow()
    
    // 执行业务逻辑
    // ...
}
```

### 3. 处理内存压力
```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    
    when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
            // 系统内存严重不足时，可以临时调整配置
            SmartHeartbeatConfig.getInstance().setCustomConfig(
                lowMemoryInterval = 600_000L // 临时设置为10分钟
            )
        }
    }
}
```

## 日志监控

智能心跳系统会输出详细的日志信息，便于调试和监控：

```
D/WebSocketClientKernel: Smart heartbeat system initialized
D/AppStateMonitor: App moved to background
D/SmartHeartbeatConfig: App state changed to: BACKGROUND, adjusting heartbeat interval
D/WebSocketClientKernel: Heartbeat config changed: interval=60000ms, timeout=15000ms
D/NetworkQualityMonitor: Network quality changed: GOOD -> FAIR
D/WebSocketClientKernel: Heartbeat sent (interval: 90000ms)
```

## 故障排除

### 1. 心跳频繁超时
- 检查网络连接稳定性
- 考虑增加超时时间或重试次数
- 查看网络质量监测结果

### 2. 应用后台连接断开
- 确认已正确初始化 AppStateMonitor
- 检查系统是否限制后台网络访问
- 考虑使用前台服务保持连接

### 3. 内存使用过高
- 调整低内存状态下的心跳间隔
- 检查是否有内存泄漏
- 考虑在极低内存时暂停心跳

通过智能心跳机制，您的应用可以在不同场景下保持最优的连接策略，既保证连接稳定性，又兼顾性能和电量消耗。