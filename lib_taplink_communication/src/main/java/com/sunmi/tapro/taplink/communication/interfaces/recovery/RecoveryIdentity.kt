package com.sunmi.tapro.taplink.communication.interfaces.recovery

/**
 * Trusted identity anchor for the original terminal, captured when a connection first succeeds and
 * used to validate that an SDK-internal recovery rebuild reconnected to the SAME terminal.
 *
 * VID/PID or USB bus path ALONE are insufficient (SPEC-ESTABLISHED-RECOVERY-001 R8); a reliable
 * anchor is a stable per-terminal value such as the mDNS-bound serial (LAN) or a USB device serial
 * number (cable). When no reliable anchor is available the adapter must refuse recovery rather than
 * silently attach to a different terminal.
 *
 * Internal to [com.sunmi.tapro.taplink.communication]; never exported to the public API.
 */
data class TerminalIdentity(
    /** Stable per-terminal anchor (e.g. mDNS serial, USB serialNumber). Null means no trusted anchor. */
    val anchor: String?
) {
    /** A trusted anchor is present and non-blank. */
    val isReliable: Boolean get() = !anchor.isNullOrBlank()
}

/**
 * Result of a read-only, non-interactive recovery prerequisite evaluation.
 *
 * This NEVER triggers a permission request, privileged grant or pairing/authorization UI
 * (SPEC-ESTABLISHED-RECOVERY-001 R14). It only reports whether existing effective authority / the
 * target are usable for a silent rebuild.
 */
sealed class RecoveryPrereqResult {
    /** Existing authority and target are available; a silent rebuild may proceed. */
    object Available : RecoveryPrereqResult()

    /**
     * Authority is missing or reauthorization would be required. Recovery must end in a single
     * terminal disconnect without prompting; [diagnostic] is retained for logging only.
     */
    data class Denied(val diagnostic: String) : RecoveryPrereqResult()

    /** The adapter does not support non-interactive recovery prerequisite evaluation. */
    object Unsupported : RecoveryPrereqResult()
}
