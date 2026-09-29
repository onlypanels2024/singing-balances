package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.function.LongConsumer;

/** Pop-up forms: gigs, payments, expenses, clients. */
final class Forms {
    private static final int TEXT_WORDS = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int TEXT_SENTENCE = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int EMAIL = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    private static final int PHONE = InputType.TYPE_CLASS_PHONE;
    private static final int MONEY = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    private static final int NOTES = TEXT_SENTENCE | InputType.TYPE_TEXT_FLAG_MULTI_LINE;

    interface Saved {
        void onSaved(long id);
    }

    private Forms() {}

    static void pickDate(Activity a, long day, LongConsumer onPicked) {
        LocalDate d = LocalDate.ofEpochDay(day);
        new DatePickerDialog(a,
                (view, y, m, dd) -> onPicked.accept(LocalDate.of(y, m + 1, dd).toEpochDay()),
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private static ScrollView wrap(LinearLayout form) {
        ScrollView s = new ScrollView(form.getContext());
        s.addView(form);
        return s;
    }

    private static Spinner spinner(LinearLayout form, String label, String[] options, int selected) {
        form.addView(Ui.label(form.getContext(), label));
        Spinner s = new Spinner(form.getContext());
        ArrayAdapter<String> ad = new ArrayAdapter<>(form.getContext(), android.R.layout.simple_spinner_item, options);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(ad);
        s.setSelection(Math.max(0, selected));
        form.addView(s);
        return s;
    }

    private static String dueLabel(long gigDay, long dueDay) {
        return dueDay == gigDay ? Dates.fmt(dueDay) + " (on the night)" : Dates.fmt(dueDay);
    }

    /** existing == null means a new gig; presetDay is used for new gigs (0 = today). */
    static void editGig(Activity a, Gig existing, long presetDay, Saved onSaved) {
        final boolean isNew = existing == null || existing.id == 0;
        final Gig g = existing == null ? new Gig() : existing;
        final int terms = Prefs.termsDays(a);
        if (isNew) {
            g.gigDay = presetDay > 0 ? presetDay : g.gigDay > 0 ? g.gigDay : Dates.today();
            g.dueDay = g.gigDay + terms;
        }
        final long[] gigDay = {g.gigDay};
        final long[] dueDay = {g.dueDay};
        final int[] start = {g.startMin};
        final boolean[] dueChosen = {!isNew && g.dueDay != g.gigDay + terms};
        Db db = Db.get(a);

        LinearLayout form = Ui.vbox(a, 20);
        form.addView(Ui.label(a, "Client (who pays you) *"));
        AutoCompleteTextView client = new AutoCompleteTextView(a);
        client.setInputType(TEXT_WORDS);
        client.setSingleLine(true);
        client.setThreshold(1);
        client.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_dropdown_item_1line, db.clientNames()));
        client.setText(g.client);
        form.addView(client);
        EditText email = Ui.field(form, "Client email (for invoices & reminders)", g.email, EMAIL);
        client.setOnItemClickListener((p, v, pos, id) -> {
            Client k = db.client(client.getText().toString());
            if (k != null && !k.email.isEmpty()) email.setText(k.email);
        });

        EditText event = Ui.field(form, "Event / venue", g.event, TEXT_SENTENCE);
        Button dayBtn = Ui.pickerButton(form, "Gig date");
        Button timeBtn = Ui.pickerButton(form, "Start time");
        Spinner status = spinner(form, "Booking", Gig.STATUS_NAMES, g.status);
        EditText fee = Ui.field(form, "Fee (" + Money.SYMBOL + ") *", isNew ? "" : Money.plain(g.feeCents), MONEY);
        Button dueBtn = Ui.pickerButton(form, "Payment due");
        EditText notes = Ui.field(form, "Notes (songs, contact on the night, parking...)", g.notes, NOTES);

        Runnable refresh = () -> {
            dayBtn.setText(Dates.withWeekday(gigDay[0]));
            timeBtn.setText(start[0] >= 0 ? Dates.time(start[0]) : "Not set");
            dueBtn.setText(dueLabel(gigDay[0], dueDay[0]));
        };
        refresh.run();

        dayBtn.setOnClickListener(v -> pickDate(a, gigDay[0], d -> {
            gigDay[0] = d;
            if (!dueChosen[0]) dueDay[0] = d + terms;
            refresh.run();
        }));
        timeBtn.setOnClickListener(v -> {
            int m = start[0] >= 0 ? start[0] : 20 * 60;
            TimePickerDialog t = new TimePickerDialog(a, (view, hh, mm) -> {
                start[0] = hh * 60 + mm;
                refresh.run();
            }, m / 60, m % 60, true);
            t.setButton(TimePickerDialog.BUTTON_NEUTRAL, "No time", (d, w) -> {
                start[0] = -1;
                refresh.run();
            });
            t.show();
        });
        dueBtn.setOnClickListener(v -> pickDate(a, dueDay[0], d -> {
            dueDay[0] = d;
            dueChosen[0] = true;
            refresh.run();
        }));

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle(isNew ? "New gig" : "Edit gig")
                .setView(wrap(form))
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = client.getText().toString().trim();
            Long cents = Money.parse(fee.getText().toString());
            if (name.isEmpty()) {
                client.setError("Who pays you for this gig?");
                return;
            }
            if (cents == null || cents <= 0) {
                fee.setError("Enter the fee, e.g. 250 or 250.50");
                return;
            }
            g.client = name;
            g.email = email.getText().toString().trim();
            g.event = event.getText().toString().trim();
            g.notes = notes.getText().toString().trim();
            g.feeCents = cents;
            g.gigDay = gigDay[0];
            g.startMin = start[0];
            g.dueDay = dueDay[0];
            g.status = status.getSelectedItemPosition();
            long id = db.save(g);
            dialog.dismiss();
            onSaved.onSaved(id);
        }));
        dialog.show();
    }

    static void recordPayment(Activity a, Gig g, Runnable onSaved) {
        final long[] day = {Dates.today()};
        LinearLayout form = Ui.vbox(a, 20);
        EditText amount = Ui.field(form, "Amount received (" + Money.SYMBOL + ")", Money.plain(g.balance()), MONEY);
        Button dayBtn = Ui.pickerButton(form, "Date received");
        EditText note = Ui.field(form, "How was it paid? (cash, bank transfer, Revolut...)", "", TEXT_SENTENCE);
        dayBtn.setText(Dates.fmt(day[0]));
        dayBtn.setOnClickListener(v -> pickDate(a, day[0], d -> {
            day[0] = d;
            dayBtn.setText(Dates.fmt(d));
        }));

        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Record payment")
                .setMessage("Still owed: " + Money.fmt(g.balance()))
                .setView(wrap(form))
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Long cents = Money.parse(amount.getText().toString());
            if (cents == null || cents <= 0) {
                amount.setError("Enter the amount received");
                return;
            }
            Db.get(a).addPayment(g.id, cents, day[0], note.getText().toString().trim());
            dialog.dismiss();
            onSaved.run();
        }));
        dialog.show();
    }

    /** existing == null for a new expense; gig may be null (not linked to a gig). */
    static void editExpense(Activity a, Expense existing, Gig gig, Runnable onSaved) {
        final boolean isNew = existing == null;
        final Expense e = isNew ? new Expense() : existing;
        if (isNew) {
            e.day = gig != null ? Math.min(gig.gigDay, Dates.today()) : Dates.today();
            if (gig != null) e.gigId = gig.id;
        }
        final long[] day = {e.day};
        LinearLayout form = Ui.vbox(a, 20);
        if (gig != null) form.addView(Ui.text(a, "For: " + gig.title() + " (" + Dates.fmt(gig.gigDay) + ")", 14, Ui.GREY, false));
        EditText amount = Ui.field(form, "Amount (" + Money.SYMBOL + ") *", isNew ? "" : Money.plain(e.cents), MONEY);
        int cat = Arrays.asList(Expense.CATEGORIES).indexOf(e.category);
        Spinner category = spinner(form, "Category", Expense.CATEGORIES, cat < 0 ? Expense.CATEGORIES.length - 1 : cat);
        Button dayBtn = Ui.pickerButton(form, "Date");
        EditText note = Ui.field(form, "Note (what was it?)", e.note, TEXT_SENTENCE);
        dayBtn.setText(Dates.fmt(day[0]));
        dayBtn.setOnClickListener(v -> pickDate(a, day[0], d -> {
            day[0] = d;
            dayBtn.setText(Dates.fmt(d));
        }));

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(isNew ? "New expense" : "Edit expense")
                .setView(wrap(form))
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (!isNew) {
            b.setNeutralButton("Delete", (d, w) -> {
                Db.get(a).deleteExpense(e.id);
                onSaved.run();
            });
        }
        AlertDialog dialog = b.create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Long cents = Money.parse(amount.getText().toString());
            if (cents == null || cents <= 0) {
                amount.setError("Enter the amount");
                return;
            }
            e.cents = cents;
            e.day = day[0];
            e.category = Expense.CATEGORIES[category.getSelectedItemPosition()];
            e.note = note.getText().toString().trim();
            Db.get(a).saveExpense(e);
            dialog.dismiss();
            onSaved.run();
        }));
        dialog.show();
    }

    /** existing == null for a new client. */
    static void editClient(Activity a, Client existing, Saved onSaved, Runnable onDeleted) {
        final boolean isNew = existing == null || existing.id == 0;
        final Client k = existing == null ? new Client() : existing;
        final String oldName = k.name;
        LinearLayout form = Ui.vbox(a, 20);
        EditText name = Ui.field(form, "Name (person, hotel, agency...) *", k.name, TEXT_WORDS);
        EditText email = Ui.field(form, "Email", k.email, EMAIL);
        EditText phone = Ui.field(form, "Phone", k.phone, PHONE);
        EditText notes = Ui.field(form, "Notes", k.notes, NOTES);

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(isNew ? "New client" : "Edit client")
                .setView(wrap(form))
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (!isNew && onDeleted != null) {
            b.setNeutralButton("Delete", (d, w) -> new AlertDialog.Builder(a)
                    .setTitle("Delete " + k.name + "?")
                    .setMessage("Their gigs stay in the app; only the saved contact details are removed.")
                    .setPositiveButton("Delete", (d2, w2) -> {
                        Db.get(a).deleteClient(k.id);
                        onDeleted.run();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        }
        AlertDialog dialog = b.create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setError("Enter a name");
                return;
            }
            k.name = n;
            k.email = email.getText().toString().trim();
            k.phone = phone.getText().toString().trim();
            k.notes = notes.getText().toString().trim();
            if (!Db.get(a).saveClient(k, isNew ? null : oldName)) {
                name.setError("You already have a client with this name");
                return;
            }
            dialog.dismiss();
            onSaved.onSaved(k.id);
        }));
        dialog.show();
    }
}
