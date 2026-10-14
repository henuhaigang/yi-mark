package com.yimark;

import com.yimark.crypto.Ed25519Keys;
import com.yimark.image.PdfUtils;
import com.yimark.image.VisibleWatermark;
import com.yimark.protect.ProtectionService;
import com.yimark.trust.TrustStore;
import com.yimark.verify.VerificationService;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    private final Slider opacity = new Slider(0.08, 0.60, 0.30);
    private final Slider watermarkSize = new Slider(0.01, 3.0, 1.0);
    private final Slider watermarkAngle = new Slider(-60, 60, -24);
    private final Slider staggerRatio = new Slider(-1.0, 1.0, VisibleWatermark.DEFAULT_STAGGER_RATIO);
    
    // Mosaic controls
    private final javafx.scene.control.CheckBox mosaicEnabled = new javafx.scene.control.CheckBox("启用马赛克");
    private final Slider mosaicBlockSize = new Slider(2, 50, 10);
    private final javafx.scene.control.Label mosaicBlockSizeLabel = new javafx.scene.control.Label("马赛克块大小: 10");
    private final Button undoButton = new Button("撤销");
    private final Button clearButton = new Button("清除所有");
    private final ColorPicker watermarkColorPicker = new ColorPicker(toFXColor(VisibleWatermark.DEFAULT_WATERMARK_COLOR));
    private final ImageView preview = new ImageView();
    private Canvas mosaicCanvas;
    private GraphicsContext mosaicGC;
    private final List<Rectangle2D.Double> mosaicRegions = new ArrayList<>();
    private final List<List<Rectangle2D.Double>> mosaicHistory = new ArrayList<>();
    private boolean isDrawingMosaic = false;
    private double lastX, lastY;

    private BufferedImage sourceImage;

    /**
     * Convert JavaFX Color to AWT Color.
     */
    private static java.awt.Color toAWTColor(javafx.scene.paint.Color fxColor) {
        return new java.awt.Color(
                (int) (fxColor.getRed() * 255),
                (int) (fxColor.getGreen() * 255),
                (int) (fxColor.getBlue() * 255),
                (int) (fxColor.getOpacity() * 255)
        );
    }

    /**
     * Convert AWT Color to JavaFX Color.
     */
    private static javafx.scene.paint.Color toFXColor(java.awt.Color awtColor) {
        return new javafx.scene.paint.Color(
                awtColor.getRed() / 255.0,
                awtColor.getGreen() / 255.0,
                awtColor.getBlue() / 255.0,
                awtColor.getAlpha() / 255.0
        );
    }

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

        // Apply sidebar button styles
        in.getStyleClass().addAll("sidebar-button");
        out.getStyleClass().addAll("sidebar-button");
        protect.getStyleClass().addAll("sidebar-button", "sidebar-button-primary");
        verify.getStyleClass().addAll("sidebar-button");
        trust.getStyleClass().addAll("sidebar-button");

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
        actionBox.getStyleClass().add("sidebar-section");

        // Settings panel
        Label issuerLabel = new Label("使用方");
        Label purposeLabel = new Label("用途");
        Label opacityLabel = new Label("透明度");
        Label sizeLabel = new Label("大小");
        Label angleLabel = new Label("角度°");
        Label staggerLabel = new Label("错位");
        Label mosaicLabel = new Label("马赛克");

        issuerLabel.getStyleClass().add("label");
        purposeLabel.getStyleClass().add("label");
        opacityLabel.getStyleClass().add("label");
        sizeLabel.getStyleClass().add("label");
        angleLabel.getStyleClass().add("label");
        staggerLabel.getStyleClass().add("label");
        mosaicLabel.getStyleClass().add("label");

        issuer.setPrefWidth(220);
        purpose.setPrefWidth(220);
        opacity.setPrefWidth(220);
        watermarkSize.setPrefWidth(220);
        watermarkAngle.setPrefWidth(220);
        staggerRatio.setPrefWidth(220);
        mosaicEnabled.setPrefWidth(220);
        mosaicBlockSize.setPrefWidth(220);
        mosaicBlockSizeLabel.setPrefWidth(220);

        issuer.getStyleClass().add("text-field");
        purpose.getStyleClass().add("text-field");
        opacity.getStyleClass().add("slider");
        watermarkSize.getStyleClass().add("slider");
        watermarkAngle.getStyleClass().add("slider");
        staggerRatio.getStyleClass().add("slider");
        mosaicBlockSize.getStyleClass().add("slider");
        mosaicBlockSizeLabel.getStyleClass().add("label");
        mosaicEnabled.getStyleClass().add("check-box");

        mosaicBlockSize.valueProperty().addListener((obs, oldVal, newVal) -> {
            mosaicBlockSizeLabel.setText("马赛克块大小: " + newVal.intValue());
            updatePreview();
        });
        mosaicEnabled.selectedProperty().addListener((obs, oldVal, newVal) -> {
            mosaicBlockSize.setDisable(!newVal);
            mosaicBlockSizeLabel.setDisable(!newVal);
            if (!newVal) {
                clearMosaic();
            }
            updatePreview();
        });

        Separator settingsDivider = new Separator();
        settingsDivider.getStyleClass().add("separator");
        Separator mosaicDivider = new Separator();
        mosaicDivider.getStyleClass().add("separator");

        undoButton.getStyleClass().add("sidebar-button");
        undoButton.setOnAction(e -> undoMosaic());
        undoButton.setDisable(true);

        clearButton.getStyleClass().add("sidebar-button");
        clearButton.getStyleClass().add("button-danger");
        clearButton.setOnAction(e -> clearMosaic());
        clearButton.setDisable(true);

        HBox mosaicButtonBox = new HBox(8, undoButton, clearButton);
        mosaicButtonBox.setAlignment(Pos.CENTER_LEFT);

        VBox settingsBox = new VBox(8,
                issuerLabel, issuer,
                purposeLabel, purpose,
                settingsDivider,
                opacityLabel, opacity,
                sizeLabel, watermarkSize,
                angleLabel, watermarkAngle,
                staggerLabel, staggerRatio,
                mosaicDivider,
                mosaicLabel, mosaicEnabled,
                mosaicBlockSizeLabel, mosaicBlockSize,
                new Label("水印颜色"), watermarkColorPicker,
                mosaicButtonBox);
        settingsBox.setPadding(new Insets(12));
        settingsBox.setAlignment(Pos.TOP_LEFT);
        settingsBox.getStyleClass().add("sidebar-section");

        VBox sidebarContent = new VBox(actionBox, new Separator(), settingsBox);
        sidebarContent.setSpacing(0);
        
        ScrollPane sidebarScrollPane = new ScrollPane(sidebarContent);
        sidebarScrollPane.setFitToWidth(true);
        sidebarScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sidebarScrollPane.getStyleClass().add("sidebar-scroll");
        
        VBox sidebar = new VBox(sidebarScrollPane);
        sidebar.setPrefWidth(280);
        sidebar.setMaxWidth(280);
        sidebar.getStyleClass().add("sidebar");

        // Center - preview with mosaic canvas overlay
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        
        mosaicCanvas = new Canvas();
        mosaicGC = mosaicCanvas.getGraphicsContext2D();
        
        javafx.scene.layout.StackPane previewStack = new javafx.scene.layout.StackPane();
        previewStack.getChildren().addAll(preview, mosaicCanvas);
        preview.setPreserveRatio(true);
        preview.setSmooth(true);
        mosaicCanvas.setMouseTransparent(false);
        
        // Log area at bottom of preview
        log.setEditable(false);
        log.setPrefHeight(100);
        log.setWrapText(true);
        log.getStyleClass().add("log-content");
        ScrollPane logPane = new ScrollPane(log);
        logPane.setFitToWidth(true);
        logPane.setPrefHeight(100);
        logPane.setMaxHeight(120);
        logPane.getStyleClass().add("log-pane");
        
        // Preview with log at bottom
        VBox previewWithLog = new VBox(previewStack, logPane);
        VBox.setVgrow(previewStack, Priority.ALWAYS);
        
        ScrollPane previewPane = new ScrollPane(previewWithLog);
        previewPane.setPannable(true);
        previewPane.setFitToWidth(true);
        previewPane.setFitToHeight(true);
        previewPane.getStyleClass().add("preview-pane");
        preview.getStyleClass().add("preview-image");

        // Mosaic drawing mouse handlers
        setupMosaicMouseHandlers();

        // Make preview responsive to viewport size
        previewPane.viewportBoundsProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.getWidth() > 0 && newVal.getHeight() > 0) {
                updatePreview(newVal.getWidth(), newVal.getHeight());
            }
        });

        // Main layout
        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setCenter(previewPane);
        BorderPane.setMargin(sidebar, new Insets(12));
        BorderPane.setMargin(previewPane, new Insets(12, 12, 12, 12));
        root.getStyleClass().add("main-container");

        issuer.textProperty().addListener((o, a, b) -> updatePreview());
        purpose.textProperty().addListener((o, a, b) -> updatePreview());
        opacity.valueProperty().addListener((o, a, b) -> updatePreview());
        watermarkSize.valueProperty().addListener((o, a, b) -> updatePreview());
        watermarkAngle.valueProperty().addListener((o, a, b) -> updatePreview());
        staggerRatio.valueProperty().addListener((o, a, b) -> updatePreview());
        mosaicEnabled.selectedProperty().addListener((o, a, b) -> updatePreview());
        // Mosaic block size only affects mosaic overlay, not full preview
        mosaicBlockSize.valueProperty().addListener((o, a, b) -> {
            updateMosaicPreview();
        });
        // Watercolor color picker
        watermarkColorPicker.valueProperty().addListener((o, a, b) -> updatePreview());

        stage.setTitle("Yi Mark - 证件水印保护工具");
        Scene scene = new Scene(root, 1200, 900);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        
        // Add keyboard shortcut for undo (Ctrl+Z)
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.Z) {
                undoMosaic();
                e.consume();
            }
        });
        
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(700);
        stage.show();

        // Ensure preview updates when scene/layout is ready
        scene.widthProperty().addListener((obs, oldVal, newVal) -> updatePreview());
        scene.heightProperty().addListener((obs, oldVal, newVal) -> updatePreview());
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
                purpose.getText(), "", opacity.getValue(), fontSize, watermarkAngle.getValue(), staggerRatio.getValue(), toAWTColor(watermarkColorPicker.getValue()));

        // Apply mosaic if enabled and there are drawn regions
        if (mosaicEnabled.isSelected() && !mosaicRegions.isEmpty()) {
            int mosaicSize = (int) mosaicBlockSize.getValue();
            // Apply mosaic only to drawn regions for preview
            for (Rectangle2D.Double rect : mosaicRegions) {
                watermarked = com.yimark.image.ImageOps.applyMosaic(watermarked, (int) rect.x, (int) rect.y, (int) rect.width, (int) rect.height, mosaicSize);
            }
        }

        preview.setImage(toFxImage(watermarked));
        preview.setFitWidth(scaledW);
        preview.setFitHeight(scaledH);
        updateMosaicCanvasSize();
    }

    private int fontSizeFor(int imageWidth) {
        return (int) Math.max(1, Math.round(
                VisibleWatermark.baseFontSize(imageWidth) * watermarkSize.getValue()));
    }

    private void chooseInput(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.PNG", "*.JPG", "*.JPEG"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf", "*.PDF"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
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

    private BufferedImage readImageForExport(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            return PdfUtils.readFirstPage(path);
        }
        return javax.imageio.ImageIO.read(path.toFile());
    }

    private void chooseOutput(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName("secured-document.png");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG", "*.png", "*.PNG"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf", "*.PDF"));
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
            boolean enableMosaic = mosaicEnabled.isSelected() && !mosaicRegions.isEmpty();
            int mosaicSize = (int) mosaicBlockSize.getValue();
            
            // Convert mosaic regions from preview coordinates to source image coordinates
            List<Rectangle2D.Double> sourceMosaicRegions = new ArrayList<>();
            if (enableMosaic && !mosaicRegions.isEmpty() && sourceImage != null) {
                double scaleX = (double) sourceImage.getWidth() / preview.getImage().getWidth();
                double scaleY = (double) sourceImage.getHeight() / preview.getImage().getHeight();
                for (Rectangle2D.Double rect : mosaicRegions) {
                    sourceMosaicRegions.add(new Rectangle2D.Double(
                        rect.x * scaleX,
                        rect.y * scaleY,
                        rect.width * scaleX,
                        rect.height * scaleY
                    ));
                }
            }
            
            ProtectionService.Result result = protection.protect(
                    source, output, issuer.getText(), purpose.getText(), opacity.getValue(),
                    fontSize, watermarkAngle.getValue(), staggerRatio.getValue(),
                    enableMosaic, (int) mosaicBlockSize.getValue(), sourceMosaicRegions,
                    toAWTColor(watermarkColorPicker.getValue()));
            
            // 直接展示生成的成品图（已含水印），不再重新加水印
            BufferedImage finalImage = readImageForExport(result.image());
            if (finalImage != null) {
                preview.setImage(toFxImage(fitWidth(finalImage, PREVIEW_MAX_WIDTH)));
                updateMosaicCanvasSize();
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

    private void setupMosaicMouseHandlers() {
        mosaicCanvas.setOnMousePressed(e -> {
            if (!mosaicEnabled.isSelected()) return;
            saveMosaicState();
            isDrawingMosaic = true;
            lastX = e.getX();
            lastY = e.getY();
            // Start a new mosaic stroke
            double x = Math.max(0, Math.min(e.getX(), mosaicCanvas.getWidth()));
            double y = Math.max(0, Math.min(e.getY(), mosaicCanvas.getHeight()));
            int blockSize = (int) mosaicBlockSize.getValue();
            // Add initial point as a small rectangle
            int size = (int) mosaicBlockSize.getValue();
            mosaicRegions.add(new Rectangle2D.Double(x - size/2, y - size/2, size, size));
            drawMosaicPreview();
            updateUndoButton();
        });

        mosaicCanvas.setOnMouseDragged(e -> {
            if (!isDrawingMosaic || !mosaicEnabled.isSelected()) return;
            double x = Math.max(0, Math.min(e.getX(), mosaicCanvas.getWidth()));
            double y = Math.max(0, Math.min(e.getY(), mosaicCanvas.getHeight()));
            
            // Calculate distance from last point
            double dx = x - lastX;
            double dy = y - lastY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            int blockSize = (int) mosaicBlockSize.getValue();
            
            // Add mosaic blocks along the path
            int steps = Math.max(1, (int) (distance / (blockSize / 2.0)));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                double ix = lastX + dx * t;
                double iy = lastY + dy * t;
                int size = (int) mosaicBlockSize.getValue();
                mosaicRegions.add(new Rectangle2D.Double(ix - size/2, iy - size/2, size, size));
            }
            
            lastX = e.getX();
            lastY = e.getY();
            drawMosaicPreview();
        });

        mosaicCanvas.setOnMouseReleased(e -> {
            isDrawingMosaic = false;
        });

        mosaicCanvas.setOnMouseExited(e -> {
            isDrawingMosaic = false;
        });
    }

    private void drawMosaicPreview() {
        if (mosaicGC == null) return;
        
        // Clear canvas
        mosaicGC.clearRect(0, 0, mosaicCanvas.getWidth(), mosaicCanvas.getHeight());
        
        if (mosaicRegions.isEmpty()) return;
        
        // Draw mosaic blocks on canvas
        mosaicGC.setFill(Color.BLACK);
        for (Rectangle2D.Double rect : mosaicRegions) {
            mosaicGC.fillRect(rect.x, rect.y, rect.width, rect.height);
        }
    }

    private void updateMosaicPreview() {
        // Only redraw mosaic canvas overlay, don't re-render full preview
        if (!mosaicEnabled.isSelected() || mosaicRegions.isEmpty()) return;
        drawMosaicPreview();
    }

    private void clearMosaic() {
        if (!mosaicRegions.isEmpty()) {
            saveMosaicState();
        }
        mosaicRegions.clear();
        if (mosaicGC != null) {
            mosaicGC.clearRect(0, 0, mosaicCanvas.getWidth(), mosaicCanvas.getHeight());
        }
        updateUndoButton();
    }

    private void saveMosaicState() {
        // Deep copy the current regions to history
        List<Rectangle2D.Double> copy = new ArrayList<>();
        for (Rectangle2D.Double rect : mosaicRegions) {
            copy.add(new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height));
        }
        mosaicHistory.add(copy);
        // Limit history size to prevent memory issues
        if (mosaicHistory.size() > 50) {
            mosaicHistory.remove(0);
        }
    }

    private void undoMosaic() {
        if (!mosaicHistory.isEmpty()) {
            List<Rectangle2D.Double> previousState = mosaicHistory.remove(mosaicHistory.size() - 1);
            mosaicRegions.clear();
            mosaicRegions.addAll(previousState);
            drawMosaicPreview();
            updateUndoButton();
        }
    }

    private void updateUndoButton() {
        if (undoButton != null) {
            undoButton.setDisable(mosaicHistory.isEmpty());
        }
        if (clearButton != null) {
            clearButton.setDisable(mosaicRegions.isEmpty());
        }
    }

    private void updateMosaicCanvasSize() {
        if (mosaicCanvas != null && preview.getImage() != null) {
            mosaicCanvas.setWidth(preview.getImage().getWidth());
            mosaicCanvas.setHeight(preview.getImage().getHeight());
            drawMosaicPreview();
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