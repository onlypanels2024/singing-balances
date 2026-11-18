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
        Theme.apply(this);
        super.onCreate(savedInstanceState);
        gigId = getIntent().getLongExtra("id", 0);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        ScrollView scroll = new ScrollView(this);
        content = Ui.vbox(this, 16);
        scroll.addView(content);
        setContentView(scroll);
        Theme.bars(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        checkContract();
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
        String clash = g.isCancelled() ? "" : Forms.clash(this, g.gigDay, g.id);
        if (!clash.isEmpty()) {
            TextView cl = Ui.text(this, clash, 14, Ui.ORANGE, true);
            cl.setPadding(0, Ui.dp(this, 4), 0, 0);
            content.addView(cl);
        }
        TextView booking = Ui.text(this, Gig.STATUS_NAMES[g.status], 14,
                g.isCancelled() ? Ui.GREY : g.status == Gig.PENCILLED ? Ui.ORANGE : Ui.GREEN, true);
        booking.setPadding(0, Ui.dp(this, 4), 0, 0);
        content.addView(booking);
        if (!g.email.isEmpty()) content.addView(Ui.text(this, g.email, 14, Ui.GREY, false));
        if (!g.invoiceNo.isEmpty()) content.addView(Ui.text(this, "Invoice " + g.invoiceNo, 14, Ui.GREY, false));

        // Money card
        LinearLayout card = Ui.card(this, g.isOverdue() ? Ui.RED_LIGHT : g.isPaid() && !g.isFuture() ? Ui.GREEN_LIGHT : Ui.PRIMARY_LIGHT);
        card.setElevation(0);
        if (g.isCancelled() || g.isFuture()) {
            card.addView(Ui.text(this, g.isCancelled() ? "Fee (cancelled)" : "Fee to collect " + Words.onTheNight(this), 14, Ui.GREY, false));
            card.addView(Ui.text(this, Money.fmt(g.total()), 32, g.isCancelled() ? Ui.GREY : Ui.DARK, true));
        } else {
            card.addView(Ui.text(this, g.isPaid() ? "Fully paid" : "Still owed", 14, Ui.GREY, false));
            card.addView(Ui.text(this, Money.fmt(g.isPaid() ? g.total() : g.balance()), 32,
                    g.isPaid() ? Ui.GREEN : g.isOverdue() ? Ui.RED : Ui.DARK, true));
            card.addView(Ui.text(this, (g.total() != g.feeCents ? "Total " : "Fee ") + Money.fmt(g.total())
                    + "  ·  Received " + Money.fmt(g.paidCents), 14, Ui.GREY, false));
        }
        if (g.total() != g.feeCents) { // the bill is more than the fee: show what's in it
            StringBuilder parts = new StringBuilder("Fee " + Money.fmt(g.feeCents));
            for (Db.Extra x : Db.get(this).extras(g.id)) parts.append("\n").append(x.label).append(" ").append(Money.fmt(x.cents));
            if (g.vatBp > 0) parts.append("\nVAT ").append(g.vatRate()).append(" ").append(Money.fmt(g.vatCents()))
                    .append(g.vatIncl ? " (included)" : "");
            TextView bd = Ui.text(this, parts.toString(), 13, Ui.GREY, false);
            bd.setPadding(0, Ui.dp(this, 6), 0, 0);
            card.addView(bd);
        }
        TextView status = Ui.text(this, g.status(this), 14, g.statusColor(), true);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(status);
        content.addView(card);

        if (!g.notes.isEmpty()) {
            TextView n = Ui.text(this, g.notes, 15, Ui.DARK, false);
            n.setPadding(0, Ui.dp(this, 12), 0, 0);
            content.addView(n);
        }

        if (!g.isCancelled()) contractSection(g);

        // Actions
        if (!g.isCancelled()) {
            if (!g.isPaid() && !g.isFuture()) {
                action("Record a payment", Ui.PRIMARY, Ui.ON_PRIMARY, () -> Forms.recordPayment(this, g, this::render));
                action("Paid in full (" + Money.fmt(g.balance()) + ")", Ui.GREEN, Theme.dark ? Ui.BG : Ui.WHITE, () -> {
                    Db.get(this).addPayment(g.id, g.balance(), Dates.today(), "Paid in full");
                    render();
                });
            }
            if (g.isFuture()) {
                action("Add to calendar", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Calendar.offer(this, g, false));
                if (g.status == Gig.PENCILLED) {
                    action("Mark as confirmed", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> setStatus(Gig.CONFIRMED));
                }
            }
            String via = Mail.isConnected(this) ? "" 
                    : Google.emailAppName(this).isEmpty() ? " by email" : " via " + Google.emailAppName(this);
            action(g.invoiceNo.isEmpty() ? "Send invoice (PDF)" + via : "Send invoice " + g.invoiceNo + via,
                    Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Google.emailInvoice(this, g));
            if (g.isOwed()) {
                action("Email payment reminder" + (Mail.isConnected(this) || Google.emailAppName(this).isEmpty()
                        ? "" : " (" + Google.emailAppName(this) + ")"), Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Google.emailReminder(this, g));
                if (!PayLink.url(this, g).isEmpty()) {
                    action("Send pay link (WhatsApp, SMS…)", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Google.sharePayLink(this, g));
                }
                action("Add chase-up to Google Calendar", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> Google.calendarChase(this, g));
            }
            if (!Prefs.hasPaymentDetails(this) && !g.isPaid()) {
                TextView hint = Ui.text(this, "Tip: add how clients pay you (bank details, payment link) in Settings so it appears on invoices and reminders.",
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
        content.addView(Ui.section(this, "Expenses for this " + Words.one(this)));
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
            content.addView(Ui.text(this, "Travel, outfits, supplies... add them to see what you really took home.",
                    14, Ui.GREY, false));
        } else if (!g.isCancelled()) {
            TextView net = Ui.text(this, "Take-home from this " + Words.one(this) + ": " + Money.fmt(g.net() - spent), 15,
                    g.net() - spent >= 0 ? Ui.PRIMARY : Ui.RED, true);
            net.setPadding(0, Ui.dp(this, 6), 0, 0);
            content.addView(net);
        }
        Button addExp = Ui.quiet(this, "+  Add an expense");
        addExp.setOnClickListener(x -> Forms.editExpense(this, null, g, this::render));
        content.addView(addExp);
    }

    // ------------------------------------------------------------------ contract (ShowFee Business)

    private void contractSection(Gig g) {
        List<Contract> all = Contract.forGig(this, g.id);
        Contract k = all.isEmpty() ? null : all.get(0);
        boolean business = Pro.isBusiness(this);
        if (k == null && !business) {
            content.addView(Ui.section(this, "Contract"));
            content.addView(Ui.text(this, "With ShowFee Business you can send a contract that you and your client "
                    + "both sign online.", 14, Ui.GREY, false));
            action("See ShowFee Business", Ui.PRIMARY_LIGHT, Ui.PRIMARY,
                    () -> startActivity(new android.content.Intent(this, ProActivity.class).putExtra("business", true)));
            return;
        }
        content.addView(Ui.section(this, "Contract"));
        LinearLayout card = Ui.card(this, k != null && k.isSigned() ? Ui.GREEN_LIGHT : Ui.SURFACE);
        card.setElevation(0);
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        if (k == null || k.isVoid()) {
            card.addView(Ui.text(this, k == null ? "No contract yet" : "Contract cancelled", 16, Ui.DARK, true));
            card.addView(Ui.text(this, "Send one to sign online – you both sign, your client on their phone, "
                    + "no account needed.", 14, Ui.GREY, false));
        } else if (k.isSent()) {
            card.addView(Ui.text(this, "Waiting for " + g.client + " to sign", 16, Ui.ORANGE, true));
            card.addView(Ui.text(this, "Version " + k.version + " · you signed and sent it " + df.format(new java.util.Date(k.sentAt)),
                    14, Ui.GREY, false));
        } else {
            card.addView(Ui.text(this, "Signed by " + k.clientName, 16, Ui.GREEN, true));
            card.addView(Ui.text(this, df.format(new java.util.Date(k.signedAt)) + " · both of you have signed",
                    14, Ui.GREY, false));
        }
        content.addView(card);

        if (k != null && k.isSent()) {
            action("Send the link again", Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> sendContractLink(k));
        }
        if (k != null && !k.isVoid()) {
            action(k.isSigned() ? "View signed contract (PDF)" : "View contract (PDF)", Ui.PRIMARY_LIGHT, Ui.PRIMARY,
                    () -> openContract(k));
        }
        if (k != null && k.isSigned()) {
            action("Email the signed copy to " + g.client, Ui.PRIMARY_LIGHT, Ui.PRIMARY, () -> emailSigned(k));
        }
        if (business) {
            action(k == null || k.isVoid() ? "Create contract" : "New version", k == null || k.isVoid() ? Ui.PRIMARY : Ui.PRIMARY_LIGHT,
                    k == null || k.isVoid() ? Ui.ON_PRIMARY : Ui.PRIMARY,
                    () -> startActivityForResult(new android.content.Intent(this, ContractActivity.class).putExtra("gig", g.id),
                            ContractActivity.REQ));
        }
        if (k != null && k.isSent()) {
            Button cancel = Ui.quiet(this, "Cancel this contract");
            cancel.setTextColor(Ui.RED);
            cancel.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Cancel this contract?")
                    .setMessage("The link stops working, so " + g.client + " can't sign it. Use this if it went out by mistake.")
                    .setPositiveButton("Cancel contract", (d, w) -> Cloud.voidContract(this, k.id, new Cloud.Done<Void>() {
                        @Override public void ok(Void r) {
                            k.status = Contract.VOID;
                            Contract.save(GigActivity.this, k);
                            render();
                        }

                        @Override public void fail(String reason) {
                            android.widget.Toast.makeText(GigActivity.this, reason, android.widget.Toast.LENGTH_LONG).show();
                        }
                    }))
                    .setNegativeButton("Keep it", null)
                    .show());
            content.addView(cancel);
        }
        if (all.size() > 1) {
            StringBuilder sb = new StringBuilder();
            for (Contract x : all) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("Version ").append(x.version).append(" · ")
                        .append(x.isSigned() ? "signed by " + x.clientName : x.isVoid() ? (x == k ? "cancelled" : "replaced") : "waiting");
            }
            TextView v = Ui.text(this, sb.toString(), 13, Ui.GREY, false);
            v.setPadding(0, Ui.dp(this, 8), 0, 0);
            content.addView(v);
        }
    }

    private String contractSubject(String what) {
        return what + " – " + (gig.event.isEmpty() ? Words.One(this) : gig.event) + " (" + Dates.fmt(gig.gigDay) + ")";
    }

    private String signOff() {
        return "Many thanks,\n" + Prefs.get(this, Prefs.NAME);
    }

    /** Emails (or shares) the signing link. */
    private void sendContractLink(Contract k) {
        String subject = contractSubject("Contract to sign");
        String body = "Hi " + gig.client + ",\n\nPlease read and sign the contract for the " + Words.one(this)
                + (gig.event.isEmpty() ? "" : " (" + gig.event + ")") + " on " + Dates.fmt(gig.gigDay) + " here:\n"
                + k.link() + "\n\nIt only takes a minute on your phone – no account needed. I've already signed it.\n\n" + signOff();
        Runnable share = () -> {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                    .putExtra(android.content.Intent.EXTRA_TEXT, body);
            if (!gig.email.isEmpty()) i.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{gig.email});
            startActivity(android.content.Intent.createChooser(i, "Send the contract link"));
        };
        if (Mail.isConnected(this)) Forms.composeEmail(this, gig.email, subject, body, null, null, share);
        else share.run();
    }

    private void openContract(Contract k) {
        try {
            java.io.File pdf = ContractPdf.create(this, k);
            android.net.Uri uri = ShareProvider.uriFor(pdf);
            startActivity(android.content.Intent.createChooser(new android.content.Intent(android.content.Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf").addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open contract"));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Couldn't make the PDF: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void emailSigned(Contract k) {
        java.io.File pdf;
        try {
            pdf = ContractPdf.create(this, k);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Couldn't make the PDF: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String subject = contractSubject("Signed contract");
        String body = "Hi " + gig.client + ",\n\nThank you for signing. Attached is the signed contract for your records.\n\n" + signOff();
        if (Mail.isConnected(this)) {
            Forms.composeEmail(this, gig.email, subject, body, pdf, null, () -> Google.openInEmailApp(this, gig, subject, body, pdf));
        } else {
            Google.openInEmailApp(this, gig, subject, body, pdf);
        }
    }

    /** Has the client signed? Checks the newest contract that's still waiting. */
    private void checkContract() {
        if (gig == null) return;
        Contract k = Contract.latest(this, gig.id);
        if (k == null || !(k.isSent() || (k.isSigned() && !k.handled))) return;
        if (k.isSigned()) {
            signedNews(k);
            return;
        }
        Cloud.contractState(this, k.id, new Cloud.Done<org.json.JSONObject>() {
            @Override public void ok(org.json.JSONObject r) {
                if (r == null || isFinishing() || !Contract.SIGNED.equals(r.optString("status"))) return;
                try {
                    String sig = r.optString("clientSig");
                    int comma = sig.indexOf(',');
                    byte[] png = android.util.Base64.decode(sig.substring(comma + 1), android.util.Base64.DEFAULT);
                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(k.clientSig(GigActivity.this))) {
                        out.write(png);
                    }
                } catch (Exception ignored) { } // the PDF still says who signed and when
                k.status = Contract.SIGNED;
                k.clientName = r.optString("clientName");
                k.signedAt = r.optLong("clientAt", System.currentTimeMillis());
                Contract.save(GigActivity.this, k);
                Gig g = Db.get(GigActivity.this).gig(k.gigId);
                if (g != null && g.status == Gig.PENCILLED && Contract.confirmOnSign(GigActivity.this)) {
                    g.status = Gig.CONFIRMED;
                    Db.get(GigActivity.this).save(g);
                }
                render();
                signedNews(k);
            }

            @Override public void fail(String reason) { } // try again next time
        });
    }

    private void signedNews(Contract k) {
        k.handled = true;
        Contract.save(this, k);
        new AlertDialog.Builder(this)
                .setTitle("Contract signed")
                .setMessage(k.clientName + " signed the contract. Email them the signed copy (PDF)?")
                .setPositiveButton("Email it", (d, w) -> emailSigned(k))
                .setNegativeButton("Later", null)
                .show();
    }

    private void setStatus(int status) {
        gig.status = status;
        Db.get(this).save(gig);
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ContractActivity.REQ) {
            if (resultCode == RESULT_OK && data != null && gig != null) {
                Contract k = Contract.find(this, data.getStringExtra("send"));
                if (k != null) getWindow().getDecorView().post(() -> sendContractLink(k));
            }
            return;
        }
        GoogleAccount.handleResult(this, requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_EDIT, 0, "Edit").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        if (gig == null || !gig.isCancelled()) menu.add(0, MENU_CANCEL, 1, Words.One(this) + " was cancelled");
        menu.add(0, MENU_DELETE, 2, "Delete " + Words.one(this));
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
                    .setTitle("Delete this " + Words.one(this) + "?")
                    .setMessage(gig.title() + "\nThis also removes its payments.")
                    .setPositiveButton("Delete", (d, w) -> {
                        Db.get(this).deleteGig(gig.id);
                        Contract.deleteForGig(this, gig.id);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
