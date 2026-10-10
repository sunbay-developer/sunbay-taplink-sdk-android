# AGENT.md — TapLink SDK (Android) 开发规范

> 本文件是 taplinksdk 仓库的 AI 编码代理行为总纲。
> 生态总览请先阅读上级目录的 [`../AGENT.md`](../AGENT.md)。
> 详细背景规则见 `.cursor/rules/taplink-*.mdc` 系列文件与 `docs/` 目录，冲突时优先遵守更严格的一方。

## 1. 项目定位

`taplinksdk` 是对外发布的支付集成 SDK 源码仓库，Maven 坐标 `com.sunmi:sunbay-taplink-sdk-android`。
它被 `tapro`（Host 端 App）以及所有第三方集成商（含 `taplinkdemo`）依赖使用。

## 2. 模块结构与公开/内部边界（最高优先级规则）

```
Integration App (集成商 / tapro / taplinkdemo)
        │  implementation(project(":lib_taplink_sdk")) 或依赖发布的 aar
        ▼
┌────────────────────┐
│  lib_taplink_sdk   │ ✅ 唯一公开 API，包名根 com.sunmi.tapro.taplink.sdk
└─────────┬──────────┘
          │ embed(project(":lib_taplink_communication"))  — 传递依赖但对外隐藏
          ▼
┌────────────────────────────┐
│ lib_taplink_communication  │ ❌ 内部实现，禁止任何外部代码直接引用
└────────────────────────────┘
```

### 2.1 公开包（可被外部引用）

```
com.sunmi.tapro.taplink.sdk               # 主包（仅命名空间，不放实现类）
com.sunmi.tapro.taplink.sdk.api           # 对外 API 入口
com.sunmi.tapro.taplink.sdk.callback      # ConnectionListener / PaymentCallback / DiscoveryListener / *Adapter(Java 适配器)
com.sunmi.tapro.taplink.sdk.config        # TaplinkConfig / ConnectionConfig
com.sunmi.tapro.taplink.sdk.enums         # ConnectionMode / CableProtocol / CardNetworkType / TipMode / FeeMode / ErrorCategory ...
com.sunmi.tapro.taplink.sdk.model         # model.common / model.request(.transaction) / model.response
com.sunmi.tapro.taplink.sdk.error         # PaymentError / ConnectionError
com.sunmi.tapro.taplink.sdk.scanner       # 扫码相关
com.sunmi.tapro.taplink.sdk.adapter       # Java 互操作适配器
```

### 2.2 内部包（禁止外部使用，新增代码不得导出这些包的类型到公开 API）

```
com.sunmi.tapro.taplink.sdk.impl          ❌ 实现类
com.sunmi.tapro.taplink.sdk.manager       ❌ 连接管理
com.sunmi.tapro.taplink.sdk.protocol      ❌ 协议实现
com.sunmi.tapro.taplink.sdk.persistence   ❌ 持久化
com.sunmi.tapro.taplink.sdk.util          ⚠️ 内部工具（TimeUtils 等极少数放行需评审）
com.sunmi.tapro.taplink.communication.*   ❌ lib_taplink_communication 全部内容
```

**新增/修改任何 public 类前必须自问**：这个类型是否需要暴露给外部集成商？如果不需要，放到 `impl/manager/protocol/persistence` 中并保持 `internal`/包私有可见性。

## 3. 连接模式规范

| 模式 | 说明 | 关键约束 |
|---|---|---|
| APP-to-APP | Intent 唤起，同设备 App 间通信 | 需处理目标 App 未安装/未响应场景 |
| LAN | 局域网发现（mDNS）+ 二维码扫描配对 | 注意 mDNS 并发问题，参考 `docs/SERVICE_DISCOVERY_CONCURRENCY_FIX.md`、`docs/LAN_PACKAGE_*` 系列文档 |
| Cable（USB） | 协议优先级 `AUTO`（推荐，自动尝试 VSP → RS232 → AOA）> `USB_VSP`（CDC-ACM，首选）> `RS232`（十六进制帧）> `USB_AOA`（兜底，配置最复杂） | Android 12+ 需运行时 USB 权限 |
| Cloud | HTTP/云端模式 | 需要 `TAPLINK_CLOUD_*` 相关凭证配置 |

连接重试策略必须使用 `ConnectionConfig` 提供的策略（如 `createHighReliabilityConfig()`），**禁止**手写无限重试循环。

## 4. 交易语义铁律（Critical Semantics — 不可违反）

1. **`onSuccess` ≠ 交易已批准**：调用方必须检查 `PaymentResult.isSuccess()` / `isProcessing()` / `isFailed()`，SDK 内部实现和示例代码也必须体现这一语义，不能简化。
2. **`onFailure` 只代表通信/系统级错误**，绝不能在文档或示例中把卡片拒绝、用户取消等业务结果映射到 `onFailure`。
3. **金额单位**：`AmountInfo` 一律使用最小货币单位（分）。`orderAmount` 与 `tipAmount`/`tipConfig` **互斥**（不可同时设置 tipAmount 和 tipConfig），且 `orderAmount` 不得混入小费金额。
4. **`transactionRequestId`**：每次新交易必须全局唯一（UUID/时间戳），API 层应对重复 ID 做防御性校验或至少在文档中强调。
5. **错误码 306（超时）**：SDK 与文档必须明确指导集成方"先查询、后决策"，任何自带的重试逻辑都不能对 306 做自动重试。
6. **错误码 307/308**：必须使用新的 `transactionRequestId` 重试，不能复用旧 ID。
7. **INIT 流程对集成方透明**：不提供公开 API 让外部手动触发 INIT；INIT 在首笔交易时自动运行，连接断开重连后状态被清除。
8. **Refund 两种模式**：`referencedBuilder()`（有原交易 ID，优先使用）与 `nonReferencedBuilder()`（无原交易 ID，需要客户重新刷卡）——两者语义、Builder API 必须保持清晰区分，不可合并成一个易混淆的接口。
9. **Abort 前置条件**：仅允许在网关授权前调用；已授权交易必须使用 Void/Refund。
10. **线程安全**：`TaplinkSDK` 的静态方法必须保证线程安全，可从任意线程调用；`PaymentCallback`/`ConnectionListener` 的实现类文档必须明确告知回调可能运行在后台线程，调用方需自行切换主线程。
11. **生命周期清理**：文档与示例代码必须展示 `disconnect()` + `removeConnectionListener()` 成对出现，缺一不可。

## 5. 错误处理规范

- 错误码需按类别（连接类、交易类 3xx 等）分组维护，参考 `docs/TAPLINK_ERROR_CODES_REDEFINED.md`、`docs/SEMI_INTEGRATION_ERROR_CODES_3DIGIT.md`。
- 新增错误码时：确认所属类别、三位数编码规则、是否需要在 `ErrorCategory` 枚举中新增分类，同步更新集成指南文档。
- InnerErrorCode → 标准错误码的映射逻辑集中维护，避免散落多处（参考 `docs/INNERERRORCODE_UPDATE_SUMMARY.md`、`docs/INNERERRORCODE_REPLACEMENT_ANALYSIS.md`）。

## 6. 安全规范

- **凭证禁止硬编码**：`appId`/`merchantId`/`secretKey` 一律通过 `BuildConfig`（由 `local.properties` 或 CI/CD 注入），严禁提交到版本库。
- **生产日志必须关闭**：`logEnabled` 仅在 Debug 构建开启，Release 构建强制为 `false`，防止交易/卡片敏感信息泄漏。
- USB 权限（Cable 模式）需在 `AndroidManifest.xml` 声明 `android.hardware.usb.host` feature，Android 12+ 需要运行时权限校验。

## 7. 文档撰写规范

参考 `docs/PROJECT_DOCUMENTATION_RULES.md`，核心规则：

- 所有技术文档一律放在项目根 `docs/` 目录（或其子目录 `docs/api/`、`docs/guides/`、`docs/architecture/`），**禁止**散落在 `lib_taplink_sdk/` 或 `src/` 下。
- 文件命名统一使用大写+下划线（`FEATURE_NAME.md`）或英文连字符（`feature-name.md`），项目内保持一致。
- 示例/演示代码只能放在 `src/test/` 或 `src/androidTest/` 下，**禁止**放在 `src/main/` 中。
- 新建文档前先确定文档类型归类目录，再创建文件，内容需包含清晰标题结构、代码示例与交叉引用。
- 提交前可运行 `.kiro/scripts/check-md-files.sh` 与 `.kiro/scripts/check-example-files.sh` 校验位置合规性（如脚本存在）。

## 8. 版本发布规范

- 版本号格式：`<major>.<minor>.<patch>[.<build>]`，正式版如 `1.0.8`，内部构建版本如 `1.0.7.28`。
- 版本号在 `lib_taplink_sdk/build.gradle.kts` 的 `SdkVersion` object 中集中定义（`CODE` 递增整数，`NAME` 语义化版本），不要在多处硬编码版本号。
- 构件命名：`sunbay-taplink-sdk-android-<版本号>-<buildType>.aar`，输出目录为 `lib_taplink_sdk/../build/`。
- 传递依赖必须在 `sdk_publish.gradle` 的 POM 配置中显式声明（`addDep(...)`），不能遗漏（如 jackson-databind、Java-WebSocket、usb-serial-for-android）。
- `-SNAPSHOT` 后缀版本发布到 Snapshot 仓库，正式版本发布到 Release 仓库，二者 URL 不同，不要混用。
- 发版五步骤：① 更新 `SdkVersion.NAME`/`CODE` → ② 在 `RELEASE-NOTES.md` 顶部新增版本条目（含 🚀新增/🔧改进/🐛修复/📦依赖/🧭迁移指南/💥breaking change）→ ③ `git commit`（英文）+ `git tag vX.Y.Z` → ④ **AAR 打包与 Maven 发布由维护者手动执行**（见下方"人工发布节点"）→ ⑤ **同步更新 `sunbay-open-docs` 仓库的公开 SDK 文档（见下）**。

### ⚠️ 人工发布节点（AI 必须暂停等待）

**第 ④ 步的 AAR 打包和 Maven 发布一律由维护者手动完成，AI agent 严禁自行执行发布相关 Gradle task**（如 publish/assembleRelease 等发布任务）。AI 的正确行为：

1. 完成 ①–③ 步（版本号、RELEASE-NOTES、commit/tag 的准备）后**停下**，明确告知维护者"请手动执行 AAR 打包与 Maven 发布"。
2. **等待维护者操作完成并提供实际发布的版本号**。
3. 拿到版本号后再继续第 ⑤ 步（sunbay-open-docs 文档同步），以及 tapro/taplinkdemo 的依赖升级——所有下游更新必须使用维护者确认的版本号，不得使用 AI 自己推测的版本号。

### 8.1 发版必须同步更新 sunbay-open-docs（强制，不可省略）

只要本次发布涉及**对外 API/行为变化**（新增/修改公开类型、连接模式、交易语义、错误码等），就必须在同一发布流程中更新 `~/StudioProjects/sunbay-open-docs` 仓库：

- `src/content/en/sdk/client-side/android.mdx` **和** `src/content/zh/sdk/client-side/android.mdx`（两者都要改，禁止只改一侧）：
  - 更新 `implementation("com.sunmi:sunbay-taplink-sdk-android:X.Y.Z")` 版本号
  - 在 "Version History"/"版本历史" 顶部新增 `### vX.Y.Z (Current)` 条目，移除上一版本的 `(Current)` 标记
  - 内容需与本仓库 `RELEASE-NOTES.md` 对应版本语义一致，但只暴露对外契约行为，不泄漏内部实现细节
  - 若为 breaking change，补充 "Migrating from vX.Y.Z" / "从 vX.Y.Z 迁移" 迁移指南章节（含前后代码对比）
- 若新增错误码：同步更新 `src/content/en/resources/reference/error-codes.mdx`（及 zh 对应文件）
- 若新增依赖库（如引入新的第三方 SDK）：同步更新文档中的 Technical Stack 说明
- 在 `src/content/en/resources/changelog.mdx`（及 zh 对应文件）顶部新增本次文档更新记录

**只发布 AAR / 更新本仓库 RELEASE-NOTES 而不同步 `sunbay-open-docs`，视为本次发布未完成**。详细规则见 `sunbay-open-docs/AGENT.md`。

## 9. Git 提交规范

- **一律使用英文**，Conventional Commits 格式（`feat:`/`fix:`/`refactor:`/`docs:`/`chore:` 等），即使交流语言是中文。

## 10. 完整发布前检查清单

- [ ] `transactionRequestId` 全局唯一（UUID/时间戳）
- [ ] 金额一律最小货币单位（分）
- [ ] `onSuccess` 分支检查 `isSuccess()`/`isProcessing()`/`isFailed()`
- [ ] 306 超时后走 query-first 流程，不直接重试
- [ ] 307/308 后使用新 transactionRequestId 重试
- [ ] 生命周期文档/示例展示 `disconnect()` + `removeConnectionListener()` 成对调用
- [ ] 所有 UI 相关回调文档提示切换主线程
- [ ] `logEnabled=false` 为生产默认值/文档中已强调
- [ ] 凭证通过 BuildConfig/环境变量注入，无硬编码，无凭证提交到仓库
- [ ] USB 权限处理（Cable 模式）文档/代码完整
- [ ] 新增/变更文档位于 `docs/` 对应子目录，示例代码位于 `test/`/`androidTest/`
- [ ] `RELEASE-NOTES.md` 已更新，版本号已在 `build.gradle.kts` 更新
- [ ] **AAR 打包与 Maven 发布已由维护者手动完成，且后续步骤使用的是维护者确认的版本号**
- [ ] **`sunbay-open-docs` 中 en + zh 的 `sdk/client-side/android.mdx` 已同步更新**（版本号、Version History、迁移指南、错误码参考、changelog）
- [ ] Git 提交信息为英文，符合 Conventional Commits
- [ ] 未在公开 API 中意外暴露 `impl/manager/protocol/persistence` 或 `lib_taplink_communication` 中的类型

## 11. AI Agent 工作流

1. 判断任务是否涉及**公开 API 变更**（新增/修改 `lib_taplink_sdk` 对外可见类型）——涉及则必须评估对 `tapro`、`taplinkdemo` 及所有第三方集成商的兼容性影响，优先保证向后兼容，必要的 breaking change 需在 `RELEASE-NOTES.md` 中明确标注 💥 并给出迁移指南。
2. 阅读 `.cursor/rules/taplink-*.mdc` 对应主题的规则文件（连接模式/交易/错误处理/最佳实践/快速集成/SDK 总览）。
3. 编码时严格遵守第 4 节"交易语义铁律"，任何简化或省略都需要在 PR 描述中明确说明理由。
4. 涉及新增错误码/连接模式/交易类型时，同步更新对应的 `docs/` 文档与 `RELEASE-NOTES.md`，**并评估是否需要同步更新 `~/StudioProjects/sunbay-open-docs`（en+zh）**——凡涉及对外行为的变更，答案通常是"是"。
5. 完成后核对第 10 节检查清单。
6. **涉及发版时，到"AAR 打包 / Maven 发布"步骤必须暂停**，请维护者手动执行并提供版本号，拿到版本号后再继续文档同步与下游依赖升级（见第 8 节"人工发布节点"）。
7. 如果需求会导致内部实现细节泄漏为公开 API，或与已有语义冲突，主动向维护者澄清而非自行决定。

### 11.1 SDD 与 TDD（与 Tapro 对齐）

- **SDD 是行为变更前置要求**：新功能、公开 API/协议语义、错误码、连接/交易行为变化，先检查 `docs/spec/` 的 active spec；缺失时按 `docs/spec/SPEC_GUIDE.md` 与 `.agents/skills/spec-authoring/SKILL.md` 的五步流水线建立规格。每条规则带来源分类、原始文档版本/章节/原文、AC 与测试映射；独立复核和人工批准后才可作为实现依据。历史资料按领域逐步迁移，不将未经审查的旧文档自动视为 active。
- **SCR 是已批准 spec 实质变更的强制记录**：L2/L3 必须有 `docs/spec/changes/{change_id}.yaml`，区分来源遗漏与新增设计决策，记录 before/after、影响和审批；金额、交易结果、306 超时、请求 ID、连接协议、公开 API breaking change 等按 L3 专项批准。运行 `./config/speccheck/spec-check.sh`；通过仅证明机械结构，不代替语义审查。
- **TDD 流程**：当前任务经用户明确要求或同意后，按 `.agents/skills/tdd/SKILL.md` 执行需求方向与用例清单两道闸门、红绿重构、集成/设备验证、回归、评审和 spec 回写；bug 走 B1–B5。启动授权与 Tapro `AGENTS.md §13.2.0` 对齐，用户在当前任务已明确授权的无需再问。用例必须引用 active spec 的 `spec_id §AC`；若发现规则缺口先走 SCR 裁决。
- **发布边界保持不变**：本流程不授权 AI 执行 AAR 打包/Maven 发布；继续遵守 §8 的维护者人工节点和确认版本后的下游/双语文档同步。

## 12. 规则文件索引

| 文件 | 说明 |
|---|---|
| `.cursor/rules/taplink-sdk-overview.mdc` | SDK 总体架构概览 |
| `.cursor/rules/taplink-quick-integration.mdc` | 3 步快速集成标准流程 |
| `.cursor/rules/taplink-connection-modes.mdc` | 连接模式详细规则 |
| `.cursor/rules/taplink-transactions.mdc` | 交易类型与语义规则 |
| `.cursor/rules/taplink-error-handling.mdc` | 错误处理规则 |
| `.cursor/rules/taplink-best-practices.mdc` | 最佳实践汇总 |
| `.cursor/rules/git-commit-english.mdc` | Git 提交必须英文 |
| `llms.txt` | 面向 LLM 的项目速览 |
| `docs/PROJECT_DOCUMENTATION_RULES.md` | 文档撰写与位置规范 |
| `docs/Sunbay Taplink SDK for Android - 集成指南 (NEW).md` | 完整集成指南（连接模式、交易类型、AmountInfo、回调语义、Go-Live 检查表） |
| `RELEASE-NOTES.md` | 版本发布记录 |
| `docs/spec/SPEC_GUIDE.md` | SDK 规格证据链、SCR 与人工闸门 |
| `docs/spec/SDD_ROADMAP.md` | SDK SDD 已建能力与待补闭环 |
| `config/speccheck/spec-check.sh` | coverage/SCR 机械校验入口 |
| `.agents/skills/spec-authoring/SKILL.md` | 五步规格生成与独立复核流程 |
| `.agents/skills/tdd/SKILL.md` | SDK 新功能 TDD 与 bug 修复流程 |

---
**文档版本**: v1.1　**最后更新**: 2026-10-10　**适用范围**: taplinksdk 仓库全部 AI 编码代理
