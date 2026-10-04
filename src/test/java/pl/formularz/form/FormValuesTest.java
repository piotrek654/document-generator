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
          - name: data_zawarcia
            label: Data zawarcia
            type: date
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
            "data_zawarcia", java.time.LocalDate.of(2026, 10, 4),
            "jednostka", "dziennie",
            "kwota", "1234,5",
            "zgoda", true)).toTemplateData();

        assertThat(data.texts()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "imie", "Jan",
            "data_zawarcia", "04-10-2026",
            "jednostka", "dziennie",
            "kwota", "1 234,50",
            "kwota_slownie", "tysiąc dwieście trzydzieści cztery złote pięćdziesiąt groszy"));
        assertThat(data.flags()).containsExactlyEntriesOf(Map.of("zgoda", true));
    }

    @Test
    void acceptsVariousDateStringFormatsAndNormalizesToDdMmYyyy() {
        TemplateData data = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "2026-10-04",
            "jednostka", "dziennie",
            "kwota", "100",
            "zgoda", true)).toTemplateData();

        assertThat(data.texts()).containsEntry("data_zawarcia", "04-10-2026");
    }

    @Test
    void rejectsInvalidDateFormat() {
        FormValues values = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "niepoprawna_data",
            "jednostka", "dziennie",
            "kwota", "100"));

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Data zawarcia")
            .hasMessageContaining("DD-MM-YYYY");
    }

    @Test
    void treatsMissingCheckboxAsUnchecked() {
        TemplateData data = FormValues.of(catalog, Map.of("imie", "Jan", "data_zawarcia", "2026-10-04", "jednostka", "dziennie", "kwota", "1"))
            .toTemplateData();

        assertThat(data.flags()).containsEntry("zgoda", false);
    }

    @Test
    void rejectsOptionOutsideSelectList() {
        FormValues values = FormValues.of(catalog, Map.of("imie", "Jan", "data_zawarcia", "2026-10-04", "jednostka", "tygodniowo", "kwota", "1"));

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Jednostka")
            .hasMessageContaining("tygodniowo");
    }

    @Test
    void reportsInvalidAmountByFieldLabel() {
        FormValues values = FormValues.of(catalog, Map.of("imie", "Jan", "data_zawarcia", "2026-10-04", "jednostka", "dziennie", "kwota", "dużo"));

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
            .hasMessageContaining("Data zawarcia")
            .hasMessageContaining("Jednostka")
            .hasMessageContaining("Kwota");
    }
}
