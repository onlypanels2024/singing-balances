package com.onlypanels.singingbalances;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;

import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;

import java.util.Arrays;

/**
 * "Sign in with Google" so ShowFee can send emails from the user's own Gmail address.
 * Only the permission to SEND email is requested – ShowFee can't read anyone's inbox.
 * Nothing is stored except the email address, for display; Google hands out a fresh
 * short-lived key each time something is sent.
 */
final class GoogleAccount {
    static final String SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    static final int REQ_AUTH = 4801;
    static final int REQ_PICK = 4802;
    /** Account the user just picked in the account list, until Google confirms the permission. */
    private static String picked;

    interface TokenCallback {
        void ok(String accessToken);

        void fail(String reason);
    }

    private static TokenCallback pending;

    private GoogleAccount() {}

    static boolean isConnected(android.content.Context c) {
        return !Prefs.get(c, Prefs.GOOGLE_EMAIL).isEmpty();
    }

    static String email(android.content.Context c) {
        return Prefs.get(c, Prefs.GOOGLE_EMAIL);
    }

    private static AuthorizationRequest request(android.content.Context c) {
        AuthorizationRequest.Builder b = AuthorizationRequest.builder()
                .setRequestedScopes(Arrays.asList(new Scope(SEND_SCOPE), new Scope("email")));
        // Always ask for the account the user chose, so sending uses that account and no other
        String email = picked != null ? picked : Prefs.get(c, Prefs.GOOGLE_EMAIL);
        if (email != null && email.contains("@")) b.setAccount(new Account(email, "com.google"));
        return b.build();
    }

    /**
     * "Sign in with Google" in Settings: first shows the phone's list of Google accounts, then
     * Google's permission screen for the chosen account (if it hasn't given permission yet).
     */
    static void chooseAccount(Activity a, TokenCallback cb) {
        try {
            Intent pick = AccountManager.newChooseAccountIntent(null, null, new String[]{"com.google"},
                    null, null, null, null);
            pending = cb;
            a.startActivityForResult(pick, REQ_PICK);
        } catch (Exception e) {
            pending = null;
            authorize(a, true, cb);
        }
    }

    /**
     * Gets a sending key. The first time, Google shows its account picker and permission screen
     * (interactive must be true); after that it's silent.
     */
    static void authorize(Activity a, boolean interactive, TokenCallback cb) {
        Identity.getAuthorizationClient(a).authorize(request(a))
                .addOnSuccessListener(result -> {
                    if (result.hasResolution()) {
                        if (!interactive || result.getPendingIntent() == null) {
                            cb.fail("Please sign in with Google again in Settings.");
                            return;
                        }
                        pending = cb;
                        try {
                            a.startIntentSenderForResult(result.getPendingIntent().getIntentSender(),
                                    REQ_AUTH, null, 0, 0, 0);
                        } catch (Exception e) {
                            pending = null;
                            cb.fail("Couldn't open Google sign-in: " + e.getMessage());
                        }
                    } else {
                        remember(a, result);
                        cb.ok(result.getAccessToken());
                    }
                })
                .addOnFailureListener(e -> cb.fail(friendly(e)));
    }

    /** Call from onActivityResult. Returns true if it was Google's sign-in result. */
    static boolean handleResult(Activity a, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK) {
            TokenCallback cb = pending;
            pending = null;
            if (cb == null) return true;
            String name = data == null ? null : data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            if (resultCode != Activity.RESULT_OK || name == null) {
                cb.fail("Sign-in was cancelled.");
                return true;
            }
            picked = name;
            authorize(a, true, cb);
            return true;
        }
        if (requestCode != REQ_AUTH) return false;
        TokenCallback cb = pending;
        pending = null;
        if (cb == null) return true;
        if (resultCode != Activity.RESULT_OK || data == null) {
            cb.fail("Sign-in was cancelled.");
            return true;
        }
        try {
            AuthorizationResult result = Identity.getAuthorizationClient(a).getAuthorizationResultFromIntent(data);
            remember(a, result);
            cb.ok(result.getAccessToken());
        } catch (Exception e) {
            cb.fail(friendly(e));
        }
        return true;
    }

    private static void remember(Activity a, AuthorizationResult result) {
        GoogleSignInAccount acc = result.toGoogleSignInAccount();
        String email = acc != null && acc.getEmail() != null ? acc.getEmail()
                : picked != null ? picked : "your Google account";
        picked = null;
        Prefs.set(a, Prefs.GOOGLE_EMAIL, email);
    }

    /** Disconnects ShowFee from the Google account (removes its permission to send). */
    static void signOut(Activity a, Runnable done) {
        Prefs.set(a, Prefs.GOOGLE_EMAIL, "");
        GoogleSignInOptions o = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build();
        picked = null;
        // Remove the permission and forget the account, so the next sign-in starts fresh
        com.google.android.gms.auth.api.signin.GoogleSignInClient g = GoogleSignIn.getClient(a, o);
        g.revokeAccess().addOnCompleteListener(t -> g.signOut().addOnCompleteListener(t2 -> done.run()));
    }

    private static String friendly(Exception e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("10:") || m.contains("DEVELOPER_ERROR")) {
            return "Google didn't recognise this copy of ShowFee (setup mismatch). Use your email app for now.";
        }
        if (m.contains("7:") || m.toLowerCase().contains("network")) return "No internet connection.";
        return m.isEmpty() ? "Google sign-in failed." : m;
    }
}
