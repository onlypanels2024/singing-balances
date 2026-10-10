package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Currency;
import java.util.Locale;

/** First launch for a new user: what they do, their name, currency and colour. Everything can be changed later. */
public class WelcomeActivity extends Activity {
    private EditText name;
    private Spinner currency;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.apply(this);
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        Prefs.sp(this).edit().putBoolean(Prefs.WELCOME_STARTED, true).apply();
        if (Prefs.get(this, Prefs.PROFESSION).isEmpty()) Prefs.set(this, Prefs.PROFESSION, Words.KEYS[0]);
        if (Prefs.get(this, Prefs.CURRENCY).isEmpty()) Prefs.set(this, Prefs.CURRENCY, guessCurrency());
        Theme.load(this);
        if (!Prefs.sp(this).getBoolean(Prefs.INTRO_DONE, false)) {
            intro(savedInstanceState == null ? 0 : savedInstanceState.getInt("intro", 0));
            return;
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout v = Ui.vbox(this, 20);
        v.setPadding(Ui.dp(this, 20), Ui.dp(this, 36), Ui.dp(this, 20), Ui.dp(this, 28));
        scroll.addView(v);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        v.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        TextView title = Ui.text(this, "Welcome to " + getString(R.string.app_name), 26, Ui.DARK, true);
        title.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4));
        v.addView(title);
        v.addView(Ui.text(this, "Log your bookings, send invoices and always know who still owes you. "
                + "Set it up in a few seconds – you can change all of this later in Settings.", 15, Ui.GREY, false));

        v.addView(heading("What do you do?"));
        int sel = Words.index(this);
        LinearLayout grid = null;
        for (int i = 0; i < Words.KEYS.length; i++) {
            if (i % 3 == 0) {
                grid = Ui.hbox(this);
                grid.setBaselineAligned(false);
                grid.setLayoutParams(Ui.matchWrap(this, 8));
                v.addView(grid);
            }
            final int idx = i;
            boolean on = i == sel;
            TextView chip = Ui.text(this, Words.NAMES[i], 14, on ? Ui.PRIMARY : Ui.DARK, on);
            chip.setGravity(Gravity.CENTER);
            int p = Ui.dp(this, 8);
            chip.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));
            if (on) {
                android.graphics.drawable.GradientDrawable d = Ui.rounded(this, Ui.PRIMARY_LIGHT, 14);
                d.setStroke(Ui.dp(this, 2), Ui.PRIMARY);
                chip.setBackground(d);
            } else {
                chip.setBackground(Ui.touchable(Ui.outlined(this, Ui.SURFACE, 14), Ui.ripple()));
            }
            chip.setOnClickListener(x -> {
                keep();
                Prefs.set(this, Prefs.PROFESSION, Words.KEYS[idx]);
                recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            lp.rightMargin = i % 3 == 2 ? 0 : Ui.dp(this, 8);
            grid.addView(chip, lp);
        }
        v.addView(note("You'll see \"" + Words.many(this) + "\" throughout the app. Prefer another word? Change it in Settings."));

        v.addView(heading("Your name or business name"));
        name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setSingleLine(true);
        name.setTextColor(Ui.DARK);
        name.setHint("Shown on your invoices");
        name.setText(Prefs.get(this, Prefs.NAME));
        v.addView(name);

        v.addView(heading("Currency"));
        currency = new Spinner(this);
        ArrayAdapter<String> ca = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Money.NAMES);
        ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currency.setAdapter(ca);
        currency.setSelection(Money.index(this));
        v.addView(currency);

        v.addView(heading("Send emails with"));
        String[][] apps = new String[3][];
        for (int i = 0; i < 3; i++) apps[i] = new String[]{Google.EMAIL_KEYS[i], Google.EMAIL_NAMES[i]};
        LinearLayout emailBar = Ui.segmented(this, apps, Google.emailChoice(this), key -> {
            keep();
            Prefs.set(this, Prefs.EMAIL_APP, key);
            recreate();
        });
        emailBar.setLayoutParams(Ui.matchWrap(this, 8));
        v.addView(emailBar);

        v.addView(heading("Pick a colour"));
        v.addView(SettingsActivity.accentPicker(this, Theme.accentIndex(this), i -> {
            keep();
            Prefs.set(this, Prefs.ACCENT, Theme.ACCENT_KEYS[i]);
            Theme.changed(this);
            recreate();
        }));

        Button go = Ui.primary(this, "Get started");
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 54));
        glp.topMargin = Ui.dp(this, 28);
        go.setLayoutParams(glp);
        go.setOnClickListener(x -> {
            keep();
            Prefs.sp(this).edit().putBoolean(Prefs.SETUP_DONE, true).apply();
            Theme.changed(this);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        v.addView(go);

        setContentView(scroll);
        Theme.bars(this);
    }

    private int introPage;

    /** Three short pages on what ShowFee does, before the setup questions. */
    private void intro(int page) {
        introPage = page;
        String[][] pages = {
                {"📅", "Welcome to " + getString(R.string.app_name),
                        "Every " + Words.one(this) + " in one place",
                        "Log a " + Words.one(this) + " in seconds – who, when, where and the fee. "
                                + "ShowFee reminds you before each one, and it can go straight into your calendar."},
                {"💶", "No more chasing from memory", "Always know who owes you",
                        "See what's paid, what's due and what's overdue at a glance. "
                                + "Record cash, transfers and part payments as they come in."},
                {"⚡", "Get paid faster", "Invoices and reminders in one tap",
                        "Send a PDF invoice or a friendly reminder with a \"Pay here\" link by email, WhatsApp or text. "
                                + "These are part of ShowFee Pro, free for your first month."}};
        String[] pg = pages[page];

        LinearLayout v = Ui.vbox(this, 0);
        int pad = Ui.dp(this, 28);
        v.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 28));
        v.setBackgroundColor(Ui.BG);

        TextView skip = Ui.text(this, page < pages.length - 1 ? "Skip" : " ", 15, Ui.GREY, false);
        skip.setGravity(Gravity.END);
        skip.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        skip.setOnClickListener(x -> introDone());
        v.addView(skip, Ui.matchWrap(this, 0));

        LinearLayout middle = Ui.vbox(this, 0);
        middle.setGravity(Gravity.CENTER);
        TextView art = Ui.text(this, pg[0], 64, Ui.DARK, false);
        art.setGravity(Gravity.CENTER);
        art.setBackground(Ui.rounded(this, Ui.PRIMARY_LIGHT, 60));
        int s = Ui.dp(this, 132);
        middle.addView(art, new LinearLayout.LayoutParams(s, s));
        TextView over = Ui.text(this, pg[1].toUpperCase(), 12, Ui.PRIMARY, true);
        over.setGravity(Gravity.CENTER);
        over.setLetterSpacing(0.08f);
        over.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 8));
        middle.addView(over);
        TextView title = Ui.text(this, pg[2], 26, Ui.DARK, true);
        title.setGravity(Gravity.CENTER);
        middle.addView(title);
        TextView body = Ui.text(this, pg[3], 16, Ui.GREY, false);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(0, 1.15f);
        body.setPadding(0, Ui.dp(this, 14), 0, 0);
        middle.addView(body);
        v.addView(middle, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout dots = Ui.hbox(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < pages.length; i++) {
            android.view.View dot = new android.view.View(this);
            dot.setBackground(Ui.rounded(this, i == page ? Ui.PRIMARY : Ui.PRIMARY_LIGHT, 4));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.dp(this, i == page ? 22 : 8), Ui.dp(this, 8));
            dl.leftMargin = dl.rightMargin = Ui.dp(this, 4);
            dots.addView(dot, dl);
        }
        dots.setPadding(0, 0, 0, Ui.dp(this, 22));
        v.addView(dots, Ui.matchWrap(this, 0));

        boolean last = page == pages.length - 1;
        Button next = Ui.primary(this, last ? "Set up ShowFee" : "Next");
        next.setOnClickListener(x -> {
            if (last) introDone();
            else intro(page + 1);
        });
        v.addView(next, new LinearLayout.LayoutParams(-1, Ui.dp(this, 54)));

        setContentView(v);
        Theme.bars(this);
    }

    private void introDone() {
        Prefs.sp(this).edit().putBoolean(Prefs.INTRO_DONE, true).apply();
        recreate();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("intro", introPage);
    }

    private TextView heading(String s) {
        TextView t = Ui.text(this, s, 16, Ui.DARK, true);
        t.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 2));
        return t;
    }

    private TextView note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.GREY, false);
        t.setPadding(0, Ui.dp(this, 10), 0, 0);
        return t;
    }

    /** Saves what's been typed so far (the screen redraws when a choice changes). */
    private void keep() {
        if (name == null) return; // still on the intro pages
        Prefs.set(this, Prefs.NAME, name.getText().toString());
        Prefs.set(this, Prefs.CURRENCY, Money.CODES[currency.getSelectedItemPosition()]);
    }

    @Override
    protected void onPause() {
        super.onPause();
        keep();
    }

    /** The phone's own currency if we support it, otherwise euro. */
    private static String guessCurrency() {
        try {
            String code = Currency.getInstance(Locale.getDefault()).getCurrencyCode();
            for (String c : Money.CODES) if (c.equals(code)) return c;
        } catch (Exception ignored) {
        }
        return "EUR";
    }
}
