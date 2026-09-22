package com.sunmi.tapro.taplink.sdk

import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.model.common.AmountInfo
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import com.sunmi.tapro.taplink.sdk.model.common.PaymentEvent
import com.sunmi.tapro.taplink.sdk.model.common.PaymentMethodInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.SaleRequest
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthAmountInfo
import com.sunmi.tapro.taplink.sdk.model.request.transaction.AuthRequest
import com.sunmi.tapro.taplink.sdk.manager.PaymentManager
import com.sunmi.tapro.taplink.sdk.protocol.ProtocolRequestBuilder
import com.sunmi.tapro.taplink.sdk.util.SignUtil
import com.sunmi.tapro.taplink.sdk.util.TaplinkJsonBridge
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

/**
 * TaplinkClient集成测试
 * 
 * 测试新的类型安全请求对象与TaplinkClient的集成
 *
 * @author Tapro Team
 * @since 2025-01-XX
 */
class TaplinkClientIntegrationTest {

    /** Signing failures must stop request construction instead of producing an empty signature. */
    @Test
    fun emptySigningKeyThrows() {
        assertThrows(SignUtil.SignException::class.java) {
            SignUtil.generateHMACSHA256("payload", "")
        }
    }

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

    @Test
    fun callbackTimeoutUsesRequestMillisecondsWithoutConversion() {
        assertEquals(30L, PaymentManager.resolveRequestTimeoutMillis(30L, 180))
    }

    @Test
    fun callbackTimeoutUsesConfiguredDefaultInMilliseconds() {
        assertEquals(180_000L, PaymentManager.resolveRequestTimeoutMillis(null, 180))
    }

    @Test
    fun callbackTimeoutUsesThe180000MillisecondDefaultWhenConfiguredValuesAreNonPositive() {
        assertEquals(180_000L, PaymentManager.resolveRequestTimeoutMillis(0L, 0))
    }

    @Test
    fun taplinkConfigDefaultTimeoutIs180Seconds() {
        val config = TaplinkConfig(appId = "app", secretKey = "secret")

        assertEquals(180, config.timeout)
    }

    @Test
    fun signedRequestUsesTheEffectiveTimeoutInMilliseconds() {
        val request = SaleRequest.builder()
            .setReferenceOrderId("ORDER123")
            .setTransactionRequestId("TXN123")
            .setAmount(AmountInfo(BigDecimal("1000"), "USD"))
            .setRequestTimeout(30_000L)
            .build()

        val basicRequest = ProtocolRequestBuilder.convertToBasicRequest(
            request = request,
            version = "1.0.10",
            appid = "app",
            secretKey = "secret",
            requestTimeoutMillis = PaymentManager.resolveRequestTimeoutMillis(30_000L, 180)
        )

        assertEquals(30_000L, JSONObject(basicRequest.bizData).getLong("requestTimeout"))
    }

    @Test
    fun dependencyFreeJsonKeepsRequestWireContract() {
        val request = SaleRequest.builder()
            .setReferenceOrderId("ORDER123")
            .setTransactionRequestId("TXN123")
            .setAmount(AmountInfo(BigDecimal("1000"), "USD"))
            .setPaymentMethod(PaymentMethodInfo(category = PaymentCategory.CARD))
            .build()
        val basicRequest = ProtocolRequestBuilder.convertToBasicRequest(
            request = request,
            version = "1.0.9.3",
            appid = "APP123",
            secretKey = "secret"
        )
        val bizData = JSONObject(basicRequest.bizData)

        assertEquals("APP123", bizData.getString("appId"))
        assertEquals("1000", bizData.getJSONObject("amount").get("orderAmount").toString())
        assertEquals("CARD", bizData.getJSONObject("paymentMethod").getString("category"))
        assertFalse(bizData.getJSONObject("paymentMethod").has("_category"))
        assertFalse(bizData.has("description"))
    }

    @Test
    fun responseReaderAcceptsLegacyObjectAndCurrentStringBizData() {
        val payload = """{"code":"100","transactionStatus":"SUCCESS"}"""
        val current = responseEnvelope(JSONObject.quote(payload))
        val legacy = responseEnvelope(payload)

        assertEquals(payload, TaplinkJsonBridge.parseBasicResponse(current).bizData)
        assertEquals(payload, TaplinkJsonBridge.parseBasicResponse(legacy).bizData)
        assertTrue(TaplinkJsonBridge.parsePaymentResult(payload).isSuccess())
    }

    @Test
    fun basicResponseWriterKeepsFlatEventFields() {
        val response = com.sunmi.tapro.taplink.sdk.model.base.BasicResponse(
            appSign = "sign",
            version = "1.0.9.3",
            timeStamp = "123",
            action = "SALE",
            event = PaymentEvent.Completed,
            bizData = "{}",
            traceId = "trace"
        )
        val json = JSONObject(TaplinkJsonBridge.toJsonString(response))

        assertEquals("COMPLETED", json.getString("eventCode"))
        assertEquals("Transaction completed", json.getString("eventMsg"))
        assertFalse(json.has("event"))
    }

    private fun responseEnvelope(bizData: String): String =
        """{"appSign":"sign","version":"1.0","timeStamp":"123","action":"SALE","eventCode":"COMPLETED","eventMsg":"done","bizData":$bizData,"traceId":"trace"}"""
}
