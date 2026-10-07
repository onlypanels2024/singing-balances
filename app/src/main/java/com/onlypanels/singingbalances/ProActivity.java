package com.onlypanels.singingbalances;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The ShowFee Pro screen: what Pro includes, the price from Google Play, and the subscribe button. */
public class ProActivity extends Activity {
    private boolean waited;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.apply(this);
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("");
        render();
        Pro.refresh(this, this::render);
        // If Google Play hasn't answered after a few seconds, say so instead of waiting forever
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            waited = true;
            if (!isFinishing()) render();
        }, 5000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pro.refresh(this, this::render);
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout v = Ui.vbox(this, 0);
        int p = Ui.dp(this, 24);
        v.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 32));
        scroll.addView(v);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        v.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)));
        TextView title = Ui.text(this, getString(R.string.app_name) + " Pro", 28, Ui.DARK, true);
        title.setPadding(0, Ui.dp(this, 14), 0, 0);
        v.addView(title);

        boolean pro = Pro.isPro(this), dev = Pro.isDeveloperCopy(this);
        String reason = getIntent().getStringExtra("reason");
        if (!pro && reason != null && !reason.isEmpty()) {
            TextView r = Ui.text(this, reason, 14, Ui.DARK, false);
            int q = Ui.dp(this, 14);
            r.setPadding(q, Ui.dp(this, 12), q, Ui.dp(this, 12));
            r.setBackground(Ui.rounded(this, Ui.ORANGE_LIGHT, 14));
            LinearLayout.LayoutParams lp = Ui.matchWrap(this, 14);
            v.addView(r, lp);
        }

        String headline = dev ? "Everything is unlocked on this copy."
                : pro ? "You're on ShowFee Pro ✓"
                : Pro.hasTrial() ? "Try every feature free for a month." : "Unlock every feature.";
        TextView h = Ui.text(this, headline, 18, pro || dev ? Ui.GREEN : Ui.PRIMARY, true);
        h.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 6));
        v.addView(h);

        String[] perks = {
                "Unlimited " + Words.many(this) + " (free plan: " + Pro.FREE_BOOKINGS + " a month)",
                "PDF invoices with your logo and colours",
                "Send invoices and reminders from your Gmail or Outlook",
                "Pay-now links, \"Pay here\" buttons and QR codes",
                "Send pay links by WhatsApp or text message"};
        for (String s : perks) v.addView(tick(s));

        if (dev) {
            v.addView(note("This copy was installed outside Google Play by the app's owner, so there's nothing to pay."));
        } else if (pro) {
            Button manage = Ui.tonal(this, "Manage or cancel in Google Play");
            manage.setOnClickListener(x -> Pro.manage(this));
            v.addView(manage);
            v.addView(note("Your subscription renews automatically through Google Play. You can cancel any time there."));
        } else {
            String price = Pro.priceLine();
            TextView pr = Ui.text(this, price.isEmpty()
                    ? (waited ? "Google Play isn't available on this phone right now, so ShowFee Pro can't be bought here."
                    : "Getting the price from Google Play…") : price, price.isEmpty() ? 14 : 17,
                    price.isEmpty() ? Ui.GREY : Ui.DARK, !price.isEmpty());
            pr.setPadding(0, Ui.dp(this, 22), 0, 0);
            v.addView(pr);
            Button buy = Ui.primary(this, Pro.hasTrial() ? "Start my free month" : "Subscribe");
            buy.setEnabled(!price.isEmpty());
            buy.setAlpha(price.isEmpty() ? 0.5f : 1f);
            buy.setOnClickListener(x -> {
                if (!Pro.buy(this)) {
                    Toast.makeText(this, "Google Play isn't ready – please try again in a moment", Toast.LENGTH_LONG).show();
                }
            });
            v.addView(buy);
            v.addView(note((Pro.hasTrial()
                    ? "You won't be charged if you cancel before the free month ends. " : "")
                    + "Payment is handled by Google Play and renews monthly – cancel any time in the Play Store. "
                    + "Your " + Words.many(this) + " and data always stay yours, with or without Pro."));
            Button later = Ui.quiet(this, "Not now");
            later.setOnClickListener(x -> finish());
            v.addView(later);
            TextView restore = Ui.text(this, "Already subscribed? Tap to check again", 13, Ui.PRIMARY, false);
            restore.setGravity(Gravity.CENTER);
            restore.setPadding(0, Ui.dp(this, 16), 0, 0);
            restore.setOnClickListener(x -> {
                Pro.refresh(this, this::render);
                Toast.makeText(this, "Checking with Google Play…", Toast.LENGTH_SHORT).show();
            });
            v.addView(restore);
            TextView code = Ui.text(this, "Have an access code?", 13, Ui.PRIMARY, false);
            code.setGravity(Gravity.CENTER);
            code.setPadding(0, Ui.dp(this, 14), 0, 0);
            code.setOnClickListener(x -> askCode());
            v.addView(code);
        }
        if (!dev && Pro.codeDaysLeft(this) > 0 && !Prefs.sp(this).getBoolean(Prefs.PRO_ACTIVE, false)) {
            v.addView(note("Unlocked with an access code – " + Pro.codeDaysLeft(this) + " days left."));
        }
        v.addView(Legal.links(this));
        setContentView(scroll);
        Theme.bars(this);
    }

    private void askCode() {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setSingleLine(true);
        e.setHint("Access code");
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        LinearLayout box = Ui.vbox(this, 20);
        box.addView(e);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Access code")
                .setView(box)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (Pro.redeem(this, e.getText().toString())) {
                        Toast.makeText(this, "ShowFee Pro unlocked for " + Pro.CODE_DAYS + " days ✓", Toast.LENGTH_LONG).show();
                        render();
                    } else {
                        Toast.makeText(this, "That code isn't valid", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout tick(String s) {
        LinearLayout row = Ui.hbox(this);
        row.setPadding(0, Ui.dp(this, 7), 0, Ui.dp(this, 7));
        TextView mark = Ui.text(this, "✓", 16, Ui.PRIMARY, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 28), -2);
        row.addView(mark, lp);
        row.addView(Ui.text(this, s, 15, Ui.DARK, false), Ui.weight(1f));
        return row;
    }

    private TextView note(String s) {
        TextView t = Ui.text(this, s, 12, Ui.GREY, false);
        t.setPadding(0, Ui.dp(this, 12), 0, 0);
        return t;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
