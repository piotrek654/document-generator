package pl.formularz.form;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NrbFormValuesTest {

    private static final String VALID = "61109010140000071219812874";
    private static final String FORMATTED = "61 1090 1014 0000 0712 1981 2874";

    private FieldCatalog catalog(String yaml) {
        return FieldCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private final FieldCatalog catalog = catalog("""
        fields:
          - name: konto
            label: Numer konta
            type: nrb
        """);

    @Test
    void acceptsAndFormatsNumberWithOrWithoutSpaces() {
        for (String value : new String[] {VALID, FORMATTED}) {
            assertThat(FormValues.of(catalog, Map.of("konto", value)).toTemplateData().texts())
                .containsEntry("konto", FORMATTED);
        }
    }

    @Test
    void rejectsMissingMalformedAndInvalidChecksum() {
        for (String value : new String[] {"", "6110901014000007121981287", "61109010140000071219812875",
            "611090101400000712198128740", "61-109010140000071219812874", "PL" + VALID,
            "99000000000000000000000036", "01000000000000000000000054", "00000000000000000000000072"}) {
            assertThatThrownBy(() -> FormValues.of(catalog, Map.of("konto", value)).toTemplateData())
                .isInstanceOf(FormException.class)
                .hasMessageContaining("Numer konta");
        }
        assertThatThrownBy(() -> FormValues.of(catalog, Map.of()).toTemplateData())
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Numer konta");
    }

    @Test
    void acceptsChecksumBoundsAndKeepsLeadingZeroes() {
        assertThat(FormValues.of(catalog, Map.of("konto", "02000000000000000000000036"))
            .toTemplateData().texts()).containsEntry("konto", "02 0000 0000 0000 0000 0000 0036");
        assertThat(FormValues.of(catalog, Map.of("konto", "98000000000000000000000054"))
            .toTemplateData().texts()).containsEntry("konto", "98 0000 0000 0000 0000 0000 0054");
    }

    @Test
    void supportsDefaultAndDisabledValues() {
        FieldCatalog conditional = catalog("""
            fields:
              - name: enabled
                label: Włączone
                type: checkbox
              - name: konto
                label: Numer konta
                type: nrb
                enabled_if: enabled
                default: "61109010140000071219812874"
                disabled_value: ""
            """);

        assertThat(FormValues.of(conditional, Map.of("enabled", true)).toTemplateData().texts())
            .containsEntry("konto", FORMATTED);
        assertThat(FormValues.of(conditional, Map.of("enabled", false, "konto", "niepoprawny"))
            .toTemplateData().texts()).containsEntry("konto", "");
    }

    @Test
    void formatsValidDisabledValueButPreservesNonNumberPlaceholder() {
        for (String value : new String[] {VALID, "nie dotyczy"}) {
            FieldCatalog conditional = catalog("""
                fields:
                  - name: enabled
                    label: Włączone
                    type: checkbox
                  - name: konto
                    label: Numer konta
                    type: nrb
                    enabled_if: enabled
                    disabled_value: "%s"
                """.formatted(value));
            assertThat(FormValues.of(conditional, Map.of("enabled", false)).toTemplateData().texts())
                .containsEntry("konto", value.equals(VALID) ? FORMATTED : value);
        }
    }
}