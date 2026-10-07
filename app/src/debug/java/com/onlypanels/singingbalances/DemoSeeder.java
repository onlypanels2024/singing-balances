package com.onlypanels.singingbalances;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONObject;

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
                Pro.fakePrice = Pro.forced == null ? null : "1 month free, then €6.99/month";
                Theme.changed(c);
                Log.i(TAG, "plan " + Pro.forced + " bookingsThisMonth=" + Pro.bookingsInMonth(c, Dates.today(), 0));
            }
            else if ("update".equals(task)) fakeUpdate(c, intent.getStringExtra("step"));
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
                + " backup=" + Backup.isSetUp(c) + " pay=" + PayLink.service(c) + ":" + PayLink.url(c, null) + " names=" + names);
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

    private static String strip(JSONObject o) {
        o.remove("exported");
        o.remove("settings");
        return o.toString();
    }
}
