# Taplink SDK for Android

[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](https://github.com/sunbay-developer/taplink-sdk-android)
[![Min SDK](https://img.shields.io/badge/minSdk-25-green.svg)](https://developer.android.com/about/versions/android-7.1)
[![Kotlin](https://img.shields.io/badge/kotlin-1.7.10-purple.svg)](https://kotlinlang.org/)

Taplink SDK is a payment integration SDK provided by SUNBAY for Android POS applications. It enables developers to quickly integrate payment capabilities with support for multiple connection modes and comprehensive transaction APIs.

## Features

- **Multiple Connection Modes**
  - App-to-App Mode: Inter-application communication on the same device
  - Cable Mode: USB/Serial cable connection
  - LAN Mode: Network connection (wired/wireless)

- **Complete Payment Functions**
  - Support for various transaction types (Sale, Refund, Void, Auth, etc.)
  - Synchronous and asynchronous calling methods
  - Comprehensive transaction query functionality

- **Developer Friendly**
  - Clean and intuitive API design
  - Comprehensive error handling
  - Detailed integration documentation

## Quick Start

### Requirements

- Android 7.1 (API 25) or higher
- Android Studio Hedgehog or later
- JDK 11 or higher

### Installation

Add the SDK module to your project's `settings.gradle.kts`:

```kotlin
include(":app")
include(":lib_taplink_sdk")
```

Then add the dependency to your app module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":lib_taplink_sdk"))
}
```

**Note:** All required permissions are already declared in the SDK module's manifest and will be automatically merged into your app.

### Basic Integration (3 Steps)

#### Step 1: Initialize SDK

Initialize the SDK in your Application class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = TaplinkConfig()
            .setAppId("your_app_id")
            .setMerchantId("your_merchant_id")
            .setSecretKey("your_secret_key")
        
        TaplinkSDK.init(this, config)
    }
}
```

#### Step 2: Connect to Payment Terminal

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Connect to Tapro (App-to-App mode)
        val connectionConfig = ConnectionConfig()
            .setConnectionMode(ConnectionMode.APP_TO_APP)
        
        TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                // Connection successful
                Toast.makeText(this@MainActivity, 
                    "Connected to Tapro $taproVersion", 
                    Toast.LENGTH_SHORT).show()
            }
            
            override fun onDisconnected(reason: String) {
                // Connection failed
                Toast.makeText(this@MainActivity, 
                    "Connection failed: $reason", 
                    Toast.LENGTH_SHORT).show()
            }
            
            override fun onError(error: ConnectionError) {
                // Connection error
                Toast.makeText(this@MainActivity, 
                    error.message, 
                    Toast.LENGTH_SHORT).show()
            }
        })
    }
}
```

#### Step 3: Process Payment

```kotlin
private fun processPayment() {
    // Get TaplinkClient instance
    val client = TaplinkSDK.getClient()
    
    // Create sale request
    val amount = AmountInfo()
        .setOrderAmount(BigDecimal("10.00"))  // Amount in smallest currency unit
        .setPricingCurrency("USD")
    
    val request = SaleRequest.builder()
        .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")
        .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
        .setAmount(amount)
        .setDescription("Product Purchase")
        .build()
    
    // Execute sale transaction
    client.sale(request, object : PaymentCallback {
        override fun onSuccess(result: PaymentResponse) {
            // Payment successful
            Toast.makeText(this@MainActivity, 
                "Payment successful: ${result.transactionId}", 
                Toast.LENGTH_SHORT).show()
        }
        
        override fun onFailure(error: PaymentError) {
            // Payment failed
            Toast.makeText(this@MainActivity, 
                "Payment failed: ${error.message}", 
                Toast.LENGTH_SHORT).show()
        }
        
        override fun onProgress(event: PaymentEvent) {
            // Update progress UI
            updateProgressUI(event.message)
        }
    })
}
```

That's it! You've completed the basic integration in just 3 steps.

## Connection Modes

### App-to-App Mode

For Android all-in-one devices where POS app and Tapro run on the same device.

```kotlin
val connectionConfig = ConnectionConfig()
    .setConnectionMode(ConnectionMode.APP_TO_APP)

TaplinkSDK.connect(connectionConfig, connectionListener)
```

**Features:**
- Millisecond-level latency
- Automatic detection
- No additional configuration required

### Cable Mode

For traditional POS devices connected to payment terminals via USB or serial cable.

```kotlin
val connectionConfig = ConnectionConfig()
    .setConnectionMode(ConnectionMode.CABLE)
    .setCableProtocol(CableProtocol.AUTO)  // Auto-detect cable type

TaplinkSDK.connect(connectionConfig, connectionListener)
```

**Supported Protocols:**
- USB AOA (Android Open Accessory 2.0)
- USB-VSP (USB Virtual Serial Port)
- RS232 (Standard serial communication)

### LAN Mode

For POS devices connected to payment terminals via local network (wired/wireless).

```kotlin
// First connection: specify IP and port
val connectionConfig = ConnectionConfig()
    .setConnectionMode(ConnectionMode.LAN)
    .setHost("192.168.1.100")
    .setPort(8443)

TaplinkSDK.connect(connectionConfig, connectionListener)

// Subsequent connections: use cached device info
val connectionConfig = ConnectionConfig()
    .setConnectionMode(ConnectionMode.LAN)

TaplinkSDK.connect(connectionConfig, connectionListener)
```

**Features:**
- TLS encryption
- mDNS auto-discovery
- Automatic IP update handling

## Transaction Types

### Sale Transaction

The most common payment transaction type.

```kotlin
val client = TaplinkSDK.getClient()

val amount = AmountInfo()
    .setOrderAmount(BigDecimal("10.00"))
    .setPricingCurrency("USD")

val request = SaleRequest.builder()
    .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setAmount(amount)
    .setDescription("Product Purchase")
    .build()

client.sale(request, paymentCallback)
```

### Refund Transaction

Supports full and partial refunds.

**Referenced Refund** (with original transaction ID):

```kotlin
val amount = AmountInfo()
    .setOrderAmount(BigDecimal("5.00"))
    .setPricingCurrency("USD")

val request = RefundRequest.referencedBuilder()
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setOriginalTransactionId("TXN20231119001")
    .setAmount(amount)
    .setDescription("Product Return")
    .build()

client.refund(request, paymentCallback)
```

**Non-Referenced Refund** (requires card swipe):

```kotlin
val amount = AmountInfo()
    .setOrderAmount(BigDecimal("5.00"))
    .setPricingCurrency("USD")

val request = RefundRequest.nonReferencedBuilder()
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setReferenceOrderId("REFUND_${System.currentTimeMillis()}")
    .setAmount(amount)
    .setDescription("Offline Refund")
    .build()

client.refund(request, paymentCallback)
```

### Void Transaction

Cancel a same-day transaction (faster than refund, no online authorization required).

```kotlin
val request = VoidRequest.builder()
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setOriginalTransactionId("TXN20231119001")
    .setDescription("Cancel Transaction")
    .build()

client.void(request, paymentCallback)
```

**Note:** Void is only available for same-day transactions. Use Refund for cross-day transactions.

### Authorization (Pre-Auth)

Freeze funds without actual deduction, commonly used for hotels and car rentals.

```kotlin
val amount = AuthAmountInfo()
    .setAuthAmount(BigDecimal("50.00"))
    .setPricingCurrency("USD")

val request = AuthRequest.builder()
    .setReferenceOrderId("AUTH_${System.currentTimeMillis()}")
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setAmount(amount)
    .setDescription("Hotel Reservation")
    .build()

client.auth(request, paymentCallback)
```

### Post-Authorization

Complete authorization and perform actual deduction.

```kotlin
val amount = AmountInfo()
    .setOrderAmount(BigDecimal("45.00"))
    .setPricingCurrency("USD")

val request = PostAuthRequest.builder()
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setOriginalTransactionId("TXN20231119002")
    .setAmount(amount)
    .setDescription("Complete Hotel Payment")
    .build()

client.postAuth(request, paymentCallback)
```

### Query Transaction

Query transaction status, especially useful for timeout scenarios.

```kotlin
val query = QueryRequest()
    .setTransactionRequestId("TXN20231119001")

client.query(query, object : PaymentCallback {
    override fun onSuccess(result: PaymentResponse) {
        // Handle query result
        when (result.transactionStatus) {
            "SUCCESS" -> handleSuccess(result)
            "PROCESSING" -> continuePolling()
            "FAILED" -> handleFailure(result)
        }
    }
    
    override fun onFailure(error: PaymentError) {
        // Handle query error
    }
})
```

### Batch Close

End-of-day settlement to close the current batch.

```kotlin
val request = BatchCloseRequest.builder()
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setDescription("Batch Close")
    .build()

client.batchClose(request, object : PaymentCallback {
    override fun onSuccess(result: PaymentResponse) {
        val batchInfo = result.batchCloseInfo
        // Display batch summary
        showBatchSummary(
            batchNo = result.batchNo,
            totalCount = batchInfo?.totalCount ?: 0,
            totalAmount = batchInfo?.totalAmount ?: BigDecimal.ZERO
        )
    }
    
    override fun onFailure(error: PaymentError) {
        // Handle error
    }
})
```

## Error Handling

The SDK provides structured error information with handling suggestions.

```kotlin
client.sale(request, object : PaymentCallback {
    override fun onFailure(error: PaymentError) {
        // Access error details
        val code = error.code
        val message = error.message
        val suggestion = error.suggestion
        val canRetry = error.canRetryWithSameId
        
        // Handle based on error category
        when (error.detail.category) {
            ErrorCategory.INITIALIZATION -> {
                // Initialization error: reinitialize SDK
                showDialog("Initialization Error", message, suggestion)
            }
            
            ErrorCategory.CONNECTION -> {
                // Connection error: reconnect
                showDialog("Connection Error", message, suggestion)
            }
            
            ErrorCategory.AUTHENTICATION -> {
                // Authentication error: check credentials
                showDialog("Authentication Failed", message, suggestion)
            }
            
            ErrorCategory.TRANSACTION -> {
                // Transaction error: handle based on retry rules
                if (canRetry) {
                    retryWithSameRequest()
                } else {
                    createNewTransaction()
                }
            }
        }
    }
})
```

### Common Error Codes

The SDK uses a segmented error code design for quick problem identification.

**Note:** Error code **100** indicates success, not an error. Error codes **20x-39x** are actual errors.

#### Error Code Ranges

| Code Range | Error Type | Description |
| --- | --- | --- |
| **100** | Success | Operation successful (not an error) |
| **20x** | Initialization | SDK initialization issues |
| **21x** | Connection State | Connection state management and failures |
| **23x** | App-to-App Mode | Same-device connection issues |
| **24x** | LAN Mode | Network connection issues |
| **25x** | Cable Mode | USB/Serial cable connection issues |
| **30x** | Transaction | Transaction processing errors |

#### Quick Reference

**Initialization Issues:**

| Code | Issue | Solution |
| --- | --- | --- |
| 201 | SDK not initialized | Call `TaplinkSDK.init()` |
| 202 | SDK service error | Restart application |
| 203 | Tapro initialization failed | Reconnect |

**Connection Issues:**

| Code | Issue | Solution |
| --- | --- | --- |
| 211-213 | Connection state error | Check connection state, call `connect()` |
| 214, 221 | Connection failed | Check network/device/credentials |
| 231-232 | App-to-App mode failed | Install Tapro app or restart device |
| 241-242 | LAN mode failed | Check network and IP address |
| 251-255 | Cable mode failed | Check cable connection and USB permissions |

**Transaction Issues:**

| Code | Issue | Solution | Retry Rule |
| --- | --- | --- | --- |
| 301-305 | Parameter/Send error | Check parameters and network | ✅ Same ID OK |
| 306 | Response timeout | **Query status first** | ⚠️ Query then decide |
| 307-311 | Transaction failed | Review details, retry with new ID | ❌ Must use new ID |

**Retry Rules:**
- ✅ **Same ID OK**: Safe to retry with the same `transactionRequestId`
- ⚠️ **Query then decide**: Query transaction status before retrying
- ❌ **Must use new ID**: Must use a new `transactionRequestId` to prevent duplicate charges

## Important Concepts

### Amount Units

All amount fields must use the **smallest currency unit**:

- **USD**: Cents (1 Dollar = 100 Cents)
- **EUR**: Cents (1 Euro = 100 Cents)
- **JPY**: Yen (1 Yen = 1 Yen)
- **CNY**: Fen (1 Yuan = 100 Fen)

**Example:**

```kotlin
// Correct: $12.34 = 1234 cents
val amount = AmountInfo()
    .setOrderAmount(BigDecimal("1234"))  // 1234 cents = $12.34
    .setPricingCurrency("USD")

// Wrong: Using base currency unit
val wrongAmount = AmountInfo()
    .setOrderAmount(BigDecimal("12.34"))  // Wrong! This will be interpreted as $0.1234
    .setPricingCurrency("USD")
```

### Order ID vs Transaction Request ID

- **referenceOrderId**: Merchant order number (one order can contain multiple transactions)
- **transactionRequestId**: Transaction request ID (unique for each transaction)

**Example:**

```kotlin
val orderId = "ORDER001"

// Sale transaction
val saleRequest = SaleRequest.builder()
    .setReferenceOrderId(orderId)           // Same
    .setTransactionRequestId("TXN001_SALE") // Different
    .setAmount(amount)
    .build()

// Refund transaction (same order)
val refundRequest = RefundRequest.referencedBuilder()
    .setTransactionRequestId("TXN001_REFUND")  // Different
    .setOriginalTransactionId(originalTxnId)   // Reference original transaction
    .setAmount(refundAmount)
    .build()
```

## Best Practices

### Connection State Monitoring

Monitor device connection status to ensure payment functionality is available.

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        TaplinkSDK.setConnectionListener(object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                runOnUiThread {
                    updateConnectionStatus("Connected to Tapro $taproVersion")
                    enablePaymentButtons(true)
                }
            }
            
            override fun onDisconnected(reason: String) {
                runOnUiThread {
                    updateConnectionStatus("Disconnected: $reason")
                    enablePaymentButtons(false)
                }
            }
            
            override fun onError(error: ConnectionError) {
                runOnUiThread {
                    updateConnectionStatus("Connection error: ${error.message}")
                }
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        TaplinkSDK.removeConnectionListener()
    }
}
```

### Timeout Handling

Implement polling query mechanism for timeout scenarios.

```kotlin
private fun handleTimeout(transactionRequestId: String) {
    queryTransactionWithPolling(transactionRequestId) { result ->
        if (result.transactionStatus == "SUCCESS") {
            handleSuccess(result)
        } else {
            showRetryDialog()
        }
    }
}

private fun queryTransactionWithPolling(
    transactionRequestId: String,
    attempt: Int = 1,
    callback: (PaymentResponse) -> Unit
) {
    if (attempt > 12) {
        // Exceeded 12 attempts (60 seconds)
        showDialog("Transaction status unknown", 
            "Please contact support. Transaction Request ID: $transactionRequestId")
        return
    }
    
    val query = QueryRequest().setTransactionRequestId(transactionRequestId)
    val client = TaplinkSDK.getClient()
    
    client.query(query, object : PaymentCallback {
        override fun onSuccess(result: PaymentResponse) {
            if (result.transactionStatus == "PROCESSING") {
                // Continue polling after 5 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    queryTransactionWithPolling(transactionRequestId, attempt + 1, callback)
                }, 5000)
            } else {
                callback(result)
            }
        }
        
        override fun onFailure(error: PaymentError) {
            showErrorMessage("Query failed: ${error.message}")
        }
    })
}
```

### Progress Event Handling

Provide friendly user feedback to enhance user experience.

```kotlin
override fun onProgress(event: PaymentEvent) {
    runOnUiThread {
        when (event.status) {
            "PROCESSING" -> showProcessingAnimation("Processing...")
            "WAITING_CARD" -> showCardPrompt("Please insert, swipe, or tap card")
            "CARD_DETECTED" -> showCardPrompt("Card detected")
            "READING_CARD" -> showProcessingAnimation("Reading card information")
            "WAITING_PIN" -> showPinPrompt("Please enter PIN on payment terminal")
            "WAITING_SIGNATURE" -> showSignaturePrompt("Please sign on payment terminal")
            "WAITING_RESPONSE" -> showProcessingAnimation("Waiting for payment gateway response...")
            "PRINTING" -> showProcessingAnimation("Printing receipt...")
            "COMPLETED" -> hideAllPrompts()
            "CANCEL" -> showCancelMessage("Transaction cancelled")
        }
    }
}
```

## API Reference

### TaplinkSDK

Main SDK class providing core functionality.

```kotlin
// Initialize SDK
TaplinkSDK.init(context: Context, config: TaplinkConfig)

// Connection management
TaplinkSDK.connect(config: ConnectionConfig?, listener: ConnectionListener)
TaplinkSDK.disconnect()
TaplinkSDK.isConnected(): Boolean

// Device information
TaplinkSDK.getConnectedDeviceId(): String?
TaplinkSDK.getConnectionMode(): String?
TaplinkSDK.getTaproVersion(): String?

// Get transaction client
TaplinkSDK.getClient(): TaplinkClient

// SDK version
TaplinkSDK.getVersion(): String
```

### TaplinkClient

Transaction client class for executing payment operations.

```kotlin
val client = TaplinkSDK.getClient()

// Transaction methods
client.sale(request: SaleRequest, callback: PaymentCallback)
client.refund(request: RefundRequest, callback: PaymentCallback)
client.void(request: VoidRequest, callback: PaymentCallback)
client.auth(request: AuthRequest, callback: PaymentCallback)
client.postAuth(request: PostAuthRequest, callback: PaymentCallback)
client.incrementalAuth(request: IncrementalAuthRequest, callback: PaymentCallback)
client.tipAdjust(request: TipAdjustRequest, callback: PaymentCallback)
client.batchClose(request: BatchCloseRequest, callback: PaymentCallback)

// Query method
client.query(request: QueryRequest, callback: PaymentCallback)
```

## Version Information

- **Current Version**: 1.0.1
- **Version Code**: 2
- **Release Date**: 2025-01

## Technical Stack

- **Language**: Kotlin 1.7.10
- **Build Tool**: Gradle with Kotlin DSL
- **Android Gradle Plugin**: 8.13.1
- **Min SDK**: Android 7.1 (API 25)
- **Target SDK**: Android API 35
- **Java Version**: Java 11

## Support

For questions or suggestions, please contact:
- Project Repository: https://github.com/sunbay-developer/taplink-sdk-android.git
- Technical Support: Contact SUNBAY technical support team

## License

Copyright © SUNBAY. All rights reserved.

---

**Note**: This SDK is for local mode integration only. For server-side cloud mode integration, please use Nexus SDK.
