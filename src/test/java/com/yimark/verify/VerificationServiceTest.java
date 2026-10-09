package com.yimark.verify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yimark.protect.ProtectionService;
import com.yimark.trust.TrustStore;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificationServiceTest {

    @TempDir
    Path tempDir;

    private static BufferedImage photo(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(11);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double v = 128 + 70 * Math.sin(x / 37.0) * Math.cos(y / 53.0)
                        + 25 * Math.sin((x + y) / 11.0);
                int value = (int) Math.max(0, Math.min(255, v + random.nextInt(21) - 10));
                image.setRGB(x, y, value << 16 | value << 8 | value);
            }
        }
        return image;
    }

    private ProtectionService.Result protect(String homeName, String outputName) throws Exception {
        Path home = tempDir.resolve(homeName);
        Path source = tempDir.resolve(homeName + "-source.png");
        ImageIO.write(photo(1200, 800), "PNG", source.toFile());
        Path output = tempDir.resolve(outputName);
        int fontSize = com.yimark.image.VisibleWatermark.baseFontSize(1200);
        return new ProtectionService(home).protect(source, output, "Acme Bank", "KYC", 0.18,
                fontSize, -24.0, com.yimark.image.VisibleWatermark.DEFAULT_STAGGER_RATIO);
    }

    @Test
    @DisplayName("a genuine file from a pinned issuer verifies as intact and trusted")
    void validFileVerifies() throws Exception {
        ProtectionService.Result result = protect("issuer-home", "id-protected.png");
        TrustStore store = TrustStore.empty()
                .trustBase64(result.data().publicKeyBase64(), "Acme Bank", "2026-01-01");

        VerificationService.Report report = new VerificationService(store).verify(result.image());
        assertTrue(report.hashMatch(), () -> report.render());
        assertTrue(report.signatureValid(), () -> report.render());
        assertTrue(report.trusted(), () -> report.render());
        assertTrue(report.intact(), () -> report.render());
        assertTrue(report.render().contains("matches the signed manifest"));
    }

    @Test
    @DisplayName("a missing manifest is reported instead of throwing")
    void missingManifestIsReported() throws Exception {
        Path image = tempDir.resolve("plain.png");
        ImageIO.write(photo(400, 400), "PNG", image.toFile());
        VerificationService.Report report =
                new VerificationService(TrustStore.empty()).verify(image);
        assertFalse(report.trusted());
        assertTrue(report.render().contains("no manifest"));
    }

    @Test
    @DisplayName("tampering with the image fails the hash even though the signature still verifies")
    void tamperedFileFailsHash() throws Exception {
        ProtectionService.Result result = protect("issuer-home", "id-protected.png");
        byte[] bytes = Files.readAllBytes(result.image());
        bytes[bytes.length / 2] ^= 0x01;
        Files.write(result.image(), bytes);

        TrustStore store = TrustStore.empty()
                .trustBase64(result.data().publicKeyBase64(), "Acme Bank", "2026-01-01");
        VerificationService.Report report = new VerificationService(store).verify(result.image());
        assertFalse(report.hashMatch());
        assertFalse(report.intact());
        assertTrue(report.render().contains("MISMATCH"));
    }

    @Test
    @DisplayName("an unpinned issuer makes the signature prove nothing")
    void unpinnedIssuerIsUntrusted() throws Exception {
        ProtectionService.Result result = protect("issuer-home", "id-protected.png");
        VerificationService.Report report =
                new VerificationService(TrustStore.empty()).verify(result.image());
        assertTrue(report.hashMatch());
        assertFalse(report.trusted());
        assertFalse(report.intact());
        assertTrue(report.render().contains("UNTRUSTED"));
    }

    @Test
    @DisplayName("an attacker who re-signs with their own key cannot impersonate a pinned issuer")
    void attackerKeyCannotImpersonate() throws Exception {
        ProtectionService.Result victim = protect("victim-home", "victim-protected.png");
        ProtectionService.Result attacker = protect("attacker-home", "attacker-protected.png");

        TrustStore store = TrustStore.empty()
                .trustBase64(victim.data().publicKeyBase64(), "Acme Bank", "2026-01-01");
        VerificationService.Report report =
                new VerificationService(store).verify(attacker.image());

        assertFalse(report.trusted());
        assertFalse(report.intact());
        assertTrue(report.render().contains("fingerprint"));
    }

    @Test
    @DisplayName("the recovered watermark carries the manifest document id and token")
    void watermarkPayloadMatchesManifest() throws Exception {
        ProtectionService.Result result = protect("issuer-home", "id-protected.png");
        TrustStore store = TrustStore.empty()
                .trustBase64(result.data().publicKeyBase64(), "Acme Bank", "2026-01-01");

        VerificationService.Report report = new VerificationService(store).verify(result.image());
        assertNotNull(report.extraction());
        assertTrue(report.extraction().recovered, () -> "watermark: " + report.extraction());
        String payload = new String(report.extraction().payload(), StandardCharsets.UTF_8);
        assertTrue(payload.contains(result.data().documentId()), payload);
        assertTrue(payload.contains(result.data().watermarkToken()), payload);
    }
}
