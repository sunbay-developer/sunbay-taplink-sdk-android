package com.sunmi.tapro.taplink.sdk.model.common

import com.google.gson.annotations.SerializedName

/**
 * 支付方式信息类
 * 
 * 用于指定首选支付方式类别和 ID
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class PaymentMethodInfo(
    /**
     * 支付方式类别（可选）
     * 值：CARD, CARD-CREDIT, CARD-DEBIT, QR-MPM, QR-CPM, EBT, CASH
     */
    @SerializedName("category")
    private val _category: String? = null,
    
    /**
     * 具体支付方式 ID（可选）
     * 值：WECHAT, ALIPAY, VOUCHER, SNAP, WITHDRAW, BENEFIT 等
     */
    @SerializedName("id")
    private val _id: String? = null
) {
    /**
     * 支付方式类别（枚举类型）
     */
    @delegate:Transient
    val category: PaymentCategory? by lazy {
        PaymentCategory.fromString(_category)
    }
    
    /**
     * 支付方式ID（枚举类型）
     */
    @delegate:Transient
    val id: PaymentMethodId? by lazy {
        PaymentMethodId.fromString(_id)
    }
    
    /**
     * 构造函数：使用枚举类型
     */
    constructor(category: PaymentCategory?, id: PaymentMethodId? = null) : this(
        _category = category?.toApiString(),
        _id = id?.toApiString()
    )
    
    /**
     * 链式调用：设置支付方式类别（字符串）
     */
    fun setCategory(category: String): PaymentMethodInfo = copy(_category = category)
    
    /**
     * 链式调用：设置支付方式类别（枚举）
     */
    fun setCategory(category: PaymentCategory): PaymentMethodInfo = copy(_category = category.toApiString())
    
    /**
     * 链式调用：设置支付方式 ID（字符串）
     */
    fun setId(id: String): PaymentMethodInfo = copy(_id = id)
    
    /**
     * 链式调用：设置支付方式 ID（枚举）
     */
    fun setId(id: PaymentMethodId): PaymentMethodInfo = copy(_id = id.toApiString())
    
    /**
     * 获取 API 传输用的类别字符串
     */
    fun getCategoryString(): String? = _category
    
    /**
     * 获取 API 传输用的ID字符串
     */
    fun getIdString(): String? = _id
}

