package com.yimark.trust;

import com.yimark.crypto.Ed25519Keys;
import com.yimark.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pinned issuer keys. Verification must resolve the manifest public key against this store:
 * a manifest that carries its own public key proves nothing, because an attacker can
 * re-sign a forged image with a key they generated themselves.
 */
public final class TrustStore {

    public static final Path DEFAULT_PATH =
            Path.of(System.getProperty("user.home"), ".securedoc", "trusted-issuers.json");

    private final Map<String, Entry> byFingerprint = new LinkedHashMap<>();

    public record Entry(String fingerprint, String publicKeyBase64, String label, String addedAt) {}

    public static TrustStore empty() {
        return new TrustStore();
    }

    public static TrustStore load(Path path) throws IOException {
        TrustStore store = new TrustStore();
        if (!Files.exists(path)) {
            return store;
        }
        Map<String, String> fields = Json.parseFlatObject(Files.readString(path));
        String array = fields.getOrDefault("trustedIssuers", "[]");
        for (Map<String, String> entry : parseEntries(array)) {
            store.trustBase64(
                    entry.get("publicKeyBase64"),
                    entry.getOrDefault("label", ""),
                    entry.getOrDefault("addedAt", ""));
        }
        return store;
    }

    /** Flat-object parser cannot read a nested array, so entries are parsed with the array reader. */
    private static List<Map<String, String>> parseEntries(String arrayJson) {
        List<Map<String, String>> out = new ArrayList<>();
        for (String element : Json.parseStringArray(arrayJson)) {
            out.add(Json.parseFlatObject(element));
        }
        return out;
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        List<String> entries = new ArrayList<>();
        for (Entry e : byFingerprint.values()) {
            Map<String, String> f = new LinkedHashMap<>();
            f.put("fingerprint", e.fingerprint());
            f.put("publicKeyBase64", e.publicKeyBase64());
            f.put("label", e.label());
            f.put("addedAt", e.addedAt());
            entries.add(Json.writeObject(f).trim());
        }
        Map<String, String> doc = new LinkedHashMap<>();
        doc.put("version", "1");
        doc.put("trustedIssuers", Json.writeStringArray(entries));
        Files.writeString(path, Json.writeObject(doc));
    }

    public TrustStore trust(PublicKey key, String label, String addedAt) {
        String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
        String fingerprint = Ed25519Keys.fingerprint(key);
        byFingerprint.put(fingerprint, new Entry(fingerprint, encoded, label, addedAt));
        return this;
    }

    public TrustStore trustBase64(String publicKeyBase64, String label, String addedAt) {
        byFingerprint.put(fingerprintOf(publicKeyBase64),
                new Entry(fingerprintOf(publicKeyBase64), publicKeyBase64, label, addedAt));
        return this;
    }

    public boolean remove(String fingerprint) {
        return byFingerprint.remove(fingerprint) != null;
    }

    public List<Entry> entries() {
        return new ArrayList<>(byFingerprint.values());
    }

    public boolean isEmpty() {
        return byFingerprint.isEmpty();
    }

    public int size() {
        return byFingerprint.size();
    }

    /** Resolves a manifest-supplied public key against the pinned set. */
    public Lookup lookup(String manifestPublicKeyBase64) {
        if (manifestPublicKeyBase64 == null || manifestPublicKeyBase64.isBlank()) {
            return Lookup.NO_KEY;
        }
        String fingerprint = fingerprintOf(manifestPublicKeyBase64);
        Entry entry = byFingerprint.get(fingerprint);
        if (entry == null) {
            return new Lookup(fingerprint, false, null, null);
        }
        boolean identical = entry.publicKeyBase64().equals(manifestPublicKeyBase64);
        return new Lookup(fingerprint, true, entry, identical ? entry.publicKeyBase64() : null);
    }

    private static String fingerprintOf(String publicKeyBase64) {
        byte[] raw = Base64.getDecoder().decode(publicKeyBase64);
        return com.yimark.crypto.Digests.toHex(com.yimark.crypto.Digests.sha256(raw)).substring(0, 32);
    }

    public record Lookup(String fingerprint, boolean trusted, Entry entry, String pinnedKeyBase64) {

        static final Lookup NO_KEY = new Lookup("", false, null, null);

        public String label() {
            return entry == null ? "" : entry.label();
        }
    }

    /** Convenience for tests and the UI. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : byFingerprint.values()) {
            sb.append(e.fingerprint()).append("  ").append(e.label()).append('\n');
        }
        return sb.toString();
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
