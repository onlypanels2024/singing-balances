package com.onlypanels.singingbalances;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int PRIMARY = 0xFF6A1B9A;
    static final int PRIMARY_LIGHT = 0xFFF3E5F5;
    static final int RED = 0xFFC62828;
    static final int GREEN = 0xFF2E7D32;
    static final int ORANGE = 0xFFE65100;
    static final int GREY = 0xFF6D6D6D;
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

    static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 50));
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    /** Adds a label + input field to a form and returns the field. */
    static EditText field(LinearLayout form, String label, String value, int inputType) {
        Context c = form.getContext();
        TextView l = text(c, label, 13, GREY, false);
        l.setPadding(0, dp(c, 12), 0, 0);
        form.addView(l);
        EditText e = new EditText(c);
        e.setInputType(inputType);
        e.setText(value);
        e.setSingleLine((inputType & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0);
        form.addView(e);
        return e;
    }

    /** Adds a label + tappable date button to a form. */
    static Button dateButton(LinearLayout form, String label) {
        Context c = form.getContext();
        TextView l = text(c, label, 13, GREY, false);
        l.setPadding(0, dp(c, 12), 0, 0);
        form.addView(l);
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        form.addView(b);
        return b;
    }
}
