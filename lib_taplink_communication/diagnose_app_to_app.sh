#!/bin/bash

# APP TO APP 连接诊断脚本
# 用于排查 APP TO APP 连接没有回调的问题

echo "🔍 APP TO APP 连接诊断开始..."
echo "=================================="

# 1. 检查目标应用是否安装
echo "1️⃣ 检查 Tapro 应用是否安装..."
TAPRO_INSTALLED=$(adb shell pm list packages | grep com.sunmi.tapro)
if [ -z "$TAPRO_INSTALLED" ]; then
    echo "❌ Tapro 应用未安装"
    echo "   请先安装 Tapro 应用"
    exit 1
else
    echo "✅ Tapro 应用已安装: $TAPRO_INSTALLED"
fi

# 2. 检查目标服务是否声明
echo ""
echo "2️⃣ 检查 Tapro 服务声明..."
SERVICE_INFO=$(adb shell dumpsys package com.sunmi.tapro | grep -A 10 "Service")
if [ -z "$SERVICE_INFO" ]; then
    echo "❌ 未找到服务声明"
else
    echo "✅ 找到服务声明:"
    echo "$SERVICE_INFO"
fi

# 3. 检查目标进程状态
echo ""
echo "3️⃣ 检查 Tapro 进程状态..."
PROCESS_INFO=$(adb shell ps -A | grep com.sunmi.tapro)
if [ -z "$PROCESS_INFO" ]; then
    echo "⚠️  Tapro 进程未运行"
    echo "   这是正常的，APP TO APP 连接会自动启动进程"
else
    echo "✅ Tapro 进程正在运行:"
    echo "$PROCESS_INFO"
fi

# 4. 测试服务查询
echo ""
echo "4️⃣ 测试服务查询..."
echo "正在查询可用的交易服务..."

# 创建临时测试应用来查询服务
TEST_INTENT="com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION"
SERVICE_QUERY=$(adb shell am query-services --intent-filter "$TEST_INTENT")
if [ -z "$SERVICE_QUERY" ]; then
    echo "❌ 未找到匹配的服务"
    echo "   检查 Tapro 应用是否正确声明了服务"
else
    echo "✅ 找到匹配的服务:"
    echo "$SERVICE_QUERY"
fi

# 5. 清除日志并准备监控
echo ""
echo "5️⃣ 准备日志监控..."
adb logcat -c
echo "✅ 日志已清除，准备监控连接过程"

echo ""
echo "🚀 诊断完成！现在请在您的应用中尝试 APP TO APP 连接"
echo ""
echo "📋 监控命令（在新终端中运行）:"
echo "   adb logcat | grep -E 'LocalServiceKernel|ConnectionManager|TaplinkApiImpl|LocalService'"
echo ""
echo "🔍 关键日志标识:"
echo "   ✅ 'Service connected' - 服务连接成功"
echo "   ✅ 'INIT command completed successfully' - INIT命令成功"
echo "   ✅ 'onConnected' - 连接回调成功"
echo "   ❌ 'Service binding died' - 服务绑定死亡"
echo "   ❌ 'INIT command failed' - INIT命令失败"
echo "   ❌ 'Bind service result: false' - 服务绑定失败"

echo ""
echo "🛠️  常见问题解决方案:"
echo "   1. 如果看到 'Service binding died':"
echo "      - 检查 Tapro 应用是否崩溃"
echo "      - 尝试重启 Tapro 应用"
echo "      - 检查 AIDL 接口是否匹配"
echo ""
echo "   2. 如果看到 'INIT command failed':"
echo "      - 检查 appId 和 secretKey 是否正确"
echo "      - 检查 Tapro 应用的认证逻辑"
echo "      - 查看详细的错误码和消息"
echo ""
echo "   3. 如果服务绑定成功但没有回调:"
echo "      - 检查 INIT 命令是否超时"
echo "      - 检查 Tapro 应用的 ITransaction 实现"
echo "      - 确认 getExtraInfos() 方法正常工作"

echo ""
echo "⏰ 现在开始监控日志，请在 30 秒内尝试连接..."
timeout 30 adb logcat | grep -E 'LocalServiceKernel|ConnectionManager|TaplinkApiImpl|LocalService|ITransaction'

echo ""
echo "📊 监控结束。如果没有看到预期的日志，请检查:"
echo "   1. 应用是否正确调用了 TaplinkSDK.connect()"
echo "   2. ConnectionListener 是否正确实现"
echo "   3. 是否有其他错误日志"