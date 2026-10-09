package com.yimark.protect;

import com.yimark.crypto.BindingSecret;
import com.yimark.crypto.Digests;
import com.yimark.crypto.Ed25519Keys;
import com.yimark.image.GeometryMarkers;
import com.yimark.image.PdfUtils;
import com.yimark.image.VisibleWatermark;
import com.yimark.manifest.Manifest;
import com.yimark.watermark.DctWatermark;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Orchestrates the full protection pipeline: visible watermark, invisible watermark, signing. */
public final class ProtectionService {

    private final Path home;
    private final Ed25519Keys keys;
    private final BindingSecret bindingSecret;

    public ProtectionService(Path home) throws IOException, GeneralSecurityException {
        this.home = home;
        this.keys = Ed25519Keys.loadOrCreate(home);
        this.bindingSecret = BindingSecret.loadOrCreate(BindingSecret.defaultPath(home));
    }

    public Ed25519Keys keys() {
        return keys;
    }

    public BindingSecret bindingSecret() {
        return bindingSecret;
    }

    public Result protect(Path source, Path output, String issuer, String purpose,
                          double opacity, int fontSize, double angle, double staggerRatio)
            throws IOException, GeneralSecurityException {
        byte[] raw = Files.readAllBytes(source);
        String sourceHash = Digests.sha256Hex(raw);
        UUID documentId = UUID.randomUUID();
        UUID session = UUID.randomUUID();

        byte[] tokenInput = (documentId + "|" + sourceHash + "|" + purpose + "|" + session)
                .getBytes(StandardCharsets.UTF_8);
        String token = Digests.toHex(Digests.hmacSha256(tokenInput, bindingSecret.key()));

        BufferedImage image = readImage(source);
        BufferedImage visible = VisibleWatermark.apply(image, issuer, purpose,
                "", opacity, fontSize, angle, staggerRatio);
        BufferedImage embedded = DctWatermark.embed(visible,
                ("SD3|" + documentId + "|" + token + "|" + sourceHash)
                        .getBytes(StandardCharsets.UTF_8));
        GeometryMarkers.draw(embedded);
        
        writeImage(embedded, output);

        String protectedHash = Digests.sha256Hex(Files.readAllBytes(output));
        byte[] signature = keys.sign((documentId + "|" + protectedHash + "|" + token)
                .getBytes(StandardCharsets.UTF_8));
        Manifest manifest = new Manifest(
                documentId.toString(),
                Instant.now().toString(),
                sourceHash,
                protectedHash,
                token,
                Base64.getEncoder().encodeToString(signature),
                keys.publicKeyBase64(),
                keys.fingerprint());
        Path manifestPath = manifestPath(output);
        Files.writeString(manifestPath, manifest.toJson());
        return new Result(output, manifestPath, manifest, session);
    }

    private static BufferedImage readImage(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            return PdfUtils.readFirstPage(path);
        }
        return javax.imageio.ImageIO.read(path.toFile());
    }

    private static void writeImage(BufferedImage image, Path output) throws IOException {
        String name = output.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            PdfUtils.writePdf(image, output);
        } else {
            javax.imageio.ImageIO.write(image, "PNG", output.toFile());
        }
    }

    public static Path manifestPath(Path output) {
        String name = output.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return output.resolveSibling(base + ".securedoc.json");
    }

    public record Result(Path image, Path manifest, Manifest data, UUID session) {}
}
