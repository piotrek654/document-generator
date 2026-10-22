package pl.formularz.pdf;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.formularz.template.DocxTemplateRenderer;
import pl.formularz.template.TemplateData;

import static org.assertj.core.api.Assertions.assertThat;

class RealCaseOswiadczeniePdfTest {

    @Test
    void testOswiadczeniePdfWhenCheckboxesAreFalse() throws Exception {
        if (!LibreOfficePdfConverter.isAvailable()) return;

        Path docxPath = Path.of("data/WZÓR oświadczenie do umów zlecenia 2026 wersja zaktualizowana.docx");
        TemplateData data = new TemplateData(
            Map.of(
                "nazwisko", "Kowalski",
                "imie", "Jan",
                "data_urodzenia", "01-01-1990",
                "miejsce_urodzenia", "Warszawa",
                "pesel", "90010112345",
                "adres_zameldowania", "ul. Główna 1, 00-001 Warszawa",
                "adres_zamieszkania", "ul. Główna 1, 00-001 Warszawa",
                "urzad_skarbowy", "US Warszawa",
                "data_zawarcia_umowy", "17-10-2026"
            ),
            Map.ofEntries(
                Map.entry("student_checkbox", false),
                Map.entry("26lat_checkbox", false),
                Map.entry("legitymacja_studencka_checkbox", false),
                Map.entry("bezrobotny_checkbox", false),
                Map.entry("zatrudniony_checkbox", false),
                Map.entry("zatrudniony_uop_checkbox", false),
                Map.entry("zatrudniony_zlecenie_checkbox", false),
                Map.entry("podstawa_wymiaru_skladek_1_checkbox", false),
                Map.entry("podstawa_wymiaru_skladek_2_checkbox", false),
                Map.entry("podstawa_wymiaru_skladek_3_checkbox", false)
            )
        );

        Path renderedDocx = Files.createTempFile("rendered-", ".docx");
        Path renderedPdf = Files.createTempFile("rendered-", ".pdf");

        DocxTemplateRenderer renderer = new DocxTemplateRenderer();
        try (var in = new FileInputStream(docxPath.toFile()); var out = new FileOutputStream(renderedDocx.toFile())) {
            renderer.render(in, data, out);
        }

        try (LibreOfficePdfConverter converter = LibreOfficePdfConverter.start()) {
            converter.convert(renderedDocx, renderedPdf);
        }

        PdfStrikeAnalyzer analyzer = PdfStrikeAnalyzer.analyze(renderedPdf);
        PdfStrikeAnalyzer.PageAnalysis page2 = analyzer.page(2);

        // On page 2, the paragraph begins with "Nie jestem / jestem studentem..."
        // When student_checkbox=false:
        // "jestem", "mam", "posiadam" MUST be struck!
        // "Nie jestem", "nie mam", "nie posiadam" MUST NOT be struck!
        String page2Struck = page2.struckText();
        System.out.println("PAGE 2 STRUCK TEXT: " + page2Struck);

        assertThat(page2Struck).contains("jestem").contains("nie mam").contains("posiadam");
        assertThat(page2Struck).doesNotContain("Nie jestem");
        assertThat(page2Struck).doesNotContain("nie posiadam");

        Files.deleteIfExists(renderedDocx);
        Files.deleteIfExists(renderedPdf);
    }

    @Test
    void testOswiadczeniePdfWhenCheckboxesAreTrue() throws Exception {
        if (!LibreOfficePdfConverter.isAvailable()) return;

        Path docxPath = Path.of("data/WZÓR oświadczenie do umów zlecenia 2026 wersja zaktualizowana.docx");
        TemplateData data = new TemplateData(
            Map.of(
                "nazwisko", "Kowalski",
                "imie", "Jan",
                "data_urodzenia", "01-01-1990",
                "miejsce_urodzenia", "Warszawa",
                "pesel", "90010112345",
                "adres_zameldowania", "ul. Główna 1, 00-001 Warszawa",
                "adres_zamieszkania", "ul. Główna 1, 00-001 Warszawa",
                "urzad_skarbowy", "US Warszawa",
                "data_zawarcia_umowy", "17-10-2026"
            ),
            Map.ofEntries(
                Map.entry("student_checkbox", true),
                Map.entry("26lat_checkbox", true),
                Map.entry("legitymacja_studencka_checkbox", true),
                Map.entry("bezrobotny_checkbox", true),
                Map.entry("zatrudniony_checkbox", true),
                Map.entry("zatrudniony_uop_checkbox", true),
                Map.entry("zatrudniony_zlecenie_checkbox", true),
                Map.entry("podstawa_wymiaru_skladek_1_checkbox", true),
                Map.entry("podstawa_wymiaru_skladek_2_checkbox", true),
                Map.entry("podstawa_wymiaru_skladek_3_checkbox", true)
            )
        );

        Path renderedDocx = Files.createTempFile("rendered-", ".docx");
        Path renderedPdf = Files.createTempFile("rendered-", ".pdf");

        DocxTemplateRenderer renderer = new DocxTemplateRenderer();
        try (var in = new FileInputStream(docxPath.toFile()); var out = new FileOutputStream(renderedDocx.toFile())) {
            renderer.render(in, data, out);
        }

        try (LibreOfficePdfConverter converter = LibreOfficePdfConverter.start()) {
            converter.convert(renderedDocx, renderedPdf);
        }

        PdfStrikeAnalyzer analyzer = PdfStrikeAnalyzer.analyze(renderedPdf);
        PdfStrikeAnalyzer.PageAnalysis page2 = analyzer.page(2);

        // When student_checkbox=true:
        // "Nie jestem", "nie mam", "nie posiadam" MUST be struck!
        // "jestem", "mam", "posiadam" MUST NOT be struck!
        String page2Struck = page2.struckText();
        System.out.println("PAGE 2 STRUCK TEXT (TRUE): " + page2Struck);

        assertThat(page2Struck).contains("Nie jestem").contains("mam").contains("nie posiadam");

        Files.deleteIfExists(renderedDocx);
        Files.deleteIfExists(renderedPdf);
    }
}
