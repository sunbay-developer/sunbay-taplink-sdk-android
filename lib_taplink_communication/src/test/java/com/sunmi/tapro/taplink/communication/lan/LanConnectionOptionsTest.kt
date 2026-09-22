package com.sunmi.tapro.taplink.communication.lan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanConnectionOptionsTest {

    @Test
    fun disabledReconnectPolicyDisablesFallbackAndMonitoring() {
        val options = LanConnectionOptions(
            allowServiceDiscoveryFallback = false,
            enableServiceMonitoring = false
        )

        assertFalse(options.allowServiceDiscoveryFallback)
        assertFalse(options.enableServiceMonitoring)
    }

    @Test
    fun defaultOptionsKeepExistingLanRecoveryBehavior() {
        val options = LanConnectionOptions()

        assertTrue(options.allowServiceDiscoveryFallback)
        assertTrue(options.enableServiceMonitoring)
    }
}
