package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** One client: contact details, how they pay, and all their gigs. */
public class ClientActivity extends Activity {
    private static final int MENU_EDIT = 1;

    private String name;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.apply(this);
        super.onCreate(savedInstanceState);
        name = getIntent().getStringExtra("name");
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
    }

    private Client stats() {
        for (Client k : Db.get(this).clientsWithStats()) if (k.name.equalsIgnoreCase(name)) return k;
        return null;
    }

    private void render() {
        Client k = stats();
        if (k == null) {
            finish();
            return;
        }
        setTitle(k.name);
        content.removeAllViews();
        LinearLayout head = Ui.hbox(this);
        head.addView(Ui.avatar(this, k.name, Ui.PRIMARY_LIGHT, Ui.PRIMARY));
        head.addView(Ui.text(this, k.name, 22, Ui.DARK, true), Ui.weight(1f));
        content.addView(head);
        if (!k.email.isEmpty()) content.addView(Ui.text(this, k.email, 15, Ui.GREY, false));
        if (!k.phone.isEmpty()) content.addView(Ui.text(this, k.phone, 15, Ui.GREY, false));
        if (!k.notes.isEmpty()) {
            TextView n = Ui.text(this, k.notes, 15, Ui.DARK, false);
            n.setPadding(0, Ui.dp(this, 8), 0, 0);
            content.addView(n);
        }

        content.addView(Ui.tiles(this,
                Ui.tile(this, "Earned from them", Money.fmt(k.earnedCents), Ui.DARK),
                Ui.tile(this, "Still owed", Money.fmt(k.owedCents), k.overdueCents > 0 ? Ui.RED : Ui.DARK)));
        LinearLayout habit = Ui.card(this, k.overdueCents > 0 ? Ui.RED_LIGHT : k.paidLate > 0 ? Ui.ORANGE_LIGHT : Ui.GREEN_LIGHT);
        habit.addView(Ui.text(this, k.payingHabit(this), 15, Ui.DARK, true));
        habit.addView(Ui.text(this, Words.count(this, k.gigCount)
                + (k.upcoming > 0 ? " · " + k.upcoming + " coming up" : ""), 13, Ui.GREY, false));
        content.addView(habit);

        LinearLayout buttons = Ui.hbox(this);
        if (!k.phone.isEmpty()) {
            Button call = Ui.tonal(this, "Call");
            call.setOnClickListener(v -> open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + k.phone))));
            LinearLayout.LayoutParams lp = Ui.weight(1f);
            lp.rightMargin = Ui.dp(this, 5);
            buttons.addView(call, lp);
            Button wa = Ui.tonal(this, "WhatsApp");
            String digits = k.phone.replaceAll("[^0-9]", "");
            wa.setOnClickListener(v -> open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + digits))));
            LinearLayout.LayoutParams lp2 = Ui.weight(1f);
            lp2.leftMargin = Ui.dp(this, 5);
            buttons.addView(wa, lp2);
            content.addView(buttons);
        }
        Button add = Ui.primary(this, "+  New " + Words.one(this) + " for " + k.name);
        add.setOnClickListener(v -> {
            Gig g = new Gig();
            g.client = k.name;
            g.email = k.email;
            g.gigDay = Dates.today();
            g.dueDay = g.gigDay + Prefs.termsDays(this);
            // Opens the form pre-filled; saving creates the gig
            Forms.editGig(this, g, 0, id -> render());
        });
        content.addView(add);

        content.addView(Ui.section(this, Words.Many(this)));
        List<Gig> gigs = Db.get(this).gigsForClient(k.name);
        if (gigs.isEmpty()) content.addView(Ui.text(this, "No " + Words.many(this) + " yet.", 15, Ui.GREY, false));
        for (Gig g : gigs) {
            LinearLayout r = Ui.row(this, g.event.isEmpty() ? Dates.fmt(g.gigDay) : g.event, g.when(), g.status(this),
                    g.statusColor(), Money.fmt(g.displayAmount()), g.amountColor());
            r.setPadding(0, r.getPaddingTop(), 0, r.getPaddingBottom());
            r.setOnClickListener(v -> startActivity(new Intent(this, GigActivity.class).putExtra("id", g.id)));
            content.addView(r);
            content.addView(Ui.divider(this));
        }
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_EDIT, 0, "Edit").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == MENU_EDIT) {
            Client k = Db.get(this).client(name);
            if (k == null) {
                k = new Client();
                k.name = name;
            }
            final Client edit = k;
            Forms.editClient(this, edit, id -> {
                Client saved = Db.get(this).client(id);
                if (saved != null) name = saved.name;
                render();
            }, this::finish);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
