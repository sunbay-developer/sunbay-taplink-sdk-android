# Release Notes

Release history for the **Taplink SDK for Android** (`com.sunmi:sunbay-taplink-sdk-android`).

Releases are listed newest first. Each entry highlights what changed for integrators — new APIs, breaking changes, bug fixes, and step-by-step migration notes.

> **Legend:** 🚀 New &nbsp;·&nbsp; 💥 Breaking &nbsp;·&nbsp; 🔧 Improved &nbsp;·&nbsp; 🐛 Fixed &nbsp;·&nbsp; 📦 Dependencies &nbsp;·&nbsp; 🧭 Migration

**Compatibility:** Min SDK Android API 25 (7.1) · Kotlin 1.7.10 · Java 8+

## Contents

- [v1.0.7](#v107) — Unified callback model, transitive dependencies, merchant-less config
- [v1.0.6](#v106) — Tip config moves to the transaction request
- [v1.0.5](#v105) — Declines split out, callback adapters, status helpers
- [v1.0.4](#v104) — On-screen tips and suggested tip amounts
- [v1.0.3](#v103) — Type-safe `TaplinkClient` and request models
- [v1.0.2](#v102) — Internal cable/transport hardening
- [v1.0.1](#v101) — Cable modes unified under `CABLE`
- [v1.0.0](#v100) — Initial public release
- [Artifact verification](#artifact-verification)

---

## v1.0.7

**Released 2026-06-01**

Simplifies payment handling down to a single result callback, publishes the SDK's runtime dependencies so Maven consumers resolve them automatically, and adds support for merchant-less configuration.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.7")
}
```

### 💥 Breaking Changes

- **Removed `onDeclined(PaymentResult)`** from `PaymentCallback` and `PaymentCallbackAdapter`. Terminal-confirmed approved, failed, and canceled outcomes now all arrive in `onSuccess(PaymentResult)`; distinguish them with `isSuccess()`, `isFailed()`, and `isProcessing()`. Any `onDeclined` override must be moved into `onSuccess`.
- **`onFailure(PaymentError)` is now reserved** for communication, protocol, or SDK errors where no valid transaction result exists. Move business-decline handling out of `onFailure`/`onDeclined` and into `onSuccess`.

### 🚀 New Features

- Added `PaymentResult.toPaymentError()` to convert a failed result into a `PaymentError`, so existing error-presentation logic can be reused for declines.
- Added `TaplinkConfig.create(appId, secretKey)` for integrations without a merchant ID, plus `clearMerchantId()` to remove a previously set value.

### 📦 Dependencies

- The published POM now declares the SDK's runtime dependencies as `compile` scope, so Maven/Gradle consumers resolve them transitively — no more manual declarations or `NoClassDefFoundError` at runtime:
  - `com.google.code.gson:gson:2.13.1`
  - `org.java-websocket:Java-WebSocket:1.5.3`
  - `com.github.mik3y:usb-serial-for-android:3.7.0`

> **Upgrading from ≤ 1.0.6?** If you previously added Gson, Java-WebSocket, or usb-serial-for-android manually to work around missing classes, you can remove those workarounds after upgrading.

### 🔧 Improvements

- Terminal cancellation and abort outcomes are normalized as **failed** transaction results, removing the need for a separate CANCELLED branch in your app.

### 🧭 Migration from v1.0.6

**Before (v1.0.6)**

```kotlin
client.sale(request, object : PaymentCallbackAdapter() {
    override fun onSuccess(result: PaymentResult) {
        showApproved(result)
    }

    override fun onDeclined(result: PaymentResult) {
        showError(result.transactionResultMsg)
    }

    override fun onFailure(error: PaymentError) {
        showError(error.message)
    }
})
```

**After (v1.0.7)**

```kotlin
client.sale(request, object : PaymentCallbackAdapter() {
    override fun onSuccess(result: PaymentResult) {
        when {
            result.isSuccess()    -> showApproved(result)
            result.isFailed()     -> showError(result.toPaymentError().message) // includes declines & cancellations
            result.isProcessing() -> startPolling(result)
        }
    }

    override fun onFailure(error: PaymentError) {
        // Communication, protocol, and SDK errors only.
        showError(error.message)
    }
})
```

See also the [API Reference](API-REFERENCE.md) and the [v1.0.6 → v1.0.7 migration guide](README.md#migrating-from-v106).

---

## v1.0.6

**Released 2026-04-30**

Refocuses `AmountInfo` on monetary values by moving tip configuration onto the transaction request.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.6")
}
```

### 💥 Breaking Changes

- **`tipConfig` moved from `AmountInfo` to the transaction request.** `PaymentRequest` now carries `tipConfig`, and the `SaleRequest` / `PostAuthRequest` constructors now include a `TipConfig` parameter.
- Because these are Kotlin `data class`es, their generated constructors, `componentN()` methods, and `copy()` signatures changed. Code that constructs, destructures, or copies these models by position **must be recompiled and updated**.

### 🔧 Improvements

- `AmountInfo` now focuses purely on monetary values (base amount, tip, tax, surcharge, cash back); tip *interaction* settings live with the transaction request.
- Retains the v1.0.5 decline-handling behavior (`onDeclined(PaymentResult)`).

### 🧭 Migration from v1.0.5

**Before (v1.0.5)**

```kotlin
val amount = AmountInfo.of(total, currency)
    .setTipConfig(tipConfig)

val request = PaymentRequest.builder()
    .setAmount(amount)
    .build()
```

**After (v1.0.6)**

```kotlin
val amount = AmountInfo.of(total, currency)

val request = PaymentRequest.builder()
    .setAmount(amount)
    .setTipConfig(tipConfig)
    .build()
```

---

## v1.0.5

**Released 2026-04-29**

Cleanly separates terminal *declines* from *communication failures*, and adds callback adapters plus status-inspection helpers.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.5")
}
```

### 💥 Breaking Changes

- **Added the abstract method `PaymentCallback.onDeclined(PaymentResult)`.** Direct `PaymentCallback` implementations must add it, or migrate to the new `PaymentCallbackAdapter` (recommended — override only the callbacks you need).
- Made `PaymentResult.tipAmount`, `incrementalAmount`, and `totalAuthorizedAmount` **read-only** by removing their setters.

### 🚀 New Features

- `PaymentCallbackAdapter` and `ConnectionListenerAdapter` — implement only the callbacks you care about instead of every abstract method.
- `TaplinkSDK.isInitialized()` and `TaplinkSDK.getConnectionStatus()` to inspect initialization and detailed connection state.
- `AmountInfo.of(long, currency)` and `AmountInfo.of(BigDecimal, currency)` factory methods, plus a no-argument `TipConfig` constructor.
- New `PaymentEvent.TipProcessing` progress event.

### 🔧 Improvements

- Declines are delivered to `onDeclined(PaymentResult)`, while communication and SDK errors continue to use `onFailure(PaymentError)` — no more inferring the difference from an error code.

### 🧭 Migration from v1.0.4

Switch to `PaymentCallbackAdapter` and route declines to `onDeclined`:

**Before (v1.0.4)**

```kotlin
val callback = object : PaymentCallback {
    override fun onProgress(event: PaymentEvent) = Unit
    override fun onSuccess(result: PaymentResult) = showResult(result)
    override fun onFailure(error: PaymentError) = showError(error) // declines were handled here
}
```

**After (v1.0.5)**

```kotlin
val callback = object : PaymentCallbackAdapter() {
    override fun onSuccess(result: PaymentResult)  = showResult(result)
    override fun onDeclined(result: PaymentResult) = showDeclined(result) // moved here
    override fun onFailure(error: PaymentError)    = showError(error)     // comms/SDK errors only
}
```

---

## v1.0.4

**Released 2026-04-22**

Adds on-screen tipping, including suggested tip amounts by percentage or fixed value.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.4")
}
```

### 🚀 New Features

- `TipConfig` — on-screen tip control with `TipMode.ON_SALE` / `TipMode.AFTER_SALE`, tax handling, and suggested tip options.
- `TipSuggestions` with `FeeMode.RATE` / `FeeMode.AMOUNT`, so suggested tips can be percentages or fixed amounts.
- `tipConfig` added to `AmountInfo`, letting an amount object carry tip-interaction settings.

**Example**

```kotlin
val amount = AmountInfo(
    orderAmount = BigDecimal("1000"),   // $10.00 in cents
    pricingCurrency = "USD",
    tipConfig = TipConfig(
        onScreenTip = true,
        tipMode = TipMode.ON_SALE,
        suggestions = TipSuggestions(
            feeMode = FeeMode.RATE,
            values = listOf(15, 18, 20)  // 15% / 18% / 20%
        )
    )
)
```

> **Note:** In v1.0.6 `tipConfig` moves off `AmountInfo` and onto the transaction request. See [v1.0.6](#v106).

### 🔧 Improvements

- Extended request validation to cover tip amounts and tip configuration (empty/negative suggestion values, tip conflicts).

### 🐛 Bug Fixes

- Fixed cable-protocol switching so stale callbacks from a previous transport attempt can no longer modify the active attempt.

---

## v1.0.3

**Released 2026-03-27**

A major API upgrade: introduces the type-safe `TaplinkClient` with dedicated request models, replacing the general-purpose `execute(PaymentRequest, …)` flow for new integrations.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.3")
}
```

### 💥 Breaking Changes

- **Connection settings moved from `TaplinkConfig` to `ConnectionConfig`** — connection mode, timeout, automatic reconnection, retry policy, and related settings.
- **Removed `Environment` and `TaplinkException`** and revised the `PaymentError` detail model (now `PaymentError.Detail`). Code that constructs or destructures these types must be updated — catch `PaymentError` and inspect its `category`/`code`/`detail` instead of `TaplinkException`.
- **Removed/relocated internal classes** from the `impl`, `reconnect`, and `util` packages (for example `RequestConverter` → `ProtocolRequestBuilder`). Depend on `TaplinkSDK`, `TaplinkClient`, and public models rather than internal managers.

### 🚀 New Features

- `TaplinkSDK.getClient()` returns a **type-safe `TaplinkClient`** with dedicated methods: `sale`, `auth`, `forcedAuth`, `incrementalAuth`, `postAuth`, `refund`, `void`, `tipAdjust`, `abort`, `query`, and `batchClose`.
- Dedicated request models with builders and validation: `SaleRequest`, `AuthRequest`, `ForcedAuthRequest`, `IncrementalAuthRequest`, `PostAuthRequest`, `RefundRequest`, `VoidRequest`, `TipAdjustRequest`, `AbortRequest`, `BatchCloseRequest`, `QueryRequest`.
- `CardNetworkType` for CREDIT/DEBIT routing, and `PrintReceipt` with `NONE`, `MERCHANT`, `CUSTOMER`, `BOTH`, and `AUTO`.
- `PaymentResult.receiptJson` for receipt data returned by the terminal.

### 🔧 Improvements

- Centralized connection timeout, automatic reconnection, and retry settings in `ConnectionConfig`.
- Expanded the embedded USB AOA, VSP, RS232, and LAN implementations (dedicated AOA sessions, VSP handshaking, LAN connection management, service discovery, and heartbeat components).

### 🧭 Migration from v1.0.2

**1. Move connection settings to `ConnectionConfig`**

```kotlin
// Before (v1.0.2)
val sdkConfig = TaplinkConfig.create(appId, merchantId, secretKey)
    .setConnectionMode(ConnectionMode.LAN)
    .setConnectionTimeout(10_000)
    .setAutoReconnect(true)

// After (v1.0.3)
val sdkConfig = TaplinkConfig.create(appId, merchantId, secretKey)
val connectionConfig = ConnectionConfig()
    .setConnectionMode(ConnectionMode.LAN)
    .setConnectionTimeout(10_000)
    .setAutoReconnect(true)

TaplinkSDK.getInstance().init(context, sdkConfig)
TaplinkSDK.getInstance().connect(connectionConfig, listener)
```

**2. Prefer a dedicated request over `PaymentRequest`**

```kotlin
TaplinkSDK.getClient().sale(
    SaleRequest(
        referenceOrderId = orderId,
        transactionRequestId = requestId,
        amount = amountInfo
    ),
    callback
)
```

**3. Replace `TaplinkException` handling** — remove `catch (e: TaplinkException)` blocks and handle errors through `PaymentCallback.onFailure(PaymentError)`, inspecting `error.category` and `error.code`.

---

## v1.0.2

**Released 2025-12-31**

An internal stability release. **No integration changes are required** — the public class set and method signatures from v1.0.1 are unchanged, so existing call sites keep working as-is.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.2")
}
```

### 🔧 Improvements

- Reorganized the embedded cable stack into dedicated AOA, VSP, and serial layers, and added a USB Standard Host protocol implementation for broader device compatibility.
- Reorganized the network, heartbeat, and application-state monitoring components in the communication layer.

> **Recommended:** a drop-in upgrade from v1.0.1 for improved transport stability with no code changes.

---

## v1.0.1

**Released 2025-12-23**

Unifies the three cable connection modes into a single `CABLE` mode selected by `CableProtocol`, and adds connection persistence and reconnection.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.1")
}
```

### 💥 Breaking Changes

- **Consolidated `ConnectionMode.USB_AOA`, `USB_VSP`, and `RS232` into `ConnectionMode.CABLE`.** Select the cable transport with `CableProtocol` (or let the SDK auto-detect with `CableProtocol.AUTO`).
- **`getConnectionMode()` now returns `String`** (previously `ConnectionMode`) on both `TaplinkApi` and `TaplinkSDK`.
- **Removed the public `BatchCloseResult` and `ConnectionResult` models.** Use the transaction `PaymentResult` or the connection callback parameters instead.

### 🔧 Improvements

- Separated connection, payment, and response processing, and added connection persistence, cable-protocol detection, and reconnection support.
- App-to-App connections now report success **only after** the terminal INIT exchange succeeds, so `onConnected` reflects a truly ready terminal.

### 🧭 Migration from v1.0.0

```kotlin
// Before (v1.0.0)
val sdkConfig = TaplinkConfig.create(appId, merchantId, secretKey)
    .setConnectionMode(ConnectionMode.USB_AOA)
val connectionConfig = ConnectionConfig()

// After (v1.0.1)
val sdkConfig = TaplinkConfig.create(appId, merchantId, secretKey)
    .setConnectionMode(ConnectionMode.CABLE)
val connectionConfig = ConnectionConfig()
    .setCableProtocol(CableProtocol.USB_AOA)   // or CableProtocol.AUTO
```

Update any code that reads the active mode, since the return type is now `String`:

```kotlin
val mode: String = TaplinkSDK.getInstance().getConnectionMode()
```

---

## v1.0.0

**Released 2025-12-15**

The first public release of the Taplink SDK for Android — a semi-integrated payment SDK for Android POS apps.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.0")
}
```

### 🚀 New Features

- **Core entry points:** `TaplinkSDK`, `TaplinkConfig`, `ConnectionConfig`, and `ConnectionListener`.
- **Three connection modes:** App-to-App (same device), LAN (local network), and Cable — with cable support for USB AOA, USB VSP, and RS232, and automatic selection via `CableProtocol.AUTO`.
- **Transaction APIs:** the general-purpose `execute(PaymentRequest, PaymentCallback)` and `query(QueryRequest, PaymentCallback)`.
- **Supported transaction actions:** SALE, REFUND, VOID, AUTH, POST_AUTH, INCREMENT_AUTH, FORCED_AUTH, TIP_ADJUST, BATCH_CLOSE, QUERY, and ABORT.
- **Result handling:** `PaymentCallback.onProgress`, `onSuccess`, and `onFailure`, plus the `PaymentResult.isSuccess()`, `isProcessing()`, and `isFailed()` outcome helpers.

### Getting Started

```kotlin
// 1. Initialize once, in Application.onCreate()
TaplinkSDK.init(context, TaplinkConfig.create(appId, merchantId, secretKey))

// 2. Connect
TaplinkSDK.connect(ConnectionConfig().setConnectionMode(ConnectionMode.APP_TO_APP), listener)

// 3. Run a transaction after connecting
TaplinkSDK.getInstance().execute(paymentRequest, paymentCallback)
```

---

## Artifact Verification

Every release is published to Maven Central. The digests below were verified against the corresponding `.sha256` files published alongside each artifact. Each release ships an AAR and POM (no standalone JAR); the public API is contained in the `classes.jar` embedded in the AAR.

Maven coordinate: `com.sunmi:sunbay-taplink-sdk-android:<version>` · [Browse on Maven Central](https://repo.maven.apache.org/maven2/com/sunmi/sunbay-taplink-sdk-android/)

| Version | AAR SHA-256 | Embedded `classes.jar` SHA-256 | POM SHA-256 |
|---|---|---|---|
| 1.0.0 | `13da3c11a0bd85b08ae1df685db1c38e9ce8b3833fdd6e9cd9045719f10966eb` | `456e37d6fecc9b2e59e751b0e7c6a36e5a0bcda8ebf3e7031216e522ed3847b9` | `e4bd3a961ca118ae6f0b08236126a3fdb2e24759b01be36820d40c0380465da2` |
| 1.0.1 | `7d793fd63054d37d3ec392e1bef3f0087249ee816d10d35db32565c0ee428f58` | `a6521702c7f62ca07cfa3a3735792629cfe95c1b6be3bded6f1d2ae499551f39` | `310016dc7f8abd278c19f55f3fa428ad36dd67aab2540efd6d1c685c8567fec7` |
| 1.0.2 | `af49bb31324caf5555e4d5eeb01414b8b4a09578eace5a23a935f7fd25d71b13` | `05e4831f9e0fa3c4ad27fc0abcf13cfa968f2c8864cfd3b84d16212077428b6d` | `7aeb5363648dcbd2138b6b8a6beca4390139caab2ad50cd53aef6f5f69060d52` |
| 1.0.3 | `d5c41545e15b4badc2b21b404dbb17e3a3e640104d31414a0c87463c5e3dee4c` | `de7d9d300d8fddbe3dae0259f3019c43b0051c3976b65cef41d2924433a5209a` | `06a22a59d674e2301553f602ab1b3a249d57707514d3a5c8b06975754f805402` |
| 1.0.4 | `072450418056944c8368fed4da0ab1e2499fb6b807cb91a9237db4dae5968aba` | `02b6c27c74e8a84133f3b989f4c6518bc9e22c78a7cea6e1c61f066422aef4b8` | `a7e9a0333cae199002f4a00c01a1b24859b101b343a821f13d7e276cd33851b6` |
| 1.0.5 | `913096d71a8f94a65a1affc6fd76acd191facc2797d1e6a57717f45c450d7561` | `500a501f6cc2f5ef56bafdf0718dcc4d78c89e1ab94101d3d2bf41dd6d237c5e` | `b4489547baee90e0488c2886c346febe4a58bfda480d8656a654a3d2de35fbeb` |
| 1.0.6 | `ec1d911666b5348212102b93093caea3a315ce41d98110c01bc2a1db0618efaa` | `8d501b32d4758e44a66d1680b4ab70e00d981f30c33552f6854676e40e749314` | `e18cc148b32af38783ee18cfc0a948af35779f66f6dd47ee5a3d227217a41afb` |
| 1.0.7 | `19b4a016293ecd67bd52241b264218d1496c07eea946a10a8bcb356380fac8e8` | `af80edd949d07c21fd73b498a1109a94284aed122cfd93f4bfa72b2ee72b0037` | `1b75f5d82f12087c32b1a8e36bc53cb2540ef2f2df1ab7b378fad31223c32c58` |

**Method:** version-to-version changes were confirmed by comparing the public API in each release's embedded `classes.jar` and the dependencies declared in each POM, for consecutive coordinates from `1.0.0` through `1.0.7`. Internal build identifiers (e.g. `1.0.7.x`) are not public releases and are intentionally omitted.

---

For full API documentation see the [API Reference](API-REFERENCE.md); for setup and usage guides see the [README](README.md).
