package pl.formularz.form;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pl.formularz.template.TemplateData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormValuesTest {

    private static final String CATALOG_YAML = """
        fields:
          - name: imie
            label: Imię
            type: text
          - name: jednostka
            label: Jednostka
            type: select
            options: [miesięcznie, dziennie, godzinowo]
          - name: kwota
            label: Kwota
            type: amount
          - name: kwota_slownie
            label: Kwota słownie
            type: amount_in_words
            source: kwota
          - name: zgoda
            label: Zgoda
            type: checkbox
        """;

    private final FieldCatalog catalog = FieldCatalog.load(
        new ByteArrayInputStream(CATALOG_YAML.getBytes(StandardCharsets.UTF_8)));

    @Test
    void buildsTemplateDataWithFormattedAndComputedValues() {
        TemplateData data = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "jednostka", "dziennie",
            "kwota", "1234,5",
            "zgoda", true)).toTemplateData();

        assertThat(data.texts()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "imie", "Jan",
            "jednostka", "dziennie",
            "kwota", "1 234,50",
            "kwota_slownie", "tysiąc dwieście trzydzieści cztery złote pięćdziesiąt groszy"));
        assertThat(data.flags()).containsExactlyEntriesOf(Map.of("zgoda", true));
    }

    @Test
    void treatsMissingCheckboxAsUnchecked() {
        TemplateData data = FormValues.of(catalog, Map.of("imie", "Jan", "jednostka", "dziennie", "kwota", "1"))
            .toTemplateData();

        assertThat(data.flags()).containsEntry("zgoda", false);
    }

    @Test
    void rejectsOptionOutsideSelectList() {
        FormValues values = FormValues.of(catalog, Map.of("imie", "Jan", "jednostka", "tygodniowo", "kwota", "1"));

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Jednostka")
            .hasMessageContaining("tygodniowo");
    }

    @Test
    void reportsInvalidAmountByFieldLabel() {
        FormValues values = FormValues.of(catalog, Map.of("imie", "Jan", "jednostka", "dziennie", "kwota", "dużo"));

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Kwota")
            .hasMessageContaining("dużo");
    }

    @Test
    void reportsEveryMissingRequiredValue() {
        FormValues values = FormValues.of(catalog, Map.of());

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Imię")
            .hasMessageContaining("Jednostka")
            .hasMessageContaining("Kwota");
    }
}
