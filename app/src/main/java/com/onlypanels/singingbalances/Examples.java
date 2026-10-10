package com.onlypanels.singingbalances;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Example bookings for someone trying ShowFee for the first time, so the home screen, calendar and money
 * pages aren't empty. They're remembered by id so one tap removes exactly them and nothing the user added.
 * They never count towards the free plan's monthly limit.
 */
final class Examples {
    static final String NOTE = "Example booking – remove all examples from the home screen when you're ready.";
    private static final String[] CLIENTS = {"The Grand Hotel", "Sarah & Tom", "Blue Lagoon Bar", "Bright Events Ltd"};

    private Examples() {}

    static boolean active(Context c) {
        return !Prefs.get(c, Prefs.EXAMPLES).isEmpty();
    }

    /** Adds a small, realistic set: paid, part-paid, overdue, due soon, tonight-ish and upcoming. */
    static void add(Context c) {
        if (active(c)) return;
        Db db = Db.get(c);
        long t = Dates.today();
        List<Long> gigs = new ArrayList<>(), exps = new ArrayList<>();
        long paid = gig(db, gigs, CLIENTS[0], "Gala dinner", t - 30, 20 * 60, 450, Gig.CONFIRMED, t - 23);
        db.addPayment(paid, 45000, t - 25, "Bank transfer");
        long part = gig(db, gigs, CLIENTS[1], "Wedding reception", t - 18, 19 * 60, 900, Gig.CONFIRMED, t - 4);
        db.addPayment(part, 30000, t - 18, "Deposit (cash)");
        gig(db, gigs, CLIENTS[3], "Company party", t - 9, 21 * 60, 600, Gig.CONFIRMED, t - 2);
        gig(db, gigs, CLIENTS[2], "Friday night", t - 2, 22 * 60, 200, Gig.CONFIRMED, t + 5);
        gig(db, gigs, CLIENTS[0], "Christmas party", t + 6, 20 * 60, 500, Gig.CONFIRMED, t + 13);
        gig(db, gigs, CLIENTS[2], "Summer launch", t + 20, 21 * 60, 250, Gig.PENCILLED, t + 27);
        exps.add(expense(db, t - 18, 40, "Fuel & transport", "Example: travel to the venue", part));
        exps.add(expense(db, t - 12, 85, "Equipment", "Example: new cables", 0));
        Prefs.set(c, Prefs.EXAMPLES, "g:" + join(gigs) + ";e:" + join(exps));
    }

    /** Removes the example bookings, their payments and expenses, and example clients nobody else uses. */
    static void remove(Context c) {
        Db db = Db.get(c);
        for (long id : ids(c, "g")) db.deleteGig(id);   // payments go with them
        for (long id : ids(c, "e")) db.deleteExpense(id);
        for (String name : CLIENTS) {
            Client k = db.client(name);
            if (k != null && db.gigsForClient(name).isEmpty()) db.deleteClient(k.id);
        }
        Prefs.set(c, Prefs.EXAMPLES, "");
    }

    /** True if this booking is one of the examples. */
    static boolean isExample(Context c, long gigId) {
        return ids(c, "g").contains(gigId);
    }

    static Set<Long> ids(Context c, String kind) {
        Set<Long> out = new HashSet<>();
        for (String part : Prefs.get(c, Prefs.EXAMPLES).split(";")) {
            if (!part.startsWith(kind + ":")) continue;
            for (String n : part.substring(kind.length() + 1).split(",")) {
                try {
                    if (!n.isEmpty()) out.add(Long.parseLong(n));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static long gig(Db db, List<Long> ids, String client, String event, long day, int start, double fee,
                            int status, long due) {
        Gig g = new Gig();
        g.client = client;
        g.event = event;
        g.gigDay = day;
        g.startMin = start;
        g.feeCents = Math.round(fee * 100);
        g.status = status;
        g.dueDay = due;
        g.notes = NOTE;
        long id = db.save(g);
        ids.add(id);
        return id;
    }

    private static long expense(Db db, long day, double amt, String cat, String note, long gigId) {
        Expense e = new Expense();
        e.day = day;
        e.cents = Math.round(amt * 100);
        e.category = cat;
        e.note = note;
        e.gigId = gigId;
        db.saveExpense(e);
        return e.id;
    }

    private static String join(List<Long> l) {
        StringBuilder sb = new StringBuilder();
        for (long v : l) sb.append(sb.length() == 0 ? "" : ",").append(v);
        return sb.toString();
    }
}
