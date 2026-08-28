package com.sunmi.tapro.taplink.communication.enums

import org.junit.Test
import org.junit.Assert.*

/**
 * Verifies the numeric error code surface and its category helpers.
 */
class InnerErrorCodeTest {

    @Test
    fun testErrorCodeProperties() {
        val success = InnerErrorCode.Success
        assertEquals("100", success.code)
        assertNotNull(success.description)
    }

    @Test
    fun testFromCodeMethod() {
        val known = InnerErrorCode.fromCode("213")
        assertEquals("213", known.code)

        val unknown = InnerErrorCode.fromCode("X99")
        assertTrue(unknown is InnerErrorCode.Unknown)
        assertEquals("X99", unknown.code)
    }

    @Test
    fun testErrorCodeCategories() {
        assertTrue(InnerErrorCode.isInitializationError("201"))
        assertFalse(InnerErrorCode.isInitializationError("211"))

        assertTrue(InnerErrorCode.isConnectionStateError("213"))
        assertFalse(InnerErrorCode.isConnectionStateError("201"))

        assertTrue(InnerErrorCode.isLanModeConnectionError("241"))
        assertFalse(InnerErrorCode.isLanModeConnectionError("251"))

        assertTrue(InnerErrorCode.isUsbModeConnectionError("251"))
        assertFalse(InnerErrorCode.isUsbModeConnectionError("241"))

        assertTrue(InnerErrorCode.isTransactionProcessingError("306"))
        assertFalse(InnerErrorCode.isTransactionProcessingError("213"))
    }

    /**
     * A timeout (306) or an ambiguous decline (308) must never be blindly retried: the
     * transaction may already have been approved on the terminal.
     */
    @Test
    fun testTimeoutRequiresQueryBeforeRetry() {
        assertTrue(InnerErrorCode.needsQueryBeforeRetry("306"))
        assertTrue(InnerErrorCode.needsQueryBeforeRetry("308"))
        assertFalse(InnerErrorCode.needsQueryBeforeRetry("301"))
    }

    @Test
    fun testGetAllErrorCodes() {
        val allErrorCodes = InnerErrorCode.getAllErrorCodes()
        assertTrue(allErrorCodes.isNotEmpty())
        assertTrue(allErrorCodes.contains(InnerErrorCode.Success))
    }
}
