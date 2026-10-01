package com.onlypanels.singingbalances;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Colours and small building blocks for every screen. Colours are set by {@link Theme#load}. */
final class Ui {
    // Accent
    static int PRIMARY = 0xFF3949AB;
    static int PRIMARY_DARK = 0xFF1A237E;
    static int PRIMARY_LIGHT = 0xFFEBEDF7;
    static int ON_PRIMARY = 0xFFFFFFFF;
    static int HERO = 0xFF3949AB;       // summary card at the top (always a deep tone with white text)
    static int HERO_DARK = 0xFF1A237E;
    // Status
    static int RED = 0xFFC62828;
    static int RED_LIGHT = 0xFFFDECEC;
    static int GREEN = 0xFF2E7D32;
    static int GREEN_LIGHT = 0xFFE8F4E9;
    static int ORANGE = 0xFFD45500;
    static int ORANGE_LIGHT = 0xFFFFF3E3;
    // Neutrals
    static int BG = 0xFFF6F6F9;         // page
    static int SURFACE = 0xFFFFFFFF;    // cards, lists, bottom bar
    static int LIGHT_GREY = 0xFFF0F0F4; // tiles, secondary buttons
    static int LINE = 0xFFE6E6EC;
    static int DARK = 0xFF1B1C20;       // main text
    static int GREY = 0xFF6B6E76;       // secondary text
    static int FAINT = 0xFFBDBEC4;      // disabled / other-month days
    static final int WHITE = 0xFFFFFFFF;

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(sp >= 18 ? Typeface.DEFAULT_BOLD : MEDIUM);
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

    /** Rounded box with a hairline border, used for cards on the page background. */
    static GradientDrawable outlined(Context c, int color, float radiusDp) {
        GradientDrawable d = rounded(c, color, radiusDp);
        d.setStroke(Math.max(1, dp(c, 1)), LINE);
        return d;
    }

    /** Adds a touch ripple on top of a background. */
    static Drawable touchable(Drawable bg, int rippleColor) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), bg, bg == null ? new android.graphics.drawable.ColorDrawable(WHITE) : bg);
    }

    static int ripple() {
        return Theme.dark ? 0x33FFFFFF : 0x1F000000;
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
        l.setBackground(color == SURFACE ? outlined(c, color, 16) : rounded(c, color, 16));
        l.setLayoutParams(matchWrap(c, 12));
        return l;
    }

    /** Full-width button. Filled when bg is the accent, softer otherwise. */
    static Button button(Context c, String label, int bg, int fg) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(MEDIUM);
        b.setTextColor(fg);
        b.setStateListAnimator(null);
        b.setBackground(touchable(rounded(c, bg, 14), ripple()));
        b.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(c, 50));
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    /** The main action on a screen. */
    static Button primary(Context c, String label) {
        return button(c, label, PRIMARY, ON_PRIMARY);
    }

    /** Secondary action in the accent's soft tone. */
    static Button tonal(Context c, String label) {
        return button(c, label, PRIMARY_LIGHT, PRIMARY);
    }

    /** Quiet action. */
    static Button quiet(Context c, String label) {
        return button(c, label, LIGHT_GREY, DARK);
    }

    static TextView section(Context c, String title) {
        TextView t = text(c, title, 16, DARK, true);
        t.setPadding(0, dp(c, 24), 0, dp(c, 6));
        return t;
    }

    /** Small uppercase label, e.g. above a group of settings. */
    static TextView overline(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, PRIMARY, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1) / 2)));
        return v;
    }

    /** Round badge with initials, e.g. "HM" for Hilton Malta. */
    static TextView avatar(Context c, String name, int bg, int fg) {
        String in = "";
        for (String w : name.trim().split("[\\s&]+")) {
            if (w.isEmpty() || !Character.isLetterOrDigit(w.charAt(0))) continue;
            in += Character.toUpperCase(w.charAt(0));
            if (in.length() == 2) break;
        }
        if (in.isEmpty()) in = "•";
        TextView t = text(c, in, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(bg);
        t.setBackground(d);
        int s = dp(c, 42);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        lp.rightMargin = dp(c, 14);
        t.setLayoutParams(lp);
        return t;
    }

    /** A list row: optional avatar, title, one or two lines, and an amount on the right. */
    static LinearLayout row(Context c, String avatarName, String title, String line2, String line3, int line3Color,
                            String amount, int amountColor) {
        LinearLayout row = hbox(c);
        int h = dp(c, 16), v = dp(c, 12);
        row.setPadding(h, v, h, v);
        if (avatarName != null) {
            boolean alert = line3Color == RED;
            row.addView(avatar(c, avatarName, alert ? RED_LIGHT : PRIMARY_LIGHT, alert ? RED : PRIMARY));
        }
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(c, title, 16, DARK, true);
        t.setMaxLines(2);
        left.addView(t);
        if (line2 != null && !line2.isEmpty()) left.addView(text(c, line2, 13, GREY, false));
        if (line3 != null && !line3.isEmpty()) {
            TextView l3 = text(c, line3, 13, line3Color, line3Color == RED || line3Color == ORANGE);
            l3.setPadding(0, dp(c, 2), 0, 0);
            left.addView(l3);
        }
        row.addView(left, weight(1f));
        if (amount != null) {
            TextView r = text(c, amount, 16, amountColor, true);
            r.setPadding(dp(c, 12), 0, 0, 0);
            row.addView(r);
        }
        row.setBackground(touchable(null, ripple()));
        return row;
    }

    static LinearLayout row(Context c, String title, String line2, String line3, int line3Color,
                            String amount, int amountColor) {
        return row(c, null, title, line2, line3, line3Color, amount, amountColor);
    }

    static LinearLayout gigRow(Context c, Gig g) {
        return row(c, g.client, g.title(), g.when(), g.status(c), g.statusColor(), Money.fmt(g.displayAmount()), g.amountColor());
    }

    /** Small heading between groups in a list, e.g. a month name. */
    static TextView groupHeader(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, GREY, true);
        t.setLetterSpacing(0.08f);
        int h = dp(c, 16);
        t.setPadding(h, dp(c, 18), h, dp(c, 6));
        return t;
    }

    /** Big number with a label, for summaries. */
    static LinearLayout tile(Context c, String label, String value, int valueColor) {
        LinearLayout l = vbox(c, 14);
        l.setBackground(outlined(c, SURFACE, 14));
        l.addView(text(c, label, 12, GREY, false));
        TextView v = text(c, value, 20, valueColor, true);
        v.setPadding(0, dp(c, 2), 0, 0);
        l.addView(v);
        return l;
    }

    /** Two tiles side by side. */
    static LinearLayout tiles(Context c, View a, View b) {
        LinearLayout r = hbox(c);
        r.setBaselineAligned(false);
        r.setLayoutParams(matchWrap(c, 12));
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, -1, 1f);
        la.rightMargin = dp(c, 6);
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, -1, 1f);
        lb.leftMargin = dp(c, 6);
        r.addView(a, la);
        r.addView(b, lb);
        return r;
    }

    /** The coloured summary card at the top of the home screen. */
    static LinearLayout hero(Context c) {
        LinearLayout l = vbox(c, 20);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{HERO, HERO_DARK});
        d.setCornerRadius(dp(c, 20));
        l.setBackground(d);
        l.setElevation(dp(c, 2));
        return l;
    }

    /** A pill-shaped segmented control. Returns the row; onPick gets the chosen key. */
    static LinearLayout segmented(Context c, String[][] options, String selected, java.util.function.Consumer<String> onPick) {
        LinearLayout bar = hbox(c);
        int p = dp(c, 4);
        bar.setPadding(p, p, p, p);
        bar.setBackground(rounded(c, LIGHT_GREY, 14));
        for (String[] o : options) {
            boolean sel = o[0].equals(selected);
            TextView t = text(c, o[1], 14, sel ? DARK : GREY, sel);
            t.setGravity(Gravity.CENTER);
            int v = dp(c, 9);
            t.setPadding(0, v, 0, v);
            if (sel) {
                GradientDrawable d = rounded(c, SURFACE, 11);
                t.setBackground(d);
                t.setElevation(dp(c, 1));
            } else {
                t.setBackground(touchable(null, ripple()));
            }
            t.setOnClickListener(x -> onPick.accept(o[0]));
            bar.addView(t, weight(1f));
        }
        return bar;
    }

    /** A vector icon tinted to a colour. */
    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView i = new ImageView(c);
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        i.setImageDrawable(d);
        int s = dp(c, sizeDp);
        i.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return i;
    }

    /** Adds a label + input field to a form and returns the field. */
    static EditText field(LinearLayout form, String label, String value, int inputType) {
        Context c = form.getContext();
        form.addView(label(c, label));
        EditText e = new EditText(c);
        e.setInputType(inputType);
        e.setText(value);
        e.setTextColor(DARK);
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
        b.setTextColor(DARK);
        b.setStateListAnimator(null);
        b.setBackground(touchable(outlined(c, SURFACE, 10), ripple()));
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        int p = dp(c, 14);
        b.setPadding(p, 0, p, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(c, 48));
        lp.topMargin = dp(c, 4);
        form.addView(b, lp);
        return b;
    }
}
