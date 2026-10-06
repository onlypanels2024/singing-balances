package com.onlypanels.singingbalances;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Makes an A4 PDF invoice for a gig. */
final class Invoice {
    private static final int W = 595, H = 842, M = 48;
    // Invoices are always printed dark-on-white, whatever the app's light/dark setting.
    private static final int INK = 0xFF1B1C20, MUTED = 0xFF6B6E76, RULE = 0xFFE3E3E8, PAID = 0xFF2E7D32;

    private Invoice() {}

    private static Paint paint(float size, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    private static float lines(Canvas c, String text, float x, float y, Paint p, float gap) {
        if (text == null || text.trim().isEmpty()) return y;
        for (String line : text.trim().split("\n")) {
            c.drawText(line.trim(), x, y, p);
            y += gap;
        }
        return y;
    }

    private static void right(Canvas c, String s, float xRight, float y, Paint p) {
        c.drawText(s, xRight - p.measureText(s), y, p);
    }

    /** Draws a QR code as sharp squares (stays crisp when printed). */
    private static void drawQr(Canvas c, String text, float x, float y, float size) {
        try {
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 0);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
            com.google.zxing.common.BitMatrix m = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, hints);
            float cell = size / m.getWidth();
            Paint ink = new Paint();
            ink.setColor(INK);
            for (int r = 0; r < m.getHeight(); r++) {
                for (int col = 0; col < m.getWidth(); col++) {
                    if (m.get(col, r)) c.drawRect(x + col * cell, y + r * cell, x + (col + 1) * cell + 0.3f, y + (r + 1) * cell + 0.3f, ink);
                }
            }
        } catch (Exception ignored) {
            // no QR code is better than no invoice
        }
    }

    /** Creates (or re-creates) the PDF and returns the file. Assigns an invoice number if needed. */
    static File create(Context ctx, Gig g) throws IOException {
        Money.load(ctx);
        Db.get(ctx).assignInvoice(g);
        int accent = Theme.strong(ctx);
        PdfDocument doc = new PdfDocument();
        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, 1).create());
        Canvas c = page.getCanvas();

        Paint title = paint(30, accent, true);
        Paint big = paint(18, INK, true);
        Paint bold = paint(11, INK, true);
        Paint body = paint(11, INK, false);
        Paint grey = paint(10, MUTED, false);
        Paint line = new Paint();
        line.setColor(RULE);
        line.setStrokeWidth(1);

        // Accent strip along the top
        Paint strip = new Paint();
        strip.setColor(accent);
        c.drawRect(0, 0, W, 6, strip);

        // Your logo and details (left)
        String name = Prefs.get(ctx, Prefs.NAME);
        float y = M + 18;
        Bitmap logo = Logo.bitmap(ctx);
        if (logo != null) {
            float maxW = 150, maxH = 64;
            float s = Math.min(maxW / logo.getWidth(), maxH / logo.getHeight());
            float lw = logo.getWidth() * s, lh = logo.getHeight() * s;
            c.drawBitmap(logo, null, new RectF(M, M - 6, M + lw, M - 6 + lh), new Paint(Paint.FILTER_BITMAP_FLAG));
            y = M - 6 + lh + 22;
        }
        c.drawText(name.isEmpty() ? "Invoice" : name, M, y, big);
        y += 18;
        y = lines(c, Prefs.get(ctx, Prefs.ADDRESS), M, y, body, 14);
        if (!Prefs.get(ctx, Prefs.PHONE).isEmpty()) { c.drawText(Prefs.get(ctx, Prefs.PHONE), M, y, body); y += 14; }
        if (!Prefs.get(ctx, Prefs.EMAIL).isEmpty()) { c.drawText(Prefs.get(ctx, Prefs.EMAIL), M, y, body); y += 14; }
        if (!Prefs.get(ctx, Prefs.VAT).isEmpty()) { c.drawText("VAT no: " + Prefs.get(ctx, Prefs.VAT), M, y, body); y += 14; }

        // Invoice box (right)
        float ry = M + 24;
        right(c, "INVOICE", W - M, ry, title);
        ry += 24;
        right(c, "No. " + g.invoiceNo, W - M, ry, bold);
        ry += 15;
        right(c, "Date: " + Dates.fmt(g.invoiceDay > 0 ? g.invoiceDay : Dates.today()), W - M, ry, body);
        ry += 15;
        long invDay = g.invoiceDay > 0 ? g.invoiceDay : Dates.today();
        String dueText = g.isPaid() ? "Paid \u2013 thank you" : g.dueDay < invDay ? "Payment due: on receipt" : "Payment due: " + Dates.fmt(g.dueDay);
        right(c, dueText, W - M, ry, body);

        // Bill to
        y = Math.max(y, ry) + 30;
        c.drawText("BILL TO", M, y, grey);
        y += 16;
        c.drawText(g.client, M, y, bold);
        y += 14;
        Client k = Db.get(ctx).client(g.client);
        if (k != null && !k.phone.isEmpty()) { c.drawText(k.phone, M, y, body); y += 14; }
        if (!g.email.isEmpty()) { c.drawText(g.email, M, y, body); y += 14; }

        // Table
        y += 24;
        float colDate = W - M - 190, colAmt = W - M;
        c.drawText("DESCRIPTION", M, y, grey);
        c.drawText("DATE", colDate, y, grey);
        right(c, "AMOUNT", colAmt, y, grey);
        y += 8;
        c.drawLine(M, y, W - M, y, line);
        y += 20;
        String desc = Words.invoiceLine(ctx);
        c.drawText(desc, M, y, bold);
        c.drawText(Dates.fmt(g.gigDay), colDate, y, body);
        right(c, Money.fmt(g.feeCents), colAmt, y, body);
        if (!g.event.isEmpty()) {
            y += 14;
            c.drawText(g.event + (g.startMin >= 0 ? ", " + Dates.time(g.startMin) : ""), M, y, grey);
        }
        y += 14;
        c.drawLine(M, y, W - M, y, line);

        // Totals
        float lx = W - M - 190;
        y += 22;
        c.drawText("Total", lx, y, body);
        right(c, Money.fmt(g.feeCents), colAmt, y, body);
        if (g.paidCents > 0) {
            y += 16;
            c.drawText("Paid", lx, y, body);
            right(c, "-" + Money.fmt(Math.min(g.paidCents, g.feeCents)), colAmt, y, body);
        }
        y += 22;
        Paint due = paint(14, g.isPaid() ? PAID : INK, true);
        c.drawText(g.isPaid() ? "PAID IN FULL" : "Balance due", lx, y, due);
        right(c, Money.fmt(g.balance()), colAmt, y, due);

        // How to pay: the one-tap link (button + QR code) and/or bank details
        String url = g.isPaid() ? "" : PayLink.url(ctx, g);
        String pay = Prefs.paymentDetails(ctx);
        if (!g.isPaid() && (!url.isEmpty() || !pay.isEmpty())) {
            y += 44;
            c.drawText("HOW TO PAY", M, y, grey);
            y += 14;
            if (!url.isEmpty()) {
                float q = 92, qx = W - M - q, qy = y - 6;
                drawQr(c, url, qx, qy, q);
                Paint cap = paint(9, MUTED, false);
                c.drawText("Scan to pay", qx + (q - cap.measureText("Scan to pay")) / 2, qy + q + 12, cap);

                String label = PayLink.label(ctx, g);
                Paint btnText = paint(13, 0xFFFFFFFF, true);
                float bw = btnText.measureText(label) + 36, bh = 32;
                Paint btn = new Paint(Paint.ANTI_ALIAS_FLAG);
                btn.setColor(accent);
                c.drawRoundRect(new RectF(M, y, M + bw, y + bh), 9, 9, btn);
                c.drawText(label, M + 18, y + 21, btnText);
                y += bh + 16;
                Paint link = paint(9.5f, accent, false);
                link.setUnderlineText(true);
                c.drawText(url, M, y, link);
                y += 13;
                if (!PayLink.fillsAmount(ctx)) {
                    c.drawText("Please enter " + Money.fmt(g.balance()) + " when you pay.", M, y, grey);
                    y += 13;
                }
                y = Math.max(y + 10, qy + q + 26);
            }
            if (!pay.isEmpty()) {
                c.drawText(url.isEmpty() ? "Bank transfer" : "Or by bank transfer", M, y, bold);
                y += 15;
                y = lines(c, pay, M, y, body, 15);
            }
            y += 2;
            c.drawText("Please quote " + g.invoiceNo + " as the payment reference.", M, y, grey);
        }

        c.drawText("Thank you!", M, H - M, paint(12, accent, true));
        doc.finishPage(page);

        File dir = new File(ctx.getCacheDir(), "share");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create folder");
        File f = new File(dir, "Invoice-" + g.invoiceNo.replaceAll("[^A-Za-z0-9_-]", "_") + ".pdf");
        try (FileOutputStream out = new FileOutputStream(f)) {
            doc.writeTo(out);
        } finally {
            doc.close();
        }
        return f;
    }
}
