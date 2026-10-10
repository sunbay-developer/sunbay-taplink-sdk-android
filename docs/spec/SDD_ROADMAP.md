# TapLink SDK SDD 建设 Roadmap

目标链：原始需求/设计 → Spec RULE → AC → 测试 TC → SDK/通信层实现 → 验证 → SCR 变更。

| 能力 | 状态 | 产物 |
|---|---|---|
| 来源分类与四级证据链 | 已建立规范 | `SPEC_GUIDE.md`、coverage 模板 |
| 五维完整性与 SDK 专用清单 | 已建立规范 | spec-authoring skill、审查模板 |
| SCR 强制追溯与 L1/L2/L3 审批 | 已建立规范 | SCR 模板、`docs/spec/changes/` |
| Spec/TDD 人工闸门与 AC→测试衔接 | 已建立规范 | spec-authoring、tdd skill |
| coverage/SCR 机械校验 | 已有初版 | `config/speccheck/spec-check.sh` |
| 现有 SDK 行为逐域迁移为 active spec | 待执行 | 连接、交易、错误码、公开 API 等逐域审查 |
| AC↔测试/实现自动回链 | 待建设 | 测试标 `spec_id §AC`、实现规则标记 |
| Spec 索引与盲区检测 | 待建设 | `docs/spec/INDEX.md`、领域台账 |
| 已批准规则与代码漂移检测 | 待建设 | CI 对变更文件和 SCR 做关联检查 |
| 验证结果回写到 AC | 待建设 | 测试证据、设备场景结果和版本 |

**当前边界**：校验器只验证已录入 coverage/SCR 的结构与部分引用，不能证明历史行为已被覆盖，也不能证明原文在语义上支持规则。先按风险迁移交易结果、超时/查询、请求 ID、金额，再覆盖连接模式和公开 API。每域经独立审查和维护者批准后才标 active；不批量伪造历史批准记录。
