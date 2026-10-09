# Architecture

JavaFX UI
 -> Protection pipeline
 -> SHA-256 / HMAC / Ed25519
 -> visible dynamic watermark
 -> 8x8 DCT watermark
 -> geometry markers
 -> PNG + signed `.securedoc.json`

## Why not claim impossible protection?

macOS does not expose a universal reliable screenshot event to ordinary desktop applications.
Another phone can always photograph a screen. AI can also regenerate a visually equivalent image.

Therefore SecureDoc uses defense in depth rather than a false promise:
- visible dynamic watermark
- cryptographic identity
- robust frequency-domain watermark
- geometry synchronization
- tamper analysis
- signed verification manifest

## Production hardening

Add Reed-Solomon/BCH, coefficient voting, affine/perspective recovery, DWT,
JPEG/screenshot/crop/rotate attack corpus, local tamper heatmap, PDF-native rendering,
online revocation and a public verifier.
