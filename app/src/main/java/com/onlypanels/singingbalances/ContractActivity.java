package com.onlypanels.singingbalances;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.MotionEvent;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * ShowFee Business: write a contract for one booking, sign it, and put it online for the client to sign.
 * Returns to the booking with "send" = the new contract's id, so the booking screen sends the link.
 */
public class ContractActivity extends Activity {
    static final int REQ = 4410;
    private static final int REQ_GOOGLE = 4411;
    private static final int MONEY = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;

    private Gig gig;
    private EditText venue, deposit, text;
    private Button depositDay;
    private long depositDue = -1;
    private String generated = "";
    private SignaturePad pad;
    private boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        Theme.apply(this);
        super.onCreate(b);
        gig = Db.get(this).gig(getIntent().getLongExtra("gig", 0));
        if (gig == null) {
            finish();
            return;
        }
        Money.load(this);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("Contract");
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = Ui.vbox(this, 16);
        scroll.addView(page);
        setContentView(scroll);
        Theme.bars(this);

        page.addView(Ui.text(this, gig.client + " · " + Dates.withWeekday(gig.gigDay), 15, Ui.GREY, false));

        venue = Ui.field(page, "Venue (optional)", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        deposit = Ui.field(page, "Deposit to confirm the date (" + Money.label() + ", optional)", "", MONEY);
        depositDay = Ui.pickerButton(page, "Deposit due by (optional)");
        depositDay.setText("No date");
        depositDay.setOnClickListener(v -> Forms.pickDate(this, depositDue > 0 ? depositDue : Math.max(Dates.today(),
                gig.gigDay - 30), d -> {
            depositDue = d;
            depositDay.setText(Dates.fmt(d));
            regenerate();
        }));

        page.addView(Ui.section(this, "Contract"));
        TextView hint = Ui.text(this, "Filled in from the booking. Change anything for this client only.", 13, Ui.GREY, false);
        page.addView(hint);
        text = new EditText(this);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        text.setSingleLine(false);
        text.setMinLines(8);
        text.setTextColor(Ui.DARK);
        text.setTextSize(14);
        text.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        text.setContentDescription("Contract wording");
        page.addView(text);
        Button tpl = Ui.quiet(this, "Change my template");
        tpl.setOnClickListener(v -> editTemplate());
        page.addView(tpl);

        TextWatcher again = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable e) { regenerate(); }
        };
        venue.addTextChangedListener(again);
        deposit.addTextChangedListener(again);
        regenerate();

        page.addView(Ui.section(this, "Your signature"));
        File mine = Contract.mySignature(this);
        page.addView(Ui.text(this, mine.exists() ? "Your saved signature. Tap Clear to sign again."
                : "Sign in the box with your finger.", 13, Ui.GREY, false));
        pad = new SignaturePad(this);
        pad.setContentDescription("Signature box");
        if (mine.exists()) pad.load(BitmapFactory.decodeFile(mine.getAbsolutePath()));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 150));
        lp.topMargin = Ui.dp(this, 6);
        page.addView(pad, lp);
        Button clear = Ui.quiet(this, "Clear");
        clear.setOnClickListener(v -> pad.clear());
        page.addView(clear);

        Switch confirm = new Switch(this);
        confirm.setText("Mark the booking confirmed when the client signs");
        confirm.setTextColor(Ui.DARK);
        confirm.setChecked(Contract.confirmOnSign(this));
        confirm.setOnCheckedChangeListener((x, on) -> Contract.setConfirmOnSign(this, on));
        confirm.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        page.addView(confirm);

        Button preview = Ui.tonal(this, "Preview PDF");
        preview.setOnClickListener(v -> preview());
        page.addView(preview);
        Button send = Ui.primary(this, "Sign & send to " + firstName(gig.client));
        send.setOnClickListener(v -> send());
        page.addView(send);
        TextView note = Ui.text(this, "Your client gets a link to read and sign it on their phone – no account needed.",
                12, Ui.GREY, false);
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        page.addView(note);
    }

    private static String firstName(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) return "client";
        int sp = t.indexOf(' ');
        return sp > 0 && !t.substring(0, sp).endsWith("&") ? t.substring(0, sp) : t;
    }

    private long depositCents() {
        Long v = Money.parse(deposit.getText().toString());
        return v == null ? 0 : v;
    }

    /** Refills the wording from the template – unless the user has changed it by hand. */
    private void regenerate() {
        String now = text.getText().toString();
        if (!now.isEmpty() && !now.equals(generated)) return;
        generated = Contract.fill(this, Contract.template(this), gig, venue.getText().toString(), depositCents(), depositDue);
        text.setText(generated);
    }

    private void editTemplate() {
        LinearLayout form = Ui.vbox(this, 20);
        form.addView(Ui.text(this, "Used for every new contract. Words in {curly brackets} are filled in for you: "
                + "{performer} {client} {event} {date} {time} {venue} {fee} {deposit}", 13, Ui.GREY, false));
        EditText t = new EditText(this);
        t.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        t.setSingleLine(false);
        t.setMinLines(10);
        t.setTextSize(14);
        t.setText(Contract.template(this));
        form.addView(t);
        ScrollView s = new ScrollView(this);
        s.addView(form);
        new AlertDialog.Builder(this)
                .setTitle("My contract template")
                .setView(s)
                .setPositiveButton("Save", (d, w) -> {
                    Contract.setTemplate(this, t.getText().toString());
                    generated = "";
                    text.setText("");
                    regenerate();
                })
                .setNeutralButton("Reset", (d, w) -> {
                    Contract.setTemplate(this, "");
                    generated = "";
                    text.setText("");
                    regenerate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Contract draft() {
        Contract k = new Contract();
        Contract last = Contract.latest(this, gig.id);
        k.id = Contract.newId(this);
        k.gigId = gig.id;
        k.version = last == null ? 1 : last.version + 1;
        k.text = text.getText().toString().trim();
        k.client = gig.client.trim();
        String me = Prefs.get(this, Prefs.NAME).trim();
        k.performer = me.isEmpty() ? Cloud.get(this, Cloud.NAME).trim() : me;
        k.sentAt = System.currentTimeMillis();
        return k;
    }

    private void preview() {
        Contract k = draft();
        k.status = Contract.SENT;
        k.sentAt = 0; // not signed yet
        try {
            File sig = k.performerSig(this);
            if (!pad.isEmpty()) write(sig, pad.png());
            File pdf = ContractPdf.create(this, k);
            sig.delete();
            Uri uri = ShareProvider.uriFor(pdf);
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open contract"));
        } catch (Exception e) {
            toast("Couldn't make the PDF: " + e.getMessage());
        }
    }

    private void send() {
        if (busy) return;
        if (text.getText().toString().trim().isEmpty()) {
            toast("The contract is empty.");
            return;
        }
        if (Prefs.get(this, Prefs.NAME).trim().isEmpty() && Cloud.get(this, Cloud.NAME).trim().isEmpty()) {
            toast("Add your name in Settings first – it goes on the contract.");
            return;
        }
        if (pad.isEmpty()) {
            toast("Please sign in the box first.");
            return;
        }
        if (!Cloud.signedIn(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Sign in with Google")
                    .setMessage("Contracts are kept online so your client can open and sign them. Sign in with Google once "
                            + "(the same as for your booking link).")
                    .setPositiveButton("Sign in", (d, w) -> startGoogle())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        upload();
    }

    private void upload() {
        Contract k = draft();
        byte[] png = pad.png();
        try {
            write(k.performerSig(this), png);
            write(Contract.mySignature(this), png);
        } catch (Exception e) {
            toast("Couldn't save your signature.");
            return;
        }
        busy = true;
        toast("Sending…");
        Contract previous = Contract.latest(this, gig.id);
        Cloud.createContract(this, k, png, new Cloud.Done<Void>() {
            @Override public void ok(Void r) {
                busy = false;
                Contract.save(ContractActivity.this, k);
                // An older version still waiting to be signed is cancelled, so only the newest can be signed.
                if (previous != null && previous.isSent()) {
                    previous.status = Contract.VOID;
                    Contract.save(ContractActivity.this, previous);
                    Cloud.voidContract(ContractActivity.this, previous.id, null);
                }
                setResult(RESULT_OK, new Intent().putExtra("send", k.id));
                finish();
            }

            @Override public void fail(String reason) {
                busy = false;
                k.performerSig(ContractActivity.this).delete();
                toast(reason);
            }
        });
    }

    private static void write(File f, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
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
            busy = true;
            Cloud.signInWithGoogle(this, idToken, acc.getEmail(), new Cloud.Done<Void>() {
                @Override public void ok(Void r) {
                    busy = false;
                    upload();
                }

                @Override public void fail(String reason) {
                    busy = false;
                    toast(reason);
                }
            });
        } catch (ApiException e) {
            int code = e.getStatusCode();
            if (code == 12501) return; // cancelled
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

    // ------------------------------------------------------------------ signature box

    /** A white box to sign in with a finger. */
    static final class SignaturePad extends View {
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private Bitmap saved;
        private boolean inked;
        private float lx, ly;

        SignaturePad(Context c) {
            super(c);
            ink.setColor(0xFF111111);
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(Ui.dp(c, 2.4f));
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeJoin(Paint.Join.ROUND);
            frame.setColor(0xFFC9CAD3);
            frame.setStyle(Paint.Style.STROKE);
            frame.setStrokeWidth(Ui.dp(c, 1.5f));
            setBackground(Ui.rounded(c, Color.WHITE, 12));
        }

        void load(Bitmap b) {
            saved = b;
            inked = b != null;
            invalidate();
        }

        void clear() {
            saved = null;
            path.reset();
            inked = false;
            invalidate();
        }

        boolean isEmpty() {
            return !inked;
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (saved != null) {
                float s = Math.min((float) getWidth() / saved.getWidth(), (float) getHeight() / saved.getHeight());
                int w = Math.round(saved.getWidth() * s), h = Math.round(saved.getHeight() * s);
                c.drawBitmap(saved, null, new Rect(0, (getHeight() - h) / 2, w, (getHeight() + h) / 2), null);
            }
            c.drawPath(path, ink);
            float r = Ui.dp(getContext(), 12);
            c.drawRoundRect(1, 1, getWidth() - 1, getHeight() - 1, r, r, frame);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    path.moveTo(x, y);
                    lx = x;
                    ly = y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    path.quadTo(lx, ly, (x + lx) / 2, (y + ly) / 2);
                    lx = x;
                    ly = y;
                    inked = true;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    path.lineTo(x, y);
                    getParent().requestDisallowInterceptTouchEvent(false);
                    invalidate();
                    return true;
            }
            return false;
        }

        /** The signature as a small PNG on white (480 × 150). */
        byte[] png() {
            int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
            Bitmap full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(full);
            c.drawColor(Color.WHITE);
            if (saved != null) {
                float s = Math.min((float) w / saved.getWidth(), (float) h / saved.getHeight());
                int sw = Math.round(saved.getWidth() * s), sh = Math.round(saved.getHeight() * s);
                c.drawBitmap(saved, null, new Rect(0, (h - sh) / 2, sw, (h + sh) / 2), null);
            }
            c.drawPath(path, ink);
            Bitmap small = Bitmap.createScaledBitmap(full, 480, 150, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            small.compress(Bitmap.CompressFormat.PNG, 100, out);
            return out.toByteArray();
        }
    }
}
