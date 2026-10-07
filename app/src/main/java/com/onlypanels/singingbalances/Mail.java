package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.Context;

import java.io.File;
import java.io.IOException;

/** Whichever account the user signed in with (Google or Microsoft) – ShowFee sends from that address. */
final class Mail {
    private Mail() {}

    static boolean isConnected(Context c) {
        return GoogleAccount.isConnected(c) || MicrosoftAccount.isConnected(c);
    }

    static boolean isMicrosoft(Context c) {
        return MicrosoftAccount.isConnected(c);
    }

    static String email(Context c) {
        return isMicrosoft(c) ? MicrosoftAccount.email(c) : GoogleAccount.email(c);
    }

    /** "Gmail" or "Outlook", for messages like "a copy is in your Outlook Sent folder". */
    static String service(Context c) {
        return isMicrosoft(c) ? "Outlook" : "Gmail";
    }

    static void authorize(Activity a, GoogleAccount.TokenCallback cb) {
        if (isMicrosoft(a)) MicrosoftAccount.authorize(a, true, cb);
        else GoogleAccount.authorize(a, true, cb);
    }

    /** Runs on a background thread. */
    static void send(Context c, String token, String to, String subject, String body, File pdf, String payUrl) throws IOException {
        String text = plain(body, payUrl), html = html(body, payUrl, Theme.strong(c));
        if (isMicrosoft(c)) GraphSender.send(token, to, subject, text, html, pdf);
        else GmailSender.send(token, to, subject, text, html, pdf);
    }

    /** Written in the message where the pay link goes; it becomes a "Pay here" button in the sent email. */
    static final String PAY_HERE = "[Pay here]";

    /** For email apps and the plain-text copy: the marker becomes the full web address. */
    static String plain(String body, String payUrl) {
        if (payUrl == null || payUrl.isEmpty()) return body.replace(PAY_HERE, "").trim();
        return body.replace(PAY_HERE, payUrl);
    }

    /** The email as a web page, with the marker turned into a "Pay here" button. Null if there's no button. */
    static String html(String body, String payUrl, int color) {
        if (payUrl == null || payUrl.isEmpty() || !body.contains(PAY_HERE)) return null;
        String e = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        String href = payUrl.replace("&", "&amp;").replace("\"", "&quot;");
        String button = "<a href=\"" + href + "\" style=\"display:inline-block;padding:10px 22px;margin:6px 0;"
                + "background:" + String.format("#%06X", color & 0xFFFFFF) + ";color:#ffffff;text-decoration:none;border-radius:8px;font-weight:bold\">Pay here</a>";
        e = e.replace(PAY_HERE, button).replace("\r\n", "\n").replace("\n", "<br>\n");
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.5;color:#1b1c20\">"
                + e + "</div>";
    }
}
