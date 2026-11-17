package com.onlypanels.singingbalances;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The booking link (showfee.app/book/&lt;name&gt;) and the booking requests clients send from it.
 * This is the only part of ShowFee that uses the internet for your data: the public booking page
 * (your name, the days you work, dates you're busy, your own questions) and the requests sent to you
 * are kept in Google Firebase (project "showfee-app"). Everything else stays on the phone.
 *
 * Only the person who set up a link can read the requests sent to it (see firebase/firestore.rules in the
 * website repository). The owner signs in once with Google; after that Firebase hands out fresh keys.
 * Uses Firebase's plain web API, so no extra libraries are added to the app.
 */
final class Cloud {
    static final String API_KEY = "AIzaSyD2zXtNDHXO6dLlnnzVe_RWNY-0YkhSZEY";
    /** "ShowFee booking link sign-in" (web client in the OutRo Google Cloud project, safelisted in Firebase). */
    static final String WEB_CLIENT_ID = "94709444990-3skvmsbtlnj7od147bpd6dqping7h737.apps.googleusercontent.com";
    static final String SITE = "showfee.app/book/";
    static final int MAX_QUESTIONS = 6;
    static final int QUESTION_MAX_LEN = 40;
    private static final String DOCS = "https://firestore.googleapis.com/v1/projects/showfee-app/databases/(default)/documents";
    private static final String DOC_PREFIX = "projects/showfee-app/databases/(default)/documents";

    // Saved on the phone (kept apart from the main settings, so a backup file never carries sign-in keys)
    static final String UID = "uid", REFRESH = "refresh", ID_TOKEN = "id_token", EXPIRES = "expires", EMAIL = "email",
            ANON = "anonymous", SLUG = "slug", NAME = "name", TAGLINE = "tagline", ACCEPTING = "accepting", DAYS = "days",
            QUESTIONS = "questions", BUSY_SENT = "busy_sent", REQUESTS = "requests", PUSH_TOKEN = "push_token",
            BRAND_LOGO = "brand_logo", BRAND_COLOR = "brand_color", BRAND_THEME = "brand_theme", BRAND_POWERED = "brand_powered";
    /** ShowFee Business brand colours offered for the booking page. */
    static final String[] BRAND_COLORS = {"#3949ab", "#0f6b5c", "#e0457b", "#d45500", "#7b3fe4", "#1e88e5", "#b8860b", "#1b1c20"};
    static final String[] BRAND_COLOR_NAMES = {"Indigo", "Green", "Pink", "Orange", "Purple", "Blue", "Gold", "Black"};

    /** Test builds only: pretend to be offline, to check the "no internet" messages. */
    static volatile boolean forceOffline;

    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Done<T> {
        void ok(T result);

        void fail(String reason);
    }

    /** One booking request sent from the booking page. */
    static final class Request {
        String id = "", slug = "", date = "", name = "", contact = "", message = "", status = "new";
        long created;
        List<String> ask = new ArrayList<>(), answers = new ArrayList<>();

        long day() {
            try {
                return java.time.LocalDate.parse(date).toEpochDay();
            } catch (Exception e) {
                return Dates.today();
            }
        }

        boolean isNew() {
            return "new".equals(status);
        }

        boolean phone() {
            return !contact.contains("@") && contact.replaceAll("[^0-9]", "").length() >= 6;
        }

        /** "Wedding · Villa Arrigo" – the client's answers, for one-line summaries. */
        String summary() {
            StringBuilder sb = new StringBuilder();
            for (String a : answers) {
                if (a.trim().isEmpty()) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(a.trim());
            }
            return sb.toString();
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject().put("id", id).put("slug", slug).put("date", date).put("name", name)
                    .put("contact", contact).put("message", message).put("status", status).put("created", created);
            o.put("ask", new JSONArray(ask)).put("answers", new JSONArray(answers));
            return o;
        }

        static Request fromJson(JSONObject o) {
            Request r = new Request();
            r.id = o.optString("id");
            r.slug = o.optString("slug");
            r.date = o.optString("date");
            r.name = o.optString("name");
            r.contact = o.optString("contact");
            r.message = o.optString("message");
            r.status = o.optString("status", "new");
            r.created = o.optLong("created");
            JSONArray a = o.optJSONArray("ask"), b = o.optJSONArray("answers");
            for (int i = 0; a != null && i < a.length(); i++) r.ask.add(a.optString(i));
            for (int i = 0; b != null && i < b.length(); i++) r.answers.add(b.optString(i));
            return r;
        }
    }

    private Cloud() {}

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("booking_link", Context.MODE_PRIVATE);
    }

    static String get(Context c, String key) {
        return sp(c).getString(key, "");
    }

    static boolean signedIn(Context c) {
        return !get(c, REFRESH).isEmpty();
    }

    static boolean hasLink(Context c) {
        return signedIn(c) && !get(c, SLUG).isEmpty();
    }

    static String link(Context c) {
        return SITE + get(c, SLUG);
    }

    static boolean accepting(Context c) {
        return sp(c).getBoolean(ACCEPTING, true);
    }

    /** Days the user works: 1 = Monday … 7 = Sunday. */
    static List<Integer> days(Context c) {
        List<Integer> out = new ArrayList<>();
        String s = sp(c).getString(DAYS, "1,2,3,4,5,6,7");
        for (String p : s.split(",")) {
            try {
                if (!p.trim().isEmpty()) out.add(Integer.parseInt(p.trim()));
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    static List<String> questions(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(QUESTIONS, "[\"Event\",\"Venue\"]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    /** "Singer" for the line under the name on the booking page (empty for "Something else"). */
    static String defaultTagline(Context c) {
        int i = Words.index(c);
        return Words.KEYS[i].equals("other") ? "" : Words.NAMES[i];
    }

    /** Suggested link name from the user's name: "Gary Falzon" → "garyfalzon". */
    static String suggestSlug(String name) {
        String s = name == null ? "" : java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        if (s.length() > 31) s = s.substring(0, 31);
        return s;
    }

    /** Empty if fine, otherwise what's wrong with the link name. */
    static String slugProblem(Context c, String slug) {
        if (slug.length() < 2) return "Use at least 2 letters or numbers.";
        if (slug.length() > 31) return "Use 31 characters or fewer.";
        if (!slug.matches("^[a-z0-9][a-z0-9-]*$")) return "Use only small letters, numbers and dashes (-), starting with a letter or number.";
        if (sp(c).getBoolean(ANON, false) && !slug.startsWith("test-")) return "Test sign-in: the link must start with test-";
        return "";
    }

    // ------------------------------------------------------------------ sign-in

    /** After Google sign-in on the phone: swaps Google's ID token for a Firebase account (the same one every time). */
    static void signInWithGoogle(Context c, String googleIdToken, String email, Done<Void> done) {
        run(done, () -> {
            JSONObject body = new JSONObject()
                    .put("postBody", "id_token=" + URLEncoder.encode(googleIdToken, "UTF-8") + "&providerId=google.com")
                    .put("requestUri", "http://localhost")
                    .put("returnSecureToken", true);
            JSONObject r = call("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" + API_KEY, body, null);
            saveSession(c, r.getString("localId"), r.getString("refreshToken"), r.getString("idToken"),
                    r.optLong("expiresIn", 3600), r.optString("email", email == null ? "" : email), false);
            return null;
        });
    }

    static void saveSession(Context c, String uid, String refresh, String idToken, long expiresInSec, String email, boolean anonymous) {
        sp(c).edit().putString(UID, uid).putString(REFRESH, refresh).putString(ID_TOKEN, idToken)
                .putLong(EXPIRES, System.currentTimeMillis() + (expiresInSec - 60) * 1000).putString(EMAIL, email)
                .putBoolean(ANON, anonymous).apply();
    }

    /** A valid Firebase key, refreshed when it's about to run out. Network thread only. */
    private static String token(Context c) throws Exception {
        SharedPreferences sp = sp(c);
        if (System.currentTimeMillis() < sp.getLong(EXPIRES, 0) && !get(c, ID_TOKEN).isEmpty()) return get(c, ID_TOKEN);
        String body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(get(c, REFRESH), "UTF-8");
        JSONObject r = http("POST", "https://securetoken.googleapis.com/v1/token?key=" + API_KEY, body,
                "application/x-www-form-urlencoded", null);
        sp.edit().putString(ID_TOKEN, r.getString("id_token")).putString(REFRESH, r.getString("refresh_token"))
                .putLong(EXPIRES, System.currentTimeMillis() + (r.optLong("expires_in", 3600) - 60) * 1000).apply();
        return r.getString("id_token");
    }

    // ------------------------------------------------------------------ the booking page

    /** Is this link name free? ok(true) = free or already yours. */
    static void checkSlug(Context c, String slug, Done<Boolean> done) {
        run(done, () -> {
            try {
                JSONObject doc = call("GET", DOCS + "/pages/" + slug, null, null);
                JSONObject f = doc.optJSONObject("fields");
                return f != null && get(c, UID).equals(str(f, "uid"));
            } catch (HttpError e) {
                if (e.code == 404) return true;
                throw e;
            }
        });
    }

    /** Makes the booking page under a new name (and removes the old one if the name changed). */
    static void createPage(Context c, String slug, String name, String tagline, Done<Void> done) {
        run(done, () -> {
            String old = get(c, SLUG);
            sp(c).edit().putString(NAME, name).putString(TAGLINE, tagline).apply();
            JSONObject write = pageWrite(c, slug);
            if (!slug.equals(old)) write.put("currentDocument", new JSONObject().put("exists", false));
            commit(c, write);
            if (!old.isEmpty() && !old.equals(slug)) {
                try {
                    call("DELETE", DOCS + "/pages/" + old, null, token(c));
                } catch (Exception ignored) { } // old name stays reserved until next time; harmless
            }
            sp(c).edit().putString(SLUG, slug).apply();
            return null;
        });
    }

    /** Saves the current settings to the booking page. */
    static void savePage(Context c, Done<Void> done) {
        if (!hasLink(c)) {
            if (done != null) done.ok(null);
            return;
        }
        run(done, () -> {
            commit(c, pageWrite(c, get(c, SLUG)));
            return null;
        });
    }

    /** Updates the booking page's busy dates if bookings changed since last time (called when the app opens). */
    static void syncBusy(Context c) {
        if (!hasLink(c) || sentKey(c, busyList(c)).equals(get(c, BUSY_SENT))) return;
        savePage(c, null);
    }

    /** Removes the booking page and signs out on this phone. Requests already sent are deleted too. */
    static void removeLink(Context c, Done<Void> done) {
        run(done, () -> {
            String t = token(c);
            for (Request r : fetchRequests(c, t)) {
                try {
                    call("DELETE", DOCS + "/requests/" + r.id, null, t);
                } catch (Exception ignored) { }
            }
            if (!get(c, SLUG).isEmpty()) call("DELETE", DOCS + "/pages/" + get(c, SLUG), null, t);
            try {
                call("DELETE", DOCS + "/devices/" + get(c, UID), null, t); // stop notifications to this account
            } catch (Exception ignored) { }
            sp(c).edit().clear().apply();
            return null;
        });
    }

    // ------------------------------------------------------------------ notifications

    /** Notifications are for people with a booking link, or who sent a contract to be signed. */
    static boolean wantsPush(Context c) {
        return hasLink(c) || (signedIn(c) && Contract.any(c));
    }

    /** Signs this phone up for booking-request and contract notifications. */
    static void registerDevice(Context c) {
        if (!wantsPush(c)) return;
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(t -> registerDevice(c, t));
        } catch (Exception ignored) { } // no Google Play services: requests still show when the app opens
    }

    static void registerDevice(Context c, String token) {
        if (!wantsPush(c) || token == null || token.isEmpty() || token.equals(get(c, PUSH_TOKEN))) return;
        run(null, () -> {
            JSONObject w = new JSONObject()
                    .put("update", new JSONObject().put("name", DOC_PREFIX + "/devices/" + get(c, UID)).put("fields", new JSONObject()))
                    .put("updateMask", new JSONObject().put("fieldPaths", new JSONArray()))
                    .put("updateTransforms", new JSONArray()
                            .put(new JSONObject().put("fieldPath", "tokens").put("appendMissingElements",
                                    new JSONObject().put("values", new JSONArray().put(sv(token)))))
                            .put(new JSONObject().put("fieldPath", "updated").put("setToServerValue", "REQUEST_TIME")));
            call("POST", DOCS + ":commit", new JSONObject().put("writes", new JSONArray().put(w)), token(c));
            sp(c).edit().putString(PUSH_TOKEN, token).apply();
            return null;
        });
    }

    /** Dates with a booking (not cancelled), today to 12 months ahead, as 2026-12-05. */
    static JSONArray busyList(Context c) {
        java.util.TreeSet<String> set = new java.util.TreeSet<>();
        long today = Dates.today(), until = java.time.LocalDate.ofEpochDay(today).plusMonths(12).toEpochDay();
        for (Gig g : Db.get(c).allGigs()) {
            if (g.isCancelled() || g.gigDay < today || g.gigDay > until) continue;
            set.add(java.time.LocalDate.ofEpochDay(g.gigDay).toString());
        }
        JSONArray a = new JSONArray();
        int n = 0;
        for (String s : set) if (n++ < 400) a.put(s);
        return a;
    }

    /** What was last sent: busy dates plus the branding state (so the page updates when either changes,
     *  including when a Business subscription ends and the branding has to come off). */
    private static String sentKey(Context c, JSONArray busy) {
        String b = !Pro.isBusiness(c) ? "none" : brandColor(c) + "/" + get(c, BRAND_THEME) + "/" + sp(c).getBoolean(BRAND_POWERED, true)
                + "/" + (sp(c).getBoolean(BRAND_LOGO, false) && Logo.exists(c) ? Logo.file(c).lastModified() : 0);
        return busy.toString() + "|" + b;
    }

    static String brandColor(Context c) {
        String s = get(c, BRAND_COLOR);
        return s.matches("^#[0-9a-fA-F]{6}$") ? s : BRAND_COLORS[0];
    }

    /** The invoice logo, made small enough for the booking page (data: URL), or null. */
    private static String logoData(Context c) {
        android.graphics.Bitmap b = Logo.bitmap(c);
        if (b == null) return null;
        float scale = Math.min(1f, 256f / Math.max(b.getWidth(), b.getHeight()));
        if (scale < 1f) b = android.graphics.Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * scale)),
                Math.max(1, Math.round(b.getHeight() * scale)), true);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        String type = "png";
        if (out.size() > 100_000) { // very detailed logo: a JPEG on white is much smaller
            android.graphics.Bitmap w = android.graphics.Bitmap.createBitmap(b.getWidth(), b.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(w);
            cv.drawColor(0xFFFFFFFF);
            cv.drawBitmap(b, 0, 0, null);
            out.reset();
            w.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out);
            type = "jpeg";
        }
        if (out.size() > 110_000) return null;
        return "data:image/" + type + ";base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
    }

    private static JSONObject pageWrite(Context c, String slug) throws Exception {
        JSONArray busy = busyList(c);
        JSONObject f = new JSONObject();
        f.put("uid", sv(get(c, UID)));
        String name = get(c, NAME).trim();
        f.put("name", sv(name.isEmpty() ? "ShowFee user" : name.length() > 60 ? name.substring(0, 60) : name));
        String tag = get(c, TAGLINE).trim();
        f.put("tagline", sv(tag.length() > 80 ? tag.substring(0, 80) : tag));
        f.put("accepting", new JSONObject().put("booleanValue", accepting(c)));
        JSONArray days = new JSONArray();
        for (int d : days(c)) days.put(new JSONObject().put("integerValue", String.valueOf(d)));
        f.put("days", arr(days));
        JSONArray b = new JSONArray();
        for (int i = 0; i < busy.length(); i++) b.put(sv(busy.getString(i)));
        f.put("busy", arr(b));
        JSONArray q = new JSONArray();
        for (String s : questions(c)) q.put(sv(s));
        f.put("fields", arr(q));
        if (Pro.isBusiness(c)) {
            JSONObject br = new JSONObject();
            br.put("color", sv(brandColor(c)));
            br.put("theme", sv("light".equals(get(c, BRAND_THEME)) ? "light" : "dark"));
            br.put("powered", new JSONObject().put("booleanValue", sp(c).getBoolean(BRAND_POWERED, true)));
            String logo = sp(c).getBoolean(BRAND_LOGO, false) ? logoData(c) : null;
            if (logo != null) br.put("logo", sv(logo));
            f.put("brand", new JSONObject().put("mapValue", new JSONObject().put("fields", br)));
        }
        sp(c).edit().putString(BUSY_SENT, sentKey(c, busy)).apply();
        return new JSONObject()
                .put("update", new JSONObject().put("name", DOC_PREFIX + "/pages/" + slug).put("fields", f))
                .put("updateTransforms", new JSONArray().put(new JSONObject().put("fieldPath", "updated").put("setToServerValue", "REQUEST_TIME")));
    }

    private static void commit(Context c, JSONObject write) throws Exception {
        try {
            call("POST", DOCS + ":commit", new JSONObject().put("writes", new JSONArray().put(write)), token(c));
        } catch (Exception e) {
            sp(c).edit().putString(BUSY_SENT, "").apply(); // try again next time
            throw e;
        }
    }

    // ------------------------------------------------------------------ booking requests

    /** Requests saved on the phone from the last check, newest date first among new ones. */
    static List<Request> cached(Context c) {
        List<Request> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(REQUESTS, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Request.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    static int newCount(Context c) {
        int n = 0;
        for (Request r : cached(c)) if (r.isNew()) n++;
        return n;
    }

    static Request find(Context c, String id) {
        for (Request r : cached(c)) if (r.id.equals(id)) return r;
        return null;
    }

    /** Checks for requests and saves them on the phone. ok(number of new ones). */
    static void refresh(Context c, Done<Integer> done) {
        if (!hasLink(c)) {
            if (done != null) done.ok(0);
            return;
        }
        run(done, () -> {
            List<Request> list = fetchRequests(c, token(c));
            JSONArray a = new JSONArray();
            for (Request r : list) a.put(r.toJson());
            sp(c).edit().putString(REQUESTS, a.toString()).apply();
            return newCount(c);
        });
    }

    /** Marks a request accepted or declined (only the owner can do this). */
    static void setStatus(Context c, String id, String status, Done<Void> done) {
        run(done, () -> {
            call("PATCH", DOCS + "/requests/" + id + "?updateMask.fieldPaths=status",
                    new JSONObject().put("fields", new JSONObject().put("status", sv(status))), token(c));
            List<Request> list = cached(c);
            JSONArray a = new JSONArray();
            for (Request r : list) {
                if (r.id.equals(id)) r.status = status;
                a.put(r.toJson());
            }
            sp(c).edit().putString(REQUESTS, a.toString()).apply();
            return null;
        });
    }

    private static List<Request> fetchRequests(Context c, String token) throws Exception {
        JSONObject q = new JSONObject().put("structuredQuery", new JSONObject()
                .put("from", new JSONArray().put(new JSONObject().put("collectionId", "requests")))
                .put("where", new JSONObject().put("fieldFilter", new JSONObject()
                        .put("field", new JSONObject().put("fieldPath", "owner"))
                        .put("op", "EQUAL")
                        .put("value", sv(get(c, UID))))));
        String raw = request("POST", DOCS + ":runQuery", q.toString(), "application/json", token);
        JSONArray rows = new JSONArray(raw);
        List<Request> out = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject doc = rows.getJSONObject(i).optJSONObject("document");
            if (doc == null) continue;
            JSONObject f = doc.getJSONObject("fields");
            Request r = new Request();
            String n = doc.getString("name");
            r.id = n.substring(n.lastIndexOf('/') + 1);
            r.slug = str(f, "slug");
            r.date = str(f, "date");
            r.name = str(f, "name");
            r.contact = str(f, "contact");
            r.message = str(f, "message");
            r.status = str(f, "status");
            r.ask = strs(f, "ask");
            r.answers = strs(f, "answers");
            JSONObject cr = f.optJSONObject("created");
            if (cr != null) {
                try {
                    r.created = java.time.Instant.parse(cr.optString("timestampValue")).toEpochMilli();
                } catch (Exception ignored) { }
            }
            out.add(r);
        }
        // New ones first (soonest date first), then answered ones (latest date first)
        out.sort((x, y) -> x.isNew() != y.isNew() ? (x.isNew() ? -1 : 1)
                : x.isNew() ? x.date.compareTo(y.date) : y.date.compareTo(x.date));
        return out;
    }

    // ------------------------------------------------------------------ contracts (ShowFee Business)

    /** Puts a contract online for the client to sign. The user's signature is a PNG. */
    static void createContract(Context c, Contract k, byte[] performerPng, Done<Void> done) {
        run(done, () -> {
            JSONObject f = new JSONObject()
                    .put("owner", sv(get(c, UID)))
                    .put("status", sv(Contract.SENT))
                    .put("title", sv(k.title))
                    .put("text", sv(k.text))
                    .put("performer", sv(k.performer))
                    .put("performerSig", sv("data:image/png;base64," + android.util.Base64.encodeToString(performerPng, android.util.Base64.NO_WRAP)))
                    .put("client", sv(k.client))
                    .put("version", new JSONObject().put("integerValue", String.valueOf(k.version)));
            JSONObject w = new JSONObject()
                    .put("update", new JSONObject().put("name", DOC_PREFIX + "/contracts/" + k.id).put("fields", f))
                    .put("currentDocument", new JSONObject().put("exists", false))
                    .put("updateTransforms", new JSONArray()
                            .put(new JSONObject().put("fieldPath", "performerAt").put("setToServerValue", "REQUEST_TIME"))
                            .put(new JSONObject().put("fieldPath", "created").put("setToServerValue", "REQUEST_TIME")));
            call("POST", DOCS + ":commit", new JSONObject().put("writes", new JSONArray().put(w)), token(c));
            return null;
        });
        registerDevice(c);
    }

    /** What the client did: ok(fields) with status, clientName, clientSig, clientAt – or ok(null) if it's gone. */
    static void contractState(Context c, String id, Done<JSONObject> done) {
        run(done, () -> {
            try {
                String raw = request("GET", DOCS + "/contracts/" + id + "?key=" + API_KEY, null, null, null);
                JSONObject f = new JSONObject(raw).getJSONObject("fields");
                JSONObject out = new JSONObject().put("status", str(f, "status")).put("clientName", str(f, "clientName"))
                        .put("clientSig", str(f, "clientSig"));
                JSONObject at = f.optJSONObject("clientAt");
                if (at != null) {
                    try {
                        out.put("clientAt", java.time.Instant.parse(at.optString("timestampValue")).toEpochMilli());
                    } catch (Exception ignored) { }
                }
                return out;
            } catch (HttpError e) {
                if (e.code == 404) return null;
                throw e;
            }
        });
    }

    /** Cancels a contract that hasn't been signed: the link stops working. */
    static void voidContract(Context c, String id, Done<Void> done) {
        run(done, () -> {
            call("PATCH", DOCS + "/contracts/" + id + "?updateMask.fieldPaths=status",
                    new JSONObject().put("fields", new JSONObject().put("status", sv(Contract.VOID))), token(c));
            return null;
        });
    }

    // ------------------------------------------------------------------ plumbing

    interface Work<T> {
        T run() throws Exception;
    }

    private static <T> void run(Done<T> done, Work<T> work) {
        NET.execute(() -> {
            try {
                if (forceOffline) throw new java.net.UnknownHostException("offline (test)");
                T result = work.run();
                if (done != null) MAIN.post(() -> done.ok(result));
            } catch (Exception e) {
                String why = friendly(e);
                if (done != null) MAIN.post(() -> done.fail(why));
            }
        });
    }

    static final class HttpError extends Exception {
        final int code;

        HttpError(int code, String body) {
            super("HTTP " + code + ": " + body);
            this.code = code;
        }
    }

    static String friendly(Exception e) {
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException
                || e instanceof java.net.ConnectException) return "No internet connection. Please try again.";
        if (e instanceof HttpError) {
            int code = ((HttpError) e).code;
            if (code == 400 && e.getMessage().contains("TOKEN")) return "Please sign in again.";
            if (code == 403 || code == 401) return "ShowFee wasn't allowed to do that. Please sign in again.";
            if (code == 409) return "That link name was just taken. Please choose another.";
        }
        return "Something went wrong. Please try again.";
    }

    private static JSONObject call(String method, String url, JSONObject body, String bearer) throws Exception {
        String raw = request(method, url, body == null ? null : body.toString(), "application/json", bearer);
        return raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
    }

    private static JSONObject http(String method, String url, String body, String type, String bearer) throws Exception {
        return new JSONObject(request(method, url, body, type, bearer));
    }

    static String request(String method, String url, String body, String type, String bearer) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        if ("PATCH".equals(method)) {
            con.setRequestMethod("POST");
            con.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        } else {
            con.setRequestMethod(method);
        }
        if (bearer != null) con.setRequestProperty("Authorization", "Bearer " + bearer);
        if (body != null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", type + "; charset=utf-8");
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = con.getResponseCode();
        InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            text = bo.toString("UTF-8");
        }
        con.disconnect();
        if (code >= 400) throw new HttpError(code, text);
        return text;
    }

    private static JSONObject sv(String s) throws Exception {
        return new JSONObject().put("stringValue", s == null ? "" : s);
    }

    private static JSONObject arr(JSONArray values) throws Exception {
        return new JSONObject().put("arrayValue", values.length() == 0 ? new JSONObject() : new JSONObject().put("values", values));
    }

    private static String str(JSONObject fields, String key) {
        JSONObject v = fields.optJSONObject(key);
        return v == null ? "" : v.optString("stringValue", "");
    }

    private static List<String> strs(JSONObject fields, String key) {
        List<String> out = new ArrayList<>();
        JSONObject v = fields.optJSONObject(key);
        JSONArray a = v == null || v.optJSONObject("arrayValue") == null ? null : v.optJSONObject("arrayValue").optJSONArray("values");
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optJSONObject(i).optString("stringValue", ""));
        return out;
    }
}
