# Taplink SDK Demo App

USB AOA (Android Open Accessory) protocol test application with Host and Accessory mode demos.

## Features

### Main Screen (MainActivity)
- Select USB Host mode testing
- Select USB Accessory mode testing

### USB Host Mode (UsbHostDemoActivity)
Acts as the USB Host and actively connects to a USB Accessory device.

**Implementation:** Uses `TaplinkSDK` (`TaplinkClient`)

**Features:**
- Connect to a USB Accessory device
- Send test data
- Receive data
- Disconnect
- Display real-time logs

**Use cases:**
- Use an Android device as the Host
- Connect to another Android device in Accessory mode
- Connect to hardware that supports the AOA protocol

### USB Accessory Mode (UsbAccessoryDemoActivity)
Acts as the USB Accessory and waits for a USB Host device to connect.

**Implementation:** Uses `UsbStandardAccessoryKernel` directly

**Features:**
- Wait for a Host device to connect
- Send test data
- Receive data
- Disconnect
- Display real-time logs

**Use cases:**
- Use an Android device as the Accessory
- Wait for another Android device in Host mode to connect
- Wait for a Host device that supports the AOA protocol to connect

## Test Procedure

### Prerequisites
1. Prepare two Android devices, or one Android device and one AOA-compatible Host
2. Prepare a USB OTG cable
3. Install this demo application on both devices

### Host Testing
1. Launch the application on device A
2. Select "USB Host Mode Test"
3. Connect the USB cable to the Accessory device
4. Tap the "Connect" button
5. Grant USB permission
6. Send test data after the connection succeeds

### Accessory Testing
1. Launch the application on device B
2. Select "USB Accessory Mode Test"
3. Tap the "Connect" button to start listening
4. Connect the USB cable to the Host device
5. Grant USB permission
6. Send test data after the connection succeeds

## Configuration

### Host Mode Initialization (Using TaplinkSDK)
```kotlin
// Create TaplinkConfig
val config = TaplinkConfig.create(
    context = this,
    appId = "demo_app_id",
    appSecretKey = "demo_secret_key"
)

// Create TaplinkClient
val taplinkClient = TaplinkClient(config)

// Set the connection listener
taplinkClient.setConnectionListener(object : ConnectionListener {
    override fun onStatusChanged(status: ConnectionStatus) {
        // Handle connection status changes
    }
    override fun onError(errorCode: String, errorMessage: String) {
        // Handle connection errors
    }
})

// Set the data receiver
taplinkClient.setDataReceiver { data ->
    // Handle received data
}
```

### Accessory Mode Initialization
```kotlin
// Create PendingIntent
val pendingIntent = UsbStandardAccessoryKernel.createRequestPermissionPendingIntent(this)

// Create standard USB information
val usbStandardInfo = UsbStandardProtocol.UsbStandardInfo(
    manufacturer = "SUNMI",
    model = "TAPLINK_SDK",
    description = "Taplink SDK Demo",
    version = "1.0",
    uri = "https://www.sunmi.com",
    serial = "12345"
)

// Create the Accessory Kernel
val accessoryKernel = UsbStandardAccessoryKernel(
    context = this,
    usbStandardInfo = usbStandardInfo,
    permissionAction = UsbStandardAccessoryKernel.ACTION_USB_STANDARD_ACCESSORY_KERNEL,
    permissionPendingIntent = pendingIntent
)

// Set the data receiver
accessoryKernel.setDataReceiver { data ->
    // Handle received data
}
```

### Connection and Data Transfer

#### Host Mode (Using TaplinkSDK)
```kotlin
// Create the connection configuration
val connectionConfig = ConnectionConfig(
    cableProtocol = CableProtocol.USB_AOA
)

// Connect
taplinkClient.connect(connectionConfig)

// Send data
taplinkClient.sendRawData(data) { success, errorCode, errorMessage ->
    if (success) {
        // Send succeeded
    } else {
        // Send failed
    }
}

// Disconnect
taplinkClient.disconnect()

// Release resources
taplinkClient.release()
```

#### Accessory Mode (Using the Kernel)
```kotlin
// Create the protocol parsing result
val parseResult = ProtocolParseResult.UsbProtocol()

// Create the connection callback
val connectionCallback = object : ConnectionCallback {
    override fun onConnected(extraInfoMap: Map<String, String?>?) {
        // Connected
    }
    override fun onWaitingConnect() {
        // Waiting for a connection
    }
    override fun onDisconnected(code: String, msg: String) {
        // Disconnected
    }
}

// Connect
accessoryKernel.connect(parseResult, connectionCallback)

// Send data
accessoryKernel.sendData(data, callback)

// Disconnect
accessoryKernel.disconnect()
```

**Note:**
- The standard USB information on the Host and Accessory must match
- The AOA protocol information sent by the Host must match the Accessory configuration

### AndroidManifest Configuration
- USB permission: `android.permission.USB_PERMISSION`
- USB feature: `android.hardware.usb.accessory`
- Accessory mode must respond to the `USB_ACCESSORY_ATTACHED` Intent

### USB Accessory Filter
Location: `app/src/main/res/xml/accessory_filter.xml`

```xml
<usb-accessory
    manufacturer="Sunmi"
    model="TaplinkDemo"
    version="1.0" />
```

**Note:** The AOA protocol information sent by the Host must match this configuration.

## Logging

The demo application displays the following logs in real time:
- Connection status changes
- Permission requests and grants
- Data sent and received
- Error messages
- Disconnection events

Log format: `[HH:mm:ss.SSS] log message`

## Frequently Asked Questions

### 1. Connection failed
- Check that the USB cable is working properly
- Check that USB permission was granted
- Check that the Accessory filter configuration matches
- Check the error code and message in the logs

### 2. Permission denied
- Reconnect and grant permission
- Check USB permissions in the system settings

### 3. Device not detected
- Confirm that the device supports USB OTG
- Try a different USB cable
- Restart the application or device

## Error Code Reference

- E251: USB device not found
- E252: USB permission denied
- E253: Device disconnected while requesting permission
- E213: Connection timed out (Accessory mode)
- E304: Data transmission failed

## Technical Architecture

### Modules
- `lib_taplink_sdk`: SDK module (used in Host mode)
- `lib_taplink_communication`: Communication layer module (used in Accessory mode)
- `lib_log`: Logging module

### Core Classes

#### Host Mode (SDK Layer)
- `TaplinkClient`: SDK client
- `TaplinkConfig`: SDK configuration
- `ConnectionConfig`: Connection configuration
- `ConnectionListener`: Connection status listener
- `CableProtocol`: Cable protocol enum

#### Accessory Mode (Kernel Layer)
- `UsbStandardAccessoryKernel`: USB Accessory mode kernel
- `UsbStandardProtocol`: USB protocol utility
- `ConnectionCallback`: Connection status callback interface
- `InnerCallback`: Data transmission callback interface

### Usage Flow

#### Host Mode (SDK Layer)
```kotlin
// 1. Create TaplinkClient
val config = TaplinkConfig.create(context, appId, appSecretKey)
val taplinkClient = TaplinkClient(config)

// 2. Set listeners
taplinkClient.setConnectionListener(...)
taplinkClient.setDataReceiver { data -> ... }

// 3. Connect
val connectionConfig = ConnectionConfig(cableProtocol = CableProtocol.USB_AOA)
taplinkClient.connect(connectionConfig)

// 4. Send data
taplinkClient.sendRawData(data) { success, errorCode, errorMessage -> ... }

// 5. Disconnect
taplinkClient.disconnect()

// 6. Release resources
taplinkClient.release()
```

#### Accessory Mode (Kernel Layer)
```kotlin
// 1. Create a Kernel instance
val kernel = UsbStandardAccessoryKernel(...)

// 2. Set the data receiver
kernel.registerDataReceiver { data -> ... }

// 3. Create the protocol parsing result and connection callback
val parseResult = ProtocolParseResult.UsbProtocol()
val connectionCallback = object : ConnectionCallback { ... }

// 4. Connect
kernel.connect(parseResult, connectionCallback)

// 5. Send data
kernel.sendData(data, callback)

// 6. Disconnect
kernel.disconnect()

// 7. Release resources
kernel.release()
```

### Key Features
- **Host mode uses the SDK layer:** Connect and transfer data through `TaplinkClient`
- **Accessory mode uses the Kernel layer:** Test directly with the underlying Kernel
- **Complete lifecycle management:** Includes Session, Token, Mutex, and related mechanisms
- **Real-time data transfer:** Supports bidirectional data transmission
- **Detailed logging:** Simplifies debugging and issue diagnosis

## Development Notes

### Adding a New Feature
1. Add UI controls to the corresponding Activity
2. Define the controls in the layout file
3. Implement the business logic
4. Update log output

### Debugging Tips
- Use `adb logcat` to view detailed logs
- Enable USB debugging
- Perform end-to-end testing with two devices

## Version Information

- Version: 1.0
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 36 (Android 14)
- Kotlin version: 1.9+

## License

Copyright © 2025 Sunmi Tapro Team
