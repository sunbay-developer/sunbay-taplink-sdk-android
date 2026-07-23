package com.sunmi.tapro.taplink.sdk.model.common

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Payment method information class.
 *
 * Used to specify preferred payment method category, id and subId.
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentMethodInfo(
    /**
     * Payment method category (optional).
     * Values: CARD, EBT, QR-MPM, QR-CPM
     */
    @JsonProperty("category")
    val _category: String? = null,

    /**
     * Specific payment method ID (optional).
     * Must not be specified when category = CARD; the system will automatically identify the payment method.
     * Values: VISA, MASTERCARD, EBT, DISCOVER, AMEX, JCB, UNIONPAY, DANA, DINERSCLUB, WECHAT_PAY, ALIPAY
     */
    @JsonProperty("id")
    val _id: String? = null,

    /**
     * Payment method sub-ID (optional).
     * Must not be specified when category = CARD.
     * Can only be specified when category = EBT and id = EBT; in that case valid values are SNAP, VOUCHER, BENEFIT.
     */
    @JsonProperty("subId")
    val _subId: String? = null
) {
    /**
     * Payment method category (enum type).
     */
    @get:JsonIgnore
    @delegate:Transient
    val category: PaymentCategory? by lazy {
        PaymentCategory.fromString(_category)
    }

    /**
     * Payment method ID (enum type).
     * Must not be specified when category = CARD; the system will automatically identify the payment method.
     */
    @get:JsonIgnore
    @delegate:Transient
    val id: PaymentMethodId? by lazy {
        PaymentMethodId.fromString(_id)
    }

    /**
     * Payment method sub-ID (enum type).
     * Must not be specified when category = CARD.
     * Can only be specified when category = EBT and id = EBT; valid values are SNAP, VOUCHER, BENEFIT.
     */
    @get:JsonIgnore
    @delegate:Transient
    val subId: PaymentMethodSubId? by lazy {
        PaymentMethodSubId.fromString(_subId)
    }

    /**
     * Constructor: using enum types.
     */
    constructor(
        category: PaymentCategory? = null,
        id: PaymentMethodId? = null,
        subId: PaymentMethodSubId? = null
    ) : this(
        _category = category?.toApiString(),
        _id = id?.toApiString(),
        _subId = subId?.toApiString()
    )

    /**
     * Sets the payment method category (string).
     *
     * @param category the category string
     * @return the updated PaymentMethodInfo instance for method chaining
     */
    fun setCategory(category: String): PaymentMethodInfo = copy(_category = category)

    /**
     * Sets the payment method category (enum).
     *
     * @param category the category enum
     * @return the updated PaymentMethodInfo instance for method chaining
     */
    fun setCategory(category: PaymentCategory): PaymentMethodInfo = copy(_category = category.toApiString())

    /**
     * Sets the payment method ID (string).
     * Must not be specified when category = CARD; the system will automatically identify the payment method.
     *
     * @param id the ID string
     * @return the updated PaymentMethodInfo instance for method chaining
     */
    fun setId(id: String): PaymentMethodInfo = copy(_id = id)

    /**
     * Sets the payment method ID (enum).
     * Must not be specified when category = CARD; the system will automatically identify the payment method.
     *
     * @param id the ID enum
     * @return the updated PaymentMethodInfo instance for method chaining
     */
    fun setId(id: PaymentMethodId): PaymentMethodInfo = copy(_id = id.toApiString())

    /**
     * Sets the payment method sub-ID (string).
     * Must not be specified when category = CARD.
     * Can only be specified when category = EBT and id = EBT; valid values are SNAP, VOUCHER, BENEFIT.
     *
     * @param subId the sub-ID string
     * @return the updated PaymentMethodInfo instance for method chaining
     */
    fun setSubId(subId: String): PaymentMethodInfo = copy(_subId = subId)

    /**
     * Sets the payment method sub-ID (enum).
     * Must not be specified when category = CARD.
     * Can only be specified when category = EBT and id = EBT; valid values are SNAP, VOUCHER, BENEFIT.
     *
     * @param subId the sub-ID enum
     * @return the updated PaymentMethodInfo instance for method chaining
     */
    fun setSubId(subId: PaymentMethodSubId): PaymentMethodInfo = copy(_subId = subId.toApiString())

    /**
     * Gets the category string for API transmission.
     */
    @JsonIgnore
    fun getCategoryString(): String? = _category

    /**
     * Gets the ID string for API transmission.
     */
    @JsonIgnore
    fun getIdString(): String? = _id

    /**
     * Gets the sub-ID string for API transmission.
     */
    @JsonIgnore
    fun getSubIdString(): String? = _subId
}
