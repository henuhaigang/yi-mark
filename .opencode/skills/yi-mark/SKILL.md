---
name: yi-mark
description: Use when working in the yi-mark JavaFX watermark project, especially for build-app.sh, GitHub Actions workflows, README/USAGE/docs updates, packaging, tests, visible watermark line spacing, manifests, or macOS/Windows/Linux release artifacts.
---

# yi-mark Project Skill

Use this skill for tasks in `/Volumes/Seagate/workspace/code/yi-mark`.

## Project Context

- Java 17 + JavaFX + Maven desktop app.
- Main class: `com.yimark.Main`.
- Visible watermark parameters include opacity, size, line spacing, angle, stagger, and color.
- Line spacing is a continuous multiplier:
  - UI range: `1.0` to `8.0`
  - default: `VisibleWatermark.DEFAULT_LINE_SPACING_RATIO = 2.4`
  - row step: `max(24, round(fontSize * lineSpacingRatio))`
  - invalid/non-finite values fall back to the default
- Invisible watermark uses 8×8 DCT coefficient pairs, CRC32 sync framing, and RS(255,223).
- Manifest output is `<output-base>.securedoc.json`, not `.yimark.json`.
- Local key/trust state lives under `~/.securedoc/`.

## Common Commands

```bash
mvn test
mvn clean javafx:run
./build-app.sh mac
./build-app.sh win
./build-app.sh linux
./build-app.sh all
```

Expected test count: 32.

## Packaging Rules

- Prefer `build-app.sh` for native packaging.
- macOS:
  - app bundle: `target/dist/yi-mark.app`
  - DMG: `target/dist/yi-mark-<version>.dmg`
  - DMG should be created with `hdiutil create`, then verified with `hdiutil verify`
  - app icon source: `src/main/resources/yi-mark.icns`
- Windows:
  - app-image: `target/dist/yi-mark/`
  - installers: `target/dist/yi-mark-<version>.exe` and `.msi`
  - MSI requires WiX Toolset
  - Windows icon should be `src/main/resources/yi-mark.ico` if present
- Linux:
  - app-image: `target/dist/yi-mark/`
  - release archive: `target/dist/yi-mark-<version>-linux.tar.gz`

## GitHub Actions

- CI workflow: `.github/workflows/ci.yml`
  - runs tests on Ubuntu, macOS, and Windows
  - packages macOS, Windows, and Linux artifacts
- Release workflow: `.github/workflows/release.yml`
  - triggered by `v*` tags
  - downloads artifacts
  - generates `SHA256SUMS.txt`
  - publishes a GitHub Release

When changing packaging, update both the workflow and docs if artifact names, paths, prerequisites, or platform behavior change.

## Documentation Sync Checklist

When changing user-visible behavior or build behavior, update:

- `README.md`
- `USAGE.md`
- `agents.md`
- `docs/BUILD.md`
- `docs/ARCHITECTURE.md`
- `.opencode/skills/yi-mark/SKILL.md`

Keep these facts consistent:

- line spacing range/default
- test count and test class breakdown
- manifest extension
- local config directory
- CI/Release triggers and artifacts
- packaging prerequisites and output paths

## Known Pitfalls

- Do not pass `-cp` through `jpackage --java-options` for the packaged app; it can override the generated launcher classpath and cause `ClassNotFoundException: com.yimark.Main`.
- `jpackage --type dmg` may fail with `hdiutil attach permission denied`; use manual `hdiutil create`.
- macOS may show a stale app icon after replacing `/Applications/yi-mark.app`; refresh Finder or wait for the icon cache to update.
- Windows packaging should not reference the macOS `.icns` file as the installer icon.
