package pl.formularz.pdf;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import pl.formularz.template.DocxTemplateRenderer;
import pl.formularz.template.TemplateData;

import static org.assertj.core.api.Assertions.assertThat;

class RealCaseOswiadczeniePdfTest {

    private static final Map<String, String> SAMPLE_VALUES = Map.ofEntries(
        Map.entry("nazwisko", "Kowalski"),
        Map.entry("imie", "Jan"),
        Map.entry("data_urodzenia", "01-01-1990"),
        Map.entry("miejsce_urodzenia", "Warszawa"),
        Map.entry("pesel", "90010112345"),
        Map.entry("adres_zameldowania", "ul. Główna 1, 00-001 Warszawa"),
        Map.entry("adres_zamieszkania", "ul. Główna 1, 00-001 Warszawa"),
        Map.entry("urzad_skarbowy", "US Warszawa"),
        Map.entry("data_zawarcia_umowy", "17-10-2026"),
        Map.entry("urzad_pracy", ""),
        Map.entry("zatrudniony_nazwa_firmy_uop", ""),
        Map.entry("zatrudniony_nazwa_firmy_zlecenie", ""),
        Map.entry("legitymacja_emerycka", ""),
        Map.entry("legitymacja_emerycka_z_dnia", ""),
        Map.entry("legitymacja_rencisty", ""),
        Map.entry("legitymacja_rencisty_z_dnia", ""),
        Map.entry("zastosowano_koszty_autorskie_kwota", "")
    );

    private static final List<String> ALL_FLAGS = List.of(
        "bezrobotny_checkbox",
        "student_checkbox",
        "26lat_checkbox",
        "legitymacja_studencka_checkbox",
        "zatrudniony_checkbox",
        "zatrudniony_uop_checkbox",
        "zatrudniony_zlecenie_checkbox",
        "podstawa_wymiaru_skladek_1_checkbox",
        "podstawa_wymiaru_skladek_2_checkbox",
        "podstawa_wymiaru_skladek_3_checkbox",
        "dzialalnosc_checkbox",
        "dzialalnosc_rolnicza_checkbox",
        "dzialalnosc_pozarolnicza_checkbox",
        "nowy_przedsiebiorca_checkbox",
        "przedmiot_umowy_checkbox",
        "ubezpieczenie_spoleczne_checkbox",
        "urlop_checkbox",
        "urlop_macierzynski_checkbox",
        "urlop_wychowawczy_checkbox",
        "urlop_bezplatny_checkbox",
        "emeryt_checkbox",
        "rencista_checkbox",
        "renta_rodzinna_checkbox",
        "niepelnosprawnosc_checkbox",
        "niepelnosprawnosc_lekka_checkbox",
        "niepelnosprawnosc_umiarkowana_checkbox",
        "niepelnosprawnosc_znaczna_checkbox",
        "obowiazek_podatkowy_checkbox",
        "limit_kosztow_autorskich_checkbox"
    );

    @Test
    void testOswiadczeniePdfWhenCheckboxesAreFalse() throws Exception {
        if (!LibreOfficePdfConverter.isAvailable()) return;

        Path docxPath = Path.of("data/WZÓR oświadczenie do umów zlecenia 2026 wersja zaktualizowana.docx");
        TemplateData data = new TemplateData(
            SAMPLE_VALUES,
            ALL_FLAGS.stream().collect(Collectors.toMap(f -> f, f -> false))
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
            SAMPLE_VALUES,
            ALL_FLAGS.stream().collect(Collectors.toMap(f -> f, f -> true))
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
