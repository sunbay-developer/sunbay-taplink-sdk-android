# TapLink SDK Spec 生成与维护规范

> 本规范与 Tapro 的 SDD 流程保持同一证据链和闸门；SDK 公开契约的发布规则仍以 `AGENT.md` 为准。

## 1. 规格的边界与单一事实源

Spec 写可断言的对外或内部行为契约，设计文档写方案和取舍。新建行为契约放 `docs/spec/`；分析记录留在 `docs/`。`status: active` 的 spec 是开发、测试和公开文档的行为依据。若现有代码与 spec 冲突，先核实正确行为；确认 spec 过时后走 SCR，再修改实现。历史文档不会因本规范自动变成 active spec，采用时须逐项迁移和审查。

### 1.1 来源可追溯性：强制硬要求

每条规则必须有可追溯来源或明确标成 `DERIVED`、`PROPOSED`、`UNSPECIFIED`、`CONFLICT`。证据链固定为：

```text
原始需求/方案（版本、章节、原文） → RULE → AC → TC → 实现/验证
```

在 `docs/spec/coverage/{spec_id}.yaml` 为每条 RULE 记录 `source_id`、`source_type`、状态、AC 与测试用例。`EXPLICIT` 来源必须填 `document_id`、`document_version`、`document_path`、`section`、`source_quote`；`source_quote` 必须逐字可在指定源文件找到，审查人确认原文确实支持规则后才能设置 `verified: true`。仅有来源 ID 或原文片段匹配不能证明语义正确。`DERIVED` 必须有 `derivation.derived_from` 和推导理由。源材料未规定的行为保持待确认；高风险规则不得据此进入实现。

### 1.2 规格骨架

使用 `.agents/skills/spec-authoring/assets/templates/spec.md`。front matter 包含 `spec_id/title/status/version/owner/last_reviewed`；正文包含 §0 Changelog、§1 范围、§2 带稳定编号的条件→行为规则、§3 接口与字段、§4 边界异常、§5 示例、§6 复杂流程图、§7 兼容性、§8 Given/When/Then AC。字段要区分未传、显式值和默认回退；金额用最小货币单位；公开 API 的可空性、默认值、错误码和兼容性必须明确。规则和 AC 编号只增不复用。

## 2. 五步 Spec 流水线

1. **Source Analyzer**：读取需求、设计、现有公开文档和协议约束；逐条分类 EXPLICIT/DERIVED/PROPOSED/UNSPECIFIED/CONFLICT。
2. **Spec Generator**：产出 `docs/spec/{主题}技术规格.md` 和 `docs/spec/coverage/{spec_id}.yaml`；每条确定规则映射来源与 AC。
3. **Completeness Reviewer**：独立会话先看原始材料和检查清单，盲查应有规则，再对照草稿；报告写会话目录，不能只依赖 Generator 的归纳。
4. **Gap Analysis**：列缺失、冲突、风险与待确认；运行 `./config/speccheck/spec-check.sh`。🔴 阻断；🟡 要解释。机械检查不替代语义复核。
5. **Human Review**：负责人确认高风险规则与所有待决策问题后，才将 spec/coverage 标为 active/approved。未批准内容不得作为 TDD 用例期望值。

五维检查：业务流程；状态与规则；接口与系统边界；异常与风险；验收与验证。SDK 还须逐项审查连接模式、交易生命周期、回调三态与线程、金额与小费、请求 ID 与 306/307/308、Refund/Abort、生命周期清理、安全日志、公开 API 与兼容性。无关项标 N/A 并说明。

### 2.1 Superpowers 与五步流水线

先读取项目规范，再读取当前环境可用的对应上游技能（<https://github.com/obra/superpowers>）；项目适配详见工作区 `SUPERPOWERS_WORKFLOW.md`，核心约束如下，独立检出也须遵守：

- `brainstorming` 辅助 Source Analyzer 澄清来源和未决规则；讨论产物不自动成为 EXPLICIT 或批准规格。
- `requesting-code-review` / `receiving-code-review` 辅助 Completeness Reviewer 与 Gap Analysis；仍须独立上下文先盲查原始材料，再核对草案。缺独立审查时记待完成，不能用作者自查冒充完成。
- Human Review 保持负责人批准；上游设计、计划或 AI review 通过不替代 Spec/SCR 审批。技能未安装不阻断现有本地流程，但不得伪称已调用。
- 批准后用 `writing-plans` 拆实施任务，每项关联 Spec 版本、RULE/AC、文件、测试及验证命令；计划和审查笔记放仓库外会话目录，正式 Spec/coverage/SCR 仍按本指南落盘。计划不新增行为规则。
- 用例确认后由本地 `tdd` 驱动 RED→GREEN→REFACTOR；发现行为缺口先 SCR。`verification-before-completion` 仅用于核对真实证据，spec-check 通过不证明测试已执行。
- worktree 按需，代理使用遵守任务授权及宿主规则；不得从上游默认步骤推导 commit/merge/发布授权或删除用户改动。SDK 人工 AAR/Maven 节点保持不变。

## 3. 质量门禁与 TDD 衔接

- 来源追溯率、关键源要求覆盖率、关键规则 AC 覆盖率、ID 引用完整性目标均为 100%；未解决的高风险问题和关键冲突为 0。`spec-check` 只能机械核对已有矩阵，**关键源要求是否遗漏**必须由独立审查确认。
- 新功能或行为变更先查 active spec；无覆盖时先走上述流水线。TDD 用例的“依据”列填 `spec_id §AC编号`，测试标记同一编号，并形成 AC→测试覆盖报告。
- 纯实现重构不强造 spec；bug 如暴露新业务规则，先创建 SCR 并取得裁决，再调整测试和实现。
- 需求方向和测试期望值沿用 Tapro 的两道人工闸门；已在当前任务明确批准的结论不重复询问。具体执行见 `.agents/skills/tdd/SKILL.md`。

## 4. Spec Change Request（SCR）：变更追溯强制机制

已批准 spec 的每次实质变更必须创建 `docs/spec/changes/{change_id}.yaml`，使用模板记录 `change_id/spec_id/rule_id/change_type/level/change_origin/reason/source/before/after/impact/approval/target_spec_version/created_at`。Git diff 记录“改了什么”，SCR 记录“为何改、依据、审批、影响”；已批准 spec 不得被 AI 静默覆盖。测试发现缺口先写 SCR，确认是原方案遗漏还是新增设计决策，再更新 spec、coverage、AC、测试与实现。

| 级别 | 条件 | 要求 |
|---|---|---|
| L1 | 仅措辞/拼写，不变行为 | Changelog + 正常评审；可不开 SCR |
| L2 | 边界、错误处理、状态转换或公开行为补充 | SCR + spec owner/测试评审 + 测试更新 |
| L3 | 金额、交易结果、重复扣款、连接协议、错误码语义、密钥安全或破坏性公开 API 变化 | SCR + 对应规则负责人专项批准 + 兼容性/下游影响分析 + 回归 |

`change_origin` 区分 `SOURCE_OMISSION`（原文已有，生成遗漏）与 `TEST_DISCOVERED_GAP`/`DESIGN_DECISION`（原文没有，需要新决策）。后一类获批前 `after.status` 只能是 `PROPOSED`。批准后记录审批人和日期，更新 spec §0 Changelog（L2/L3 必须关联 SCR）、版本、coverage、测试；行为变更与相应 spec 更新同一提交。跨仓库协议/公开 API 变更还须按上级 `AGENT.md` 和本仓库 §8 的人工发版节点处理；在维护者确认实际发布版本前，不更新下游依赖版本。

## 5. 机械校验边界

首次运行前安装 `python3 -m pip install -r config/speccheck/requirements.txt`。`./config/speccheck/spec-check.sh` 检查 coverage/SCR 的结构、字段、来源文件与原文片段、引用和可计算门禁。当前尚无 coverage/SCR 文件时脚本会显示“跳过”；这表示尚未迁移 active spec，**不能当作业务覆盖率通过**。即使有文件且检查通过，也不能证明原文语义支持规则、需求覆盖完整或测试真的执行；这些由独立 Reviewer、测试记录和人工审批承担。
