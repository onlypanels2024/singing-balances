package com.onlypanels.singingbalances;

import android.app.Activity;
import android.content.Context;

import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.InstallStatus;
import com.google.android.play.core.install.model.UpdateAvailability;

/**
 * Google Play's in-app updates: when a newer ShowFee is on the Play Store, the home screen shows a small
 * "new version" bar. The update downloads while the user carries on, then one tap restarts into it.
 * Urgent fixes (released with high priority in Play Console) ask the user to update straight away.
 * Copies not installed from Google Play (test downloads) simply never show anything.
 */
final class Updates {
    static final int AVAILABLE = 1, DOWNLOADING = 2, READY = 3;
    private static final int REQ = 7301;
    /** Test builds swap in Google's pretend update manager here. */
    static AppUpdateManager override;
    /** "Later" hides the bar until ShowFee is next opened from scratch. */
    static boolean dismissed;
    private static AppUpdateManager manager;
    private static AppUpdateInfo latest;
    private static InstallStateUpdatedListener listener;

    interface Bar {
        void show(int state); // 0 hides it
    }

    private Updates() {}

    private static AppUpdateManager manager(Context c) {
        if (override != null) return override;
        if (manager == null) manager = AppUpdateManagerFactory.create(c.getApplicationContext());
        return manager;
    }

    /** Asks Google Play (quietly) whether there's a newer version. */
    static void check(Activity a, Bar bar) {
        AppUpdateManager m;
        try {
            m = manager(a);
        } catch (Exception e) {
            return;
        }
        if (listener != null) m.unregisterListener(listener);
        listener = state -> {
            if (state.installStatus() == InstallStatus.DOWNLOADED) bar.show(READY);
            else if (state.installStatus() == InstallStatus.DOWNLOADING) bar.show(DOWNLOADING);
        };
        m.registerListener(listener);
        m.getAppUpdateInfo().addOnSuccessListener(info -> {
            latest = info;
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                bar.show(READY);
            } else if (info.installStatus() == InstallStatus.DOWNLOADING) {
                bar.show(DOWNLOADING);
            } else if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                start(a, AppUpdateType.IMMEDIATE); // an urgent update was interrupted: carry on with it
            } else if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (info.updatePriority() >= 4 && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    start(a, AppUpdateType.IMMEDIATE);
                } else if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    bar.show(dismissed ? 0 : AVAILABLE);
                }
            } else {
                bar.show(0);
            }
        }).addOnFailureListener(e -> bar.show(0)); // not from Google Play, offline, etc.
    }

    /** "Update" tapped: Google Play's own download screen. */
    static void start(Activity a, int type) {
        if (latest == null) return;
        try {
            manager(a).startUpdateFlowForResult(latest, a, AppUpdateOptions.defaultOptions(type), REQ);
        } catch (Exception ignored) {
            // Google Play couldn't start it; the bar stays so the user can try again
        }
    }

    /** "Restart" tapped: installs the downloaded update and reopens ShowFee. */
    static void finish(Context c) {
        try {
            manager(c).completeUpdate();
        } catch (Exception ignored) {
        }
    }
}
