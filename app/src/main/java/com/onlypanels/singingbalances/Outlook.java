package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Hands reminders to the Outlook app on the phone. No sign-in needed: Outlook opens
 * with everything filled in, and you tap Send / Save. If Outlook can't handle it,
 * Android offers your other mail or calendar apps instead.
 */
final class Outlook {
    static final String PACKAGE = "com.microsoft.office.outlook";

    private Outlook() {}

    private static void launch(Activity a, Intent i, String chooserTitle) {
        i.setPackage(PACKAGE);
        try {
            a.startActivity(i);
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        i.setPackage(null);
        try {
            a.startActivity(Intent.createChooser(i, chooserTitle));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No app found to do this. Is Outlook installed?", Toast.LENGTH_LONG).show();
        }
    }

    /** Opens an email to the client asking for the outstanding balance. */
    static void emailReminder(Activity a, Gig g) {
        String when = Dates.fmt(g.gigDay);
        String subject = "Payment reminder – " + (g.event.isEmpty() ? "performance on " + when : g.event + " (" + when + ")");
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(g.client).append(",\n\n")
                .append("I hope you're well. Just a friendly reminder that ")
                .append(Money.fmt(g.balance()))
                .append(" is still outstanding for my performance");
        if (!g.event.isEmpty()) body.append(" at ").append(g.event);
        body.append(" on ").append(when);
        if (g.paidCents > 0) {
            body.append(" (fee ").append(Money.fmt(g.feeCents))
                    .append(", ").append(Money.fmt(g.paidCents)).append(" received so far)");
        }
        body.append(".\n\nI'd be grateful if you could arrange payment at your earliest convenience.\n\nMany thanks,\n");

        String to = g.email == null ? "" : g.email.trim();
        Uri uri = Uri.parse("mailto:" + Uri.encode(to)
                + "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body.toString()));
        Intent i = new Intent(Intent.ACTION_SENDTO, uri);
        if (!to.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body.toString());
        if (to.isEmpty()) {
            Toast.makeText(a, "No client email saved – add the address in Outlook", Toast.LENGTH_LONG).show();
        }
        launch(a, i, "Send reminder with");
    }

    /** Adds a 10:00 calendar reminder on the due date (or tomorrow if that has passed). */
    static void calendarReminder(Activity a, Gig g) {
        long day = Math.max(g.dueDay, Dates.today() + 1);
        long begin = LocalDate.ofEpochDay(day).atTime(10, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String details = g.title() + "\nGig date: " + Dates.fmt(g.gigDay)
                + "\nFee: " + Money.fmt(g.feeCents)
                + "\nPaid so far: " + Money.fmt(g.paidCents)
                + "\nStill owed: " + Money.fmt(g.balance())
                + (g.email.isEmpty() ? "" : "\nEmail: " + g.email);

        Intent i = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 15 * 60 * 1000L)
                .putExtra(CalendarContract.Events.TITLE, "Chase payment: " + g.client + " " + Money.fmt(g.balance()))
                .putExtra(CalendarContract.Events.DESCRIPTION, details);
        launch(a, i, "Add reminder to");
    }
}
