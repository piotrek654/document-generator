package pl.formularz.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import pl.formularz.pdf.PdfBoxPdfMerger;
import pl.formularz.pdf.PdfConverter;
import pl.formularz.template.DocxTemplateRenderer;
import pl.formularz.template.ExcelTemplateRenderer;
import pl.formularz.template.TemplateData;
import pl.formularz.template.TemplateException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentGeneratorTest {

    private static final float MARGIN = 50;
    private static final float FONT_SIZE = 12;

    @TempDir
    Path workDir;

    private final TextPdfConverter converter = new TextPdfConverter();
    private final DocumentGenerator generator =
        new DocumentGenerator(new DocxTemplateRenderer(), new ExcelTemplateRenderer(), converter, new PdfBoxPdfMerger());

    @Test
    void mergesAllTemplatesInAlphabeticalOrderIntoOnePdf() throws IOException {
        Path templates = templatesDir("02_umowa.docx", "Umowa {imie}", "01_wniosek.docx", "Wniosek {imie}",
            "03_oswiadczenie.docx", "Oswiadczenie {imie}");
        Path output = workDir.resolve("output/dokumenty.pdf");

        Path pdf = generator.generate(templates, data(), output);

        assertThat(pdf).isEqualTo(output);
        assertThat(pagesOf(pdf)).containsExactly("Wniosek Jan", "Umowa Jan", "Oswiadczenie Jan");
    }

    @Test
    void mergesDocxAndSpreadsheetTemplatesTogether() throws IOException {
        Path dir = Files.createDirectories(workDir.resolve("mixed-templates"));
        createDocx(dir.resolve("01_wniosek.docx"), "Wniosek {imie}");
        createXlsx(dir.resolve("02_arkusz.xlsx"), "Arkusz {imie}");
        createDocx(dir.resolve("03_umowa.docx"), "Umowa {imie}");

        Path output = workDir.resolve("output/mixed.pdf");
        Path pdf = generator.generate(dir, data(), output);

        assertThat(pdf).isEqualTo(output);
        assertThat(pagesOf(pdf)).containsExactly("Wniosek Jan", "Arkusz Jan", "Umowa Jan");
    }

    @Test
    void ignoresOfficeLockFilesAndOtherFiles() throws IOException {
        Path templates = templatesDir("wniosek.docx", "Wniosek {imie}", "~$wniosek.docx", "Word lock {imie}");
        Files.writeString(templates.resolve(".~lock.wniosek.docx#"), "LibreOffice lock");
        Files.writeString(templates.resolve("notatki.txt"), "notes");

        Path pdf = generator.generate(templates, data(), workDir.resolve("dokumenty.pdf"));

        assertThat(pagesOf(pdf)).containsExactly("Wniosek Jan");
    }

    @Test
    void removesIntermediateFilesAfterMerging() throws IOException {
        Path templates = templatesDir("a.docx", "A {imie}", "b.docx", "B {imie}");

        generator.generate(templates, data(), workDir.resolve("dokumenty.pdf"));

        assertThat(converter.touchedFiles).hasSize(4).allSatisfy(file -> assertThat(file).doesNotExist());
    }

    @Test
    void namesTemplateThatCannotBeFilled() throws IOException {
        Path templates = templatesDir("a.docx", "A {imie}", "b.docx", "B {nieznane}");

        assertThatThrownBy(() -> generator.generate(templates, data(), workDir.resolve("dokumenty.pdf")))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining("b.docx")
            .hasMessageContaining("{nieznane}");
    }

    @Test
    void failsWhenDirectoryHasNoTemplates() throws IOException {
        Path empty = Files.createDirectories(workDir.resolve("empty"));

        assertThatThrownBy(() -> generator.generate(empty, data(), workDir.resolve("dokumenty.pdf")))
            .isInstanceOf(TemplateException.class)
            .hasMessageContaining(empty.toString());
    }

    private static TemplateData data() {
        return new TemplateData(Map.of("imie", "Jan"), Map.of());
    }

    private Path templatesDir(String... namesAndTexts) throws IOException {
        Path dir = Files.createDirectories(workDir.resolve("templates"));
        for (int i = 0; i < namesAndTexts.length; i += 2) {
            createDocx(dir.resolve(namesAndTexts[i]), namesAndTexts[i + 1]);
        }
        return dir;
    }

    private static void createDocx(Path target, String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             OutputStream output = Files.newOutputStream(target)) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
    }

    private static void createXlsx(Path target, String text) throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             OutputStream output = Files.newOutputStream(target)) {
            Sheet sheet = wb.createSheet("Arkusz1");
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(text);
            wb.write(output);
        }
    }

    private static List<String> pagesOf(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            List<String> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document).strip());
            }
            return pages;
        }
    }

    /** Stands in for LibreOffice: writes a one-page PDF with the plain text of the filled document. */
    private static final class TextPdfConverter implements PdfConverter {

        private final List<Path> touchedFiles = new ArrayList<>();

        @Override
        public void convert(Path document, Path pdf) {
            touchedFiles.add(document);
            touchedFiles.add(pdf);
            String text = extractText(document);
            try (PDDocument output = new PDDocument()) {
                PDPage page = new PDPage();
                output.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(output, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
                    content.newLineAtOffset(MARGIN, page.getMediaBox().getHeight() - MARGIN);
                    content.showText(text);
                    content.endText();
                }
                output.save(pdf.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static String extractText(Path document) {
            String fileName = document.getFileName().toString().toLowerCase(Locale.ROOT);
            try (InputStream input = Files.newInputStream(document)) {
                if (fileName.endsWith(".docx")) {
                    try (XWPFDocument doc = new XWPFDocument(input);
                         XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                        return extractor.getText().strip();
                    }
                } else {
                    try (Workbook wb = WorkbookFactory.create(input)) {
                        StringBuilder sb = new StringBuilder();
                        for (Sheet sheet : wb) {
                            for (Row row : sheet) {
                                for (Cell cell : row) {
                                    sb.append(cell.getStringCellValue()).append(" ");
                                }
                            }
                        }
                        return sb.toString().strip();
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
