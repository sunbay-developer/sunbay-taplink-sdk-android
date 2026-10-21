# TapLink onError 回调生命周期与断开交易收口原始需求

- document_id: REQ-ONERROR-CALLBACK-LIFECYCLE-001
- document_version: 1.0
- 日期：2026-10-19（Asia/Shanghai）
- 原始来源：当前维护者会话。以下逐字保存，不将 AI 的解释混入原文。
- 状态：需求来源记录；不是完整 spec/SCR 批准。

## S1. 评审与新增调研要求（原文）

> /Users/sm4306/StudioProjects/taplinksdk/docs/architecture/TAPLINK_ONERROR_CALLBACK_LIFECYCLE_DESIGN.md检查并评审taplinkonerror逻辑方案，并且需要调研增加交易中onerror失败导致ondisconnect或直接是交易中disconnect时关闭交易允许下一笔新的交易进入

## S2. onError 处理顺序澄清（原文）

> `onError`本来就不需要被移除，需要`onError`的重试结果拿到后再决定是否移除

## S3. 内部流程与模板要求（原文）

> 你需要走内部sdd流程和内部的方案文档模版而不是你自己瞎编

## S4. 来源边界

S1 明确要求调研断开后关闭交易、允许下一笔；S2 明确先取得重试结果再决定移除。原文没有给出 Host 关闭等待时长、不可取消阶段的强制结束权限、手动 disconnect 的 PaymentCallback 通知策略或跨端关闭协议。以上缺口在 coverage 中标 UNSPECIFIED/CONFLICT，不视为维护者已经批准 AI 推荐值。S2 的“重试”与既有 active established recovery 的对应关系由原有已批准 R4/R6/R10/R11/R15 约束；不据此增加交易重发。

## S5. Host active 规格摘录快照（非新批准）

- 原文路径：`../tapro/docs/spec/TAPLINK_LAN_TRACEID_REBIND_SPEC.md`
- spec_id：SPEC-HOST-LAN-TRACEID-REBIND-001，版本 v1.2，status active。
- 本节仅保存跨仓库核对所需的原文片段，不能用本副本修改 Host 审批状态。
- R3 原文行为片段：

> 保留绑定：不 invalidate、不暂存 TTL；仅 `currentClient.compareAndSet(conn, null)` + 刷新客户端缓存后返回；掉线期间不发 `onDisconnected`、不置 `WAITING_CONNECT`（与现状分支 2 的「暂存」语义一致，但去掉 TTL 与 IP key）

- R9 原文行为片段：

> Host「保留在途绑定」的最大保留时长兜底对齐 SDK 180s 交易回调超时（`LocalCallbackManager` 默认 180s），不对齐 5s `FAST_RECOVERY_BUDGET_MS`

R9 同行含旧“5s 快恢失败后 E306”措辞；SDK 当前 established recovery R10 为最终 E213，故双方存在需核对的历史语义漂移，不在本轮静默改写。
