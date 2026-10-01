package com.onlypanels.singingbalances;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Once a day (default 10:00) checks your gigs and sends reminders to your phone. */
public class Nudges extends BroadcastReceiver {
    static final String ACTION_CHECK = "com.onlypanels.singingbalances.CHECK";
    private static final String CHANNEL = "reminders";

    /** Sets up (or refreshes) the daily alarm. Safe to call often. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(c);
        am.cancel(pi);
        if (!Prefs.notify(c)) return;
        LocalDateTime next = LocalDate.now().atTime(Prefs.notifyHour(c), 0);
        if (!next.isAfter(LocalDateTime.now())) next = next.plusDays(1);
        long at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at, AlarmManager.INTERVAL_DAY, pi);
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, Nudges.class).setAction(ACTION_CHECK);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            schedule(c);
            Widget.updateAll(c);
            return;
        }
        Widget.updateAll(c);
        if (Prefs.notify(c)) check(c, false);
    }

    private static class Note {
        final int id;
        final String title, text;
        final long gigId;

        Note(int id, String title, String text, long gigId) {
            this.id = id;
            this.title = title;
            this.text = text;
            this.gigId = gigId;
        }
    }

    /** Works out today's reminders and shows them. Returns how many were shown. */
    static int check(Context c, boolean test) {
        Theme.load(c);
        Db db = Db.get(c);
        long today = Dates.today();
        List<Note> notes = new ArrayList<>();

        for (Gig g : db.gigsBetween(today, today + 1)) {
            if (g.isCancelled()) continue;
            boolean isToday = g.gigDay == today;
            String when = (isToday ? Words.tonight(c) : "Tomorrow") + (g.startMin >= 0 ? " at " + Dates.time(g.startMin) : "");
            String extra = g.status == Gig.PENCILLED ? " · still only pencilled in" : "";
            notes.add(new Note((int) (1000 + g.id), when + ": " + g.title(),
                    "Fee " + Money.fmt(g.feeCents) + " – remember to collect payment" + extra, g.id));
        }

        long overdue = 0;
        int overdueCount = 0;
        for (Gig g : db.unpaidGigs()) {
            if (g.gigDay == today - 1) {
                notes.add(new Note((int) (2000 + g.id), "Did " + g.client + " pay you?",
                        Money.fmt(g.balance()) + " still open from yesterday's " + Words.one(c) + (g.event.isEmpty() ? "" : " at " + g.event)
                                + ". Tap to record the payment.", g.id));
            } else if (g.isOverdue()) {
                overdue += g.balance();
                overdueCount++;
            }
        }
        if (overdueCount > 0) {
            notes.add(new Note(1, Money.fmt(overdue) + " overdue",
                    Words.count(c, overdueCount) + (overdueCount == 1 ? " is" : " are") + " past the payment date. Tap to see who owes you.", 0));
        }
        if (test && notes.isEmpty()) {
            notes.add(new Note(2, "Reminders are working ✓",
                    "Nothing needs your attention today. You'll be reminded about " + Words.many(c) + " and late payments here.", 0));
        }

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Booking & payment reminders",
                NotificationManager.IMPORTANCE_DEFAULT));
        for (Note n : notes) {
            Intent open = n.gigId > 0
                    ? new Intent(c, GigActivity.class).putExtra("id", n.gigId)
                    : new Intent(c, MainActivity.class).putExtra("tab", "unpaid");
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, n.id, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification notif = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notify)
                    .setColor(Ui.PRIMARY)
                    .setContentTitle(n.title)
                    .setContentText(n.text)
                    .setStyle(new Notification.BigTextStyle().bigText(n.text))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            try {
                nm.notify(n.id, notif);
            } catch (SecurityException ignored) {
                // notifications not allowed
            }
        }
        return notes.size();
    }
}
