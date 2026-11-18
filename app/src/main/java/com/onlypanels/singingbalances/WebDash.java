package com.onlypanels.singingbalances;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * ShowFee web dashboard (every plan) (showfee.app/dashboard). When switched on, a copy of the user's bookings,
 * payments, clients and expenses is kept online under their own account, so they can see it on a computer.
 * Bank details, sign-ins and settings are never sent. Changes made on the web are written to dash/{uid}/ops; a
 * silent message (or opening the app) makes this phone apply them with {@link #apply} – the phone stays the main copy.
 */
final class WebDash {
    static final String URL = "https://showfee.app/dashboard/";
    private static final String ON = "web_dash_on", SENT_HASH = "web_dash_hash", SENT_AT = "web_dash_at",
            REFS = "web_dash_refs", APPLIED = "web_dash_applied";

    private WebDash() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("web_dash", Context.MODE_PRIVATE);
    }

    static boolean isOn(Context c) {
        return sp(c).getBoolean(ON, false);
    }

    static long sentAt(Context c) {
        return sp(c).getLong(SENT_AT, 0);
    }

    static void setOn(Context c, boolean on) {
        sp(c).edit().putBoolean(ON, on).remove(SENT_HASH).apply();
    }

    /** What the dashboard shows. */
    static String payload(Context c) throws Exception {
        Money.load(c);
        JSONObject all = Db.get(c).exportAll();
        JSONObject out = new JSONObject()
                .put("v", 2)
                .put("terms", Prefs.termsDays(c))
                .put("refs", new JSONObject(sp(c).getString(REFS, "{}")))
                .put("applied", new JSONArray(sp(c).getString(APPLIED, "[]")))
                .put("name", Prefs.get(c, Prefs.NAME))
                .put("currency", Money.code(c))
                .put("one", Words.one(c))
                .put("many", Words.many(c));
        for (String t : new String[]{"clients", "gigs", "payments", "expenses"}) {
            JSONArray rows = all.optJSONArray(t);
            out.put(t, rows == null ? new JSONArray() : rows);
        }
        return out.toString();
    }

    /** Sends the latest copy if something changed. Quietly does nothing when off or not signed in. */
    static void sync(Context c) {
        if (!isOn(c) || !Cloud.signedIn(c)) return;
        try {
            String data = payload(c);
            String hash = Integer.toHexString(data.hashCode()) + ":" + data.length();
            if (hash.equals(sp(c).getString(SENT_HASH, ""))) return;
            Cloud.uploadDash(c, data, new Cloud.Done<Void>() {
                @Override public void ok(Void r) {
                    sp(c).edit().putString(SENT_HASH, hash).putLong(SENT_AT, System.currentTimeMillis()).apply();
                }

                @Override public void fail(String reason) { } // tried again next time the app opens
            });
        } catch (Exception ignored) { }
    }

    static void markSent(Context c, String data) {
        String hash = Integer.toHexString(data.hashCode()) + ":" + data.length();
        sp(c).edit().putString(SENT_HASH, hash).putLong(SENT_AT, System.currentTimeMillis()).apply();
    }

    /** Brings in changes made on the web dashboard (then sends a fresh copy). Quiet when off or offline. */
    static void pull(Context c, Runnable changed) {
        if (!isOn(c) || !Cloud.signedIn(c)) return;
        Cloud.pullDash(c, new Cloud.Done<Integer>() {
            @Override public void ok(Integer n) {
                if (n != null && n > 0) {
                    Cloud.syncBusy(c);  // the booking page's busy dates
                    if (changed != null) changed.run();
                }
            }

            @Override public void fail(String reason) { } // tried again next time
        });
    }

    // ------------------------------------------------------------------ changes made on the web

    /**
     * Makes the changes on this phone, oldest first, and returns the ids of the ones that are done (including ones
     * that couldn't be used, e.g. for a booking deleted since – those are just dropped). Runs on a background thread.
     */
    static synchronized java.util.List<String> apply(Context c, java.util.List<JSONObject> ops) {
        java.util.List<String> done = new java.util.ArrayList<>();
        JSONObject refs;
        JSONArray applied;
        try {
            refs = new JSONObject(sp(c).getString(REFS, "{}"));
            applied = new JSONArray(sp(c).getString(APPLIED, "[]"));
        } catch (Exception e) {
            refs = new JSONObject();
            applied = new JSONArray();
        }
        java.util.Set<String> already = new java.util.HashSet<>();
        for (int i = 0; i < applied.length(); i++) already.add(applied.optString(i));
        Db db = Db.get(c);
        for (JSONObject op : ops) {
            String id = op.optString("id");
            if (!already.contains(id)) {
                try {
                    one(c, db, op.optString("t"), op.optJSONObject("d"), refs);
                } catch (Exception ignored) {
                    // a change that doesn't fit any more (or was badly formed): skip it
                }
                applied.put(id);
            }
            done.add(id);
        }
        // keep the lists short: the last 300 ids, and the newest 300 references
        JSONArray keep = new JSONArray();
        for (int i = Math.max(0, applied.length() - 300); i < applied.length(); i++) keep.put(applied.optString(i));
        if (refs.length() > 300) {
            JSONObject trimmed = new JSONObject();
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (java.util.Iterator<String> k = refs.keys(); k.hasNext(); ) keys.add(k.next());
            java.util.Collections.sort(keys); // refs start with the time they were made
            for (String k : keys.subList(keys.size() - 300, keys.size())) {
                try { trimmed.put(k, refs.opt(k)); } catch (Exception ignored) { }
            }
            refs = trimmed;
        }
        sp(c).edit().putString(REFS, refs.toString()).putString(APPLIED, keep.toString()).apply();
        return done;
    }

    private static String text(JSONObject d, String key, int max) {
        String s = d.optString(key, "");
        if (JSONObject.NULL.equals(d.opt(key))) s = "";
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** An id from the web: a number, or a reference to something made on the web that this phone has saved. */
    private static long id(JSONObject d, String key, JSONObject refs) {
        Object v = d.opt(key);
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            String s = (String) v;
            if (refs.has(s)) return refs.optLong(s, 0);
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private static void one(Context c, Db db, String t, JSONObject d, JSONObject refs) throws Exception {
        if (d == null) return;
        switch (t) {
            case "gig": {
                String client = text(d, "client", 80);
                if (client.isEmpty()) return;
                long gid = d.has("id") ? id(d, "id", refs) : id(d, "ref", refs);
                Gig g = gid > 0 ? db.gig(gid) : null;
                if (g == null) {
                    if (d.has("id")) return; // deleted on the phone since
                    g = new Gig();
                    g.vatBp = Prefs.vatBp(c); // like a new booking made in the app
                    g.vatIncl = Prefs.vatIncluded(c);
                }
                g.client = client;
                g.event = text(d, "event", 120);
                g.notes = text(d, "notes", 2000);
                g.gigDay = d.getLong("gig_day");
                g.dueDay = d.optLong("due_day", g.gigDay);
                g.feeCents = Math.max(0, d.optLong("fee_cents", 0));
                int st = d.optInt("status", Gig.CONFIRMED);
                g.status = st >= 0 && st <= 2 ? st : Gig.CONFIRMED;
                int m = d.optInt("start_min", -1);
                g.startMin = m >= 0 && m < 24 * 60 ? m : -1;
                g.email = "";
                boolean isNew = g.id == 0;
                db.save(g);
                if (isNew && d.has("ref")) refs.put(d.getString("ref"), g.id);
                return;
            }
            case "gigDel": {
                long gid = id(d, "id", refs);
                if (gid <= 0 || db.gig(gid) == null) return;
                db.deleteGig(gid);
                Contract.deleteForGig(c, gid);
                return;
            }
            case "pay": {
                long gid = id(d, "gig", refs), cents = d.optLong("cents", 0);
                if (gid <= 0 || cents <= 0 || db.gig(gid) == null) return;
                long pid = db.addPayment(gid, cents, d.optLong("day", Dates.today()), text(d, "note", 80));
                if (d.has("ref")) refs.put(d.getString("ref"), pid);
                return;
            }
            case "payDel": {
                long pid = id(d, "id", refs);
                if (pid > 0) db.deletePayment(pid);
                return;
            }
            case "client": {
                String name = text(d, "name", 80), old = text(d, "old", 80);
                if (name.isEmpty()) return;
                long cid = d.has("id") ? id(d, "id", refs) : 0;
                Client k = cid > 0 ? db.client(cid) : null;
                if (k == null && !old.isEmpty()) k = db.client(old);
                boolean isNew = k == null;
                if (k == null) k = new Client();
                String oldName = isNew ? null : k.name;
                k.name = name;
                k.email = text(d, "email", 120);
                k.phone = text(d, "phone", 40);
                k.notes = text(d, "notes", 2000);
                if (!db.saveClient(k, oldName)) return; // that name is taken
                if (isNew && !old.isEmpty() && !old.equalsIgnoreCase(name)) db.renameClientOnGigs(old, name);
                if (isNew && d.has("ref")) refs.put(d.getString("ref"), k.id);
                return;
            }
            case "clientDel": {
                long cid = id(d, "id", refs);
                Client k = cid > 0 ? db.client(cid) : null;
                if (k != null && db.gigsForClient(k.name).isEmpty()) db.deleteClient(cid);
                return;
            }
            case "exp": {
                long cents = d.optLong("cents", 0);
                if (cents <= 0) return;
                Expense e = new Expense();
                long eid = d.has("id") ? id(d, "id", refs) : 0;
                if (d.has("id") && eid <= 0) return;
                e.id = Math.max(0, eid);
                e.day = d.optLong("day", Dates.today());
                e.cents = cents;
                String cat = text(d, "category", 60);
                e.category = cat.isEmpty() ? "Other" : cat;
                e.note = text(d, "note", 120);
                long gid = id(d, "gig", refs);
                e.gigId = gid > 0 && db.gig(gid) != null ? gid : 0;
                db.saveExpense(e);
                if (!d.has("id") && d.has("ref")) refs.put(d.getString("ref"), e.id);
                return;
            }
            case "expDel": {
                long eid = id(d, "id", refs);
                if (eid > 0) db.deleteExpense(eid);
                return;
            }
            default:
        }
    }

    /** Sends a copy now (after switching on). */
    static void syncNow(Context c, Cloud.Done<Void> done) {
        try {
            String data = payload(c);
            String hash = Integer.toHexString(data.hashCode()) + ":" + data.length();
            Cloud.uploadDash(c, data, new Cloud.Done<Void>() {
                @Override public void ok(Void r) {
                    sp(c).edit().putString(SENT_HASH, hash).putLong(SENT_AT, System.currentTimeMillis()).apply();
                    done.ok(null);
                }

                @Override public void fail(String reason) {
                    done.fail(reason);
                }
            });
        } catch (Exception e) {
            done.fail("Couldn't prepare your data.");
        }
    }

    /** Switches off and deletes the online copy. */
    static void remove(Context c, Cloud.Done<Void> done) {
        Cloud.deleteDash(c, new Cloud.Done<Void>() {
            @Override public void ok(Void r) {
                setOn(c, false);
                sp(c).edit().remove(SENT_AT).apply();
                done.ok(null);
            }

            @Override public void fail(String reason) {
                done.fail(reason);
            }
        });
    }
}
