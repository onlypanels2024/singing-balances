package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Home screen: total owed, and the list of gigs. */
public class MainActivity extends Activity {
    private static final int REQ_EXPORT = 1;
    private static final int MENU_EXPORT = 1;

    private boolean showAll = false;
    private final List<Gig> items = new ArrayList<>();
    private TextView totalView, subView, emptyView, tabUnpaid, tabAll;
    private GigAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.WHITE);

        // Header: total owed
        LinearLayout header = Ui.vbox(this, 20);
        header.setBackgroundColor(Ui.PRIMARY);
        header.addView(Ui.text(this, "Still owed to you", 14, 0xDDFFFFFF, false));
        totalView = Ui.text(this, "", 38, Ui.WHITE, true);
        header.addView(totalView);
        subView = Ui.text(this, "", 14, 0xDDFFFFFF, false);
        header.addView(subView);
        root.addView(header);

        // Tabs
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabUnpaid = tab("Unpaid");
        tabAll = tab("All gigs");
        tabUnpaid.setOnClickListener(v -> { showAll = false; reload(); });
        tabAll.setOnClickListener(v -> { showAll = true; reload(); });
        tabs.addView(tabUnpaid);
        tabs.addView(tabAll);
        root.addView(tabs);

        // List
        FrameLayout frame = new FrameLayout(this);
        ListView list = new ListView(this);
        adapter = new GigAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) ->
                startActivity(new Intent(this, GigActivity.class).putExtra("id", items.get(pos).id)));
        emptyView = Ui.text(this, "", 16, Ui.GREY, false);
        emptyView.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 32);
        emptyView.setPadding(p, p, p, p);
        frame.addView(list, new FrameLayout.LayoutParams(-1, -1));
        frame.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(emptyView);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Add button
        LinearLayout bottom = Ui.vbox(this, 12);
        bottom.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 16));
        Button add = Ui.button(this, "+  Add a gig", Ui.PRIMARY, Ui.WHITE);
        add.setOnClickListener(v -> Forms.editGig(this, null, id -> reload()));
        bottom.addView(add);
        root.addView(bottom);

        setContentView(root);
    }

    private TextView tab(String label) {
        TextView t = Ui.text(this, label, 15, Ui.DARK, false);
        t.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 14);
        t.setPadding(p, p, p, p);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        return t;
    }

    private void styleTab(TextView t, boolean selected) {
        t.setBackgroundColor(selected ? Ui.PRIMARY_LIGHT : Ui.WHITE);
        t.setTextColor(selected ? Ui.PRIMARY : Ui.GREY);
        t.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        Db db = Db.get(this);
        List<Gig> unpaid = db.gigs(true);
        long total = 0, overdue = 0;
        int overdueCount = 0;
        for (Gig g : unpaid) {
            total += g.balance();
            if (g.isOverdue()) {
                overdue += g.balance();
                overdueCount++;
            }
        }
        totalView.setText(Money.fmt(total));
        if (unpaid.isEmpty()) {
            subView.setText("All paid up 🎉");
        } else {
            String s = unpaid.size() + (unpaid.size() == 1 ? " unpaid gig" : " unpaid gigs");
            if (overdueCount > 0) s += "  ·  " + Money.fmt(overdue) + " overdue";
            subView.setText(s);
        }

        items.clear();
        items.addAll(showAll ? db.gigs(false) : unpaid);
        adapter.notifyDataSetChanged();
        styleTab(tabUnpaid, !showAll);
        styleTab(tabAll, showAll);
        emptyView.setText(showAll
                ? "No gigs yet.\nTap “Add a gig” to log your first one."
                : "Nobody owes you anything right now.");
    }

    // ---- CSV export (backup / open in Excel) ----

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_EXPORT, 0, "Export to spreadsheet (CSV)");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_EXPORT) {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_TITLE, "singing-balances-" + Dates.iso(Dates.today()) + ".csv");
            startActivityForResult(i, REQ_EXPORT);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("Could not open file");
            out.write(buildCsv().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Exported", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String buildCsv() {
        StringBuilder sb = new StringBuilder("﻿"); // lets Excel read € correctly
        sb.append("Client,Email,Event,Gig date,Due date,Fee,Paid,Owed,Status,Notes\n");
        for (Gig g : Db.get(this).gigs(false)) {
            sb.append(csv(g.client)).append(',')
                    .append(csv(g.email)).append(',')
                    .append(csv(g.event)).append(',')
                    .append(Dates.iso(g.gigDay)).append(',')
                    .append(Dates.iso(g.dueDay)).append(',')
                    .append(Money.plain(g.feeCents)).append(',')
                    .append(Money.plain(g.paidCents)).append(',')
                    .append(Money.plain(g.balance())).append(',')
                    .append(g.isPaid() ? "Paid" : g.isOverdue() ? "Overdue" : "Unpaid").append(',')
                    .append(csv(g.notes)).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // ---- List rows ----

    private class GigAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int pos) { return items.get(pos); }
        @Override public long getItemId(int pos) { return items.get(pos).id; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            Gig g = items.get(pos);
            MainActivity c = MainActivity.this;

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int h = Ui.dp(c, 16), v = Ui.dp(c, 12);
            row.setPadding(h, v, h, v);

            LinearLayout left = new LinearLayout(c);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(Ui.text(c, g.title(), 16, Ui.DARK, true));
            left.addView(Ui.text(c, "Gig " + Dates.fmt(g.gigDay), 13, Ui.GREY, false));
            left.addView(Ui.text(c, g.status(), 13, g.statusColor(), g.isOverdue()));
            row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

            String amount = g.isPaid() ? Money.fmt(g.feeCents) : Money.fmt(g.balance());
            TextView right = Ui.text(c, amount, 18, g.isPaid() ? Ui.GREEN : g.isOverdue() ? Ui.RED : Ui.DARK, true);
            right.setPadding(Ui.dp(c, 12), 0, 0, 0);
            row.addView(right);
            return row;
        }
    }
}
