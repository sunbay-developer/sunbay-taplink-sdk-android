package com.sunmi.tapro.taplink.sdk.enums

/**
 * Transaction action enumeration.
 *
 * Defines all supported transaction types to avoid using magic strings.
 *
 * @author TaPro Team
 * @since 2025-12-12
 */
enum class TransactionAction(val value: String) {
    /**
     * Initialize connection.
     * Used to initialize the TaPro application after establishing a connection.
     */
    INIT("INIT"),

    /**
     * Sale transaction.
     * Standard payment transaction.
     */
    SALE("SALE"),

    /**
     * Refund transaction.
     * Refunds a completed transaction.
     */
    REFUND("REFUND"),

    /**
     * Void transaction.
     * Voids a transaction from the same day (same-day refund).
     */
    VOID("VOID"),

    /**
     * Authorization.
     * Freezes funds without charging.
     */
    AUTH("AUTH"),

    /**
     * Post-authorization.
     * Completes authorization and charges the funds.
     */
    POST_AUTH("POST_AUTH"),

    /**
     * Incremental authorization.
     * Increases the authorization amount.
     */
    INCREMENT_AUTH("INCREMENT_AUTH"),

    /**
     * Forced authorization.
     * Forces transaction completion using an authorization code.
     */
    FORCED_AUTH("FORCED_AUTH"),

    /**
     * Tip adjustment.
     * Adjusts the tip amount for a completed transaction.
     */
    TIP_ADJUST("TIP_ADJUST"),

    /**
     * Batch close.
     * Settles all transactions in the current batch.
     */
    BATCH_CLOSE("BATCH_CLOSE"),

    /**
     * Query transaction.
     * Queries transaction status.
     */
    QUERY("QUERY"),

    /**
     * Abort transaction.
     * Terminates the current ongoing transaction.
     */
    ABORT("ABORT");

    companion object {
        /**
         * Gets the enum from a string value.
         *
         * @param value the string value
         * @return the corresponding enum, or null if not found
         */
        @JvmStatic
        fun fromValue(value: String): TransactionAction? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }

        /**
         * Gets all supported transaction action values.
         *
         * @return list of all transaction action string values
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
