package com.onlypanels.singingbalances;

final class Expense {
    static final String[] CATEGORIES = {
            "Fuel & transport", "Outfits & costumes", "Hair & make-up", "Backing tracks & music",
            "Equipment", "Lessons & training", "Promotion", "Food & drinks", "Other"
    };

    long id;
    long day;
    long cents;
    String category = CATEGORIES[0];
    String note = "";
    long gigId;  // 0 = not linked to a gig
}
