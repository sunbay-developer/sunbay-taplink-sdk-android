package com.sunmi.tapro.taplink.sdk.protocol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * USB attach monitor.
 *
 * Listens for USB device / accessory attach broadcasts and invokes [onAttached] on the main thread
 * whenever a cable is physically plugged in. Used by the Cross-Device AUTO hot-switch feature so a
 * running session (e.g. over LAN) can take over the preferred cable transport the moment it is
 * inserted.
 *
 * Lifecycle:
 * - [start] registers a single [BroadcastReceiver] for [UsbManager.ACTION_USB_DEVICE_ATTACHED] and
 *   [UsbManager.ACTION_USB_ACCESSORY_ATTACHED]. Idempotent — a second [start] is a no-op.
 * - [stop] unregisters the receiver. Idempotent and safe to call when not started.
 *
 * The monitor holds no connection state; it only forwards attach events. The decision of whether to
 * actually switch transports is made by the caller (ConnectionManager).
 *
 * @param context any Context; the application context is used for registration to avoid leaks.
 * @param onAttached invoked (on the thread the broadcast is delivered on, typically main) when a USB
 *   device/accessory attach broadcast is received.
 */
internal class UsbAttachMonitor(
    context: Context,
    private val onAttached: () -> Unit
) {

    private val appContext: Context = context.applicationContext ?: context

    private var receiver: BroadcastReceiver? = null

    private val innerReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                action == UsbManager.ACTION_USB_ACCESSORY_ATTACHED
            ) {
                LogUtil.d(TAG, "USB attach broadcast received: $action")
                try {
                    onAttached()
                } catch (e: Exception) {
                    LogUtil.e(TAG, "onAttached handler threw: ${e.message}")
                }
            }
        }
    }

    /**
     * Registers the USB attach broadcast receiver. No-op if already started.
     */
    fun start() {
        if (receiver != null) {
            LogUtil.d(TAG, "UsbAttachMonitor already started, ignoring")
            return
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        }
        try {
            // These are system-protected broadcasts; RECEIVER_NOT_EXPORTED keeps the receiver from
            // being reachable by other apps on Android 13+ while still receiving system delivery.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(innerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(innerReceiver, filter)
            }
            receiver = innerReceiver
            LogUtil.d(TAG, "UsbAttachMonitor started")
        } catch (e: Exception) {
            LogUtil.e(TAG, "Failed to register USB attach receiver: ${e.message}")
        }
    }

    /**
     * Unregisters the USB attach broadcast receiver. Idempotent.
     */
    fun stop() {
        val current = receiver ?: return
        try {
            appContext.unregisterReceiver(current)
            LogUtil.d(TAG, "UsbAttachMonitor stopped")
        } catch (e: Exception) {
            LogUtil.w(TAG, "Failed to unregister USB attach receiver: ${e.message}")
        } finally {
            receiver = null
        }
    }

    companion object {
        private const val TAG = "UsbAttachMonitor"
    }
}
