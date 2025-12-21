#!/bin/bash

# Service Binding 诊断脚本
# 用于快速诊断 "Service binding died" 问题

echo "========================================="
echo "Service Binding 诊断工具"
echo "========================================="
echo ""

TARGET_PACKAGE="com.sunmi.tapro"
TARGET_SERVICE="com.sunmi.tapro.taplink.service.local.LocalService"
TARGET_ACTION="com.sunmi.tapro.taplink.service.local.ACTION_TRANSACTION"

echo "目标包名: $TARGET_PACKAGE"
echo "目标服务: $TARGET_SERVICE"
echo "服务 Action: $TARGET_ACTION"
echo ""

# 1. 检查目标应用是否安装
echo "========================================="
echo "1. 检查目标应用是否安装"
echo "========================================="
adb shell pm list packages | grep "$TARGET_PACKAGE"
if [ $? -eq 0 ]; then
    echo "✅ 目标应用已安装"
    # 获取版本信息
    adb shell dumpsys package "$TARGET_PACKAGE" | grep "versionName"
else
    echo "❌ 目标应用未安装"
    exit 1
fi
echo ""

# 2. 检查目标进程是否运行
echo "========================================="
echo "2. 检查目标进程状态"
echo "========================================="
adb shell ps -A | grep "$TARGET_PACKAGE"
if [ $? -eq 0 ]; then
    echo "✅ 目标进程正在运行"
else
    echo "⚠️  目标进程未运行（这是正常的，bindService 会自动启动）"
fi
echo ""

# 3. 检查服务声明
echo "========================================="
echo "3. 检查服务声明"
echo "========================================="
echo "查找服务声明..."
adb shell dumpsys package "$TARGET_PACKAGE" | grep -A 10 "Service"
echo ""

# 4. 检查服务是否 exported
echo "========================================="
echo "4. 检查服务 exported 状态"
echo "========================================="
adb shell dumpsys package "$TARGET_PACKAGE" | grep -E "exported|permission"
echo ""

# 5. 清除日志并准备监控
echo "========================================="
echo "5. 准备监控日志"
echo "========================================="
echo "清除旧日志..."
adb logcat -c
echo "✅ 日志已清除"
echo ""

# 6. 提示用户操作
echo "========================================="
echo "6. 开始监控"
echo "========================================="
echo "请在你的应用中尝试连接服务..."
echo "按 Ctrl+C 停止监控"
echo ""
echo "监控以下日志："
echo "  - LocalServiceKernel (客户端)"
echo "  - LocalService (服务端)"
echo "  - AndroidRuntime (崩溃)"
echo "  - ActivityManager (进程管理)"
echo ""

# 7. 监控日志
adb logcat | grep -E "LocalServiceKernel|LocalService|AndroidRuntime|FATAL|ActivityManager.*$TARGET_PACKAGE"
