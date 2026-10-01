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
    private Switch notify;
    private Spinner hour;
    private TextView backupStatus;
    private Spinner profession, currency;
    private ImageView logoPreview;
    private Button logoButton, logoRemove;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.apply(this);
        super.onCreate(savedInstanceState);
        setTitle("Settings");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.vbox(this, 16);
        page.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(page);

        // ---- Your work ----
        LinearLayout f = group(page, "Your work", "Choose what you do and the app uses your words everywhere.");
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

        // ---- Look ----
        LinearLayout look = group(page, "Look", "Pick a colour and light or dark mode. Your invoices use the colour too.");
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
        look.addView(Ui.label(this, "Currency"));
        currency = new Spinner(this);
        ArrayAdapter<String> ca = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Money.NAMES);
        ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        currency.setAdapter(ca);
        currency.setSelection(Money.index(this));
        look.addView(currency);
        look.addView(hint("Changing currency only changes the symbol – amounts you've already logged aren't converted."));

        // ---- Your details ----
        f = group(page, "Your details", "Shown on your invoices and emails.");
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

        // ---- Email ----
        f = group(page, "Email", "Which app opens when you send an invoice or a payment reminder. "
                + "You always see the email first and tap Send yourself.");
        f.addView(Ui.label(this, "Send emails with"));
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

        // ---- Payment ----
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

        f = group(page, "Reminders", null);
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

        setContentView(scroll);
        Theme.bars(this);
    }

    /** A titled card on the settings page; returns the card to add fields to. */
    private LinearLayout group(LinearLayout page, String title, String subtitle) {
        TextView t = Ui.overline(this, title);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), 0, Ui.dp(this, 8));
        page.addView(t);
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
        backupStatus.setText(Backup.status(this));
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        for (Map.Entry<String, EditText> e : fields.entrySet()) {
            Prefs.set(this, e.getKey(), e.getValue().getText().toString());
        }
        Prefs.set(this, Prefs.CURRENCY, Money.CODES[currency.getSelectedItemPosition()]);
        int t = 0, n = 1;
        try { t = Integer.parseInt(terms.getText().toString().trim()); } catch (NumberFormatException ignored) { }
        try { n = Integer.parseInt(nextInvoice.getText().toString().trim()); } catch (NumberFormatException ignored) { }
        Prefs.sp(this).edit()
                .putInt(Prefs.TERMS, Math.max(0, Math.min(365, t)))
                .putInt(Prefs.NEXT_INVOICE, Math.max(1, n))
                .putBoolean(Prefs.NOTIFY, notify.isChecked())
                .putInt(Prefs.NOTIFY_HOUR, hour.getSelectedItemPosition() + 7)
                .apply();
        Nudges.schedule(this);
        Backup.schedule(this);
        Theme.changed(this); // wording, currency or name may have changed: home screen redraws
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
