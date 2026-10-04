package pl.formularz.template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxTemplateRendererTest {

    private static final String OPTIONAL_PARAGRAPH =
        "Ten dodatkowy akapit może tu być jak jest, lub może być przekreślony jeśli nie dotyczy.";

    private final TemplateRenderer renderer = new DocxTemplateRenderer();

    @Test
    void fillsAllPlaceholdersOfSampleTemplate() throws IOException {
        String text = allText(renderSample(sampleData(true)));

        assertThat(text)
            .contains("Jan Kowalski")
            .contains("90010112345")
            .contains("dla Firma z bardzo długą nazwą i coś wnosi")
            .contains("Mamy też 1 234,50 zł za miesięcznie, słownie tysiąc dwieście trzydzieści cztery złote pięćdziesiąt groszy.")
            .doesNotContain("{", "}");
    }

    @Test
    void strikesOptionalParagraphWhenCheckboxIsUnchecked() throws IOException {
        XWPFDocument result = renderSample(sampleData(false));

        assertThat(struckText(result)).isEqualTo(OPTIONAL_PARAGRAPH);
        assertThat(allText(result)).contains(OPTIONAL_PARAGRAPH).doesNotContain("skresl");
    }

    @Test
    void leavesOptionalParagraphIntactWhenCheckboxIsChecked() throws IOException {
        XWPFDocument result = renderSample(sampleData(true));

        assertThat(struckText(result)).isEmpty();
        assertThat(allText(result)).contains(OPTIONAL_PARAGRAPH).doesNotContain("skresl");
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

    private static TemplateData sampleData(boolean optionalParagraphApplies) {
        return new TemplateData(
            Map.of(
                "imie", "Jan",
                "nazwisko", "Kowalski",
                "pesel", "90010112345",
                "nazwa_firmy", "Firma z bardzo długą nazwą",
                "kwota", "1 234,50",
                "jednostka", "miesięcznie",
                "kwota_slownie", "tysiąc dwieście trzydzieści cztery złote pięćdziesiąt groszy"),
            Map.of("dodatkowy_checkbox", optionalParagraphApplies));
    }

    private XWPFDocument renderSample(TemplateData data) throws IOException {
        try (InputStream template = getClass().getResourceAsStream("/templates/testowy-dokument.docx")) {
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
