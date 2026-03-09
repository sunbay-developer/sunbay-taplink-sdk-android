package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Payment method ID enumeration.
 *
 * Defines all supported specific payment method IDs.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
enum class PaymentMethodId(val value: String) {
    /**
     * Visa.
     */
    VISA("VISA"),

    /**
     * Mastercard.
     */
    MASTERCARD("MASTERCARD"),

    /**
     * Electronic Benefit Transfer.
     */
    EBT("EBT"),

    /**
     * Discover.
     */
    DISCOVER("DISCOVER"),

    /**
     * American Express.
     */
    AMEX("AMEX"),

    /**
     * JCB.
     */
    JCB("JCB"),

    /**
     * UnionPay.
     */
    UNIONPAY("UNIONPAY"),

    /**
     * DANA.
     */
    DANA("DANA"),

    /**
     * Diners Club.
     */
    DINERSCLUB("DINERSCLUB"),

    /**
     * WeChat Pay.
     */
    WECHAT_PAY("WECHAT_PAY"),

    /**
     * Alipay.
     */
    ALIPAY("ALIPAY");

    companion object {
        /**
         * Converts from string to enum.
         *
         * @param value the string value
         * @return the corresponding enum value, or null if not recognized
         */
        fun fromString(value: String?): PaymentMethodId? {
            if (value.isNullOrBlank()) return null

            return when (value.uppercase().replace("-", "_")) {
                "VISA" -> VISA
                "MASTERCARD" -> MASTERCARD
                "EBT" -> EBT
                "DISCOVER" -> DISCOVER
                "AMEX" -> AMEX
                "JCB" -> JCB
                "UNIONPAY" -> UNIONPAY
                "DANA" -> DANA
                "DINERSCLUB" -> DINERSCLUB
                "WECHAT_PAY" -> WECHAT_PAY
                "ALIPAY" -> ALIPAY
                else -> null
            }
        }
    }

    /**
     * Converts to string (for API transmission).
     */
    fun toApiString(): String = value
}
