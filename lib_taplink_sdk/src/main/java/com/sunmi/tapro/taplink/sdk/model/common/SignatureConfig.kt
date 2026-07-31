package com.sunmi.tapro.taplink.sdk.model.common

import com.sunmi.tapro.taplink.sdk.enums.SignatureEntryLocation
import java.math.BigDecimal

/**
 * Per-transaction signature settings managed by the POS.
 *
 * Amounts use the transaction currency's smallest unit.
 */
data class SignatureConfig(
    val entryLocation: SignatureEntryLocation,
    val threshold: BigDecimal? = null,
) {
    companion object {
        @JvmStatic
        fun onScreen(): SignatureConfig = SignatureConfig(SignatureEntryLocation.ON_SCREEN)

        @JvmStatic
        fun onScreenAbove(threshold: BigDecimal): SignatureConfig =
            SignatureConfig(SignatureEntryLocation.ON_SCREEN, threshold)

        @JvmStatic
        fun onReceipt(): SignatureConfig = SignatureConfig(SignatureEntryLocation.ON_RECEIPT)

        @JvmStatic
        fun onReceiptAbove(threshold: BigDecimal): SignatureConfig =
            SignatureConfig(SignatureEntryLocation.ON_RECEIPT, threshold)

        @JvmStatic
        fun none(): SignatureConfig = SignatureConfig(SignatureEntryLocation.NONE)
    }
}
