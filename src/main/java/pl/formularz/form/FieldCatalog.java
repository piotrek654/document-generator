package pl.formularz.form;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Field definitions read from fields.yaml, in declaration order (the order of the form).
 */
public record FieldCatalog(List<FieldDefinition> fields) {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    public FieldCatalog {
        fields = List.copyOf(fields);
    }

    public static FieldCatalog load(InputStream yaml) {
        try {
            CatalogFile file = YAML.readValue(yaml, CatalogFile.class);
            return new FieldCatalog(file.fields().stream().map(FieldSpec::toDefinition).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read field catalog", e);
        }
    }

    private record CatalogFile(List<FieldSpec> fields) {
    }

    private record FieldSpec(String name, String label, String type, List<String> options, String source) {

        FieldDefinition toDefinition() {
            return switch (type) {
                case "text" -> new FieldDefinition.Text(name, label);
                case "date" -> new FieldDefinition.Date(name, label);
                case "select" -> new FieldDefinition.Select(name, label, options);
                case "amount" -> new FieldDefinition.Amount(name, label);
                case "amount_in_words" -> new FieldDefinition.AmountInWords(name, label, source);
                case "checkbox" -> new FieldDefinition.Checkbox(name, label);
                default -> throw new IllegalArgumentException("Unknown type '" + type + "' of field '" + name + "'");
            };
        }
    }
}
