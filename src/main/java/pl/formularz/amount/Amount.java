package pl.formularz.amount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Amount in złoty with grosz precision. Polish format: "1 234,50".
 */
public record Amount(BigDecimal value) {

    private static final int SCALE = 2;
    private static final int THOUSANDS_GROUP = 3;
    private static final Pattern INPUT = Pattern.compile("\\d+([.,]\\d{1,2})?");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]");

    public Amount {
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Accepts "1234,5", "1 234,50", "1234.5"; rejects negative values and more than two decimal places. */
    public static Amount parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Kwota nie może być pusta (null)");
        }
        String compact = WHITESPACE.matcher(input).replaceAll("");
        if (!INPUT.matcher(compact).matches()) {
            throw new IllegalArgumentException("Niepoprawna kwota: '" + input + "'");
        }
        return new Amount(new BigDecimal(compact.replace(',', '.')));
    }

    public String formatPl() {
        String plain = value.toPlainString();
        int dot = plain.indexOf('.');
        return groupThousands(plain.substring(0, dot)) + "," + plain.substring(dot + 1);
    }

    public String inWords() {
        return AmountInWords.of(value);
    }

    private static String groupThousands(String digits) {
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % THOUSANDS_GROUP == 0) {
                grouped.append(' ');
            }
            grouped.append(digits.charAt(i));
        }
        return grouped.toString();
    }
}
