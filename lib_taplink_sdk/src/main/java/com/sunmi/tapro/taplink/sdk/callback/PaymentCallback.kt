package com.sunmi.tapro.taplink.sdk.callback

import com.sunmi.tapro.taplink.sdk.error.PaymentError
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.response.PaymentResult

/**
 * Payment callback interface
 *
 * Callback lifecycle for a card-present transaction:
 * 1. [onProgress] — fired multiple times as the transaction advances (card read, PIN, online auth, etc.)
 * 2. [onSuccess] — fired when the terminal returns a **final** result. This fires for **all**
 *    terminal-confirmed outcomes: approved, declined, and cancelled. Inspect [PaymentResult] to
 *    determine the actual outcome:
 *    - [PaymentResult.isSuccess] — transaction approved by issuer
 *    - [PaymentResult.isFailed] — transaction declined, cancelled, or failed by issuer/terminal
 *    - [PaymentResult.isProcessing] — gateway still deciding; poll with `client.query()`
 * 3. [onFailure] — fired **only** when a technical or communication error prevents the terminal
 *    from returning any response (connection lost, timeout, invalid request, etc.).
 *    This is NOT a card decline — it means no result was received at all.
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
     * In Headless mode, intermediate callbacks may be delivered as
     * [com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent.HeadlessEvent], which carries
     * full metadata (`eventId`, `transactionRequestId`, `referenceOrderId`, `cancelable`,
     * `timestamp`, and `data`) for idempotency and UI recovery.
     *
     * @param event Progress event with stage code and description
     */
    fun onProgress(event: PaymentEvent)

    /**
     * Terminal returned a final transaction response.
     *
     * This callback fires for **all** terminal-confirmed outcomes — approved, declined, and
     * cancelled. Always inspect the result status to determine the actual outcome:
     *
     * ```kotlin
     * override fun onSuccess(result: PaymentResult) {
     *     when {
     *         result.isSuccess()    -> handleApproved(result)
     *         result.isFailed()     -> handleDeclined(result)
     *         result.isProcessing() -> pollForFinalStatus(result)
     *     }
     * }
     * ```
     *
     * Note: Cancelled/aborted transactions arrive as FAILED (transactionStatus = "FAILED").
     *
     * @param result Transaction result; inspect [PaymentResult.isSuccess], [PaymentResult.isFailed],
     *   and [PaymentResult.isProcessing] to determine the outcome
     */
    fun onSuccess(result: PaymentResult)

    /**
     * Transaction failure — technical or communication error.
     *
     * This indicates the transaction **could not be completed** — no response was received from
     * the terminal. This is NOT a card decline; declines arrive via [onSuccess] with
     * [PaymentResult.isFailed] returning `true`.
     *
     * Common causes: connection lost, request timeout (error 306), invalid parameters.
     *
     * See [PaymentError.code], [PaymentError.suggestion], and [PaymentError.canRetryWithSameId]
     * to decide whether to retry or use a new [com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest.transactionRequestId].
     *
     * @param error Error details including code, message, and retry guidance
     */
    fun onFailure(error: PaymentError)

}

