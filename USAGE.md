# Yi Mark 用户使用手册

## 简介

Yi Mark 是一款跨平台的证件/文档水印保护工具，支持 macOS、Windows 和 Linux。它为图片（PNG/JPG）和 PDF 文档添加**可见水印**和**隐形水印**，并生成带有 Ed25519 数字签名的清单文件，用于后续验证文件是否被篡改。

**核心定位**：篡改可感知 + 来源可验证 + 提高去水印成本。不是 DRM，无法阻止拍照、截屏或 AI 重绘。

---

## 安装与运行

### 方式一：下载原生安装包（推荐）

| 平台 | 安装包 |
|------|--------|
| macOS | `yi-mark-1.0.9.dmg` → 拖入 Applications |
| Windows | `yi-mark-1.0.9.exe` 或 `yi-mark-1.0.9.msi` |
| Linux | `yi-mark-1.0.9-linux.tar.gz` 或 JAR 包运行（见下文） |

安装包可从 GitHub Release 下载；CI 工作流也会在 `main` 分支 push、PR 和手动触发时生成打包 artifacts。

> macOS 如果提示“Apple 无法验证开发者”或“已损坏，无法打开”，说明当前包未使用 Developer ID 签名/公证。详细处理步骤见下方 [macOS：没有 Apple 开发者账号如何安装](#macos没有-apple-开发者账号如何安装未签名包)。

### macOS：没有 Apple 开发者账号如何安装（未签名包）

> **为什么会出现这个问题？**
> macOS 的 Gatekeeper 要求分发的 App 必须用 **Developer ID** 签名并通过 **Apple 公证（notarization）**。没有 Apple 开发者账号（99 美元/年）时，打包产物只能做 **ad-hoc 签名**。这类包从互联网下载后，Gatekeeper 会拦截首次启动，常见提示有：
> - “Apple 无法验证此 App 的开发者”
> - 无法打开“yi-mark”，因为无法验证开发者
> - “yi-mark”已损坏，无法打开
>
> **关键区别（先判断你属于哪种情况）**：
>
> | 场景 | 是否有隔离属性 | 是否需要处理 |
> |------|----------------|--------------|
> | 本机执行 `./build-app.sh mac` 构建 | 无 `com.apple.quarantine` | **不需要**，直接双击即可运行 |
> | 从 GitHub Release / 邮件 / 微信等下载 | 有 `com.apple.quarantine` | **需要**，按下面任一方法放行 |
>
> 隔离属性（quarantine）是 macOS 给“从网络下载的文件”打上的标记，Gatekeeper 依据它决定是否拦截。下面所有方法都针对**下载来的包**。

#### 方法一：右键打开（推荐，无需终端）

1. 双击 DMG 挂载，把 `yi-mark.app` 拖入 `Applications` 文件夹
2. **不要直接双击** App（会被拦截）
3. 在「应用程序」文件夹中，**右键**（或按住 `Control` 点击）`yi-mark` → 选择 **打开**
4. 在弹出的对话框中再次点击 **打开** 确认
5. 首次放行成功后，之后双击即可正常启动

> 若右键菜单没有「打开」选项，或仍提示“已损坏”，请改用方法二或方法三。

#### 方法二：通过系统设置放行（macOS Ventura 及以上）

1. 先双击一次 App（会被拦截，但此步会生成拦截记录，必须执行）
2. 打开 **系统设置** → **隐私与安全性**
3. 向下滚动，会看到提示：`“yi-mark”已被阻止使用，因为其开发者无法验证`
4. 点击 **仍要打开**，输入登录密码
5. 回到 App 弹窗，点击 **打开**

> 旧版 macOS（Monterey 及更早）路径为 **系统偏好设置** → **安全性与隐私** → **通用** 标签页 → 「仍要打开」。

#### 方法三：终端移除隔离属性（最彻底）

```bash
# 移除整个 App 的隔离属性
xattr -dr com.apple.quarantine /Applications/yi-mark.app

# 验证属性已移除（无输出即表示已干净）
xattr /Applications/yi-mark.app

# 启动
open /Applications/yi-mark.app
```

若提示“已损坏，无法打开”，用更彻底的清除命令：

```bash
sudo xattr -cr /Applications/yi-mark.app
```

#### 如何确认 App 的签名状态

```bash
# 查看签名信息（ad-hoc 签名会显示 flags=0x2(adhoc)）
codesign -dv --verbose=4 /Applications/yi-mark.app 2>&1

# 查看隔离属性（有 com.apple.quarantine 即表示被 Gatekeeper 标记）
xattr -l /Applications/yi-mark.app

# 查看 Gatekeeper 评估结果（rejected 表示被拦截）
spctl --assess --type execute /Applications/yi-mark.app
```

未签名（ad-hoc）包的典型输出：

```
Executable=/Applications/yi-mark.app/Contents/MacOS/yi-mark
Identifier=com.yimark
Format=app bundle with Mach-O thin (arm64)
CodeDirectory v=20400 size=1475 flags=0x2(adhoc) hashes=40+3 location=embedded
```

> ⚠️ **不要**使用 `sudo spctl --global-disable`（或旧命令 `sudo spctl --master-disable`），那会关闭**整个系统**的 Gatekeeper，让所有来源的 App 都能运行，存在严重安全风险。请优先使用上面三种针对性方法。

#### 安全提醒

1. **只对你信任来源的 App 移除隔离属性**。`com.apple.quarantine` 是 macOS 防止恶意软件的安全机制，移除它等于告诉系统“我信任这个 App”。
2. **校验来源**：只从官方 GitHub Release 下载，并用 `SHA256SUMS.txt` 核对哈希：
   ```bash
   shasum -a 256 yi-mark-1.0.9.dmg
   ```
3. **正式分发需要签名**：若要让他人无需上述步骤即可安装，必须购买 Apple 开发者账号（99 美元/年），用 Developer ID 签名并完成公证。`build-app.sh` 已支持：
   ```bash
   export MAC_SIGNING_IDENTITY="Developer ID Application: Your Name (TEAMID)"
   export APPLE_ID="you@example.com"
   export APPLE_APP_PASSWORD="xxxx xxxx xxxx xxxx"  # App 专用密码
   export APPLE_TEAM_ID="TEAMID"
   ./build-app.sh mac
   ```

### 方式二：开发环境运行

```bash
# 克隆项目
git clone <repo-url>
cd yi-mark

# 运行（需 JDK 17 + Maven）
mvn clean javafx:run
```

### 方式三：运行 JAR 包（Linux/通用）

```bash
# 先打包
mvn clean package

# 运行（需安装 JavaFX 17）
java --module-path /path/to/javafx-sdk-17/lib \
     --add-modules javafx.controls,javafx.graphics \
     -jar target/yi-mark-1.0.9.jar
```

---

## 界面说明

启动后主界面包含以下区域：

### 顶部按钮栏
| 按钮 | 功能 |
|------|------|
| **选择原图** | 选择源文件（支持 PNG、JPG、PDF） |
| **选择输出** | 选择输出路径（支持 PNG、PDF） |
| **生成保护文件** | 对原图加水印并生成清单 |
| **验证文件** | 校验输出文件是否被篡改 |
| **信任当前签发方** | 将本机公钥加入信任库 |

### 参数设置区
| 参数 | 范围 | 默认值 | 说明 |
|------|------|--------|------|
| **使用方** | 文本 | SecureDoc | 水印中显示的签发机构名称 |
| **用途** | 文本 | 仅供指定用途使用 | 水印中显示的使用限制说明 |
| **透明度** | 0.08–0.60 | 0.30 | 可见水印透明度，越大越明显 |
| **大小** | 0.01×–3.0× | 1.0× | 水印字体大小倍率 |
| **行间距** | 1.0×–8.0× | 2.4× | 可见水印行距倍率，值越大行与行之间越稀疏 |
| **角度°** | –60° 到 60° | –24° | 水印倾斜角度，负值向右上倾斜 |
| **错位** | –1.0 到 1.0 | 0.5 | **新增**：水印行间错位模式（见下文详解） |
| **水印颜色** | **新增** | 灰色 (128,128,128) | **新增**：水印颜色选择器，默认灰色，在深/浅背景下均清晰 |

### 预览区
实时显示加水印后的效果，拖动滑块即时刷新。

### 日志区
显示操作结果、错误信息、验证报告等。

---

## 核心功能详解

### 1. 错位模式详解（新增）

「错位」滑块控制可见水印行与行之间的横向偏移：

| 值 | 模式 | 效果 | 适用场景 |
|----|------|------|----------|
| **0.5（默认）** | 固定半步交错 | 奇数行向右偏移半个列间距，形成砖墙状交错 | 通用场景，美观且难以去除 |
| **0.0** | 无交错 | 所有行对齐，整齐网格 | 需要整齐排版时 |
| **0.1–1.0** | 固定比例交错 | 奇数行按比例错位（如 0.3 = 30% 列间距） | 自定义交错密度 |
| **< 0（如 –0.5、–1.0）** | **随机错位** | 每行独立随机偏移 [0, 列间距) | **推荐**：最大程度提高去水印难度，破坏规律性 |

> **建议**：日常使用保持默认 0.5；对抗去水印工具时，设为负值（如 –0.5 或 –1.0）启用随机错位。

### 1.5 行间距（新增）

「行间距」滑块控制可见水印相邻行之间的垂直距离：

| 值 | 效果 | 适用场景 |
|----|------|----------|
| **1.0×** | 行距最紧凑，约为字体大小 1 倍 | 小图或需要更高水印密度 |
| **2.4×（默认）** | 行距清晰、覆盖均匀 | 通用证件/文档场景 |
| **8.0×** | 行距最稀疏，水印更分散 | 大尺寸文档或需要降低视觉密度 |

> **提示**：行间距只影响可见水印排版，不影响隐形水印强度、签名清单或验证结果。

### 2. 马赛克涂抹工具（新增）

**「马赛克」**区域提供手动涂抹马赛克功能，用于遮盖敏感信息（如身份证号、人脸等）。

#### 控制项
| 控件 | 说明 |
|------|------|
| **启用马赛克** | 勾选后启用涂抹模式，取消勾选则禁用 |
| **马赛克块大小** | 滑块调节（2–50 像素），默认 10，实时预览效果 |
| **撤销** | 按钮 / `Ctrl+Z` 快捷键，逐步撤销涂抹笔画（最多保存 50 步历史） |
| **清除所有** | 一键清除所有马赛克（清除前自动保存状态，可撤销恢复） |

#### 交互操作
1. 勾选「启用马赛克」
2. 调整「马赛克块大小」滑块（2–50 像素，默认 10）
3. 在预览图上**按住鼠标左键拖拽**涂抹想要马赛克的区域
4. 实时预览显示黑色马赛克块（仅涂抹区域）
5. 涂抹错误 → 点「撤销」或按 `Ctrl+Z` 逐步撤销
5. 想重来 → 点「清除所有」一键清空（可再撤销恢复）
6. 满意后点「生成保护文件」 → 仅对涂抹区域应用马赛克

> **提示**：马赛克仅应用于用户涂抹的区域，非全图马赛克。块大小越小越精细，越大越模糊。

### 2.5 水印颜色选择器（新增）

**「水印颜色」**区域提供自定义水印颜色功能，解决白色水印在深色背景下不可见的问题。

#### 控制项
| 控件 | 说明 |
|------|------|
| **水印颜色** | 颜色选择器，默认灰色 (128,128,128)，在深色/浅色背景下均清晰可见 |

#### 使用建议
- **浅色背景图片**：建议使用深色水印（如黑色、深蓝、深灰）
- **深色背景图片**：建议使用浅色水印（如白色、浅灰、黄色）
- **复杂背景图片**：默认灰色 (128,128,128) 在大多数背景下均清晰可见

> **提示**：水印颜色仅影响可见水印，不影响隐形水印和马赛克效果。

### 3. 生成保护文件

#### 交互操作
1. 勾选「启用马赛克」
2. 调整「马赛克块大小」滑块（2–50 像素，默认 10）
3. 在预览图上**按住鼠标左键拖拽**涂抹想要马赛克的区域
4. 实时预览显示黑色马赛克块（仅涂抹区域）
5. 涂抹错误 → 点「撤销」或按 `Ctrl+Z` 逐步撤销
5. 想重来 → 点「清除所有」一键清空（可再撤销恢复）
6. 满意后点「生成保护文件」 → 仅对涂抹区域应用马赛克

> **提示**：马赛克仅应用于用户涂抹的区域，非全图马赛克。块大小越小越精细，越大越模糊。

### 3. 生成保护文件

操作步骤：
1. 点「选择原图」选取源文件（PNG/JPG/PDF）
2. 点「选择输出」设置输出路径（建议用 `.png` 或 `.pdf` 后缀）
3. 调整水印参数（实时预览）
4. 点「生成保护文件」

**输出产物**：
- `xxx.png` 或 `xxx.pdf` —— 加了可见+隐形水印+四角几何标记的文件
- `xxx.securedoc.json` —— 签名清单（含 documentId、哈希、签名、公钥等）

> **注意**：PDF 输入时仅处理第一页，输出为单页 PDF。

### 3. 信任当前签发方

首次使用或换新机器时，**必须**执行此操作，否则验证会显示 `UNTRUSTED ISSUER`。

操作：
1. 先生成一次保护文件
2. 点「信任当前签发方」
3. 系统将本机公钥写入 `~/.securedoc/trusted-issuers.json`

> 信任库是**本地**的，不同电脑需各自信任。收到他人文件时，需导入对方公钥（见进阶用法）。

### 4. 验证文件

操作：
1. 点「选择输出」选中待验证的 PNG/PDF 文件
2. 点「验证文件」

**验证做三件事**：
1. **字节级 SHA-256 对比**：`文件哈希 == 清单中 protectedSha256`？任何改动（哪怕重保存一次）都会失配
2. **Ed25519 签名验证**：用**信任库中**的公钥验签（清单自带公钥不被信任）
3. **隐形水印提取**：尝试恢复载荷，报告同步得分、是否恢复、命中的变换（旋转/缩放/裁剪）

**结果判读**：
| 结论 | 含义 |
|------|------|
| `file matches the signed manifest and the issuer is trusted` | 完好，受信任签发 |
| `UNTRUSTED ISSUER` | 公钥不在信任库，**签名虽有效但不可信** |
| `file is suspicious` | 字节哈希失配或签名无效，文件被篡改 |
| `Watermark: recovered` | 隐形水印成功恢复（即使字节失配也可能恢复） |

---

## 进阶用法

### 导入他人的公钥（跨机器验证）

收到别人发来的 `xxx.securedoc.json` 和 `xxx.png`，想在自己电脑上验证：

```bash
# 1. 从清单提取公钥（Base64）
cat xxx.securedoc.json | grep publicKeyBase64

# 2. 在自己机器上导入（需手动编辑 trusted-issuers.json）
# 文件位置：~/.securedoc/trusted-issuers.json
# 格式参考现有条目，添加：
{
  "fingerprint": "sha256:...",
  "publicKeyBase64": "...",
  "label": "对方名称 @ 2026-01-01",
  "trustedAt": "2026-01-01T00:00:00Z"
}
```

> 也可让对方把他们的 `ed25519.pub` 发给你，用 `Ed25519Keys.fromBase64()` 加载后调用 `TrustStore.trust()`。

### 批量处理（命令行）

目前无 CLI 批量模式，需通过代码调用 `ProtectionService`。示例：

```java
ProtectionService svc = new ProtectionService(Paths.get(System.getProperty("user.home"), ".securedoc"));
for (Path src : sources) {
    Path out = src.resolveSibling(src.getFileName() + "-watermarked.png");
    svc.protect(
        src,
        out,
        "MyOrg",
        "Internal Use",
        0.30,
        24,
        -24.0,
        0.5,
        VisibleWatermark.DEFAULT_LINE_SPACING_RATIO,
        false,
        10,
        List.of(),
        VisibleWatermark.DEFAULT_WATERMARK_COLOR
    );
}
```

### 调整隐形水印强度

代码中写死 `strength = 18`（`DctWatermark.java`），如需修改需重新编译。PSNR 约 52 dB，肉眼不可见。

---

## 常见问题

### Q: 验证提示 `UNTRUSTED ISSUER` 怎么办？
**A**: 点「信任当前签发方」把本机公钥加入信任库。跨机器验证需导入对方公钥。

### Q: PDF 只处理第一页？
**A**: 是的，当前版本仅渲染 PDF 第一页加水印，输出单页 PDF。多页 PDF 需自行拆分处理。

### Q: 截图/微信发送后验证失败？
**A**: 这是**预期行为**。验证是字节级 SHA-256，任何重压缩、格式转换都会导致哈希失配。隐形水印可能仍可恢复（日志会显示），但字节级校验不通过。

### Q: 旋转/缩放后隐形水印恢复很慢？
**A**: 同步搜索需遍历离散角度/缩放网格并重采样，单张图可能耗时数秒到十几秒。这是已知限制。

### Q: 如何备份密钥？
**A**: 备份整个 `~/.securedoc/` 目录：
- `ed25519.pk8` / `ed25519.pub` —— 签名密钥对
- `binding.key` —— HMAC 绑定密钥（32 字节，**权限 0600**）
- `trusted-issuers.json` —— 信任库

### Q: 换电脑了怎么迁移？
**A**: 把 `~/.securedoc/` 整个目录复制到新电脑同一路径即可。

---

## 安全提醒

1. **私钥即身份**：`ed25519.pk8` 泄露 = 任何人可伪造你的签名。妥善保管，建议加密存储。
2. **信任库即信任锚**：只有你手动添加的公钥才会被信任。不要盲目信任收到的清单中的公钥。
3. **非防拍照工具**：水印无法阻止手机拍照、屏幕截图、AI 重绘。定位是**事后取证**。
4. **字节级验证严格**：任何再编码（微信压缩、截图保存、格式转换）都会导致 SHA-256 失配。这是特性，不是 Bug。

---

## 文件结构

```
~/.securedoc/
├── ed25519.pk8          # Ed25519 私钥 (PKCS#8)
├── ed25519.pub          # Ed25519 公钥 (X.509)
├── binding.key          # 32 字节 HMAC 密钥 (chmod 600)
└── trusted-issuers.json # 信任的签发方公钥列表
```

输出文件命名约定：
```
document.png           -> document.png + document.securedoc.json
report.pdf             -> report.pdf + report.securedoc.json
```

---

## 许可证

MIT License。详见源码根目录 `LICENSE` 文件。