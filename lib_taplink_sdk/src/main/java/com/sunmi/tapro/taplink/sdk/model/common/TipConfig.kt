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
 * Combining [onScreenTip] and [suggestions] selects where the tip is collected:
 *
 * | onScreenTip | suggestions | Behavior |
 * |---|---|---|
 * | true | provided | Tip screen with the given suggestions |
 * | true | null | Tip screen without suggestions (custom amount only) |
 * | false | provided | No tip screen; suggested tip amounts are printed on the receipt |
 * | false | null | Tip flow is skipped entirely |
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
     * - true: the terminal displays a tip input screen and the tip is captured on screen
     * - false: no tip screen is displayed. If [suggestions] is also provided, the terminal
     *   prints the suggested tip amounts on the receipt instead, so the cardholder can write
     *   the tip by hand. If [suggestions] is null, the tip flow is skipped entirely.
     *
     * This field is ignored when [useHostConfig] is true.
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
     *
     * Where the suggestions are shown depends on [onScreenTip]:
     * - [onScreenTip] = true: the suggestions become the selectable tip options on the tip screen
     * - [onScreenTip] = false: the suggestions are printed on the receipt as suggested tip
     *   amounts and no tip screen is shown
     *
     * A null value means no suggestions are used; it never falls back to the host suggestions.
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
