# macOS build

```bash
java -version
mvn -version
mvn clean javafx:run
```

Create `.app`:

```bash
mvn clean package
jpackage --name SecureDoc --input target --main-jar securedoc-java-1.0.0.jar --main-class com.securedoc.Main --type app-image
```

The current signing key is stored under `~/.securedoc` as a development fallback.
Before production, replace this with the macOS Keychain adapter (`security` CLI or JNI/JNA).
