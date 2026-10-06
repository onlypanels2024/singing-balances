package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.Context;

import com.microsoft.identity.client.AcquireTokenParameters;
import com.microsoft.identity.client.AcquireTokenSilentParameters;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.IPublicClientApplication;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.SilentAuthenticationCallback;
import com.microsoft.identity.client.exception.MsalClientException;
import com.microsoft.identity.client.exception.MsalException;
import com.microsoft.identity.client.exception.MsalServiceException;
import com.microsoft.identity.client.exception.MsalUiRequiredException;

import java.util.Collections;
import java.util.List;

/**
 * "Sign in with Microsoft" so ShowFee can send emails from the user's own Outlook / Hotmail / Microsoft 365
 * address. Only permission to SEND mail is requested. Microsoft's library keeps the sign-in on the phone.
 */
final class MicrosoftAccount {
    static final List<String> SCOPES = Collections.singletonList("Mail.Send");
    /** Asked for only when the user first adds a booking to their Outlook calendar. */
    static final List<String> CALENDAR_SCOPES = Collections.singletonList("Calendars.ReadWrite");
    private static ISingleAccountPublicClientApplication app;

    private MicrosoftAccount() {}

    static boolean isConnected(Context c) {
        return !Prefs.get(c, Prefs.MS_EMAIL).isEmpty();
    }

    static String email(Context c) {
        return Prefs.get(c, Prefs.MS_EMAIL);
    }

    private interface AppReady {
        void ready(ISingleAccountPublicClientApplication app);
    }

    private static void withApp(Activity a, AppReady r, GoogleAccount.TokenCallback cb) {
        if (app != null) {
            r.ready(app);
            return;
        }
        java.io.File config;
        try {
            config = configFile(a.getApplicationContext());
        } catch (Exception e) {
            cb.fail("Couldn't start Microsoft sign-in (" + e.getMessage() + ")");
            return;
        }
        PublicClientApplication.createSingleAccountPublicClientApplication(a.getApplicationContext(), config,
                new IPublicClientApplication.ISingleAccountApplicationCreatedListener() {
                    @Override
                    public void onCreated(ISingleAccountPublicClientApplication created) {
                        app = created;
                        a.runOnUiThread(() -> r.ready(created));
                    }

                    @Override
                    public void onError(MsalException e) {
                        a.runOnUiThread(() -> cb.fail(friendly(e)));
                    }
                });
    }

    /**
     * Microsoft checks that the sign-in comes back to an address made from the app's signature. Copies installed
     * from Google Play are signed by Google and test copies by us, so the address is worked out on the phone.
     */
    private static java.io.File configFile(Context c) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (java.io.InputStream in = c.getResources().openRawResource(R.raw.msal_config)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
        }
        org.json.JSONObject json = new org.json.JSONObject(sb.toString());
        json.put("redirect_uri", "msauth://" + c.getPackageName() + "/"
                + java.net.URLEncoder.encode(signatureHash(c), "UTF-8"));
        java.io.File f = new java.io.File(c.getNoBackupFilesDir(), "msal_config.json");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write(json.toString().getBytes("UTF-8"));
        }
        return f;
    }

    /** The app's signature in the form Microsoft uses (base64 of its SHA-1). */
    static String signatureHash(Context c) throws Exception {
        android.content.pm.PackageManager pm = c.getPackageManager();
        android.content.pm.Signature[] sigs;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.content.pm.SigningInfo info = pm.getPackageInfo(c.getPackageName(),
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
            sigs = info.hasMultipleSigners() ? info.getApkContentsSigners() : info.getSigningCertificateHistory();
        } else {
            //noinspection deprecation
            sigs = pm.getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_SIGNATURES).signatures;
        }
        byte[] sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(sigs[sigs.length - 1].toByteArray());
        return android.util.Base64.encodeToString(sha1, android.util.Base64.NO_WRAP);
    }

    /** Gets a sending key: silently if already signed in, otherwise Microsoft's sign-in screen (if interactive). */
    static void authorize(Activity a, boolean interactive, GoogleAccount.TokenCallback cb) {
        authorize(a, interactive, SCOPES, cb);
    }

    static void authorize(Activity a, boolean interactive, List<String> scopes, GoogleAccount.TokenCallback cb) {
        withApp(a, app -> new Thread(() -> {
            IAccount acc = null;
            try {
                acc = app.getCurrentAccount().getCurrentAccount();
            } catch (Exception ignored) {
            }
            final IAccount account = acc;
            a.runOnUiThread(() -> {
                if (account == null) {
                    if (interactive) signIn(a, app, scopes, cb);
                    else cb.fail("Please sign in with Microsoft again in Settings.");
                    return;
                }
                app.acquireTokenSilentAsync(new AcquireTokenSilentParameters.Builder()
                        .forAccount(account)
                        .fromAuthority(account.getAuthority())
                        .withScopes(scopes)
                        .withCallback(new SilentAuthenticationCallback() {
                            @Override
                            public void onSuccess(IAuthenticationResult r) {
                                a.runOnUiThread(() -> done(a, r, cb));
                            }

                            @Override
                            public void onError(MsalException e) {
                                a.runOnUiThread(() -> {
                                    if (e instanceof MsalUiRequiredException && interactive) {
                                        interactive(a, app, account, scopes, cb);
                                    } else {
                                        cb.fail(friendly(e));
                                    }
                                });
                            }
                        })
                        .build());
            });
        }).start(), cb);
    }

    private static AuthenticationCallback callback(Activity a, GoogleAccount.TokenCallback cb) {
        return new AuthenticationCallback() {
            @Override
            public void onSuccess(IAuthenticationResult r) {
                a.runOnUiThread(() -> done(a, r, cb));
            }

            @Override
            public void onError(MsalException e) {
                a.runOnUiThread(() -> cb.fail(friendly(e)));
            }

            @Override
            public void onCancel() {
                a.runOnUiThread(() -> cb.fail("Sign-in was cancelled."));
            }
        };
    }

    private static void signIn(Activity a, ISingleAccountPublicClientApplication app, List<String> scopes,
                               GoogleAccount.TokenCallback cb) {
        app.acquireToken(new AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(a)
                .withScopes(scopes)
                .withCallback(callback(a, cb))
                .build());
    }

    private static void interactive(Activity a, ISingleAccountPublicClientApplication app, IAccount account,
                                    List<String> scopes, GoogleAccount.TokenCallback cb) {
        app.acquireToken(new AcquireTokenParameters.Builder()
                .startAuthorizationFromActivity(a)
                .forAccount(account)
                .withScopes(scopes)
                .withCallback(callback(a, cb))
                .build());
    }

    private static void done(Activity a, IAuthenticationResult r, GoogleAccount.TokenCallback cb) {
        String user = r.getAccount() != null ? r.getAccount().getUsername() : "";
        Prefs.set(a, Prefs.MS_EMAIL, user == null || user.isEmpty() ? "your Microsoft account" : user);
        cb.ok(r.getAccessToken());
    }

    /** Disconnects ShowFee from the Microsoft account on this phone. */
    static void signOut(Activity a, Runnable done) {
        Prefs.set(a, Prefs.MS_EMAIL, "");
        withApp(a, app -> app.signOut(new ISingleAccountPublicClientApplication.SignOutCallback() {
            @Override
            public void onSignOut() {
                a.runOnUiThread(done);
            }

            @Override
            public void onError(MsalException e) {
                a.runOnUiThread(done);
            }
        }), new GoogleAccount.TokenCallback() {
            @Override
            public void ok(String t) {
            }

            @Override
            public void fail(String reason) {
                done.run();
            }
        });
    }

    private static String friendly(MsalException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (e instanceof MsalClientException && "io_error".equals(e.getErrorCode())) return "No internet connection.";
        if (e instanceof MsalServiceException) return "Microsoft sign-in problem: " + m;
        return m.isEmpty() ? "Microsoft sign-in failed." : m;
    }
}
