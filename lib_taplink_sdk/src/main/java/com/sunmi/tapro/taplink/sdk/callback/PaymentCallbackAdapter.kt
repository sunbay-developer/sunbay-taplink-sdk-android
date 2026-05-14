package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * Abstract adapter for [PaymentCallback].
 *
 * Provides empty default implementations for all callback methods so that
 * integrators (especially Java callers) only need to override the methods
 * they care about.
 *
 * **[onSuccess] fires for ALL terminal-confirmed outcomes** — approved, declined, and cancelled.
 * Inspect the result to determine the actual outcome:
 *
 * Kotlin usage:
 * ```kotlin
 * client.sale(request, object : PaymentCallbackAdapter() {
 *     override fun onSuccess(result: PaymentResult) {
 *         when {
 *             result.isSuccess()    -> handleApproved(result)
 *             result.isFailed()     -> handleDeclined(result)
 *             result.isProcessing() -> pollForFinalStatus(result)
 *         }
 *     }
 *     override fun onFailure(error: PaymentError) {
 *         // technical/communication error — no response received from terminal
 *     }
 * })
 * ```
 *
 * Java usage:
 * ```java
 * client.sale(request, new PaymentCallbackAdapter() {
 *     @Override
 *     public void onSuccess(PaymentResult result) {
 *         if (result.isSuccess()) { ... }
 *         else if (result.isFailed()) { ... }
 *         else if (result.isProcessing()) { ... }
 *     }
 *     @Override
 *     public void onFailure(PaymentError error) { ... }
 * });
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
