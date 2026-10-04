package pl.formularz.pdf;

import java.nio.file.Path;

/**
 * Converts a filled document to PDF.
 */
public interface PdfConverter {

    void convert(Path document, Path pdf);
}
