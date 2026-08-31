# Secondary Display Integration Guide

This guide covers **two distinct but related** TapLink features, both introduced in SDK
v1.0.9 and both centered on driving a customer-facing screen from your POS application:

| | **Part A: `SECONDARY_DISPLAY` connection mode** | **Part B: USB customer-facing screen (`Presentation`)** |
|---|---|---|
| What it is | A dedicated **connection mode** (`ConnectionConfig.createSecondaryDisplayMode()`) that connects over USB VSP to a **remote** TaPro terminal and automatically launches ("拉起") TaPro's screen player on that remote device as part of `connect()` | A **rendering technique** your app uses to draw its own ad/order/tip UI on a USB display attached to the **same** device your app runs on |
| Devices involved | Two devices: your business app device ↔ a separate TaPro terminal, joined by a USB cable | One device: your app and TaPro (or just your app) share the same `DisplayManager` |
| Who owns the on-screen content | TaPro (native screen player UI on the remote device) | Your app (Compose/`Presentation`) |
| Typical use case | Headless controller / subscreen device that only needs to drive a TaPro terminal's customer display and process payments | POS terminal with its own attached USB customer display that your app fully controls |
| Read this first if... | You call `ConnectionConfig.createSecondaryDisplayMode()` and need to understand how connecting "pulls up" TaPro | You need to render custom Compose/XML content (ads, tip selection) on a USB display |

**SDK version:** v1.0.9+

---

## Table of Contents

**Part A — Secondary Display Connection Mode (launching TaPro over USB VSP)**
- [A.1 Overview](#a1-overview)
- [A.2 Architecture](#a2-architecture)
- [A.3 Prerequisites](#a3-prerequisites)
- [A.4 Quick Start](#a4-quick-start)
- [A.5 Connection Flow & Timing](#a5-connection-flow--timing)
- [A.6 Error Handling](#a6-error-handling)
- [A.7 Transaction Behavior in SECONDARY_DISPLAY Mode](#a7-transaction-behavior-in-secondary_display-mode)
- [A.8 Comparison with `autoOpenUsbScreenPlayer`](#a8-comparison-with-autoopenusbscreenplayer)
- [A.9 Troubleshooting: TaPro Doesn't Launch](#a9-troubleshooting-tapro-doesnt-launch)

**Part B — USB Customer-Facing Screen (rendering your own UI with `Presentation`)**
- [Overview](#overview)
- [Architecture & Ownership Model](#architecture--ownership-model)
- [Prerequisites](#prerequisites)
- [Step 1: Launch the USB Screen Player](#step-1-launch-the-usb-screen-player)
- [Step 2: Detect the Secondary Display](#step-2-detect-the-secondary-display)
- [Step 3: Render Content with Presentation](#step-3-render-content-with-presentation)
- [Step 4: Use Compose Inside Presentation](#step-4-use-compose-inside-presentation)
- [Lifecycle Management](#lifecycle-management)
- [Common Use Cases](#common-use-cases)
- [Complete Integration Example](#complete-integration-example)
- [Troubleshooting](#troubleshooting)
- [API Reference](#api-reference)

---

# Part A — Secondary Display Connection Mode

## A.1 Overview

`ConnectionMode.SECONDARY_DISPLAY` (created via `ConnectionConfig.createSecondaryDisplayMode()`)
is a **dedicated connection mode**, not a UI-rendering helper. It is the mode to use when your
business app runs on a device whose only job is to drive a **remote** TaPro terminal — for
example a headless controller or subscreen board wired to a TaPro terminal over USB — and you
need `connect()` to guarantee that TaPro is not just reachable, but actually showing its
customer-facing screen before you start taking payments.

**What `connect()` does in this mode, in one call:**
1. Establishes a transport connection to the remote TaPro over **USB Virtual Serial Port (VSP)**.
2. Once the transport is up, the SDK automatically sends `OPEN_USB_SCREEN_PLAYER` to TaPro —
   this is exactly what "拉起 TaPro" (launching/pulling up TaPro) means in this mode: TaPro
   activates its USB screen player and is ready to present transaction UI on the customer
   display attached to *its own* device.
3. `onConnected(deviceId, taproVersion)` is delivered **only** when both steps succeed.
4. If step 2 fails or times out, the SDK disconnects automatically and delivers `onError`
   instead — a connection is never reported as "connected" with a non-functional customer
   display in this mode.

This is different from calling `TaplinkSDK.connect()` with `ConnectionMode.CABLE`/`PINPAD` and
separately calling `openUsbScreenPlayer()`: in `SECONDARY_DISPLAY` mode the screen player step is
**mandatory and part of the connection contract**, not an optional follow-up call.

---

## A.2 Architecture

```
┌────────────────────────────┐   USB cable (VSP)   ┌──────────────────────────────┐
│   Your Business App         │◄────────────────────►│        TaPro Terminal        │
│  (headless controller /     │                      │                              │
│   subscreen device)         │                      │  ┌────────────────────────┐  │
│                              │                      │  │ USB Screen Player       │  │
│  ConnectionConfig            │  1. VSP connect      │  │ (native TaPro UI:       │  │
│    .createSecondaryDisplay   │ ──────────────────►  │  │  ads / order / tip /    │  │
│    Mode()                    │                      │  │  payment prompts)       │  │
│                              │  2. OPEN_USB_SCREEN_  │  └───────────┬────────────┘  │
│  TaplinkSDK.connect(config,  │     PLAYER (auto)     │              │               │
│    listener)                 │ ──────────────────►  │              ▼               │
│                              │  3. onConnected only  │      USB customer-facing     │
│  TaplinkSDK.getClient()      │     after (1)+(2) OK  │      display (attached to    │
│    .sale(...) / refund(...)  │ ◄──────────────────  │      the TaPro device)       │
└────────────────────────────┘                      └──────────────────────────────┘
```

Subsequent transactions (`sale`, `refund`, `void`, `auth`, ...) are sent over the same VSP link
using the full TapLink transaction API — no separate API is needed for payments once connected.

---

## A.3 Prerequisites

1. **Hardware**: two devices joined by a USB cable — your business app device, and a TaPro
   terminal with a USB customer-facing display attached to it (or an on-board/subscreen display
   wired to TaPro over the same on-board UART/VSP transport).
2. **Remote TaPro version**: `1.0.5` or later (`MIN_TAPRO_VERSION_FOR_USB_SCREEN_PLAYER`). Older
   TaPro versions do not recognize `OPEN_USB_SCREEN_PLAYER` and the connection will fail with
   error `351`.
3. **SDK Version**: `com.sunmi:sunbay-taplink-sdk-android:1.0.9` or later (for the
   `SECONDARY_DISPLAY` enum value and `createSecondaryDisplayMode()` factory).
4. **TaPro service mode**: TaPro must be running with VSP/Cable service mode enabled so it can
   accept the incoming VSP transport.
5. **USB host permission**: same as other Cable-based modes — declare
   `<uses-feature android:name="android.hardware.usb.host" />` in `AndroidManifest.xml`, and
   handle the Android 12+ runtime USB permission flow.
6. Do **not** set `host`/`port`/`cableProtocol` on the config — `createSecondaryDisplayMode()`
   always resolves to the VSP protocol regardless of any other fields.

---

## A.4 Quick Start

```kotlin
// 1) Init once (Application.onCreate)
TaplinkSDK.init(context, TaplinkConfig.create(BuildConfig.TAPLINK_APP_ID, BuildConfig.TAPLINK_SECRET_KEY))

// 2) Connect using the dedicated Secondary Display mode
val config = ConnectionConfig.createSecondaryDisplayMode()

TaplinkSDK.connect(config, object : ConnectionListener {
    override fun onConnected(deviceId: String, taproVersion: String) {
        // VSP link is up AND TaPro's screen player is confirmed open.
        // TaPro is now "拉起" (launched) and ready — safe to start taking payments.
        runOnUiThread { showReadyState() }
    }

    override fun onDisconnected(reason: String) {
        runOnUiThread { showDisconnectedState(reason) }
    }

    override fun onError(error: ConnectionError) {
        runOnUiThread { handleSecondaryDisplayConnectError(error) }
    }
})

// 3) Once onConnected fires, the full transaction API is available as usual
val client = TaplinkSDK.getClient()
client.sale(saleRequest, paymentCallback)

// 4) Lifecycle cleanup (e.g. onDestroy)
TaplinkSDK.removeConnectionListener()
TaplinkSDK.disconnect()
```

---

## A.5 Connection Flow & Timing

| Step | What happens | Timing |
|------|---------------|--------|
| 1 | VSP transport connects to the remote TaPro | Governed by `ConnectionConfig.connectionTimeout` (default 60s) |
| 2 | SDK sends `OPEN_USB_SCREEN_PLAYER` to TaPro | Immediately after step 1 succeeds |
| 3a | TaPro confirms the screen player opened | `onConnected` delivered |
| 3b | TaPro rejects, or does not answer within **3 seconds** | `disconnect()` is called automatically, then `onError(code = "351")` is delivered |

Only one of 3a/3b ever fires — `onConnected`/`onError` for the screen-player step is delivered
exactly once, the same guarantee the SDK gives for `autoOpenUsbScreenPlayer` (see A.8).

---

## A.6 Error Handling

| Code | Meaning | Suggested action |
|------|---------|-------------------|
| `351` | VSP transport connected, but TaPro failed to open the USB screen player (or didn't respond within 3s) | Confirm the remote TaPro is `1.0.5`+, confirm a customer-facing display is attached to the TaPro device, then retry `connect()` |
| *(any connection error)* | The underlying VSP transport itself failed (cable unplugged, USB permission denied, TaPro not in service mode, etc.) | Handle like any other Cable/VSP connection failure — check the cable and USB permission, confirm TaPro's service mode |

```kotlin
override fun onError(error: ConnectionError) {
    when (error.code) {
        "351" -> {
            // Transport was fine, but TaPro's screen player didn't come up.
            showError("Customer display on TaPro is unavailable: ${error.message}")
        }
        else -> {
            // VSP transport itself failed — treat as a normal connection failure.
            showError("Could not reach TaPro: ${error.message}")
        }
    }
}
```

> `351` is only ever raised for `SECONDARY_DISPLAY` mode, where opening the screen player is a
> hard requirement. Compare with `350`, which is raised by `openUsbScreenPlayer()`/
> `autoOpenUsbScreenPlayer` when the remote TaPro version is too old to support the action at all
> (see A.8) — `350` does not fail the connection in that softer path, but it **does** fail the
> connection here.

---

## A.7 Transaction Behavior in SECONDARY_DISPLAY Mode

Every transaction sent while connected in `SECONDARY_DISPLAY` mode is tagged internally so TaPro
routes it straight into the payment engine and shows the result **on the secondary display**,
instead of opening TaPro's normal full-screen TapLink page. You don't need to configure this —
it's automatic for this mode. All other transaction semantics are unchanged: check
`result.isSuccess()`/`isProcessing()`/`isFailed()`, use `query()` before retrying on error `306`,
and use a new `transactionRequestId` for `307`/`308` retries.

---

## A.8 Comparison with `autoOpenUsbScreenPlayer`

| | `SECONDARY_DISPLAY` mode | `TaplinkConfig.setAutoOpenUsbScreenPlayer(true)` (Part B) |
|---|---|---|
| Connection type | USB VSP only, cross-device | Any connection mode (`ON_DEVICE`/`APP_TO_APP`, `LAN`, `CABLE`, `PINPAD`) |
| Screen player failure | **Fatal** — connection fails with `onError(351)` | **Non-blocking** — `onConnected` still fires after a 3s timeout |
| Who renders content | TaPro itself (native screen player UI) | Your app (`Presentation`), see Part B |
| Typical use case | Dedicated device driving a remote TaPro's customer display | Optional customer screen on the same device as TaPro |

---

## A.9 Troubleshooting: TaPro Doesn't Launch

| Symptom | Cause | Fix |
|---------|-------|-----|
| `onError(code="351")` immediately | Remote TaPro version `< 1.0.5` doesn't support `OPEN_USB_SCREEN_PLAYER` | Upgrade TaPro; verify `taproVersion` reported in earlier successful connects, or check TaPro's about screen |
| `onError(code="351")` after ~3s | TaPro received the request but didn't confirm (no customer display attached, or TaPro busy) | Verify a USB customer-facing display is physically attached to the TaPro device; retry once TaPro is idle |
| `onConnected` never fires, no `onError` either | VSP transport itself never came up (cable/permission issue) | This indicates a transport-level failure, not a `351` — check the USB cable, `android.hardware.usb.host` permission, and that TaPro is in VSP/Cable service mode |
| Connects fine with `CABLE`/`PINPAD` mode but fails with `SECONDARY_DISPLAY` | Screen player step failing while the transport is fine — narrow it down first | Try `ConnectionConfig.createCableMode()` + manual `TaplinkSDK.openUsbScreenPlayer()` (Part B, Step 1 Option B) against the same remote TaPro to isolate whether it's a transport or screen-player problem |
| Works once, then fails after reconnect | TaPro's screen player was dismissed/closed after the first session | Each new `connect()` in this mode re-sends `OPEN_USB_SCREEN_PLAYER`; this is expected and requires no special handling |

---

# Part B — USB Customer-Facing Screen (Rendering with `Presentation`)

## Overview

The secondary display feature enables POS applications to show customer-facing content
(ad carousels, order lists, tip selection) on a USB-attached external screen while
processing payments through TapLink SDK.

**Key points:**
- Secondary display rendering is **your app's responsibility**, not a TapLink protocol feature
- The SDK provides `openUsbScreenPlayer()` to request Tapro to launch the display driver
- Your app uses standard Android `Presentation` API to render on the detected display
- Works on SUNMI terminals with USB customer-facing displays (e.g., T3 series)
- This section assumes your app and TaPro share the **same** device/`DisplayManager`. If your
  app runs on a separate device connected over USB VSP, use Part A instead.

---

## Architecture & Ownership Model

```
┌──────────────────────────────────────────────────┐
│            Your POS Application                   │
│                                                   │
│  ┌─────────────────────────────────────────────┐ │
│  │ SecondaryDisplayController                   │ │
│  │  • Detect display (DisplayManager)           │ │
│  │  • Launch USB screen player (local Intent)   │ │
│  │  • Manage Presentation lifecycle             │ │
│  │  • Render Compose UI on secondary screen     │ │
│  └─────────────────────────────────────────────┘ │
│                                                   │
│  ┌─────────────────────────────────────────────┐ │
│  │ TapLink SDK                                  │ │
│  │  • openUsbScreenPlayer(callback)             │ │
│  │  • autoOpenUsbScreenPlayer config             │ │
│  │  • Payment transactions                      │ │
│  └─────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────┘
           │                              │
           │ Presentation API             │ TapLink Protocol
           ▼                              ▼
    ┌──────────────┐            ┌──────────────────┐
    │ USB Display  │            │ Tapro Terminal    │
    │ (Hardware)   │            │ (Payment Host)    │
    └──────────────┘            └──────────────────┘
```

| Concern | Owner |
|---------|-------|
| Detecting the secondary `Display` | Your app (Android `DisplayManager`) |
| Rendering content on it | Your app (Android `Presentation` + Compose) |
| Launching the USB screen player | Your app (local `Intent`) or SDK (`openUsbScreenPlayer`) |
| Payment transactions | TapLink SDK ↔ Tapro |

> **Critical constraint:** Android `Presentation` can only render to a `Display`
> registered in the **calling process's own** `DisplayManager`. The USB customer
> display must be physically attached to the device running your app.

---

## Prerequisites

1. **Hardware**: SUNMI terminal with USB customer-facing display
2. **SDK Version**: `com.sunmi:sunbay-taplink-sdk-android:1.0.9` or later
3. **Min SDK**: Android API 24 (Android 7.0)
4. **SUNMI USB Screen Player**: Pre-installed system app on supported terminals

### Version Support

| API | Since Version | Notes |
|-----|---------------|-------|
| `TaplinkSDK.openUsbScreenPlayer(callback)` | v1.0.9 | Manual control. Existed earlier but was broken (T06 error) until v1.0.9 fixed it. |
| `TaplinkConfig.setAutoOpenUsbScreenPlayer(true)` | v1.0.9 | Automatic launch on connect (before `onConnected`). |

---

## Step 1: Launch the USB Screen Player

The USB screen player is a SUNMI system app that enables the secondary display hardware.
You have two options to launch it:

### Option A: Automatic (Recommended for App-to-App mode)

Configure the SDK to automatically open the screen player when connecting to Tapro:

```kotlin
val config = TaplinkConfig.create(appId, secretKey)
    .setAutoOpenUsbScreenPlayer(true)  // Opens player before onConnected

TaplinkSDK.init(context, config)
```

When enabled:
- The player is launched right after the transport connects, **before** `onConnected` fires
- If Tapro doesn't respond within 3 seconds, `onConnected` is delivered anyway
- If the launch fails, `onConnected` is still delivered (non-blocking)

### Option B: Manual

Call `openUsbScreenPlayer()` at any point after connecting:

```kotlin
TaplinkSDK.openUsbScreenPlayer(object : PaymentCallback {
    override fun onSuccess(result: PaymentResult) {
        // Tapro launched the USB screen player successfully.
        // NOTE: This does NOT mean your Display is ready — see Step 2.
    }

    override fun onFailure(error: PaymentError) {
        // Communication error (E303 = not connected, etc.)
        Log.w(TAG, "Failed to open USB screen player: ${error.message}")
    }

    override fun onProgress(event: PaymentEvent) = Unit
})
```

### Option C: Local Intent (No SDK required)

If your app runs on the same device as the display, launch the player directly:

```kotlin
private fun launchLocalUsbScreenPlayer(context: Context) {
    val intent = Intent("sunmi.intent.action.SunmiUsbScreenPlayer")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
```

> **⚠️ Important:** `openUsbScreenPlayer()` callback does NOT mean the display is ready.
> It only confirms Tapro received the command. Always use `DisplayManager` to detect
> when the display becomes available (Step 2).

---

## Step 2: Detect the Secondary Display

After launching the screen player, the display registers **asynchronously** in
`DisplayManager`. You must wait for it:

```kotlin
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Find an available non-default secondary display.
 */
private fun findSecondaryDisplay(context: Context): Display? {
    val manager = context.getSystemService(DisplayManager::class.java) ?: return null
    return manager.displays.firstOrNull { display ->
        display.displayId != Display.DEFAULT_DISPLAY
                && display.state == Display.STATE_ON
    }
}

/**
 * Wait for the secondary display to become available.
 *
 * @param context Application context
 * @param timeoutMs Maximum wait time (recommended: 5000ms)
 * @return The secondary Display, or null if timeout
 */
private suspend fun awaitSecondaryDisplay(
    context: Context,
    timeoutMs: Long = 5_000L
): Display? {
    // Fast path: display already available
    findSecondaryDisplay(context)?.let { return it }

    val manager = context.getSystemService(DisplayManager::class.java) ?: return null

    return withTimeoutOrNull(timeoutMs.milliseconds) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : DisplayManager.DisplayListener {
                private fun resumeIfReady() {
                    if (!continuation.isActive) return
                    val display = findSecondaryDisplay(context) ?: return
                    manager.unregisterDisplayListener(this)
                    continuation.resume(display)
                }

                override fun onDisplayAdded(displayId: Int) = resumeIfReady()
                override fun onDisplayChanged(displayId: Int) = resumeIfReady()
                override fun onDisplayRemoved(displayId: Int) = Unit
            }

            manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
            continuation.invokeOnCancellation {
                runCatching { manager.unregisterDisplayListener(listener) }
            }

            // Race guard: display may have appeared between registration and query
            findSecondaryDisplay(context)?.takeIf { continuation.isActive }?.let { display ->
                runCatching { manager.unregisterDisplayListener(listener) }
                continuation.resume(display)
            }
        }
    }
}
```

> **⚡ The #1 integration bug:** Calling `getDisplays()` immediately after launching the
> player. The display registers asynchronously — always use `DisplayListener` with a timeout.

---

## Step 3: Render Content with Presentation

Use Android's `Presentation` class to render UI on the secondary display:

```kotlin
import android.app.Presentation
import android.os.Bundle
import android.view.Display
import android.view.WindowManager

class MySecondaryPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set your content view here (XML or Compose — see Step 4)
    }
}

// Show the presentation
suspend fun showOnSecondaryDisplay(context: Context) {
    val display = awaitSecondaryDisplay(context) ?: run {
        Log.d(TAG, "No secondary display available")
        return
    }

    val presentation = MySecondaryPresentation(context, display)
    try {
        presentation.show()
    } catch (e: WindowManager.InvalidDisplayException) {
        // Display disconnected between detection and show
        Log.e(TAG, "Display became invalid", e)
    }
}
```

---

## Step 4: Use Compose Inside Presentation

`Presentation` is **not** an Activity, so Compose's `ViewTree*Owner` are not
automatically injected. You need a helper class:

### PresentationComposeViewTreeOwner

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Provides lifecycle, ViewModelStore, and SavedStateRegistry for Compose
 * running inside a Presentation (which is not an Activity).
 *
 * Without this, ComposeView throws "ViewTreeLifecycleOwner not found".
 */
class PresentationComposeViewTreeOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreInternal = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private var created = false
    private var started = false
    private var destroyed = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInternal
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    /** Call in Presentation.onCreate() before setting ComposeView content. */
    fun onCreate() {
        if (created || destroyed) return
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        created = true
    }

    /** Call in Presentation.onStart() to enter visible state. */
    fun onStart() {
        if (!created || destroyed || started) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        started = true
    }

    /** Call in Presentation.onStop() to leave visible state. */
    fun onStop() {
        if (!created || destroyed || !started) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        started = false
    }

    /** Call in Presentation.onDetachedFromWindow() for final cleanup. */
    fun onDestroy() {
        if (destroyed) return
        if (started) onStop()
        if (created) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        viewModelStoreInternal.clear()
        destroyed = true
    }
}
```

### Using it in your Presentation

```kotlin
class MyComposePresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private val composeOwner = PresentationComposeViewTreeOwner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeOwner.onCreate()

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeViewModelStoreOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setContent {
                MaterialTheme {
                    // Your Compose UI here
                    Text("Hello from secondary display!")
                }
            }
        }
        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        composeOwner.onStart()
    }

    override fun onStop() {
        composeOwner.onStop()
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        composeOwner.onDestroy()
        super.onDetachedFromWindow()
    }
}
```

---

## Lifecycle Management

### Rules

| Event | Secondary Display Action |
|-------|--------------------------|
| App launch / SDK connected | Launch USB screen player → show idle content (ads) |
| Idle state | Show ad carousel (optionally with order summary) |
| Order items change | **Update state only** — do not rebuild Presentation |
| Payment initiated | Dismiss secondary content (Tapro takes over the display) |
| Transaction complete | Re-launch player → restore idle content |
| Screen/Activity destroyed | Dismiss Presentation to prevent window leaks |
| Settings toggle off | Dismiss all secondary content |

### Best Practices

1. **Update, don't rebuild:** When order content changes, push data into a Compose
   `MutableState` held by the running Presentation. Rebuilding causes ad carousel
   to restart and visible flicker.

```kotlin
class AdCarouselPresentation(context: Context, display: Display) : Presentation(context, display) {
    // Compose observes this state — changes trigger recomposition, not recreation
    private val orderState = mutableStateOf<OrderState?>(null)

    fun updateOrder(order: OrderState?) {
        orderState.value = order
    }
}
```

2. **Dismiss before transaction:** When a payment starts, dismiss your Presentation.
   Tapro may take over the display for PIN entry or card prompts.

3. **Guard against display loss:** Always catch `WindowManager.InvalidDisplayException`
   when calling `show()` — the display may disconnect between detection and rendering.

4. **Add cooldown for player launch:** Avoid repeatedly launching the USB player if
   the display is unavailable. Use a 10-second cooldown:

```kotlin
private var lastPlayerLaunchAt = 0L
private val PLAYER_LAUNCH_COOLDOWN_MS = 10_000L

private fun launchPlayerWithCooldown(context: Context) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastPlayerLaunchAt < PLAYER_LAUNCH_COOLDOWN_MS) return
    lastPlayerLaunchAt = now
    launchLocalUsbScreenPlayer(context)
}
```

5. **Dispose on lifecycle end:** Use `DisposableEffect` or `onDestroy` to ensure
   cleanup:

```kotlin
DisposableEffect(Unit) {
    onDispose { secondaryController.dismiss() }
}
```

---

## Common Use Cases

### Ad Carousel (Idle Mode)

Display rotating advertisements when the POS is idle:

```kotlin
@Composable
fun AdCarouselContent(images: List<Int>) {
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(images) {
        while (true) {
            delay(5000L)  // 5-second rotation
            currentIndex = (currentIndex + 1) % images.size
        }
    }

    Image(
        painter = painterResource(id = images[currentIndex]),
        contentDescription = "Advertisement",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
}
```

### Ad Carousel + Order List (Split Layout)

When the customer has items in their order, show a split view:

```kotlin
@Composable
fun SplitContent(images: List<Int>, order: OrderState?) {
    if (order != null && order.hasItems()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left 30%: order list
            Box(modifier = Modifier.fillMaxHeight().weight(0.3f)) {
                OrderListContent(order)
            }
            // Right 70%: ad carousel
            Box(modifier = Modifier.fillMaxHeight().weight(0.7f)) {
                AdCarouselContent(images)
            }
        }
    } else {
        // Full-screen ad carousel
        AdCarouselContent(images)
    }
}
```

### Customer Tip Selection

Display tip options on the customer screen before starting payment:

```kotlin
@Composable
fun TipSelectionContent(
    subtotal: BigDecimal,
    currency: String,
    onTipSelected: (BigDecimal) -> Unit,
    onCancelled: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Select Tip", style = MaterialTheme.typography.headlineMedium)
        Text("Subtotal: $currency ${subtotal.setScale(2)}")

        // Tip options
        listOf("No Tip" to BigDecimal.ZERO, "10%" to BigDecimal("0.10"),
               "15%" to BigDecimal("0.15"), "20%" to BigDecimal("0.20")
        ).forEach { (label, rate) ->
            Button(
                onClick = {
                    val tip = subtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                    onTipSelected(tip)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(label) }
        }

        OutlinedButton(onClick = onCancelled, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
```

**Tip selection flow:**
1. User taps Pay on cashier screen
2. App shows `TipSelectionContent` on secondary display
3. If `awaitSecondaryDisplay()` returns null → fall back to primary screen tip flow
4. Customer selects tip → `onTipSelected(tipAmount)` fires
5. App includes `tipAmount` in the payment request and starts the transaction

---

## Complete Integration Example

A full controller class that manages the secondary display lifecycle:

```kotlin
import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class SecondaryDisplayController(private val context: Context) {

    companion object {
        private const val TAG = "SecondaryDisplayCtrl"
        private const val ACTION_USB_SCREEN_PLAYER = "sunmi.intent.action.SunmiUsbScreenPlayer"
        private const val PLAYER_LAUNCH_COOLDOWN_MS = 10_000L
        private const val DISPLAY_READY_TIMEOUT_MS = 5_000L
    }

    private var currentPresentation: Presentation? = null
    private var lastPlayerLaunchAt = 0L

    // ─── Public API ───────────────────────────────────────────────

    /**
     * Show ad carousel on the secondary display.
     * @return true if shown, false if no secondary display available
     */
    suspend fun showAdCarousel(images: List<Int>, order: OrderState? = null): Boolean {
        val display = awaitSecondaryDisplayReady() ?: return false
        dismiss()

        val presentation = AdCarouselPresentation(context, display, images, order)
        return showSafely(presentation)
    }

    /**
     * Show tip selection on the secondary display.
     * @return true if shown, false if no secondary display available
     */
    suspend fun showTipSelection(
        subtotal: BigDecimal,
        currency: String,
        onTipSelected: (BigDecimal) -> Unit,
        onCancelled: () -> Unit
    ): Boolean {
        val display = awaitSecondaryDisplayReady() ?: return false
        dismiss()

        val presentation = TipSelectionPresentation(
            context, display, subtotal, currency, onTipSelected, onCancelled
        )
        return showSafely(presentation)
    }

    /**
     * Safely dismiss the current presentation.
     */
    fun dismiss() {
        currentPresentation?.dismiss()
        currentPresentation = null
    }

    // ─── Internal ─────────────────────────────────────────────────

    private suspend fun awaitSecondaryDisplayReady(): Display? {
        findSecondaryDisplay()?.let { return it }
        launchPlayerWithCooldown()
        return awaitSecondaryDisplayAdded(DISPLAY_READY_TIMEOUT_MS)
    }

    private fun launchPlayerWithCooldown() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayerLaunchAt < PLAYER_LAUNCH_COOLDOWN_MS) return
        lastPlayerLaunchAt = now

        runCatching {
            val intent = Intent(ACTION_USB_SCREEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        }
    }

    private suspend fun awaitSecondaryDisplayAdded(timeoutMs: Long): Display? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        return withTimeoutOrNull(timeoutMs.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : DisplayManager.DisplayListener {
                    private fun resumeIfReady() {
                        if (!continuation.isActive) return
                        val display = findSecondaryDisplay() ?: return
                        manager.unregisterDisplayListener(this)
                        continuation.resume(display)
                    }
                    override fun onDisplayAdded(displayId: Int) = resumeIfReady()
                    override fun onDisplayChanged(displayId: Int) = resumeIfReady()
                    override fun onDisplayRemoved(displayId: Int) = Unit
                }
                manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
                continuation.invokeOnCancellation {
                    runCatching { manager.unregisterDisplayListener(listener) }
                }
                // Race guard
                findSecondaryDisplay()?.takeIf { continuation.isActive }?.let { display ->
                    runCatching { manager.unregisterDisplayListener(listener) }
                    continuation.resume(display)
                }
            }
        }
    }

    private fun findSecondaryDisplay(): Display? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        return manager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY
                    && display.state == Display.STATE_ON
        }
    }

    private fun showSafely(presentation: Presentation): Boolean {
        return try {
            presentation.show()
            currentPresentation = presentation
            true
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.e(TAG, "Display became invalid", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot show presentation", e)
            false
        }
    }
}
```

### Integrating with Payment Flow

```kotlin
class PaymentViewModel : ViewModel() {

    private val secondaryController: SecondaryDisplayController = // inject or create

    fun onPayClicked(orderTotal: BigDecimal) {
        viewModelScope.launch {
            // Try showing tip selection on secondary display
            val shown = secondaryController.showTipSelection(
                subtotal = orderTotal,
                currency = "USD",
                onTipSelected = { tipAmount -> startPayment(orderTotal, tipAmount) },
                onCancelled = { /* User cancelled, stay on order screen */ }
            )

            if (!shown) {
                // No secondary display — use primary screen tip flow or skip tips
                startPayment(orderTotal, tipAmount = BigDecimal.ZERO)
            }
        }
    }

    private fun startPayment(amount: BigDecimal, tipAmount: BigDecimal) {
        secondaryController.dismiss()  // Tapro takes over the display

        val request = PaymentRequest.sale(
            transactionRequestId = UUID.randomUUID().toString(),
            amount = amount.toPlainString(),       // In smallest currency unit (cents)
            tipAmount = tipAmount.toPlainString()   // In smallest currency unit (cents)
        )
        TaplinkSDK.getClient().sale(request, paymentCallback)
    }

    override fun onCleared() {
        super.onCleared()
        secondaryController.dismiss()
    }
}
```

### SDK Configuration for Best Experience

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = TaplinkConfig.create(appId, secretKey)
            .setAutoOpenUsbScreenPlayer(true)  // Player ready before onConnected

        TaplinkSDK.init(this, config)
    }
}
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `findSecondaryDisplay()` always returns `null` | Queried before the player registered the display | Wait via `DisplayListener` with timeout (see Step 2) |
| Display never appears even after waiting | Display is attached to the Tapro device, not to this device | `Presentation` cannot be used across devices; display must be on *your* device |
| `sunmi.intent.action.SunmiUsbScreenPlayer` not resolved | USB screen player app not installed | Skip silently; treat the secondary display as unavailable |
| `ViewTreeLifecycleOwner not found` crash | Compose used inside `Presentation` without owners | Set the three `ViewTree*Owner`s manually (see Step 4) |
| Ad carousel restarts on every order change | Presentation rebuilt instead of updated | Push updates into a `MutableState`, not a new Presentation |
| `WindowManager.InvalidDisplayException` | Display disconnected between detection and `show()` | Catch the exception and fall back gracefully |
| `openUsbScreenPlayer()` returns `onFailure(E303)` | Called before SDK is connected | Ensure `onConnected` has fired, or use `autoOpenUsbScreenPlayer` config |
| Display shows after several seconds | Normal USB player startup latency | Use `awaitSecondaryDisplay()` with 5s timeout |

---

## API Reference

### SDK APIs

| API | Description |
|-----|-------------|
| `TaplinkSDK.openUsbScreenPlayer(callback: PaymentCallback)` | Request Tapro to launch the USB screen player app. Only relevant in App-to-App mode where Tapro runs on the same device. |
| `TaplinkConfig.setAutoOpenUsbScreenPlayer(enabled: Boolean)` | Automatically launch the player on connect, before `onConnected` fires. 3-second timeout, non-blocking. Default: `false`. |
| `ConnectionConfig.createSecondaryDisplayMode()` | Cross-device connection mode over USB VSP that makes opening the screen player mandatory for a successful `connect()`. See [Part A](#part-a--secondary-display-connection-mode) instead of this section if you need this. |

### Callback Semantics for `openUsbScreenPlayer()`

| Callback | Meaning |
|----------|---------|
| `onSuccess` | Tapro successfully launched the screen player |
| `onFailure(E303)` | Not connected to Tapro |
| `onFailure(E304)` | Failed to send command |
| `onProgress` | Not used for this API |

### Android Platform APIs Used

| API | Usage |
|-----|-------|
| `DisplayManager.getDisplays()` | Find available displays |
| `DisplayManager.DisplayListener` | Listen for display connect/disconnect |
| `android.app.Presentation` | Render UI on external display |
| `Display.DEFAULT_DISPLAY` | Identify the primary display (exclude it) |
| `Display.STATE_ON` | Check display is powered on |

---

## Reference Implementation

The complete reference implementation is available in the TapLink Demo app:

- **Controller**: `taplinkdemo/app-compose/.../ui/secondary/SecondaryTipSelectionPresentation.kt`
- **Business logic**: `taplinkdemo/app-compose/.../ui/screens/main/MainScreen.kt`
- **Settings**: `taplinkdemo/app-compose/.../ui/screens/settings/SettingsScreen.kt`
- **SDK bridge**: `taplinkdemo/lib_service/.../TaplinkPaymentService.kt`
