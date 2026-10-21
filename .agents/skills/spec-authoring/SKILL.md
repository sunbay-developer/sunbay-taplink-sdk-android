---
name: spec-authoring
description: 将 SDK 需求或设计转成可追溯、可验证的 Spec；执行源分析、规格生成、独立审查、缺口分析、人工批准五步，并维护 SCR。
---

# TapLink SDK Spec Authoring

先读 `docs/spec/SPEC_GUIDE.md`、根目录 `AGENT.md`。本 skill 与 Tapro 的五步流水线、来源分类、量化门禁、SCR 分级一致，SDK 领域检查清单以本仓库为准。

## Superpowers 辅助路由

按 `docs/spec/SPEC_GUIDE.md §2.1` 接入 <https://github.com/obra/superpowers>：源分析用 `brainstorming` 澄清，独立完整性复核用 requesting/receiving-code-review 方法，批准后用 `writing-plans` 关联 Spec 版本/RULE/AC 拆任务，收尾用 `verification-before-completion` 检查实际证据。先读项目规范，再读取可用技能；未安装时说明并执行下列本地五步，不伪称调用。

上游设计/计划确认不能替代 Human Review，独立 AI 审查不能批准高风险规则。计划和审查笔记放仓库外会话目录，不将上游计划路径写入正式 Spec 目录。无独立上下文时报告审查待完成。worktree/代理/提交与发布遵守本地授权，不删除用户已有改动。

## 五步流水线

1. **Source Analyzer**：逐项摘取原始需求/设计/协议文档，标 `EXPLICIT/DERIVED/PROPOSED/UNSPECIFIED/CONFLICT`。EXPLICIT 记录文档 ID、版本、仓库路径、章节、逐字原文。不得把推测标为明确需求。
2. **Spec Generator**：按 `assets/templates/spec.md` 和 `assets/templates/spec-coverage.yaml` 产出 `docs/spec/` 的正式文件。每条 RULE 关联来源；关键 RULE 关联可断言的 Given/When/Then AC。
3. **Completeness Reviewer**：独立会话先从原始材料盲查五维和 SDK 清单，再看生成物找差。报告使用 `assets/templates/spec-review.md`，留在会话工作目录，不进版本库。无独立 agent 可用时由不同会话/人工审查，不能自称独立复核已经完成。
4. **Gap Analysis**：记录缺口、风险、冲突、待确认，运行 `./config/speccheck/spec-check.sh`；阻断项清零后才提交审批。机械检查不能替代原文语义核对。
5. **Human Review**：负责人裁决高风险行为和关键冲突，写入批准人/时间；然后 `status: active`、`approved: true`。未经批准的规则不得进入实现。

SDK 清单：APP-to-APP/LAN/Cable/Cloud、连接与重连、交易结果三态、回调线程、最小货币单位、请求 ID 与 306 查询优先、307/308 换 ID、Refund/Abort、错误码映射、公开 API 兼容性、凭证与日志、生命周期清理。每项适用性写明；N/A 给理由。

对已 active spec 的 L2/L3 变更，先使用 `assets/templates/spec-change-request.yaml` 创建 SCR。类型 A `SOURCE_OMISSION` 与类型 B `TEST_DISCOVERED_GAP`/`DESIGN_DECISION` 必须区分；类型 B 审批前保持 PROPOSED。批准后联动 §0 Changelog、coverage、AC、用例和实现。发布与下游文档同步遵守 `AGENT.md` 的人工节点。
