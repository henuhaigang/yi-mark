package com.yimark.image;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

 /** Utility for reading/writing PDF files via PDFBox. */
public final class PdfUtils {

    public static final int RENDER_DPI = 300;

    private PdfUtils() {}

    /** Renders all pages of a PDF to a list of BufferedImages at 300 DPI. */
    public static List<BufferedImage> readAllPages(Path pdfPath) throws IOException {
        List<BufferedImage> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                images.add(renderer.renderImageWithDPI(i, RENDER_DPI));
            }
        }
        return images;
    }

    /** Renders the first page of a PDF to a BufferedImage at 300 DPI (backward compatibility). */
    public static BufferedImage readFirstPage(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            return renderer.renderImageWithDPI(0, RENDER_DPI);
        }
    }

    /** Writes a list of BufferedImages as a multi-page PDF. */
    public static void writePdf(List<BufferedImage> images, Path outputPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (BufferedImage image : images) {
                int width = image.getWidth();
                int height = image.getHeight();
                float pageWidth = width * 72f / RENDER_DPI;
                float pageHeight = height * 72f / RENDER_DPI;
                PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
                document.addPage(page);
                PDPageContentStream contentStream =
                        new PDPageContentStream(document, page);
                PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
                contentStream.drawImage(pdImage, 0, 0, pageWidth, pageHeight);
                contentStream.close();
            }
            document.save(outputPath.toFile());
        }
    }

    /** Writes a single BufferedImage as a single-page PDF (backward compatibility). */
    public static void writePdf(BufferedImage image, Path outputPath) throws IOException {
        writePdf(List.of(image), outputPath);
    }
}