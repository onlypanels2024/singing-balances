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
