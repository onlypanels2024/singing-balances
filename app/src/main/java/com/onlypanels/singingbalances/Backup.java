package com.onlypanels.singingbalances;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Automatic backup to a file you choose once (e.g. in Google Drive via the Drive app).
 * After every change the app rewrites that file a few seconds later.
 */
final class Backup {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static Runnable pending;

    private Backup() {}

    static boolean isSetUp(Context c) {
        return !Prefs.get(c, Prefs.BACKUP_URI).isEmpty();
    }

    static Intent chooseFileIntent() {
        return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "Singing-backup.json");
    }

    static Intent restoreIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
    }

    /** Remember the chosen file and keep permission to write to it. */
    static void setFile(Context c, Uri uri) {
        try {
            c.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // some apps don't offer lasting access; we'll find out on the first write
        }
        Prefs.set(c, Prefs.BACKUP_URI, uri.toString());
    }

    static void schedule(Context c) {
        if (!isSetUp(c)) return;
        final Context app = c.getApplicationContext();
        if (pending != null) MAIN.removeCallbacks(pending);
        pending = () -> IO.execute(() -> writeNow(app));
        MAIN.postDelayed(pending, 4000);
    }

    /** Writes the backup file now. Returns null on success, or an error message. */
    static String writeNow(Context c) {
        String u = Prefs.get(c, Prefs.BACKUP_URI);
        if (u.isEmpty()) return "No backup file chosen yet";
        String error = null;
        try {
            byte[] data = Db.get(c).exportAll().toString(1).getBytes(StandardCharsets.UTF_8);
            Uri uri = Uri.parse(u);
            OutputStream out;
            try {
                out = c.getContentResolver().openOutputStream(uri, "wt");
            } catch (Exception e) {
                out = c.getContentResolver().openOutputStream(uri, "w");
            }
            if (out == null) throw new Exception("couldn't open the file");
            try (OutputStream o = out) {
                o.write(data);
            }
            Prefs.sp(c).edit().putLong(Prefs.BACKUP_LAST_OK, System.currentTimeMillis())
                    .putString(Prefs.BACKUP_ERROR, "").apply();
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Prefs.set(c, Prefs.BACKUP_ERROR, error);
        }
        return error;
    }

    static JSONObject read(Context c, Uri uri) throws Exception {
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("couldn't open the file");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return new JSONObject(new String(buf.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    static String status(Context c) {
        if (!isSetUp(c)) return "Not set up – your data is only on this phone.";
        String err = Prefs.get(c, Prefs.BACKUP_ERROR);
        long last = Prefs.sp(c).getLong(Prefs.BACKUP_LAST_OK, 0);
        String when = last == 0 ? "never" : java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(new java.util.Date(last));
        if (!err.isEmpty()) return "⚠ Last backup failed: " + err + "\nLast good backup: " + when;
        return "✓ Backing up automatically. Last backup: " + when;
    }
}
