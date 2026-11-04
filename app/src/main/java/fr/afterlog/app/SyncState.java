package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** État local de la synchronisation, jamais envoyé sur GitHub. */
final class SyncState {
    static final String PREFS="afterlog_sync";
    static final String IDLE="idle", RUNNING="running", DONE="done", SELECTION="selection", PARTIAL="partial", BLOCKED="blocked", ERROR="error", CANCELED="canceled";
    static void save(Context ctx,String state,String phase,int done,int total,long eta,String detail) {
        SharedPreferences p=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        p.edit().putString("state",state).putString("phase",phase)
            .putInt("done",done).putInt("total",total)
            .putLong("eta",eta).putString("detail",detail)
            .putLong("updatedAt",System.currentTimeMillis()).apply();
    }
    static JSONObject read(Context ctx) {
        SharedPreferences p=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        JSONObject result=new JSONObject();
        try {
            result.put("state",p.getString("state",IDLE));
            result.put("phase",p.getString("phase",""));
            result.put("done",p.getInt("done",0));
            result.put("total",p.getInt("total",0));
            result.put("eta",p.getLong("eta",-1));
            result.put("detail",p.getString("detail",""));
            result.put("updatedAt",p.getLong("updatedAt",0));
        }catch(Exception ignored){}
        return result;
    }
}
