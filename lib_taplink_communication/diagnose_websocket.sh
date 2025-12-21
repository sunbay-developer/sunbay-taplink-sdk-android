#!/bin/bash

# WebSocket 连接诊断脚本
# 用于快速诊断 ECONNREFUSED 问题

echo "========================================="
echo "WebSocket 连接诊断工具"
echo "========================================="
echo ""

# 从参数获取目标地址，默认使用日志中的地址
TARGET_IP="${1:-10.14.78.11}"
TARGET_PORT="${2:-8443}"

echo "目标地址: $TARGET_IP:$TARGET_PORT"
echo "时间: $(date)"
echo ""

# 1. 检查网络连通性
echo "========================================="
echo "1. 检查网络连通性"
echo "========================================="
echo "正在 ping $TARGET_IP..."
ping -c 3 $TARGET_IP
if [ $? -eq 0 ]; then
    echo "✅ 网络连通正常"
else
    echo "❌ 网络不通，请检查网络连接"
    exit 1
fi
echo ""

# 2. 检查目标端口
echo "========================================="
echo "2. 检查目标端口 $TARGET_PORT"
echo "========================================="
echo "正在检查端口 $TARGET_PORT..."

# 尝试使用 nc (netcat)
if command -v nc >/dev/null 2>&1; then
    timeout 5 nc -zv $TARGET_IP $TARGET_PORT
    if [ $? -eq 0 ]; then
        echo "✅ 端口 $TARGET_PORT 开放"
        PORT_OPEN=true
    else
        echo "❌ 端口 $TARGET_PORT 未开放 (ECONNREFUSED)"
        PORT_OPEN=false
    fi
elif command -v telnet >/dev/null 2>&1; then
    # 备选：使用 telnet
    timeout 5 telnet $TARGET_IP $TARGET_PORT
    if [ $? -eq 0 ]; then
        echo "✅ 端口 $TARGET_PORT 开放"
        PORT_OPEN=true
    else
        echo "❌ 端口 $TARGET_PORT 未开放 (ECONNREFUSED)"
        PORT_OPEN=false
    fi
else
    echo "⚠️  无法检查端口（缺少 nc 或 telnet 工具）"
    PORT_OPEN=false
fi
echo ""

# 3. 扫描常用端口
echo "========================================="
echo "3. 扫描常用 WebSocket 端口"
echo "========================================="
COMMON_PORTS=(8080 8443 9090 3000 8000 8888 9999)
OPEN_PORTS=()

for port in "${COMMON_PORTS[@]}"; do
    if command -v nc >/dev/null 2>&1; then
        timeout 2 nc -zv $TARGET_IP $port >/dev/null 2>&1
        if [ $? -eq 0 ]; then
            echo "✅ 端口 $port 开放"
            OPEN_PORTS+=($port)
        else
            echo "❌ 端口 $port 关闭"
        fi
    fi
done

if [ ${#OPEN_PORTS[@]} -gt 0 ]; then
    echo ""
    echo "发现开放的端口: ${OPEN_PORTS[*]}"
    echo "建议尝试连接这些端口"
else
    echo ""
    echo "⚠️  没有发现开放的常用端口"
fi
echo ""

# 4. 尝试 HTTP 请求
echo "========================================="
echo "4. 尝试 HTTP 连接"
echo "========================================="
for port in 8080 8000 3000; do
    echo "尝试 HTTP 连接到端口 $port..."
    if command -v curl >/dev/null 2>&1; then
        timeout 5 curl -I "http://$TARGET_IP:$port" 2>/dev/null
        if [ $? -eq 0 ]; then
            echo "✅ HTTP 服务在端口 $port 响应"
            break
        else
            echo "❌ HTTP 服务在端口 $port 无响应"
        fi
    fi
done
echo ""

# 5. 检查本地网络配置
echo "========================================="
echo "5. 本地网络信息"
echo "========================================="
echo "本地 IP 地址:"
if command -v ip >/dev/null 2>&1; then
    ip addr show | grep "inet " | grep -v "127.0.0.1"
elif command -v ifconfig >/dev/null 2>&1; then
    ifconfig | grep "inet " | grep -v "127.0.0.1"
else
    echo "无法获取本地 IP 地址"
fi
echo ""

echo "路由信息:"
if command -v ip >/dev/null 2>&1; then
    ip route | head -5
elif command -v route >/dev/null 2>&1; then
    route -n | head -5
else
    echo "无法获取路由信息"
fi
echo ""

# 6. 生成诊断报告
echo "========================================="
echo "6. 诊断结果总结"
echo "========================================="

if [ "$PORT_OPEN" = true ]; then
    echo "✅ 目标端口 $TARGET_PORT 开放，连接应该正常"
    echo ""
    echo "如果仍然连接失败，请检查："
    echo "  - 服务端应用是否正常运行"
    echo "  - WebSocket 握手是否正确"
    echo "  - 是否需要认证或特殊头部"
else
    echo "❌ 目标端口 $TARGET_PORT 未开放"
    echo ""
    echo "可能的原因："
    echo "  1. 服务端应用未启动"
    echo "  2. 服务端监听的端口不是 $TARGET_PORT"
    echo "  3. 服务端应用崩溃了"
    echo "  4. 防火墙阻止了连接"
    echo ""
    
    if [ ${#OPEN_PORTS[@]} -gt 0 ]; then
        echo "建议尝试以下端口: ${OPEN_PORTS[*]}"
        echo ""
        echo "修改客户端连接协议："
        for port in "${OPEN_PORTS[@]}"; do
            echo "  lan://$TARGET_IP:$port"
        done
    else
        echo "建议："
        echo "  1. 确认服务端应用已启动"
        echo "  2. 检查服务端日志"
        echo "  3. 使用 mDNS 自动发现: lan://auto"
    fi
fi

echo ""
echo "========================================="
echo "诊断完成"
echo "========================================="