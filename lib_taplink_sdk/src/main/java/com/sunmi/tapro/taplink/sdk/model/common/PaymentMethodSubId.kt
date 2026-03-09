package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Payment method sub-ID enumeration.
 *
 * Defines sub-payment method identifiers.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PaymentMethodSubId(val value: String) {
    /**
     * SNAP (Supplemental Nutrition Assistance Program).
     */
    SNAP("SNAP"),

    /**
     * Voucher.
     */
    VOUCHER("VOUCHER"),

    /**
     * Benefit.
     */
    BENEFIT("BENEFIT"),

    /**
     * Google Pay.
     */
    GOOGLE_PAY("GOOGLE_PAY"),

    /**
     * Apple Pay.
     */
    APPLE_PAY("APPLE_PAY");

    companion object {
        /**
         * Converts from string to enum.
         *
         * @param value the string value
         * @return the corresponding enum value, or null if not recognized
         */
        fun fromString(value: String?): PaymentMethodSubId? {
            if (value.isNullOrBlank()) return null

            return when (value.uppercase().replace("-", "_")) {
                "SNAP" -> SNAP
                "VOUCHER" -> VOUCHER
                "BENEFIT" -> BENEFIT
                "GOOGLE_PAY" -> GOOGLE_PAY
                "APPLE_PAY" -> APPLE_PAY
                else -> null
            }
        }
    }

    /**
     * Converts to string (for API transmission).
     */
    fun toApiString(): String = value
}
