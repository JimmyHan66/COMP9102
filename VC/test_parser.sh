#!/bin/bash

# 创建存放测试结果的目录
mkdir -p results comparision_results

# 设置比较输出文件
COMPARISON_OUTPUT="comparision_results/all_tests_comparison.txt"

# 清空比较输出文件
> "$COMPARISON_OUTPUT"

# 获取 Parser 目录下的所有 .vc 测试文件
TEST_FILES=$(find Parser -name "t*.vc")

# 遍历所有 .vc 测试文件
for TEST_FILE in $TEST_FILES; do
    TEST_NAME=$(basename "$TEST_FILE" .vc)

    echo "========== 测试文件: $TEST_NAME ==========" | tee -a "$COMPARISON_OUTPUT"
    echo "正在测试 $TEST_NAME..."

    # 运行 VC 编译器，生成 AST 结果 (.vcu)
    OUTPUT_FILE="Parser/${TEST_NAME}.vcu"
    java VC.vc "$TEST_FILE" > /dev/null 2>&1

    if [ ! -f "$OUTPUT_FILE" ]; then
        echo "错误: 未生成 $OUTPUT_FILE" | tee -a "$COMPARISON_OUTPUT"
        continue
    fi

    # 运行 -u 选项进行 AST 解析检查
    UNPARSED_FILE="Parser/${TEST_NAME}.vcuu"
    java VC.vc -u "$UNPARSED_FILE" "$OUTPUT_FILE" > /dev/null 2>&1

    if [ ! -f "$UNPARSED_FILE" ]; then
        echo "错误: 未生成 $UNPARSED_FILE" | tee -a "$COMPARISON_OUTPUT"
        continue
    fi

    # 进行 diff 比较，检查 AST 是否正确
    echo "AST 比较结果:" >> "$COMPARISON_OUTPUT"
    diff "$OUTPUT_FILE" "$UNPARSED_FILE" >> "$COMPARISON_OUTPUT"

    if [ $? -eq 0 ]; then
        echo "✅ AST 解析正确: $TEST_NAME" | tee -a "$COMPARISON_OUTPUT"
    else
        echo "❌ AST 解析错误: $TEST_NAME" | tee -a "$COMPARISON_OUTPUT"
    fi

    # 检查是否存在标准答案文件
    SOL_FILE="Parser/${TEST_NAME}.sol"
    if [ -f "$SOL_FILE" ]; then
        echo "与标准答案比较:" >> "$COMPARISON_OUTPUT"
        diff "$OUTPUT_FILE" "$SOL_FILE" >> "$COMPARISON_OUTPUT"

        if [ $? -eq 0 ]; then
            echo "✅ 测试通过: 输出与标准答案匹配" | tee -a "$COMPARISON_OUTPUT"
        else
            echo "❌ 测试失败: 输出与标准答案不匹配" | tee -a "$COMPARISON_OUTPUT"
        fi
    else
        echo "⚠️  警告: 找不到标准答案文件 $SOL_FILE" | tee -a "$COMPARISON_OUTPUT"
    fi

    echo "" | tee -a "$COMPARISON_OUTPUT"
    echo "----------------------------------------" | tee -a "$COMPARISON_OUTPUT"
    echo "" | tee -a "$COMPARISON_OUTPUT"
done

echo "✅ 所有测试完成! 结果已保存到 $COMPARISON_OUTPUT"
