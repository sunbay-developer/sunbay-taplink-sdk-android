package com.sunmi.tapro.taplink.sdk.model.common

import com.sunmi.tapro.taplink.sdk.enums.SignatureEntryLocation
import java.math.BigDecimal

/**
 * Per-transaction signature settings managed by the POS.
 *
 * The configuration source is selected as a whole through [useHostConfig]; the host signature
 * configuration and the request signature configuration are never merged field by field.
 *
 * For backwards compatibility with the pre-1.0.8 `signatureEntryLocation` field, providing an
 * [entryLocation] is by itself enough to select the request configuration — see
 * [resolvedUseHostConfig].
 *
 * Amounts use the transaction currency's smallest unit.
 */
data class SignatureConfig(
    /**
     * Whether the whole signature configuration comes from the Tapro host (default: true).
     * - true: the host signature configuration is used and [entryLocation]/[threshold] are ignored
     * - false: the request configuration is used and the host configuration is never read;
     *   [entryLocation] must then be provided explicitly (use [SignatureEntryLocation.NONE]
     *   to explicitly disable signature capture)
     */
    val useHostConfig: Boolean = true,
    /**
     * Where the signature is captured for this transaction.
     *
     * Required when the request configuration is used. [SignatureEntryLocation.NONE] explicitly
     * disables signature capture and cannot be combined with a [threshold].
     */
    val entryLocation: SignatureEntryLocation? = null,
    /**
     * Amount above which a signature is captured, in the smallest currency unit (cents).
     *
     * The comparison is **strictly greater than**: with `threshold = 5000` ($50.00) a $50.00
     * transaction captures **no** signature, while $50.01 does. `null` (the default) captures a
     * signature for every amount.
     *
     * Must be a non-negative integer, and must be `null` when [entryLocation] is
     * [SignatureEntryLocation.NONE].
     */
    val threshold: BigDecimal? = null,
) {
    /**
     * The configuration source actually applied to the transaction.
     *
     * An explicitly provided [entryLocation] always wins over [useHostConfig], so
     * `SignatureConfig(entryLocation = ON_SCREEN)` keeps the pre-1.0.8
     * `signatureEntryLocation` behaviour (override the host configuration for this
     * transaction) instead of being silently ignored because [useHostConfig] defaults to true.
     */
    val resolvedUseHostConfig: Boolean
        get() = useHostConfig && entryLocation == null

    companion object {
        /**
         * Uses the host signature configuration as a whole.
         */
        @JvmStatic
        fun useHostConfig(): SignatureConfig = SignatureConfig(useHostConfig = true)

        @JvmStatic
        fun onScreen(): SignatureConfig =
            SignatureConfig(useHostConfig = false, entryLocation = SignatureEntryLocation.ON_SCREEN)

        @JvmStatic
        fun onScreenAbove(threshold: BigDecimal): SignatureConfig =
            SignatureConfig(false, SignatureEntryLocation.ON_SCREEN, threshold)

        @JvmStatic
        fun onReceipt(): SignatureConfig =
            SignatureConfig(useHostConfig = false, entryLocation = SignatureEntryLocation.ON_RECEIPT)

        @JvmStatic
        fun onReceiptAbove(threshold: BigDecimal): SignatureConfig =
            SignatureConfig(false, SignatureEntryLocation.ON_RECEIPT, threshold)

        @JvmStatic
        fun none(): SignatureConfig =
            SignatureConfig(useHostConfig = false, entryLocation = SignatureEntryLocation.NONE)
    }
}
