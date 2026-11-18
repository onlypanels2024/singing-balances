package com.onlypanels.singingbalances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** TEST BUILDS ONLY. Driven by the automated emulator test via adb broadcasts. */
public class DemoSeeder extends BroadcastReceiver {
    private static final String TAG = "UITEST";

    @Override
    public void onReceive(Context c, Intent intent) {
        String task = intent.getStringExtra("task");
        try {
            if ("seed".equals(task)) seed(c);
            else if ("invoice".equals(task)) invoice(c, intent.getLongExtra("id", 3));
            else if ("mail".equals(task)) mail(c, intent.getLongExtra("id", 4));
            else if ("notify".equals(task)) Log.i(TAG, "notifications shown: " + Nudges.check(c, true));
            else if ("backup".equals(task)) backupRoundTrip(c);
            else if ("look".equals(task)) look(c, intent);
            else if ("migrate".equals(task)) {
                // Pretend this is the old app: remove the new personalisation settings, keep the data
                Prefs.sp(c).edit().remove(Prefs.SETUP_DONE).remove(Prefs.PROFESSION).remove(Prefs.TAB_TITLE)
                        .remove(Prefs.CURRENCY).remove(Prefs.ACCENT).remove(Prefs.THEME_MODE).remove(Prefs.WELCOME_STARTED).apply();
                Theme.changed(c);
                Log.i(TAG, "migrate: settings cleared, needsSetup=" + Prefs.needsSetup(c)
                        + " profession=" + Prefs.get(c, Prefs.PROFESSION) + " tab=" + Words.tab(c)
                        + " accent=" + Prefs.get(c, Prefs.ACCENT) + " currency=" + Prefs.get(c, Prefs.CURRENCY));
            }
            else if ("logo".equals(task)) logo(c);
            else if ("dump".equals(task)) dump(c);
            else if ("plan".equals(task)) {
                // --es plan free|pro|- : pretend to be on the free plan or subscribed ("-" = normal)
                String p = intent.getStringExtra("plan");
                Pro.forced = p == null || p.equals("-") ? null : p;
                Pro.fakePrice = Pro.forced == null ? null : "1 month free, then €9.99/month";
                Pro.fakeBusinessPrice = Pro.forced == null ? null : "1 month free, then €19.99/month";
                Pro.fakeYearly = Pro.forced == null ? null : "€107.99/year";
                Pro.fakeBusinessYearly = Pro.forced == null ? null : "€215.99/year";
                Pro.fakeSaving = 10;
                if (Pro.forced == null) Prefs.sp(c).edit().remove(Prefs.PRO_CODE_UNTIL).remove(Prefs.PRO_CODE_USED).remove(Prefs.PRO_TESTER_USED).apply();
                if ("expire".equals(intent.getStringExtra("code"))) Prefs.sp(c).edit().remove(Prefs.PRO_CODE_UNTIL).apply();
                Theme.changed(c);
                Log.i(TAG, "plan " + Pro.forced + " bookingsThisMonth=" + Pro.bookingsInMonth(c, Dates.today(), 0));
            }
            else if ("update".equals(task)) fakeUpdate(c, intent.getStringExtra("step"));
            else if (task != null && task.startsWith("bk")) {
                // Booking link tests against the real Firebase project, using a throwaway anonymous account
                PendingResult pr = goAsync();
                new Thread(() -> {
                    try {
                        bookingTask(c, task, intent);
                    } catch (Exception e) {
                        Log.e(TAG, "booking task " + task + " failed", e);
                    } finally {
                        pr.finish();
                    }
                }).start();
            }
            else if ("widget".equals(task)) {
                Widget.updateAll(c);
                Log.i(TAG, "widget updated");
            }
            else if ("clientstats".equals(task)) {
                for (Client k : Db.get(c).clientsWithStats()) {
                    Log.i(TAG, "client " + k.name + " gigs=" + k.gigCount + " earned=" + k.earnedCents + " owed=" + k.owedCents
                            + " overdue=" + k.overdueCents + " habit=" + k.payingHabit(c));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "task " + task + " FAILED", e);
        }
    }

    private static long gig(Db db, String client, String event, long day, int start, double fee, int status, long due) {
        Gig g = new Gig();
        g.client = client;
        g.event = event;
        g.gigDay = day;
        g.startMin = start;
        g.feeCents = Math.round(fee * 100);
        g.status = status;
        g.dueDay = due;
        return db.save(g);
    }

    private static void client(Db db, String name, String email, String phone) {
        Client k = db.client(name);
        if (k == null) k = new Client();
        k.name = name;
        k.email = email;
        k.phone = phone;
        db.saveClient(k, null);
    }

    private static void expense(Db db, long day, double amt, String cat, String note, long gigId) {
        Expense e = new Expense();
        e.day = day;
        e.cents = Math.round(amt * 100);
        e.category = cat;
        e.note = note;
        e.gigId = gigId;
        db.saveExpense(e);
    }

    /** Switch the look: --es profession photographer --es accent teal --es mode dark --es currency GBP --es tab "" */
    private static void look(Context c, Intent i) {
        String[][] map = {{"profession", Prefs.PROFESSION}, {"accent", Prefs.ACCENT}, {"mode", Prefs.THEME_MODE},
                {"currency", Prefs.CURRENCY}, {"tab", Prefs.TAB_TITLE}, {"email", Prefs.EMAIL_APP}, {"google", Prefs.GOOGLE_EMAIL}, {"microsoft", Prefs.MS_EMAIL}};
        for (String[] m : map) {
            String v = i.getStringExtra(m[0]);
            if (v != null) Prefs.set(c, m[1], v.equals("-") ? "" : v);
        }
        Theme.changed(c);
        Log.i(TAG, "look: " + Words.tab(c) + " / " + Words.many(c) + " / " + Prefs.get(c, Prefs.ACCENT) + " / "
                + Prefs.get(c, Prefs.THEME_MODE) + " / " + Money.fmt(123456));
    }

    /** Draws a simple sample logo and saves it as the invoice logo. */
    private static void logo(Context c) throws Exception {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(480, 160, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(Theme.strong(c));
        cv.drawCircle(80, 80, 70, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(64);
        p.setFakeBoldText(true);
        cv.drawText("MB", 34, 102, p);
        p.setColor(0xFF1B1C20);
        p.setTextSize(52);
        cv.drawText("Maria Borg", 170, 78, p);
        p.setTextSize(30);
        p.setFakeBoldText(false);
        p.setColor(0xFF6B6E76);
        cv.drawText("LIVE VOCALS", 172, 120, p);
        File f = new File(c.getCacheDir(), "demo-logo.png");
        try (OutputStream out = new FileOutputStream(f)) {
            b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
        Logo.save(c, android.net.Uri.fromFile(f));
        Log.i(TAG, "logo saved " + Logo.exists(c) + " size=" + Logo.file(c).length());
    }

    private static com.google.android.play.core.appupdate.testing.FakeAppUpdateManager fake;

    /** Pretend Google Play has a newer version: --es step available | download | off */
    private static void fakeUpdate(Context c, String step) throws Exception {
        if ("off".equals(step)) {
            Updates.override = null;
            fake = null;
        } else if ("download".equals(step) && fake != null) {
            fake.userAcceptsUpdate();
            fake.downloadStarts();
            fake.downloadCompletes();
        } else {
            fake = new com.google.android.play.core.appupdate.testing.FakeAppUpdateManager(c.getApplicationContext());
            int v = (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
            fake.setUpdateAvailable(v + 1);
            Updates.override = fake;
            Updates.dismissed = false;
        }
        Log.i(TAG, "update " + step);
    }

    /** One line with everything the full test checks against. */
    private static void dump(Context c) {
        Db db = Db.get(c);
        int gigs = 0, cancelled = 0, pencilled = 0;
        long paid = 0, owed = 0;
        StringBuilder names = new StringBuilder();
        for (Gig g : db.allGigs()) {
            gigs++;
            if (g.isCancelled()) cancelled++;
            if (g.status == Gig.PENCILLED) pencilled++;
            paid += g.paidCents;
            if (!g.isCancelled() && !g.isFuture()) owed += g.balance();
            names.append(g.client).append('|');
        }
        java.util.List<Expense> ex = db.expensesBetween(0, 200000);
        long exTotal = 0;
        for (Expense e : ex) exTotal += e.cents;
        int clients = db.clientsWithStats().size();
        Log.i(TAG, "dump gigs=" + gigs + " cancelled=" + cancelled + " pencilled=" + pencilled + " paid=" + paid
                + " owed=" + owed + " expenses=" + ex.size() + " expTotal=" + exTotal + " clients=" + clients
                + " currency=" + Prefs.get(c, Prefs.CURRENCY) + " accent=" + Prefs.get(c, Prefs.ACCENT)
                + " mode=" + Prefs.get(c, Prefs.THEME_MODE) + " profession=" + Prefs.get(c, Prefs.PROFESSION)
                + " terms=" + Prefs.termsDays(c) + " notify=" + Prefs.notify(c) + " logo=" + Logo.exists(c)
                + " backup=" + Backup.isSetUp(c) + " pay=" + PayLink.service(c) + ":" + PayLink.url(c, null)
                + " vat_charge=" + Prefs.get(c, Prefs.VAT_CHARGE) + " " + gig4(db) + " names=" + names);
    }

    /** Extras and VAT of booking 4 (the one the extras test changes). */
    private static String gig4(Db db) {
        Gig g = db.gig(4);
        return g == null ? "gig4=none" : "extras=" + g.extrasCents + " vat=" + g.vatBp + " total=" + g.total();
    }

    private static void seed(Context c) {
        Prefs.sp(c).edit().putBoolean(Prefs.SETUP_DONE, true).putString(Prefs.PROFESSION, "singer")
                .putString(Prefs.ACCENT, "indigo").putString(Prefs.THEME_MODE, "light").putString(Prefs.CURRENCY, "EUR").apply();
        Theme.changed(c);
        Prefs.set(c, Prefs.NAME, "Maria Borg");
        Prefs.set(c, Prefs.EMAIL, "maria.sings@example.com");
        Prefs.set(c, Prefs.PHONE, "+356 7900 1234");
        Prefs.set(c, Prefs.ADDRESS, "12 Triq il-Kbira\nSliema SLM 1234\nMalta");
        Prefs.set(c, Prefs.IBAN, "MT84 MALT 0110 0001 2345 MTLC AST0 01S");
        Prefs.set(c, Prefs.BIC, "MALTMTMT");
        Prefs.set(c, Prefs.REVOLUT, "@mariaborg");

        Db db = Db.get(c);
        long t = Dates.today();
        int C = Gig.CONFIRMED;
        long g1 = gig(db, "Hilton Malta", "Gala dinner", t - 40, 20 * 60, 450, C, t - 40);
        db.addPayment(g1, 45000, t - 40, "Cash");
        long g2 = gig(db, "Café del Mar", "Sunset session", t - 20, 18 * 60 + 30, 250, C, t - 20);
        db.addPayment(g2, 25000, t - 15, "Revolut");
        long g3 = gig(db, "Radisson Blu", "Company party", t - 12, -1, 600, C, t - 12);
        db.addPayment(g3, 20000, t - 12, "Cash");
        long g4 = gig(db, "Joanna & Mark", "Wedding reception, Villa Arrigo", t - 1, 19 * 60, 900, C, t - 1);
        gig(db, "Bob's Bar", "Friday live", t, 21 * 60, 150, C, t);
        gig(db, "Hilton Malta", "New Year party rehearsal", t + 1, 22 * 60, 800, C, t + 1);
        gig(db, "Café del Mar", "Acoustic night", t + 9, 20 * 60, 200, Gig.PENCILLED, t + 9);
        gig(db, "Radisson Blu", "Corporate event", t + 25, 19 * 60 + 30, 500, C, t + 25);
        gig(db, "Bob's Bar", "Karaoke special", t - 5, 21 * 60, 120, Gig.CANCELLED, t - 5);
        long g10 = gig(db, "Hilton Malta", "Summer terrace", t - 70, 19 * 60, 300, C, t - 70);
        db.addPayment(g10, 30000, t - 60, "Bank transfer");

        client(db, "Hilton Malta", "events@hilton.example", "+356 2138 3383");
        client(db, "Joanna & Mark", "joanna.mark@example.com", "+356 9912 3456");
        client(db, "Wedding Planners Ltd", "hello@planners.example", "");

        expense(db, t - 1, 25, "Fuel & transport", "Fuel to Mdina", g4);
        expense(db, t - 1, 60, "Hair & make-up", "", g4);
        expense(db, t - 30, 120, "Outfits & costumes", "Red dress", 0);
        expense(db, t - 10, 45, "Backing tracks & music", "Wedding set tracks", 0);
        expense(db, t - 50, 300, "Equipment", "New microphone", 0);
        Log.i(TAG, "seeded, today=" + Dates.iso(t));
    }

    private static void copy(File from, File to) throws Exception {
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
    }

    /** The exact email ShowFee would send for this booking's invoice (with the Pay here button), saved as a file. */
    private static void mail(Context c, long id) throws Exception {
        Gig g = Db.get(c).gig(id);
        File pdf = Invoice.create(c, g);
        String body = "Hi " + g.client + ",\n\nTest of the pay link.\n\n" + PayLink.label(c, g) + ":\n" + Mail.PAY_HERE
                + "\n\nMany thanks";
        String url = PayLink.url(c, g);
        String raw = GmailSender.mime(g.email, "Invoice " + g.invoiceNo, Mail.plain(body, url),
                Mail.html(body, url, Theme.strong(c)), pdf.getName(), GmailSender.read(pdf));
        File out = new File(c.getExternalFilesDir(null), "email-" + id + ".eml");
        try (OutputStream o = new FileOutputStream(out)) {
            o.write(raw.getBytes(StandardCharsets.UTF_8));
        }
        Log.i(TAG, "mail written " + out.length());
    }

    private static void invoice(Context c, long id) throws Exception {
        Gig g = Db.get(c).gig(id);
        File pdf = Invoice.create(c, g);
        File out = new File(c.getExternalFilesDir(null), "invoice-" + id + ".pdf");
        copy(pdf, out);
        Log.i(TAG, "invoice written " + out + " no=" + g.invoiceNo + " size=" + out.length());
    }

    private static void backupRoundTrip(Context c) throws Exception {
        Db db = Db.get(c);
        String before = strip(db.exportAll());
        File f = new File(c.getExternalFilesDir(null), "backup.json");
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(db.exportAll().toString(1).getBytes(StandardCharsets.UTF_8));
        }
        byte[] data = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int off = 0;
            while (off < data.length) off += in.read(data, off, data.length - off);
        }
        db.importAll(new JSONObject(new String(data, StandardCharsets.UTF_8)));
        String after = strip(db.exportAll());
        Log.i(TAG, "backup roundtrip " + (before.equals(after) ? "IDENTICAL" : "DIFFERENT\n" + before + "\n" + after)
                + " bytes=" + data.length);
    }

    private static final String KEY = Cloud.API_KEY;
    private static final String DOCS = "https://firestore.googleapis.com/v1/projects/showfee-app/databases/(default)/documents";

    private static void bookingTask(Context c, String task, Intent in) throws Exception {
        switch (task) {
            case "bkanon": {
                // Signs in with a new anonymous account, like a ShowFee user who has just signed in with Google
                Cloud.sp(c).edit().clear().apply();
                JSONObject r = new JSONObject(Cloud.request("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + KEY,
                        "{\"returnSecureToken\":true}", "application/json", null));
                Cloud.saveSession(c, r.getString("localId"), r.getString("refreshToken"), r.getString("idToken"),
                        r.optLong("expiresIn", 3600), "test@showfee.app", true);
                Cloud.sp(c).edit().putString(Cloud.NAME, "Maria Borg").apply();
                Log.i(TAG, "bkanon signed in uid=" + r.getString("localId"));
                break;
            }
            case "bksend": {
                // A client sends a request from the booking page (no sign-in, exactly like the website does)
                String slug = Cloud.get(c, Cloud.SLUG);
                java.util.List<String> qs = Cloud.questions(c);
                org.json.JSONArray ask = new org.json.JSONArray(), ans = new org.json.JSONArray();
                String[] given = (in.getStringExtra("answers") == null ? "" : in.getStringExtra("answers")).split("\\|", -1);
                for (int i = 0; i < qs.size(); i++) {
                    ask.put(new JSONObject().put("stringValue", qs.get(i)));
                    ans.put(new JSONObject().put("stringValue", i < given.length ? given[i] : ""));
                }
                JSONObject f = new JSONObject()
                        .put("slug", sv(slug)).put("owner", sv(Cloud.get(c, Cloud.UID)))
                        .put("date", sv(java.time.LocalDate.now().plusDays(in.getIntExtra("days", 20)).toString()))
                        .put("name", sv(in.getStringExtra("name"))).put("contact", sv(in.getStringExtra("contact")))
                        .put("message", sv(in.getStringExtra("message") == null ? "" : in.getStringExtra("message")))
                        .put("status", sv("new"))
                        .put("ask", new JSONObject().put("arrayValue", ask.length() == 0 ? new JSONObject() : new JSONObject().put("values", ask)))
                        .put("answers", new JSONObject().put("arrayValue", ans.length() == 0 ? new JSONObject() : new JSONObject().put("values", ans)));
                String id = "t" + Long.toHexString(System.nanoTime());
                JSONObject w = new JSONObject().put("update", new JSONObject()
                                .put("name", "projects/showfee-app/databases/(default)/documents/requests/" + id).put("fields", f))
                        .put("updateTransforms", new org.json.JSONArray().put(new JSONObject().put("fieldPath", "created").put("setToServerValue", "REQUEST_TIME")))
                        .put("currentDocument", new JSONObject().put("exists", false));
                try {
                    Cloud.request("POST", DOCS + ":commit?key=" + KEY, new JSONObject().put("writes", new org.json.JSONArray().put(w)).toString(),
                            "application/json", null);
                    Log.i(TAG, "bksend sent " + id);
                } catch (Cloud.HttpError e) {
                    Log.i(TAG, "bksend refused " + e.code);
                }
                break;
            }
            case "bkpage": {
                // What the public booking page sees (read without signing in)
                try {
                    String raw = Cloud.request("GET", DOCS + "/pages/" + (in.getStringExtra("slug") != null ? in.getStringExtra("slug") : Cloud.get(c, Cloud.SLUG)) + "?key=" + KEY, null, null, null);
                    JSONObject fields = new JSONObject(raw).getJSONObject("fields");
                    // the logo is a long data: address; keep only its start so the log line isn't cut off
                    String line = fields.toString().replace("\n", " ").replaceAll("(data:image\\\\?/[a-z]+;base64,)[A-Za-z0-9+/=\\\\]+", "$1...");
                    Log.i(TAG, "bkpage " + line);
                } catch (Cloud.HttpError e) {
                    Log.i(TAG, "bkpage missing " + e.code);
                }
                break;
            }
            case "bkkstate": {
                // The contracts saved on the phone for one booking (newest first)
                long gid = in.getLongExtra("id", 0);
                StringBuilder sb = new StringBuilder();
                for (Contract k : Contract.forGig(c, gid)) {
                    sb.append(" v").append(k.version).append("=").append(k.status)
                            .append(k.isSigned() ? "(" + k.clientName + ",sig=" + k.clientSig(c).exists() + ")" : "")
                            .append(",psig=").append(k.performerSig(c).exists()).append(",id=").append(k.id);
                }
                Gig g = Db.get(c).gig(gid);
                Log.i(TAG, "bkkstate gig=" + gid + " n=" + Contract.forGig(c, gid).size() + sb
                        + " booking=" + (g == null ? "-" : Gig.STATUS_NAMES[g.status]) + " mysig=" + Contract.mySignature(c).exists());
                break;
            }
            case "bkksign": {
                // The client signs the newest contract of a booking – without signing in, exactly like showfee.app/sign does
                Contract k = Contract.latest(c, in.getLongExtra("id", 0));
                if (k == null) { Log.i(TAG, "bkksign none"); break; }
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(480, 150, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(b);
                cv.drawColor(0xFFFFFFFF);
                android.graphics.Paint p = new android.graphics.Paint();
                p.setColor(0xFF111111);
                p.setStrokeWidth(5);
                cv.drawLine(40, 110, 200, 40, p);
                cv.drawLine(200, 40, 300, 110, p);
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bo);
                String sig = "data:image/png;base64," + android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
                String name = in.getStringExtra("name") == null ? "Paul Grech" : in.getStringExtra("name");
                JSONObject f = new JSONObject()
                        .put("status", sv("signed")).put("clientName", sv(name)).put("clientSig", sv(sig));
                JSONObject w = new JSONObject()
                        .put("update", new JSONObject().put("name", "projects/showfee-app/databases/(default)/documents/contracts/" + k.id).put("fields", f))
                        .put("updateMask", new JSONObject().put("fieldPaths", new JSONArray().put("status").put("clientName").put("clientSig")))
                        .put("updateTransforms", new JSONArray().put(new JSONObject().put("fieldPath", "clientAt").put("setToServerValue", "REQUEST_TIME")));
                try {
                    Cloud.request("POST", DOCS + ":commit?key=" + KEY, new JSONObject().put("writes", new JSONArray().put(w)).toString(),
                            "application/json", null);
                    Log.i(TAG, "bkksign ok " + k.id);
                } catch (Cloud.HttpError e) {
                    Log.i(TAG, "bkksign refused " + e.code);
                }
                break;
            }
            case "bkkhack": {
                // Someone with the link tries to change the wording or the fee of a contract – must be refused
                Contract k = Contract.latest(c, in.getLongExtra("id", 0));
                if (k == null) { Log.i(TAG, "bkkhack none"); break; }
                try {
                    Cloud.request("PATCH", DOCS + "/contracts/" + k.id + "?key=" + KEY + "&updateMask.fieldPaths=text",
                            new JSONObject().put("fields", new JSONObject().put("text", sv("Fee: €1.00"))).toString(), "application/json", null);
                    Log.i(TAG, "bkkhack ALLOWED");
                } catch (Cloud.HttpError e) {
                    Log.i(TAG, "bkkhack refused " + e.code);
                }
                break;
            }
            case "bkkdoc": {
                // What the signing page sees for the newest contract of a booking (without the signature images)
                Contract k = Contract.latest(c, in.getLongExtra("id", 0));
                if (k == null) { Log.i(TAG, "bkkdoc none"); break; }
                try {
                    JSONObject f = new JSONObject(Cloud.request("GET", DOCS + "/contracts/" + k.id + "?key=" + KEY, null, null, null))
                            .getJSONObject("fields");
                    f.remove("performerSig");
                    f.remove("clientSig");
                    Log.i(TAG, "bkkdoc " + f.toString().replace("\n", " "));
                } catch (Cloud.HttpError e) {
                    Log.i(TAG, "bkkdoc missing " + e.code);
                }
                break;
            }
            case "bkdash": {
                // What the web dashboard reads for this user (dash/{uid}, signed in as them)
                try {
                    JSONObject f = new JSONObject(Cloud.request("GET", DOCS + "/dash/" + Cloud.get(c, Cloud.UID), null, null,
                            Cloud.token(c))).getJSONObject("fields");
                    String data = f.getJSONObject("data").getString("stringValue");
                    JSONObject d = new JSONObject(data);
                    Log.i(TAG, "bkdash name=" + d.optString("name") + " currency=" + d.optString("currency")
                            + " gigs=" + d.getJSONArray("gigs").length() + " clients=" + d.getJSONArray("clients").length()
                            + " payments=" + d.getJSONArray("payments").length() + " expenses=" + d.getJSONArray("expenses").length()
                            + " iban=" + (data.contains("MT84") ? "yes" : "no") + " settings=" + (d.has("settings") ? "yes" : "no")
                            + " bytes=" + data.length());
                } catch (Cloud.HttpError e) {
                    Log.i(TAG, "bkdash missing " + e.code);
                }
                break;
            }
            case "bkdashother": {
                // Another account must not be able to read someone's dashboard
                String uid = Cloud.get(c, Cloud.UID);
                JSONObject r = new JSONObject(Cloud.request("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + KEY,
                        "{\"returnSecureToken\":true}", "application/json", null));
                try {
                    Cloud.request("GET", DOCS + "/dash/" + uid, null, null, r.getString("idToken"));
                    Log.i(TAG, "bkdashother ALLOWED");
                } catch (Cloud.HttpError e) {
                    Log.i(TAG, "bkdashother refused " + e.code);
                }
                break;
            }
            case "bkop": {
                // A change made on the web dashboard, written exactly like the website does (t + d + server time)
                String uid = Cloud.get(c, Cloud.UID);
                // the details come base64-encoded (--es d64 …) so the JSON survives adb's shell quoting
                JSONObject d = new JSONObject(new String(android.util.Base64.decode(in.getStringExtra("d64"), android.util.Base64.DEFAULT),
                        StandardCharsets.UTF_8));
                String token = "other".equals(in.getStringExtra("as"))
                        ? new JSONObject(Cloud.request("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + KEY,
                        "{\"returnSecureToken\":true}", "application/json", null)).getString("idToken")
                        : Cloud.token(c);
                JSONObject w = new JSONObject().put("update", new JSONObject()
                                .put("name", "projects/showfee-app/databases/(default)/documents/dash/" + uid + "/ops/t" + Long.toHexString(System.nanoTime()))
                                .put("fields", new JSONObject().put("t", sv(in.getStringExtra("t"))).put("d", fv(d))))
                        .put("updateTransforms", new org.json.JSONArray().put(new JSONObject().put("fieldPath", "at").put("setToServerValue", "REQUEST_TIME")))
                        .put("currentDocument", new JSONObject().put("exists", false));
                try {
                    Cloud.request("POST", DOCS + ":commit", new JSONObject().put("writes", new org.json.JSONArray().put(w)).toString(),
                            "application/json", token);
                    Log.i(TAG, "bkop saved " + in.getStringExtra("t"));
                } catch (Cloud.HttpError e) {
                    Log.i(TAG, "bkop refused " + e.code);
                }
                break;
            }
            case "bkpull": {
                // What happens when the silent message arrives: apply the changes now
                java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
                final String[] out = {"?"};
                Cloud.pullDash(c, new Cloud.Done<Integer>() {
                    @Override public void ok(Integer n) { out[0] = "applied=" + n; done.countDown(); }
                    @Override public void fail(String reason) { out[0] = "failed " + reason; done.countDown(); }
                });
                done.await(30, java.util.concurrent.TimeUnit.SECONDS);
                Log.i(TAG, "bkpull " + out[0]);
                break;
            }
            case "bkcheck": {
                // The phone's data after web changes, and how many changes are still waiting online
                Db db = Db.get(c);
                StringBuilder sb = new StringBuilder("bkcheck");
                for (Gig g : db.allGigs()) {
                    if (!g.client.startsWith("Web ")) continue;
                    sb.append(" gig[").append(g.client).append("|").append(g.event).append("|fee=").append(g.feeCents)
                            .append("|paid=").append(g.paidCents).append("|status=").append(g.status).append("|start=").append(g.startMin)
                            .append("|exp=").append(db.expensesForGig(g.id).size()).append("]");
                }
                Client k = db.client("Web New Client");
                sb.append(" client=").append(k == null ? "none" : k.email + "/" + k.phone);
                int left;
                try {
                    org.json.JSONArray docs = new JSONObject(Cloud.request("GET", DOCS + "/dash/" + Cloud.get(c, Cloud.UID) + "/ops", null, null,
                            Cloud.token(c))).optJSONArray("documents");
                    left = docs == null ? 0 : docs.length();
                } catch (Cloud.HttpError e) {
                    left = -e.code;
                }
                sb.append(" waiting=").append(left);
                Log.i(TAG, sb.toString());
                break;
            }
            case "bkoffline":
                Cloud.forceOffline = "on".equals(in.getStringExtra("mode"));
                Log.i(TAG, "bkoffline " + Cloud.forceOffline);
                break;
            case "bkstate":
                Log.i(TAG, "bkstate signedIn=" + Cloud.signedIn(c) + " slug=" + Cloud.get(c, Cloud.SLUG) + " accepting=" + Cloud.accepting(c)
                        + " days=" + Cloud.days(c) + " questions=" + Cloud.questions(c) + " new=" + Cloud.newCount(c)
                        + " cached=" + Cloud.cached(c).size() + " push=" + (Cloud.get(c, Cloud.PUSH_TOKEN).isEmpty() ? "no" : "yes"));
                break;
            default:
                Log.i(TAG, "unknown booking task " + task);
        }
    }

    /** Plain JSON as a Firestore value (like the website's Firebase library writes it). */
    private static JSONObject fv(Object v) throws Exception {
        if (v instanceof JSONObject) {
            JSONObject f = new JSONObject(), o = (JSONObject) v;
            for (java.util.Iterator<String> k = o.keys(); k.hasNext(); ) {
                String key = k.next();
                f.put(key, fv(o.get(key)));
            }
            return new JSONObject().put("mapValue", new JSONObject().put("fields", f));
        }
        if (v instanceof Integer || v instanceof Long) return new JSONObject().put("integerValue", String.valueOf(v));
        if (v instanceof Number) return new JSONObject().put("doubleValue", ((Number) v).doubleValue());
        if (v instanceof Boolean) return new JSONObject().put("booleanValue", v);
        if (v == JSONObject.NULL) return new JSONObject().put("nullValue", JSONObject.NULL);
        return sv(String.valueOf(v));
    }

    private static JSONObject sv(String s) throws Exception {
        return new JSONObject().put("stringValue", s == null ? "" : s);
    }

    private static String strip(JSONObject o) {
        o.remove("exported");
        o.remove("settings");
        return o.toString();
    }
}
