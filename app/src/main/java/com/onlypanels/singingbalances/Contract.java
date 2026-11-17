package com.onlypanels.singingbalances;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * ShowFee Business contracts. Each version of a booking's contract is kept on the phone (text, status,
 * signatures as small images) and online in contracts/{id}, where the client opens and signs it
 * (showfee.app/sign/?c=id). Both the ShowFee user and the client sign.
 */
final class Contract {
    static final String SENT = "sent", SIGNED = "signed", VOID = "void";
    static final String SIGN_PAGE = "https://showfee.app/sign/?c=";
    static final String TITLE = "Booking agreement";
    static final String CONFIRM_ON_SIGN = "contract_confirm_on_sign";
    private static final String TEMPLATE = "contract_template";

    String id = "", status = SENT, title = TITLE, text = "", client = "", performer = "", clientName = "";
    int version = 1;
    long gigId, sentAt, signedAt;
    boolean handled; // the "signed" news was already dealt with in the app

    boolean isSent() { return SENT.equals(status); }
    boolean isSigned() { return SIGNED.equals(status); }
    boolean isVoid() { return VOID.equals(status); }

    String link() { return SIGN_PAGE + id; }

    File performerSig(Context c) { return new File(dir(c), id + "-p.png"); }
    File clientSig(Context c) { return new File(dir(c), id + "-c.png"); }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "contracts");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** The user's own signature, kept to sign the next contract with one tap. */
    static File mySignature(Context c) { return new File(dir(c), "my-signature.png"); }

    /** A long random id: only people given the link can open the contract. */
    static String newId(Context c) {
        String abc = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(Cloud.sp(c).getBoolean(Cloud.ANON, false) ? "test-" : "");
        for (int i = 0; i < 22; i++) sb.append(abc.charAt(r.nextInt(abc.length())));
        return sb.toString();
    }

    // ------------------------------------------------------------------ saved on the phone

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("contracts", Context.MODE_PRIVATE);
    }

    JSONObject toJson() throws Exception {
        return new JSONObject().put("id", id).put("status", status).put("title", title).put("text", text)
                .put("client", client).put("performer", performer).put("clientName", clientName)
                .put("version", version).put("gigId", gigId).put("sentAt", sentAt).put("signedAt", signedAt)
                .put("handled", handled);
    }

    static Contract fromJson(JSONObject o) {
        Contract k = new Contract();
        k.id = o.optString("id");
        k.status = o.optString("status", SENT);
        k.title = o.optString("title", TITLE);
        k.text = o.optString("text");
        k.client = o.optString("client");
        k.performer = o.optString("performer");
        k.clientName = o.optString("clientName");
        k.version = o.optInt("version", 1);
        k.gigId = o.optLong("gigId");
        k.sentAt = o.optLong("sentAt");
        k.signedAt = o.optLong("signedAt");
        k.handled = o.optBoolean("handled");
        return k;
    }

    /** All versions for one booking, newest first. */
    static List<Contract> forGig(Context c, long gigId) {
        List<Contract> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("g" + gigId, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        out.sort((x, y) -> y.version - x.version);
        return out;
    }

    static Contract latest(Context c, long gigId) {
        List<Contract> all = forGig(c, gigId);
        return all.isEmpty() ? null : all.get(0);
    }

    /** True if this phone has any contract still waiting to be signed. */
    static boolean anyWaiting(Context c) {
        for (String key : sp(c).getAll().keySet()) {
            if (!key.startsWith("g")) continue;
            try {
                for (Contract k : forGig(c, Long.parseLong(key.substring(1)))) if (k.isSent()) return true;
            } catch (NumberFormatException ignored) { }
        }
        return false;
    }

    static boolean any(Context c) {
        for (String key : sp(c).getAll().keySet()) if (key.startsWith("g")) return true;
        return false;
    }

    /** Finds a contract by its id (for notifications). */
    static Contract find(Context c, String id) {
        for (String key : sp(c).getAll().keySet()) {
            if (!key.startsWith("g")) continue;
            try {
                for (Contract k : forGig(c, Long.parseLong(key.substring(1)))) if (k.id.equals(id)) return k;
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    static synchronized void save(Context c, Contract k) {
        List<Contract> all = forGig(c, k.gigId);
        JSONArray a = new JSONArray();
        try {
            boolean replaced = false;
            for (Contract x : all) {
                if (x.id.equals(k.id)) { a.put(k.toJson()); replaced = true; }
                else a.put(x.toJson());
            }
            if (!replaced) a.put(k.toJson());
        } catch (Exception ignored) { }
        sp(c).edit().putString("g" + k.gigId, a.toString()).apply();
    }

    /** When a booking is deleted. */
    static void deleteForGig(Context c, long gigId) {
        for (Contract k : forGig(c, gigId)) {
            k.performerSig(c).delete();
            k.clientSig(c).delete();
        }
        sp(c).edit().remove("g" + gigId).apply();
    }

    static boolean confirmOnSign(Context c) {
        return sp(c).getBoolean(CONFIRM_ON_SIGN, true);
    }

    static void setConfirmOnSign(Context c, boolean on) {
        sp(c).edit().putBoolean(CONFIRM_ON_SIGN, on).apply();
    }

    // ------------------------------------------------------------------ the wording

    /** Words in {curly brackets} are filled in from the booking. */
    static final String DEFAULT_TEMPLATE =
            "This agreement is between {performer} (\"the performer\") and {client} (\"the client\").\n\n"
            + "The performer will provide their services for {event} on {date}{time}{venue}.\n\n"
            + "Fee: {fee}.{deposit} The balance is due on the day of the booking unless agreed otherwise in writing.\n\n"
            + "Cancellation: if the client cancels, any deposit paid is not refundable. If the performer cannot attend "
            + "because of illness or an emergency, all money paid for this booking will be refunded.\n\n"
            + "The client will provide safe access, a suitable space and power where needed.\n\n"
            + "By signing below, both parties agree to these terms.";

    static String template(Context c) {
        String t = Prefs.get(c, TEMPLATE);
        return t.trim().isEmpty() ? DEFAULT_TEMPLATE : t;
    }

    static void setTemplate(Context c, String t) {
        Prefs.set(c, TEMPLATE, t == null || t.trim().equals(DEFAULT_TEMPLATE.trim()) ? "" : t);
    }

    /** The template with this booking's details filled in. Venue and deposit are optional. */
    static String fill(Context c, String template, Gig g, String venue, long depositCents, long depositDay) {
        String name = Prefs.get(c, Prefs.NAME).trim();
        String event = g.event == null || g.event.trim().isEmpty() ? "this booking" : g.event.trim();
        String deposit = depositCents <= 0 ? ""
                : " A deposit of " + Money.fmt(depositCents)
                + (depositDay > 0 ? " is due by " + Dates.fmt(depositDay) : " is due")
                + " to confirm the date.";
        return template
                .replace("{performer}", name.isEmpty() ? "the performer" : name)
                .replace("{client}", g.client.trim().isEmpty() ? "the client" : g.client.trim())
                .replace("{event}", event)
                .replace("{date}", Dates.withWeekday(g.gigDay))
                .replace("{time}", g.startMin >= 0 ? ", starting at " + Dates.time(g.startMin) : "")
                .replace("{venue}", venue == null || venue.trim().isEmpty() ? "" : " at " + venue.trim())
                .replace("{fee}", Money.fmt(g.feeCents))
                .replace("{deposit}", deposit);
    }
}
