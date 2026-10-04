package pl.formularz.template;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Renders .xls and .xlsx spreadsheet templates with {@code {name}} placeholders
 * and {@code {#skresl:flag}...{/skresl}} blocks across all sheets.
 */
public class ExcelTemplateRenderer implements TemplateRenderer {

    private static final Pattern STRIKE_BLOCK = Pattern.compile("\\{#skresl:([\\p{L}0-9_]+)}(.*?)\\{/skresl}");
    private static final Pattern STRIKE_MARKER = Pattern.compile("\\{#skresl:[\\p{L}0-9_]+}|\\{/skresl}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([\\p{L}0-9_]+)}");
    private static final int FLAG_GROUP = 1;
    private static final int CONTENT_GROUP = 2;
    private static final int NAME_GROUP = 1;

    @Override
    public void render(InputStream template, TemplateData data, OutputStream output) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(template)) {
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            String text = cell.getStringCellValue();
                            if (text != null && !text.isEmpty()) {
                                String rendered = renderText(text, data);
                                cell.setCellValue(rendered);
                            }
                        }
                    }
                }
            }
            workbook.write(output);
        }
    }

    private static String renderText(String text, TemplateData data) {
        String result = applyStrikeBlocks(text, data);
        return replacePlaceholders(result, data);
    }

    private static String applyStrikeBlocks(String text, TemplateData data) {
        StringBuilder sb = new StringBuilder(text);
        List<MatchResult> matches = STRIKE_BLOCK.matcher(sb).results().toList().reversed();
        for (MatchResult block : matches) {
            String flag = block.group(FLAG_GROUP);
            Boolean applies = data.resolveFlag(flag);
            if (applies == null) {
                throw new TemplateException("Brak wartości pola wyboru '" + flag + "' dla bloku " + block.group());
            }
            if (applies) {
                // Keep content, remove markers
                sb.replace(block.end(CONTENT_GROUP), block.end(), "");
                sb.replace(block.start(), block.start(CONTENT_GROUP), "");
            } else {
                // Not applicable - remove entire block
                sb.replace(block.start(), block.end(), "");
            }
        }
        STRIKE_MARKER.matcher(sb).results().findFirst().ifPresent(marker -> {
            throw new TemplateException("Niedomknięty blok przekreślenia: " + marker.group()
                + " w komórce \"" + text + "\"");
        });
        return sb.toString();
    }

    private static String replacePlaceholders(String text, TemplateData data) {
        StringBuilder sb = new StringBuilder(text);
        List<MatchResult> matches = PLACEHOLDER.matcher(sb).results().toList().reversed();
        for (MatchResult placeholder : matches) {
            String name = placeholder.group(NAME_GROUP);
            String value = data.resolveText(name);
            if (value == null) {
                throw new TemplateException("Brak wartości dla " + placeholder.group()
                    + " w komórce \"" + text + "\"");
            }
            sb.replace(placeholder.start(), placeholder.end(), value);
        }
        return sb.toString();
    }
}
