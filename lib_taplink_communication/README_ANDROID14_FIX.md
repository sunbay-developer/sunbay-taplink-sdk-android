# Android 14 服务绑定问题 - 完整解决方案

## 当前实现的策略

### 策略：先启动应用，再绑定服务

```kotlin
// 1. 先启动目标应用的主 Activity（确保进程运行）
val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
launchIntent?.let {
    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    it.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    context.startActivity(it)
    Thread.sleep(500)  // 等待进程启动
}

// 2. 绑定服务
val bindFlags = Context.BIND_AUTO_CREATE or 
               Context.BIND_IMPORTANT or 
               Context.BIND_ABOVE_CLIENT
context.bindService(intent, serviceConnection, bindFlags)
```

## 为什么这样做？

### Android 14 的限制

1. **后台启动限制更严格**
   - 直接 `bindService()` 可能无法启动未运行的进程
   - 系统可能拒绝启动 "bad" 状态的进程

2. **隐式 Intent 限制**
   - 通过 action 查找服务可能受限
   - 需要确保目标进程已运行

3. **进程状态问题**
   - 如果进程之前崩溃，系统会标记为 "bad"
   - 需要先清理状态再启动

### 解决方案的优势

✅ **确保进程运行** - 先启动 Activity 确保进程存在  
✅ **避免 "process is bad"** - 启动 Activity 会清理异常状态  
✅ **兼容性好** - 适用于所有 Android 版本  
✅ **成功率高** - 进程运行后绑定成功率大大提高

## 如果还是失败怎么办？

### 备选方案 1: 使用显式组件名

修改 `performConnect()` 方法，使用显式组件名而不是 action：

```kotlin
// 不使用 action
val intent = Intent()
intent.component = ComponentName(
    packageName,
    "$packageName.taplink.service.local.LocalService"  // 完整类名
)
intent.putExtra("app_id", appId)
intent.putExtra("secret_key", appSecretKey)

val bindResult = context.bindService(intent, serviceConnection, bindFlags)
```

**需要知道服务的完整类名，可以通过以下方式获取：**

```bash
# 查询服务信息
adb shell dumpsys package com.sunmi.tapro | grep "Service"
```

---

### 备选方案 2: 增加等待时间

如果进程启动较慢，可以增加等待时间：

```kotlin
// 启动应用后等待更长时间
context.startActivity(launchIntent)
Thread.sleep(1000)  // 从 500ms 增加到 1000ms
```

---

### 备选方案 3: 检查进程是否真的启动了

```kotlin
// 启动应用后，检查进程是否运行
context.startActivity(launchIntent)

// 等待并检查进程
var processRunning = false
for (i in 0 until 10) {  // 最多等待 5 秒
    Thread.sleep(500)
    
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningProcesses = am.runningAppProcesses ?: emptyList()
    processRunning = runningProcesses.any { it.processName == packageName }
    
    if (processRunning) {
        LogUtil.d(TAG, "Target process is running")
        break
    }
}

if (!processRunning) {
    LogUtil.e(TAG, "Target process failed to start")
    notifyConnectionError("Target process not running", InnerErrorCode.C25)
    return
}

// 进程运行后再绑定服务
val bindResult = context.bindService(intent, serviceConnection, bindFlags)
```

---

### 备选方案 4: 使用广播唤醒服务

让目标应用实现一个广播接收器，收到广播后启动服务：

**目标应用端：**

```kotlin
class ServiceWakeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 收到广播后启动服务
        val serviceIntent = Intent(context, LocalService::class.java)
        context.startService(serviceIntent)
    }
}
```

```xml
<receiver
    android:name=".ServiceWakeUpReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.sunmi.tapro.WAKE_UP_SERVICE" />
    </intent-filter>
</receiver>
```

**客户端（SDK）：**

```kotlin
// 先发送广播唤醒服务
val wakeUpIntent = Intent("com.sunmi.tapro.WAKE_UP_SERVICE")
wakeUpIntent.setPackage(packageName)
context.sendBroadcast(wakeUpIntent)

// 等待服务启动
Thread.sleep(500)

// 再绑定服务
val bindResult = context.bindService(intent, serviceConnection, bindFlags)
```

---

## 诊断步骤

### 1. 运行诊断脚本

```bash
cd lib_taplink_communication
./diagnose_binding.sh
```

### 2. 查看详细日志

```bash
adb logcat | grep -E "LocalServiceKernel|LocalService|AndroidRuntime|FATAL"
```

### 3. 检查关键信息

在日志中查找：

- ✅ `Target app launch intent sent` - 应用启动成功
- ✅ `bindService() returned: true` - 绑定请求成功
- ✅ `[onServiceConnected]` - 服务连接成功
- ❌ `[onBindingDied]` - 服务绑定失败
- ❌ `FATAL EXCEPTION` - 服务崩溃

### 4. 检查目标服务

确保目标服务（com.sunmi.tapro）：

- [ ] 正确声明服务（`android:exported="true"`）
- [ ] `onBind()` 返回非 null 的 Binder
- [ ] 没有在 `onCreate()` 或 `onBind()` 中崩溃
- [ ] AIDL 接口与客户端一致
- [ ] 添加了异常处理和日志

---

## 目标服务端检查清单

### AndroidManifest.xml

```xml
<service
    android:name=".taplink.service.local.LocalService"
    android:enabled="true"
    android:exported="true">  <!-- 必须是 true -->
    <intent-filter>
        <action android:name="com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION" />
    </intent-filter>
</service>
```

### 服务实现

```kotlin
class LocalService : Service() {
    
    private val TAG = "LocalService"
    
    private val binder = object : ITransaction.Stub() {
        override fun onTransaction(requestData: String?, callback: ITransactionCallback?) {
            try {
                Log.d(TAG, "onTransaction called: $requestData")
                // 处理业务逻辑
                callback?.callback("SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "Error in onTransaction", e)
                callback?.onError("ERROR", e.message)
            }
        }
        
        override fun getExtraInfos(): MutableMap<String, String> {
            return try {
                Log.d(TAG, "getExtraInfos called")
                mutableMapOf(
                    "version" to "1.0.0",
                    "deviceId" to "xxx"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in getExtraInfos", e)
                mutableMapOf()
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "✅ Service onCreate")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "✅ Service onBind: $intent")
        Log.d(TAG, "Returning binder: $binder")
        return binder  // 必须返回非 null
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service onUnbind")
        return super.onUnbind(intent)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        super.onDestroy()
    }
}
```

---

## 常见错误和解决方案

### 错误 1: onBindingDied 被调用

**原因：** 服务进程崩溃或返回 null

**解决：**
1. 查看服务端日志，找到崩溃原因
2. 确保 `onBind()` 返回非 null
3. 添加异常处理

---

### 错误 2: bindService() 返回 false

**原因：** 服务不存在或权限不足

**解决：**
1. 检查服务声明（`android:exported="true"`）
2. 检查包名和 action 是否正确
3. 尝试使用显式组件名

---

### 错误 3: 进程无法启动

**原因：** 应用被禁用或卸载

**解决：**
1. 检查应用是否安装：`adb shell pm list packages | grep com.sunmi.tapro`
2. 检查应用是否被禁用
3. 重新安装目标应用

---

## 相关文档

- [绑定标志详解](./BIND_FLAGS_GUIDE.md)
- [问题排查指南](./BINDING_DIED_TROUBLESHOOTING.md)
- [显式组件方案](./ANDROID14_EXPLICIT_COMPONENT.md)
- [修复说明](./ANDROID14_SERVICE_BINDING_FIX.md)

---

## 总结

当前实现的策略（先启动应用再绑定服务）应该能解决大部分 Android 14 的绑定问题。如果还是失败：

1. **检查目标服务端** - 确保服务正确实现
2. **查看详细日志** - 找到具体失败原因
3. **尝试备选方案** - 使用显式组件名或增加等待时间
4. **联系服务端开发** - 如果是服务端问题，需要他们修复

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team
