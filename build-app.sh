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

# 模块路径（仅 JavaFX 模块，用于 jlink）
JAVAFX_VERSION="17.0.6"
PDFBOX_VERSION="3.0.3"
MAVEN_REPO="$HOME/.m2/repository"

MODULE_JAR="target/yi-mark-${VERSION}.jar"

JAVAFX_MODULE_PATH="${MAVEN_REPO}/org/openjfx/javafx-controls/${JAVAFX_VERSION}/javafx-controls-${JAVAFX_VERSION}.jar"
for mod in javafx-graphics javafx-base; do
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}:${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}.jar"
  if [[ "$OSTYPE" == "darwin"* ]]; then
    JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}:${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}-mac-aarch64.jar"
  fi
done
if [[ "$OSTYPE" == "darwin"* ]]; then
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}:${MAVEN_REPO}/org/openjfx/javafx-controls/${JAVAFX_VERSION}/javafx-controls-${JAVAFX_VERSION}-mac-aarch64.jar"
fi

# 创建运行时镜像（仅 JavaFX + 标准库模块）
echo "=== 创建运行时镜像（JavaFX + 标准库）==="
rm -rf target/runtime
jlink --module-path "${JAVAFX_MODULE_PATH}" \
  --add-modules "javafx.controls,javafx.graphics,javafx.base,java.base,java.logging,java.xml,java.desktop" \
  --output target/runtime \
  --strip-debug --compress 2 --no-header-files --no-man-pages

# 复制模块化 JAR 到运行时镜像的模块目录
mkdir -p target/runtime/mods
cp "${MODULE_JAR}" target/runtime/mods/

# 复制非模块化依赖到运行时镜像的 lib 目录
echo "=== 复制非模块化依赖到 runtime/lib ==="
mkdir -p target/runtime/lib
cp "${MAVEN_REPO}/org/apache/pdfbox/pdfbox/${PDFBOX_VERSION}/pdfbox-${PDFBOX_VERSION}.jar" target/runtime/lib/
cp "${MAVEN_REPO}/org/apache/pdfbox/pdfbox-io/${PDFBOX_VERSION}/pdfbox-io-${PDFBOX_VERSION}.jar" target/runtime/lib/
cp "${MAVEN_REPO}/org/apache/pdfbox/fontbox/${PDFBOX_VERSION}/fontbox-${PDFBOX_VERSION}.jar" target/runtime/lib/
cp "${MAVEN_REPO}/commons-logging/commons-logging/1.3.3/commons-logging-1.3.3.jar" target/runtime/lib/

echo "=== 打包应用（使用运行时镜像 + 模块路径包含我们的 JAR）==="
rm -rf target/dist
TIMESTAMP=$(date +%s)

if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS: 生成 .dmg 和 .app
  # --runtime-image 使用预构建的运行时（含 JavaFX 模块）
  # --module-path 包含：运行时镜像 + 我们的模块化 JAR
  # --add-modules 添加我们的模块
  # PDFBox 等非模块化依赖通过 --resource-dir 复制，并在 --java-options 中指定 classpath
  FULL_MODULE_PATH="target/runtime:target/runtime/mods"
  
  jpackage --type dmg \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --runtime-image target/runtime \
    --module-path "${FULL_MODULE_PATH}" \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m -cp lib/pdfbox-${PDFBOX_VERSION}.jar:lib/pdfbox-io-${PDFBOX_VERSION}.jar:lib/fontbox-${PDFBOX_VERSION}.jar:lib/commons-logging-1.3.3.jar" \
    --resource-dir target/runtime/lib \
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
    --runtime-image target/runtime \
    --module-path "${FULL_MODULE_PATH}" \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m -cp lib/pdfbox-${PDFBOX_VERSION}.jar:lib/pdfbox-io-${PDFBOX_VERSION}.jar:lib/fontbox-${PDFBOX_VERSION}.jar:lib/commons-logging-1.3.3.jar" \
    --resource-dir target/runtime/lib \
    --mac-package-identifier "com.yimark.app" \
    --mac-package-name "${APP_NAME}" \
    --temp "target/tmp-app-${TIMESTAMP}" \
    --verbose

  echo "✅ macOS 包已生成到 target/dist/"
else
  # Windows: 生成 .exe 和 .msi
  FULL_MODULE_PATH="target/runtime;target/runtime/mods"
  
  jpackage --type exe \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --runtime-image target/runtime \
    --module-path "${FULL_MODULE_PATH}" \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m -cp lib/pdfbox-${PDFBOX_VERSION}.jar;lib/pdfbox-io-${PDFBOX_VERSION}.jar;lib/fontbox-${PDFBOX_VERSION}.jar;lib/commons-logging-1.3.3.jar" \
    --resource-dir target/runtime/lib \
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
    --runtime-image target/runtime \
    --module-path "${FULL_MODULE_PATH}" \
    --module "${MODULE}/${MAIN_CLASS}" \
    --dest target/dist \
    --java-options "-Xmx512m -cp lib/pdfbox-${PDFBOX_VERSION}.jar;lib/pdfbox-io-${PDFBOX_VERSION}.jar;lib/fontbox-${PDFBOX_VERSION}.jar;lib/commons-logging-1.3.3.jar" \
    --resource-dir target/runtime/lib \
    --temp "target/tmp-msi-${TIMESTAMP}" \
    --verbose

  echo "✅ Windows 包已生成到 target/dist/"
fi

echo "=== 完成 ==="
ls -la target/dist/