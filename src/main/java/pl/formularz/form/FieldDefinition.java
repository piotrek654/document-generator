package pl.formularz.form;

import java.util.List;

/**
 * A form field declared in fields.yaml. The name is the placeholder used in templates.
 */
public sealed interface FieldDefinition {

    String name();

    String label();

    record Text(String name, String label) implements FieldDefinition {
    }

    record Select(String name, String label, List<String> options) implements FieldDefinition {

        public Select {
            options = List.copyOf(options);
        }
    }

    record Amount(String name, String label) implements FieldDefinition {
    }

    /** Computed from another amount field; not editable in the form. */
    record AmountInWords(String name, String label, String source) implements FieldDefinition {
    }

    /** Drives {@code {#skresl:name}} blocks. */
    record Checkbox(String name, String label) implements FieldDefinition {
    }
}
