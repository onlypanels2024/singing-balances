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
    static void send(Context c, String token, String to, String subject, String body, File pdf) throws IOException {
        if (isMicrosoft(c)) GraphSender.send(token, to, subject, body, pdf);
        else GmailSender.send(token, to, subject, body, pdf);
    }
}
