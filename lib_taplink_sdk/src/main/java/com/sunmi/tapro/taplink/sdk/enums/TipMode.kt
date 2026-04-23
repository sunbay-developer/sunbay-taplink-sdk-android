package com.sunmi.tapro.taplink.sdk.enums

/**
 * Tip mode enumeration.
 *
 * Defines when the tip is applied during the transaction flow.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class TipMode(val value: String) {
    /**
     * Tip is applied during the sale transaction.
     */
    ON_SALE("ON_SALE"),

    /**
     * Tip is applied after the sale transaction.
     */
    AFTER_SALE("AFTER_SALE");

    companion object {
        /**
         * Gets the enum from a string value.
         *
         * @param value the string value
         * @return the corresponding enum, or null if not found
         */
        @JvmStatic
        fun fromValue(value: String): TipMode? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }
    }

    /**
     * Converts to string representation.
     */
    override fun toString(): String = value
}
