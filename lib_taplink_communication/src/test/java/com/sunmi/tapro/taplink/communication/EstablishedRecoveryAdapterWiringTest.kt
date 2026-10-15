package com.sunmi.tapro.taplink.communication

import android.content.Context
import android.content.ContextWrapper
import com.sunmi.tapro.taplink.communication.interfaces.recovery.RecoveryPrereqResult
import com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity
import com.sunmi.tapro.taplink.communication.lan.LanClientKernel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adapter-level recovery wiring tests for the kernel identity/prerequisite hooks.
 * SPEC-ESTABLISHED-RECOVERY-001 §AC1/§AC8/§AC12/§AC14 / TC-ER-001, TC-ER-008, TC-ER-012, TC-ER-014.
 *
 * These assert the per-adapter overrides fail CLOSED (never auto-attach to another terminal) and
 * that LAN recovery needs no USB permission. Hardware/Host end-to-end behaviour (USB serial read on
 * a real device, SUB_SCREEN readiness, AOA re-enumeration) remains a documented blocker (B1–B5) and
 * is NOT asserted here.
 */
class EstablishedRecoveryAdapterWiringTest {

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
    }

    private fun lanKernel(): LanClientKernel = LanClientKernel("app", "secret", TestContext())

    private fun setSerial(kernel: LanClientKernel, serial: String?) {
        LanClientKernel::class.java.getDeclaredField("targetServiceSerial")
            .apply { isAccessible = true }.set(kernel, serial)
    }

    /** §AC1/§AC14 / TC-ER-001, TC-ER-014: LAN recovery is possible with no USB permission. */
    @Test
    fun lanPrerequisiteIsAvailableWithoutUsbPermission() {
        val kernel = lanKernel()
        assertEquals(RecoveryPrereqResult.Available, kernel.evaluateRecoveryPrerequisite())
    }

    /** §AC8 / TC-ER-008: LAN recovery succeeds only when the rebuilt link carries the SAME serial. */
    @Test
    fun lanIdentityValidatesSameSerialOnly() = runBlocking {
        val kernel = lanKernel()
        setSerial(kernel, "SERIAL-A")
        assertEquals(TerminalIdentity("SERIAL-A"), kernel.captureTerminalIdentity())
        // Same serial -> validated.
        assertTrue(kernel.validateRecoveredIdentity(TerminalIdentity("SERIAL-A")))
        // Different serial -> refuse (never auto-attach to another terminal).
        assertFalse(kernel.validateRecoveredIdentity(TerminalIdentity("SERIAL-B")))
    }

    /** §AC8 / TC-ER-008: no reliable anchor -> identity fails closed, no capture. */
    @Test
    fun lanMissingSerialFailsClosed() = runBlocking {
        val kernel = lanKernel()
        setSerial(kernel, null)
        assertEquals(null, kernel.captureTerminalIdentity())
        assertFalse(kernel.validateRecoveredIdentity(TerminalIdentity(null)))
        assertFalse(kernel.validateRecoveredIdentity(TerminalIdentity("SERIAL-A")))
    }
}
