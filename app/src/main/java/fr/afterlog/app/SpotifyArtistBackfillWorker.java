package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Backfill across the WHOLE private journal, not only the event currently open.
 * Discovers unique artist names from all saved line-ups, shares only validated
 * profiles/photos across occurrences, and never uploads the journal.
 */
public final class SpotifyArtistBackfillWorker extends Worker {
    private static final String JOB="afterlog-spotify-artists-backfill-v4";
    private static final String PERIODIC="afterlog-spotify-artists-daily-v4";
    private static final String PREFS="afterlog_spotify_backfill_v4";
    private static final int MAX_LOOKUPS=8; // Keep each background batch under Android's worker deadline.
    private static final long FAILED_RETRY=6L*60*60*1000;
    private static final long QUEUE_COOLDOWN=2L*60*1000;

    private static final class Artist {
        final String name;
        String shotgunPhoto="", spotifyLink="", spotifyPhoto="";
        boolean ambiguous=false;
        Artist(String name){this.name=name;}
    }
    private static final class Counts {
        int total,linked,photos,events,withoutLineup;
    }

    public SpotifyArtistBackfillWorker(@NonNull Context ctx,
                                       @NonNull WorkerParameters params){
        super(ctx,params);
    }
    static void schedule(Context context){
        schedule(context,false);
    }
    static void refreshAll(Context context){
        long now=System.currentTimeMillis();
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(now-prefs.getLong("forcedAt",0)<60_000L)return;
        SharedPreferences.Editor editor=prefs.edit().putLong("forcedAt",now);
        for(String key:prefs.getAll().keySet())
            if(key.startsWith("attempt_")||key.startsWith("portrait_"))
                editor.remove(key);
        editor.apply();
        SpotifyArtistFinder.clearNegativeCache(context);
        schedule(context,true);
    }
    private static void schedule(Context context,boolean replace){
        Constraints network=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        WorkManager manager=WorkManager.getInstance(context);
        PeriodicWorkRequest periodic=new PeriodicWorkRequest.Builder(
            SpotifyArtistBackfillWorker.class,24,TimeUnit.HOURS)
            .setConstraints(network).build();
        manager.enqueueUniquePeriodicWork(PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,periodic);

        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if(!replace && now-prefs.getLong("lastQueued",0)<QUEUE_COOLDOWN)return;
        prefs.edit().putLong("lastQueued",now).apply();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(
            SpotifyArtistBackfillWorker.class).setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.LINEAR,15,TimeUnit.MINUTES).build();
        manager.enqueueUniqueWork(JOB,
            replace?ExistingWorkPolicy.REPLACE:ExistingWorkPolicy.KEEP,request);
    }

    static String status(Context context){
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String state=prefs.getString("state","");
        if("".equals(state))return "Recherche Spotify prévue pour toutes tes soirées.";
        int total=prefs.getInt("total",0);
        int events=prefs.getInt("events",0);
        int withoutLineup=prefs.getInt("withoutLineup",0);
        int linked=prefs.getInt("linked",0);
        int photos=prefs.getInt("photos",0);
        int pending=Math.max(0,total-linked);
        int missingPortraits=Math.max(0,linked-photos);
        String suffix="running".equals(state)?" · Recherche en cours":
            "partial".equals(state)?" · Autres artistes en attente de vérification":
            " · Vérification terminée pour ce passage";
        return events+" soirées analysées · "+withoutLineup+" sans artistes renseignés · "+
            total+" artistes distincts · "+linked+" profils Spotify · "+
            photos+" portraits Spotify · "+missingPortraits+
            " portraits à récupérer · "+pending+" comptes sans correspondance"+suffix;
    }

    @Override @NonNull public Result doWork(){
        Context ctx=getApplicationContext();
        SharedPreferences prefs=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        try {
            // A fresh app version must retry artists wrongly cached as missing
            // by the previous Spotify oEmbed-only implementation.
            if(!prefs.getBoolean("initialPhotoRescan",false)){
                SpotifyArtistFinder.clearNegativeCache(ctx);
                prefs.edit().putBoolean("initialPhotoRescan",true).apply();
            }
            // Capture all events. Even older imported festivals may have only an
            // "artists" string and no "artistProfiles" objects.
            ensureProfiles(ctx);
            LinkedHashMap<String,Artist> artists=collect(new AppStore(ctx).events());
            // Old imported line-ups gain verified SoundCloud/track links without
            // having to sync every Shotgun ticket again.
            for(Map.Entry<String,Artist> row:artists.entrySet()){
                if(isStopped())return Result.retry();
                String account=SoundCloudArtistFinder.knownProfile(row.getValue().name);
                if(account.isEmpty())continue;
                String track=account.equals("https://soundcloud.com/skonebzh")?
                    SoundCloudArtistFinder.firstOfficialTrack(account):"";
                applyMusic(ctx,row.getKey(),account,track);
            }
            // First propagate every already-confirmed direct link across ALL events.
            // This is fast and does not consume a slow network lookup slot.
            for(Map.Entry<String,Artist> row:artists.entrySet()){
                Artist artist=row.getValue();
                if(artist.ambiguous)continue;
                String known=SpotifyArtistFinder.savedProfile(ctx,artist.name);
                if(known.isEmpty())known=SpotifyArtistFinder.trustedProfile(artist.name);
                if(!SpotifyArtistVerifier.isArtistUrl(known))continue;
                if(!artist.spotifyLink.isEmpty()&&!artist.spotifyLink.equals(known))
                    continue; // A namesake in the journal might use another ID.
                apply(ctx,row.getKey(),known,"");
                artist.spotifyLink=known;
            }
            updateStatus(ctx,"running");
            int attempted=0;
            boolean pending=false;
            long now=System.currentTimeMillis();
            // Prioritise photos for profiles already found: users should not
            // see a verified green Spotify badge with a placeholder initial.
            for(int pass=0;pass<2;pass++){
                for(Map.Entry<String,Artist> row:artists.entrySet()){
                    if(isStopped())return Result.retry();
                    String key=row.getKey();
                    Artist artist=row.getValue();
                    if(artist.ambiguous)continue;
                    boolean linked=SpotifyArtistVerifier.isArtistUrl(artist.spotifyLink);
                    if(pass==0&&!linked)continue; // portraits first
                    if(pass==1&&linked)continue;  // new profiles second
                    if(linked&&!artist.spotifyPhoto.isEmpty()){
                        apply(ctx,key,artist.spotifyLink,artist.spotifyPhoto);
                        continue;
                    }
                    String retryKey=(linked?"portrait_":"attempt_")+key;
                    if(now-prefs.getLong(retryKey,0)<FAILED_RETRY)continue;
                    if(attempted>=MAX_LOOKUPS){pending=true;continue;}
                    attempted++;
                    try{
                        String link=linked?artist.spotifyLink:
                            SpotifyArtistFinder.resolve(ctx,artist.name,
                                artist.shotgunPhoto,"");
                        if(SpotifyArtistVerifier.isArtistUrl(link)){
                            // A matching name is verified inside verifiedThumbnail,
                            // using Spotify's real artist and embed pages. Don't
                            // request oEmbed twice for every artist.
                            String photo=SpotifyArtistVerifier.verifiedThumbnail(
                                link,artist.name);
                            if(!linked&&!SpotifyArtistVerifier.matchesName(
                                link,artist.name)){
                                // Never silently assign a name-sake by coincidence.
                                continue;
                            }
                            apply(ctx,key,link,photo);
                            if(!photo.isEmpty())artist.spotifyPhoto=photo;
                        }
                    }catch(Exception ignored){
                        // Keep previous Spotify link / Shotgun artwork intact.
                    }finally{
                        prefs.edit().putLong(retryKey,System.currentTimeMillis()).apply();
                    }
                    if(attempted%3==0)updateStatus(ctx,"running");
                }
            }
            updateStatus(ctx,pending?"partial":"complete");
            if(pending){
                // Do NOT use Result.retry() here: WorkManager increases its
                // backoff delay (15, 30, 45... minutes) across large line-ups.
                // Chain a fresh network-limited chunk instead, until all unique
                // artists have been attempted across EVERY saved event.
                Constraints online=new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build();
                OneTimeWorkRequest chunk=new OneTimeWorkRequest.Builder(
                    SpotifyArtistBackfillWorker.class)
                    .setConstraints(online)
                    .setInitialDelay(45,TimeUnit.SECONDS)
                    .build();
                WorkManager.getInstance(ctx).enqueueUniqueWork(
                    JOB,ExistingWorkPolicy.APPEND_OR_REPLACE,chunk);
            }
            return Result.success();
        }catch(Exception failure){
            prefs.edit().putString("state","partial").apply();
            return Result.retry();
        }
    }

    private static String key(String input){
        if(input==null)return "";
        return Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT)
            .replace("ø","o"),Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("[^a-z0-9]+"," ").trim();
    }
    private static List<String> names(String raw){
        ArrayList<String> result=new ArrayList<>();
        if(raw==null)return result;
        for(String name:raw.split("[,;\\n]+")){
            String trimmed=name.trim();
            if(trimmed.length()>=2 && trimmed.length()<=120)result.add(trimmed);
        }
        return result;
    }
    private static Artist find(LinkedHashMap<String,Artist> artists,String name){
        String normalized=key(name);
        if(normalized.isEmpty())return null;
        Artist item=artists.get(normalized);
        if(item==null){
            item=new Artist(name.trim());
            artists.put(normalized,item);
        }
        return item;
    }
    private static LinkedHashMap<String,Artist> collect(List<JSONObject> events){
        LinkedHashMap<String,Artist> collected=new LinkedHashMap<>();
        for(JSONObject event:events){
            JSONArray profiles=event.optJSONArray("artistProfiles");
            if(profiles!=null)for(int i=0;i<profiles.length();i++){
                JSONObject p=profiles.optJSONObject(i);
                if(p==null)continue;
                Artist a=find(collected,p.optString("name"));
                if(a==null)continue;
                if(a.shotgunPhoto.isEmpty() &&
                   p.optString("photo").startsWith("https://"))
                    a.shotgunPhoto=p.optString("photo");
                String link=p.optString("spotify");
                if(SpotifyArtistVerifier.isArtistUrl(link)){
                    if(!a.spotifyLink.isEmpty()&&!a.spotifyLink.equals(link))
                        a.ambiguous=true;
                    if(a.spotifyLink.isEmpty())a.spotifyLink=link;
                    if(link.equals(a.spotifyLink) &&
                       p.optString("spotifyPhoto").startsWith("https://"))
                        a.spotifyPhoto=p.optString("spotifyPhoto");
                }
            }
            for(String name:names(event.optString("artists")))find(collected,name);
        }
        return collected;
    }
    private static void ensureProfiles(Context ctx){
        for(JSONObject event:new AppStore(ctx).events()){
            JSONArray profiles=event.optJSONArray("artistProfiles");
            if(profiles==null)profiles=new JSONArray();
            HashSet<String> seen=new HashSet<>();
            for(int i=0;i<profiles.length();i++){
                JSONObject p=profiles.optJSONObject(i);
                if(p!=null)seen.add(key(p.optString("name")));
            }
            boolean modified=false;
            for(String name:names(event.optString("artists"))){
                if(!seen.add(key(name)))continue;
                try{
                    profiles.put(new JSONObject().put("name",name));
                    modified=true;
                }catch(Exception ignored){}
            }
            if(modified){
                try{
                    // Reload immediately before writing to preserve recent
                    // edits and user-entered journal data.
                    AppStore store=new AppStore(ctx);
                    JSONObject current=store.event(event.optLong("id"));
                    if(current==null)continue;
                    JSONArray latest=current.optJSONArray("artistProfiles");
                    if(latest==null)latest=new JSONArray();
                    HashSet<String> present=new HashSet<>();
                    for(int i=0;i<latest.length();i++){
                        JSONObject p=latest.optJSONObject(i);
                        if(p!=null)present.add(key(p.optString("name")));
                    }
                    for(String name:names(current.optString("artists"))){
                        if(present.add(key(name)))
                            latest.put(new JSONObject().put("name",name));
                    }
                    current.put("artistProfiles",latest);
                    store.saveEvent(current);
                }catch(Exception ignored){}
            }
        }
    }
    private static void applyMusic(Context context,String target,
                                   String soundcloud,String preview){
        synchronized(ShotgunSyncService.class){
            for(JSONObject snapshot:new AppStore(context).events()){
                try{
                    AppStore store=new AppStore(context);
                    JSONObject event=store.event(snapshot.optLong("id"));
                    if(event==null)continue;
                    JSONArray profiles=event.optJSONArray("artistProfiles");
                    if(profiles==null)continue;
                    boolean changed=false;
                    for(int i=0;i<profiles.length();i++){
                        JSONObject item=profiles.optJSONObject(i);
                        if(item==null||!target.equals(key(item.optString("name"))))continue;
                        if(item.optString("soundcloud").isEmpty()&&
                            SoundCloudArtistFinder.validProfile(soundcloud)){
                            item.put("soundcloud",soundcloud);changed=true;
                        }
                        if(item.optString("previewUrl").isEmpty()&&
                            preview.startsWith("https://soundcloud.com/")){
                            item.put("previewUrl",preview);changed=true;
                        }
                    }
                    if(changed){
                        event.put("artistProfiles",profiles);
                        store.saveEvent(event);
                    }
                }catch(Exception ignored){}
            }
        }
    }
    private static void apply(Context context,String target,String link,String photo){
        for(JSONObject snapshot:new AppStore(context).events()){
            try{
                AppStore store=new AppStore(context);
                JSONObject event=store.event(snapshot.optLong("id"));
                if(event==null)continue;
                JSONArray profiles=event.optJSONArray("artistProfiles");
                if(profiles==null)profiles=new JSONArray();
                boolean changed=false;
                for(int i=0;i<profiles.length();i++){
                    JSONObject profile=profiles.optJSONObject(i);
                    if(profile==null||!target.equals(key(profile.optString("name"))))
                        continue;
                    String previous=profile.optString("spotify");
                    if(!previous.isEmpty()&&!previous.equals(link))continue;
                    if(!link.equals(previous)){
                        profile.put("spotify",link);changed=true;
                    }
                    if(!photo.isEmpty()&&!photo.equals(profile.optString("spotifyPhoto"))){
                        profile.put("spotifyPhoto",photo);changed=true;
                    }
                }
                if(changed){
                    event.put("artistProfiles",profiles);
                    store.saveEvent(event);
                }
            }catch(Exception ignored){}
        }
    }
    private static void updateStatus(Context ctx,String state){
        LinkedHashMap<String,Artist> artists=collect(new AppStore(ctx).events());
        Counts c=new Counts();
        c.total=artists.size();
        for(Artist a:artists.values()){
            if(SpotifyArtistVerifier.isArtistUrl(a.spotifyLink))c.linked++;
            if(!a.spotifyPhoto.isEmpty())c.photos++;
        }
        for(JSONObject event:new AppStore(ctx).events()){
            c.events++;
            boolean any=event.optJSONArray("artistProfiles")!=null &&
                event.optJSONArray("artistProfiles").length()>0;
            if(!any && names(event.optString("artists")).isEmpty())
                c.withoutLineup++;
        }
        ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putInt("total",c.total).putInt("linked",c.linked)
            .putInt("photos",c.photos).putInt("events",c.events)
            .putInt("withoutLineup",c.withoutLineup)
            .putString("state",state).putLong("lastRun",System.currentTimeMillis())
            .apply();
    }
}
