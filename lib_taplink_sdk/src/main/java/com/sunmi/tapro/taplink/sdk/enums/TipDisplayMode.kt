package com.sunmi.tapro.taplink.sdk.enums

/**
 * Tip display mode enumeration.
 *
 * Defines when the tip prompt should be displayed during a transaction.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class TipDisplayMode(val value: String) {
    /**
     * Display tip prompt during sale.
     * Tip prompt is shown before the transaction is completed.
     */
    ON_SALE("ON_SALE"),

    /**
     * Display tip prompt after sale.
     * Tip prompt is shown after the transaction is completed.
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
        fun fromValue(value: String): TipDisplayMode? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }

        /**
         * Gets all supported tip display mode values.
         *
         * @return list of all tip display mode string values
         */
        @JvmStatic
        fun getAllValues(): List<String> {
            return values().map { it.value }
        }
    }

    /**
     * Converts to string representation.
     */
    override fun toString(): String = value
}
