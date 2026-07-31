package com.sunmi.tapro.taplink.sdk.enums

/**
 * Signature entry location for a single transaction.
 */
enum class SignatureEntryLocation(val value: String) {
    /**
     * Collect the signature on the terminal screen.
     */
    ON_SCREEN("ON_SCREEN"),

    /**
     * Print a signature line on the receipt.
     */
    ON_RECEIPT("ON_RECEIPT"),

    /**
     * Do not collect a signature for the transaction.
     */
    NONE("NONE");

    companion object {
        @JvmStatic
        fun fromValue(value: String): SignatureEntryLocation? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }
    }

    override fun toString(): String = value
}
