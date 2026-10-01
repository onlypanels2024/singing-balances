package com.onlypanels.singingbalances;

import android.content.Context;

/**
 * What the user does, and the words the app uses for it: a singer has "gigs",
 * a photographer has "shoots", a make-up artist has "bookings". Every preset can be
 * overridden in Settings.
 */
final class Words {
    static final String[] KEYS = {"singer", "band", "dj", "photographer", "videographer", "makeup", "host", "dancer", "other"};
    static final String[] NAMES = {"Singer", "Band / musician", "DJ", "Photographer", "Videographer",
            "Hair & make-up artist", "MC / host", "Dancer / performer", "Something else"};
    private static final String[] ONE = {"gig", "gig", "gig", "shoot", "shoot", "booking", "event", "show", "booking"};
    private static final String[] MANY = {"gigs", "gigs", "gigs", "shoots", "shoots", "bookings", "events", "shows", "bookings"};
    private static final String[] LINE = {"Live vocal performance", "Live music performance", "DJ set",
            "Photography services", "Videography services", "Hair & make-up services", "Hosting services",
            "Performance", "Services"};
    private static final boolean[] NIGHT = {true, true, true, false, false, false, true, true, false};

    private Words() {}

    static int index(Context c) {
        String p = Prefs.get(c, Prefs.PROFESSION);
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(p)) return i;
        return 0;
    }

    private static String or(Context c, String key, String fallback) {
        String v = Prefs.get(c, key);
        return v.isEmpty() ? fallback : v;
    }

    /** "gig" */
    static String one(Context c) {
        return or(c, Prefs.WORD_ONE, ONE[index(c)]).toLowerCase();
    }

    /** "gigs" */
    static String many(Context c) {
        return or(c, Prefs.WORD_MANY, MANY[index(c)]).toLowerCase();
    }

    /** "Gig" */
    static String One(Context c) {
        return cap(one(c));
    }

    /** "Gigs" */
    static String Many(Context c) {
        return cap(many(c));
    }

    /** "1 gig", "3 gigs" */
    static String count(Context c, int n) {
        return n + " " + (n == 1 ? one(c) : many(c));
    }

    /** Name of the first tab, e.g. "Gigs" (or "Singing" if they chose it). */
    static String tab(Context c) {
        return or(c, Prefs.TAB_TITLE, Many(c));
    }

    /** First line on invoices, e.g. "Live vocal performance". */
    static String invoiceLine(Context c) {
        return or(c, Prefs.INVOICE_LINE, LINE[index(c)]);
    }

    /** "on the night" for performers, "on the day" for everyone else. */
    static String onTheNight(Context c) {
        return NIGHT[index(c)] ? "on the night" : "on the day";
    }

    /** "Tonight" for performers, "Today" for everyone else. */
    static String tonight(Context c) {
        return NIGHT[index(c)] ? "Tonight" : "Today";
    }

    static String presetOne(int i) {
        return ONE[i];
    }

    static String presetMany(int i) {
        return MANY[i];
    }

    static String presetLine(int i) {
        return LINE[i];
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "a gig", "an event" */
    static String a(Context c) {
        String w = one(c);
        return ("aeiou".indexOf(w.isEmpty() ? 'x' : w.charAt(0)) >= 0 ? "an " : "a ") + w;
    }
}
