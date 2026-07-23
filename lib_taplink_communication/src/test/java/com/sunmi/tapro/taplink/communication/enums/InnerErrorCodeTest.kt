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
        // Test custom description
        val customError = InnerErrorCode.Unknown(
            "CUSTOM01",
            "Custom error description"
        )
        assertEquals("CUSTOM01", customError.code)
        assertEquals("Custom error description", customError.description)

        // Test default description
        val defaultError = InnerErrorCode.Unknown("DEFAULT01")
        assertEquals("DEFAULT01", defaultError.code)
        assertNotNull(defaultError.description)
    }

    @Test
    fun testErrorCodeCategories() {
        // Test T-series error codes
        assertTrue(InnerErrorCode.isTSeriesError("T01"))
        assertFalse(InnerErrorCode.isTSeriesError("C20"))

        // Test connection errors
        assertTrue(InnerErrorCode.isConnectionError("T01"))
        assertTrue(InnerErrorCode.isConnectionError("T14"))
        assertFalse(InnerErrorCode.isConnectionError("T15"))

        // Test parameter validation errors
        assertTrue(InnerErrorCode.isParameterValidationError("T15"))
        assertTrue(InnerErrorCode.isParameterValidationError("T19"))
        assertFalse(InnerErrorCode.isParameterValidationError("T20"))

        // Test data transmission errors
        assertTrue(InnerErrorCode.isDataTransmissionError("T20"))
        assertTrue(InnerErrorCode.isDataTransmissionError("T25"))
        assertFalse(InnerErrorCode.isDataTransmissionError("T27"))

        // Test transaction processing errors
        assertTrue(InnerErrorCode.isTransactionProcessingError("T27"))
        assertTrue(InnerErrorCode.isTransactionProcessingError("T31"))
        assertFalse(InnerErrorCode.isTransactionProcessingError("T34"))

        // Test system timeout errors
        assertTrue(InnerErrorCode.isSystemTimeoutError("T34"))
        assertFalse(InnerErrorCode.isSystemTimeoutError("T31"))
    }

    @Test
    fun testGetAllErrorCodes() {
        val allErrorCodes = InnerErrorCode.getAllErrorCodes()
        assertTrue(allErrorCodes.isNotEmpty())
        assertTrue(allErrorCodes.contains(InnerErrorCode.Success))
        assertTrue(allErrorCodes.contains(InnerErrorCode.C20))
    }
}