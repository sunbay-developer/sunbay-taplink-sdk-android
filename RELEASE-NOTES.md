# Release Notes

Release history for the **Taplink SDK for Android** (`com.sunmi:sunbay-taplink-sdk-android`).

Releases are listed newest first. Each entry highlights what changed for integrators — new APIs, breaking changes, bug fixes, and step-by-step migration notes.

> **Legend:** 🚀 New &nbsp;·&nbsp; 💥 Breaking &nbsp;·&nbsp; 🔧 Improved &nbsp;·&nbsp; 🐛 Fixed &nbsp;·&nbsp; 📦 Dependencies &nbsp;·&nbsp; 🧭 Migration

**Compatibility:** Min SDK Android API 25 (7.1) · Kotlin 1.7.10 · Java 8+

## Contents

- [Unreleased](#unreleased) — PINPAD / ON_DEVICE integration modes, PinpadStrategy config, AUTO transport fallback
- [v1.0.9](#v109) — Secondary Display connection mode; auto-open USB customer display; cable/USB VSP reconnect fixes
- [v1.0.8](#v108) — LAN discovery & QR scan, signature/tip config source, Jackson migration
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

## Unreleased

> **100% additive & backward-compatible.** All classic APIs (`ConnectionMode.APP_TO_APP` / `LAN` / `CABLE`, `CableProtocol.*`, the `createAppMode/createLanMode/createCableMode` factories) remain **fully supported with no removal planned**. They are marked `@Deprecated` only to *guide new integrations* toward the new integration-mode API — existing integrations require **no changes** and behave exactly as before.

### 🚀 New Features

- **Integration modes decoupled from transport.** `ConnectionMode` gains two business-level values:
  - **`ON_DEVICE`** — the business app and TaPro run on the same terminal (currently implemented via App-to-App). Normalizes internally onto `APP_TO_APP`.
  - **`PINPAD`** — TaPro acts as a standalone PIN Pad reached over a connection channel. The concrete channel is chosen via the new **`PinpadStrategy`** config (mirroring how `CableProtocol` configures `CABLE`), not by a separate top-level mode.

  ```kotlin
  // On-device (same terminal)
  val onDevice = ConnectionConfig.createOnDeviceMode()

  // PinPad over LAN
  val lan = ConnectionConfig.createPinpadMode(PinpadStrategy.LAN, host = "192.168.1.20", port = 8443)

  // PinPad over cable (defaults to cable AUTO try-order: VSP → RS232 → AOA)
  val cable = ConnectionConfig.createPinpadMode(PinpadStrategy.CABLE)

  // PinPad AUTO — pick a transport and fall back if it becomes unusable
  val auto = ConnectionConfig.createPinpadMode(
      PinpadStrategy.AUTO,
      host = "192.168.1.20", port = 8443,
      autoPriority = listOf(PinpadStrategy.LAN, PinpadStrategy.CABLE) // default; configurable
  )
  ```

- **`PinpadStrategy { AUTO, LAN, CABLE }`** — new config enum selecting the PINPAD connection strategy. Set via `ConnectionConfig.setPinpadStrategy(...)`. `PINPAD` defaults to `AUTO` when no strategy is given.

- **Configurable AUTO priority.** `ConnectionConfig.setAutoPriority(...)` (list or single-preferred overload) controls the LAN vs CABLE try order. Default is `[LAN, CABLE]` (`ConnectionConfig.DEFAULT_AUTO_PRIORITY`) — never hard-coded in call sites.

- **AUTO transport fallback.** Under `PinpadStrategy.AUTO`, connection establishment tries each candidate transport in priority order. At runtime, if the active transport becomes blocked (a payment request times out — inner code **306** — while the socket appears connected), the SDK rotates to the next candidate and reconnects. This only affects the **connection layer**; the single-transaction rule (a 306 timeout must be resolved via `query()` first, never blindly retried) is unchanged.

### 🔧 Improved

- **Cable → LAN switching.** Requesting a *different* configuration while a previous attempt is stuck in `CONNECTING` now cancels the stuck attempt and starts fresh, instead of silently queueing behind it.
- **Cable connection reliability.** USB cable kernels (VSP / RS232-Serial / AOA) now fail fast when **no USB device is attached**, avoiding phantom on-board UART polling and freed-up transport switching. AOA control-transfer timeouts were raised (100 ms → 1000 ms) with added diagnostics so the accessory hand-off no longer silently fails.

### 🧭 Migration

| Classic API (still supported) | New integration-mode API (recommended) |
|---|---|
| `ConnectionMode.APP_TO_APP` / `createAppMode()` | `ConnectionMode.ON_DEVICE` / `createOnDeviceMode()` |
| `ConnectionMode.LAN` / `createLanMode(host, port)` | `createPinpadMode(PinpadStrategy.LAN, host, port)` |
| `ConnectionMode.CABLE` / `createCableMode(protocol)` | `createPinpadMode(PinpadStrategy.CABLE, cableProtocol = protocol)` |
| `CableProtocol.AUTO` (cable-only VSP/RS232/AOA) | `createPinpadMode(PinpadStrategy.AUTO)` (spans LAN **and** cable) |
| `ConnectionMode.SECONDARY_DISPLAY` | *unchanged* (independent integration mode) |

> **AUTO semantics note:** Classic `ConnectionMode.CABLE` + `CableProtocol.AUTO` behavior is **unchanged** — it still only selects among cable protocols (VSP/RS232/AOA) and never switches to LAN. The new cross-transport AUTO fallback (LAN ⇄ CABLE) applies **only** when you opt in via `PinpadStrategy.AUTO`.

---

## v1.0.9

### 🚀 New Features

- **`SECONDARY_DISPLAY` connection mode (recommended for cross-device secondary display)** — A dedicated connection mode that combines USB Virtual Serial Port (VSP) transport with mandatory secondary display activation. Use `ConnectionConfig.createSecondaryDisplayMode()` or `ConnectionConfig().setConnectionMode(ConnectionMode.SECONDARY_DISPLAY)` to connect.

  This mode **treats screen player failure as a connection failure**: `onConnected` is only delivered when both the VSP link AND the USB customer-facing display are confirmed ready. If the screen player cannot be opened, `onError` is delivered with the new error code **351** and the connection is torn down.

  ```kotlin
  // Recommended: one-step connection for cross-device secondary display
  val config = ConnectionConfig.createSecondaryDisplayMode()

  TaplinkSDK.connect(config, object : ConnectionListener {
      override fun onConnected(deviceId: String, taproVersion: String) {
          // Both VSP transport AND secondary display are ready.
          // Full transaction API is available (sale, refund, void, etc.).
      }

      override fun onDisconnected(reason: String) { /* ... */ }

      override fun onError(error: ConnectionError) {
          if (error.code == "351") {
              // VSP connected, but secondary display failed to open.
              // Check: TaPro v1.0.5+ running? USB display connected?
          }
      }
  })
  ```

- **Error code 351 — Secondary Display Open Failed** — Returned in `ConnectionListener.onError` when using `SECONDARY_DISPLAY` mode and the remote TaPro fails to open the USB screen player within the timeout window.

- **`TaplinkConfig.setAutoOpenUsbScreenPlayer(true)` (optional, best-effort)** — Makes the SDK open the TaPro USB customer-display player as part of `connect()`, **before** `ConnectionListener.onConnected` is delivered. Unlike `SECONDARY_DISPLAY` mode, this is **non-blocking**: `onConnected` is still delivered even if TaPro fails to answer or does not respond within 3 seconds. Disabled by default. Applies to `connect()`, `autoDiscoverAndConnect()` and `scanAndConnect()`.

  ```kotlin
  // Optional screen player (non-blocking, best-effort — for same-device or nice-to-have scenarios)
  TaplinkSDK.init(
      context,
      TaplinkConfig.create(appId, secretKey)
          .setAutoOpenUsbScreenPlayer(true)
  )
  ```

  **When to use which:**

  | Scenario | Recommended Approach |
  |----------|---------------------|
  | Cross-device secondary display (USB cable between POS and terminal with customer screen) | `ConnectionConfig.createSecondaryDisplayMode()` |
  | Same-device optional screen (App-to-App or LAN, screen is nice-to-have) | `TaplinkConfig.setAutoOpenUsbScreenPlayer(true)` |

  For a complete guide on integrating the secondary display — including display detection, Compose rendering inside `Presentation`, lifecycle management, and common use cases (ad carousel, order list, tip selection) — see **[SECONDARY_DISPLAY_INTEGRATION.md](./SECONDARY_DISPLAY_INTEGRATION.md)**.

### �🐛 Fixed

- **`openUsbScreenPlayer()` no longer fails with `T06`.** The internally generated `transactionRequestId` was built from the action name plus a millisecond timestamp (`OPEN_USB_SCREEN_PLAYER_<13 digits>` = 36 characters), exceeding the terminal's 32-character limit. It now uses a short `USP_<timestamp>` prefix (17 characters). Integrators could not work around this because the API takes no request-id parameter.
- **Cable / `USB_VSP` reconnect no longer times out.** The VSP client waited passively for TaPro to send the application-layer handshake request. TaPro only sends that request during its own connect flow, so when the cable was never physically unplugged — TaPro still reporting the USB link as connected — a reconnect initiated by the POS app alone never received a handshake and `onConnected` was never delivered, leaving the app unable to send further requests. The VSP client now actively sends `##TAPLINK_HSK_REQ##` every second until the peer answers, matching the behaviour already used by the `RS232` and `USB_AOA` transports. No API change is required; `connect()` after a `disconnect()` now succeeds on an already-open cable.

### 🐛 Fixed

- **Cable / `USB_VSP` "cannot connect" right after plugging in the cable.** The VSP client looked for the USB CDC-ACM serial device exactly once at `connect()` time and failed immediately if it was not found. When `connect()` was called right after the cable was plugged in — before the CDC-ACM interface finished enumerating — the attempt failed with "No VSP device found" even though the cable was physically connected. The VSP client now waits up to 6 seconds (polling every 500 ms, fully cancellable) for the VSP device to appear before giving up, matching the plug-and-play behaviour of the `RS232` transport. No API change is required.
- **Cable `AUTO` no longer mis-orders a `USB_VSP` device as `RS232`.** A Sunmi VSP virtual serial endpoint can enumerate under a USB-serial bridge vendor id (e.g. Prolific `0x067B`). The inserted-cable classifier now recognises the VSP `CDC ACM Data` interface and excludes such devices from the RS232 match, so a plugged-in VSP cable is tried on `USB_VSP` first instead of wasting an `RS232` attempt.

### 🔧 Improved

- **Cable `AUTO` mode now connects the inserted cable first (plug-and-play).** Previously `AUTO` tried protocols in a fixed order (`USB_VSP` → `RS232` → `USB_AOA`), reordered only by the cached last-successful protocol, so a freshly plugged-in `RS232`/`USB_AOA` cable had to wait for the on-board VSP attempt to fail before it connected. `AUTO` now inspects the USB subsystem and tries the physically inserted cable's protocol first — `RS232` when a USB-serial bridge chip (CH340 / PL2303 / FTDI / CP210x) is present, `USB_AOA` when a USB accessory is present — then retries the remaining protocols in base order. When no external cable is detected, the on-board `USB_VSP` UART is tried first, followed by the cached last-successful protocol as a hint. Every protocol is still retried until one succeeds; only the try order changes. No API change is required.

### 🧭 Migration

- No code changes required. Update the dependency version:

  ```kotlin
  dependencies {
      implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.9")
  }
  ```

- When reconnecting, still wait until `TaplinkSDK.getConnectionStatus()` reports `DISCONNECTED` before calling `connect()` again — `disconnect()` returns before the underlying serial handle is fully released.

---

## v1.0.8

**Released 2026-08-07**

Adds LAN terminal discovery (mDNS and QR code scan), per-transaction signature and tip configuration with an explicit configuration source, more granular transaction progress events, and convenient `ConnectionConfig` factory methods. JSON serialisation moves from Gson to Jackson.

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.8")
}
```

### 🚀 New Features

- **LAN service discovery** — `TaplinkSDK.discoverLanServices(DiscoveryListener)` runs a one-shot mDNS (`_taplink._tcp`) discovery and returns the resolved `host`/`port` list **without connecting**. Discovery times out after 15 seconds.
- **LAN QR code scan** — `TaplinkSDK.scanLanQrCode(DiscoveryListener)` opens the SDK's built-in full-screen camera scanner, reads a `lan://host/port` QR code shown by Tapro, and returns the address **without connecting**.
- **One-call connection variants** — `TaplinkSDK.autoDiscoverAndConnect(ConnectionListener)` and `TaplinkSDK.scanAndConnect(ConnectionListener)` discover/scan and connect in a single step.
- **`DiscoveredService`** model and **`DiscoveryListener`** callback for the new discovery APIs.
- **Discovery/scan error codes** `E501`–`E506` (no services found, all failed, already in progress, user cancel, permission denied, no camera).
- **Signature routing** — `signatureConfig` on `SaleRequest`, `AuthRequest` and `RefundRequest.nonReferencedBuilder()` controls per-transaction signature capture. `SignatureConfig.useHostConfig` (default `true`) selects the configuration source **as a whole**: `true` uses the terminal's signature settings and ignores `entryLocation`/`threshold`; `false` uses the request configuration only and never reads the terminal settings. With `useHostConfig = false`, `entryLocation` is mandatory — `ON_SCREEN` forces the e-signature page, `ON_RECEIPT` skips the screen and prints a signature line, and `NONE` explicitly disables signature capture. Optional `threshold` (smallest currency unit) captures a signature only when the final amount is **greater than** the threshold. For compatibility with the previous `signatureEntryLocation` field, providing an `entryLocation` is by itself enough to select the request configuration — `SignatureConfig(entryLocation = ON_SCREEN)` overrides the terminal settings instead of being ignored because `useHostConfig` defaults to `true` (see `SignatureConfig.resolvedUseHostConfig`).
- **Signature on non-referenced refunds** — `RefundRequest.nonReferencedBuilder().setSignatureConfig(...)` applies the same signature rules to a non-referenced refund, where the cardholder presents a card and can be asked to sign. A **referenced** refund reuses the original transaction's card data and rejects `signatureConfig` with `IllegalArgumentException` (raw JSON / Cloud requests are rejected with `E302`).
- **Request-driven receipt tip area** — when the request tip configuration applies (`useHostConfig = false`), it replaces the terminal's receipt tip settings **as a whole** and the two are never combined. The tip is captured in exactly one place: with `onScreenTip = true` the amount is settled on the tip screen and the receipt carries no tip area at all, while `onScreenTip = false` moves capture to the receipt — the terminal prints the suggested tip amounts when `suggestions` is provided, or a blank tip line when it is not, so the cardholder can write the tip by hand. The terminal's own blank tip line is never added on top. The resulting tip area is also written into the `receiptJson` returned with the transaction result, so it is available whether the terminal prints the receipt or your app does.
- **Tip configuration source** — `TipConfig.useHostConfig` (default `false`) selects the tip configuration source as a whole: `false` uses the request fields as-is, and any field left out is treated as "not specified by the request" rather than falling back to the terminal value — this now applies to `tipMode` and `tipWithTax` as well as to `suggestions`. `true` uses the terminal's tip configuration and ignores `onScreenTip`/`tipMode`/`tipWithTax`/`suggestions`; it is evaluated **before** `onScreenTip`, so `useHostConfig = true` combined with `onScreenTip = false` does not skip tipping. When `true` and the terminal has no tip configuration, the transaction is **not** blocked — the tip flow is simply skipped. Note that `tipMode` and `tipWithTax` are non-null with defaults in the typed SDK, so only raw-JSON and Cloud requests can actually omit them.
- **More `PaymentEvent` progress states** — `PaymentCallback.onProgress(event)` can now report additional stages of the transaction, including `OnlineProcessing`, `EmvProcessing`, `RemoveCard`, `CardRemoved`, `SignatureCompleted`, `PrintCompleted` and `PrintFailed`. Event codes the SDK does not recognize are delivered as a generic fallback event instead of being dropped, so a newer terminal never breaks an older SDK. Treat `PaymentEvent` as an **open set**: match only the states you care about and always keep an `else` branch.
- **`AppToAppMode`** enum with `ConnectionConfig.setAppToAppMode(mode)` and `ConnectionConfig.createAppMode(mode)` for selecting the App-to-App behaviour per connection.
- **`TipSuggestions.names`** — optional display labels matched positionally to `values`; set through the constructor or `setNames(...)`. When `names` is missing or its size differs from `values`, the labels are ignored and the values are still used.
- **`PaymentResult.relatedTransactionStatus` and `PaymentResult.transactionBatchStatus`** — expose the status of the related transaction and of the batch the transaction belongs to.
- **`PrintReceipt.TOTAL` and `PrintReceipt.DETAIL`** enum constants for batch report printing.
- **`ConnectionConfig` factory methods** — `createAppMode()`, `createCableMode()`, `createLanMode(host, port)`, `createLanMode()` (cached), and `createDefault()` for concise connection setup.

### 💥 Breaking Changes

- **JSON serialisation moved from Gson to Jackson.** The published POM now declares `com.fasterxml.jackson.core:jackson-databind:2.17.2` and `com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2` instead of `com.google.code.gson:gson:2.13.1`. Both are `implementation` dependencies of the SDK, so **integrations that only use the typed request/response models need no change** — the switch is not visible through `TaplinkClient`, `SaleRequest`, `PaymentResult`, and so on.
- **`BasicRequest.bizData` and `BasicResponse.bizData` changed type** from `com.google.gson.JsonObject` to `String` (the raw JSON payload). This only affects integrations that build or read the low-level protocol envelope directly. Parse the string with your own JSON library, or move to the typed request/response models.
- **`BasicResponseTypeAdapter` (Gson) was removed** and replaced by `BasicResponseJacksonSerializer` / `BasicResponseJacksonDeserializer`. These are internal serialisation helpers — remove any manual Gson `registerTypeAdapter` wiring for `BasicResponse`.
- **`PaymentEvent.WaitingOnlineResponse` was removed.** Use `PaymentEvent.OnlineProcessing` instead, which reports the same stage. Because `PaymentEvent` gained further states in this release, an exhaustive `when` over it will no longer compile — add an `else` branch.
- **`TipConfig` and `TipSuggestions` constructors gained parameters** (`useHostConfig` and `names` respectively). Named arguments and the `setXxx()` chaining API are unaffected; only positional construction needs updating.
- **`ConnectionConfig.createAppMode()` now takes an optional `AppToAppMode`.** Kotlin callers are unaffected thanks to the default value; Java callers using the no-argument form must pass `AppToAppMode.CUSTOM` explicitly.

### 📦 Dependencies

- **Jackson replaces Gson** — `jackson-databind` and `jackson-module-kotlin` **2.17.2**. If you previously added Gson manually to work around missing SDK classes, that workaround is no longer needed. Applications that use Gson for their own code are unaffected — the SDK no longer pulls it in transitively.
- QR scanner is built on **CameraX 1.3.4** and **ZXing 3.5.3**. These are `implementation` dependencies inside the SDK and are **not** exposed transitively. Host apps that use `scanLanQrCode` or `scanAndConnect` must add them explicitly:

```kotlin
dependencies {
    // Required only if you use the QR scan APIs
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.zxing:core:3.5.3")
}
```

- mDNS discovery (`discoverLanServices` / `autoDiscoverAndConnect`) uses Android's built-in NSD — **no extra dependency required**.

### 🔧 Improvements

- `CAMERA` permission and scanner Activity are pre-declared in the SDK manifest — no manifest changes needed in your app.
- Scanner selects back camera → front camera → first available camera; reports `E506` if none is usable.
- `BatchCloseRequest.printReceipt` now supports batch-report-specific values: `TOTAL` (total report only) and `DETAIL` (detail report only) alongside the existing `AUTO`, `BOTH`, and `NONE`.
- `BatchCloseRequest` no longer rejects the transaction-receipt values `MERCHANT` and `CUSTOMER`. They describe receipt copies and are meaningless for a batch report, so the SDK normalizes them (together with `AUTO` and `null`) to `AUTO` through the new `BatchCloseRequest.resolvedPrintReceipt` property. The terminal therefore falls back to its own batch report configuration instead of receiving a validation error.

### 🧭 Migration from v1.0.7

For integrations built on the typed request/response models (`TaplinkClient`, `SaleRequest`, `PaymentResult`, …), v1.0.8 is a drop-in upgrade — the Gson-to-Jackson switch is invisible at that level. Check the [Breaking Changes](#-breaking-changes) above if you touch `BasicRequest`/`BasicResponse` directly or exhaustively match on `PaymentEvent`. Beyond that, these details are worth checking:

- **`TipConfig` no longer falls back to the terminal configuration field by field.** With the default `useHostConfig = false`, `suggestions = null` means "no suggestions" instead of "use the terminal's suggested values". Pass `TipConfig(useHostConfig = true)` to keep letting the terminal own the tip experience, or provide `suggestions` explicitly.
- **Batch Close no longer fails on `MERCHANT` / `CUSTOMER`.** If your integration relied on receiving a validation error for those values, note that a batch close is now accepted and printed according to the terminal's batch report configuration.
- **`TipConfig` gained `useHostConfig` as its first constructor parameter.** Named arguments and the `setXxx()` chaining API are unaffected; only positional construction such as `TipConfig(true, TipMode.ON_SALE)` needs updating.

**Signature entry location (raw JSON / Cloud integrations)**

Integrations that send the legacy `signatureEntryLocation` field — the Cloud API and raw-JSON semi-integrations — keep working unchanged: Tapro maps it to a request-side `signatureConfig` with the same override semantics as before. `signatureConfig` takes precedence when both are present.

```jsonc
// Still supported — behaves exactly as in v1.0.7
{ "signatureEntryLocation": "ON_RECEIPT" }

// Equivalent v1.0.8 form
{ "signatureConfig": { "useHostConfig": false, "entryLocation": "ON_RECEIPT" } }

// Also equivalent — an explicit entryLocation implies the request configuration
{ "signatureConfig": { "entryLocation": "ON_RECEIPT" } }
```

**LAN Discovery (optional)**

Replace manual IP/port input with automatic discovery:

```kotlin
// Before (v1.0.7) — user must type IP and port manually
val config = ConnectionConfig()
    .setConnectionMode(ConnectionMode.LAN)
    .setHost("192.168.1.100")
    .setPort(8443)
TaplinkSDK.connect(config, listener)

// After (v1.0.8) — auto-discover and connect
TaplinkSDK.autoDiscoverAndConnect(listener)

// Or: discover first, then connect manually
TaplinkSDK.discoverLanServices(object : DiscoveryListener {
    override fun onDiscovered(services: List<DiscoveredService>) {
        val target = services.firstOrNull() ?: return
        val config = ConnectionConfig.createLanMode(target.host, target.port)
        TaplinkSDK.connect(config, listener)
    }
    override fun onError(error: ConnectionError) { showError(error.message) }
})
```

**Signature Routing (optional)**

Add per-transaction signature control to Sale or Auth. Omitting `signatureConfig` keeps the v1.0.7 behavior (the terminal's own signature settings apply):

```kotlin
// Use the terminal (host) signature configuration — same as omitting the object
val request = SaleRequest.builder()
    .setReferenceOrderId("ORDER_001")
    .setTransactionRequestId("TXN_${System.currentTimeMillis()}")
    .setAmount(AmountInfo.of(1000L, "USD"))
    .setSignatureConfig(SignatureConfig.useHostConfig())  // new in v1.0.8
    .build()

// Use the request configuration only
.setSignatureConfig(SignatureConfig.onScreen())                          // always sign on screen
.setSignatureConfig(SignatureConfig.onScreenAbove(BigDecimal("2500")))   // sign above $25.00
.setSignatureConfig(SignatureConfig.onReceipt())                         // print a signature line
.setSignatureConfig(SignatureConfig.none())                              // explicitly no signature
```

**Tip Configuration Source (optional)**

`TipConfig` gains `useHostConfig`. The default (`false`) keeps the v1.0.7 request-driven behavior, except that `suggestions` is no longer implicitly filled in from the terminal — pass it explicitly when you need suggested values:

```kotlin
// Let the terminal own the whole tip experience
TipConfig(useHostConfig = true)

// Drive the tip experience entirely from the request
TipConfig(
    useHostConfig = false,
    onScreenTip = true,
    tipMode = TipMode.ON_SALE,
    suggestions = TipSuggestions(FeeMode.RATE, listOf(15, 18, 20))
)
```

See also the [API Reference](API-REFERENCE.md) and the [LAN Address Acquisition guide](README.md#lan-address-acquisition-discovery--qr-scan).

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
