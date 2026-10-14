# yi-mark 发布包

## 目录结构

```
release/
├── yi-mark.app          # macOS 应用程序包（可直接运行）
├── yi-mark-1.0.0.dmg    # macOS 安装包（双击安装）
├── yi-mark.jar          # 主程序 JAR 包
├── yi-mark.sh           # Linux/macOS 启动脚本
├── yi-mark.bat          # Windows 启动脚本
└── lib/                 # 依赖库目录
    ├── yi-mark-1.0.0.jar (原始模块化 JAR)
    ├── pdfbox-3.0.3.jar
    ├── pdfbox-io-3.0.3.jar
    ├── fontbox-3.0.3.jar
    ├── commons-logging-1.3.3.jar
    ├── javafx-controls-17.0.6.jar
    ├── javafx-controls-17.0.6-mac-aarch64.jar
    ├── javafx-graphics-17.0.6.jar
    ├── javafx-graphics-17.0.6-mac-aarch64.jar
    ├── javafx-base-17.0.6.jar
    └── javafx-base-17.0.6-mac-aarch64.jar
```

## 运行方式

### macOS

**方式 1：使用 .app 包（推荐）**
```bash
open yi-mark.app
```
或双击 `yi-mark.app` 图标。

**方式 2：使用安装包**
```bash
# 双击 yi-mark-1.0.0.dmg 安装
# 安装后在 Applications 文件夹中找到 yi-mark
```

**方式 3：使用启动脚本（开发/调试）**
```bash
chmod +x yi-mark.sh
./yi-mark.sh
```

### Linux

```bash
chmod +x yi-mark.sh
./yi-mark.sh
```

### Windows

```cmd
yi-mark.bat
```

或双击 `yi-mark.bat`。

## 依赖说明

`lib/` 目录包含所有运行时依赖：
- **JavaFX 17.0.6**：GUI 框架（含 macOS ARM64 原生库）
- **PDFBox 3.0.3**：PDF 处理
- **FontBox 3.0.3**：PDF 字体处理
- **Commons Logging 1.3.3**：日志框架

## 环境要求

- Java 17+ (JDK 或 JRE)
- macOS 13+ / Windows 10+ / Linux (glibc 2.31+)

## 故障排除

### macOS 无法打开应用
```bash
# 移除隔离属性
xattr -dr com.apple.quarantine yi-mark.app

# 或右键点击应用选择"打开"
```

### 启动脚本权限问题
```bash
chmod +x yi-mark.sh
```

### Java 版本问题
确保使用 Java 17+：
```bash
java -version
```

## 版本信息

- 版本: 1.0.0
- 构建时间: 2026-10-10
- Java 版本: 17
- JavaFX 版本: 17.0.6
- PDFBox 版本: 3.0.3