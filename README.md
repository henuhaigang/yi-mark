# Yi Mark (yi-mark)

Java 17 + JavaFX + Maven 的 macOS/Windows/Linux 证件/文档水印保护工具。

对图片/PDF 施加动态可见水印 + 频域隐形水印，并生成带 Ed25519 签名的 `.yimark.json` 清单，用于校验文件是否被改动。定位是「篡改可感知 + 提高去水印成本」，不是 DRM，也不阻止拍照或 AI 重绘。

---

## 环境要求

- macOS 13+ / Windows 10+ / Linux（需 JDK 17）
- JDK 17（与 `pom.xml` 的 `release 17` 一致）
- Maven 3.8+

---

## 快速开始

### 开发运行

```bash
mvn clean javafx:run
```

### 本地打包（生成原生安装包）

```bash
# macOS 生成 .app + .dmg
./build-app.sh mac

# Windows 生成 .exe + .msi（需在 Windows 上运行）
./build-app.sh win
```

> 打包脚本会自动下载 JavaFX 原生库、构建模块化运行时、生成原生安装包。

---

## 界面与功能

| 区域 | 说明 |
|------|------|
| **选择原图 / 选择输出** | 选取源图片（PNG/JPG）或 PDF 与导出路径（PNG/PDF） |
| **实时预览** | 中间大区域即时渲染可见水印，拖动滑块即时刷新 |
| **使用方** | 签发方身份（如「招商银行」「派出所」），为空则不显示 |
| **用途** | 使用场景（如「仅供 KYC 使用」），为空则不显示 |
| **透明度** | 0.08–0.45，默认 0.20 |
| **大小** | 0.01×–3.0×，默认 1.0×，可调至 1px（近乎隐形） |
| **角度°** | –60° 到 60°，默认 –24°（负值向右上倾斜） |
| **错位** | –1.0 到 1.0，默认 0.5（半步交错）<br>• >0：奇数行按比例错位（0.5 = 半列步）<br>• =0：不交错，整齐网格<br>• <0：随机错位，每行独立随机偏移（推荐用于提高去水印难度） |
| **生成保护文件** | 写入可见/隐形水印 + 四角几何标记，输出 `xxx.png` / `xxx.pdf` + `xxx.yimark.json` |
| **信任当前签发方** | 把本机签发公钥写入 `~/.yimark/trusted-issuers.json` |
| **验证文件** | 校验 SHA-256、Ed25519 签名、信任库、隐形水印恢复 |

### 水印设计

- **可见水印**：交错布局（支持固定比例 / 无交错 / 随机错位），FontMetrics 动态测宽，长文本不重叠
- **隐形水印**：8×8 DCT 频域，系数对 (2,3)/(3,2)，强度 18（PSNR ≈ 52 dB），CRC32 同步前导码 + 4 字节长度 + 载荷，RS(255,223) 交错编码，块哈希映射天然抗裁剪/旋转
- **载荷**：`SD3|<documentId>|<watermarkToken>|<sourceSha256>`

---

## 清单字段

| 字段 | 含义 |
|------|------|
| `version` | 清单格式版本，当前 3 |
| `documentId` | 本次保护的 UUID |
| `createdAt` | 签发时间（ISO-8601） |
| `sourceSha256` | 原图/原 PDF 字节 SHA-256 |
| `protectedSha256` | 输出 PNG/PDF 字节 SHA-256 |
| `watermarkToken` | `HMAC-SHA256(documentId\|sourceSha256\|用途\|session)`，密钥见下 |
| `signatureBase64` | 对 `documentId\|protectedSha256\|watermarkToken` 的 Ed25519 签名 |
| `publicKeyBase64` | 签名用的 Ed25519 公钥（X.509） |
| `publicKeyFingerprint` | 公钥 X.509 编码 SHA-256 前 32 hex |
| `algorithmSuite` | `SHA-256/HMAC-SHA256/Ed25519/8x8-DCT-COEFF-PAIR/CRC32/RS(255,223)` |

本地文件（首次运行自动生成）：
- `~/.yimark/ed25519.pk8 / ed25519.pub`：Ed25519 密钥对
- `~/.yimark/binding.key`：32 字节随机绑定密钥（权限 0600），用于 HMAC
- `~/.yimark/trusted-issuers.json`：信任的签发方公钥列表

---

## 验证行为

「验证文件」做三件事：

1. `SHA-256(output PNG/PDF) == protectedSha256` —— 字节级比对，任何改动失配
2. 用**信任库中固定公钥**（按指纹解析）校验 Ed25519 签名；清单自带公钥本身不作为信任依据
3. 解隐形水印，报告是否恢复、同步得分与命中的变换

只要公钥不在信任库，结论就是 `UNTRUSTED ISSUER`。

---

## 实测（JDK 17、1600×1000 证件图、strength=18、170 字节载荷）

| 项 | 结果 |
|------|------|
| 原图 PNG | 1.98 MB |
| 保护后 PNG | ~2.09 MB |
| 清单 | 734 B |
| 保护耗时 | ~850 ms |
| 验证耗时 | ~150 ms |
| 原样验证 | SHA-256 MATCH / Ed25519 VALID / 信任库命中 / 水印恢复（sync 100%） |
| 改 1 字节再验证 | SHA-256 MISMATCH |

| 变换 | 隐形水印恢复 | 耗时 |
|------|-------------|------|
| 干净 PNG | 是 | ~60 ms |
| JPEG q90 | 是 | ~70 ms |
| JPEG q75 | 是 | ~70 ms |
| 缩小 50% | 是 | ~1.5 s |
| 非对齐裁剪 | 是 | ~1.5 s |
| 旋转 1.5° | 是 | ~10 s |
| 旋转 0.7° | 是 | ~10 s |
| 局部涂黑 400×300 | 是 | ~60 ms |

单元测试 28 项（DctWatermark 9 / ReedSolomon 6 / TrustStore 7 / VerificationService 6）：

```bash
mvn test
```

---

## 已知限制

- **校验是字节级 SHA-256**：截图、另存、社交软件再压缩会让 `protectedSha256` 失配；当前 `verify()` 不用「水印能恢复」抵消字节失配
- **旋转恢复依赖固定角度网格**：提取只尝试离散角度（0.5°、0.7°、1.0°、1.2°、1.5° 等），不在网格附近的角度可能失败；缩放同理只在候选因子网格上搜索
- **缩放/旋转搜索较慢**：需逐个重采样并重跑平移搜索，单张图数秒到十几秒
- **水印恢复后未与清单交叉校验**：`verify()` 只报告，不把解出的 `documentId`/`token` 与清单比对
- **签名字段范围**：Ed25519 只覆盖 `documentId\|protectedSha256\|watermarkToken`；`sourceSha256` 仅通过 token 间接绑定
- **信任锚需人工维护**：全新安装时信任库为空，所有签发方判为 UNTRUSTED，需先「信任当前签发方」
- **密钥仍在文件系统**：私钥与绑定密钥在 `~/.yimark`，未接 macOS Keychain / Windows Credential Manager
- **载荷上限**：每个 RS 码字最多 223 字节数据，需足够 8×8 块；过小图片直接拒绝嵌入
- 不抵抗任意 AI 重绘，也不阻止另一台设备拍照

---

## 路线图

- 验证结论支持「字节失配但水印可恢复」的再处理场景
- 水印解出的 `documentId`/`token` 与清单交叉校验，纳入判定
- 旋转/缩放改为连续估计（相位相关/角点模板）而非离散候选网格，并加速
- 更抗裁剪的稀疏同步模板 / 冗余布局
- 私钥与绑定密钥迁到 macOS Keychain / Windows Credential Manager
- 考虑 DWT 方案替代当前 DCT 参考实现
- 把 JPEG、截图、裁剪、旋转、缩放攻击集纳入 CI
- Windows 代码签名 / macOS 公证

---

## 安全边界

无法保证阻止另一台设备拍照，也无法保证抵抗任意 AI 重绘。
产品正确定位：**「篡改可感知 + 来源可验证 + 提高去水印成本」**。

---

## 许可证

MIT License