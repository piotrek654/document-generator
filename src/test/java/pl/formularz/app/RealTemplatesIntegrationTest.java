package pl.formularz.app;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import pl.formularz.pdf.LibreOfficePdfConverter;
import pl.formularz.pdf.PdfBoxPdfMerger;
import pl.formularz.template.DocxTemplateRenderer;
import pl.formularz.template.ExcelTemplateRenderer;
import pl.formularz.template.TemplateData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("libreoffice")
class RealTemplatesIntegrationTest {

    @Test
    void generatesMergedPdfFromActualDataFolder() throws Exception {
        assumeTrue(LibreOfficePdfConverter.isAvailable(), "LibreOffice not available");
        Path templatesDir = Path.of("data");
        Path outputPdf = Files.createTempFile("actual-output-", ".pdf");

        TemplateData data = new TemplateData(
            Map.of(
                "numer_umowy", "UM/2026/001",
                "data_zawarcia_umowy", "04-10-2026",
                "imie", "Stanisław",
                "nazwisko", "Żółć-Brzęczyszczykiewicz",
                "pesel", "90010112345",
                "nazwa_firmy", "Firma 1",
                "kwota", "1 234,50",
                "jednostka", "miesięcznie",
                "kwota_slownie", "tysiąc dwieście trzydzieści cztery złote pięćdziesiąt groszy"),
            Map.of("dodatkowy_checkbox", true));

        try (LibreOfficePdfConverter converter = LibreOfficePdfConverter.start()) {
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
        } finally {
            Files.deleteIfExists(outputPdf);
        }
    }
}
