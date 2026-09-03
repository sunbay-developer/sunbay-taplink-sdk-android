# Release Notes

Release history for the **Taplink SDK for Android** (`com.sunmi:sunbay-taplink-sdk-android`).

Releases are listed newest first. Each entry highlights what changed for integrators — new APIs, breaking changes, bug fixes, and step-by-step migration notes.

> **Legend:** 🚀 New &nbsp;·&nbsp; 💥 Breaking &nbsp;·&nbsp; 🔧 Improved &nbsp;·&nbsp; 🐛 Fixed &nbsp;·&nbsp; 📦 Dependencies &nbsp;·&nbsp; 🧭 Migration

**Compatibility:** Min SDK Android API 24 (7.0) · Kotlin 1.7.10 · Java 8+

## Contents

- [v1.0.9](#v109) — CROSS_DEVICE / ON_DEVICE integration modes, `CrossDeviceStrategy`, AUTO channel failover, Sub_Screen mode, `getTerminalInfo()`, LAN & cable stability fixes
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

## v1.0.9

**Released 2026-09-03**

This release reorganizes how a connection is described. Integration is now expressed as a **business form** — where the business app and TaPro physically live relative to each other — instead of as a **transport** (App-to-App / LAN / Cable). Everything from v1.0.8 keeps working unchanged; the new API is additive.

### 💥 Connection model: what changed since v1.0.8

**Before (v1.0.8 and earlier)** — you picked the transport directly:

```kotlin
ConnectionConfig().setConnectionMode(ConnectionMode.APP_TO_APP)          // same device
ConnectionConfig().setConnectionMode(ConnectionMode.LAN).setHost(...)    // over the network
ConnectionConfig().setConnectionMode(ConnectionMode.CABLE)
    .setCableProtocol(CableProtocol.AUTO)                                // over a cable
```

**Now (v1.0.9)** — you pick the integration form, and the transport is a property of it:

```kotlin
// Same terminal: business app and TaPro on one device
ConnectionConfig.createOnDeviceMode()

// Separate terminal (TaPro as a standalone PIN Pad), reached over LAN
ConnectionConfig.createCrossDeviceMode(CrossDeviceStrategy.LAN, host = "192.168.1.20", port = 8443)

// Separate terminal, reached over a cable (cable try-order VSP -> RS232 -> AOA)
ConnectionConfig.createCrossDeviceMode(CrossDeviceStrategy.CABLE)

// Separate terminal, SDK picks the channel and re-routes if the active one drops
ConnectionConfig.createCrossDeviceMode(
    CrossDeviceStrategy.AUTO,
    host = "192.168.1.20", port = 8443
)
```

| | v1.0.8 model | v1.0.9 model |
|---|---|---|
| Selection unit | Transport (`ConnectionMode`) | Integration form (`ConnectionMode`) + channel (`CrossDeviceStrategy`) |
| Same-device | `APP_TO_APP` | `ON_DEVICE` (normalizes onto App-to-App internally) |
| Separate device | `LAN` **or** `CABLE`, chosen at build time | `CROSS_DEVICE` + `LAN` / `CABLE` / `AUTO` |
| Channel failover | none across transports — a LAN outage ended the session | `AUTO` rotates LAN <-> cable after a real drop and reconnects the **same** terminal |
| Cable-only auto | `CableProtocol.AUTO` (VSP/RS232/AOA) | unchanged; `CrossDeviceStrategy.AUTO` is a separate, wider concept |
| Sub-screen | `SUB_SCREEN` | unchanged |

**Scope of the change**

- **No breaking change.** `APP_TO_APP`, `LAN`, `CABLE`, `CableProtocol`, `createAppMode()`, `createLanMode()`, `createCableMode()` and `SUB_SCREEN` all behave exactly as in v1.0.8. The mapping table is in the Migration section below.
- **`CrossDeviceStrategy.AUTO` is opt-in.** It is the only thing that can change transports mid-session, and it does so **only after the active link actually drops** — never while a transaction is in flight.
- **`CrossDeviceStrategy` has no `CLOUD` value on purpose.** Cloud-dispatched orders are delivered to TaPro by the cloud backend; the SDK never establishes or drives a Cloud connection. A `Cross-Device / Cloud` badge shown on TaPro is a TaPro-side display concern only.

### 🚀 New Features

- **Integration modes decoupled from transport.** `ConnectionMode` gains two business-level values:
  - **`ON_DEVICE`** — the business app and TaPro run on the same terminal (currently implemented via App-to-App). Normalizes internally onto `APP_TO_APP`.
  - **`CROSS_DEVICE`** — TaPro acts as a standalone PIN Pad reached over a connection channel. The concrete channel is chosen via the new **`CrossDeviceStrategy`** config (mirroring how `CableProtocol` configures `CABLE`), not by a separate top-level mode.

  ```kotlin
  // On-device (same terminal)
  val onDevice = ConnectionConfig.createOnDeviceMode()

  // Cross-Device over LAN
  val lan = ConnectionConfig.createCrossDeviceMode(CrossDeviceStrategy.LAN, host = "192.168.1.20", port = 8443)

  // Cross-Device over cable (defaults to cable AUTO try-order: VSP → RS232 → AOA)
  val cable = ConnectionConfig.createCrossDeviceMode(CrossDeviceStrategy.CABLE)

  // Cross-Device AUTO — pick a transport and fall back if it becomes unusable
  val auto = ConnectionConfig.createCrossDeviceMode(
      CrossDeviceStrategy.AUTO,
      host = "192.168.1.20", port = 8443,
      autoPriority = listOf(CrossDeviceStrategy.LAN, CrossDeviceStrategy.CABLE) // default; configurable
  )
  ```

- **`CrossDeviceStrategy { AUTO, LAN, CABLE }`** — new config enum selecting the CROSS_DEVICE connection strategy. Set via `ConnectionConfig.setCrossDeviceStrategy(...)`. `CROSS_DEVICE` defaults to `AUTO` when no strategy is given.

- **Configurable AUTO priority.** `ConnectionConfig.setAutoPriority(...)` (list or single-preferred overload) controls the LAN vs CABLE try order. Default is `[LAN, CABLE]` (`ConnectionConfig.DEFAULT_AUTO_PRIORITY`) — never hard-coded in call sites.

- **`CrossDeviceStrategy` intentionally has no `CLOUD` value.** Cloud-dispatched orders are delivered to TaPro directly by the cloud backend; the SDK never establishes, authenticates, or drives a Cloud connection. TaPro may still display a `Cross-Device / Cloud` badge based on service-plan/sign-in permissions, but that is a TaPro-side display concern only — it is not selectable or resolvable through this SDK.

- **AUTO transport fallback happens only after the active connection drops.** Transport switching in a `CrossDeviceStrategy.AUTO` session is performed **exclusively when the current connection actually disconnects** (cable unplugged, LAN/Wi-Fi lost, peer reset). The SDK then rotates to the next candidate and reconnects — **cable dropped → LAN, LAN dropped → cable (if a cable candidate is present)**. This is the default for any AUTO session with two or more candidates; no opt-in flag is needed. The SDK never proactively tears down a live connection to switch transports, so an in-flight transaction is never interrupted by a transport change. During the reconnect the SDK reports `CONNECTING` (never a stale `CONNECTED`), and if no transport is reachable it settles on `DISCONNECTED` instead of hanging.

- **Same-device mDNS discovery on LAN fallback (serial-matched, configured host as fallback).** When an AUTO fallback needs the LAN transport, the SDK runs a bounded mDNS discovery and connects to the **current advertised address of the SAME TaPro**, matched by the serial parsed from the `Taplink-Server-…-<serial>` service name. The target serial is taken from the live/persisted device id or, crucially, **the serial persisted from a prior LAN connection to that device** — so once you have connected a device over LAN, later fallbacks rediscover exactly that device by serial even if its IP changed. AUTO sessions must configure a LAN `host`; when no serial match is available (e.g. a cable-first session that never established LAN) the SDK uses that configured host as the fallback target instead of connecting an arbitrary discovered terminal.

- **`getTerminalInfo()` — read-only terminal info query.** The `GET_TERMINAL_INFO` control action lets integrators fetch the merchant and terminal information for **the single, currently connected TaPro device** — not a cloud merchant lookup, and not a terminal-list query (no pagination, no `terminals[]`, no `nextToken`).

  ```kotlin
  TaplinkSDK.getTerminalInfo(object : TerminalInfoCallback {
      override fun onSuccess(info: TerminalInfo) {
          // info.merchantId, info.dbaName, info.midList, info.terminal.sn, info.terminal.model, ...
      }

      override fun onFailure(error: PaymentError) {
          // error.code == "352" → TaPro does not support GET_TERMINAL_INFO, please upgrade TaPro
      }
  })
  ```

  - Public API surface: `TaplinkApi.getTerminalInfo(callback)`, `TaplinkSDK.getTerminalInfo(callback)` (static + instance), `TaplinkClient.getTerminalInfo(callback)`.
  - New independent callback `TerminalInfoCallback` (`onSuccess(TerminalInfo)` / `onFailure(PaymentError)`) — **not** `PaymentCallback`. `GET_TERMINAL_INFO` is a read-only control action, not a transaction: it does not create a transaction, does not require a `transactionRequestId`, and its result must never be interpreted with payment-progress/success semantics.
  - New public models: `TerminalInfo`, `MerchantMid`, `Terminal`, `TerminalTid`. Top-level merchant fields (`merchantId`, `dbaName`, `mcc`, `country`, `stateName`, `cityName`, `street`, `detailAddress`, `zipCode`, `midList`) mirror the public "Retrieve merchant" API contract; `terminal` is nested (mirroring a single element of the "List merchant terminals" contract: `sn`, `vendor`, `model`, `tidList`). Merchant branding metadata is intentionally excluded from the SDK response because this read-only query is limited to merchant/terminal identity metadata. Never returns `secretKey`, `appId`, network addresses, card data, or transaction details. Merchant `status` and `createTime` (merchant and terminal level) are intentionally **not** part of the response — TaPro has no real, backend-provisioned source for them today, so they are omitted rather than fabricated or returned as `null` placeholders.
  - `GET_TERMINAL_INFO` responds even while TaPro is processing a payment transaction, and is not affected by transactional API-permission / service-plan gating — though it still goes through the same request signature verification as every other action.
  - Legacy TaPro versions that do not recognize `GET_TERMINAL_INFO` return a misleading busy/unsupported-action error (e.g. `303`); the SDK now converts this into a clear, dedicated error code **`352`** ("TaPro does not support GET_TERMINAL_INFO, please upgrade TaPro") so integrators are not confused by false "transaction in progress" reports.

- **`SUB_SCREEN` connection mode (recommended for cross-device secondary display)** — A dedicated connection mode that combines USB Virtual Serial Port (VSP) transport with mandatory sub-screen activation. Use `ConnectionConfig.createSubScreenMode()` or `ConnectionConfig().setConnectionMode(ConnectionMode.SUB_SCREEN)` to connect.

  This mode **treats screen player failure as a connection failure**: `onConnected` is only delivered when both the VSP link AND the USB customer-facing display are confirmed ready. If the screen player cannot be opened, `onError` is delivered with the new error code **351** and the connection is torn down.

  ```kotlin
  // Recommended: one-step connection for cross-device sub-screen
  val config = ConnectionConfig.createSubScreenMode()

  TaplinkSDK.connect(config, object : ConnectionListener {
      override fun onConnected(deviceId: String, taproVersion: String) {
          // Both VSP transport AND the sub-screen are ready.
          // Full transaction API is available (sale, refund, void, etc.).
      }

      override fun onDisconnected(reason: String) { /* ... */ }

      override fun onError(error: ConnectionError) {
          if (error.code == "351") {
              // VSP connected, but the sub-screen failed to open.
              // Check: TaPro v1.0.5+ running? USB display connected?
          }
      }
  })
  ```

- **Error code 351 — Sub_Screen Open Failed** — Returned in `ConnectionListener.onError` when using `SUB_SCREEN` mode and the remote TaPro fails to open the USB screen player within the timeout window.

  For a complete guide on integrating the secondary display — including display detection, Compose rendering inside `Presentation`, lifecycle management, and common use cases (ad carousel, order list, tip selection) — see **[SUB_SCREEN_INTEGRATION.md](./SUB_SCREEN_INTEGRATION.md)**.

### 🔧 Improved

- **Cable → LAN switching.** Requesting a *different* configuration while a previous attempt is stuck in `CONNECTING` now cancels the stuck attempt and starts fresh, instead of silently queueing behind it.
- **Cable connection reliability.** USB cable kernels (VSP / RS232-Serial / AOA) now fail fast when **no USB device is attached**, avoiding phantom on-board UART polling and freed-up transport switching. AOA control-transfer timeouts were raised (100 ms → 1000 ms) with added diagnostics so the accessory hand-off no longer silently fails.
- **Fail-fast on an unresponsive cable link.** Before every transaction/query the SDK now runs a lightweight liveness probe on the active transport (for VSP cable, a handshake round-trip). If the peer (TaPro) has stopped responding — e.g. its UART receive loop is stuck with a *"port timeout"* after a prior transaction — the call now returns error **`213` (connection disconnected)** within ~3 s instead of hanging until the full transaction timeout. The dead link is torn down so the next call can reconnect. Integrators should treat `213` like other connection errors (reconnect and retry).
- **Cable `AUTO` mode now connects the inserted cable first (plug-and-play).** Previously `AUTO` tried protocols in a fixed order (`USB_VSP` → `RS232` → `USB_AOA`), reordered only by the cached last-successful protocol, so a freshly plugged-in `RS232`/`USB_AOA` cable had to wait for the on-board VSP attempt to fail before it connected. `AUTO` now inspects the USB subsystem and tries the physically inserted cable's protocol first — `RS232` when a USB-serial bridge chip (CH340 / PL2303 / FTDI / CP210x) is present, `USB_AOA` when a USB accessory is present — then retries the remaining protocols in base order. When no external cable is detected, the on-board `USB_VSP` UART is tried first, followed by the cached last-successful protocol as a hint. Every protocol is still retried until one succeeds; only the try order changes. No API change is required.

### 🐛 Fixed

- **LAN connection no longer drops (~every 5s) on mDNS re-registration.** TaPro re-advertises its mDNS service with a fresh timestamp in the instance name (`Taplink-Server-<timestamp>-<serial>`) without actually restarting. The client mis-classified the new instance name at the same host:port as a "peer restart" and force-closed the healthy WebSocket (surfacing as a `1000` normal closure loop). Peer-restart detection now compares the parsed **serial**, so a same-serial re-registration (timestamp-only change) keeps the live socket instead of tearing it down.
- **AUTO fallback no longer storms on LAN-only setups.** A LAN drop/churn could trigger the AUTO passive-drop fallback, which then rotated through cable candidates that all fail with "no USB device attached" before reconnecting LAN — repeating every few seconds. AUTO fallback from the kernel status listener is now restricted to CABLE transports, and a LAN drop with no physically-attached cable is left to the LAN kernel's own reconnect instead of triggering a cable rotation. A passive-drop fallback triggered while a transaction/query is still awaiting its response would tear down the transport, dropping the response callback (and, on a shared network, risk reconnecting to a different terminal). The SDK now checks for in-flight transactions and skips the switch — the normal disconnect path fails the pending transaction and notifies the integrator, who can `query()` and retry.
- **AUTO LAN fallback never connects an arbitrary terminal.** When falling back to LAN without a reliable target serial (e.g. a cable-first session that never established LAN and has no persisted serial), the SDK no longer connects the *first* discovered mDNS service — which on a multi-terminal network is very likely the wrong TaPro. It now refuses to connect an unverified service and reports `DISCONNECTED` instead; a preconfigured `host` is still honored.
- **LAN target serial is now retained across transport switches for same-device discovery.** A Cross-Device AUTO session parses the Tapro serial from the LAN mDNS service name, but that serial lived only on the LAN kernel and was lost when the transport switched to cable or the link dropped — so a later "cable dropped → rediscover the SAME TaPro over LAN" could not match the device. The serial is now persisted on every successful connection and used as a last-resort source for same-device mDNS matching. Additionally, a LAN connection's `onConnected` deviceId of `"unknown"` no longer overwrites a previously stored valid device id.
- **AUTO session no longer reports `CONNECTED` after the peer dropped.** In a Cross-Device AUTO session, when the active transport dropped (e.g. cable unplugged) and the LAN fallback could not immediately reach TaPro, the SDK could remain in `CONNECTED` even though TaPro was gone. The passive-drop fallback now transitions to `CONNECTING` while it discovers/reconnects the next transport, and settles on `DISCONNECTED` when no transport is reachable — the reported status always reflects the real link state. The fallback is now also driven by the unified kernel **status listener**, so transports that signal a drop only via a status change (notably USB AOA on device-detach, which updates kernel status without invoking the connection callback) still trigger the automatic LAN fallback instead of leaving the SDK stuck in `CONNECTED`.
- **`CrossDeviceStrategy.LAN` no longer attempts a USB/cable connection.** When a Cross-Device session explicitly selected the LAN strategy but the configuration also carried a `cableProtocol` value (common when callers pass a non-null protocol regardless of the chosen strategy), protocol resolution matched the cable branch before the LAN branch and connected over USB AOA instead of the configured host — triggering a USB permission prompt and failing with error **`212`**, while the configured `host`/`port` were never used. LAN now takes precedence during protocol resolution, `CrossDeviceStrategy.LAN` clears any residual cable protocol, and a LAN session can no longer fall back to the cable try-order.

- **LAN connection no longer hangs forever after TaPro restarts.** When the TaPro terminal restarted (or its WebSocket server was recreated) without a graceful close, the client socket was left *half-open*: the local endpoint still reported `ESTABLISHED`, so no close callback ever arrived and the SDK kept reporting `CONNECTED` while every request silently disappeared with no error, no timeout and no reconnect. Three defects combined to produce this:
  - The LAN client did not enable WebSocket protocol-level ping/pong (RFC 6455), so a vanished peer was never detected. Keepalive is now enabled with a 60-second timeout. This is transport-level liveness detection only — no business message is exchanged and TaPro answers pings automatically in the WebSocket layer — so it does not reintroduce an application heartbeat. The interval is deliberately generous: during a transaction TaPro is busy driving the card reader, PIN pad and online authorization, and its automatic pong can legitimately stall for several seconds, so a tighter value risks tearing down the socket mid card-read and losing an in-flight payment result.
  - TaPro re-registers its mDNS service under a **new instance name at the same `host:port`** when it restarts. That is neither an address change (the endpoint is identical) nor a socket event, so it was ignored entirely. The SDK now recognises this pattern as a peer restart and invalidates the stale link so the existing reconnect path can run.
  - Every LAN close was reported upward as a generic `E213`, discarding the real WebSocket close code. Close code `1006` (abnormal closure) was consequently classified as a permanent connection error that skipped auto-reconnect, so a routine network drop left the SDK stuck until a manual reconnect. The real close code is now propagated and `1006` is treated as a recoverable disconnect that triggers auto-reconnect.

- **mDNS discovery no longer floods with `Resolve failed ... error: 3`.** Every TaPro terminal advertising on the subnet was resolved concurrently, but Android's `NsdManager` permits only one in-flight `resolveService()` call and rejects the rest with `FAILURE_ALREADY_ACTIVE` (error 3) — including the resolve for the terminal actually being connected to. Discovery now resolves only the bound terminal and serialises resolve requests, with a watchdog that releases the resolve slot if `NsdManager` never invokes either callback.

- **`openUsbScreenPlayer()` no longer fails with `T06`.** The internally generated `transactionRequestId` was built from the action name plus a millisecond timestamp (`OPEN_USB_SCREEN_PLAYER_<13 digits>` = 36 characters), exceeding the terminal's 32-character limit. It now uses a short `USP_<timestamp>` prefix (17 characters). Integrators could not work around this because the API takes no request-id parameter.
- **Cable / `USB_VSP` reconnect no longer times out.** The VSP client waited passively for TaPro to send the application-layer handshake request. TaPro only sends that request during its own connect flow, so when the cable was never physically unplugged — TaPro still reporting the USB link as connected — a reconnect initiated by the POS app alone never received a handshake and `onConnected` was never delivered, leaving the app unable to send further requests. The VSP client now actively sends `##TAPLINK_HSK_REQ##` every second until the peer answers, matching the behaviour already used by the `RS232` and `USB_AOA` transports. No API change is required; `connect()` after a `disconnect()` now succeeds on an already-open cable.

- **Cable / `USB_VSP` "cannot connect" right after plugging in the cable.** The VSP client looked for the USB CDC-ACM serial device exactly once at `connect()` time and failed immediately if it was not found. When `connect()` was called right after the cable was plugged in — before the CDC-ACM interface finished enumerating — the attempt failed with "No VSP device found" even though the cable was physically connected. The VSP client now waits up to 6 seconds (polling every 500 ms, fully cancellable) for the VSP device to appear before giving up, matching the plug-and-play behaviour of the `RS232` transport. No API change is required.
- **Cable `AUTO` no longer mis-orders a `USB_VSP` device as `RS232`.** A Sunmi VSP virtual serial endpoint can enumerate under a USB-serial bridge vendor id (e.g. Prolific `0x067B`). The inserted-cable classifier now recognises the VSP `CDC ACM Data` interface and excludes such devices from the RS232 match, so a plugged-in VSP cable is tried on `USB_VSP` first instead of wasting an `RS232` attempt.

### 🧭 Migration

Update the dependency version:

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.9")
}
```

**No code changes are required.** The table below is the recommended mapping if you want to adopt the new integration-mode API:

| v1.0.8 API (still supported) | v1.0.9 API (recommended) |
|---|---|
| `ConnectionMode.APP_TO_APP` / `createAppMode()` | `ConnectionMode.ON_DEVICE` / `createOnDeviceMode()` |
| `ConnectionMode.LAN` / `createLanMode(host, port)` | `createCrossDeviceMode(CrossDeviceStrategy.LAN, host, port)` |
| `ConnectionMode.CABLE` / `createCableMode(protocol)` | `createCrossDeviceMode(CrossDeviceStrategy.CABLE, cableProtocol = protocol)` |
| *(no equivalent)* | `createCrossDeviceMode(CrossDeviceStrategy.AUTO, host, port)` — LAN **and** cable with automatic re-routing |
| `ConnectionMode.SUB_SCREEN` / `createSubScreenMode()` | *unchanged* |

> **AUTO semantics.** Classic `ConnectionMode.CABLE` + `CableProtocol.AUTO` is **unchanged** — it still only picks among cable protocols (VSP/RS232/AOA) and never switches to LAN. The new cross-transport failover (LAN <-> CABLE) applies **only** when you opt in via `CrossDeviceStrategy.AUTO`.

**If you adopt `CrossDeviceStrategy.AUTO`, note:**

- Configure a LAN `host` (and `port`) on the config even when a cable is your preferred channel — it is the fallback target when no discovered terminal can be matched by serial.
- Set the try order with `setAutoPriority(...)`; the default is `[LAN, CABLE]`.
- During a re-route the SDK reports `CONNECTING`, then `CONNECTED` or `DISCONNECTED`. It never reports a stale `CONNECTED`.
- An in-flight transaction is never interrupted by a re-route. If the link genuinely dies mid-transaction you still get the normal disconnect/failure path — follow the usual `query()`-then-decide rule before retrying.

**Reconnect timing:** wait until `TaplinkSDK.getConnectionStatus()` reports `DISCONNECTED` before calling `connect()` again — `disconnect()` returns before the underlying serial handle is fully released.

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
