---
name: tdd
description: TapLink SDK 的新功能和行为缺陷开发流程，按 Spec 的 AC 先确认用例，再执行红绿重构、集成验证、评审和文档回写。
---

# TapLink SDK TDD 流程

先读根目录 `AGENT.md`、`docs/spec/SPEC_GUIDE.md` 和相关 `.cursor/rules/taplink-*.mdc`。沿用 Tapro 的两道人为闸门：Step 0 确认需求方向和影响范围，Step 3 确认用例与期望；已在当前任务明确确认的结论可直接复用。此流程只在用户明确要求 TDD 或同意使用时启动，普通改动按仓库规范做必要验证。

## 新功能 Step 0–10

0. 澄清需求、列可选方案、公开 API 与 tapro/taplinkdemo/sunbay-open-docs 影响、支付红线；向维护者确认方向。确认前不改实现。
1. 查现有 `docs/spec/` 的 active spec、设计与代码先例；缺行为契约时先运行 spec-authoring 五步并取得批准。确定可测试的纯逻辑及连接/硬件集成边界。
2. 查必要的 Android/协议一手文档，记录来源版本和链接；不以搜索结果代替 spec 裁决。
3. 从 AC 产出用例表（输入、期望、边界、依据 `spec_id §AC`、测试层级），建立 AC→测试覆盖报告；请维护者确认，未确认不得写测试或实现。
4. 对可在 JVM 隔离的逻辑逐例执行 RED → GREEN，记录失败断言与通过结果。不要用编译错误冒充 RED。
5. 在绿灯保护下重构，保持公开 API 和行为契约。
6. 实现 SDK 与内部通信层接线；按 APP-to-APP/LAN/Cable/Cloud 实际影响补集成或设备场景。公开/内部包边界仍遵守 `AGENT.md §2`。
7. 运行受影响模块测试、编译与必要连接/设备回归；支付相关场景检查 `onSuccess` 三态、306 查询优先、请求 ID 和金额单位。
8. 审查 API 兼容性、线程、连接清理、安全日志及测试与 AC 的一致性；缺陷回流到用例或 SCR，不静默改测试期望。
9. 需真机/双 App/网络/USB 时列验证场景和设备前提；执行前由维护者确认场景。无法执行时记录残留风险。
10. 回写 spec、coverage、SCR 与测试证据，运行 `spec-check`；更新必要 SDK 文档与 RELEASE-NOTES。涉及发布按 `AGENT.md §8` 停在人工 AAR/Maven 节点，确认实际版本后再更新下游。

## Bug 修复 B1–B5

复现并固定输入/实际/期望 → 定位根因 → 对可测试逻辑先补失败回归用例 → 最小修复 → 回归与文档回写。若缺失的是业务定义，创建 SCR 并经负责人裁决，不把测试人员或 AI 的预期直接变成正式 SDK 规则。若修复扩大为新行为或跨模块方案，退回 Step 0。
