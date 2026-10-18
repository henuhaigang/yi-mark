#!/usr/bin/env bash
# yi-mark 跨平台打包脚本
# 用法:
#   ./build-app.sh mac    # 在 macOS 上生成 .app + .dmg
#   ./build-app.sh win    # 在 Windows 上生成 .app + .exe + .msi
#   ./build-app.sh linux  # 在 Linux 上生成 app-image
#   ./build-app.sh all    # 按当前操作系统生成对应安装包
#
# 环境变量:
#   VERSION - 应用版本号（默认从 pom.xml 读取）

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

COMMAND="${1:-all}"

APP_NAME="yi-mark"
JAVAFX_VERSION="17.0.6"
PDFBOX_VERSION="3.0.3"
BOUNCYCASTLE_VERSION="1.78.1"
COMMONS_LOGGING_VERSION="1.3.3"

if [[ "$OSTYPE" == "darwin"* ]]; then
  OS="mac"
  if [[ "$(uname -m)" == "arm64" ]]; then
    JAVAFX_CLASSIFIER="mac-aarch64"
  else
    JAVAFX_CLASSIFIER="mac"
  fi
  PATH_SEP=":"
elif [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "cygwin" ]] || [[ -n "$WINDIR" ]]; then
  OS="win"
  JAVAFX_CLASSIFIER="win"
  PATH_SEP=";"
else
  OS="linux"
  JAVAFX_CLASSIFIER="linux"
  PATH_SEP=":"
fi

case "$COMMAND" in
  mac|win|linux)
    ;;
  all)
    COMMAND="$OS"
    ;;
  *)
    echo "ERROR: unknown command: $COMMAND"
    echo "Usage: $0 [mac|win|linux|all]"
    exit 1
    ;;
esac

if [[ "$COMMAND" != "$OS" ]]; then
  echo "ERROR: $COMMAND packaging must run on the $COMMAND host (current host: $OS)"
  exit 1
fi

VERSION="${VERSION:-$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout | tr -d '[:space:]')}"
VERSION="${VERSION:-1.0.9}"
MAIN_JAR="yi-mark-${VERSION}.jar"

echo "=== yi-mark packaging ==="
echo "host=$OS command=$COMMAND version=$VERSION"

echo "=== 清理并打包 JAR ==="
mvn -B -ntp clean package -DskipTests

MAVEN_REPO="$(mvn -q help:evaluate -Dexpression=settings.localRepository -DforceStdout | tr -d '[:space:]')"
MAVEN_REPO="${MAVEN_REPO:-$HOME/.m2/repository}"

echo "=== 收集运行时依赖 ==="
rm -rf target/applib target/input target/dist
mkdir -p target/applib target/input target/dist

copy_dependency() {
  local artifact="$1"
  mvn -B -ntp org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy \
    -Dartifact="$artifact" \
    -DoutputDirectory="target/applib"
}

copy_dependency "org.apache.pdfbox:pdfbox:${PDFBOX_VERSION}"
copy_dependency "org.apache.pdfbox:pdfbox-io:${PDFBOX_VERSION}"
copy_dependency "org.apache.pdfbox:fontbox:${PDFBOX_VERSION}"
copy_dependency "commons-logging:commons-logging:${COMMONS_LOGGING_VERSION}"
copy_dependency "org.bouncycastle:bcprov-jdk18on:${BOUNCYCASTLE_VERSION}"

cp "target/${MAIN_JAR}" target/input/
cp target/applib/*.jar target/input/

echo "=== 创建 jlink 运行时 ==="
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

IFS="$PATH_SEP" read -r -a MODULE_JARS <<< "$JAVAFX_MODULE_PATH"
for jar in "${MODULE_JARS[@]}"; do
  if [[ ! -f "$jar" ]]; then
    echo "ERROR: JavaFX JAR not found: $jar"
    exit 1
  fi
done

jlink \
  --module-path "$JAVAFX_MODULE_PATH" \
  --add-modules "javafx.controls,javafx.graphics,javafx.base,java.base,java.logging,java.xml,java.desktop,java.naming,jdk.crypto.ec" \
  --output target/runtime \
  --strip-debug --compress 2 --no-header-files --no-man-pages

echo "=== 生成 app-image ==="
ICON_ARGS=()
if [[ "$OS" == "mac" && -f "src/main/resources/yi-mark.icns" ]]; then
  ICON_ARGS+=(--icon "src/main/resources/yi-mark.icns")
elif [[ "$OS" == "win" && -f "src/main/resources/yi-mark.ico" ]]; then
  ICON_ARGS+=(--icon "src/main/resources/yi-mark.ico")
elif [[ "$OS" == "linux" && -f "src/main/resources/yi-mark.png" ]]; then
  ICON_ARGS+=(--icon "src/main/resources/yi-mark.png")
fi

jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$VERSION" \
  --description "Yi Mark - 证件/文档水印保护工具" \
  --vendor "Yi Mark Team" \
  --copyright "2024 Yi Mark Team" \
  --runtime-image target/runtime \
  --input target/input \
  --main-jar "$MAIN_JAR" \
  --dest target/dist \
  --java-options "-Xmx512m --add-modules jdk.crypto.ec" \
  --temp "target/tmp-app-${OS}-$$" \
  "${ICON_ARGS[@]}"

rm -rf "target/tmp-app-${OS}-$$"

if [[ "$OS" == "mac" ]]; then
  echo "=== 签名 / 公证（可选） ==="
  if [[ -n "${MAC_SIGNING_IDENTITY:-}" ]]; then
    codesign --force --deep --options runtime --sign "$MAC_SIGNING_IDENTITY" "target/dist/${APP_NAME}.app"
  fi

  if [[ -n "${APPLE_ID:-}" && -n "${APPLE_APP_PASSWORD:-}" && -n "${APPLE_TEAM_ID:-}" ]]; then
    NOTARIZE_ZIP="target/dist/${APP_NAME}-${VERSION}-notarize.zip"
    (cd "target/dist" && zip -qr "$(basename "$NOTARIZE_ZIP")" "${APP_NAME}.app")
    xcrun notarytool submit "$NOTARIZE_ZIP" \
      --apple-id "$APPLE_ID" \
      --password "$APPLE_APP_PASSWORD" \
      --team-id "$APPLE_TEAM_ID" \
      --wait
    xcrun stapler staple "target/dist/${APP_NAME}.app"
    rm -f "$NOTARIZE_ZIP"
  fi

  echo "=== 生成 DMG ==="
  DMG_SRC="target/dmg-src"
  DMG_PATH="target/dist/${APP_NAME}-${VERSION}.dmg"
  rm -rf "$DMG_SRC"
  mkdir -p "$DMG_SRC"
  ditto "target/dist/${APP_NAME}.app" "${DMG_SRC}/${APP_NAME}.app"
  ln -s /Applications "${DMG_SRC}/Applications"
  hdiutil create \
    -volname "$APP_NAME" \
    -srcfolder "$DMG_SRC" \
    -ov \
    -format UDZO \
    "$DMG_PATH"
  hdiutil verify "$DMG_PATH"
  rm -rf "$DMG_SRC"
elif [[ "$OS" == "win" ]]; then
  echo "=== 生成 Windows EXE ==="
  jpackage \
    --type exe \
    --app-image "target/dist/${APP_NAME}" \
    --name "$APP_NAME" \
    --app-version "$VERSION" \
    --vendor "Yi Mark Team" \
    --dest target/dist \
    --win-dir-chooser \
    --win-shortcut \
    --win-menu \
    --temp "target/tmp-exe-${OS}-$$"
  rm -rf "target/tmp-exe-${OS}-$$"

  echo "=== 生成 Windows MSI ==="
  jpackage \
    --type msi \
    --app-image "target/dist/${APP_NAME}" \
    --name "$APP_NAME" \
    --app-version "$VERSION" \
    --vendor "Yi Mark Team" \
    --dest target/dist \
    --win-dir-chooser \
    --win-shortcut \
    --win-menu \
    --temp "target/tmp-msi-${OS}-$$"
  rm -rf "target/tmp-msi-${OS}-$$"
elif [[ "$OS" == "linux" ]]; then
  echo "=== 生成 Linux tar.gz ==="
  tar -czf "target/dist/${APP_NAME}-${VERSION}-linux.tar.gz" -C "target/dist" "$APP_NAME"
fi

echo "=== 完成 ==="
ls -la target/dist/
