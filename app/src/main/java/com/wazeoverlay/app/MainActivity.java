package com.wazeoverlay.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY_PERMISSION = 1001;
    private TextView tvStatus;
    private Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnStart = findViewById(R.id.btn_start);

        createNotificationChannel();

        btnStart.setOnClickListener(v -> checkAndRequestPermissions());

        Button btnNotifAccess = findViewById(R.id.btn_notif_access);
        btnNotifAccess.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean overlayOk = Settings.canDrawOverlays(this);
        boolean notifOk = isNotificationListenerEnabled();

        if (overlayOk && notifOk) {
            tvStatus.setText("✅ Tout est configuré ! WazeOverlay est actif.");
            tvStatus.setTextColor(getColor(android.R.color.holo_green_dark));
            btnStart.setText("⚙️ Relancer le service");
        } else {
            StringBuilder sb = new StringBuilder("Permissions manquantes :\n");
            if (!overlayOk) sb.append("❌ Afficher par-dessus les apps\n");
            if (!notifOk) sb.append("❌ Accès aux notifications\n");
            tvStatus.setText(sb.toString());
            tvStatus.setTextColor(getColor(android.R.color.holo_red_dark));
            btnStart.setText("🔑 Configurer les permissions");
        }
    }

    private void checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            return;
        }

        if (!isNotificationListenerEnabled()) {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            Toast.makeText(this,
                    "Activez WazeOverlay dans la liste des apps autorisées",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // All permissions OK — start the overlay service
        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "🚗 WazeOverlay démarré !", Toast.LENGTH_SHORT).show();
    }

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "overlay_service_channel",
                    "WazeOverlay Service",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Service actif en arrière-plan pour les alertes");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            updateStatus();
        }
    }
}
