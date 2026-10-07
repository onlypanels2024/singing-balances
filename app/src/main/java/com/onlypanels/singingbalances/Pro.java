package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * ShowFee Pro: a monthly subscription through Google Play (price and free trial are set in Play Console).
 * Without it the app still works as a free plan: up to {@link #FREE_BOOKINGS} bookings a month, and no
 * invoices, emails or pay links. Nobody ever loses access to their own data.
 */
final class Pro {
    static final String PRODUCT = "showfee_pro";
    static final int FREE_BOOKINGS = 5;
    /** Copies we build and hand out ourselves (not from Google Play) are signed with this key: all unlocked. */
    private static final String DEVELOPER_SIGNATURE = "4Y+anr53U/y8bSlzpzy/MWNi4vs=";

    /** Test builds can force "free" or "pro" here. */
    static String forced;
    /** Test builds can show a pretend price here when Google Play isn't on the phone. */
    static String fakePrice;

    private static BillingClient client;
    private static ProductDetails details;
    private static Boolean developer;
    private static Runnable onChange;

    private Pro() {}

    // ---- Status ----

    static boolean isPro(Context c) {
        if ("free".equals(forced)) return false;
        if ("pro".equals(forced)) return true;
        return isDeveloperCopy(c) || Prefs.sp(c).getBoolean(Prefs.PRO_ACTIVE, false);
    }

    static boolean isDeveloperCopy(Context c) {
        if (forced != null) return false;
        if (developer == null) {
            try {
                developer = DEVELOPER_SIGNATURE.equals(MicrosoftAccount.signatureHash(c));
            } catch (Exception e) {
                developer = false;
            }
        }
        return developer;
    }

    /** "ShowFee Pro", "Free trial", "Free plan · 2 of 5 bookings this month"… for the Settings menu. */
    static String summary(Context c) {
        if (isDeveloperCopy(c)) return "All features unlocked (developer copy)";
        if (isPro(c)) return "Active – all features";
        int n = bookingsInMonth(c, Dates.today(), 0);
        return "Free plan · " + Math.min(n, FREE_BOOKINGS) + " of " + FREE_BOOKINGS + " " + Words.many(c) + " this month";
    }

    /** Bookings (not cancelled) in the same month as {@code day}, not counting the one being edited. */
    static int bookingsInMonth(Context c, long day, long exceptId) {
        java.time.LocalDate d = java.time.LocalDate.ofEpochDay(day);
        long from = d.withDayOfMonth(1).toEpochDay(), to = d.withDayOfMonth(d.lengthOfMonth()).toEpochDay();
        int n = 0;
        for (Gig g : Db.get(c).gigsBetween(from, to)) if (!g.isCancelled() && g.id != exceptId) n++;
        return n;
    }

    /** True if Pro, otherwise shows the Pro screen explaining why. */
    static boolean allow(Activity a, String feature) {
        if (isPro(a)) return true;
        open(a, feature);
        return false;
    }

    static void open(Activity a, String reason) {
        a.startActivity(new Intent(a, ProActivity.class).putExtra("reason", reason));
    }

    // ---- Google Play ----

    /** Connects to Google Play and refreshes whether the user is subscribed. Quiet if Play isn't there. */
    static void refresh(Context c, Runnable changed) {
        onChange = changed;
        if (forced != null) return;
        try {
            if (client == null) {
                client = BillingClient.newBuilder(c.getApplicationContext())
                        .setListener((result, purchases) -> {
                            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                                handle(c, purchases);
                            }
                        })
                        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                        .enableAutoServiceReconnection()
                        .build();
            }
            if (client.isReady()) {
                query(c);
                return;
            }
            client.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(BillingResult r) {
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) query(c);
                }

                @Override
                public void onBillingServiceDisconnected() {}
            });
        } catch (Exception ignored) {
            // no Google Play on this phone: keep whatever we knew last
        }
    }

    private static void query(Context c) {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                (r, purchases) -> {
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) handle(c, purchases);
                });
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build()))
                        .build(),
                (r, result) -> {
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && !result.getProductDetailsList().isEmpty()) {
                        details = result.getProductDetailsList().get(0);
                        changed();
                    }
                });
    }

    private static void handle(Context c, List<Purchase> purchases) {
        boolean active = false;
        for (Purchase p : purchases) {
            if (!p.getProducts().contains(PRODUCT)) continue;
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            active = true;
            if (!p.isAcknowledged()) {
                // Google refunds purchases that aren't confirmed within 3 days
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.getPurchaseToken()).build(), r -> {});
            }
        }
        boolean was = Prefs.sp(c).getBoolean(Prefs.PRO_ACTIVE, false);
        Prefs.sp(c).edit().putBoolean(Prefs.PRO_ACTIVE, active).apply();
        if (was != active) changed();
    }

    private static void changed() {
        Runnable r = onChange;
        if (r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    /** The offer with the free month if there is one, otherwise the normal monthly plan. */
    private static ProductDetails.SubscriptionOfferDetails bestOffer() {
        if (details == null || details.getSubscriptionOfferDetails() == null) return null;
        ProductDetails.SubscriptionOfferDetails best = null;
        for (ProductDetails.SubscriptionOfferDetails o : details.getSubscriptionOfferDetails()) {
            if (best == null) best = o;
            for (ProductDetails.PricingPhase ph : o.getPricingPhases().getPricingPhaseList()) {
                if (ph.getPriceAmountMicros() == 0) return o; // has a free trial
            }
        }
        return best;
    }

    /** E.g. "1 month free, then €6.99/month", or "" while Google Play hasn't answered. */
    static String priceLine() {
        if (fakePrice != null) return fakePrice;
        ProductDetails.SubscriptionOfferDetails o = bestOffer();
        if (o == null) return "";
        List<ProductDetails.PricingPhase> phases = o.getPricingPhases().getPricingPhaseList();
        String free = "", price = "";
        for (ProductDetails.PricingPhase ph : phases) {
            if (ph.getPriceAmountMicros() == 0) free = period(ph.getBillingPeriod()) + " free, then ";
            else price = ph.getFormattedPrice() + "/" + unit(ph.getBillingPeriod());
        }
        return free + price;
    }

    static boolean hasTrial() {
        if (fakePrice != null) return fakePrice.contains("free");
        return priceLine().contains("free");
    }

    /** "P1M" -> "1 month", "P7D" -> "7 days", "P1W" -> "1 week". */
    private static String period(String iso) {
        try {
            int n = Integer.parseInt(iso.replaceAll("[^0-9]", ""));
            String u = iso.endsWith("D") ? "day" : iso.endsWith("W") ? "week" : iso.endsWith("Y") ? "year" : "month";
            return n + " " + u + (n == 1 ? "" : "s");
        } catch (Exception e) {
            return "First period";
        }
    }

    private static String unit(String iso) {
        return iso.endsWith("Y") ? "year" : iso.endsWith("W") ? "week" : "month";
    }

    /** Opens Google Play's payment sheet. Returns false if Google Play isn't ready. */
    static boolean buy(Activity a) {
        ProductDetails.SubscriptionOfferDetails o = bestOffer();
        if (client == null || details == null || o == null) return false;
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details).setOfferToken(o.getOfferToken()).build()))
                .build();
        BillingResult r = client.launchBillingFlow(a, params);
        return r.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    /** Google Play's own page to change or cancel the subscription. */
    static void manage(Activity a) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://play.google.com/store/account/subscriptions?sku=" + PRODUCT + "&package=" + a.getPackageName())));
        } catch (Exception ignored) {
        }
    }
}
