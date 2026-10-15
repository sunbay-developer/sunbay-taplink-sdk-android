# Fast-Recovery 全模式放开 —— 只读静态风险评估

> SDD 规格修订任务 STEP 1（只读静态风险评估）。本报告仅做静态代码走读与证据链核对，
> **未修改任何源码，未执行 git commit，未运行构建/测试**。
>
> - 目标仓库：`taplinksdk`，分支 `dev_pro`（工作树含未提交改动，只读）。
> - 关联 active spec：`docs/spec/TAPLINK_LAN_RECOVERY_SPEC.md`（v1.1）；R8/AC8 当前主张「LAN 无需 SDK 改动，`isLanMode` 门控保留，放宽到 Cable 超范围」。
> - 已有 SCR：`docs/spec/changes/SCR-LAN-TRACEID-REBIND-001.yaml`（APPROVED，L3，2026-10-10）。
> - 工作树实际改动：`ConnectionManager.kt` **移除了 `canFastRecover` 的 `isLanMode` 门控**，抽出纯函数 `computeCanFastRecover(...)`，把交易中快速恢复从 LAN-only 放开到**所有连接模式**。
> - 维护者决策：**保留**该代码（放开到全模式是产品决策）。本报告为后续 SCR 重写 R8/AC8 提供静态结论。

本评估回答一个核心问题：移除 `isLanMode` 门控后，`computeCanFastRecover` 在运行态条件满足时对**所有模式**返回 `true`，这对每种连接模式实际意味着什么？是「只有长连接模式真正能触发，其余模式天然无法触达，故无害放宽」，还是「某些模式被错误拖入快恢路径并产生副作用」？

---

## 0. 关键代码事实（file:line 证据）

### 0.1 放开点：快恢进入判定不再看连接模式

`ConnectionManager.onDisconnected` 的 **normalDisconnect 分支**（即「已建立连接后的被动/正常断开」分支）计算 `canFastRecover`：

- `ConnectionManager.kt:486-507` —— `onDisconnected` else 分支内收集运行态条件并调用 `computeCanFastRecover(...)`：
  ```
  val autoReconnectEnabled = currentConnectionConfig?.autoReconnect == true
  val isManual = reconnectManager?.isManualDisconnect() == true
  val hasInFlight = paymentManager?.hasInFlightTransactions() == true
  val canFastRecover = computeCanFastRecover(
      autoReconnectEnabled, isManual, hasInFlight,
      isFastRecovering, isReconnecting = reconnectManager?.isReconnecting() == true,
      hasConnectionConfig = currentConnectionConfig != null)
  if (canFastRecover) { tryFastRecovery(listener); syncStatusFromKernel(); return }
  ```
- `ConnectionManager.kt:552-567` —— 抽出的纯函数 `computeCanFastRecover(...)`，**不含任何连接模式参数**，仅由 `autoReconnect && !isManual && hasInFlight && !isFastRecovering && !isReconnecting && hasConnectionConfig` 决定。注释（`:542-547`）明确「原先写死 `isLanMode`，现放开为所有连接模式（LAN / Cable 的 USB_VSP·RS232·USB_AOA / App-to-App / Cloud）均可进」。
- 单测 `FastRecoveryModeAgnosticTest.kt:52-67` 直接断言纯函数不再依赖 LAN/Cable；全部条件满足即返回 `true`。

### 0.2 决定「哪条断开能进 normalDisconnect 分支」的闸门：`isConnectionError`

快恢路径只存在于 `onDisconnected` 的 **else（normalDisconnect）分支**。进入哪条分支由 `isConnectionError(code, msg)` 决定（`ConnectionManager.kt:443`）：

- `isConnectionError` 返回 `true` → 走**连接错误分支**（`ConnectionManager.kt:445-470`），该分支里**没有** `computeCanFastRecover` / `tryFastRecovery`，只做 `failAllPendingTransactions` + 报 ERROR/恢复失败。
- `isConnectionError` 返回 `false` → 走 **normalDisconnect else 分支**（`:471-530`），这才是快恢路径所在。

`isConnectionError` 的分类表（`ConnectionManager.kt:1121-1159`）：
- **连接错误码集合**（`connectionErrorCodes`，`:1123-1140`）包含：`201/202/203`、`211/212/213/214/221`、`231/232`（App-to-App）、`241/242`（LAN）、`251..255`（USB/Cable）、`-1`、`1002/1015`、以及若干字符串码。
- **normalDisconnect 码集合**（`normalDisconnectCodes`，`:1145-1151`）**只有 WebSocket 语义码**：`1000`（正常关闭）、`1001`（端点离开）、`1006`（异常关闭/网络掉线）、`MANUAL_DISCONNECT`、`HEARTBEAT_TIMEOUT`。
- 其余按消息关键字 + 当前是否处于 CONNECTING 判定，默认在连接阶段视为连接错误。

**推论**：只有当底层上报的断开码落在 `normalDisconnectCodes`（本质是 WebSocket 的 `1000/1001/1006`）时，才会进入 normalDisconnect 分支、才有机会触发快恢。`1000/1001/1006` 是 **LAN（WebSocket）** 传输独有的关闭码——见 `lib_taplink_communication/.../lan/connection/ConnectionManager.kt:201,222,224`（`close(1000,...)`、`CloseFrame.ABNORMAL_CLOSE`=1006）与 `lan/LanClientKernel.kt:966-994`（保留真实 WebSocket close code，`code == 1006 || code >= 1002` 判为 error）。

---

## 1. Cable（USB_VSP / RS232 / USB_AOA）

**结论：无害放宽——Cable 的被动断开根本不会进入 normalDisconnect 分支，`computeCanFastRecover` 对 Cable 永远触达不到。**

证据：

1. `ProtocolConfigResolver.buildProtocol` 对 Cable 产出的 `connectionMode` 是 **具体子协议名**（不是 "CABLE"）：`USB_AOA` / `USB_VSP` / `RS232`（`ProtocolConfigResolver.kt:80-94` 的 `buildProtocolFromCableProtocol`）。
2. Cable 各内核的**被动断开/拔线**上报的都是**连接错误码**，而非 WebSocket 的 `1000/1001/1006`：
   - VSP：`VSPClientKernel.kt:900-904` —— 连接中掉线上报 `notifyConnectionDisconnected(E232, ...)`（码 `232`）。
   - RS232（Serial）：`SerialServiceKernel.kt:553` 等 —— `notifyConnectionError(..., E232)`（`232`）；连接失败/无设备为 `E251`（`251`）。
   - AOA：`CableAoaHostKernel.kt:299-302` / `CableAoaAccessoryKernel.kt:266-269` —— 拔线/断开上报 `notifyConnectionDisconnected(E212, "Device disconnected")`（码 `212`）。
3. `212` / `232` / `251..255` **全部属于 `connectionErrorCodes`**（`ConnectionManager.kt:1125-1131`），因此 `isConnectionError` 返回 `true`，走连接错误分支，**不进 normalDisconnect 分支**。

因此，`tryFastRecovery` 里基于 `currentConnectionConfig` 的「原配置快速重连」语义在 Cable 上**不会被触发**。即便假设性地触发（例如将来某条 Cable 路径改用 `1006` 语义码），`tryFastRecovery → connectInternal(config)` 会用保存的 Cable 配置经 `ProtocolConfigResolver` 重新解析到对应 USB 子协议传输——但这属于**假设性路径，当前代码不可达**。放开门控对 Cable 是 **no-op**。

> 备注（非阻断）：R8 原文把「放开到 Cable」描述为「超范围/未做」。工作树代码确实已经从**判定层**放开到含 Cable，但因 `isConnectionError` 的分类，Cable 在**运行层仍无法进入快恢**。SCR 重写 R8 时应基于「判定层已放开、运行层由断开码分类天然约束」这一事实，而不是沿用「Cable 未放开」的旧措辞。

---

## 2. App-to-App（On-Device / Intent 唤起）

**结论：无害放宽——App-to-App 没有长连接/无「重连」语义，其进程崩溃断开上报 `E213`（连接错误码），走连接错误分支，`computeCanFastRecover` 永远触达不到；(在途交易 + 被动断开) 组合在 App-to-App 下不可达。**

证据：

1. App-to-App 走 `LocalServiceKernel`（AIDL `bindService`），其「断开」只有三种回调，**全部上报连接错误码**：
   - `onServiceDisconnected`（目标进程崩溃）：`LocalServiceKernel.kt:277-284` → `notifyConnectionDisconnected(E213, ...)`（码 `213`）。
   - `onBindingDied`：`LocalServiceKernel.kt:287-296` → `notifyConnectionDisconnected(E213, ...)`（码 `213`）。
   - `onNullBinding`：`LocalServiceKernel.kt:298-304` → `notifyConnectionError(..., E213)`。
2. `213` 属于 `connectionErrorCodes`（`ConnectionManager.kt:1126`），`isConnectionError` 返回 `true` → 走**连接错误分支**（`:445-470`），该分支无快恢逻辑，直接 `failAllPendingTransactions` + 报错清理。
3. App-to-App 不存在 `1000/1001/1006` 之类的 WebSocket 正常关闭码来源（`LocalServiceKernel` 从不产生这些码），因此**无法进入 normalDisconnect 分支**。

即：移除门控后，`computeCanFastRecover` 对 App-to-App 返回值无意义——因为控制流在上一层（`isConnectionError`）就被导向了连接错误分支，`computeCanFastRecover` 根本不会被调用。**既不会误触发一次「无意义快恢」，也无副作用**。

---

## 3. Cloud（HTTP / 云端模式）

**结论：无害放宽——SDK 侧根本不建立/驱动 Cloud 连接，没有可被 SDK 感知的长连接，也没有「被动断开 → onDisconnected normalDisconnect」路径到达快恢；放开门控对 Cloud 是 no-op。**

证据：

1. `CrossDeviceStrategy.kt:13-16` 明确：Cloud 虽是 Tapro 展示/状态层的一种 Cross-Device 传输，但**不是本枚举的值**；「Cloud 交易由云端后端直接下发给 Tapro，**SDK 从不建立、认证或驱动 Cloud 连接**」，因此不存在 SDK 可选的 `CrossDeviceStrategy.CLOUD`。
2. `ProtocolConfigResolver.buildProtocol`（`ProtocolConfigResolver.kt:36-74`）**没有任何 Cloud 分支**：输出只会是 Local（App-to-App）、LAN、或 Cable 子协议。SDK 不会为 Cloud 创建内核，`TaplinkServiceKernel` 的内核类型只有 `SERIAL/VSP/LOCAL/LAN`（`TaplinkServiceKernel.kt:178-181` 的 `ServiceType`）。
3. 既然 SDK 不持有 Cloud 长连接，就不存在 Cloud 的 `onDisconnected` 事件进入 `ConnectionManager.onDisconnected`，`computeCanFastRecover` 对 Cloud 也**不可触达**。

即：代码注释里列的「Cloud」只是穷举式说明「判定层不再按模式门控」，但 Cloud 在 SDK 内**没有连接实体**，放开门控是纯粹的 no-op。

---

## 4. 总体结论（供 R8 重写用）

**判定：无害放宽（harmless-widening）。** 移除 `isLanMode` 门控把快恢**判定层**放开到所有模式，但**运行层**由 `onDisconnected` 的 `isConnectionError` 分类天然约束：只有上报 WebSocket 正常/异常关闭码（`1000/1001/1006`）的断开才会进入 normalDisconnect 分支并触达 `computeCanFastRecover`，而这类码**只有 LAN（WebSocket）传输会产生**。

| 模式 | 是否能触达 `computeCanFastRecover` | 断开上报码 | 结论 |
|---|---|---|---|
| **LAN** | 是（唯一真正能触发快恢的模式） | `1000/1001/1006`（normalDisconnect） | 快恢按原设计工作（R7/R9 行为不变） |
| **Cable（VSP/RS232/AOA）** | 否 | `E212/E232/E251..` 等连接错误码 | 走连接错误分支，快恢不可达 → no-op |
| **App-to-App（On-Device）** | 否 | `E213`（连接错误码） | 走连接错误分支，快恢不可达 → no-op；(在途+被动断开) 组合不可达 |
| **Cloud** | 否 | SDK 无 Cloud 连接实体，无断开事件 | 判定层穷举提及，运行层 no-op |

因此 R8 **不应**重写成笼统的「所有模式都允许并会执行快恢」。更准确、与代码一致的表述是：

> **放开判定层的连接模式门控（移除 `isLanMode`），交易中单次快速恢复对所有具备长连接且被动断开上报 WebSocket 关闭码（当前即 LAN）的场景生效；不具备 SDK 长连接或其被动断开被分类为连接错误的模式（Cable / App-to-App / Cloud）天然不会进入快恢路径，故放宽无副作用（no-op）。** 判定层由运行态条件（`autoReconnect`、非主动断开、有在途交易、未在恢复/重连、有可复用配置）共同约束，连接模式不再参与判定；运行层由 `isConnectionError` 的断开码分类作为天然边界。

### 对规格治理的附带观察（非阻断）

- 这是对已 APPROVED 的 active spec（SPEC-LAN-RECOVERY-001 v1.1）R8/AC8 的**实质行为措辞变更**，`change_origin` 属于 **Type B / DESIGN_DECISION**（维护者产品决策，非原文遗漏）。按 SPEC_GUIDE §4，连接协议语义相关变更应按 **L3** 处理（专项规则负责人批准 + 兼容性/下游影响分析 + 回归）；获批前新规则 `after.status` 只能是 `PROPOSED`。
- **公开契约不变**：无新增公开 API、无新错误码、无新交易身份锚点（`traceId` 仍唯一）；`FastRecoveryModeAgnosticTest` 属内部实现测试。据此 **无需** AAR 打包/Maven 发布，也 **无需** 同步 `sunbay-open-docs`（en/zh）——除非后续另有公开行为变更。
- R7/R9/R10 的 SDK 语义**不受本次放宽影响**：快恢窗口 `FAST_RECOVERY_BUDGET_MS=5s`（`ConnectionManager.kt:1884`）、180s 回调超时、失败收口 `E306`→回落 `E213`（`tryFastRecovery`/`fastRecoveryFallback`，`:596-660`）、`isSelfHealing` 守卫（`:1659`）均未改动。

---

## 附：核对过的关键文件

- `lib_taplink_sdk/.../manager/ConnectionManager.kt`（onDisconnected 两分支、`computeCanFastRecover`、`tryFastRecovery`、`fastRecoveryFallback`、`isConnectionError`、`setupKernelStatusListener`/`isSelfHealing`、常量）
- `lib_taplink_sdk/.../protocol/ProtocolConfigResolver.kt`
- `lib_taplink_sdk/.../manager/PaymentManager.kt`（`hasInFlightTransactions`）
- `lib_taplink_sdk/.../enums/ConnectionMode.kt`、`enums/CableProtocol.kt`、`enums/CrossDeviceStrategy.kt`
- `lib_taplink_sdk/.../test/.../manager/FastRecoveryModeAgnosticTest.kt`
- `lib_taplink_communication/.../local/kernel/LocalServiceKernel.kt`（App-to-App 断开码）
- `lib_taplink_communication/.../cable/{vsp/VSPClientKernel.kt, serial/SerialServiceKernel.kt, aoa/host/CableAoaHostKernel.kt, aoa/accessory/CableAoaAccessoryKernel.kt}`（Cable 断开码）
- `lib_taplink_communication/.../lan/{connection/ConnectionManager.kt, LanClientKernel.kt}`（WebSocket 关闭码来源）
- `lib_taplink_communication/.../enums/InnerErrorCode.kt`（E212/E213/E232/E251/E306 码值）
- `docs/spec/TAPLINK_LAN_RECOVERY_SPEC.md`、`docs/spec/coverage/SPEC-LAN-RECOVERY-001.yaml`、`docs/spec/changes/SCR-LAN-TRACEID-REBIND-001.yaml`、`docs/spec/sources/TAPLINK_LAN_TRACEID_REBIND_SDK_ROLE.md`、`docs/spec/SPEC_GUIDE.md`、`.agents/skills/spec-authoring/SKILL.md`
