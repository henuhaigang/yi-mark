package com.yimark.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Ed25519 signing keys with a development file-backed store under the SecureDoc home directory. */
public final class Ed25519Keys {

    private final PublicKey publicKey;
    private final PrivateKey privateKey;

    private Ed25519Keys(PublicKey publicKey, PrivateKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public static Path privateKeyPath(Path home) {
        return home.resolve("ed25519.pk8");
    }

    public static Path publicKeyPath(Path home) {
        return home.resolve("ed25519.pub");
    }

    public static Ed25519Keys loadOrCreate(Path home) throws IOException, GeneralSecurityException {
        Files.createDirectories(home);
        Path priv = privateKeyPath(home);
        Path pub = publicKeyPath(home);
        KeyFactory factory = KeyFactory.getInstance("Ed25519");
        if (Files.exists(priv) && Files.exists(pub)) {
            PublicKey pk = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub)));
            PrivateKey sk = factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv)));
            return new Ed25519Keys(pk, sk);
        }
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.write(priv, pair.getPrivate().getEncoded());
        Files.write(pub, pair.getPublic().getEncoded());
        restrict(priv);
        return new Ed25519Keys(pair.getPublic(), pair.getPrivate());
    }

    public static Ed25519Keys publicOnly(PublicKey publicKey) {
        return new Ed25519Keys(publicKey, null);
    }

    public static Ed25519Keys fromBase64(String publicKeyBase64) throws GeneralSecurityException {
        byte[] raw = Base64.getDecoder().decode(publicKeyBase64);
        return publicOnly(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(raw)));
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    public boolean canSign() {
        return privateKey != null;
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** Stable identifier of the signing key: first 32 hex chars of SHA-256 over its X.509 encoding. */
    public String fingerprint() {
        return fingerprint(publicKey);
    }

    public static String fingerprint(PublicKey key) {
        return Digests.toHex(Digests.sha256(key.getEncoded())).substring(0, 32);
    }

    public byte[] sign(byte[] data) throws GeneralSecurityException {
        if (privateKey == null) {
            throw new IllegalStateException("no private key available");
        }
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    public boolean verify(byte[] data, byte[] signatureBytes) throws GeneralSecurityException {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(signatureBytes);
    }

    private static void restrict(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX filesystem
        }
    }
}
