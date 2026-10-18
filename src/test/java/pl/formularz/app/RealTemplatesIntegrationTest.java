package pl.formularz.app;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import pl.formularz.pdf.LibreOfficePdfConverter;
import pl.formularz.pdf.PdfBoxPdfMerger;
import pl.formularz.pdf.PdfStrikeAnalyzer;
import pl.formularz.template.DocxTemplateRenderer;
import pl.formularz.template.ExcelTemplateRenderer;
import pl.formularz.template.TemplateData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("libreoffice")
class RealTemplatesIntegrationTest {

    private static final String PARAGRAPH_CHECKED =
        "Ten dodatkowy akapit będzie skreślony przy zaznaczonym.";
    private static final String PARAGRAPH_UNCHECKED =
        "Ten dodatkowy akapit będzie nieskreślony przy zaznaczonym.";

    private static LibreOfficePdfConverter converter;

    @BeforeAll
    static void startLibreOffice() {
        assumeTrue(LibreOfficePdfConverter.isAvailable(), "LibreOffice not available");
        converter = LibreOfficePdfConverter.start();
    }

    @AfterAll
    static void stopLibreOffice() {
        if (converter != null) {
            converter.close();
        }
    }

    @Test
    void generatesMergedPdfFromActualDataFolder() throws Exception {
        Path templatesDir = Path.of("src/test/resources/data");
        Path outputPdf = Files.createTempFile("actual-output-", ".pdf");

        TemplateData data = sampleData(true);

        try {
            DocumentGenerator generator = new DocumentGenerator(
                new DocxTemplateRenderer(),
                new ExcelTemplateRenderer(),
                converter,
                new PdfBoxPdfMerger());

            generator.generate(templatesDir, data, outputPdf);

            assertThat(Files.exists(outputPdf)).isTrue();
            assertThat(Files.size(outputPdf)).isGreaterThan(1000);

            try (PDDocument doc = Loader.loadPDF(outputPdf.toFile())) {
                String fullText = new PDFTextStripper().getText(doc);
                // Word template checks
                assertThat(fullText).contains("Stanisław Żółć-Brzęczyszczykiewicz");
                assertThat(fullText).contains("90010112345");
                // Excel template checks
                assertThat(fullText).contains("UM/2026/001");
                assertThat(fullText).contains("04-10-2026");
                assertThat(fullText).contains("Bardzo poważny tytuł dokumentu");
            }

            PdfStrikeAnalyzer analyzer = PdfStrikeAnalyzer.analyze(outputPdf);
            assertThat(analyzer.isPhraseStruck(PARAGRAPH_CHECKED)).isTrue();
            assertThat(analyzer.isPhraseUnstruck(PARAGRAPH_UNCHECKED)).isTrue();
            assertThat(analyzer.isPhraseUnstruck("Stanisław Żółć-Brzęczyszczykiewicz")).isTrue();
        } finally {
            Files.deleteIfExists(outputPdf);
        }
    }

    @Test
    void verifiesStrikethroughWhenCheckboxIsFalse() throws Exception {
        Path templatesDir = Path.of("src/test/resources/data");
        Path outputPdf = Files.createTempFile("actual-output-false-", ".pdf");

        TemplateData data = sampleData(false);

        try {
            DocumentGenerator generator = new DocumentGenerator(
                new DocxTemplateRenderer(),
                new ExcelTemplateRenderer(),
                converter,
                new PdfBoxPdfMerger());

            generator.generate(templatesDir, data, outputPdf);

            PdfStrikeAnalyzer analyzer = PdfStrikeAnalyzer.analyze(outputPdf);
            assertThat(analyzer.isPhraseStruck(PARAGRAPH_UNCHECKED)).isTrue();
            assertThat(analyzer.isPhraseUnstruck(PARAGRAPH_CHECKED)).isTrue();
        } finally {
            Files.deleteIfExists(outputPdf);
        }
    }

    private static TemplateData sampleData(boolean checkbox) {
        return new TemplateData(
            Map.of(
                "numer_umowy", "UM/2026/001",
                "data_zawarcia_umowy", "04-10-2026",
                "imie", "Stanisław",
                "nazwisko", "Żółć-Brzęczyszczykiewicz",
                "pesel", "90010112345",
                "nazwa_firmy", "Firma 1",
                "kwota", "1 234,50",
                "jednostka", "miesięcznie",
                "kwota_slownie", "tysiąc dwieście trzydzieści cztery PLN 50/100"),
            Map.of("dodatkowy_checkbox", checkbox));
    }
}
