package com.onlypanels.singingbalances;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** All data lives in a private database on the phone. */
final class Db extends SQLiteOpenHelper {
    private static Db instance;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "balances.db", null, 1);
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    private static final String PAID = "IFNULL((SELECT SUM(p.cents) FROM payments p WHERE p.gig_id = g.id), 0)";
    private static final String SELECT = "SELECT g.id, g.client, g.email, g.event, g.notes, g.gig_day, g.due_day, g.fee_cents, "
            + PAID + " AS paid FROM gigs g";

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
        return g;
    }

    /** unpaidOnly: oldest due first. Otherwise every gig, newest first. */
    List<Gig> gigs(boolean unpaidOnly) {
        String sql = unpaidOnly
                ? SELECT + " WHERE g.fee_cents > " + PAID + " ORDER BY g.due_day ASC, g.gig_day ASC"
                : SELECT + " ORDER BY g.gig_day DESC, g.id DESC";
        List<Gig> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) out.add(readGig(c));
        }
        return out;
    }

    Gig gig(long id) {
        try (Cursor c = getReadableDatabase().rawQuery(SELECT + " WHERE g.id = ?", args(id))) {
            return c.moveToFirst() ? readGig(c) : null;
        }
    }

    long save(Gig g) {
        ContentValues v = new ContentValues();
        v.put("client", g.client);
        v.put("email", g.email);
        v.put("event", g.event);
        v.put("notes", g.notes);
        v.put("gig_day", g.gigDay);
        v.put("due_day", g.dueDay);
        v.put("fee_cents", g.feeCents);
        SQLiteDatabase db = getWritableDatabase();
        if (g.id == 0) {
            g.id = db.insert("gigs", null, v);
        } else {
            db.update("gigs", v, "id = ?", args(g.id));
        }
        return g.id;
    }

    void deleteGig(long id) {
        getWritableDatabase().delete("gigs", "id = ?", args(id));
    }

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

    void addPayment(long gigId, long cents, long day, String note) {
        ContentValues v = new ContentValues();
        v.put("gig_id", gigId);
        v.put("cents", cents);
        v.put("day", day);
        v.put("note", note);
        getWritableDatabase().insert("payments", null, v);
    }

    void deletePayment(long id) {
        getWritableDatabase().delete("payments", "id = ?", args(id));
    }
}
