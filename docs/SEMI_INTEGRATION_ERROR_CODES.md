# Semi-Integration Error Codes

## Overview

This document defines comprehensive error codes for Taplink SDK semi-integration mode, supporting APP_TO_APP, LAN, and CABLE connection modes. Error codes are categorized for different user types: ISV (Independent Software Vendor) and ISO (Independent Sales Organization).

## Error Code Structure

### Format: `[Category][SubCategory][Number]`

- **Category**: Connection type (A=APP_TO_APP, L=LAN, C=CABLE, G=General)
- **SubCategory**: Error type (0=General, 1=Connection, 2=Authentication, 3=Protocol, 4=Device, 5=Network, 6=Configuration, 7=Permission, 8=Timeout, 9=Data)
- **Number**: Sequential error number (01-99)

### Example: `A1001` = APP_TO_APP Connection Error #001

## General Error Codes (G)

### G0xxx - General System Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| G0001 | SDK_NOT_INITIALIZED | SDK not initialized | SDK not initialized, please call init() first | System not ready, please restart application | Call TaplinkSDK.init() |
| G0002 | INVALID_CONFIGURATION | Invalid configuration | Invalid SDK configuration provided | Configuration error, contact support | Check TaplinkConfig parameters |
| G0003 | INSUFFICIENT_PERMISSIONS | Insufficient permissions | App lacks required permissions | Permission denied, check app settings | Request required permissions |
| G0004 | UNSUPPORTED_OPERATION | Unsupported operation | Operation not supported in current mode | Feature not available | Use supported operations |
| G0005 | INTERNAL_ERROR | Internal SDK error | Internal SDK error occurred | System error, please try again | Contact technical support |

### G1xxx - Connection Management Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| G1001 | CONNECTION_ALREADY_EXISTS | Connection already active | Connection already established | Already connected to device | Disconnect first or use existing connection |
| G1002 | NO_CONNECTION_AVAILABLE | No active connection | No active connection found | Not connected to payment device | Establish connection first |
| G1003 | CONNECTION_LIMIT_EXCEEDED | Too many connections | Maximum connection limit reached | Too many active connections | Close unused connections |
| G1004 | CONNECTION_REJECTED | Connection rejected | Connection rejected by remote device | Device rejected connection | Check device status and retry |
| G1005 | CONNECTION_LOST | Connection lost unexpectedly | Connection lost during operation | Connection interrupted | Check network/cable and reconnect |

### G2xxx - Authentication Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| G2001 | INVALID_CREDENTIALS | Invalid credentials | Invalid appId or secretKey | Authentication failed | Verify credentials |
| G2002 | AUTHENTICATION_FAILED | Authentication failed | Authentication with device failed | Login failed | Check credentials and retry |
| G2003 | SESSION_EXPIRED | Session expired | Authentication session expired | Session timeout | Re-authenticate |
| G2004 | UNAUTHORIZED_ACCESS | Unauthorized access | Access denied for this operation | Not authorized for this action | Contact administrator |
| G2005 | CERTIFICATE_ERROR | Certificate error | SSL/TLS certificate validation failed | Security certificate error | Check certificate configuration |

## APP_TO_APP Mode Errors (A)

### A1xxx - APP_TO_APP Connection Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| A1001 | TAPRO_APP_NOT_FOUND | TaPro app not installed | TaPro application not found on device | Payment app not installed | Install TaPro application |
| A1002 | TAPRO_APP_OUTDATED | TaPro app version incompatible | TaPro app version not supported | Payment app needs update | Update TaPro application |
| A1003 | INTENT_FAILED | Intent communication failed | Failed to communicate with TaPro app | Communication error with payment app | Restart both applications |
| A1004 | TAPRO_APP_BUSY | TaPro app busy | TaPro app is processing another request | Payment app is busy | Wait and retry |
| A1005 | PACKAGE_VERIFICATION_FAILED | Package verification failed | TaPro app package verification failed | Security verification failed | Reinstall TaPro app |

### A3xxx - APP_TO_APP Protocol Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| A3001 | INVALID_INTENT_DATA | Invalid intent data | Invalid data in intent communication | Invalid request format | Check request parameters |
| A3002 | INTENT_TIMEOUT | Intent timeout | Intent communication timeout | Request timeout | Increase timeout or retry |
| A3003 | RESPONSE_FORMAT_ERROR | Invalid response format | Invalid response from TaPro app | Invalid response received | Check TaPro app version |
| A3004 | PROTOCOL_VERSION_MISMATCH | Protocol version mismatch | Protocol version not supported | Communication protocol error | Update SDK or TaPro app |
| A3005 | DATA_SERIALIZATION_ERROR | Data serialization error | Failed to serialize request data | Data format error | Check request data format |

## LAN Mode Errors (L)

### L1xxx - LAN Connection Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| L1001 | DEVICE_NOT_FOUND | Device not found on network | Payment device not found on network | Device not detected | Check device network connection |
| L1002 | CONNECTION_REFUSED | Connection refused | Device refused connection | Connection denied by device | Check device settings |
| L1003 | NETWORK_UNREACHABLE | Network unreachable | Cannot reach payment device | Network connection failed | Check network connectivity |
| L1004 | INVALID_IP_ADDRESS | Invalid IP address | Invalid device IP address | Invalid network address | Verify device IP address |
| L1005 | PORT_UNAVAILABLE | Port unavailable | Cannot connect to device port | Network port blocked | Check firewall settings |

### L5xxx - LAN Network Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| L5001 | WIFI_DISCONNECTED | WiFi disconnected | WiFi connection lost | Network disconnected | Reconnect to WiFi |
| L5002 | NETWORK_TIMEOUT | Network timeout | Network operation timeout | Network response timeout | Check network stability |
| L5003 | DNS_RESOLUTION_FAILED | DNS resolution failed | Cannot resolve device hostname | Name resolution failed | Use IP address instead |
| L5004 | SSL_HANDSHAKE_FAILED | SSL handshake failed | SSL/TLS handshake failed | Secure connection failed | Check SSL configuration |
| L5005 | WEBSOCKET_ERROR | WebSocket error | WebSocket connection error | Real-time connection failed | Check WebSocket support |

### L6xxx - LAN Configuration Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| L6001 | MISSING_HOST_CONFIG | Missing host configuration | Device host not configured | Network address not set | Configure device IP address |
| L6002 | MISSING_PORT_CONFIG | Missing port configuration | Device port not configured | Network port not set | Configure device port |
| L6003 | INVALID_HOST_FORMAT | Invalid host format | Invalid host address format | Invalid network address format | Use valid IP or hostname |
| L6004 | INVALID_PORT_RANGE | Invalid port range | Port number out of valid range | Invalid port number | Use port 8443-8453 |
| L6005 | MDNS_DISCOVERY_FAILED | mDNS discovery failed | Device discovery failed | Automatic detection failed | Configure manually |

## CABLE Mode Errors (C)

### C1xxx - CABLE Connection Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| C1001 | CABLE_NOT_CONNECTED | Cable not connected | USB cable not connected | Cable connection required | Connect USB cable |
| C1002 | DEVICE_NOT_DETECTED | Device not detected | Payment device not detected | Device not found | Check cable and device |
| C1003 | USB_PERMISSION_DENIED | USB permission denied | USB device access denied | Permission required | Grant USB permission |
| C1004 | INCOMPATIBLE_DEVICE | Incompatible device | Connected device not supported | Device not compatible | Use supported device |
| C1005 | CABLE_DISCONNECTED | Cable disconnected | USB cable disconnected during operation | Cable unplugged | Reconnect cable |

### C3xxx - CABLE Protocol Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| C3001 | PROTOCOL_DETECTION_FAILED | Protocol detection failed | Cannot detect cable protocol | Communication protocol error | Try manual protocol selection |
| C3002 | AOA_INITIALIZATION_FAILED | AOA initialization failed | USB AOA mode initialization failed | USB connection setup failed | Check USB AOA support |
| C3003 | VSP_COMMUNICATION_ERROR | VSP communication error | Virtual Serial Port error | Serial communication error | Check VSP driver |
| C3004 | RS232_PORT_ERROR | RS232 port error | Serial port communication error | Serial port unavailable | Check serial port settings |
| C3005 | PROTOCOL_SWITCH_FAILED | Protocol switch failed | Failed to switch cable protocol | Protocol change failed | Reconnect with specific protocol |

### C4xxx - CABLE Device Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| C4001 | DEVICE_BUSY | Device busy | Payment device is busy | Device processing other request | Wait and retry |
| C4002 | DEVICE_ERROR | Device error | Payment device reported error | Device malfunction | Check device status |
| C4003 | FIRMWARE_INCOMPATIBLE | Firmware incompatible | Device firmware not supported | Device software incompatible | Update device firmware |
| C4004 | DEVICE_LOCKED | Device locked | Payment device is locked | Device access restricted | Unlock device |
| C4005 | POWER_INSUFFICIENT | Insufficient power | USB power insufficient for device | Power supply issue | Use powered USB hub |

### C7xxx - CABLE Permission Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| C7001 | USB_PERMISSION_REQUIRED | USB permission required | USB device permission required | Device access permission needed | Grant USB permission |
| C7002 | SERIAL_PORT_PERMISSION | Serial port permission | Serial port access permission required | Serial access permission needed | Grant serial port permission |
| C7003 | DEVICE_ACCESS_DENIED | Device access denied | Cannot access USB device | Device access blocked | Check device permissions |
| C7004 | DRIVER_PERMISSION_ERROR | Driver permission error | USB driver permission error | Driver access denied | Install/update USB drivers |
| C7005 | SYSTEM_PERMISSION_DENIED | System permission denied | System-level permission denied | System access restricted | Run with elevated permissions |

## Payment Transaction Errors (T)

### T1xxx - Transaction Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| T1001 | TRANSACTION_FAILED | Transaction failed | Payment transaction failed | Transaction unsuccessful | Retry transaction |
| T1002 | TRANSACTION_CANCELLED | Transaction cancelled | Transaction cancelled by user | Payment cancelled | Start new transaction |
| T1003 | TRANSACTION_TIMEOUT | Transaction timeout | Transaction timeout | Payment timeout | Retry with longer timeout |
| T1004 | INVALID_AMOUNT | Invalid amount | Invalid transaction amount | Invalid payment amount | Check amount format |
| T1005 | DUPLICATE_TRANSACTION | Duplicate transaction | Duplicate transaction detected | Transaction already processed | Use unique transaction ID |

### T2xxx - Card/Payment Method Errors

| Code | Name | Description | ISV Message | ISO Message | Suggested Action |
|------|------|-------------|-------------|-------------|------------------|
| T2001 | CARD_READ_ERROR | Card read error | Cannot read payment card | Card reading failed | Try again or use different card |
| T2002 | CARD_NOT_SUPPORTED | Card not supported | Payment card type not supported | Card type not accepted | Use supported card type |
| T2003 | CARD_EXPIRED | Card expired | Payment card has expired | Expired card | Use valid card |
| T2004 | INSUFFICIENT_FUNDS | Insufficient funds | Insufficient funds on card | Payment declined - insufficient funds | Use different payment method |
| T2005 | CARD_BLOCKED | Card blocked | Payment card is blocked | Card blocked by issuer | Contact card issuer |

## ISV-Specific Error Handling

### Error Response Format for ISVs

```json
{
  "success": false,
  "errorCode": "L1001",
  "errorName": "DEVICE_NOT_FOUND",
  "message": "Payment device not found on network",
  "details": {
    "category": "LAN_CONNECTION",
    "severity": "ERROR",
    "retryable": true,
    "suggestedAction": "Check device network connection",
    "technicalDetails": "mDNS discovery timeout after 5 seconds"
  },
  "timestamp": "2025-12-22T10:30:00Z",
  "traceId": "trace-12345"
}
```

### ISV Error Handling Best Practices

1. **Implement Retry Logic**
   ```kotlin
   fun handleError(error: ConnectionError) {
       when (error.code) {
           "L5002", "C1005" -> {
               // Retryable errors
               scheduleRetry(error)
           }
           "G2001", "T2003" -> {
               // Non-retryable errors
               showUserError(error)
           }
       }
   }
   ```

2. **Error Categorization**
   ```kotlin
   enum class ErrorSeverity {
       INFO,     // Informational, no action needed
       WARNING,  // Warning, operation may continue
       ERROR,    // Error, operation failed
       CRITICAL  // Critical, system may be unstable
   }
   ```

## ISO-Specific Error Handling

### Simplified Error Messages for ISOs

ISOs receive simplified, user-friendly error messages without technical details:

```json
{
  "success": false,
  "message": "Device not detected",
  "action": "Check cable and device",
  "canRetry": true
}
```

### ISO Error Categories

1. **Device Issues** - Hardware/connection problems
2. **Network Issues** - Connectivity problems  
3. **Payment Issues** - Transaction-related problems
4. **System Issues** - App/software problems

### ISO Error Handling Guidelines

```kotlin
class ISOErrorHandler {
    fun handleError(error: ConnectionError): UserMessage {
        return when (error.code.substring(0, 1)) {
            "A" -> UserMessage("Check payment app", "Restart application")
            "L" -> UserMessage("Check network", "Verify WiFi connection")
            "C" -> UserMessage("Check cable", "Reconnect device")
            "T" -> UserMessage("Payment failed", "Try again")
            else -> UserMessage("System error", "Contact support")
        }
    }
}
```

## Error Code Usage Examples

### Connection Error Handling

```kotlin
TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
    override fun onError(error: ConnectionError) {
        when (error.code) {
            "A1001" -> {
                // TaPro app not found
                showInstallDialog()
            }
            "L1001" -> {
                // Device not found on network
                startDeviceDiscovery()
            }
            "C1001" -> {
                // Cable not connected
                showCableConnectionDialog()
            }
            "G2001" -> {
                // Invalid credentials
                showCredentialsDialog()
            }
        }
    }
})
```

### Payment Error Handling

```kotlin
TaplinkSDK.execute(paymentRequest, object : PaymentCallback {
    override fun onFailure(error: PaymentError) {
        when (error.code) {
            "T1001" -> {
                // Transaction failed
                showRetryDialog()
            }
            "T2001" -> {
                // Card read error
                showCardRetryDialog()
            }
            "T2004" -> {
                // Insufficient funds
                showInsufficientFundsDialog()
            }
        }
    }
})
```

## Error Logging and Analytics

### Error Tracking for ISVs

```kotlin
class ErrorTracker {
    fun trackError(error: ConnectionError) {
        val errorData = mapOf(
            "errorCode" to error.code,
            "errorName" to error.name,
            "connectionMode" to getCurrentConnectionMode(),
            "deviceType" to getDeviceType(),
            "sdkVersion" to TaplinkSDK.getVersion(),
            "timestamp" to System.currentTimeMillis()
        )
        
        // Send to analytics service
        Analytics.track("sdk_error", errorData)
    }
}
```

### Error Reporting for ISOs

```kotlin
class ISOErrorReporter {
    fun reportError(error: ConnectionError) {
        // Simplified error reporting for ISOs
        val report = ErrorReport(
            category = getErrorCategory(error.code),
            severity = getErrorSeverity(error.code),
            userMessage = getISOMessage(error.code),
            timestamp = System.currentTimeMillis()
        )
        
        // Send simplified report
        sendErrorReport(report)
    }
}
```

## Migration Guide

### Updating Existing Error Handling

1. **Replace Old Error Codes**
   ```kotlin
   // Old
   when (error.code) {
       "CONNECTION_FAILED" -> handleConnectionError()
   }
   
   // New
   when (error.code) {
       "A1003", "L1002", "C1002" -> handleConnectionError()
   }
   ```

2. **Add Error Category Handling**
   ```kotlin
   fun getErrorCategory(code: String): ErrorCategory {
       return when (code.substring(0, 1)) {
           "A" -> ErrorCategory.APP_TO_APP
           "L" -> ErrorCategory.LAN
           "C" -> ErrorCategory.CABLE
           "G" -> ErrorCategory.GENERAL
           "T" -> ErrorCategory.TRANSACTION
           else -> ErrorCategory.UNKNOWN
       }
   }
   ```

## Testing Error Scenarios

### Error Simulation for Testing

```kotlin
class ErrorSimulator {
    fun simulateConnectionError(mode: ConnectionMode) {
        when (mode) {
            ConnectionMode.APP_TO_APP -> throw ConnectionError("A1001", "TaPro app not found")
            ConnectionMode.LAN -> throw ConnectionError("L1001", "Device not found on network")
            ConnectionMode.CABLE -> throw ConnectionError("C1001", "Cable not connected")
        }
    }
}
```

---

**Version**: 1.0.0  
**Updated**: 2025-12-22  
**Authors**: TaPro Team