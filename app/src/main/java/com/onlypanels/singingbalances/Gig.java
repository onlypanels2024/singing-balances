package com.onlypanels.singingbalances;

final class Gig {
    long id;
    String client = "";
    String email = "";
    String event = "";
    String notes = "";
    long gigDay;
    long dueDay;
    long feeCents;
    long paidCents;

    long balance() {
        return Math.max(0, feeCents - paidCents);
    }

    boolean isPaid() {
        return paidCents >= feeCents;
    }

    boolean isOverdue() {
        return !isPaid() && dueDay < Dates.today();
    }

    String title() {
        return event == null || event.isEmpty() ? client : client + " · " + event;
    }

    String status() {
        if (isPaid()) return "Paid in full ✓";
        long days = dueDay - Dates.today();
        if (days < 0) {
            long late = -days;
            return "Overdue " + late + (late == 1 ? " day" : " days") + " · was due " + Dates.fmt(dueDay);
        }
        String due = days == 0 ? "due today" : "due " + Dates.fmt(dueDay);
        if (paidCents > 0) {
            return "Part paid " + Money.fmt(paidCents) + " of " + Money.fmt(feeCents) + " · " + due;
        }
        return days == 0 ? "Due today" : "Due " + Dates.fmt(dueDay) + " (in " + days + (days == 1 ? " day)" : " days)");
    }

    int statusColor() {
        if (isPaid()) return Ui.GREEN;
        if (isOverdue()) return Ui.RED;
        if (dueDay == Dates.today()) return Ui.ORANGE;
        return Ui.GREY;
    }
}
