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
