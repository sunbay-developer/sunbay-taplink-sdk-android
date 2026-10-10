#!/bin/bash
# TapLink SDK Spec 完整性机械校验
#
# 规则来源：docs/spec/SPEC_GUIDE.md（§1.3 来源可追溯、量化门禁）、
#          .agents/skills/spec-authoring/（coverage.yaml / SCR 结构）。
#
# 只检查**可机械判定**的项：结构完整、ID 引用能解析、门禁数字达标、
# EXPLICIT 来源字段齐全且源文件可定位。语义正确性（原文是否真支持规则、
# 规则是否业务正确）靠 spec-authoring 的独立审查与人工闸门，不在本脚本。
#
# 用法：
#   ./config/speccheck/spec-check.sh        校验 docs/spec/coverage|changes 下全部 yaml
#
# 退出码：0 全通过（可含 🟡 提示）；1 有 🔴 阻断项；2 环境缺失。

set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 1

if ! command -v python3 >/dev/null 2>&1; then
    echo "spec-check: 需要 python3" >&2
    exit 2
fi

exec python3 config/speccheck/spec_check.py "$@"
