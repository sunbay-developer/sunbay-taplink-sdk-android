package com.sunmi.tapro.taplink.sdk.protocol

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.sunmi.tapro.taplink.sdk.enums.CableProtocol
import com.sunmi.tapro.taplink.communication.util.LogUtil

/**
 * Inserted-cable classifier.
 *
 * Synchronously inspects the USB subsystem to determine which physical cable is currently plugged
 * in, so cable AUTO mode can try the matching protocol first (plug-and-play) before falling back to
 * the remaining protocols.
 *
 * Signals:
 * - A USB-to-serial bridge chip (CH340 / PL2303 / FTDI / Silicon Labs) enumerates as a concrete USB
 *   device with a known vendor id -> [CableProtocol.RS232]. This is the strongest, most reliable
 *   signal because these chips expose `/dev/ttyUSB*` and must go through the serial kernel.
 * - A USB accessory present in `accessoryList` -> [CableProtocol.USB_AOA].
 * - No external USB device/accessory -> `null`, meaning no external cable is detected. VSP travels
 *   over the on-board UART (SUNMI `UartManager` `CHANNEL_AP_DEV`) and produces no USB signal, so the
 *   "nothing detected" case naturally maps to trying VSP first as the on-board fallback.
 *
 * Unlike [CableProtocolDetector] (which is AOA-first and always defaults to AOA when nothing is
 * found), this classifier only reports a protocol when there is an unambiguous physical signal, and
 * returns `null` otherwise. That keeps AUTO ordering honest: a real cable always wins, and the
 * on-board VSP fallback is used only when no external cable is present.
 */
object InsertedCableClassifier {

    private const val TAG = "InsertedCableClassifier"

    /**
     * Vendor ids of common USB-to-serial bridge chips. A device with one of these vendor ids is a
     * USB-serial cable (RS232), never the on-board VSP UART. Kept in sync with the Host-side
     * `CableAutoDetector.USB_SERIAL_BRIDGE_VENDOR_IDS`.
     *
     * - 0x1A86: QinHeng Electronics (CH340/CH341)
     * - 0x067B: Prolific (PL2303)
     * - 0x0403: FTDI
     * - 0x10C4: Silicon Labs (CP210x)
     */
    private val USB_SERIAL_BRIDGE_VENDOR_IDS = setOf(0x1A86, 0x067B, 0x0403, 0x10C4)

    /**
     * Classify the physically inserted cable via USB enumeration.
     *
     * @return [CableProtocol.RS232] when a USB-serial bridge is present, [CableProtocol.USB_AOA]
     *   when a USB accessory is present, or `null` when no external USB cable is detected (VSP
     *   on-board UART fallback applies).
     */
    fun classify(context: Context): CableProtocol? {
        val usbManager = runCatching {
            context.getSystemService(Context.USB_SERVICE) as? UsbManager
        }.getOrNull() ?: return null

        // 1) USB-to-serial bridge chip -> RS232 (strongest, concrete vendor-id signal).
        //    A Sunmi VSP virtual serial device may enumerate under a bridge vendor id (e.g. Prolific
        //    0x067B) while actually being a CDC-ACM VSP endpoint; such devices must NOT be treated as
        //    RS232, so exclude anything exposing the VSP "CDC ACM Data" interface.
        val serialBridge = runCatching {
            usbManager.deviceList?.values?.firstOrNull {
                it.vendorId in USB_SERIAL_BRIDGE_VENDOR_IDS && !isVspCdcAcmDevice(it)
            }
        }.getOrNull()
        if (serialBridge != null) {
            LogUtil.d(
                TAG,
                "Inserted cable classified as RS232 (USB-serial bridge vendor=0x${
                    serialBridge.vendorId.toString(16)
                }, product=0x${serialBridge.productId.toString(16)})"
            )
            return CableProtocol.RS232
        }

        // 2) USB accessory present -> AOA.
        val hasAccessory = runCatching {
            usbManager.accessoryList?.isNotEmpty() == true
        }.getOrDefault(false)
        if (hasAccessory) {
            LogUtil.d(TAG, "Inserted cable classified as USB_AOA (accessory present)")
            return CableProtocol.USB_AOA
        }

        // 3) No external USB cable -> VSP on-board UART fallback (reported as null).
        LogUtil.d(TAG, "No external USB cable detected; VSP on-board fallback will be tried first")
        return null
    }

    /**
     * A Sunmi VSP virtual serial endpoint exposes a CDC-ACM data interface named "CDC ACM Data"
     * (interfaceClass 10). Matches the identification used by the VSP client kernel so the two stay
     * consistent, and prevents a VSP device from being mistaken for an RS232 USB-serial bridge when
     * their vendor ids collide (e.g. Prolific 0x067B).
     */
    private fun isVspCdcAcmDevice(device: UsbDevice): Boolean {
        return runCatching {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == 10 && "CDC ACM Data".equals(iface.name, ignoreCase = true)) {
                    return true
                }
            }
            false
        }.getOrDefault(false)
    }
}
