# SDK role under the Host-side traceId-anchored unified LAN rebind plan (SRC-3)

> Local, repo-internal source document for SPEC-LAN-RECOVERY-001 rules R7–R10, added by
> SCR-LAN-TRACEID-REBIND-001 (APPROVED, L3, 2026-10-10). This file records the SDK-side evidence
> that the Host "traceId-anchored unified LAN rebind" plan requires NO SDK code change for LAN, so
> the coverage source trace stays inside the taplinksdk repository (per SPEC_GUIDE §1.3.3 — EXPLICIT
> sources must be locatable). The full Host plan and the code-level feasibility report live in the
> tapro repository and are referenced from the Host spec SPEC-HOST-LAN-TRACEID-REBIND-001; they are
> not duplicated here.

## S1. SDK fast-recovery preservation evidence

The in-transaction OPT-R1 fast-recovery path (LAN + autoReconnect + hasInFlight + non-manual) already
suppresses the paired disconnect cleanup via `isFastRecovering`; transaction callbacks and the
terminal-dedup set (`ResponseProcessor.processedTerminalEvents`) are not cleared during a recoverable
in-flight drop. A recovered connection keeps delivering the in-flight transaction's traceId events
under the existing 180s callback timeout (`LocalCallbackManager` default). This behavior is unchanged
by the Host plan.

## S2. No SDK code change for LAN

The Host plan requires no SDK code change for LAN. The `canFastRecover` `isLanMode` gate is retained
as-is; widening it to Cable is out of scope and deferred to a separate change. The existing
fast-recovery regression suite stays green with no SDK diff.

## S3. Window alignment and authorization boundary

Host-retained in-flight bindings must align to the SDK 180s transaction callback timeout, not the 5s
`FAST_RECOVERY_BUDGET_MS`. When fast recovery is not achieved within 5s the SDK fails in-flight
callbacks with E306 and reports E213; the Host must not expect the SDK to keep an in-flight callback
alive beyond the 180s callback timeout, and 306 must not be retried directly. The appId / merchant
identity guard that replaces the LAN different-IP reject is a Host-side authorization boundary; the
SDK adds no public API, no new error code and no new identity anchor; traceId remains the globally
unique transaction identity.

## S4. Host-side decisions resolved at the approval gate (context only)

The two Host-side high-risk questions were resolved safety-first at the 2026-10-10 approval gate:
empty appId does not rebind (Q-1); the rebind trigger is the sign-in success callback, not onOpen
(Q-2). These are Host-side decisions and do not change the SDK's no-code-change conclusion for LAN.
