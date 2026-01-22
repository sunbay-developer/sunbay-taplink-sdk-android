package com.sunmi.tapro.taplink.sdk.model.common

/**
 * 商品详情类
 * 
 * 用于明细交易的商品详情
 * 
 * @author TaPro Team
 * @since 2025-01-XX
 */
data class GoodsDetail(
    /**
     * 商品 ID（可选）
     * 商品/服务的唯一标识符
     */
    val goodsId: String? = null,
    
    /**
     * 商品名称（可选）
     * 商品/服务的名称或描述
     */
    val goodsName: String? = null,
    
    /**
     * 数量（可选）
     * 单位数
     */
    val quantity: Int? = null,
    
    /**
     * 单价（可选，单位：分）
     * 每单位价格，以最小货币单位计
     */
    val price: Int? = null
) {
    /**
     * 链式调用：设置商品 ID
     */
    fun setGoodsId(goodsId: String): GoodsDetail = copy(goodsId = goodsId)
    
    /**
     * 链式调用：设置商品名称
     */
    fun setGoodsName(goodsName: String): GoodsDetail = copy(goodsName = goodsName)
    
    /**
     * 链式调用：设置数量
     */
    fun setQuantity(quantity: Int): GoodsDetail = copy(quantity = quantity)
    
    /**
     * 链式调用：设置单价
     */
    fun setPrice(price: Int): GoodsDetail = copy(price = price)
}

