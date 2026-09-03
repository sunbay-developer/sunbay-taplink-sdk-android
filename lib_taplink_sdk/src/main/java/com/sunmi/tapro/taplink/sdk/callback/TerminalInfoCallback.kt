package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.response.TerminalInfo

/**
 * Callback for [com.sunmi.tapro.taplink.sdk.api.TaplinkApi.getTerminalInfo].
 *
 * GET_TERMINAL_INFO is a read-only control action, not a transaction — it never goes through
 * [PaymentCallback] and must not be confused with payment-success semantics:
 * - [onSuccess] fires once with the merchant/terminal info for the connected TaPro device. The SDK
 *   automatically establishes the configured connection before sending the request when necessary.
 * - [onFailure] fires for any communication/signature/technical error, including when an
 *   older TaPro does not support GET_TERMINAL_INFO.
 *
 * As with [PaymentCallback], callbacks may run on a background thread — switch to the
 * main thread yourself before touching UI.
 *
 * @author TaPro Team
 * @since 2026-08-31
 */
interface TerminalInfoCallback {

    /**
     * TaPro returned merchant and terminal information for the connected device.
     *
     * @param info Merchant/terminal info for the single currently connected TaPro device
     */
    fun onSuccess(info: TerminalInfo)

    /**
     * GET_TERMINAL_INFO failed — technical or communication error, or TaPro does not support
     * GET_TERMINAL_INFO (see [PaymentError.code] and [PaymentError.suggestion]).
     *
     * @param error Error details
     */
    fun onFailure(error: PaymentError)
}
