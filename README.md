# Taplink SDK for Android

[![Version](https://img.shields.io/badge/version-1.0.7.19-blue.svg)](https://github.com/sunbay-developer/taplink-sdk-android)
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
  - Card network routing: specify CREDIT or DEBIT for card payment routing (auto-detection when not specified)
  - Receipt print control: specify NONE, MERCHANT, CUSTOMER, BOTH, or AUTO (determined by Tapro app when not specified)
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
include(":lib_taplink_communication")
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
                Toast.makeText(this@MainActivity,
                    "Disconnected: $reason",
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
        .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
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

**Session `INIT`:** After a physical connection is established, the SDK runs **INIT** with Tapro on the **first payment operation** (not necessarily inside `onConnected`). If the transport drops, INIT state is cleared and the next transaction performs INIT again—avoid assuming INIT remains valid across disconnect/reconnect.

---

## AI-Assisted Integration

Using Cursor, GitHub Copilot, Windsurf, or any other AI coding tool? Follow these steps to give your AI full SDK context — so it generates production-correct code from your very first prompt.

> **Why this matters:** Without context, AI tools hallucinate API names and miss critical rules (e.g., amount units in cents, error 306 query-first pattern). With context loaded, AI writes working Taplink code directly.

---

### Step 1 — Load SDK Context into Your AI Tool

Choose the method that matches your tool:

#### Cursor IDE

1. In your project, create the file `.cursor/rules/taplink.mdc`
2. Paste the following content:

<details>
<summary>📋 Click to expand and copy — <code>.cursor/rules/taplink.mdc</code></summary>

````markdown
---
description: Taplink SDK integration rules for Android payment development
globs: ["**/*.kt", "**/*.java"]
alwaysApply: false
---

# Taplink SDK — Integration Rules

## Critical Rules (Never Break)
- All amounts in SMALLEST CURRENCY UNIT (cents): $10.00 → `BigDecimal("1000")`
- `transactionRequestId` MUST be globally unique per request — use `UUID.randomUUID().toString()`
- Never reuse `transactionRequestId` after errors 307, 308, 309, 310, 311
- Error 306 (timeout): MUST call `client.query()` BEFORE creating any new transaction
- Only import from `lib_taplink_sdk` — never import `lib_taplink_communication`
- Always call `TaplinkSDK.disconnect()` + `TaplinkSDK.removeConnectionListener()` in `onDestroy()`
- `tipConfig` and `tipAmount` in `AmountInfo` are mutually exclusive — set one or neither

## SDK Entry Points
```kotlin
TaplinkSDK.init(context, config)              // in Application.onCreate()
TaplinkSDK.connect(config, listener)          // in onResume()
TaplinkSDK.disconnect()                       // in onDestroy()
TaplinkSDK.getClient()                        // returns TaplinkClient for transactions
TaplinkSDK.isConnected(): Boolean
TaplinkSDK.isInitialized(): Boolean
TaplinkSDK.getConnectionStatus(): String?
```

## Sale Transaction Pattern
```kotlin
val request = SaleRequest.builder()
    .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setAmount(AmountInfo(BigDecimal("1000"), "USD"))   // 1000 cents = $10.00
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
    .build()

TaplinkSDK.getClient().sale(request, object : PaymentCallback {
    override fun onProgress(event: PaymentEvent) { /* update UI */ }
    override fun onSuccess(result: PaymentResult) {
        when {
            result.isSuccess()    -> confirmPayment(result)
            result.isProcessing() -> pollStatus(result.transactionRequestId!!)
            result.isFailed()     -> showDeclined(result.transactionResultMsg)
        }
    }
    override fun onFailure(error: PaymentError) {
        if (error.code == "306") pollStatus(error.transactionRequestId!!)
        else if (error.canRetryWithSameId) retry()
        else showError(error.message)
    }
})
```

## Connection Modes
```kotlin
ConnectionConfig.createAppMode()                          // Same device (APP_TO_APP)
ConnectionConfig.createLanMode("192.168.1.100", 8443)    // LAN first-time
ConnectionConfig.createLanMode()                          // LAN reuse cached
ConnectionConfig.createCableMode()                        // Cable AUTO (VSP→RS232→AOA)
ConnectionConfig.createDefault()                          // Auto-detect all modes
```

## Error Handling
```kotlin
// Error 306 — timeout: MUST query before retry
if (error.code == "306") client.query(QueryRequest().setTransactionRequestId(id), ...)

// Retry rules:
// 301-305 → canRetryWithSameId=true  → retry with same transactionRequestId
// 306     → query first              → then decide based on transactionStatus
// 307-311 → canRetryWithSameId=false → use new UUID for transactionRequestId
```

## TipConfig
```kotlin
TipConfig(
    onScreenTip = true,
    tipMode = TipMode.ON_SALE,        // or AFTER_SALE
    suggestions = TipSuggestions(FeeMode.RATE, listOf(15, 18, 20))  // 15%, 18%, 20%
)
// Do NOT set tipAmount in AmountInfo when using tipConfig
```

## PaymentCallback — UI thread
All callback methods may run on a background thread. Always wrap UI updates:
```kotlin
override fun onSuccess(result: PaymentResult) {
    runOnUiThread { updateUI(result) }
}
```
````

</details>

3. Cursor auto-attaches this rule when you edit `.kt` or `.java` files — no further action needed.

#### GitHub Copilot

1. In your project, create or open `.github/copilot-instructions.md`
2. Add the following block:

```markdown
## Taplink SDK Integration Rules

This project uses the Taplink SDK (lib_taplink_sdk) from SUNBAY for Android payment integration.

Critical rules — always follow these when generating Taplink code:
- All amounts are in the SMALLEST CURRENCY UNIT: $10.00 USD = BigDecimal("1000") (1000 cents)
- transactionRequestId must be globally unique per request: always use UUID.randomUUID().toString()
- Error 306 = timeout: MUST call client.query() to check status BEFORE creating any new transaction
- Error 307/308/309/310/311: NEVER reuse transactionRequestId — generate a new UUID
- canRetryWithSameId=true (errors 301–305): safe to retry with the same transactionRequestId
- Only import from lib_taplink_sdk — never from lib_taplink_communication
- PaymentCallback methods run on a background thread — always use runOnUiThread {} for UI updates
- Always call TaplinkSDK.disconnect() AND TaplinkSDK.removeConnectionListener() in onDestroy()
- tipConfig and tipAmount in AmountInfo are mutually exclusive — set one or neither, never both

Integration flow: TaplinkSDK.init() → TaplinkSDK.connect() → TaplinkSDK.getClient() → .sale()/.refund()/etc.
```

#### Other AI Tools (Claude, ChatGPT, Windsurf, etc.)

Paste the contents of [`llms.txt`](./llms.txt) at the beginning of your chat or add it as a project context file. This file contains the complete SDK reference in a compact, AI-readable format.

---

### Step 2 — Use Prompts to Generate Integration Code

Once context is loaded, use the following prompts to generate each part of your integration. Copy and adapt them directly.

#### 2.1 — Initialize the SDK

> **Prompt:**
> ```
> Using the Taplink SDK, write the Application class initialization code.
> App ID is "my_app_id", merchant ID is "my_merchant_id", secret key from BuildConfig.
> Enable debug logging only in debug builds.
> ```

#### 2.2 — Connect in an Activity

> **Prompt:**
> ```
> Using the Taplink SDK with LAN mode (terminal IP: 192.168.1.100, port 8443),
> write the complete connection setup for a MainActivity, including:
> - Persistent connection listener in onCreate
> - Auto-connect in onResume if not already connected
> - Cleanup in onDestroy
> ```

#### 2.3 — Execute a Sale Transaction

> **Prompt:**
> ```
> Using the Taplink SDK, write a sale transaction for $25.00 USD paid by card.
> Include full PaymentCallback handling:
> - onProgress: show transaction stage on screen
> - onSuccess: handle SUCCESS / PROCESSING / FAILED states
> - onFailure: handle error 306 with query-first polling, and other error codes
> ```

#### 2.4 — Refund a Transaction

> **Prompt:**
> ```
> Using the Taplink SDK, write a referenced refund of $10.00 for
> original transaction ID "TXN20231119001". Include full error handling.
> ```

#### 2.5 — Add On-Screen Tip

> **Prompt:**
> ```
> Using the Taplink SDK, modify a sale request to show a tip prompt on the terminal.
> Tip mode is ON_SALE, suggest 15%, 18%, 20% as options.
> ```

#### 2.6 — Handle Error 306 (Timeout Recovery)

> **Prompt:**
> ```
> Using the Taplink SDK, write a reusable pollTransactionStatus() function
> that queries the transaction status after error 306, retries every 5 seconds,
> gives up after 12 attempts, and handles SUCCESS / PROCESSING / FAILED results.
> ```

#### 2.7 — End-of-Day Batch Close

> **Prompt:**
> ```
> Using the Taplink SDK, write a batch close transaction and display
> the settlement summary (total count and total amount) on success.
> ```

---

### Step 3 — Review AI-Generated Code

Before committing AI-generated code, verify these items:

| Check | What to look for |
|-------|-----------------|
| ✅ Amount unit | `BigDecimal("1000")` for $10.00 — **not** `BigDecimal("10.00")` |
| ✅ Transaction ID | `UUID.randomUUID().toString()` — not sequential integers |
| ✅ Error 306 | `client.query()` called before any new transaction attempt |
| ✅ Error 307/308 | New UUID generated, not the original ID reused |
| ✅ UI thread | `runOnUiThread {}` inside every callback that touches UI |
| ✅ Lifecycle | `disconnect()` + `removeConnectionListener()` in `onDestroy()` |
| ✅ Import path | `com.sunmi.tapro.taplink.sdk.*` — no `lib_taplink_communication` imports |
| ✅ tipConfig | `tipAmount` in `AmountInfo` is `null` when `tipConfig` is set |

---

### Step 4 — Test Integration

Run through this checklist before going to QA:

- [ ] SDK initializes without crashing (`TaplinkSDK.isInitialized()` returns `true`)
- [ ] Connection succeeds and `onConnected` fires (`TaplinkSDK.isConnected()` returns `true`)
- [ ] Sale transaction completes and `onSuccess` receives a result with `isSuccess() == true`
- [ ] Disconnect triggers `onDisconnected` callback
- [ ] Reconnect after disconnect works without app restart
- [ ] Error 306 scenario handled — query is called, not a blind retry
- [ ] All amounts display correctly (not 100x inflated)

---

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
- RS232 (USB serial adapter, hex-framed payload)

**Cable `AUTO` detection:** The SDK tries cable transports in order **VSP → RS232 → AOA** until one succeeds (more reliable than guessing from device metadata alone). Serial paths are tried first so peers that enumerate as CDC connect without waiting on an AOA attempt; pure AOA accessories are still reached last.

**RS232 link behavior (summary):**
- Opening the USB serial port is not enough for `onConnected`: the stack waits for **peer liveness** via lightweight markers `##TAPLINK_HSK_REQ##` / `##TAPLINK_HSK_ACK##`, or **application payload** (hex-framed business data) after markers are stripped from the byte stream.
- While connected, periodic REQ and a **peer silence** watchdog (default **5 seconds** without handshake or payload after peer activity; adjust `RS232_HSK_PEER_SILENCE_MS` in `SerialServiceKernel` if you maintain a fork) help detect a dead or unplugged far end. USB attach checks and I/O errors complement this.
- Ensure the host app has **USB host** support and grants **USB permission** when the system prompts; RS232 uses standard Android USB serial probing (e.g. FTDI, CH340, PL2303).

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

The most common payment transaction type. For card payments, specify `paymentMethod` as `PaymentCategory.CARD`.

```kotlin
val client = TaplinkSDK.getClient()

val amount = AmountInfo()
    .setOrderAmount(BigDecimal("10.00"))
    .setPricingCurrency("USD")

val request = SaleRequest.builder()
    .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setAmount(amount)
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
    .setDescription("Product Purchase")
    .build()

client.sale(request, paymentCallback)
```

### Tip Configuration

When you want the terminal to collect tip interactively, set `tipConfig` in `AmountInfo`. `tipConfig` and `tipAmount` are mutually exclusive, so `tipAmount` must be `null`.

```kotlin
val amount = AmountInfo(
    orderAmount = BigDecimal("1000"),
    pricingCurrency = "USD",
    tipConfig = TipConfig(
        onScreenTip = true,
        tipMode = TipMode.ON_SALE,
        tipWithTax = false,
        suggestions = TipSuggestions(
            feeMode = FeeMode.RATE,
            values = listOf(15, 18, 20)
        )
    )
)
```

#### TipConfig Field Reference

**`TipConfig`**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `onScreenTip` | `Boolean` | ✅ | — | Whether to show a tip input screen on the terminal. Set to `true` to prompt the customer for a tip. |
| `tipMode` | `TipMode` | ✅ | — | When the tip is collected. `ON_SALE` — collected during the sale; `AFTER_SALE` — collected after the sale completes. |
| `tipWithTax` | `Boolean` | ❌ | `false` | Whether tip percentages are calculated on the tax-inclusive amount. When `false`, percentages apply to the base order amount only. |
| `suggestions` | `TipSuggestions?` | ❌ | `null` | Predefined tip options displayed on the terminal for the customer to choose from. Omit to allow free-form tip entry only. |

**`TipSuggestions`**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `feeMode` | `FeeMode` | ✅ | How the `values` are interpreted. `RATE` — percentage (e.g., `15` = 15%); `AMOUNT` — fixed amount in smallest currency unit (e.g., `100` = $1.00). |
| `values` | `List<Int>` | ✅ | List of suggested tip options shown on screen. Typically 3–4 values, e.g., `[15, 18, 20]` for percentages or `[100, 200, 500]` for fixed amounts. |

**`TipMode` Enum**

| Value | Description |
|-------|-------------|
| `ON_SALE` | Tip is collected during the sale flow. The total charged to the card includes the tip. |
| `AFTER_SALE` | Tip is adjusted after the sale authorization. Use in combination with `TipAdjust` transaction. |

**`FeeMode` Enum**

| Value | Description |
|-------|-------------|
| `RATE` | Suggestion values are tip percentages. `15` means 15% of the order amount (or tax-inclusive amount when `tipWithTax = true`). |
| `AMOUNT` | Suggestion values are fixed tip amounts in the smallest currency unit (cents). `100` means $1.00 USD. |

> **Mutual exclusivity:** `tipConfig` and `tipAmount` in `AmountInfo` cannot be set at the same time. Use `tipConfig` for interactive terminal tip collection; use `tipAmount` when the tip amount is known upfront (e.g., from a TipAdjust flow).

---

### Refund Transaction

Supports full and partial refunds. For card refunds, specify `paymentMethod` as `PaymentCategory.CARD`.

**Referenced Refund** (with original transaction ID):

```kotlin
val amount = AmountInfo()
    .setOrderAmount(BigDecimal("5.00"))
    .setPricingCurrency("USD")

val request = RefundRequest.referencedBuilder()
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setOriginalTransactionId("TXN20231119001")
    .setAmount(amount)
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
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
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
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
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
    .build()

// Refund transaction (same order)
val refundRequest = RefundRequest.referencedBuilder()
    .setTransactionRequestId("TXN001_REFUND")  // Different
    .setOriginalTransactionId(originalTxnId)   // Reference original transaction
    .setAmount(refundAmount)
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
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

- **Current Version**: 1.0.7.19 (see `lib_taplink_sdk/build.gradle.kts` → `SdkVersion.NAME`)
- **Version Code**: 6 (`SdkVersion.CODE`)
- **Release Date**: see project tags / release notes

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
