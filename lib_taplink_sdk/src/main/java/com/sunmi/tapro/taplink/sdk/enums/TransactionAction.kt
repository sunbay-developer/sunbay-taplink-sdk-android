package com.sunmi.tapro.taplink.sdk.enums

/**
 * Transaction Type Enumeration
 *
 * Defines all supported transaction types, avoiding the use of magic strings
 *
 * @author TaPro Team
 * @since 2025-12-12
 */
enum class TransactionAction(val value: String) {
    /**
     * Initialize connection
     * Used to initialize TaPro application after establishing connection
     */
    INIT("INIT"),

    /**
     * Sale transaction
     * Standard payment transaction
     */
    SALE("SALE"),

    /**
     * Refund transaction
     * Refund a completed transaction
     */
    REFUND("REFUND"),

    /**
     * Void transaction
     * Cancel same-day transaction (same-day refund)
     */
    VOID("VOID"),

    /**
     * Pre-authorization
     * Freeze funds but do not charge
     */
    AUTH("AUTH"),

    /**
     * Pre-authorization completion
     * Complete pre-authorization and charge
     */
    POST_AUTH("POST_AUTH"),

    /**
     * Pre-authorization increment
     * Increase pre-authorization amount
     */
    INCREMENT_AUTH("INCREMENT_AUTH"),

    /**
     * Forced authorization
     * Force complete transaction using authorization code
     */
    FORCED_AUTH("FORCED_AUTH"),

    /**
     * Tip adjustment
     * Adjust tip amount for completed transaction
     */
    TIP_ADJUST("TIP_ADJUST"),

    /**
     * Batch settlement
     * Settle all transactions in current batch
     */
    BATCH_CLOSE("BATCH_CLOSE"),

    /**
     * Query transaction
     * Query transaction status
     */
    QUERY("QUERY"),

    /**
     * Abort transaction
     * Terminate current ongoing transaction
     */
    ABORT("ABORT");

    companion object {
        /**
         * Get enumeration from string value
         *
         * @param value String value
         * @return TransactionAction? Corresponding enumeration, returns null if not found
         */
        @JvmStatic
        fun fromValue(value: String): TransactionAction? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }

        /**
         * Get all supported transaction type values
         *
         * @return List<String> List of string values for all transaction types
         */
        @JvmStatic
        fun getAllValues(): List<String> {
            return values().map { it.value }
        }
    }

    /**
     * Convert to string
     */
    override fun toString(): String = value
}
