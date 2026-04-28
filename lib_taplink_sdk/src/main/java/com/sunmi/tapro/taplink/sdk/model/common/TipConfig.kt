package com.sunmi.tapro.taplink.sdk.model.common

import com.sunmi.tapro.taplink.sdk.enums.TipMode

/**
 * Tip configuration class.
 *
 * Defines the on-screen tip behavior and suggestion options for a transaction.
 * This configuration must not be used when tipAmount in AmountInfo is set.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TipConfig(
    /**
     * Whether to enable on-screen tip prompt (optional, default: false).
     * When true, the payment terminal will display a tip input screen.
     */
    val onScreenTip: Boolean = false,

    /**
     * Tip mode (optional, default: ON_SALE).
     * - ON_SALE: Tip is collected during the sale transaction
     * - AFTER_SALE: Tip is collected after the sale transaction
     */
    val tipMode: TipMode = TipMode.ON_SALE,

    /**
     * Whether tip calculation includes tax amount (optional, default: false).
     * When true, the tip percentage is calculated based on the amount including tax.
     */
    val tipWithTax: Boolean = false,

    /**
     * Tip suggestion options (optional).
     * Provides predefined tip choices for the customer to select from.
     */
    val suggestions: TipSuggestions? = null
) {
    /**
     * Sets the on-screen tip flag.
     *
     * @param onScreenTip whether to enable on-screen tip
     * @return the updated TipConfig instance for method chaining
     */
    fun setOnScreenTip(onScreenTip: Boolean): TipConfig = copy(onScreenTip = onScreenTip)

    /**
     * Sets the tip mode.
     *
     * @param tipMode the tip mode
     * @return the updated TipConfig instance for method chaining
     */
    fun setTipMode(tipMode: TipMode): TipConfig = copy(tipMode = tipMode)

    /**
     * Sets the tip with tax flag.
     *
     * @param tipWithTax whether tip includes tax
     * @return the updated TipConfig instance for method chaining
     */
    fun setTipWithTax(tipWithTax: Boolean): TipConfig = copy(tipWithTax = tipWithTax)

    /**
     * Sets the tip suggestions.
     *
     * @param suggestions the tip suggestions configuration
     * @return the updated TipConfig instance for method chaining
     */
    fun setSuggestions(suggestions: TipSuggestions): TipConfig = copy(suggestions = suggestions)
}
