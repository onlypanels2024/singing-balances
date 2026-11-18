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
import android.widget.TextView;
import android.content.Context;
import java.util.List;

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

    private static String dueLabel(Activity a, long gigDay, long dueDay) {
        return dueDay == gigDay ? Dates.fmt(dueDay) + " (" + Words.onTheNight(a) + ")" : Dates.fmt(dueDay);
    }

    /** "You already have Bob's Bar at 21:00 on this day" – or "" when the day is free. */
    static String clash(Context c, long day, long exceptId) {
        List<Gig> l = Db.get(c).sameDay(day, exceptId);
        if (l.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < 3; i++) {
            Gig o = l.get(i);
            if (i > 0) sb.append(i == l.size() - 1 ? " and " : ", ");
            sb.append(o.client).append(o.startMin >= 0 ? " at " + Dates.time(o.startMin) : "");
            if (o.status == Gig.PENCILLED) sb.append(" (pencilled in)");
        }
        if (l.size() > 3) sb.append(" and ").append(l.size() - 3).append(" more");
        return "⚠  You already have " + sb + " on this day";
    }

    /** existing == null means a new gig; presetDay is used for new gigs (0 = today). */
    static void editGig(Activity a, Gig existing, long presetDay, Saved onSaved) {
        final boolean isNew = existing == null || existing.id == 0;
        final Gig g = existing == null ? new Gig() : existing;
        final int terms = Prefs.termsDays(a);
        if (isNew) {
            g.gigDay = presetDay > 0 ? presetDay : g.gigDay > 0 ? g.gigDay : Dates.today();
            g.dueDay = g.gigDay + terms;
            g.vatBp = Prefs.vatBp(a);
            g.vatIncl = Prefs.vatIncluded(a);
        }
        final long[] gigDay = {g.gigDay};
        final long[] dueDay = {g.dueDay};
        final int[] start = {g.startMin};
        final boolean[] dueChosen = {!isNew && g.dueDay != g.gigDay + terms};
        final java.time.YearMonth wasMonth = isNew ? null : java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(g.gigDay));
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
        Button dayBtn = Ui.pickerButton(form, Words.One(a) + " date");
        TextView clashLine = Ui.text(a, "", 13, Ui.ORANGE, true);
        clashLine.setPadding(Ui.dp(a, 4), Ui.dp(a, 2), 0, Ui.dp(a, 6));
        form.addView(clashLine);
        Button timeBtn = Ui.pickerButton(form, "Start time");
        Spinner status = spinner(form, "Booking", Gig.STATUS_NAMES, g.status);
        EditText fee = Ui.field(form, "Fee (" + Money.label() + ") *", isNew ? "" : Money.plain(g.feeCents), MONEY);

        // Extra lines on the bill (travel, extra hour...) and VAT
        form.addView(Ui.label(a, "Extras on the bill (optional)"));
        LinearLayout extrasBox = Ui.vbox(a, 0);
        form.addView(extrasBox);
        java.util.List<EditText[]> extraRows = new java.util.ArrayList<>();
        Button addExtra = Ui.quiet(a, "+ Add an extra (travel, extra hour…)");
        form.addView(addExtra, Ui.matchWrap(a, 4));
        final int[] vatBp = {g.vatBp > 0 ? g.vatBp : Prefs.parseRateBp(Prefs.get(a, Prefs.VAT_RATE)) > 0
                ? Prefs.parseRateBp(Prefs.get(a, Prefs.VAT_RATE)) : 1800};
        final boolean[] vatIncl = {g.vatBp > 0 ? g.vatIncl : Prefs.vatIncluded(a)};
        android.widget.Switch vat = new android.widget.Switch(a);
        Gig rateHolder = new Gig();
        rateHolder.vatBp = vatBp[0];
        vat.setText("Charge VAT (" + rateHolder.vatRate() + (vatIncl[0] ? ", included in the prices)" : ", added on top)"));
        vat.setTextSize(15);
        vat.setTextColor(Ui.DARK);
        vat.setChecked(g.vatBp > 0);
        vat.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 2));
        boolean showVat = g.vatBp > 0 || Prefs.vatBp(a) > 0;
        if (showVat) form.addView(vat);
        TextView totalLine = Ui.text(a, "", 14, Ui.PRIMARY, true);
        totalLine.setPadding(0, Ui.dp(a, 6), 0, Ui.dp(a, 4));
        form.addView(totalLine);
        Runnable totals = () -> {
            Gig t = new Gig();
            Long f = Money.parse(fee.getText().toString());
            t.feeCents = f == null ? 0 : f;
            for (EditText[] r : extraRows) {
                Long x = Money.parse(r[1].getText().toString());
                if (x != null) t.extrasCents += x;
            }
            t.vatBp = vat.isChecked() && showVat ? vatBp[0] : 0;
            t.vatIncl = vatIncl[0];
            boolean simple = t.extrasCents == 0 && t.vatBp == 0;
            totalLine.setText(simple ? "" : "Total for the client: " + Money.fmt(t.total())
                    + (t.vatBp > 0 ? " (VAT " + t.vatRate() + ": " + Money.fmt(t.vatCents()) + (t.vatIncl ? " included)" : ")") : ""));
            totalLine.setVisibility(simple ? android.view.View.GONE : android.view.View.VISIBLE);
        };
        android.text.TextWatcher watch = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { totals.run(); }
        };
        java.util.function.BiConsumer<String, Long> addRow = (label, cents) -> {
            LinearLayout row = Ui.hbox(a);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            EditText l = new EditText(a);
            l.setHint("e.g. Extra hour");
            l.setInputType(TEXT_SENTENCE);
            l.setSingleLine(true);
            l.setText(label);
            EditText m = new EditText(a);
            m.setHint("0.00");
            m.setInputType(MONEY);
            m.setText(cents == null ? "" : Money.plain(cents));
            m.addTextChangedListener(watch);
            Button x = new Button(a, null, android.R.attr.borderlessButtonStyle);
            x.setText("×");
            x.setTextSize(20);
            x.setTextColor(Ui.GREY);
            x.setContentDescription("Remove this extra");
            EditText[] pair = {l, m};
            x.setOnClickListener(v -> {
                extrasBox.removeView(row);
                extraRows.remove(pair);
                totals.run();
            });
            row.addView(l, Ui.weight(1.6f));
            row.addView(m, Ui.weight(1f));
            row.addView(x, new LinearLayout.LayoutParams(Ui.dp(a, 44), -2));
            extrasBox.addView(row);
            extraRows.add(pair);
        };
        if (!isNew) for (Db.Extra x : db.extras(g.id)) addRow.accept(x.label, x.cents);
        addExtra.setOnClickListener(v -> {
            addRow.accept("", null);
            extraRows.get(extraRows.size() - 1)[0].requestFocus();
        });
        vat.setOnCheckedChangeListener((b, on) -> totals.run());
        fee.addTextChangedListener(watch);
        totals.run();

        Button dueBtn = Ui.pickerButton(form, "Payment due");
        EditText notes = Ui.field(form, "Notes (contact " + Words.onTheNight(a) + ", parking, special requests...)", g.notes, NOTES);

        Runnable refresh = () -> {
            dayBtn.setText(Dates.withWeekday(gigDay[0]));
            String clash = clash(a, gigDay[0], g.id);
            clashLine.setText(clash);
            clashLine.setVisibility(clash.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
            timeBtn.setText(start[0] >= 0 ? Dates.time(start[0]) : "Not set");
            dueBtn.setText(dueLabel(a, gigDay[0], dueDay[0]));
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
                .setTitle(isNew ? "New " + Words.one(a) : "Edit " + Words.one(a))
                .setView(wrap(form))
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = client.getText().toString().trim();
            Long cents = Money.parse(fee.getText().toString());
            if (name.isEmpty()) {
                client.setError("Who pays you for this " + Words.one(a) + "?");
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
            java.util.List<Db.Extra> extras = new java.util.ArrayList<>();
            for (EditText[] r : extraRows) {
                String label = r[0].getText().toString().trim();
                String amt = r[1].getText().toString().trim();
                if (label.isEmpty() && amt.isEmpty()) continue;
                Long xc = Money.parse(amt);
                if (xc == null || xc <= 0) {
                    r[1].setError("Enter the amount, e.g. 30");
                    return;
                }
                if (label.isEmpty()) {
                    r[0].setError("What is it for?");
                    return;
                }
                extras.add(new Db.Extra(label.length() > 80 ? label.substring(0, 80) : label, xc));
            }
            g.vatBp = showVat && vat.isChecked() ? vatBp[0] : 0;
            g.vatIncl = g.vatBp > 0 && vatIncl[0];
            // Free plan: a few bookings a month. Editing an existing booking in the same month is always allowed.
            java.time.YearMonth month = java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(g.gigDay));
            if (!g.isCancelled() && !month.equals(wasMonth) && !Pro.isPro(a)
                    && Pro.bookingsInMonth(a, g.gigDay, g.id) >= Pro.FREE_BOOKINGS) {
                Pro.open(a, "The free plan includes " + Pro.FREE_BOOKINGS + " " + Words.many(a) + " a month, and "
                        + month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
                        + " already has " + Pro.FREE_BOOKINGS + ". ShowFee Pro gives you unlimited " + Words.many(a)
                        + ". (Nothing you typed is lost – this window stays open.)");
                return;
            }
            long id = db.save(g);
            db.saveExtras(g, extras);
            dialog.dismiss();
            onSaved.onSaved(id);
            if (isNew && !g.isCancelled() && Prefs.askCalendar(a) && !CalendarSync.isOn(a)) Calendar.offer(a, g, true);
        }));
        dialog.show();
    }

    static void recordPayment(Activity a, Gig g, Runnable onSaved) {
        final long[] day = {Dates.today()};
        LinearLayout form = Ui.vbox(a, 20);
        EditText amount = Ui.field(form, "Amount received (" + Money.label() + ")", Money.plain(g.balance()), MONEY);
        Button dayBtn = Ui.pickerButton(form, "Date received");
        EditText note = Ui.field(form, "How was it paid? (cash, bank transfer, card...)", "", TEXT_SENTENCE);
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
        EditText amount = Ui.field(form, "Amount (" + Money.label() + ") *", isNew ? "" : Money.plain(e.cents), MONEY);
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

    /**
     * Preview of an email ShowFee is about to send from the signed-in Google account.
     * Everything is editable; nothing goes until the user taps Send.
     */
    static void composeEmail(Activity a, String to, String subject, String body, java.io.File pdf, String payUrl,
                             Runnable useEmailApp) {
        LinearLayout form = Ui.vbox(a, 20);
        form.addView(Ui.text(a, "From " + Mail.email(a) + " (" + Mail.service(a) + ")", 13, Ui.GREY, false));
        EditText toField = Ui.field(form, "To", to, EMAIL);
        EditText subj = Ui.field(form, "Subject", subject, TEXT_SENTENCE);
        EditText msg = Ui.field(form, "Message", body, NOTES);
        msg.setMinLines(6);
        msg.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        if (payUrl != null && !payUrl.isEmpty() && body.contains(Mail.PAY_HERE)) {
            android.widget.TextView tip = Ui.text(a, Mail.PAY_HERE + " becomes a \"Pay here\" button that opens your pay link.",
                    12, Ui.GREY, false);
            tip.setPadding(0, Ui.dp(a, 6), 0, 0);
            form.addView(tip);
        }
        if (pdf != null) {
            android.widget.TextView att = Ui.text(a, "📎  " + pdf.getName(), 14, Ui.PRIMARY, true);
            att.setPadding(0, Ui.dp(a, 12), 0, 0);
            form.addView(att);
        }
        AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Send email")
                .setView(wrap(form))
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Use email app", (d, w) -> useEmailApp.run())
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = toField.getText().toString().trim();
            if (!t.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                toField.setError("Enter the client's email address");
                return;
            }
            dialog.dismiss();
            String s = subj.getText().toString().trim(), b = msg.getText().toString();
            android.widget.Toast.makeText(a, "Sending…", android.widget.Toast.LENGTH_SHORT).show();
            sendNow(a, t, s, b, pdf, payUrl, useEmailApp, true);
        }));
        dialog.show();
    }

    private static void sendNow(Activity a, String to, String subject, String body, java.io.File pdf, String payUrl,
                                Runnable useEmailApp, boolean firstTry) {
        Mail.authorize(a, new GoogleAccount.TokenCallback() {
            @Override
            public void ok(String token) {
                new Thread(() -> {
                    String err = null;
                    boolean expired = false;
                    try {
                        Mail.send(a, token, to, subject, body, pdf, payUrl);
                    } catch (GmailSender.AuthExpired e) {
                        expired = true;
                    } catch (Exception e) {
                        err = e.getMessage() == null ? "Couldn't send" : e.getMessage();
                    }
                    final String fErr = err;
                    final boolean fExpired = expired;
                    a.runOnUiThread(() -> {
                        if (fExpired && firstTry) {
                            sendNow(a, to, subject, body, pdf, payUrl, useEmailApp, false);
                        } else if (fErr == null && !fExpired) {
                            android.widget.Toast.makeText(a, "Sent ✓  A copy is in your " + Mail.service(a) + " Sent folder.",
                                    android.widget.Toast.LENGTH_LONG).show();
                        } else {
                            failed(a, fExpired ? Mail.service(a) + " sign-in expired." : fErr, useEmailApp);
                        }
                    });
                }).start();
            }

            @Override
            public void fail(String reason) {
                failed(a, reason, useEmailApp);
            }
        });
    }

    private static void failed(Activity a, String reason, Runnable useEmailApp) {
        if (a.isFinishing()) return;
        new AlertDialog.Builder(a)
                .setTitle("Not sent")
                .setMessage(reason + "\n\nNothing was sent. You can open it in your email app instead.")
                .setPositiveButton("Use email app", (d, w) -> useEmailApp.run())
                .setNegativeButton("Close", null)
                .show();
    }

    /** existing == null for a new client. */
    static void editClient(Activity a, Client existing, Saved onSaved, Runnable onDeleted) {
        final boolean isNew = existing == null || existing.id == 0;
        final Client k = existing == null ? new Client() : existing;
        final String oldName = k.name;
        LinearLayout form = Ui.vbox(a, 20);
        EditText name = Ui.field(form, "Name (person, venue, agency...) *", k.name, TEXT_WORDS);
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
                    .setMessage("Their " + Words.many(a) + " stay in the app; only the saved contact details are removed.")
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
