# Build & Run

## Development
```bash
mvn clean javafx:run
```

## Native Packaging

### macOS (generates .app + .dmg)
```bash
./build-app.sh mac
```

### Windows (generates .exe + .msi) - run on Windows
```bash
./build-app.sh win
```

The build script automatically:
- Downloads JavaFX native libraries
- Creates modular runtime image with jlink
- Generates native installer via jpackage

## Version Management
Version is read from `pom.xml`. Override with `VERSION` env var:
```bash
VERSION=2.0.0 ./build-app.sh mac
```
