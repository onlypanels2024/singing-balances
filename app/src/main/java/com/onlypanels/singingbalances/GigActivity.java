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

/** One gig: what's owed, payments received, and reminder buttons. */
public class GigActivity extends Activity {
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;

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

    private void render() {
        gig = Db.get(this).gig(gigId);
        if (gig == null) {
            finish();
            return;
        }
        setTitle(gig.client);
        content.removeAllViews();

        content.addView(Ui.text(this, gig.title(), 22, Ui.DARK, true));
        content.addView(Ui.text(this, "Gig on " + Dates.fmt(gig.gigDay), 15, Ui.GREY, false));
        if (!gig.email.isEmpty()) content.addView(Ui.text(this, gig.email, 15, Ui.GREY, false));

        // Balance card
        LinearLayout card = Ui.vbox(this, 16);
        card.setBackground(Ui.rounded(this, gig.isOverdue() ? 0xFFFFEBEE : Ui.PRIMARY_LIGHT, 12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = Ui.dp(this, 16);
        card.setLayoutParams(cardLp);
        card.addView(Ui.text(this, gig.isPaid() ? "Fully paid" : "Still owed", 14, Ui.GREY, false));
        card.addView(Ui.text(this, Money.fmt(gig.isPaid() ? gig.feeCents : gig.balance()), 32,
                gig.isPaid() ? Ui.GREEN : gig.isOverdue() ? Ui.RED : Ui.DARK, true));
        card.addView(Ui.text(this, "Fee " + Money.fmt(gig.feeCents) + "  ·  Received " + Money.fmt(gig.paidCents),
                14, Ui.GREY, false));
        TextView status = Ui.text(this, gig.status(), 14, gig.statusColor(), true);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(status);
        content.addView(card);

        if (!gig.notes.isEmpty()) {
            TextView n = Ui.text(this, gig.notes, 15, Ui.DARK, false);
            n.setPadding(0, Ui.dp(this, 12), 0, 0);
            content.addView(n);
        }

        // Actions
        if (!gig.isPaid()) {
            Button pay = Ui.button(this, "Record a payment", Ui.PRIMARY, Ui.WHITE);
            pay.setOnClickListener(v -> Forms.recordPayment(this, gig, this::render));
            content.addView(pay);

            Button full = Ui.button(this, "Mark as paid in full (" + Money.fmt(gig.balance()) + ")", Ui.GREEN, Ui.WHITE);
            full.setOnClickListener(v -> {
                Db.get(this).addPayment(gig.id, gig.balance(), Dates.today(), "Paid in full");
                render();
            });
            content.addView(full);

            Button mail = Ui.button(this, "Email payment reminder (Outlook)", Ui.PRIMARY_LIGHT, Ui.PRIMARY);
            mail.setOnClickListener(v -> Outlook.emailReminder(this, gig));
            content.addView(mail);

            Button cal = Ui.button(this, "Add chase-up to Outlook calendar", Ui.PRIMARY_LIGHT, Ui.PRIMARY);
            cal.setOnClickListener(v -> Outlook.calendarReminder(this, gig));
            content.addView(cal);
        }

        // Payments
        TextView h = Ui.text(this, "Payments received", 17, Ui.DARK, true);
        h.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 4));
        content.addView(h);
        List<Payment> payments = Db.get(this).payments(gig.id);
        if (payments.isEmpty()) {
            content.addView(Ui.text(this, "Nothing received yet.", 15, Ui.GREY, false));
        } else {
            content.addView(Ui.text(this, "Tap a payment to remove it.", 12, Ui.GREY, false));
        }
        for (Payment p : payments) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            int v = Ui.dp(this, 10);
            row.setPadding(0, v, 0, v);
            String label = Dates.fmt(p.day) + (p.note.isEmpty() ? "" : "  ·  " + p.note);
            row.addView(Ui.text(this, label, 15, Ui.DARK, false), new LinearLayout.LayoutParams(0, -2, 1f));
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_EDIT, 0, "Edit").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, MENU_DELETE, 1, "Delete gig");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == MENU_EDIT && gig != null) {
            Forms.editGig(this, gig, x -> render());
            return true;
        }
        if (id == MENU_DELETE && gig != null) {
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
