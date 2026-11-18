package pl.formularz.form;

import java.time.DateTimeException;
import java.time.LocalDate;

public final class Pesel {

    private static final int[] WEIGHTS = { 1, 3, 7, 9, 1, 3, 7, 9, 1, 3 };

    private Pesel() {
    }

    public static boolean isValid(String value) {
        if (value == null || !value.matches("[0-9]{11}")) {
            return false;
        }

        int year = Integer.parseInt(value.substring(0, 2));
        int encodedMonth = Integer.parseInt(value.substring(2, 4));
        int month;
        if (encodedMonth >= 1 && encodedMonth <= 12) {
            year += 1900;
            month = encodedMonth;
        } else if (encodedMonth >= 21 && encodedMonth <= 32) {
            year += 2000;
            month = encodedMonth - 20;
        } else if (encodedMonth >= 41 && encodedMonth <= 52) {
            year += 2100;
            month = encodedMonth - 40;
        } else if (encodedMonth >= 61 && encodedMonth <= 72) {
            year += 2200;
            month = encodedMonth - 60;
        } else if (encodedMonth >= 81 && encodedMonth <= 92) {
            year += 1800;
            month = encodedMonth - 80;
        } else {
            return false;
        }

        int day = Integer.parseInt(value.substring(4, 6));
        try {
            LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return false;
        }

        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (value.charAt(i) - '0') * WEIGHTS[i];
        }
        int checkDigit = (10 - sum % 10) % 10;
        return checkDigit == value.charAt(10) - '0';
    }
}