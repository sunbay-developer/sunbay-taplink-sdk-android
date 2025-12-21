package com.sunmi.tapro.taplink.communication.enums

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
        // Test predefined error codes
        val errorC20 = InnerErrorCode.C20
        assertEquals("C20", errorC20.code)
        assertNotNull(errorC20.description)
    }

    @Test
    fun testFromCodeMethod() {
        // Test known error codes
        val errorFromCode = InnerErrorCode.fromCode("C20")
        assertEquals("C20", errorFromCode.code)

        // Test unknown error codes
        val unknownError = InnerErrorCode.fromCode("X99")
        assertTrue(unknownError is InnerErrorCode.Unknown)
        assertEquals("X99", unknownError.code)

        // Test empty error code
        val emptyError = InnerErrorCode.fromCode("")
        assertTrue(emptyError is InnerErrorCode.Unknown)
        assertEquals("", emptyError.code)

        // Test null error code
        val nullError = InnerErrorCode.fromCode(null)
        assertTrue(nullError is InnerErrorCode.Unknown)
        assertEquals("", nullError.code)
    }

    @Test
    fun testUnknownErrorCode() {
        // Test custom description and solution
        val customError = InnerErrorCode.Unknown(
            "CUSTOM01",
            "Custom error description",
        )
        assertEquals("CUSTOM01", customError.code)
        assertEquals("Custom error description", customError.description)

        // Test default description and solution
        val defaultError = InnerErrorCode.Unknown("DEFAULT01")
        assertEquals("DEFAULT01", defaultError.code)
        assertNotNull(defaultError.description)
    }

    @Test
    fun testErrorCodeCategories() {
        // Test client errors
        assertTrue(InnerErrorCode.isClientError("C20"))
        assertFalse(InnerErrorCode.isClientError("S01"))

        // Test system errors
        assertTrue(InnerErrorCode.isSystemError("E01"))
        assertFalse(InnerErrorCode.isSystemError("C20"))

        // Test security errors
        assertTrue(InnerErrorCode.isSecurityError("S01"))
        assertFalse(InnerErrorCode.isSecurityError("C20"))

        // Test merchant configuration errors
        assertTrue(InnerErrorCode.isMerchantConfigError("M01"))
        assertFalse(InnerErrorCode.isMerchantConfigError("C20"))

        // Test payment channel errors
        assertTrue(InnerErrorCode.isPaymentChannelError("P01"))
        assertFalse(InnerErrorCode.isPaymentChannelError("C20"))

        // Test risk control errors
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