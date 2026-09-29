package com.onlypanels.singingbalances;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Amounts are stored as whole cents to avoid rounding errors. */
final class Money {
    static final String SYMBOL = "€"; // €

    private Money() {}

    static String fmt(long cents) {
        long a = Math.abs(cents);
        String s = String.format(Locale.UK, "%,d.%02d", a / 100, a % 100);
        return (cents < 0 ? "-" : "") + SYMBOL + s;
    }

    /** Plain number for editing / CSV, e.g. 150.00 */
    static String plain(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + String.format(Locale.US, "%d.%02d", a / 100, a % 100);
    }

    /** Accepts "150", "150.5", "150,50", "1,500", "€ 1,500.00". Returns null if not a valid amount. */
    static Long parse(String s) {
        if (s == null) return null;
        s = s.trim().replace(SYMBOL, "").replace(" ", "");
        if (s.isEmpty()) return null;
        if (s.matches("^\\d+,\\d{1,2}$")) {
            s = s.replace(',', '.');
        } else {
            s = s.replace(",", "");
        }
        try {
            BigDecimal b = new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
            if (b.signum() < 0) return null;
            return b.movePointRight(2).longValueExact();
        } catch (Exception e) {
            return null;
        }
    }
}
