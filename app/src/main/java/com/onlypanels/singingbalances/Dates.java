package com.onlypanels.singingbalances;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dates are stored as "epoch days" (days since 1 Jan 1970). */
final class Dates {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private Dates() {}

    static String fmt(long day) {
        return LocalDate.ofEpochDay(day).format(F);
    }

    static String iso(long day) {
        return LocalDate.ofEpochDay(day).toString();
    }

    static long today() {
        return LocalDate.now().toEpochDay();
    }
}
