package com.onlypanels.singingbalances;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Booking-request notifications. When a client sends a request from the booking link, a small program in
 * Google Cloud (firebase/functions in the website repository) sends this phone a message, and this shows it.
 * Only phones of people who set up a booking link are registered (see Cloud.registerDevice).
 */
public class PushService extends FirebaseMessagingService {
    static final String CHANNEL = "booking_requests";

    @Override
    public void onNewToken(String token) {
        Cloud.registerDevice(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        if (!"booking_request".equals(d.get("type"))) return;
        show(this, d.get("id"), d.get("name"), d.get("when"));
    }

    static void show(Context c, String id, String name, String when) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Booking requests",
                NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(c, RequestsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int nid = 7000 + Math.abs(String.valueOf(id).hashCode() % 1000);
        PendingIntent pi = PendingIntent.getActivity(c, nid, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String who = name == null || name.trim().isEmpty() ? "A client" : name.trim();
        String text = who + (when == null || when.isEmpty() ? "" : " · " + when);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(Ui.PRIMARY)
                .setContentTitle("New booking request")
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(nid, n);
        } catch (SecurityException ignored) {
            // notifications not allowed on this phone
        }
    }
}
