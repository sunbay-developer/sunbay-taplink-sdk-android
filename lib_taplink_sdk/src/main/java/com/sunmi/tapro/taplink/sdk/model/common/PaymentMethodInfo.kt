package com.sunmi.tapro.taplink.sdk.model.common

import com.google.gson.annotations.SerializedName

/**
 * Payment Method Information Class
 * 
 * Used to specify preferred payment method category and ID
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentMethodInfo(
    /**
     * Payment method category (optional)
     * Values: CARD, CARD-CREDIT, CARD-DEBIT, QR-MPM, QR-CPM, EBT, CASH
     */
    @SerializedName("category")
    private val _category: String? = null,
    
    /**
     * Specific payment method ID (optional)
     * Values: WECHAT, ALIPAY, VOUCHER, SNAP, WITHDRAW, BENEFIT, etc.
     */
    @SerializedName("id")
    private val _id: String? = null
) {
    /**
     * Payment method category (enum type)
     */
    @delegate:Transient
    val category: PaymentCategory? by lazy {
        PaymentCategory.fromString(_category)
    }
    
    /**
     * Payment method ID (enum type)
     */
    @delegate:Transient
    val id: PaymentMethodId? by lazy {
        PaymentMethodId.fromString(_id)
    }
    
    /**
     * Constructor: Using enum types
     */
    constructor(category: PaymentCategory?, id: PaymentMethodId? = null) : this(
        _category = category?.toApiString(),
        _id = id?.toApiString()
    )
    
    /**
     * Chained call: Set payment method category (string)
     */
    fun setCategory(category: String): PaymentMethodInfo = copy(_category = category)
    
    /**
     * Chained call: Set payment method category (enum)
     */
    fun setCategory(category: PaymentCategory): PaymentMethodInfo = copy(_category = category.toApiString())
    
    /**
     * Chained call: Set payment method ID (string)
     */
    fun setId(id: String): PaymentMethodInfo = copy(_id = id)
    
    /**
     * Chained call: Set payment method ID (enum)
     */
    fun setId(id: PaymentMethodId): PaymentMethodInfo = copy(_id = id.toApiString())
    
    /**
     * Get category string for API transmission
     */
    fun getCategoryString(): String? = _category
    
    /**
     * Get ID string for API transmission
     */
    fun getIdString(): String? = _id
}

