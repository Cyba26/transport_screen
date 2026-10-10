package io.github.cyba26.transport;

import org.json.JSONException;
import org.json.JSONObject;

/** Un passage : heure de départ (epoch ms) et destination. */
final class Departure {

    final long time;
    final String destination;

    Departure(long time, String destination) {
        this.time = time;
        this.destination = destination;
    }

    JSONObject toJson() throws JSONException {
        return new JSONObject().put("t", time).put("d", destination);
    }

    static Departure fromJson(JSONObject json) {
        return new Departure(json.optLong("t"), json.optString("d"));
    }
}
