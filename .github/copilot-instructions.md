# GitHub Copilot Custom Instructions – Taplink SDK

This repository contains the **Taplink SDK**, a semi-integrated payment SDK for Android POS applications, provided by SUNBAY. The SDK enables developers to integrate payment capabilities using three connection modes: **App-to-App (Local)**, **LAN (WLAN)**, and **Cable (USB/Serial)**.

When helping with code in this repository, always follow the rules and patterns described below.

---

## Repository Structure

```
taplink-sdk-android/
├── lib_taplink_sdk/          ← Public API module (customers use this)
│   └── src/main/java/com/sunmi/tapro/taplink/sdk/
│       ├── TaplinkSDK.kt         ← Singleton: init, connect, disconnect
│       ├── TaplinkClient.kt      ← Transactions: sale, refund, void, auth, etc.
│       ├── config/               ← TaplinkConfig, ConnectionConfig
│       ├── callback/             ← ConnectionListener, PaymentCallback
│       ├── enums/                ← ConnectionMode, CableProtocol, CardNetworkType, etc.
│       ├── model/                ← Request/response/common models
│       └── error/                ← PaymentError, ConnectionError
├── lib_taplink_communication/ ← Internal transport (do NOT expose to customers)
└── app/                       ← Demo application (reference implementation)
```

---

## Core Integration Pattern (3 Steps)

```kotlin
// Step 1: Initialize in Application.onCreate()
TaplinkSDK.init(context, TaplinkConfig(
    appId = BuildConfig.TAPLINK_APP_ID,
    merchantId = BuildConfig.TAPLINK_MERCHANT_ID,
    secretKey = BuildConfig.TAPLINK_SECRET_KEY
))

// Step 2: Connect in Activity/Service
TaplinkSDK.connect(connectionConfig, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) { ... }
    override fun onDisconnected(reason: String) { ... }
    override fun onError(error: ConnectionError) { ... }
})

// Step 3: Execute transaction after connection
val client = TaplinkSDK.getClient()
client.sale(request, paymentCallback)
```

---

## Connection Modes

### App-to-App (Local) – Same Android device
```kotlin
// POS app and Tapro run on the same device
val config = ConnectionConfig().setConnectionMode(ConnectionMode.APP_TO_APP)
// or shortcut:
val config = ConnectionConfig.createAppMode()
```

### LAN (WLAN) – Local network connection
```kotlin
// First connection: specify IP and port (range: 8443–8453)
val config = ConnectionConfig()
    .setConnectionMode(ConnectionMode.LAN)
    .setHost("192.168.1.100")
    .setPort(8443)

// Subsequent connections: SDK uses cached device info
val config = ConnectionConfig.createLanMode()
```

### Cable – USB or serial cable
```kotlin
// Recommended: let SDK auto-detect (tries VSP → RS232 → AOA)
val config = ConnectionConfig()
    .setConnectionMode(ConnectionMode.CABLE)
    .setCableProtocol(CableProtocol.AUTO)
// or:
val config = ConnectionConfig.createCableMode()
```

**Cable protocol options:** `AUTO` (recommended), `USB_VSP`, `RS232`, `USB_AOA`

---

## Amount Units — CRITICAL RULE

**All amount fields use the smallest currency unit.** Never use decimal dollars/euros.

```kotlin
// ✅ Correct: $12.34 = 1234 cents
val amount = AmountInfo(orderAmount = BigDecimal("1234"), pricingCurrency = "USD")

// ❌ Wrong: this will be treated as 12.34 cents = $0.1234
val amount = AmountInfo(orderAmount = BigDecimal("12.34"), pricingCurrency = "USD")
```

| Currency | Unit | $10.00 |
|----------|------|--------|
| USD | cents | `BigDecimal("1000")` |
| EUR | cents | `BigDecimal("1000")` |
| JPY | yen | `BigDecimal("1000")` |
| CNY | fen | `BigDecimal("1000")` |

---

## Transaction Types

All transactions use `TaplinkSDK.getClient()` and a `PaymentCallback`.

### Sale
```kotlin
val request = SaleRequest.builder()
    .setReferenceOrderId("ORDER_${System.currentTimeMillis()}")    // 6–32 chars, required
    .setTransactionRequestId(UUID.randomUUID().toString())          // Unique, required
    .setAmount(AmountInfo(BigDecimal("1000"), "USD"))               // Required (cents)
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))      // For card payments
    .setDescription("Product Purchase")
    .setPrintReceipt(PrintReceipt.AUTO)
    .build()
client.sale(request, callback)
```

### Refund
```kotlin
// Referenced refund (with original transaction ID)
val request = RefundRequest.referencedBuilder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setOriginalTransactionId("original_txn_id")
    .setAmount(AmountInfo(BigDecimal("500"), "USD"))
    .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
    .build()
client.refund(request, callback)

// Non-referenced refund (requires card swipe)
val request = RefundRequest.nonReferencedBuilder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setReferenceOrderId("REFUND_ORDER_${System.currentTimeMillis()}")
    .setAmount(AmountInfo(BigDecimal("500"), "USD"))
    .build()
client.refund(request, callback)
```

### Void (same-day cancel only)
```kotlin
val request = VoidRequest.builder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setOriginalTransactionId("original_txn_id")
    .build()
client.void(request, callback)
```
> For cross-day cancels, use `Refund` instead of `Void`.

### Authorization (Pre-Auth)
```kotlin
val request = AuthRequest.builder()
    .setReferenceOrderId("AUTH_${System.currentTimeMillis()}")
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setAmount(AuthAmountInfo(authAmount = BigDecimal("5000"), pricingCurrency = "USD"))
    .build()
client.auth(request, callback)
```

### Forced Authorization (Offline / Voice Auth)
```kotlin
val request = ForcedAuthRequest.builder()
    .setReferenceOrderId("FORCED_${System.currentTimeMillis()}")
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setAmount(AuthAmountInfo(authAmount = BigDecimal("3000"), pricingCurrency = "USD"))
    .build()
client.forcedAuth(request, callback)
```

### Post-Authorization (Pre-Auth Completion)
```kotlin
val request = PostAuthRequest.builder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setOriginalTransactionId("original_auth_txn_id")
    .setAmount(AmountInfo(BigDecimal("4500"), "USD"))
    .build()
client.postAuth(request, callback)
```

### Batch Close
```kotlin
val request = BatchCloseRequest.builder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .setDescription("End of Day Settlement")
    .build()
client.batchClose(request, callback)
```

### Abort (Cancel In-Progress Transaction)
```kotlin
val request = AbortRequest.builder()
    .setTransactionRequestId(UUID.randomUUID().toString())
    .build()
client.abort(request, callback)
```

### Query (status check / timeout recovery)
```kotlin
val query = QueryRequest().setTransactionRequestId("original_txn_request_id")
client.query(query, callback)
// result.transactionStatus: "SUCCESS" | "PROCESSING" | "FAILED"
```

---

## PaymentCallback

```kotlin
val callback = object : PaymentCallback {
    override fun onProgress(event: PaymentEvent) {
        // Always use runOnUiThread for UI updates
        runOnUiThread {
            when (event.eventCode) {
                "WAITING_CARD"      -> showHint("Please present card")
                "WAITING_PIN"       -> showHint("Enter PIN on terminal")
                "WAITING_SIGNATURE" -> showHint("Please sign on terminal")
                "WAITING_RESPONSE"  -> showHint("Contacting bank...")
                "PRINTING"          -> showHint("Printing receipt...")
                "COMPLETED"         -> hideHint()
                "CANCEL"            -> showHint("Transaction cancelled")
            }
        }
    }

    override fun onSuccess(result: PaymentResult) {
        runOnUiThread {
            when {
                result.isSuccess()    -> showSuccess(result.transactionId)
                result.isProcessing() -> pollTransactionStatus(result.transactionRequestId!!)
                result.isFailed()     -> showError(result.transactionResultMsg)
            }
        }
    }

    override fun onFailure(error: PaymentError) {
        runOnUiThread { handlePaymentError(error) }
    }
}
```

---

## Error Handling

### Error Categories
```kotlin
when (error.category) {
    ErrorCategory.INITIALIZATION -> reinitSDK()
    ErrorCategory.CONNECTION     -> reconnect()
    ErrorCategory.AUTHENTICATION -> checkCredentials()
    ErrorCategory.TRANSACTION    -> handleTransactionError(error)
}
```

### Error Code Quick Reference

| Range | Category | Action |
|-------|----------|--------|
| 201–203 | Initialization | Re-init SDK or restart app |
| 211–214 | Connection state | Check connection and reconnect |
| 231–232 | App-to-App | Install/restart Tapro app |
| 241–242 | LAN | Check IP and network |
| 251–255 | Cable | Check cable and USB permissions |
| 301–305 | Transaction param/send | Fix params, retry (same ID) ✅ |
| 306 | **Timeout** | **Query first**, then decide ⚠️ |
| 307–311 | Transaction failed | Retry with **new ID** ❌ |

### Retry Rules
```kotlin
fun handleTransactionError(error: PaymentError) {
    when {
        error.code == "306" -> {
            // MUST query status before retrying
            pollTransactionStatus(error.transactionRequestId!!)
        }
        error.canRetryWithSameId -> {
            // Safe to retry with the same transactionRequestId
            retryWithSameRequest()
        }
        else -> {
            // Must use a new transactionRequestId
            retryWithNewId()
        }
    }
}
```

### Timeout Polling Pattern (Error 306)
```kotlin
fun pollTransactionStatus(txnRequestId: String, attempt: Int = 1) {
    if (attempt > 12) {  // 12 × 5s = 60s max
        showError("Payment status unknown. Please contact support.\nID: $txnRequestId")
        return
    }
    val query = QueryRequest().setTransactionRequestId(txnRequestId)
    client.query(query, object : PaymentCallback {
        override fun onSuccess(result: PaymentResult) {
            when (result.transactionStatus) {
                "SUCCESS"    -> handleSuccess(result)
                "FAILED"     -> handleFailure(result)
                "PROCESSING" -> Handler(Looper.getMainLooper()).postDelayed(
                    { pollTransactionStatus(txnRequestId, attempt + 1) }, 5000
                )
            }
        }
        override fun onFailure(error: PaymentError) {
            Handler(Looper.getMainLooper()).postDelayed(
                { pollTransactionStatus(txnRequestId, attempt + 1) }, 5000
            )
        }
        override fun onProgress(event: PaymentEvent) {}
    })
}
```

---

## Key Concepts

### transactionRequestId
- **Must be globally unique** per transaction attempt
- Used for idempotency — enables safe retry without duplicate charges
- Always save it before sending (needed for timeout recovery)

### referenceOrderId vs transactionRequestId
```
referenceOrderId    — Business order number (one order, multiple transactions OK)
transactionRequestId — Single transaction attempt ID (must be unique globally)
```

### INIT Session
- SDK runs INIT with Tapro on the **first payment** after connecting (not in `onConnected`)
- If the connection drops and reconnects, INIT state is **cleared** and re-runs on next payment
- Don't assume INIT persists across disconnect/reconnect cycles

### PaymentResult Check
```kotlin
// ⚠️ onSuccess is called even when transactionStatus is "FAILED"
// Always check result.isSuccess(), result.isProcessing(), result.isFailed()
result.isSuccess()    // code=="0" && transactionStatus=="SUCCESS"
result.isProcessing() // transactionStatus=="PROCESSING"  → must query
result.isFailed()     // transactionStatus=="FAILED" || code!="0"
```

---

## Connection Lifecycle

```kotlin
// Application
override fun onCreate() {
    TaplinkSDK.init(this, config)   // Once, before anything else
}

// Activity
override fun onCreate() {
    TaplinkSDK.setConnectionListener(persistentListener)  // For ongoing monitoring
}
override fun onResume() {
    if (!TaplinkSDK.isConnected()) TaplinkSDK.connect(config, oneTimeListener)
}
override fun onDestroy() {
    TaplinkSDK.removeConnectionListener()  // Prevent memory leaks
    TaplinkSDK.disconnect()
}
```

---

## Tip Configuration

```kotlin
// On-screen tip collection during sale (tipConfig and tipAmount are mutually exclusive)
val amount = AmountInfo(
    orderAmount = BigDecimal("1000"),
    pricingCurrency = "USD",
    tipConfig = TipConfig(
        onScreenTip = true,
        tipMode = TipMode.ON_SALE,
        tipWithTax = false,
        suggestions = TipSuggestions(
            feeMode = FeeMode.RATE,
            values = listOf(15, 18, 20)  // 15%, 18%, 20%
        )
    )
)
```

---

## Production Checklist

- `logEnabled = false` (or `BuildConfig.DEBUG`)
- Credentials from `BuildConfig` or secure storage, never hardcoded
- All amounts in smallest currency unit (cents for USD/EUR)
- `transactionRequestId` unique per transaction attempt (UUID recommended)
- Error 306 → query-before-retry implemented
- `disconnect()` and `removeConnectionListener()` in `onDestroy()`
- USB host permission handled for Cable mode

---

## SDK Version

Current: **1.0.7.19** | Min SDK: **Android API 25 (7.1)** | Language: **Kotlin 1.7.10**
