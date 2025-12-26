# Default Connection Configuration

## Overview

The Taplink SDK now automatically creates appropriate default connection configurations when no explicit `ConnectionConfig` is provided to the `connect()` method. The default configuration is determined by the `connectionMode` specified in `TaplinkConfig` during SDK initialization.

## Behavior

### Priority Order

When calling `TaplinkSDK.connect(config, listener)`, the SDK determines the actual configuration in the following priority order:

1. **Provided Config** - If `config` parameter is not null, use it directly
2. **Previous Config** - If no config provided, try to use the last successful connection configuration
3. **Default Config** - If no previous config available, create a default based on `TaplinkConfig.connectionMode`

### Default Configuration Rules

| Connection Mode | Default Configuration | Description |
|----------------|----------------------|-------------|
| `APP_TO_APP` | `ConnectionConfig.createDefault()` | Empty config for local Intent-based connection |
| `CABLE` | `ConnectionConfig().setCableProtocol(CableProtocol.AUTO)` | Auto-detect cable protocol (AOA/VSP/RS232) |
| `LAN` | `ConnectionConfig.createDefault()` | Empty config (requires manual host/port setup) |
| `NONE` | `ConnectionConfig.createDefault()` | Fallback to APP_TO_APP behavior |

## Usage Examples

### 1. APP_TO_APP Mode (Simplest)

```kotlin
// Initialize with APP_TO_APP mode
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setConnectionMode(ConnectionMode.APP_TO_APP)

TaplinkSDK.init(context, config)

// Connect without providing ConnectionConfig
// SDK automatically creates default APP_TO_APP config
TaplinkSDK.connect(null, connectionListener)
```

### 2. CABLE Mode with Auto-Detection

```kotlin
// Initialize with CABLE mode
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setConnectionMode(ConnectionMode.CABLE)

TaplinkSDK.init(context, config)

// Connect without providing ConnectionConfig
// SDK automatically creates config with CableProtocol.AUTO
TaplinkSDK.connect(null, connectionListener)
```

### 3. LAN Mode (Requires Manual Setup)

```kotlin
// Initialize with LAN mode
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id",
    secretKey = "your_secret_key"
)
    .setConnectionMode(ConnectionMode.LAN)

TaplinkSDK.init(context, config)

// Connect without config - will create default but needs host/port
TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onError(error: ConnectionError) {
        // LAN mode requires explicit host/port configuration
        val lanConfig = ConnectionConfig()
            .setHost("192.168.1.100")
            .setPort(8443)
        
        TaplinkSDK.connect(lanConfig, this)
    }
    // ... other callbacks
})
```

## Advanced Usage

### Override Default Behavior

Even when a default would be created, you can always override by providing explicit configuration:

```kotlin
// SDK initialized with CABLE mode (default would be AUTO)
val config = TaplinkConfig(...)
    .setConnectionMode(ConnectionMode.CABLE)

TaplinkSDK.init(context, config)

// Override default AUTO with specific protocol
val explicitConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.USB_VSP)

TaplinkSDK.connect(explicitConfig, connectionListener)
```

### Previous Configuration Persistence

The SDK remembers successful connection configurations:

```kotlin
// First connection with explicit config
val firstConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.USB_AOA)

TaplinkSDK.connect(firstConfig, connectionListener)

// After successful connection and later disconnect...
// Reconnect without config - uses previous successful config
TaplinkSDK.connect(null, connectionListener) // Uses USB_AOA from previous connection
```

### Fallback Chain Example

```kotlin
val config = TaplinkConfig(...)
    .setConnectionMode(ConnectionMode.CABLE)

TaplinkSDK.init(context, config)

// This call will:
// 1. Check if explicit config provided (null) ❌
// 2. Check for previous successful config (none initially) ❌  
// 3. Create default based on CABLE mode ✅
//    → ConnectionConfig().setCableProtocol(CableProtocol.AUTO)
TaplinkSDK.connect(null, connectionListener)
```

## Implementation Details

### Default Config Creation Logic

```kotlin
private fun createDefaultConnectionConfig(): ConnectionConfig {
    val currentConfig = this.config ?: return ConnectionConfig.createDefault()

    return when (currentConfig.connectionMode) {
        ConnectionMode.APP_TO_APP -> {
            ConnectionConfig.createDefault()
        }
        
        ConnectionMode.CABLE -> {
            ConnectionConfig().setCableProtocol(CableProtocol.AUTO)
        }
        
        ConnectionMode.LAN -> {
            ConnectionConfig.createDefault() // Requires manual host/port
        }
        
        ConnectionMode.NONE -> {
            ConnectionConfig.createDefault() // Fallback to APP_TO_APP
        }
    }
}
```

### Logging

Enable debug logging to see the configuration selection process:

```kotlin
val config = TaplinkConfig(...)
    .setLogEnabled(true)
    .setLogLevel(LogLevel.DEBUG)
```

Example log output:
```
D/TaplinkApiImpl: No connection config provided, created default config based on connection mode: CABLE
D/TaplinkApiImpl: Built protocol string: usb://accessory, connectionMode: USB_AOA
```

## Migration Guide

### Before (Required Explicit Config)

```kotlin
// Old way - always required explicit config
val connectionConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.AUTO)

TaplinkSDK.connect(connectionConfig, connectionListener)
```

### After (Optional Config)

```kotlin
// New way - config is optional if TaplinkConfig.connectionMode is set
TaplinkSDK.connect(null, connectionListener) // SDK creates appropriate default
```

## Best Practices

### 1. Set Connection Mode During Initialization

```kotlin
// ✅ Recommended - Set connection mode in TaplinkConfig
val config = TaplinkConfig(...)
    .setConnectionMode(ConnectionMode.CABLE)

TaplinkSDK.init(context, config)
TaplinkSDK.connect(null, connectionListener) // Uses CABLE default
```

### 2. Use Explicit Config for Specific Requirements

```kotlin
// ✅ Use explicit config when you need specific settings
val specificConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.USB_VSP) // Specific protocol
    .setHost("192.168.1.100")               // Specific host
    .setPort(8443)                          // Specific port

TaplinkSDK.connect(specificConfig, connectionListener)
```

### 3. Handle LAN Mode Properly

```kotlin
// ✅ LAN mode requires explicit host/port
val config = TaplinkConfig(...)
    .setConnectionMode(ConnectionMode.LAN)

TaplinkSDK.init(context, config)

// Provide host/port for LAN connections
val lanConfig = ConnectionConfig()
    .setHost("192.168.1.100")
    .setPort(8443)

TaplinkSDK.connect(lanConfig, connectionListener)
```

## Error Handling

### Common Scenarios

```kotlin
TaplinkSDK.connect(null, object : ConnectionListener {
    override fun onError(error: ConnectionError) {
        when (error.code) {
            "C20" -> {
                // SDK not initialized
                Log.e(TAG, "Please call TaplinkSDK.init() first")
            }
            "C41" -> {
                // Invalid protocol (e.g., LAN without host/port)
                Log.e(TAG, "Invalid connection configuration")
            }
            else -> {
                Log.e(TAG, "Connection error: ${error.message}")
            }
        }
    }
    
    // ... other callbacks
})
```

## Troubleshooting

### Issue: "No connection config provided" Error

**Cause**: Using old SDK version or not setting `connectionMode` in `TaplinkConfig`

**Solution**:
```kotlin
// Ensure connectionMode is set
val config = TaplinkConfig(...)
    .setConnectionMode(ConnectionMode.CABLE) // Add this line

TaplinkSDK.init(context, config)
```

### Issue: LAN Connection Fails with Default Config

**Cause**: LAN mode requires explicit host and port configuration

**Solution**:
```kotlin
// Always provide host/port for LAN connections
val lanConfig = ConnectionConfig()
    .setHost("your_device_ip")
    .setPort(8443)

TaplinkSDK.connect(lanConfig, connectionListener)
```

### Issue: Cable Detection Not Working

**Cause**: Default CABLE config uses AUTO detection which may fail

**Solution**:
```kotlin
// Try specific cable protocol if AUTO fails
val cableConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.USB_AOA) // or USB_VSP, RS232

TaplinkSDK.connect(cableConfig, connectionListener)
```

---

**Version**: 1.2.0  
**Updated**: 2025-12-22