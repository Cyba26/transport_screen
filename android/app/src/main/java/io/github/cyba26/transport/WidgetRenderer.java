package io.github.cyba26.transport;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.SizeF;
import android.view.View;
import android.widget.RemoteViews;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Construit l'affichage du widget. Trois mises en page selon la taille :
 * TINY (1 colonne), ROW (bande d'une ligne de haut) et LIST (2 à 6 passages).
 */
final class WidgetRenderer {

    private static final int MAX_ROWS = 6;
    /** Un train parti depuis moins de 30 s reste affiché ("< 1 min"). */
    private static final long DEPARTED_GRACE_MS = 30_000;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private enum Kind { TINY, ROW, LIST }

    private static final class Variant {
        final Kind kind;
        final int rows;
        final boolean showDestination;
        final SizeF minSize;

        Variant(Kind kind, int rows, boolean showDestination, float minWidthDp, float minHeightDp) {
            this.kind = kind;
            this.rows = rows;
            this.showDestination = showDestination;
            this.minSize = new SizeF(minWidthDp, minHeightDp);
        }
    }

    private static final Variant[] VARIANTS = buildVariants();

    private WidgetRenderer() {}

    private static Variant[] buildVariants() {
        List<Variant> variants = new ArrayList<>();
        variants.add(new Variant(Kind.TINY, 1, false, 40, 40));
        variants.add(new Variant(Kind.ROW, 3, true, 120, 40));
        for (int rows = 2; rows <= MAX_ROWS; rows++) {
            // Hauteur de widget_list.xml : 16dp de marges + 28dp d'en-tête + 4dp + 26dp par passage
            float height = 48 + 26 * rows;
            variants.add(new Variant(Kind.LIST, rows, false, 120, height));
            variants.add(new Variant(Kind.LIST, rows, true, 200, height));
        }
        return variants.toArray(new Variant[0]);
    }

    static RemoteViews build(Context context, int widgetId, Station station,
                             WidgetStore.Snapshot snapshot, Bundle options) {
        long now = System.currentTimeMillis();
        List<Departure> upcoming = new ArrayList<>();
        for (Departure departure : snapshot.departures) {
            if (departure.time >= now - DEPARTED_GRACE_MS) upcoming.add(departure);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ : le launcher choisit lui-même la variante adaptée à la taille réelle
            Map<SizeF, RemoteViews> layouts = new ArrayMap<>();
            for (Variant variant : VARIANTS) {
                layouts.put(variant.minSize, build(context, widgetId, station, snapshot, upcoming, variant, now));
            }
            return new RemoteViews(layouts);
        }
        return build(context, widgetId, station, snapshot, upcoming, pickVariant(context, options), now);
    }

    /** Même règle que RemoteViews sur Android 12+ : la plus proche parmi celles qui tiennent. */
    private static Variant pickVariant(Context context, Bundle options) {
        boolean landscape = context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int width = options.getInt(landscape
                ? AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int height = options.getInt(landscape
                ? AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (width == 0 || height == 0) {
            width = 250;
            height = 130;
        }

        Variant best = VARIANTS[0];
        float bestDistance = Float.MAX_VALUE;
        for (Variant variant : VARIANTS) {
            float dw = width - variant.minSize.getWidth();
            float dh = height - variant.minSize.getHeight();
            if (dw < 0 || dh < 0) continue;
            float distance = dw * dw + dh * dh;
            if (distance < bestDistance) {
                best = variant;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static RemoteViews build(Context context, int widgetId, Station station, WidgetStore.Snapshot snapshot,
                                     List<Departure> upcoming, Variant variant, long now) {
        RemoteViews views;
        switch (variant.kind) {
            case TINY:
                views = new RemoteViews(context.getPackageName(), R.layout.widget_tiny);
                views.setTextViewText(R.id.next_minutes, upcoming.isEmpty()
                        ? context.getString(R.string.no_value) : minutesLabel(context, upcoming.get(0), now));
                views.setTextViewText(R.id.updated_at, updatedLabel(context, snapshot, false));
                break;

            case ROW:
                views = new RemoteViews(context.getPackageName(), R.layout.widget_row);
                if (upcoming.isEmpty()) {
                    views.setTextViewText(R.id.next_minutes, context.getString(R.string.no_value));
                    views.setTextViewText(R.id.then_minutes, "");
                    views.setTextViewText(R.id.next_detail, statusMessage(context, snapshot, now));
                } else {
                    Departure next = upcoming.get(0);
                    views.setTextViewText(R.id.next_minutes, minutesLabel(context, next, now));
                    views.setTextViewText(R.id.then_minutes, thenLabel(context, upcoming, variant.rows, now));
                    views.setTextViewText(R.id.next_detail, context.getString(R.string.departure_detail,
                            clock(next.time), shortDestination(next.destination)));
                }
                views.setTextViewText(R.id.updated_at, updatedLabel(context, snapshot, true));
                views.setOnClickPendingIntent(R.id.badge, configureIntent(context, widgetId));
                views.setOnClickPendingIntent(R.id.content, openAppIntent(context));
                break;

            default:
                views = new RemoteViews(context.getPackageName(), R.layout.widget_list);
                views.setTextViewText(R.id.station_name, station.name);
                views.setTextViewText(R.id.updated_at, updatedLabel(context, snapshot, true));
                views.removeAllViews(R.id.rows);
                int count = Math.min(variant.rows, upcoming.size());
                for (int i = 0; i < count; i++) {
                    Departure departure = upcoming.get(i);
                    RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_list_item);
                    row.setTextViewText(R.id.row_time, clock(departure.time));
                    row.setTextViewText(R.id.row_destination,
                            variant.showDestination ? shortDestination(departure.destination) : "");
                    row.setTextViewText(R.id.row_minutes, minutesLabel(context, departure, now));
                    views.addView(R.id.rows, row);
                }
                views.setViewVisibility(R.id.empty_text, count == 0 ? View.VISIBLE : View.GONE);
                views.setTextViewText(R.id.empty_text, statusMessage(context, snapshot, now));
                views.setOnClickPendingIntent(R.id.header_station, configureIntent(context, widgetId));
                views.setOnClickPendingIntent(R.id.list_body, openAppIntent(context));
                break;
        }

        views.setImageViewResource(R.id.badge, station.badgeRes);
        views.setContentDescription(R.id.badge, station.lineLabel + " " + station.name);

        boolean loading = snapshot.isLoading(now);
        views.setViewVisibility(R.id.refresh_icon, loading ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.refresh_progress, loading ? View.VISIBLE : View.GONE);
        views.setOnClickPendingIntent(R.id.refresh_button, refreshIntent(context, widgetId));
        return views;
    }

    private static String minutesLabel(Context context, Departure departure, long now) {
        long minutes = Math.round((departure.time - now) / 60_000.0);
        if (minutes <= 0) return context.getString(R.string.departure_imminent);
        if (minutes < 60) return context.getString(R.string.departure_minutes, minutes);
        return context.getString(R.string.departure_hours, minutes / 60, minutes % 60);
    }

    /** "puis 11 min · 18 min" pour les passages suivants. */
    private static String thenLabel(Context context, List<Departure> upcoming, int total, long now) {
        StringBuilder following = new StringBuilder();
        for (int i = 1; i < Math.min(upcoming.size(), total); i++) {
            if (following.length() > 0) following.append(" · ");
            following.append(minutesLabel(context, upcoming.get(i), now));
        }
        return following.length() == 0 ? "" : context.getString(R.string.then_departures, following);
    }

    /** Heure du dernier chargement réussi, préfixée de ⚠ si la dernière tentative a échoué. */
    private static String updatedLabel(Context context, WidgetStore.Snapshot snapshot, boolean withPrefix) {
        String time = snapshot.fetchedAt == 0 ? context.getString(R.string.no_time) : clock(snapshot.fetchedAt);
        String label = withPrefix ? context.getString(R.string.updated_at, time) : time;
        return snapshot.lastAttemptFailed() ? "⚠ " + label : label;
    }

    private static String statusMessage(Context context, WidgetStore.Snapshot snapshot, long now) {
        if (snapshot.isLoading(now)) return context.getString(R.string.status_loading);
        if (snapshot.lastAttemptFailed()) return context.getString(R.string.status_offline);
        if (snapshot.fetchedAt == 0) return context.getString(R.string.status_tap_refresh);
        return context.getString(R.string.status_no_departure);
    }

    /** "Aéroport CDG 2 TGV (Tremblay-en-France)" → "Aéroport CDG 2 TGV". */
    private static String shortDestination(String destination) {
        String trimmed = destination.replaceFirst("\\s*\\([^)]*\\)\\s*$", "");
        return trimmed.isEmpty() ? destination : trimmed;
    }

    private static String clock(long epochMillis) {
        return CLOCK.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }

    private static PendingIntent refreshIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, TransportWidgetProvider.class)
                .setAction(TransportWidgetProvider.ACTION_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        return PendingIntent.getBroadcast(context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent configureIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, WidgetConfigActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                // Une URI distincte par widget pour ne pas partager le même PendingIntent
                .setData(Uri.parse("transport://widget/" + widgetId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
