# TaplinkConfig setter request evidence

Document ID: REQ-CONFIG-001
Version: 1.0
Date: 2026-10-14 (Asia/Shanghai)

## S1. Runtime timeout request

User quote:

> 是否可以在sdk侧优化set了timeout后自动生效不需要用户手动initsdk

## S2. Ignored setter return compatibility

User quote:

> 需要兼容这个写法

Attached screenshot, relevant code transcribed:

```kotlin
val config = TaplinkConfig.create(appId, secretKey)
config.setLogEnabled(true)
    .setLogLevel(LogLevel.DEBUG)
TaplinkSDK.init(context, config)
```

The screenshot calls setters before initialization and discards their return value.
It does not define post-initialization logging updates, concurrency, copy semantics,
or the scope of setters beyond the illustrated logging methods and preceding timeout discussion.

## S3. Existing contract conflict

Current TaplinkConfig.setTimeout KDoc:

> Returns a new configuration instance; the original instance is unchanged.

Source: lib_taplink_sdk/src/main/java/com/sunmi/tapro/taplink/sdk/config/TaplinkConfig.kt.
The requested ignored-return behavior conflicts with this documented contract.
This evidence record is a conversation transcription, not a new approved requirement.

## S4. Final scope approval

User quote:

> 不需要实现初始化后`setTimeout()` 动态影响后续请求，兼容setter即可

This reply narrows the previously reviewed all-setter proposal to pre-init
configuration. Runtime updates are excluded. Approval is recorded from this
conversation, not inferred from the earlier question about runtime updates.

## S5. SDK-wide field setter scope

User quote:

> sdk中所有的set方法都需要支持这种调用并兼容链式调用

Scope clarification accepted by user:

> 仅字段 setter，保留运行时接口签名（推荐）

This authorizes same-instance field setters for configurations, requests, models
and builders. Runtime APIs such as setConnectionListener keep their existing signatures.
