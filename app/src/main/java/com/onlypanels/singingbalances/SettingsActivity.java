package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Your details, payment details, invoices, reminders, backup and export. */
public class SettingsActivity extends Activity {
    private static final int REQ_BACKUP_FILE = 10, REQ_RESTORE = 11, REQ_EXPORT_GIGS = 12, REQ_EXPORT_EXPENSES = 13,
            REQ_LOGO = 14;
    private static final int TEXT_SENTENCE = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int PLAIN = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
    private static final int NUMBER = InputType.TYPE_CLASS_NUMBER;

    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private EditText terms, nextInvoice;
    private Switch notify, askCal;
    private Spinner hour;
    private TextView backupStatus;
    private Spinner profession, currency;
    private ImageView logoPreview;
    private Button logoButton, logoRemove;

    /** Which page this is: null = the Settings menu, otherwise one of the keys below. */
    private String section;
    static final String WORK = "work", LOOK = "look", BUSINESS = "business", PAY = "pay", EMAIL_S = "email",
            REMIND = "reminders", BACKUP = "backup";

    private boolean is(String key) {
        return key.equals(section);
    }

    static void open(Activity a, String section) {
        a.startActivity(new Intent(a, SettingsActivity.class).putExtra("section", section));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.apply(this);
        super.onCreate(savedInstanceState);
        section = getIntent().getStringExtra("section");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        if (section == null) {
            setTitle("Settings");
            Theme.bars(this);
            return; // the menu is drawn in onResume so its summaries are always up to date
        }
        setTitle(titleOf(section));
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.vbox(this, 16);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(page);
        LinearLayout f;

        // ---- Your work ----
        if (is(WORK)) {
        f = group(page, null, "Choose what you do and OutRo uses your words everywhere.");
        f.addView(Ui.label(this, "What do you do?"));
        profession = new Spinner(this);
        ArrayAdapter<String> pa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Words.NAMES);
        pa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        profession.setAdapter(pa);
        profession.setSelection(Words.index(this));
        f.addView(profession);
        add(f, Prefs.TAB_TITLE, "Name of the first tab", TEXT);
        add(f, Prefs.WORD_ONE, "Word for one job", InputType.TYPE_CLASS_TEXT);
        add(f, Prefs.WORD_MANY, "Word for several", InputType.TYPE_CLASS_TEXT);
        add(f, Prefs.INVOICE_LINE, "Description on invoices", TEXT_SENTENCE);
        f.addView(hint("Leave a box empty to use the suggestion shown in grey."));
        refreshHints();
        profession.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> p, android.view.View v, int pos, long id) {
                if (Words.KEYS[pos].equals(Prefs.get(SettingsActivity.this, Prefs.PROFESSION))) return;
                Prefs.set(SettingsActivity.this, Prefs.PROFESSION, Words.KEYS[pos]);
                refreshHints();
                Theme.changed(SettingsActivity.this);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> p) {
            }
        });
        }

        // ---- Appearance ----
        if (is(LOOK)) {
        LinearLayout look = group(page, null, "Pick a colour and light or dark mode. Your invoices use the colour too.");
        look.addView(Ui.label(this, "Colour"));
        look.addView(accentPicker(this, Theme.accentIndex(this), i -> {
            save();
            Prefs.set(this, Prefs.ACCENT, Theme.ACCENT_KEYS[i]);
            Theme.changed(this);
            recreate();
        }));
        look.addView(Ui.label(this, "Mode"));
        String mode = Prefs.get(this, Prefs.THEME_MODE);
        String[][] modes = new String[3][];
        for (int i = 0; i < 3; i++) modes[i] = new String[]{Theme.MODE_KEYS[i], Theme.MODE_NAMES[i]};
        LinearLayout modeBar = Ui.segmented(this, modes, mode.isEmpty() ? Theme.MODE_LIGHT : mode, key -> {
            save();
            Prefs.set(this, Prefs.THEME_MODE, key);
            Theme.changed(this);
            recreate();
        });
        modeBar.setLayoutParams(Ui.matchWrap(this, 6));
        look.addView(modeBar);
        }

        // ---- Business details ----
        if (is(BUSINESS)) {
        f = group(page, null, "Shown on your invoices and emails.");
        add(f, Prefs.NAME, "Your name / business name", TEXT);
        add(f, Prefs.EMAIL, "Your email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        add(f, Prefs.PHONE, "Your phone", InputType.TYPE_CLASS_PHONE);
        add(f, Prefs.ADDRESS, "Address (optional)", TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        add(f, Prefs.VAT, "VAT / tax number (if you have one)", PLAIN);
        f.addView(Ui.label(this, "Logo on invoices"));
        logoPreview = new ImageView(this);
        logoPreview.setAdjustViewBounds(true);
        logoPreview.setMaxHeight(Ui.dp(this, 72));
        logoPreview.setScaleType(ImageView.ScaleType.FIT_START);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = Ui.dp(this, 6);
        f.addView(logoPreview, llp);
        logoButton = Ui.tonal(this, "");
        logoButton.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_LOGO));
        f.addView(logoButton);
        logoRemove = Ui.quiet(this, "Remove logo");
        logoRemove.setOnClickListener(v -> {
            Logo.delete(this);
            showLogo();
        });
        f.addView(logoRemove);
        showLogo();
        }

        // ---- Email ----
        if (is(EMAIL_S)) {
        f = group(page, null, null);
        TextView signTitle = Ui.text(this, "Send straight from OutRo", 15, Ui.DARK, true);
        signTitle.setPadding(0, Ui.dp(this, 10), 0, 0);
        f.addView(signTitle);
        if (Mail.isConnected(this)) {
            f.addView(Ui.text(this, "Signed in as " + Mail.email(this) + " (" + Mail.service(this) + "). Invoices and "
                    + "reminders are sent from this address – you see a preview first, and a copy lands in your Sent folder.",
                    13, Ui.GREY, false));
            Button out = Ui.quiet(this, "Sign out of " + (Mail.isMicrosoft(this) ? "Microsoft" : "Google"));
            out.setOnClickListener(v -> {
                if (Mail.isMicrosoft(this)) MicrosoftAccount.signOut(this, this::recreate);
                else GoogleAccount.signOut(this, this::recreate);
            });
            f.addView(out);
        } else {
            f.addView(Ui.text(this, "Sign in once and OutRo sends invoices and reminders from your own email address, "
                    + "without opening another app. OutRo can only send – it can't read your emails.", 13, Ui.GREY, false));
            GoogleAccount.TokenCallback signedIn = new GoogleAccount.TokenCallback() {
                @Override
                public void ok(String token) {
                    Toast.makeText(SettingsActivity.this, "Signed in ✓", Toast.LENGTH_SHORT).show();
                    recreate();
                }

                @Override
                public void fail(String reason) {
                    Toast.makeText(SettingsActivity.this, reason, Toast.LENGTH_LONG).show();
                }
            };
            Button in = Ui.primary(this, "Sign in with Google (Gmail)");
            in.setOnClickListener(v -> GoogleAccount.authorize(this, true, signedIn));
            f.addView(in);
            Button ms = Ui.primary(this, "Sign in with Microsoft (Outlook, Hotmail)");
            ms.setOnClickListener(v -> MicrosoftAccount.authorize(this, true, signedIn));
            f.addView(ms);
        }
        f.addView(Ui.label(this, Mail.isConnected(this)
                ? "If you choose \"Use email app\", open it in" : "Otherwise, open emails in"));
        String[][] apps = new String[3][];
        for (int i = 0; i < 3; i++) apps[i] = new String[]{Google.EMAIL_KEYS[i], Google.EMAIL_NAMES[i]};
        LinearLayout emailBar = Ui.segmented(this, apps, Google.emailChoice(this), key -> {
            save();
            Prefs.set(this, Prefs.EMAIL_APP, key);
            recreate();
        });
        emailBar.setLayoutParams(Ui.matchWrap(this, 6));
        f.addView(emailBar);
        f.addView(hint("If the chosen app isn't on the phone, you'll be offered your other email apps. "
                + "Calendar entries still go to Google Calendar."));
        }

        // ---- Getting paid ----
        if (is(PAY)) {
        f = group(page, "Currency", null);
        currency = new Spinner(this);
        ArrayAdapter<String> ca = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Money.NAMES);
        ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currency.setAdapter(ca);
        currency.setSelection(Money.index(this));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(this, 8);
        f.addView(currency, clp);
        f.addView(hint("Changing currency only changes the symbol – amounts you've already logged aren't converted."));

        f = group(page, "How clients pay you", "Added to invoices and payment reminders automatically.");
        add(f, Prefs.BANK_NAME, "Account holder name (if different)", TEXT);
        add(f, Prefs.IBAN, "IBAN / account number", PLAIN);
        add(f, Prefs.BIC, "BIC / SWIFT / sort code (optional)", PLAIN);
        add(f, Prefs.REVOLUT, "Payment link or tag – Revolut, PayPal… (optional)", InputType.TYPE_CLASS_TEXT);

        f = group(page, "Invoices & payment terms", null);
        add(f, Prefs.INVOICE_PREFIX, "Invoice number starts with", PLAIN);
        if (Prefs.get(this, Prefs.INVOICE_PREFIX).isEmpty()) fields.get(Prefs.INVOICE_PREFIX).setText("INV-");
        nextInvoice = Ui.field(f, "Next invoice number", String.valueOf(Prefs.sp(this).getInt(Prefs.NEXT_INVOICE, 1)), NUMBER);
        terms = Ui.field(f, "Payment due (days after the " + Words.one(this) + ") – 0 means paid " + Words.onTheNight(this),
                String.valueOf(Prefs.termsDays(this)), NUMBER);
        }

        // ---- Reminders ----
        if (is(REMIND)) {
        f = group(page, null, null);
        notify = new Switch(this);
        notify.setText("Daily reminders about " + Words.many(this) + " and late payments");
        notify.setTextSize(15);
        notify.setTextColor(Ui.DARK);
        notify.setChecked(Prefs.notify(this));
        notify.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        f.addView(notify);
        f.addView(Ui.label(this, "Remind me at"));
        String[] hours = new String[16];
        for (int i = 0; i < hours.length; i++) hours[i] = Dates.time((i + 7) * 60);
        hour = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hour.setAdapter(ad);
        hour.setSelection(Math.max(0, Math.min(hours.length - 1, Prefs.notifyHour(this) - 7)));
        f.addView(hour);
        f.addView(hint("You'll get a heads-up on the day and the day before, a nudge the day after if it isn't "
                + "marked paid, and a daily overdue total."));
        Button test = Ui.quiet(this, "Show today's reminders now");
        test.setOnClickListener(v -> {
            save();
            int n = Nudges.check(this, true);
            Toast.makeText(this, n + (n == 1 ? " reminder" : " reminders") + " sent – check your notifications",
                    Toast.LENGTH_SHORT).show();
        });
        f.addView(test);

        f = group(page, "Calendar", null);
        askCal = new Switch(this);
        askCal.setText("Ask to add new " + Words.many(this) + " to your calendar");
        askCal.setTextSize(15);
        askCal.setTextColor(Ui.DARK);
        askCal.setChecked(Prefs.askCalendar(this));
        askCal.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
        f.addView(askCal);
        f.addView(hint("After you save a new " + Words.one(this) + ", choose Google Calendar (opens filled in – tap Save) "
                + "or Outlook (added straight away when you're signed in with Microsoft)."));
        }

        // ---- Backup & export ----
        if (is(BACKUP)) {
        f = group(page, "Backup (Google Drive)", "Choose a backup file once – pick Google Drive in the file picker. "
                + "After every change the app updates that file, so a lost or broken phone doesn't mean lost records.");
        backupStatus = Ui.text(this, "", 14, Ui.DARK, true);
        backupStatus.setPadding(0, Ui.dp(this, 8), 0, 0);
        f.addView(backupStatus);
        Button choose = Ui.primary(this, Backup.isSetUp(this) ? "Change backup file" : "Choose backup file");
        choose.setOnClickListener(v -> startActivityForResult(Backup.chooseFileIntent(this), REQ_BACKUP_FILE));
        f.addView(choose);
        Button now = Ui.tonal(this, "Back up now");
        now.setOnClickListener(v -> backupNow());
        f.addView(now);
        Button restore = Ui.tonal(this, "Restore from a backup file");
        restore.setOnClickListener(v -> startActivityForResult(Backup.restoreIntent(), REQ_RESTORE));
        f.addView(restore);

        f = group(page, "Export to spreadsheet", null);
        Button eg = Ui.quiet(this, "Export " + Words.many(this) + " (CSV for Excel)");
        eg.setOnClickListener(v -> startActivityForResult(csvIntent("gigs"), REQ_EXPORT_GIGS));
        f.addView(eg);
        Button ee = Ui.quiet(this, "Export expenses (CSV for Excel)");
        ee.setOnClickListener(v -> startActivityForResult(csvIntent("expenses"), REQ_EXPORT_EXPENSES));
        f.addView(ee);
        }

        setContentView(scroll);
        Theme.bars(this);
    }

    /** A titled card on the settings page; returns the card to add fields to. */
    private LinearLayout group(LinearLayout page, String title, String subtitle) {
        if (title != null) {
            TextView t = Ui.overline(this, title);
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 8));
            page.addView(t);
        } else {
            android.view.View gap = new android.view.View(this);
            page.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(this, 12)));
        }
        LinearLayout card = Ui.vbox(this, 16);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 16));
        card.setBackground(Ui.outlined(this, Ui.SURFACE, 16));
        if (subtitle != null) {
            TextView s = Ui.text(this, subtitle, 13, Ui.GREY, false);
            s.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(s);
        }
        page.addView(card);
        return card;
    }

    private TextView hint(String s) {
        TextView t = Ui.text(this, s, 12, Ui.GREY, false);
        t.setPadding(0, Ui.dp(this, 8), 0, 0);
        return t;
    }

    private void refreshHints() {
        int i = Words.index(this);
        fields.get(Prefs.TAB_TITLE).setHint(Words.cap(Words.presetMany(i)));
        fields.get(Prefs.WORD_ONE).setHint(Words.presetOne(i));
        fields.get(Prefs.WORD_MANY).setHint(Words.presetMany(i));
        fields.get(Prefs.INVOICE_LINE).setHint(Words.presetLine(i));
    }

    private void showLogo() {
        boolean has = Logo.exists(this);
        logoPreview.setImageBitmap(has ? Logo.bitmap(this) : null);
        logoPreview.setVisibility(has ? android.view.View.VISIBLE : android.view.View.GONE);
        logoButton.setText(has ? "Change logo" : "Add your logo");
        logoRemove.setVisibility(has ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    /** Row of round colour swatches; the chosen one has a ring. */
    static LinearLayout accentPicker(Activity a, int selected, java.util.function.IntConsumer onPick) {
        LinearLayout row = Ui.hbox(a);
        row.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        for (int i = 0; i < Theme.ACCENT_KEYS.length; i++) {
            final int idx = i;
            android.widget.FrameLayout cell = new android.widget.FrameLayout(a);
            android.view.View dot = new android.view.View(a);
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            d.setColor(Theme.strong(i));
            if (i == selected) d.setStroke(Ui.dp(a, 3), Ui.SURFACE);
            dot.setBackground(d);
            int s = Ui.dp(a, 34);
            android.widget.FrameLayout.LayoutParams dl = new android.widget.FrameLayout.LayoutParams(s, s, android.view.Gravity.CENTER);
            cell.addView(dot, dl);
            if (i == selected) {
                android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
                ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                ring.setStroke(Ui.dp(a, 2), Theme.strong(i));
                cell.setBackground(ring);
            }
            cell.setContentDescription(Theme.ACCENT_NAMES[i]);
            cell.setOnClickListener(v -> onPick.accept(idx));
            int cs = Ui.dp(a, 42);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cs, cs);
            lp.rightMargin = Ui.dp(a, 4);
            row.addView(cell, lp);
        }
        return row;
    }

    private void add(LinearLayout f, String key, String label, int type) {
        fields.put(key, Ui.field(f, label, Prefs.get(this, key), type));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (section == null) {
            menu();
            return;
        }
        if (backupStatus != null) backupStatus.setText(Backup.status(this));
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    /** Saves whatever is on this page (each page only has some of the settings). */
    private void save() {
        if (section == null) return;
        for (Map.Entry<String, EditText> e : fields.entrySet()) {
            Prefs.set(this, e.getKey(), e.getValue().getText().toString());
        }
        android.content.SharedPreferences.Editor ed = Prefs.sp(this).edit();
        if (currency != null) ed.putString(Prefs.CURRENCY, Money.CODES[currency.getSelectedItemPosition()]);
        if (terms != null) {
            int t = 0;
            try { t = Integer.parseInt(terms.getText().toString().trim()); } catch (NumberFormatException ignored) { }
            ed.putInt(Prefs.TERMS, Math.max(0, Math.min(365, t)));
        }
        if (nextInvoice != null) {
            int n = 1;
            try { n = Integer.parseInt(nextInvoice.getText().toString().trim()); } catch (NumberFormatException ignored) { }
            ed.putInt(Prefs.NEXT_INVOICE, Math.max(1, n));
        }
        if (notify != null) ed.putBoolean(Prefs.NOTIFY, notify.isChecked());
        if (askCal != null) ed.putBoolean(Prefs.ASK_CALENDAR, askCal.isChecked());
        if (hour != null) ed.putInt(Prefs.NOTIFY_HOUR, hour.getSelectedItemPosition() + 7);
        ed.apply();
        Nudges.schedule(this);
        Backup.schedule(this);
        Theme.changed(this); // wording, currency or name may have changed: home screen redraws
    }

    static String titleOf(String key) {
        switch (key) {
            case WORK: return "Your work";
            case LOOK: return "Appearance";
            case BUSINESS: return "Business details";
            case PAY: return "Getting paid";
            case EMAIL_S: return "Email";
            case REMIND: return "Reminders & calendar";
            default: return "Backup & export";
        }
    }

    /** The Settings menu: one tidy row per topic, each with a summary of what's set now. */
    private void menu() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.vbox(this, 16);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(page);

        int idx = Words.index(this);
        String mode = Prefs.get(this, Prefs.THEME_MODE);
        int modeIdx = java.util.Arrays.asList(Theme.MODE_KEYS).indexOf(mode.isEmpty() ? Theme.MODE_LIGHT : mode);
        String name = Prefs.get(this, Prefs.NAME);
        int terms = Prefs.termsDays(this);
        String cur = Money.NAMES[Money.index(this)];

        LinearLayout g = menuGroup(page, "Personalise");
        menuRow(g, R.drawable.ic_set_work, "Your work", Words.NAMES[idx] + " · \u201c" + Words.many(this) + "\u201d", WORK, false);
        menuRow(g, R.drawable.ic_set_look, "Appearance",
                Theme.ACCENT_NAMES[Theme.accentIndex(this)] + " · " + Theme.MODE_NAMES[Math.max(0, modeIdx)], LOOK, true);

        g = menuGroup(page, "Business");
        menuRow(g, R.drawable.ic_set_business, "Business details & logo",
                (name.isEmpty() ? "Add your name for invoices" : name) + (Logo.exists(this) ? " · logo added" : ""), BUSINESS, false);
        menuRow(g, R.drawable.ic_set_pay, "Getting paid",
                cur + " · " + (terms == 0 ? "paid " + Words.onTheNight(this) : "due in " + terms + " days")
                        + (Prefs.hasPaymentDetails(this) ? "" : " · add bank details"), PAY, false);
        menuRow(g, R.drawable.ic_set_email, "Email",
                Mail.isConnected(this) ? "Sending from " + Mail.email(this)
                        : Google.emailAppName(this).isEmpty() ? "Choose an app each time" : "Opens in " + Google.emailAppName(this),
                EMAIL_S, true);

        g = menuGroup(page, "App");
        menuRow(g, R.drawable.ic_set_bell, "Reminders & calendar",
                (Prefs.notify(this) ? "Daily at " + Dates.time(Prefs.notifyHour(this) * 60) : "Off")
                        + (Prefs.askCalendar(this) ? " · calendar prompt on" : ""), REMIND, false);
        menuRow(g, R.drawable.ic_set_backup, "Backup & export",
                Backup.isSetUp(this) ? "Backing up automatically" : "Not set up – your data is only on this phone", BACKUP, true);

        TextView ver = Ui.text(this, getString(R.string.app_name) + " · version " + version(), 12, Ui.GREY, false);
        ver.setGravity(android.view.Gravity.CENTER);
        ver.setPadding(0, Ui.dp(this, 24), 0, 0);
        page.addView(ver);
        setContentView(scroll);
        Theme.bars(this);
    }

    private String version() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private LinearLayout menuGroup(LinearLayout page, String title) {
        TextView t = Ui.overline(this, title);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 20), 0, Ui.dp(this, 8));
        page.addView(t);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outlined(this, Ui.SURFACE, 16));
        card.setClipToOutline(true);
        page.addView(card);
        return card;
    }

    private void menuRow(LinearLayout card, int icon, String title, String summary, String key, boolean last) {
        LinearLayout row = Ui.hbox(this);
        int h = Ui.dp(this, 16), v = Ui.dp(this, 14);
        row.setPadding(h, v, Ui.dp(this, 10), v);
        android.widget.FrameLayout badge = new android.widget.FrameLayout(this);
        badge.setBackground(Ui.rounded(this, Ui.PRIMARY_LIGHT, 12));
        ImageView ic = Ui.icon(this, icon, Ui.PRIMARY, 20);
        badge.addView(ic, new android.widget.FrameLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20), android.view.Gravity.CENTER));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        blp.rightMargin = Ui.dp(this, 14);
        row.addView(badge, blp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(Ui.text(this, title, 16, Ui.DARK, true));
        TextView sum = Ui.text(this, summary, 13,
                key.equals(BACKUP) && !Backup.isSetUp(this) ? Ui.ORANGE : Ui.GREY, false);
        sum.setMaxLines(2);
        sum.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(sum);
        row.addView(texts, Ui.weight(1f));
        row.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.FAINT, 22));
        row.setBackground(Ui.touchable(null, Ui.ripple()));
        row.setOnClickListener(x -> open(this, key));
        card.addView(row);
        if (!last) {
            android.view.View line = new android.view.View(this);
            line.setBackgroundColor(Ui.LINE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1) / 2));
            lp.leftMargin = Ui.dp(this, 70);
            card.addView(line, lp);
        }
    }

    private void backupNow() {
        save();
        backupStatus.setText("Backing up…");
        new Thread(() -> {
            String err = Backup.writeNow(this);
            runOnUiThread(() -> {
                backupStatus.setText(Backup.status(this));
                Toast.makeText(this, err == null ? "Backed up ✓" : "Backup failed: " + err, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------- Files ----------------

    private Intent csvIntent(String what) {
        return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE, getString(R.string.app_name).toLowerCase() + "-" + what + "-" + Dates.iso(Dates.today()) + ".csv");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (GoogleAccount.handleResult(this, requestCode, resultCode, data)) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        switch (requestCode) {
            case REQ_BACKUP_FILE:
                Backup.setFile(this, uri);
                backupNow();
                break;
            case REQ_LOGO:
                try {
                    Logo.save(this, uri);
                    showLogo();
                } catch (Exception e) {
                    Toast.makeText(this, "Couldn't use that picture: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                break;
            case REQ_RESTORE:
                restore(uri);
                break;
            case REQ_EXPORT_GIGS:
                writeText(uri, gigsCsv());
                break;
            case REQ_EXPORT_EXPENSES:
                writeText(uri, expensesCsv());
                break;
            default:
                break;
        }
    }

    private void restore(Uri uri) {
        JSONObject root;
        try {
            root = Backup.read(this, uri);
            if (!root.has("gigs")) throw new Exception("this isn't a backup file from this app");
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't read that file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        int gigs = root.optJSONArray("gigs") == null ? 0 : root.optJSONArray("gigs").length();
        final JSONObject backup = root;
        new AlertDialog.Builder(this)
                .setTitle("Restore this backup?")
                .setMessage("Backup from " + root.optString("exported", "unknown date") + " with "
                        + Words.count(this, gigs) + ".\n\nEverything currently in the app will be replaced.")
                .setPositiveButton("Restore", (d, w) -> {
                    try {
                        Db.get(this).importAll(backup);
                        Toast.makeText(this, "Restored ✓", Toast.LENGTH_SHORT).show();
                        recreate();
                    } catch (Exception e) {
                        Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void writeText(Uri uri, String text) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("Could not open file");
            out.write(text.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Exported ✓", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String csv(String s) {
        return s == null ? "" : "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private String gigsCsv() {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Client,Email,Event,Date,Start,Booking status,Due date,Fee,Paid,Owed,Payment status,Invoice,Notes\n");
        for (Gig g : Db.get(this).allGigs()) {
            String st = g.isCancelled() ? "Cancelled" : g.isFuture() ? "Upcoming" : g.isPaid() ? "Paid"
                    : g.isOverdue() ? "Overdue" : "Unpaid";
            sb.append(csv(g.client)).append(',').append(csv(g.email)).append(',').append(csv(g.event)).append(',')
                    .append(Dates.iso(g.gigDay)).append(',')
                    .append(g.startMin >= 0 ? Dates.time(g.startMin) : "").append(',')
                    .append(Gig.STATUS_NAMES[g.status]).append(',')
                    .append(Dates.iso(g.dueDay)).append(',')
                    .append(Money.plain(g.feeCents)).append(',')
                    .append(Money.plain(g.paidCents)).append(',')
                    .append(Money.plain(g.isCancelled() ? 0 : g.balance())).append(',')
                    .append(st).append(',').append(csv(g.invoiceNo)).append(',').append(csv(g.notes)).append('\n');
        }
        return sb.toString();
    }

    private String expensesCsv() {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Date,Category,Note,Amount,Booking\n");
        Db db = Db.get(this);
        long from = LocalDate.of(1970, 1, 1).toEpochDay(), to = LocalDate.of(2200, 1, 1).toEpochDay();
        for (Expense e : db.expensesBetween(from, to)) {
            String gig = "";
            if (e.gigId > 0) {
                Gig g = db.gig(e.gigId);
                if (g != null) gig = g.title() + " " + Dates.iso(g.gigDay);
            }
            sb.append(Dates.iso(e.day)).append(',').append(csv(e.category)).append(',').append(csv(e.note)).append(',')
                    .append(Money.plain(e.cents)).append(',').append(csv(gig)).append('\n');
        }
        return sb.toString();
    }
}
