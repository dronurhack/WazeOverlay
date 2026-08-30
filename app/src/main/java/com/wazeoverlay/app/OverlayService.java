package com.wazeoverlay.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private static final String TAG = "OverlayService";
    public static final String ACTION_SHOW_ALERT  = "com.wazeoverlay.SHOW_ALERT";
    public static final String EXTRA_EMOJI        = "emoji";
    public static final String EXTRA_TITLE        = "title";
    public static final String EXTRA_TEXT         = "text";
    public static final String EXTRA_CATEGORY     = "category";
    public static final String EXTRA_STILL_THERE  = "still_there";
    public static final String EXTRA_GONE         = "gone";

    private WindowManager windowManager;
    private View overlayView;
    private Handler handler;
    private Runnable dismissRunnable;

    // How long to show the overlay (ms) if user doesn't interact
    private static final long AUTO_DISMISS_MS = 12_000;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        startForegroundNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SHOW_ALERT.equals(intent.getAction())) {
            String emoji    = intent.getStringExtra(EXTRA_EMOJI);
            String title    = intent.getStringExtra(EXTRA_TITLE);
            String text     = intent.getStringExtra(EXTRA_TEXT);
            String category = intent.getStringExtra(EXTRA_CATEGORY);
            PendingIntent stillThereIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? intent.getParcelableExtra(EXTRA_STILL_THERE, PendingIntent.class)
                    : (PendingIntent) intent.getParcelableExtra(EXTRA_STILL_THERE);
            PendingIntent goneIntent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ? intent.getParcelableExtra(EXTRA_GONE, PendingIntent.class)
                    : (PendingIntent) intent.getParcelableExtra(EXTRA_GONE);

            showOverlay(emoji, title, text, category, stillThereIntent, goneIntent);
            vibrate(category);
        }
        return START_STICKY;
    }

    private void showOverlay(String emoji, String title, String text, String category,
                             PendingIntent stillThere, PendingIntent gone) {
        // Remove previous overlay if any
        dismissOverlay();

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_alert, null);

        TextView tvEmoji    = overlayView.findViewById(R.id.tv_emoji);
        TextView tvTitle    = overlayView.findViewById(R.id.tv_title);
        TextView tvText     = overlayView.findViewById(R.id.tv_text);
        Button   btnStill   = overlayView.findViewById(R.id.btn_still_there);
        Button   btnGone    = overlayView.findViewById(R.id.btn_gone);
        View     card       = overlayView.findViewById(R.id.card_root);

        tvEmoji.setText(emoji != null ? emoji : "⚠️");
        tvTitle.setText(title != null ? title : "Alerte Waze");
        tvText.setText(text  != null ? text  : "");

        // Color border by category
        int borderColor = getCategoryColor(category);
        card.setBackgroundColor(borderColor);

        // Buttons for community confirmation
        if (stillThere != null) {
            btnStill.setVisibility(View.VISIBLE);
            final PendingIntent pi = stillThere;
            btnStill.setOnClickListener(v -> {
                try { pi.send(); } catch (Exception e) { Log.e(TAG, "stillThere send failed", e); }
                dismissOverlay();
            });
        } else {
            btnStill.setVisibility(View.GONE);
        }

        if (gone != null) {
            btnGone.setVisibility(View.VISIBLE);
            final PendingIntent pi = gone;
            btnGone.setOnClickListener(v -> {
                try { pi.send(); } catch (Exception e) { Log.e(TAG, "gone send failed", e); }
                dismissOverlay();
            });
        } else {
            btnGone.setVisibility(View.GONE);
        }

        // Close button (X)
        overlayView.findViewById(R.id.btn_close).setOnClickListener(v -> dismissOverlay());

        // Window params — floating, non-focusable so it doesn't steal input
        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = (int) (dm.widthPixels * 0.92f);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = 80; // offset from top

        windowManager.addView(overlayView, params);

        // Auto-dismiss after delay
        dismissRunnable = this::dismissOverlay;
        handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS);
    }

    private void dismissOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            overlayView = null;
        }
        if (dismissRunnable != null) {
            handler.removeCallbacks(dismissRunnable);
            dismissRunnable = null;
        }
    }

    private int getCategoryColor(String category) {
        if (category == null) return Color.parseColor("#CC2C2C2C");
        switch (category) {
            case "police":   return Color.parseColor("#CC1A1A2E"); // dark navy red
            case "radar":    return Color.parseColor("#CC1A237E"); // dark navy blue
            case "accident": return Color.parseColor("#CCB71C1C"); // deep red
            case "travaux":  return Color.parseColor("#CCE65100"); // deep orange
            case "trafic":   return Color.parseColor("#CC827717"); // dark amber
            case "danger":   return Color.parseColor("#CCAD1457"); // dark pink
            case "fermeture":return Color.parseColor("#CC4A148C"); // deep purple
            case "obstacle": return Color.parseColor("#CC37474F"); // blue grey
            default:         return Color.parseColor("#CC2C2C2C");
        }
    }

    private void vibrate(String category) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;

        long[] pattern;
        if ("police".equals(category) || "radar".equals(category)) {
            // Urgent: long-short-long
            pattern = new long[]{0, 500, 150, 500, 150, 800};
        } else if ("accident".equals(category)) {
            pattern = new long[]{0, 300, 100, 300};
        } else {
            pattern = new long[]{0, 250, 100, 250};
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }

    private void startForegroundNotification() {
        String channelId = "overlay_service_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "WazeOverlay Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("WazeOverlay actif 🚗")
                .setContentText("En attente des alertes Waze…")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        dismissOverlay();
        super.onDestroy();
    }
}
