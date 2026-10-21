#!/usr/bin/env bash
#
# TapLink SDK — 公开内容白名单导出脚本（供维护者生成对外 GitHub 公开源码单提交）
# ---------------------------------------------------------------------------
# 用途：把内部 dev_pro / release_<version> 分支的内容，按「白名单驱动、默认全排除」
# 的方式导出为只含 SDK 公开源码 + 必要构建配置 + 面向集成商公开文档的产物，经泄漏
# 自检后压缩为一个英文 Conventional Commit（孤儿分支），供维护者 review 后手动推送到
# GitHub main。落地严格对齐 AGENTS.md §8/§8.1/§10。
#
# 核心原则：
#   - 白名单驱动：默认排除一切，只拷贝下方 WHITELIST_* 显式枚举的路径。
#     不依赖 .gitignore（仓库中 docs/、secring.gpg、gradle.properties 等均已被 Git 追踪，
#     忽略规则对已追踪文件无效），也不用「全拷贝再删黑名单」。
#   - 导出副本清理只改产物，绝不改内部分支上给 Jenkins 打包用的原始文件：
#       * 移除 lib_taplink_sdk/build.gradle.kts 末尾 apply(from = "sdk_publish.gradle")
#       * 删除 settings.gradle.kts 的 include(":app")
#   - 泄漏自检命中任一项即失败并中止，不产出任何公开 commit。
#
# 安全红线：
#   - 绝不自动 git push 到任何 remote；绝不自动打 tag；凭证从不写入产物。
#   - 真实 GitHub 推送、打公开 tag 均为维护者人工闸门。
#
# 用法：
#   ci/release/export_public.sh                 # dry-run（默认）：预览将导出的白名单文件清单
#   ci/release/export_public.sh --out <dir>     # 指定导出目录（默认 build/public-export）
#   ci/release/export_public.sh --version X.Y.Z # 覆盖自动解析的版本号（用于 commit message）
#   ci/release/export_public.sh --commit        # 导出 + 清理 + 泄漏自检 + 生成孤儿分支单提交
#   ci/release/export_public.sh --help          # 查看用法
#
# 退出码：
#   0  成功（dry-run 预览成功 或 导出+自检+单提交成功）
#   2  输入 / 校验失败（版本解析失败、白名单源缺失、参数错误）
#   3  泄漏自检失败（命中凭证明文 / 敏感文件 / 内部路径 / 清理未生效）
#      —— 与 publish_sdk.sh 的退出码风格一致（0 成功 / 2 输入校验 / 3+ 处理或安全失败）；
#         本脚本不构建，故 3 专用于泄漏自检失败。
# ---------------------------------------------------------------------------

set -euo pipefail

# ----- 路径与常量 -----------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SDK_BUILD_FILE="${REPO_ROOT}/lib_taplink_sdk/build.gradle.kts"
DEFAULT_OUT="${REPO_ROOT}/build/public-export"

# ----- 公开白名单（导出产物只保留这些路径，其余一律不拷贝）------------------
# 顶层文件
WHITELIST_FILES=(
    "build.gradle.kts"
    "settings.gradle.kts"       # 导出副本需删除 include(":app")
    "gradle.properties"
    "gradlew"
    "gradlew.bat"
    "README.md"
    "API-REFERENCE.md"
    "RELEASE-NOTES.md"
    "gradle/wrapper/gradle-wrapper.jar"
    "gradle/wrapper/gradle-wrapper.properties"
    "gradle/libs.versions.toml"
    "lib_taplink_sdk/build.gradle.kts"      # 导出副本需移除 apply(from = "sdk_publish.gradle")
    "lib_taplink_sdk/consumer-rules.pro"
    "lib_taplink_sdk/proguard-rules.pro"
    "lib_taplink_communication/build.gradle.kts"
    "lib_taplink_communication/consumer-rules.pro"
    "lib_taplink_communication/proguard-rules.pro"
)
# 白名单目录（递归拷贝整个目录；仅 main，不含 test/androidTest，保持最小公开面）
WHITELIST_DIRS=(
    "lib_taplink_sdk/src/main"
    "lib_taplink_communication/src/main"
)

# ----- 精确排除（即使白名单目录内也兜底剔除的敏感文件名/相对路径）-----------
# 这些不应出现在导出产物里；若出现即视为白名单逻辑异常，由泄漏自检二次兜底。
EXCLUDE_PATHS=(
    "ci"
    "docs"
    ".cursor"
    ".agents"
    ".kiro"
    ".github"
    ".idea"
    ".gradle"
    "build"
    "app"
    "config"
    ".worktrees"
    "sdk_publish.gradle"
    "lib_taplink_sdk/sdk_publish.gradle"
    "lib_taplink_sdk/secring.gpg"
    "lib_taplink_sdk/gradle.properties"
    "secring.gpg"
    "local.properties"
    "AGENT.md"
    "AGENTS.md"
    "llms.txt"
    "SUB_SCREEN_INTEGRATION.md"
)

# ----- 泄漏自检关键词（命中任一即失败；维护者可在此增补）--------------------
# 1) 凭证明文（RUNBOOK / gradle.properties 中的真实样本）
# 2) 签名/凭证配置键
LEAK_KEYWORDS=(
    "PDUNa6"
    "ikAlguiWOhfrTQM6nkE9MzHeGFsV89pGC"
    "sunmi388"
    "CC5611FB"
    "signing.password"
    "signing.keyId"
    "signing.secretKeyRingFile"
    "SUNBAY_MAVEN_PASSWORD"
    "SUNBAY_MAVEN_USERNAME"
    "sunbayMavenPassword"
    "sunbayMavenUsername"
)
# 敏感文件名/内部材料残留（按文件名检测）
LEAK_FILENAMES=(
    "sdk_publish.gradle"
    "secring.gpg"
    "local.properties"
    "AGENT.md"
    "AGENTS.md"
    "llms.txt"
)
# 清理未生效检测（这些字符串不得出现在导出产物中）
LEAK_RESIDUE=(
    'apply(from = "sdk_publish.gradle")'
    'include(":app")'
)

# ----- 日志工具 -------------------------------------------------------------
log() { printf '[export_public] %s\n' "$*"; }
err() { printf '[export_public][ERROR] %s\n' "$*" >&2; }

die() {
    # $1 = 退出码, 其余 = 信息
    local code="$1"; shift
    err "$*"
    exit "${code}"
}

# ----- 参数解析 -------------------------------------------------------------
DO_COMMIT=false
OUT_DIR="${DEFAULT_OUT}"
OVERRIDE_VERSION=""

usage() {
    sed -n '2,52p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --commit)  DO_COMMIT=true ;;
        --dry-run) DO_COMMIT=false ;;
        --out)
            shift
            [[ $# -gt 0 ]] || die 2 "--out 需要一个目录参数"
            OUT_DIR="$1"
            ;;
        --version)
            shift
            [[ $# -gt 0 ]] || die 2 "--version 需要一个版本号参数"
            OVERRIDE_VERSION="$1"
            ;;
        -h|--help) usage; exit 0 ;;
        *) die 2 "未知参数：$1（使用 -h 查看用法）" ;;
    esac
    shift
done

# ----- 前置校验 -------------------------------------------------------------
command -v git >/dev/null 2>&1 || die 2 "缺少必需命令：git"
[[ -f "${SDK_BUILD_FILE}" ]] || die 2 "找不到版本文件：${SDK_BUILD_FILE}"
git -C "${REPO_ROOT}" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || die 2 "不是 git 工作树：${REPO_ROOT}"

# 解析版本号（用于 commit message）
if [[ -n "${OVERRIDE_VERSION}" ]]; then
    VERSION="${OVERRIDE_VERSION}"
else
    RAW_VERSION="$(
        grep -E 'const[[:space:]]+val[[:space:]]+NAME[[:space:]]*=' "${SDK_BUILD_FILE}" \
            | head -n1 \
            | sed -E 's/.*NAME[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/'
    )"
    [[ -n "${RAW_VERSION}" ]] || die 2 "无法从 ${SDK_BUILD_FILE} 解析 SdkVersion.NAME"
    VERSION="$(printf '%s' "${RAW_VERSION}" | cut -d. -f1-3)"
fi
if ! [[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    die 2 "版本号格式非法：'${VERSION}'（期望形如 1.1.0，可用 --version 覆盖）"
fi

# 白名单源必须存在（缺失视为校验失败）
for f in "${WHITELIST_FILES[@]}"; do
    [[ -e "${REPO_ROOT}/${f}" ]] || die 2 "白名单源文件缺失：${f}"
done
for d in "${WHITELIST_DIRS[@]}"; do
    [[ -d "${REPO_ROOT}/${d}" ]] || die 2 "白名单源目录缺失：${d}"
done

# ----- 收集将导出的文件清单（供 dry-run 预览与实际拷贝共用）-----------------
# 判断某相对路径是否命中精确排除
is_excluded() {
    local path="$1" ex
    for ex in "${EXCLUDE_PATHS[@]}"; do
        # 完全相等，或位于被排除目录下（ex/ 前缀）
        if [[ "${path}" == "${ex}" || "${path}" == "${ex}/"* ]]; then
            return 0
        fi
    done
    return 1
}

# 生成完整相对路径清单（白名单文件 + 白名单目录递归），已过滤精确排除
collect_files() {
    local f d rel
    for f in "${WHITELIST_FILES[@]}"; do
        is_excluded "${f}" && continue
        printf '%s\n' "${f}"
    done
    for d in "${WHITELIST_DIRS[@]}"; do
        while IFS= read -r abs; do
            rel="${abs#"${REPO_ROOT}/"}"
            is_excluded "${rel}" && continue
            printf '%s\n' "${rel}"
        done < <(find "${REPO_ROOT}/${d}" -type f | LC_ALL=C sort)
    done
}

# ----- 概览 -----------------------------------------------------------------
log "仓库根目录 : ${REPO_ROOT}"
log "对外版本   : ${VERSION}"
log "导出目录   : ${OUT_DIR}"
log "执行模式   : $(${DO_COMMIT} && echo '导出+自检+单提交（--commit）' || echo 'dry-run（仅预览清单）')"

# 收集清单到数组（兼容 bash 3.2，不用 mapfile）
EXPORT_LIST=()
while IFS= read -r rel; do
    [[ -n "${rel}" ]] && EXPORT_LIST+=("${rel}")
done < <(collect_files)
[[ ${#EXPORT_LIST[@]} -gt 0 ]] || die 2 "白名单收集结果为空，请检查白名单配置"

log "将导出 ${#EXPORT_LIST[@]} 个文件（白名单驱动，默认排除其余一切）："
for rel in "${EXPORT_LIST[@]}"; do
    printf '  + %s\n' "${rel}"
done

# ----- dry-run：到此为止，不碰任何文件 / git ------------------------------
if ! ${DO_COMMIT}; then
    log "dry-run 完成：未生成导出产物、未修改 git。如需实际导出并生成单提交，追加 --commit。"
    log "请人工核对以上清单不含任何排除项（ci/、sdk_publish.gradle、docs/、app/、secring.gpg、gradle.properties、AGENT(S).md 等）。"
    exit 0
fi

# ===========================================================================
# 以下为 --commit 实际导出流程
# ===========================================================================

# ----- 1. 准备隔离导出目录（清空重建）-------------------------------------
log "步骤 1/5：准备隔离导出目录 ${OUT_DIR} ..."
rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

# ----- 2. 按清单拷贝白名单内容 ---------------------------------------------
log "步骤 2/5：拷贝白名单内容 ..."
for rel in "${EXPORT_LIST[@]}"; do
    dest="${OUT_DIR}/${rel}"
    mkdir -p "$(dirname "${dest}")"
    cp -p "${REPO_ROOT}/${rel}" "${dest}"
done

# ----- 3. 导出副本清理（只改产物，不碰内部分支原始文件）--------------------
log "步骤 3/5：清理导出副本（移除 sdk_publish.gradle apply 行、include(:app)）..."

SDK_BUILD_COPY="${OUT_DIR}/lib_taplink_sdk/build.gradle.kts"
SETTINGS_COPY="${OUT_DIR}/settings.gradle.kts"

# 移除 lib_taplink_sdk/build.gradle.kts 中引用 sdk_publish.gradle 的 apply 行及其上方注释
# （整行删除，公开版不含发布脚本）
if [[ -f "${SDK_BUILD_COPY}" ]]; then
    tmp="$(mktemp)"
    grep -v 'apply(from = "sdk_publish.gradle")' "${SDK_BUILD_COPY}" \
        | grep -v '// Apply publish script for Maven Central deployment' > "${tmp}"
    mv "${tmp}" "${SDK_BUILD_COPY}"
fi

# 从 settings.gradle.kts 删除 include(":app")（公开版只含两个 lib 模块）
if [[ -f "${SETTINGS_COPY}" ]]; then
    tmp="$(mktemp)"
    grep -v 'include(":app")' "${SETTINGS_COPY}" > "${tmp}"
    mv "${tmp}" "${SETTINGS_COPY}"
fi

# ----- 4. 泄漏自检（命中任一即失败、退出码 3，不产出 commit）--------------
log "步骤 4/5：泄漏自检（扫描凭证明文 / 敏感文件名 / 内部残留）..."

LEAK_FOUND=false

# 4a. 敏感文件名残留（整个产物目录按文件名匹配）
for name in "${LEAK_FILENAMES[@]}"; do
    if find "${OUT_DIR}" -type f -name "${name}" | grep -q .; then
        err "泄漏自检失败：产物中出现敏感文件 '${name}'"
        LEAK_FOUND=true
    fi
done

# 4b. 精确排除目录残留（产物中不应存在这些目录）
for ex in "${EXCLUDE_PATHS[@]}"; do
    # 只检查目录型排除项（文件名型已由 4a/4c 覆盖）
    if [[ -d "${OUT_DIR}/${ex}" ]]; then
        err "泄漏自检失败：产物中出现被排除目录 '${ex}'"
        LEAK_FOUND=true
    fi
done

# 4c. 凭证明文 / 签名键关键词（扫描文本内容，排除二进制）
for kw in "${LEAK_KEYWORDS[@]}"; do
    if grep -rIl -F -- "${kw}" "${OUT_DIR}" >/dev/null 2>&1; then
        err "泄漏自检失败：产物中出现敏感关键词 '${kw}'"
        LEAK_FOUND=true
    fi
done

# 4d. 清理未生效残留
for res in "${LEAK_RESIDUE[@]}"; do
    if grep -rIl -F -- "${res}" "${OUT_DIR}" >/dev/null 2>&1; then
        err "泄漏自检失败：清理未生效，产物中仍存在 '${res}'"
        LEAK_FOUND=true
    fi
done

if ${LEAK_FOUND}; then
    log "已中止：泄漏自检未通过，不生成任何公开 commit。请修正白名单/清理逻辑后重试。"
    # 删除不可信产物，避免误推
    rm -rf "${OUT_DIR}"
    exit 3
fi
log "泄漏自检通过。"

# ----- 5. 生成孤儿分支单提交（压缩为一个英文 Conventional Commit）----------
# 用临时 git worktree + 孤儿分支，仅加入导出产物，一次 commit 成单提交。
# 绝不 push、绝不打 tag。
log "步骤 5/5：在隔离 worktree 生成孤儿分支单提交 ..."

STAGING_BRANCH="public-export-staging"
TMP_WORKTREE="${REPO_ROOT}/build/public-export-worktree"
COMMIT_MSG="feat: public SDK source release v${VERSION}"

# 清理可能残留的上次 worktree / 分支
git -C "${REPO_ROOT}" worktree remove --force "${TMP_WORKTREE}" >/dev/null 2>&1 || true
rm -rf "${TMP_WORKTREE}"
git -C "${REPO_ROOT}" branch -D "${STAGING_BRANCH}" >/dev/null 2>&1 || true

# 创建一个脱离 HEAD 的临时 worktree，在其中建孤儿分支
git -C "${REPO_ROOT}" worktree add --detach "${TMP_WORKTREE}" >/dev/null \
    || die 2 "创建临时 worktree 失败：${TMP_WORKTREE}"

(
    cd "${TMP_WORKTREE}"
    git checkout --orphan "${STAGING_BRANCH}" >/dev/null 2>&1
    git rm -rf --quiet . >/dev/null 2>&1 || true
    # 拷贝导出产物进 worktree（保留目录结构）
    cp -a "${OUT_DIR}/." .
    git add -A
    git commit -q -m "${COMMIT_MSG}"
)

STAGING_SHA="$(git -C "${TMP_WORKTREE}" rev-parse HEAD)"

log "完成：公开导出单提交已生成。"
log "  孤儿分支   : ${STAGING_BRANCH}"
log "  提交       : ${STAGING_SHA}"
log "  提交信息   : ${COMMIT_MSG}"
log "  worktree   : ${TMP_WORKTREE}"
log "  导出目录   : ${OUT_DIR}"
log ""
log "后续维护者人工步骤（脚本不代执行，严格遵守 AGENTS.md §8/§10）："
log "  1. review 孤儿分支 ${STAGING_BRANCH} 的单提交内容（diff / 文件清单）。"
log "  2. 确认无误后手动推送到 GitHub 公开 main："
log "       git push GitHub ${STAGING_BRANCH}:main"
log "  3. 仅在 GitHub main 的该公开提交上手动打公开 tag v${VERSION}（只推 GitHub，不用 git push --tags）。"
log "  4. 清理临时产物（review 结束后）："
log "       git worktree remove --force ${TMP_WORKTREE} && git branch -D ${STAGING_BRANCH}"
exit 0
