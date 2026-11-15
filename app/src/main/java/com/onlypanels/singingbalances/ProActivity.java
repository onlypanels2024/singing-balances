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

    /** Which plan the user has picked on this screen (Pro or Business). */
    private String chosen;

    private void render() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout v = Ui.vbox(this, 0);
        int p = Ui.dp(this, 24);
        v.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 32));
        scroll.addView(v);

        boolean pro = Pro.isPro(this), dev = Pro.isDeveloperCopy(this), business = Pro.isBusiness(this);
        boolean subscribed = Prefs.sp(this).getBoolean(Prefs.PRO_ACTIVE, false);
        boolean byCode = pro && !dev && !subscribed && Pro.codeDaysLeft(this) > 0;
        String reason = getIntent().getStringExtra("reason");
        if (chosen == null) {
            chosen = pro || getIntent().getBooleanExtra("business", false) || (reason != null && reason.contains("Business"))
                    ? Pro.BUSINESS : Pro.PRODUCT;
        }

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        v.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        TextView title = Ui.text(this, dev ? getString(R.string.app_name) + " Pro"
                : business ? "ShowFee Business" : subscribed ? "ShowFee Pro" : "Choose your plan", 28, Ui.DARK, true);
        title.setPadding(0, Ui.dp(this, 12), 0, 0);
        v.addView(title);

        if (!pro && reason != null && !reason.isEmpty()) {
            TextView r = Ui.text(this, reason, 14, Ui.DARK, false);
            int q = Ui.dp(this, 14);
            r.setPadding(q, Ui.dp(this, 12), q, Ui.dp(this, 12));
            r.setBackground(Ui.rounded(this, Ui.ORANGE_LIGHT, 14));
            v.addView(r, Ui.matchWrap(this, 14));
        }

        String headline = dev ? "Everything is unlocked on this copy."
                : byCode ? "Unlocked with an access code ✓"
                : business ? "You're on ShowFee Business ✓"
                : pro ? "You're on ShowFee Pro ✓"
                : Pro.hasTrial(chosen) ? "Try every feature free for a month." : "Unlock every feature.";
        TextView h = Ui.text(this, headline, 18, pro || dev ? Ui.GREEN : Ui.PRIMARY, true);
        h.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
        v.addView(h);

        // The two plans. Someone already on Business just sees what they have.
        if (!business) {
            v.addView(plan(Pro.PRODUCT, "ShowFee Pro", new String[]{
                    "Unlimited " + Words.many(this) + " (free plan: " + Pro.FREE_BOOKINGS + " a month)",
                    "Your own booking link – clients book you from WhatsApp, Facebook, Instagram and more",
                    "A notification the moment a request comes in"}, pro && !byCode));
        }
        v.addView(plan(Pro.BUSINESS, "ShowFee Business", new String[]{
                "Everything in Pro",
                "Your logo and brand colour on your booking page",
                "A light or dark page to match your brand",
                "No “Powered by ShowFee”",
                "Your own web address, e.g. book.yourbusiness.com – we set it up for you"}, business));
        v.addView(note("Invoices, reminders and pay links are free for everyone."));

        if (dev) {
            v.addView(note("This copy was installed outside Google Play by the app's owner, so there's nothing to pay."));
        } else if (business || (subscribed && Pro.PRODUCT.equals(chosen))) {
            Button manage = Ui.tonal(this, "Manage or cancel in Google Play");
            manage.setOnClickListener(x -> Pro.manage(this));
            v.addView(manage);
            v.addView(note("Your subscription renews automatically through Google Play. You can cancel any time there."));
        } else {
            if (byCode) {
                v.addView(note("ShowFee Pro is unlocked on this phone for " + Pro.codeDaysLeft(this)
                        + " more days. Nothing was bought and nothing will be charged."));
                if (Pro.PRODUCT.equals(chosen)) chosen = Pro.BUSINESS;
            }
            boolean upgrade = subscribed && Pro.BUSINESS.equals(chosen);
            String price = Pro.priceLine(chosen);
            TextView pr = Ui.text(this, price.isEmpty()
                    ? (waited ? "Google Play isn't available on this phone right now, so plans can't be bought here."
                    : "Getting the price from Google Play…") : price, price.isEmpty() ? 14 : 17,
                    price.isEmpty() ? Ui.GREY : Ui.DARK, !price.isEmpty());
            pr.setPadding(0, Ui.dp(this, 20), 0, 0);
            v.addView(pr);
            Button buy = Ui.primary(this, upgrade ? "Upgrade to Business"
                    : Pro.hasTrial(chosen) ? "Start my free month" : "Subscribe");
            buy.setEnabled(!price.isEmpty());
            buy.setAlpha(price.isEmpty() ? 0.5f : 1f);
            buy.setOnClickListener(x -> {
                if (!Pro.buy(this, chosen)) {
                    Toast.makeText(this, "Google Play isn't ready – please try again in a moment", Toast.LENGTH_LONG).show();
                }
            });
            v.addView(buy);
            v.addView(note((Pro.hasTrial(chosen) && !upgrade
                    ? "You won't be charged if you cancel before the free month ends. " : "")
                    + "Payment is handled by Google Play and renews monthly – cancel any time in the Play Store. "
                    + (upgrade ? "Google Play adjusts what you've already paid for Pro. " : "")
                    + "Your " + Words.many(this) + " and data always stay yours, whatever plan you're on."));
            if (!subscribed) {
                Button later = Ui.quiet(this, "Not now");
                later.setOnClickListener(x -> finish());
                v.addView(later);
            }
            TextView restore = Ui.text(this, "Already subscribed? Tap to check again", 13, Ui.PRIMARY, false);
            restore.setGravity(Gravity.CENTER);
            restore.setPadding(0, Ui.dp(this, 16), 0, 0);
            restore.setOnClickListener(x -> {
                Pro.refresh(this, this::render);
                Toast.makeText(this, "Checking with Google Play…", Toast.LENGTH_SHORT).show();
            });
            v.addView(restore);
            if (!pro) {
                TextView code = Ui.text(this, "Have an access code?", 13, Ui.PRIMARY, false);
                code.setGravity(Gravity.CENTER);
                code.setPadding(0, Ui.dp(this, 14), 0, 0);
                code.setOnClickListener(x -> askCode());
                v.addView(code);
            }
        }
        v.addView(Legal.links(this));
        setContentView(scroll);
        Theme.bars(this);
    }

    /** One plan card. Tap to choose it; the one you have is marked. */
    private LinearLayout plan(String product, String name, String[] perks, boolean have) {
        boolean sel = product.equals(chosen);
        LinearLayout card = Ui.vbox(this, 16);
        android.graphics.drawable.GradientDrawable bg = Ui.rounded(this, Ui.SURFACE, 18);
        bg.setStroke(Ui.dp(this, sel ? 2 : 1), sel ? Ui.PRIMARY : Ui.LINE);
        card.setBackground(bg);
        card.setLayoutParams(Ui.matchWrap(this, 12));
        LinearLayout top = Ui.hbox(this);
        TextView n = Ui.text(this, name, 19, Ui.DARK, true);
        top.addView(n, Ui.weight(1f));
        String price = Pro.priceLine(product);
        String monthly = price.contains("then ") ? price.substring(price.indexOf("then ") + 5) : price;
        top.addView(Ui.text(this, have ? "Your plan ✓" : monthly, 15, have ? Ui.GREEN : Ui.DARK, true));
        card.addView(top);
        for (String s : perks) card.addView(tick(s));
        card.setContentDescription(name + (have ? ", your plan" : sel ? ", selected" : ""));
        card.setOnClickListener(x -> {
            chosen = product;
            render();
        });
        return card;
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
                    int r = Pro.redeem(this, e.getText().toString());
                    if (r == Pro.CODE_OK) {
                        Toast.makeText(this, Pro.lastCodeDays >= 365
                                ? "Thank you for testing! ShowFee Pro is yours free for a year ✓"
                                : "ShowFee Pro unlocked for " + Pro.lastCodeDays + " days ✓", Toast.LENGTH_LONG).show();
                        render();
                    } else if (r == Pro.CODE_USED) {
                        Toast.makeText(this, "This phone has already used that code", Toast.LENGTH_LONG).show();
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
