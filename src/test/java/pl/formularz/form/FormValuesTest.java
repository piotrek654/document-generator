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

    @Test
    void disablesFieldAndAppliesDisabledValueWhenConditionNotMet() {
        String yaml = """
            fields:
              - name: bezrobotny
                label: Bezrobotny
                type: checkbox
              - name: urzad
                label: Urząd skarbowy
                type: text
                enabled_if: "!bezrobotny"
                disabled_value: "Brak"
              - name: student
                label: Student
                type: checkbox
              - name: legitymacja
                label: Legitymacja
                type: checkbox
                enabled_if: "student"
                disabled_value: false
            """;
        FieldCatalog cat = FieldCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        // Case 1: bezrobotny=true -> urzad disabled ("Brak"), student=false -> legitymacja disabled (false)
        TemplateData data1 = FormValues.of(cat, Map.of("bezrobotny", true, "student", false)).toTemplateData();
        assertThat(data1.texts()).containsEntry("urzad", "Brak");
        assertThat(data1.flags()).containsEntry("bezrobotny", true);
        assertThat(data1.flags()).containsEntry("student", false);
        assertThat(data1.flags()).containsEntry("legitymacja", false);

        // Case 2: bezrobotny=false -> urzad enabled (validated as required), student=true -> legitymacja enabled
        TemplateData data2 = FormValues.of(cat, Map.of(
            "bezrobotny", false,
            "urzad", "US Warszawa",
            "student", true,
            "legitymacja", true
        )).toTemplateData();
        assertThat(data2.texts()).containsEntry("urzad", "US Warszawa");
        assertThat(data2.flags()).containsEntry("bezrobotny", false);
        assertThat(data2.flags()).containsEntry("student", true);
        assertThat(data2.flags()).containsEntry("legitymacja", true);
    }

    @Test
    void handlesCascadingDependencies() {
        String yaml = """
            fields:
              - name: zatrudniony
                label: Zatrudniony
                type: checkbox
              - name: etat
                label: Na etat
                type: checkbox
                enabled_if: "zatrudniony"
                disabled_value: false
              - name: firma
                label: Nazwa firmy
                type: text
                enabled_if: "etat"
                disabled_value: "Niedotyczy"
            """;
        FieldCatalog cat = FieldCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        // If zatrudniony is false -> etat becomes false -> firma becomes disabled with "Niedotyczy"
        TemplateData data = FormValues.of(cat, Map.of("zatrudniony", false)).toTemplateData();
        assertThat(data.flags()).containsEntry("zatrudniony", false);
        assertThat(data.flags()).containsEntry("etat", false);
        assertThat(data.texts()).containsEntry("firma", "Niedotyczy");
    }

    @Test
    void appliesDefaultValuesWhenPresentAndInputMissing() {
        String yaml = """
            fields:
              - name: imie
                label: Imię
                type: text
                default: "Jan"
              - name: jednostka
                label: Jednostka
                type: select
                options: [miesięcznie, godzinowo]
                default: "miesięcznie"
              - name: student
                label: Student
                type: checkbox
                default: true
            """;
        FieldCatalog cat = FieldCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        TemplateData data = FormValues.of(cat, Map.of()).toTemplateData();
        assertThat(data.texts()).containsEntry("imie", "Jan");
        assertThat(data.texts()).containsEntry("jednostka", "miesięcznie");
        assertThat(data.flags()).containsEntry("student", true);
    }
}
