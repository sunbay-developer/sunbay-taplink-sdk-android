# Tapro Sub-Screen Integration Guide

Use this guide when an integrating application runs on a **primary SUNMI POS**
and connects by USB to a **CPad Pay Sub-Screen edition with Tapro installed**.
CPad Pay serves as both the customer-facing display and the payment terminal.
The POS application controls the customer-facing content; Tapro on CPad Pay
handles payment processing and the payment UI.

`ConnectionMode.SUB_SCREEN` is available in TapLink SDK **v1.0.9+**. It is the
only mode needed for this scenario.

## What this mode provides

`ConnectionConfig.createSubScreenMode()` establishes a USB VSP connection from
the primary-screen terminal to Tapro on the secondary-screen terminal and
starts Tapro's USB sub-screen player as part of the connection.

- `onConnected()` means both the connection and Tapro sub-screen player are
  ready. Only start a transaction after this callback.
- If Tapro cannot start the sub-screen player, the SDK closes the connection
  and calls `onError()` with code `351`.
- Tapro on CPad Pay owns the payment UI during the transaction, including
  card, PIN, signature, and result screens.
- The POS application creates, updates, and restores its customer-facing
  content, such as the order list, amount, advertisements, and idle screen.
  For Android display rendering, the POS application can manage the external
  `Display` and its own `Presentation` through Android's display APIs.
- The SDK starts the USB sub-screen player during `SUB_SCREEN` connection,
  so a separate `openUsbScreenPlayer()` request is unnecessary for this step.

```text
Primary SUNMI POS                         CPad Pay Sub-Screen edition
POS app + TapLink SDK  -- USB VSP ------>  Tapro payment processing/UI
POS display content   -- USB display -->  USB sub-screen player
```

## Display ownership and payment handoff

1. **Before payment:** the POS application displays and updates its own
   customer-facing content on CPad Pay.
2. **During payment:** the POS application sends a transaction through
   TapLink SDK, and Tapro takes the foreground on CPad Pay to handle payment.
3. **After payment:** once Tapro completes its payment UI flow, including
   required card removal and receipt steps, it closes the transaction UI and
   returns to the background. The POS application maintains or restores its
   customer-facing content through its own display logic.

Use `PaymentCallback` to update the POS order and display state. Check
`isSuccess()`, `isProcessing()`, and `isFailed()` before deciding what to show;
query a processing transaction until its outcome is known. Switch to the main
thread for Android UI updates. If the external display is recreated or
reconnected, the POS application must reattach its customer-facing content.

## Prerequisites

Before integrating, confirm all of the following:

1. The primary-screen SUNMI terminal running the integrating application and
   the secondary-screen SUNMI terminal running Tapro are connected by the
   supported USB VSP connection.
2. The secondary-screen terminal is the CPad Pay Sub-Screen edition, with Tapro
   installed and configured to provide its cable/VSP service. The POS
   application manages the customer-facing display content.
3. Tapro is version **1.0.5 or later**.
4. The application uses TapLink SDK **1.0.9 or later**.
5. The application declares USB host support and completes any USB access
   authorization required by the Android version and device:

   ```xml
   <uses-feature android:name="android.hardware.usb.host" />
   ```

Do not add `host`, `port`, or `cableProtocol` settings for this mode.
`createSubScreenMode()` always uses VSP.

## Integration

### 1. Add the SDK dependency

Use the TapLink SDK version supplied for your integration:

```kotlin
dependencies {
    implementation("com.sunmi:sunbay-taplink-sdk-android:1.0.9")
}
```

### 2. Initialize once

Initialize the SDK in `Application.onCreate()`. Keep credentials out of source
control and supply them through `BuildConfig` or another secure configuration
mechanism. Disable SDK logging in production builds.

```kotlin
class MerchantApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        TaplinkSDK.init(
            this,
            TaplinkConfig.create(
                BuildConfig.TAPLINK_APP_ID,
                BuildConfig.TAPLINK_SECRET_KEY
            )
        )
    }
}
```

### 3. Connect from the primary-screen terminal and wait for Tapro

Call `connect()` from the component that owns the payment session. Callback
methods can run off the main thread; switch to the main thread before changing
Android UI.

```kotlin
private fun connectSubScreen() {
    TaplinkSDK.connect(
        ConnectionConfig.createSubScreenMode(),
        object : ConnectionListener {
            override fun onConnected(deviceId: String, taproVersion: String) {
                runOnUiThread {
                    // Tapro on the secondary-screen terminal is ready.
                    // Its sub-screen player is open. Enable payment now.
                    setPaymentReady(true)
                }
            }

            override fun onDisconnected(reason: String) {
                runOnUiThread {
                    setPaymentReady(false)
                    showConnectionError(reason)
                }
            }

            override fun onError(error: ConnectionError) {
                runOnUiThread {
                    setPaymentReady(false)
                    when (error.code) {
                        "351" -> showConnectionError(
                            "Tapro could not open the customer sub-screen. " +
                                "Check the terminal and its sub-screen."
                        )
                        else -> showConnectionError(error.message)
                    }
                }
            }
        }
    )
}
```

`deviceId` and `taproVersion` in `onConnected()` can be `"unknown"` until
Tapro performs its automatic initialization during the first transaction. They
are not required to start the payment.

### 4. Start a transaction after `onConnected()`

Use the ordinary TapLink transaction APIs. `SUB_SCREEN` only changes how Tapro
is connected and how it presents the terminal-side transaction UI; it does not
change request or result semantics.

```kotlin
private fun startSale(orderId: String, amountInCents: Long) {
    val request = SaleRequest.builder()
        .setReferenceOrderId(orderId)
        .setTransactionRequestId(UUID.randomUUID().toString().replace("-", ""))
        .setAmount(AmountInfo.of(amountInCents, "USD"))
        .setPaymentMethod(PaymentMethodInfo(PaymentCategory.CARD))
        .build()

    TaplinkSDK.getClient().sale(request, object : PaymentCallback {
        override fun onProgress(event: PaymentEvent) {
            runOnUiThread {
                showPaymentProgress(event)
            }
        }

        override fun onSuccess(result: PaymentResult) {
            runOnUiThread {
                when {
                    result.isSuccess() -> showApproved(result.transactionId)
                    result.isProcessing() -> queryPaymentStatus(
                        requireNotNull(result.transactionRequestId)
                    )
                    result.isFailed() -> showDeclined(result.message)
                }
            }
        }

        override fun onFailure(error: PaymentError) {
            runOnUiThread {
                showTechnicalFailure(error.message)
            }
        }
    })
}
```

Required imports for the example:

```kotlin
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.callback.ConnectionListener
import com.sunmi.tapro.taplink.sdk.callback.PaymentCallback
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.error.ConnectionError
import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult
import java.util.UUID
```

All monetary values use the smallest currency unit. For example,
`amountInCents = 1000L` represents USD 10.00. Generate a new globally unique
`transactionRequestId` for every new payment attempt.

`onSuccess()` indicates that Tapro returned a transaction response; it does not by
itself mean that the payment was approved. Always check `isSuccess()`,
`isProcessing()`, or `isFailed()`. `onFailure()` represents a technical or
communication failure, not a card decline.

For error `306`, query the original transaction status before taking any retry
action. For errors `307` and `308`, use a new `transactionRequestId` for the
retry.

### 5. Release the payment session

Remove listeners and disconnect when the payment-owning component is destroyed:

```kotlin
override fun onDestroy() {
    TaplinkSDK.removeConnectionListener()
    TaplinkSDK.disconnect()
    super.onDestroy()
}
```

## Troubleshooting

| Symptom | Action |
|---|---|
| `onError` code `351` | Confirm that the secondary-screen terminal supports its customer sub-screen, Tapro is version 1.0.5 or later, and the sub-screen is available. Then reconnect. |
| A connection error occurs before `351` | Check the USB cable and access authorization on the primary-screen terminal, and confirm that Tapro cable/VSP service is enabled on the secondary-screen terminal. |
| Payment starts before the sub-screen is ready | Wait for `onConnected()` before enabling the payment action. |
| Tapro transaction UI is not shown as expected | Use `ConnectionConfig.createSubScreenMode()` rather than Cable or another connection mode. Do not call `openUsbScreenPlayer()` separately. |
| Tapro remains visible after payment | Confirm that the connection uses `createSubScreenMode()`, check the SDK and Tapro versions, and complete any pending card-removal or receipt prompts. If it persists, collect the connection configuration and sanitized transaction logs for support. |
| Customer-facing content is blank or outdated after payment | Check the POS application's display lifecycle, order state, and content restoration. Reattach the POS-managed content if the external display was recreated or reconnected. |

For other transaction types, error handling, and payment-result recovery, see
the SDK's main integration guide.
