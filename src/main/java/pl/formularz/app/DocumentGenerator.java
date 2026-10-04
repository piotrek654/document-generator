package pl.formularz.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import pl.formularz.pdf.PdfConverter;
import pl.formularz.pdf.PdfMerger;
import pl.formularz.template.TemplateData;
import pl.formularz.template.TemplateException;
import pl.formularz.template.TemplateRenderer;

/**
 * Fills every .docx template of a directory (in file name order) and merges the results into one PDF.
 * Filled documents hold personal data, so they live only in a temp directory removed afterwards.
 */
public class DocumentGenerator {

    private final TemplateRenderer renderer;
    private final PdfConverter converter;
    private final PdfMerger merger;

    public DocumentGenerator(TemplateRenderer renderer, PdfConverter converter, PdfMerger merger) {
        this.renderer = renderer;
        this.converter = converter;
        this.merger = merger;
    }

    public Path generate(Path templatesDir, TemplateData data, Path pdf) {
        List<Path> templates = templatesIn(templatesDir);
        if (templates.isEmpty()) {
            throw new TemplateException("Brak szablonów .docx w katalogu " + templatesDir);
        }
        Path workDir = createTempDirectory();
        try {
            List<Path> parts = new ArrayList<>();
            for (int i = 0; i < templates.size(); i++) {
                Path filled = workDir.resolve(i + ".docx");
                render(templates.get(i), data, filled);
                Path part = workDir.resolve(i + ".pdf");
                converter.convert(filled, part);
                parts.add(part);
            }
            createParentDirectories(pdf);
            merger.merge(parts, pdf);
            return pdf;
        } finally {
            deleteRecursively(workDir);
        }
    }

    /** Office lock files ("~$name.docx" from Word, ".~lock.name.docx#" from LibreOffice) are skipped. */
    private static List<Path> templatesIn(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files
                .filter(Files::isRegularFile)
                .filter(file -> isTemplate(file.getFileName().toString()))
                .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list templates in " + dir, e);
        }
    }

    private static boolean isTemplate(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".docx")
            && !fileName.startsWith("~$")
            && !fileName.startsWith(".~lock");
    }

    private void render(Path template, TemplateData data, Path filled) {
        try (InputStream input = Files.newInputStream(template);
             OutputStream output = Files.newOutputStream(filled)) {
            renderer.render(input, data, output);
        } catch (TemplateException e) {
            throw new TemplateException("Szablon " + template.getFileName() + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot fill template " + template, e);
        }
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("formularz-");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create a temp directory", e);
        }
    }

    private static void createParentDirectories(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + parent, e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
        } catch (IOException e) {
            dir.toFile().deleteOnExit();
        }
    }
}
