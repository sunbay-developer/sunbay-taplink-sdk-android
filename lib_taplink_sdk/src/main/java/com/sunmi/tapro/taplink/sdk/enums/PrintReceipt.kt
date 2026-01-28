package com.sunmi.tapro.taplink.sdk.enums

/**
 * Receipt type enumeration.
 *
 * Defines the types of receipts that can be printed for a transaction.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PrintReceipt(val value: String) {
    /**
     * No receipt.
     * No receipt will be printed.
     */
    NONE("NONE"),

    /**
     * Merchant copy.
     * Only the merchant copy of the receipt will be printed.
     */
    MERCHANT("MERCHANT"),

    /**
     * Customer copy.
     * Only the customer copy of the receipt will be printed.
     */
    CUSTOMER("CUSTOMER"),

    /**
     * Both copies.
     * Both merchant and customer copies of the receipt will be printed.
     */
    BOTH("BOTH");

    companion object {
        /**
         * Gets the enum from a string value.
         *
         * @param value the string value
         * @return the corresponding enum, or null if not found
         */
        @JvmStatic
        fun fromValue(value: String): PrintReceipt? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }

        /**
         * Gets all supported receipt type values.
         *
         * @return list of all receipt type string values
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
