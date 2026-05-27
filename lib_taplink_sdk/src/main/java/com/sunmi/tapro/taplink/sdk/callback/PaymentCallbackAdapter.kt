package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * Abstract adapter for [PaymentCallback].
 *
 * Provides empty default implementations for all callback methods. Integrators can either:
 *
 * **Option A — override [onSuccess] directly** (same as implementing [PaymentCallback]):
 * ```kotlin
 * client.sale(request, object : PaymentCallbackAdapter() {
 *     override fun onSuccess(result: PaymentResult) {
 *         when {
 *             result.isSuccess()    -> handleApproved(result)
 *             result.isFailed()     -> handleDeclined(result)
 *             result.isProcessing() -> pollForFinalStatus(result)
 *         }
 *     }
 *     override fun onFailure(error: PaymentError) { /* comm error */ }
 * })
 * ```
 *
 * **Option B — override the semantic helpers** (recommended for clarity and easier migration from v1.0.6):
 * ```kotlin
 * client.sale(request, object : PaymentCallbackAdapter() {
 *     override fun onTransactionApproved(result: PaymentResult)  { showApproved(result) }
 *     override fun onTransactionDeclined(result: PaymentResult)  { showDeclined(result) }
 *     override fun onTransactionProcessing(result: PaymentResult){ pollForFinalStatus(result) }
 *     override fun onFailure(error: PaymentError) { /* comm error */ }
 * })
 * ```
 *
 * Java callers can use Option B in the same way (just `@Override` the methods they need).
 *
 * > **Migration note (v1.0.6 → v1.0.7)**:
 * > In v1.0.6, declined transactions were delivered via `onFailure(PaymentError)`.
 * > From v1.0.7 onwards they arrive via `onSuccess` with `result.isFailed() == true`.
 * > If you previously handled declines in `onFailure`, move that logic to [onTransactionDeclined].
 * > See README §"Migrating from v1.0.6" for a full before/after guide.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
abstract class PaymentCallbackAdapter : PaymentCallback {

    override fun onProgress(event: PaymentEvent) {}

    /**
     * Terminal returned a final transaction response.
     *
     * The default implementation dispatches to the semantic helpers
     * [onTransactionApproved], [onTransactionDeclined], and [onTransactionProcessing].
     * Override this method directly if you prefer a single entry point.
     */
    override fun onSuccess(result: PaymentResult) {
        when {
            result.isSuccess()    -> onTransactionApproved(result)
            result.isFailed()     -> onTransactionDeclined(result)
            result.isProcessing() -> onTransactionProcessing(result)
        }
    }

    /**
     * Transaction approved by the issuer.
     *
     * Called by the default [onSuccess] when `result.isSuccess() == true`.
     * Override this instead of [onSuccess] for cleaner, intent-revealing code.
     *
     * @param result Approved transaction result
     */
    open fun onTransactionApproved(result: PaymentResult) {}

    /**
     * Transaction declined, cancelled, or aborted.
     *
     * Called by the default [onSuccess] when `result.isFailed() == true`.
     *
     * **Migration note**: In SDK ≤ v1.0.6, declined transactions were delivered via
     * `onFailure(PaymentError)`. Move your old decline-handling code here.
     *
     * @param result Failed transaction result; inspect [PaymentResult.transactionResultMsg]
     *   for the decline reason
     */
    open fun onTransactionDeclined(result: PaymentResult) {}

    /**
     * Transaction is still being processed by the gateway.
     *
     * Called by the default [onSuccess] when `result.isProcessing() == true`.
     * Use [PaymentResult.transactionId] to poll for the final status.
     *
     * @param result Processing transaction result
     */
    open fun onTransactionProcessing(result: PaymentResult) {}

    /**
     * Communication or technical error — no response received from the terminal.
     *
     * This is NOT a card decline. Declines arrive via [onTransactionDeclined].
     *
     * @param error Error details including code, message, and retry guidance
     */
    override fun onFailure(error: PaymentError) {}
}
