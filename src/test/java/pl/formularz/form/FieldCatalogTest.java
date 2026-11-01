package pl.formularz.form;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import pl.formularz.app.FormApp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCatalogTest {

    private static final Path TEST_CONFIG_PATH = Path.of("src/test/resources/config/fields.yaml");
    private static final Path MAIN_CONFIG_PATH = Path.of("config/fields.yaml");

    @Test
    void projectCatalogDefinesAllFieldsOfSampleTemplate() throws IOException {
        try (InputStream yaml = Files.newInputStream(TEST_CONFIG_PATH)) {
            FieldCatalog catalog = FieldCatalog.load(yaml);

            assertThat(catalog.fields()).extracting(FieldDefinition::name).containsExactly(
                "numer_umowy", "data_zawarcia_umowy", "data_rozpoczecia_umowy", "imie", "nazwisko", "pesel", "nazwa_firmy", "kwota", "jednostka", "kwota_slownie",
                "dodatkowy_checkbox");
            assertThat(catalog.fields()).extracting(FieldDefinition::name)
                .doesNotContain("data_zakonczenia_umowy", "rok");
        }
    }

    @Test
    void loadsCatalogDirectlyFromPath() {
        FieldCatalog catalog = FieldCatalog.load(TEST_CONFIG_PATH);

        assertThat(catalog.fields()).extracting(FieldDefinition::name).contains(
            "nazwa_firmy", "numer_umowy", "data_rozpoczecia_umowy", "imie", "nazwisko", "pesel");
    }

    @Test
    void readsSelectOptionsInOrder() throws IOException {
        try (InputStream yaml = Files.newInputStream(TEST_CONFIG_PATH)) {
            FieldDefinition company = FieldCatalog.load(yaml).fields().get(6);

            assertThat(company.name()).isEqualTo("nazwa_firmy");
            assertThat(company.label()).isEqualTo("Nazwa firmy");
            assertThat(((FieldDefinition.Select) company).options()).containsExactly("Firma 1", "Firma 2", "Firma z bardzo długą nazwą");
        }
    }

    @Test
    void loadsMainConfigWithDependenciesAndDefaults() {
        FieldCatalog catalog = FieldCatalog.load(MAIN_CONFIG_PATH);

        FieldDefinition legitymacja = catalog.fields().stream()
            .filter(f -> f.name().equals("legitymacja_studencka_checkbox"))
            .findFirst()
            .orElseThrow();
        assertThat(legitymacja.enabledIf()).contains("student_checkbox");
        assertThat(legitymacja.disabledValue()).contains(false);

        FieldDefinition podstawa2 = catalog.fields().stream()
            .filter(f -> f.name().equals("podstawa_wymiaru_skladek_2_checkbox"))
            .findFirst()
            .orElseThrow();
        assertThat(podstawa2.enabledIf()).contains("podstawa_wymiaru_skladek_1_checkbox");
        assertThat(podstawa2.disabledValue()).contains(false);
    }

    @Test
    void loadsMainConfigWithContractDatesAndYearOrder() {
        FieldCatalog catalog = FieldCatalog.load(MAIN_CONFIG_PATH);
        List<String> fieldNames = catalog.fields().stream().map(FieldDefinition::name).toList();

        int zawarciaIndex = fieldNames.indexOf("data_zawarcia_umowy");
        int rokIndex = fieldNames.indexOf("rok");
        int rozpoczeciaIndex = fieldNames.indexOf("data_rozpoczecia_umowy");
        int zakonczeniaIndex = fieldNames.indexOf("data_zakonczenia_umowy");

        assertThat(zawarciaIndex).isGreaterThanOrEqualTo(0);
        assertThat(rokIndex).isEqualTo(zawarciaIndex + 1);
        assertThat(rozpoczeciaIndex).isEqualTo(rokIndex + 1);
        assertThat(zakonczeniaIndex).isEqualTo(rozpoczeciaIndex + 1);

        FieldDefinition rokField = catalog.fields().get(rokIndex);
        assertThat(rokField).isInstanceOf(FieldDefinition.Select.class);
        FieldDefinition.Select rokSelect = (FieldDefinition.Select) rokField;
        assertThat(rokSelect.options()).containsExactly(
            "2026", "2027", "2028", "2029", "2030", "2031", "2032", "2033", "2034", "2035"
        );
    }

    @Test
    void computesDefaultYearAccordingToQuarterRule() {
        assertThat(FormApp.defaultYear(LocalDate.of(2026, 1, 1))).isEqualTo("2026");
        assertThat(FormApp.defaultYear(LocalDate.of(2026, 6, 15))).isEqualTo("2026");
        assertThat(FormApp.defaultYear(LocalDate.of(2026, 9, 30))).isEqualTo("2026");
        assertThat(FormApp.defaultYear(LocalDate.of(2026, 10, 1))).isEqualTo("2027");
        assertThat(FormApp.defaultYear(LocalDate.of(2026, 11, 15))).isEqualTo("2027");
        assertThat(FormApp.defaultYear(LocalDate.of(2026, 12, 31))).isEqualTo("2027");
    }

    @Test
    void rejectsEnabledIfReferencingUnknownField() {
        String yaml = """
            fields:
              - name: pole
                label: Pole
                type: text
                enabled_if: "nieznany_checkbox"
            """;

        assertThatThrownBy(() -> FieldCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nieznany_checkbox");
    }

    @Test
    void rejectsEnabledIfReferencingLaterField() {
        String yaml = """
            fields:
              - name: pole
                label: Pole
                type: text
                enabled_if: "student_checkbox"
              - name: student_checkbox
                label: Student
                type: checkbox
            """;

        assertThatThrownBy(() -> FieldCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("student_checkbox");
    }

    @Test
    void rejectsEnabledIfReferencingNonCheckboxField() {
        String yaml = """
            fields:
              - name: imie
                label: Imię
                type: text
              - name: pole
                label: Pole
                type: text
                enabled_if: "imie"
            """;

        assertThatThrownBy(() -> FieldCatalog.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("imie");
    }
}
