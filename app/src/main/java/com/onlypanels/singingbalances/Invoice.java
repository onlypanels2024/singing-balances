package com.onlypanels.singingbalances;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Makes an A4 PDF invoice for a gig. */
final class Invoice {
    private static final int W = 595, H = 842, M = 48;

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

    /** Creates (or re-creates) the PDF and returns the file. Assigns an invoice number if needed. */
    static File create(Context ctx, Gig g) throws IOException {
        Db.get(ctx).assignInvoice(g);
        PdfDocument doc = new PdfDocument();
        PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, 1).create());
        Canvas c = page.getCanvas();

        Paint title = paint(30, Ui.PRIMARY, true);
        Paint big = paint(18, Ui.DARK, true);
        Paint bold = paint(11, Ui.DARK, true);
        Paint body = paint(11, Ui.DARK, false);
        Paint grey = paint(10, Ui.GREY, false);
        Paint line = new Paint();
        line.setColor(Ui.LINE);
        line.setStrokeWidth(1);

        // Your details (left)
        String name = Prefs.get(ctx, Prefs.NAME);
        float y = M + 18;
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
        right(c, "Payment due: " + Dates.fmt(g.dueDay), W - M, ry, body);

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
        String desc = "Live vocal performance";
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
        Paint due = paint(14, g.isPaid() ? Ui.GREEN : Ui.DARK, true);
        c.drawText(g.isPaid() ? "PAID IN FULL" : "Balance due", lx, y, due);
        right(c, Money.fmt(g.balance()), colAmt, y, due);

        // Payment details
        String pay = Prefs.paymentDetails(ctx);
        if (!g.isPaid() && !pay.isEmpty()) {
            y += 44;
            c.drawText("HOW TO PAY", M, y, grey);
            y += 16;
            y = lines(c, pay, M, y, body, 15);
            y += 2;
            c.drawText("Please quote " + g.invoiceNo + " as the payment reference.", M, y, grey);
        }

        c.drawText("Thank you!", M, H - M, paint(12, Ui.PRIMARY, true));
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
