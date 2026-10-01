package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.Toast;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Hands emails to Gmail or Outlook (the user's choice) and calendar entries to Google Calendar on the phone. No sign-in needed:
 * the app opens with everything filled in and you tap Send / Save. If it can't handle it,
 * Android offers your other apps instead.
 */
final class Google {
    static final String GMAIL = "com.google.android.gm";
    static final String OUTLOOK = "com.microsoft.office.outlook";
    static final String[] EMAIL_KEYS = {"gmail", "outlook", "ask"};
    static final String[] EMAIL_NAMES = {"Gmail", "Outlook", "Ask each time"};

    static String emailChoice(android.content.Context c) {
        String v = Prefs.get(c, Prefs.EMAIL_APP);
        return v.isEmpty() ? "gmail" : v;
    }

    /** "Gmail", "Outlook", or "" when the user picks an app each time. */
    static String emailAppName(android.content.Context c) {
        switch (emailChoice(c)) {
            case "outlook": return "Outlook";
            case "ask": return "";
            default: return "Gmail";
        }
    }

    private static String emailPackage(android.content.Context c) {
        switch (emailChoice(c)) {
            case "outlook": return OUTLOOK;
            case "ask": return null;
            default: return GMAIL;
        }
    }
    static final String GOOGLE_CALENDAR = "com.google.android.calendar";

    private Google() {}

    private static void launch(Activity a, Intent i, String chooserTitle) {
        launch(a, i, chooserTitle, emailPackage(a));
    }

    /** Tries the preferred app first; if it isn't installed, offers the phone's other apps. */
    private static void launch(Activity a, Intent i, String chooserTitle, String preferredPackage) {
        if (preferredPackage != null) {
            i.setPackage(preferredPackage);
            try {
                a.startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
                // that app isn't installed: offer the phone's other apps instead
            }
        }
        i.setPackage(null);
        try {
            a.startActivity(Intent.createChooser(i, chooserTitle));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No app found to do this. Is the app installed?", Toast.LENGTH_LONG).show();
        }
    }

    private static String signOff(Activity a) {
        String name = Prefs.get(a, Prefs.NAME);
        return "Many thanks,\n" + name;
    }

    private static String payBlock(Activity a, Gig g) {
        String pay = Prefs.paymentDetails(a);
        if (pay.isEmpty()) return "";
        return "\n\nYou can pay by:\n" + pay + "\nReference: " + g.invoiceNo;
    }

    private static String gigDesc(Gig g) {
        return (g.event.isEmpty() ? "" : " at " + g.event) + " on " + Dates.fmt(g.gigDay);
    }

    /** Email with the invoice PDF attached. */
    private static void sendWithInvoice(Activity a, Gig g, String subject, String body) {
        File pdf;
        try {
            pdf = Invoice.create(a, g);
        } catch (Exception e) {
            Toast.makeText(a, "Couldn't make the invoice: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Uri uri = ShareProvider.uriFor(pdf);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri(pdf.getName(), uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!g.email.isEmpty()) i.putExtra(Intent.EXTRA_EMAIL, new String[]{g.email});
        i.putExtra(Intent.EXTRA_SUBJECT, subject);
        i.putExtra(Intent.EXTRA_TEXT, body);
        if (g.email.isEmpty()) {
            Toast.makeText(a, "No email saved for " + g.client + " – add the address in your email app", Toast.LENGTH_LONG).show();
        }
        launch(a, i, "Send with");
    }

    static void emailInvoice(Activity a, Gig g) {
        Db.get(a).assignInvoice(g);
        String subject = "Invoice " + g.invoiceNo + " – " + (g.event.isEmpty() ? Words.invoiceLine(a) : g.event)
                + " (" + Dates.fmt(g.gigDay) + ")";
        String body = "Hi " + g.client + ",\n\n"
                + (g.isPaid()
                ? "Please find attached the invoice for the " + Words.one(a) + gigDesc(g) + ", marked as paid. Thank you!"
                : "Please find attached my invoice for the " + Words.one(a) + gigDesc(g) + ". The amount due is "
                + Money.fmt(g.balance()) + (g.dueDay < Dates.today() ? ", payable on receipt." : ", payable by " + Dates.fmt(g.dueDay) + ".") + payBlock(a, g))
                + "\n\n" + signOff(a);
        sendWithInvoice(a, g, subject, body);
    }

    /** Friendly payment reminder with payment details and the invoice attached. */
    static void emailReminder(Activity a, Gig g) {
        Db.get(a).assignInvoice(g);
        String subject = "Payment reminder – " + (g.event.isEmpty() ? Words.one(a) + " on " + Dates.fmt(g.gigDay)
                : g.event + " (" + Dates.fmt(g.gigDay) + ")");
        StringBuilder body = new StringBuilder();
        body.append("Hi ").append(g.client).append(",\n\n")
                .append("I hope you're well. Just a friendly reminder that ")
                .append(Money.fmt(g.balance()))
                .append(" is still outstanding for the ").append(Words.one(a)).append(gigDesc(g));
        if (g.paidCents > 0) {
            body.append(" (fee ").append(Money.fmt(g.feeCents))
                    .append(", ").append(Money.fmt(g.paidCents)).append(" received so far)");
        }
        body.append(". I've attached the invoice (").append(g.invoiceNo).append(") for your reference.");
        body.append(payBlock(a, g));
        body.append("\n\nI'd be grateful if you could arrange payment at your earliest convenience.\n\n").append(signOff(a));
        sendWithInvoice(a, g, subject, body.toString());
    }

    private static long millis(long day, int minutes) {
        return LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                + minutes * 60_000L;
    }

    /** Puts the gig itself in your calendar. */
    static void calendarGig(Activity a, Gig g) {
        Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, g.title())
                .putExtra(CalendarContract.Events.EVENT_LOCATION, g.event)
                .putExtra(CalendarContract.Events.DESCRIPTION, "Client: " + g.client
                        + "\nFee: " + Money.fmt(g.feeCents)
                        + (g.status == Gig.PENCILLED ? "\n(Pencilled in – not confirmed yet)" : "")
                        + (g.notes.isEmpty() ? "" : "\n\n" + g.notes));
        if (g.startMin >= 0) {
            long begin = millis(g.gigDay, g.startMin);
            i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3 * 3_600_000L);
        } else {
            long begin = millis(g.gigDay, 0);
            i.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 86_400_000L);
        }
        launch(a, i, "Add to calendar", GOOGLE_CALENDAR);
    }

    /** A 10:00 reminder to chase payment on the due date (or tomorrow if that has passed). */
    static void calendarChase(Activity a, Gig g) {
        long day = Math.max(g.dueDay, Dates.today() + 1);
        long begin = millis(day, 10 * 60);
        String details = g.title() + "\n" + Words.One(a) + " date: " + Dates.fmt(g.gigDay)
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
        launch(a, i, "Add reminder to", GOOGLE_CALENDAR);
    }
}
