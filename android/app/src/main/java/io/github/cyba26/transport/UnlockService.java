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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

/**
 * Actualise les widgets à chaque déverrouillage du téléphone.
 *
 * Depuis Android 8, ACTION_USER_PRESENT ne peut plus être reçu via le manifeste :
 * il faut un receiver enregistré dans un processus vivant, d'où ce service au premier plan.
 * Sa notification est de priorité minimale. Si les notifications de l'appli sont coupées,
 * MainActivity affiche un bandeau qui renvoie vers les réglages Android.
 */
public class UnlockService extends Service {

    private static final String TAG = "TransportWidget";
    static final String CHANNEL_ID = "unlock_refresh";
    private static final int NOTIFICATION_ID = 1;
    /** Écran allumé : les minutes restantes sont recalculées toutes les 30 s (sans réseau)… */
    private static final long TICK_MS = 30_000;
    /** … et les passages rechargés toutes les 2 min, tant que l'écran reste allumé. */
    private static final long SCREEN_ON_REFRESH_MS = 120_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastRefreshAt;

    /**
     * Sans ce minuteur, un widget regardé longtemps après le déverrouillage gardait les minutes
     * calculées au déverrouillage (« 3 min » pour un train déjà parti).
     */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            int[] widgetIds = WidgetUpdater.widgetIds(UnlockService.this);
            if (widgetIds.length == 0) {
                stopSelf();
                return;
            }
            if (System.currentTimeMillis() - lastRefreshAt >= SCREEN_ON_REFRESH_MS) {
                refreshNow(widgetIds);
            } else {
                WidgetUpdater.renderAll(UnlockService.this);
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                handler.removeCallbacks(tick);
                return;
            }
            int[] widgetIds = WidgetUpdater.widgetIds(context);
            if (widgetIds.length == 0) {
                stopSelf();
                return;
            }
            refreshNow(widgetIds);
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, TICK_MS);
        }
    };

    private void refreshNow(int[] widgetIds) {
        lastRefreshAt = System.currentTimeMillis();
        WidgetUpdater.refresh(this, widgetIds, false, null);
    }

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
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // EXPORTED obligatoire : USER_PRESENT est envoyé par SystemUI, qui n'a pas l'uid système,
            // et un receiver NOT_EXPORTED ne reçoit que l'uid système ou sa propre appli.
            // Sans risque : ces deux actions sont protégées, aucune appli ne peut les envoyer.
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(unlockReceiver, filter);
        }
        PowerManager power = getSystemService(PowerManager.class);
        if (power != null && power.isInteractive()) handler.postDelayed(tick, TICK_MS);
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
        handler.removeCallbacks(tick);
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
