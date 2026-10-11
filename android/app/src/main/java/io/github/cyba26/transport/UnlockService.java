package io.github.cyba26.transport;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Actualise les widgets à chaque déverrouillage du téléphone.
 *
 * Depuis Android 8, ACTION_USER_PRESENT ne peut plus être reçu via le manifeste :
 * il faut un receiver enregistré dans un processus vivant, d'où ce service au premier plan.
 * Sa notification est de priorité minimale (et masquée d'office sur Android 13+,
 * l'application ne demandant pas la permission de notifier).
 */
public class UnlockService extends Service {

    private static final String TAG = "TransportWidget";
    private static final String CHANNEL_ID = "unlock_refresh";
    private static final int NOTIFICATION_ID = 1;

    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int[] widgetIds = WidgetUpdater.widgetIds(context);
            if (widgetIds.length == 0) {
                stopSelf();
                return;
            }
            WidgetUpdater.refresh(context, widgetIds, false, null);
        }
    };

    /** Démarre le service s'il y a au moins un widget ; ignoré si Android l'interdit à cet instant. */
    static void start(Context context) {
        if (WidgetUpdater.widgetIds(context).length == 0) return;
        try {
            context.startForegroundService(new Intent(context, UnlockService.class));
        } catch (IllegalStateException | SecurityException e) {
            // ForegroundServiceStartNotAllowedException (Android 12+) depuis l'arrière-plan
            Log.w(TAG, "Service de déverrouillage non démarré", e);
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, UnlockService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Écran allumé : le chargement démarre avant même la fin du déverrouillage ;
        // le déverrouillage qui suit est absorbé par le délai minimal de WidgetUpdater
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // EXPORTED obligatoire : USER_PRESENT est envoyé par SystemUI, qui n'a pas l'uid système,
            // et un receiver NOT_EXPORTED ne reçoit que l'uid système ou sa propre appli.
            // Sans risque : ces deux actions sont protégées, aucune appli ne peut les envoyer.
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(unlockReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
        } catch (IllegalStateException | SecurityException e) {
            Log.w(TAG, "Passage au premier plan refusé", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(unlockReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.unlock_channel_name), NotificationManager.IMPORTANCE_MIN);
        channel.setDescription(getString(R.string.unlock_channel_description));
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        PendingIntent openApp = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_tp)
                .setContentTitle(getString(R.string.unlock_notification_title))
                .setContentText(getString(R.string.unlock_notification_text))
                .setContentIntent(openApp)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }
}
