package com.onlypanels.singingbalances;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Dates are stored as "epoch days" (days since 1 Jan 1970); times as minutes after midnight. */
final class Dates {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);
    private static final DateTimeFormatter F_DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK);
    private static final DateTimeFormatter F_SHORT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK);
    private static final DateTimeFormatter F_MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK);

    private Dates() {}

    static String fmt(long day) {
        return LocalDate.ofEpochDay(day).format(F);
    }

    static String withWeekday(long day) {
        return LocalDate.ofEpochDay(day).format(F_DAY);
    }

    static String shortDay(long day) {
        return LocalDate.ofEpochDay(day).format(F_SHORT);
    }

    static String month(long day) {
        return LocalDate.ofEpochDay(day).format(F_MONTH);
    }

    static String iso(long day) {
        return LocalDate.ofEpochDay(day).toString();
    }

    static long today() {
        return LocalDate.now().toEpochDay();
    }

    static String time(int minutes) {
        return String.format(Locale.UK, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /** "today", "tomorrow", "in 5 days", "yesterday", "3 days ago" */
    static String relative(long day) {
        long d = day - today();
        if (d == 0) return "today";
        if (d == 1) return "tomorrow";
        if (d == -1) return "yesterday";
        return d > 0 ? "in " + d + " days" : (-d) + " days ago";
    }
}
