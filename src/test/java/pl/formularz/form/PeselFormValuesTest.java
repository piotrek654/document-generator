package pl.formularz.form;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pl.formularz.template.TemplateData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeselFormValuesTest {

    private static final String PESEL_FIELD = """
        fields:
          - name: pesel
            label: PESEL
            type: pesel
        """;

    @Test
    void acceptsValidPeselsFromDifferentCenturiesAndPreservesLeadingZeros() {
        for (String pesel : List.of(
            "00810100002",
            "00010100008",
            "00210100004",
            "00410100000",
            "00610100006",
            "24022900009")) {
            TemplateData data = FormValues.of(catalog(PESEL_FIELD), Map.of("pesel", pesel)).toTemplateData();

            assertThat(data.texts()).containsEntry("pesel", pesel);
        }
    }

    @Test
    void rejectsInvalidPeselLengthCharactersChecksumAndEncodedDates() {
        for (String pesel : List.of(
            "12345",
            "4405140145A",
            "44051401459",
            "24023100006",
            "23022900006",
            "00400100009")) {
            assertInvalidPesel(pesel);
        }
    }

    @Test
    void requiresPeselAndReportsItsFieldName() {
        assertThatThrownBy(() -> FormValues.of(catalog(PESEL_FIELD), Map.of()).toTemplateData())
            .isInstanceOf(FormException.class)
            .hasMessageContaining("PESEL")
            .satisfies(error -> assertThat(((FormException) error).fieldNames()).contains("pesel"));
    }

    @Test
    void usesAndValidatesDefaultPesel() {
        String yaml = """
            fields:
              - name: pesel
                label: PESEL
                type: pesel
                default: "00210100004"
            """;

        TemplateData data = FormValues.of(catalog(yaml), Map.of()).toTemplateData();

        assertThat(data.texts()).containsEntry("pesel", "00210100004");
    }

    @Test
    void disabledPeselUsesDisabledValueWithoutValidatingIt() {
        String yaml = """
            fields:
              - name: aktywny
                label: Aktywny
                type: checkbox
                default: false
              - name: pesel
                label: PESEL
                type: pesel
                enabled_if: "aktywny"
                disabled_value: "nie dotyczy"
            """;

        TemplateData data = FormValues.of(catalog(yaml), Map.of("aktywny", false)).toTemplateData();

        assertThat(data.texts()).containsEntry("pesel", "nie dotyczy");
    }

    private static void assertInvalidPesel(String pesel) {
        assertThatThrownBy(() -> FormValues.of(catalog(PESEL_FIELD), Map.of("pesel", pesel)).toTemplateData())
            .isInstanceOf(FormException.class)
            .hasMessageContaining("PESEL")
            .satisfies(error -> assertThat(((FormException) error).fieldNames()).contains("pesel"));
    }

    private static FieldCatalog catalog(String yaml) {
        return FieldCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}