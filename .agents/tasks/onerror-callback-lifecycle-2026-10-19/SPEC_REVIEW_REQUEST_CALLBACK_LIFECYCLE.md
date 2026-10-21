# Spec 完整性独立审查报告

> 过程产物，不进版本库（落会话目录）。本报告以「源文档 + 五维清单 + SDK 清单」为输入先盲查应覆盖项，
> 再对照 spec.md 找差，并结合 decisions 文档记录的 Q-1~Q-4 人工裁决评估其对门禁的影响。
> 结论只回答"是否可批准 + 为什么"，不写实现建议。

- spec_id：SPEC-REQUEST-CALLBACK-LIFECYCLE-001
- 被审 spec：docs/spec/TAPLINK_REQUEST_CALLBACK_LIFECYCLE_SPEC.md @ v1.0（status: draft）
- 配套件：
  - coverage：docs/spec/coverage/SPEC-REQUEST-CALLBACK-LIFECYCLE-001.yaml
  - SCR：docs/spec/changes/SCR-ONERROR-CALLBACK-LIFECYCLE-001.yaml（PENDING）
  - 裁决清单：docs/spec/TAPLINK_ONERROR_CALLBACK_LIFECYCLE_DECISIONS.md（Q-1~Q-4 已人工裁决）
- 源材料：
  - docs/spec/sources/TAPLINK_ONERROR_CALLBACK_LIFECYCLE_REQUEST.md（S1–S5 原文）
  - docs/spec/TAPLINK_ESTABLISHED_RECOVERY_SPEC.md v1.2（已批准基线）
  - docs/architecture/TAPLINK_ONERROR_CALLBACK_LIFECYCLE_DESIGN.md v2.0（设计方案）
  - AGENT.md §4 交易语义铁律
- 审查人/模型：独立复核（与 Generator 分离会话）
- 日期：2026-10-19

---

## 0. 审查范围与裁决输入说明

本报告在两个时点之间评估：
- **裁决前**（spec v1.0 draft 原始状态）：R6–R9 为 BLOCKED，4 个 open question 未决，门禁多项红。
- **裁决后**（decisions 文档记录维护者 2026-10-19 对 Q-1~Q-4 的结论）：裁决已做出，但**尚未回写**
  coverage/规格/SCR，也**未建立** established recovery 预算变更所需的配套 SCR，Host 侧 SCR 亦未在 tapro 建立。

因此本报告的核心判断是：**裁决已消除"无依据"这一阻塞成因，但证据链回写与跨仓库 SCR 尚未落地，spec 仍不可批准。**

---

## 1. 五维完整性缺口

| 维度 | 结论 | 缺口描述 | 风险 | 关联问题 |
|---|---|---|---|---|
| 业务流程完整性 | 裁决后完整、待回写 | onError→恢复→最终断开关交易全链路已由 Q-1/Q-2 裁决（10s 统一窗、失败后 E213 关交易）；但 coverage business_flow 仍标 partial/gap_ref Q-1 | high | Q-1 |
| 状态与规则完整性 | 完整 | R4 原 deadline/一次终局已对齐已批准基线；Q-2 已明确 Host 退出证据与下一笔准入 | — | Q-2 |
| 接口与系统边界完整性 | 裁决后完整、待回写 | R5 恢复期 E213 稳定；R7 手动断开通知已裁 A2（补 onFailure E213）；coverage interface_boundary 仍 partial | high | Q-3 |
| 异常与风险完整性 | 裁决后完整、待回写 | R8 adapter 分类已出判定矩阵 + P1–P4 裁决；R9 Host 保留窗与新关闭政策冲突已由"10s 统一值 + E213"消解；coverage 仍标 CONFLICT 未 resolve | critical | Q-1/Q-4 |
| 验收与验证完整性 | 不完整 | R6–R9 的候选 AC（AC6/AC6-NEXT/AC9/AC9-STAGE/AC7-A2/AC8）仅存在于 decisions 文档，尚未进入规格 §8 与 coverage rules；全部 TC 为计划编号，未实现/未运行 | critical | 全部 |

## 2. SDK 专用清单缺口

仅列适用但未覆盖或适用性存疑的项：

| 清单项 | 结论 | 说明 | 关联问题 |
|---|---|---|---|
| 连接模式-逐 adapter 分类 | 裁决后覆盖、待回写 | VSP/RS232/AOA/Local/SUB_SCREEN 的 onError 归属已由 Q-4 判定矩阵 + P1–P4 覆盖；矩阵仍在 decisions 文档，未回写 coverage R8 | Q-4 |
| 交易生命周期-超时/结果未知 | 裁决后覆盖、待回写 | Q-2.c 已定"本地释放 + 集成方 query-first"；AC6-NEXT 未入规格 | Q-2 |
| 回调三态与线程 | 覆盖 | R11 保留 AGENT §4 三态/后台线程，AC11 已在规格 | — |
| 请求 ID 与 306/307/308 | 覆盖 | R11/AC11 保留 query-first、新 ID 语义 | — |
| Refund/Abort | 覆盖 | Q-2 明确"本地释放≠网关 Abort"，不扩大 Abort 资格 | Q-2 |
| 公开 API 与兼容性 | 存疑-待回写 | Q-3 A2 属公开可见行为变更（静默→onFailure），须 RELEASE-NOTES 标注并评估 Tapro/Demo 回归；尚未记录 | Q-3 |
| 跨仓库协议一致性 | 阻塞 | Q-1/Q-2 的 Host 侧（10s 窗、E213 文案、本地强制释放、联机风险接受）须在 tapro 对 SPEC-HOST-LAN-TRACEID-REBIND-001 另立 L3 SCR，当前未建 | Q-1/Q-2 |
| established recovery 预算基线 | 阻塞 | Q-1.b 把已批准的 5000ms 预算改为 10000ms，属改 active 基线（L3），须单独 SCR + budget 测试同步；当前未建 | Q-1 |

## 3. 五问复核（Step 3 固定项）

1. **五维哪一维有缺口？**
   → 验收与验证维仍不完整（R6–R9 候选 AC 未进规格 §8/coverage）；业务流程、接口边界、异常风险三维在**裁决后内容已完整**，但 coverage 仍标 partial/CONFLICT，属"已裁未回写"。

2. **哪些 DERIVED 推导不成立？**
   → 无推导错误。设计 §2.2 的 adapter onError 入口为 DERIVED；本轮已逐入口一手源码核实（VSPClientKernel/SerialServiceKernel/CableAoaHostKernel/LocalServiceKernel），结论与设计一致，并补正两点更精确的事实（"未就绪拒发"与"发送中途失败"错误码不同；Local/AIDL 经 dataReceiver→ResponseProcessor 而非直接 InnerCallback），已纳入 Q-4 矩阵。推导成立。

3. **哪些 UNSPECIFIED 被当成了确定规则？**
   → 裁决前：无（Generator 正确地把 R6–R9 标为 BLOCKED，未冒充确定规则，这点合规）。
   → 裁决后：需警惕反向风险——维护者裁决已把 UNSPECIFIED（SRC-10/11/12）转为可定规则，但在回写时**必须据 decisions 文档的裁决原文**回填，不得由 AI 扩写超出裁决范围（如 Q-2 的"本地释放"不得被回写成"向网关 Abort"）。

4. **哪些规则无对应 AC（不可验收）？**
   → 当前规格 §8：R6、R7、R8、R9 四条 critical 规则在**规格内**仍无 AC（候选 AC 只在 decisions 文档）。回写前，coverage 的 critical_acceptance_coverage 维持 5/9。

5. **有无未标注的 CONFLICT？**
   → 已标注的 SRC-13（Host 保留窗 vs 新关闭目标；E306/E213 漂移）是唯一关键冲突，decisions 文档已用"10s 统一值 + C1 以 E213 为准"给出消解方向，但 coverage 的 `unresolved_conflicts` 仍为 1，须随回写与 Host SCR 一并 resolve。无新增未标注冲突。

## 4. 待确认问题汇总（输入给 Gap Analysis / Human Review）

> 四个 Q 的**业务方向已由维护者裁决**（见 decisions 文档裁决记录表）。下表反映"裁决已定、但落地动作未完成"的剩余阻塞。

| id | 风险 | 问题（剩余部分） | 来源 | 阻塞 | 裁决状态 / 剩余动作 |
|---|---|---|---|---|---|
| Q-1 | critical | 10s 统一窗 + E213 关交易 | SRC-13/SRC-1 | R6/R9 | ✅ 已裁决；剩余：回写 coverage/规格 + 建 established recovery 预算 SCR + 建 Host SCR |
| Q-2 | critical | 本地释放占用 + 集成方 query-first，不向网关 Abort | SRC-10 | R6/R9 | ✅ 已裁决（含联机阶段风险接受）；剩余：回写 + Host SCR 记录风险接受 |
| Q-3 | high | 手动断开补发 onFailure(E213)，先快照后锁外通知 | SRC-11 | R7 | ✅ 已裁决（A2+B2）；剩余：回写 AC7-A2/AC7-A2-ORDER + RELEASE-NOTES 标注 |
| Q-4 | high | adapter 判定矩阵 + P1🟢/P2·P3·P4 确认 | SRC-12 | R8 | ✅ 已裁决；剩余：回写矩阵与 AC8 到 coverage/规格 |

## 5. 门禁预判（Step 4 据此计算）

> 分"当前（未回写）"与"回写+SCR 落地后（预期）"两栏。

| 门禁 | 当前（未回写） | 回写+SCR 后（预期） | 说明 |
|---|---|---|---|
| 来源追溯率 | 12/12 通过 | 通过 | 裁决把 UNSPECIFIED 转为有 decisions 来源 |
| 关键规则 AC 覆盖率 | 不达标 5/9 | 9/9 | R6–R9 候选 AC 回写规格 §8 后达标 |
| 待确认高风险问题 | 不达标 4 | 0 | 四个 Q 已裁决，回写后清零 |
| 关键冲突 | 不达标 1 | 0 | SRC-13 经 10s 统一值 + E213 消解，随 Host SCR resolve |
| ID 引用完整性 | 需回写后复核 | 通过 | 新 AC/TC 编号入 coverage 后校验 |

## 6. 审查结论

- [ ] 可进入 Human Review（无关键阻塞）
- [x] **当前不可批准**，但阻塞成因已从"无业务裁决"转为"裁决未落地"。批准前须完成以下**可追溯动作**（均属文档/规格层，无需本报告给实现建议）：
  1. 在 SDK 建立 established recovery 预算 5000ms→10000ms 的 L3 SCR（`change_origin=DESIGN_DECISION`，负责人批准），并在其 impact 标注 `EstablishedRecoveryBudgetTest` 期望同步；
  2. 在 tapro 仓库对 `SPEC-HOST-LAN-TRACEID-REBIND-001` 建立对应 L3 SCR（10s 窗 + E213 文案 + 本地强制释放 + 联机阶段风险接受声明）；
  3. 将 decisions 文档的 Q-1~Q-4 裁决回写：coverage 的 R6–R9 由 BLOCKED→REVIEW_REQUIRED、`open_questions[].decision` 填入、`unresolved_conflicts`/`unresolved_high_risk` 清零；规格 §8 补 AC6/AC6-NEXT/AC9/AC9-STAGE/AC7-A2/AC7-A2-ORDER/AC8，§0 Changelog 关联两条 SCR；
  4. 运行 `./config/speccheck/spec-check.sh` 机械校验通过（仅证明结构，不代替本语义复核）；
  5. 由负责人在人工闸门确认后，才将 spec/coverage 置 active/approved。

> 说明：Step 3 只负责"是否可批准 + 为什么"。上述第 1–5 项为**可追溯的流程动作清单**（源自 SPEC_GUIDE 五步流水线与 SCR 机制），非实现方案建议。实现层（改 `RECOVERY_BUDGET_MS`、改测试等）须在相应 SCR 批准后另按 TDD 授权执行，不在本报告范围。
