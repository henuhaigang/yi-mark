# Architecture

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

## Protection Pipeline

1. **Read Source** — PNG/JPG/PDF (PDF: all pages supported)
2. **Visible Watermark** — Staggered tiles with configurable stagger, opacity, size, angle, line spacing, and color
3. **Line Spacing** — Continuous vertical row-step multiplier (`1.0-8.0`, default `2.4`)
4. **Watermark Color** — Custom color via JavaFX ColorPicker, default gray (128,128,128)
5. **Mosaic Brush** — User-drawn regions, configurable block size (2-50px), undo/redo (50 steps)
6. **Invisible Watermark** — 8×8 DCT, coeff pair (2,3)/(3,2), strength 18, RS(255,223) ECC
7. **Geometry Markers** — 4-corner markers for sync recovery
8. **Sign Manifest** — Ed25519 over `documentId|protectedSha256|watermarkToken`
9. **Output** — PNG/PDF + `<output-base>.securedoc.json` manifest

## Security Model

- **Threat model**: Tampering detection, source authentication, raising watermark removal cost
- **Not DRM**: Cannot prevent photos, screenshots, AI redraws
- **Trust anchor**: User must manually pin issuer keys; first use requires "Trust Current Issuer" click
- **Signature scope**: `documentId|protectedSha256|watermarkToken` only; `sourceSha256` only bound via HMAC token
- **Mosaic**: User-drawn regions only; coordinates mapped from preview to source image
- **Local state**: Keys, binding secret, and trust store live under `~/.securedoc/`

## Known Limitations

1. Byte-level SHA-256 verification flags any re-save/re-compress as "suspicious" even if watermark survives
2. Rotation/scale search uses discrete candidate grid (misses off-grid angles)
3. Watermark extraction result not cross-checked against manifest fields
4. Private keys stored in filesystem, not Keychain / Credential Manager
5. Windows code signing / macOS notarization not configured

## CI / Packaging

- CI runs unit tests on Ubuntu, macOS, and Windows.
- CI packages macOS `.app` + `.dmg`, Windows app-image + `.exe` + `.msi`, and Linux app-image + tar.gz.
- Release workflow publishes artifacts and `SHA256SUMS.txt` on `v*` tags.
- macOS DMG generation uses `hdiutil create` to avoid `jpackage --type dmg` attach failures.

## Future Work

- Verification: "byte mismatch but watermark recoverable" handling
- Cross-check extracted `documentId`/`token` with manifest
- Continuous rotation/scale estimation (phase correlation / keypoint templates)
- Sparser sync templates / redundant layout for crop resistance
- Migrate keys to macOS Keychain / Windows Credential Manager
- DWT alternative to DCT reference implementation
- CI: JPEG, screenshot, crop, rotate, scale attack corpus
- Windows code signing / macOS notarization
