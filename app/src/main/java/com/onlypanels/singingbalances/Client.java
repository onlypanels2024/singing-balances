package com.onlypanels.singingbalances;

final class Client {
    long id;
    String name = "";
    String email = "";
    String phone = "";
    String notes = "";

    // Stats, filled in by Db.clientsWithStats()
    int gigCount;
    long earnedCents;     // fees for gigs that have happened (not cancelled)
    long receivedCents;
    long owedCents;
    long overdueCents;
    int upcoming;
    int paidGigs;
    long totalDaysToPay;  // sum over fully-paid gigs of (last payment day - gig day)
    int paidLate;         // fully paid after the due date
    long lastGigDay = Long.MIN_VALUE;

    /** Average days from gig to full payment, or -1 if not known yet. */
    double avgDaysToPay() {
        return paidGigs == 0 ? -1 : (double) totalDaysToPay / paidGigs;
    }

    String payingHabit() {
        if (paidGigs == 0) {
            if (overdueCents > 0) return receivedCents > 0 ? "Part paid – rest overdue" : "Hasn't paid yet – overdue";
            return receivedCents > 0 ? "Part paid so far" : "No payment history yet";
        }
        double avg = avgDaysToPay();
        String s;
        if (avg < 0.5) s = "Usually pays on the night";
        else s = "Pays in about " + Math.round(avg) + (Math.round(avg) == 1 ? " day" : " days") + " on average";
        if (paidLate > 0) s += " · paid late " + paidLate + (paidLate == 1 ? " time" : " times");
        return s;
    }
}
