# yi-mark Agent Instructions

## Project Overview
- **Name**: yi-mark (formerly SecureDoc-Java)
- **Type**: Cross-platform document watermark protection tool (Java 17 + JavaFX)
- **Core features**: Visible tiled watermark + invisible DCT frequency-domain watermark + Ed25519 signed manifest
- **Platforms**: macOS (app + dmg), Windows (exe + msi), Linux (runnable JAR)
- **Language**: Java 17, Maven, JavaFX 17.0.6

## Architecture

```
com.yimark
├── crypto/           # Ed25519 keys, HMAC binding secret, SHA-256/HMAC digests
├── image/            # VisibleWatermark (staggered tiles), GeometryMarkers, ImageOps
├── manifest/         # Signed manifest (V3, JSON, includes Ed25519 sig + HMAC token)
├── protect/          # ProtectionService (orchestrates visible + invisible + signing)
├── trust/            # TrustStore (pinned issuer keys, ~/.yimark/trusted-issuers.json)
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
- **Trust model**: Manifest carries its own public key, but verification ONLY trusts keys pinned in `~/.yimark/trusted-issuers.json`
- **No external JSON lib**: Custom flat-object parser in `Json.java`

## Build & Run

```bash
# Dev run
mvn clean javafx:run

# Native packaging (macOS)
./build-app.sh mac   # produces target/dist/yi-mark.app + yi-mark-1.0.0.dmg

# Native packaging (Windows) - run on Windows
./build-app.sh win   # produces target/dist/yi-mark-1.0.0.exe + .msi

# Tests
mvn test   # 28 tests (DctWatermark 9, ReedSolomon 6, TrustStore 7, VerificationService 6)
```

## Module System
- `module-info.java` declares `module yi.mark` with `requires javafx.controls/javafx.graphics/javafx.base/java.desktop`
- All internal packages `opens ... to javafx.graphics` for JavaFX reflection
- `jpackage` builds native images using `--module-path` with compiled classes + JavaFX jars

## Configuration Files
- `~/.yimark/ed25519.pk8` / `ed25519.pub` — Ed25519 keypair (auto-generated)
- `~/.yimark/binding.key` — 32-byte HMAC key, 0600 perms
- `~/.yimark/trusted-issuers.json` — pinned issuer public keys

## Testing Notes
- Invisible watermark tests: clean, JPEG q90/q75, 50% downscale, unaligned crop, 1.5°/0.7° rotation, local damage — all recover with sync 100%
- Reed-Solomon: 6 tests covering encode/decode/interleave/edge cases
- TrustStore: 7 tests for pinning, lookup, save/load, revoke
- VerificationService: 6 tests covering valid/tampered/untrusted/forged scenarios

## Known Issues / TODO
1. Byte-level SHA-256 verification flags any re-save/re-compress as "suspicious" even if watermark survives
2. Rotation/scale search uses discrete candidate grid (misses off-grid angles)
3. Watermark extraction result not cross-checked against manifest fields
4. Private keys stored in filesystem, not Keychain / Credential Manager
5. Windows code signing / macOS notarization not configured

## Security Model
- **Threat model**: Tampering detection, source authentication, raising watermark removal cost
- **Not DRM**: Cannot prevent photos, screenshots, AI redraws
- **Trust anchor**: User must manually pin issuer keys; first use requires "Trust Current Issuer" click
- **Signature scope**: `documentId|protectedSha256|watermarkToken` only; `sourceSha256` only bound via HMAC token