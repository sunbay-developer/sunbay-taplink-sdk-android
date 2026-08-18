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
 * | onScreenTip | suggestions | Screen | Receipt tip area |
 * |---|---|---|---|
 * | true | provided | Tip screen with the given suggestions | None |
 * | true | null | Tip screen, custom amount only | None |
 * | false | provided | No tip screen | Suggested amounts, for a hand-written tip |
 * | false | null | No tip screen | Blank tip line, for a hand-written tip |
 *
 * The tip is only ever captured in one place. With [onScreenTip] enabled the amount is settled
 * on the tip screen, so the receipt carries no tip area at all. With [onScreenTip] disabled the
 * receipt takes over: it prints your [suggestions] when provided, or a blank tip line when not.
 * The request configuration replaces the host receipt tip settings as a whole and the two are
 * never combined.
 *
 * When [useHostConfig] is false, a field left at its default is treated as "not specified by the
 * request" and never falls back to the host value. Note that [tipMode] and [tipWithTax] are
 * non-null with defaults, so typed SDK requests always carry a value; only raw-JSON and Cloud
 * requests can omit them.
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
     * - false: no tip screen is displayed and the tip is captured on the receipt instead, so the
     *   cardholder can write it by hand — the terminal prints the suggested tip amounts when
     *   [suggestions] is provided, or a blank tip line when it is null.
     *
     * This field is ignored when [useHostConfig] is true.
     */
    val onScreenTip: Boolean = false,

    /**
     * Tip mode (optional, default: ON_SALE).
     * - ON_SALE: Tip is collected during the sale transaction
     * - AFTER_SALE: Tip is collected after the sale transaction
     *
     * This field is ignored when [useHostConfig] is true. When [useHostConfig] is false it is
     * used as-is and never falls back to the host tip mode.
     */
    val tipMode: TipMode = TipMode.ON_SALE,

    /**
     * Whether tip calculation includes tax amount (optional, default: false).
     * When true, the tip percentage is calculated based on the amount including tax.
     *
     * This field is ignored when [useHostConfig] is true. When [useHostConfig] is false it is
     * used as-is and never falls back to the host setting.
     */
    val tipWithTax: Boolean = false,

    /**
     * Tip suggestion options (optional).
     *
     * Where the suggestions are shown depends on [onScreenTip]:
     * - [onScreenTip] = true: the suggestions become the selectable tip options on the tip screen,
     *   and the receipt carries no tip area
     * - [onScreenTip] = false: the suggestions are printed on the receipt as suggested tip
     *   amounts and no tip screen is shown
     *
     * A null value means no suggestions are used; it never falls back to the host suggestions.
     * With [onScreenTip] = false and no suggestions, the receipt prints a blank tip line instead.
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
