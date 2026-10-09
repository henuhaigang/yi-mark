package com.yimark.verify;

import com.yimark.crypto.Digests;
import com.yimark.crypto.Ed25519Keys;
import com.yimark.manifest.Manifest;
import com.yimark.protect.ProtectionService;
import com.yimark.trust.TrustStore;
import com.yimark.watermark.DctWatermark;
import com.yimark.watermark.Extraction;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Verifies a protected image against its manifest.
 *
 * <p>The manifest's own public key is never trusted on its own: an attacker can
 * re-sign a forged image with a key they generated. The key must resolve against
 * the pinned {@link TrustStore}, otherwise the signature proves nothing.
 */
public final class VerificationService {

    private final TrustStore trustStore;

    public VerificationService(TrustStore trustStore) {
        this.trustStore = trustStore;
    }

    public Report verify(Path imagePath) throws IOException {
        Path manifestPath = ProtectionService.manifestPath(imagePath);
        if (!Files.exists(manifestPath)) {
            return Report.failure("no manifest at " + manifestPath);
        }
        Manifest manifest;
        try {
            manifest = Manifest.parse(Files.readString(manifestPath));
        } catch (IllegalArgumentException e) {
            return Report.failure("manifest unreadable: " + e.getMessage());
        }

        byte[] bytes = Files.readAllBytes(imagePath);
        String actualHash = Digests.sha256Hex(bytes);
        boolean hashMatch = manifest.protectedSha256().equals(actualHash);

        TrustStore.Lookup lookup = trustStore.lookup(manifest.publicKeyBase64());
        if (!lookup.trusted()) {
            return Report.untrusted(manifest, actualHash, hashMatch,
                    "manifest public key is not in the trusted issuer store (fingerprint "
                            + lookup.fingerprint() + ")");
        }

        boolean signatureValid;
        String signatureError = null;
        try {
            Ed25519Keys issuer = Ed25519Keys.fromBase64(lookup.pinnedKeyBase64());
            signatureValid = issuer.verify(manifest.signingPayload(),
                    Base64.getDecoder().decode(manifest.signatureBase64()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            signatureValid = false;
            signatureError = e.getMessage();
        }

        Extraction extraction = null;
        String extractionNote = null;
        try {
            BufferedImage image = javax.imageio.ImageIO.read(imagePath.toFile());
            extraction = DctWatermark.extract(image);
        } catch (IOException e) {
            extractionNote = "image unreadable: " + e.getMessage();
        }

        return new Report(manifest, actualHash, hashMatch, signatureValid, signatureError,
                lookup, extraction, extractionNote);
    }

    public record Report(Manifest manifest, String actualSha256, boolean hashMatch,
                        boolean signatureValid, String signatureError,
                        TrustStore.Lookup issuer, Extraction extraction, String note) {

        static Report failure(String reason) {
            return new Report(null, null, false, false, null, null, null, reason);
        }

        static Report untrusted(Manifest manifest, String actualSha256, boolean hashMatch,
                               String reason) {
            return new Report(manifest, actualSha256, hashMatch, false, null, null, null, reason);
        }

        public boolean trusted() {
            return issuer != null && issuer.trusted();
        }

        public boolean intact() {
            return hashMatch && signatureValid;
        }

        public String render() {
            StringBuilder sb = new StringBuilder();
            sb.append("SecureDoc Verification\n\n");
            if (manifest == null) {
                return sb.append(note).append('\n').toString();
            }
            sb.append("Document ID : ").append(manifest.documentId()).append('\n');
            sb.append("Issued      : ").append(manifest.createdAt()).append('\n');
            sb.append("Issuer      : ").append(issuer == null ? "unknown" : issuer.label()).append('\n');
            sb.append("Key         : ").append(manifest.publicKeyFingerprint()).append('\n');
            sb.append('\n');
            sb.append("SHA-256     : ").append(hashMatch ? "MATCH" : "MISMATCH").append('\n');
            sb.append("Ed25519     : ").append(signatureValid ? "VALID" : "INVALID");
            if (signatureError != null) {
                sb.append(" (").append(signatureError).append(')');
            }
            sb.append('\n');
            sb.append("Trusted key : ").append(trusted() ? "YES" : "NO").append('\n');
            sb.append('\n');
            if (extraction != null) {
                sb.append("Watermark   : ").append(extraction).append('\n');
            } else if (note != null) {
                sb.append("Watermark   : ").append(note).append('\n');
            }
            sb.append('\n');
            sb.append("Conclusion  : ");
            if (!trusted()) {
                sb.append("UNTRUSTED ISSUER - the manifest key is not pinned, so the signature proves nothing");
            } else if (intact()) {
                sb.append("file matches the signed manifest and the issuer is trusted");
            } else {
                sb.append("file is suspicious");
            }
            sb.append('\n');
            return sb.toString();
        }
    }
}
