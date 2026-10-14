#!/bin/bash
# yi-mark 启动脚本
# 用法: ./yi-mark.sh [参数...]

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="$SCRIPT_DIR/yi-mark.jar"
LIB_DIR="$SCRIPT_DIR/lib"

# 检查 JAR 文件
if [ ! -f "$JAR_FILE" ]; then
    echo "错误: 找不到 yi-mark.jar"
    exit 1
fi

# 构建类路径
CLASSPATH="$JAR_FILE"
for jar in "$LIB_DIR"/*.jar; do
    if [ -f "$jar" ]; then
        CLASSPATH="$CLASSPATH:$jar"
    fi
done

# JavaFX 模块路径（用于 --add-modules）
MODULE_PATH=""
for jar in "$LIB_DIR"/javafx-*.jar; do
    if [ -f "$jar" ]; then
        if [ -z "$MODULE_PATH" ]; then
            MODULE_PATH="$jar"
        else
            MODULE_PATH="$MODULE_PATH:$jar"
        fi
    fi
done

# JVM 选项
JAVA_OPTS="-Xmx512m"

# 如果有模块路径，添加 JavaFX 模块
if [ -n "$MODULE_PATH" ]; then
    JAVA_OPTS="$JAVA_OPTS --add-modules=javafx.controls,javafx.graphics,javafx.base -p $MODULE_PATH"
fi

# 启动应用
exec java $JAVA_OPTS -cp "$CLASSPATH" com.yimark.Main "$@"