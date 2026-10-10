package io.github.cyba26.transport;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Arrêt choisi pour chaque widget et dernier résultat connu pour chaque arrêt. */
final class WidgetStore {

    /** Au-delà, un chargement interrompu (processus tué) n'est plus affiché comme en cours. */
    private static final long LOADING_TIMEOUT_MS = 30_000;

    private WidgetStore() {}

    static final class Snapshot {
        final List<Departure> departures;
        /** Heure du dernier chargement réussi, 0 si jamais chargé. */
        final long fetchedAt;
        /** Heure du dernier échec, 0 si aucun. */
        final long errorAt;
        final long loadingSince;

        Snapshot(List<Departure> departures, long fetchedAt, long errorAt, long loadingSince) {
            this.departures = departures;
            this.fetchedAt = fetchedAt;
            this.errorAt = errorAt;
            this.loadingSince = loadingSince;
        }

        boolean isLoading(long now) {
            return loadingSince > 0 && now - loadingSince < LOADING_TIMEOUT_MS;
        }

        boolean lastAttemptFailed() {
            return errorAt > fetchedAt;
        }

        long lastAttemptAt() {
            return Math.max(fetchedAt, errorAt);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("transport_widget", Context.MODE_PRIVATE);
    }

    static String stationFor(Context context, int widgetId) {
        return prefs(context).getString("station_" + widgetId, Station.ALL[0].id);
    }

    static void setStation(Context context, int widgetId, String stationId) {
        prefs(context).edit().putString("station_" + widgetId, stationId).apply();
    }

    static void removeWidget(Context context, int widgetId) {
        prefs(context).edit().remove("station_" + widgetId).apply();
    }

    static Snapshot load(Context context, String stationId) {
        SharedPreferences p = prefs(context);
        List<Departure> departures = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(p.getString("departures_" + stationId, "[]"));
            for (int i = 0; i < array.length(); i++) {
                departures.add(Departure.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            departures = Collections.emptyList();
        }
        return new Snapshot(departures,
                p.getLong("fetched_at_" + stationId, 0),
                p.getLong("error_at_" + stationId, 0),
                p.getLong("loading_since_" + stationId, 0));
    }

    static void setLoading(Context context, String stationId, boolean loading) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (loading) {
            editor.putLong("loading_since_" + stationId, System.currentTimeMillis());
        } else {
            editor.remove("loading_since_" + stationId);
        }
        editor.commit();
    }

    static void saveSuccess(Context context, String stationId, List<Departure> departures) {
        JSONArray array = new JSONArray();
        try {
            for (Departure departure : departures) array.put(departure.toJson());
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs(context).edit()
                .putString("departures_" + stationId, array.toString())
                .putLong("fetched_at_" + stationId, System.currentTimeMillis())
                .commit();
    }

    static void saveError(Context context, String stationId) {
        prefs(context).edit().putLong("error_at_" + stationId, System.currentTimeMillis()).commit();
    }
}
