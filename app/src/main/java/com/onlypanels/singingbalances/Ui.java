package com.onlypanels.singingbalances;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int PRIMARY = 0xFF6A1B9A;
    static final int PRIMARY_DARK = 0xFF4A148C;
    static final int PRIMARY_LIGHT = 0xFFF3E5F5;
    static final int RED = 0xFFC62828;
    static final int RED_LIGHT = 0xFFFFEBEE;
    static final int GREEN = 0xFF2E7D32;
    static final int GREEN_LIGHT = 0xFFE8F5E9;
    static final int ORANGE = 0xFFE65100;
    static final int GREY = 0xFF6D6D6D;
    static final int LIGHT_GREY = 0xFFF2F2F2;
    static final int LINE = 0xFFE3E3E3;
    static final int DARK = 0xFF212121;
    static final int WHITE = 0xFFFFFFFF;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout vbox(Context c, int padDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, padDp);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, -2, w);
    }

    /** Rounded coloured box with padding. */
    static LinearLayout card(Context c, int color) {
        LinearLayout l = vbox(c, 16);
        l.setBackground(rounded(c, color, 12));
        l.setLayoutParams(matchWrap(c, 12));
        return l;
    }

    /** Full-width coloured button. */
    static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(fg);
        b.setBackground(rounded(c, bg, 10));
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(c, 50));
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    static TextView section(Context c, String title) {
        TextView t = text(c, title, 17, DARK, true);
        t.setPadding(0, dp(c, 24), 0, dp(c, 4));
        return t;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1) / 2)));
        return v;
    }

    /** A list row: title, one or two grey/coloured lines, and an amount on the right. */
    static LinearLayout row(Context c, String title, String line2, String line3, int line3Color,
                            String amount, int amountColor) {
        LinearLayout row = hbox(c);
        int h = dp(c, 16), v = dp(c, 12);
        row.setPadding(h, v, h, v);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(c, title, 16, DARK, true));
        if (line2 != null && !line2.isEmpty()) left.addView(text(c, line2, 13, GREY, false));
        if (line3 != null && !line3.isEmpty()) left.addView(text(c, line3, 13, line3Color, line3Color == RED));
        row.addView(left, weight(1f));
        if (amount != null) {
            TextView r = text(c, amount, 17, amountColor, true);
            r.setPadding(dp(c, 12), 0, 0, 0);
            row.addView(r);
        }
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        return row;
    }

    static LinearLayout gigRow(Context c, Gig g) {
        return row(c, g.title(), g.when(), g.status(), g.statusColor(), Money.fmt(g.displayAmount()), g.amountColor());
    }

    /** Small heading between groups in a list, e.g. a month name. */
    static TextView groupHeader(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, PRIMARY, true);
        t.setLetterSpacing(0.05f);
        t.setBackgroundColor(LIGHT_GREY);
        int h = dp(c, 16), v = dp(c, 6);
        t.setPadding(h, v, h, v);
        return t;
    }

    /** Big number with a label, for summaries. */
    static LinearLayout tile(Context c, String label, String value, int valueColor) {
        LinearLayout l = vbox(c, 12);
        l.setBackground(rounded(c, LIGHT_GREY, 10));
        l.addView(text(c, label, 12, GREY, false));
        l.addView(text(c, value, 20, valueColor, true));
        return l;
    }

    /** Two tiles side by side. */
    static LinearLayout tiles(Context c, View a, View b) {
        LinearLayout r = hbox(c);
        r.setLayoutParams(matchWrap(c, 10));
        LinearLayout.LayoutParams la = weight(1f);
        la.rightMargin = dp(c, 5);
        LinearLayout.LayoutParams lb = weight(1f);
        lb.leftMargin = dp(c, 5);
        r.addView(a, la);
        r.addView(b, lb);
        return r;
    }

    /** Adds a label + input field to a form and returns the field. */
    static EditText field(LinearLayout form, String label, String value, int inputType) {
        Context c = form.getContext();
        form.addView(label(c, label));
        EditText e = new EditText(c);
        e.setInputType(inputType);
        e.setText(value);
        e.setSingleLine((inputType & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        form.addView(e);
        return e;
    }

    static TextView label(Context c, String s) {
        TextView l = text(c, s, 13, GREY, false);
        l.setPadding(0, dp(c, 12), 0, 0);
        return l;
    }

    /** Adds a label + tappable button (for dates/times) to a form. */
    static Button pickerButton(LinearLayout form, String label) {
        Context c = form.getContext();
        form.addView(label(c, label));
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        form.addView(b);
        return b;
    }
}
