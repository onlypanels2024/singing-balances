package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

import org.json.JSONArray;

import java.util.List;

/**
 * Settings → Your booking link: set up showfee.app/book/&lt;name&gt;, share it, pause it, choose the days you
 * work and write your own questions for clients. The owner signs in with Google once; clients never sign in.
 */
public class BookingLinkActivity extends Activity {
    private static final int REQ_GOOGLE = 4901;
    private static final String[] DAY_LETTERS = {"M", "T", "W", "T", "F", "S", "S"};
    private static final String[] DAY_NAMES = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private LinearLayout page;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Theme.apply(this);
        super.onCreate(savedInstanceState);
        setTitle("Your booking link");
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        ScrollView scroll = new ScrollView(this);
        page = Ui.vbox(this, 16);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(page);
        setContentView(scroll);
        Theme.bars(this);
        render();
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
        if (!Cloud.signedIn(this)) intro();
        else if (!Cloud.hasLink(this)) chooseLink(true);
        else settings();
    }

    // ------------------------------------------------------------------ first time

    private void intro() {
        page.addView(spacer(8));
        page.addView(Ui.text(this, "Let clients book you with one link", 20, Ui.DARK, true));
        TextView t = Ui.text(this, "Share your link on WhatsApp, Facebook, Instagram or anywhere else. Clients pick a free date and send "
                + "you a request – they don't need an account or the app. Requests appear here in ShowFee for you to accept or decline.",
                15, Ui.GREY, false);
        t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        page.addView(t);
        TextView why = Ui.text(this, "Sign in with Google once, so only you can see the requests sent to your link.", 14, Ui.GREY, false);
        why.setPadding(0, 0, 0, Ui.dp(this, 4));
        page.addView(why);
        Button g = Ui.googleSignIn(this);
        g.setOnClickListener(v -> startGoogle());
        page.addView(g);
        TextView note = Ui.text(this, "Your link page shows your name, the days you work and which dates are free – never your clients or fees.",
                12, Ui.GREY, false);
        note.setPadding(0, Ui.dp(this, 18), 0, 0);
        note.setGravity(Gravity.CENTER);
        page.addView(note);
    }

    private GoogleSignInClient google() {
        GoogleSignInOptions o = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(Cloud.WEB_CLIENT_ID).requestEmail().build();
        return GoogleSignIn.getClient(this, o);
    }

    private void startGoogle() {
        if (busy) return;
        try {
            startActivityForResult(google().getSignInIntent(), REQ_GOOGLE);
        } catch (Exception e) {
            toast("Couldn't open Google sign-in.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_GOOGLE) return;
        try {
            GoogleSignInAccount acc = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            String idToken = acc == null ? null : acc.getIdToken();
            if (idToken == null) {
                toast("Google sign-in didn't finish. Please try again.");
                return;
            }
            String display = acc.getDisplayName() == null ? "" : acc.getDisplayName();
            google().signOut(); // only needed once – this doesn't affect sending email from Gmail
            busy = true;
            toast("Signing in…");
            Cloud.signInWithGoogle(this, idToken, acc.getEmail(), new Cloud.Done<Void>() {
                @Override public void ok(Void r) {
                    busy = false;
                    if (Cloud.get(BookingLinkActivity.this, Cloud.NAME).isEmpty()) {
                        String n = Prefs.get(BookingLinkActivity.this, Prefs.NAME);
                        Cloud.sp(BookingLinkActivity.this).edit().putString(Cloud.NAME, n.isEmpty() ? display : n).apply();
                    }
                    render();
                }

                @Override public void fail(String reason) {
                    busy = false;
                    toast(reason);
                }
            });
        } catch (ApiException e) {
            int code = e.getStatusCode();
            if (code == 12501) return; // cancelled
            toast(code == 7 ? "No internet connection." : code == 10
                    ? "Google didn't recognise this copy of ShowFee. Please update the app from Google Play."
                    : "Google sign-in failed (" + code + "). Please try again.");
        }
    }

    /** Choose (or change) the link name, plus the name shown on the page. */
    private void chooseLink(boolean first) {
        String current = Cloud.get(this, Cloud.SLUG);
        String name = Cloud.get(this, Cloud.NAME);
        if (name.isEmpty()) name = Prefs.get(this, Prefs.NAME);
        String tag = Cloud.hasLink(this) ? Cloud.get(this, Cloud.TAGLINE) : Cloud.defaultTagline(this);

        LinearLayout form = Ui.vbox(this, 0);
        if (first) {
            page.addView(spacer(8));
            page.addView(Ui.text(this, "Choose your link", 20, Ui.DARK, true));
            TextView sub = Ui.text(this, "Signed in as " + Cloud.get(this, Cloud.EMAIL), 13, Ui.GREY, false);
            sub.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
            page.addView(sub);
            page.addView(form);
        }
        EditText slug = Ui.field(form, "Your link: " + Cloud.SITE + "…", current.isEmpty() ? Cloud.suggestSlug(name) : current,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        TextView preview = Ui.text(this, "", 13, Ui.PRIMARY, true);
        preview.setPadding(0, Ui.dp(this, 4), 0, 0);
        form.addView(preview);
        Runnable upd = () -> preview.setText(Cloud.SITE + slug.getText().toString().trim().toLowerCase(java.util.Locale.ROOT));
        upd.run();
        slug.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { upd.run(); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        EditText shown = Ui.field(form, "Your name on the page", name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText line = Ui.field(form, "Line under your name (e.g. Singer · Malta)", tag,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        Runnable save = () -> {
            String s = slug.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            String n = shown.getText().toString().trim();
            String why = Cloud.slugProblem(this, s);
            if (!why.isEmpty()) {
                slug.setError(why);
                return;
            }
            if (n.isEmpty()) {
                shown.setError("Clients see this name on your page.");
                return;
            }
            if (busy) return;
            busy = true;
            Cloud.checkSlug(this, s, new Cloud.Done<Boolean>() {
                @Override public void ok(Boolean free) {
                    if (!free) {
                        busy = false;
                        slug.setError("Someone already has this link. Try another, e.g. " + s + "-malta");
                        return;
                    }
                    Cloud.createPage(BookingLinkActivity.this, s, n, line.getText().toString().trim(), new Cloud.Done<Void>() {
                        @Override public void ok(Void r) {
                            busy = false;
                            if (dialog[0] != null) dialog[0].dismiss();
                            toast(first ? "Your booking link is ready" : "Saved");
                            Cloud.registerDevice(BookingLinkActivity.this);
                            render();
                        }

                        @Override public void fail(String reason) {
                            busy = false;
                            toast(reason);
                        }
                    });
                }

                @Override public void fail(String reason) {
                    busy = false;
                    toast(reason);
                }
            });
        };
        if (first) {
            Button go = Ui.primary(this, "Create my link");
            go.setLayoutParams(Ui.matchWrap(this, 20));
            go.setOnClickListener(v -> save.run());
            page.addView(go);
            Button out = Ui.quiet(this, "Cancel");
            out.setLayoutParams(Ui.matchWrap(this, 8));
            out.setOnClickListener(v -> {
                Cloud.sp(this).edit().clear().apply();
                finish();
            });
            page.addView(out);
        } else {
            ScrollView sv = new ScrollView(this);
            sv.addView(form);
            dialog[0] = new AlertDialog.Builder(this).setTitle("Your link and name").setView(sv)
                    .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
            dialog[0].setOnShowListener(x -> dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> save.run()));
            dialog[0].show();
        }
    }

    private final AlertDialog[] dialog = new AlertDialog[1];

    // ------------------------------------------------------------------ settings

    private void settings() {
        String link = Cloud.link(this);

        // Link card
        LinearLayout card = card();
        LinearLayout top = Ui.hbox(this);
        top.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 4));
        LinearLayout texts = Ui.vbox(this, 0);
        texts.addView(Ui.text(this, link, 16, Ui.PRIMARY, true));
        String tag = Cloud.get(this, Cloud.TAGLINE);
        texts.addView(Ui.text(this, Cloud.get(this, Cloud.NAME) + (tag.isEmpty() ? "" : " · " + tag), 13, Ui.GREY, false));
        top.addView(texts, Ui.weight(1f));
        TextView edit = Ui.text(this, "Edit", 14, Ui.PRIMARY, true);
        edit.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        edit.setOnClickListener(v -> chooseLink(false));
        top.addView(edit);
        top.setOnClickListener(v -> chooseLink(false));
        card.addView(top);
        LinearLayout btns = Ui.hbox(this);
        btns.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 14));
        Button share = Ui.primary(this, "Share link");
        share.setOnClickListener(v -> share());
        Button copy = Ui.tonal(this, "Copy");
        copy.setOnClickListener(v -> {
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Booking link", "https://" + link));
            toast("Link copied");
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1f), l2 = new LinearLayout.LayoutParams(0, -2, 1f);
        l2.leftMargin = Ui.dp(this, 10);
        btns.addView(share, l1);
        btns.addView(copy, l2);
        card.addView(btns);
        page.addView(card);

        // Booking requests
        LinearLayout rq = card();
        rq.setLayoutParams(Ui.matchWrap(this, 12));
        LinearLayout rrow = Ui.hbox(this);
        rrow.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        int n = Cloud.newCount(this);
        LinearLayout rt = Ui.vbox(this, 0);
        rt.addView(Ui.text(this, "Booking requests", 16, Ui.DARK, true));
        rt.addView(Ui.text(this, n == 0 ? "No new requests" : n == 1 ? "1 new – tap to answer" : n + " new – tap to answer",
                13, n == 0 ? Ui.GREY : Ui.ORANGE, n > 0));
        rrow.addView(rt, Ui.weight(1f));
        rrow.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.FAINT, 22));
        rrow.setBackground(Ui.touchable(null, Ui.ripple()));
        rrow.setOnClickListener(v -> startActivity(new Intent(this, RequestsActivity.class)));
        rq.addView(rrow);
        page.addView(rq);

        // Taking bookings
        LinearLayout acc = card();
        acc.setLayoutParams(Ui.matchWrap(this, 12));
        LinearLayout row = Ui.hbox(this);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        LinearLayout at = Ui.vbox(this, 0);
        at.addView(Ui.text(this, "Taking bookings", 16, Ui.DARK, true));
        at.addView(Ui.text(this, Cloud.accepting(this) ? "Clients can send you requests" : "Paused – clients see that you're not taking bookings",
                13, Ui.GREY, false));
        row.addView(at, Ui.weight(1f));
        Switch sw = new Switch(this);
        sw.setChecked(Cloud.accepting(this));
        sw.setContentDescription("Taking bookings");
        sw.setOnCheckedChangeListener((b, on) -> {
            Cloud.sp(this).edit().putBoolean(Cloud.ACCEPTING, on).apply();
            push(() -> render());
        });
        row.addView(sw);
        acc.addView(row);
        page.addView(acc);

        // Days you work
        page.addView(overline("Days you work"));
        LinearLayout dcard = card();
        LinearLayout days = Ui.hbox(this);
        days.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 8));
        List<Integer> on = Cloud.days(this);
        for (int i = 0; i < 7; i++) {
            final int d = i + 1;
            boolean sel = on.contains(d);
            TextView chip = Ui.text(this, DAY_LETTERS[i], 14, sel ? Ui.ON_PRIMARY : Ui.GREY, true);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(sel ? Ui.rounded(this, Ui.PRIMARY, 12) : Ui.outlined(this, Ui.SURFACE, 12));
            chip.setContentDescription(DAY_NAMES[i] + (sel ? ", you work" : ", you don't work"));
            chip.setOnClickListener(v -> toggleDay(d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            days.addView(chip, lp);
        }
        dcard.addView(days);
        TextView dn = Ui.text(this, "Dates you already have a " + Words.one(this) + " show as not available automatically.", 13, Ui.GREY, false);
        dn.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12));
        dcard.addView(dn);
        page.addView(dcard);

        // Your questions
        page.addView(overline("Your questions"));
        LinearLayout qcard = card();
        List<String> qs = Cloud.questions(this);
        for (int i = 0; i < qs.size(); i++) {
            final int idx = i;
            LinearLayout q = Ui.hbox(this);
            q.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
            TextView label = Ui.text(this, qs.get(i), 16, Ui.DARK, false);
            label.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            label.setOnClickListener(v -> editQuestion(idx));
            q.addView(label, Ui.weight(1f));
            TextView x = Ui.text(this, "✕", 18, Ui.GREY, false);
            x.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            x.setContentDescription("Remove " + qs.get(i));
            x.setOnClickListener(v -> removeQuestion(idx));
            q.addView(x);
            qcard.addView(q);
            qcard.addView(line());
        }
        if (qs.size() < Cloud.MAX_QUESTIONS) {
            TextView add = Ui.text(this, "+  Add a question", 15, Ui.PRIMARY, true);
            add.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            add.setOnClickListener(v -> editQuestion(-1));
            qcard.addView(add);
        } else {
            TextView max = Ui.text(this, "That's the most questions (6).", 13, Ui.GREY, false);
            max.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
            qcard.addView(max);
        }
        page.addView(qcard);
        TextView qn = Ui.text(this, "Clients always give their name and phone or email, and can add a message. "
                + "Tap a question to rename it. Clients can leave your questions blank.", 13, Ui.GREY, false);
        qn.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), 0);
        page.addView(qn);

        Button preview = Ui.tonal(this, "See my page");
        preview.setLayoutParams(Ui.matchWrap(this, 20));
        preview.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://" + Cloud.link(this))));
            } catch (Exception e) {
                toast("No web browser found.");
            }
        });
        page.addView(preview);

        TextView signed = Ui.text(this, "Signed in as " + Cloud.get(this, Cloud.EMAIL), 12, Ui.GREY, false);
        signed.setGravity(Gravity.CENTER);
        signed.setPadding(0, Ui.dp(this, 20), 0, 0);
        page.addView(signed);
        TextView remove = Ui.text(this, "Remove my booking link", 14, Ui.RED, true);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        remove.setOnClickListener(v -> confirmRemove());
        page.addView(remove);
    }

    private void toggleDay(int d) {
        List<Integer> on = Cloud.days(this);
        if (on.contains(d)) {
            if (on.size() == 1) {
                toast("Keep at least one day – or turn off Taking bookings to pause your link.");
                return;
            }
            on.remove(Integer.valueOf(d));
        } else on.add(d);
        java.util.Collections.sort(on);
        StringBuilder sb = new StringBuilder();
        for (int x : on) sb.append(sb.length() == 0 ? "" : ",").append(x);
        Cloud.sp(this).edit().putString(Cloud.DAYS, sb.toString()).apply();
        render();
        push(null);
    }

    private void editQuestion(int idx) {
        List<String> qs = Cloud.questions(this);
        LinearLayout form = Ui.vbox(this, 20);
        EditText f = Ui.field(form, "Question shown to clients (e.g. Service, Venue, Number of guests)", idx >= 0 ? qs.get(idx) : "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        f.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(Cloud.QUESTION_MAX_LEN)});
        AlertDialog d = new AlertDialog.Builder(this).setTitle(idx >= 0 ? "Rename question" : "Add a question").setView(form)
                .setPositiveButton("Save", null).setNegativeButton("Cancel", null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String s = f.getText().toString().trim();
            if (s.isEmpty()) {
                f.setError("Type the question, e.g. Venue");
                return;
            }
            if (idx >= 0) qs.set(idx, s);
            else qs.add(s);
            saveQuestions(qs);
            d.dismiss();
        }));
        d.show();
    }

    private void removeQuestion(int idx) {
        List<String> qs = Cloud.questions(this);
        new AlertDialog.Builder(this).setTitle("Remove “" + qs.get(idx) + "”?")
                .setMessage("Clients won't be asked this any more. Requests you already have keep their answers.")
                .setPositiveButton("Remove", (d, w) -> {
                    qs.remove(idx);
                    saveQuestions(qs);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void saveQuestions(List<String> qs) {
        Cloud.sp(this).edit().putString(Cloud.QUESTIONS, new JSONArray(qs).toString()).apply();
        render();
        push(null);
    }

    /** Sends the settings to the booking page; tells the user if it didn't work. */
    private void push(Runnable after) {
        Cloud.savePage(this, new Cloud.Done<Void>() {
            @Override public void ok(Void r) {
                if (after != null) after.run();
            }

            @Override public void fail(String reason) {
                toast("Not saved to your page yet: " + reason);
                if (after != null) after.run();
            }
        });
    }

    private void share() {
        String name = Cloud.get(this, Cloud.NAME);
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Book " + (name.isEmpty() ? "me" : name.split(" ")[0]) + " here: https://" + Cloud.link(this));
        startActivity(Intent.createChooser(i, "Share your booking link"));
    }

    private void confirmRemove() {
        new AlertDialog.Builder(this).setTitle("Remove your booking link?")
                .setMessage("Your page " + Cloud.link(this) + " stops working and the requests sent to it are deleted. "
                        + "Your " + Words.many(this) + " in ShowFee are not affected. You can make a new link any time.")
                .setPositiveButton("Remove", (d, w) -> Cloud.removeLink(this, new Cloud.Done<Void>() {
                    @Override public void ok(Void r) {
                        toast("Booking link removed");
                        render();
                    }

                    @Override public void fail(String reason) {
                        toast(reason);
                    }
                }))
                .setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ small helpers

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.outlined(this, Ui.SURFACE, 16));
        c.setClipToOutline(true);
        return c;
    }

    private TextView overline(String s) {
        TextView t = Ui.overline(this, s);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 20), 0, Ui.dp(this, 8));
        return t;
    }

    private View line() {
        View v = new View(this);
        v.setBackgroundColor(Ui.LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1) / 2)));
        return v;
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(this, dp)));
        return v;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
