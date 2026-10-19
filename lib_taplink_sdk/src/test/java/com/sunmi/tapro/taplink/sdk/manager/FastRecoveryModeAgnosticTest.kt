package com.sunmi.tapro.taplink.sdk.manager

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Guards that OPT-R1 fast recovery is mode-agnostic after removing the `isLanMode` gate.
 *
 * The entry decision was extracted into the pure [ConnectionManager.computeCanFastRecover] so it can
 * be asserted directly without sockets/NSD. Before the change only LAN could fast-recover; now every
 * connection mode (LAN / Cable USB_VSP·RS232·USB_AOA / App-to-App / Cloud) may, constrained purely by
 * the runtime conditions (in-flight transaction, passive drop, autoReconnect, not already recovering/
 * reconnecting, has a reusable config) — the connection mode no longer participates.
 */
class FastRecoveryModeAgnosticTest {

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val editor = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(SharedPreferences.Editor::class.java)
            ) { proxy, method, _ ->
                when (method.name) {
                    "commit" -> true
                    "apply" -> null
                    else -> proxy
                }
            }
            return Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(SharedPreferences::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "edit" -> editor
                    "getAll" -> emptyMap<String, Any>()
                    "contains" -> false
                    else -> args?.getOrNull(1)
                }
            } as SharedPreferences
        }
    }

    private fun manager(): ConnectionManager =
        ConnectionManager(TaplinkConfig(appId = "test-app", secretKey = "test-secret"), TestContext())

    @Test
    fun `all required conditions met enters fast recovery regardless of connection mode`() {
        val manager = manager()
        // No connection-mode argument exists any more: the decision cannot depend on LAN vs Cable.
        assertTrue(
            manager.computeCanFastRecover(
                autoReconnectEnabled = true,
                isManual = false,
                hasInFlight = true,
                isFastRecovering = false,
                isReconnecting = false,
                hasConnectionConfig = true
            )
        )
    }

    @Test
    fun `each missing precondition blocks fast recovery`() {
        val manager = manager()
        // autoReconnect disabled
        assertFalse(
            manager.computeCanFastRecover(
                autoReconnectEnabled = false,
                isManual = false,
                hasInFlight = true,
                isFastRecovering = false,
                isReconnecting = false,
                hasConnectionConfig = true
            )
        )
        // user-initiated (manual) disconnect
        assertFalse(
            manager.computeCanFastRecover(
                autoReconnectEnabled = true,
                isManual = true,
                hasInFlight = true,
                isFastRecovering = false,
                isReconnecting = false,
                hasConnectionConfig = true
            )
        )
        // no in-flight transaction
        assertFalse(
            manager.computeCanFastRecover(
                autoReconnectEnabled = true,
                isManual = false,
                hasInFlight = false,
                isFastRecovering = false,
                isReconnecting = false,
                hasConnectionConfig = true
            )
        )
        // already fast-recovering
        assertFalse(
            manager.computeCanFastRecover(
                autoReconnectEnabled = true,
                isManual = false,
                hasInFlight = true,
                isFastRecovering = true,
                isReconnecting = false,
                hasConnectionConfig = true
            )
        )
        // regular reconnect in progress
        assertFalse(
            manager.computeCanFastRecover(
                autoReconnectEnabled = true,
                isManual = false,
                hasInFlight = true,
                isFastRecovering = false,
                isReconnecting = true,
                hasConnectionConfig = true
            )
        )
        // no reusable connection config
        assertFalse(
            manager.computeCanFastRecover(
                autoReconnectEnabled = true,
                isManual = false,
                hasInFlight = true,
                isFastRecovering = false,
                isReconnecting = false,
                hasConnectionConfig = false
            )
        )
    }

    @Test
    fun `the original LAN scenario still enters fast recovery (regression)`() {
        val manager = manager()
        // Before the widening the gate required isLanMode AND the same runtime conditions.
        // The LAN case is exactly "all runtime conditions satisfied" (autoReconnect + in-flight +
        // passive, non-manual drop + not already recovering/reconnecting + reusable config).
        // After removing the isLanMode gate this must still return true, so the previously
        // supported LAN in-flight fast-recovery behavior is preserved (no regression).
        assertTrue(
            manager.computeCanFastRecover(
                autoReconnectEnabled = true,
                isManual = false,
                hasInFlight = true,
                isFastRecovering = false,
                isReconnecting = false,
                hasConnectionConfig = true
            )
        )
    }
}
