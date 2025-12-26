# Cable Protocol Auto-Detection

## Overview

The Taplink SDK now supports automatic cable protocol detection when using Cable connection mode. When no specific `cableProtocol` is specified, the SDK will automatically detect and select the best available cable connection method.

## Detection Priority

The SDK detects cable protocols in the following order:

1. **USB AOA (Android Open Accessory)** - Default and preferred
2. **USB VSP (Virtual Serial Port)** - CDC-ACM compatible devices
3. **RS232 (Serial Communication)** - Traditional serial devices

## Usage Examples

### 1. Basic Cable Connection (Auto-Detection)

```kotlin
// Initialize SDK with Cable mode
val config = TaplinkConfig(
    appId = "your_app_id",
    merchantId = "your_merchant_id", 
    secretKey = "your_secret_key"
)
    .setConnectionMode(ConnectionMode.CABLE) // No specific protocol specified

TaplinkSDK.init(context, config)

// Connect without specifying cable protocol - auto-detection will occur
val connectionConfig = ConnectionConfig.createDefault()
TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        // Connection successful with auto-detected protocol
        Log.i("Taplink", "Connected via auto-detected protocol")
    }
    
    override fun onError(error: ConnectionError) {
        Log.e("Taplink", "Connection failed: ${error.message}")
    }
    
    // ... other callbacks
})
```

### 2. Explicit Auto-Detection

```kotlin
// Explicitly request auto-detection
val connectionConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.AUTO)

TaplinkSDK.connect(connectionConfig, connectionListener)
```

### 3. Manual Protocol Selection (Skip Detection)

```kotlin
// Skip auto-detection by specifying exact protocol
val connectionConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.USB_AOA)

TaplinkSDK.connect(connectionConfig, connectionListener)
```

## Detection Logic

### 1. Cache Check
- First checks for recent detection results (within 5 minutes)
- Uses cached result if available and valid

### 2. Previous Connection Check  
- Checks for previous successful connection configuration
- Reuses last working protocol if available

### 3. Hardware Detection
- **USB AOA Detection:**
  - Checks for USB Accessories (AOA mode)
  - Checks for AOA-compatible USB Host devices
  - Looks for Google AOA vendor ID (0x18D1)
  - Checks common payment terminal vendor IDs

- **USB VSP Detection:**
  - Looks for CDC-ACM class devices (Communications Device Class)
  - Checks for FTDI devices (vendor ID 0x0403)
  - Identifies Virtual Serial Port compatible devices

- **RS232 Detection:**
  - Scans common serial device paths:
    - `/dev/ttyS0`, `/dev/ttyS1`, `/dev/ttyS2`, `/dev/ttyS3`
    - `/dev/ttyUSB0`, `/dev/ttyUSB1`
    - `/dev/ttyACM0`, `/dev/ttyACM1`
  - Checks device file permissions (read/write access)

### 4. Fallback Strategy
- If no devices detected, defaults to USB AOA
- Provides confidence score for each detection
- Logs detailed detection information for debugging

## Configuration Persistence

The SDK automatically saves successful connection configurations:

```kotlin
// After successful connection, the detected protocol is saved
// Next connection attempt will prefer the previously successful protocol

// Manual disconnect clears saved configuration
TaplinkSDK.disconnect() // Clears saved config

// Automatic disconnection preserves configuration for reconnection
// Network issues, device unplugged, etc. - config is preserved
```

## Debugging Detection

### Get Device Information

```kotlin
// For debugging, you can get detailed device information
val detector = CableProtocolDetector(context)
val deviceInfo = detector.getDeviceInfo()
Log.d("Taplink", "Available devices:\n$deviceInfo")
```

### Detection Logs

Enable debug logging to see detection details:

```kotlin
val config = TaplinkConfig(...)
    .setLogEnabled(true)
    .setLogLevel(LogLevel.DEBUG)
```

Example log output:
```
D/CableProtocolDetector: Starting cable protocol detection...
D/CableProtocolDetector: Found USB Accessory: Sunmi TapLink Device
I/CableProtocolDetector: USB AOA detected: Sunmi TapLink Device
I/ProtocolBuilder: Cable protocol detection completed: USB_AOA (confidence: 0.9)
```

## Error Handling

### Detection Timeout
- Detection has a 5-second timeout
- Falls back to USB AOA if timeout occurs

### Permission Issues
- USB device access requires user permission
- SDK handles permission requests automatically
- Falls back to next protocol if permission denied

### Device Compatibility
- Not all USB devices support all protocols
- Detection provides confidence scores
- Higher confidence = better compatibility match

## Best Practices

### 1. Let Auto-Detection Work
```kotlin
// ✅ Recommended - Let SDK detect automatically
val config = TaplinkConfig(...)
    .setConnectionMode(ConnectionMode.CABLE)

// ❌ Avoid unless you know the exact protocol
val connectionConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.USB_VSP) // Only if you're certain
```

### 2. Handle Connection Errors Gracefully
```kotlin
override fun onError(error: ConnectionError) {
    when (error.code) {
        "C41" -> {
            // Invalid protocol - detection may have failed
            Log.e("Taplink", "Protocol detection failed, try manual selection")
        }
        "USB_PERMISSION_DENIED" -> {
            // User denied USB permission
            Log.e("Taplink", "USB permission required for cable connection")
        }
        else -> {
            Log.e("Taplink", "Connection error: ${error.message}")
        }
    }
}
```

### 3. Provide User Feedback
```kotlin
override fun onWaitingConnect() {
    // Show progress during detection
    showProgress("Detecting cable connection...")
}

override fun onConnected(deviceId: String, taproVersion: String) {
    hideProgress()
    showSuccess("Connected via cable")
}
```

## Troubleshooting

### Common Issues

1. **No devices detected**
   - Check USB cable connection
   - Verify device is powered on
   - Check USB permissions in Android settings

2. **Detection always returns AOA**
   - May indicate no compatible devices found
   - AOA is the fallback protocol
   - Check device compatibility

3. **Connection fails after detection**
   - Detected protocol may not be fully compatible
   - Try manual protocol selection
   - Check device-specific requirements

### Manual Override

If auto-detection doesn't work for your specific device:

```kotlin
// Override auto-detection for specific devices
val connectionConfig = when (getDeviceModel()) {
    "SpecificDevice1" -> ConnectionConfig().setCableProtocol(CableProtocol.USB_VSP)
    "SpecificDevice2" -> ConnectionConfig().setCableProtocol(CableProtocol.RS232)
    else -> ConnectionConfig() // Use auto-detection
}

TaplinkSDK.connect(connectionConfig, connectionListener)
```

## Technical Details

### Detection Timeout
- Default: 5 seconds
- Configurable in `CableProtocolDetector.DETECTION_TIMEOUT_MS`

### Cache Duration
- Detection results cached for 5 minutes
- Prevents repeated detection for same session

### Supported Vendor IDs
- Google AOA: `0x18D1`
- FTDI: `0x0403`
- Prolific: `0x067B`
- Silicon Labs: `0x10C4`
- QinHeng Electronics: `0x1A86`
- Netchip Technology: `0x0525`

---

**Version**: 1.2.0  
**Updated**: 2025-12-22