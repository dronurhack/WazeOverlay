package com.wazeoverlay.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class WazeNotificationListener extends NotificationListenerService {

    private static final String TAG = "WazeListener";
    private static final String WAZE_PACKAGE = "com.waze";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();

        if (!WAZE_PACKAGE.equals(pkg)) return;

        Notification notif = sbn.getNotification();
        if (notif == null) return;

        Bundle extras = notif.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text  = extras.getString(Notification.EXTRA_TEXT, "");
        String full  = (title + " " + text).toLowerCase();

        Log.d(TAG, "Waze notif → title=" + title + " | text=" + text);

        // Only forward real driving alerts (ignore navigation/ETA updates)
        if (!isDrivingAlert(full)) return;

        String category = categorize(full);
        String emoji    = getEmoji(category);

        // Extract action buttons from Waze notification (Toujours là / Disparu)
        PendingIntent stillThereAction = null;
        PendingIntent gonAction        = null;

        Notification.Action[] actions = notif.actions;
        if (actions != null) {
            for (Notification.Action action : actions) {
                if (action == null || action.title == null) continue;
                String actionTitle = action.title.toString().toLowerCase();
                if (actionTitle.contains("toujours") || actionTitle.contains("still")
                        || actionTitle.contains("confirme") || actionTitle.contains("there")) {
                    stillThereAction = action.actionIntent;
                } else if (actionTitle.contains("disp") || actionTitle.contains("gone")
                        || actionTitle.contains("cleared") || actionTitle.contains("effac")) {
                    gonAction = action.actionIntent;
                }
            }
        }

        // Send alert to OverlayService
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_SHOW_ALERT);
        intent.putExtra(OverlayService.EXTRA_EMOJI, emoji);
        intent.putExtra(OverlayService.EXTRA_TITLE, title);
        intent.putExtra(OverlayService.EXTRA_TEXT, text);
        intent.putExtra(OverlayService.EXTRA_CATEGORY, category);
        if (stillThereAction != null) intent.putExtra(OverlayService.EXTRA_STILL_THERE, stillThereAction);
        if (gonAction        != null) intent.putExtra(OverlayService.EXTRA_GONE,        gonAction);

        startService(intent);
    }

    private boolean isDrivingAlert(String text) {
        return text.contains("police") || text.contains("radar") || text.contains("accident")
                || text.contains("danger") || text.contains("route ferm") || text.contains("embouteillage")
                || text.contains("travaux") || text.contains("bouchon") || text.contains("obstacle")
                || text.contains("objet") || text.contains("signalement") || text.contains("alert")
                || text.contains("hazard") || text.contains("cop") || text.contains("speed cam")
                || text.contains("traffic") || text.contains("incident");
    }

    private String categorize(String text) {
        if (text.contains("police") || text.contains("cop") || text.contains("contrôle")) return "police";
        if (text.contains("radar") || text.contains("speed cam")) return "radar";
        if (text.contains("accident")) return "accident";
        if (text.contains("travaux")) return "travaux";
        if (text.contains("embouteillage") || text.contains("bouchon") || text.contains("traffic")) return "trafic";
        if (text.contains("danger") || text.contains("hazard")) return "danger";
        if (text.contains("route ferm") || text.contains("road closed")) return "fermeture";
        if (text.contains("objet") || text.contains("obstacle")) return "obstacle";
        return "alerte";
    }

    private String getEmoji(String category) {
        switch (category) {
            case "police":   return "🚨";
            case "radar":    return "📷";
            case "accident": return "💥";
            case "travaux":  return "🚧";
            case "trafic":   return "🚦";
            case "danger":   return "⚠️";
            case "fermeture":return "🚫";
            case "obstacle": return "🪨";
            default:         return "⚠️";
        }
    }
}
