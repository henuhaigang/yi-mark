package com.yimark.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Set;

/**
 * Per-installation secret used to bind the watermark token to this issuer.
 * Generated once as 32 random bytes with owner-only permissions.
 */
public final class BindingSecret {

    private static final int LENGTH = 32;
    private final byte[] key;

    private BindingSecret(byte[] key) {
        this.key = key.clone();
    }

    public static Path defaultPath(Path home) {
        return home.resolve("binding.key");
    }

    public static BindingSecret loadOrCreate(Path path) throws IOException {
        if (Files.exists(path)) {
            byte[] stored = Files.readAllBytes(path);
            if (stored.length != LENGTH) {
                throw new IOException("binding secret has wrong length: " + stored.length);
            }
            return new BindingSecret(stored);
        }
        Files.createDirectories(path.getParent());
        byte[] fresh = new byte[LENGTH];
        new SecureRandom().nextBytes(fresh);
        writeOwnerOnly(path, fresh);
        return new BindingSecret(fresh);
    }

    public static BindingSecret of(byte[] key) {
        if (key.length < 16) {
            throw new IllegalArgumentException("binding secret too short");
        }
        return new BindingSecret(key);
    }

    public byte[] key() {
        return key.clone();
    }

    public String fingerprint() {
        return Digests.toHex(Digests.sha256(key)).substring(0, 16);
    }

    private static void writeOwnerOnly(Path path, byte[] data) throws IOException {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.write(path, data, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            Files.write(path, data);
        }
    }
}
