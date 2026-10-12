package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/** Opens from the "Get my free year" link Gary emails to testers (showfee://gift?code=…):
 *  applies the thank-you code with one tap, then shows the ShowFee Pro screen. */
public class GiftActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri u = getIntent().getData();
        String code = u == null ? null : u.getQueryParameter("code");
        int r = code == null ? Pro.CODE_WRONG : Pro.redeem(this, code);
        String msg;
        if (r == Pro.CODE_OK) msg = Pro.lastCodeDays >= 365
                ? "Thank you for testing! ShowFee Pro is yours free for a year ✓"
                : "ShowFee Pro unlocked for " + Pro.lastCodeDays + " days ✓";
        else if (r == Pro.CODE_USED) msg = "This phone has already used that gift";
        else msg = "That gift link isn't valid";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, ProActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
}
