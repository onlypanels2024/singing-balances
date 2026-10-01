package com.onlypanels.singingbalances;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** The user's own logo for invoices, kept as a small PNG inside the app. */
final class Logo {
    private static final int MAX = 800;

    private Logo() {}

    static File file(Context c) {
        return new File(c.getFilesDir(), "logo.png");
    }

    static boolean exists(Context c) {
        return file(c).exists();
    }

    static Bitmap bitmap(Context c) {
        return exists(c) ? BitmapFactory.decodeFile(file(c).getPath()) : null;
    }

    static void delete(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }

    /** Copies a picture the user picked, shrunk to at most 800 px. */
    static void save(Context c, Uri uri) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0) throw new IOException("That file isn't a picture");
        int sample = 1;
        while (o.outWidth / (sample * 2) >= MAX || o.outHeight / (sample * 2) >= MAX) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap b;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            b = BitmapFactory.decodeStream(in, null, o2);
        }
        if (b == null) throw new IOException("Couldn't read that picture");
        float scale = Math.min(1f, (float) MAX / Math.max(b.getWidth(), b.getHeight()));
        if (scale < 1f) {
            b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * scale), Math.round(b.getHeight() * scale), true);
        }
        try (FileOutputStream out = new FileOutputStream(file(c))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    /** For backups: the logo as text, or "" if there's none. */
    static String toBase64(Context c) {
        Bitmap b = bitmap(c);
        if (b == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    static void fromBase64(Context c, String s) {
        if (s == null || s.isEmpty()) return;
        try (FileOutputStream out = new FileOutputStream(file(c))) {
            out.write(Base64.decode(s, Base64.DEFAULT));
        } catch (Exception ignored) {
            // a broken logo shouldn't stop a restore
        }
    }
}
