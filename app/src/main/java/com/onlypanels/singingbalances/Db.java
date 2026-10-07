package com.onlypanels.singingbalances;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All data lives in a private database on the phone. */
final class Db extends SQLiteOpenHelper {
    private static Db instance;
    private final Context app;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "balances.db", null, 2);
        app = c;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE gigs (id INTEGER PRIMARY KEY AUTOINCREMENT, client TEXT NOT NULL, email TEXT, "
                + "event TEXT, notes TEXT, gig_day INTEGER NOT NULL, due_day INTEGER NOT NULL, fee_cents INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "gig_id INTEGER NOT NULL REFERENCES gigs(id) ON DELETE CASCADE, "
                + "cents INTEGER NOT NULL, day INTEGER NOT NULL, note TEXT)");
        upgradeTo2(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) upgradeTo2(db);
    }

    /** Version 2: gig status & start time, invoices, clients, expenses. Keeps existing data. */
    private static void upgradeTo2(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE gigs ADD COLUMN status INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE gigs ADD COLUMN start_min INTEGER NOT NULL DEFAULT -1");
        db.execSQL("ALTER TABLE gigs ADD COLUMN invoice_no TEXT");
        db.execSQL("ALTER TABLE gigs ADD COLUMN invoice_day INTEGER NOT NULL DEFAULT 0");
        db.execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL UNIQUE COLLATE NOCASE, email TEXT, phone TEXT, notes TEXT)");
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER NOT NULL, "
                + "cents INTEGER NOT NULL, category TEXT, note TEXT, gig_id INTEGER REFERENCES gigs(id) ON DELETE SET NULL)");
        // Turn existing gig clients into client records
        db.execSQL("INSERT OR IGNORE INTO clients (name, email) "
                + "SELECT client, MAX(IFNULL(email, '')) FROM gigs GROUP BY client COLLATE NOCASE");
    }

    /** Called after every change: refresh the widget and back up. */
    private void changed() {
        Widget.updateAll(app);
        Backup.schedule(app);
    }

    // ---------------- Gigs ----------------

    private static final String PAID = "IFNULL((SELECT SUM(p.cents) FROM payments p WHERE p.gig_id = g.id), 0)";
    private static final String SELECT = "SELECT g.id, g.client, IFNULL(NULLIF(c.email, ''), g.email), g.event, g.notes, "
            + "g.gig_day, g.due_day, g.fee_cents, " + PAID + " AS paid, "
            + "IFNULL((SELECT MAX(p.day) FROM payments p WHERE p.gig_id = g.id), -1), "
            + "g.status, g.start_min, g.invoice_no, g.invoice_day "
            + "FROM gigs g LEFT JOIN clients c ON c.name = g.client COLLATE NOCASE";

    private static String str(Cursor c, int i) {
        return c.isNull(i) ? "" : c.getString(i);
    }

    private static String[] args(long id) {
        return new String[]{String.valueOf(id)};
    }

    private static Gig readGig(Cursor c) {
        Gig g = new Gig();
        g.id = c.getLong(0);
        g.client = str(c, 1);
        g.email = str(c, 2);
        g.event = str(c, 3);
        g.notes = str(c, 4);
        g.gigDay = c.getLong(5);
        g.dueDay = c.getLong(6);
        g.feeCents = c.getLong(7);
        g.paidCents = c.getLong(8);
        g.lastPaidDay = c.getLong(9);
        g.status = c.getInt(10);
        g.startMin = c.getInt(11);
        g.invoiceNo = str(c, 12);
        g.invoiceDay = c.getLong(13);
        return g;
    }

    private List<Gig> queryGigs(String where, String[] a, String order) {
        List<Gig> out = new ArrayList<>();
        String sql = SELECT + (where == null ? "" : " WHERE " + where) + " ORDER BY " + order;
        try (Cursor c = getReadableDatabase().rawQuery(sql, a)) {
            while (c.moveToNext()) out.add(readGig(c));
        }
        return out;
    }

    /** Gigs that have happened, aren't cancelled and aren't fully paid. Oldest due first. */
    List<Gig> unpaidGigs() {
        return queryGigs("g.status != 2 AND g.gig_day <= ? AND g.fee_cents > " + PAID,
                args(Dates.today()), "g.due_day ASC, g.gig_day ASC");
    }

    /** Today and future gigs (not cancelled), soonest first. */
    List<Gig> upcomingGigs() {
        return queryGigs("g.status != 2 AND g.gig_day >= ?", args(Dates.today()), "g.gig_day ASC, g.start_min ASC");
    }

    List<Gig> allGigs() {
        return queryGigs(null, null, "g.gig_day DESC, g.start_min DESC, g.id DESC");
    }

    List<Gig> gigsBetween(long fromDay, long toDay) {
        return queryGigs("g.gig_day BETWEEN ? AND ?",
                new String[]{String.valueOf(fromDay), String.valueOf(toDay)}, "g.gig_day ASC, g.start_min ASC");
    }

    List<Gig> gigsForClient(String name) {
        return queryGigs("g.client = ? COLLATE NOCASE", new String[]{name}, "g.gig_day DESC");
    }

    Gig gig(long id) {
        List<Gig> l = queryGigs("g.id = ?", args(id), "g.id");
        return l.isEmpty() ? null : l.get(0);
    }

    long save(Gig g) {
        ContentValues v = new ContentValues();
        v.put("client", g.client);
        v.put("event", g.event);
        v.put("notes", g.notes);
        v.put("gig_day", g.gigDay);
        v.put("due_day", g.dueDay);
        v.put("fee_cents", g.feeCents);
        v.put("status", g.status);
        v.put("start_min", g.startMin);
        v.put("invoice_no", g.invoiceNo);
        v.put("invoice_day", g.invoiceDay);
        SQLiteDatabase db = getWritableDatabase();
        if (g.id == 0) {
            g.id = db.insert("gigs", null, v);
        } else {
            db.update("gigs", v, "id = ?", args(g.id));
        }
        ensureClient(g.client, g.email);
        changed();
        return g.id;
    }

    void deleteGig(long id) {
        getWritableDatabase().delete("gigs", "id = ?", args(id));
        changed();
    }

    /** Gives the gig an invoice number (if it hasn't got one) and returns it. */
    String assignInvoice(Gig g) {
        if (g.invoiceNo == null || g.invoiceNo.isEmpty()) {
            g.invoiceNo = Prefs.takeInvoiceNumber(app);
            g.invoiceDay = Dates.today();
            ContentValues v = new ContentValues();
            v.put("invoice_no", g.invoiceNo);
            v.put("invoice_day", g.invoiceDay);
            getWritableDatabase().update("gigs", v, "id = ?", args(g.id));
            changed();
        }
        return g.invoiceNo;
    }

    // ---------------- Payments ----------------

    List<Payment> payments(long gigId) {
        List<Payment> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, gig_id, cents, day, note FROM payments WHERE gig_id = ? ORDER BY day DESC, id DESC", args(gigId))) {
            while (c.moveToNext()) {
                Payment p = new Payment();
                p.id = c.getLong(0);
                p.gigId = c.getLong(1);
                p.cents = c.getLong(2);
                p.day = c.getLong(3);
                p.note = str(c, 4);
                out.add(p);
            }
        }
        return out;
    }

    /** All payments received between two days (for earnings). Each entry: {day, cents}. */
    List<long[]> paymentsBetween(long fromDay, long toDay) {
        List<long[]> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT day, cents FROM payments WHERE day BETWEEN ? AND ?",
                new String[]{String.valueOf(fromDay), String.valueOf(toDay)})) {
            while (c.moveToNext()) out.add(new long[]{c.getLong(0), c.getLong(1)});
        }
        return out;
    }

    void addPayment(long gigId, long cents, long day, String note) {
        ContentValues v = new ContentValues();
        v.put("gig_id", gigId);
        v.put("cents", cents);
        v.put("day", day);
        v.put("note", note);
        getWritableDatabase().insert("payments", null, v);
        changed();
    }

    void deletePayment(long id) {
        getWritableDatabase().delete("payments", "id = ?", args(id));
        changed();
    }

    // ---------------- Clients ----------------

    private static Client readClient(Cursor c) {
        Client k = new Client();
        k.id = c.getLong(0);
        k.name = str(c, 1);
        k.email = str(c, 2);
        k.phone = str(c, 3);
        k.notes = str(c, 4);
        return k;
    }

    Client client(String name) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, email, phone, notes FROM clients WHERE name = ? COLLATE NOCASE", new String[]{name})) {
            return c.moveToFirst() ? readClient(c) : null;
        }
    }

    Client client(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, email, phone, notes FROM clients WHERE id = ?", args(id))) {
            return c.moveToFirst() ? readClient(c) : null;
        }
    }

    List<String> clientNames() {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT name FROM clients ORDER BY name COLLATE NOCASE", null)) {
            while (c.moveToNext()) out.add(c.getString(0));
        }
        return out;
    }

    /** Creates the client if new; fills in the email if one is given. */
    private void ensureClient(String name, String email) {
        if (name == null || name.trim().isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        v.put("email", email == null ? "" : email.trim());
        db.insertWithOnConflict("clients", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (email != null && !email.trim().isEmpty()) {
            ContentValues e = new ContentValues();
            e.put("email", email.trim());
            db.update("clients", e, "name = ? COLLATE NOCASE", new String[]{name.trim()});
        }
    }

    /** Saves a client. Renaming also renames them on their gigs. Returns false if the name is taken. */
    boolean saveClient(Client k, String oldName) {
        SQLiteDatabase db = getWritableDatabase();
        Client clash = client(k.name);
        if (clash != null && clash.id != k.id) return false;
        ContentValues v = new ContentValues();
        v.put("name", k.name);
        v.put("email", k.email);
        v.put("phone", k.phone);
        v.put("notes", k.notes);
        db.beginTransaction();
        try {
            if (k.id == 0) {
                k.id = db.insert("clients", null, v);
            } else {
                db.update("clients", v, "id = ?", args(k.id));
                if (oldName != null && !oldName.equals(k.name)) {
                    ContentValues gv = new ContentValues();
                    gv.put("client", k.name);
                    db.update("gigs", gv, "client = ? COLLATE NOCASE", new String[]{oldName});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        changed();
        return true;
    }

    void deleteClient(long id) {
        getWritableDatabase().delete("clients", "id = ?", args(id));
        changed();
    }

    /** Every client with their totals worked out from their gigs. Biggest earners first. */
    List<Client> clientsWithStats() {
        Map<String, Client> byName = new LinkedHashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id, name, email, phone, notes FROM clients", null)) {
            while (c.moveToNext()) {
                Client k = readClient(c);
                byName.put(k.name.toLowerCase(), k);
            }
        }
        long today = Dates.today();
        for (Gig g : allGigs()) {
            Client k = byName.get(g.client.toLowerCase());
            if (k == null) {
                k = new Client();
                k.name = g.client;
                byName.put(g.client.toLowerCase(), k);
            }
            if (g.isCancelled()) continue;
            k.gigCount++;
            k.lastGigDay = Math.max(k.lastGigDay, g.gigDay);
            if (g.gigDay > today) {
                k.upcoming++;
                continue;
            }
            k.earnedCents += g.feeCents;
            k.receivedCents += Math.min(g.paidCents, g.feeCents);
            k.owedCents += g.balance();
            if (g.isOverdue()) k.overdueCents += g.balance();
            if (g.isPaid() && g.lastPaidDay >= 0) {
                k.paidGigs++;
                k.totalDaysToPay += Math.max(0, g.lastPaidDay - g.gigDay);
                if (g.lastPaidDay > g.dueDay) k.paidLate++;
            }
        }
        List<Client> out = new ArrayList<>(byName.values());
        out.sort((a, b) -> {
            int cmp = Long.compare(b.earnedCents + b.owedCents, a.earnedCents + a.owedCents);
            return cmp != 0 ? cmp : a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }

    // ---------------- Expenses ----------------

    private static Expense readExpense(Cursor c) {
        Expense e = new Expense();
        e.id = c.getLong(0);
        e.day = c.getLong(1);
        e.cents = c.getLong(2);
        e.category = str(c, 3);
        e.note = str(c, 4);
        e.gigId = c.isNull(5) ? 0 : c.getLong(5);
        return e;
    }

    List<Expense> expensesBetween(long fromDay, long toDay) {
        List<Expense> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, day, cents, category, note, gig_id FROM expenses WHERE day BETWEEN ? AND ? ORDER BY day DESC, id DESC",
                new String[]{String.valueOf(fromDay), String.valueOf(toDay)})) {
            while (c.moveToNext()) out.add(readExpense(c));
        }
        return out;
    }

    List<Expense> expensesForGig(long gigId) {
        List<Expense> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, day, cents, category, note, gig_id FROM expenses WHERE gig_id = ? ORDER BY day DESC, id DESC",
                args(gigId))) {
            while (c.moveToNext()) out.add(readExpense(c));
        }
        return out;
    }

    void saveExpense(Expense e) {
        ContentValues v = new ContentValues();
        v.put("day", e.day);
        v.put("cents", e.cents);
        v.put("category", e.category);
        v.put("note", e.note);
        if (e.gigId > 0) v.put("gig_id", e.gigId);
        else v.putNull("gig_id");
        SQLiteDatabase db = getWritableDatabase();
        if (e.id == 0) e.id = db.insert("expenses", null, v);
        else db.update("expenses", v, "id = ?", args(e.id));
        changed();
    }

    void deleteExpense(long id) {
        getWritableDatabase().delete("expenses", "id = ?", args(id));
        changed();
    }

    // ---------------- Backup / restore ----------------

    private static final String[] TABLES = {"clients", "gigs", "payments", "expenses"};

    JSONObject exportAll() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("app", app.getString(R.string.app_name));
        root.put("version", 2);
        root.put("exported", Dates.iso(Dates.today()));
        for (String t : TABLES) {
            JSONArray rows = new JSONArray();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM " + t, null)) {
                while (c.moveToNext()) {
                    JSONObject r = new JSONObject();
                    for (int i = 0; i < c.getColumnCount(); i++) {
                        if (c.isNull(i)) continue;
                        if (c.getType(i) == Cursor.FIELD_TYPE_INTEGER) r.put(c.getColumnName(i), c.getLong(i));
                        else r.put(c.getColumnName(i), c.getString(i));
                    }
                    rows.put(r);
                }
            }
            root.put(t, rows);
        }
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, ?> e : Prefs.sp(app).getAll().entrySet()) {
            if (Prefs.BACKUP_URI.equals(e.getKey()) || e.getKey().startsWith("backup_")
                    || Prefs.GOOGLE_EMAIL.equals(e.getKey()) || Prefs.MS_EMAIL.equals(e.getKey())
                    || Prefs.PRO_ACTIVE.equals(e.getKey()) || Prefs.PRO_CODE_UNTIL.equals(e.getKey())) continue; // sign-ins and the subscription belong to one phone / Google account
            settings.put(e.getKey(), e.getValue());
        }
        root.put("settings", settings);
        String logo = Logo.toBase64(app);
        if (!logo.isEmpty()) root.put("logo_png", logo);
        return root;
    }

    /** Replaces everything with the contents of a backup. */
    void importAll(JSONObject root) throws JSONException {
        if (!root.has("gigs")) throw new JSONException("This isn't a backup file from this app");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("expenses", null, null);
            db.delete("payments", null, null);
            db.delete("gigs", null, null);
            db.delete("clients", null, null);
            for (String t : TABLES) {
                JSONArray rows = root.optJSONArray(t);
                if (rows == null) continue;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject r = rows.getJSONObject(i);
                    ContentValues v = new ContentValues();
                    Iterator<String> keys = r.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        Object val = r.get(k);
                        if (val instanceof Number) v.put(k, ((Number) val).longValue());
                        else v.put(k, String.valueOf(val));
                    }
                    db.insertOrThrow(t, null, v);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        JSONObject s = root.optJSONObject("settings");
        if (s != null) {
            android.content.SharedPreferences.Editor ed = Prefs.sp(app).edit();
            Iterator<String> keys = s.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (Prefs.PRO_ACTIVE.equals(k) || Prefs.PRO_CODE_UNTIL.equals(k)) continue; // only Google Play / a code decides this
                Object val = s.get(k);
                if (val instanceof Boolean) ed.putBoolean(k, (Boolean) val);
                else if (val instanceof Integer) ed.putInt(k, (Integer) val);
                else if (val instanceof Long) ed.putInt(k, ((Long) val).intValue());
                else ed.putString(k, String.valueOf(val));
            }
            ed.apply();
        }
        if (root.has("logo_png")) Logo.fromBase64(app, root.optString("logo_png"));
        Theme.changed(app);
        changed();
    }
}
