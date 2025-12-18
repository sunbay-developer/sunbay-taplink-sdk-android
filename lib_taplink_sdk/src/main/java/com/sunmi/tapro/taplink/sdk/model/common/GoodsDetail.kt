package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Goods Detail Class
 * 
 * Used for detailed transaction goods information
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class GoodsDetail(
    /**
     * Goods ID (optional)
     * Unique identifier for goods/service
     */
    val goodsId: String? = null,
    
    /**
     * Goods name (optional)
     * Name or description of goods/service
     */
    val goodsName: String? = null,
    
    /**
     * Quantity (optional)
     * Number of units
     */
    val quantity: Int? = null,
    
    /**
     * Unit price (optional, unit: cents)
     * Price per unit, in smallest currency unit
     */
    val price: Int? = null
) {
    /**
     * Chained call: Set goods ID
     */
    fun setGoodsId(goodsId: String): GoodsDetail = copy(goodsId = goodsId)
    
    /**
     * Chained call: Set goods name
     */
    fun setGoodsName(goodsName: String): GoodsDetail = copy(goodsName = goodsName)
    
    /**
     * Chained call: Set quantity
     */
    fun setQuantity(quantity: Int): GoodsDetail = copy(quantity = quantity)
    
    /**
     * Chained call: Set unit price
     */
    fun setPrice(price: Int): GoodsDetail = copy(price = price)
}

