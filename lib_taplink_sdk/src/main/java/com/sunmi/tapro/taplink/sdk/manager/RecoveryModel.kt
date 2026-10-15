package com.sunmi.tapro.taplink.sdk.manager

import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.communication.interfaces.recovery.TerminalIdentity

/**
 * Normalized transport-fault event fed into the [EstablishedRecoveryCoordinator].
 *
 * Produced from the various lower-layer signals (kernel status listener ERROR/DISCONNECTED and
 * [com.sunmi.tapro.taplink.communication.interfaces.ConnectionCallback.onDisconnected]) so the
 * coordinator decides recovery from session/epoch/established context instead of inferring phase
 * from error codes 232/213.
 *
 * SPEC-ESTABLISHED-RECOVERY-001 (design §3.2; R5).
 *
 * @param sessionId logical session identifier the fault belongs to.
 * @param epoch the recovery epoch the fault was observed under.
 * @param code transport diagnostic code (internal only, not surfaced to callers).
 * @param msg transport diagnostic message (internal only).
 * @param established whether this logical session has ever reached CONNECTED (R3 hard gate).
 */
internal data class TransportFaultEvent(
    val sessionId: Long,
    val epoch: Long,
    val code: String,
    val msg: String,
    val established: Boolean
)

/**
 * Immutable description of a single established-recovery episode.
 *
 * One episode owns exactly one serial same-protocol/same-terminal rebuild attempt bounded by a
 * [deadlineElapsedMs] monotonic budget. [originalConfig] is the original [ConnectionConfig] reference
 * (its property setters are private, so it is not mutated by callers during recovery) used as the
 * read-only rebuild configuration. [recoveryIntent] distinguishes an SDK-internal recovery attempt
 * from an explicit caller connect so permission/pairing branches can be fenced (R14).
 *
 * SPEC-ESTABLISHED-RECOVERY-001 (design §3.6; R4/R8/R14).
 */
internal data class RecoveryEpisode(
    val sessionId: Long,
    val epoch: Long,
    val deadlineElapsedMs: Long,
    val originalConfig: ConnectionConfig?,
    val expectedIdentity: TerminalIdentity?,
    val attemptLimit: Int = 1,
    val recoveryIntent: Boolean = true
)
