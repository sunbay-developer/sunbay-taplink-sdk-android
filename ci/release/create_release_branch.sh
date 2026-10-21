#!/usr/bin/env bash
#
# TapLink SDK — 版本分支创建脚本（供维护者在本地/服务器创建 release_<version> 分支）
# ---------------------------------------------------------------------------
# 用途：把 AGENTS.md §8 的「每个 SDK 版本一个独立分支，方便 Jenkins 选分支打包」
# 落地为可重复执行的工具。本脚本只在本地创建 release_<version> 分支，分支带完整内部
# 内容（含 sdk_publish.gradle / app / docs 等），供 Jenkins 选该分支直接打包。
# 公开导出由 export_public.sh 另行负责，本脚本不碰公开内容。
#
# 行为：
#   1. 从 lib_taplink_sdk/build.gradle.kts 的 SdkVersion.NAME 解析对外版本号。
#      默认取三段正式版（如 1.1.0.3 -> 1.1.0），--full-version 取完整四段（1.1.0.3）。
#   2. 校验来源分支（默认 dev_pro）、工作树干净、目标分支未存在。
#   3. 创建本地分支 release_<version>（默认 dry-run 不落地；--commit 才真正创建）。
#
# 安全红线：
#   - 绝不自动 git push 到任何 remote；绝不自动打 tag。
#   - 不改动来源分支的提交历史；只做 git branch 创建新分支。
#   - 真实 GitHub 推送、打公开 tag、Maven 发布均为维护者人工闸门。
#
# 用法：
#   ci/release/create_release_branch.sh                 # dry-run：解析并预览将创建的分支
#   ci/release/create_release_branch.sh --commit        # 实际创建本地 release_<version> 分支
#   ci/release/create_release_branch.sh --full-version  # 用四段内部构建号命名
#   ci/release/create_release_branch.sh --from dev_pro  # 指定来源分支（默认 dev_pro）
#   ci/release/create_release_branch.sh --help          # 查看用法
#
# 退出码：
#   0  成功（dry-run 预览成功 或 分支创建成功）
#   2  输入 / 前置校验失败（版本解析失败、来源分支不符、工作树脏、目标分支已存在、参数错误）
# ---------------------------------------------------------------------------

set -euo pipefail

# ----- 路径与常量 -----------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SDK_BUILD_FILE="${REPO_ROOT}/lib_taplink_sdk/build.gradle.kts"

# ----- 日志工具 -------------------------------------------------------------
log() { printf '[create_release_branch] %s\n' "$*"; }
err() { printf '[create_release_branch][ERROR] %s\n' "$*" >&2; }

die() {
    # $1 = 退出码, 其余 = 信息
    local code="$1"; shift
    err "$*"
    exit "${code}"
}

# ----- 参数解析 -------------------------------------------------------------
DO_COMMIT=false
FULL_VERSION=false
FROM_BRANCH="dev_pro"

usage() {
    sed -n '2,45p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --commit)       DO_COMMIT=true ;;
        --full-version) FULL_VERSION=true ;;
        --from)
            shift
            [[ $# -gt 0 ]] || die 2 "--from 需要一个分支名参数"
            FROM_BRANCH="$1"
            ;;
        -h|--help) usage; exit 0 ;;
        *) die 2 "未知参数：$1（使用 -h 查看用法）" ;;
    esac
    shift
done

# ----- 前置校验（失败不创建分支）-------------------------------------------
command -v git >/dev/null 2>&1 || die 2 "缺少必需命令：git"
[[ -f "${SDK_BUILD_FILE}" ]] || die 2 "找不到版本文件：${SDK_BUILD_FILE}"

# 必须在 git 仓库内
git -C "${REPO_ROOT}" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || die 2 "不是 git 工作树：${REPO_ROOT}"

# 解析 SdkVersion.NAME（唯一版本来源）。匹配形如：const val NAME = "1.1.0.3"
RAW_VERSION="$(
    grep -E 'const[[:space:]]+val[[:space:]]+NAME[[:space:]]*=' "${SDK_BUILD_FILE}" \
        | head -n1 \
        | sed -E 's/.*NAME[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/'
)"

[[ -n "${RAW_VERSION}" ]] || die 2 "无法从 ${SDK_BUILD_FILE} 解析 SdkVersion.NAME"
# 版本号必须形如 X.Y.Z 或 X.Y.Z.B（纯数字段，点分隔）
if ! [[ "${RAW_VERSION}" =~ ^[0-9]+(\.[0-9]+)+$ ]]; then
    die 2 "解析到的版本号格式非法：'${RAW_VERSION}'（期望形如 1.1.0 或 1.1.0.3）"
fi

# 选择对外版本：默认三段正式版，--full-version 用完整版本
if ${FULL_VERSION}; then
    VERSION="${RAW_VERSION}"
else
    VERSION="$(printf '%s' "${RAW_VERSION}" | cut -d. -f1-3)"
    if ! [[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        die 2 "无法从 '${RAW_VERSION}' 提取三段正式版本号（使用 --full-version 可用完整号）"
    fi
fi

TARGET_BRANCH="release_${VERSION}"

# 来源分支必须存在
git -C "${REPO_ROOT}" show-ref --verify --quiet "refs/heads/${FROM_BRANCH}" \
    || die 2 "来源分支不存在：${FROM_BRANCH}"

# 当前分支必须是来源分支（防止从错误基线切分支）
CURRENT_BRANCH="$(git -C "${REPO_ROOT}" rev-parse --abbrev-ref HEAD)"
if [[ "${CURRENT_BRANCH}" != "${FROM_BRANCH}" ]]; then
    die 2 "当前分支为 '${CURRENT_BRANCH}'，需先切到来源分支 '${FROM_BRANCH}'（或用 --from 指定）"
fi

# 工作树必须干净
if [[ -n "$(git -C "${REPO_ROOT}" status --porcelain)" ]]; then
    die 2 "工作树不干净，请先提交或清理改动后再创建版本分支"
fi

# 目标分支不得已存在
if git -C "${REPO_ROOT}" show-ref --verify --quiet "refs/heads/${TARGET_BRANCH}"; then
    die 2 "目标分支已存在：${TARGET_BRANCH}（如需重建请由维护者手动删除后再执行）"
fi

# ----- 概览 -----------------------------------------------------------------
log "仓库根目录 : ${REPO_ROOT}"
log "来源分支   : ${FROM_BRANCH}"
log "SdkVersion : ${RAW_VERSION}"
log "对外版本   : ${VERSION}（$(${FULL_VERSION} && echo '完整四段' || echo '三段正式版')）"
log "目标分支   : ${TARGET_BRANCH}"
log "执行模式   : $(${DO_COMMIT} && echo '创建分支（--commit）' || echo 'dry-run（不落地）')"

# ----- dry-run：到此为止 ----------------------------------------------------
if ! ${DO_COMMIT}; then
    log "dry-run 完成：未创建任何分支。如需实际创建，追加 --commit。"
    exit 0
fi

# ----- 创建本地分支（不切换、不推送）---------------------------------------
log "创建本地分支 ${TARGET_BRANCH}（基于 ${FROM_BRANCH}）..."
git -C "${REPO_ROOT}" branch "${TARGET_BRANCH}" "${FROM_BRANCH}" \
    || die 2 "创建分支失败：${TARGET_BRANCH}"

log "完成：已在本地创建 ${TARGET_BRANCH}（未切换当前分支、未推送任何 remote）。"
log ""
log "后续维护者人工步骤（脚本不代执行）："
log "  1. Jenkins 打包：在 Jenkins Job 中选择分支 '${TARGET_BRANCH}' 直接打包。"
log "  2. 推送内部分支到内部 origin（确认无误后手动执行）："
log "       git push origin ${TARGET_BRANCH}"
log "  3. 打内部标签只推 origin，公开标签只推 GitHub，禁止 git push --tags 混推（见 AGENTS.md §8）。"
log "  4. 公开导出请使用 ci/release/export_public.sh（白名单导出 + 泄漏自检 + 单提交）。"
exit 0
