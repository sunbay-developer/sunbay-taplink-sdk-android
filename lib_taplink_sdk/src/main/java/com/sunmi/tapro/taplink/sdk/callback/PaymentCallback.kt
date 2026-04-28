package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * Payment callback interface
 *
 * Lifecycle for a successful card-present transaction:
 * 1. [onProgress] — fired multiple times as the transaction advances (card read, PIN, online auth, etc.)
 * 2. [onSuccess] — fired when the terminal returns a final result. **Check [PaymentResult.isSuccess]
 *    or implement [onDeclined] to distinguish an approved from a declined transaction.**
 * 3. [onDeclined] — optional convenience callback fired when the terminal returns a FAILED/declined
 *    status. Override this instead of checking status inside [onSuccess].
 * 4. [onFailure] — fired when a technical or communication error prevents the transaction from
 *    completing (e.g., connection lost, timeout, invalid request). This is NOT a card decline.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
interface PaymentCallback {
    
    /**
     * Transaction progress update.
     *
     * Called multiple times during a transaction as it moves through different stages
     * (waiting for card, card detected, PIN entry, online authorization, printing, etc.).
     *
     * @param event Progress event with stage code and description
     */
    fun onProgress(event: PaymentEvent)
    
    /**
     * Transaction completed — result received from the terminal.
     *
     * **Important:** this callback fires for both approved AND declined results.
     * Always check [PaymentResult.isSuccess] to determine the actual outcome, or implement
     * [onDeclined] to handle declined transactions separately.
     *
     * @param result Transaction result; call [PaymentResult.isSuccess] to check approval
     */
    fun onSuccess(result: PaymentResult)

    /**
     * Transaction declined by the issuer or terminal.
     *
     * This is a convenience callback fired when the terminal returns a FAILED status.
     * The default implementation delegates to [onSuccess] for backward compatibility —
     * override this method to handle declines separately from approvals.
     *
     * @param result Transaction result with decline details
     */
    fun onDeclined(result: PaymentResult) {
        onSuccess(result)
    }
    
    /**
     * Transaction failure — technical or communication error.
     *
     * This indicates the transaction could **not** be completed, not that it was declined.
     * Common causes: connection lost, request timeout (error 306), invalid parameters.
     *
     * See [PaymentError.code], [PaymentError.suggestion], and [PaymentError.canRetryWithSameId]
     * to decide whether to retry or use a new [com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest.transactionRequestId].
     *
     * @param error Error details including code, message, and retry guidance
     */
    fun onFailure(error: PaymentError)
    
}



