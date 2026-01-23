package com.sunmi.tapro.taplink.sdk.model.common

import com.google.gson.annotations.SerializedName

/**
 * Payment method information class.
 * 
 * Used to specify preferred payment method category and ID.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentMethodInfo(
    /**
     * Payment method category (optional).
     * Values: CARD, CARD-CREDIT, CARD-DEBIT, QR-MPM, QR-CPM, EBT, CASH
     */
    @SerializedName("category")
    private val _category: String? = null,
    
    /**
     * Specific payment method ID (optional).
     * Values: WECHAT, ALIPAY, VOUCHER, SNAP, WITHDRAW, BENEFIT, etc.
     */
    @SerializedName("id")
    private val _id: String? = null
) {
    /**
     * Payment method category (enum type).
     */
    @delegate:Transient
    val category: PaymentCategory? by lazy {
        PaymentCategory.fromString(_category)
    }
    
    /**
     * Payment method ID (enum type).
     */
    @delegate:Transient
    val id: PaymentMethodId? by lazy {
        PaymentMethodId.fromString(_id)
    }
    
    /**
     * Constructor: using enum types.
     */
    constructor(category: PaymentCategory?, id: PaymentMethodId? = null) : this(
        _category = category?.toApiString(),
        _id = id?.toApiString()
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
     *
     * @param id the ID string
     * @return the updated PaymentMethodInfo instance for method chaining
     */
    fun setId(id: String): PaymentMethodInfo = copy(_id = id)
    
    /**
     * Sets the payment method ID (enum).
     *
     * @param id the ID enum
     * @return the updated PaymentMethodInfo instance for method chaining
     */
    fun setId(id: PaymentMethodId): PaymentMethodInfo = copy(_id = id.toApiString())
    
    /**
     * Gets the category string for API transmission.
     */
    fun getCategoryString(): String? = _category
    
    /**
     * Gets the ID string for API transmission.
     */
    fun getIdString(): String? = _id
}

