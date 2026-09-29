package com.onlypanels.singingbalances;

import android.content.Context;
import android.content.SharedPreferences;

/** Your details and app settings, stored on the phone. */
final class Prefs {
    static final String NAME = "name";           // your name / stage name
    static final String EMAIL = "email";
    static final String PHONE = "phone";
    static final String ADDRESS = "address";
    static final String VAT = "vat";
    static final String IBAN = "iban";
    static final String BIC = "bic";
    static final String BANK_NAME = "bank_name"; // account holder name
    static final String REVOLUT = "revolut";     // Revolut tag or payment link
    static final String INVOICE_PREFIX = "invoice_prefix";
    static final String NEXT_INVOICE = "next_invoice";
    static final String TERMS = "terms_days";
    static final String NOTIFY = "notify";
    static final String NOTIFY_HOUR = "notify_hour";
    static final String BACKUP_URI = "backup_uri";
    static final String BACKUP_LAST_OK = "backup_last_ok";
    static final String BACKUP_ERROR = "backup_error";

    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static String get(Context c, String key) {
        return sp(c).getString(key, "");
    }

    static void set(Context c, String key, String value) {
        sp(c).edit().putString(key, value == null ? "" : value.trim()).apply();
    }

    /** Days after the gig that payment is due. 0 = paid on the night. */
    static int termsDays(Context c) {
        return sp(c).getInt(TERMS, 0);
    }

    static boolean notify(Context c) {
        return sp(c).getBoolean(NOTIFY, true);
    }

    static int notifyHour(Context c) {
        return sp(c).getInt(NOTIFY_HOUR, 10);
    }

    static String invoicePrefix(Context c) {
        String p = get(c, INVOICE_PREFIX);
        return p.isEmpty() ? "INV-" : p;
    }

    /** Returns the next invoice number (e.g. INV-0007) and moves the counter on. */
    static synchronized String takeInvoiceNumber(Context c) {
        int n = sp(c).getInt(NEXT_INVOICE, 1);
        sp(c).edit().putInt(NEXT_INVOICE, n + 1).apply();
        return invoicePrefix(c) + String.format(java.util.Locale.US, "%04d", n);
    }

    static boolean hasPaymentDetails(Context c) {
        return !get(c, IBAN).isEmpty() || !get(c, REVOLUT).isEmpty();
    }

    /** Lines describing how to pay you, for emails and invoices. Empty if none set. */
    static String paymentDetails(Context c) {
        StringBuilder sb = new StringBuilder();
        String holder = get(c, BANK_NAME).isEmpty() ? get(c, NAME) : get(c, BANK_NAME);
        if (!get(c, IBAN).isEmpty()) {
            sb.append("Bank transfer\n");
            if (!holder.isEmpty()) sb.append("Account name: ").append(holder).append('\n');
            sb.append("IBAN: ").append(get(c, IBAN)).append('\n');
            if (!get(c, BIC).isEmpty()) sb.append("BIC/SWIFT: ").append(get(c, BIC)).append('\n');
        }
        if (!get(c, REVOLUT).isEmpty()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("Revolut: ").append(get(c, REVOLUT)).append('\n');
        }
        return sb.toString().trim();
    }
}
