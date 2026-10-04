package pl.formularz.template;

import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParagraphTextTest {

    private final XWPFDocument document = new XWPFDocument();

    @Test
    void joinsTextOfAllRuns() {
        XWPFParagraph paragraph = paragraphOf("Pan ", "{imi", "e", "}", " Kowalski");

        assertThat(new ParagraphText(paragraph).text()).isEqualTo("Pan {imie} Kowalski");
    }

    @Test
    void replacesTokenSplitAcrossRuns() {
        XWPFParagraph paragraph = paragraphOf("Pan ", "{imi", "e", "}", " Kowalski");
        ParagraphText text = new ParagraphText(paragraph);

        text.replace(4, 10, "Jan");

        assertThat(new ParagraphText(paragraph).text()).isEqualTo("Pan Jan Kowalski");
    }

    @Test
    void replacementKeepsFormattingOfRunWhereTokenStarts() {
        XWPFParagraph paragraph = paragraphOf("Pan ", "{imi", "e}");
        paragraph.getRuns().get(1).setBold(true);

        new ParagraphText(paragraph).replace(4, 10, "Jan");

        assertThat(textOfRunsWhere(paragraph, XWPFRun::isBold)).containsExactly("Jan");
    }

    @Test
    void replacesTokenInsideSingleRun() {
        XWPFParagraph paragraph = paragraphOf("dla {firma} i coś");

        new ParagraphText(paragraph).replace(4, 11, "Firma 1");

        assertThat(new ParagraphText(paragraph).text()).isEqualTo("dla Firma 1 i coś");
    }

    @Test
    void strikesExactlyGivenRangeSplittingRunsAtBoundaries() {
        XWPFParagraph paragraph = paragraphOf("Ala ma ", "kota i ", "psa.");

        new ParagraphText(paragraph).strike(4, 11);

        assertThat(new ParagraphText(paragraph).text()).isEqualTo("Ala ma kota i psa.");
        assertThat(String.join("", textOfRunsWhere(paragraph, XWPFRun::isStrikeThrough))).isEqualTo("ma kota");
    }

    @Test
    void splitRunsKeepOriginalFormatting() {
        XWPFParagraph paragraph = paragraphOf("Ala ma kota");
        paragraph.getRuns().getFirst().setBold(true);

        new ParagraphText(paragraph).strike(4, 6);

        assertThat(paragraph.getRuns()).allMatch(XWPFRun::isBold);
    }

    private XWPFParagraph paragraphOf(String... runTexts) {
        XWPFParagraph paragraph = document.createParagraph();
        for (String runText : runTexts) {
            paragraph.createRun().setText(runText);
        }
        return paragraph;
    }

    private static List<String> textOfRunsWhere(XWPFParagraph paragraph, java.util.function.Predicate<XWPFRun> condition) {
        return paragraph.getRuns().stream()
            .filter(condition)
            .map(XWPFRun::text)
            .filter(runText -> !runText.isEmpty())
            .toList();
    }
}
