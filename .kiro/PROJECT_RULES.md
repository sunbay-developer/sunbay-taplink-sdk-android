# 项目规则和约定

## 文件组织规则

### 1. Markdown 文档位置规则 ⭐

**所有 `.md` 文件必须在 `docs/` 目录下创建**

#### 允许的位置
- ✅ `docs/` - 所有技术文档、指南、总结
- ✅ `docs/api/` - API 文档
- ✅ `docs/guides/` - 使用指南
- ✅ `docs/architecture/` - 架构文档
- ✅ `README.md` - 项目根目录（仅此文件）
- ✅ `*/README.md` - 各模块根目录（仅 README）
- ✅ `.kiro/specs/*/` - Spec 相关文档

#### 禁止的位置
- ❌ 源代码目录中的 `.md` 文件（除 README.md）
- ❌ 项目根目录的其他 `.md` 文件（除 README.md）
- ❌ 随意放置的文档文件

#### 示例

```
✅ 正确:
docs/HEX_FRAME_PROTOCOL_USAGE.md
docs/api/TAPLINK_CLIENT_API.md
docs/guides/GETTING_STARTED.md
README.md
lib_taplink_sdk/README.md

❌ 错误:
lib_taplink_communication/src/.../SomeDoc.md
src/DOCUMENT.md
RANDOM_DOC.md (根目录)
```

### 2. 代码组织规则

#### lib_taplink_sdk 包结构规则 ⭐

**重要**: 必须遵循标准的包结构和职责划分

详细的包结构规则请参考: [SDK 包结构规则](../docs/SDK_PACKAGE_STRUCTURE_RULES.md)

**标准包结构**:
```
com.sunmi.tapro.taplink.sdk/
├── adapter/           # 业务适配层
├── api/               # 公共 API 接口
├── callback/          # 回调接口
├── config/            # 配置类
├── enums/             # 枚举类
├── error/             # 错误类
├── impl/              # 实现类
├── manager/           # 管理器类 ⭐
├── model/             # 数据模型
├── persistence/       # 持久化
├── protocol/          # 协议处理 ⭐
└── util/              # 工具类
```

**关键规则**:
- ✅ Manager 类必须放在 `manager/` 包中
- ✅ Protocol 相关类必须放在 `protocol/` 包中
- ✅ 适配器类必须放在 `adapter/` 包中
- ❌ 不允许在错误的包中放置类文件

#### Kotlin/Java 源代码
- 源代码: `*/src/main/java/`
- 测试代码: `*/src/test/java/`
- Android 测试: `*/src/androidTest/java/`

#### 示例代码位置 ⭐
**重要**: 示例代码（Example 类）不应该在 main 目录下创建

- ✅ 正确: `*/src/test/java/.../ExampleTest.kt`
- ✅ 正确: `*/src/androidTest/java/.../IntegrationExample.kt`
- ❌ 错误: `*/src/main/java/.../Example.kt`

#### 资源文件
- Android 资源: `*/src/main/res/`
- Assets: `*/src/main/assets/`

#### 配置文件
- Gradle 配置: `*.gradle.kts`, `gradle/`
- Kiro 配置: `.kiro/`
- Git 配置: `.gitignore`

### 3. 命名规范

#### 文档文件命名
- 使用大写字母和下划线: `FEATURE_NAME.md`
- 或使用小写字母和连字符: `feature-name.md`
- 保持项目内命名风格一致

#### 代码文件命名
- Kotlin/Java 类: `PascalCase.kt`
- 包名: `lowercase.with.dots`

### 4. 文档结构建议

```
docs/
├── api/                    # API 文档
├── guides/                 # 使用指南
├── architecture/           # 架构设计
├── changelog/              # 变更日志
└── *.md                    # 其他技术文档
```

## 开发流程规则

### 1. 创建新功能
1. 在 `.kiro/specs/` 创建 spec
2. 编写 requirements.md
3. 编写 design.md
4. 编写 tasks.md
5. 实现功能
6. 在 `docs/` 创建相关文档

### 2. 文档更新
1. 技术文档放在 `docs/`
2. 使用清晰的标题和结构
3. 包含代码示例
4. 更新相关的 README.md

### 3. 代码审查
1. 检查文档位置是否正确
2. 确保 `.md` 文件在 `docs/` 目录
3. 验证文档内容完整性
4. 检查类是否在正确的包中 ⭐
5. 验证包结构是否符合规范 ⭐

## 工具和自动化

### Kiro Steering 规则
- 文档规则: `.kiro/steering/documentation-rules.md`
- 自动检查文件位置
- 提供位置建议

### Git Hooks（可选）
可以添加 pre-commit hook 检查 `.md` 文件位置：

```bash
#!/bin/bash
# 检查是否有 .md 文件在错误位置
WRONG_MD_FILES=$(git diff --cached --name-only | grep '\.md$' | grep -v '^docs/' | grep -v '^README\.md$' | grep -v '/README\.md$' | grep -v '^\.kiro/specs/')

if [ -n "$WRONG_MD_FILES" ]; then
    echo "错误: 以下 .md 文件位置不正确，应该放在 docs/ 目录下:"
    echo "$WRONG_MD_FILES"
    exit 1
fi
```

## 违规处理

如果发现违反规则的文件：

1. **立即修正**: 将文件移动到正确位置
2. **更新引用**: 修改所有引用该文件的链接
3. **提交变更**: 提交位置修正的 commit

## 规则更新

本规则文件由项目维护者维护，如需修改请：

1. 提出修改建议
2. 团队讨论
3. 更新此文件
4. 通知所有开发者

---

**最后更新**: 2025-01-06
**维护者**: Tapro Team
