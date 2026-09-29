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
    private static final int REQ_BACKUP_FILE = 10, REQ_RESTORE = 11, REQ_EXPORT_GIGS = 12, REQ_EXPORT_EXPENSES = 13;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int PLAIN = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
    private static final int NUMBER = InputType.TYPE_CLASS_NUMBER;

    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private EditText terms, nextInvoice;
    private Switch notify;
    private Spinner hour;
    private TextView backupStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Settings & backup");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        ScrollView scroll = new ScrollView(this);
        LinearLayout f = Ui.vbox(this, 16);
        scroll.addView(f);

        f.addView(Ui.text(this, "Your details", 18, Ui.PRIMARY, true));
        f.addView(Ui.text(this, "Shown on your invoices and emails.", 13, Ui.GREY, false));
        add(f, Prefs.NAME, "Your name / stage name", TEXT);
        add(f, Prefs.EMAIL, "Your email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        add(f, Prefs.PHONE, "Your phone", InputType.TYPE_CLASS_PHONE);
        add(f, Prefs.ADDRESS, "Address (optional)", TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        add(f, Prefs.VAT, "VAT number (if you have one)", PLAIN);

        f.addView(Ui.section(this, "How clients pay you"));
        f.addView(Ui.text(this, "Added to invoices and payment reminders automatically.", 13, Ui.GREY, false));
        add(f, Prefs.BANK_NAME, "Account holder name (if different)", TEXT);
        add(f, Prefs.IBAN, "IBAN", PLAIN);
        add(f, Prefs.BIC, "BIC / SWIFT (optional)", PLAIN);
        add(f, Prefs.REVOLUT, "Revolut @tag or payment link (optional)", InputType.TYPE_CLASS_TEXT);

        f.addView(Ui.section(this, "Invoices & payment terms"));
        add(f, Prefs.INVOICE_PREFIX, "Invoice number starts with", PLAIN);
        if (Prefs.get(this, Prefs.INVOICE_PREFIX).isEmpty()) fields.get(Prefs.INVOICE_PREFIX).setText("INV-");
        nextInvoice = Ui.field(f, "Next invoice number", String.valueOf(Prefs.sp(this).getInt(Prefs.NEXT_INVOICE, 1)), NUMBER);
        terms = Ui.field(f, "Payment due (days after the gig) – 0 means paid on the night",
                String.valueOf(Prefs.termsDays(this)), NUMBER);

        f.addView(Ui.section(this, "Reminders"));
        notify = new Switch(this);
        notify.setText("Daily reminders about gigs and late payments");
        notify.setTextSize(15);
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
        f.addView(Ui.text(this, "You'll get a heads-up on gig days and the day before, a nudge the day after a gig "
                + "if it isn't marked paid, and a daily overdue total.", 13, Ui.GREY, false));
        Button test = Ui.button(this, "Show today's reminders now", Ui.LIGHT_GREY, Ui.DARK);
        test.setOnClickListener(v -> {
            save();
            int n = Nudges.check(this, true);
            Toast.makeText(this, n + (n == 1 ? " reminder" : " reminders") + " sent – check your notifications",
                    Toast.LENGTH_SHORT).show();
        });
        f.addView(test);

        f.addView(Ui.section(this, "Backup (OneDrive)"));
        f.addView(Ui.text(this, "Choose a backup file once – pick OneDrive in the file picker. After every change the "
                + "app updates that file, so a lost or broken phone doesn't mean lost records.", 13, Ui.GREY, false));
        backupStatus = Ui.text(this, "", 14, Ui.DARK, true);
        backupStatus.setPadding(0, Ui.dp(this, 8), 0, 0);
        f.addView(backupStatus);
        Button choose = Ui.button(this, Backup.isSetUp(this) ? "Change backup file" : "Choose backup file",
                Ui.PRIMARY, Ui.WHITE);
        choose.setOnClickListener(v -> startActivityForResult(Backup.chooseFileIntent(), REQ_BACKUP_FILE));
        f.addView(choose);
        Button now = Ui.button(this, "Back up now", Ui.PRIMARY_LIGHT, Ui.PRIMARY);
        now.setOnClickListener(v -> backupNow());
        f.addView(now);
        Button restore = Ui.button(this, "Restore from a backup file", Ui.PRIMARY_LIGHT, Ui.PRIMARY);
        restore.setOnClickListener(v -> startActivityForResult(Backup.restoreIntent(), REQ_RESTORE));
        f.addView(restore);

        f.addView(Ui.section(this, "Export to spreadsheet"));
        Button eg = Ui.button(this, "Export gigs (CSV for Excel)", Ui.LIGHT_GREY, Ui.DARK);
        eg.setOnClickListener(v -> startActivityForResult(csvIntent("gigs"), REQ_EXPORT_GIGS));
        f.addView(eg);
        Button ee = Ui.button(this, "Export expenses (CSV for Excel)", Ui.LIGHT_GREY, Ui.DARK);
        ee.setOnClickListener(v -> startActivityForResult(csvIntent("expenses"), REQ_EXPORT_EXPENSES));
        f.addView(ee);

        setContentView(scroll);
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
                .putExtra(Intent.EXTRA_TITLE, "singing-" + what + "-" + Dates.iso(Dates.today()) + ".csv");
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
            if (!root.has("gigs")) throw new Exception("this isn't a Singing backup file");
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't read that file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        int gigs = root.optJSONArray("gigs") == null ? 0 : root.optJSONArray("gigs").length();
        final JSONObject backup = root;
        new AlertDialog.Builder(this)
                .setTitle("Restore this backup?")
                .setMessage("Backup from " + root.optString("exported", "unknown date") + " with " + gigs
                        + " gigs.\n\nEverything currently in the app will be replaced.")
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
        sb.append("Client,Email,Event,Gig date,Start,Booking,Due date,Fee,Paid,Owed,Status,Invoice,Notes\n");
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
        sb.append("Date,Category,Note,Amount,Gig\n");
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
