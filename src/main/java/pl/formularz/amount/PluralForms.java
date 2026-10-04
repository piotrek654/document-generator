package pl.formularz.amount;

/**
 * Polish noun forms after a numeral: 1 złoty, 2–4 złote, 5+ złotych (12–14 also "złotych").
 */
record PluralForms(String one, String few, String many) {

    private static final int TEN = 10;
    private static final int HUNDRED = 100;
    private static final int FEW_FROM = 2;
    private static final int FEW_TO = 4;
    private static final int TEENS_FROM = 12;
    private static final int TEENS_TO = 14;

    String forCount(long count) {
        if (count == 1) {
            return one;
        }
        long lastDigit = count % TEN;
        long lastTwoDigits = count % HUNDRED;
        boolean endsWithTwoToFour = lastDigit >= FEW_FROM && lastDigit <= FEW_TO;
        boolean isTeen = lastTwoDigits >= TEENS_FROM && lastTwoDigits <= TEENS_TO;
        return endsWithTwoToFour && !isTeen ? few : many;
    }
}
