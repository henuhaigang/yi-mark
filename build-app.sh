#!/bin/bash
# yi-mark 跨平台打包脚本
# 用法: ./build-app.sh [mac|win|all]
# macOS 上运行可生成 macOS 安装包
# Windows 上运行可生成 Windows 安装包

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
VERSION="1.0.0"
APP_NAME="yi-mark"
MAIN_CLASS="com.yimark.Main"
MODULE="yi.mark"

cd "$PROJECT_DIR"

echo "=== 清理并编译 ==="
mvn -B clean compile

echo "=== 打包模块化 JAR ==="
mvn -B package -DskipTests

# 创建模块路径（包含 JavaFX 模块 + 我们的模块化 JAR）
JAVAFX_VERSION="17.0.6"
MAVEN_REPO="$HOME/.m2/repository/org/openjfx"

MODULE_JAR="target/yi-mark-${VERSION}.jar"

MODULE_PATH="${MODULE_JAR}"
for mod in javafx-controls javafx-graphics javafx-base; do
  MODULE_PATH="${MODULE_PATH}:${MAVEN_REPO}/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}.jar"
  if [[ "$OSTYPE" == "darwin"* ]]; then
    MODULE_PATH="${MODULE_PATH}:${MAVEN_REPO}/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}-mac-aarch64.jar"
  fi
done

echo "=== 创建模块化运行时镜像 ==="
rm -rf target/runtime
jlink --module-path "${MODULE_PATH}" \
  --add-modules "${MODULE},javafx.controls,javafx.graphics,javafx.base,java.base,java.logging,java.xml,java.desktop" \
  --output target/runtime \
  --strip-debug --compress 2 --no-header-files --no-man-pages

echo "=== 验证模块已包含 ==="
/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home/bin/java --list-modules --module-path target/runtime | grep yi || { echo "ERROR: yi.mark not in runtime!"; exit 1; }

echo "=== 打包应用 ==="
if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS: 生成 .app 和 .dmg
  jpackage --type app-image \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --module-path target/runtime \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m" \
    --mac-package-identifier "com.yimark.app" \
    --mac-package-name "${APP_NAME}"

  jpackage --type dmg \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --module-path target/runtime \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m" \
    --mac-package-identifier "com.yimark.app" \
    --mac-package-name "${APP_NAME}"

  echo "✅ macOS 包已生成到 target/dist/"
else
  # Windows: 生成 .exe 和 .msi
  jpackage --type exe \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --module-path target/runtime \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m" \
    --win-per-user-install \
    --win-dir-chooser \
    --win-menu \
    --win-shortcut

  jpackage --type msi \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --module-path target/runtime \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m"

  echo "✅ Windows 包已生成到 target/dist/"
fi

echo "=== 完成 ==="
ls -la target/dist/