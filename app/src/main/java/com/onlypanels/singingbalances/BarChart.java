package com.onlypanels.singingbalances;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Simple 12-month bar chart. spent may be null for a single series. */
final class BarChart extends View {
    private static final String[] MONTHS = {"J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"};
    private final long[] earned;
    private final long[] spent;
    private final int highlight;
    private final Paint pEarned = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSpent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrid = new Paint();

    /** highlight = month index (0-11) to emphasise, or -1. */
    BarChart(Context c, long[] earned, long[] spent, int highlight) {
        super(c);
        this.earned = earned;
        this.spent = spent;
        this.highlight = highlight;
        pEarned.setColor(Ui.PRIMARY);
        pSpent.setColor(0xFFE57373);
        pLabel.setColor(Ui.GREY);
        pLabel.setTextSize(Ui.dp(c, 11));
        pLabel.setTextAlign(Paint.Align.CENTER);
        pGrid.setColor(Ui.LINE);
        pGrid.setStrokeWidth(1);
        setMinimumHeight(Ui.dp(c, 170));
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), Ui.dp(getContext(), 170));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float top = Ui.dp(getContext(), 18), bottom = h - Ui.dp(getContext(), 20);
        long max = 1;
        for (int i = 0; i < 12; i++) max = Math.max(max, Math.max(earned[i], spent == null ? 0 : spent[i]));
        c.drawLine(0, bottom, w, bottom, pGrid);
        float slot = w / 12f;
        float bw = slot * (spent == null ? 0.5f : 0.32f);
        float r = Ui.dp(getContext(), 3);
        for (int i = 0; i < 12; i++) {
            float cx = slot * i + slot / 2f;
            float he = (bottom - top) * earned[i] / max;
            float hs = spent == null ? 0 : (bottom - top) * spent[i] / max;
            pEarned.setAlpha(highlight < 0 || i == highlight ? 255 : 150);
            if (he > 0) {
                if (spent == null) c.drawRoundRect(new RectF(cx - bw / 2, bottom - he, cx + bw / 2, bottom), r, r, pEarned);
                else c.drawRoundRect(new RectF(cx - bw - 1, bottom - he, cx - 1, bottom), r, r, pEarned);
            }
            if (hs > 0) c.drawRoundRect(new RectF(cx + 1, bottom - hs, cx + bw + 1, bottom), r, r, pSpent);
            pLabel.setColor(i == highlight ? Ui.PRIMARY : Ui.GREY);
            pLabel.setFakeBoldText(i == highlight);
            c.drawText(MONTHS[i], cx, h - Ui.dp(getContext(), 4), pLabel);
        }
        // label the best month
        int best = -1;
        for (int i = 0; i < 12; i++) if (earned[i] > 0 && (best < 0 || earned[i] > earned[best])) best = i;
        if (best >= 0) {
            float cx = slot * best + slot / 2f;
            float he = (bottom - top) * earned[best] / max;
            pLabel.setColor(Ui.DARK);
            pLabel.setFakeBoldText(true);
            String s = Money.fmt(earned[best]);
            if (s.endsWith(".00")) s = s.substring(0, s.length() - 3);
            float x = Math.max(pLabel.measureText(s) / 2, Math.min(w - pLabel.measureText(s) / 2, cx));
            c.drawText(s, x, bottom - he - Ui.dp(getContext(), 4), pLabel);
        }
    }
}
