# Build & Run

## Development

```bash
mvn clean javafx:run
```

## Tests

```bash
mvn test
```

Current test suite: 32 tests.

| Test class | Tests |
|------------|------:|
| `DctWatermarkTest` | 9 |
| `ReedSolomonTest` | 6 |
| `TrustStoreTest` | 7 |
| `VerificationServiceTest` | 7 |
| `VisibleWatermarkTest` | 2 |
| `MainProviderTest` | 1 |

## Native Packaging

### macOS

```bash
./build-app.sh mac
```

Produces:

- `target/dist/yi-mark.app`
- `target/dist/yi-mark-<version>.dmg`

The DMG is generated with `hdiutil create` and verified with `hdiutil verify`.

### Windows

```bash
./build-app.sh win
```

Produces:

- `target/dist/yi-mark/`
- `target/dist/yi-mark-<version>.exe`
- `target/dist/yi-mark-<version>.msi`

MSI generation requires WiX Toolset. The GitHub Actions workflow installs WiX before packaging.

### Linux

```bash
./build-app.sh linux
```

Produces:

- `target/dist/yi-mark/`

### Auto

```bash
./build-app.sh all
```

Builds for the current host operating system.

## Build Script Behavior

`build-app.sh` performs the following steps:

1. Reads the version from `pom.xml`, unless `VERSION` is set.
2. Runs `mvn clean package -DskipTests`.
3. Copies runtime dependencies into `target/applib`.
4. Creates a jlink runtime image with JavaFX and `jdk.crypto.ec`.
5. Builds a `jpackage` app-image using `--input`, `--main-jar`, and the generated runtime image.
6. Generates platform installers:
   - macOS: DMG via `hdiutil`
   - Windows: EXE and MSI via `jpackage`
   - Linux: app-image only

## Version Management

Version is read from `pom.xml`. Override with the `VERSION` environment variable:

```bash
VERSION=2.0.0 ./build-app.sh mac
```

## GitHub Actions

### CI

`.github/workflows/ci.yml` runs on:

- `main` branch pushes
- pull requests
- manual dispatch

Jobs:

- `test`: `mvn test` on Ubuntu, macOS, and Windows
- `package-macos`: builds `.app` + `.dmg`
- `package-windows`: builds app-image + `.exe` + `.msi`
- `package-linux`: builds app-image

### Release

`.github/workflows/release.yml` runs on `v*` tags and publishes:

- macOS DMG
- Windows EXE/MSI
- Linux app-image
- `SHA256SUMS.txt`

Use a tag that matches the `pom.xml` version, for example `v1.0.9` for `1.0.9`.
