package com.yimark.protect;

import com.yimark.crypto.BindingSecret;
import com.yimark.crypto.Digests;
import com.yimark.crypto.Ed25519Keys;
import com.yimark.image.GeometryMarkers;
import com.yimark.image.ImageOps;
import com.yimark.image.PdfUtils;
import com.yimark.image.VisibleWatermark;
import com.yimark.manifest.Manifest;
import com.yimark.watermark.DctWatermark;
import com.yimark.image.ImageOps;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
                          double opacity, int fontSize, double angle, double staggerRatio,
                          double lineSpacingRatio,
                          boolean enableMosaic, int mosaicSize, List<Rectangle2D.Double> mosaicRegions,
                          Color watermarkColor)
            throws IOException, GeneralSecurityException {
        byte[] raw = Files.readAllBytes(source);
        String sourceHash = Digests.sha256Hex(raw);
        UUID documentId = UUID.randomUUID();
        UUID session = UUID.randomUUID();

        byte[] tokenInput = (documentId + "|" + sourceHash + "|" + purpose + "|" + session)
                .getBytes(StandardCharsets.UTF_8);
        String token = Digests.toHex(Digests.hmacSha256(tokenInput, bindingSecret.key()));

        List<BufferedImage> images = readAllImages(source);
        List<BufferedImage> processedImages = new ArrayList<>();

        for (BufferedImage image : images) {
            BufferedImage visible = VisibleWatermark.apply(image, issuer, purpose,
                    "", opacity, fontSize, angle, staggerRatio, lineSpacingRatio, watermarkColor);
            
            // Apply mosaic if enabled
            if (enableMosaic && mosaicRegions != null && !mosaicRegions.isEmpty()) {
                for (Rectangle2D.Double rect : mosaicRegions) {
                    // Clamp to image bounds
                    int x = Math.max(0, (int) Math.round(rect.x));
                    int y = Math.max(0, (int) Math.round(rect.y));
                    int w = Math.min((int) Math.round(rect.width), visible.getWidth() - x);
                    int h = Math.min((int) Math.round(rect.height), visible.getHeight() - y);
                    if (w > 0 && h > 0) {
                        visible = ImageOps.applyMosaic(visible, x, y, w, h, mosaicSize);
                    }
                }
            } else if (enableMosaic) {
                // Fallback: full image mosaic
                visible = ImageOps.applyMosaic(visible, 0, 0, visible.getWidth(), visible.getHeight(), mosaicSize);
            }
            
            BufferedImage embedded = DctWatermark.embed(visible,
                    ("SD3|" + documentId + "|" + token + "|" + sourceHash)
                            .getBytes(StandardCharsets.UTF_8));
            GeometryMarkers.draw(embedded);
            processedImages.add(embedded);
        }

        writeImages(processedImages, output);

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

    private static List<BufferedImage> readAllImages(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            return PdfUtils.readAllPages(path);
        }
        return List.of(javax.imageio.ImageIO.read(path.toFile()));
    }

    private static void writeImages(List<BufferedImage> images, Path output) throws IOException {
        String name = output.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            PdfUtils.writePdf(images, output);
        } else {
            // For non-PDF output, only write the first image as PNG
            javax.imageio.ImageIO.write(images.get(0), "PNG", output.toFile());
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
