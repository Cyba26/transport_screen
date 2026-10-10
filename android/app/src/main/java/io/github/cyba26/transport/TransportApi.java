package io.github.cyba26.transport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Récupère les prochains passages via le proxy Vercel (/api/proxy), qui porte les clés API.
 * Reprend la logique de fetchData() / renderTrains() de index.html.
 */
final class TransportApi {

    private static final int CONNECT_TIMEOUT_MS = 6_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter NAVITIA_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private TransportApi() {}

    /** Passages filtrés sur la ligne et la direction de l'arrêt, triés par heure. */
    static List<Departure> fetchDepartures(Station station) throws IOException {
        try {
            List<Departure> departures;
            if (station.sncfStopAreaId != null) {
                JSONObject json = getJson(proxyUrl("sncf-departures",
                        "stop_area_id", station.sncfStopAreaId, "count", "20"));
                departures = parseSncf(json);
            } else {
                JSONObject json = getJson(proxyUrl("stop-monitoring",
                        "MonitoringRef", station.primMonitoringRef, "LineRef", station.lineRef));
                departures = parsePrim(json, station.lineRef);
            }

            List<Departure> result = new ArrayList<>();
            for (Departure departure : departures) {
                if (station.acceptsDestination(departure.destination)) result.add(departure);
            }
            Collections.sort(result, (a, b) -> Long.compare(a.time, b.time));
            return result;
        } catch (JSONException e) {
            throw new IOException("Réponse API illisible", e);
        }
    }

    private static List<Departure> parseSncf(JSONObject json) throws JSONException, IOException {
        JSONArray items = json.optJSONArray("departures");
        if (items == null) throw new IOException(apiError(json));

        List<Departure> departures = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            JSONObject stopTime = item.optJSONObject("stop_date_time");
            JSONObject display = item.optJSONObject("display_informations");
            if (stopTime == null) continue;
            String raw = stopTime.optString("departure_date_time",
                    stopTime.optString("arrival_date_time", ""));
            try {
                long time = LocalDateTime.parse(raw, NAVITIA_DATE).atZone(PARIS).toInstant().toEpochMilli();
                departures.add(new Departure(time, display == null ? "" : display.optString("direction")));
            } catch (DateTimeParseException ignored) {
                // Passage sans horaire exploitable
            }
        }
        return departures;
    }

    private static List<Departure> parsePrim(JSONObject json, String lineRef) throws JSONException, IOException {
        JSONObject siri = json.optJSONObject("Siri");
        if (siri == null) throw new IOException(apiError(json));
        JSONArray deliveries = siri.getJSONObject("ServiceDelivery").optJSONArray("StopMonitoringDelivery");
        JSONArray visits = deliveries == null || deliveries.length() == 0
                ? null : deliveries.getJSONObject(0).optJSONArray("MonitoredStopVisit");
        if (visits == null) return new ArrayList<>();

        List<Departure> all = new ArrayList<>();
        List<Departure> onLine = new ArrayList<>();
        for (int i = 0; i < visits.length(); i++) {
            JSONObject journey = visits.getJSONObject(i).optJSONObject("MonitoredVehicleJourney");
            if (journey == null) continue;
            JSONObject call = journey.optJSONObject("MonitoredCall");
            if (call == null) continue;

            String raw = firstNonEmpty(call, "ExpectedDepartureTime", "AimedDepartureTime",
                    "ExpectedArrivalTime", "AimedArrivalTime");
            Long time = parseIso(raw);
            if (time == null) continue;

            Departure departure = new Departure(time, firstValue(journey.optJSONArray("DestinationName")));
            all.add(departure);
            JSONObject line = journey.optJSONObject("LineRef");
            if (line != null && lineRef.equals(line.optString("value"))) onLine.add(departure);
        }
        // Comme le web : si aucun passage ne porte le LineRef attendu, on garde tout
        return onLine.isEmpty() ? all : onLine;
    }

    private static String proxyUrl(String endpoint, String... params) throws IOException {
        StringBuilder url = new StringBuilder(BuildConfig.BASE_URL)
                .append("/api/proxy?endpoint=").append(endpoint);
        for (int i = 0; i + 1 < params.length; i += 2) {
            url.append('&').append(params[i]).append('=')
                    .append(URLEncoder.encode(params[i + 1], "UTF-8"));
        }
        return url.toString();
    }

    private static JSONObject getJson(String url) throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "TransportScreen-Android/" + BuildConfig.VERSION_NAME);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = connection.getInputStream()) {
                return new JSONObject(readUtf8(in));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String apiError(JSONObject json) {
        String message = json.optString("message", json.optString("error", ""));
        return message.isEmpty() ? "Réponse API inattendue" : message;
    }

    private static String firstNonEmpty(JSONObject obj, String... keys) {
        for (String key : keys) {
            String value = obj.optString(key, "");
            if (!value.isEmpty() && !"null".equals(value)) return value;
        }
        return null;
    }

    /** Premier "value" d'un tableau SIRI du type [{"value": "..."}]. */
    private static String firstValue(JSONArray array) {
        if (array == null || array.length() == 0) return "";
        JSONObject first = array.optJSONObject(0);
        return first == null ? "" : first.optString("value");
    }

    private static Long parseIso(String raw) {
        if (raw == null) return null;
        try {
            return OffsetDateTime.parse(raw).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(raw).atZone(PARIS).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
