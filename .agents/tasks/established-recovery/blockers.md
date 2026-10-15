# Established Recovery — JVM-unverifiable blockers (SPEC-ESTABLISHED-RECOVERY-001)

These items are NOT verifiable in JVM unit tests. They require maintainer-authorized device /
real-backend testing before any end-to-end "works" claim. They are recorded here as UNVERIFIED,
never claimed as passing. (Plan §10/§11; design §10.)

- **B1 — VSP/RS232/AOA trusted identity-anchor capture + non-LAN Host-side continuation.**
  The SDK now captures `UsbDevice.serialNumber` (VSP/RS232/AOA) only when existing permission allows,
  and fails recovery closed when no reliable anchor is available. Whether a real device actually
  exposes a usable serial, and whether the non-LAN Host continues the original response path after a
  transport rebuild, needs device/Host validation. The current Host rebind spec
  (`SPEC-HOST-LAN-TRACEID-REBIND-001`) is LAN-only / onOpen-only; non-LAN continuation requires a
  separate Host-approved spec/SCR. JVM proves only callback/traceId mapping retention + route
  reattach.
- **B2 — SUB_SCREEN screen-initialization readiness.** "Transport connected but screen not ready
  must not count as success" needs real-device validation.
- **B3 — USB physical re-enumeration / attach / replug / port power-down** (R12 "wait once within the
  remaining budget") needs authorized device testing.
- **B4 — Local AIDL process-restart semantics.** Process death must not be presented as resumable
  execution; only the response channel is restored. Needs integration validation of binding
  lifecycle.
- **B5 — AOA Accessory shared classes.** Audit only; no public client route; no device coverage
  claim.

All five require maintainer-authorized device/real-backend testing and must be reported as
unexecuted, not passing.
