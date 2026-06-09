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
    val values: List<Int>
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
}
