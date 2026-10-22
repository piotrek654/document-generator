package pl.formularz.form;

import java.util.List;
import java.util.Optional;

/**
 * A form field declared in fields.yaml. The name is the placeholder used in templates.
 */
public sealed interface FieldDefinition {

    String name();

    String label();

    default Optional<String> enabledIf() {
        return Optional.empty();
    }

    default Optional<Object> disabledValue() {
        return Optional.empty();
    }

    default Optional<Object> defaultValue() {
        return Optional.empty();
    }

    record Text(
        String name,
        String label,
        Optional<String> enabledIf,
        Optional<Object> disabledValue,
        Optional<Object> defaultValue
    ) implements FieldDefinition {

        public Text(String name, String label) {
            this(name, label, Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    record Date(
        String name,
        String label,
        Optional<String> enabledIf,
        Optional<Object> disabledValue,
        Optional<Object> defaultValue
    ) implements FieldDefinition {

        public Date(String name, String label) {
            this(name, label, Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    record Select(
        String name,
        String label,
        List<String> options,
        Optional<String> enabledIf,
        Optional<Object> disabledValue,
        Optional<Object> defaultValue
    ) implements FieldDefinition {

        public Select {
            options = List.copyOf(options);
        }

        public Select(String name, String label, List<String> options) {
            this(name, label, options, Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    record Amount(
        String name,
        String label,
        Optional<String> enabledIf,
        Optional<Object> disabledValue,
        Optional<Object> defaultValue
    ) implements FieldDefinition {

        public Amount(String name, String label) {
            this(name, label, Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    /** Computed from another amount field; not editable in the form. */
    record AmountInWords(String name, String label, String source) implements FieldDefinition {
    }

    /** Drives {@code {#skresl:name}} blocks. */
    record Checkbox(
        String name,
        String label,
        Optional<String> enabledIf,
        Optional<Object> disabledValue,
        Optional<Object> defaultValue
    ) implements FieldDefinition {

        public Checkbox(String name, String label) {
            this(name, label, Optional.empty(), Optional.empty(), Optional.empty());
        }
    }
}
