# 项目文档管理规则

## 概述

本项目已建立了严格的文档管理规则，确保所有 Markdown 文件都在正确的位置，便于查找和维护。

## 核心规则

### ⭐ 所有 `.md` 文件必须在 `docs/` 目录下

### ⭐ 示例代码（Example 类）不应该在 `src/main/` 目录下

除了以下例外情况：
- ✅ `README.md` - 项目根目录
- ✅ `*/README.md` - 各模块根目录（仅 README）
- ✅ `.kiro/specs/*/` - Spec 相关文档
- ✅ `.kiro/*.md` - Kiro 配置相关文档

## 文件位置规范

### 正确的位置 ✅

```
docs/
├── api/                              # API 文档
│   └── TAPLINK_CLIENT_API.md
├── guides/                           # 使用指南
│   └── GETTING_STARTED.md
├── architecture/                     # 架构设计
│   └── SYSTEM_DESIGN.md
├── HEX_FRAME_PROTOCOL_USAGE.md      # 技术文档
├── TAPLINK_CLIENT_UPDATE_SUMMARY.md # 更新总结
└── 半集成云端模式接口文档.md          # 接口文档

README.md                             # 项目根目录
lib_taplink_sdk/README.md            # 模块 README
.kiro/specs/feature/requirements.md  # Spec 文档
.kiro/PROJECT_RULES.md               # 项目规则
```

### 错误的位置 ❌

```
❌ lib_taplink_sdk/半集成云端模式接口文档.md
❌ src/docs/SOME_DOC.md
❌ RANDOM_DOC.md (项目根目录，非 README)
❌ lib_taplink_communication/src/.../SomeDoc.md
```

## 示例代码位置规范

### 正确的位置 ✅

```
src/test/java/com/sunmi/tapro/taplink/communication/protocol/HexFrameProtocolExample.kt
src/androidTest/java/com/sunmi/tapro/taplink/sdk/ExampleInstrumentedTest.java
```

### 错误的位置 ❌

```
❌ src/main/java/com/sunmi/tapro/taplink/communication/protocol/HexFrameProtocolExample.kt
❌ src/main/java/com/sunmi/tapro/taplink/sdk/SampleUsage.kt
```

## 配置文件

项目已创建以下配置文件来强化规则：

### 1. Steering 规则
**位置**: `.kiro/steering/documentation-rules.md`

这是 Kiro AI 助手的规则文件，会自动检查和提示文档位置。

### 2. 项目规则
**位置**: `.kiro/PROJECT_RULES.md`

详细的项目规则和约定，包括文档管理、代码组织等。

### 3. 检查脚本
**位置**: `.kiro/scripts/check-md-files.sh`

自动检查脚本，可以扫描整个项目并报告位置不正确的文件。

**位置**: `.kiro/scripts/check-example-files.sh`

示例代码检查脚本，确保示例代码不在 main 目录下。

## 使用检查脚本

### 检查文档位置

```bash
# 在项目根目录执行
.kiro/scripts/check-md-files.sh
```

### 检查示例代码位置

```bash
# 在项目根目录执行
.kiro/scripts/check-example-files.sh
```

### 输出示例

```
🔍 检查 Markdown 文件位置...

✅ ./docs/HEX_FRAME_PROTOCOL_USAGE.md
✅ ./README.md
❌ ./lib_taplink_sdk/半集成云端模式接口文档.md

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 统计结果:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
总文件数: 77
✅ 位置正确: 76
❌ 位置错误: 1

⚠️  发现 1 个文件位置不正确！

建议的修正命令:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
mv "./lib_taplink_sdk/半集成云端模式接口文档.md" "docs/半集成云端模式接口文档.MD"
```

### 修正文件位置

脚本会自动生成修正命令，直接复制执行即可：

```bash
mv "./lib_taplink_sdk/半集成云端模式接口文档.md" "docs/半集成云端模式接口文档.md"
```

## 创建新文档

### 步骤

1. **确定文档类型**
   - API 文档 → `docs/api/`
   - 使用指南 → `docs/guides/`
   - 架构设计 → `docs/architecture/`
   - 其他技术文档 → `docs/`

2. **选择文件名**
   - 使用大写字母和下划线: `FEATURE_NAME.md`
   - 或使用小写字母和连字符: `feature-name.md`
   - 保持项目内命名风格一致

3. **创建文件**
   ```bash
   # 在 docs/ 目录下创建
   touch docs/MY_NEW_DOCUMENT.md
   ```

4. **编写内容**
   - 使用清晰的标题结构
   - 包含代码示例
   - 添加必要的链接

### 示例

```bash
# 创建 API 文档
touch docs/api/PAYMENT_API.md

# 创建使用指南
touch docs/guides/QUICK_START.md

# 创建技术文档
touch docs/BLUETOOTH_PROTOCOL.md
```

## 移动现有文档

如果需要移动现有文档：

1. **移动文件**
   ```bash
   mv old/path/DOCUMENT.md docs/DOCUMENT.md
   ```

2. **更新引用**
   - 搜索项目中所有引用该文件的地方
   - 更新链接路径
   - 确保所有引用都正确

3. **验证**
   ```bash
   # 运行检查脚本
   .kiro/scripts/check-md-files.sh
   ```

## Git 集成（可选）

可以添加 Git pre-commit hook 自动检查：

### 创建 hook

```bash
# 创建 .git/hooks/pre-commit
cat > .git/hooks/pre-commit << 'EOF'
#!/bin/bash

# 运行文档位置检查
.kiro/scripts/check-md-files.sh

# 如果检查失败，阻止提交
if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 提交被阻止：请修正文档位置后再提交"
    exit 1
fi
EOF

# 添加执行权限
chmod +x .git/hooks/pre-commit
```

### 效果

当尝试提交位置不正确的 `.md` 文件时，会自动阻止提交并提示修正。

## 常见问题

### Q: 为什么要统一文档位置？

A: 
- 便于查找和管理
- 避免文档散落在各处
- 提高项目可维护性
- 统一团队协作规范

### Q: 模块的 README.md 可以放在模块目录吗？

A: 可以。模块根目录的 `README.md` 是允许的，例如：
- `lib_taplink_sdk/README.md` ✅
- `lib_taplink_communication/README.md` ✅

### Q: Spec 相关的文档放在哪里？

A: Spec 相关文档应该放在 `.kiro/specs/` 目录下：
- `.kiro/specs/feature-name/requirements.md` ✅
- `.kiro/specs/feature-name/design.md` ✅
- `.kiro/specs/feature-name/tasks.md` ✅

### Q: 如何处理中文文档名？

A: 中文文档名是允许的，例如：
- `docs/半集成云端模式接口文档.md` ✅

### Q: 检查脚本报错怎么办？

A: 
1. 查看脚本输出的错误文件列表
2. 使用脚本提供的修正命令移动文件
3. 更新所有引用该文件的链接
4. 重新运行检查脚本验证

## 维护

### 定期检查

建议定期运行检查脚本：

```bash
# 每周或每次大的文档更新后
.kiro/scripts/check-md-files.sh
```

### 更新规则

如需修改规则：

1. 更新 `.kiro/steering/documentation-rules.md`
2. 更新 `.kiro/PROJECT_RULES.md`
3. 更新本文档
4. 通知团队成员

## 总结

通过遵循这些规则，我们可以：

- ✅ 保持项目文档整洁有序
- ✅ 快速找到需要的文档
- ✅ 提高团队协作效率
- ✅ 降低维护成本
- ✅ 统一开发规范

---

**最后更新**: 2025-01-06  
**维护者**: TaPro Team  
**相关文档**: 
- [项目规则](.kiro/PROJECT_RULES.md)
- [Steering 规则](.kiro/steering/documentation-rules.md)
