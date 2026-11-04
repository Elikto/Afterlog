package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.util.List;

/** Repair legacy imported parties automatically, without making the user
 * delete events, sign in again or visit event URLs. Only public event pages
 * are read. Never reads tickets, QR codes, email or account cookies. */
public final class ShotgunAboutBackfillWorker extends Worker {
    private static final String JOB="noctaria-shotgun-about-backfill-v1";
    private static final String PREFS="noctaria-shotgun-about-backfill-v1";
    private static final int MAX_PER_RUN=12;
    private static final long RETRY_AFTER=24L*60*60*1000;

    public ShotgunAboutBackfillWorker(@NonNull Context context,
                                     @NonNull WorkerParameters parameters){
        super(context,parameters);
    }
    static void schedule(Context context){
        boolean needed=false;
        for(JSONObject event:new AppStore(context).events()){
            if(ShotgunAboutExtractor.validEventUrl(event.optString("url")) &&
               !"event_section".equals(event.optString("shotgunAboutSource"))){
                needed=true;break;
            }
        }
        if(!needed)return;
        Constraints network=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(
            ShotgunAboutBackfillWorker.class).setConstraints(network).build();
        WorkManager.getInstance(context).enqueueUniqueWork(
            JOB,ExistingWorkPolicy.KEEP,work);
    }
    @Override @NonNull public Result doWork(){
        Context ctx=getApplicationContext();
        SharedPreferences attempts=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        int scanned=0;
        List<JSONObject> events=new AppStore(ctx).events();
        for(JSONObject event:events){
            if(isStopped())return Result.retry();
            String url=event.optString("url");
            if(!ShotgunAboutExtractor.validEventUrl(url) ||
               "event_section".equals(event.optString("shotgunAboutSource")))continue;
            String key="attempt_"+event.optLong("id");
            if(now-attempts.getLong(key,0)<RETRY_AFTER)continue;
            if(++scanned>MAX_PER_RUN)break;
            attempts.edit().putLong(key,now).apply();
            try{
                String about=ShotgunAboutExtractor.readPublic(url);
                if(about.isEmpty())continue;
                synchronized(ShotgunSyncService.class){
                    AppStore store=new AppStore(ctx);
                    JSONObject latest=store.event(event.optLong("id"));
                    if(latest==null)continue;
                    latest.put("shotgunAbout",about);
                    latest.put("shotgunAboutSource","event_section");
                    store.saveEvent(latest);
                }
            }catch(Exception ignored){
                // A public page may be temporarily unavailable; leave all data
                // untouched and permit a new attempt on another day.
            }
        }
        return Result.success();
    }
}
