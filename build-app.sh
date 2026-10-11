#!/bin/bash
# yi-mark 跨平台打包脚本
# 用法: ./build-app.sh [mac|win|all]
# macOS 上运行可生成 macOS 安装包
# Windows 上运行可生成 Windows 安装包
#
# 环境变量:
#   VERSION - 应用版本号 (默认从 pom.xml 读取)

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# 从 pom.xml 读取版本，或使用环境变量 VERSION
if [ -z "$VERSION" ]; then
  VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec 2>/dev/null)
fi
VERSION="${VERSION:-1.0.0}"

APP_NAME="yi-mark"
MAIN_CLASS="com.yimark.Main"
MODULE="yi.mark"

echo "=== Building version: $VERSION ==="

cd "$PROJECT_DIR"

echo "=== 清理并编译 ==="
mvn -B clean compile

echo "=== 打包模块化 JAR ==="
mvn -B package -DskipTests

# 模块路径（仅 JavaFX 模块，用于 jlink）
JAVAFX_VERSION="17.0.6"
PDFBOX_VERSION="3.0.3"

# 使用 Maven 查找本地仓库路径，兼容跨平台
MAVEN_REPO=$(mvn -q -Dexec.executable=echo -Dexec.args='${settings.localRepository}' --non-recursive exec:exec 2>/dev/null)
MAVEN_REPO="${MAVEN_REPO:-$HOME/.m2/repository}"

MODULE_JAR="target/yi-mark-${VERSION}.jar"

# 检测架构并设置 JavaFX classifier
if [[ "$OSTYPE" == "darwin"* ]]; then
  ARCH=$(uname -m)
  if [[ "$ARCH" == "arm64" ]]; then
    JAVAFX_CLASSIFIER="mac-aarch64"
  else
    JAVAFX_CLASSIFIER="mac"
  fi
elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ -n "$WINDIR" ]]; then
  # Windows (Git Bash, MSYS2, Cygwin)
  JAVAFX_CLASSIFIER="win"
else
  JAVAFX_CLASSIFIER=""
fi

JAVAFX_MODULE_PATH="${MAVEN_REPO}/org/openjfx/javafx-controls/${JAVAFX_VERSION}/javafx-controls-${JAVAFX_VERSION}.jar"
for mod in javafx-graphics javafx-base; do
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}:${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}.jar"
  if [[ -n "$JAVAFX_CLASSIFIER" ]]; then
    JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}:${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}-${JAVAFX_CLASSIFIER}.jar"
  fi
done
if [[ -n "$JAVAFX_CLASSIFIER" ]]; then
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}:${MAVEN_REPO}/org/openjfx/javafx-controls/${JAVAFX_VERSION}/javafx-controls-${JAVAFX_VERSION}-${JAVAFX_CLASSIFIER}.jar"
fi

# 验证 JavaFX JAR 存在 (兼容 Windows 路径中的盘符冒号)
# Windows 路径包含盘符 (C:\)，不能简单按冒号分割
# 使用 find 命令逐个验证
validate_jars() {
  local module_path="$1"
  # 将模块路径转换为数组，处理 Windows 盘符
  if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ -n "$WINDIR" ]]; then
    # Windows: 使用 ; 作为分隔符（Java 支持），避免盘符冒号干扰
    # 将 : 替换为 ; 再分割
    local normalized="${module_path//:;/;}"
    IFS=';' read -ra JARS <<< "$normalized"
  else
    IFS=':' read -ra JARS <<< "$module_path"
  fi
  for jar in "${JARS[@]}"; do
    if [[ ! -f "$jar" ]]; then
      echo "ERROR: JavaFX JAR not found: $jar"
      return 1
    fi
  done
  return 0
}

validate_jars "$JAVAFX_MODULE_PATH" || exit 1

# 创建运行时镜像（仅 JavaFX + 标准库模块）
echo "=== 创建运行时镜像（JavaFX + 标准库）==="
echo "JAVAFX_MODULE_PATH: ${JAVAFX_MODULE_PATH}"
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
    --icon src/main/resources/yi-mark.icns \
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
    --icon src/main/resources/yi-mark.icns \
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