package com.sunmi.tapro.taplink.sdk.model.common

import com.sunmi.tapro.taplink.sdk.enums.TipMode

/**
 * Tip configuration class.
 *
 * Defines the on-screen tip behavior and suggestion options for a transaction.
 * This configuration must not be used when the request amount already includes `tipAmount`.
 *
 * The configuration source is selected as a whole through [useHostConfig]; the host tip
 * configuration and the request tip configuration are never merged field by field.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class TipConfig(
    /**
     * Whether the whole tip configuration comes from the Tapro host (optional, default: false).
     * - false: the request fields below are used as-is, the host tip configuration is never read
     * - true: the host tip configuration is used as a whole and every other field here is ignored
     */
    val useHostConfig: Boolean = false,

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
     * Sets the configuration source flag.
     *
     * @param useHostConfig true to use the host tip configuration as a whole
     * @return the updated TipConfig instance for method chaining
     */
    fun setUseHostConfig(useHostConfig: Boolean): TipConfig = copy(useHostConfig = useHostConfig)

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
