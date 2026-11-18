package com.onlypanels.singingbalances;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the user's bookings in a calendar on their phone (Google, Samsung, Outlook… – any calendar on the phone that
 * can be written to). Every booking is added, updated when it changes, and removed when it's cancelled or deleted.
 * One way only: ShowFee is the main copy. Bookings older than 90 days are left alone.
 */
final class CalendarSync {
    private static final String PREFS = "cal_sync", ON = "on", CAL = "cal_id", CAL_NAME = "cal_name";
    private static final String MARK = "Added by ShowFee";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static Runnable pending;

    private CalendarSync() {}

    static final class Cal {
        long id;
        String name, account;

        @Override public String toString() {
            return name.equals(account) || account.isEmpty() ? name : name + " (" + account + ")";
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean isOn(Context c) {
        return sp(c).getBoolean(ON, false) && sp(c).getLong(CAL, -1) >= 0;
    }

    static String calendarName(Context c) {
        return sp(c).getString(CAL_NAME, "");
    }

    /** Calendars on this phone that ShowFee can add events to. */
    static List<Cal> calendars(Context c) {
        List<Cal> out = new ArrayList<>();
        if (!allowed(c)) return out;
        String[] cols = {CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME};
        try (Cursor k = c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI, cols,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ? AND " + CalendarContract.Calendars.VISIBLE + " = 1",
                new String[]{String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)},
                CalendarContract.Calendars.ACCOUNT_NAME + ", " + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)) {
            while (k != null && k.moveToNext()) {
                Cal x = new Cal();
                x.id = k.getLong(0);
                x.name = k.getString(1) == null ? "Calendar" : k.getString(1);
                x.account = k.getString(2) == null ? "" : k.getString(2);
                out.add(x);
            }
        } catch (Exception ignored) {
            // no calendar app / not allowed
        }
        return out;
    }

    /** Switches syncing on to this calendar (moving ShowFee's events from another calendar if one was used). */
    static void start(Context c, Cal cal, Runnable done) {
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            long old = sp(app).getLong(CAL, -1);
            if (old >= 0 && old != cal.id) removeEvents(app);
            sp(app).edit().putBoolean(ON, true).putLong(CAL, cal.id).putString(CAL_NAME, cal.toString()).apply();
            syncNow(app);
            if (done != null) MAIN.post(done);
        });
    }

    /** Switches syncing off; removeEvents = also take ShowFee's events out of the calendar. */
    static void stop(Context c, boolean removeEvents, Runnable done) {
        Context app = c.getApplicationContext();
        IO.execute(() -> {
            if (removeEvents) removeEvents(app);
            sp(app).edit().clear().apply();
            if (done != null) MAIN.post(done);
        });
    }

    /** After any change to the bookings: sync a moment later (several quick changes = one sync). */
    static void schedule(Context c) {
        if (!isOn(c)) return;
        final Context app = c.getApplicationContext();
        if (pending != null) MAIN.removeCallbacks(pending);
        pending = () -> IO.execute(() -> syncNow(app));
        MAIN.postDelayed(pending, 1500);
    }

    /** How many bookings are in the calendar now. */
    static int count(Context c) {
        int n = 0;
        for (String k : sp(c).getAll().keySet()) if (k.startsWith("g")) n++;
        return n;
    }

    /** Brings the calendar up to date. Returns how many events were added, changed or removed. */
    static synchronized int syncNow(Context c) {
        if (!isOn(c) || !allowed(c)) return 0;
        long cal = sp(c).getLong(CAL, -1);
        ContentResolver cr = c.getContentResolver();
        SharedPreferences.Editor ed = sp(c).edit();
        Map<String, ?> known = new HashMap<>(sp(c).getAll());
        long from = Dates.today() - 90;
        int changes = 0;
        java.util.Set<String> live = new java.util.HashSet<>();
        for (Gig g : Db.get(c).allGigs()) {
            String key = "g" + g.id;
            if (g.gigDay < from) {
                live.add(key); // old: leave its event as it is
                continue;
            }
            if (g.isCancelled()) continue; // removed below
            live.add(key);
            ContentValues v = values(c, g, cal);
            String hash = Integer.toHexString(v.toString().hashCode());
            String was = known.get(key) instanceof String ? (String) known.get(key) : "";
            long eventId = was.contains("|") ? Long.parseLong(was.substring(0, was.indexOf('|'))) : -1;
            if (eventId >= 0 && was.endsWith("|" + hash) && exists(cr, eventId)) continue;
            try {
                if (eventId >= 0 && cr.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), v, null, null) > 0) {
                    changes++;
                } else {
                    Uri u = cr.insert(CalendarContract.Events.CONTENT_URI, v);
                    if (u == null) continue;
                    eventId = ContentUris.parseId(u);
                    if (g.startMin >= 0) {
                        ContentValues r = new ContentValues();
                        r.put(CalendarContract.Reminders.EVENT_ID, eventId);
                        r.put(CalendarContract.Reminders.MINUTES, 120);
                        r.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
                        cr.insert(CalendarContract.Reminders.CONTENT_URI, r);
                    }
                    changes++;
                }
                ed.putString(key, eventId + "|" + hash);
            } catch (Exception ignored) {
                // calendar busy or gone: tried again next time
            }
        }
        for (String key : known.keySet()) {
            if (!key.startsWith("g") || live.contains(key)) continue;
            String was = String.valueOf(known.get(key));
            try {
                long eventId = Long.parseLong(was.substring(0, was.indexOf('|')));
                cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null);
                changes++;
            } catch (Exception ignored) { }
            ed.remove(key);
        }
        ed.apply();
        return changes;
    }

    private static boolean exists(ContentResolver cr, long eventId) {
        try (Cursor k = cr.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                new String[]{CalendarContract.Events.DELETED}, null, null, null)) {
            return k != null && k.moveToFirst() && k.getInt(0) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static ContentValues values(Context c, Gig g, long cal) {
        ContentValues v = new ContentValues();
        v.put(CalendarContract.Events.CALENDAR_ID, cal);
        v.put(CalendarContract.Events.TITLE, g.title() + (g.status == Gig.PENCILLED ? " (pencilled in)" : ""));
        v.put(CalendarContract.Events.EVENT_LOCATION, g.event);
        v.put(CalendarContract.Events.DESCRIPTION, "Client: " + g.client + "\nFee: " + Money.fmt(g.total())
                + (g.status == Gig.PENCILLED ? "\n(Pencilled in – not confirmed yet)" : "")
                + (g.notes.isEmpty() ? "" : "\n\n" + g.notes) + "\n\n" + MARK);
        v.put(CalendarContract.Events.AVAILABILITY, g.status == Gig.PENCILLED
                ? CalendarContract.Events.AVAILABILITY_TENTATIVE : CalendarContract.Events.AVAILABILITY_BUSY);
        if (g.startMin >= 0) {
            long begin = Google.millis(g.gigDay, g.startMin);
            v.put(CalendarContract.Events.DTSTART, begin);
            v.put(CalendarContract.Events.DTEND, begin + 3 * 3_600_000L);
            v.put(CalendarContract.Events.ALL_DAY, 0);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().getID());
        } else {
            long begin = g.gigDay * 86_400_000L; // all-day events are stored at midnight UTC
            v.put(CalendarContract.Events.DTSTART, begin);
            v.put(CalendarContract.Events.DTEND, begin + 86_400_000L);
            v.put(CalendarContract.Events.ALL_DAY, 1);
            v.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC");
        }
        return v;
    }

    /** Takes every event ShowFee added out of the calendar. */
    private static void removeEvents(Context c) {
        ContentResolver cr = c.getContentResolver();
        for (Map.Entry<String, ?> e : sp(c).getAll().entrySet()) {
            if (!e.getKey().startsWith("g")) continue;
            String was = String.valueOf(e.getValue());
            try {
                long eventId = Long.parseLong(was.substring(0, was.indexOf('|')));
                if (allowed(c)) cr.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null);
            } catch (Exception ignored) { }
        }
        SharedPreferences.Editor ed = sp(c).edit();
        for (String k : sp(c).getAll().keySet()) if (k.startsWith("g")) ed.remove(k);
        ed.apply();
    }
}
