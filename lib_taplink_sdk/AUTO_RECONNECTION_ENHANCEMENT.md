# Auto-Reconnection and Persistence Enhancement

## 📋 Overview

Enhanced the Taplink SDK with comprehensive auto-reconnection and connection persistence capabilities, including device ID based mDNS discovery and automatic IP/port change handling.

## 🎯 Key Features

### 1. Connection Configuration Persistence
- **Automatic saving** of successful connection configurations
- **Device ID tracking** for targeted reconnection
- **Persistent storage** using Android SharedPreferences
- **Auto-connect on app startup** with saved configurations

### 2. Device ID Based mDNS Discovery
- **Targeted device discovery** by specific device ID
- **Service attribute parsing** from mDNS TXT records
- **Service name pattern matching** for device identification
- **Continuous monitoring** for service changes

### 3. Enhanced Auto-Reconnection
- **IP/Port change detection** and automatic reconnection
- **Intelligent retry logic** with configurable limits
- **Service address monitoring** in LAN mode
- **Graceful fallback** when auto-reconnection fails

## 🏗️ Architecture

### New Components

#### 1. ConnectionPersistence
```kotlin
class ConnectionPersistence(context: Context) {
    // Save/load connection configurations
    fun saveConnectionConfig(config: ConnectionConfig?, deviceId: String?)
    fun getLastConnectionConfig(): ConnectionConfig?
    
    // Device service information management
    fun saveDeviceServiceInfo(deviceServiceInfo: DeviceServiceInfo)
    fun getDeviceServiceInfo(deviceId: String): DeviceServiceInfo?
    
    // Auto-connect settings
    fun setAutoConnectEnabled(enabled: Boolean)
    fun isAutoConnectEnabled(): Boolean
}
```

#### 2. DeviceDiscoveryManager
```kotlin
class DeviceDiscoveryManager(context: Context) {
    // Device discovery by ID
    suspend fun discoverDevice(targetDeviceId: String?): DeviceServiceInfo?
    
    // Continuous monitoring
    fun startMonitoring(listener: ServiceChangeListener)
    fun stopMonitoring()
    
    // Service change detection
    interface ServiceChangeListener {
        fun onServiceFound(deviceServiceInfo: DeviceServiceInfo)
        fun onServiceLost(deviceId: String)
        fun onServiceChanged(deviceId: String, oldInfo: DeviceServiceInfo, newInfo: DeviceServiceInfo)
    }
}
```

#### 3. Enhanced ReconnectManager
```kotlin
class ReconnectManager(context: Context, config: TaplinkConfig, reconnectAction: ...) {
    // Persistence integration
    fun shouldAutoConnect(): Boolean
    fun getAutoConnectConfig(): ConnectionConfig?
    
    // Device service management
    fun updateDeviceServiceInfo(deviceId: String, serviceName: String, host: String, port: Int)
    fun getDeviceServiceInfo(deviceId: String): DeviceServiceInfo?
    
    // Enhanced reconnection
    fun onAddressChanged(newConnectionConfig: ConnectionConfig, listener: ConnectionListener)
}
```

## 🔄 Connection Flow

### 1. Initial Connection
```kotlin
// User initiates connection
taplinkApi.connect(connectionConfig, listener)

// On successful connection:
// 1. Save connection config to persistent storage
// 2. Save device ID and service information
// 3. Enable auto-connect for future app launches
// 4. Start mDNS monitoring for service changes
```

### 2. Auto-Connect on App Startup
```kotlin
// During SDK initialization:
taplinkApi.init(context, config)

// Automatically checks for saved connection:
// 1. Check if auto-connect is enabled
// 2. Load last connection configuration
// 3. Attempt automatic connection
// 4. Fall back to manual connection if auto-connect fails
```

### 3. Service Address Change Handling
```kotlin
// mDNS monitoring detects IP/port change:
// 1. DeviceDiscoveryManager detects service change
// 2. WebSocketClientKernel receives onServiceAddressChanged callback
// 3. ReconnectManager updates connection config
// 4. Automatic reconnection to new address
// 5. Update persistent storage with new address
```

## 📱 Usage Examples

### Basic Connection with Auto-Reconnection
```kotlin
// Initialize SDK
val config = TaplinkConfig.builder()
    .setAppId("your_app_id")
    .setSecretKey("your_secret_key")
    .setAutoReconnect(true)
    .setMaxRetryCount(5)
    .setRetryDelayMs(3000)
    .build()

taplinkApi.init(context, config)

// Connect (configuration will be automatically saved)
val connectionConfig = ConnectionConfig()
    .setHost("192.168.1.100")
    .setPort(8443)

taplinkApi.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        // Connection successful - config automatically saved
        // Auto-connect enabled for future app launches
    }
    
    override fun onError(error: ConnectionError) {
        // Handle connection error
    }
    
    override fun onDisconnected(reason: String) {
        // Auto-reconnection will be attempted if enabled
    }
    
    override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
        // Show reconnection progress to user
    }
})
```

### LAN Mode with Device ID Discovery
```kotlin
// Connect using device ID (mDNS discovery)
val connectionConfig = ConnectionConfig()
    .setCableProtocol("lan://deviceId123")  // Specify target device ID

taplinkApi.connect(connectionConfig, listener)

// SDK will:
// 1. Use mDNS to discover device with ID "deviceId123"
// 2. Connect to discovered IP/port
// 3. Monitor for IP/port changes
// 4. Automatically reconnect if address changes
```

### Auto-Connect Without Parameters
```kotlin
// On subsequent app launches, connect without parameters
// SDK will use saved configuration automatically
taplinkApi.connect(null, listener)  // Uses last saved config

// Or check if auto-connect is available
if (reconnectManager.shouldAutoConnect()) {
    val savedConfig = reconnectManager.getAutoConnectConfig()
    taplinkApi.connect(savedConfig, listener)
}
```

### Manual Disconnect (Clears Auto-Connect)
```kotlin
// Manual disconnect clears saved configuration
taplinkApi.disconnect()

// This will:
// 1. Disconnect current connection
// 2. Clear saved connection configuration
// 3. Disable auto-connect for future app launches
// 4. Stop mDNS monitoring
```

## 🔧 Configuration Options

### TaplinkConfig Auto-Reconnection Settings
```kotlin
val config = TaplinkConfig.builder()
    .setAutoReconnect(true)           // Enable auto-reconnection
    .setMaxRetryCount(5)              // Maximum retry attempts
    .setRetryDelayMs(3000)            // Delay between retries (3 seconds)
    .build()
```

### ConnectionConfig LAN Mode Settings
```kotlin
val connectionConfig = ConnectionConfig()
    .setCableProtocol("lan://auto")           // Auto-discover any device
    .setCableProtocol("lan://deviceId123")    // Discover specific device ID
    .setCableProtocol("lan://192.168.1.100:8443")  // Direct IP/port
```

## 📊 Persistence Data Structure

### Saved Connection Configuration
```json
{
  "last_connection_config": {
    "cableProtocol": "lan://deviceId123",
    "host": "192.168.1.100",
    "port": 8443,
    "timeout": 30000
  },
  "connected_device_id": "deviceId123",
  "auto_connect_enabled": true
}
```

### Device Service Information
```json
{
  "device_service_info_deviceId123": {
    "deviceId": "deviceId123",
    "serviceName": "taplink-deviceId123",
    "host": "192.168.1.100",
    "port": 8443,
    "attributes": {
      "deviceId": "deviceId123",
      "version": "1.0.0"
    },
    "lastSeen": 1703123456789
  }
}
```

## 🔍 mDNS Service Discovery

### Service Type
- **Service Type**: `_taplink._tcp`
- **Protocol**: DNS-SD (Bonjour/Zeroconf)

### Device ID Extraction Methods
1. **TXT Record Attributes**: `deviceId` or `device_id` in service attributes
2. **Service Name Patterns**:
   - `taplink-{deviceId}`
   - `taplink_{deviceId}`
   - `device-{deviceId}`
   - `{deviceId}-taplink`
3. **Fallback**: Use service name as device ID

### Example mDNS Service Registration
```kotlin
// On the device/server side, register service with device ID
val serviceInfo = NsdServiceInfo().apply {
    serviceName = "taplink-deviceId123"
    serviceType = "_taplink._tcp"
    port = 8443
    
    // Add device ID as TXT record
    setAttribute("deviceId", "deviceId123")
    setAttribute("version", "1.0.0")
}

nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
```

## 🚨 Error Handling

### Auto-Connect Failure
```kotlin
// Auto-connect failures are handled gracefully:
// 1. Log the failure reason
// 2. Don't notify application layer (silent failure)
// 3. Allow manual connection attempts
// 4. Preserve saved configuration for future attempts
```

### Service Discovery Timeout
```kotlin
// If device discovery times out:
// 1. Fall back to direct IP connection if available
// 2. Return appropriate error to application
// 3. Maintain saved configuration for future attempts
```

### Address Change Detection
```kotlin
// When service address changes:
// 1. Detect change through mDNS monitoring
// 2. Update saved configuration
// 3. Trigger immediate reconnection
// 4. Notify application of reconnection attempt
```

## 📈 Benefits

### For Developers
1. **Simplified Integration**: Auto-reconnection works out of the box
2. **Robust Connection Management**: Handles network changes gracefully
3. **Persistent Sessions**: Users don't need to reconfigure on app restart
4. **Device Discovery**: Easy connection to devices without knowing IP addresses

### For End Users
1. **Seamless Experience**: Automatic reconnection on network changes
2. **Quick App Startup**: Automatic connection to last used device
3. **Network Resilience**: Handles IP address changes transparently
4. **Reduced Configuration**: Device discovery eliminates manual IP entry

## 🔄 Migration from Previous Version

### Existing Code Compatibility
```kotlin
// Existing code continues to work unchanged
taplinkApi.connect(connectionConfig, listener)  // Still works

// New features are opt-in
taplinkApi.connect(null, listener)  // Uses saved config (new feature)
```

### Gradual Adoption
1. **Phase 1**: Update SDK, existing functionality unchanged
2. **Phase 2**: Enable auto-reconnection in TaplinkConfig
3. **Phase 3**: Use device ID based discovery for LAN connections
4. **Phase 4**: Implement auto-connect on app startup

## 🎯 Best Practices

### 1. Connection Configuration
```kotlin
// Always enable auto-reconnection for production apps
val config = TaplinkConfig.builder()
    .setAutoReconnect(true)
    .setMaxRetryCount(3)        // Reasonable retry limit
    .setRetryDelayMs(2000)      // 2-second delay between retries
    .build()
```

### 2. Error Handling
```kotlin
override fun onError(error: ConnectionError) {
    when (error.code) {
        "C35" -> {
            // No devices found via mDNS
            // Show device discovery UI or manual IP entry
        }
        "C25" -> {
            // Connection failed
            // Check network connectivity
        }
    }
}
```

### 3. User Experience
```kotlin
override fun onReconnecting(currentAttempt: Int, maxAttempts: Int) {
    // Show reconnection progress to user
    showReconnectionDialog("Reconnecting... ($currentAttempt/$maxAttempts)")
}

override fun onConnected(deviceId: String, taproVersion: String) {
    // Hide reconnection dialog
    hideReconnectionDialog()
    
    // Show connection success
    showConnectionStatus("Connected to device: $deviceId")
}
```

## 📚 Related Documentation

- [Connection Configuration Guide](CONNECTION_CONFIG_GUIDE.md)
- [mDNS Service Discovery](MDNS_DISCOVERY_GUIDE.md)
- [Error Handling Reference](ERROR_HANDLING_REFERENCE.md)
- [Auto-Reconnection Examples](AUTO_RECONNECTION_EXAMPLES.md)

---

**Enhancement Date**: 2025-12-21  
**Enhancement Team**: TaPro Team  
**Version**: 1.3.0