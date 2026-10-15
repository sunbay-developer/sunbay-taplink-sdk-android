# Review：taplinksdk 当前未提交改动的兼容性与 LAN 快速重连正确性评审

> 范围：`dev_pro` 分支当前工作区未提交改动（`git status` 见文末附录）。本评审为**只读调查**，未修改任何源码。
> 证据以 `文件:行号` 与实际命令输出为准。

---

## 结论速览

### 问题 1：兼容性（9 个 data class → 普通 class + 原地可变 setter + 快照）

| 子问题 | 结论 | 一句话依据 |
|---|---|---|
| 1a 手写 `copy()`/`componentN()` 与原主构造器一致性 | ✅ **一致，无错位** | 9 个类的参数顺序/数量/可空性/默认值逐一核对完全一致；`PaymentMethodInfo` 暴露的是 `_category/_id/_subId`（与旧 data class 主构造参数名一致） |
| 1b Java / Kotlin 源码与二进制兼容性 | ✅ **基本兼容**（有 1 处极端边界） | 所有 `getXxx`/`setXxx`/`copy`/`componentN`/`equals`/`hashCode`/`toString` 的 JVM 签名保留；Java 本就不用 `copy()` 默认参数与解构；唯一边界是 Kotlin 反射 `KClass.isData` 现为 `false`（已在 RELEASE-NOTES 标注💥） |
| 1c 既有集成用户是否依赖「setter 返回新副本、原对象不变」旧语义 | ✅ **未发现受影响用法**（但确有一处依赖「返回值被捕获」才正确） | taplinkdemo 与 tapro/biz_taplink 中对这些 setter 的调用都捕获返回值或采用 `var x = x.setXxx()` 累积写法，结果在新旧语义下一致 |
| 1d 快照 `snapshotForInitialization/Execution` 深度是否足够 | ✅ **足够，无遗漏** | 嵌套可变对象（AmountInfo/PaymentMethodInfo/StaffInfo/DeviceInfo/GoodsDetail 列表/TipConfig→TipSuggestions.values&names）全部拷贝；`SignatureConfig` 仍为不可变 data class 无需拷贝 |

**问题 1 总体结论：这批兼容性改动不会破坏既有集成用户的正确使用。** 编译通过，新增 2 个兼容性测试（7+1 条）全绿。

### 问题 2：LAN 被动断开 + autoReconnect=true 的快速重连

| 子问题 | 结论 | 一句话依据 |
|---|---|---|
| 2a 触发条件完备 & 不误触发 | ✅ **正确** | `ProtocolConfigResolver.buildProtocol` 仅 LAN(含 CROSS_DEVICE+LAN 规范化)返回 `LAN.name`；APP_TO_APP/Cable/SUB_SCREEN 各自返回自己的 mode，不会被当成 LAN |
| 2b 不走 mDNS、用原 IP | ⚠️ **部分成立（需澄清措辞）** | **主路径**确用 `currentConnectionConfig` 原 host/port 直连；但 autoReconnect=true 时 `allowServiceDiscoveryFallback/enableServiceMonitoring` 仍为 true，mDNS 发现是作为**回退**保留的，并非完全关闭 |
| 2c 并发/重入安全 | ⚠️ **主防线有效，但有一处未覆盖的内核状态同步回调风险** | `@Volatile isFastRecovering` + `reconnectManager.isReconnecting()` 能挡住 OPT-R1 与常规重连的双重触发、`connectInternal` 直连绕过 public `connect()` 的早退不会吞掉本次恢复；**但** kernel 状态同步监听的 `isSelfHealing` 只判 `isReconnecting()` 未判 `isFastRecovering`，恢复期内核中间态 DISCONNECTED 可能触发一次「伪 onDisconnected + onPhysicalDisconnected」 |
| 2d 预算/超时协调 & 是否违反 306 铁律 | ✅ **协调且不违反铁律** | FAST_RECOVERY_BUDGET_MS=5s ≪ 交易回调超时 TRANSACTION_TIMEOUT=180s；SDK 的快速重连只重连**传输层**、从不重发交易，失败才以 E306 上报，由集成方「先查询」。306 铁律约束的是「集成方不要用原 ID 自动重试交易」，与此不冲突 |
| 2e 成功路径「维持在途交易」 | ⚠️ **SDK 侧成立，端到端取决于 Host/内核会话语义** | SDK 侧快速恢复分支不调用 `onPhysicalDisconnected()`，在途回调（按 traceId）与 INIT 状态被保留，180s 内不超时；但「之前 send 出去的请求重连后还能收到响应」依赖 Tapro Host 在同一笔交易上继续回推——此为协议/Host 行为，本仓库静态代码无法单独证明 |

**问题 2 总体结论：快速重连的核心逻辑正确实现且不违反交易铁律**；存在 2 处需关注项（2c 内核状态同步未覆盖 `isFastRecovering`；2b/2e 的措辞需明确「mDNS 作为回退仍在」「在途交易能否收到响应取决于 Host」）。

---

## 问题 1 详细证据

### 1a. 9 个类的 copy()/componentN() 与旧主构造器逐一核对

方法：`git show HEAD:<path>` 取旧 data class 主构造器，与新文件的 `operator fun componentN()` 与 `fun copy(...)` 对比。

| 类 | 参数数 | 顺序/数量/可空性/默认值 | 结论 |
|---|---|---|---|
| `config/TaplinkConfig.kt` | 11 | appId, merchantId?, secretKey, logEnabled=false, logLevel=INFO, defaultStaffInfo?, defaultDeviceInfo?, version=BuildConfig, timeout=180, taproAppWidthRatio?, autoOpenUsbScreenPlayer=false | ✅ component1-11、copy 全一致（`TaplinkConfig.kt:237-273`） |
| `model/common/AmountInfo.kt` | 7 | orderAmount, pricingCurrency(均必填), tipAmount?, taxAmount?, surchargeAmount?, cashbackAmount?, serviceFee? | ✅（`AmountInfo.kt:160-184`） |
| `model/common/DeviceInfo.kt` | 13 | 全部 String? = null | ✅ component1-13（`DeviceInfo.kt:227-269`） |
| `model/common/GoodsDetail.kt` | 4 | goodsId?, goodsName?, quantity?, price? | ✅（`GoodsDetail.kt:85-100`） |
| `model/common/PaymentMethodInfo.kt` | 3 | `_category?`, `_id?`, `_subId?` | ✅ component1-3 暴露的是**下划线后备字段**（与旧 data class 主构造参数名一致），非枚举 `category/id/subId`（`PaymentMethodInfo.kt` copy/component 段） |
| `model/common/StaffInfo.kt` | 2 | operatorId?, tipRecipientId? | ✅ |
| `model/common/TipConfig.kt` | 5 | useHostConfig=false, onScreenTip=false, tipMode=ON_SALE, tipWithTax=false, suggestions? | ✅ 默认值保留（`TipConfig.kt:149-167`） |
| `model/common/TipSuggestions.kt` | 3 | feeMode, values(均必填), names? | ✅（`TipSuggestions.kt:76-88`） |
| `model/request/PaymentRequest.kt` | 21 | action(必填)…printReceipt?=PrintReceipt.AUTO…tipConfig? | ✅ component1-21、copy 全一致，`printReceipt` 默认值 `PrintReceipt.AUTO` 在新主构造器保留（`PaymentRequest.kt:461-527`、新构造器 `:224`） |

未发现任何 componentN 错位、copy 缺参数或默认值变化。

**公开方法签名 diff（旧 vs 新）**：对 9 个文件做 `fun xxx(...)` 签名集合 diff，结果**只有新增**（`componentN`/`equals`/`hashCode`/`toString` 由编译器自动生成变为显式方法），**无任何删除或签名变更**。所有 `setXxx`/`getXxx`/`copy`/`builder` 保留。

**一处无害的语义细节**：`PaymentMethodInfo` 的 `category/id/subId` 旧实现用 `by lazy`（缓存一次），新实现用 `get()`（每次按 `_category` 重算）。因 setter 现在会原地改 `_category`，用 `get()` 反而更正确（旧 lazy 若在 setter 之后读会读到旧值）；新增测试 `enumGettersSerializationAndMerchantValidationStayConsistent` 正是锁这个行为。

### 1b. Java / Kotlin 兼容性

- **Java 集成用户**：Java 从来无法使用 Kotlin 的 `copy(默认参数)` 与解构 `component`，一直通过 `getXxx()` 访问、`new AmountInfo(a,b,null,null,...)` 全参构造。这些在普通 class 下全部保留。`JavaFieldSetterCompatibilityTest`（`.../config/JavaFieldSetterCompatibilityTest.java`）验证 `getTimeout()/getLogEnabled()/getOrderAmount()/getStaffInfo()` 与链式 setter 返回 this 均正常。→ **Java 侧无影响**。
- **Kotlin 集成用户**：
  - 解构 `val (a,b,c)=x`：`componentN()` 描述符保留 → 二进制兼容。
  - `copy(...)` 带默认参数：data class 与手写版都会生成 `copy$default` 合成方法，描述符一致 → 已编译的 Kotlin 调用方二进制兼容。
  - `equals/hashCode` 作为 HashMap key：手写实现与 data class 行为对齐。`hashCodesMatchLegacyDataClasses` 测试显式校验 **boolean 字段按 Kotlin 的 0/1 计算（非 Java 的 1231/1237）**，并覆盖 `-0.0f`/`NaN` 浮点边界（`copyComponentsAndValueSemanticsRemainAvailable`）→ 作为 key 的行为未变。
  - **唯一边界**：`KClass.isData` 由 `true` 变 `false`。仅影响依赖「反射判断是否 data class」的极端用法。此点 RELEASE-NOTES 已💥标注并给出迁移说明（`RELEASE-NOTES.md`：“Kotlin reflection consumers must account for isData = false”）。
- **public 方法签名/返回类型**：无变化（见 1a 的 diff 结论）。

### 1c. 既有集成是否依赖「setter 返回新对象、原对象不变」旧语义

grep 了 taplinkdemo 全仓与 tapro/biz_taplink 对这 9 类 setter / copy 的调用：

- **taplinkdemo** 唯一命中点 `lib_service/.../TaplinkPaymentService.kt:198-206` `buildAmountInfo`：
  ```kotlin
  var amountInfo = AmountInfo(orderAmount=…, pricingCurrency=…)
  tipAmount?.let { amountInfo = amountInfo.setTipAmount(toCents(it)) }   // 始终重新赋值
  …
  return amountInfo
  ```
  旧语义：每次 setter 返回新副本并重新赋值；新语义：原地改并返回 this。**两者最终 `amountInfo` 内容相同**，且从未保留 setter 之前的旧引用 → 无受影响。
- **tapro/biz_taplink** 两处 `resolveRefundPaymentCategory`（`headless/HeadlessTransactionDelegateImpl.kt:572-627`、`dispatch/DispatchViewModel.kt:767+`）：
  ```kotlin
  return try { when(product){ CPM -> paymentRequest.setPaymentMethod(PaymentMethodInfo(category=QR_CPM, id=paymentRequest.paymentMethod?.id, …)) … } }
  // 调用方：paymentRequest = resolveRefundPaymentCategory(paymentRequest)
  ```
  `setPaymentMethod` 的返回值作为函数返回值被**调用方捕获**（`paymentRequest = …`），后续只用返回值传给 `TaplinkPaymentConverter.convert`。新旧语义下返回对象都带正确 QR 分类 → **结果一致**。另注意 `PaymentMethodInfo` 构造里先读 `paymentRequest.paymentMethod?.id/subId` 再 `setPaymentMethod` 覆盖，先读后写、无 read-after-write 风险。
- `DispatchViewModel.kt:918` `return paymentRequest.copy(signatureConfig = signatureConfig.copy(threshold = null))`：用 `copy()` 产生新对象，语义不变（`copy()` 始终返回独立对象，测试 `copyComponentsAndValueSemanticsRemainAvailable` 已验证）。

**结论**：未发现任何「保留旧引用并期望其不被 setter 改动」的受影响用法。唯一需知悉的是上面两处依赖「返回值被捕获」——现状代码确实捕获了，故安全；若未来有人改成「只调 `obj.setXxx(...)` 丢弃返回值并继续用 `obj`」，新语义下反而仍正确（原地改）。RELEASE-NOTES 已给出「依赖 copy 语义者请显式 `original.copy().setXxx()`」的迁移提示。

### 1d. 快照深度

`impl/RequestSnapshots.kt`：
```kotlin
internal fun TaplinkConfig.snapshotForInitialization() = copy(
    defaultStaffInfo = defaultStaffInfo?.copy(),
    defaultDeviceInfo = defaultDeviceInfo?.copy())
internal fun PaymentRequest.snapshotForExecution() = copy(
    amount = amount?.copy(), paymentMethod = paymentMethod?.copy(),
    staffInfo = staffInfo?.copy(), deviceInfo = deviceInfo?.copy(),
    goodsDetail = goodsDetail?.map { it.copy() },
    tipConfig = tipConfig?.let { it.copy(suggestions = it.suggestions?.let { s ->
        s.copy(values = s.values.toList(), names = s.names?.toList()) }) })
```
- 覆盖了 PaymentRequest 的全部嵌套可变对象，并对 `GoodsDetail` 列表、`TipSuggestions.values/names` 列表做了**新建列表**拷贝（`.map{}` / `.toList()`），隔离外部对列表元素/内容的后续 mutation。
- `AmountInfo` 内均为不可变 `BigDecimal`，浅拷贝即足够。
- `SignatureConfig`（`model/common/SignatureConfig.kt`）仍是 `val` data class、无原地 setter，调用方无法在 execute 后改它，故未拷贝不构成遗漏。
- 使用点：`TaplinkApiImpl.init():154 config.snapshotForInitialization()`、`PaymentManager.execute():132 request.snapshotForExecution()`，都在 SDK 取得所有权的边界处。
- 测试 `submittedSnapshotDoesNotTrackLaterSettersOrCollectionChanges` 在快照后对 request 及其所有嵌套对象/列表做 mutation，断言快照 JSON 不变 → **隔离成立，无遗漏**。

### 1 的构建与测试证据

```
./gradlew :lib_taplink_sdk:compileDebugKotlin :lib_taplink_sdk:testDebugUnitTest \
  --tests '*FieldSetterCompatibility*' --tests '*JavaFieldSetterCompatibility*'
=> BUILD SUCCESSFUL
```
- `TEST-...FieldSetterCompatibilityTest.xml`：tests="7" failures="0" errors="0"
- `TEST-...config.JavaFieldSetterCompatibilityTest.xml`：tests="1" failures="0" errors="0"
- 编译仅 4 条无害 warning（903 行冗余 `!!`、1360/1361 行未用参数），与本评审主题无关。

---

## 问题 2 详细证据

OPT-R1 代码位于 `manager/ConnectionManager.kt`：`onDisconnected`(70/422)、`tryFastRecovery`(540)、`fastRecoveryFallback`(591)、常量 `FAST_RECOVERY_BUDGET_MS=5_000L`/`FAST_RECOVERY_POLL_MS=200L`(1800-1801)。

### 2a. 触发条件完备且无误触发

`onDisconnected` else(normalDisconnect) 分支（`ConnectionManager.kt:473-499`）：
```kotlin
val isLanMode = currentConnectionMode == ConnectionMode.LAN.name
val autoReconnectEnabled = currentConnectionConfig?.autoReconnect == true
val isManual = reconnectManager?.isManualDisconnect() == true
val hasInFlight = paymentManager?.hasInFlightTransactions() == true
val canFastRecover = isLanMode && autoReconnectEnabled && !isManual &&
    hasInFlight && !isFastRecovering &&
    reconnectManager?.isReconnecting() != true && currentConnectionConfig != null
```
- `currentConnectionMode` 由 `ProtocolConfigResolver.buildProtocol` 产生（`connectInternal:321`）。`ProtocolConfigResolver.kt`：仅 `connectionMode==LAN && host 非空`（或 host 提供）返回 `ConnectionMode.LAN.name`；SUB_SCREEN→`SUB_SCREEN.name`、cable→cable 协议名、其余→`APP_TO_APP.name`。
- `CROSS_DEVICE+LAN`：`ConnectionConfig.applyCrossDeviceNormalization()`（`ConnectionConfig.kt:168`）把 `connectionMode` 归一为 `ConnectionMode.LAN` 并清掉 cableProtocol → 经 resolver 得 `LAN.name`。`CROSS_DEVICE+CABLE`→`ConnectionMode.CABLE`→cable 协议名（非 LAN）。
- `hasInFlightTransactions()`（`PaymentManager.kt:865`）= `pendingTransaction!=null || hasPendingAutoConnectTransaction || callbackManager.hasActiveCallbacks()`，能准确反映「有在途/排队交易」。
→ **仅真实 LAN 且确有在途交易、autoReconnect 开启、非手动断开、未在恢复/重连中才触发，APP_TO_APP/Cable/SUB_SCREEN 不会误触发。**

### 2b. 不走 mDNS、用原 IP —— 需澄清

- `tryFastRecovery` 用 `currentConnectionConfig`（含原 host/port）调 `connectInternal(config, internalReconnectListener)`（`ConnectionManager.kt:540-552`）。
- `connectInternal:321` 经 `ProtocolConfigResolver.buildProtocol(config)` → `ProtocolManager.buildLanProtocol(host, port, secure=false)`，即**用原 IP/端口直连**。
- **但**：`connectInternal:368` 传给内核的 `LanConnectionOptions(allowServiceDiscoveryFallback = autoReconnect != false, enableServiceMonitoring = autoReconnect != false)`。本场景 autoReconnect=true，故**mDNS 发现回退与服务监控仍开启**，只是作为「直连失败后的回退/地址变更跟随」，并非完全不走 mDNS。RELEASE-NOTES（`:78-81`）也明确「autoReconnect 开启时失败的直连可跟随 mDNS 新端口重连」。
→ **主路径确为原 IP 直连、不先做 mDNS 发现；但 mDNS 作为回退仍在，描述「完全不走 mDNS」需修正为「优先原 IP 直连，mDNS 仅作回退」。**

### 2c. 并发/重入安全 —— 主防线有效，一处未覆盖

**有效的部分**：
- `@Volatile private var isFastRecovering`（`ConnectionManager.kt:123-124`，确为 @Volatile）+ `canFastRecover` 里的 `!isFastRecovering && reconnectManager?.isReconnecting() != true`，并在触发后 `tryFastRecovery(...)` 结束即 `return`（`:489-493`），避免落入下方 `reconnectManager?.onDisconnected()` 常规重连 → **OPT-R1 与 ReconnectManager 不会双触发**。
- 恢复期内核若再回调 `onDisconnected`：`canFastRecover` 因 `!isFastRecovering` 为 false → 不会二次进入 OPT-R1，而是走常规 `updateConnectionStatus(DISCONNECTED)+reconnectManager.onDisconnected()`。`failAllPendingTransactions` 幂等（`callbackManager.failAllCallbacks` 对已清空集合无副作用）。
- `tryFastRecovery` 直接调**私有** `connectInternal`，绕过 public `connect()` 的早退（stale CONNECTING 自愈、`isSameConfig` 已连接跳过、same-config 入队，见 `ConnectionManager.kt:201-290`）；`connectInternal:300-303` 在非 CONNECTING 时置 CONNECTING 并继续执行 → **本次恢复连接不会被早退吞掉**。

**未覆盖的风险（建议修复）**：
- kernel 状态同步监听 `setupKernelStatusListener`（`ConnectionManager.kt:1560+`）在 `droppedToInactive && wasActive && !isSelfHealing` 时会 `connectionDisconnectedListener?.invoke()` + 向集成方 `listener.onDisconnected(reason)`。其中 `isSelfHealing = reconnectManager?.isReconnecting() == true`，**没有把 `isFastRecovering` 计入抑制条件**（`:1577`）。
- 快速恢复期间 `connectInternal` 重连时内核会出现 CONNECTED→(中间态 DISCONNECTED/ERROR)→CONNECTING 的跳变；此时 `isReconnecting()` 为 false（OPT-R1 不走 ReconnectManager），抑制不生效，于是可能：① 向集成方发一次**伪 `onDisconnected`**；② 触发 `connectionDisconnectedListener`→`PaymentManager.onPhysicalDisconnected()`（`PaymentManager.kt:117-125`），它会清 `hasPendingAutoConnectTransaction` 并 `responseProcessor.clearProcessedTerminalEvents()`——与「维持在途交易」的意图相悖。
- **风险等级：中**。是否真的发生取决于内核在 `connectInternal` 期间是否产生中间 DISCONNECTED 状态事件；本评审为静态分析，未在真机 LAN 场景复现确认。**建议**：把 `isSelfHealing` 改为 `reconnectManager?.isReconnecting() == true || isFastRecovering`，使快速恢复期的内核状态抖动同样被抑制。

### 2d. 预算/超时协调 & 306 铁律

- `FAST_RECOVERY_BUDGET_MS=5_000L`、轮询 200ms；交易回调超时 `LocalCallbackManager.TRANSACTION_TIMEOUT=180_000L`（`lib_taplink_communication/.../util/LocalCallbackManager.kt:53-55`）。5s ≪ 180s，恢复窗口内在途回调不会超时，故成功路径「无需冻结倒计时」成立。
- 失败路径 `tryFastRecovery:566-572`：`failAllPendingTransactions(E306)` 后 `fastRecoveryFallback` 报 `E213`（`InnerErrorCode` 定义见 `.../enums/InnerErrorCode.kt`：E306="306" Response timeout / 先查询；E213="213" Connection disconnected）。
- **是否违反「306 严禁自动重试」铁律？不违反。**
  - 该铁律（`taplinksdk/AGENTS.md §4.5`、集成指南 `docs/Sunbay Taplink SDK for Android - 集成指南 (NEW).md:184/196`）约束的是**集成方收到 306 后不得用原 `transactionRequestId` 盲目重发交易**，必须先 `query()`。
  - OPT-R1 做的是**传输层单次重连**（`connectInternal` 只重建 WebSocket/链路），**从不重发交易请求**；重连失败才把在途交易以 E306 收口交回集成方去「先查询」。两者语义正交 → 不冲突。
- 另 `fastRecoveryFallback` 明确「不再透传底层 1006，统一报 E213」，与 `isConnectionError` 把 `1006` 归入 `normalDisconnectCodes`（`:1089-1098`，返回 false 走 normalDisconnect 分支）一致。

### 2e. 成功路径「维持在途交易」

- 快速恢复成功分支（`:562-564`）只记日志、不进 ReconnectManager；关键在于它**没有**调用 `onPhysicalDisconnected()`（后者会清 INIT/terminal 状态）。按 traceId 注册的在途回调在 `LocalCallbackManager` 中保留，180s 内不超时。→ **SDK 侧「保留在途交易上下文」成立。**
- **但端到端能否真的收到响应**取决于 Tapro Host：重连后是否在同一笔交易（同 traceId）上继续回推结果。若 Host 把断链视作会话结束而不再回推，则在途交易会一直挂到 180s 超时。本仓库静态代码无法单独证明 Host 行为，故「维持在途交易=一定能收到原响应」这一说法在**跨仓库协议层面有前提**，建议：成功恢复后仍受交易超时兜底（已有 180s），并在真机 LAN 场景验证 Host 侧的续推行为。另见 2c 的内核状态同步风险，若该路径误触发 `clearProcessedTerminalEvents()`，可能导致同一终态被当作「未处理」重复分发，需一并验证。

---

## 建议（仅建议，未改动任何代码）

1. **（2c，中优先）** `setupKernelStatusListener` 的 `isSelfHealing` 判定加入 `|| isFastRecovering`，避免快速恢复期的内核状态抖动触发伪 `onDisconnected` 与 `onPhysicalDisconnected()`。
2. **（2b/2e，文档）** 修正 OPT-R1 注释与 RELEASE-NOTES 措辞：快速恢复「**优先原 IP 直连，mDNS 仅作回退**」而非「不走 mDNS」；「维持在途交易」补充「取决于 Host 在重连后对同一 traceId 的续推」。
3. **（验证）** 在真机 LAN + autoReconnect=true 场景跑一次被动断开（拔网/Host 侧 1006）压测，重点观测：恢复成功时集成方是否收到多余 `onDisconnected`、在途交易回调是否收到最终结果、是否出现终态重复分发。
4. 顺手可清掉 `ConnectionManager.kt:903` 的冗余 `!!` 与 1360/1361 未用参数（非阻塞）。

---

## 附录：本批未提交改动文件（`git status`，分支 dev_pro）

改动源码：`config/TaplinkConfig.kt`、`model/common/{AmountInfo,DeviceInfo,GoodsDetail,PaymentMethodInfo,StaffInfo,TipConfig,TipSuggestions}.kt`、`model/request/PaymentRequest.kt`、`impl/TaplinkApiImpl.kt`、`manager/{ConnectionManager,PaymentManager}.kt`、`lib_taplink_sdk/build.gradle.kts`。
新增：`impl/RequestSnapshots.kt`、`src/test/.../FieldSetterCompatibilityTest.kt`、`src/test/.../config/JavaFieldSetterCompatibilityTest.java`、`docs/spec/TAPLINK_CONFIG_SETTER_SPEC.md`（及 spec coverage/sources）。
文档：`AGENT.md`、`API-REFERENCE.md`、`README.md`、`RELEASE-NOTES.md`、`.agents/skills/tdd/SKILL.md`。

> 评审方式：静态代码核对 + 对 taplinkdemo/tapro 调用点 grep + 实际编译与新增兼容性测试运行。未执行真机 LAN 场景，2c/2e 的实际触发以真机验证为准。
