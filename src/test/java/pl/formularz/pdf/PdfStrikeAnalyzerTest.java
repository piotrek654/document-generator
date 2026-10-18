package pl.formularz.pdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("libreoffice")
class PdfStrikeAnalyzerTest {

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
    void detectsStruckAndUnstruckPhrasesInPdf() throws IOException {
        Path docx = workDir.resolve("sample.docx");
        Path pdf = workDir.resolve("sample.pdf");

        try (XWPFDocument doc = new XWPFDocument(); var out = Files.newOutputStream(docx)) {
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("To jest ");
            XWPFRun rStruck = p1.createRun();
            rStruck.setText("przekreślony fragment tekstu");
            rStruck.setStrikeThrough(true);
            p1.createRun().setText(" oraz dalszy zwykły tekst.");

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun rPolishStruck = p2.createRun();
            rPolishStruck.setText("Zażółć gęślą jaźń");
            rPolishStruck.setStrikeThrough(true);
            p2.createRun().setText(" - koniec testu.");

            doc.write(out);
        }

        converter.convert(docx, pdf);

        PdfStrikeAnalyzer analyzer = PdfStrikeAnalyzer.analyze(pdf);

        assertThat(analyzer.pages()).hasSize(1);
        assertThat(analyzer.allText())
            .contains("To jest przekreślony fragment tekstu oraz dalszy zwykły tekst.")
            .contains("Zażółć gęślą jaźń - koniec testu.");

        assertThat(analyzer.isPhraseStruck("przekreślony fragment tekstu")).isTrue();
        assertThat(analyzer.isPhraseStruck("Zażółć gęślą jaźń")).isTrue();

        assertThat(analyzer.isPhraseUnstruck("To jest")).isTrue();
        assertThat(analyzer.isPhraseUnstruck("oraz dalszy zwykły tekst.")).isTrue();
        assertThat(analyzer.isPhraseUnstruck("- koniec testu.")).isTrue();

        assertThat(analyzer.isPhraseStruck("To jest")).isFalse();
        assertThat(analyzer.isPhraseUnstruck("przekreślony fragment tekstu")).isFalse();

        assertThat(analyzer.struckText())
            .contains("przekreślony fragment tekstu")
            .contains("Zażółć gęślą jaźń");
    }

    @Test
    void detectsMultipleStrikethroughBlocksInSingleLine() throws IOException {
        Path docx = workDir.resolve("multi-strike.docx");
        Path pdf = workDir.resolve("multi-strike.pdf");

        try (XWPFDocument doc = new XWPFDocument(); var out = Files.newOutputStream(docx)) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r1 = p.createRun();
            r1.setText("Nie jestem");
            r1.setStrikeThrough(true);

            p.createRun().setText(" / jestem studentem, ");

            XWPFRun r2 = p.createRun();
            r2.setText("nie posiadam");
            r2.setStrikeThrough(true);

            p.createRun().setText(" / posiadam legitymację.");

            doc.write(out);
        }

        converter.convert(docx, pdf);

        PdfStrikeAnalyzer analyzer = PdfStrikeAnalyzer.analyze(pdf);

        assertThat(analyzer.isPhraseStruck("Nie jestem")).isTrue();
        assertThat(analyzer.isPhraseUnstruck("jestem studentem")).isTrue();
        assertThat(analyzer.isPhraseStruck("nie posiadam")).isTrue();
        assertThat(analyzer.isPhraseUnstruck("posiadam legitymację")).isTrue();
    }
}
