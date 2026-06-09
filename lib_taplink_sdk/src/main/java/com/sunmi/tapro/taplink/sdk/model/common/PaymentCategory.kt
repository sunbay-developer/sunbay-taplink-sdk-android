package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Payment method category enumeration.
 *
 * Defines all supported payment method categories.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PaymentCategory(val value: String) {
    /**
     * Bank card.
     */
    CARD("CARD"),

    /**
     * Electronic Benefit Transfer.
     */
    EBT("EBT"),

    /**
     * QR code merchant-presented mode (merchant scans customer).
     */
    QR_MPM("QR-MPM"),

    /**
     * QR code customer-presented mode (customer scans merchant).
     */
    QR_CPM("QR-CPM");

    companion object {
        /**
         * Converts from string to enum.
         *
         * @param value the string value
         * @return the corresponding enum value, or null if not recognized
         */
        fun fromString(value: String?): PaymentCategory? {
            if (value.isNullOrBlank()) return null

            return when (value.uppercase()) {
                "CARD" -> CARD
                "EBT" -> EBT
                "QR-MPM" -> QR_MPM
                "QR-CPM" -> QR_CPM
                else -> null
            }
        }
    }

    /**
     * Converts to string (for API transmission).
     */
    fun toApiString(): String = value
}
