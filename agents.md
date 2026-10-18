# yi-mark Agent Instructions

## Project Overview
- **Name**: yi-mark (formerly SecureDoc-Java)
- **Type**: Cross-platform document watermark protection tool (Java 17 + JavaFX)
- **Core features**: Visible tiled watermark + invisible DCT frequency-domain watermark + Ed25519 signed manifest
- **Platforms**: macOS (app + dmg), Windows (app-image + exe + msi), Linux (app-image + tar.gz)
- **Language**: Java 17, Maven, JavaFX 17.0.6

## Architecture

```
com.yimark
├── crypto/           # Ed25519 keys, HMAC binding secret, SHA-256/HMAC digests
├── image/            # VisibleWatermark (staggered tiles), GeometryMarkers, ImageOps (mosaic brush)
├── manifest/         # Signed manifest (V3, JSON, includes Ed25519 sig + HMAC token)
├── protect/          # ProtectionService (orchestrates visible + invisible + mosaic + signing)
├── trust/            # TrustStore (pinned issuer keys, ~/.securedoc/trusted-issuers.json)
├── util/             # Minimal JSON parser (no external deps)
├── verify/           # VerificationService (SHA-256 + Ed25519 + trust anchor + watermark extraction)
└── watermark/
    ├── DctWatermark.java   # Invisible: embed/extract, sync search, RS(255,223)
    ├── DctTransform.java   # 8x8 DCT basis (coeff pair 2,3 / 3,2)
    ├── ReedSolomon.java    # RS(255,223) interleaved encode/decode
    ├── Frame.java          # Sync preamble + length + CRC32 framing
    └── Extraction.java     # Extraction result DTO
```

## Key Design Decisions
- **Invisible watermark**: DCT coefficient pair (2,3) vs (3,2), strength 18, PSNR ~52 dB
- **Sync**: 16-byte CRC32-derived preamble + 4-byte length, spread via block-hash mapping (survives crop/rotate)
- **ECC**: RS(255,223) interleaved, corrects up to 16 byte errors per codeword
- **Visible watermark**: Staggered tile grid (odd rows offset by half column step), FontMetrics-based spacing, no overlap
- **Line spacing**: Continuous `lineSpacingRatio` multiplier for vertical row step, UI range `1.0-8.0`, default `2.4`, invalid values fall back to default
- **Mosaic brush**: Manual brush tool with adjustable block size (2-50px), undo/redo history (50 steps), clear-all with undo support, real-time canvas overlay preview
- **Watermark color picker**: Custom color support via JavaFX ColorPicker, default gray (128,128,128) for visibility on both light/dark backgrounds
- **Trust model**: Manifest carries its own public key, but verification ONLY trusts keys pinned in `~/.securedoc/trusted-issuers.json`
- **No external JSON lib**: Custom flat-object parser in `Json.java`

## Build & Run

```bash
# Dev run
mvn clean javafx:run

# Native packaging (macOS)
./build-app.sh mac   # produces target/dist/yi-mark.app + yi-mark-1.0.9.dmg

# Native packaging (Windows) - run on Windows
./build-app.sh win   # produces target/dist/yi-mark + yi-mark-1.0.9.exe + yi-mark-1.0.9.msi

# Native packaging (Linux)
./build-app.sh linux # produces target/dist/yi-mark app-image + yi-mark-1.0.9-linux.tar.gz

# Tests
mvn test   # 32 tests (DctWatermark 9, ReedSolomon 6, TrustStore 7, VerificationService 7, VisibleWatermark 2, MainProvider 1)
```

## CI / Release
- `.github/workflows/ci.yml`: runs `mvn test` on Ubuntu/macOS/Windows and packages macOS, Windows, and Linux artifacts.
- `.github/workflows/release.yml`: triggered by `v*` tags, downloads packaging artifacts, generates `SHA256SUMS.txt`, and publishes a GitHub Release.
- macOS DMG is created with `hdiutil create` because `jpackage --type dmg` may fail in local/CI environments with `hdiutil attach` permission issues.
- Windows MSI requires WiX Toolset; the workflow installs it with Chocolatey before running `./build-app.sh win`.

## Module System
- `module-info.java` declares `module yi.mark` with `requires javafx.controls/javafx.graphics/javafx.base/java.desktop`
- All internal packages `opens ... to javafx.graphics` for JavaFX reflection
- `build-app.sh` creates a jlink runtime with JavaFX modules, then uses `jpackage --type app-image` with `--input` + `--main-jar` so the app launcher classpath includes the app JAR and runtime dependencies
- Do not pass `-cp` through `--java-options` for the packaged app; it can override the generated `app.classpath` and cause `ClassNotFoundException: com.yimark.Main`

## Configuration Files
- `~/.securedoc/ed25519.pk8` / `ed25519.pub` — Ed25519 keypair (auto-generated)
- `~/.securedoc/binding.key` — 32-byte HMAC key, 0600 perms
- `~/.securedoc/trusted-issuers.json` — pinned issuer public keys
- Protected output manifest is written as `<output-base>.securedoc.json`

## Testing Notes
- Invisible watermark tests: clean, JPEG q90/q75, 50% downscale, unaligned crop, 1.5°/0.7° rotation, local damage — all recover with sync 100%
- Reed-Solomon: 6 tests covering encode/decode/interleave/edge cases
- TrustStore: 7 tests for pinning, lookup, save/load, revoke
- VerificationService: 7 tests covering valid/tampered/untrusted/forged scenarios
- VisibleWatermark: 2 tests covering continuous line-spacing row-step calculation and invalid-value fallback
- MainProvider: 1 test covering BouncyCastle provider availability
- Mosaic brush: undo/redo/clear all functionality tested

## Known Issues / TODO
1. Byte-level SHA-256 verification flags any re-save/re-compress as "suspicious" even if watermark survives
2. Rotation/scale search uses discrete candidate grid (misses off-grid angles)
3. Watermark extraction result not cross-checked against manifest fields
4. Private keys stored in filesystem, not Keychain / Credential Manager
5. Windows code signing / macOS notarization not configured
6. Mosaic brush preview flickering fixed (block size slider now only updates overlay)
7. Windows app icon requires `src/main/resources/yi-mark.ico`; if absent, `build-app.sh` omits `--icon` for Windows
8. macOS icon cache may briefly show the old app icon after replacing `/Applications/yi-mark.app`

## Security Model
- **Threat model**: Tampering detection, source authentication, raising watermark removal cost
- **Not DRM**: Cannot prevent photos, screenshots, AI redraws
- **Trust anchor**: User must manually pin issuer keys; first use requires "Trust Current Issuer" click
- **Signature scope**: `documentId|protectedSha256|watermarkToken` only; `sourceSha256` only bound via HMAC token