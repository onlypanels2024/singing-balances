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
 * ShowFee Pro and ShowFee Business: monthly or yearly subscriptions through Google Play. Business includes everything in Pro
 * plus branding on the booking page (logo, colour, light/dark page, no "Powered by ShowFee", own web address).
 * Pro: a monthly subscription through Google Play (price and free trial are set in Play Console).
 * Without it the app still works as a free plan: up to {@link #FREE_BOOKINGS} bookings a month, with invoices,
 * emails, reminders and pay links included. Nobody ever loses access to their own data.
 */
final class Pro {
    static final String PRODUCT = "showfee_pro";
    static final String BUSINESS = "showfee_business";
    static final int FREE_BOOKINGS = 5;
    /** Copies we build and hand out ourselves (not from Google Play) are signed with this key: all unlocked. */
    private static final String DEVELOPER_SIGNATURE = "4Y+anr53U/y8bSlzpzy/MWNi4vs=";

    /** Test builds can force "free" or "pro" here. */
    static String forced;
    /** Test builds can show a pretend price here when Google Play isn't on the phone. */
    static String fakePrice, fakeBusinessPrice, fakeYearly, fakeBusinessYearly;
    static int fakeSaving;

    private static BillingClient client;
    private static final java.util.Map<String, ProductDetails> details = new java.util.HashMap<>();
    /** The subscription this phone has now (for upgrading Pro → Business and for "Manage"). */
    private static String currentProduct, currentToken;
    private static Boolean developer;
    private static Runnable onChange;

    private Pro() {}

    // ---- Status ----

    static boolean isPro(Context c) {
        if ("free".equals(forced)) return codeDaysLeft(c) > 0;
        if ("pro".equals(forced) || "business".equals(forced)) return true;
        return isDeveloperCopy(c) || Prefs.sp(c).getBoolean(Prefs.PRO_ACTIVE, false) || codeDaysLeft(c) > 0;
    }

    /** ShowFee Business (branding). Access codes unlock Pro only. */
    static boolean isBusiness(Context c) {
        if (forced != null) return "business".equals(forced);
        return isDeveloperCopy(c) || Prefs.sp(c).getBoolean(Prefs.BUSINESS_ACTIVE, false);
    }

    // ---- Access codes (for Google's app reviewers, and the owner's guests) ----
    /** SHA-256 of the current code, so the code itself isn't written in the app. Change it to retire a code. */
    private static final String CODE_HASH = "14dae3d125b8c1f30bb3639346f1174d9c3c5f1269bd169c18159562e3e39937";
    static final int CODE_DAYS = 30;
    /** How many days the last code that worked unlocked. */
    static int lastCodeDays = CODE_DAYS;

    static final int CODE_OK = 1, CODE_WRONG = 0, CODE_USED = 2;

    /** Unlocks Pro on this phone for 30 days if the reviewer code is right. Each phone can use it once.
     *  (Testers who already unlocked their free year keep it: it's stored on their phone.) */
    static int redeem(Context c, String code) {
        String hash;
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(code.trim().toUpperCase(java.util.Locale.ROOT).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            hash = sb.toString();
        } catch (Exception e) {
            return CODE_WRONG;
        }
        String usedKey;
        int days;
        if (CODE_HASH.equals(hash)) { usedKey = Prefs.PRO_CODE_USED; days = CODE_DAYS; }
        else return CODE_WRONG;
        if (Prefs.sp(c).getBoolean(usedKey, false)) return CODE_USED;
        long until = Math.max(Prefs.sp(c).getLong(Prefs.PRO_CODE_UNTIL, 0), Dates.today() + days);
        Prefs.sp(c).edit().putLong(Prefs.PRO_CODE_UNTIL, until).putBoolean(usedKey, true).apply();
        lastCodeDays = days;
        return CODE_OK;
    }

    static long codeDaysLeft(Context c) {
        return Math.max(0, Prefs.sp(c).getLong(Prefs.PRO_CODE_UNTIL, 0) - Dates.today());
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
        if (isBusiness(c)) return "ShowFee Business – active";
        if (Prefs.sp(c).getBoolean(Prefs.PRO_ACTIVE, false)) return "Active – all features";
        if (codeDaysLeft(c) > 0) return "Unlocked with a code · " + codeDaysLeft(c) + " days left";
        int n = bookingsInMonth(c, Dates.today(), 0);
        return "Free plan · " + Math.min(n, FREE_BOOKINGS) + " of " + FREE_BOOKINGS + " " + Words.many(c) + " this month";
    }

    /** Bookings (not cancelled, not examples) in the same month as {@code day}, not counting the one being edited. */
    static int bookingsInMonth(Context c, long day, long exceptId) {
        java.time.LocalDate d = java.time.LocalDate.ofEpochDay(day);
        long from = d.withDayOfMonth(1).toEpochDay(), to = d.withDayOfMonth(d.lengthOfMonth()).toEpochDay();
        int n = 0;
        java.util.Set<Long> examples = Examples.ids(c, "g"); // example bookings never use up the free plan
        for (Gig g : Db.get(c).gigsBetween(from, to)) {
            if (!g.isCancelled() && g.id != exceptId && !examples.contains(g.id)) n++;
        }
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
                        .setProductList(java.util.Arrays.asList(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId(PRODUCT).setProductType(BillingClient.ProductType.SUBS).build(),
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId(BUSINESS).setProductType(BillingClient.ProductType.SUBS).build()))
                        .build(),
                (r, result) -> {
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK
                            && !result.getProductDetailsList().isEmpty()) {
                        for (ProductDetails d : result.getProductDetailsList()) details.put(d.getProductId(), d);
                        changed();
                    }
                });
    }

    private static void handle(Context c, List<Purchase> purchases) {
        boolean active = false, business = false;
        for (Purchase p : purchases) {
            boolean isB = p.getProducts().contains(BUSINESS);
            if (!p.getProducts().contains(PRODUCT) && !isB) continue;
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            active = true;
            if (isB) business = true;
            if (isB || currentProduct == null) {
                currentProduct = isB ? BUSINESS : PRODUCT;
                currentToken = p.getPurchaseToken();
            }
            if (!p.isAcknowledged()) {
                // Google refunds purchases that aren't confirmed within 3 days
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.getPurchaseToken()).build(), r -> {});
            }
        }
        if (!active) currentProduct = currentToken = null;
        boolean was = Prefs.sp(c).getBoolean(Prefs.PRO_ACTIVE, false), wasB = Prefs.sp(c).getBoolean(Prefs.BUSINESS_ACTIVE, false);
        Prefs.sp(c).edit().putBoolean(Prefs.PRO_ACTIVE, active).putBoolean(Prefs.BUSINESS_ACTIVE, business).apply();
        if (was != active || wasB != business) changed();
    }

    private static void changed() {
        Runnable r = onChange;
        if (r != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    /** The offer with the free month if there is one, otherwise the normal plan – monthly or yearly.
     *  The plan is recognised by how often it's paid (P1M / P1Y), so a yearly plan is never picked by mistake. */
    private static ProductDetails.SubscriptionOfferDetails bestOffer(String product, boolean yearly) {
        ProductDetails d = details.get(product);
        if (d == null || d.getSubscriptionOfferDetails() == null) return null;
        ProductDetails.SubscriptionOfferDetails best = null;
        for (ProductDetails.SubscriptionOfferDetails o : d.getSubscriptionOfferDetails()) {
            ProductDetails.PricingPhase paid = paidPhase(o);
            if (paid == null || paid.getBillingPeriod().endsWith("Y") != yearly) continue;
            if (best == null) best = o;
            for (ProductDetails.PricingPhase ph : o.getPricingPhases().getPricingPhaseList()) {
                if (ph.getPriceAmountMicros() == 0) return o; // has a free trial
            }
        }
        return best;
    }

    /** The phase that repeats (the real price after any free month). */
    private static ProductDetails.PricingPhase paidPhase(ProductDetails.SubscriptionOfferDetails o) {
        List<ProductDetails.PricingPhase> l = o.getPricingPhases().getPricingPhaseList();
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    /** E.g. "1 month free, then €9.99/month", or "" while Google Play hasn't answered. */
    static String priceLine() {
        return priceLine(PRODUCT, false);
    }

    static String priceLine(String product) {
        return priceLine(product, false);
    }

    static String priceLine(String product, boolean yearly) {
        String fake = BUSINESS.equals(product) ? (yearly ? fakeBusinessYearly : fakeBusinessPrice) : (yearly ? fakeYearly : fakePrice);
        if (fakePrice != null || fakeBusinessPrice != null) return fake == null ? "" : fake;
        ProductDetails.SubscriptionOfferDetails o = bestOffer(product, yearly);
        if (o == null) return "";
        List<ProductDetails.PricingPhase> phases = o.getPricingPhases().getPricingPhaseList();
        String free = "", price = "";
        for (ProductDetails.PricingPhase ph : phases) {
            if (ph.getPriceAmountMicros() == 0) free = period(ph.getBillingPeriod()) + " free, then ";
            else price = ph.getFormattedPrice() + "/" + unit(ph.getBillingPeriod());
        }
        return free + price;
    }

    /** True when Google Play offers a yearly plan for this product. */
    static boolean hasYearly(String product) {
        return !priceLine(product, true).isEmpty();
    }

    /** How much cheaper a year is than 12 months, in whole percent (0 if unknown). Worked out from Google Play's
     *  own prices for this country, so it is always true for what the person will pay. */
    static int yearlySaving(String product) {
        if (fakePrice != null || fakeBusinessPrice != null) return fakeSaving;
        ProductDetails.SubscriptionOfferDetails m = bestOffer(product, false), y = bestOffer(product, true);
        if (m == null || y == null) return 0;
        long month = paidPhase(m).getPriceAmountMicros(), year = paidPhase(y).getPriceAmountMicros();
        if (month <= 0 || year <= 0) return 0;
        return (int) Math.max(0, Math.round(100.0 - year * 100.0 / (month * 12.0)));
    }

    static boolean hasTrial() {
        return hasTrial(PRODUCT, false);
    }

    static boolean hasTrial(String product) {
        return hasTrial(product, false);
    }

    static boolean hasTrial(String product, boolean yearly) {
        return priceLine(product, yearly).contains("free");
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
        return buy(a, PRODUCT, false);
    }

    static boolean buy(Activity a, String product) {
        return buy(a, product, false);
    }

    /** Pro → Business is an upgrade of the same subscription: Google Play adjusts what was already paid. */
    static boolean buy(Activity a, String product, boolean yearly) {
        ProductDetails.SubscriptionOfferDetails o = bestOffer(product, yearly);
        ProductDetails d = details.get(product);
        if (client == null || d == null || o == null) return false;
        BillingFlowParams.Builder b = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(d).setOfferToken(o.getOfferToken()).build()));
        if (currentToken != null && currentProduct != null && !currentProduct.equals(product)) {
            b.setSubscriptionUpdateParams(BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(currentToken)
                    .setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION)
                    .build());
        }
        BillingFlowParams params = b.build();
        BillingResult r = client.launchBillingFlow(a, params);
        return r.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    /** Google Play's own page to change or cancel the subscription. */
    static void manage(Activity a) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://play.google.com/store/account/subscriptions?sku=" + (currentProduct == null ? PRODUCT : currentProduct) + "&package=" + a.getPackageName())));
        } catch (Exception ignored) {
        }
    }
}
