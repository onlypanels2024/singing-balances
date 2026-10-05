package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * "Add to your calendar?" – Google Calendar opens with the booking filled in (tap Save);
 * Outlook gets the booking added straight away through the user's Microsoft sign-in.
 */
final class Calendar {
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Calendar() {}

    /** afterNew = shown right after a new booking is saved. */
    static void offer(Activity a, Gig g, boolean afterNew) {
        if (a.isFinishing()) return;
        new AlertDialog.Builder(a)
                .setTitle(afterNew ? "Add to your calendar?" : "Add to calendar")
                .setMessage(g.title() + "\n" + g.when())
                .setPositiveButton("Google Calendar", (d, w) -> Google.calendarGig(a, g))
                .setNeutralButton("Outlook", (d, w) -> outlook(a, g))
                .setNegativeButton(afterNew ? "Not now" : "Cancel", null)
                .show();
    }

    private static void outlook(Activity a, Gig g) {
        if (!MicrosoftAccount.isConnected(a)) {
            new AlertDialog.Builder(a)
                    .setTitle("Sign in with Microsoft")
                    .setMessage("To add " + Words.many(a) + " straight to your Outlook calendar, sign in with your "
                            + "Microsoft account (Outlook, Hotmail).\n\nOutRo will then also send your invoices and "
                            + "reminders from that Outlook address. You can change this in Settings › Email.")
                    .setPositiveButton("Sign in", (d, w) -> addToOutlook(a, g, true))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        addToOutlook(a, g, true);
    }

    private static void addToOutlook(Activity a, Gig g, boolean firstTry) {
        MicrosoftAccount.authorize(a, true, MicrosoftAccount.CALENDAR_SCOPES, new GoogleAccount.TokenCallback() {
            @Override
            public void ok(String token) {
                Toast.makeText(a, "Adding to your Outlook calendar…", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    String err = null;
                    boolean expired = false;
                    try {
                        createEvent(token, g, a);
                    } catch (GmailSender.AuthExpired e) {
                        expired = true;
                    } catch (Exception e) {
                        err = e.getMessage() == null ? "Couldn't add it" : e.getMessage();
                    }
                    final String fErr = err;
                    final boolean fExpired = expired;
                    a.runOnUiThread(() -> {
                        if (fExpired && firstTry) addToOutlook(a, g, false);
                        else if (fErr == null && !fExpired) {
                            Toast.makeText(a, "Added to your Outlook calendar ✓", Toast.LENGTH_LONG).show();
                        } else failed(a, g, fExpired ? "Microsoft sign-in expired." : fErr);
                    });
                }).start();
            }

            @Override
            public void fail(String reason) {
                failed(a, g, reason);
            }
        });
    }

    private static void failed(Activity a, Gig g, String reason) {
        if (a.isFinishing()) return;
        new AlertDialog.Builder(a)
                .setTitle("Not added to Outlook")
                .setMessage(reason + "\n\nYou can add it to Google Calendar instead.")
                .setPositiveButton("Google Calendar", (d, w) -> Google.calendarGig(a, g))
                .setNegativeButton("Close", null)
                .show();
    }

    /** The event as Outlook (Microsoft Graph) wants it. Times are sent in UTC. */
    static String eventJson(Gig g, String feeText, String noteLine) throws Exception {
        JSONObject e = new JSONObject();
        e.put("subject", g.title());
        e.put("body", new JSONObject().put("contentType", "Text").put("content",
                "Client: " + g.client + "\nFee: " + feeText + noteLine + (g.notes.isEmpty() ? "" : "\n\n" + g.notes)));
        if (!g.event.isEmpty()) e.put("location", new JSONObject().put("displayName", g.event));
        e.put("showAs", g.status == Gig.PENCILLED ? "tentative" : "busy");
        LocalDate day = LocalDate.ofEpochDay(g.gigDay);
        if (g.startMin >= 0) {
            Instant start = LocalDateTime.of(day, java.time.LocalTime.ofSecondOfDay(g.startMin * 60L))
                    .atZone(ZoneId.systemDefault()).toInstant();
            Instant end = start.plusSeconds(3 * 3600);
            e.put("start", new JSONObject().put("dateTime", ISO.format(start.atOffset(ZoneOffset.UTC))).put("timeZone", "UTC"));
            e.put("end", new JSONObject().put("dateTime", ISO.format(end.atOffset(ZoneOffset.UTC))).put("timeZone", "UTC"));
            e.put("isReminderOn", true);
            e.put("reminderMinutesBeforeStart", 120);
        } else {
            e.put("isAllDay", true);
            e.put("start", new JSONObject().put("dateTime", day + "T00:00:00").put("timeZone", "UTC"));
            e.put("end", new JSONObject().put("dateTime", day.plusDays(1) + "T00:00:00").put("timeZone", "UTC"));
        }
        return e.toString();
    }

    private static void createEvent(String token, Gig g, Activity a) throws Exception {
        String json = eventJson(g, Money.fmt(g.feeCents),
                g.status == Gig.PENCILLED ? "\n(Pencilled in – not confirmed yet)" : "");
        HttpURLConnection c = (HttpURLConnection) new URL("https://graph.microsoft.com/v1.0/me/events").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (OutputStream out = c.getOutputStream()) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        if (code == 401) throw new GmailSender.AuthExpired();
        if (code / 100 != 2) {
            String err = "";
            try (InputStream es = c.getErrorStream()) {
                if (es != null) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] b = new byte[4096];
                    int n;
                    while ((n = es.read(b)) > 0) bo.write(b, 0, n);
                    err = bo.toString("UTF-8");
                }
            } catch (Exception ignored) {
            }
            String msg = err.contains("\"message\"") ? err.replaceAll("(?s).*\"message\"\\s*:\\s*\"([^\"]*)\".*", "$1") : "";
            throw new IOException("Outlook said no (" + code + ")" + (msg.isEmpty() ? "" : ": " + msg));
        }
    }
}
