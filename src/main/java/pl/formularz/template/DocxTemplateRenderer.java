package pl.formularz.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * Renders .docx templates with {@code {name}} placeholders and {@code {#skresl:flag}...{/skresl}} blocks.
 * A block is struck through when its flag is false; the markers themselves are always removed.
 */
public class DocxTemplateRenderer implements TemplateRenderer {

    private static final Pattern STRIKE_BLOCK = Pattern.compile("\\{#skresl:([\\p{L}0-9_]+)}(.*?)\\{/skresl}");
    private static final Pattern STRIKE_MARKER = Pattern.compile("\\{#skresl:[\\p{L}0-9_]+}|\\{/skresl}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([\\p{L}0-9_]+)}");
    private static final int FLAG_GROUP = 1;
    private static final int CONTENT_GROUP = 2;
    private static final int NAME_GROUP = 1;

    @Override
    public void render(InputStream template, TemplateData data, OutputStream output) throws IOException {
        try (XWPFDocument document = new XWPFDocument(template)) {
            for (XWPFParagraph paragraph : DocumentParagraphs.of(document)) {
                ParagraphText text = new ParagraphText(paragraph);
                applyStrikeBlocks(text, data);
                replacePlaceholders(text, data);
            }
            document.write(output);
        }
    }

    private static void applyStrikeBlocks(ParagraphText text, TemplateData data) {
        // Right to left, so offsets of earlier matches stay valid after each edit.
        for (MatchResult block : matchesFromLast(STRIKE_BLOCK, text.text())) {
            String flag = block.group(FLAG_GROUP);
            Boolean applies = data.resolveFlag(flag);
            if (applies == null) {
                throw new TemplateException("Brak wartości pola wyboru '" + flag + "' dla bloku " + block.group());
            }
            if (!applies) {
                text.strike(block.start(CONTENT_GROUP), block.end(CONTENT_GROUP));
            }
            text.replace(block.end(CONTENT_GROUP), block.end(), "");
            text.replace(block.start(), block.start(CONTENT_GROUP), "");
        }
        STRIKE_MARKER.matcher(text.text()).results().findFirst().ifPresent(marker -> {
            throw new TemplateException("Niedomknięty blok przekreślenia: " + marker.group()
                + " w akapicie \"" + text.text() + "\"");
        });
    }

    private static void replacePlaceholders(ParagraphText text, TemplateData data) {
        for (MatchResult placeholder : matchesFromLast(PLACEHOLDER, text.text())) {
            String value = data.resolveText(placeholder.group(NAME_GROUP));
            if (value == null) {
                throw new TemplateException("Brak wartości dla " + placeholder.group()
                    + " w akapicie \"" + text.text() + "\"");
            }
            text.replace(placeholder.start(), placeholder.end(), value);
        }
    }

    private static List<MatchResult> matchesFromLast(Pattern pattern, String text) {
        return pattern.matcher(text).results().toList().reversed();
    }
}
