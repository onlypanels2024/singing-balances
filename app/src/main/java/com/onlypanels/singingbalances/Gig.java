package com.onlypanels.singingbalances;

final class Gig {
    static final int CONFIRMED = 0;
    static final int PENCILLED = 1;
    static final int CANCELLED = 2;
    static final String[] STATUS_NAMES = {"Confirmed", "Pencilled in", "Cancelled"};

    long id;
    String client = "";
    String email = "";   // client's email (from the client list)
    String event = "";
    String notes = "";
    long gigDay;
    int startMin = -1;   // -1 = no time set
    long dueDay;
    long feeCents;       // the fee itself (without extras or VAT added on top)
    long extrasCents;    // extra lines on the bill (travel, extra hour...)
    int vatBp;           // VAT rate in hundredths of a percent (1800 = 18%); 0 = no VAT on this booking
    boolean vatIncl;     // true: the prices already include VAT; false: VAT is added on top
    long paidCents;
    long lastPaidDay = -1;
    int status = CONFIRMED;
    String invoiceNo = "";
    long invoiceDay;

    /** Fee plus extras, before any VAT added on top. */
    long subtotal() {
        return feeCents + extrasCents;
    }

    /** The VAT part of the bill (0 when there's no VAT). */
    long vatCents() {
        if (vatBp <= 0) return 0;
        long sub = subtotal();
        if (vatIncl) return sub - Math.round(sub * 10000.0 / (10000 + vatBp));
        return Math.round(sub * vatBp / 10000.0);
    }

    /** What the client pays in all. */
    long total() {
        return vatBp > 0 && !vatIncl ? subtotal() + vatCents() : subtotal();
    }

    /** What the user earns (the total without VAT). */
    long net() {
        return total() - vatCents();
    }

    /** "18%" or "5.5%". */
    String vatRate() {
        return vatBp % 100 == 0 ? (vatBp / 100) + "%" : String.format(java.util.Locale.ROOT, "%.2f", vatBp / 100.0)
                .replaceAll("0+$", "").replaceAll("\\.$", "") + "%";
    }

    long balance() {
        return Math.max(0, total() - paidCents);
    }

    boolean isPaid() {
        return paidCents >= total();
    }

    boolean isCancelled() {
        return status == CANCELLED;
    }

    boolean isFuture() {
        return gigDay > Dates.today();
    }

    /** Gig has happened (or is today), isn't cancelled, and isn't fully paid. */
    boolean isOwed() {
        return !isCancelled() && gigDay <= Dates.today() && !isPaid();
    }

    boolean isOverdue() {
        return isOwed() && dueDay < Dates.today();
    }

    String title() {
        return event == null || event.isEmpty() ? client : client + " · " + event;
    }

    String when() {
        return Dates.withWeekday(gigDay) + (startMin >= 0 ? " · " + Dates.time(startMin) : "");
    }

    String status(android.content.Context c) {
        if (isCancelled()) return "Cancelled";
        long today = Dates.today();
        if (gigDay > today) {
            return (status == PENCILLED ? "Pencilled in" : "Confirmed") + " · " + Dates.relative(gigDay);
        }
        if (isPaid()) return "Paid in full ✓";
        if (gigDay == today && dueDay >= today) {
            return Words.tonight(c) + (startMin >= 0 ? " at " + Dates.time(startMin) : "") + " · collect " + Money.fmt(balance());
        }
        long days = dueDay - today;
        if (days < 0) {
            long late = -days;
            return "Overdue " + late + (late == 1 ? " day" : " days") + " · was due " + Dates.fmt(dueDay);
        }
        String due = days == 0 ? "due today" : "due " + Dates.fmt(dueDay);
        if (paidCents > 0) {
            return "Part paid " + Money.fmt(paidCents) + " of " + Money.fmt(total()) + " · " + due;
        }
        return days == 0 ? "Due today" : "Due " + Dates.fmt(dueDay) + " (" + Dates.relative(dueDay) + ")";
    }

    int statusColor() {
        if (isCancelled()) return Ui.GREY;
        if (isFuture()) return status == PENCILLED ? Ui.ORANGE : Ui.PRIMARY;
        if (isPaid()) return Ui.GREEN;
        if (isOverdue()) return Ui.RED;
        if (dueDay == Dates.today()) return Ui.ORANGE;
        return Ui.GREY;
    }

    /** Amount to show on the right of a list row. */
    long displayAmount() {
        if (isCancelled() || isFuture() || isPaid()) return total();
        return balance();
    }

    int amountColor() {
        if (isCancelled()) return Ui.GREY;
        if (isFuture()) return Ui.DARK;
        if (isPaid()) return Ui.GREEN;
        return isOverdue() ? Ui.RED : Ui.DARK;
    }
}
