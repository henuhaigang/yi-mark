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

# 模块路径（JavaFX 模块 + 我们的模块化 JAR + PDFBox 自动模块）
JAVAFX_VERSION="17.0.6"
PDFBOX_VERSION="3.0.3"
MAVEN_REPO="$HOME/.m2/repository"

MODULE_JAR="target/yi-mark-${VERSION}.jar"

MODULE_PATH="${MODULE_JAR}"
for mod in javafx-controls javafx-graphics javafx-base; do
  MODULE_PATH="${MODULE_PATH}:${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}.jar"
  if [[ "$OSTYPE" == "darwin"* ]]; then
    MODULE_PATH="${MODULE_PATH}:${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}-mac-aarch64.jar"
  fi
done

# PDFBox 自动模块
MODULE_PATH="${MODULE_PATH}:${MAVEN_REPO}/org/apache/pdfbox/pdfbox/${PDFBOX_VERSION}/pdfbox-${PDFBOX_VERSION}.jar"
MODULE_PATH="${MODULE_PATH}:${MAVEN_REPO}/org/apache/pdfbox/pdfbox-io/${PDFBOX_VERSION}/pdfbox-io-${PDFBOX_VERSION}.jar"
MODULE_PATH="${MODULE_PATH}:${MAVEN_REPO}/org/apache/pdfbox/fontbox/${PDFBOX_VERSION}/fontbox-${PDFBOX_VERSION}.jar"
MODULE_PATH="${MODULE_PATH}:${MAVEN_REPO}/commons-logging/commons-logging/1.3.3/commons-logging-1.3.3.jar"

echo "=== 直接使用 jpackage 打包（模块化模式，内部运行 jlink） ==="
rm -rf target/dist
TIMESTAMP=$(date +%s)

if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS: 生成 .dmg 和 .app
  jpackage --type dmg \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --module-path "${MODULE_PATH}" \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m" \
    --add-modules "${MODULE},javafx.controls,javafx.graphics,javafx.base,org.apache.pdfbox" \
    --mac-package-identifier "com.yimark.app" \
    --mac-package-name "${APP_NAME}" \
    --temp "target/tmp-dmg-${TIMESTAMP}" \
    --verbose

  jpackage --type app-image \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --module-path "${MODULE_PATH}" \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m" \
    --add-modules "${MODULE},javafx.controls,javafx.graphics,javafx.base,org.apache.pdfbox" \
    --mac-package-identifier "com.yimark.app" \
    --mac-package-name "${APP_NAME}" \
    --temp "target/tmp-app-${TIMESTAMP}" \
    --verbose

  echo "✅ macOS 包已生成到 target/dist/"
else
  # Windows: 生成 .exe 和 .msi
  jpackage --type exe \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --module-path "${MODULE_PATH}" \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m" \
    --add-modules "${MODULE},javafx.controls,javafx.graphics,javafx.base,org.apache.pdfbox" \
    --win-per-user-install \
    --win-dir-chooser \
    --win-menu \
    --win-shortcut \
    --temp "target/tmp-exe-${TIMESTAMP}" \
    --verbose

  jpackage --type msi \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --module-path "${MODULE_PATH}" \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m" \
    --add-modules "${MODULE},javafx.controls,javafx.graphics,javafx.base,org.apache.pdfbox" \
    --temp "target/tmp-msi-${TIMESTAMP}" \
    --verbose

  echo "✅ Windows 包已生成到 target/dist/"
fi

echo "=== 完成 ==="
ls -la target/dist/