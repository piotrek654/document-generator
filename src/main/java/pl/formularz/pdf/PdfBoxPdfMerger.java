package pl.formularz.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

public class PdfBoxPdfMerger implements PdfMerger {

    @Override
    public void merge(List<Path> pdfs, Path target) {
        PDFMergerUtility merger = new PDFMergerUtility();
        try {
            for (Path pdf : pdfs) {
                merger.addSource(pdf.toFile());
            }
            merger.setDestinationFileName(target.toString());
            merger.mergeDocuments(IOUtils.createTempFileOnlyStreamCache());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot merge PDFs into " + target, e);
        }
    }
}
