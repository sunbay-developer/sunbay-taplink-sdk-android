package com.sunmi.tapro.taplink.communication.lan.discovery

import android.net.nsd.NsdManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDiscoveryRetryPolicyTest {

    @Test
    fun `only already-active failures are retried`() {
        assertTrue(
            ServiceDiscoveryManager.shouldRetryResolve(
                NsdManager.FAILURE_ALREADY_ACTIVE,
                retryAttempt = 1
            )
        )
        assertFalse(
            ServiceDiscoveryManager.shouldRetryResolve(
                NsdManager.FAILURE_INTERNAL_ERROR,
                retryAttempt = 1
            )
        )
    }

    @Test
    fun `already-active retries are bounded`() {
        assertTrue(ServiceDiscoveryManager.shouldRetryResolve(NsdManager.FAILURE_ALREADY_ACTIVE, 4))
        assertFalse(ServiceDiscoveryManager.shouldRetryResolve(NsdManager.FAILURE_ALREADY_ACTIVE, 5))
    }

    @Test
    fun `retry delay backs off without delaying normal resolve`() {
        assertEquals(250L, ServiceDiscoveryManager.resolveRetryDelayMs(1))
        assertEquals(500L, ServiceDiscoveryManager.resolveRetryDelayMs(2))
        assertEquals(1_000L, ServiceDiscoveryManager.resolveRetryDelayMs(3))
        assertEquals(2_000L, ServiceDiscoveryManager.resolveRetryDelayMs(4))
    }
}
