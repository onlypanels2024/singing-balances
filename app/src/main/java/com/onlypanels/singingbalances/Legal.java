package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** "Privacy policy · Terms of use" links (Google Play requires the privacy policy to be reachable in the app). */
final class Legal {
    static final String PRIVACY = "https://showfee.app/privacy.html";
    static final String TERMS = "https://showfee.app/terms.html";

    private Legal() {}

    static LinearLayout links(Activity a) {
        LinearLayout row = Ui.hbox(a);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 6));
        row.addView(link(a, "Privacy policy", PRIVACY));
        row.addView(Ui.text(a, "  ·  ", 12, Ui.GREY, false));
        row.addView(link(a, "Terms of use", TERMS));
        return row;
    }

    private static TextView link(Activity a, String label, String url) {
        TextView t = Ui.text(a, label, 12, Ui.PRIMARY, false);
        t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 8));
        t.setOnClickListener(v -> {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
        });
        return t;
    }
}
