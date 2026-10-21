---
document_id: DECISIONS-ONERROR-CALLBACK-LIFECYCLE-001
title: TapLink onError 回调生命周期 — 维护者裁决清单（Step 5 人工评审）
status: pending_human_review
version: v1.0
owner: 待维护者指定（SDK 连接/交易规则负责人；Host 规则负责人共同参与 Q-1/Q-2）
last_reviewed: 2026-10-19
spec_ref: SPEC-REQUEST-CALLBACK-LIFECYCLE-001 (draft)
scr_ref: SCR-ONERROR-CALLBACK-LIFECYCLE-001 (PENDING)
design_ref: DESIGN-ONERROR-CALLBACK-LIFECYCLE-001 v2.0
---

# TapLink onError 回调生命周期 — 维护者裁决清单

> 本文件是 SDD 五步流水线 **Step 5（人工评审）** 的输入材料，只做一件事：把阻塞 draft 规格的
> Q-1~Q-4 拆成最小可裁决项，列出候选选项与各自的 breaking/回归影响，供维护者勾选裁决。
> **本文件不新增已批准规则、不填虚构 AC、不改变任何 active 规格的批准状态。** 维护者在此勾选的决定，
> 落地时须回写到 coverage 的 `open_questions[].decision`、对应 R6–R9 的 AC，并按级别走 SCR 审批。

## 0. 前置状态快照（勿在本轮改动）

| 项目 | 当前状态 | 依据 |
|---|---|---|
| 规格 SPEC-REQUEST-CALLBACK-LIFECYCLE-001 | draft / approved=false | coverage 头部 |
| SCR-ONERROR-CALLBACK-LIFECYCLE-001 | PENDING / after.status=PROPOSED | SCR 文件 |
| R1–R5、R10、R11 | REVIEW_REQUIRED（已对齐已批准 established recovery） | coverage rules |
| R6、R9 | BLOCKED by Q-1 + Q-2（critical） | coverage rules |
| R7 | BLOCKED by Q-3（critical，SDK 单仓库可闭环） | coverage rules |
| R8 | BLOCKED by Q-4（critical，含可静态推进部分） | coverage rules |
| 失败 gate | critical_acceptance_coverage 5/9、unresolved_high_risk 4、unresolved_conflicts 1 | coverage gates |

> 已批准基线（不在本清单的裁决范围内，列出仅为约束边界）：established recovery 为同协议/同终端
> **一次**、5,000ms 单调预算；原请求 deadline 恢复中不重置/不延长；恢复成功续推原 callback/traceId/
> transactionRequestId，绝不重发支付；最终失败走既有 physical-disconnect/failAllPendingTransactions
> 清理一次并终局 `onDisconnected(E213)`；恢复期间新请求 E213 拒绝；ConnectionListener 不被 SDK 摘除。
> 任一候选选项若与上述基线冲突，必须先对基线另立 SCR，不能在本清单内隐式推翻。

---

## Q-1 —— Host 如何识别"与当前交易相关的最终断开"，保留窗如何衔接？【展开版 · 已裁决 · 含候选 AC】

- **风险**：critical ｜ **阻塞**：R6、R9 ｜ **来源**：SRC-13（CONFLICT）、SRC-1、SRC-14、SRC-16
- **冲突点**：Host active `SPEC-HOST-LAN-TRACEID-REBIND-001 v1.2` R3/R9 的"保留在途绑定、掉线期间不发
  `onDisconnected`、最大保留窗兜底对齐 180s 交易回调超时" 与 本需求 S1"最终断开后关闭交易、允许下一笔"
  尚无时序衔接；且 Host 旧文案的 "5s 快恢失败后 E306" 与 SDK 当前 established recovery 最终 **E213** 存在历史语义漂移。

> ### ✅ 维护者裁决（2026-10-19，SDK + Host 规则负责人同一人）
>
> **目标行为**：通信 onError → SDK 在**统一 10s 窗**内自动尝试既有单次恢复；恢复成功续推原交易；
> **恢复 10s 内未成功 → SDK 发一次最终 `onDisconnected`，tapro 收到后关闭在途交易并放行下一笔**（方案甲）。
>
> | 决策点 | 选定 | 结论 |
> |---|---|---|
> | Q-1.a 事件来源 | **A1 本地断开感知为准** | 不新增跨端关闭指令（无 wire breaking）；tapro 以 SDK 最终 `onDisconnected` 为关交易触发 |
> | Q-1.b 保留窗时长 | **统一 10s（两端同值）** | SDK established recovery 预算 5s→**10s**；Host 关交易等待窗 = 同一 10s，不再用 180s 兜底；替代原 B1/B2/B3 |
> | Q-1.c E306/E213 漂移 | **C1 以 E213 为准** | 最终断开 = E213；修订 Host 旧 "5s→E306" 文案 |
>
> ⚠️ **配套记录义务（本轮暂不建，待"去建"指令）**：
> - 5s→10s 改的是**已批准 established recovery 基线**（L3）。须在 SDK 建一条 `change_origin=DESIGN_DECISION`
>   的 SCR，由本负责人当场批准；影响标注 `EstablishedRecoveryBudgetTest` 期望需从 5000ms 同步为 10000ms。
> - Host 10s 等待窗 + C1 文案修订：须在 **tapro** 对 `SPEC-HOST-LAN-TRACEID-REBIND-001` 建对应 L3 SCR
>   （R3/R9），由本负责人批准。两端同值 10s 是"连接统一性"的落点，不可一端 10s 一端 180s。

### 决策点 Q-1.a：最终断开的"事件来源"

| 选项 | 说明 | breaking | 回归影响 |
|---|---|---|---|
| A1 本地断开感知为准 | Host 仅凭自身链路断开感知触发关闭，不依赖 SDK 跨端通知 | 否（不改 wire） | Host 侧断开检测路径；LAN `onClose` 可能不通知 Host listener 的分支需回归 |
| A2 新增跨端关闭指令 | SDK/Host 协商一条显式关闭/ABORT 消息 | **是**（新增 wire 协议，跨端 breaking） | 全连接模式协议回归 + 断链下指令不可达兜底；需协议负责人裁决 |
| A3 本地感知为主 + 有链路时协商关闭、本地兜底 | A1 为底，链路尚存时附带协商 | 视实现（新增可选消息则部分 breaking） | A1 + A2 两条路径都需回归 |

### 决策点 Q-1.b：保留窗起算点与时长

| 选项 | 说明 | breaking | 回归影响 |
|---|---|---|---|
| B1 沿用 Host 180s 交易回调超时兜底 | 不新增数值，保留窗 = 现有 `LocalCallbackManager` 180s | 否 | 需确认 180s 与"尽快放行下一笔"目标是否矛盾 |
| B2 新增独立关闭窗（数值待定） | 为关闭/放行单设时长 | 否（纯 Host 内部） | 新计时路径 + 与 established 5s/Host 180s 三者边界回归 |
| B3 事件驱动、不设固定时长 | 以引擎退出/资源释放证据为准，无墙钟窗 | 否 | 依赖 Q-2 的退出证据定义，二者需联合裁决 |

> ⚠️ 约束：原文明确 **"不能先定为 5s"**（coverage Q-1）。任何选项都不得把 established recovery 的
> 5,000ms 预算直接当作 Host 关闭窗。

### 决策点 Q-1.c：E306/E213 历史语义漂移

| 选项 | 说明 | breaking | 回归影响 |
|---|---|---|---|
| C1 以 SDK 现状 E213 为准，更新 Host 文案 | 认定 SDK 最终失败 = E213，修订 Host 旧 "5s→E306" 表述 | 否（文档对齐） | 须在 Host 仓库对 `SPEC-HOST-LAN-TRACEID-REBIND-001` 另立 SCR 修订 R9 文案 |
| C2 保留双码并澄清各自触发场景 | E306=原请求超时、E213=最终断开，分别成立 | 否 | 需核对两码是否真的来自不同路径，避免同一事件双码 |

---

## Q-2 —— 不同交易阶段如何安全关闭？什么证据才允许放行下一笔？【展开版 · 已裁决 · 含候选 AC】

- **风险**：critical ｜ **阻塞**：R6、R9 ｜ **来源**：SRC-10（UNSPECIFIED）
- **硬约束**：`TransactionStage` 中 PIN/联机/签名/移卡等阶段 **不允许一般取消**；`cancelTransaction`
  返回 true 只表示"已请求取消"，不等于 job 已退出；`resetEcrTransactionState` 只清
  `isInMessageForwardPage` 标记，**不证明硬件/引擎已释放**（设计 §2.2 已静态定位）。

> ### ✅ 维护者裁决（2026-10-19）
>
> **核心澄清**：这里的"强制中止"指 **tapro 本地释放交易占用、放行下一笔**，**不是**向支付网关发 Abort。
> 对资金结果**不下 approved/declined 结论**；真实结果由集成方对原 `transactionRequestId` 自行 query。
> 这样既满足"保证新交易能正常进行"，又不违反 AGENT §4.9（授权后不得 Abort）与 §4.5（306 query-first）。
>
> | 决策点 | 选定 | 结论 |
> |---|---|---|
> | Q-2.a 可取消阶段 | **A1 取消 + 等释放后放行** | 取消请求后以引擎/硬件退出证据清 busy，而非仅清 `isInMessageForwardPage` 标志 |
> | Q-2.b 不可取消阶段 | **强制中止（本地）** | 10s 窗后仍在 PIN/联机/签名/移卡阶段：**本地**释放占用放行下一笔，不等阶段自然结束；绝不向网关 Abort，不伪造资金失败 |
> | Q-2.c 下一笔准入 | **C1 本地释放 + 集成方 query** | 不对本笔下资金结论；集成方须先 query 原交易，再决定独立新订单 / 补扣 / 冲正；SDK/Host 只保证下一笔可进入 |
>
> ⚠️ **风险接受声明（维护者已知悉）**：在联机授权阶段本地强制释放，可能出现"网关已扣款、本地已放行"的
> 不一致窗口。维护者接受该风险，并以"集成方 query-first"作为资金正确性的保证手段。此接受须记入 Host SCR。

### 决策点 Q-2.a：可取消阶段的关闭策略

| 选项 | 说明 | breaking | 回归影响 |
|---|---|---|---|
| A1 Host 自主取消 + 等待引擎/硬件释放 ACK 后放行 | 取消后必须拿到退出证据才清 busy | 否（行为变更，非 API） | `CardTransactionEngineImpl` 取消→释放全链路回归；超时兜底需定义 |
| A2 取消即放行（仅清 busy 标志） | 不等待释放证据 | 否 | ❌ 与"清标志不证明释放"结论冲突，高风险，需明确拒绝或接受残留风险 |

### 决策点 Q-2.b：不可取消阶段（PIN/联机/签名/移卡）的收口

| 选项 | 说明 | breaking | 回归影响 |
|---|---|---|---|
| B1 等待当前阶段自然结束再收口 | 不强制打断，阶段完成后按结果处理 | 否 | 需定义"等待"期间是否仍拒绝下一笔（与 Q-1.b 联动） |
| B2 定义阶段级别的安全中止点 | 为部分阶段新增可中止点 | 视实现（可能改引擎行为） | 引擎状态机回归；须支付规则负责人裁决 |

> ⚠️ 约束：不得以通信失败 **伪造支付失败**，不得扩大 Abort 资格（Abort 仅限网关授权前，AGENT §4.9）。

### 决策点 Q-2.c：资金结果未知时的"下一笔准入"

| 选项 | 说明 | breaking | 回归影响 |
|---|---|---|---|
| C1 仅放行"独立新订单"，补扣同一订单须先 query | 区分独立新订单 vs 同订单补扣；306 强制 query-first | 否 | 须有机制区分两类意图；对齐 AGENT §4.5 |
| C2 一律放行，由集成方自行判断 | SDK/Host 不区分 | 否 | ❌ 存在重复扣款风险，与 306 query-first 铁律冲突，需明确风险接受方 |

> "允许下一笔"须拆成四级独立判定：① 调用端退出等待 ② SDK 可提交 ③ 连接就绪 ④ Host 真正可执行。
> 任一选项都要指明它满足到第几级，不能用"清 busy"冒充第 ④ 级（设计 §3.3.3）。
> 本轮裁决：Q-2.a/A1 要求满足到第 ④ 级（引擎/硬件退出证据）才清 busy；Q-2.b 的本地强制释放在 10s 窗后
> 也须达到第 ④ 级"本地资源已释放"，再放行下一笔。

### Q-1 / Q-2 候选 AC 草案（⚠️ 未经 spec-check · 待建 SCR · TC 为计划编号）

> 以下 AC 固化上述两份维护者裁决，随 SCR 批准后回写 coverage R6/R9 的 acceptance 与规格 §8。
> R6 聚焦"关交易 + 放行下一笔"的接口边界；R9 聚焦"Host 保留/续推 vs 新关闭政策"的时序与 E213 收口。
> SDK 侧 `verification_method=local_fake_transport_test`；Host 侧阶段/引擎释放须设备或 Host 集成验证。

```text
AC6（映射 R6，计划 TC-RCL-011）：
  Given 已建立连接且一笔交易在途，发生通信 onError
  When  SDK 在统一 10s 窗内尝试既有单次恢复且最终未成功
  Then  SDK 发出恰好一次最终 onDisconnected(E213)；tapro 收到后关闭该在途交易并使下一笔可进入；
        恢复窗内（未到 10s 且未判定失败）不得提前关交易，也不得提前放行下一笔
```

```text
AC6-NEXT（映射 R6，计划 TC-RCL-012）：
  Given 恢复 10s 内未成功、tapro 已关闭在途交易
  When  集成方提交一笔新 transactionRequestId 的交易
  Then  新交易可正常进入并执行（第 ④ 级 Host 可执行已满足）；
        SDK/Host 不对上一笔下 approved/declined 结论；对上一笔的补扣/冲正须集成方先 query 原 ID 再决定
```

```text
AC9（映射 R9，计划 TC-RCL-013）：
  Given LAN/Cable 被动掉线且仍有在途绑定（Host active R3 保留分支）
  When  10s 统一窗内 SDK 恢复成功
  Then  按 traceId 续推原交易，tapro 保留分支不发 onDisconnected、不关交易（与现有 R3 一致）；
        仅当 10s 窗耗尽恢复失败，才转入 AC6 的最终 onDisconnected(E213) + 关交易路径
```

```text
AC9-STAGE（映射 R9，计划 TC-RCL-014）：
  Given 10s 窗耗尽恢复失败，且交易正处于 PIN/联机/签名/移卡等不可取消阶段
  When  tapro 执行最终关交易
  Then  本地释放交易占用（busy/MessageForwardPage/引擎执行）并放行下一笔，须有引擎/硬件退出证据（非仅清标志）；
        全程不向支付网关发送 Abort，不扩大 Abort 资格，不将通信失败映射为资金 declined
```

> 边界说明（供评审核对，非新增规则）：
> - 10s 为 SDK established recovery 预算与 Host 关交易等待窗的**同一统一值**；5s→10s 改已批准基线，
>   须配套 SDK established recovery SCR（含 `EstablishedRecoveryBudgetTest` 期望同步）。
> - AC6/AC9 不改 established recovery 的"一次恢复、原 deadline 不延长、不重发支付"等已批准语义，仅改预算数值。
> - AC9-STAGE 的"本地强制释放"是 Host 行为，须在 tapro 的 `SPEC-HOST-LAN-TRACEID-REBIND-001` SCR 内记录，
>   含维护者的联机阶段风险接受声明；不改任何 public 签名。

---

## Q-3 —— 手动 `disconnect()`/`cleanup()` 对已发送在途请求的通知契约【展开版 · 已裁决 · 含候选 AC 草案】

- **风险**：high ｜ **阻塞**：R7 ｜ **来源**：SRC-11（UNSPECIFIED）
- **可闭环性**：这是 **SDK 单仓库内可闭环** 的决策点，不依赖 Host，建议优先裁决。

> ### ✅ 维护者裁决（2026-10-19）
>
> | 决策点 | 选定 | 结论 |
> |---|---|---|
> | Q-3.a 通知语义 | **A2 补发 `onFailure(E213)`** | 集成方主动 disconnect 时，已发送在途请求收到恰好一次 `onFailure(E213, 原ID)`，与排队项 E213 对齐 |
> | Q-3.b 清理顺序 | **B2 先快照后锁外通知** | 临界区内快照待通知项并清理，锁外逐一通知，防重入死锁 |
>
> ⚠️ **配套记录义务**：A2 属**公开可见行为变更**（原静默 → 现 onFailure），须在 `RELEASE-NOTES.md` 标注 💥，
> 评估 `tapro`/`taplinkdemo` 消费者回归；不改任何 public 签名。候选 AC 采用 AC7-A2 + AC7-A2-ORDER。

### Q-3.0 现状事实基线（DERIVED，静态源码核实，非设备复现）

> 以下为 `dev_pro` 当前基线的静态事实，用于把两类"在途"分清；是候选 AC 的事实前提，不是新规则。

`TaplinkApiImpl.disconnect()`（`TaplinkApiImpl.kt:352–358`）先 `connectionManager.disconnect()`，
再 `paymentManager.clearAllCallbacks()`。`clearAllCallbacks()`（`PaymentManager.kt:861–878`）内部：

| 在途类型 | 现状行为 | 源码证据 |
|---|---|---|
| **已发送在途请求**（已序列化发出、等结果） | `callbackManager.clearAll()` —— **静默清理，不补发回调** | `PaymentManager.kt:863` |
| **已排队未发送交易**（`pendingTransaction`，连接前排队） | `onFailure(E213, "disconnected before the queued transaction was sent")` —— **已有明确失败通知** | `PaymentManager.kt:867–876` |

> 结论：Q-3 要裁决的只是**"已发送在途请求"**那一行；排队未发送项已经有确定的 E213 契约，不在本决策范围，
> 也不应被本决策改动。

### 决策点 Q-3.a：已发送在途请求的通知语义

| 选项 | 说明 | breaking | 回归影响 |
|---|---|---|---|
| **A1 维持现状：静默清理** | 手动断开不对已发送请求补发回调（固化 `callbackManager.clearAll()` 现状） | 否（行为不变） | 仅需补文档/测试固化现状；最小风险 |
| **A2 改为 `PaymentCallback.onFailure` 通知** | 手动断开时对在途已发送请求补发技术失败回调（与排队项 E213 对齐） | **是**（回调时序行为变更，消费者可见） | 回调重入（onFailure 内再调 disconnect）、与后续旧清理竞态、Tapro/Demo 回归；需在 RELEASE-NOTES 标注 💥 |

### 决策点 Q-3.b：清理顺序与重入边界（仅当 Q-3.a = A2 时才需裁决）

| 选项 | 说明 | breaking | 回归影响 |
|---|---|---|---|
| B1 先通知后清理 | 先发 onFailure 再 `callbackManager.clearAll()` | 是（随 A2） | 须保证不在 registry 锁内回调应用，防重入死锁 |
| **B2 先快照后锁外通知（推荐）** | 先在临界区快照待通知项并清理，锁外逐一通知 | 是（随 A2） | 与既有 `failAllPendingTransactions` 快照清理模式一致，天然对齐 once/fencing |

### Q-3 候选 AC 草案（⚠️ 未批准 · 人工裁决前不得作为 TDD 期望 · TC 为计划编号）

> 下列 AC 随 Q-3.a 的裁决**二选一生效**：选 A1 用 AC7-A1；选 A2 用 AC7-A2（+ 按 Q-3.b 选 B1/B2）。
> 裁决落地时，R7 由 BLOCKED → REVIEW_REQUIRED，并把选中的 AC 回写到 coverage R7.acceptance 与规格 §8。
> `verification_method` 建议 `local_fake_transport_test`，与 R1–R5 同族。

```text
# 若 Q-3.a = A1（维持静默清理）
AC7-A1（映射 R7，计划 TC-RCL-009，前提 Q-3.a=A1）：
  Given 已建立连接且存在一笔"已发送在途"请求（已序列化发出、未终局），原 deadline 未到
  When  集成方主动调用 disconnect()
  Then  该已发送请求被静默清理，不触发 PaymentCallback.onSuccess/onFailure；
        且任何"已排队未发送"交易仍按现状收到 onFailure(E213) 一次；
        后续该请求的迟到响应/原 timer 不得复活或补发任何回调（最多一次终局，此处为静默终局）
```

```text
# 若 Q-3.a = A2（改为 onFailure 通知）
AC7-A2（映射 R7，计划 TC-RCL-009，前提 Q-3.a=A2）：
  Given 已建立连接且存在一笔"已发送在途"请求（已序列化发出、未终局），原 deadline 未到
  When  集成方主动调用 disconnect()
  Then  该请求收到恰好一次 PaymentCallback.onFailure(E213, transactionRequestId=原ID)，
        onFailure 仅表达通信/系统级断开、不表达资金结果（AGENT §4.2）；
        "已排队未发送"交易仍各自收到 onFailure(E213) 一次；
        同一请求不因后续迟到响应/原 timer 二次通知（一次终局、去重保持）
```

```text
# 仅当 Q-3.a = A2 时追加，固化清理顺序与重入安全
AC7-A2-ORDER（映射 R7，计划 TC-RCL-010，前提 Q-3.a=A2 且 Q-3.b=B2）：
  Given 一笔已发送在途请求，其 PaymentCallback.onFailure 实现内部会再次调用 disconnect()（重入）
  When  手动 disconnect() 触发通知
  Then  通知在 registry/callback 锁之外进行，不发生死锁或重入导致的二次清理；
        重入的 disconnect() 为幂等空操作（已无在途项可清理），不产生额外回调
```

> 边界说明（供评审核对，非新增规则）：
> - A1/A2 都**不改变** `onSuccess≠approved`、金额最小单位、306 query-first、307/308 新 ID 等 AGENT §4 铁律。
> - A1/A2 都**不触及** established recovery 中的"恢复期间保留在途、恢复失败走最终断开一次清理"（R3）——
>   本决策只覆盖**集成方主动 disconnect** 这一路径，不覆盖通信故障触发的自动恢复/最终断开路径。
> - 若选 A2，属公开可见行为变更，须在 `RELEASE-NOTES.md` 标注，并评估对 `tapro`/`taplinkdemo` 消费者回归；
>   不新增/不修改任何 public 签名（`disconnect()`、`PaymentCallback` 均不变）。

---

## Q-4 —— 各 adapter 的 onError 故障归属与请求终局分类【展开版 · 技术矩阵已核实 · 待政策裁决】

- **风险**：high ｜ **阻塞**：R8 ｜ **来源**：SRC-12（UNSPECIFIED）
- **本轮已完成**：逐 adapter `performSendData`/onError 入口已**一手源码核实**（非设计 §2.2 二手引用），
  产出下方判定矩阵（Q-4.a = A1）。R8 可据此从 BLOCKED 推进到 REVIEW_REQUIRED。
- **仍需你裁决**：矩阵中标 **🟡 待政策** 的少数格子——即"这个入口是否允许不经恢复直接判请求终局"。

### Q-4.0 一手源码核实结论（DERIVED，`dev_pro` 基线静态核实）

两个比设计 §2.2 更精确的事实，直接影响分类：
1. **"未就绪拒发" 与 "发送中途失败" 是两类不同事件，错误码也不同**：未就绪拒发（VSP E202 / RS232 E255 /
   AOA "Not connected"E304 / Local iTransaction==null E304）是**请求从未上链路**，可直接判终局；发送中途
   IOException（统一 E304）才可能是"线抖"，应先走恢复。
2. **Local/AIDL 的 onError 不直接调 `InnerCallback`**，而是封成带 traceId 的 ERROR 帧经 `dataReceiver` →
   ResponseProcessor（`LocalServiceKernel.kt:193–212`）。它的终局归属点与 Cable 完全不同，**不能套同一规则**。

### Q-4.a 技术判定矩阵（源码核实，不改公开 API）

> 分类口径：🟢 = 走 established recovery（10s 窗内自动恢复）；🔴 = 明确非恢复，直接判请求终局；
> 🟡 = **待你政策裁决**。"已建立?" 指出错时连接是否曾成功建立。

| adapter | 入口（源码行） | 错误码 | 性质 | 已建立? | 建议分类 |
|---|---|---|---|---|---|
| VSP | `VSPClientKernel.kt:255` 未就绪拒发 | E202 | 请求未上链路 | 否/边界 | 🔴 直接终局 |
| VSP | `VSPClientKernel.kt:269` 发送 IOException + `handleConnectionError()` | E304 | 发送中途失败 | 是 | 🟢 先恢复 |
| VSP | `VSPClientKernel.kt:274` 发送其他 Exception（无连接处理） | E304 | 发送中途失败 | 是 | � 先恢复（P1 裁决，与 IOException 一致） |
| RS232 | `SerialServiceKernel.kt:333` 未就绪拒发 | E255 | 请求未上链路 | 否/边界 | 🔴 直接终局 |
| RS232 | `SerialServiceKernel.kt:349` 发送 IOException + `notifySerialLinkLost()` | E304 | 发送中途失败 | 是 | 🟢 先恢复 |
| RS232 | `SerialServiceKernel.kt:354` 发送其他 Exception（无链路通知） | E304 | 发送中途失败 | 是 | � 先恢复（P1 裁决，与 IOException 一致） |
| AOA | `CableAoaHostKernel.kt:323/330/338` 未连接/端点缺失/锁后失联 | E304 | 请求未上链路 | 否 | 🔴 直接终局 |
| AOA | `CableAoaHostKernel.kt:363` bulk 失败（已内部重试一次）+ `scheduleKernelDisconnect()` | E304 | 发送中途失败 | 是 | 🟢 先恢复（内部 bulk 重试 ≠ 业务重发支付） |
| AOA | `CableAoaHostKernel.kt:372` 发送异常 | E304 | 发送中途失败 | 是 | � 先恢复（P1 裁决，与 IOException 一致） |
| Local/AIDL | `LocalServiceKernel.kt:176` iTransaction==null 拒发 | E304 | 请求未上链路 | 否 | 🔴 直接终局 |
| Local/AIDL | `LocalServiceKernel.kt:201` AIDL 回调 onError → ERROR 帧经 dataReceiver | 透传 | 经响应通道 | 是 | ⚪ P3 确认：经 ResponseProcessor 按 traceId 终局，不进连接恢复 |
| SUB_SCREEN | 映射 VSP（`ProtocolConfigResolver.kt:43`）+ 屏初始化就绪 | 同 VSP | 同 VSP + 屏就绪 | — | 复用 VSP 分类，但屏未就绪另计为 🔴 |

### Q-4.b 政策边界裁决（✅ 已裁决，2026-10-19）

| # | 场景 | 裁决 | 约束 |
|---|---|---|---|
| P1 | VSP/RS232/AOA 发送路径的"**其他 Exception**"分支（非 IOException、未触发连接处理） | **🟢 先恢复**（与 IOException 一致走 10s 恢复） | 不得按异常类名武断 finish |
| P2 | AOA bulk 失败已内部重试一次 | **确认**：内部 bulk 重试**不计入** established 10s 预算、**不等于**业务重发支付 | 不扩大重发 |
| P3 | Local/AIDL 的 onError 经 ERROR 帧走 ResponseProcessor | **确认**：其"请求终局"由响应通道按 traceId 判定，**不进连接恢复** | 进程死亡另按已批准恢复路径核对 |
| P4 | "未就绪拒发"（E202/E255/E304-未连接/iTransaction null）统一判 🔴 直接终局 | **确认**：请求从未上链路，直接一次 `onFailure`、不触发 10s 恢复 | 按"是否已上链路"判定，不按方法名/字符串 |

### Q-4 候选 AC 草案（⚠️ 待 P1–P4 裁决后定稿 · TC 为计划编号）

```text
AC8（映射 R8，计划 TC-RCL-015，已据 P1–P4 定稿）：
  Given 某 adapter 在 performSendData 报 onError
  When  按判定矩阵归类（未上链路 / 发送中途失败 / 经响应通道）
  Then  未上链路错误（VSP E202 / RS232 E255 / AOA 未连接 E304 / Local iTransaction null E304）
        直接一次请求终局，不触发 10s 恢复（P4）；
        发送中途失败（IOException 及其他 Exception 分支，统一 E304）先走 established 单次恢复、
        不抢先 finish（P1）；AOA 内部 bulk 重试不计入 10s 预算、不等于业务重发（P2）；
        Local/AIDL ERROR 帧经 ResponseProcessor 按 traceId 终局、不进连接恢复（P3）；
        全程不按方法名或错误字符串武断判死
```

> 边界说明：矩阵仅澄清**分类**，不改任何 public 签名、不改错误码定义、不新增重发。P1–P4 定稿后回写
> coverage R8（BLOCKED→REVIEW_REQUIRED）与规格 §8。

---

## 汇总：裁决即可解锁的 gate

| 决策点 | 解锁规则 | 预期 gate 变化 |
|---|---|---|
| Q-3.a + Q-3.b | **已裁决 A2+B2**：R7 → REVIEW_REQUIRED，回写 AC7-A2 + AC7-A2-ORDER，映射 TC-RCL-009/010 | critical_acceptance_coverage 5/9 → 6/9 |
| Q-4.a + Q-4.b | **全部已裁决**（P1🟢 / P2-P4 确认）：R8 → REVIEW_REQUIRED，回写 AC8/TC-RCL-015 | critical_acceptance_coverage → 7/9 |
| Q-1（a/b/c）+ Q-2（a/b/c） | **已裁决**：R6、R9 → REVIEW_REQUIRED，回写 AC6/AC6-NEXT/AC9/AC9-STAGE（TC-RCL-011~014）；SRC-13 冲突经 C1+10s 统一值消解 | critical_acceptance_coverage → 9/9；unresolved_high_risk → 0；unresolved_conflicts → 0 |

> 全部解锁后，SCR-ONERROR-CALLBACK-LIFECYCLE-001 方可由 PENDING 进入审批；R6/R9 的 Host 侧裁决
> **还须在 tapro 仓库对 `SPEC-HOST-LAN-TRACEID-REBIND-001` 另立 L3 SCR 并由 Host 规则负责人批准**，
> 不能用本 SDK SCR 代批（上级 AGENT 铁律 13 + 本仓库 §11.1）。

## 维护者裁决记录（勾选后回填）

| 决策点 | 选定选项 | 裁决人 | 日期 | 备注 / 新增 SCR 编号 |
|---|---|---|---|---|
| Q-1.a | A1 本地断开感知为准 | SDK+Host 规则负责人（同一人） | 2026-10-19 | 不新增跨端指令 |
| Q-1.b | 统一 10s（两端同值） | SDK+Host 规则负责人 | 2026-10-19 | 待建：SDK established recovery 5s→10s SCR（含 budget 测试期望同步） |
| Q-1.c | C1 以 E213 为准 | SDK+Host 规则负责人 | 2026-10-19 | 待建：tapro `SPEC-HOST-LAN-TRACEID-REBIND-001` R9 文案修订 SCR |
| Q-2.a | A1 取消+等释放后放行 | SDK+Host 规则负责人 | 2026-10-19 | 清 busy 须有引擎/硬件退出证据 |
| Q-2.b | 本地强制中止（非网关 Abort） | SDK+Host 规则负责人 | 2026-10-19 | 待建 Host SCR；含联机阶段风险接受声明 |
| Q-2.c | C1 本地释放 + 集成方 query | SDK+Host 规则负责人 | 2026-10-19 | 对齐 306 query-first；不下资金结论 |
| Q-3.a | A2 补发 onFailure(E213) | SDK+Host 规则负责人 | 2026-10-19 | 公开可见行为变更，须标 RELEASE-NOTES 💥 |
| Q-3.b | B2 先快照后锁外通知 | SDK+Host 规则负责人 | 2026-10-19 | 防重入死锁 |
| Q-4.a | A1 技术判定矩阵（已建、源码核实） | SDK+Host 规则负责人 | 2026-10-19 | 矩阵见 Q-4.a 节 |
| Q-4.b | P1🟢先恢复 / P2·P3·P4 确认 | SDK+Host 规则负责人 | 2026-10-19 | 4 个政策边界全部定稿 |
