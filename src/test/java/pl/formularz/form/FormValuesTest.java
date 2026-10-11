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
          - name: data_rozpoczecia_umowy
            label: Data rozpoczęcia umowy
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
            "data_rozpoczecia_umowy", java.time.LocalDate.of(2026, 3, 1),
            "jednostka", "dziennie",
            "kwota", "1234,5",
            "zgoda", true)).toTemplateData();

        assertThat(data.texts()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "imie", "Jan",
            "data_zawarcia", "04-10-2026",
            "data_rozpoczecia_umowy", "01-03-2026",
            "data_zakonczenia_umowy", "31-12-2026",
            "rok", "2026",
            "jednostka", "dziennie",
            "kwota", "1 234,50",
            "kwota_slownie", "tysiąc dwieście trzydzieści cztery PLN 50/100"));
        assertThat(data.flags()).containsExactlyEntriesOf(Map.of("zgoda", true));
    }

    @Test
    void acceptsVariousDateStringFormatsAndNormalizesToDdMmYyyy() {
        TemplateData data = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "2026-10-04",
            "data_rozpoczecia_umowy", "2026-03-01",
            "jednostka", "dziennie",
            "kwota", "100",
            "zgoda", true)).toTemplateData();

        assertThat(data.texts()).containsEntry("data_zawarcia", "04-10-2026");
        assertThat(data.texts()).containsEntry("data_rozpoczecia_umowy", "01-03-2026");
        assertThat(data.texts()).containsEntry("data_zakonczenia_umowy", "31-12-2026");
        assertThat(data.texts()).containsEntry("rok", "2026");
    }

    @Test
    void rejectsInvalidDateFormat() {
        FormValues values = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "niepoprawna_data",
            "data_rozpoczecia_umowy", "2026-03-01",
            "jednostka", "dziennie",
            "kwota", "100"));

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Data zawarcia")
            .hasMessageContaining("DD-MM-YYYY");
    }

    @Test
    void treatsMissingCheckboxAsUnchecked() {
        TemplateData data = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "2026-10-04",
            "data_rozpoczecia_umowy", "2026-03-01",
            "jednostka", "dziennie",
            "kwota", "1"))
            .toTemplateData();

        assertThat(data.flags()).containsEntry("zgoda", false);
    }

    @Test
    void rejectsOptionOutsideSelectList() {
        FormValues values = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "2026-10-04",
            "data_rozpoczecia_umowy", "2026-03-01",
            "jednostka", "tygodniowo",
            "kwota", "1"));

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Jednostka")
            .hasMessageContaining("tygodniowo");
    }

    @Test
    void reportsInvalidAmountByFieldLabel() {
        FormValues values = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "2026-10-04",
            "data_rozpoczecia_umowy", "2026-03-01",
            "jednostka", "dziennie",
            "kwota", "dużo"));

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
            .hasMessageContaining("Data rozpoczęcia umowy")
            .hasMessageContaining("Jednostka")
            .hasMessageContaining("Kwota");
    }

    @Test
    void acceptsSpacesAsValidTextValue() {
        TemplateData data = FormValues.of(catalog, Map.of(
            "imie", " ",
            "data_zawarcia", "04-10-2026",
            "data_rozpoczecia_umowy", "01-03-2026",
            "jednostka", "dziennie",
            "kwota", "100"
        )).toTemplateData();

        assertThat(data.texts()).containsEntry("imie", " ");
    }

    @Test
    void rejectsEmptyStringForTextField() {
        FormValues values = FormValues.of(catalog, Map.of(
            "imie", "",
            "data_zawarcia", "04-10-2026",
            "data_rozpoczecia_umowy", "01-03-2026",
            "jednostka", "dziennie",
            "kwota", "100"
        ));

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Imię");
    }

    @Test
    void includesProblemFieldNamesInException() {
        FormValues values = FormValues.of(catalog, Map.of());

        try {
            values.toTemplateData();
        } catch (FormException e) {
            assertThat(e.fieldNames()).contains("imie", "data_zawarcia", "data_rozpoczecia_umowy", "jednostka", "kwota");
        }
    }

    @Test
    void handlesMapWithNullValuesGracefullyWithoutThrowingNullPointerException() {
        java.util.Map<String, Object> inputWithNulls = new java.util.HashMap<>();
        inputWithNulls.put("imie", null);
        inputWithNulls.put("jednostka", null);
        inputWithNulls.put("kwota", null);
        inputWithNulls.put("data_zawarcia", null);
        inputWithNulls.put("data_rozpoczecia_umowy", null);

        FormValues values = FormValues.of(catalog, inputWithNulls);

        assertThatThrownBy(values::toTemplateData)
            .isInstanceOf(FormException.class)
            .hasMessageContaining("Imię")
            .hasMessageContaining("Data zawarcia")
            .hasMessageContaining("Data rozpoczęcia umowy")
            .hasMessageContaining("Jednostka")
            .hasMessageContaining("Kwota");
    }

    @Test
    void computesDataZakonczeniaUmowyAndRokFromStartDate() {
        TemplateData data = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "01-01-2025",
            "data_rozpoczecia_umowy", "2025-05-15",
            "jednostka", "dziennie",
            "kwota", "100",
            "zgoda", true)).toTemplateData();

        assertThat(data.texts())
            .containsEntry("data_rozpoczecia_umowy", "15-05-2025")
            .containsEntry("data_zakonczenia_umowy", "31-12-2025")
            .containsEntry("rok", "2025");
    }

    @Test
    void computesDataZakonczeniaUmowyAndRokForLeapYear() {
        TemplateData data = FormValues.of(catalog, Map.of(
            "imie", "Jan",
            "data_zawarcia", "01-01-2024",
            "data_rozpoczecia_umowy", "29.02.2024",
            "jednostka", "dziennie",
            "kwota", "100")).toTemplateData();

        assertThat(data.texts())
            .containsEntry("data_rozpoczecia_umowy", "29-02-2024")
            .containsEntry("data_zakonczenia_umowy", "31-12-2024")
            .containsEntry("rok", "2024");
    }
}
