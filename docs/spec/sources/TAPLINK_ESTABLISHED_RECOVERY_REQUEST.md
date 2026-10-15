# REQ-ESTABLISHED-RECOVERY-001

Version: 1.0; captured: 2026-10-15 (Asia/Shanghai).

## S1. Maintainer request (verbatim)

所有的协议底层都需要在连接异常的时候先去尝试一次重连再报错disconnect，这样才是sdk应该做的，请开启sdd流程进行sdk该逻辑更新

## S2. Mandatory recovery configuration decision (verbatim)

Question presented: 现有 autoReconnect=false 表示关闭自动重连，与这次“所有协议异常后先重连一次”有契约冲突。此次单次恢复应如何处理？

Maintainer answer:

始终恢复一次，包括 autoReconnect=false；作为新的 SDK 内部自恢复契约

## S3. Established connection scope decision (verbatim)

Question presented: “连接异常先重连一次”是否同时覆盖首次 connect() 尚未成功时的连接失败？

Maintainer answer:

仅覆盖曾连接成功后的异常断开，首次失败沿用现有策略

## S4. Diagnostic evidence, not a new requirement

The supplied Tapro application/system logs show UART -6003, a system external-power
disconnect event, ADB errors and USB detach near device-local 01:15:20, followed by
client error 232 at 01:15:22.676. They do not contain USB VID/PID descriptors or the
client's original VSP exception. No physical-unplug, VID/PID-change or driver-root-cause
claim is established. These logs motivate the task, not the recovery contract.

Current-code evidence (dev_pro inspected 2026-10-15):

- VSPClientKernel.handleConnectionError() updates ERROR synchronously, reports internal
  onDisconnected(232), and only then releases USB resources.
- BaseServiceKernel.updateStatus() invokes the kernel status listener synchronously.
- SDK ConnectionManager's status listener may emit public onDisconnected before the
  formal callback classifies 232 and emits onError.
- Existing fast recovery requires an in-flight transaction and autoReconnect=true;
  error-code classification prevents common non-LAN failures from reaching it.
- ProtocolConfigResolver maps public modes to LAN, VSP, RS232, AOA Host or local AIDL.
  SUB_SCREEN uses VSP. CrossDeviceStrategy explicitly excludes Cloud connections.

## S5. Authority boundary

S1-S3 are human decisions in this chat. They authorize the SDD work and settle these
scope questions; they are not approval of the complete proposed specification, budget,
callback-success policy, identity validation or in-flight failure policy. Proposed
details require the independent review and L3 human gate in docs/spec/SPEC_GUIDE.md.

## S6. Non-interactive recovery constraint (verbatim)

还有这种重连会需要设备端授权吗，我不希望sdk内部重连还触发授权逻辑

Captured 2026-10-15 in the same chat. This adds a recovery constraint; it is not
approval of the remaining Q1-Q4 proposals or permission to bypass platform/security
checks. Existing first-connect authorization behavior remains outside this change.

## S7. Callback preservation priority (verbatim)

重连不能清楚已有的交易的callback这是最重要的，

## S8. Successful recovery continuation clarification (verbatim)

我的意思是重连成功了如果有正在交易的交易回调需要恢复callback走到我之前的重连交易续推逻辑

S8 clarifies the intended observable result: successful reconnect must resume the
original in-flight callback chain and existing transaction continuation, not create
a new transaction. It does not by itself approve the recovery-failure callback policy.

## S9. Existing continuation evidence (not new approval)

SDK ResponseProcessor.processResponse locates the callback by response traceId;
PaymentManager owns the LocalCallbackManager and its existing traceId mapping.
A successful transport rebuild must preserve/reconnect this receive-routing chain.

Current Host authority: tapro/docs/spec/TAPLINK_LAN_TRACEID_REBIND_SPEC.md,
SPEC-HOST-LAN-TRACEID-REBIND-001 active v1.2. It explicitly limits continuation to LAN
and uses onOpen to rebind in-flight senders; v1.2 reverses the older sign-in-success
trigger. SDK source TAPLINK_LAN_TRACEID_REBIND_SDK_ROLE.md captures the older decision
and is historical, not authority to require re-sign-in in this new proposal. Cable
Host continuation is not established by the LAN spec; SDK callback preservation alone
does not prove Cable end-to-end continuation. Cross-repository gaps require separately
approved Host changes before promising full continuation on that transport.

## S10. Q1 budget / single-attempt decision (verbatim)

Captured 2026-10-15 in the same chat, in reply to the Q1 budget and single-attempt proposal.

Maintainer answer:

只恢复一次

Interpretation (not part of the verbatim quote): one established recovery attempt only,
within the proposed 5,000ms monotonic budget, with no appended ReconnectManager/fast-recovery/
mDNS retry chain; maxRetryCount does not multiply the attempt. This resolves Q1 and authorizes
R4/R5/R7/R9/R12.

## S11. Q2 callback-silence decision (verbatim)

Captured 2026-10-15 in the same chat, in reply to the Q2 callback proposal.

Maintainer answer:

恢复期间不发进度回调，仅终局一次结果回调，onError的回调也不给调用端

Interpretation (not part of the verbatim quote): during recovery the SDK emits no
onReconnecting progress and no transient onError/onDisconnected to callers; only one terminal
result callback is delivered (onConnected on success, or onDisconnected with E213 on failure),
with no paired onError. This resolves Q2 and authorizes R6.

## S12. Q3 identity-validation decision (verbatim)

Captured 2026-10-15 in the same chat, in reply to the Q3 same-terminal validation proposal.

Maintainer answer:

身份无法可靠确认时，不自动接入其他终端

Interpretation (not part of the verbatim quote): recovery validates the original terminal
identity per adapter; when identity cannot be reliably confirmed the single attempt ends with
disconnect and the SDK never auto-selects another terminal. The pre-INIT idle limitation is
accepted. This resolves Q3 and authorizes R8.

## S13. Q4 recovery-failure callback decision (verbatim)

Captured 2026-10-15 in the same chat, in reply to the Q4 recovery-failure callback proposal.

Maintainer answer:

恢复失败后的交易callback都被清除了呀

Interpretation (not part of the verbatim quote): on terminal recovery failure the in-flight
transaction callbacks are cleared/failed through the existing physical-disconnect cleanup path,
released once together with the single terminal onDisconnected(E213); new submissions during
recovery are rejected with E213; no synthetic early E306 cleanup runs before or during the
attempt. Successful recovery still continues the original callbacks per S8/R15. This resolves
Q4 and authorizes R10/R11.

## S14. Q2 follow-up: onError scope and listener retention (verbatim)

Captured 2026-10-15 in the same chat, refining S11 after a clarifying question about what
onError means for an already-established connection.

Maintainer answer:

onError只在调用connect()时有对应回调，连接建立过后的onError不需要返回给他，我们sdk内部做恢复重试，并保证连接监听不被移除

Interpretation (not part of the verbatim quote): onError is scoped to the explicit connect()
action; a fault on an already-established connection is not an onError case and is never
delivered to the caller through onError. The SDK absorbs the established fault and runs the one
internal recovery attempt. The registered ConnectionListener must not be removed by the SDK
during recovery (success or failure); only the caller's explicit removeConnectionListener()/
disconnect() removes it. Terminal failure of an established-connection recovery is reported via
one onDisconnected(E213), never via onError. This refines (does not relax) S11/Q2 and R6.
