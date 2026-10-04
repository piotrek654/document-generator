package pl.formularz.amount;

import java.math.BigDecimal;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AmountInWordsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            0            | zero złotych zero groszy
            0.01         | zero złotych jeden grosz
            1            | jeden złoty zero groszy
            2            | dwa złote zero groszy
            5            | pięć złotych zero groszy
            12           | dwanaście złotych zero groszy
            21           | dwadzieścia jeden złotych zero groszy
            22           | dwadzieścia dwa złote zero groszy
            112          | sto dwanaście złotych zero groszy
            1000         | tysiąc złotych zero groszy
            2000         | dwa tysiące złotych zero groszy
            5000         | pięć tysięcy złotych zero groszy
            12000        | dwanaście tysięcy złotych zero groszy
            22000        | dwadzieścia dwa tysiące złotych zero groszy
            1000000      | milion złotych zero groszy
            3000000      | trzy miliony złotych zero groszy
            123.45       | sto dwadzieścia trzy złote czterdzieści pięć groszy
            0.22         | zero złotych dwadzieścia dwa grosze
            1234567.89   | milion dwieście trzydzieści cztery tysiące pięćset sześćdziesiąt siedem złotych osiemdziesiąt dziewięć groszy
            """)
    void spellsAmountInPolishZlotyAndGrosze(BigDecimal value, String expected) {
        assertThat(AmountInWords.of(value)).isEqualTo(expected);
    }
}
