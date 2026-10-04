package pl.formularz.pdf;

import java.nio.file.Path;
import java.util.List;

/**
 * Joins PDFs, in the given order, into one file.
 */
public interface PdfMerger {

    void merge(List<Path> pdfs, Path target);
}
