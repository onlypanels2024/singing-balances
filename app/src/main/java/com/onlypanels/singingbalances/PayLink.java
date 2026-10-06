package com.onlypanels.singingbalances;

import android.content.Context;
import android.net.Uri;

import java.util.Locale;

/**
 * One-tap payment links for clients: PayPal.me, Revolut, a Stripe Payment Link or any other link.
 * The money goes straight from the client to the user; ShowFee only builds the link.
 * The user's username or link is kept under {@link Prefs#REVOLUT} (its original name, so old backups still work).
 */
final class PayLink {
    static final String NONE = "none", PAYPAL = "paypal", REVOLUT = "revolut", STRIPE = "stripe", OTHER = "link";
    static final String[] KEYS = {NONE, PAYPAL, REVOLUT, STRIPE, OTHER};
    static final String[] NAMES = {"None", "PayPal", "Revolut", "Stripe", "Other link"};

    private PayLink() {}

    /** Which service the user picked. Older versions only had a free-text field, so it's worked out from that. */
    static String service(Context c) {
        String s = Prefs.get(c, Prefs.PAY_SERVICE);
        if (!s.isEmpty()) return s;
        return guess(Prefs.get(c, Prefs.REVOLUT));
    }

    static String guess(String v) {
        String l = v.toLowerCase(Locale.ROOT).trim();
        if (l.isEmpty()) return NONE;
        if (l.contains("paypal")) return PAYPAL;
        if (l.contains("stripe.com")) return STRIPE;
        if (l.contains("revolut") || l.startsWith("@")) return REVOLUT;
        if (l.startsWith("http") || l.contains(".")) return OTHER;
        return REVOLUT; // a bare tag was almost always a Revolut tag
    }

    static String name(Context c) {
        String s = service(c);
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(s)) return NAMES[i];
        return NAMES[0];
    }

    /** Just the username from "paypal.me/gary", "@gary", "https://revolut.me/gary" … */
    static String username(String v) {
        String u = v.trim();
        u = u.replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "");
        u = u.replaceFirst("(?i)^(paypal\\.me|paypal\\.com/paypalme|revolut\\.me)/", "");
        if (u.startsWith("@")) u = u.substring(1);
        int slash = u.indexOf('/');
        if (slash >= 0) u = u.substring(0, slash);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        return u.trim();
    }

    /** True if this service fills the amount in for the client. */
    static boolean fillsAmount(Context c) {
        return PAYPAL.equals(service(c));
    }

    /** The link the client taps, or "" if none is set up. Amount is filled in where the service allows it. */
    static String url(Context c, Gig g) {
        String v = Prefs.get(c, Prefs.REVOLUT).trim();
        if (v.isEmpty()) return "";
        switch (service(c)) {
            case PAYPAL: {
                String u = username(v);
                if (u.isEmpty()) return "";
                String link = "https://paypal.me/" + u;
                long cents = g == null ? 0 : g.balance();
                return cents > 0 ? link + "/" + amount(cents) + Money.code(c) : link;
            }
            case REVOLUT: {
                String u = username(v);
                return u.isEmpty() ? "" : "https://revolut.me/" + u;
            }
            case STRIPE:
            case OTHER: {
                String link = v.matches("(?i)^https?://.*") ? v : "https://" + v;
                // Stripe Payment Links can carry the invoice number, so it shows up in the user's Stripe dashboard.
                if (STRIPE.equals(service(c)) && g != null && g.invoiceNo != null && !g.invoiceNo.isEmpty()
                        && !link.contains("client_reference_id")) {
                    link = Uri.parse(link).buildUpon()
                            .appendQueryParameter("client_reference_id", g.invoiceNo.replaceAll("[^A-Za-z0-9_-]", "_"))
                            .build().toString();
                }
                return link;
            }
            default:
                return "";
        }
    }

    /** Short label for the button and emails, e.g. "Pay €900.00 with PayPal". */
    static String label(Context c, Gig g) {
        String via = OTHER.equals(service(c)) ? "online" : "with " + name(c);
        return "Pay " + (g != null && g.balance() > 0 ? Money.fmt(g.balance()) + " " : "") + via;
    }

    /** "900" or "900.50" – PayPal wants a plain number with a dot. */
    static String amount(long cents) {
        return cents % 100 == 0 ? String.valueOf(cents / 100)
                : String.format(Locale.US, "%d.%02d", cents / 100, cents % 100);
    }

    /** What the user should type for each service. */
    static String hint(String service) {
        switch (service) {
            case PAYPAL: return "Your PayPal.me username, e.g. garyfalzon";
            case REVOLUT: return "Your Revolut @username, e.g. @garyfalzon";
            case STRIPE: return "Your Stripe Payment Link, e.g. https://buy.stripe.com/…";
            case OTHER: return "Any payment web address";
            default: return "";
        }
    }

    static String note(String service) {
        switch (service) {
            case PAYPAL: return "The amount is filled in for your client. They can pay with PayPal or a card.";
            case REVOLUT: return "Your client types in the amount – it's shown right next to the link.";
            case STRIPE: return "Make a Payment Link in Stripe and choose \"Customers choose what to pay\". "
                    + "The invoice number is added to the link so you can match payments.";
            case OTHER: return "Your client opens this link to pay.";
            default: return "Pick a service to add a \"Pay now\" link and QR code to your invoices and reminders.";
        }
    }
}
