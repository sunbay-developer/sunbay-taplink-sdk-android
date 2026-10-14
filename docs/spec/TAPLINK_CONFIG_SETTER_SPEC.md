---
spec_id: SPEC-CONFIG-001
title: SDK field setter compatibility
status: active
version: v1.1
owner: SDK maintainer
last_reviewed: 2026-10-14
supersedes: null
---

# SDK Field Setter Compatibility

## 0. Changelog

| Version | Date | Change | Rules / AC | Level | Breaking | Approval |
|---|---|---|---|---|---|---|
| v1.0 | 2026-10-14 | Initial unapproved draft, runtime timeout proposed | R1–R5 | L3 | Behavioral | Draft only |
| v1.1 | 2026-10-14 | User approves field setters SDK-wide and excludes runtime updates; replaces draft before first activation | R1–R5 / AC1–AC5 | L3 | Setter alias behavior | Current conversation, source S4/S5 |

Approval evidence: REQ-CONFIG-001 S4/S5. No earlier active spec is being changed;
initial draft never entered active status. Independent review report:
/private/tmp/taplink-config-spec-review.md (initial review plus scope follow-up).

## 1. Scope

All existing public field setters in config/model/request and their Builders support
ignored return values and fluent chaining. Runtime APIs retain their current return
signatures. No new setters are added. No post-init configuration hot updates. SDK snapshot implementation rules R2/R5 are DERIVED preservation measures, not direct user quotes.

## 2. Rules (Five Dimensions)

| Rule | Dimension | Condition → behavior | Source |
|---|---|---|---|
| R1 | Business flow | Each public field setter called → change its receiver and return exactly that receiver; ignored return, separate calls and chains accumulate values; last successful write wins | SRC-1 |
| R2 | State and rules | init called → SDK retains an independent snapshot of config values including mutable default staff/device (DERIVED from setter scope and exclusion of runtime changes); later config setters do not affect initialized SDK, including timeout | SRC-2 |
| R3 | Interface boundary | Existing Java/Kotlin consumer → constructor/getter/fluent/copy/component JVM descriptors and defaults remain (Kotlin reflection isData is excluded); copy remains shallow, components/equality/hash/toString reflect current values; runtime API descriptors unchanged | SRC-3 |
| R4 | Exceptions and risk | Merchant validation fails → previous value remains; computed payment-method enum getters reflect latest string/enum setter; field setters preserve input types, units, existing validation and serialization; mutable objects require serialized writes, and must not be mutated as hash keys | SRC-4 |
| R5 | Acceptance | PaymentManager.execute entry, before connection checks, submits a request → SDK snapshots mutable field objects and nested collection values before async connection/processing; subsequent caller setters do not change submitted amount, IDs, tip, metadata or timeout | SRC-5 |

R1→AC1; R2→AC2; R3→AC3; R4→AC4; R5→AC5.

## 3. Interfaces and Fields

Changed: TaplinkConfig (10 setters plus clearMerchantId), PaymentRequest (23 overloads),
AmountInfo (7), DeviceInfo (13), GoodsDetail (4), PaymentMethodInfo (6 overloads),
StaffInfo (2), TipConfig (5), TipSuggestions (3). ConnectionConfig, QueryRequest,
and transaction request Builders already use same-instance mutation and are retained.
Constructor fields retain types/nullability/defaults and getter signatures. Private
property setters prevent conflicting public void setX methods. copy/component methods
are explicitly preserved when replacing a data class with a mutable class.
No new error codes; timeout seconds and requestTimeout milliseconds unchanged.

## 4. Boundaries and Exceptions

Explicit false, null-capable setters, clearMerchantId, repeated writes and overloaded
enum/string setters remain supported. Invalid merchant IDs fail before changing state.
PaymentMethodInfo computed getters must not cache the pre-mutation value.
copy is a top-level shallow copy, not a public deep-copy operation. SDK ownership
boundaries use internal snapshots of affected nested mutable values. Serialize writes
before init/execute; no atomic multi-field concurrent update promise is added.
All money remains integer smallest units; no auto retry on 306, no callback result
classification, connection mode, Refund/Abort or lifecycle changes.

## 5. Examples

```kotlin
val config = TaplinkConfig.create(appId, secretKey)
config.setLogEnabled(BuildConfig.DEBUG).setLogLevel(LogLevel.DEBUG)
config.setTimeout(30)
TaplinkSDK.init(context, config)
```

```java
TaplinkConfig config = TaplinkConfig.create(appId, secretKey);
config.setTimeout(30).setLogEnabled(false);
TaplinkSDK.initSDK(context, config);
```

Use copy() explicitly when preserving an original object while preparing different values.
Finish all setters before SDK init/execute. Production logging remains disabled.

## 6. Flow

Create → mutate same receiver → init/submit → SDK uses owned snapshot.
No complex branching; diagram N/A.

## 7. Compatibility

Behavioral change: aliases now observe field setter updates. Source/binary method
signatures are preserved, but Kotlin reflection isData becomes false for converted
classes. Mutable hash key behavior requires migration guidance. Public shallow copy,
components, equality/hash and serialized field names are preserved and checked.
Evaluate Host/Demo/public en+zh docs; no dependency bump or publication in this task.
Maintainer manually packages and publishes; downstream public release documentation
must be synchronized after actual version confirmation. Version remains pending.

## 8. Approved Acceptance Criteria

- AC1 (R1): Given each field setter and receiver, when called without retaining return,
  then original getters/serialized fields show the value and return is identical;
  separate/chained/repeated writes preserve unrelated values. Include existing
  ConnectionConfig/QueryRequest/Builders and enum/string overloads.
- AC2 (R2): Given config with timeout/log/credentials/default metadata set, when init
  occurs then caller mutates those objects, then active manager configs remain
  unchanged; a new init uses newly configured values.
- AC3 (R3): Given baseline JVM descriptors and Java/Kotlin callers, when compiled
  against updated SDK, then constructors/getters/copy/components/fluent signatures
  link; independent top-level copies, default arguments, components, equality/hash (excluding reflection isData),
  nullable Float NaN/signed zero and JSON names retain prior semantics.
- AC4 (R4): Given invalid merchant mutation, then prior value remains; given already-read
  enum getters, when payment-method setter changes underlying value, then getters
  and JSON reflect new value. No amount unit or validation contract change.
- AC5 (R5): Given request containing nested mutable fields and lists, when submitted and
  original fields/lists are later changed, then SDK-held request/serialized payload
  retains the values captured on submission.
