package pl.formularz.form;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldCatalogTest {

    private static final Path TEST_CONFIG_PATH = Path.of("src/test/resources/config/fields.yaml");

    @Test
    void projectCatalogDefinesAllFieldsOfSampleTemplate() throws IOException {
        try (InputStream yaml = Files.newInputStream(TEST_CONFIG_PATH)) {
            FieldCatalog catalog = FieldCatalog.load(yaml);

            assertThat(catalog.fields()).extracting(FieldDefinition::name).containsExactly(
                "numer_umowy", "data_zawarcia_umowy", "imie", "nazwisko", "pesel", "nazwa_firmy", "kwota", "jednostka", "kwota_slownie",
                "dodatkowy_checkbox");
        }
    }

    @Test
    void loadsCatalogDirectlyFromPath() {
        FieldCatalog catalog = FieldCatalog.load(TEST_CONFIG_PATH);

        assertThat(catalog.fields()).extracting(FieldDefinition::name).contains(
            "nazwa_firmy", "numer_umowy", "imie", "nazwisko", "pesel");
    }

    @Test
    void readsSelectOptionsInOrder() throws IOException {
        try (InputStream yaml = Files.newInputStream(TEST_CONFIG_PATH)) {
            FieldDefinition company = FieldCatalog.load(yaml).fields().get(5);

            assertThat(company).isEqualTo(new FieldDefinition.Select("nazwa_firmy", "Nazwa firmy",
                List.of("Firma 1", "Firma 2", "Firma z bardzo długą nazwą")));
        }
    }
}
