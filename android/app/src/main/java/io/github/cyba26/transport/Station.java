package io.github.cyba26.transport;

import java.text.Normalizer;
import java.util.Locale;

/** Arrêts suivis par le widget — miroir de la constante STATIONS de index.html. */
final class Station {

    static final Station[] ALL = {
            new Station("laplace", "Laplace", "RER B", R.drawable.badge_rer_b,
                    "STIF:Line::C01743:", "stop_area:SNCF:87758664", null,
                    // Direction Paris uniquement : on écarte la branche sud et les terminus Laplace
                    new String[]{"robinson", "massy", "palaiseau", "orsay", "gif", "saint-remy", "limours", "laplace"}),
            new Station("bicetre", "Hôp. Bicêtre", "Métro 14", R.drawable.badge_m14,
                    "STIF:Line::C01384:", null, "STIF:StopPoint:Q:490849:", new String[0]),
            new Station("barbara", "Barbara", "Métro 4", R.drawable.badge_m4,
                    "STIF:Line::C01374:", null, "STIF:StopPoint:Q:483277:", new String[0]),
    };

    final String id;
    final String name;
    final String lineLabel;
    final int badgeRes;
    final String lineRef;
    /** Arrêt de l'API SNCF, utilisée à la place de PRIM quand il est renseigné. */
    final String sncfStopAreaId;
    /** MonitoringRef de l'API PRIM (stop-monitoring). */
    final String primMonitoringRef;
    /** Mots-clés (minuscules, sans accents) des destinations à ignorer. */
    private final String[] excludedDestinations;

    private Station(String id, String name, String lineLabel, int badgeRes, String lineRef,
                    String sncfStopAreaId, String primMonitoringRef, String[] excludedDestinations) {
        this.id = id;
        this.name = name;
        this.lineLabel = lineLabel;
        this.badgeRes = badgeRes;
        this.lineRef = lineRef;
        this.sncfStopAreaId = sncfStopAreaId;
        this.primMonitoringRef = primMonitoringRef;
        this.excludedDestinations = excludedDestinations;
    }

    boolean acceptsDestination(String destination) {
        String normalized = normalize(destination);
        for (String keyword : excludedDestinations) {
            if (normalized.contains(keyword)) return false;
        }
        return true;
    }

    static Station byId(String id) {
        for (Station station : ALL) {
            if (station.id.equals(id)) return station;
        }
        return ALL[0];
    }

    private static String normalize(String s) {
        String decomposed = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
