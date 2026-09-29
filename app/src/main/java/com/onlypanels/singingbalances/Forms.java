package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.time.LocalDate;
import java.util.function.LongConsumer;

/** Pop-up forms for adding/editing gigs and recording payments. */
final class Forms {
    /** Default payment terms for a new gig: due this many days after the gig. */
    static final int DEFAULT_TERMS_DAYS = 14;

    private static final int TEXT_WORDS = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int TEXT_SENTENCE = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int EMAIL = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    private static final int MONEY = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    private static final int NOTES = TEXT_SENTENCE | InputType.TYPE_TEXT_FLAG_MULTI_LINE;

    interface Saved {
        void onSaved(long gigId);
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

    /** existing == null means a new gig. */
    static void editGig(Activity a, Gig existing, Saved onSaved) {
        final boolean isNew = existing == null;
        final Gig g = isNew ? new Gig() : existing;
        if (isNew) {
            g.gigDay = Dates.today();
            g.dueDay = g.gigDay + DEFAULT_TERMS_DAYS;
        }
        final long[] gigDay = {g.gigDay};
        final long[] dueDay = {g.dueDay};
        final boolean[] dueChosen = {!isNew};

        LinearLayout form = Ui.vbox(a, 20);
        EditText client = Ui.field(form, "Client (who pays you) *", g.client, TEXT_WORDS);
        EditText email = Ui.field(form, "Client email (for payment reminders)", g.email, EMAIL);
        EditText event = Ui.field(form, "Event / venue", g.event, TEXT_SENTENCE);
        EditText fee = Ui.field(form, "Fee (" + Money.SYMBOL + ") *", isNew ? "" : Money.plain(g.feeCents), MONEY);
        Button gigBtn = Ui.dateButton(form, "Gig date");
        Button dueBtn = Ui.dateButton(form, "Payment due by");
        EditText notes = Ui.field(form, "Notes", g.notes, NOTES);

        Runnable refresh = () -> {
            gigBtn.setText(Dates.fmt(gigDay[0]));
            dueBtn.setText(Dates.fmt(dueDay[0]));
        };
        refresh.run();

        gigBtn.setOnClickListener(v -> pickDate(a, gigDay[0], d -> {
            gigDay[0] = d;
            if (!dueChosen[0]) dueDay[0] = d + DEFAULT_TERMS_DAYS;
            refresh.run();
        }));
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
            g.dueDay = dueDay[0];
            long id = Db.get(a).save(g);
            dialog.dismiss();
            onSaved.onSaved(id);
        }));
        dialog.show();
    }

    static void recordPayment(Activity a, Gig g, Runnable onSaved) {
        final long[] day = {Dates.today()};
        LinearLayout form = Ui.vbox(a, 20);
        EditText amount = Ui.field(form, "Amount received (" + Money.SYMBOL + ")", Money.plain(g.balance()), MONEY);
        Button dayBtn = Ui.dateButton(form, "Date received");
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
}
