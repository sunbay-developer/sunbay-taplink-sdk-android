# 根因定位报告：连接端网络断开后重连被误判为 `invalid recovery handoff`

## 一句话结论

**根因是 f935c54（OPT-R1）在 `LanClientKernel.tryServiceDiscoveryForOperation` 的「上层决定不重连」分支里，把一个本来良性的收尾（旧代码只是 `return`）改成了抛致命异常 `IllegalStateException("Service discovery target rejected; invalid recovery handoff")`；该异常消息含 `invalid` 命中非重试错误清单，于是被判为 non-retryable 直接 abort 整个 CONNECT 操作、最终上报 241 CONNECTION。** 而「上层决定不重连」本身，是 SDK 侧 `ConnectionManager.analyzeAddressChange` 在「同地址 + 状态仍为 CONNECTING」时误判「已连接、无需重连」——这段判断在"掉网重连"语境下（旧连接实际已死、CONNECTING 是残留过渡态）本身就是错的。两处叠加导致掉网重连场景被打成致命错误。

- **是否回归：是。** 致命 abort 这一步是 f935c54 引入的回归；上层「同地址已连接→不重连」的误判逻辑是旧有缺陷（commit 82baec9，2026-01-22），但在 f935c54 之前只会被良性吞掉，不会打死连接。

---

## 一、完整失败链路（代码证据）

### 环节 A：直连失败 → 回落 service discovery（符合预期）

`LanClientKernel.kt` 直连失败后，在 `allowServiceDiscoveryFallback=true` 时回落到 discovery：

`lib_taplink_communication/.../lan/LanClientKernel.kt:522-560`
```kotlin
val options = connectionOptions
val result = connectionManager.connect(
    uri,
    deferHandshakeFailureCallbacks = options.allowServiceDiscoveryFallback &&
        options.deferHandshakeFailureCallbacks
)
...
is ConnectionManager.ConnectionResult.Failure -> {
    if (connectionOptions.allowServiceDiscoveryFallback) {
        tryServiceDiscoveryForOperation(operationId)   // ← 回落
    } else {
        notifyConnectionError("Direct LAN connection failed: ${result.error}")
    }
}
```
`deferHandshakeFailureCallbacks` 默认 `true`（`LanConnectionOptions.kt:13`，注释写明 Fast transaction recovery 才会 opt-out 为 false）。因此**掉网重连走的正是 `deferHandshakeFailureCallbacks=true` 这条分支**。

### 环节 B：discovery 发现同一台 Tapro → 回调上层决定是否重连

`lib_taplink_communication/.../lan/LanClientKernel.kt:618-671`
```kotlin
val shouldReconnect = if (listener != null) {
    try {
        listener.onServiceAddressChanged(service.name, service.host, service.port, oldHost, oldPort)
    } catch (e: Exception) { ...; false }
} else { ...; false }
notified = true
if (shouldReconnect) {
    LogUtil.d(TAG, "Upper layer decided to reconnect, service discovery completed ...")
    return
} else {
    LogUtil.d(TAG, "Upper layer decided not to reconnect for this service, trying next service ...")  // ← logcat :664
}
```

### 环节 C：上层（SDK 侧）错误地返回「不重连」

`onServiceAddressChanged` 直通到 SDK 侧 `ConnectionManager.handleServiceAddressChanged`（`lib_taplink_sdk/.../manager/ConnectionManager.kt:1499`）。该方法第 2 步 `analyzeAddressChange`：

`lib_taplink_sdk/.../manager/ConnectionManager.kt:1424-1437`
```kotlin
if (isSameAddress) {
    val actualStatus = getActualConnectionStatus()
    return if (actualStatus in listOf(ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR)) {
        ReconnectionDecision(true,  "Same address but currently disconnected, reconnection needed")
    } else {
        ReconnectionDecision(false, "Same address and already connected (status: $actualStatus), no reconnection needed") // ← logcat :1231
    }
}
```
`getActualConnectionStatus()` 读取 kernel 实时状态（`:1674` 起），回落 discovery 期间 kernel 仍停在 `CONNECTING`。`CONNECTING` 不在 `{DISCONNECTED, ERROR}` 集合里，于是返回 `shouldReconnect=false`，`handleServiceAddressChanged` 回传 `false`。

> **问题点 1（旧有缺陷）**：此判断只认 `DISCONNECTED/ERROR` 为「需要重连」，把 `CONNECTING` 当成「已连接」。但掉网重连场景里旧 WebSocket 已断、`CONNECTING` 只是尚未被清理的残留过渡态——此处把"连接已死但状态还停在 CONNECTING"误当成"还连着"。该分支由 commit **82baec9（2026-01-22）** 引入，**非 f935c54**。

### 环节 D：f935c54 把「上层不重连」翻译成致命错误（回归点）

回到 kernel。所有发现到的 service 都被上层拒绝（`notified=true` 但没有一个 `shouldReconnect`）后：

`lib_taplink_communication/.../lan/LanClientKernel.kt:681-690`
```kotlin
} else {
    // No accepted handoff means nobody owns a replacement connection.
    // Complete this attempt instead of leaving it CONNECTING forever.
    if (connectionOptions.deferHandshakeFailureCallbacks) {
        throw IllegalStateException("Service discovery target rejected; invalid recovery handoff")  // ← logcat :729
    }
    // Fast transaction recovery retains its immediate error callback path.
    return
}
```

### 环节 E：错误被判为 non-retryable → abort

抛出的异常被同函数 catch 捕获，经 `isRetryableServiceDiscoveryError` 分类：

`lib_taplink_communication/.../lan/LanClientKernel.kt:745-760`（catch 分支，logcat :729/:753）
```kotlin
val isRetryableError = isRetryableServiceDiscoveryError(e)
if (isRetryableError) { retryCount++; continue }
else {
    LogUtil.w(TAG, "Non-retryable error detected, aborting service discovery ...")  // ← logcat :753
    throw e
}
```
`isRetryableServiceDiscoveryError` 的非重试清单含 `"invalid"`：
```kotlin
val nonRetryableErrors = listOf("cancelled", "operation cancelled", "invalid")
if (nonRetryableErrors.any { errorMessage.contains(it) }) return false
```
错误消息 `...invalid recovery handoff` 命中 `"invalid"` → 判为 non-retryable → 直接 `throw`。

### 环节 F：abort 冒泡 → 241 CONNECTION → 失败在途交易

异常冒泡到 `executeConnectionOperation` 的 catch（`LanClientKernel.kt:408-416`）：
```kotlin
if (isOperationValid(operationId) && !isWebSocketConnected()) {
    currentOperationId = null
    handleConnectionError("${InnerErrorCode.E241.description}:Operation failed: ${e.message}", callback, InnerErrorCode.E241.code)
}
```
最终上报 **241 CONNECTION**，`PaymentManager.FailAllPendingTransactions` 把在途交易全部判死（现象第 5 步）。

---

## 二、是否为 f935c54 引入的回归（git 证据）

`git show f935c54 -- .../lan/LanClientKernel.kt` 对 "上层决定不重连" 分支的改动：

```diff
-                        // Upper layer notified, but decided not to reconnect, service discovery completed
-                        LogUtil.d(TAG, "Service discovery completed, upper layer will handle connection ($operationId)")
+                        // No accepted handoff means nobody owns a replacement connection.
+                        // Complete this attempt instead of leaving it CONNECTING forever.
+                        if (connectionOptions.deferHandshakeFailureCallbacks) {
+                            throw IllegalStateException("Service discovery target rejected; invalid recovery handoff")
+                        }
+                        // Fast transaction recovery retains its immediate error callback path.
                         return
```

**结论：**
- **致命 abort（环节 D/E/F）是 f935c54 引入的回归。** f935c54 之前此分支只是打一行日志后 `return`，连接不会被判死。
- **上层「同地址已连接→不重连」误判（环节 C，问题点 1）是旧有缺陷**，由 82baec9 引入（`git blame` 确认 `analyzeAddressChange` 第 1420–1440 行均属 82baec9）。f935c54 **未改动**该段 SDK 逻辑，只是让它的错误后果从"被良性吞掉"升级成了"打死整条连接"。

> f935c54 的本意有据可依：`docs/spec/TAPLINK_LAN_RECOVERY_SPEC.md` §4 要求 "A discovered target rejected without an accepted handoff cannot silently finish the logical attempt with no final outcome"（不能让逻辑连接静默收尾、永远卡在 CONNECTING）。改动是为修复旧代码 `return` 导致的"永久 CONNECTING 卡死"。但它用力过猛——把本应"继续恢复/给出最终结论"的收敛，直接写成了"立即非重试致命 abort"。

---

## 三、正确行为应该是什么

参照同一份已批准规格 `SPEC-LAN-RECOVERY-001`：

- **R1**：开启 autoReconnect 时，初始 LAN 端点失败不得在尝试恢复到被发现端点之前就报终态失败。
- **R3**：只有在「没有可接受目标 / 替换连接失败」**且** 配置的恢复预算**已耗尽**时，才终止逻辑连接并报一次最终错误。
- **§4 边界**：明确把 "upper-layer rejection (including unchanged address or identity/mode protection)" 列为需要正确处理的受测场景。

对照本场景「连接端掉网重连、discovery 发现到同一台 Tapro 同地址」，正确行为应为：

1. 旧连接已因掉网断开，`CONNECTING` 是残留过渡态——上层**本就该判定需要重连**，重新建立到同地址 10.23.76.117:8443 的连接（把这次 discovery 视为一次**有效 handoff**，走 `executeReconnection`）。
2. 即便上层出于某种原因拒绝，kernel 也**不应把"上层拒绝"等同于"恢复接力被拒绝的致命错误"**：按 R3，应在恢复预算未耗尽时继续/收敛，只有真正无可接受目标且预算耗尽才报一次终态错误；且该终态错误不该是 non-retryable 的瞬时 abort。
3. 关键区分：**「上层决定不重连」≠「恢复接力无效（invalid handoff）」**。前者是一次关于单个 service 的决策结果，后者才是协议级的接力失败。f935c54 把两者混为一谈。

---

## 四、最小修复方向与影响面（不含实现代码）

两处缺陷可独立修复，建议**优先修问题点 1（SDK 侧上层判断）**，它直接命中"掉网重连"根因；问题点 2（kernel 侧错误分级）作为防御性加固。

### 方向 A（推荐，治本）：修 `ConnectionManager.analyzeAddressChange` 的过渡态误判
- **位置**：`lib_taplink_sdk/.../manager/ConnectionManager.kt:1424-1437`。
- **思路**：在"掉网重连 / 回落 discovery"语境下，`CONNECTING`（及 `WAIT_CONNECTING` 等过渡态）不应被当作"已连接、无需重连"。应把判据从"仅 DISCONNECTED/ERROR 才重连"改为"只有**真正已建立且存活**的连接（如 WebSocket 实际连通）才视为无需重连"，使残留 CONNECTING 过渡态被正确判定为"需要重连"，从而回传 `shouldReconnect=true`、走 `executeReconnection` 重建连接。
- **影响面**：改变的是 `handleServiceAddressChanged` 的返回值语义——同地址但连接实际已死时会触发一次重连。需回归：真正已连接时同地址不应重复重连（避免抖动）；`analyzeConnectionState`（第 2 道校验，`:1450` 起）仍会用 `getLastConnectionConfig` 做二次防抖，可一并核对两道判断口径是否一致。该模块是内部 `manager` 包，不涉及公开 API。

### 方向 B（防御性，治标）：kernel 侧不要把"上层拒绝"当成 non-retryable 致命错
- **位置**：`lib_taplink_communication/.../lan/LanClientKernel.kt:681-690`（抛异常处）与 `:739-760`（`isRetryableServiceDiscoveryError` 分类）。
- **思路**：区分"上层拒绝单个 service"与"恢复接力真正失败"。可选其一或组合：
  (1) 当 `notified=true` 但全部被拒时，**不要立即抛 non-retryable 异常**，而是按 R3 走"恢复预算是否耗尽"的判断——未耗尽则继续 discovery 重试，耗尽才报一次终态错误；
  (2) 若仍需以异常收尾，异常消息/类型不应命中 `"invalid"` 这类被归入 non-retryable 的关键词，避免被 `isRetryableServiceDiscoveryError` 瞬时 abort。
- **影响面**：改变 OPT-R1 恢复路径在"全部 service 被上层拒绝"时的收敛行为。需确保仍满足规格 §4「不得静默卡死在 CONNECTING」——即最终必须有一个明确结论（成功重连 / 预算耗尽后报一次终态 241），不能回退成 f935c54 之前的裸 `return`。同时需核对 Fast transaction recovery 分支（`deferHandshakeFailureCallbacks=false`）不受影响。

### 影响面对比小结
| 方向 | 改哪里 | 治本/治标 | 主要回归点 |
|---|---|---|---|
| A（推荐） | SDK `ConnectionManager.analyzeAddressChange`（过渡态判断） | 治本：让掉网重连正确触发重连 | 真连接时同地址勿重复重连；两道校验口径一致 |
| B（加固） | 通信层 `LanClientKernel` 异常抛出/分级 | 治标：避免"上层拒绝"被当致命 abort | 不得退回静默卡死；Fast recovery 分支不受影响 |

建议 A+B 一并落地：A 消除根因，B 防止未来其他"上层拒绝"场景再次被误判为致命错误。

---

## 五、关键文件 / 行号索引

| 环节 | 文件:行号 | 说明 |
|---|---|---|
| 回落 discovery | `lib_taplink_communication/.../lan/LanClientKernel.kt:522-560` | 直连失败回落、`deferHandshakeFailureCallbacks` 取值 |
| 上层回调决策 | `lib_taplink_communication/.../lan/LanClientKernel.kt:618-671` | logcat :664「decided not to reconnect」 |
| **致命抛出（回归点）** | `lib_taplink_communication/.../lan/LanClientKernel.kt:681-690` | logcat :729「invalid recovery handoff」（f935c54 新增） |
| 非重试分类 + abort | `lib_taplink_communication/.../lan/LanClientKernel.kt:739-760` | `"invalid"` 命中 non-retryable；logcat :753 |
| abort → 241 | `lib_taplink_communication/.../lan/LanClientKernel.kt:408-416` | `handleConnectionError(... E241 ...)` |
| 默认开启 defer | `lib_taplink_communication/.../lan/LanConnectionOptions.kt:13` | `deferHandshakeFailureCallbacks = true` |
| **上层误判（旧缺陷）** | `lib_taplink_sdk/.../manager/ConnectionManager.kt:1424-1437` | logcat :1231「already connected (CONNECTING), no reconnection needed」（82baec9） |
| 读取实时状态 | `lib_taplink_sdk/.../manager/ConnectionManager.kt:1674+` | `getActualConnectionStatus` 读 kernel 的 CONNECTING |
| 统一入口 | `lib_taplink_sdk/.../manager/ConnectionManager.kt:1185 / 1499` | `handleServiceAddressChanged` / listener 直通 |
| 设计意图 | `docs/spec/TAPLINK_LAN_RECOVERY_SPEC.md` R1/R3/§4 | 不得静默卡死，但终态须"预算耗尽后一次性"报出 |

---
*本报告为纯只读调查结论，未修改任何代码。*
