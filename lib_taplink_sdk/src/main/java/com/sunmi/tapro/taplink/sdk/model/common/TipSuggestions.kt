package com.sunmi.tapro.taplink.sdk.model.common

import com.sunmi.tapro.taplink.sdk.enums.FeeMode

/**
 * Tip suggestions configuration.
 *
 * Defines the suggested tip values and how they should be interpreted.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TipSuggestions(
    /**
     * Fee mode that determines how suggestion values are interpreted.
     * - RATE: values represent percentage rates (e.g., 15 means 15%)
     * - AMOUNT: values represent fixed amounts in base currency unit
     */
    val feeMode: FeeMode,

    /**
     * List of suggested tip values.
     * Interpretation depends on feeMode:
     * - When feeMode is RATE: values like [15, 18, 20] mean 15%, 18%, 20%
     * - When feeMode is AMOUNT: values like [100, 200, 500] mean fixed amounts
     */
    val values: List<Int>,

    /**
     * Optional list of descriptive name labels for each tip suggestion.
     * Each element corresponds positionally to the entry at the same index in [values].
     * For example: ["Acceptable", "Good", "Great", "Excellent"]
     *
     * No length validation is performed against [values] at this layer.
     */
    val names: List<String>? = null
) {
    /**
     * Sets the fee mode.
     *
     * @param feeMode the fee mode
     * @return the updated TipSuggestions instance for method chaining
     */
    fun setFeeMode(feeMode: FeeMode): TipSuggestions = copy(feeMode = feeMode)

    /**
     * Sets the suggestion values.
     *
     * @param values the list of suggestion values
     * @return the updated TipSuggestions instance for method chaining
     */
    fun setValues(values: List<Int>): TipSuggestions = copy(values = values)

    /**
     * Sets the name labels for tip suggestions.
     *
     * @param names the list of name labels, or null to clear
     * @return the updated TipSuggestions instance for method chaining
     */
    fun setNames(names: List<String>?): TipSuggestions = copy(names = names)
}
