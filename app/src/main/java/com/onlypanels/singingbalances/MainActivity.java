package com.onlypanels.singingbalances;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
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
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Home: four tabs – Gigs, Calendar, Clients, Money. */
public class MainActivity extends Activity {
    private static final int MENU_SETTINGS = 1;
    private static final String[] PAGES = {"gigs", "calendar", "clients", "money"};
    private static final String[] PAGE_LABELS = {"🎤\nSinging","📅\nCalendar", "👥\nClients", "💶\nMoney"};

    private String page = "gigs";
    private String gigsTab = "unpaid";
    private LocalDate calMonth = LocalDate.now().withDayOfMonth(1);
    private long calSelected = Dates.today();
    private int moneyYear = LocalDate.now().getYear();

    private FrameLayout content;
    private final TextView[] navItems = new TextView[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            page = savedInstanceState.getString("page", page);
            gigsTab = savedInstanceState.getString("gigsTab", gigsTab);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.WHITE);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(Ui.divider(this));

        LinearLayout nav = Ui.hbox(this);
        nav.setBackgroundColor(Ui.WHITE);
        for (int i = 0; i < 4; i++) {
            final String p = PAGES[i];
            TextView t = Ui.text(this, PAGE_LABELS[i], 12, Ui.GREY, false);
            t.setGravity(Gravity.CENTER);
            int pad = Ui.dp(this, 8);
            t.setPadding(0, pad, 0, pad);
            t.setOnClickListener(v -> {
                page = p;
                render();
            });
            navItems[i] = t;
            nav.addView(t, Ui.weight(1f));
        }
        root.addView(nav);
        setContentView(root);

        handleIntent(getIntent());
        Nudges.schedule(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Prefs.sp(this).getBoolean("asked_notify", false)) {
            Prefs.sp(this).edit().putBoolean("asked_notify", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
        render();
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        if ("unpaid".equals(i.getStringExtra("tab"))) {
            page = "gigs";
            gigsTab = "unpaid";
        }
        if (i.getStringExtra("page") != null) page = i.getStringExtra("page");
        if (i.getStringExtra("gigsTab") != null) gigsTab = i.getStringExtra("gigsTab");
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("page", page);
        out.putString("gigsTab", gigsTab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SETTINGS, 0, "Settings & backup");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SETTINGS) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void render() {
        content.removeAllViews();
        View v;
        switch (page) {
            case "calendar": v = calendarPage(); setTitle("Calendar"); break;
            case "clients": v = clientsPage(); setTitle("Clients"); break;
            case "money": v = moneyPage(); setTitle("Money"); break;
            default: v = gigsPage(); setTitle("Singing"); break;
        }
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < 4; i++) {
            boolean sel = PAGES[i].equals(page);
            navItems[i].setTextColor(sel ? Ui.PRIMARY : Ui.GREY);
            navItems[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            navItems[i].setBackgroundColor(sel ? Ui.PRIMARY_LIGHT : Ui.WHITE);
        }
    }

    private void openGig(long id) {
        startActivity(new Intent(this, GigActivity.class).putExtra("id", id));
    }

    // =====================================================================
    // Gigs
    // =====================================================================

    private View gigsPage() {
        Db db = Db.get(this);
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);

        List<Gig> unpaid = db.unpaidGigs();
        long total = 0, overdue = 0;
        int overdueCount = 0;
        for (Gig g : unpaid) {
            total += g.balance();
            if (g.isOverdue()) {
                overdue += g.balance();
                overdueCount++;
            }
        }
        LinearLayout header = Ui.vbox(this, 20);
        header.setBackgroundColor(Ui.PRIMARY);
        header.addView(Ui.text(this, "Still owed to you", 14, 0xDDFFFFFF, false));
        header.addView(Ui.text(this, Money.fmt(total), 38, Ui.WHITE, true));
        String sub;
        if (unpaid.isEmpty()) sub = "All paid up 🎉";
        else {
            sub = unpaid.size() + (unpaid.size() == 1 ? " unpaid gig" : " unpaid gigs");
            if (overdueCount > 0) sub += "  ·  " + Money.fmt(overdue) + " overdue";
        }
        header.addView(Ui.text(this, sub, 14, overdueCount > 0 ? 0xFFFFCDD2 : 0xDDFFFFFF, overdueCount > 0));
        List<Gig> upcoming = db.upcomingGigs();
        if (!upcoming.isEmpty()) {
            Gig n = upcoming.get(0);
            TextView next = Ui.text(this, "Next gig: " + Dates.shortDay(n.gigDay)
                    + (n.startMin >= 0 ? " " + Dates.time(n.startMin) : "") + " · " + n.title(), 13, 0xDDFFFFFF, false);
            next.setPadding(0, Ui.dp(this, 6), 0, 0);
            header.addView(next);
        }
        v.addView(header);

        LinearLayout tabs = Ui.hbox(this);
        String[][] t = {{"upcoming", "Upcoming"}, {"unpaid", "Unpaid"}, {"all", "All gigs"}};
        for (String[] tab : t) {
            TextView tv = Ui.text(this, tab[1], 15, Ui.GREY, false);
            tv.setGravity(Gravity.CENTER);
            int p = Ui.dp(this, 13);
            tv.setPadding(p, p, p, p);
            boolean sel = tab[0].equals(gigsTab);
            tv.setBackgroundColor(sel ? Ui.PRIMARY_LIGHT : Ui.WHITE);
            tv.setTextColor(sel ? Ui.PRIMARY : Ui.GREY);
            tv.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tv.setOnClickListener(x -> {
                gigsTab = tab[0];
                render();
            });
            tabs.addView(tv, Ui.weight(1f));
        }
        v.addView(tabs);

        List<Object> rows = new ArrayList<>();
        if (Prefs.get(this, Prefs.NAME).isEmpty() || !Prefs.hasPaymentDetails(this)) rows.add("setup");
        String empty;
        switch (gigsTab) {
            case "upcoming":
                groupByMonth(upcoming, rows);
                empty = "No gigs booked yet.\nTap “Add a gig” to log a booking.";
                break;
            case "all":
                groupByMonth(db.allGigs(), rows);
                empty = "No gigs yet.\nTap “Add a gig” to log your first one.";
                break;
            default:
                rows.addAll(unpaid);
                empty = "Nobody owes you anything right now 🎉";
        }

        FrameLayout frame = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setAdapter(new RowsAdapter(rows));
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Object o = rows.get(pos);
            if (o instanceof Gig) openGig(((Gig) o).id);
            else if ("setup".equals(o)) startActivity(new Intent(this, SettingsActivity.class));
        });
        TextView emptyView = Ui.text(this, empty, 16, Ui.GREY, false);
        emptyView.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 32);
        emptyView.setPadding(p, p, p, p);
        frame.addView(list, new FrameLayout.LayoutParams(-1, -1));
        boolean onlySetup = rows.size() == 1 && "setup".equals(rows.get(0));
        if (rows.isEmpty() || onlySetup) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            if (onlySetup) lp.topMargin = Ui.dp(this, 90);
            frame.addView(emptyView, lp);
        }
        v.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = Ui.vbox(this, 0);
        bottom.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 12));
        Button add = Ui.button(this, "+  Add a gig", Ui.PRIMARY, Ui.WHITE);
        add.setOnClickListener(x -> Forms.editGig(this, null, 0, id -> render()));
        bottom.addView(add);
        v.addView(bottom);
        return v;
    }

    private static void groupByMonth(List<Gig> gigs, List<Object> out) {
        String last = null;
        for (Gig g : gigs) {
            String m = Dates.month(g.gigDay);
            if (!m.equals(last)) {
                out.add(m);
                last = m;
            }
            out.add(g);
        }
    }

    /** Rows: Gig, month header (String), or "setup" hint. */
    private class RowsAdapter extends BaseAdapter {
        private final List<Object> rows;

        RowsAdapter(List<Object> rows) {
            this.rows = rows;
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int pos) { return rows.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public boolean isEnabled(int pos) { return !(rows.get(pos) instanceof String) || "setup".equals(rows.get(pos)); }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            Object o = rows.get(pos);
            MainActivity c = MainActivity.this;
            if (o instanceof Gig) {
                LinearLayout wrap = new LinearLayout(c);
                wrap.setOrientation(LinearLayout.VERTICAL);
                wrap.addView(Ui.gigRow(c, (Gig) o));
                wrap.addView(Ui.divider(c));
                return wrap;
            }
            if ("setup".equals(o)) {
                LinearLayout box = Ui.vbox(c, 14);
                box.setBackgroundColor(0xFFFFF8E1);
                box.addView(Ui.text(c, "✨ Add your details for invoices", 15, Ui.DARK, true));
                box.addView(Ui.text(c, "Your name, IBAN and Revolut go on invoices and payment reminders. Tap to set up.",
                        13, Ui.GREY, false));
                return box;
            }
            return Ui.groupHeader(c, (String) o);
        }
    }

    // =====================================================================
    // Calendar
    // =====================================================================

    private View calendarPage() {
        Db db = Db.get(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout v = Ui.vbox(this, 12);
        scroll.addView(v);

        // Month switcher
        LinearLayout nav = Ui.hbox(this);
        TextView prev = Ui.text(this, "  ‹  ", 26, Ui.PRIMARY, true);
        TextView next = Ui.text(this, "  ›  ", 26, Ui.PRIMARY, true);
        TextView title = Ui.text(this, Dates.month(calMonth.toEpochDay()), 19, Ui.DARK, true);
        title.setGravity(Gravity.CENTER);
        prev.setOnClickListener(x -> { calMonth = calMonth.minusMonths(1); render(); });
        next.setOnClickListener(x -> { calMonth = calMonth.plusMonths(1); render(); });
        title.setOnClickListener(x -> {
            calMonth = LocalDate.now().withDayOfMonth(1);
            calSelected = Dates.today();
            render();
        });
        nav.addView(prev);
        nav.addView(title, Ui.weight(1f));
        nav.addView(next);
        v.addView(nav);

        // Weekday names (Monday first)
        LinearLayout wk = Ui.hbox(this);
        for (String d : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"}) {
            TextView t = Ui.text(this, d, 12, Ui.GREY, false);
            t.setGravity(Gravity.CENTER);
            wk.addView(t, Ui.weight(1f));
        }
        wk.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        v.addView(wk);

        LocalDate gridStart = calMonth.minusDays(calMonth.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        long from = gridStart.toEpochDay(), to = from + 41;
        Map<Long, List<Gig>> byDay = new HashMap<>();
        for (Gig g : db.gigsBetween(from, to)) {
            byDay.computeIfAbsent(g.gigDay, k -> new ArrayList<>()).add(g);
        }
        long today = Dates.today();
        long monthBooked = 0;
        int monthGigs = 0;
        for (Map.Entry<Long, List<Gig>> e : byDay.entrySet()) {
            if (LocalDate.ofEpochDay(e.getKey()).getMonth() != calMonth.getMonth()) continue;
            for (Gig g : e.getValue()) {
                if (g.isCancelled()) continue;
                monthGigs++;
                monthBooked += g.feeCents;
            }
        }

        for (int w = 0; w < 6; w++) {
            LinearLayout week = Ui.hbox(this);
            for (int d = 0; d < 7; d++) {
                final long day = from + w * 7L + d;
                LocalDate ld = LocalDate.ofEpochDay(day);
                boolean inMonth = ld.getMonth() == calMonth.getMonth();
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                int p = Ui.dp(this, 5);
                cell.setPadding(0, p, 0, p);
                TextView num = Ui.text(this, String.valueOf(ld.getDayOfMonth()), 15,
                        !inMonth ? 0xFFBDBDBD : day == today ? Ui.PRIMARY : Ui.DARK, day == today);
                num.setGravity(Gravity.CENTER);
                cell.addView(num);
                StringBuilder dots = new StringBuilder();
                int dotColor = Ui.PRIMARY;
                List<Gig> gs = byDay.get(day);
                if (gs != null) {
                    for (Gig g : gs) {
                        if (g.isCancelled()) continue;
                        if (dots.length() < 6) dots.append("●");
                        if (g.isOverdue()) dotColor = Ui.RED;
                        else if (g.isOwed() && dotColor != Ui.RED) dotColor = Ui.ORANGE;
                        else if (g.status == Gig.PENCILLED && dotColor == Ui.PRIMARY) dotColor = Ui.ORANGE;
                        else if (g.isPaid() && !g.isFuture() && dotColor == Ui.PRIMARY) dotColor = Ui.GREEN;
                    }
                }
                TextView dt = Ui.text(this, dots.length() == 0 ? " " : dots.toString(), 8, inMonth ? dotColor : 0xFFBDBDBD, false);
                dt.setGravity(Gravity.CENTER);
                cell.addView(dt);
                if (day == calSelected) cell.setBackground(Ui.rounded(this, Ui.PRIMARY_LIGHT, 10));
                cell.setOnClickListener(x -> {
                    calSelected = day;
                    if (!inMonth) calMonth = ld.withDayOfMonth(1);
                    render();
                });
                week.addView(cell, Ui.weight(1f));
            }
            v.addView(week);
        }

        TextView summary = Ui.text(this, monthGigs == 0 ? "No gigs this month"
                : monthGigs + (monthGigs == 1 ? " gig" : " gigs") + " this month · " + Money.fmt(monthBooked) + " in fees",
                13, Ui.GREY, false);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(0, Ui.dp(this, 6), 0, 0);
        v.addView(summary);
        LinearLayout legend = Ui.hbox(this);
        legend.setGravity(Gravity.CENTER);
        String[] keys = {"● booked  ", "● to collect / pencilled  ", "● overdue  ", "● paid"};
        int[] cols = {Ui.PRIMARY, Ui.ORANGE, Ui.RED, Ui.GREEN};
        for (int i = 0; i < keys.length; i++) legend.addView(Ui.text(this, keys[i], 11, cols[i], false));
        v.addView(legend);

        v.addView(Ui.section(this, Dates.withWeekday(calSelected)));
        List<Gig> dayGigs = byDay.get(calSelected);
        if (dayGigs == null) dayGigs = db.gigsBetween(calSelected, calSelected);
        if (dayGigs.isEmpty()) v.addView(Ui.text(this, "Nothing booked.", 15, Ui.GREY, false));
        for (Gig g : dayGigs) {
            LinearLayout r = Ui.gigRow(this, g);
            r.setPadding(0, r.getPaddingTop(), 0, r.getPaddingBottom());
            r.setOnClickListener(x -> openGig(g.id));
            v.addView(r);
            v.addView(Ui.divider(this));
        }
        Button add = Ui.button(this, "+  Add a gig on " + Dates.shortDay(calSelected), Ui.PRIMARY, Ui.WHITE);
        add.setOnClickListener(x -> Forms.editGig(this, null, calSelected, id -> render()));
        v.addView(add);
        return scroll;
    }

    // =====================================================================
    // Clients
    // =====================================================================

    private View clientsPage() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout v = Ui.vbox(this, 0);
        scroll.addView(v);
        List<Client> clients = Db.get(this).clientsWithStats();

        LinearLayout top = Ui.vbox(this, 16);
        Button add = Ui.button(this, "+  Add a client", Ui.PRIMARY, Ui.WHITE);
        add.setOnClickListener(x -> Forms.editClient(this, null, id -> render(), null));
        top.addView(add);
        if (clients.isEmpty()) {
            TextView e = Ui.text(this, "Your clients appear here automatically when you add gigs. "
                    + "You can also save regulars (hotels, planners, restaurants) here first.", 15, Ui.GREY, false);
            e.setPadding(0, Ui.dp(this, 16), 0, 0);
            top.addView(e);
        }
        v.addView(top);

        for (Client k : clients) {
            String line2 = k.gigCount + (k.gigCount == 1 ? " gig" : " gigs")
                    + (k.earnedCents > 0 ? " · " + Money.fmt(k.earnedCents) + " earned" : "")
                    + (k.upcoming > 0 ? " · " + k.upcoming + " coming up" : "");
            String amount = k.owedCents > 0 ? Money.fmt(k.owedCents) : null;
            LinearLayout r = Ui.row(this, k.name, line2, k.payingHabit(),
                    k.overdueCents > 0 ? Ui.RED : k.paidLate > 0 ? Ui.ORANGE : Ui.GREY,
                    amount, k.overdueCents > 0 ? Ui.RED : Ui.DARK);
            r.setOnClickListener(x -> startActivity(new Intent(this, ClientActivity.class).putExtra("name", k.name)));
            v.addView(r);
            v.addView(Ui.divider(this));
        }
        return scroll;
    }

    // =====================================================================
    // Money: earnings summary & expenses
    // =====================================================================

    private View moneyPage() {
        Db db = Db.get(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout v = Ui.vbox(this, 16);
        scroll.addView(v);

        LinearLayout nav = Ui.hbox(this);
        TextView prev = Ui.text(this, "  ‹  ", 26, Ui.PRIMARY, true);
        TextView next = Ui.text(this, "  ›  ", 26, Ui.PRIMARY, true);
        TextView title = Ui.text(this, String.valueOf(moneyYear), 20, Ui.DARK, true);
        title.setGravity(Gravity.CENTER);
        prev.setOnClickListener(x -> { moneyYear--; render(); });
        next.setOnClickListener(x -> { moneyYear++; render(); });
        nav.addView(prev);
        nav.addView(title, Ui.weight(1f));
        nav.addView(next);
        v.addView(nav);

        long from = LocalDate.of(moneyYear, 1, 1).toEpochDay();
        long to = LocalDate.of(moneyYear, 12, 31).toEpochDay();
        long today = Dates.today();
        long[] receivedByMonth = new long[12];
        long received = 0, pending = 0, pendingOverdue = 0;
        Map<String, Long> byClient = new HashMap<>();
        for (Gig g : db.gigsBetween(from, Math.min(to, today))) {
            if (g.isCancelled()) continue;
            pending += g.balance();
            if (g.isOverdue()) pendingOverdue += g.balance();
            byClient.merge(g.client, g.feeCents, Long::sum);
        }
        for (long[] p : db.paymentsBetween(from, to)) {
            received += p[1];
            receivedByMonth[LocalDate.ofEpochDay(p[0]).getMonthValue() - 1] += p[1];
        }
        List<Expense> expenses = db.expensesBetween(from, to);
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Expense e : expenses) byCategory.merge(e.category, e.cents, Long::sum);

        LinearLayout pendingTile = Ui.tile(this, "Pending balance", Money.fmt(pending), pending > 0 ? Ui.ORANGE : Ui.DARK);
        if (pendingOverdue > 0) pendingTile.addView(Ui.text(this, Money.fmt(pendingOverdue) + " overdue", 12, Ui.RED, true));
        v.addView(Ui.tiles(this, Ui.tile(this, "Received", Money.fmt(received), Ui.GREEN), pendingTile));

        // This month
        LocalDate now = LocalDate.now();
        if (now.getYear() == moneyYear) {
            int m = now.getMonthValue() - 1;
            LinearLayout card = Ui.card(this, Ui.PRIMARY_LIGHT);
            card.addView(Ui.text(this, "Received this month (" + Dates.month(today) + ")", 13, Ui.GREY, false));
            card.addView(Ui.text(this, Money.fmt(receivedByMonth[m]), 20, Ui.DARK, true));
            v.addView(card);
        }

        v.addView(Ui.section(this, "Received month by month"));
        v.addView(new BarChart(this, receivedByMonth, null, now.getYear() == moneyYear ? now.getMonthValue() - 1 : -1));
        int best = -1;
        for (int i = 0; i < 12; i++) if (receivedByMonth[i] > 0 && (best < 0 || receivedByMonth[i] > receivedByMonth[best])) best = i;
        if (best >= 0) {
            v.addView(Ui.text(this, "Best month: " + Dates.month(LocalDate.of(moneyYear, best + 1, 1).toEpochDay())
                    + " (" + Money.fmt(receivedByMonth[best]) + " received)", 14, Ui.DARK, false));
        }

        if (!byClient.isEmpty()) {
            v.addView(Ui.section(this, "Top clients"));
            List<Map.Entry<String, Long>> top = new ArrayList<>(byClient.entrySet());
            top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < Math.min(5, top.size()); i++) {
                LinearLayout r = Ui.hbox(this);
                r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                r.addView(Ui.text(this, (i + 1) + ".  " + top.get(i).getKey(), 15, Ui.DARK, false), Ui.weight(1f));
                r.addView(Ui.text(this, Money.fmt(top.get(i).getValue()), 15, Ui.DARK, true));
                v.addView(r);
            }
        }

        v.addView(Ui.section(this, "Expenses " + moneyYear));
        Button add = Ui.button(this, "+  Add an expense", Ui.PRIMARY, Ui.WHITE);
        add.setOnClickListener(x -> Forms.editExpense(this, null, null, this::render));
        v.addView(add);
        if (!byCategory.isEmpty()) {
            LinearLayout card = Ui.card(this, Ui.LIGHT_GREY);
            for (Map.Entry<String, Long> e : byCategory.entrySet()) {
                LinearLayout r = Ui.hbox(this);
                r.addView(Ui.text(this, e.getKey(), 14, Ui.DARK, false), Ui.weight(1f));
                r.addView(Ui.text(this, Money.fmt(e.getValue()), 14, Ui.DARK, true));
                card.addView(r);
            }
            v.addView(card);
        }
        if (expenses.isEmpty()) {
            TextView e = Ui.text(this, "No expenses logged for " + moneyYear + ". Fuel, outfits, backing tracks and "
                    + "equipment all count – handy at tax time.", 14, Ui.GREY, false);
            e.setPadding(0, Ui.dp(this, 10), 0, 0);
            v.addView(e);
        }
        for (Expense e : expenses) {
            String gigName = "";
            if (e.gigId > 0) {
                Gig g = db.gig(e.gigId);
                if (g != null) gigName = "For " + g.title();
            }
            LinearLayout r = Ui.row(this, e.note.isEmpty() ? e.category : e.note,
                    Dates.fmt(e.day) + (e.note.isEmpty() ? "" : " · " + e.category), gigName, Ui.GREY,
                    Money.fmt(e.cents), Ui.RED);
            r.setPadding(0, r.getPaddingTop(), 0, r.getPaddingBottom());
            r.setOnClickListener(x -> Forms.editExpense(this, e, null, this::render));
            v.addView(r);
            v.addView(Ui.divider(this));
        }
        return scroll;
    }
}
