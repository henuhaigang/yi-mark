# Architecture

```
com.yimark
├── crypto/           # Ed25519 keys, HMAC binding secret, SHA-256/HMAC digests
├── image/            # VisibleWatermark (staggered tiles), GeometryMarkers, ImageOps (mosaic brush)
├── manifest/         # Signed manifest (V3, JSON, includes Ed25519 sig + HMAC token)
├── protect/          # ProtectionService (orchestrates visible + invisible + mosaic + signing)
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

## Protection Pipeline

1. **Read Source** — PNG/JPG/PDF (PDF: all pages supported)
2. **Visible Watermark** — Staggered tiles with configurable stagger/opacity/size/angle
3. **Watermark Color** — Custom color via JavaFX ColorPicker, default gray (128,128,128)
3. **Mosaic Brush** — User-drawn regions, configurable block size (2-50px), undo/redo (50 steps)
4. **Invisible Watermark** — 8×8 DCT, coeff pair (2,3)/(3,2), strength 18, RS(255,223) ECC
4. **Geometry Markers** — 4-corner markers for sync recovery
5. **Sign Manifest** — Ed25519 over `documentId|protectedSha256|watermarkToken`
5. **Output** — PNG/PDF + `.yimark.json` manifest

## Security Model

- **Threat model**: Tampering detection, source authentication, raising watermark removal cost
- **Not DRM**: Cannot prevent photos, screenshots, AI redraws
- **Trust anchor**: User must manually pin issuer keys; first use requires "Trust Current Issuer" click
- **Signature scope**: `documentId|protectedSha256|watermarkToken` only; `sourceSha256` only bound via HMAC token
- **Mosaic**: User-drawn regions only; coordinates mapped from preview to source image

## Known Limitations

1. Byte-level SHA-256 verification flags any re-save/re-compress as "suspicious" even if watermark survives
2. Rotation/scale search uses discrete candidate grid (misses off-grid angles)
3. Watermark extraction result not cross-checked against manifest fields
4. Private keys stored in filesystem, not Keychain / Credential Manager
5. Windows code signing / macOS notarization not configured

## Future Work

- Verification: "byte mismatch but watermark recoverable" handling
- Cross-check extracted `documentId`/`token` with manifest
- Continuous rotation/scale estimation (phase correlation / keypoint templates)
- Sparser sync templates / redundant layout for crop resistance
- Migrate keys to macOS Keychain / Windows Credential Manager
- DWT alternative to DCT reference implementation
- CI: JPEG, screenshot, crop, rotate, scale attack corpus
- Windows code signing / macOS notarization
