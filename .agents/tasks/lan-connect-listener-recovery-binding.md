# LAN connect(config, listener) 形参 listener 是否进入 EstablishedRecovery 恢复 observer —— 源码决定性核验

> 只读源码核查，未改动任何代码。所有关键断言附 `文件:行号` + 代码片段。
> 仓库：`/Users/sm4306/StudioProjects/taplinksdk`
> 目标：决定性回答「1006→EstablishedRecovery 的恢复终态回调，到底能不能投递到集成方通过 `connect(config, listener)` 传入的 listener 形参」。

---

## 一、结论摘要（先给答案）

**能投递到。前一份报告 `lan-1006-no-callback-report.md` 的 H1 定性（“恢复终态回调投递不到 connect 形参 listener”）是错误的，应被推翻。**

决定性证据链：

1. `connect(config, listener)` 在**正常新建连接路径**里，`connectInternal` 之前一定会调用 `reconnectManager.prepareConnect(actualConfig, listener)`（`ConnectionManager.kt:329`）。
2. `prepareConnect` 把该 listener 存成 `lastConnectionListener = listener`（`ReconnectManager.kt:88-89`）。
3. 恢复 observer 集合 `recoveryObservers()` 显式包含 `reconnectManager?.getLastConnectionListener()`（`ConnectionManager.kt:1005-1006`），而 `getLastConnectionListener()` 返回的就是上面那个 `lastConnectionListener`（`ReconnectManager.kt:510-512`）。
4. `EstablishedRecoveryCoordinator` 在 `claimEpoch` 时通过 `addEpisodeObserversFromManager()` 把 `recoveryObservers()` 快照进 `episodeObservers`（`EstablishedRecoveryCoordinator.kt:139、151-156`），终态 `settleSuccess`/`settleFailure` 就对这个集合逐个投 `onConnected` / `onDisconnected(E213)`（`EstablishedRecoveryCoordinator.kt:236-245、258-266`）。

因此，**即使集成方从未调用过 `setConnectionListener`（全局 `connectionListener == null`）**，`connect(config, listener)` 形参 listener 仍然经由 `reconnectManager.getLastConnectionListener()` 这条**间接登记路径**进入恢复 observer 集合，1006→静默恢复的终态回调（成功 `onConnected` / 失败 `onDisconnected(E213)`）**会**投给它。

**所以这不是“恢复终态回调投不到 connect 形参 listener”的 SDK bug。** 用户“LAN connect 不应出现收不到回调”的直觉与 SDK 实际行为一致——SDK 确实会把恢复终态回调投给该 listener。真正需要留意的是**回调语义与时序**（见第五节），而非“observer 集合漏掉了 listener”。

---

## 二、问题 1：connect 形参 listener 在入口里如何被处理

### 2.1 入口 `connect(config, listener)` → 正常路径一定调用 `prepareConnect`

`ConnectionManager.kt:263` 起是对外入口 `fun connect(config, listener)`。核心分支在「未处于 reconnecting」的 `else` 块（`ConnectionManager.kt:280` 起）：
- 若状态是 CONNECTING/WAIT_CONNECTING → 走“在途判重/排队”分支（可能 `pendingConnectionListeners.add(listener)`，`ConnectionManager.kt:289`）；
- 若状态是 CONNECTED 同配置 → 直接 `listener.onConnected(...)` 并 return（`ConnectionManager.kt:315-318`）；
- **否则（本例首连/断开后重连等 fresh-connect 场景）**，落到 `else` 块末尾：

```kotlin
            // Prepare reconnect manager for new connection
            reconnectManager?.prepareConnect(actualConfig, listener)   // :329
        }

        // Execute actual connection
        connectInternal(actualConfig, listener)                        // :333
```
`ConnectionManager.kt:328-333`

即：**在 `connectInternal` 真正下发内核之前，形参 listener 已被 `prepareConnect` 登记。** 这一步无条件执行（只要进入 fresh-connect 分支）。在 reconnecting 分支里、以及“切不同 config 取消旧连接”分支里，也同样调用了 `reconnectManager.prepareConnect(actualConfig, listener)`（`ConnectionManager.kt:276、339`）。

### 2.2 `prepareConnect` 把 listener 存成 `lastConnectionListener`

```kotlin
    private var lastConnectionListener: ConnectionListener? = null       // :63
    ...
    fun prepareConnect(config: ConnectionConfig?, listener: ConnectionListener) {
        LogUtil.d(TAG, "Preparing connection, resetting reconnect state")
        lastConnectionConfig = config
        lastConnectionListener = listener                                // :89
        isManualDisconnect = false
        currentRetryCount = 0
        ...
```
`ReconnectManager.kt:63、85-91`

### 2.3 形参 listener 同时也被一路带进 `updateConnectionStatus`（但这不是恢复路径）

`connectInternal` 把 `listener` 作为 `connectionCallback.onConnected/onDisconnected` 的闭包捕获变量一路带到 `updateConnectionStatus(..., listener = listener)`（`ConnectionManager.kt:380` 起的 `connectInternal`，CONNECTED 回调在 `ConnectionManager.kt:426-468`）。`updateConnectionStatus` 的 CONNECTED/ERROR/DISCONNECTED 分支确实会直接通知形参 `listener`（`ConnectionManager.kt:1161-1176` 的 `listenersToNotify` 去重；ERROR 分支 `listener?.onError(it)` 在 `:1197`；DISCONNECTED 分支 `listOfNotNull(listener, connectionListener)` 在 `:1212`）。

**关键区分**：这条“形参 listener 直达 `updateConnectionStatus`”的路径，服务的是**非恢复**的首次 onConnected 和普通断开。而 1006→EstablishedRecovery 命中时，`onDisconnected` 回调会**early-return，根本不进 `updateConnectionStatus`**（`ConnectionManager.kt:481-500`：拦截返回 true 时 `syncStatusFromKernel(); return`）。所以恢复终态能不能到形参 listener，**不取决于** `updateConnectionStatus` 的形参分支，而取决于 `recoveryObservers()`——这正是前一份报告判断的落点，但它漏看了 `getLastConnectionListener()` 这条间接路径。

---

## 三、问题 2：`setConnectionListener` 会不会被 connect 自动调用？形参 listener 是否进入全局 connectionListener？

**不会自动调用，形参 listener 也不会被写入全局 `connectionListener`。**

```kotlin
    fun setConnectionListener(listener: ConnectionListener?) {
        LogUtil.d(TAG, "Setting connection listener")
        this.connectionListener = listener                               // :960
    }
```
`ConnectionManager.kt:958-962`

全仓库对 `connectionListener =` 的赋值只有三处（grep 结果）：
- `setConnectionListener` 内（`:960`）——需集成方**显式调用**；
- `removeConnectionListener` 内置 null（`:972`）；
- `startDiscovery` 相关的 `connectionListener = listener`（`:2255`，属扫描发现流程，非本例 LAN connect 主路径）。

`connect(config, listener)` / `connectInternal` 内**没有**对 `connectionListener` 赋值，也**没有**调用 `setConnectionListener`。所以在“集成方只调 connect、从不调 setConnectionListener”的前提下，全局 `connectionListener == null`。形参 listener 进入恢复 observer 的唯一通道因此是第四节的 `getLastConnectionListener()`（或 pending 队列）。

---

## 四、问题 3 + 4：`recoveryObservers()` 的来源聚合，及 `getLastConnectionListener()` 在 LAN connect 流程是否被填充

### 4.1 `recoveryObservers()` 的完整实现与三个来源

```kotlin
    /** Transfer queued callers to the recovery episode that now owns their terminal outcome. */
    override fun recoveryObservers(): List<ConnectionListener> =
        listOfNotNull(connectionListener, reconnectManager?.getLastConnectionListener()) +
            takePendingConnectionListeners()
```
`ConnectionManager.kt:1004-1006`

三个来源：
1. **`connectionListener`（全局）**：仅当集成方显式 `setConnectionListener` 才非空。本例前提下为 null。
2. **`reconnectManager?.getLastConnectionListener()`**：返回 `lastConnectionListener`（`ReconnectManager.kt:510-512`）。**本例前提下 = 本次 connect 形参 listener**（见 §2.1/§2.2）。✅ 这就是决定性的间接登记路径。
3. **`takePendingConnectionListeners()`**：取并清空 `pendingConnectionListeners`（`ConnectionManager.kt:995-998`）。只有“在途重复 connect 排队”才有内容；首连通常为空。

### 4.2 `getLastConnectionListener()` 在 LAN connect 流程确实被设置成本次 listener

- 填充时机：`connect → prepareConnect(actualConfig, listener)`（`ConnectionManager.kt:329`）→ `lastConnectionListener = listener`（`ReconnectManager.kt:89`）。
- 读取实现：
```kotlin
    fun getLastConnectionListener(): ConnectionListener? {
        return lastConnectionListener                                    // :511
    }
```
`ReconnectManager.kt:510-512`。方法上方注释也写明其用途是“当没有注册全局持久 connectionListener 时”兜底（`ReconnectManager.kt:509`）——正好覆盖本例。
- 清空时机：**只有 `ReconnectManager.disconnect()`（手动断开）才会 `lastConnectionListener = null`**（`ReconnectManager.kt:243-244`）。1006 命中 EstablishedRecovery 时**不会**走到这里（拦截 early-return，不进 ReconnectManager、不触发手动 disconnect，见 §2.3 与 `ConnectionManager.kt:481-500`）。因此恢复窗口期内 `lastConnectionListener` 保持为本次 listener。

### 4.3 pendingConnectionListeners 的 add / takePending 时机（补全问题 3）

- add：仅在“同配置连接在途、再次 connect”时 `pendingConnectionListeners.add(listener)`（`ConnectionManager.kt:289`）。
- take+clear：`takePendingConnectionListeners()`（`ConnectionManager.kt:995-998`）在 `recoveryObservers()` 被调用时清空——即 claim 快照那一刻把排队者“转移”给恢复 episode（见 §4.4 测试佐证），避免二次重复通知。

### 4.4 coordinator 如何消费 `recoveryObservers()`

```kotlin
    private fun addEpisodeObserversFromManager() {
        synchronized(episodeObservers) {
            connectionManager.recoveryObservers().forEach { episodeObservers.add(it) }   // :153-154
        }
    }
```
`EstablishedRecoveryCoordinator.kt:151-156`，在 `claimEpoch` 内被调用（`EstablishedRecoveryCoordinator.kt:139`，且重复 fault 合并时也会再并入，`:124`）。
终态投递：
- 成功：`settleSuccess` → `snapshotObservers()` → `observer.onConnected(deviceId, taproVersion)`（`EstablishedRecoveryCoordinator.kt:236-245`）。
- 失败：`settleFailure` → `observer.onDisconnected("Code: 213, ...")`（`EstablishedRecoveryCoordinator.kt:252-266`，E213 来自 `InnerErrorCode.E213`）。

`episodeObservers` 用 `IdentityHashMap` 去重（`EstablishedRecoveryCoordinator.kt:89`），所以即便形参 listener 同时又是全局 listener，也只投一次。

---

## 五、问题 5：最终裁定

**在“集成方只调用 `connect(config, listener)`、从未单独调用 `setConnectionListener`”的前提下：1006→EstablishedRecovery 的恢复终态回调（成功 `onConnected` / 失败 `onDisconnected` E213）能够投递到这个形参 listener。** 这不是 SDK 的 observer 绑定缺陷。

> 推翻前一份报告 H1 的具体点：`lan-1006-no-callback-report.md` §6.2 / §7 H1 断言 `recoveryObservers()`“并不包含当次 connect 传入的 listener 形参（除非它同时也是全局 listener 或在 pending 列表里）”。该断言**漏看了第三个等价来源**——`reconnectManager.getLastConnectionListener()`。由于 `connect` 正常路径必走 `prepareConnect` 把形参 listener 存为 `lastConnectionListener`，该 listener 必然出现在 `recoveryObservers()` 里。H1 不成立。

### 那客户“没收到回调”的真正解释在哪？（把定性从 H1 转向语义/时序）

observer 绑定没问题，所以“彻底收不到任何回调”若属实，真正可能的点是以下几类（均需客户端日志坐实，属语义/时序而非 observer 丢失）：

1. **恢复仍在 5s 窗口内未落终态**：窗口期 SDK 刻意静默（无 onReconnecting/onError/过渡 onDisconnected），`isConnecting()` 投影为 true、`isConnected()` 为 false（`EstablishedRecoveryCoordinator.kt:17-25` 契约；预算 `RECOVERY_BUDGET_MS = 5000L`，`:70`）。若客户恰在窗口内观察，会“看起来没回调”，但终态一定会补发给形参 listener。
2. **回调语义错位（最可能的“体感没回调”）**：恢复失败时投的是 `onDisconnected(E213)`，**不是** 1006、**也不是** `onError`（1006 本就被归类为 normalDisconnect，不走 onError）。若集成方只监听 `onError`、或把“连接成功=onConnected 且随后稳定”作为判据，就会主观判定“没有连接回调”。这属于语义理解问题，不是 observer 没收到。
3. **恢复成功后又掉线**：恢复成功投 `onConnected`，若随后链路再次异常，又会进入新的恢复/断开流程；集成方若只看某一瞬间状态会误判。
4. **回调在后台线程被集成方自身吞掉**：回调非主线程（SDK 规范明确要求集成方自行切主线程），且 SDK 对 observer 回调体抛出的异常是 try/catch 吞并打日志（`EstablishedRecoveryCoordinator.kt:241-243、262-264`；`ConnectionManager.kt:1178-1180`）。若集成方回调里直接刷 UI 抛异常，会表现为“像没回调”。

**最小修复方向**：observer 绑定层面**无需修复**（本就正确）。若要进一步降低“体感无回调”，属**可观测性/文档**改进，而非缺陷修复：
- 可选地在进入恢复窗口时给上层一个“恢复中”的信号（当前设计刻意静默，是 spec 选择，不是 bug）；
- 在集成指南强调：1006 属已建立后的 normalDisconnect，SDK 做一次 5s 静默恢复，失败统一报 `onDisconnected(E213)`（非 1006、非 onError）；回调需自行切主线程。

---

## 六、补充核查

### 6.1 恢复窗口期 disconnect() + removeConnectionListener() 是否清空 observer

- EstablishedRecovery 命中时，`onDisconnected` 拦截分支 early-return，**不会触发** ReconnectManager 的手动 `disconnect()`（后者才清 `lastConnectionListener`，`ReconnectManager.kt:243-244`）。
- 只有**集成方主动**在窗口期调用 `TaplinkSDK.disconnect()` / `removeConnectionListener()` 才会清空：`removeConnectionListener` 置 `connectionListener = null`（`ConnectionManager.kt:972`）并 `establishedRecoveryCoordinator.onExplicitDetachOrDisconnect()`（会取消 episode）。即这是集成方显式行为，不是 SDK 在恢复期自行清空。本例“只调 connect”前提下不发生。
- 结论：恢复窗口期 SDK **不会**自行清空形参 listener。

### 6.2 是否有单测直接覆盖“connect 形参 listener 在恢复终态收到回调”

- `LanRecoveryIntegrationTest.recoveryTakesOwnershipOfPreviouslyQueuedCallers`（`LanRecoveryIntegrationTest.kt:63-80`）：断言 `manager.recoveryObservers()` 包含排队的 caller，并在转移后从旧队列清空——覆盖的是 **pending 队列** 来源。
- `LanRecoveryIntegrationTest.recoveryFailureDeliversOneOutcomeToTransferredQueuedCaller`（`LanRecoveryIntegrationTest.kt:~82-108`）：把 caller 经 `setConnectionListener(queued)` 设为全局 listener + 塞入 pending，驱动真实失败，断言 `queued.disconnected.size == 1` 且含 "213"——覆盖 **全局 listener / pending** 来源。
- `terminalCloseReleasesQueuedCallersWithoutDuplicateDisconnects`（`LanRecoveryIntegrationTest.kt:30-59`）：`primary` 既 `setConnectionListener` 又作为 connect 形参，断言 1006 后 `primary.disconnected.size == 1`、`queued.disconnected.size == 1`——此处 primary 同时是全局 listener，故不能单独隔离“仅形参 listener”这一路。
- **缺口**：目前**没有**一条单测隔离验证“**只经 connect 形参传入、从未 setConnectionListener** 的 listener，在 1006 恢复终态收到回调”这一精确场景。这正是前一份报告 H1 误判的温床。源码层面该路径成立（§2/§4），建议补一条针对性回归测试：仅 `manager.connect(endpoint, listener)`（不 `setConnectionListener`），驱动 1006 established fault，断言该 listener 收到恰好一次 `onConnected`（成功分支）或 `onDisconnected(E213)`（失败分支）。

---

## 七、建议（仅建议，未改动代码）

1. **纠正前一份报告定性**：`lan-1006-no-callback-report.md` 的 H1 应从“observer 不含形参 listener 的 SDK bug”更正为“observer 正确包含形参 listener（经 `getLastConnectionListener()`）；体感无回调源于恢复窗口静默 + E213 语义 + 回调线程，而非 observer 丢失”。
2. **补回归测试**（见 §6.2 缺口）：隔离“仅形参 listener”场景，锁死 `getLastConnectionListener()` 这条间接登记路径，防止未来重构 `prepareConnect`/`recoveryObservers()` 时回归。
3. **文档澄清**（非缺陷修复）：集成指南说明 1006=normalDisconnect→一次 5s 静默恢复→失败报 E213（非 1006/非 onError）；回调可能在后台线程；仍建议集成方成对使用 `setConnectionListener` + `removeConnectionListener`，但即便只用 connect 形参，恢复终态回调亦会送达。
4. **定位客户问题的下一步**：抓客户端 `EstablishedRecovery` 标签日志（`Claimed established-recovery epoch=` / `Recovery SUCCESS` / `Recovery FAILURE ... (E213)` / `Recovery rebuild not ready within budget` / `Recovery prerequisite denied`），确认究竟停在哪个终态；并核对集成方是否只监听 onError、是否在回调里切主线程、是否在 5s 内就判定“无回调”。

---

*报告完。只读核查，未修改任何文件；关键结论均附 `文件:行号` 证据。涉及第三方来源的内容已改写以符合许可要求。*
