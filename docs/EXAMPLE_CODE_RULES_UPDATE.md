# 示例代码位置规则更新

## 概述

根据用户要求，更新了项目规则，确保示例代码（Example 类）不在 `src/main/` 目录下生成，而是在 `src/test/` 或 `src/androidTest/` 目录下。

## 更新内容

### 1. 移动现有示例文件

**移动的文件**:
```
lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/protocol/HexFrameProtocolExample.kt
→
lib_taplink_communication/src/test/java/com/sunmi/tapro/taplink/communication/protocol/HexFrameProtocolExample.kt
```

### 2. 更新规则文档

**更新的文件**:
- `.kiro/steering/documentation-rules.md` - 添加示例代码位置规则
- `.kiro/PROJECT_RULES.md` - 添加代码组织规则
- `docs/PROJECT_DOCUMENTATION_RULES.md` - 添加示例代码规范说明

### 3. 创建检查脚本

**新增文件**: `.kiro/scripts/check-example-files.sh`

**功能**:
- ✅ 扫描所有包含 "Example" 的 `.kt` 和 `.java` 文件
- ✅ 检查是否在 `src/main/` 目录下
- ✅ 生成修正命令
- ✅ 统计报告

**使用方法**:
```bash
.kiro/scripts/check-example-files.sh
```

### 4. 更新协议包 README

**更新文件**: `lib_taplink_communication/src/main/java/com/sunmi/tapro/taplink/communication/protocol/README.md`

修正了示例文件的路径引用。

## 新规则

### ⭐ 示例代码位置规则

**示例代码（Example 类）不应该在 `src/main/` 目录下创建**

#### 正确位置 ✅

```
src/test/java/.../ExampleTest.kt
src/test/java/.../ProtocolExample.kt
src/androidTest/java/.../ExampleInstrumentedTest.kt
src/androidTest/java/.../IntegrationExample.kt
```

#### 错误位置 ❌

```
src/main/java/.../Example.kt
src/main/java/.../SampleCode.kt
```

### 原因

1. **避免打包**: 示例代码不应该被打包到最终的 AAR 中
2. **清晰分离**: 将示例代码与生产代码分离
3. **测试性质**: 示例代码本质上是测试和演示代码

## 验证结果

### 运行检查脚本

```bash
$ .kiro/scripts/check-example-files.sh
🔍 检查示例代码文件位置...

✅ ./lib_taplink_sdk/src/androidTest/java/com/sunmi/tapro/taplink/sdk/ExampleInstrumentedTest.java
✅ ./app/src/androidTest/java/com/sunmi/tapro/taplink/sdk/demo/ExampleInstrumentedTest.kt
✅ ./app/src/test/java/com/sunmi/tapro/taplink/sdk/demo/ExampleUnitTest.kt
✅ ./lib_taplink_communication/src/androidTest/java/com/sunmi/tapro/taplink/communication/ExampleInstrumentedTest.kt
✅ ./lib_taplink_communication/src/test/java/com/sunmi/tapro/taplink/communication/ExampleUnitTest.kt
✅ ./lib_taplink_communication/src/test/java/com/sunmi/tapro/taplink/communication/protocol/HexFrameProtocolExample.kt

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📊 统计结果:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
总示例文件数: 6
✅ 位置正确: 6
❌ 位置错误: 0

🎉 所有示例文件位置都正确！
```

### 编译检查

所有移动后的文件都通过了编译检查，没有语法错误。

## 使用指南

### 创建新的示例代码

```bash
# 在测试目录下创建示例
touch src/test/java/com/example/MyExample.kt

# 或在 Android 测试目录下创建
touch src/androidTest/java/com/example/MyIntegrationExample.kt
```

### 检查现有示例代码

```bash
# 运行检查脚本
.kiro/scripts/check-example-files.sh
```

### 移动错误位置的示例代码

如果检查脚本发现错误位置的文件，会自动生成修正命令：

```bash
mkdir -p "target/directory"
mv "wrong/path/Example.kt" "correct/path/Example.kt"
```

## 集成到开发流程

### 1. 代码审查

在代码审查时，检查：
- 是否有示例代码在 `src/main/` 目录下
- 运行检查脚本验证

### 2. CI/CD 集成（可选）

可以将检查脚本集成到 CI 流程中：

```yaml
# 在 CI 配置中添加
- name: Check Example Files Location
  run: .kiro/scripts/check-example-files.sh
```

### 3. Git Hooks（可选）

可以添加 pre-commit hook：

```bash
#!/bin/bash
# 检查示例文件位置
.kiro/scripts/check-example-files.sh
if [ $? -ne 0 ]; then
    echo "❌ 提交被阻止：请修正示例文件位置"
    exit 1
fi
```

## 文件清单

### 新增文件 (2个)

1. `.kiro/scripts/check-example-files.sh` - 示例代码检查脚本
2. `docs/EXAMPLE_CODE_RULES_UPDATE.md` - 本文档

### 移动文件 (1个)

1. `HexFrameProtocolExample.kt` - 从 `src/main/` 移动到 `src/test/`

### 更新文件 (4个)

1. `.kiro/steering/documentation-rules.md` - 添加示例代码规则
2. `.kiro/PROJECT_RULES.md` - 添加代码组织规则
3. `docs/PROJECT_DOCUMENTATION_RULES.md` - 添加示例代码规范
4. `lib_taplink_communication/.../protocol/README.md` - 更新路径引用

## 总结

成功实现了示例代码位置规则：

- ✅ **规则明确**: 示例代码不在 main 目录下
- ✅ **自动检查**: Shell 脚本 + Kiro AI 双重保障
- ✅ **文档完善**: 详细的规则说明和使用指南
- ✅ **验证通过**: 所有现有文件位置正确
- ✅ **易于维护**: 集中管理，自动化检查

这个规则确保了：
1. 示例代码不会被打包到最终的 AAR 中
2. 生产代码和示例代码清晰分离
3. 项目结构更加规范和专业

---

**创建日期**: 2025-01-06  
**创建者**: Tapro Team  
**相关文档**:
- [项目规则](.kiro/PROJECT_RULES.md)
- [文档管理规则](PROJECT_DOCUMENTATION_RULES.md)
- [Steering 规则](.kiro/steering/documentation-rules.md)