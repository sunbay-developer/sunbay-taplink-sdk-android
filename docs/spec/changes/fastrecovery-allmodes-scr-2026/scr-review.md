# DRAFT SCR review — SPEC-LAN-RECOVERY-001 R8/AC8 all-modes fast-recovery widening

Independent gate review of the DRAFT deliverables that revise the already-L3-approved
`SPEC-LAN-RECOVERY-001` so R8/AC8 match the uncommitted `ConnectionManager.kt` change that widened
OPT-R1 in-flight fast-recovery entry from LAN-only to all connection modes (via the extracted pure
internal `computeCanFastRecover`). Four artifacts were reviewed: the new SCR
`SCR-LAN-FASTRECOVERY-ALLMODES-001.yaml`, the revised spec `TAPLINK_LAN_RECOVERY_SPEC.md`, the
coverage `SPEC-LAN-RECOVERY-001.yaml`, and the new unit test `FastRecoveryModeAgnosticTest.kt`. This
is a DRAFT-only task: the question is **not** whether the widening should be approved, but whether
the deliverables are correct and correctly left pending the human L3 gate.

Watch for: nothing blocking. The pending-state discipline is clean across all four files, the SCR
field structure mirrors the reference SCR, change_origin is honestly DESIGN_DECISION, the L3 call is
justified, the R8/AC8/AC8b rewrite is consistent with the risk assessment's harmless-widening
framing, R6/R7/R9/R10 are left untouched, the test covers the three required cases, and the author
recorded compile / unit-test / spec-check evidence. One non-blocking observation on SRC-4 wording is
noted below.

**Verdict**: APPROVED (the DRAFT deliverables are correct and correctly left pending human approval;
this does NOT approve the SCR itself).

## High-level view

The hard rule is satisfied. No new or changed item in any of the four files carries
APPROVED / Approved / active. The SCR's `approval.status`, top `after.status`, and R8
`proposed_rules` status are PENDING / PROPOSED; the spec front matter is `status: draft` with an
explicit top-of-spec PENDING banner and R8/AC8/AC8b marked DRAFT / PENDING_APPROVAL; the coverage is
`status: draft` / `approved: false` with R8 at `REVIEW_REQUIRED` and `pending_change.status: DRAFT`.
The pre-existing R7/R9/R10 (approved under the prior SCR) correctly keep their APPROVED status —
those are not touched by this change, so leaving them approved is right, not a violation.

The SCR structure matches the reference `SCR-LAN-TRACEID-REBIND-001.yaml` field-for-field
(change_id, spec_id, rule_id, change_type, level, change_origin, reason, source, before/after,
impact, approval, version/time) and extends it sensibly with a `harmless_widening` and
`relationship_to_other_rules` block. `change_origin` is honestly DESIGN_DECISION: the prior spec
explicitly asserted the opposite (isLanMode retained, Cable out of scope), so the widening is a new
maintainer decision, not a source omission. The L3 level is justified on payment-response-path /
in-flight-callback-lifetime grounds, and `after.status: PROPOSED` is exactly what SPEC_GUIDE §4
requires for an unapproved Type-B change.

The R8/AC8 rewrite tracks the risk assessment's two-layer framing precisely: the judgment layer is
widened to all modes, the runtime layer stays bounded by `onDisconnected`'s `isConnectionError`
classification, so only LAN (WebSocket 1000/1001/1006) actually reaches fast recovery and non-LAN
modes are a runtime no-op. AC8b was added to assert that no-op rather than inventing a positive
Cable fast-recovery AC the code cannot exhibit — the correct choice. The relationship to R6/R7/R9/R10
is addressed explicitly and those rules are left unchanged in all three governance files.

The unit test exercises the three required cases against the real internal function, and the author
recorded compile, unit-test (3/3 for the new test, 34/0/0/0 full suite), and spec-check results.
Spot-checking `ConnectionManager.kt` confirms `computeCanFastRecover` is `internal` (not public), is
a pure boolean over the six runtime conditions with no mode parameter, and that
`FAST_RECOVERY_BUDGET_MS`, `tryFastRecovery`, `fastRecoveryFallback` and `isConnectionError` are
unchanged.

<details>
<summary>Issues (1)</summary>

1. **SRC-4 source_quote language (non-blocking)** — SRC-4's `source_quote` is the risk assessment's
   Chinese conclusion sentence and is verbatim-present in the cited file, which satisfies SPEC_GUIDE
   §1.3; `verified: false` is correctly set pending human semantic confirmation. No action required
   for the DRAFT; the human L3 reviewer is the one who flips `verified` to true. Flagged only so the
   reviewer knows the quote is intentionally unverified, not accidentally so.

</details>

<details>
<summary>Details</summary>

## HARD RULE: nothing new/changed is APPROVED, and pending markers are present

Every new or changed item is held in a not-approved state, and each file carries an explicit
"pending maintainer human approval" marker.

SCR `SCR-LAN-FASTRECOVERY-ALLMODES-001.yaml`:
- Header banner `░░░ DRAFT — PENDING MAINTAINER HUMAN APPROVAL ░░░`.
- `after.status: PROPOSED`, R8 `proposed_rules[].status: PROPOSED`.
- `approval.status: PENDING`, `approved_by: null`, with a `pending_note` spelling out what the human
  L3 reviewer must confirm.
- `resolved_at: null`; `target_spec_version: "v1.2"` explicitly marked "proposed; applies only after
  human approval".

Spec `TAPLINK_LAN_RECOVERY_SPEC.md`:
- Front matter `status: draft`, `version: v1.2`, `pending_change: SCR-LAN-FASTRECOVERY-ALLMODES-001`.
- Top-of-spec banner `░░░ THIS REVISION (v1.2) IS PENDING HUMAN APPROVAL ░░░`.
- R8 status cell = `DRAFT / PENDING`; AC8 and AC8b headers = `DRAFT / PENDING_APPROVAL`.
- §2.1 and §9 both state R8/AC8/AC8b are DRAFT and R1–R7/R9/R10 keep their v1.1 approved state.

Coverage `SPEC-LAN-RECOVERY-001.yaml`:
- `status: draft`, `approved: false`, with a `draft_note` explaining the temporary draft state is
  only because of the three unapproved items.
- R8 `status: REVIEW_REQUIRED` plus `draft_status: DRAFT / PENDING_APPROVAL`; the note documents that
  the spec-check vocabulary has no literal DRAFT token so REVIEW_REQUIRED is the not-yet-approved
  value — a reasonable mapping, not a disguised approval.
- AC8 and AC8b each carry `status: DRAFT`.
- `pending_change.status: DRAFT`, `pending_change.approval: PENDING_HUMAN_APPROVAL`.

The pre-existing R7/R9/R10 remain `APPROVED` and `applied_change.status: APPROVED` for
SCR-LAN-TRACEID-REBIND-001. That is correct: those rules were approved under the prior L3 gate and
are not modified by this SCR, so leaving them approved is required, not a hard-rule violation. The
hard rule applies to the new/changed items (R8/AC8/AC8b, SRC-4, this SCR), and none of them is
APPROVED/active.

## SCR structure and evidence chain vs the reference SCR

The new SCR carries the same field spine as `SCR-LAN-TRACEID-REBIND-001.yaml`: `change_id`,
`spec_id`, `rule_id`, `change_type`, `level` (with an inline rationale), `change_origin` (with the
A-vs-B justification), `reported_by`, `initiator_role`, `reason`, `source` (`type` + `references`
with `document_id`/`document_version`/`document_path`/`section`/`source_quote` + `evidence`),
`before`/`after`, `impact`, `approval`, `target_spec_version`, `created_at`, `resolved_at`. It adds
`harmless_widening` and `relationship_to_other_rules` sub-blocks under `impact`, which are an
appropriate extension for this change's shape rather than a departure from the template.

`change_origin: DESIGN_DECISION` is honestly classified. The reference SCR added R8 as
"no SDK code change for LAN; isLanMode gate retained; Cable out of scope"; this SCR reverses that
wording to match code the maintainer decided to keep. SPEC_GUIDE §4's distinction is between
`SOURCE_OMISSION` (the original source already specified it, the generator missed it) and
`DESIGN_DECISION` (the source did not specify it, a new decision is needed). The prior spec
explicitly asserted the opposite behavior, so this is a new design decision, not a recovered
omission — the DESIGN_DECISION label and the Type-B treatment (`after.status: PROPOSED` until human
approval) are correct.

The L3 judgement is justified and leans the right way. The rationale ties the level to the trigger
scope of in-flight payment-response-path fast recovery and in-flight callback lifetime, and keeps L3
"regardless of the small code size" even though Step-1 found the widening to be a runtime no-op for
non-LAN modes. That is the conservative, correct call: a rule governing when a payment transaction's
response channel is kept alive across a drop is an L3 connection-protocol / payment-path item under
the SPEC_GUIDE §4 table.

`before`/`after` accurately describe the change. `before` quotes the v1.1 R8/AC8 wording
("isLanMode gate retained; Cable out of scope"); `after` describes the gate removal and the
`computeCanFastRecover` six-condition runtime semantics with no mode parameter, matching the code.

## R8/AC8/AC8b consistency with the risk assessment

The risk assessment's central conclusion is a two-layer model: removing `isLanMode` widens the
**judgment** layer to all modes, but the **runtime** layer is bounded by `onDisconnected`'s
`isConnectionError` classification, so only disconnects reporting WebSocket close codes
1000/1001/1006 reach `computeCanFastRecover`, and only the LAN (WebSocket) transport produces those
codes. The rewritten R8 reproduces exactly this framing ("judgment layer widened to all modes; the
runtime layer stays bounded by the onDisconnected isConnectionError classification, so in practice
only LAN … reach the judgment and non-LAN modes are a runtime no-op"). The SCR's
`impact.harmless_widening.per_mode` block carries the same per-mode verdicts the assessment reached
(Cable → E212/E232/E251.. connection-error branch; App-to-App → E213 connection-error branch;
Cloud → no SDK connection entity, no onDisconnected).

This is the "all-modes-harmless" framing, and the deliverables correctly do **not** overclaim it as
"all modes actively fast-recover." AC8b asserts the no-op rather than adding a positive Cable
fast-recovery AC. The SCR's `new_acs_assessment` states the reason outright: a positive Cable AC
would "assert behavior the code does not actually exhibit." That is the right modeling decision —
asserting a reachable positive path the code cannot currently take would be a false AC.

`relationship_to_other_rules` addresses R6/R7/R9/R10 individually: R6 (pre-connection handshake
recovery) is a different scope; R7 (isFastRecovering suppression + 180s callback lifetime) is
mechanically unchanged because only the entry-mode set changed, not the suppression/timeout; R9
(5s budget vs 180s timeout, E306→E213) is untouched because `FAST_RECOVERY_BUDGET_MS`,
`tryFastRecovery`, `fastRecoveryFallback` are unchanged; R10 (identity boundary, no public surface)
is preserved because `computeCanFastRecover` is internal and adds no public API / error code /
identity anchor. All three governance files leave R1–R7, R9, R10 statements and statuses unchanged.

## Unit test coverage and the internal-function / no-code-change constraints

`FastRecoveryModeAgnosticTest.kt` exercises the three required cases against the real
`ConnectionManager.computeCanFastRecover`:
- `all required conditions met enters fast recovery regardless of connection mode` — all six
  runtime conditions true → true, with a comment noting there is no connection-mode argument so the
  decision cannot depend on LAN vs Cable (the mode-agnostic / any-mode case).
- `each missing precondition blocks fast recovery` — flips each of the six conditions one at a time
  and asserts false for every one (any-condition-false → false).
- `the original LAN scenario still enters fast recovery (regression)` — the LAN case is exactly
  "all runtime conditions satisfied," asserting the pre-widening LAN behavior is preserved.

The test calls the function directly (no sockets/NSD), which is why it can assert the pure decision.
It does not touch `ConnectionManager`'s disconnect flow, so it does not modify or depend on the
existing logic. Spot-check of `ConnectionManager.kt` confirms the production constraints hold:
`computeCanFastRecover` is declared `internal fun` (not public — consistent with the SDK's
public/internal boundary, since `manager` is an internal package), it returns the plain conjunction
`autoReconnectEnabled && !isManual && hasInFlight && !isFastRecovering && !isReconnecting &&
hasConnectionConfig` with no mode parameter, the `onDisconnected` normalDisconnect branch calls it
with the collected runtime values, and `FAST_RECOVERY_BUDGET_MS` (`5_000L`), `tryFastRecovery`,
`fastRecoveryFallback` and `isConnectionError` are present and unchanged. There is no residual
`isLanMode` reference in the fast-recovery path.

## Recorded verification evidence

The author recorded the evidence rather than leaving the reviewer to run suites. The SCR's
`impact.verification` lists the commands run (spec-check, `compileDebugKotlin`, the targeted
`FastRecoveryModeAgnosticTest` run, and the full `testDebugUnitTest` suite) and the results:
spec-check PASS with one yellow (SRC-4 `verified=false` pending human review — intentional, not a
defect), compile BUILD SUCCESSFUL, the new test 3/0/0/0, and the full SDK suite 34/0/0/0 (up from
the v1.1 baseline of 29, no regression). The checked-in test-results XML
(`TEST-…FastRecoveryModeAgnosticTest.xml`) independently shows `tests="3" failures="0" errors="0"
skipped="0"`, matching the recorded count. Per the task instruction the build and full suite were
not re-run; the recorded evidence is present and internally consistent, so there is no missing-
evidence rejection.

## Non-blocking observation: SRC-4

SRC-4 in the coverage cites the risk assessment's §4 conclusion with a Chinese `source_quote` that
is verbatim-present in that file, satisfying SPEC_GUIDE §1.3's "source_quote must be found verbatim"
requirement. `verified: false` is correct: §1.3 allows `verified: true` only after a human confirms
the quote supports the rule, and that confirmation is part of the pending L3 gate. This is noted
only so the human reviewer knows SRC-4 is intentionally unverified; it is not a defect in the DRAFT.

</details>

<details>
<summary>File map</summary>

- `docs/spec/changes/SCR-LAN-FASTRECOVERY-ALLMODES-001.yaml` — new DRAFT SCR; MODIFY_RULE R8/AC8,
  L3, DESIGN_DECISION; approval PENDING, after.status PROPOSED.
- `docs/spec/TAPLINK_LAN_RECOVERY_SPEC.md` — v1.2 DRAFT; R8 rewritten, AC8 rewritten, AC8b added, all
  marked DRAFT/PENDING_APPROVAL; R1–R7/R9/R10 untouched; top banner + §0/§2.1/§9 pending notes.
- `docs/spec/coverage/SPEC-LAN-RECOVERY-001.yaml` — status draft / approved false; R8
  REVIEW_REQUIRED, AC8/AC8b DRAFT, SRC-4 added (verified false), pending_change DRAFT; R7/R9/R10
  remain APPROVED.
- `lib_taplink_sdk/src/test/java/.../manager/FastRecoveryModeAgnosticTest.kt` — new JVM unit test;
  3 cases (any-mode true, each-condition-false, LAN regression) against internal
  `computeCanFastRecover`.
- `lib_taplink_sdk/src/main/java/.../manager/ConnectionManager.kt` — spot-checked only (change
  pre-made): `internal computeCanFastRecover` pure fn, no isLanMode gate, budget/recovery helpers
  unchanged.

</details>
