package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Payment method information class.
 *
 * Used to specify preferred payment method category, id and subId.
 *
 * Field setters update this instance and return it for chaining. Use [copy] for
 * independent top-level values, and finish updates before request submission.
 * Serialize writes and do not mutate instances used as hash collection keys.
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class PaymentMethodInfo(
    /**
     * Payment method category (optional).
     * Values: CARD, EBT, QR-MPM, QR-CPM
     */
    _category: String? = null,

    /**
     * Specific payment method ID (optional).
     * Must not be specified when category = CARD; the system will automatically identify the payment method.
     * Values: VISA, MASTERCARD, EBT, DISCOVER, AMEX, JCB, UNIONPAY, DANA, DINERSCLUB, WECHAT_PAY, ALIPAY
     */
    _id: String? = null,

    /**
     * Payment method sub-ID (optional).
     * Must not be specified when category = CARD.
     * Can only be specified when category = EBT and id = EBT; in that case valid values are SNAP, VOUCHER, BENEFIT.
     */
    _subId: String? = null
) {
    var _category: String? = _category
        private set

    var _id: String? = _id
        private set

    var _subId: String? = _subId
        private set

    /**
     * Payment method category (enum type).
     */
    val category: PaymentCategory?
        get() = PaymentCategory.fromString(_category)

    /**
     * Payment method ID (enum type).
     * Must not be specified when category = CARD; the system will automatically identify the payment method.
     */
    val id: PaymentMethodId?
        get() = PaymentMethodId.fromString(_id)

    /**
     * Payment method sub-ID (enum type).
     * Must not be specified when category = CARD.
     * Can only be specified when category = EBT and id = EBT; valid values are SNAP, VOUCHER, BENEFIT.
     */
    val subId: PaymentMethodSubId?
        get() = PaymentMethodSubId.fromString(_subId)

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
     * @return this PaymentMethodInfo instance for method chaining
     */
    fun setCategory(category: String): PaymentMethodInfo = apply { this._category = category }

    /**
     * Sets the payment method category (enum).
     *
     * @param category the category enum
     * @return this PaymentMethodInfo instance for method chaining
     */
    fun setCategory(category: PaymentCategory): PaymentMethodInfo = apply { this._category = category.toApiString() }

    /**
     * Sets the payment method ID (string).
     * Must not be specified when category = CARD; the system will automatically identify the payment method.
     *
     * @param id the ID string
     * @return this PaymentMethodInfo instance for method chaining
     */
    fun setId(id: String): PaymentMethodInfo = apply { this._id = id }

    /**
     * Sets the payment method ID (enum).
     * Must not be specified when category = CARD; the system will automatically identify the payment method.
     *
     * @param id the ID enum
     * @return this PaymentMethodInfo instance for method chaining
     */
    fun setId(id: PaymentMethodId): PaymentMethodInfo = apply { this._id = id.toApiString() }

    /**
     * Sets the payment method sub-ID (string).
     * Must not be specified when category = CARD.
     * Can only be specified when category = EBT and id = EBT; valid values are SNAP, VOUCHER, BENEFIT.
     *
     * @param subId the sub-ID string
     * @return this PaymentMethodInfo instance for method chaining
     */
    fun setSubId(subId: String): PaymentMethodInfo = apply { this._subId = subId }

    /**
     * Sets the payment method sub-ID (enum).
     * Must not be specified when category = CARD.
     * Can only be specified when category = EBT and id = EBT; valid values are SNAP, VOUCHER, BENEFIT.
     *
     * @param subId the sub-ID enum
     * @return this PaymentMethodInfo instance for method chaining
     */
    fun setSubId(subId: PaymentMethodSubId): PaymentMethodInfo = apply { this._subId = subId.toApiString() }

    /**
     * Gets the category string for API transmission.
     */
    fun getCategoryString(): String? = _category

    /**
     * Gets the ID string for API transmission.
     */
    fun getIdString(): String? = _id

    /**
     * Gets the sub-ID string for API transmission.
     */
    fun getSubIdString(): String? = _subId

    /** Creates an independent shallow copy; setters mutate this instance. */
    fun copy(
        _category: String? = this._category,
        _id: String? = this._id,
        _subId: String? = this._subId
    ): PaymentMethodInfo = PaymentMethodInfo(
        _category = _category,
        _id = _id,
        _subId = _subId
    )

    operator fun component1(): String? = _category
    operator fun component2(): String? = _id
    operator fun component3(): String? = _subId

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaymentMethodInfo) return false
        return _category == other._category &&
            _id == other._id &&
            _subId == other._subId
    }

    override fun hashCode(): Int {
        var result = (_category?.hashCode() ?: 0)
        result = 31 * result + (_id?.hashCode() ?: 0)
        result = 31 * result + (_subId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "PaymentMethodInfo(_category=$_category, _id=$_id, _subId=$_subId)"

}
