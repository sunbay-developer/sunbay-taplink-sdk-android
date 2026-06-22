package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Transaction progress event.
 *
 * Uses sealed class to provide type-safe event definitions.
 * Supports exhaustive checking in when expressions.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
sealed class PaymentEvent(
    /**
     * Status code.
     */
    open val eventCode: String,

    /**
     * Status description.
     */
    open val eventMsg: String,

    /**
     * Progress (0-100).
     */
    val progress: Int,

    /**
     * Whether the transaction can be cancelled at this stage.
     * Only applicable in Headless mode.
     */
    open val cancelable: Boolean = false,

    /**
     * Global unique event ID for idempotent handling in Headless mode.
     * Present when Tapro emits intermediate Headless callbacks.
     */
    open val eventId: String? = null,

    /**
     * Transaction request ID for correlating callbacks with the transaction.
     * Present when Tapro emits intermediate Headless callbacks.
     */
    open val transactionRequestId: String? = null,

    /**
     * Merchant reference order ID, if provided by POS.
     * Present when Tapro emits intermediate Headless callbacks.
     */
    open val referenceOrderId: String? = null,

    /**
     * Event-specific payload (e.g. entryMode / print failure reason).
     * Present when Tapro emits intermediate Headless callbacks.
     */
    open val data: Map<String, String>? = null,



    /**
     * Timestamp.
     */
    open val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Processing event.
     */
    object Processing : PaymentEvent("PROCESSING", "Processing", 10)

    /**
     * Tip selection is in progress.
     *
     * This event is emitted after payment initiation and before [WaitingCard]
     * when on-screen tip collection is configured through `TipConfig` with
     * `onScreenTip = true`.
     */
    object TipProcessing : PaymentEvent("TIP_PROCESSING", "Tip selection in progress", 15)

    /**
     * Waiting for card event.
     */
    object WaitingCard : PaymentEvent("WAITING_CARD", "Waiting for card", 20, cancelable = true)

    /**
     * Card detected event.
     */
    object CardDetected : PaymentEvent("CARD_DETECTED", "Card detected", 30)

    /**
     * Reading card event.
     */
    object ReadingCard : PaymentEvent("READING_CARD", "Reading card", 40)

    /**
     * Waiting for PIN event.
     */
    object WaitingPin : PaymentEvent("WAITING_PIN", "Waiting for PIN", 50, cancelable = true)
    /**
     * Waiting for signature event.
     */
    object WaitingSignature : PaymentEvent("WAITING_SIGNATURE", "Waiting for signature", 55)

    /**
     * Online processing event.
     * Represents the stage when Tapro is contacting the bank for transaction processing.
     */
    object OnlineProcessing : PaymentEvent("ONLINE_PROCESSING", "Online processing", 70)

    /**
     * QR code displayed, waiting for customer to scan (MPM mode).
     */
    object WaitingQrScan : PaymentEvent("WAITING_QR_SCAN", "Waiting for customer to scan", 25)

    /**
     * Scanning customer QR code (CPM mode).
     */
    object ScanningCustomerQr : PaymentEvent("SCANNING_CUSTOMER_QR", "Scanning customer QR code", 25)

    /**
     * Customer QR code scanned, processing payment (CPM mode).
     */
    object QrPaymentProcessing : PaymentEvent("QR_PAYMENT_PROCESSING", "QR payment processing", 60)

    /**
     * Printing event.
     */
    object Printing : PaymentEvent("PRINTING", "Printing receipt", 80)

    /**
     * Completed event.
     */
    object Completed : PaymentEvent("COMPLETED", "Transaction completed", 100)

    /**
     * Cancel event.
     */
    object Cancel : PaymentEvent("CANCEL", "Transaction cancelled", 0)

    // ==================== Headless Mode Events ====================

    /**
     * Transaction has been accepted and is being initialized.
     * Headless mode: POS app should display "Processing..."
     */
    object TransactionInitiated : PaymentEvent("TRANSACTION_INITIATED", "Transaction initiated", 5, cancelable = true)

    /**
     * Reader hardware is being initialized.
     * Headless mode: POS app should display "Preparing..."
     */
    object InitializingReader : PaymentEvent("INITIALIZING_READER", "Initializing reader", 15, cancelable = true)

    /**
     * Card entry mode has been confirmed (swipe/insert/tap/manual).
     * Headless mode: POS app may display entry mode info.
     */
    object EntryModeConfirmed : PaymentEvent("ENTRY_MODE_CONFIRMED", "Entry mode confirmed", 35)

    /**
     * Waiting for manual card number entry (Terminal-Mandatory UI).
     * Headless mode: POS app should display "Please enter card number on terminal"
     */
    object WaitingManualEntry : PaymentEvent("WAITING_MANUAL_ENTRY", "Waiting for manual entry", 25, cancelable = true)

    /**
     * Manual card number entry completed.
     */
    object ManualEntryCompleted : PaymentEvent("MANUAL_ENTRY_COMPLETED", "Manual entry completed", 35)

    /**
     * EMV chip processing in progress.
     * Headless mode: POS app should display "Processing..."
     */
    object EmvProcessing : PaymentEvent("EMV_PROCESSING", "EMV processing", 40)

    /**
     * Waiting for multi-application selection (Terminal-Mandatory UI).
     * Headless mode: POS app should display "Please select application on terminal"
     */
    object WaitingAppSelection : PaymentEvent("WAITING_APP_SELECTION", "Waiting for app selection", 45, cancelable = true)

    /**
     * Application selection completed.
     */
    object AppSelected : PaymentEvent("APP_SELECTED", "App selected", 48)

    /**
     * PIN entry completed.
     */
    object PinEntered : PaymentEvent("PIN_ENTERED", "PIN entered", 55)

    /**
     * Signature collection completed.
     */
    object SignatureCompleted : PaymentEvent("SIGNATURE_COMPLETED", "Signature completed", 58)

    /**
     * Please remove card (Terminal-Mandatory UI).
     * Headless mode: POS app may also display "Please remove card"
     */
    object RemoveCard : PaymentEvent("REMOVE_CARD", "Please remove card", 90)

    /**
     * Card has been removed.
     */
    object CardRemoved : PaymentEvent("CARD_REMOVED", "Card removed", 92)

    /**
     * Print completed successfully.
     */
    object PrintCompleted : PaymentEvent("PRINT_COMPLETED", "Print completed", 85)

    /**
     * Print failed (does not affect transaction result).
     */
    object PrintFailed : PaymentEvent("PRINT_FAILED", "Print failed", 85)



    /**
     * Reconnecting event.
     */
    data class Reconnecting(
        val attempt: Int,
        val maxRetries: Int,
        override val eventCode: String = "RECONNECTING",
        override val eventMsg: String = "Reconnecting... (attempt $attempt/$maxRetries)"
    ) : PaymentEvent(eventCode, eventMsg, 0)

    /**
     * Custom/dynamic event for Headless mode intermediate events.
     * Used when the event code doesn't match any predefined event.
     */
    data class HeadlessEvent(
        override val eventCode: String,
        override val eventMsg: String,
        override val cancelable: Boolean = false,
        override val eventId: String? = null,
        override val transactionRequestId: String? = null,
        override val referenceOrderId: String? = null,
        override val data: Map<String, String>? = null,
        override val timestamp: Long = System.currentTimeMillis(),
        val rawEventCode: String? = null,
    ) : PaymentEvent(
        eventCode = eventCode,
        eventMsg = eventMsg,
        progress = 50,
        cancelable = cancelable,
        eventId = eventId,
        transactionRequestId = transactionRequestId,
        referenceOrderId = referenceOrderId,
        data = data,
        timestamp = timestamp
    )



    companion object {
        /**
         * Creates corresponding PaymentEvent from eventCode string.
         * Used for JSON deserialization.
         *
         * @param eventCode the event code
         * @return the corresponding PaymentEvent subclass instance
         */
        fun fromEventCode(eventCode: String): PaymentEvent {
            // 支持两种格式：UPPER_SNAKE_CASE (标准) 和 PascalCase (Headless 中间事件)
            val normalized = eventCode.uppercase()
            // 将 PascalCase 转为 UPPER_SNAKE_CASE 进行匹配
            val snakeCase = eventCode.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

            return when {
                normalized == "WAITING_CARD" || snakeCase == "WAITING_CARD" -> WaitingCard
                normalized == "TIP_PROCESSING" || snakeCase == "TIP_PROCESSING" -> TipProcessing
                normalized == "CARD_DETECTED" || snakeCase == "CARD_DETECTED" -> CardDetected
                normalized == "READING_CARD" || snakeCase == "READING_CARD" -> ReadingCard
                normalized == "WAITING_PIN" || snakeCase == "WAITING_PIN" -> WaitingPin
                normalized == "WAITING_SIGNATURE" || snakeCase == "WAITING_SIGNATURE" -> WaitingSignature
                normalized == "PROCESSING" -> Processing
                normalized == "ONLINE_PROCESSING" || snakeCase == "ONLINE_PROCESSING" -> OnlineProcessing
                // Backward compatibility: old event code "WAITING_RESPONSE" maps to OnlineProcessing
                normalized == "WAITING_RESPONSE" || snakeCase == "WAITING_ONLINE_RESPONSE" -> OnlineProcessing
                normalized == "PRINTING" -> Printing
                normalized == "COMPLETED" || normalized == "4003" -> Completed
                normalized == "CANCEL" -> Cancel
                // Headless mode events
                normalized == "TRANSACTION_INITIATED" || snakeCase == "TRANSACTION_INITIATED" -> TransactionInitiated
                normalized == "INITIALIZING_READER" || snakeCase == "INITIALIZING_READER" -> InitializingReader
                normalized == "ENTRY_MODE_CONFIRMED" || snakeCase == "ENTRY_MODE_CONFIRMED" -> EntryModeConfirmed
                normalized == "WAITING_MANUAL_ENTRY" || snakeCase == "WAITING_MANUAL_ENTRY" -> WaitingManualEntry
                normalized == "MANUAL_ENTRY_COMPLETED" || snakeCase == "MANUAL_ENTRY_COMPLETED" -> ManualEntryCompleted
                normalized == "EMV_PROCESSING" || snakeCase == "EMV_PROCESSING" -> EmvProcessing
                normalized == "WAITING_APP_SELECTION" || snakeCase == "WAITING_APP_SELECTION" -> WaitingAppSelection
                normalized == "APP_SELECTED" || snakeCase == "APP_SELECTED" -> AppSelected
                normalized == "PIN_ENTERED" || snakeCase == "PIN_ENTERED" -> PinEntered
                normalized == "SIGNATURE_COMPLETED" || snakeCase == "SIGNATURE_COMPLETED" -> SignatureCompleted
                normalized == "REMOVE_CARD" || snakeCase == "REMOVE_CARD" -> RemoveCard
                normalized == "CARD_REMOVED" || snakeCase == "CARD_REMOVED" -> CardRemoved
                normalized == "PRINT_COMPLETED" || snakeCase == "PRINT_COMPLETED" -> PrintCompleted
                normalized == "PRINT_FAILED" || snakeCase == "PRINT_FAILED" -> PrintFailed

                normalized == "WAITING_QR_SCAN" || snakeCase == "WAITING_QR_SCAN" -> WaitingQrScan
                normalized == "SCANNING_CUSTOMER_QR" || snakeCase == "SCANNING_CUSTOMER_QR" -> ScanningCustomerQr
                normalized == "QR_PAYMENT_PROCESSING" || snakeCase == "QR_PAYMENT_PROCESSING" -> QrPaymentProcessing

                else -> {
                    // Unknown event type, use HeadlessEvent as fallback for intermediate events
                    HeadlessEvent(eventCode = eventCode, eventMsg = eventCode)
                }
            }
        }
    }
}
