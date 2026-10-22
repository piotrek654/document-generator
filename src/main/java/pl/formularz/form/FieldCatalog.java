package pl.formularz.form;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
            validate(file.fields());
            return new FieldCatalog(file.fields().stream().map(FieldSpec::toDefinition).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read field catalog", e);
        }
    }

    public static FieldCatalog load(Path yamlPath) {
        try (InputStream input = Files.newInputStream(yamlPath)) {
            return load(input);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read field catalog from " + yamlPath, e);
        }
    }

    private static void validate(List<FieldSpec> specs) {
        if (specs == null) {
            return;
        }
        Set<String> declaredCheckboxes = new HashSet<>();
        for (FieldSpec spec : specs) {
            if (spec.enabledIf() != null && !spec.enabledIf().isBlank()) {
                String source = ConditionEvaluator.getSourceCheckbox(spec.enabledIf());
                if (!declaredCheckboxes.contains(source)) {
                    throw new IllegalArgumentException(
                        "Pole „" + spec.name() + "” odwołuje się w enabled_if do nieznanego, późniejszego lub niebędącego checkboxem pola „" + source + "”."
                    );
                }
            }
            if ("checkbox".equals(spec.type())) {
                declaredCheckboxes.add(spec.name());
            }
        }
    }

    private record CatalogFile(List<FieldSpec> fields) {
    }

    private record FieldSpec(
        String name,
        String label,
        String type,
        List<String> options,
        String source,
        @JsonProperty("enabled_if") @JsonAlias("enabledIf") String enabledIf,
        @JsonProperty("disabled_value") @JsonAlias("disabledValue") Object disabledValue,
        @JsonProperty("default") @JsonAlias("defaultValue") Object defaultValue
    ) {

        FieldDefinition toDefinition() {
            Optional<String> enabledIfOpt = Optional.ofNullable(enabledIf).filter(s -> !s.isBlank());
            Optional<Object> disabledValueOpt = Optional.ofNullable(disabledValue);
            Optional<Object> defaultValueOpt = Optional.ofNullable(defaultValue);

            return switch (type) {
                case "text" -> new FieldDefinition.Text(name, label, enabledIfOpt, disabledValueOpt, defaultValueOpt);
                case "date" -> new FieldDefinition.Date(name, label, enabledIfOpt, disabledValueOpt, defaultValueOpt);
                case "select" -> new FieldDefinition.Select(name, label, options != null ? options : List.of(), enabledIfOpt, disabledValueOpt, defaultValueOpt);
                case "amount" -> new FieldDefinition.Amount(name, label, enabledIfOpt, disabledValueOpt, defaultValueOpt);
                case "amount_in_words" -> new FieldDefinition.AmountInWords(name, label, source);
                case "checkbox" -> new FieldDefinition.Checkbox(name, label, enabledIfOpt, disabledValueOpt, defaultValueOpt);
                default -> throw new IllegalArgumentException("Unknown type '" + type + "' of field '" + name + "'");
            };
        }
    }
}
