package pl.formularz.amount;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            0            | zero PLN 0/100
            0.01         | zero PLN 1/100
            1            | jeden PLN 0/100
            2            | dwa PLN 0/100
            5            | pięć PLN 0/100
            12           | dwanaście PLN 0/100
            21           | dwadzieścia jeden PLN 0/100
            22           | dwadzieścia dwa PLN 0/100
            112          | sto dwanaście PLN 0/100
            1000         | tysiąc PLN 0/100
            1234         | tysiąc dwieście trzydzieści cztery PLN 0/100
            2000         | dwa tysiące PLN 0/100
            5000         | pięć tysięcy PLN 0/100
            12000        | dwanaście tysięcy PLN 0/100
            22000        | dwadzieścia dwa tysiące PLN 0/100
            1000000      | milion PLN 0/100
            3000000      | trzy miliony PLN 0/100
            123.45       | sto dwadzieścia trzy PLN 45/100
            0.22         | zero PLN 22/100
            1234567.89   | milion dwieście trzydzieści cztery tysiące pięćset sześćdziesiąt siedem PLN 89/100
            """)
    void spellsAmountInPolishZlotyAndGrosze(BigDecimal value, String expected) {
        assertThat(AmountInWords.of(value)).isEqualTo(expected);
    }
}
