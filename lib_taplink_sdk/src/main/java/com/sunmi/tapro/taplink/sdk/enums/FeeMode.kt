package com.sunmi.tapro.taplink.sdk.enums

/**
 * Fee mode enumeration.
 *
 * Defines how tip suggestion values are interpreted.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class FeeMode(val value: String) {
    /**
     * Tip suggestions are expressed as percentage rates (e.g., 15 means 15%).
     */
    RATE("RATE"),

    /**
     * Tip suggestions are expressed as fixed amounts (in base currency unit).
     */
    AMOUNT("AMOUNT");

    companion object {
        /**
         * Gets the enum from a string value.
         *
         * @param value the string value
         * @return the corresponding enum, or null if not found
         */
        @JvmStatic
        fun fromValue(value: String): FeeMode? {
            return values().find { it.value.equals(value, ignoreCase = true) }
        }
    }

    /**
     * Converts to string representation.
     */
    override fun toString(): String = value
}
