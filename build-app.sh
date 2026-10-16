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

# 检测平台
if [[ "$OSTYPE" == "darwin"* ]]; then
  ARCH=$(uname -m)
  if [[ "$ARCH" == "arm64" ]]; then
    JAVAFX_CLASSIFIER="mac-aarch64"
  else
    JAVAFX_CLASSIFIER="mac"
  fi
  PATH_SEP=":"
elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ -n "$WINDIR" ]]; then
  # Windows (Git Bash, MSYS2, Cygwin)
  JAVAFX_CLASSIFIER="win"
  PATH_SEP=";"
else
  JAVAFX_CLASSIFIER=""
  PATH_SEP=":"
fi

echo "OSTYPE=$OSTYPE, PATH_SEP=$PATH_SEP, JAVAFX_CLASSIFIER=$JAVAFX_CLASSIFIER"

MAVEN_REPO=$(mvn -q -Dexec.executable=echo -Dexec.args='${settings.localRepository}' --non-recursive exec:exec 2>/dev/null)
MAVEN_REPO="${MAVEN_REPO:-$HOME/.m2/repository}"

MODULE_JAR="target/yi-mark-${VERSION}.jar"

JAVAFX_VERSION="17.0.6"
PDFBOX_VERSION="3.0.3"

JAVAFX_MODULE_PATH="${MAVEN_REPO}/org/openjfx/javafx-controls/${JAVAFX_VERSION}/javafx-controls-${JAVAFX_VERSION}.jar"
for mod in javafx-graphics javafx-base; do
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}${PATH_SEP}${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}.jar"
  if [[ -n "$JAVAFX_CLASSIFIER" ]]; then
    JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}${PATH_SEP}${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}-${JAVAFX_CLASSIFIER}.jar"
  fi
done
if [[ -n "$JAVAFX_CLASSIFIER" ]]; then
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}${PATH_SEP}${MAVEN_REPO}/org/openjfx/javafx-controls/${JAVAFX_VERSION}/javafx-controls-${JAVAFX_VERSION}-${JAVAFX_CLASSIFIER}.jar"
fi

echo "JAVAFX_MODULE_PATH: $JAVAFX_MODULE_PATH"

# 验证 JavaFX JAR 存在
validate_jars() {
  local module_path="$1"
  local sep="$2"
  IFS="$sep" read -ra JARS <<< "$module_path"
  for jar in "${JARS[@]}"; do
    if [[ ! -f "$jar" ]]; then
      echo "ERROR: JavaFX JAR not found: $jar"
      return 1
    fi
  done
  return 0
}

validate_jars "$JAVAFX_MODULE_PATH" "$PATH_SEP" || exit 1

# 模块路径（包含 JavaFX + 我们的 JAR + PDFBox 自动模块）
JAVAFX_VERSION="17.0.6"
PDFBOX_VERSION="3.0.3"
MAVEN_REPO="$HOME/.m2/repository"

MODULE_JAR="target/yi-mark-${VERSION}.jar"

# JavaFX 模块路径
JAVAFX_MODULE_PATH="${MAVEN_REPO}/org/openjfx/javafx-controls/${JAVAFX_VERSION}/javafx-controls-${JAVAFX_VERSION}.jar"
for mod in javafx-graphics javafx-base; do
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}${PATH_SEP}${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}.jar"
  if [[ -n "$JAVAFX_CLASSIFIER" ]]; then
    JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}${PATH_SEP}${MAVEN_REPO}/org/openjfx/${mod}/${JAVAFX_VERSION}/${mod}-${JAVAFX_VERSION}-${JAVAFX_CLASSIFIER}.jar"
  fi
done
if [[ -n "$JAVAFX_CLASSIFIER" ]]; then
  JAVAFX_MODULE_PATH="${JAVAFX_MODULE_PATH}${PATH_SEP}${MAVEN_REPO}/org/openjfx/javafx-controls/${JAVAFX_VERSION}/javafx-controls-${JAVAFX_VERSION}-${JAVAFX_CLASSIFIER}.jar"
fi

# PDFBox 自动模块路径（包含 commons-logging）
PDFBOX_VERSION="3.0.3"
PDFBOX_MODULE_PATH="${MAVEN_REPO}/org/apache/pdfbox/pdfbox/${PDFBOX_VERSION}/pdfbox-${PDFBOX_VERSION}.jar"
PDFBOX_MODULE_PATH="${PDFBOX_MODULE_PATH}:${MAVEN_REPO}/org/apache/pdfbox/pdfbox-io/${PDFBOX_VERSION}/pdfbox-io-${PDFBOX_VERSION}.jar"
PDFBOX_MODULE_PATH="${PDFBOX_MODULE_PATH}:${MAVEN_REPO}/org/apache/pdfbox/fontbox/${PDFBOX_VERSION}/fontbox-${PDFBOX_VERSION}.jar"
PDFBOX_MODULE_PATH="${PDFBOX_MODULE_PATH}:${MAVEN_REPO}/commons-logging/commons-logging/1.3.3/commons-logging-1.3.3.jar"

# 完整模块路径：JavaFX + 我们的模块 + PDFBox 自动模块
FULL_MODULE_PATH="${JAVAFX_MODULE_PATH}:${MODULE_JAR}:${PDFBOX_MODULE_PATH}"

echo "=== 创建运行时镜像（仅 JavaFX + 标准库）==="
echo "FULL_MODULE_PATH: $FULL_MODULE_PATH"
rm -rf target/runtime
jlink --module-path "${JAVAFX_MODULE_PATH}" \
  --add-modules "javafx.controls,javafx.graphics,javafx.base,java.base,java.logging,java.xml,java.desktop,java.naming,jdk.crypto.ec" \
  --output target/runtime \
  --strip-debug --compress 2 --no-header-files --no-man-pages

# 复制 PDFBox 等非模块化依赖到运行时镜像的 lib 目录（供 classpath 使用）
mkdir -p target/runtime/lib
cp "${MAVEN_REPO}/org/apache/pdfbox/pdfbox/${PDFBOX_VERSION}/pdfbox-${PDFBOX_VERSION}.jar" target/runtime/lib/
cp "${MAVEN_REPO}/org/apache/pdfbox/pdfbox-io/${PDFBOX_VERSION}/pdfbox-io-${PDFBOX_VERSION}.jar" target/runtime/lib/
cp "${MAVEN_REPO}/org/apache/pdfbox/fontbox/${PDFBOX_VERSION}/fontbox-${PDFBOX_VERSION}.jar" target/runtime/lib/
cp "${MAVEN_REPO}/commons-logging/commons-logging/1.3.3/commons-logging-1.3.3.jar" target/runtime/lib/
BOUNCYCASTLE_VERSION="1.78.1"
cp "${MAVEN_REPO}/org/bouncycastle/bcprov-jdk18on/${BOUNCYCASTLE_VERSION}/bcprov-jdk18on-${BOUNCYCASTLE_VERSION}.jar" target/runtime/lib/

# 创建单独的 applib 目录供 jpackage --resource-dir 使用（只包含 PDFBox jars）
mkdir -p target/applib
cp "${MAVEN_REPO}/org/apache/pdfbox/pdfbox/${PDFBOX_VERSION}/pdfbox-${PDFBOX_VERSION}.jar" target/applib/
cp "${MAVEN_REPO}/org/apache/pdfbox/pdfbox-io/${PDFBOX_VERSION}/pdfbox-io-${PDFBOX_VERSION}.jar" target/applib/
cp "${MAVEN_REPO}/org/apache/pdfbox/fontbox/${PDFBOX_VERSION}/fontbox-${PDFBOX_VERSION}.jar" target/applib/
cp "${MAVEN_REPO}/commons-logging/commons-logging/1.3.3/commons-logging-1.3.3.jar" target/applib/
cp "${MAVEN_REPO}/org/bouncycastle/bcprov-jdk18on/${BOUNCYCASTLE_VERSION}/bcprov-jdk18on-${BOUNCYCASTLE_VERSION}.jar" target/applib/

# jpackage 的模块路径只包含 JavaFX 模块 + 我们的模块（PDFBox 走 classpath）
JPackage_MODULE_PATH="${JAVAFX_MODULE_PATH}:${MODULE_JAR}"

# 更彻底的清理，确保没有残留的临时目录
rm -rf target/dist
find target -maxdepth 1 -name 'tmp-*' -type d -exec rm -rf {} + 2>/dev/null || true
find target -maxdepth 1 -name 'tmp-*' -type d -exec rm -rf {} + 2>/dev/null || true
# Clean jpackage mods directory that causes FileAlreadyExistsException
find target/dist -name 'mods' -type d -exec rm -rf {} + 2>/dev/null || true
rm -rf target/dist
# 彻底清理之前的 mods 目录残留
rm -rf target/dist
find target -name 'mods' -type d -exec rm -rf {} + 2>/dev/null || true
ls -la target/ 2>/dev/null || echo "target not exist"
# 使用 UUID 确保临时目录名唯一
TIMESTAMP_DMG=$(uuidgen 2>/dev/null || echo "dmg-$(date +%s%N)-${RANDOM}-$$")
TIMESTAMP_APP=$(uuidgen 2>/dev/null || echo "app-$(date +%s%N)-${RANDOM}-$$")
TIMESTAMP_EXE=$(uuidgen 2>/dev/null || echo "exe-$(date +%s%N)-${RANDOM}-$$")
TIMESTAMP_MSI=$(uuidgen 2>/dev/null || echo "msi-$(date +%s%N)-${RANDOM}-$$")

if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS: 先生成 app-image，再用 app-image 生成 DMG（避免 jpackage 内部临时目录冲突）
  echo "=== 步骤 1/2: 生成 app-image ==="
  
  # 准备输入目录 - 包含 JAR 文件
  JAR_SOURCE="/Users/scorpion/workspace/codeworkspace/yi-mark/target/yi-mark-1.0.0.jar"
  INPUT_DIR="/Users/scorpion/workspace/codeworkspace/yi-mark/tmp-input"
  mkdir -p "$INPUT_DIR"
  cp "$JAR_SOURCE" "$INPUT_DIR/"
  
  # 输出目录 - 用于存放生成的 app-image
  OUTPUT_DIR="/Users/scorpion/workspace/codeworkspace/yi-mark/target/dist"
  mkdir -p "$OUTPUT_DIR"
  
  # 使用 --input 和 --dest 避免路径冲突
  jpackage --type app-image \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --runtime-image target/runtime \
    --input "$INPUT_DIR" \
    --main-jar yi-mark-1.0.0.jar \
    --dest "$OUTPUT_DIR" \
    --java-options "-Xmx512m -cp /Applications/yi-mark.app/Contents/app/pdfbox-3.0.3.jar:/Applications/yi-mark.app/Contents/app/pdfbox-io-3.0.3.jar:/Applications/yi-mark.app/Contents/app/fontbox-3.0.3.jar:/Applications/yi-mark.app/Contents/app/commons-logging-1.3.3.jar -Djavafx.verbose=true" \
    --resource-dir target/applib \
    --mac-package-identifier "com.yimark.app" \
    --mac-package-name "${APP_NAME}" \
    --temp "target/tmp-app-${TIMESTAMP_APP}" \
    --verbose

  # 清理临时目录，等待文件系统释放
  rm -rf "target/tmp-app-${TIMESTAMP_APP}"

  # 在生成 app-image 之前清理 mods 目录，避免 FileAlreadyExistsException
  rm -rf "/Users/scorpion/workspace/codeworkspace/yi-mark/target/dist/yi-mark.app/Contents/app/mods" 2>/dev/null || true
  
  echo "=== 步骤 1/2: 生成 app-image ==="
  
  # 在生成 app-image 之前清理 mods 目录，避免 FileAlreadyExistsException
  rm -rf "/Users/scorpion/workspace/codeworkspace/yi-mark/target/dist/yi-mark.app/Contents/app/mods" 2>/dev/null || true
  sleep 1

  echo "=== 步骤 2/2: 从 app-image 生成 DMG ==="
  jpackage --type dmg \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --app-image "target/dist/${APP_NAME}.app" \
    --dest target/dist \
    --mac-package-identifier "com.yimark.app" \
    --mac-package-name "${APP_NAME}" \
    --icon src/main/resources/yi-mark.icns \
    --temp "target/tmp-dmg-${TIMESTAMP_DMG}" \
    --verbose

  echo "✅ macOS 包已生成到 target/dist/"
else
  # Windows: 生成 .exe 和 .msi
  TIMESTAMP_EXE=$(date +%s%N)${RANDOM}
  TIMESTAMP_MSI=$(date +%s%N)${RANDOM}
  FULL_MODULE_PATH="target/runtime${PATH_SEP}target/runtime/mods"
  
jpackage --type exe \
    --name "${APP_NAME}" \
    --app-version "${VERSION}" \
    --description "Yi Mark - 证件/文档水印保护工具" \
    --vendor "Yi Mark Team" \
    --copyright "2024 Yi Mark Team" \
    --runtime-image target/runtime \
    --input "$INPUT_DIR" \
    --main-jar yi-mark-1.0.0.jar \
    --dest "$INPUT_DIR" \
    --java-options "-Xmx512m --add-modules=org.apache.pdfbox,org.apache.pdfbox.io,org.apache.fontbox,org.apache.commons.logging --add-reads=yi.mark=org.apache.pdfbox,org.apache.pdfbox.io,org.apache.fontbox,org.apache.commons.logging -Djavafx.verbose=true" \
    --resource-dir target/applib \
    --icon src/main/resources/yi-mark.icns \
    --temp "target/tmp-exe-${TIMESTAMP_EXE}" \
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
    --temp "target/tmp-msi-${TIMESTAMP_MSI}" \
    --verbose

  echo "✅ Windows 包已生成到 target/dist/"
fi

echo "=== 完成 ==="
ls -la target/dist/