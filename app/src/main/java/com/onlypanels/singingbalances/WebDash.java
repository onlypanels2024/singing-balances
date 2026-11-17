package com.onlypanels.singingbalances;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * ShowFee web dashboard (every plan) (showfee.app/dashboard). When switched on, a copy of the user's bookings,
 * payments, clients and expenses is kept online under their own account, so they can see it on a computer.
 * Bank details, sign-ins and settings are never sent. Read-only on the web for now (stage 1).
 */
final class WebDash {
    static final String URL = "https://showfee.app/dashboard/";
    private static final String ON = "web_dash_on", SENT_HASH = "web_dash_hash", SENT_AT = "web_dash_at";

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
                .put("v", 1)
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
