package pl.formularz.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("libreoffice")
class LibreOfficePdfConverterTest {

    private static LibreOfficePdfConverter converter;

    @TempDir
    Path workDir;

    @BeforeAll
    static void startLibreOffice() {
        assumeTrue(LibreOfficePdfConverter.isAvailable(), "LibreOffice is not installed");
        converter = LibreOfficePdfConverter.start();
    }

    @AfterAll
    static void stopLibreOffice() {
        if (converter != null) {
            converter.close();
        }
    }

    @Test
    void convertsDocxToPdfKeepingPolishCharacters() throws IOException {
        Path docx = workDir.resolve("dokument.docx");
        Path pdf = workDir.resolve("dokument.pdf");
        writeDocx(docx, "Zażółć gęślą jaźń: sto dwadzieścia trzy złote czterdzieści pięć groszy");

        converter.convert(docx, pdf);

        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            assertThat(new PDFTextStripper().getText(document))
                .contains("Zażółć gęślą jaźń: sto dwadzieścia trzy złote czterdzieści pięć groszy");
        }
    }

    private static void writeDocx(Path target, String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); var output = Files.newOutputStream(target)) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
    }
}
