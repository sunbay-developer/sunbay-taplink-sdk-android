# Secondary Display (USB Customer-Facing Screen) Integration Guide

How to drive a USB customer-facing secondary display from your POS application — showing
advertisements, order summaries, and tip selection to the customer while the cashier
operates the primary screen.

**SDK version:** v1.0.9+

---

## Table of Contents

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

## Overview

The secondary display feature enables POS applications to show customer-facing content
(ad carousels, order lists, tip selection) on a USB-attached external screen while
processing payments through TapLink SDK.

**Key points:**
- Secondary display rendering is **your app's responsibility**, not a TapLink protocol feature
- The SDK provides `openUsbScreenPlayer()` to request Tapro to launch the display driver
- Your app uses standard Android `Presentation` API to render on the detected display
- Works on SUNMI terminals with USB customer-facing displays (e.g., T3 series)

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
│  │  • autoOpenUsbScreenPlayer config            │ │
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
3. **Min SDK**: Android API 25 (Android 7.1)
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
