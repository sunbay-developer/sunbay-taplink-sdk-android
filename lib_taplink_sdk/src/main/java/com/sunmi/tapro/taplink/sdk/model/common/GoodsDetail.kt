package com.sunmi.tapro.taplink.sdk.model.common

/**
 * Goods detail class.
 * 
 * Used for itemized transaction goods details.
 * 
 * Field setters update this instance and return it for chaining. Use [copy] for
 * independent top-level values, and finish updates before request submission.
 * Serialize writes and do not mutate instances used as hash collection keys.
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class GoodsDetail(
    /**
     * Goods ID (optional).
     * Unique identifier for the goods/service.
     */
    goodsId: String? = null,
    
    /**
     * Goods name (optional).
     * Name or description of the goods/service.
     */
    goodsName: String? = null,
    
    /**
     * Quantity (optional).
     * Number of units.
     */
    quantity: Int? = null,
    
    /**
     * Unit price (optional, unit: cents).
     * Price per unit in smallest currency unit.
     */
    price: Int? = null
) {
    var goodsId: String? = goodsId
        private set

    var goodsName: String? = goodsName
        private set

    var quantity: Int? = quantity
        private set

    var price: Int? = price
        private set

    /**
     * Sets the goods ID.
     *
     * @param goodsId the goods ID
     * @return this GoodsDetail instance for method chaining
     */
    fun setGoodsId(goodsId: String): GoodsDetail = apply { this.goodsId = goodsId }
    
    /**
     * Sets the goods name.
     *
     * @param goodsName the goods name
     * @return this GoodsDetail instance for method chaining
     */
    fun setGoodsName(goodsName: String): GoodsDetail = apply { this.goodsName = goodsName }
    
    /**
     * Sets the quantity.
     *
     * @param quantity the quantity
     * @return this GoodsDetail instance for method chaining
     */
    fun setQuantity(quantity: Int): GoodsDetail = apply { this.quantity = quantity }
    
    /**
     * Sets the unit price.
     *
     * @param price the unit price
     * @return this GoodsDetail instance for method chaining
     */
    fun setPrice(price: Int): GoodsDetail = apply { this.price = price }

    /** Creates an independent shallow copy; setters mutate this instance. */
    fun copy(
        goodsId: String? = this.goodsId,
        goodsName: String? = this.goodsName,
        quantity: Int? = this.quantity,
        price: Int? = this.price
    ): GoodsDetail = GoodsDetail(
        goodsId = goodsId,
        goodsName = goodsName,
        quantity = quantity,
        price = price
    )

    operator fun component1(): String? = goodsId
    operator fun component2(): String? = goodsName
    operator fun component3(): Int? = quantity
    operator fun component4(): Int? = price

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoodsDetail) return false
        return goodsId == other.goodsId &&
            goodsName == other.goodsName &&
            quantity == other.quantity &&
            price == other.price
    }

    override fun hashCode(): Int {
        var result = (goodsId?.hashCode() ?: 0)
        result = 31 * result + (goodsName?.hashCode() ?: 0)
        result = 31 * result + (quantity?.hashCode() ?: 0)
        result = 31 * result + (price?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "GoodsDetail(goodsId=$goodsId, goodsName=$goodsName, quantity=$quantity, price=$price)"

}

