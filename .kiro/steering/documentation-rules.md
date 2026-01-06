---
inclusion: always
---

# 文档管理规则

## Markdown 文件位置规则

**重要规则**: 所有 `.md` 文件必须在 `docs/` 目录下创建和管理。

### 规则详情

1. **所有文档文件位置**
   - ✅ 正确: `docs/FEATURE_NAME.md`
   - ✅ 正确: `docs/subfolder/DOCUMENT.md`
   - ❌ 错误: `lib_taplink_communication/src/.../SomeDoc.md`
   - ❌ 错误: `项目根目录/DOCUMENT.md`

2. **例外情况**
   - `README.md` 可以在项目根目录
   - 模块级别的 `README.md` 可以在各模块根目录
   - Spec 相关的 `.md` 文件在 `.kiro/specs/` 目录下

3. **文档类型和位置**
   - 技术文档: `docs/`
   - API 文档: `docs/api/`
   - 架构文档: `docs/architecture/`
   - 使用指南: `docs/guides/`
   - 变更日志: `docs/changelog/`
   - 总结文档: `docs/`

### 执行规则

当需要创建 `.md` 文件时：

1. **检查文件类型**
   - 如果是 README.md 且在项目/模块根目录 → 允许
   - 如果是 spec 相关文档 → 放在 `.kiro/specs/` 下
   - 其他所有 `.md` 文件 → 必须放在 `docs/` 目录下

2. **创建文件前**
   - 确定文档类型和用途
   - 选择合适的子目录（如果需要）
   - 使用清晰的文件命名

3. **文件命名规范**
   - 使用大写字母和下划线: `FEATURE_NAME.md`
   - 或使用小写字母和连字符: `feature-name.md`
   - 保持命名一致性

### 示例

```
✅ 正确的文件位置:
docs/HEX_FRAME_PROTOCOL_EXTRACTION_SUMMARY.md
docs/api/TAPLINK_API_REFERENCE.md
docs/guides/GETTING_STARTED.md
docs/architecture/SYSTEM_DESIGN.md
README.md (项目根目录)
lib_taplink_sdk/README.md (模块根目录)
.kiro/specs/feature-name/requirements.md

❌ 错误的文件位置:
lib_taplink_communication/src/main/java/.../HexFrameProtocolUsage.md
src/docs/SOME_DOC.md
RANDOM_DOC.md (项目根目录，非README)
```

### 迁移现有文件

如果发现 `.md` 文件在错误的位置：

1. 将文件移动到 `docs/` 目录
2. 更新所有引用该文件的链接
3. 确保文件在正确的子目录中

### 自动检查

在创建或修改 `.md` 文件时，AI 助手会：
- 自动检查文件位置是否符合规则
- 如果位置不正确，提示正确的位置
- 建议合适的子目录结构

## 代码示例位置规则

**重要规则**: 示例代码（Example 类）不应该在 main 目录下创建。

### 示例代码位置

1. **单元测试示例**
   - ✅ 正确: `src/test/java/.../ExampleTest.kt`
   - ✅ 正确: `src/test/java/.../ProtocolExample.kt`

2. **Android 测试示例**
   - ✅ 正确: `src/androidTest/java/.../ExampleInstrumentedTest.kt`
   - ✅ 正确: `src/androidTest/java/.../IntegrationExample.kt`

3. **禁止位置**
   - ❌ 错误: `src/main/java/.../Example.kt`
   - ❌ 错误: `src/main/java/.../SampleCode.kt`

### 示例

```
✅ 正确的示例代码位置:
src/test/java/com/sunmi/tapro/taplink/communication/protocol/HexFrameProtocolExample.kt
src/androidTest/java/com/sunmi/tapro/taplink/sdk/TaplinkClientExample.kt

❌ 错误的示例代码位置:
src/main/java/com/sunmi/tapro/taplink/communication/protocol/HexFrameProtocolExample.kt
src/main/java/com/sunmi/tapro/taplink/sdk/SampleUsage.kt
```

## SDK 包结构规则

**重要规则**: lib_taplink_sdk 必须遵循标准的包结构和职责划分。

详细规则请参考: `docs/SDK_PACKAGE_STRUCTURE_RULES.md`

### 标准包结构

```
com.sunmi.tapro.taplink.sdk/
├── adapter/           # 业务适配层
├── api/               # 公共 API 接口
├── callback/          # 回调接口
├── config/            # 配置类
├── enums/             # 枚举类
├── error/             # 错误类
├── impl/              # 实现类
├── manager/           # 管理器类
├── model/             # 数据模型
├── persistence/       # 持久化
├── protocol/          # 协议处理
└── util/              # 工具类
```

### 关键规则

1. **Manager 类规则**
   - ✅ 所有 Manager 类必须放在 `manager/` 包中
   - ✅ 类名必须以 `Manager` 结尾
   - ❌ 不允许在其他包中放置 Manager 类

2. **Protocol 类规则**
   - ✅ 所有协议相关类必须放在 `protocol/` 包中
   - ✅ 包括检测器、解析器、构建器等
   - ❌ 不允许协议相关类分散在其他包中

3. **Adapter 类规则**
   - ✅ 所有适配器类必须放在 `adapter/` 包中
   - ✅ 类名必须以 `Adapter` 结尾
   - ❌ 不允许在其他包中放置适配器类

4. **命名规则**
   - 管理器: `XxxManager`
   - 适配器: `XxxAdapter`
   - 构建器: `XxxBuilder`
   - 解析器: `XxxResolver`
   - 检测器: `XxxDetector`

### 执行规则

当创建新类时：

1. **确定类的职责**
   - 管理功能 → `manager/` 包
   - 协议处理 → `protocol/` 包
   - 数据适配 → `adapter/` 包
   - 配置相关 → `config/` 包

2. **检查命名规范**
   - 使用正确的后缀命名
   - 避免模糊或通用的名称

3. **验证包位置**
   - 确保类在正确的包中
   - 检查是否符合职责划分

### 自动检查

AI 助手会：
- 检查新创建的类是否在正确的包中
- 验证类名是否符合命名规范
- 提示正确的包位置和命名建议
