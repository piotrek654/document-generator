package pl.formularz.template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxTemplateRendererTest {

    private static final String PARAGRAPH_UNCHECKED =
        "Ten dodatkowy akapit będzie nieskreślony przy zaznaczonym.";
    private static final String PARAGRAPH_CHECKED =
        "Ten dodatkowy akapit będzie skreślony przy zaznaczonym.";

    private final TemplateRenderer renderer = new DocxTemplateRenderer();

    @Test
    void fillsAllPlaceholdersOfSampleTemplate() throws IOException {
        String text = allText(renderSample(sampleData(true)));

        assertThat(text)
            .contains("Jan Kowalski")
            .contains("90010112345")
            .contains("dla Firma z bardzo długą nazwą i coś wnosi")
            .contains("Mamy też 1 234,50 zł za miesięcznie, słownie tysiąc dwieście trzydzieści cztery PLN 50/100.")
            .doesNotContain("{", "}");
    }

    @Test
    void strikesDirectParagraphWhenCheckboxIsChecked() throws IOException {
        XWPFDocument result = renderSample(sampleData(true));

        assertThat(struckText(result)).isEqualTo(PARAGRAPH_CHECKED);
        assertThat(allText(result))
            .contains(PARAGRAPH_UNCHECKED)
            .contains(PARAGRAPH_CHECKED)
            .doesNotContain("skresl");
    }

    @Test
    void strikesNegatedParagraphWhenCheckboxIsUnchecked() throws IOException {
        XWPFDocument result = renderSample(sampleData(false));

        assertThat(struckText(result)).isEqualTo(PARAGRAPH_UNCHECKED);
        assertThat(allText(result))
            .contains(PARAGRAPH_UNCHECKED)
            .contains(PARAGRAPH_CHECKED)
            .doesNotContain("skresl");
    }

    @Test
    void handlesDirectAndNegatedStrikeBlocks() throws IOException {
        byte[] template = templateWithText(
            "{#!skresl:dodatkowy_checkbox}Ten dodatkowy akapit będzie nieskreślony.{/skresl} " +
            "{#skresl:dodatkowy_checkbox}Ten dodatkowy akapit będzie skreślony.{/skresl}");

        XWPFDocument docTrue = render(template, new TemplateData(Map.of(), Map.of("dodatkowy_checkbox", true)));
        assertThat(struckText(docTrue)).isEqualTo("Ten dodatkowy akapit będzie skreślony.");
        assertThat(allText(docTrue)).isEqualTo("Ten dodatkowy akapit będzie nieskreślony. Ten dodatkowy akapit będzie skreślony.");

        XWPFDocument docFalse = render(template, new TemplateData(Map.of(), Map.of("dodatkowy_checkbox", false)));
        assertThat(struckText(docFalse)).isEqualTo("Ten dodatkowy akapit będzie nieskreślony.");
        assertThat(allText(docFalse)).isEqualTo("Ten dodatkowy akapit będzie nieskreślony. Ten dodatkowy akapit będzie skreślony.");
    }

    @Test
    void rejectsPlaceholderWithoutValue() throws IOException {
        byte[] template = templateWithText("Dzień dobry {tytul} Kowalski");

        assertThatThrownBy(() -> render(template, new TemplateData(Map.of(), Map.of())))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining("{tytul}");
    }

    @Test
    void rejectsStrikeBlockWithoutCheckboxValue() throws IOException {
        byte[] template = templateWithText("{#skresl:zgoda}Tekst{/skresl}");

        assertThatThrownBy(() -> render(template, new TemplateData(Map.of(), Map.of())))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining("zgoda");
    }

    @Test
    void rejectsUnclosedStrikeBlock() throws IOException {
        byte[] template = templateWithText("{#skresl:zgoda}Tekst bez końca");

        assertThatThrownBy(() -> render(template, new TemplateData(Map.of(), Map.of("zgoda", false))))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining("{#skresl:zgoda}");
    }

    @Test
    void rejectsUnclosedNegatedStrikeBlock() throws IOException {
        byte[] template = templateWithText("{#!skresl:zgoda}Tekst bez końca");

        assertThatThrownBy(() -> render(template, new TemplateData(Map.of(), Map.of("zgoda", false))))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining("{#!skresl:zgoda}");
    }

    @Test
    void fillsContractDates() throws IOException {
        byte[] template = templateWithText("Umowa od {data_rozpoczecia_umowy} do {data_zakonczenia_umowy} na rok {rok}.");
        XWPFDocument result = render(template, new TemplateData(
            Map.of("data_rozpoczecia_umowy", "01-03-2026", "data_zakonczenia_umowy", "31-12-2026", "rok", "2026"),
            Map.of()));

        assertThat(allText(result)).isEqualTo("Umowa od 01-03-2026 do 31-12-2026 na rok 2026.");
    }

    @Test
    void handlesMultipleStrikeBlocksInSingleParagraph() throws IOException {
        byte[] template = templateWithText(
            "{#skresl:student_checkbox}Nie jestem{/skresl} / {#!skresl:student_checkbox}jestem{/skresl} studentem, " +
            "{#skresl:26lat_checkbox}nie mam{/skresl} / {#!skresl:26lat_checkbox}mam{/skresl} skończonych 26 lat i " +
            "{#skresl:legitymacja_checkbox}nie posiadam{/skresl} / {#!skresl:legitymacja_checkbox}posiadam{/skresl} legitymację.");

        // All true -> #skresl struck, #!skresl kept
        XWPFDocument docAllTrue = render(template, new TemplateData(Map.of(), Map.of(
            "student_checkbox", true,
            "26lat_checkbox", true,
            "legitymacja_checkbox", true
        )));
        assertThat(struckText(docAllTrue)).isEqualTo("Nie jestemnie mamnie posiadam");
        assertThat(allText(docAllTrue)).isEqualTo("Nie jestem / jestem studentem, nie mam / mam skończonych 26 lat i nie posiadam / posiadam legitymację.");

        // All false -> #skresl kept, #!skresl struck
        XWPFDocument docAllFalse = render(template, new TemplateData(Map.of(), Map.of(
            "student_checkbox", false,
            "26lat_checkbox", false,
            "legitymacja_checkbox", false
        )));
        assertThat(struckText(docAllFalse)).isEqualTo("jestemmamposiadam");

        // Mixed: student=true, 26lat=false, legitymacja=true
        XWPFDocument docMixed = render(template, new TemplateData(Map.of(), Map.of(
            "student_checkbox", true,
            "26lat_checkbox", false,
            "legitymacja_checkbox", true
        )));
        assertThat(struckText(docMixed)).isEqualTo("Nie jestemmamnie posiadam");
    }

    @Test
    void removesStrikethroughWhenBlockEvaluatesToNotStruckEvenIfPreStruckInTemplate() throws IOException {
        byte[] template;
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun r1 = paragraph.createRun();
            r1.setText("{#skresl:zgoda}Skreślone gdy prawda{/skresl}");
            r1.setStrikeThrough(true);
            XWPFRun rSpace = paragraph.createRun();
            rSpace.setText(" ");
            XWPFRun r2 = paragraph.createRun();
            r2.setText("{#!skresl:zgoda}Nieskreślone gdy prawda{/skresl}");
            r2.setStrikeThrough(true);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            template = output.toByteArray();
        }

        // zgoda = true -> #skresl struck, #!skresl UNSTRUCK (even though pre-struck in template)
        XWPFDocument docTrue = render(template, new TemplateData(Map.of(), Map.of("zgoda", true)));
        assertThat(struckText(docTrue)).isEqualTo("Skreślone gdy prawda");
        assertThat(allText(docTrue)).isEqualTo("Skreślone gdy prawda Nieskreślone gdy prawda");

        // zgoda = false -> #skresl UNSTRUCK, #!skresl STRUCK
        XWPFDocument docFalse = render(template, new TemplateData(Map.of(), Map.of("zgoda", false)));
        assertThat(struckText(docFalse)).isEqualTo("Nieskreślone gdy prawda");
        assertThat(allText(docFalse)).isEqualTo("Skreślone gdy prawda Nieskreślone gdy prawda");
    }

    @Test
    void rendersActualOswiadczenieTemplateWithCorrectStrikes() throws IOException {
        java.nio.file.Path oswiadczeniePath = java.nio.file.Path.of("data/WZÓR oświadczenie do umów zlecenia 2026 wersja zaktualizowana.docx");
        if (!java.nio.file.Files.exists(oswiadczeniePath)) {
            return;
        }

        byte[] template = java.nio.file.Files.readAllBytes(oswiadczeniePath);
        Map<String, String> values = Map.of(
            "nazwisko", "Kowalski",
            "imie", "Jan",
            "data_urodzenia", "01-01-1990",
            "miejsce_urodzenia", "Warszawa",
            "pesel", "90010112345",
            "adres_zameldowania", "ul. Główna 1, 00-001 Warszawa",
            "adres_zamieszkania", "ul. Główna 1, 00-001 Warszawa",
            "urzad_skarbowy", "US Warszawa",
            "data_zawarcia_umowy", "17-10-2026"
        );
        TemplateData data = new TemplateData(
            values,
            Map.ofEntries(
                Map.entry("bezrobotny_checkbox", false),
                Map.entry("student_checkbox", false),
                Map.entry("26lat_checkbox", false),
                Map.entry("legitymacja_studencka_checkbox", false),
                Map.entry("zatrudniony_checkbox", false),
                Map.entry("zatrudniony_uop_checkbox", false),
                Map.entry("zatrudniony_zlecenie_checkbox", false),
                Map.entry("podstawa_wymiaru_skladek_1_checkbox", false),
                Map.entry("podstawa_wymiaru_skladek_2_checkbox", false),
                Map.entry("podstawa_wymiaru_skladek_3_checkbox", false)
            )
        );

        XWPFDocument result = render(template, data);

        // Find the paragraph with "studentem / uczniem"
        XWPFParagraph studentParagraph = DocumentParagraphs.of(result).stream()
            .filter(p -> p.getText().contains("studentem / uczniem"))
            .findFirst()
            .orElseThrow();

        List<XWPFRun> runs = studentParagraph.getRuns();
        String struckInParagraph = runs.stream()
            .filter(XWPFRun::isStrikeThrough)
            .map(XWPFRun::text)
            .collect(Collectors.joining());

        String unstruckInParagraph = runs.stream()
            .filter(r -> !r.isStrikeThrough())
            .map(XWPFRun::text)
            .collect(Collectors.joining());

        // When student_checkbox=false, 26lat_checkbox=false, legitymacja_studencka_checkbox=false:
        // "Nie jestem" is UNSTRUCK, "jestem" is STRUCK
        // "mam" is UNSTRUCK, "nie mam" is STRUCK
        // "nie posiadam" is UNSTRUCK, "posiadam" is STRUCK
        assertThat(struckInParagraph).isEqualTo("jestemnie mamposiadam");
        assertThat(unstruckInParagraph).contains("Nie jestem").contains("mam").contains("nie posiadam");

        // When all are true:
        // "Nie jestem" is STRUCK, "jestem" is UNSTRUCK
        // "mam" is STRUCK, "nie mam" is UNSTRUCK
        // "nie posiadam" is STRUCK, "posiadam" is UNSTRUCK
        TemplateData dataTrue = new TemplateData(
            values,
            Map.ofEntries(
                Map.entry("bezrobotny_checkbox", true),
                Map.entry("student_checkbox", true),
                Map.entry("26lat_checkbox", true),
                Map.entry("legitymacja_studencka_checkbox", true),
                Map.entry("zatrudniony_checkbox", true),
                Map.entry("zatrudniony_uop_checkbox", true),
                Map.entry("zatrudniony_zlecenie_checkbox", true),
                Map.entry("podstawa_wymiaru_skladek_1_checkbox", true),
                Map.entry("podstawa_wymiaru_skladek_2_checkbox", true),
                Map.entry("podstawa_wymiaru_skladek_3_checkbox", true)
            )
        );
        XWPFDocument resultTrue = render(template, dataTrue);
        XWPFParagraph studentParagraphTrue = DocumentParagraphs.of(resultTrue).stream()
            .filter(p -> p.getText().contains("studentem / uczniem"))
            .findFirst()
            .orElseThrow();
        List<XWPFRun> runsTrue = studentParagraphTrue.getRuns();
        String struckInParagraphTrue = runsTrue.stream()
            .filter(XWPFRun::isStrikeThrough)
            .map(XWPFRun::text)
            .collect(Collectors.joining());
        String unstruckInParagraphTrue = runsTrue.stream()
            .filter(r -> !r.isStrikeThrough())
            .map(XWPFRun::text)
            .collect(Collectors.joining());

        assertThat(struckInParagraphTrue).isEqualTo("Nie jestemmamnie posiadam");
        assertThat(unstruckInParagraphTrue).isEqualTo(" / jestem studentem / uczniem, nie mam /  skończonych 26 lat i  / posiadam ważną legitymację szkolną / studencką, której kopię dołączam do niniejszego oświadczenia ");
    }

    private static TemplateData sampleData(boolean optionalParagraphApplies) {
        return new TemplateData(
            Map.of(
                "imie", "Jan",
                "nazwisko", "Kowalski",
                "pesel", "90010112345",
                "nazwa_firmy", "Firma z bardzo długą nazwą",
                "kwota", "1 234,50",
                "jednostka", "miesięcznie",
                "kwota_slownie", "tysiąc dwieście trzydzieści cztery PLN 50/100"),
            Map.of("dodatkowy_checkbox", optionalParagraphApplies));
    }

    private XWPFDocument renderSample(TemplateData data) throws IOException {
        try (InputStream template = getClass().getResourceAsStream("/data/testowy-dokument.docx")) {
            return render(template.readAllBytes(), data);
        }
    }

    private XWPFDocument render(byte[] template, TemplateData data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        renderer.render(new ByteArrayInputStream(template), data, output);
        return new XWPFDocument(new ByteArrayInputStream(output.toByteArray()));
    }

    private static byte[] templateWithText(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            return output.toByteArray();
        }
    }

    private static String allText(XWPFDocument document) {
        return DocumentParagraphs.of(document).stream()
            .map(paragraph -> new ParagraphText(paragraph).text())
            .collect(Collectors.joining("\n"));
    }

    private static String struckText(XWPFDocument document) {
        List<XWPFRun> runs = DocumentParagraphs.of(document).stream()
            .flatMap(paragraph -> paragraph.getRuns().stream())
            .toList();
        return runs.stream().filter(XWPFRun::isStrikeThrough).map(XWPFRun::text).collect(Collectors.joining());
    }
}
