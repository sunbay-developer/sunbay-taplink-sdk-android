# 手动重连使用示例

## 场景说明

当自动重连次数用完后，用户可以通过 UI 按钮手动触发重连。

---

## 完整示例代码

### 1. Activity 实现

```kotlin
class PaymentActivity : AppCompatActivity() {
    
    private val TAG = "PaymentActivity"
    
    private lateinit var statusText: TextView
    private lateinit var reconnectButton: Button
    
    private var isConnected = false
    private var isReconnecting = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)
        
        statusText = findViewById(R.id.statusText)
        reconnectButton = findViewById(R.id.reconnectButton)
        
        // 初始化 SDK
        val config = TaplinkConfig(
            appId = "your_app_id",
            merchantId = "your_merchant_id",
            secretKey = "your_secret_key"
        )
            .setAutoReconnect(true)
            .setRetryPolicy(maxRetries = 3, delayMs = 3000L)
        
        TaplinkSDK.init(this, config)
        
        // 设置重连按钮点击事件
        reconnectButton.setOnClickListener {
            handleManualReconnect()
        }
        
        // 初始连接
        connectToDevice()
    }
    
    /**
     * 连接到设备
     */
    private fun connectToDevice() {
        Log.d(TAG, "Connecting to device...")
        updateStatus("正在连接...", false)
        
        TaplinkSDK.connect(null, object : ConnectionListener {
            override fun onConnected(deviceId: String, version: String) {
                Log.d(TAG, "Connected: deviceId=$deviceId, version=$version")
                isConnected = true
                isReconnecting = false
                updateStatus("已连接 (设备: $deviceId)", false)
            }
            
            override fun onDisconnected(reason: String) {
                Log.w(TAG, "Disconnected: $reason")
                isConnected = false
                isReconnecting = false
                
                // 显示重连按钮
                updateStatus("连接断开: $reason", true)
                
                // 可选：显示提示
                Toast.makeText(
                    this@PaymentActivity,
                    "连接已断开，请点击重连按钮",
                    Toast.LENGTH_LONG
                ).show()
            }
            
            override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
                Log.d(TAG, "Reconnecting: $currentAttempt/$maxAttempts")
                isReconnecting = true
                updateStatus("正在重连... ($currentAttempt/$maxAttempts)", false)
            }
            
            override fun onError(error: ConnectionError) {
                Log.e(TAG, "Connection error: ${error.message}")
                isConnected = false
                isReconnecting = false
                updateStatus("连接错误: ${error.message}", true)
            }
        })
    }
    
    /**
     * 处理手动重连
     */
    private fun handleManualReconnect() {
        if (isConnected) {
            Toast.makeText(this, "已经连接", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isReconnecting) {
            Toast.makeText(this, "正在重连中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        
        Log.d(TAG, "User manually reconnecting...")
        connectToDevice()
    }
    
    /**
     * 更新状态显示
     */
    private fun updateStatus(status: String, showReconnectButton: Boolean) {
        runOnUiThread {
            statusText.text = status
            reconnectButton.visibility = if (showReconnectButton) View.VISIBLE else View.GONE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        TaplinkSDK.disconnect()
    }
}
```

### 2. 布局文件

```xml
<!-- activity_payment.xml -->
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    android:gravity="center">
    
    <!-- 状态文本 -->
    <TextView
        android:id="@+id/statusText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="未连接"
        android:textSize="18sp"
        android:textColor="@android:color/black"
        android:layout_marginBottom="24dp"/>
    
    <!-- 重连按钮 -->
    <Button
        android:id="@+id/reconnectButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="重新连接"
        android:visibility="gone"/>
    
</LinearLayout>
```

---

## 使用 ViewModel 的示例

### 1. ViewModel

```kotlin
class PaymentViewModel : ViewModel() {
    
    private val TAG = "PaymentViewModel"
    
    // 连接状态
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    // 重连信息
    private val _reconnectInfo = MutableLiveData<ReconnectInfo>()
    val reconnectInfo: LiveData<ReconnectInfo> = _reconnectInfo
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceId: String, val version: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    data class ReconnectInfo(
        val isReconnecting: Boolean,
        val currentAttempt: Int = 0,
        val maxAttempts: Int = 0
    )
    
    private val connectionListener = object : ConnectionListener {
        override fun onConnected(deviceId: String, version: String) {
            Log.d(TAG, "Connected: deviceId=$deviceId")
            _connectionState.postValue(ConnectionState.Connected(deviceId, version))
            _reconnectInfo.postValue(ReconnectInfo(false))
        }
        
        override fun onDisconnected(reason: String) {
            Log.w(TAG, "Disconnected: $reason")
            _connectionState.postValue(ConnectionState.Disconnected)
            _reconnectInfo.postValue(ReconnectInfo(false))
        }
        
        override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
            Log.d(TAG, "Reconnecting: $currentAttempt/$maxAttempts")
            _reconnectInfo.postValue(
                ReconnectInfo(
                    isReconnecting = true,
                    currentAttempt = currentAttempt,
                    maxAttempts = maxAttempts
                )
            )
        }
        
        override fun onError(error: ConnectionError) {
            Log.e(TAG, "Error: ${error.message}")
            _connectionState.postValue(ConnectionState.Error(error.message))
            _reconnectInfo.postValue(ReconnectInfo(false))
        }
    }
    
    /**
     * 连接到设备
     */
    fun connect() {
        Log.d(TAG, "Connecting...")
        _connectionState.value = ConnectionState.Connecting
        TaplinkSDK.connect(null, connectionListener)
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        TaplinkSDK.disconnect()
    }
    
    /**
     * 手动重连
     */
    fun manualReconnect() {
        Log.d(TAG, "Manual reconnect triggered")
        connect()
    }
}
```

### 2. Activity with ViewModel

```kotlin
class PaymentActivity : AppCompatActivity() {
    
    private val viewModel: PaymentViewModel by viewModels()
    
    private lateinit var statusText: TextView
    private lateinit var reconnectButton: Button
    private lateinit var progressBar: ProgressBar
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment)
        
        statusText = findViewById(R.id.statusText)
        reconnectButton = findViewById(R.id.reconnectButton)
        progressBar = findViewById(R.id.progressBar)
        
        // 观察连接状态
        viewModel.connectionState.observe(this) { state ->
            handleConnectionState(state)
        }
        
        // 观察重连信息
        viewModel.reconnectInfo.observe(this) { info ->
            handleReconnectInfo(info)
        }
        
        // 重连按钮点击
        reconnectButton.setOnClickListener {
            viewModel.manualReconnect()
        }
        
        // 初始连接
        viewModel.connect()
    }
    
    private fun handleConnectionState(state: PaymentViewModel.ConnectionState) {
        when (state) {
            is PaymentViewModel.ConnectionState.Disconnected -> {
                statusText.text = "未连接"
                reconnectButton.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
            }
            
            is PaymentViewModel.ConnectionState.Connecting -> {
                statusText.text = "正在连接..."
                reconnectButton.visibility = View.GONE
                progressBar.visibility = View.VISIBLE
            }
            
            is PaymentViewModel.ConnectionState.Connected -> {
                statusText.text = "已连接 (${state.deviceId})"
                reconnectButton.visibility = View.GONE
                progressBar.visibility = View.GONE
            }
            
            is PaymentViewModel.ConnectionState.Error -> {
                statusText.text = "错误: ${state.message}"
                reconnectButton.visibility = View.VISIBLE
                progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun handleReconnectInfo(info: PaymentViewModel.ReconnectInfo) {
        if (info.isReconnecting) {
            statusText.text = "正在重连... (${info.currentAttempt}/${info.maxAttempts})"
            reconnectButton.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnect()
    }
}
```

---

## 最佳实践

### 1. 显示重连进度

```kotlin
override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
    // 显示进度
    val progress = (currentAttempt.toFloat() / maxAttempts * 100).toInt()
    progressBar.progress = progress
    
    // 显示文本
    statusText.text = "正在重连... ($currentAttempt/$maxAttempts)"
}
```

### 2. 防止重复点击

```kotlin
private var isReconnecting = false

reconnectButton.setOnClickListener {
    if (isReconnecting) {
        Toast.makeText(this, "正在重连中，请稍候", Toast.LENGTH_SHORT).show()
        return@setOnClickListener
    }
    
    isReconnecting = true
    viewModel.manualReconnect()
}
```

### 3. 添加重连延迟提示

```kotlin
override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
    val delaySeconds = 3 // 从配置中获取
    statusText.text = "正在重连... ($currentAttempt/$maxAttempts)\n" +
                     "将在 $delaySeconds 秒后重试"
}
```

### 4. 记录重连历史

```kotlin
data class ReconnectHistory(
    val timestamp: Long,
    val attempt: Int,
    val success: Boolean,
    val reason: String?
)

private val reconnectHistory = mutableListOf<ReconnectHistory>()

override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
    reconnectHistory.add(
        ReconnectHistory(
            timestamp = System.currentTimeMillis(),
            attempt = currentAttempt,
            success = false,
            reason = null
        )
    )
}

override fun onConnected(deviceId: String, version: String) {
    reconnectHistory.lastOrNull()?.let {
        reconnectHistory[reconnectHistory.lastIndex] = it.copy(success = true)
    }
}
```

---

## 常见问题

### Q1: 手动重连后，自动重连次数会重置吗？

**A:** 是的。调用 `connect()` 会重置 `currentRetryCount = 0`，重新开始计数。

### Q2: 如果正在自动重连，用户点击手动重连会怎样？

**A:** 建议在 UI 层判断 `isReconnecting` 状态，防止重复操作。

### Q3: 手动重连失败后，会继续自动重连吗？

**A:** 会的。手动重连后，自动重连机制会重新生效。

### Q4: 如何禁用自动重连，只使用手动重连？

**A:** 在配置中设置 `setAutoReconnect(false)`：

```kotlin
val config = TaplinkConfig(...)
    .setAutoReconnect(false)  // 禁用自动重连
```

---

**文档版本**: 1.0.0  
**更新日期**: 2025-12-17  
**作者**: TaPro Team
