package com.sunmi.tapro.taplink.sdk.manager

import android.os.SystemClock

/**
 * Monotonic clock seam for the established-connection recovery coordinator.
 *
 * The production implementation delegates to [SystemClock.elapsedRealtime], which is monotonic and
 * unaffected by wall-clock jumps. JVM unit tests inject a fake clock so budget/deadline behaviour is
 * deterministic. Internal to the SDK; never exported to the public API.
 *
 * SPEC-ESTABLISHED-RECOVERY-001 (design §2, §11; R4).
 */
internal interface RecoveryClock {
    /** Monotonic elapsed time in milliseconds. */
    fun elapsedRealtimeMs(): Long
}

/** Production clock backed by [SystemClock.elapsedRealtime]. */
internal class SystemRecoveryClock : RecoveryClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
