package com.sunmi.tapro.taplink.sdk.enums

/**
 * Card network type enumeration.
 *
 * Defines the type of card network for payment transactions.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class CardNetworkType(val value: String) {
    /**
     * Credit card.
     */
    CREDIT("CREDIT"),

    /**
     * Debit card.
     */
    DEBIT("DEBIT");

    companion object {
        /**
         * Gets the enum from a string value.
         *
         * @param value the string value
         * @return the corresponding enum, or null if not found
         */
        @JvmStatic
        fun fromValue(value: String): CardNetworkType? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }
    }

    /**
     * Converts to string representation.
     */
    override fun toString(): String = value
}
