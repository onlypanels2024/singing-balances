package com.onlypanels.singingbalances;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;

/**
 * The user's look: accent colour and light / dark mode. Sets the colours in {@link Ui}
 * and the matching Android theme, so dialogs, text fields and switches match too.
 */
final class Theme {
    static final String[] ACCENT_KEYS = {"plum", "indigo", "ocean", "teal", "rose", "graphite", "gold"};
    static final String[] ACCENT_NAMES = {"Plum", "Indigo", "Ocean", "Teal", "Rose", "Graphite", "Gold"};
    // Strong tone (light mode, buttons, invoices) and soft tone (dark mode) for each accent.
    private static final int[] STRONG = {0xFF6A1B9A, 0xFF3949AB, 0xFF1565C0, 0xFF00796B, 0xFFC2185B, 0xFF455A64, 0xFF8D6E00};
    private static final int[] DEEP = {0xFF4A148C, 0xFF1A237E, 0xFF0D47A1, 0xFF004D40, 0xFF880E4F, 0xFF263238, 0xFF5D4600};
    private static final int[] SOFT = {0xFFD9A6E8, 0xFFA9B4F0, 0xFF90CAF9, 0xFF80CBC4, 0xFFF48FB1, 0xFFB0BEC5, 0xFFF2CF5B};

    static final String MODE_LIGHT = "light", MODE_DARK = "dark", MODE_PHONE = "phone";
    static final String[] MODE_KEYS = {MODE_LIGHT, MODE_DARK, MODE_PHONE};
    static final String[] MODE_NAMES = {"Light", "Dark", "Same as phone"};

    private static final int[] LIGHT_STYLES = {R.style.AppTheme_Light_Plum, R.style.AppTheme_Light_Indigo,
            R.style.AppTheme_Light_Ocean, R.style.AppTheme_Light_Teal, R.style.AppTheme_Light_Rose,
            R.style.AppTheme_Light_Graphite, R.style.AppTheme_Light_Gold};
    private static final int[] DARK_STYLES = {R.style.AppTheme_Dark_Plum, R.style.AppTheme_Dark_Indigo,
            R.style.AppTheme_Dark_Ocean, R.style.AppTheme_Dark_Teal, R.style.AppTheme_Dark_Rose,
            R.style.AppTheme_Dark_Graphite, R.style.AppTheme_Dark_Gold};

    /** Bumped whenever the look changes, so open screens know to redraw. */
    static int generation;
    static boolean dark;

    private Theme() {}

    static int accentIndex(Context c) {
        String a = Prefs.get(c, Prefs.ACCENT);
        for (int i = 0; i < ACCENT_KEYS.length; i++) if (ACCENT_KEYS[i].equals(a)) return i;
        return 1; // indigo for new users
    }

    /** The strong accent colour, e.g. for invoices and the colour picker. */
    static int strong(int i) {
        return STRONG[i];
    }

    static int strong(Context c) {
        return STRONG[accentIndex(c)];
    }

    static int deep(Context c) {
        return DEEP[accentIndex(c)];
    }

    static boolean isDark(Context c) {
        String m = Prefs.get(c, Prefs.THEME_MODE);
        if (MODE_DARK.equals(m)) return true;
        if (MODE_PHONE.equals(m)) {
            int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return night == Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }

    /** Mixes two colours; t = 0 gives a, t = 1 gives b. */
    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Loads the colours (and currency) without touching any screen. Safe to call from anywhere. */
    static void load(Context c) {
        Money.load(c);
        int i = accentIndex(c);
        dark = isDark(c);
        Ui.HERO = STRONG[i];
        Ui.HERO_DARK = DEEP[i];
        if (dark) {
            Ui.BG = 0xFF121317;
            Ui.SURFACE = 0xFF1D1E23;
            Ui.LIGHT_GREY = 0xFF26272D;
            Ui.LINE = 0xFF2E3036;
            Ui.DARK = 0xFFECEDF0;
            Ui.GREY = 0xFFA2A5AD;
            Ui.FAINT = 0xFF5C5F66;
            Ui.PRIMARY = SOFT[i];
            Ui.PRIMARY_DARK = SOFT[i];
            Ui.ON_PRIMARY = 0xFF121317;
            Ui.PRIMARY_LIGHT = mix(Ui.SURFACE, SOFT[i], 0.16f);
            Ui.RED = 0xFFF2726F;
            Ui.RED_LIGHT = 0xFF3A2224;
            Ui.GREEN = 0xFF72C47A;
            Ui.GREEN_LIGHT = 0xFF1E3323;
            Ui.ORANGE = 0xFFFFB259;
            Ui.ORANGE_LIGHT = 0xFF3A2E1C;
        } else {
            Ui.BG = 0xFFF6F6F9;
            Ui.SURFACE = 0xFFFFFFFF;
            Ui.LIGHT_GREY = 0xFFF0F0F4;
            Ui.LINE = 0xFFE6E6EC;
            Ui.DARK = 0xFF1B1C20;
            Ui.GREY = 0xFF6B6E76;
            Ui.FAINT = 0xFFBDBEC4;
            Ui.PRIMARY = STRONG[i];
            Ui.PRIMARY_DARK = DEEP[i];
            Ui.ON_PRIMARY = 0xFFFFFFFF;
            Ui.PRIMARY_LIGHT = mix(0xFFFFFFFF, STRONG[i], 0.10f);
            Ui.RED = 0xFFC62828;
            Ui.RED_LIGHT = 0xFFFDECEC;
            Ui.GREEN = 0xFF2E7D32;
            Ui.GREEN_LIGHT = 0xFFE8F4E9;
            Ui.ORANGE = 0xFFD45500;
            Ui.ORANGE_LIGHT = 0xFFFFF3E3;
        }
    }

    /** Call at the very start of onCreate, before super.onCreate. */
    static void apply(Activity a) {
        load(a);
        int id = (dark ? DARK_STYLES : LIGHT_STYLES)[accentIndex(a)];
        if (id != 0) a.setTheme(id);
    }

    /** Call after setContentView: flat top bar and system bars in the page colour. */
    static void bars(Activity a) {
        ActionBar bar = a.getActionBar();
        if (bar != null) {
            bar.setBackgroundDrawable(new ColorDrawable(Ui.BG));
            bar.setElevation(0);
        }
        Window w = a.getWindow();
        w.setStatusBarColor(Ui.BG);
        w.setNavigationBarColor(Ui.SURFACE);
        View d = w.getDecorView();
        int flags = d.getSystemUiVisibility();
        int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        d.setSystemUiVisibility(dark ? flags & ~light : flags | light);
        d.setBackgroundColor(Ui.BG);
    }

    static void changed(Context c) {
        generation++;
        load(c);
        Widget.updateAll(c);
    }
}
