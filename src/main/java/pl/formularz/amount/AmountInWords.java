package pl.formularz.amount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Amount spelled out in Polish, e.g. "sto dwadzieścia trzy złote czterdzieści pięć groszy".
 */
public final class AmountInWords {

    private static final String[] UNITS = {
        "", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć",
        "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście",
        "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście"
    };
    private static final String[] TENS = {
        "", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt",
        "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt"
    };
    private static final String[] HUNDREDS = {
        "", "sto", "dwieście", "trzysta", "czterysta", "pięćset",
        "sześćset", "siedemset", "osiemset", "dziewięćset"
    };

    private static final PluralForms ZLOTY = new PluralForms("złoty", "złote", "złotych");
    private static final PluralForms GROSZ = new PluralForms("grosz", "grosze", "groszy");
    /** Three-digit groups from the highest: billions, millions, thousands. */
    private static final List<Group> GROUPS = List.of(
        new Group(1_000_000_000L, new PluralForms("miliard", "miliardy", "miliardów")),
        new Group(1_000_000L, new PluralForms("milion", "miliony", "milionów")),
        new Group(1_000L, new PluralForms("tysiąc", "tysiące", "tysięcy"))
    );

    private static final int TEEN_LIMIT = 20;
    private static final int TEN = 10;
    private static final int HUNDRED = 100;
    private static final int GROSZE_SCALE = 2;

    private AmountInWords() {
    }

    public static String of(BigDecimal value) {
        BigDecimal rounded = value.setScale(GROSZE_SCALE, RoundingMode.HALF_UP);
        long zlote = rounded.longValue();
        int grosze = rounded.remainder(BigDecimal.ONE).movePointRight(GROSZE_SCALE).intValue();
        return spell(zlote) + " " + ZLOTY.forCount(zlote) + " " + spell(grosze) + " " + GROSZ.forCount(grosze);
    }

    private static String spell(long number) {
        if (number == 0) {
            return "zero";
        }
        List<String> words = new ArrayList<>();
        long rest = number;
        for (Group group : GROUPS) {
            int count = (int) (rest / group.size());
            rest %= group.size();
            if (count == 1) {
                words.add(group.name().forCount(1));
            } else if (count > 1) {
                words.add(spellBelowThousand(count));
                words.add(group.name().forCount(count));
            }
        }
        if (rest > 0) {
            words.add(spellBelowThousand((int) rest));
        }
        return String.join(" ", words);
    }

    private static String spellBelowThousand(int number) {
        List<String> words = new ArrayList<>();
        words.add(HUNDREDS[number / HUNDRED]);
        int belowHundred = number % HUNDRED;
        if (belowHundred < TEEN_LIMIT) {
            words.add(UNITS[belowHundred]);
        } else {
            words.add(TENS[belowHundred / TEN]);
            words.add(UNITS[belowHundred % TEN]);
        }
        return String.join(" ", words.stream().filter(word -> !word.isEmpty()).toList());
    }

    private record Group(long size, PluralForms name) {
    }
}
