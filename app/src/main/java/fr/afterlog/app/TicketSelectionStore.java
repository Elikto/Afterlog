package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/**
 * Liste de présélection privée (aucun QR code / identifiant de paiement).
 * Les URLs restent seulement dans le stockage privé Android.
 */
final class TicketSelectionStore {
    private static final String FILE="afterlog_ticket_selection";
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE,Context.MODE_PRIVATE);
    }
    static void saveFound(Context context,JSONArray found) throws Exception {
        JSONArray safe=new JSONArray();
        Set<String> urls=new HashSet<>();
        for(int i=0;i<found.length()&&safe.length()<ShotgunTicketDiscovery.MAX_EVENTS;i++) {
            JSONObject ticket=found.optJSONObject(i);
            if(ticket==null)continue;
            String url=ticket.optString("url");
            if(!trusted(url))continue;
            String normalized=canonical(url);
            if(normalized.isEmpty()||!urls.add(normalized))continue;
            String title=ticket.optString("title").trim();
            if(title.length()<3)continue;
            JSONObject record=new JSONObject();
            record.put("title",title.length()>180?title.substring(0,180):title);
            record.put("url",url);
            record.put("date",ticket.optString("date"));
            String poster=ticket.optString("poster");
            record.put("poster",poster.startsWith("https://")?poster:"");
            record.put("selected",true);
            safe.put(record);
        }
        prefs(context).edit().putString("pending",safe.toString())
            .putLong("foundAt",System.currentTimeMillis()).apply();
    }
    static JSONArray list(Context context) {
        try {return new JSONArray(prefs(context).getString("pending","[]"));}
        catch(Exception e){return new JSONArray();}
    }
    static int selectedCount(Context context) {
        int count=0; JSONArray list=list(context);
        for(int i=0;i<list.length();i++)
            if(list.optJSONObject(i)!=null&&list.optJSONObject(i).optBoolean("selected",true))count++;
        return count;
    }
    static void mark(Context context,int index,boolean selected) {
        JSONArray items=list(context);
        JSONObject item=items.optJSONObject(index);
        if(item==null)return;
        try {item.put("selected",selected);}catch(Exception ignored){}
        persist(context,items);
    }
    static void markAll(Context context,boolean selected) {
        JSONArray items=list(context);
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i);
            if(item!=null)try {item.put("selected",selected);}catch(Exception ignored){}
        }
        persist(context,items);
    }
    private static void persist(Context ctx,JSONArray items) {
        prefs(ctx).edit().putString("pending",items.toString()).apply();
    }
    static JSONArray chosen(Context context) {
        JSONArray out=new JSONArray();
        JSONArray all=list(context);
        for(int i=0;i<all.length();i++) {
            JSONObject item=all.optJSONObject(i);
            if(item!=null && item.optBoolean("selected",true))out.put(item);
        }
        return out;
    }
    static void commitExclusions(Context ctx) throws Exception {
        JSONArray all=list(ctx);
        Set<String> excluded=new HashSet<>();
        // Conserver les exclusions d'événements plus anciens n'étant pas dans cette liste.
        excluded.addAll(prefs(ctx).getStringSet("excluded",new HashSet<>()));
        for(int i=0;i<all.length();i++){
            JSONObject item=all.optJSONObject(i);
            if(item==null)continue;
            String url=canonical(item.optString("url"));
            if(url.isEmpty())continue;
            if(item.optBoolean("selected",true))excluded.remove(url);
            else excluded.add(url);
        }
        prefs(ctx).edit().putStringSet("excluded",new HashSet<>(excluded)).apply();
    }
    static boolean isExcluded(Context ctx,String url) {
        return prefs(ctx).getStringSet("excluded",new HashSet<>()).contains(canonical(url));
    }
    static String canonical(String url) {
        if(!trusted(url))return "";
        try {
            return Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString();
        }catch(Exception e){return "";}
    }
    private static boolean trusted(String url) {
        try {
            Uri u=Uri.parse(url);
            String host=u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) &&
                host!=null &&
                (host.equalsIgnoreCase("shotgun.live")||
                 host.toLowerCase(java.util.Locale.ROOT).endsWith(".shotgun.live"));
        }catch(Exception e){return false;}
    }
}
