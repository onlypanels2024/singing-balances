package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Booking requests sent from the user's booking link. Without an "id" extra: the list (new and answered).
 * With an "id": one request, with Accept (adds it to bookings), Decline and Reply.
 */
public class RequestsActivity extends Activity {
    private LinearLayout page;
    private String id;

    static void open(Activity a, String id) {
        a.startActivity(new Intent(a, RequestsActivity.class).putExtra("id", id));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.apply(this);
        super.onCreate(savedInstanceState);
        id = getIntent().getStringExtra("id");
        setTitle(id == null ? "Booking requests" : "Request");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        ScrollView scroll = new ScrollView(this);
        page = Ui.vbox(this, 16);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(page);
        setContentView(scroll);
        Theme.bars(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        if (id == null) {
            Cloud.refresh(this, new Cloud.Done<Integer>() {
                @Override public void ok(Integer n) {
                    if (!isFinishing()) render();
                }

                @Override public void fail(String reason) {
                    if (!isFinishing()) Toast.makeText(RequestsActivity.this, "Couldn't check for new requests: " + reason,
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void render() {
        page.removeAllViews();
        if (id == null) list();
        else detail();
    }

    // ------------------------------------------------------------------ list

    private void list() {
        List<Cloud.Request> all = Cloud.cached(this);
        int newOnes = 0;
        for (Cloud.Request r : all) if (r.isNew()) newOnes++;
        if (all.isEmpty()) {
            TextView t = Ui.text(this, "No requests yet.\nShare your link " + Cloud.link(this)
                    + " and requests from clients will appear here.", 15, Ui.GREY, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(this, 16), Ui.dp(this, 60), Ui.dp(this, 16), Ui.dp(this, 16));
            page.addView(t);
            Button b = Ui.tonal(this, "Your booking link");
            b.setOnClickListener(v -> startActivity(new Intent(this, BookingLinkActivity.class)));
            page.addView(b);
            return;
        }
        if (newOnes > 0) {
            page.addView(overline("New"));
            LinearLayout card = card();
            boolean first = true;
            for (Cloud.Request r : all) {
                if (!r.isNew()) continue;
                if (!first) card.addView(line());
                card.addView(row(r));
                first = false;
            }
            page.addView(card);
        }
        if (newOnes < all.size()) {
            page.addView(overline("Answered"));
            LinearLayout card = card();
            boolean first = true;
            for (Cloud.Request r : all) {
                if (r.isNew()) continue;
                if (!first) card.addView(line());
                card.addView(row(r));
                first = false;
            }
            page.addView(card);
        }
        TextView n = Ui.text(this, "Requests come from your booking link " + Cloud.link(this)
                + ". Accepted ones are added to your " + Words.many(this) + ".", 13, Ui.GREY, false);
        n.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), 0);
        page.addView(n);
    }

    private View row(Cloud.Request r) {
        LinearLayout row = Ui.hbox(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        row.addView(dateBadge(r.day(), 46, r.isNew()));
        LinearLayout t = Ui.vbox(this, 0);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        t.addView(Ui.text(this, r.name, 16, Ui.DARK, true));
        String s = r.summary();
        if (!s.isEmpty()) t.addView(oneLine(Ui.text(this, s, 13, Ui.GREY, false)));
        t.addView(Ui.text(this, Dates.withWeekday(r.day()), 13, Ui.GREY, false));
        row.addView(t, Ui.weight(1f));
        if (r.isNew()) {
            View dot = new View(this);
            dot.setBackground(Ui.rounded(this, 0xFFFFD400, 5));
            row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 10), Ui.dp(this, 10)));
        } else {
            boolean ok = "accepted".equals(r.status);
            TextView tag = Ui.text(this, ok ? "Accepted" : "Declined", 12, ok ? Ui.GREEN : Ui.GREY, true);
            tag.setBackground(Ui.rounded(this, ok ? Ui.GREEN_LIGHT : Ui.LIGHT_GREY, 10));
            tag.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
            row.addView(tag);
        }
        row.setBackground(Ui.touchable(null, Ui.ripple()));
        row.setOnClickListener(v -> open(this, r.id));
        return row;
    }

    // ------------------------------------------------------------------ one request

    private void detail() {
        Cloud.Request r = Cloud.find(this, id);
        if (r == null) {
            finish();
            return;
        }
        LinearLayout head = Ui.hbox(this);
        head.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        head.addView(dateBadge(r.day(), 56, true));
        LinearLayout ht = Ui.vbox(this, 0);
        ht.setPadding(Ui.dp(this, 14), 0, 0, 0);
        ht.addView(Ui.text(this, r.name, 20, Ui.DARK, true));
        ht.addView(Ui.text(this, Dates.withWeekday(r.day()), 14, Ui.GREY, false));
        head.addView(ht, Ui.weight(1f));
        page.addView(head);

        if (!r.isNew()) {
            boolean ok = "accepted".equals(r.status);
            TextView st = Ui.text(this, ok ? "You accepted this request" : "You declined this request", 14, ok ? Ui.GREEN : Ui.GREY, true);
            st.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 10));
            page.addView(st);
        }

        LinearLayout card = card();
        kv(card, r.contact.contains("@") ? "Email" : "Phone or email", r.contact, true, false);
        for (int i = 0; i < r.ask.size(); i++) {
            String a = i < r.answers.size() ? r.answers.get(i) : "";
            kv(card, r.ask.get(i), a.trim().isEmpty() ? "–" : a, false, true);
        }
        if (!r.message.trim().isEmpty()) kv(card, "Message", r.message, false, true);
        page.addView(card);
        if (!r.ask.isEmpty()) {
            TextView n = Ui.text(this, "These are the questions on your booking link, with the client's answers.", 13, Ui.GREY, false);
            n.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), 0);
            page.addView(n);
        }

        Button reply = Ui.quiet(this, r.phone() ? "Reply on WhatsApp" : r.contact.contains("@") ? "Reply by email" : "Reply");
        reply.setLayoutParams(Ui.matchWrap(this, 24));
        reply.setOnClickListener(v -> reply(r));
        page.addView(reply);

        if (r.isNew()) {
            LinearLayout two = Ui.hbox(this);
            two.setLayoutParams(Ui.matchWrap(this, 10));
            Button decline = Ui.quiet(this, "Decline");
            decline.setOnClickListener(v -> decline(r));
            Button accept = Ui.primary(this, "Accept");
            accept.setOnClickListener(v -> accept(r));
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1f), b = new LinearLayout.LayoutParams(0, -2, 1f);
            b.leftMargin = Ui.dp(this, 10);
            two.addView(decline, a);
            two.addView(accept, b);
            page.addView(two);
            TextView n = Ui.text(this, "Accept adds it to your " + Words.many(this) + " – you add the fee there.", 13, Ui.GREY, false);
            n.setGravity(Gravity.CENTER);
            n.setPadding(0, Ui.dp(this, 8), 0, 0);
            page.addView(n);
        }
    }

    private void accept(Cloud.Request r) {
        Gig g = new Gig();
        g.client = r.name;
        if (r.contact.contains("@")) g.email = r.contact;
        g.event = r.summary();
        StringBuilder notes = new StringBuilder("Booked through your link.");
        if (!r.contact.contains("@")) notes.append("\nPhone: ").append(r.contact);
        for (int i = 0; i < r.ask.size(); i++) {
            String a = i < r.answers.size() ? r.answers.get(i).trim() : "";
            if (!a.isEmpty()) notes.append('\n').append(r.ask.get(i)).append(": ").append(a);
        }
        if (!r.message.trim().isEmpty()) notes.append("\nMessage: ").append(r.message.trim());
        g.notes = notes.toString();
        g.gigDay = r.day();
        g.dueDay = g.gigDay + Prefs.termsDays(this);
        Forms.editGig(this, g, r.day(), gigId -> Cloud.setStatus(this, r.id, "accepted", new Cloud.Done<Void>() {
            @Override public void ok(Void x) {
                Toast.makeText(RequestsActivity.this, "Accepted and added to your " + Words.many(RequestsActivity.this),
                        Toast.LENGTH_LONG).show();
                Cloud.syncBusy(RequestsActivity.this);
                render();
            }

            @Override public void fail(String reason) {
                Toast.makeText(RequestsActivity.this, "Added to your " + Words.many(RequestsActivity.this)
                        + ", but the request couldn't be marked as accepted: " + reason, Toast.LENGTH_LONG).show();
            }
        }));
    }

    private void decline(Cloud.Request r) {
        new AlertDialog.Builder(this).setTitle("Decline this request?")
                .setMessage(r.name.split(" ")[0] + " isn't told automatically – you can reply to let them know.")
                .setPositiveButton("Decline", (d, w) -> Cloud.setStatus(this, r.id, "declined", new Cloud.Done<Void>() {
                    @Override public void ok(Void x) {
                        render();
                    }

                    @Override public void fail(String reason) {
                        Toast.makeText(RequestsActivity.this, reason, Toast.LENGTH_LONG).show();
                    }
                }))
                .setNegativeButton("Back", null).show();
    }

    private void reply(Cloud.Request r) {
        String first = r.name.split(" ")[0];
        String text = "Hi " + first + ", thanks for your booking request for " + Dates.withWeekday(r.day()) + ".";
        try {
            if (r.phone()) {
                String digits = r.contact.replaceAll("[^0-9+]", "");
                if (digits.startsWith("00")) digits = digits.substring(2);
                digits = digits.replace("+", "");
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits + "?text=" + Uri.encode(text))));
            } else if (r.contact.contains("@")) {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + r.contact))
                        .putExtra(Intent.EXTRA_SUBJECT, "Your booking request")
                        .putExtra(Intent.EXTRA_TEXT, text), "Reply by email"));
            } else {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, text), "Reply"));
            }
        } catch (Exception e) {
            Toast.makeText(this, "No app found to reply with. Contact: " + r.contact, Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------------ helpers

    private View dateBadge(long day, int sizeDp, boolean strong) {
        LocalDate d = LocalDate.ofEpochDay(day);
        LinearLayout b = Ui.vbox(this, 0);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rounded(this, strong ? Ui.PRIMARY_LIGHT : Ui.LIGHT_GREY, 12));
        b.addView(Ui.text(this, d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ROOT), 11,
                strong ? Ui.PRIMARY : Ui.GREY, true));
        b.addView(Ui.text(this, String.valueOf(d.getDayOfMonth()), sizeDp > 50 ? 21 : 18, strong ? Ui.PRIMARY : Ui.GREY, true));
        for (int i = 0; i < b.getChildCount(); i++) ((TextView) b.getChildAt(i)).setGravity(Gravity.CENTER);
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp + 2)));
        return b;
    }

    private void kv(LinearLayout card, String k, String v, boolean link, boolean line) {
        if (line || card.getChildCount() > 0) card.addView(line());
        LinearLayout b = Ui.vbox(this, 0);
        b.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        b.addView(Ui.text(this, k, 13, Ui.GREY, false));
        TextView val = Ui.text(this, v, 16, link ? Ui.PRIMARY : Ui.DARK, false);
        val.setTextIsSelectable(true);
        b.addView(val);
        card.addView(b);
    }

    private TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.outlined(this, Ui.SURFACE, 16));
        c.setClipToOutline(true);
        return c;
    }

    private TextView overline(String s) {
        TextView t = Ui.overline(this, s);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), 0, Ui.dp(this, 8));
        return t;
    }

    private View line() {
        View v = new View(this);
        v.setBackgroundColor(Ui.LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1) / 2)));
        return v;
    }
}
