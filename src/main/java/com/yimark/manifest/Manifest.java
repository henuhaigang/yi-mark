package com.yimark.manifest;

import com.yimark.util.Json;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Signed issuance manifest stored next to the protected image as {@code <name>.securedoc.json}. */
public final class Manifest {

    public static final int VERSION = 3;
    public static final String ALGORITHM_SUITE =
            "SHA-256/HMAC-SHA256/Ed25519/8x8-DCT-COEFF-PAIR/CRC32/RS(255,223)";

    private final String documentId;
    private final String createdAt;
    private final String sourceSha256;
    private final String protectedSha256;
    private final String watermarkToken;
    private final String signatureBase64;
    private final String publicKeyBase64;
    private final String publicKeyFingerprint;

    public Manifest(String documentId, String createdAt, String sourceSha256, String protectedSha256,
                    String watermarkToken, String signatureBase64, String publicKeyBase64,
                    String publicKeyFingerprint) {
        this.documentId = documentId;
        this.createdAt = createdAt;
        this.sourceSha256 = sourceSha256;
        this.protectedSha256 = protectedSha256;
        this.watermarkToken = watermarkToken;
        this.signatureBase64 = signatureBase64;
        this.publicKeyBase64 = publicKeyBase64;
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    /** Exact bytes covered by the Ed25519 signature. */
    public byte[] signingPayload() {
        return (documentId + "|" + protectedSha256 + "|" + watermarkToken)
                .getBytes(StandardCharsets.UTF_8);
    }

    public String toJson() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("version", String.valueOf(VERSION));
        fields.put("documentId", documentId);
        fields.put("createdAt", createdAt);
        fields.put("sourceSha256", sourceSha256);
        fields.put("protectedSha256", protectedSha256);
        fields.put("watermarkToken", watermarkToken);
        fields.put("signatureBase64", signatureBase64);
        fields.put("publicKeyBase64", publicKeyBase64);
        fields.put("publicKeyFingerprint", publicKeyFingerprint);
        fields.put("algorithmSuite", ALGORITHM_SUITE);
        return Json.writeObject(fields);
    }

    public static Manifest parse(String json) {
        Map<String, String> f = Json.parseFlatObject(json);
        return new Manifest(
                require(f, "documentId"),
                f.getOrDefault("createdAt", ""),
                require(f, "sourceSha256"),
                require(f, "protectedSha256"),
                require(f, "watermarkToken"),
                require(f, "signatureBase64"),
                require(f, "publicKeyBase64"),
                f.getOrDefault("publicKeyFingerprint", ""));
    }

    private static String require(Map<String, String> f, String key) {
        String v = f.get(key);
        if (v == null) {
            throw new IllegalArgumentException("manifest missing field: " + key);
        }
        return v;
    }

    public String documentId() {
        return documentId;
    }

    public String createdAt() {
        return createdAt;
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    public String protectedSha256() {
        return protectedSha256;
    }

    public String watermarkToken() {
        return watermarkToken;
    }

    public String signatureBase64() {
        return signatureBase64;
    }

    public String publicKeyBase64() {
        return publicKeyBase64;
    }

    public String publicKeyFingerprint() {
        return publicKeyFingerprint;
    }
}
