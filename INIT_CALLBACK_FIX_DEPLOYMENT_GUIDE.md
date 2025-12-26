# INIT 回调修复部署指南

## 修复概述

本次修复解决了 APP TO APP 连接中 `sendInitCommand` 没有将回调保存到 `callbackManager` 的问题，这个问题导致 INIT 响应返回时找不到对应的回调，从而使连接流程卡住，用户无法收到 `onConnected` 或 `onError` 回调。

## 修复内容

### 核心问题
- **问题**：`ConnectionManager.sendInitCommand()` 方法没有将 INIT 命令的回调注册到 `callbackManager`
- **影响**：APP TO APP 连接时，用户永远收不到连接成功或失败的回调
- **严重性**：高（核心功能完全不可用）

### 修复文件
- `lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt`

### 修复要点
1. ✅ 添加了专用的 `callbackManager` 用于管理 INIT 命令回调
2. ✅ 添加了 `responseProcessor` 用于处理 INIT 响应
3. ✅ 修复了 `sendInitCommand()` 方法，正确注册回调
4. ✅ 修复了 `registerDataReceiver()` 方法，正确处理 INIT 响应
5. ✅ 添加了错误处理时的回调清理逻辑
6. ✅ 添加了详细的调试日志

## 部署前检查

### 1. 代码验证
```bash
# 运行验证脚本
./lib_taplink_communication/verify_init_callback_fix.sh
```

预期输出应该显示所有检查项都是 ✅。

### 2. 编译验证
```bash
# 编译项目
./gradlew build

# 检查是否有编译错误
echo $?  # 应该输出 0
```

### 3. 单元测试
```bash
# 运行相关测试
./gradlew test --tests "*ConnectionTest*"
./gradlew test --tests "*AppToAppTest*"
```

## 部署步骤

### 步骤 1: 备份当前版本
```bash
# 创建当前版本的备份
git tag backup-before-init-fix-$(date +%Y%m%d)
git push origin backup-before-init-fix-$(date +%Y%m%d)
```

### 步骤 2: 部署到测试环境
```bash
# 构建测试版本
./gradlew assembleDebug

# 部署到测试设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 步骤 3: 测试验证

#### 3.1 基础连接测试
```kotlin
// 在测试应用中运行
AppToAppConnectionTester.runFullTest(
    context = this,
    appId = "your_test_app_id",
    merchantId = "your_test_merchant_id", 
    secretKey = "your_test_secret_key"
)
```

#### 3.2 日志验证
```bash
# 清除日志
adb logcat -c

# 启动连接测试
# 在应用中触发 APP TO APP 连接

# 监控关键日志
adb logcat | grep -E "ConnectionManager|LocalServiceKernel|INIT"
```

**预期日志输出：**
```
D/ConnectionManager: === APP TO APP Connection Debug ===
D/LocalServiceKernel: === APP TO APP Service Binding Debug ===
D/LocalServiceKernel: Bind service result: true
D/LocalServiceKernel: === ServiceConnection.onServiceConnected ===
D/ConnectionManager: === Service Connection Success ===
D/ConnectionManager: Starting INIT command...
D/ConnectionManager: INIT callback registered with traceId: xxx
D/ConnectionManager: INIT command sent successfully
D/ConnectionManager: Received data: {"traceId":"xxx",...}
D/ConnectionManager: Processing received response: ...
D/ConnectionManager: === INIT Command Result ===
D/ConnectionManager: INIT success: true
D/ConnectionManager: === Calling onConnected Callbacks ===
D/ConnectionManager: === All onConnected Callbacks Completed ===
```

#### 3.3 功能验证清单
- [ ] APP TO APP 连接能够成功建立
- [ ] 收到 `onConnected` 回调
- [ ] 连接后能够正常发起支付
- [ ] 连接失败时能够收到 `onError` 回调
- [ ] 重连机制正常工作
- [ ] 没有内存泄漏（回调被正确清理）

### 步骤 4: 性能测试
```bash
# 连接性能测试
for i in {1..10}; do
    echo "=== 连接测试 $i ==="
    # 触发连接
    # 记录连接时间
    sleep 2
done
```

### 步骤 5: 部署到生产环境
```bash
# 构建生产版本
./gradlew assembleRelease

# 签名和发布
# ... 根据你的发布流程
```

## 回滚计划

如果部署后发现问题，可以快速回滚：

### 快速回滚
```bash
# 回滚到备份版本
git reset --hard backup-before-init-fix-YYYYMMDD
./gradlew assembleRelease
# 重新部署
```

### 问题排查
如果需要排查问题：
```bash
# 收集详细日志
adb logcat > deployment-issue-$(date +%Y%m%d-%H%M%S).log

# 运行诊断脚本
./lib_taplink_communication/diagnose_app_to_app.sh
```

## 监控指标

部署后需要监控以下指标：

### 1. 连接成功率
- **修复前**：APP TO APP 连接成功率可能为 0%
- **修复后**：APP TO APP 连接成功率应该 > 90%

### 2. 连接时间
- **正常范围**：2-10 秒
- **异常情况**：> 30 秒（可能是超时）

### 3. 错误率
- **常见错误**：C22（服务未找到）、C25（绑定失败）
- **修复相关**：不应该再有回调丢失的问题

### 4. 日志监控
监控关键日志的出现频率：
- `INIT callback registered` - 应该与连接尝试次数相等
- `INIT command completed successfully` - 应该与连接成功次数相等
- `=== Calling onConnected Callbacks ===` - 应该与用户收到回调次数相等

## 用户通知

### 发布说明
```
版本 X.X.X 更新内容：

🔧 修复问题：
- 修复了 APP TO APP 连接时可能无法收到连接结果回调的问题
- 提高了连接的稳定性和可靠性

📈 改进：
- 增加了详细的连接日志，便于问题排查
- 优化了连接流程的错误处理

建议所有使用 APP TO APP 连接的用户立即更新到此版本。
```

### 技术支持准备
为技术支持团队准备以下材料：
1. 本部署指南
2. 问题排查指南：`lib_taplink_communication/APP_TO_APP_CALLBACK_TROUBLESHOOTING.md`
3. 诊断脚本：`lib_taplink_communication/diagnose_app_to_app.sh`
4. 修复详细说明：`lib_taplink_communication/INIT_CALLBACK_FIX.md`

## 风险评估

### 低风险
- ✅ 只修改内部实现，不影响公共 API
- ✅ 向后兼容，不会破坏现有功能
- ✅ 有完整的回滚计划

### 中等风险
- ⚠️ 涉及核心连接逻辑，需要充分测试
- ⚠️ 可能影响其他连接模式（LAN、CABLE），需要回归测试

### 缓解措施
- 📋 完整的测试计划
- 📋 分阶段部署（测试环境 → 小范围生产 → 全量部署）
- 📋 实时监控和快速回滚能力

## 成功标准

部署成功的标准：
1. ✅ APP TO APP 连接成功率 > 90%
2. ✅ 用户能够正常收到连接回调
3. ✅ 没有新的崩溃或错误
4. ✅ 连接时间在合理范围内（< 30秒）
5. ✅ 其他连接模式不受影响

## 联系信息

如果部署过程中遇到问题：
- **技术负责人**：TaPro Team
- **紧急联系**：[联系方式]
- **问题报告**：[问题跟踪系统]

---

**部署版本**：1.0.1  
**部署日期**：2025-12-22  
**负责人**：TaPro Team  
**优先级**：高（关键 bug 修复）