package com.sunmi.tapro.taplink.service.enums

import org.junit.Test
import org.junit.Assert.*

/**
 * 错误码测试类
 * 
 * 验证错误码的基本功能和本地化支持
 */
class InnerErrorCodeTest {

    @Test
    fun testErrorCodeProperties() {
        // 测试预定义错误码
        val errorC20 = InnerErrorCode.C20
        assertEquals("C20", errorC20.code)
        assertNotNull(errorC20.description)
    }

    @Test
    fun testFromCodeMethod() {
        // 测试已知错误码
        val errorFromCode = InnerErrorCode.fromCode("C20")
        assertEquals("C20", errorFromCode.code)

        // 测试未知错误码
        val unknownError = InnerErrorCode.fromCode("X99")
        assertTrue(unknownError is InnerErrorCode.Unknown)
        assertEquals("X99", unknownError.code)

        // 测试空错误码
        val emptyError = InnerErrorCode.fromCode("")
        assertTrue(emptyError is InnerErrorCode.Unknown)
        assertEquals("", emptyError.code)

        // 测试null错误码
        val nullError = InnerErrorCode.fromCode(null)
        assertTrue(nullError is InnerErrorCode.Unknown)
        assertEquals("", nullError.code)
    }

    @Test
    fun testUnknownErrorCode() {
        // 测试自定义描述和解决方案
        val customError = InnerErrorCode.Unknown(
            "CUSTOM01",
            "Custom error description",
        )
        assertEquals("CUSTOM01", customError.code)
        assertEquals("Custom error description", customError.description)

        // 测试默认描述和解决方案
        val defaultError = InnerErrorCode.Unknown("DEFAULT01")
        assertEquals("DEFAULT01", defaultError.code)
        assertNotNull(defaultError.description)
    }

    @Test
    fun testErrorCodeCategories() {
        // 测试客户端错误
        assertTrue(InnerErrorCode.isClientError("C20"))
        assertFalse(InnerErrorCode.isClientError("S01"))

        // 测试系统错误
        assertTrue(InnerErrorCode.isSystemError("E01"))
        assertFalse(InnerErrorCode.isSystemError("C20"))

        // 测试安全错误
        assertTrue(InnerErrorCode.isSecurityError("S01"))
        assertFalse(InnerErrorCode.isSecurityError("C20"))

        // 测试商户配置错误
        assertTrue(InnerErrorCode.isMerchantConfigError("M01"))
        assertFalse(InnerErrorCode.isMerchantConfigError("C20"))

        // 测试支付渠道错误
        assertTrue(InnerErrorCode.isPaymentChannelError("P01"))
        assertFalse(InnerErrorCode.isPaymentChannelError("C20"))

        // 测试风控错误
        assertTrue(InnerErrorCode.isRiskControlError("R01"))
        assertFalse(InnerErrorCode.isRiskControlError("C20"))
    }

    @Test
    fun testGetAllErrorCodes() {
        val allErrorCodes = InnerErrorCode.getAllErrorCodes()
        assertTrue(allErrorCodes.isNotEmpty())
        assertTrue(allErrorCodes.contains(InnerErrorCode.Success))
        assertTrue(allErrorCodes.contains(InnerErrorCode.C20))
        assertTrue(allErrorCodes.contains(InnerErrorCode.Exception))
    }
}