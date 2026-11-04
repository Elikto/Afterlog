package fr.afterlog.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Contrôle périodique limité : utilise la session WebView, ne fait aucune requête directe
 * avec les cookies du compte, n'essaie jamais de contourner un blocage HTTP 429.
 * Les restrictions Android peuvent empêcher ou retarder cette tâche. */
public final class ShotgunScheduledWorker extends Worker {
    private static final String HOME="https://shotgun.live/fr";
    private static final String PREFS="afterlog_auto_sync";
    private static final int MAX_EVENTS=ShotgunTicketDiscovery.MAX_EVENTS;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WebView web;
    private volatile boolean blocked=false,failed=false;
    private volatile CountDownLatch loadLatch;
    private final AppStore store;
    private int countAdded=0;

    public ShotgunScheduledWorker(@NonNull Context context,@NonNull WorkerParameters parameters) {
        super(context,parameters);
        store=new AppStore(context);
    }
    @Override @NonNull public Result doWork() {
        Context ctx=getApplicationContext();
        if(!AutoSyncScheduler.enabled(ctx))return Result.success();
        SharedPreferences prefs=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if(now<prefs.getLong("blockedUntil",0))return Result.success();
        JSONObject current=SyncState.read(ctx);
        if(SyncState.RUNNING.equals(current.optString("state")) &&
            now-current.optLong("updatedAt")<10*60*1000L)return Result.success();
        String start=prefs.getString("ticketsUrl",HOME);
        if(!safe(start))start=HOME;
        try {
            if(!createWebView())return Result.retry();
            // Reuse the logged-in Shotgun WebView, read all three ticket categories.
            List<JSONObject> found=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            for(String ticketPage:ShotgunTicketDiscovery.TICKET_PAGES){
                if(blocked||isStopped())break;
                if(!load(ticketPage))continue;
                for(int attempt=0;attempt<5&&!blocked&&!isStopped();attempt++){
                    String raw=evaluate(ShotgunTicketDiscovery.SCAN_JS);
                    if(raw==null)break;
                    JSONObject response=new JSONObject(ShotgunBrowserActivity.decoded(raw));
                    String action=response.optString("action");
                    if("login".equals(action)){
                        prefs.edit().putString("lastCheck",
                            "Session expirée — reconnecte Shotgun dans NØCTARIA").apply();
                        return Result.success();
                    }
                    if("scroll".equals(action)||"wait".equals(action)){
                        Thread.sleep(1500);
                        continue;
                    }
                    if("ready".equals(action)){
                        addLinks(response.optJSONArray("events"),found,seen);
                        break;
                    }
                }
            }
            if(blocked)return finish(ctx,"Shotgun HTTP 429 : vérifications suspendues.");
            if(found.isEmpty())return finish(ctx,"Aucun événement accessible lors du contrôle.");
            List<JSONObject> missing=new ArrayList<>();
            for(JSONObject item:found) {
                JSONObject existing=match(item);
                if(TicketSelectionStore.isExcluded(ctx,item.optString("url")))continue;
                boolean lineupIncomplete=existing!=null && needsArtistRefresh(existing);
                long previous=existing==null?0:prefs.getLong(
                    "artistRefresh_"+existing.optLong("id"),0);
                if(existing==null || needsDetails(existing) ||
                   (lineupIncomplete && now-previous>24L*60*60*1000))
                    missing.add(item);
            }
            // Cap par contrôle pour ménager Shotgun et conserver une exécution brève.
            int total=Math.min(12,missing.size());
            if(total>0)SyncState.save(ctx,SyncState.RUNNING,
                "Vérification des nouvelles soirées",0,total,-1,"Import automatique périodique");
            for(int i=0;i<total && !blocked && !isStopped();i++) {
                JSONObject item=missing.get(i);
                String url=item.optString("url");
                if(!safe(url)||!load(url))continue;
                for(int depth=0;depth<3 && !blocked;depth++) {
                    if(eventUrl(webUrl()))break;
                    JSONObject next=new JSONObject(ShotgunBrowserActivity.decoded(
                        evaluate(ShotgunBatchImporter.FOLLOW_EVENT_JS)));
                    String link=next.optString("url");
                    if("link".equals(next.optString("kind")) && eventUrl(link)) {
                        if(!load(link))break;
                    }else if("clicked".equals(next.optString("kind"))){
                        Thread.sleep(1500);
                    }else break;
                }
                if(blocked||!eventUrl(webUrl()))continue;
                String data=evaluate(ShotgunBrowserActivity.CURRENT_EVENT_JS);
                if(data==null)continue;
                JSONObject extracted=new JSONObject(ShotgunBrowserActivity.decoded(data));
                if(extracted.optString("title").isEmpty())continue;
                boolean details=false;
                for(String key:new String[]{"poster","description","date","start","venue","artists"}) {
                    if(!extracted.optString(key).trim().isEmpty()){details=true;break;}
                }
                if(!details)continue;
                extracted.put("url",normalize(webUrl()));
                extracted.put("origin","Shotgun · Synchronisation");
                if(extracted.optString("date").isEmpty())
                    extracted.put("date",item.optString("date"));
                if(extracted.optString("poster").isEmpty())
                    extracted.put("poster",item.optString("poster"));
                persist(extracted);
                JSONObject saved=match(extracted);
                if(saved!=null)
                    prefs.edit().putLong("artistRefresh_"+saved.optLong("id"),
                        System.currentTimeMillis()).apply();
                countAdded++;
                SyncState.save(ctx,SyncState.RUNNING,"Vérification périodique",
                    i+1,total,-1,"Nouvelles soirées et mises à jour");
                if(i+1<total)Thread.sleep(1300);
            }
            return finish(ctx,blocked?"Shotgun HTTP 429 : vérifications suspendues.":
                countAdded+" fiche(s) nouvelle(s) ou complétée(s).");
        }catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.success();
        }catch(Exception e) {
            return finish(ctx,"Vérification non terminée : session ou site indisponible.");
        }finally {
            main.post(()->{
                if(web!=null){web.stopLoading();web.destroy();web=null;}
            });
        }
    }
    private Result finish(Context ctx,String message) {
        long when=System.currentTimeMillis();
        SharedPreferences.Editor editor=ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putLong("lastCheckAt",when).putString("lastCheck",message);
        if(blocked)editor.putLong("blockedUntil",when+6*60*60*1000L);
        editor.apply();
        String current=SyncState.read(ctx).optString("state");
        if(SyncState.RUNNING.equals(current))
            SyncState.save(ctx,blocked?SyncState.BLOCKED:SyncState.DONE,
                message,countAdded,countAdded,0,message);
        return Result.success();
    }
    private boolean createWebView() throws InterruptedException {
        CountDownLatch ready=new CountDownLatch(1);
        main.post(()->{
            try {
                web=new WebView(getApplicationContext());
                WebSettings settings=web.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setAllowFileAccess(false);
                settings.setAllowContentAccess(false);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                web.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req) {
                        return !safe(req.getUrl().toString());
                    }
                    @Override public void onPageFinished(WebView v,String url){
                        CountDownLatch latch=loadLatch;
                        if(latch!=null)main.postDelayed(latch::countDown,1100);
                    }
                    @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,
                            WebResourceResponse resp){
                        if(r.isForMainFrame()&&resp.getStatusCode()==429){
                            blocked=true;
                            CountDownLatch latch=loadLatch;
                            if(latch!=null)latch.countDown();
                        }
                    }
                    @Override public void onReceivedError(WebView v,WebResourceRequest r,
                            android.webkit.WebResourceError error){
                        if(r.isForMainFrame()){
                            failed=true;
                            CountDownLatch latch=loadLatch;
                            if(latch!=null)latch.countDown();
                        }
                    }
                });
            }catch(Exception e){failed=true;}
            ready.countDown();
        });
        return ready.await(10,TimeUnit.SECONDS)&&web!=null&&!failed;
    }
    private boolean load(String url) throws InterruptedException {
        if(web==null||blocked||!safe(url))return false;
        failed=false;
        CountDownLatch latch=new CountDownLatch(1);
        loadLatch=latch;
        main.post(()->web.loadUrl(url));
        boolean done=latch.await(22,TimeUnit.SECONDS);
        loadLatch=null;
        return done&&!failed&&!blocked;
    }
    private String evaluate(String js) throws InterruptedException {
        if(web==null||blocked)return null;
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<String> result=new AtomicReference<>();
        main.post(()->web.evaluateJavascript(js,value->{result.set(value);latch.countDown();}));
        if(!latch.await(15,TimeUnit.SECONDS))return null;
        return result.get();
    }
    private String webUrl() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<String> result=new AtomicReference<>();
        main.post(()->{result.set(web==null?"":web.getUrl());latch.countDown();});
        latch.await(3,TimeUnit.SECONDS);
        return result.get();
    }
    private static void addLinks(JSONArray arr,List<JSONObject> out,Set<String> seen) {
        if(arr==null)return;
        for(int i=0;i<arr.length()&&out.size()<MAX_EVENTS;i++) {
            JSONObject item=arr.optJSONObject(i);
            if(item==null)continue;
            String url=item.optString("url");
            String normalized=normalize(url);
            if(normalized.isEmpty()||!seen.add(normalized))continue;
            out.add(item);
        }
    }
    private JSONObject match(JSONObject incoming) {
        String url=normalize(incoming.optString("url"));
        for(JSONObject e:store.events()) {
            if(!url.isEmpty()&&
               (url.equals(normalize(e.optString("url"))) ||
                url.equals(normalize(e.optString("ticketUrl")))))
                return e;
        }
        return null;
    }
    private static boolean needsDetails(JSONObject entry) {
        return entry.optString("poster").isEmpty() || entry.optString("date").isEmpty()||
            entry.optString("description").isEmpty() || entry.optString("venue").isEmpty();
    }
    private static boolean needsArtistRefresh(JSONObject event){
        JSONArray profiles=event.optJSONArray("artistProfiles");
        if(event.optString("artists").trim().isEmpty() &&
           (profiles==null||profiles.length()==0))return true;
        if(profiles==null||profiles.length()==0)return true;
        for(int i=0;i<profiles.length();i++){
            JSONObject artist=profiles.optJSONObject(i);
            if(artist==null)continue;
            if(artist.optString("photo").isEmpty() &&
               artist.optString("spotifyPhoto").isEmpty())return true;
        }
        return false;
    }

    private void persist(JSONObject incoming) throws Exception {
        JSONObject old=match(incoming);
        if(old==null){store.saveEvent(incoming);SpotifyArtistBackfillWorker.schedule(getApplicationContext());return;}
        for(String key:new String[]{"poster","date","start","end","endDate","venue",
                "city","address","description","artists","organizer","genres","url"}) {
            String value=incoming.optString(key);
            if(!value.isEmpty() && old.optString(key).isEmpty())old.put(key,value);
        }
        ArtistProfileMerger.merge(old,incoming);
        store.saveEvent(old);
        SpotifyArtistBackfillWorker.schedule(getApplicationContext());
    }
    private static boolean safe(String url) {
        try {
            Uri u=Uri.parse(url);
            String host=u.getHost();
            return "https".equalsIgnoreCase(u.getScheme())&&host!=null&&
                ("shotgun.live".equalsIgnoreCase(host)||host.toLowerCase(Locale.ROOT).endsWith(".shotgun.live"));
        }catch(Exception e){return false;}
    }
    private static boolean eventUrl(String url) {
        if(!safe(url))return false;
        String path=Uri.parse(url).getPath();
        return path!=null&&path.matches("^/(?:[a-z]{2}/)?(?:events|festivals)/[^/]+/?$");
    }
    private static String normalize(String url) {
        try {return safe(url)?Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString():"";}
        catch(Exception e){return "";}
    }
}
