package com.sunmi.tapro.taplink.communication.cable.aoa.host

import android.app.Activity
import android.os.Bundle
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Invisible Activity that claims the AOA USB device dispatched by the system, which
 * implicitly grants USB permission without showing a prompt.
 *
 * ## Why this has to be an Activity
 * Like accessory events, `ACTION_USB_DEVICE_ATTACHED` is dispatched by AOSP's
 * `UsbProfileGroupSettingsManager` as an **Activity Intent** to components that declare a
 * `<usb-device>` filter in the manifest. A runtime-registered `BroadcastReceiver` never
 * receives it.
 *
 * ## Why no permission prompt appears
 * When the system dispatches a device to a matching Activity, it grants USB permission for
 * that device to the owning package at the same time (see
 * `UsbUserSettingsManager.grantDevicePermission`). So as long as
 * `res/xml/taplink_usb_device_filter.xml` is declared, `usbManager.hasPermission(device)`
 * already returns true by the time [CableAoaHostKernel] enumerates the AOA device after the
 * peer switched into accessory mode — no permission dialog is needed.
 *
 * Note: the peer's **pre-switch** device is not covered by the filter (its VID/PID varies per
 * model and cannot be declared up front), so the very first AOA switch command still triggers
 * one system permission dialog. It stops appearing once the user checks "always allow".
 *
 * This Activity does nothing but trigger the grant above: it calls [finish] immediately in
 * `onCreate` and never interferes with the integrator's UI.
 */
class TaplinkUsbDeviceAttachedActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogUtil.i(TAG, "AOA USB device claimed — permission granted implicitly by the system")
        finish()
    }

    private companion object {
        const val TAG = "TaplinkUsbDeviceAttached"
    }
}
