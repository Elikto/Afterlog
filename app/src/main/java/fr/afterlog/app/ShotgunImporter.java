package fr.afterlog.app;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

final class ShotgunImporter {
    static JSONObject fetch(String url) throws Exception {
        URI uri = new URI(url.trim());
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) ||
            host == null || !(host.equalsIgnoreCase("shotgun.live") ||
            host.equalsIgnoreCase("www.shotgun.live"))) {
            throw new IllegalArgumentException("Utilise un lien HTTPS shotgun.live");
        }
        Document doc = Jsoup.connect(uri.toString()).userAgent(
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .timeout(18000).maxBodySize(3_000_000).followRedirects(true).get();

        JSONObject event = new JSONObject();
        String title = meta(doc, "og:title");
        if (title.isEmpty()) title = doc.title();
        title = title.replaceAll("(?i)\\s*[|–-]\\s*Shotgun\\s*$", "").trim();
        String poster = meta(doc, "og:image");
        String description = meta(doc, "og:description");
        if (description.isEmpty()) description = meta(doc, "description");
        event.put("title", title);
        event.put("poster", poster);
        event.put("description", description);
        // Only the event's own visible "À propos" qualifies for this field.
        // The og:description above is deliberately not a substitute.
        String actualAbout=ShotgunAboutExtractor.extract(doc);
        if(!actualAbout.isEmpty()){
            event.put("shotgunAbout",actualAbout);
            event.put("shotgunAboutSource","event_section");
        }
        event.put("url", uri.toString());
        event.put("origin", "Shotgun");
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                String raw = script.data().trim();
                if (raw.isEmpty()) raw = script.html().trim();
                if (raw.startsWith("{")) parseJsonLd(new JSONObject(raw), event);
                if (raw.startsWith("[")) {
                    JSONArray array = new JSONArray(raw);
                    for (int i=0; i<array.length(); i++)
                        if (array.optJSONObject(i) != null) parseJsonLd(array.getJSONObject(i), event);
                }
            } catch (Exception ignored) {}
        }
        if (event.optString("title").isEmpty()) throw new IllegalStateException(
            "Shotgun n'a pas fourni de nom d'événement. Essaie une saisie manuelle.");
        return event;
    }
    private static String meta(Document doc, String key) {
        Element el = doc.selectFirst("meta[property='" + key + "']");
        if (el == null) el = doc.selectFirst("meta[name='" + key + "']");
        return el == null ? "" : el.attr("content").trim();
    }
    private static void parseJsonLd(JSONObject node, JSONObject target) throws Exception {
        if (node.has("@graph")) {
            JSONArray graph = node.optJSONArray("@graph");
            if (graph != null) for (int i=0;i<graph.length();i++)
                if (graph.optJSONObject(i) != null) parseJsonLd(graph.getJSONObject(i),target);
        }
        String type = node.optString("@type");
        if (!type.toLowerCase().contains("event")) return;
        if (target.optString("title").isEmpty()) target.put("title", node.optString("name"));
        if (target.optString("description").isEmpty()) target.put("description", node.optString("description"));
        if (target.optString("poster").isEmpty()) {
            Object img = node.opt("image");
            if (img instanceof String) target.put("poster", img);
            else if (img instanceof JSONArray) target.put("poster", ((JSONArray)img).optString(0));
        }
        readDate(node.optString("startDate"), target, "date", "start");
        readDate(node.optString("endDate"), target, "endDate", "end");
        JSONObject location = node.optJSONObject("location");
        if (location != null) {
            target.put("venue", location.optString("name"));
            JSONObject address = location.optJSONObject("address");
            if (address != null) {
                target.put("city", address.optString("addressLocality"));
                target.put("address", address.optString("streetAddress"));
            } else if (location.has("address")) target.put("address", location.optString("address"));
        }
        target.put("artists", listNames(node.opt("performer")));
        target.put("organizer", listNames(node.opt("organizer")));
    }
    private static void readDate(String iso, JSONObject result, String dateKey, String timeKey) throws Exception {
        if (iso.length() >= 10) result.put(dateKey, iso.substring(0,10));
        if (iso.length() >= 16 && iso.charAt(10) == 'T')
            result.put(timeKey, iso.substring(11,16));
    }
    private static String listNames(Object obj) {
        List<String> names = new ArrayList<>();
        if (obj instanceof JSONObject) {
            names.add(((JSONObject)obj).optString("name"));
        } else if (obj instanceof JSONArray) {
            JSONArray arr = (JSONArray)obj;
            for (int i=0;i<arr.length();i++) {
                JSONObject j = arr.optJSONObject(i);
                if (j!=null) names.add(j.optString("name"));
                else if (arr.opt(i) instanceof String) names.add(arr.optString(i));
            }
        } else if (obj instanceof String) names.add((String)obj);
        return android.text.TextUtils.join(", ", names).trim();
    }
}
