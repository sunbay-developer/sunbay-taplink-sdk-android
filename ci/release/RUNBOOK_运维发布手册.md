# TapLink SDK 发布运维手册

配套脚本：`ci/release/publish_sdk.sh`（在仓库根目录执行）

---

## 1. 需要的环境变量

| 变量 | 值 | 说明 |
|---|---|---|
| `SUNBAY_MAVEN_USERNAME` | `PDUNa6` | release + snapshot 共用一套 |
| `SUNBAY_MAVEN_PASSWORD` | `ikAlguiWOhfrTQM6nkE9MzHeGFsV89pGC` | 同上 |

凭证不在仓库里，执行前必须注入（CI 用 secret）。签名材料（`secring.gpg` + `gradle.properties`）已在仓库内，无需准备。

## 2. 环境要求

JDK 17、Android SDK（compileSdk 35）、`local.properties` 配好 `sdk.dir`、curl、可访问 `ossrh-staging-api.central.sonatype.com` 与 `central.sonatype.com`。Gradle 用仓库自带 `./gradlew`。

## 3. 发布仓库（脚本按通道自动选，凭证相同）

| 通道 | publication | 版本 | 仓库 URL |
|---|---|---|---|
| release | release | `1.1.0.3` | `https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/` |
| snapshot | debug | `1.1.0.3-SNAPSHOT` | `https://central.sonatype.com/repository/maven-snapshots/` |

版本号在 `lib_taplink_sdk/build.gradle.kts` 的 `SdkVersion`，由维护者维护。

## 4. 发布命令（推荐，走脚本）

```bash
cd <path-to>/taplinksdk
export SUNBAY_MAVEN_USERNAME='PDUNa6'
export SUNBAY_MAVEN_PASSWORD='ikAlguiWOhfrTQM6nkE9MzHeGFsV89pGC'

./ci/release/publish_sdk.sh release --dry-run   # 只打包，不推送不联网
./ci/release/publish_sdk.sh release             # 发正式版（交互确认）
./ci/release/publish_sdk.sh release --yes       # 发正式版（CI 无人值守）
./ci/release/publish_sdk.sh snapshot            # 发 SNAPSHOT
```

退出码：`0` 成功 / `2` 输入或凭证问题（未构建未联网）/ `3` 构建或推送失败 / `4` 远端校验失败（先查 Central 控制台，别盲重试）。

release 通道推送后脚本会显式调一次 Central manual upload 并校验 HTTP；snapshot 不调。

## 5. 底层 Gradle 命令（脚本内部等价，手动排障时用）

```bash
# 仅打包 + 重命名 AAR（输出到 ../build/）
./gradlew --no-daemon --console=plain :lib_taplink_sdk:copyReleaseAar     # release
./gradlew --no-daemon --console=plain :lib_taplink_sdk:copyDebugAar       # snapshot

# 推送（需先 export 上面两个环境变量）
./gradlew --no-daemon --console=plain :lib_taplink_sdk:publishReleasePublicationToReleaseUrlRepository    # release
./gradlew --no-daemon --console=plain :lib_taplink_sdk:publishDebugPublicationToSnapshotUrlRepository     # snapshot

# release 推送后手动触发 Central manual upload（脚本已自动做这步）
curl -sS -o /dev/null -w '%{http_code}\n' -X POST \
  -H 'Content-Type: application/json' \
  --user "$SUNBAY_MAVEN_USERNAME:$SUNBAY_MAVEN_PASSWORD" \
  'https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.sunmi'
```

## 6. 注意

- 凭证只走环境变量，别写回脚本或提交仓库。
- 上传成功 ≠ 已发版，正式发布与版本确认由维护者在 Central 控制台完成。
- 正式版同版本号不可覆盖重发，发错只能发新版本。
