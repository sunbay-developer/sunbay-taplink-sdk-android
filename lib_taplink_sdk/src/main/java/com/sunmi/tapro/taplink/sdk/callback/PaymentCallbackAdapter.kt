package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * Abstract adapter for [PaymentCallback].
 *
 * Provides empty default implementations for all callback methods so integrators can
 * override only the callbacks they need.
 *
 * Override [onSuccess] directly when you want a single entry point for transaction outcomes.
 * If you want to reuse a legacy `PaymentError`-based UI for failed transactions, call
 * `result.toPaymentError()` from [onSuccess] when `result.isFailed()` is `true`.
 *
 * ```kotlin
 * client.sale(request, object : PaymentCallbackAdapter() {
 *     override fun onSuccess(result: PaymentResult) {
 *         when {
 *             result.isSuccess()    -> handleApproved(result)
 *             result.isFailed()     -> showError(result.toPaymentError())
 *             result.isProcessing() -> pollForFinalStatus(result)
 *         }
 *     }
 *
 *     override fun onFailure(error: PaymentError) {
 *         // communication error
 *     }
 * })
 * ```
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class PaymentCallbackAdapter : PaymentCallback {

    override fun onProgress(event: PaymentEvent) {}

    override fun onSuccess(result: PaymentResult) {}

    override fun onFailure(error: PaymentError) {}
}
