#!/usr/bin/env bash
#
# TapLink SDK — AAR 打包与 Maven 推送脚本（供运维/维护者在服务器上手动执行）
# ---------------------------------------------------------------------------
# 本脚本是 TAPLINK_JENKINS_RELEASE_AUTOMATION_DESIGN.md 落地的第一步：把原先隐藏在
# sdk_publish.gradle 的 doLast 里的网络副作用变为显式、可判失败的步骤，并保证凭证
# 只从环境变量注入，脚本与仓库中均不含任何明文凭证。
#
# 行为（方案 B）：
#   1. 用 Gradle 只做本地 assemble + copy AAR（不触发远端）。
#   2. 调用 Gradle publish task 把制品推送到对应 staging / snapshot 仓库。
#   3. 仅 release 通道：显式调用 Central manual upload 端点并校验 HTTP 返回码。
#      snapshot 通道不触发 release 的 manual upload。
#
# 凭证（必须通过环境变量注入，脚本内无任何凭证）：
#   SUNBAY_MAVEN_USERNAME   Central / staging 仓库用户名
#   SUNBAY_MAVEN_PASSWORD   Central / staging 仓库密码（token）
#
# 用法：
#   ci/release/publish_sdk.sh release           # 打包并发布正式版
#   ci/release/publish_sdk.sh snapshot          # 打包并发布 SNAPSHOT
#   ci/release/publish_sdk.sh release --dry-run # 只打包+组装 task 图，不推送、不联网
#   ci/release/publish_sdk.sh release --yes     # 跳过交互确认（CI 场景）
#
# 退出码：
#   0  成功
#   2  输入 / 凭证 / 前置校验失败（未触发任何构建或网络）
#   3  本地构建 / 推送失败
#   4  远端 manual upload 校验失败
# ---------------------------------------------------------------------------

set -euo pipefail

# ----- 路径与常量 -----------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
GRADLEW="${REPO_ROOT}/gradlew"
SDK_MODULE=":lib_taplink_sdk"

# Central manual upload 端点（仅 release 通道使用）。与原 sdk_publish.gradle 中
# doLast 的目标一致，命名空间 com.sunmi。
MANUAL_UPLOAD_URL="https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.sunmi"

# ----- 日志工具 -------------------------------------------------------------
log()  { printf '[publish_sdk] %s\n' "$*"; }
err()  { printf '[publish_sdk][ERROR] %s\n' "$*" >&2; }

die() {
    # $1 = 退出码, 其余 = 信息
    local code="$1"; shift
    err "$*"
    exit "${code}"
}

# ----- 参数解析 -------------------------------------------------------------
CHANNEL=""
DRY_RUN=false
ASSUME_YES=false

usage() {
    sed -n '2,40p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

for arg in "$@"; do
    case "${arg}" in
        release|snapshot)
            [[ -n "${CHANNEL}" ]] && die 2 "通道只能指定一个，已有：${CHANNEL}"
            CHANNEL="${arg}"
            ;;
        --dry-run) DRY_RUN=true ;;
        --yes|-y)  ASSUME_YES=true ;;
        -h|--help) usage; exit 0 ;;
        *) die 2 "未知参数：${arg}（使用 -h 查看用法）" ;;
    esac
done

[[ -z "${CHANNEL}" ]] && { usage; die 2 "必须指定通道：release 或 snapshot"; }

# ----- 通道相关配置 ---------------------------------------------------------
# 有效的 publish task 组合（非匹配的 publication×repository 组合在 sdk_publish.gradle
# 中已被 task.enabled=false 禁用）：
#   release  → publishReleasePublicationToReleaseUrlRepository
#   snapshot → publishDebugPublicationToSnapshotUrlRepository（debug publication 带 -SNAPSHOT）
if [[ "${CHANNEL}" == "release" ]]; then
    ASSEMBLE_TASK="${SDK_MODULE}:copyReleaseAar"
    PUBLISH_TASK="${SDK_MODULE}:publishReleasePublicationToReleaseUrlRepository"
    DO_MANUAL_UPLOAD=true
else
    ASSEMBLE_TASK="${SDK_MODULE}:copyDebugAar"
    PUBLISH_TASK="${SDK_MODULE}:publishDebugPublicationToSnapshotUrlRepository"
    DO_MANUAL_UPLOAD=false
fi

# ----- 前置校验（失败不触发任何构建 / 网络）--------------------------------
[[ -x "${GRADLEW}" ]] || die 2 "找不到可执行的 gradlew：${GRADLEW}"

for tool in curl; do
    command -v "${tool}" >/dev/null 2>&1 || die 2 "缺少必需命令：${tool}"
done

# 凭证校验：dry-run 时不要求凭证（因为不推送、不联网）。
if ! ${DRY_RUN}; then
    [[ -n "${SUNBAY_MAVEN_USERNAME:-}" ]] || die 2 "未设置 SUNBAY_MAVEN_USERNAME 环境变量"
    [[ -n "${SUNBAY_MAVEN_PASSWORD:-}" ]] || die 2 "未设置 SUNBAY_MAVEN_PASSWORD 环境变量"
fi

# ----- 概览与确认 -----------------------------------------------------------
log "仓库根目录 : ${REPO_ROOT}"
log "通道       : ${CHANNEL}"
log "组装 task  : ${ASSEMBLE_TASK}"
log "推送 task  : ${PUBLISH_TASK}"
log "manual upload : ${DO_MANUAL_UPLOAD}"
log "dry-run    : ${DRY_RUN}"

if ! ${DRY_RUN} && ! ${ASSUME_YES}; then
    printf '[publish_sdk] 确认向 %s 通道发布？输入 yes 继续：' "${CHANNEL}"
    read -r reply
    [[ "${reply}" == "yes" ]] || die 2 "用户未确认，已取消。"
fi

# ----- Gradle 参数 ----------------------------------------------------------
# --no-daemon + --console=plain：CI 友好、输出稳定。
GRADLE_ARGS=(--no-daemon --console=plain)

# ----- 步骤 1：本地打包（assemble + copy AAR，不触发远端）------------------
log "步骤 1/3：本地打包 AAR ..."
"${GRADLEW}" "${GRADLE_ARGS[@]}" "${ASSEMBLE_TASK}" \
    || die 3 "本地打包失败（${ASSEMBLE_TASK}）"

# ----- dry-run：到此为止 ----------------------------------------------------
if ${DRY_RUN}; then
    log "dry-run 完成：仅本地打包，未推送、未联网。"
    exit 0
fi

# ----- 步骤 2：推送到 Maven 仓库 --------------------------------------------
# 凭证通过环境变量传给 Gradle（sdk_publish.gradle 会读取同名环境变量），
# 不在命令行以 -P 明文形式出现，避免进入进程参数 / 构建日志。
log "步骤 2/3：推送制品到 Maven 仓库 ..."
SUNBAY_MAVEN_USERNAME="${SUNBAY_MAVEN_USERNAME}" \
SUNBAY_MAVEN_PASSWORD="${SUNBAY_MAVEN_PASSWORD}" \
    "${GRADLEW}" "${GRADLE_ARGS[@]}" "${PUBLISH_TASK}" \
    || die 3 "推送失败（${PUBLISH_TASK}）"

# ----- 步骤 3：仅 release — 显式触发 Central manual upload 并校验返回码 -----
if ${DO_MANUAL_UPLOAD}; then
    log "步骤 3/3：触发 Central manual upload 并校验返回码 ..."

    # 用 --user 让 curl 自己做 Basic Auth 编码，凭证不出现在 URL / 命令可见参数里。
    # -s 静默、-S 出错仍显示、-o 丢弃响应体、-w 仅输出 HTTP 状态码。
    http_code="$(
        curl -sS -o /dev/null -w '%{http_code}' \
            -X POST \
            -H 'Content-Type: application/json' \
            --user "${SUNBAY_MAVEN_USERNAME}:${SUNBAY_MAVEN_PASSWORD}" \
            "${MANUAL_UPLOAD_URL}" \
        || true
    )"

    log "manual upload HTTP 返回码：${http_code}"

    case "${http_code}" in
        2??) log "manual upload 成功。" ;;
        "")  die 4 "manual upload 无返回码（网络异常 / 连接失败），结果未知，请人工核对后再决定是否重试。" ;;
        *)   die 4 "manual upload 返回非 2xx（${http_code}），请人工核对 Central 控制台状态，不要盲目重试。" ;;
    esac
else
    log "步骤 3/3：snapshot 通道，跳过 release manual upload。"
fi

log "完成：${CHANNEL} 通道发布流程结束。"
log "提示：上传成功不等于已对外发布。请到 Central 控制台确认部署状态，"
log "      并在确认实际版本号后再进行 Demo / Tapro / sunbay-open-docs 的下游同步。"
exit 0
