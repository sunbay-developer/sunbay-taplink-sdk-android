package com.sunmi.tapro.taplink.sdk.enums

/**
 * Signature collection preference for a single transaction.
 *
 * Controls whether signature is collected on terminal screen or on paper receipt.
 */
enum class Signature(val value: String) {
    /**
     * Collect signature on terminal screen.
     */
    ON_SCREEN("ON_SCREEN"),

    /**
     * Print signature line on receipt for handwriting.
     */
    ON_RECEIPT("ON_RECEIPT");

    companion object {
        @JvmStatic
        fun fromValue(value: String): Signature? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }
    }

    override fun toString(): String = value
}

