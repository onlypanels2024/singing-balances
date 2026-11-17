package com.onlypanels.singingbalances;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

/** A4 PDF of a contract: the wording, then both signatures (or a space for the client's, if not signed yet). */
final class ContractPdf {
    private static final int W = 595, H = 842, M = 54;
    private static final int INK = 0xFF1B1C20, MUTED = 0xFF6B6E76, RULE = 0xFFB9BBC4;

    private ContractPdf() {}

    private static TextPaint paint(float size, int color, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    static File create(Context ctx, Contract k) throws IOException {
        int accent = Theme.strong(ctx);
        PdfDocument doc = new PdfDocument();
        int pageNo = 1;
        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        Canvas c = page.getCanvas();
        Paint strip = new Paint();
        strip.setColor(accent);
        c.drawRect(0, 0, W, 6, strip);

        float y = M;
        Bitmap logo = Logo.bitmap(ctx);
        if (logo != null) {
            float s = Math.min(130f / logo.getWidth(), 56f / logo.getHeight());
            c.drawBitmap(logo, null, new RectF(M, y - 10, M + logo.getWidth() * s, y - 10 + logo.getHeight() * s),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            y += logo.getHeight() * s + 6;
        }
        c.drawText(k.title, M, y + 14, paint(22, accent, true));
        y += 34;
        if (k.version > 1) {
            c.drawText("Version " + k.version, M, y, paint(10, MUTED, false));
            y += 16;
        }
        y += 6;

        // The wording, split across pages if it's long
        TextPaint body = paint(11, INK, false);
        StaticLayout text = StaticLayout.Builder.obtain(k.text, 0, k.text.length(), body, W - 2 * M)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3, 1f).build();
        int line = 0;
        while (line < text.getLineCount()) {
            float room = H - M - y;
            int first = line;
            float top = text.getLineTop(first);
            while (line < text.getLineCount() && text.getLineBottom(line) - top <= room) line++;
            if (line == first) line++; // never stall
            c.save();
            c.translate(M, y - top);
            c.clipRect(0, top, W - 2 * M, text.getLineBottom(line - 1));
            text.draw(c);
            c.restore();
            y += text.getLineBottom(line - 1) - top;
            if (line < text.getLineCount()) {
                doc.finishPage(page);
                page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, ++pageNo).create());
                c = page.getCanvas();
                y = M;
            }
        }

        // Signatures need about 150pt
        if (H - M - y < 160) {
            doc.finishPage(page);
            page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, ++pageNo).create());
            c = page.getCanvas();
            y = M;
        }
        y += 28;
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        float colW = (W - 2 * M - 30) / 2f;
        signature(c, M, y, colW, k.performerSig(ctx), k.performer,
                k.sentAt > 0 ? "Signed " + df.format(new Date(k.sentAt)) : "");
        signature(c, M + colW + 30, y, colW, k.isSigned() ? k.clientSig(ctx) : null,
                k.isSigned() ? k.clientName : (k.client.isEmpty() ? "Client" : k.client),
                k.isSigned() ? "Signed " + df.format(new Date(k.signedAt)) : "Not signed yet");
        y += 128;
        Paint grey = paint(9, MUTED, false);
        c.drawText("Signed online with ShowFee · reference " + k.id, M, Math.min(y, H - M + 20), grey);
        doc.finishPage(page);

        File dir = new File(ctx.getCacheDir(), "share");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create folder");
        String who = (k.client.isEmpty() ? "client" : k.client).replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        File f = new File(dir, "Contract-" + (who.isEmpty() ? "client" : who) + (k.version > 1 ? "-v" + k.version : "")
                + (k.isSigned() ? "-signed" : "") + ".pdf");
        try (FileOutputStream out = new FileOutputStream(f)) {
            doc.writeTo(out);
        } finally {
            doc.close();
        }
        return f;
    }

    private static void signature(Canvas c, float x, float y, float w, File img, String name, String when) {
        Bitmap b = img != null && img.exists() ? BitmapFactory.decodeFile(img.getAbsolutePath()) : null;
        if (b != null) {
            float s = Math.min(w / b.getWidth(), 70f / b.getHeight());
            c.drawBitmap(b, null, new RectF(x, y + 70 - b.getHeight() * s, x + b.getWidth() * s, y + 70),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
        }
        Paint rule = new Paint();
        rule.setColor(RULE);
        rule.setStrokeWidth(1);
        c.drawLine(x, y + 76, x + w, y + 76, rule);
        c.drawText(name, x, y + 92, paint(11, INK, true));
        c.drawText(when, x, y + 106, paint(9, MUTED, false));
    }
}
