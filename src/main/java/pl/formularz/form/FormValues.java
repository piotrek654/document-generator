package pl.formularz.form;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pl.formularz.amount.Amount;
import pl.formularz.template.TemplateData;

/**
 * Raw values entered in the form (String for text, select and amount fields; Boolean for checkboxes; LocalDate/String for dates).
 */
public final class FormValues {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final FieldCatalog catalog;
    private final Map<String, Object> input;

    private FormValues(FieldCatalog catalog, Map<String, Object> input) {
        this.catalog = catalog;
        this.input = Map.copyOf(input);
    }

    public static FormValues of(FieldCatalog catalog, Map<String, Object> input) {
        return new FormValues(catalog, input);
    }

    /** Validates, formats and computes values; throws {@link FormException} listing all problems. */
    public TemplateData toTemplateData() {
        Map<String, String> texts = new HashMap<>();
        Map<String, Boolean> flags = new HashMap<>();
        Map<String, Amount> amounts = new HashMap<>();
        List<String> problems = new ArrayList<>();

        for (FieldDefinition field : catalog.fields()) {
            switch (field) {
                case FieldDefinition.Text text -> required(text, problems).ifPresent(v -> texts.put(text.name(), v));
                case FieldDefinition.Date dateField -> required(dateField, problems).ifPresent(value -> {
                    Object raw = input.get(dateField.name());
                    if (raw instanceof LocalDate ld) {
                        texts.put(dateField.name(), ld.format(DATE_FORMATTER));
                    } else {
                        try {
                            LocalDate parsed = parseDate(value);
                            texts.put(dateField.name(), parsed.format(DATE_FORMATTER));
                        } catch (DateTimeParseException e) {
                            problems.add("Pole „" + dateField.label() + "”: niepoprawny format daty „" + value + "” (wymagany format DD-MM-YYYY).");
                        }
                    }
                });
                case FieldDefinition.Select select -> required(select, problems).ifPresent(value -> {
                    if (select.options().contains(value)) {
                        texts.put(select.name(), value);
                    } else {
                        problems.add("Pole „" + select.label() + "”: niedozwolona wartość „" + value + "”.");
                    }
                });
                case FieldDefinition.Amount amountField -> required(amountField, problems).ifPresent(value -> {
                    try {
                        Amount amount = Amount.parse(value);
                        amounts.put(amountField.name(), amount);
                        texts.put(amountField.name(), amount.formatPl());
                    } catch (IllegalArgumentException e) {
                        problems.add("Pole „" + amountField.label() + "”: niepoprawna kwota „" + value + "”.");
                    }
                });
                case FieldDefinition.Checkbox checkbox ->
                    flags.put(checkbox.name(), Boolean.TRUE.equals(input.get(checkbox.name())));
                case FieldDefinition.AmountInWords ignored -> {
                    // computed below, once all source amounts are known
                }
            }
        }
        for (FieldDefinition field : catalog.fields()) {
            if (field instanceof FieldDefinition.AmountInWords words && amounts.containsKey(words.source())) {
                texts.put(words.name(), amounts.get(words.source()).inWords());
            }
        }
        if (!problems.isEmpty()) {
            throw new FormException(problems);
        }
        return new TemplateData(texts, flags);
    }

    private static LocalDate parseDate(String value) {
        String text = value.trim();
        if (text.matches("\\d{2}-\\d{2}-\\d{4}")) {
            return LocalDate.parse(text, DATE_FORMATTER);
        }
        if (text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        }
        if (text.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) {
            return LocalDate.parse(text, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
        return LocalDate.parse(text, DATE_FORMATTER);
    }

    private Optional<String> required(FieldDefinition field, List<String> problems) {
        Object value = input.get(field.name());
        String text = value == null ? "" : value.toString().strip();
        if (text.isEmpty()) {
            problems.add("Pole „" + field.label() + "” jest wymagane.");
            return Optional.empty();
        }
        return Optional.of(text);
    }
}
