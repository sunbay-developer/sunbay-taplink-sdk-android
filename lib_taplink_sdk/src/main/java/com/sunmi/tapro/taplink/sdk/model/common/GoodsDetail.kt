package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Goods detail class.
 * 
 * Used for itemized transaction goods details.
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class GoodsDetail(
    /**
     * Goods ID (optional).
     * Unique identifier for the goods/service.
     */
    val goodsId: String? = null,
    
    /**
     * Goods name (optional).
     * Name or description of the goods/service.
     */
    val goodsName: String? = null,
    
    /**
     * Quantity (optional).
     * Number of units.
     */
    val quantity: Int? = null,
    
    /**
     * Unit price (optional, unit: cents).
     * Price per unit in smallest currency unit.
     */
    val price: Int? = null
) {
    /**
     * Sets the goods ID.
     *
     * @param goodsId the goods ID
     * @return the updated GoodsDetail instance for method chaining
     */
    fun setGoodsId(goodsId: String): GoodsDetail = copy(goodsId = goodsId)
    
    /**
     * Sets the goods name.
     *
     * @param goodsName the goods name
     * @return the updated GoodsDetail instance for method chaining
     */
    fun setGoodsName(goodsName: String): GoodsDetail = copy(goodsName = goodsName)
    
    /**
     * Sets the quantity.
     *
     * @param quantity the quantity
     * @return the updated GoodsDetail instance for method chaining
     */
    fun setQuantity(quantity: Int): GoodsDetail = copy(quantity = quantity)
    
    /**
     * Sets the unit price.
     *
     * @param price the unit price
     * @return the updated GoodsDetail instance for method chaining
     */
    fun setPrice(price: Int): GoodsDetail = copy(price = price)
}

