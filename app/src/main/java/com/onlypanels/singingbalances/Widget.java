package com.onlypanels.singingbalances;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.List;

/** Home-screen widget: how much you're owed, what's overdue, and your next gig. */
public class Widget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        updateAll(c);
    }

    static void updateAll(Context c) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(c);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(c, Widget.class));
            if (ids == null || ids.length == 0) return;
            Theme.load(c);
            Db db = Db.get(c);
            long owed = 0, overdue = 0;
            List<Gig> unpaid = db.unpaidGigs();
            for (Gig g : unpaid) {
                owed += g.balance();
                if (g.isOverdue()) overdue += g.balance();
            }
            String sub;
            if (unpaid.isEmpty()) sub = "All paid up";
            else if (overdue > 0) sub = Money.fmt(overdue) + " overdue";
            else sub = Words.count(c, unpaid.size()) + " to be paid";

            String next = "No " + Words.many(c) + " coming up";
            for (Gig g : db.upcomingGigs()) {
                next = "Next: " + Dates.shortDay(g.gigDay) + (g.startMin >= 0 ? " " + Dates.time(g.startMin) : "")
                        + " · " + g.title();
                break;
            }

            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.w_amount, Money.fmt(owed));
            v.setTextViewText(R.id.w_sub, sub);
            v.setTextColor(R.id.w_sub, overdue > 0 ? 0xFFFFD5D5 : 0xDDFFFFFF);
            v.setInt(R.id.w_bg, "setColorFilter", Ui.HERO);
            v.setTextViewText(R.id.w_title, "Owed to you");
            v.setTextViewText(R.id.w_next, next);
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 99, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            mgr.updateAppWidget(ids, v);
        } catch (Exception ignored) {
            // never let the widget break the app
        }
    }
}
