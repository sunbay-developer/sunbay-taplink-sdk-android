package com.sunmi.tapro.taplink.sdk

import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthAmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthRequest
import org.junit.Test
import java.math.BigDecimal

/**
 * TaplinkClient集成测试
 * 
 * 测试新的类型安全请求对象与TaplinkClient的集成
 *
 * @author TaPro Team
 * @since 2025-01-XX
 */
class TaplinkClientIntegrationTest {

    /**
     * 测试SaleRequest的使用
     */
    @Test
    fun testSaleRequestUsage() {
        // 测试SaleRequest的构建
        val saleRequest = SaleRequest.builder()
            .setReferenceOrderId("ORDER123")
            .setTransactionRequestId("TXN123")
            .setAmount(AmountInfo(
                orderAmount = BigDecimal("10.00"),
                pricingCurrency = "USD"
            ))
            .setDescription("Test Sale")
            .setRequestTimeout(30L)
            .build()

        // 验证字段设置正确
        assert(saleRequest.referenceOrderId == "ORDER123")
        assert(saleRequest.transactionRequestId == "TXN123")
        assert(saleRequest.amount.orderAmount == BigDecimal("10.00"))
        assert(saleRequest.description == "Test Sale")
        assert(saleRequest.requestTimeout == 30L)
    }

    /**
     * 测试AuthRequest的使用
     */
    @Test
    fun testAuthRequestUsage() {
        // 测试AuthRequest的构建
        val authRequest = AuthRequest.builder()
            .setReferenceOrderId("ORDER456")
            .setTransactionRequestId("TXN456")
            .setAmount(AuthAmountInfo(
                orderAmount = BigDecimal("20.00"),
                pricingCurrency = "USD"
            ))
            .setDescription("Test Auth")
            .setRequestTimeout(60L)
            .build()

        // 验证字段设置正确
        assert(authRequest.referenceOrderId == "ORDER456")
        assert(authRequest.transactionRequestId == "TXN456")
        assert(authRequest.amount.orderAmount == BigDecimal("20.00"))
        assert(authRequest.description == "Test Auth")
        assert(authRequest.requestTimeout == 60L)
    }
}