# "Process is bad" 错误处理指南

## 错误现象

```
Unable to launch app com.sunmi.tapro/10140 for service: process is bad
```

## 什么是 "Process is bad"？

当应用进程之前崩溃或异常退出时，Android 系统会将该进程标记为 "bad process"。在这种状态下：

- ❌ `startService()` 会失败
- ❌ `startForegroundService()` 会失败
- ❌ `bindService()` 可能触发 `onBindingDied()`
- ❌ 系统拒绝启动该进程

## 为什么会出现这个问题？

### 常见原因

1. **服务崩溃**
   - 服务的 `onCreate()` 或 `onStartCommand()` 抛出异常
   - 服务代码有 bug

2. **进程被杀死**
   - 系统因为内存不足杀死进程
   - 用户手动强制停止应用

3. **前台服务超时**
   - 服务没有在 5 秒内调用 `startForeground()`
   - Android 14 杀死了服务进程

4. **重复崩溃**
   - 服务多次崩溃
   - 系统认为进程不稳定

## 解决方案

### 方案 1: 先启动 Activity 清理状态（当前实现）

```kotlin
// 步骤 0: 启动应用的主 Activity 来清理 "bad" 状态
val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
launchIntent?.let {
    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    context.startActivity(it)
    Thread.sleep(500)
}

// 步骤 1: 启动前台服务
context.startForegroundService(explicitIntent)
Thread.sleep(1200)

// 步骤 2: 绑定服务
context.bindService(explicitIntent, serviceConnection, bindFlags)
```

**原理：**
- 启动 Activity 会创建新的进程
- 新进程不会被标记为 "bad"
- 服务可以在新进程中正常启动

**优点：**
- ✅ 自动清理 "bad" 状态
- ✅ 不需要用户干预
- ✅ 成功率高

**缺点：**
- ⚠️ 会短暂显示目标应用
- ⚠️ 需要更多时间

---

### 方案 2: 捕获错误并提示用户

```kotlin
try {
    context.startForegroundService(explicitIntent)
} catch (e: Exception) {
    if (e.message?.contains("process is bad") == true) {
        // 提示用户手动清理
        notifyConnectionError(
            "Target app is in bad state. Please restart the app or device.",
            InnerErrorCode.C25
        )
        return
    }
}
```

**优点：**
- ✅ 明确告知用户问题
- ✅ 代码简单

**缺点：**
- ❌ 需要用户手动操作
- ❌ 用户体验差

---

### 方案 3: 使用 ActivityManager 强制停止（需要权限）

```kotlin
// ⚠️ 需要系统权限，普通应用无法使用
val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
am.killBackgroundProcesses(packageName)  // 需要 KILL_BACKGROUND_PROCESSES 权限
```

**优点：**
- ✅ 彻底清理进程

**缺点：**
- ❌ 需要系统权限
- ❌ 普通应用无法使用

---

### 方案 4: 重试机制

```kotlin
var retryCount = 0
val maxRetries = 3

fun attemptStartService() {
    try {
        context.startForegroundService(explicitIntent)
    } catch (e: Exception) {
        if (e.message?.contains("process is bad") == true && retryCount < maxRetries) {
            retryCount++
            LogUtil.w(TAG, "Retry $retryCount/$maxRetries after 2 seconds...")
            
            // 先启动 Activity 清理状态
            launchTargetApp()
            Thread.sleep(2000)
            
            // 重试
            attemptStartService()
        } else {
            throw e
        }
    }
}
```

**优点：**
- ✅ 提高成功率
- ✅ 自动恢复

**缺点：**
- ⚠️ 需要更多时间
- ⚠️ 代码复杂

---

## 当前实现的完整流程

```kotlin
override fun performConnect(parseResult: ProtocolParseResult, connectionCallback: ConnectionCallback) {
    val packageName = "com.sunmi.tapro"
    
    // 创建显式 Intent
    val explicitIntent = Intent()
    explicitIntent.component = ComponentName(packageName, "$packageName.local.LocalService")
    explicitIntent.putExtra("app_id", appId)
    explicitIntent.putExtra("secret_key", appSecretKey)
    
    // 步骤 0: 清理 "bad process" 状态
    try {
        LogUtil.d(TAG, "Step 0: Checking if target process needs cleanup...")
        
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            
            context.startActivity(it)
            Thread.sleep(500)
            LogUtil.d(TAG, "✅ Target app launched")
        }
    } catch (e: Exception) {
        LogUtil.w(TAG, "Error during process cleanup: ${e.message}")
    }
    
    // 步骤 1: 启动前台服务
    try {
        LogUtil.d(TAG, "Step 1: Starting foreground service...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(explicitIntent)
        } else {
            context.startService(explicitIntent)
        }
        
        Thread.sleep(1200)
        LogUtil.d(TAG, "✅ Foreground service should be running now")
        
    } catch (e: Exception) {
        LogUtil.e(TAG, "Failed to start foreground service: ${e.message}")
        
        // 检查是否是 "process is bad" 错误
        if (e.message?.contains("process is bad") == true) {
            LogUtil.e(TAG, "⚠️ 'process is bad' error detected!")
            LogUtil.e(TAG, "Possible solutions:")
            LogUtil.e(TAG, "  1. Force stop: adb shell am force-stop $packageName")
            LogUtil.e(TAG, "  2. Restart device")
            LogUtil.e(TAG, "  3. Reinstall app")
            
            notifyConnectionError("Target process is in bad state", InnerErrorCode.C25)
            return
        }
    }
    
    // 步骤 2: 绑定服务
    try {
        LogUtil.d(TAG, "Step 2: Binding to the service...")
        
        val bindFlags = Context.BIND_AUTO_CREATE or 
                       Context.BIND_IMPORTANT or 
                       Context.BIND_ABOVE_CLIENT
        
        val bindResult = context.bindService(explicitIntent, serviceConnection, bindFlags)
        
        if (bindResult) {
            LogUtil.d(TAG, "Binding initiated successfully")
        } else {
            LogUtil.e(TAG, "Binding failed")
        }
    } catch (e: Exception) {
        LogUtil.e(TAG, "Exception during binding: ${e.message}")
    }
}
```

## 手动清理方法

如果自动清理失败，用户可以手动清理：

### 方法 1: 使用 ADB

```bash
# 强制停止应用
adb shell am force-stop com.sunmi.tapro

# 等待几秒
sleep 3

# 重新尝试连接
```

### 方法 2: 在设置中强制停止

1. 打开设置
2. 应用管理
3. 找到目标应用（com.sunmi.tapro）
4. 点击"强制停止"
5. 重新尝试连接

### 方法 3: 重启设备

```bash
adb reboot
```

### 方法 4: 重新安装应用

```bash
# 卸载
adb uninstall com.sunmi.tapro

# 重新安装
adb install app.apk
```

## 预防措施

### 服务端（目标应用）

1. **添加异常处理**

```kotlin
override fun onCreate() {
    super.onCreate()
    try {
        // 初始化代码
        startForegroundService()
    } catch (e: Exception) {
        Log.e(TAG, "Error in onCreate", e)
        // 不要让服务崩溃
    }
}

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
        // 业务逻辑
        startForegroundService()
        return START_STICKY
    } catch (e: Exception) {
        Log.e(TAG, "Error in onStartCommand", e)
        return START_NOT_STICKY
    }
}
```

2. **及时调用 startForeground()**

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // 必须在 5 秒内调用
    startForeground(NOTIFICATION_ID, notification)
    return START_STICKY
}
```

3. **添加崩溃日志**

```kotlin
Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    Log.e(TAG, "Uncaught exception", throwable)
    // 记录到文件或上报
}
```

### 客户端（SDK）

1. **使用显式 Intent**

```kotlin
// ✅ 推荐
intent.component = ComponentName(packageName, "$packageName.local.LocalService")

// ❌ 不推荐
intent.action = "com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION"
```

2. **先启动 Activity**

```kotlin
// 清理 "bad" 状态
launchTargetApp()
Thread.sleep(500)

// 再启动服务
startForegroundService()
```

3. **添加重试机制**

```kotlin
// 如果失败，延迟重试
if (!success && retryCount < maxRetries) {
    handler.postDelayed({
        attemptConnect()
    }, 3000)
}
```

## 诊断

### 检查进程状态

```bash
# 查看进程是否运行
adb shell ps -A | grep com.sunmi.tapro

# 查看进程详细信息
adb shell dumpsys activity processes | grep com.sunmi.tapro
```

### 查看崩溃日志

```bash
# 查看最近的崩溃
adb logcat -b crash

# 查看服务日志
adb logcat | grep -E "LocalService|FATAL|AndroidRuntime"
```

### 测试清理方法

```bash
# 1. 强制停止
adb shell am force-stop com.sunmi.tapro

# 2. 等待
sleep 2

# 3. 启动应用
adb shell am start -n com.sunmi.tapro/.MainActivity

# 4. 等待
sleep 1

# 5. 尝试连接
# 在你的应用中调用 connect()
```

## 总结

| 方案 | 成功率 | 用户体验 | 实现难度 |
|------|--------|---------|---------|
| 先启动 Activity | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| 捕获错误提示 | ⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| 强制停止进程 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ❌ 需要权限 |
| 重试机制 | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |

**推荐方案：先启动 Activity + 重试机制**

这是当前实现的方案，在大多数情况下可以自动解决 "process is bad" 问题。

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team
