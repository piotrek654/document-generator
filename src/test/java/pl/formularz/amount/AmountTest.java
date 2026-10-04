package pl.formularz.amount;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            1234,5      | 1234.50
            1 234,50    | 1234.50
            1234.5      | 1234.50
            ' 12 '      | 12.00
            0,01        | 0.01
            """)
    void parsesUserInput(String input, BigDecimal expected) {
        assertThat(Amount.parse(input).value()).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "12,345", "-5", "1,2,3"})
    void rejectsInvalidInput(String input) {
        assertThatThrownBy(() -> Amount.parse(input))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(input.isEmpty() ? "kwota" : input);
    }

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @CsvSource(delimiter = '|', textBlock = """
            0          | 0,00
            5          | 5,00
            123.45     | 123,45
            1234.5     | 1 234,50
            1234567.89 | 1 234 567,89
            """)
    void formatsWithPolishSeparators(BigDecimal value, String expected) {
        assertThat(new Amount(value).formatPl()).isEqualTo(expected);
    }

    @Test
    void spellsItselfInWords() {
        assertThat(Amount.parse("123,45").inWords())
            .isEqualTo("sto dwadzieścia trzy złote czterdzieści pięć groszy");
    }
}
