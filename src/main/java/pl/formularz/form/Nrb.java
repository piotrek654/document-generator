package pl.formularz.form;

public final class Nrb {

    private Nrb() {
    }

    public static String format(String value) {
        if (!value.matches("[0-9 ]*")) {
            throw new IllegalArgumentException("Niepoprawny numer NRB");
        }
        String digits = value.replace(" ", "");
        if (digits.length() != 26 || digits.substring(0, 2).compareTo("02") < 0
            || digits.substring(0, 2).compareTo("98") > 0 || !hasValidChecksum(digits)) {
            throw new IllegalArgumentException("Niepoprawny numer NRB");
        }
        return formatDigits(digits);
    }

    public static String formatDigits(String digits) {
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i == 2 || (i > 2 && (i - 2) % 4 == 0)) {
                formatted.append(' ');
            }
            formatted.append(digits.charAt(i));
        }
        return formatted.toString();
    }

    private static boolean hasValidChecksum(String digits) {
        String ibanDigits = digits.substring(2) + "2521" + digits.substring(0, 2);
        int remainder = 0;
        for (int i = 0; i < ibanDigits.length(); i++) {
            remainder = (remainder * 10 + ibanDigits.charAt(i) - '0') % 97;
        }
        return remainder == 1;
    }
}