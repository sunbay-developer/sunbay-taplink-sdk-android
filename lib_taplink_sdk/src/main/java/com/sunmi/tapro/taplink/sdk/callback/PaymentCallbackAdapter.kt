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
 * **Important:** [onSuccess] fires for both approved and declined transactions.
 * Always check [PaymentResult.isSuccess] (or implement [onDeclined]) to distinguish outcomes.
 *
 * Kotlin usage:
 * ```kotlin
 * client.sale(request, object : PaymentCallbackAdapter() {
 *     override fun onSuccess(result: PaymentResult) {
 *         if (result.isSuccess()) {
 *             // payment approved
 *         }
 *     }
 *     override fun onDeclined(result: PaymentResult) {
 *         // payment declined by issuer
 *     }
 *     override fun onFailure(error: PaymentError) {
 *         // technical/communication error
 *     }
 * })
 * ```
 *
 * Java usage:
 * ```java
 * client.sale(request, new PaymentCallbackAdapter() {
 *     @Override
 *     public void onSuccess(PaymentResult result) { ... }
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

    override fun onDeclined(result: PaymentResult) {}

    override fun onFailure(error: PaymentError) {}
}
