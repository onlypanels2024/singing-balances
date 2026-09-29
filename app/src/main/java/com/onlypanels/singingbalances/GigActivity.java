package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** One gig: booking details, what's owed, invoices, reminders, payments and expenses. */
public class GigActivity extends Activity {
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;
    private static final int MENU_CANCEL = 3;

    private long gigId;
    private Gig gig;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gigId = getIntent().getLongExtra("id", 0);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        ScrollView scroll = new ScrollView(this);
        content = Ui.vbox(this, 16);
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private Button action(String label, int bg, int fg, Runnable r) {
        Button b = Ui.button(this, label, bg, fg);
        b.setOnClickListener(v -> r.run());
        content.addView(b);
        return b;
    }

    private void render() {
        gig = Db.get(this).gig(gigId);
        if (gig == null) {
            finish();
            return;
        }
        setTitle(gig.client);
        invalidateOptionsMenu();
        content.removeAllViews();
        Gig g = gig;

        content.addView(Ui.text(this, g.title(), 22, Ui.DARK, true));
        content.addView(Ui.text(this, g.when(), 15, Ui.GREY, false));
        TextView booking = Ui.text(this, Gig.STATUS_NAMES[g.status], 14,
                g.isCancelled() ? Ui.GREY : g.status == Gig.PENCILLED ? Ui.ORANGE : Ui.GREEN, true);
        booking.setPadding(0, Ui.dp(this, 4), 0, 0);
        content.addView(booking);
        if (!g.email.isEmpty()) content.addView(Ui.text(this, g.email, 14, Ui.GREY, false));
        if (!g.invoiceNo.isEmpty()) content.addView(Ui.text(this, "Invoice " + g.invoiceNo, 14, Ui.GREY, false));

        // Money card
        LinearLayout card = Ui.card(this, g.isOverdue() ? Ui.RED_LIGHT : g.isPaid() && !g.isFuture() ? Ui.GREEN_LIGHT : Ui.PRIMARY_LIGHT);
        if (g.isCancelled() || g.isFuture()) {
            card.addView(Ui.text(this, g.isCancelled() ? "Fee (cancelled)" : "Fee to collect on the night", 14, Ui.GREY, false));
            card.addView(Ui.text(this, Money.fmt(g.feeCents), 32, g.isCancelled() ? Ui.GREY : Ui.DARK, true));
        } else {
            card.addView(Ui.text(this, g.isPaid() ? "Fully paid" : "Still owed", 14, Ui.GREY, false));
            card.addView(Ui.text(this, Money.fmt(g.isPaid() ? g.feeCents : g.balance()), 32,
                    g.isPaid() ? Ui.GREEN : g.isOverdue() ? Ui.RED : Ui.DARK, true));
            card.addView(Ui.text(this, "Fee " + Money.fmt(g.feeCents) + "  ·  Received " + Money.fmt(g.paidCents),
                    14, Ui.GREY, false));
        }
        TextView status = Ui.text(this, g.status(), 14, g.statusColor(), true);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(status);
        content.addView(card);

        if (!g.notes.isEmpty()) {
            TextView n = Ui.text(this, g.notes, 15, Ui.DARK, false);
            n.setPadding(0, Ui.dp(this, 12), 0, 0);
            content.addView(n);
        }

        // Actions
        if (!g.isCancelled()) {
            if (!g.isPaid()) {
                action("Record a payment", Ui.PRIMARY, Ui.WHITE, () -> Forms.recordPayment(this, g, this::render));
                action("Paid in full (" + Money.fmt(g.balance()) + ")", Ui.GREEN, Ui.WHITE, () -> {
                    Db.get(this).addPayment(g.id, g.balance(), Dates.today(), "Paid in full");
                    render();
                });
            }
            if (g.isFuture()) {
                action("Add gig to Outlook calendar", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Outlook.calendarGig(this, g));
                if (g.status == Gig.PENCILLED) {
                    action("Mark as confirmed", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> setStatus(Gig.CONFIRMED));
                }
            }
            action(g.invoiceNo.isEmpty() ? "Send invoice (PDF) via Outlook" : "Send invoice " + g.invoiceNo + " via Outlook",
                    Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Outlook.emailInvoice(this, g));
            if (g.isOwed()) {
                action("Email payment reminder (Outlook)", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Outlook.emailReminder(this, g));
                action("Add chase-up to Outlook calendar", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Outlook.calendarChase(this, g));
            }
            if (!Prefs.hasPaymentDetails(this) && !g.isPaid()) {
                TextView hint = Ui.text(this, "Tip: add your IBAN / Revolut in Settings so they appear on invoices and reminders.",
                        13, Ui.ORANGE, false);
                hint.setPadding(0, Ui.dp(this, 8), 0, 0);
                content.addView(hint);
            }
        } else {
            action("Restore booking", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> setStatus(Gig.CONFIRMED));
        }

        // Payments
        content.addView(Ui.section(this, "Payments received"));
        List<Payment> payments = Db.get(this).payments(g.id);
        if (payments.isEmpty()) content.addView(Ui.text(this, "Nothing received yet.", 15, Ui.GREY, false));
        else content.addView(Ui.text(this, "Tap a payment to remove it.", 12, Ui.GREY, false));
        for (Payment p : payments) {
            LinearLayout row = Ui.hbox(this);
            int v = Ui.dp(this, 10);
            row.setPadding(0, v, 0, v);
            String label = Dates.fmt(p.day) + (p.note.isEmpty() ? "" : "  ·  " + p.note);
            row.addView(Ui.text(this, label, 15, Ui.DARK, false), Ui.weight(1f));
            row.addView(Ui.text(this, Money.fmt(p.cents), 15, Ui.GREEN, true));
            row.setOnClickListener(x -> new AlertDialog.Builder(this)
                    .setTitle("Remove this payment?")
                    .setMessage(Money.fmt(p.cents) + " on " + Dates.fmt(p.day))
                    .setPositiveButton("Remove", (d, w) -> {
                        Db.get(this).deletePayment(p.id);
                        render();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            content.addView(row);
        }

        // Expenses for this gig
        content.addView(Ui.section(this, "Expenses for this gig"));
        List<Expense> expenses = Db.get(this).expensesForGig(g.id);
        long spent = 0;
        for (Expense e : expenses) {
            spent += e.cents;
            LinearLayout row = Ui.hbox(this);
            int v = Ui.dp(this, 10);
            row.setPadding(0, v, 0, v);
            row.addView(Ui.text(this, e.note.isEmpty() ? e.category : e.note + " · " + e.category, 15, Ui.DARK, false),
                    Ui.weight(1f));
            row.addView(Ui.text(this, Money.fmt(e.cents), 15, Ui.RED, true));
            row.setOnClickListener(x -> Forms.editExpense(this, e, g, this::render));
            content.addView(row);
        }
        if (expenses.isEmpty()) {
            content.addView(Ui.text(this, "Fuel, outfit, backing tracks... add them to see what you really took home.",
                    14, Ui.GREY, false));
        } else if (!g.isCancelled()) {
            TextView net = Ui.text(this, "Take-home from this gig: " + Money.fmt(g.feeCents - spent), 15,
                    g.feeCents - spent >= 0 ? Ui.PRIMARY : Ui.RED, true);
            net.setPadding(0, Ui.dp(this, 6), 0, 0);
            content.addView(net);
        }
        Button addExp = Ui.button(this, "+  Add an expense", Ui.LIGHT_GREY, Ui.DARK);
        addExp.setOnClickListener(x -> Forms.editExpense(this, null, g, this::render));
        content.addView(addExp);
    }

    private void setStatus(int status) {
        gig.status = status;
        Db.get(this).save(gig);
        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_EDIT, 0, "Edit").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        if (gig == null || !gig.isCancelled()) menu.add(0, MENU_CANCEL, 1, "Gig was cancelled");
        menu.add(0, MENU_DELETE, 2, "Delete gig");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (gig == null) return super.onOptionsItemSelected(item);
        if (id == MENU_EDIT) {
            Forms.editGig(this, gig, 0, x -> render());
            return true;
        }
        if (id == MENU_CANCEL) {
            new AlertDialog.Builder(this)
                    .setTitle("Mark as cancelled?")
                    .setMessage("It stays in your history but won't count as owed or earned.")
                    .setPositiveButton("Cancelled", (d, w) -> setStatus(Gig.CANCELLED))
                    .setNegativeButton("Back", null)
                    .show();
            return true;
        }
        if (id == MENU_DELETE) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete this gig?")
                    .setMessage(gig.title() + "\nThis also removes its payments.")
                    .setPositiveButton("Delete", (d, w) -> {
                        Db.get(this).deleteGig(gig.id);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
