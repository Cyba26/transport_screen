package io.github.cyba26.transport;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Charge les passages des arrêts affichés et redessine les widgets. */
final class WidgetUpdater {

    private static final String TAG = "TransportWidget";
    /** Les actualisations automatiques (déverrouillage…) sont ignorées si la dernière date de moins de 20 s. */
    private static final long AUTO_REFRESH_MIN_INTERVAL_MS = 20_000;
    private static final long RETRY_DELAY_MS = 2_000;
    /** Un seul chargement à la fois : un clic pendant un déverrouillage attend son tour. */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private WidgetUpdater() {}

    static int[] widgetIds(Context context) {
        return AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, TransportWidgetProvider.class));
    }

    /** Redessine tous les widgets depuis le cache, sans réseau. */
    static void renderAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (int widgetId : widgetIds(context)) {
            render(context, manager, widgetId);
        }
    }

    static void render(Context context, AppWidgetManager manager, int widgetId) {
        Station station = Station.byId(WidgetStore.stationFor(context, widgetId));
        WidgetStore.Snapshot snapshot = WidgetStore.load(context, station.id);
        manager.updateAppWidget(widgetId, WidgetRenderer.build(
                context, widgetId, station, snapshot, manager.getAppWidgetOptions(widgetId)));
    }

    /**
     * Recharge les arrêts affichés par ces widgets puis redessine tout.
     *
     * @param force  false pour une actualisation automatique, limitée par AUTO_REFRESH_MIN_INTERVAL_MS
     * @param onDone appelé une fois terminé, sur un thread d'arrière-plan (peut être null)
     */
    static void refresh(Context context, int[] widgetIds, boolean force, Runnable onDone) {
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();

        Set<String> stations = new LinkedHashSet<>();
        for (int widgetId : widgetIds) stations.add(WidgetStore.stationFor(app, widgetId));
        List<String> toFetch = new ArrayList<>();
        for (String stationId : stations) {
            WidgetStore.Snapshot snapshot = WidgetStore.load(app, stationId);
            if (force || now - snapshot.lastAttemptAt() >= AUTO_REFRESH_MIN_INTERVAL_MS) toFetch.add(stationId);
        }

        // Affiche tout de suite le cache (et la roue de chargement)
        for (String stationId : toFetch) WidgetStore.setLoading(app, stationId, true);
        renderAll(app);

        if (toFetch.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                for (String stationId : toFetch) fetch(app, Station.byId(stationId));
                renderAll(app);
            } finally {
                if (onDone != null) onDone.run();
            }
        });
    }

    private static void fetch(Context context, Station station) {
        try {
            List<Departure> departures;
            try {
                departures = TransportApi.fetchDepartures(station);
            } catch (IOException first) {
                // Juste après le déverrouillage, le réseau met parfois une seconde à revenir
                SystemClock.sleep(RETRY_DELAY_MS);
                departures = TransportApi.fetchDepartures(station);
            }
            WidgetStore.saveSuccess(context, station.id, departures);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Échec du chargement pour " + station.id, e);
            WidgetStore.saveError(context, station.id);
        } finally {
            WidgetStore.setLoading(context, station.id, false);
        }
    }
}
