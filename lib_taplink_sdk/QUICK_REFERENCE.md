# Auto-Reconnection Feature - Quick Reference

## 🚀 Quick Start

### 1. Use Default Configuration (Recommended)

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
// Auto-reconnection is enabled by default

TaplinkSDK.init(context, config)
```

### 2. Custom Reconnection Strategy

```kotlin
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setAutoReconnect(true)
    .setRetryPolicy(maxRetries = 5, delayMs = 3000L)

TaplinkSDK.init(context, config)
```

### 3. Listen to Reconnection Events

```kotlin
TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        // Connection successful
    }
    
    override fun onDisconnected(reason: String) {
        // Connection disconnected
    }
    
    override fun onError(error: ConnectionError) {
        // Connection error
    }
    
    override fun onReconnecting(attempt: Int, maxRetries: Int) {
        // Reconnecting (optional)
        showToast("Reconnecting... ($attempt/$maxRetries)")
    }
})
```

## ⚙️ Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `autoReconnect` | Boolean | `true` | Whether to enable auto-reconnection |
| `maxRetryCount` | Int | `3` | Maximum retry attempts |
| `retryDelayMs` | Long | `2000L` | Retry delay (milliseconds) |

## 📋 Recommended Configuration

| Connection Mode | Retry Count | Delay Time |
|----------------|-------------|------------|
| APP_TO_APP | 3 | 1000ms |
| CABLE | 3 | 2000ms |
| LAN | 5 | 3000ms |

## 🔄 Reconnection Trigger Conditions

Auto-reconnection is triggered when **all** of the following conditions are met:

- ✅ `autoReconnect = true`
- ✅ Not actively disconnected (not called `disconnect()`)
- ✅ Current retry count < maximum retry count

## 🚫 Cases Where Reconnection Won't Trigger

- ❌ Actively called `disconnect()`
- ❌ `autoReconnect = false`
- ❌ Maximum retry count reached
- ❌ SDK not initialized

## 📞 Callback Methods

### onReconnecting()
```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    // Called before each reconnection attempt
    // attempt: Current retry count (1-based)
    // maxRetries: Maximum retry count
}
```

### onConnected()
```kotlin
override fun onConnected(deviceId: String, taproVersion: String) {
    // Called when connection is successful (including reconnection success)
}
```

### onDisconnected()
```kotlin
override fun onDisconnected(reason: String) {
    // Called when connection is disconnected (retry failed or max count reached)
}
```

## 💡 Common Usage

### Disable Auto-Reconnection

```kotlin
val config = TaplinkConfig(...)
    .setAutoReconnect(false)
```

### Configure Based on Connection Mode

```kotlin
val (maxRetries, delayMs) = when (connectionMode) {
    ConnectionMode.APP_TO_APP -> Pair(3, 1000L)
    ConnectionMode.CABLE -> Pair(3, 2000L)
    ConnectionMode.LAN -> Pair(5, 3000L)
    else -> Pair(3, 2000L)
}

val config = TaplinkConfig(...)
    .setConnectionMode(connectionMode)
    .setRetryPolicy(maxRetries, delayMs)
```

### Show Reconnection Progress

```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    runOnUiThread {
        progressBar.visibility = View.VISIBLE
        statusText.text = "Reconnecting... ($attempt/$maxRetries)"
    }
}

override fun onConnected(deviceId: String, taproVersion: String) {
    runOnUiThread {
        progressBar.visibility = View.GONE
        statusText.text = "Connected"
    }
}
```

## 🔍 Debugging Tips

### Enable Logging

```kotlin
val config = TaplinkConfig(...)
    .setLogEnabled(true)
    .setLogLevel(LogLevel.DEBUG)
```

### Check Reconnection Status

```kotlin
// Check if connected
if (TaplinkSDK.isConnected()) {
    // Connected
} else {
    // Not connected (may be reconnecting)
}
```

## ⚠️ Important Notes

1. **Cannot initiate payment during reconnection**
   - `isConnected()` returns `false`
   - Recommend disabling payment button

2. **Active disconnection won't trigger reconnection**
   - Need to manually `connect()` after calling `disconnect()`

3. **Stops after reaching maximum retry count**
   - User needs to manually reconnect

4. **All callbacks execute on main thread**
   - Can directly update UI

## 📖 Complete Documentation

- [Detailed Usage Guide](AUTO_RECONNECT_USAGE.md)
- [Changelog](CHANGELOG_AUTO_RECONNECT.md)
- [Example Code](EXAMPLE_AUTO_RECONNECT.kt)
- [Refactoring Summary](REFACTORING_SUMMARY.md)
- [Implementation Summary](AUTO_RECONNECT_IMPLEMENTATION.md)

## 🆘 FAQ

**Q: How to determine if it's first connection or reconnection?**

A: `onConnected()` triggers for both. You can maintain a flag at application level:

```kotlin
private var isFirstConnection = true

override fun onConnected(deviceId: String, taproVersion: String) {
    if (isFirstConnection) {
        // First connection
        isFirstConnection = false
    } else {
        // Reconnection successful
    }
}
```

**Q: Can I initiate payment during reconnection?**

A: No. Recommend disabling payment functionality:

```kotlin
override fun onReconnecting(attempt: Int, maxRetries: Int) {
    paymentButton.isEnabled = false
}

override fun onConnected(deviceId: String, taproVersion: String) {
    paymentButton.isEnabled = true
}
```

**Q: How to manually trigger reconnection?**

A: If auto-reconnection is disabled, you can manually call:

```kotlin
override fun onDisconnected(reason: String) {
    showReconnectButton {
        TaplinkSDK.connect(lastConfig, this)
    }
}
```

---

**Version**: 1.1.0  
**Updated**: 2025-12-11
