#!/bin/bash

# 检查项目中示例代码位置是否符合规则
# 规则: 示例代码（Example 类）不应该在 src/main/ 目录下

echo "🔍 检查示例代码文件位置..."
echo ""

# 查找所有包含 Example 的 .kt 和 .java 文件
EXAMPLE_FILES=$(find . -name "*Example*.kt" -o -name "*Example*.java" | grep -v "node_modules" | grep -v ".gradle" | grep -v "build/")

# 统计
TOTAL_COUNT=0
CORRECT_COUNT=0
WRONG_COUNT=0

# 存储错误文件
WRONG_FILES=()

while IFS= read -r file; do
    if [ -z "$file" ]; then
        continue
    fi
    
    TOTAL_COUNT=$((TOTAL_COUNT + 1))
    
    # 检查是否在 src/main/ 目录下
    if [[ "$file" == *"/src/main/"* ]]; then
        WRONG_COUNT=$((WRONG_COUNT + 1))
        WRONG_FILES+=("$file")
        echo "❌ $file (在 main 目录下)"
    else
        CORRECT_COUNT=$((CORRECT_COUNT + 1))
        echo "✅ $file"
    fi
done <<< "$EXAMPLE_FILES"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 统计结果:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "总示例文件数: $TOTAL_COUNT"
echo "✅ 位置正确: $CORRECT_COUNT"
echo "❌ 位置错误: $WRONG_COUNT"
echo ""

if [ $WRONG_COUNT -gt 0 ]; then
    echo "⚠️  发现 $WRONG_COUNT 个示例文件位置不正确！"
    echo ""
    echo "建议的修正命令:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    for file in "${WRONG_FILES[@]}"; do
        # 将 src/main/ 替换为 src/test/
        suggested_path=$(echo "$file" | sed 's|/src/main/|/src/test/|')
        
        # 创建目标目录
        target_dir=$(dirname "$suggested_path")
        echo "mkdir -p \"$target_dir\""
        echo "mv \"$file\" \"$suggested_path\""
        echo ""
    done
    
    echo "💡 提示:"
    echo "1. 示例代码应该放在 src/test/ 或 src/androidTest/ 目录"
    echo "2. 这样可以避免示例代码被打包到最终的 AAR 中"
    echo "3. 移动文件后，记得更新相关文档中的路径引用"
    echo ""
    
    exit 1
else
    echo "🎉 所有示例文件位置都正确！"
    echo ""
    exit 0
fi