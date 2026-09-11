package com.sunmi.tapro.taplink.communication.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbStandardProtocolTest {

    @Test
    fun createAoaDeviceInfo_displaysModelAndSerialInAuthorizationName() {
        val info = UsbStandardProtocol.createAoaDeviceInfo("P3 MIX", "SN123456")

        assertEquals("P3 MIX:SN123456", info.model)
        assertEquals("SN123456", info.serial)
    }

    @Test
    fun createAoaDeviceInfo_trimsValuesAndFallsBackForMissingSerial() {
        val info = UsbStandardProtocol.createAoaDeviceInfo("  P3 MIX  ", "   ")

        assertEquals("P3 MIX", info.model)
        assertEquals("Taplink001", info.serial)
    }

    @Test
    fun createAoaDeviceInfo_usesExistingDefaultsWhenDeviceIdentityIsUnavailable() {
        val info = UsbStandardProtocol.createAoaDeviceInfo(null, null)

        assertEquals(UsbStandardProtocol.UsbStandardInfo(), info)
    }
}
