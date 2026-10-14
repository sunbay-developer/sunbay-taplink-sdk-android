package com.sunmi.tapro.taplink.sdk.model.common

import com.sunmi.tapro.taplink.sdk.enums.FeeMode

/**
 * Tip suggestions configuration.
 *
 * Defines the suggested tip values and how they should be interpreted.
 *
 * Field setters update this instance and return it for chaining. Use [copy] for
 * independent top-level values, and finish updates before request submission.
 * Serialize writes and do not mutate instances used as hash collection keys.
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class TipSuggestions(
    /**
     * Fee mode that determines how suggestion values are interpreted.
     * - RATE: values represent percentage rates (e.g., 15 means 15%)
     * - AMOUNT: values represent fixed amounts in base currency unit
     */
    feeMode: FeeMode,

    /**
     * List of suggested tip values.
     * Interpretation depends on feeMode:
     * - When feeMode is RATE: values like [15, 18, 20] mean 15%, 18%, 20%
     * - When feeMode is AMOUNT: values like [100, 200, 500] mean fixed amounts
     */
    values: List<Int>,

    /**
     * Optional list of descriptive name labels for each tip suggestion.
     * Each element corresponds positionally to the entry at the same index in [values].
     * For example: ["Acceptable", "Good", "Great", "Excellent"]
     *
     * No length validation is performed against [values] at this layer.
     */
    names: List<String>? = null
) {
    var feeMode: FeeMode = feeMode
        private set

    var values: List<Int> = values
        private set

    var names: List<String>? = names
        private set

    /**
     * Sets the fee mode.
     *
     * @param feeMode the fee mode
     * @return this TipSuggestions instance for method chaining
     */
    fun setFeeMode(feeMode: FeeMode): TipSuggestions = apply { this.feeMode = feeMode }

    /**
     * Sets the suggestion values.
     *
     * @param values the list of suggestion values
     * @return this TipSuggestions instance for method chaining
     */
    fun setValues(values: List<Int>): TipSuggestions = apply { this.values = values }

    /**
     * Sets the name labels for tip suggestions.
     *
     * @param names the list of name labels, or null to clear
     * @return this TipSuggestions instance for method chaining
     */
    fun setNames(names: List<String>?): TipSuggestions = apply { this.names = names }

    /** Creates an independent shallow copy; setters mutate this instance. */
    fun copy(
        feeMode: FeeMode = this.feeMode,
        values: List<Int> = this.values,
        names: List<String>? = this.names
    ): TipSuggestions = TipSuggestions(
        feeMode = feeMode,
        values = values,
        names = names
    )

    operator fun component1(): FeeMode = feeMode
    operator fun component2(): List<Int> = values
    operator fun component3(): List<String>? = names

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TipSuggestions) return false
        return feeMode == other.feeMode &&
            values == other.values &&
            names == other.names
    }

    override fun hashCode(): Int {
        var result = feeMode.hashCode()
        result = 31 * result + values.hashCode()
        result = 31 * result + (names?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "TipSuggestions(feeMode=$feeMode, values=$values, names=$names)"

}
