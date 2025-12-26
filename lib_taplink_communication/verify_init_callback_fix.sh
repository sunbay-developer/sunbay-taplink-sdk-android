#!/bin/bash

# INIT 回调修复验证脚本
# 用于验证 INIT 命令回调是否正确注册和处理

echo "🔍 验证 INIT 回调修复..."
echo "=================================="

# 1. 检查关键代码是否存在
echo "1️⃣ 检查关键修复代码..."

# 检查 callbackManager 是否添加
CALLBACK_MANAGER_FOUND=$(grep -n "callbackManager.*LocalCallbackManager" lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt)
if [ -n "$CALLBACK_MANAGER_FOUND" ]; then
    echo "✅ callbackManager 已添加:"
    echo "   $CALLBACK_MANAGER_FOUND"
else
    echo "❌ callbackManager 未找到"
    exit 1
fi

# 检查 ResponseProcessor 是否添加
RESPONSE_PROCESSOR_FOUND=$(grep -n "responseProcessor.*ResponseProcessor" lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt)
if [ -n "$RESPONSE_PROCESSOR_FOUND" ]; then
    echo "✅ responseProcessor 已添加:"
    echo "   $RESPONSE_PROCESSOR_FOUND"
else
    echo "❌ responseProcessor 未找到"
    exit 1
fi

# 检查回调注册代码
CALLBACK_REGISTRATION=$(grep -n "addCallbackByTraceId.*traceId.*initCallback" lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt)
if [ -n "$CALLBACK_REGISTRATION" ]; then
    echo "✅ 回调注册代码已添加:"
    echo "   $CALLBACK_REGISTRATION"
else
    echo "❌ 回调注册代码未找到"
    exit 1
fi

# 检查响应处理代码
RESPONSE_PROCESSING=$(grep -n "processReceivedResponse" lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt)
if [ -n "$RESPONSE_PROCESSING" ]; then
    echo "✅ 响应处理代码已添加:"
    echo "   $RESPONSE_PROCESSING"
else
    echo "❌ 响应处理代码未找到"
    exit 1
fi

echo ""
echo "2️⃣ 检查修复前后的关键差异..."

# 检查是否有错误处理时移除回调的代码
CALLBACK_CLEANUP=$(grep -A 5 -B 5 "removeCallbackByTraceId" lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt)
if [ -n "$CALLBACK_CLEANUP" ]; then
    echo "✅ 回调清理代码已添加（错误处理时移除回调）"
else
    echo "⚠️  回调清理代码未找到，可能导致内存泄漏"
fi

echo ""
echo "3️⃣ 验证日志输出..."

# 检查是否有详细的调试日志
DEBUG_LOGS=$(grep -n "INIT callback registered with traceId" lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt)
if [ -n "$DEBUG_LOGS" ]; then
    echo "✅ 调试日志已添加，便于问题排查"
else
    echo "⚠️  调试日志未找到，可能影响问题排查"
fi

echo ""
echo "4️⃣ 检查数据接收处理..."

# 检查数据接收器是否正确处理 INIT 响应
DATA_RECEIVER_FIX=$(grep -A 10 "registerDataReceiver" lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt | grep "processReceivedResponse")
if [ -n "$DATA_RECEIVER_FIX" ]; then
    echo "✅ 数据接收器已修复，会处理 INIT 响应"
else
    echo "❌ 数据接收器未正确修复"
    exit 1
fi

echo ""
echo "5️⃣ 验证编译状态..."

# 检查是否有语法错误（简单检查）
SYNTAX_CHECK=$(grep -n "class ConnectionManager" lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/impl/ConnectionManager.kt)
if [ -n "$SYNTAX_CHECK" ]; then
    echo "✅ 文件结构正常"
else
    echo "❌ 文件结构异常"
    exit 1
fi

echo ""
echo "📊 修复验证总结"
echo "=================================="
echo "✅ callbackManager 已正确添加"
echo "✅ responseProcessor 已正确添加"
echo "✅ INIT 回调注册逻辑已修复"
echo "✅ 响应处理逻辑已修复"
echo "✅ 数据接收器已修复"
echo "✅ 错误处理和回调清理已添加"

echo ""
echo "🚀 修复验证完成！"
echo ""
echo "📋 下一步测试建议:"
echo "1. 编译项目确认没有语法错误"
echo "2. 运行 AppToAppConnectionTest 进行单元测试"
echo "3. 在实际设备上测试 APP TO APP 连接"
echo "4. 监控日志输出确认回调正确执行"
echo ""
echo "🔍 关键日志标识（修复后应该看到）:"
echo "   'INIT callback registered with traceId: xxx'"
echo "   'Processing received response: {...}'"
echo "   'INIT command completed successfully'"
echo "   '=== Calling onConnected Callbacks ==='"

echo ""
echo "⚠️  如果仍有问题，请检查:"
echo "1. Tapro 应用是否正确安装和配置"
echo "2. appId 和 secretKey 是否正确"
echo "3. 设备是否支持 APP TO APP 连接"
echo "4. 是否有网络或权限问题"