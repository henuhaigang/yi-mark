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

/** Utility for reading/writing PDF files via PDFBox. */
public final class PdfUtils {

    private PdfUtils() {}

    /** Renders the first page of a PDF to a BufferedImage at 300 DPI. */
    public static BufferedImage readFirstPage(Path pdfPath) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            return renderer.renderImageWithDPI(0, 300);
        }
    }

    /** Writes a BufferedImage as a single-page PDF. */
    public static void writePdf(BufferedImage image, Path outputPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            int width = image.getWidth();
            int height = image.getHeight();
            PDPage page = new PDPage(new PDRectangle(width, height));
            document.addPage(page);
            PDPageContentStream contentStream =
                    new PDPageContentStream(document, page);
            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            contentStream.drawImage(pdImage, 0, 0, width, height);
            contentStream.close();
            document.save(outputPath.toFile());
        }
    }
}