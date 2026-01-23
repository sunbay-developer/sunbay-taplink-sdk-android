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
     * Timestamp.
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Processing event.
     */
    object Processing : PaymentEvent("PROCESSING", "Processing", 10)

    /**
     * Waiting for card event.
     */
    object WaitingCard : PaymentEvent("WAITING_CARD", "Waiting for card", 20)

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
    object WaitingPin : PaymentEvent("WAITING_PIN", "Waiting for PIN", 50)

    /**
     * Waiting for signature event.
     */
    object WaitingSignature : PaymentEvent("WAITING_SIGNATURE", "Waiting for signature", 55)

    /**
     * Waiting for online response event.
     */
    object WaitingOnlineResponse : PaymentEvent("WAITING_RESPONSE", "Waiting for online response", 70)

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

    /**
     * Reconnecting event.
     */
    data class Reconnecting(
        val attempt: Int,
        val maxRetries: Int,
        override val eventCode: String = "RECONNECTING",
        override val eventMsg: String = "Reconnecting... (attempt $attempt/$maxRetries)"
    ) : PaymentEvent(eventCode, eventMsg, 0)

    companion object {
        /**
         * Creates corresponding PaymentEvent from eventCode string.
         * Used for JSON deserialization.
         *
         * @param eventCode the event code
         * @return the corresponding PaymentEvent subclass instance
         */
        fun fromEventCode(eventCode: String): PaymentEvent {
            return when (eventCode.uppercase()) {
                "WAITING_CARD" -> WaitingCard
                "CARD_DETECTED" -> CardDetected
                "READING_CARD" -> ReadingCard
                "WAITING_PIN" -> WaitingPin
                "WAITING_SIGNATURE" -> WaitingSignature
                "PROCESSING" -> Processing
                "WAITING_RESPONSE" -> WaitingOnlineResponse
                "PRINTING" -> Printing
                "COMPLETED", "4003" -> Completed  // 4003 is the numeric code for completed event
                "CANCEL" -> Cancel
                else -> {
                    // Unknown event type, use Processing as default
                    Processing
                }
            }
        }
    }
}
