package com.onlypanels.singingbalances;

import android.content.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** Amounts are stored as whole cents to avoid rounding errors. The currency is the user's choice. */
final class Money {
    static final String[] CODES = {"EUR", "GBP", "USD", "CAD", "AUD", "NZD", "CHF", "SEK", "NOK", "DKK", "PLN",
            "CZK", "HUF", "ZAR", "INR", "AED", "NGN", "PHP", "BRL", "MXN", "SGD"};
    private static final String[] SYMBOLS = {"€", "£", "$", "CA$", "A$", "NZ$", "CHF ", "kr", "kr", "kr", "zł",
            "Kč", "Ft", "R", "₹", "AED ", "₦", "₱", "R$", "MX$", "S$"};
    /** Currencies written after the amount, e.g. "150.00 kr". */
    private static final boolean[] AFTER = {false, false, false, false, false, false, false, true, true, true, true,
            true, true, false, false, false, false, false, false, false, false};
    static final String[] NAMES = {"Euro (€)", "British pound (£)", "US dollar ($)", "Canadian dollar (CA$)",
            "Australian dollar (A$)", "New Zealand dollar (NZ$)", "Swiss franc (CHF)", "Swedish krona (kr)",
            "Norwegian krone (kr)", "Danish krone (kr)", "Polish złoty (zł)", "Czech koruna (Kč)", "Hungarian forint (Ft)",
            "South African rand (R)", "Indian rupee (₹)", "UAE dirham (AED)", "Nigerian naira (₦)", "Philippine peso (₱)",
            "Brazilian real (R$)", "Mexican peso (MX$)", "Singapore dollar (S$)"};

    static String SYMBOL = "€";
    private static boolean after;

    private Money() {}

    static int index(Context c) {
        String code = Prefs.get(c, Prefs.CURRENCY);
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(code)) return i;
        return 0;
    }

    static void load(Context c) {
        int i = index(c);
        SYMBOL = SYMBOLS[i];
        after = AFTER[i];
    }

    /** Short symbol for form labels, e.g. "€" or "kr". */
    static String label() {
        return SYMBOL.trim();
    }

    static String fmt(long cents) {
        long a = Math.abs(cents);
        String s = String.format(Locale.UK, "%,d.%02d", a / 100, a % 100);
        String sign = cents < 0 ? "-" : "";
        return after ? sign + s + " " + SYMBOL.trim() : sign + SYMBOL + s;
    }

    /** Whole amounts without ".00", for charts. */
    static String fmtShort(long cents) {
        String s = fmt(cents);
        return s.replace(".00", "");
    }

    /** Plain number for editing / CSV, e.g. 150.00 */
    static String plain(long cents) {
        long a = Math.abs(cents);
        return (cents < 0 ? "-" : "") + String.format(Locale.US, "%d.%02d", a / 100, a % 100);
    }

    /** Accepts "150", "150.5", "150,50", "1,500", "€ 1,500.00". Returns null if not a valid amount. */
    static Long parse(String s) {
        if (s == null) return null;
        s = s.trim().replace(SYMBOL.trim(), "").replaceAll("[^0-9.,-]", "");
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
