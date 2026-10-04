package pl.formularz.template;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelTemplateRendererTest {

    private final TemplateRenderer renderer = new ExcelTemplateRenderer();

    @Test
    void fillsAllPlaceholdersInSampleXls() throws IOException {
        TemplateData data = new TemplateData(
            Map.of(
                "numer_umowy", "123/2026/GĘŚ",
                "data_zawarcia_umowy", "04-10-2026",
                "imie", "Michał",
                "nazwisko", "Żółtowski-Łącki"),
            Map.of());

        File xlsFile = new File("data/arkusz-testowy.xls");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(xlsFile)) {
            renderer.render(input, data, output);
        }

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            String docNumber = cellValue(sheet, 3, 3);
            String docDate = cellValue(sheet, 4, 3);
            String person = cellValue(sheet, 8, 3);

            assertThat(docNumber).isEqualTo("123/2026/GĘŚ");
            assertThat(docDate).isEqualTo("04-10-2026");
            assertThat(person).isEqualTo("Michał Żółtowski-Łącki");
        }
    }

    @Test
    void fillsXlsxAndHandlesStrikeBlocks() throws IOException {
        byte[] xlsxBytes = createXlsxWithText("Umowa: {numer_umowy}, Opcja: {#skresl:zgoda}Włączona{/skresl}");

        // zgoda = true -> keep content, remove markers
        ByteArrayOutputStream outTrue = new ByteArrayOutputStream();
        renderer.render(new ByteArrayInputStream(xlsxBytes),
            new TemplateData(Map.of("numer_umowy", "99/2026"), Map.of("zgoda", true)), outTrue);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(outTrue.toByteArray()))) {
            assertThat(cellValue(wb.getSheetAt(0), 0, 0)).isEqualTo("Umowa: 99/2026, Opcja: Włączona");
        }

        // zgoda = false -> remove block
        ByteArrayOutputStream outFalse = new ByteArrayOutputStream();
        renderer.render(new ByteArrayInputStream(xlsxBytes),
            new TemplateData(Map.of("numer_umowy", "99/2026"), Map.of("zgoda", false)), outFalse);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(outFalse.toByteArray()))) {
            assertThat(cellValue(wb.getSheetAt(0), 0, 0)).isEqualTo("Umowa: 99/2026, Opcja: ");
        }
    }

    @Test
    void rejectsMissingPlaceholderInCell() throws IOException {
        byte[] xlsxBytes = createXlsxWithText("Cześć {brakujace_pole}");

        assertThatThrownBy(() -> renderer.render(
            new ByteArrayInputStream(xlsxBytes),
            new TemplateData(Map.of(), Map.of()),
            new ByteArrayOutputStream()))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining("{brakujace_pole}");
    }

    @Test
    void rejectsUnclosedStrikeBlockInCell() throws IOException {
        byte[] xlsxBytes = createXlsxWithText("Test {#skresl:flaga}Niezamknięty");

        assertThatThrownBy(() -> renderer.render(
            new ByteArrayInputStream(xlsxBytes),
            new TemplateData(Map.of(), Map.of("flaga", true)),
            new ByteArrayOutputStream()))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining("{#skresl:flaga}");
    }

    private static byte[] createXlsxWithText(String text) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Arkusz1");
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(text);
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private static String cellValue(Sheet sheet, int rowNum, int colNum) {
        Row row = sheet.getRow(rowNum);
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(colNum);
        return cell == null ? null : cell.getStringCellValue();
    }
}
