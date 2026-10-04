package pl.formularz.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import pl.formularz.form.FieldCatalog;
import pl.formularz.form.FieldDefinition;
import pl.formularz.form.FormValues;
import pl.formularz.pdf.LibreOfficePdfConverter;
import pl.formularz.pdf.PdfBoxPdfMerger;
import pl.formularz.template.DocxTemplateRenderer;

/**
 * Generates a PDF without the UI, e.g.:
 * {@code GenerateCli data output/dokumenty.pdf imie=Jan kwota=123,45 dodatkowy_checkbox=false}.
 */
public final class GenerateCli {

    private static final Path CATALOG = Path.of("config/fields.yaml");
    private static final int FIRST_VALUE_ARG = 2;

    private GenerateCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < FIRST_VALUE_ARG) {
            System.err.println("Usage: GenerateCli <templates-dir> <output.pdf> [name=value ...]");
            System.exit(1);
        }
        FieldCatalog catalog = loadCatalog();
        FormValues values = FormValues.of(catalog, parseValues(catalog, args));
        try (LibreOfficePdfConverter converter = LibreOfficePdfConverter.start()) {
            Path pdf = new DocumentGenerator(new DocxTemplateRenderer(), converter, new PdfBoxPdfMerger())
                .generate(Path.of(args[0]), values.toTemplateData(), Path.of(args[1]));
            System.out.println("Generated " + pdf);
        }
    }

    static FieldCatalog loadCatalog() throws IOException {
        try (InputStream yaml = Files.newInputStream(CATALOG)) {
            return FieldCatalog.load(yaml);
        }
    }

    private static Map<String, Object> parseValues(FieldCatalog catalog, String[] args) {
        Map<String, Object> values = new HashMap<>();
        for (int i = FIRST_VALUE_ARG; i < args.length; i++) {
            String[] pair = args[i].split("=", 2);
            boolean isCheckbox = catalog.fields().stream()
                .anyMatch(field -> field instanceof FieldDefinition.Checkbox && field.name().equals(pair[0]));
            values.put(pair[0], isCheckbox ? Boolean.parseBoolean(pair[1]) : pair[1]);
        }
        return values;
    }
}
