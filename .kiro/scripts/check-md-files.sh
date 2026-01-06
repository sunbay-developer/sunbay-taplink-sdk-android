#!/bin/bash

# 检查项目中 .md 文件位置是否符合规则
# 规则: 所有 .md 文件应该在 docs/ 目录下，除了 README.md 和 .kiro/specs/ 下的文件

echo "🔍 检查 Markdown 文件位置..."
echo ""

# 查找所有 .md 文件
ALL_MD_FILES=$(find . -name "*.md" -type f | grep -v "node_modules" | grep -v ".gradle" | grep -v "build/")

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
    
    # 检查是否符合规则
    if [[ "$file" == "./docs/"* ]] || \
       [[ "$file" == "./README.md" ]] || \
       [[ "$file" == "./"*"/README.md" ]] || \
       [[ "$file" == "./.kiro/specs/"* ]] || \
       [[ "$file" == "./.kiro/"*".md" ]]; then
        CORRECT_COUNT=$((CORRECT_COUNT + 1))
        echo "✅ $file"
    else
        WRONG_COUNT=$((WRONG_COUNT + 1))
        WRONG_FILES+=("$file")
        echo "❌ $file"
    fi
done <<< "$ALL_MD_FILES"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 统计结果:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "总文件数: $TOTAL_COUNT"
echo "✅ 位置正确: $CORRECT_COUNT"
echo "❌ 位置错误: $WRONG_COUNT"
echo ""

if [ $WRONG_COUNT -gt 0 ]; then
    echo "⚠️  发现 $WRONG_COUNT 个文件位置不正确！"
    echo ""
    echo "建议的修正命令:"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    for file in "${WRONG_FILES[@]}"; do
        # 提取文件名
        filename=$(basename "$file")
        # 转换为大写并替换特殊字符
        suggested_name=$(echo "$filename" | tr '[:lower:]' '[:upper:]' | sed 's/-/_/g')
        
        echo "mv \"$file\" \"docs/$suggested_name\""
    done
    
    echo ""
    echo "💡 提示:"
    echo "1. 检查上述文件是否应该移动到 docs/ 目录"
    echo "2. 如果是模块的 README.md，可以保留在模块根目录"
    echo "3. 如果是 spec 相关文档，应该在 .kiro/specs/ 目录"
    echo "4. 移动文件后，记得更新所有引用该文件的链接"
    echo ""
    
    exit 1
else
    echo "🎉 所有 Markdown 文件位置都正确！"
    echo ""
    exit 0
fi
