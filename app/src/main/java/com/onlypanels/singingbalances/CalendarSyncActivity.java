package com.onlypanels.singingbalances;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Settings › Calendar sync: keep every booking in a calendar on the phone, updated automatically. */
public class CalendarSyncActivity extends Activity {
    private static final int REQ_PERM = 4431;
    private LinearLayout page;
    private boolean busy;

    @Override
    protected void onCreate(Bundle b) {
        Theme.apply(this);
        super.onCreate(b);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("Calendar sync");
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
        page.addView(Ui.icon(this, R.drawable.ic_nav_calendar, Ui.PRIMARY, 44));
        TextView h = Ui.text(this, "Your " + Words.many(this) + " in your calendar", 22, Ui.DARK, true);
        h.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        page.addView(h);
        page.addView(Ui.text(this, "Every " + Words.one(this) + " goes into a calendar on this phone – Google, Samsung or "
                + "Outlook. When you change a " + Words.one(this) + " the calendar changes too, and cancelled or deleted "
                + Words.many(this) + " are taken out. No tapping \"Add to calendar\" any more.", 15, Ui.GREY, false));

        LinearLayout card = Ui.card(this, Ui.SURFACE);
        Switch sw = new Switch(this);
        sw.setText("Keep my " + Words.many(this) + " in my calendar");
        sw.setTextColor(Ui.DARK);
        sw.setTextSize(16);
        sw.setChecked(CalendarSync.isOn(this));
        sw.setOnCheckedChangeListener((x, on) -> {
            if (busy) return;
            if (on) switchOn();
            else switchOff();
        });
        card.addView(sw);
        TextView status = Ui.text(this, CalendarSync.isOn(this)
                ? "Calendar: " + CalendarSync.calendarName(this) + "\n" + CalendarSync.count(this) + " "
                + (CalendarSync.count(this) == 1 ? Words.one(this) : Words.many(this)) + " in it"
                : "Off", 13, Ui.GREY, false);
        status.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(status);
        page.addView(card);

        if (CalendarSync.isOn(this)) {
            Button change = Ui.tonal(this, "Use a different calendar");
            change.setOnClickListener(v -> pick());
            page.addView(change);
        }

        TextView note = Ui.text(this, "Only ShowFee's own events are changed – nothing else in your calendar is touched. "
                + Words.Many(this) + " from more than 90 days ago are left as they are. Changes made in the calendar don't "
                + "come back into ShowFee.", 12, Ui.GREY, false);
        note.setPadding(0, Ui.dp(this, 16), 0, 0);
        note.setGravity(Gravity.START);
        page.addView(note);
    }

    private void switchOn() {
        if (!CalendarSync.allowed(this)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, REQ_PERM);
            return;
        }
        pick();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQ_PERM) return;
        boolean ok = results.length > 0;
        for (int r : results) ok &= r == PackageManager.PERMISSION_GRANTED;
        if (ok) {
            pick();
            return;
        }
        render();
        new AlertDialog.Builder(this)
                .setTitle("Calendar access is off")
                .setMessage("To put your " + Words.many(this) + " in your calendar, ShowFee needs to be allowed to use your "
                        + "calendar. You can allow it in the app settings (Permissions › Calendar).")
                .setPositiveButton("Open app settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", getPackageName(), null)));
                    } catch (Exception ignored) { }
                })
                .setNegativeButton("Not now", null)
                .show();
    }

    /** Choose which calendar on the phone to use. */
    private void pick() {
        List<CalendarSync.Cal> cals = CalendarSync.calendars(this);
        if (cals.isEmpty()) {
            render();
            new AlertDialog.Builder(this)
                    .setTitle("No calendar found")
                    .setMessage("There's no calendar on this phone that ShowFee can add to. Add your Google or Outlook "
                            + "account to the phone's calendar app first, then try again.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] names = new String[cals.size()];
        for (int i = 0; i < names.length; i++) names[i] = cals.get(i).toString();
        new AlertDialog.Builder(this)
                .setTitle("Which calendar?")
                .setItems(names, (d, i) -> {
                    busy = true;
                    Toast.makeText(this, "Adding your " + Words.many(this) + "…", Toast.LENGTH_SHORT).show();
                    CalendarSync.start(this, cals.get(i), () -> {
                        busy = false;
                        Toast.makeText(this, "Your " + Words.many(this) + " are in " + cals.get(i).name + ".", Toast.LENGTH_LONG).show();
                        render();
                    });
                })
                .setOnCancelListener(d -> render())
                .show();
    }

    private void switchOff() {
        new AlertDialog.Builder(this)
                .setTitle("Switch off calendar sync?")
                .setMessage("Do you want to take ShowFee's " + Words.many(this) + " out of " + CalendarSync.calendarName(this)
                        + ", or leave them there?")
                .setPositiveButton("Take them out", (d, w) -> stop(true))
                .setNeutralButton("Leave them", (d, w) -> stop(false))
                .setNegativeButton("Cancel", (d, w) -> render())
                .setOnCancelListener(d -> render())
                .show();
    }

    private void stop(boolean remove) {
        busy = true;
        CalendarSync.stop(this, remove, () -> {
            busy = false;
            render();
        });
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
