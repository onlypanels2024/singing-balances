package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
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

/** Settings › Web dashboard (every plan): switch the online copy on or off. */
public class WebDashActivity extends Activity {
    private static final int REQ_GOOGLE = 4421;
    private LinearLayout page;
    private boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        Theme.apply(this);
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("Web dashboard");
        ScrollView scroll = new ScrollView(this);
        page = Ui.vbox(this, 20);
        scroll.addView(page);
        setContentView(scroll);
        Theme.bars(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        page.removeAllViews();
        page.addView(Ui.icon(this, R.drawable.ic_set_web, Ui.PRIMARY, 44));
        TextView h = Ui.text(this, "Your " + Words.many(this) + " on a computer", 22, Ui.DARK, true);
        h.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        page.addView(h);
        page.addView(Ui.text(this, "See and change your " + Words.many(this) + ", calendar, payments and clients at "
                + "showfee.app/dashboard – on any computer or tablet. Sign in there with the same Google account.", 15, Ui.GREY, false));

        if (!Cloud.signedIn(this)) {
            TextView t = Ui.text(this, "First, sign in with Google so your dashboard is kept under your own account.",
                    15, Ui.DARK, false);
            t.setPadding(0, Ui.dp(this, 18), 0, 0);
            page.addView(t);
            Button g = Ui.googleSignIn(this);
            g.setOnClickListener(v -> startGoogle());
            page.addView(g);
            return;
        }

        LinearLayout card = Ui.card(this, Ui.SURFACE);
        Switch sw = new Switch(this);
        sw.setText("Show my " + Words.many(this) + " on the web dashboard");
        sw.setTextColor(Ui.DARK);
        sw.setTextSize(16);
        sw.setChecked(WebDash.isOn(this));
        sw.setOnCheckedChangeListener((x, on) -> {
            if (busy) return;
            if (on) switchOn();
            else switchOff();
        });
        card.addView(sw);
        String who = Cloud.get(this, Cloud.EMAIL);
        long at = WebDash.sentAt(this);
        TextView status = Ui.text(this, WebDash.isOn(this)
                ? (at > 0 ? "Updated " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT).format(new java.util.Date(at)) : "Waiting to send…")
                + (who.isEmpty() ? "" : "\nSign in on the web as " + who)
                : "Off – nothing is stored online.", 13, Ui.GREY, false);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(status);
        page.addView(card);

        if (WebDash.isOn(this)) {
            Button open = Ui.tonal(this, "Open showfee.app/dashboard");
            open.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WebDash.URL)));
                } catch (Exception e) {
                    toast("No web browser found.");
                }
            });
            page.addView(open);
        }

        TextView note = Ui.text(this, "You can add and change " + Words.many(this) + ", payments, clients and expenses there "
                + "too – changes reach this phone within seconds when it's online. Bank details, sign-ins and app settings are "
                + "never sent. Switch off to delete the online copy.", 12, Ui.GREY, false);
        note.setPadding(0, Ui.dp(this, 16), 0, 0);
        note.setGravity(Gravity.START);
        page.addView(note);
    }

    private void switchOn() {
        busy = true;
        WebDash.setOn(this, true);
        toast("Sending…");
        WebDash.syncNow(this, new Cloud.Done<Void>() {
            @Override public void ok(Void r) {
                busy = false;
                toast("Your dashboard is ready.");
                Cloud.registerDevice(WebDashActivity.this); // so changes made on the web reach this phone straight away
                render();
            }

            @Override public void fail(String reason) {
                busy = false;
                WebDash.setOn(WebDashActivity.this, false);
                toast(reason);
                render();
            }
        });
    }

    private void switchOff() {
        new AlertDialog.Builder(this)
                .setTitle("Switch off the web dashboard?")
                .setMessage("The copy online is deleted. Everything stays on this phone.")
                .setPositiveButton("Switch off", (d, w) -> {
                    busy = true;
                    WebDash.remove(this, new Cloud.Done<Void>() {
                        @Override public void ok(Void r) {
                            busy = false;
                            render();
                        }

                        @Override public void fail(String reason) {
                            busy = false;
                            toast(reason);
                            render();
                        }
                    });
                })
                .setNegativeButton("Cancel", (d, w) -> render())
                .setOnCancelListener(d -> render())
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ Google sign-in (once)

    private GoogleSignInClient google() {
        GoogleSignInOptions o = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(Cloud.WEB_CLIENT_ID).requestEmail().build();
        return GoogleSignIn.getClient(this, o);
    }

    private void startGoogle() {
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
            google().signOut(); // only needed once – this doesn't affect sending email from Gmail
            Cloud.signInWithGoogle(this, idToken, acc.getEmail(), new Cloud.Done<Void>() {
                @Override public void ok(Void r) {
                    render();
                }

                @Override public void fail(String reason) {
                    toast(reason);
                }
            });
        } catch (ApiException e) {
            int code = e.getStatusCode();
            if (code == 12501) return;
            toast(code == 7 ? "No internet connection." : "Google sign-in failed (" + code + "). Please try again.");
        }
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
