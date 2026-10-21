---
name: tdd
description: 用户当前任务明确要求启动 TDD 或同意使用时执行的 TapLink SDK 开发流程；按 Spec AC 确认用例，再执行获准的红绿重构、集成验证、评审和文档回写。普通开发请求不自动触发。
---

# TapLink SDK TDD 流程

先读根目录 `AGENT.md`、`docs/spec/SPEC_GUIDE.md` 和相关 `.cursor/rules/taplink-*.mdc`。沿用 Tapro 的两道人为闸门：Step 0 确认需求方向和影响范围，Step 3 确认用例与期望；已在当前任务明确确认的结论可直接复用。此流程只在用户明确要求 TDD 或同意使用时启动，普通改动按仓库规范做必要验证。

## Superpowers 辅助路由

先读本地规范，再按阶段读取可用上游技能：<https://github.com/obra/superpowers>。工作区 `SUPERPOWERS_WORKFLOW.md` 给出完整适配；独立检出时本节同样生效，技能未安装时说明并沿用本地流程，不伪称调用。

| 本地步骤 | Superpowers 方法与交付 |
|---|---|
| Step 0–1 | `brainstorming` 澄清，按本地 spec-authoring 完成来源→RULE→AC 和人工批准 |
| Spec 批准后、Step 3 前 | `writing-plans` 列仓库/文件、Spec 版本/RULE/AC、测试、验证命令和下游依赖；用例仍另经 Step 3 确认 |
| Step 4–7 | `test-driven-development` + `executing-plans`，逐例失败断言→最小实现→重构，再接线回归 |
| B1–B5 | `systematic-debugging` 复现、根因、按批准 AC 补失败回归、最小修复与验证；规则缺口先 SCR |
| Step 8 | `requesting-code-review` / `receiving-code-review`，先规格符合性后代码质量，包含未提交 diff；测试期望变更回 Step 3，行为变化先 SCR |
| Step 10 | `verification-before-completion` 核对真实编译/测试/规格结果、未验证范围和发布交接 |

本地流程主导：上游设计/计划确认不替代 Spec/SCR 和两道人工闸门；既有确认不重复询问。计划、执行日志和审查笔记放仓库外会话目录；禁止依上游要求删除已有代码或用户改动来重做 TDD，不从 commit/merge/cleanup 步骤推导操作授权。worktree 按需、代理按任务授权及宿主规则；无独立上下文时记独立审查待完成，不以作者自查替代。纯逻辑与连接/硬件分层验证、完整 TDD 一次授权、人工 AAR/Maven 发布均保持原规则；本次流程接入不授权后续任务的完整 TDD。

## 执行授权与验证入口

- 按 `AGENT.md §11.1` 和 Tapro `AGENTS.md §13.2.0` 分级执行：常规本地纯逻辑测试和静态检查自动进行；完整 TDD 按任务授权一次，默认包含相关测试、静态预评估和 Step 8 AI Review，用户可明确排除。已包含步骤不重复询问；设备/真实后端测试及发布另行确认。
- 需求方向、用例和 Spec/SCR 批准是业务闸门，与执行授权分开。未启动 TDD 仍须在行为变更前核对批准规格。
- 从 SDK 仓库根执行：必要编译使用 `./gradlew :lib_taplink_sdk:compileDebugKotlin`、`./gradlew :lib_taplink_communication:compileDebugKotlin`（按影响模块选择）；SDK 不使用 Tapro 的 flavor 任务名。
- 相关本地纯逻辑测试自动使用 `./gradlew :lib_taplink_sdk:testDebugUnitTest` 或 `./gradlew :lib_taplink_communication:testDebugUnitTest`。静态检查使用 `git diff --check`，规格记录用 `./config/speccheck/spec-check.sh`；无对象跳过不等于规格已验证。
- 不运行 AAR 打包或 Maven 发布任务，继续遵守维护者人工发布节点。验证结果区分通过、失败、未授权、环境受阻、不适用。

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
