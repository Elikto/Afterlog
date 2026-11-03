package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Échecs d'import privés, lisibles depuis l'accueil après la synchronisation. */
final class SyncFailureStore {
    private static final String PREFS="afterlog_failed_events";
    private static final String KEY="failures";
    static void clear(Context ctx) {
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(KEY,"[]").apply();
    }
    static void remove(Context ctx,String url) {
        JSONArray old=list(ctx),next=new JSONArray();
        for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);
            if(item!=null&&!url.equals(item.optString("url")))next.put(item);
        }
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString(KEY,next.toString()).apply();
    }
    static JSONArray list(Context ctx) {
        try {
            SharedPreferences p=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
            return new JSONArray(p.getString(KEY,"[]"));
        }catch(Exception e){return new JSONArray();}
    }
    /**
     * Echecs concernant uniquement les soirées cochées du dernier import.
     * Une ancienne erreur d'une soirée décochée ne doit plus apparaître.
     */
    static JSONArray actionable(Context ctx) {
        JSONArray selected=TicketSelectionStore.chosen(ctx);
        JSONArray failures=list(ctx), active=new JSONArray();
        for(int i=0;i<failures.length();i++) {
            JSONObject fail=failures.optJSONObject(i);
            if(fail==null)continue;
            String url=TicketSelectionStore.canonical(fail.optString("url"));
            for(int j=0;j<selected.length();j++) {
                JSONObject selectedEvent=selected.optJSONObject(j);
                if(selectedEvent==null)continue;
                String expected=TicketSelectionStore.canonical(selectedEvent.optString("url"));
                if(!url.isEmpty() && url.equals(expected)) {
                    active.put(fail);break;
                }
            }
        }
        return active;
    }
    static int actionableCount(Context ctx){return actionable(ctx).length();}
    static void discardStale(Context ctx) {
        JSONArray current=actionable(ctx);
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString(KEY,current.toString()).apply();
    }
    static void add(Context ctx,JSONObject source,String reason) {
        if(source==null)return;
        try {
            String link=source.optString("url");
            String title=source.optString("title","Événement Shotgun");
            JSONArray existing=list(ctx), next=new JSONArray();
            for(int i=0;i<existing.length();i++) {
                JSONObject old=existing.optJSONObject(i);
                if(old!=null&&!link.equals(old.optString("url")))next.put(old);
            }
            JSONObject failed=new JSONObject();
            failed.put("title",title);
            failed.put("url",link);
            failed.put("poster",source.optString("poster"));
            failed.put("date",source.optString("date"));
            failed.put("reason",reason);
            failed.put("at",System.currentTimeMillis());
            next.put(failed);
            ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(KEY,next.toString()).apply();
        }catch(Exception ignored){}
    }
}
