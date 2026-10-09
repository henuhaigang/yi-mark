package com.yimark;

import com.yimark.crypto.Ed25519Keys;
import com.yimark.image.PdfUtils;
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
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
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
    private final Slider staggerRatio = new Slider(-1.0, 1.0, VisibleWatermark.DEFAULT_STAGGER_RATIO);
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

        // Left sidebar - controls
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

        // Make buttons full width in sidebar
        in.setMaxWidth(Double.MAX_VALUE);
        out.setMaxWidth(Double.MAX_VALUE);
        protect.setMaxWidth(Double.MAX_VALUE);
        verify.setMaxWidth(Double.MAX_VALUE);
        trust.setMaxWidth(Double.MAX_VALUE);
        protect.setDefaultButton(true);

        VBox actionBox = new VBox(8, in, out, protect, verify, trust);
        actionBox.setPadding(new Insets(12));
        actionBox.setAlignment(Pos.TOP_LEFT);

        // Settings panel
        Label issuerLabel = new Label("使用方");
        Label purposeLabel = new Label("用途");
        Label opacityLabel = new Label("透明度");
        Label sizeLabel = new Label("大小");
        Label angleLabel = new Label("角度°");
        Label staggerLabel = new Label("错位");

        issuer.setPrefWidth(180);
        purpose.setPrefWidth(180);
        opacity.setPrefWidth(180);
        watermarkSize.setPrefWidth(180);
        watermarkAngle.setPrefWidth(180);
        staggerRatio.setPrefWidth(180);

        VBox settingsBox = new VBox(8,
                issuerLabel, issuer,
                purposeLabel, purpose,
                new Separator(),
                opacityLabel, opacity,
                sizeLabel, watermarkSize,
                angleLabel, watermarkAngle,
                staggerLabel, staggerRatio);
        settingsBox.setPadding(new Insets(12));
        settingsBox.setAlignment(Pos.TOP_LEFT);

        VBox sidebar = new VBox(actionBox, new Separator(), settingsBox);
        sidebar.setPrefWidth(240);
        sidebar.setStyle("-fx-background-color: #f5f5f5;");

        // Center - preview
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        ScrollPane previewPane = new ScrollPane(preview);
        previewPane.setPannable(true);
        previewPane.setStyle("-fx-background-color:#262626;");
        previewPane.setFitToWidth(true);
        previewPane.setFitToHeight(true);

        // Make preview responsive to viewport size
        previewPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getWidth() > 0 && newVal.getHeight() > 0) {
                updatePreview(newVal.getWidth(), newVal.getHeight());
            }
        });

        // Bottom - log
        log.setEditable(false);
        log.setPrefHeight(180);
        log.setWrapText(true);
        ScrollPane logPane = new ScrollPane(log);
        logPane.setFitToWidth(true);
        logPane.setPrefHeight(180);
        logPane.setMaxHeight(180);

        // Main layout
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(previewPane);
        root.setBottom(logPane);
        BorderPane.setMargin(sidebar, new Insets(12));
        BorderPane.setMargin(previewPane, new Insets(12, 12, 0, 12));
        BorderPane.setMargin(logPane, new Insets(0, 12, 12, 12));

        issuer.textProperty().addListener((o, a, b) -> updatePreview());
        purpose.textProperty().addListener((o, a, b) -> updatePreview());
        opacity.valueProperty().addListener((o, a, b) -> updatePreview());
        watermarkSize.valueProperty().addListener((o, a, b) -> updatePreview());
        watermarkAngle.valueProperty().addListener((o, a, b) -> updatePreview());
        staggerRatio.valueProperty().addListener((o, a, b) -> updatePreview());

        stage.setTitle("Yi Mark - 证件水印保护工具");
        stage.setScene(new Scene(root, 1200, 900));
        stage.setMinWidth(900);
        stage.setMinHeight(700);
        stage.show();
        updatePreview();
    }

    /** Re-renders the visible watermark on the preview image with the current settings. */
    private void updatePreview() {
        if (sourceImage == null) {
            preview.setImage(null);
            return;
        }
        double vpWidth = preview.getScene() != null ? preview.getScene().getWidth() : PREVIEW_MAX_WIDTH;
        double vpHeight = preview.getScene() != null ? preview.getScene().getHeight() : 600;
        updatePreview(vpWidth, vpHeight);
    }

    private void updatePreview(double viewportWidth, double viewportHeight) {
        if (sourceImage == null) {
            preview.setImage(null);
            return;
        }
        // Calculate scale to fit within viewport (with some margin)
        double margin = 24;
        double maxW = Math.max(100, viewportWidth - margin);
        double maxH = Math.max(100, viewportHeight - margin);
        double scale = Math.min(1.0, Math.min(maxW / sourceImage.getWidth(), maxH / sourceImage.getHeight()));
        int scaledW = (int) Math.max(1, Math.round(sourceImage.getWidth() * scale));
        int scaledH = (int) Math.max(1, Math.round(sourceImage.getHeight() * scale));

        BufferedImage scaled = fitWidth(sourceImage, scaledW);
        int fontSize = fontSizeFor(scaled.getWidth());
        BufferedImage watermarked = VisibleWatermark.apply(scaled, issuer.getText(),
                purpose.getText(), "", opacity.getValue(), fontSize, watermarkAngle.getValue(), staggerRatio.getValue());
        preview.setImage(toFxImage(watermarked));
        preview.setFitWidth(scaledW);
        preview.setFitHeight(scaledH);
    }

    private int fontSizeFor(int imageWidth) {
        return (int) Math.max(1, Math.round(
                VisibleWatermark.baseFontSize(imageWidth) * watermarkSize.getValue()));
    }

    private void chooseInput(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        java.io.File file = chooser.showOpenDialog(stage);
        if (file != null) {
            source = file.toPath();
            log.setText("正在读取文件...");
            loadImageAsync(file);
        }
    }

    private void loadImageAsync(java.io.File file) {
        Task<BufferedImage> task = new Task<>() {
            @Override
            protected BufferedImage call() throws IOException {
                return readImage(file);
            }
        };
        task.setOnSucceeded(e -> {
            sourceImage = task.getValue();
            log.setText("原图: " + source + (sourceImage == null ? " (无法读取)" : "")
                    + "  宽 " + (sourceImage == null ? "?" : sourceImage.getWidth()) + " px");
            updatePreview();
        });
        task.setOnFailed(e -> {
            sourceImage = null;
            log.setText("读取失败: " + task.getException().getMessage());
            updatePreview();
        });
        new Thread(task).start();
    }

    private BufferedImage readImage(java.io.File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) {
            return PdfUtils.readFirstPage(file.toPath());
        }
        return javax.imageio.ImageIO.read(file);
    }

    private void chooseOutput(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("secured-document.png");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf"));
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
                    fontSize, watermarkAngle.getValue(), staggerRatio.getValue());
            // 直接展示生成的成品图（不再二次加水印），保留 sourceImage 供后续预览
            BufferedImage finalImage = javax.imageio.ImageIO.read(result.image().toFile());
            if (finalImage != null) {
                sourceImage = finalImage;
                updatePreview();
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