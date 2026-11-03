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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.text.Normalizer;
import java.util.concurrent.TimeUnit;

/**
 * Enrichit TOUS les événements enregistrés, anciens et nouveaux.
 * Ne lit jamais la session Spotify/Shotgun : seules les pages publiques sont consultées.
 * Les portraits provenant de Spotify restent séparés des images Shotgun.
 */
public final class SpotifyArtistBackfillWorker extends Worker {
    private static final String JOB="afterlog-spotify-artists-backfill";
    private static final String PERIODIC="afterlog-spotify-artists-daily";
    private static final String PREFS="afterlog_spotify_backfill";
    private static final long RETRY_AFTER=12L*60L*60L*1000L;
    private static final int MAX_LOOKUPS=12;

    private static final class Artist {
        final String name;
        String shotgunPhoto="", explicitSpotify="";
        String knownSpotify="", knownPhoto="";
        Artist(String name){this.name=name;}
    }

    public SpotifyArtistBackfillWorker(@NonNull Context context,
                                       @NonNull WorkerParameters parameters) {
        super(context,parameters);
    }

    /** Travail ponctuel à l'ouverture + rappel quotidien pour les artistes non trouvés. */
    static void schedule(Context ctx) {
        SharedPreferences prefs=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        Constraints network=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest recurring=new PeriodicWorkRequest.Builder(
            SpotifyArtistBackfillWorker.class,24,TimeUnit.HOURS)
            .setConstraints(network).build();
        WorkManager manager=WorkManager.getInstance(ctx);
        manager.enqueueUniquePeriodicWork(PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,recurring);
        if(now-prefs.getLong("lastQueued",0)<2L*60L*1000L)return;
        prefs.edit().putLong("lastQueued",now).apply();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(
            SpotifyArtistBackfillWorker.class)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.LINEAR,15,TimeUnit.MINUTES)
            .build();
        manager.enqueueUniqueWork(JOB,ExistingWorkPolicy.KEEP,request);
    }

    @Override @NonNull public Result doWork() {
        Context context=getApplicationContext();
        SharedPreferences prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        LinkedHashMap<String,Artist> artists=new LinkedHashMap<>();
        AppStore initial=new AppStore(context);
        for(JSONObject event:initial.events()){
            JSONArray profiles=event.optJSONArray("artistProfiles");
            if(profiles!=null)for(int i=0;i<profiles.length();i++){
                JSONObject p=profiles.optJSONObject(i);
                if(p==null)continue;
                String name=p.optString("name").trim();
                Artist a=artistFor(artists,name);
                if(a==null)continue;
                if(a.shotgunPhoto.isEmpty())a.shotgunPhoto=p.optString("photo");
                if(a.explicitSpotify.isEmpty() &&
                    SpotifyArtistVerifier.isArtistUrl(p.optString("spotify")))
                    a.explicitSpotify=p.optString("spotify");
                if(SpotifyArtistVerifier.isArtistUrl(p.optString("spotify")) &&
                   p.optString("spotifyPhoto").startsWith("https://")){
                    a.knownSpotify=p.optString("spotify");
                    a.knownPhoto=p.optString("spotifyPhoto");
                }
            }
            for(String n:event.optString("artists").split(","))artistFor(artists,n.trim());
        }

        int checked=0,found=0,attempted=0;
        boolean remaining=false;
        long now=System.currentTimeMillis();
        for(Artist artist:artists.values()){
            if(isStopped())return Result.retry();
            String key=key(artist.name);
            // Un artiste déjà confirmé sur une autre soirée s'applique partout.
            if(!artist.knownSpotify.isEmpty() && !artist.knownPhoto.isEmpty()){
                apply(context,key,artist.knownSpotify,artist.knownPhoto);
                found++;checked++;continue;
            }
            if(!needsEnrichment(context,key)){
                if(!artist.explicitSpotify.isEmpty())found++;
                checked++;continue;
            }
            if(now-prefs.getLong("attempt_"+key,0)<RETRY_AFTER){
                checked++;continue;
            }
            if(attempted>=MAX_LOOKUPS){remaining=true;continue;}
            attempted++;
            String link="";
            String photo="";
            try {
                link=SpotifyArtistFinder.resolve(context,artist.name,
                    artist.shotgunPhoto,artist.explicitSpotify);
                if(SpotifyArtistVerifier.isArtistUrl(link) &&
                   SpotifyArtistVerifier.matchesName(link,artist.name)){
                    photo=SpotifyArtistVerifier.verifiedThumbnail(link,artist.name);
                    if(!photo.isEmpty()){
                        apply(context,key,link,photo);
                        found++;
                    }else {
                        // Le lien a été reconnu, mais une vignette manquante ne
                        // doit jamais effacer un portrait Shotgun existant.
                        apply(context,key,link,"");
                        found++;
                    }
                }
            }catch(Exception ignored){}
            prefs.edit().putLong("attempt_"+key,System.currentTimeMillis()).apply();
            checked++;
        }
        prefs.edit().putInt("artistsTotal",artists.size())
            .putInt("artistsChecked",checked)
            .putInt("artistsFound",found)
            .putLong("lastRun",System.currentTimeMillis()).apply();
        return remaining?Result.retry():Result.success();
    }

    private static Artist artistFor(LinkedHashMap<String,Artist> map,String name){
        if(name==null||name.trim().isEmpty())return null;
        String key=key(name);
        if(key.isEmpty())return null;
        Artist existing=map.get(key);
        if(existing!=null)return existing;
        Artist a=new Artist(name.trim());map.put(key,a);return a;
    }

    private static String key(String name){
        return Normalizer.normalize(name.toLowerCase(Locale.ROOT).replace("ø","o"),
            Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .replaceAll("[^a-z0-9]+"," ").trim();
    }

    private static boolean needsEnrichment(Context context,String target){
        for(JSONObject event:new AppStore(context).events()){
            boolean named=false;
            JSONArray arr=event.optJSONArray("artistProfiles");
            if(arr!=null)for(int i=0;i<arr.length();i++){
                JSONObject p=arr.optJSONObject(i);
                if(p==null||!key(p.optString("name")).equals(target))continue;
                named=true;
                if(p.optString("spotifyPhoto").isEmpty())return true;
            }
            if(!named)for(String name:event.optString("artists").split(",")){
                if(key(name.trim()).equals(target))return true;
            }
        }
        return false;
    }

    private static void apply(Context context,String target,String spotify,String photo){
        // Relire la version à jour : ne pas écraser les modifications personnelles
        // ajoutées pendant la recherche réseau.
        for(JSONObject event:new AppStore(context).events()){
            try{
                JSONArray profiles=event.optJSONArray("artistProfiles");
                if(profiles==null)profiles=new JSONArray();
                boolean changed=false,exists=false;
                for(int i=0;i<profiles.length();i++){
                    JSONObject p=profiles.optJSONObject(i);
                    if(p==null||!key(p.optString("name")).equals(target))continue;
                    exists=true;
                    String oldLink=p.optString("spotify");
                    // Deux profils officiels différents : ne pas confondre les homonymes.
                    if(!oldLink.isEmpty()&&!oldLink.equals(spotify))continue;
                    if(!oldLink.equals(spotify)){
                        p.put("spotify",spotify);changed=true;
                    }
                    if(!photo.isEmpty()&&!photo.equals(p.optString("spotifyPhoto"))){
                        p.put("spotifyPhoto",photo);changed=true;
                    }
                }
                if(!exists){
                    for(String name:event.optString("artists").split(",")){
                        String trimmed=name.trim();
                        if(!key(trimmed).equals(target))continue;
                        JSONObject p=new JSONObject();
                        p.put("name",trimmed);
                        p.put("spotify",spotify);
                        if(!photo.isEmpty())p.put("spotifyPhoto",photo);
                        profiles.put(p);changed=true;break;
                    }
                }
                if(changed){
                    event.put("artistProfiles",profiles);
                    new AppStore(context).saveEvent(event);
                }
            }catch(Exception ignored){}
        }
    }
}
