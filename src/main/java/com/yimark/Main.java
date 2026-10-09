package com.yimark;

import com.yimark.crypto.Ed25519Keys;
import com.yimark.image.VisibleWatermark;
import com.yimark.protect.ProtectionService;
import com.yimark.trust.TrustStore;
import com.yimark.verify.VerificationService;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class Main extends Application {

    private static final Path HOME = Paths.get(System.getProperty("user.home"), ".securedoc");
    private static final double PREVIEW_MAX_WIDTH = 1100;

    private Path source;
    private Path output;
    private ProtectionService protection;
    private TrustStore trustStore;
    private VerificationService verification;

    private final TextArea log = new TextArea();
    private final TextField issuer = new TextField("SecureDoc");
    private final TextField purpose = new TextField("仅供指定用途使用");
    private final Slider opacity = new Slider(0.08, 0.45, 0.20);
    private final Slider watermarkSize = new Slider(0.01, 3.0, 1.0);
    private final Slider watermarkAngle = new Slider(-60, 60, -24);
    private final ImageView preview = new ImageView();

    private BufferedImage sourceImage;

    @Override
    public void start(Stage stage) {
        try {
            protection = new ProtectionService(HOME);
            trustStore = TrustStore.load(TrustStore.DEFAULT_PATH);
            verification = new VerificationService(trustStore);
        } catch (IOException | GeneralSecurityException e) {
            log.setText("初始化失败: " + e);
        }

        Button in = new Button("选择原图");
        Button out = new Button("选择输出");
        Button protect = new Button("生成保护文件");
        Button verify = new Button("验证文件");
        Button trust = new Button("信任当前签发方");
        in.setOnAction(e -> chooseInput(stage));
        out.setOnAction(e -> chooseOutput(stage));
        protect.setOnAction(e -> protect());
        verify.setOnAction(e -> verify());
        trust.setOnAction(e -> trustCurrentIssuer());
        HBox buttons = new HBox(10, in, out, protect, verify, trust);

        HBox settings = new HBox(10,
                new Label("使用方"), issuer,
                new Label("用途"), purpose,
                new Label("透明度"), opacity,
                new Label("大小"), watermarkSize,
                new Label("角度°"), watermarkAngle);
        settings.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        preview.setFitWidth(PREVIEW_MAX_WIDTH);
        ScrollPane previewPane = new ScrollPane(preview);
        previewPane.setPannable(true);
        previewPane.setStyle("-fx-background-color:#262626;");
        previewPane.setFitToWidth(true);

        issuer.textProperty().addListener((o, a, b) -> updatePreview());
        purpose.textProperty().addListener((o, a, b) -> updatePreview());
        opacity.valueProperty().addListener((o, a, b) -> updatePreview());
        watermarkSize.valueProperty().addListener((o, a, b) -> updatePreview());
        watermarkAngle.valueProperty().addListener((o, a, b) -> updatePreview());

        log.setEditable(false);
        VBox box = new VBox(12, buttons, settings, previewPane, log);
        box.setPadding(new javafx.geometry.Insets(18));
        VBox.setVgrow(previewPane, Priority.ALWAYS);
        VBox.setVgrow(log, Priority.SOMETIMES);

        stage.setTitle("SecureDoc - Java");
        stage.setScene(new Scene(box, 1200, 900));
        stage.show();
        updatePreview();
    }

    /** Re-renders the visible watermark on the preview image with the current settings. */
    private void updatePreview() {
        if (sourceImage == null) {
            preview.setImage(null);
            return;
        }
        BufferedImage scaled = fitWidth(sourceImage, PREVIEW_MAX_WIDTH);
        int fontSize = fontSizeFor(scaled.getWidth());
        BufferedImage watermarked = VisibleWatermark.apply(scaled, issuer.getText(),
                purpose.getText(), "", opacity.getValue(), fontSize, watermarkAngle.getValue());
        preview.setImage(toFxImage(watermarked));
    }

    private int fontSizeFor(int imageWidth) {
        return (int) Math.max(1, Math.round(
                VisibleWatermark.baseFontSize(imageWidth) * watermarkSize.getValue()));
    }

    private void chooseInput(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file != null) {
            source = file.toPath();
            try {
                sourceImage = javax.imageio.ImageIO.read(file);
            } catch (IOException e) {
                sourceImage = null;
            }
            log.setText("原图: " + source + (sourceImage == null ? " (无法读取)" : "")
                    + "  宽 " + (sourceImage == null ? "?" : sourceImage.getWidth()) + " px");
            updatePreview();
        }
    }

    private void chooseOutput(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("secured-document.png");
        java.io.File file = chooser.showSaveDialog(stage);
        if (file != null) {
            output = file.toPath();
        }
    }

    private void protect() {
        if (source == null || output == null) {
            log.setText("请先选择原图和输出文件");
            return;
        }
        try {
            int fontSize = fontSizeFor(sourceImage == null ? 800 : sourceImage.getWidth());
            ProtectionService.Result result = protection.protect(
                    source, output, issuer.getText(), purpose.getText(), opacity.getValue(),
                    fontSize, watermarkAngle.getValue());
            // 直接展示生成的成品图（不再二次加水印），保留 sourceImage 供后续预览
            BufferedImage finalImage = javax.imageio.ImageIO.read(result.image().toFile());
            if (finalImage != null) {
                preview.setImage(toFxImage(fitWidth(finalImage, PREVIEW_MAX_WIDTH)));
            }
            log.setText("保护成功\n"
                    + "PNG: " + result.image() + "\n"
                    + "Manifest: " + result.manifest() + "\n"
                    + "Document ID: " + result.data().documentId() + "\n"
                    + "Session: " + result.session() + "\n"
                    + "Issuer key: " + result.data().publicKeyFingerprint() + "\n"
                    + "Binding secret: " + protection.bindingSecret().fingerprint() + "\n\n"
                    + "下一步: 点「信任当前签发方」把本机签发公钥加入信任库，\n"
                    + "之后「验证文件」才会认可这个清单的签名。");
        } catch (Exception e) {
            log.setText("失败: " + e);
        }
    }

    private void verify() {
        if (output == null) {
            log.setText("请先选择输出图片作为待验证文件");
            return;
        }
        try {
            log.setText(verification.verify(output).render());
        } catch (Exception e) {
            log.setText("验证失败: " + e);
        }
    }

    private void trustCurrentIssuer() {
        if (output == null) {
            log.setText("请先选择输出图片");
            return;
        }
        try {
            Path manifestPath = ProtectionService.manifestPath(output);
            if (!Files.exists(manifestPath)) {
                log.setText("找不到清单: " + manifestPath);
                return;
            }
            com.yimark.manifest.Manifest manifest =
                    com.yimark.manifest.Manifest.parse(Files.readString(manifestPath));
            Ed25519Keys keys = Ed25519Keys.fromBase64(manifest.publicKeyBase64());
            trustStore.trust(keys.publicKey(),
                    issuer.getText() + " @ " + Instant.now(), Instant.now().toString());
            trustStore.save(TrustStore.DEFAULT_PATH);
            verification = new VerificationService(trustStore);
            log.setText("已信任签发方\n"
                    + "Fingerprint: " + keys.fingerprint() + "\n"
                    + "Label: " + issuer.getText() + "\n"
                    + "Trust store: " + TrustStore.DEFAULT_PATH + "\n"
                    + "共 " + trustStore.size() + " 个受信任签发方");
        } catch (Exception e) {
            log.setText("信任失败: " + e);
        }
    }

    private static BufferedImage fitWidth(BufferedImage source, double maxWidth) {
        double scale = Math.min(1.0, maxWidth / source.getWidth());
        int width = (int) Math.max(1, Math.round(source.getWidth() * scale));
        int height = (int) Math.max(1, Math.round(source.getHeight() * scale));
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return out;
    }

    private static WritableImage toFxImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        WritableImage out = new WritableImage(width, height);
        out.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getIntArgbInstance(), argb, 0, width);
        return out;
    }

    public static void main(String[] args) {
        launch(args);
    }
}