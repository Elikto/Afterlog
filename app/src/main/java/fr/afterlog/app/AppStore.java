package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class AppStore {
    private static final String KEY = "afterlog_v1";
    private final SharedPreferences preferences;
    private JSONArray events = new JSONArray();
    private JSONArray entries = new JSONArray();

    AppStore(Context context) {
        preferences = context.getSharedPreferences("afterlog_private", Context.MODE_PRIVATE);
        try {
            JSONObject root = new JSONObject(preferences.getString(KEY, "{}"));
            events = root.optJSONArray("events") != null ? root.getJSONArray("events") : new JSONArray();
            entries = root.optJSONArray("entries") != null ? root.getJSONArray("entries") : new JSONArray();
        } catch (Exception ignored) { events = new JSONArray(); entries = new JSONArray(); }
    }
    private void persist() {
        JSONObject root = new JSONObject();
        try { root.put("format", "afterlog-v1"); root.put("events", events); root.put("entries", entries); }
        catch (Exception e) { throw new IllegalStateException(e); }
        if (!preferences.edit().putString(KEY, root.toString()).commit()) {
            throw new IllegalStateException("Impossible d'enregistrer les données");
        }
    }
    private static long newId() {
        return System.currentTimeMillis() * 10000L + new SecureRandom().nextInt(10000);
    }
    List<JSONObject> events() {
        List<JSONObject> result = toList(events);
        Collections.sort(result, (a, b) -> {
            int byDate = b.optString("date").compareTo(a.optString("date"));
            return byDate == 0 ? Long.compare(b.optLong("id"), a.optLong("id")) : byDate;
        });
        return result;
    }
    List<JSONObject> entries(long eventId) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject item : toList(entries)) {
            if (item.optLong("eventId") == eventId) result.add(item);
        }
        Collections.sort(result, (a,b) -> {
            int byDate = b.optString("date").compareTo(a.optString("date"));
            return byDate == 0 ? b.optString("time").compareTo(a.optString("time")) : byDate;
        });
        return result;
    }
    List<JSONObject> allEntries() { return toList(entries); }
    JSONObject event(long id) {
        for (JSONObject item : toList(events)) if (item.optLong("id") == id) return item;
        return null;
    }
    JSONObject eventByUrl(String url) {
        for (JSONObject item : toList(events)) if (!url.isEmpty() && url.equals(item.optString("url"))) return item;
        return null;
    }
    JSONObject saveEvent(JSONObject event) {
        try {
            if (event.optLong("id") == 0) event.put("id", newId());
            JSONArray next = new JSONArray();
            for (JSONObject item : toList(events))
                if (item.optLong("id") != event.optLong("id")) next.put(item);
            next.put(event);
            events = next; persist(); return event;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    JSONObject saveEntry(JSONObject entry) {
        try {
            if (entry.optLong("id") == 0) entry.put("id", newId());
            JSONArray next = new JSONArray();
            for (JSONObject item : toList(entries))
                if (item.optLong("id") != entry.optLong("id")) next.put(item);
            next.put(entry);
            entries = next; persist(); return entry;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    void deleteEvent(long id) {
        JSONArray next = new JSONArray(), other = new JSONArray();
        for (JSONObject item : toList(events)) if (item.optLong("id") != id) next.put(item);
        for (JSONObject item : toList(entries)) if (item.optLong("eventId") != id) other.put(item);
        events = next; entries = other; persist();
    }
    void deleteEntry(long id) {
        JSONArray next = new JSONArray();
        for (JSONObject item : toList(entries)) if (item.optLong("id") != id) next.put(item);
        entries = next; persist();
    }
    String backup() {
        try {
            return new JSONObject().put("format","afterlog-v1")
                .put("events", events).put("entries", entries).toString(2);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    void restore(String content) throws Exception {
        JSONObject root = new JSONObject(content);
        if (!"afterlog-v1".equals(root.optString("format"))) throw new IllegalArgumentException("Format inconnu");
        JSONArray newEvents = root.getJSONArray("events");
        JSONArray newEntries = root.getJSONArray("entries");
        if (newEvents.length() > 10000 || newEntries.length() > 200000)
            throw new IllegalArgumentException("Sauvegarde trop volumineuse");
        java.util.HashSet<Long> ids = new java.util.HashSet<>();
        for (int i=0; i<newEvents.length(); i++) {
            JSONObject obj = newEvents.getJSONObject(i);
            if (obj.getLong("id") <= 0 || !ids.add(obj.getLong("id")) || !obj.has("title"))
                throw new IllegalArgumentException("Événement invalide");
        }
        for (int i=0; i<newEntries.length(); i++) {
            JSONObject obj = newEntries.getJSONObject(i);
            if (!ids.contains(obj.getLong("eventId")) || !obj.has("category"))
                throw new IllegalArgumentException("Consommation invalide");
        }
        events = newEvents; entries = newEntries; persist();
    }
    private static List<JSONObject> toList(JSONArray arr) {
        List<JSONObject> result = new ArrayList<>();
        for (int i=0;i<arr.length();i++) {
            JSONObject object = arr.optJSONObject(i);
            if (object != null) result.add(object);
        }
        return result;
    }
}
