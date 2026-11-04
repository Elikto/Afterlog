package fr.afterlog.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.Normalizer;

/**
 * Synchronisation en service de premier plan avec notification Android.
 * Fonctionne sans maintenir l'écran Shotgun à l'avant, dans la mesure où
 * Android conserve le moteur WebView actif. Non garantie sous contraintes système,
 * compte Shotgun expiré, changement de DOM ou protection anti-automatisation.
 * Une seule navigation à la fois, arrêt immédiat sur HTTP 429.
 */
public final class ShotgunSyncService extends Service {
    private static final String START="fr.afterlog.app.DISCOVER_PAST";
    private static final String IMPORT_SELECTED="fr.afterlog.app.IMPORT_SELECTED";
    private static final String DISCOVER_ALL="fr.afterlog.app.DISCOVER_ALL";
    private static final String DISCOVER_CHOOSE="fr.afterlog.app.DISCOVER_CHOOSE";
    private static final String RETRY_FAILED="fr.afterlog.app.RETRY_FAILED";
    private static final String EXTRA_FAILED_URL="failed_url";
    private static final String STOP="fr.afterlog.app.SYNC_STOP";
    private static final String CHANNEL="afterlog_sync";
    private static final int NOTIF_ID=4104;
    public static final String EXTRA_URL="start_url";
    private static final String HOME="https://shotgun.live/fr";
    private static final int MAX_EVENTS=ShotgunTicketDiscovery.MAX_EVENTS;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WebView browser;
    private AppStore store;
    private NotificationManager notifications;
    private boolean active,seenPast,clickedPast,triedMenu,gotTickets,readingPage,rateLimited;
    private boolean backgroundDiscovery,chooseBeforeImport;
    private int ticketPageIndex,discoveryRetries,successfulPages;
    private int navigationId,discoveryAttempts,processed,errors,nextIndex,detailAttempts;
    private long startedAt;
    private String phase="",lastError="";
    private final List<JSONObject> queue=new ArrayList<>();
    private final Set<String> seenUrls=new HashSet<>();
    // Spotify matching runs alongside event retrieval, within the same sync.
    private final ExecutorService artistPool=Executors.newFixedThreadPool(2);
    private int artistsPending,artistsFinished,artistLinksVerified;
    private int artistEventsComplete,artistMissingLinks,artistMissingPhotos,missingLineups;
    private final List<JSONObject> artistSnapshots=new ArrayList<>();
    // Final music/artist enrichment is ONE shared stage, after every event.
    private boolean artistStageStarted,metadataDone;
    private String finalSummary="";

    public static void start(Context ctx,String url) {
        Intent i=new Intent(ctx,ShotgunSyncService.class);
        i.setAction(START);
        i.putExtra(EXTRA_URL,url);
        if(Build.VERSION.SDK_INT>=26)ctx.startForegroundService(i);
        else ctx.startService(i);
    }
    /** Launch a complete silent scan using the authenticated Android WebView session. */
    public static void discoverAccount(Context ctx,boolean chooseEvents) {
        Intent i=new Intent(ctx,ShotgunSyncService.class);
        i.setAction(chooseEvents?DISCOVER_CHOOSE:DISCOVER_ALL);
        if(Build.VERSION.SDK_INT>=26)ctx.startForegroundService(i);
        else ctx.startService(i);
    }
    public static void importSelected(Context ctx) throws Exception {
        JSONArray selected=TicketSelectionStore.chosen(ctx);
        if(selected.length()==0)throw new IllegalStateException("Aucune soirée cochée");
        TicketSelectionStore.commitExclusions(ctx);
        Intent i=new Intent(ctx,ShotgunSyncService.class);
        i.setAction(IMPORT_SELECTED);
        if(Build.VERSION.SDK_INT>=26)ctx.startForegroundService(i);
        else ctx.startService(i);
    }
    public static void retryFailed(Context ctx,String url) throws Exception {
        if(url==null||!url.startsWith("https://"))throw new IllegalArgumentException("Lien absent");
        Intent intent=new Intent(ctx,ShotgunSyncService.class);
        intent.setAction(RETRY_FAILED);
        intent.putExtra(EXTRA_FAILED_URL,url);
        if(Build.VERSION.SDK_INT>=26)ctx.startForegroundService(intent);
        else ctx.startService(intent);
    }
    public static void cancel(Context ctx) {
        Intent i=new Intent(ctx,ShotgunSyncService.class);
        i.setAction(STOP);
        ctx.startService(i);
    }
    @Override public void onCreate() {
        super.onCreate();
        store=new AppStore(this);
        notifications=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Synchronisation Shotgun",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        String action=intent==null?"":intent.getAction();
        if(STOP.equals(action)) {
            finish(SyncState.CANCELED,"Synchronisation interrompue");
            return START_NOT_STICKY;
        }
        if(active)return START_NOT_STICKY;
        backgroundDiscovery=DISCOVER_ALL.equals(action)||DISCOVER_CHOOSE.equals(action);
        chooseBeforeImport=DISCOVER_CHOOSE.equals(action);
        ticketPageIndex=0;discoveryRetries=0;successfulPages=0;
        boolean retryFailed=RETRY_FAILED.equals(action);
        boolean selectedImport=IMPORT_SELECTED.equals(action)||retryFailed;
        active=true;rateLimited=false;seenPast=false;clickedPast=false;triedMenu=false;
        gotTickets=false;readingPage=false;discoveryAttempts=0;processed=0;
        errors=0;nextIndex=0;navigationId=0;detailAttempts=0;
        artistSnapshots.clear();artistStageStarted=false;metadataDone=false;
        startedAt=System.currentTimeMillis();
        if(IMPORT_SELECTED.equals(action))SyncFailureStore.clear(this);
        String url=intent==null?HOME:intent.getStringExtra(EXTRA_URL);
        if(!trusted(url))url=HOME;
        if(selectedImport) {
            JSONArray picked=retryFailed?SyncFailureStore.list(this):TicketSelectionStore.chosen(this);
            for(int n=0;n<picked.length() && queue.size()<MAX_EVENTS;n++) {
                JSONObject item=picked.optJSONObject(n);
                if(item==null)continue;
                if(retryFailed&&!item.optString("url").equals(intent.getStringExtra(EXTRA_FAILED_URL)))continue;
                if(retryFailed && TicketSelectionStore.isExcluded(this,item.optString("url")))continue;
                if(trusted(item.optString("url")))queue.add(item);
            }
            if(queue.isEmpty()) {
                active=false;stopSelf();return START_NOT_STICKY;
            }
            gotTickets=true;
        }
        status(selectedImport?"Synchronisation des soirées choisies…":
            "Recherche des billets Shotgun en arrière-plan…",selectedImport?
            "Artistes et informations des soirées inclus":"Tous les billets, à venir et passés");
        if(Build.VERSION.SDK_INT>=29)
            startForeground(NOTIF_ID,notification("Synchronisation des billets Shotgun…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIF_ID,notification("Synchronisation des billets Shotgun…"));
        try {
            browser=new WebView(getApplicationContext());
            WebSettings config=browser.getSettings();
            config.setJavaScriptEnabled(true);
            config.setDomStorageEnabled(true);
            config.setAllowFileAccess(false);
            config.setAllowContentAccess(false);
            config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            config.setJavaScriptCanOpenWindowsAutomatically(false);
            CookieManager.getInstance().setAcceptCookie(true);
            browser.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
                    return !trusted(req.getUrl().toString());
                }
                @Override public void onPageFinished(WebView v,String address){
                    if(!active)return;
                    int nav=navigationId;
                    handler.postDelayed(()->{
                        if(active && nav==navigationId) inspectPage();
                    },1600);
                }
                @Override public void onReceivedHttpError(WebView v,WebResourceRequest req,
                        WebResourceResponse response){
                    if(active&&req.isForMainFrame()&&response.getStatusCode()==429){
                        rateLimited=true;finish(SyncState.BLOCKED,"Shotgun limite la synchronisation (HTTP 429).");
                    }
                }
                @Override public void onReceivedError(WebView v,WebResourceRequest req,
                        WebResourceError error){
                    if(active&&req.isForMainFrame()){
                        if(gotTickets){failCurrent("Page Shotgun inaccessible");}
                        else finish(SyncState.ERROR,"Impossible d'ouvrir Shotgun.");
                    }
                }
            });
            if(selectedImport)next();
            else if(backgroundDiscovery)open(ShotgunTicketDiscovery.TICKET_PAGES[0]);
            else open(url);
        }catch(Exception e) {
            finish(SyncState.ERROR,"Navigateur Android indisponible : "+e.getMessage());
        }
        return START_NOT_STICKY;
    }
    private Notification notification(String message) {
        Intent open=new Intent(this,MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,42,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent stop=new Intent(this,ShotgunSyncService.class).setAction(STOP);
        PendingIntent cancel=PendingIntent.getService(this,43,stop,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?
            new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return builder.setContentTitle("NØCTARIA · Shotgun")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Arrêter",cancel)
            .build();
    }
    private void status(String text,String detail) {
        phase=text;
        // N evenings = N event steps + ONE final common artist/music step.
        int total=gotTickets?queue.size()+1:0;
        int done=gotTickets?Math.min(queue.size(),processed+errors):0;
        long eta=estimateTotalEtaSeconds();
        SyncState.save(this,SyncState.RUNNING,text,done,total,eta,detail);
        if(notifications!=null && Build.VERSION.SDK_INT>=26 && active)
            notifications.notify(NOTIF_ID,notification(text));
    }
    private void open(String url) {
        if(!active)return;
        if(!trusted(url)){errorOrAdvance("Lien Shotgun non autorisé");return;}
        int id=++navigationId;
        browser.loadUrl(url);
        handler.postDelayed(()->{
            if(active && id==navigationId && !readingPage) {
                if(!gotTickets) inspectPage();
                else {failCurrent("Le chargement de la page a expiré");}
            }
        },27000);
    }
    private void inspectPage() {
        if(!active||readingPage||browser==null)return;
        readingPage=true;
        final int nav=navigationId;
        if(backgroundDiscovery){
            inspectTicketCategory(nav);
            return;
        }
        if(gotTickets) {
            browser.evaluateJavascript(ShotgunBrowserActivity.CURRENT_EVENT_JS,raw->{
                readingPage=false;
                if(!active||nav!=navigationId)return;
                try {
                    String url=browser.getUrl();
                    if(!eventUrl(url)) {
                        // Ticket page : suivre seulement l'action Voir l'événement.
                        if(++detailAttempts>4){failCurrent("Lien « Voir l'événement » introuvable");return;}
                        browser.evaluateJavascript(ShotgunBatchImporter.FOLLOW_EVENT_JS,follow->{
                            if(!active||nav!=navigationId)return;
                            try {
                                JSONObject outcome=new JSONObject(ShotgunBrowserActivity.decoded(follow));
                                String link=outcome.optString("url");
                                if("link".equals(outcome.optString("kind")) && eventUrl(link)) {
                                    open(link);
                                } else if("clicked".equals(outcome.optString("kind"))) {
                                    handler.postDelayed(()->inspectPage(),1800);
                                } else {failCurrent("Lien vers l'événement introuvable");}
                            }catch(Exception e){failCurrent("Impossible d'extraire les détails");}
                        });
                        return;
                    }
                    JSONObject extracted=new JSONObject(ShotgunBrowserActivity.decoded(raw));
                    if(extracted.optString("title").trim().isEmpty()) {
                        retryDetails(nav);return;
                    }
                    boolean hasData=false;
                    for(String field:new String[]{"poster","description","date","start","venue","address","artists"}) {
                        if(!extracted.optString(field).isEmpty()){hasData=true;break;}
                    }
                    if(!hasData){retryDetails(nav);return;}
                    extracted.put("url",normalizeUrl(url));
                    extracted.put("origin","Shotgun · Synchronisation");
                    JSONObject previous=queue.get(nextIndex);
                    // Permet de retrouver précisément la carte de billet choisie
                    // même si l'URL du billet et celle de l'événement diffèrent.
                    extracted.put("ticketUrl",normalizeUrl(previous.optString("url")));
                    if(extracted.optString("poster").isEmpty())
                        extracted.put("poster",previous.optString("poster"));
                    if(extracted.optString("date").isEmpty())
                        extracted.put("date",previous.optString("date"));
                    saveEventPreservingUserData(extracted);
                    // Artist verification and audio previews start ONLY after
                    // every chosen event has been scanned and saved.
                    artistSnapshots.add(new JSONObject(extracted.toString()));
                    SyncFailureStore.remove(this,queue.get(nextIndex).optString("url"));
                    processed++;advance();
                }catch(Exception e){failCurrent("Extraction indisponible ou en erreur");}
            });
        } else {
            browser.evaluateJavascript(DISCOVER_JS,raw->{
                readingPage=false;
                if(!active||nav!=navigationId)return;
                try {
                    JSONObject state=new JSONObject(ShotgunBrowserActivity.decoded(raw));
                    String action=state.optString("action");
                    if("login".equals(action)) {
                        finish(SyncState.BLOCKED,"Session Shotgun expirée : reconnecte-toi.");
                        return;
                    }
                    if("tickets".equals(action)) {
                        String url=state.optString("url");
                        if(trusted(url)){
                            getSharedPreferences("afterlog_auto_sync",MODE_PRIVATE)
                                .edit().putString("ticketsUrl",url).apply();
                            open(url);return;
                        }
                    }
                    if("past".equals(action)) {
                        if(clickedPast) {
                            // Le DOM n'a pas changé : ne pas cliquer indéfiniment.
                            discoveryAttempts++;
                        } else clickedPast=true;
                        if(discoveryAttempts>4) {
                            finish(SyncState.BLOCKED,"Impossible d'afficher « Passés » sur le site Shotgun.");
                            return;
                        }
                        handler.postDelayed(()->inspectPage(),1900);
                        return;
                    }
                    if("ready".equals(action)) {
                        enqueueDiscovered(state.optJSONArray("events"));
                        if(queue.isEmpty()) {
                            finish(SyncState.BLOCKED,
                                "Aucun lien d’événement accessible parmi les billets en cours, à venir ou passés.");
                            return;
                        }
                        JSONArray discovered=new JSONArray();
                        for(JSONObject ticket:queue) discovered.put(ticket);
                        try {
                            TicketSelectionStore.saveFound(this,discovered);
                            finish(SyncState.SELECTION,
                                queue.size()+" soirée(s) trouvée(s) · sélectionne celles à importer.");
                        }catch(Exception e) {
                            finish(SyncState.ERROR,"Impossible de préparer la liste des billets.");
                        }
                        return;
                    }
                    if("menu".equals(action)||"wait".equals(action)) {
                        if(++discoveryAttempts>7) {
                            finish(SyncState.BLOCKED,
                                "Navigation automatique impossible. Ouvre « Mes billets → Passés » dans Shotgun.");
                        } else handler.postDelayed(()->inspectPage(),1800);
                        return;
                    }
                    finish(SyncState.BLOCKED,"La liste de billets Shotgun est introuvable.");
                }catch(Exception e) {
                    finish(SyncState.ERROR,"Erreur de lecture du compte Shotgun.");
                }
            });
        }
    }
    /** One category per invisible WebView navigation, no user-visible browsing. */
    private void inspectTicketCategory(int nav) {
        if(!active||!backgroundDiscovery)return;
        browser.evaluateJavascript(ShotgunTicketDiscovery.SCAN_JS,raw->{
            readingPage=false;
            if(!active||!backgroundDiscovery||nav!=navigationId)return;
            try {
                JSONObject data=new JSONObject(ShotgunBrowserActivity.decoded(raw));
                String action=data.optString("action");
                if("login".equals(action)){
                    finish(SyncState.BLOCKED,
                        "Connexion Shotgun expirée. Reconnecte ton compte pour synchroniser.");
                    return;
                }
                if("wait".equals(action)||"scroll".equals(action)){
                    if(++discoveryRetries>7){nextTicketCategory(false);return;}
                    handler.postDelayed(()->inspectPage(),1600);
                    return;
                }
                if("ready".equals(action)){
                    enqueueDiscovered(data.optJSONArray("events"));
                    nextTicketCategory(true);
                    return;
                }
                nextTicketCategory(false);
            }catch(Exception error){
                if(++discoveryRetries>5)nextTicketCategory(false);
                else handler.postDelayed(()->inspectPage(),1600);
            }
        });
    }
    private void nextTicketCategory(boolean successful) {
        if(!active||!backgroundDiscovery)return;
        if(successful)successfulPages++;
        ticketPageIndex++;discoveryRetries=0;
        if(ticketPageIndex<ShotgunTicketDiscovery.TICKET_PAGES.length){
            status("Billets Shotgun : "+(ticketPageIndex+1)+"/3",
                queue.size()+" soirée(s) trouvée(s) ; lecture invisible en cours.");
            open(ShotgunTicketDiscovery.TICKET_PAGES[ticketPageIndex]);
            return;
        }
        backgroundDiscovery=false;
        if(successfulPages==0){
            finish(SyncState.BLOCKED,
                "Impossible de lire les listes de billets Shotgun depuis la session.");
            return;
        }
        if(queue.isEmpty()){
            finish(SyncState.BLOCKED,
                "Aucun événement accessible dans les billets Shotgun : liste vide ou chargement incomplet.");
            return;
        }
        JSONArray discovered=new JSONArray();
        for(JSONObject item:queue)discovered.put(item);
        try{
            TicketSelectionStore.saveFound(this,discovered);
            if(chooseBeforeImport){
                finish(SyncState.SELECTION,
                    queue.size()+" soirée(s) trouvée(s). Choisis celles à synchroniser.");
                return;
            }
            TicketSelectionStore.markAll(this,true);
            AutoSyncScheduler.enable(this);
            gotTickets=true;
            nextIndex=0;
            status("Import automatique de toutes les soirées",
                queue.size()+" événement(s) · artistes inclus.");
            next();
        }catch(Exception error){
            finish(SyncState.ERROR,"Impossible de préparer les événements Shotgun.");
        }
    }

    private void enqueueDiscovered(JSONArray list) {
        if(list==null)return;
        for(int i=0;i<list.length() && queue.size()<MAX_EVENTS;i++) {
            JSONObject e=list.optJSONObject(i);
            if(e==null)continue;
            String u=e.optString("url");
            if(!trusted(u))continue;
            if(!seenUrls.add(normalizeUrl(u)))continue;
            queue.add(e);
        }
    }
    private void retryDetails(int nav) {
        if(++detailAttempts>2){failCurrent("Détails incomplets ou indisponibles");return;}
        handler.postDelayed(()->{
            if(active&&nav==navigationId)inspectPage();
        },2100);
    }
    private void next() {
        if(!active)return;
        if(nextIndex>=queue.size()) {
            finish(SyncState.DONE,errors==0?
                "Tous les événements accessibles ont été importés.":
                "Import terminé : "+errors+" événement(s) non récupéré(s).");
            return;
        }
        detailAttempts=0;
        JSONObject item=queue.get(nextIndex);
        status("Événement "+(nextIndex+1)+"/"+queue.size(),
               item.optString("title","Récupération des informations"));
        handler.postDelayed(()->{
            if(active)open(item.optString("url"));
        },1800);
    }
    private void failCurrent(String reason) {
        if(!active)return;
        if(nextIndex>=0&&nextIndex<queue.size())
            SyncFailureStore.add(this,queue.get(nextIndex),reason);
        errors++;advance();
    }
    private void advance() {
        if(!active)return;
        nextIndex++;
        navigationId++;
        if(processed+errors>=queue.size()) {
            startArtistStage();
        } else next();
    }
    private void errorOrAdvance(String message) {
        if(gotTickets){failCurrent(message);}
        else finish(SyncState.BLOCKED,message);
    }
    /**
     * Conservative estimate for ALL remaining work, not merely event-page
     * browsing. Spotify/SoundCloud lookups can be slow or unavailable.
     * No countdown to an artificial zero: recalculate when work advances.
     */
    private long estimateTotalEtaSeconds(){
        if(!gotTickets||queue.isEmpty())return -1;
        int eventsRemaining=Math.max(0,queue.size()-processed-errors);
        long eventAverage=processed+errors>0?
            (System.currentTimeMillis()-startedAt)/1000L/Math.max(1,processed+errors):35;
        // Never assume each event takes less than 30 seconds.
        long eventSecs=eventsRemaining*Math.max(30,Math.min(90,eventAverage*3/2));
        if(!artistStageStarted)return Math.max(240,eventSecs+240+queue.size()*20L);
        if(artistsPending>0)return Math.max(240,artistsPending*50L+90);
        return Math.max(120,eventSecs+120);
    }
    private void startArtistStage(){
        if(!active||artistStageStarted)return;
        artistStageStarted=true;
        metadataDone=true;
        for(JSONObject event:artistSnapshots)enrichArtistsDuringSync(event);
        if(artistsPending==0){
            finish(SyncState.DONE,"Vérification des artistes terminée.");
        }else status("Étape "+(queue.size()+1)+"/"+(queue.size()+1)+
            " · Tous les artistes",artistsPending+
            " line-up(s) : Spotify, SoundCloud, portraits et extraits.");
    }
    private static String artistKey(String input){
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT).replace("ø","o"),
            Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .replaceAll("[^a-z0-9]+"," ").trim();
    }
    private void enrichArtistsDuringSync(JSONObject snapshot){
        JSONArray given=snapshot.optJSONArray("artistProfiles");
        Map<String,JSONObject> discovered=new LinkedHashMap<>();
        if(given!=null)for(int i=0;i<given.length();i++){
            JSONObject p=given.optJSONObject(i);
            if(p==null)continue;
            String name=p.optString("name").trim(),key=artistKey(name);
            if(!key.isEmpty())discovered.put(key,p);
        }
        for(String item:snapshot.optString("artists").split("[,;\\n]+")){
            String name=item.trim(),key=artistKey(name);
            if(name.length()>=2&&!key.isEmpty()&&!discovered.containsKey(key)){
                try{discovered.put(key,new JSONObject().put("name",name));}
                catch(Exception ignored){}
            }
        }
        if(discovered.isEmpty()){
            missingLineups++;
            return; // Missing line-up is reported, not a successful artist lookup.
        }
        artistsPending++;
        artistPool.execute(()->{
            int linked=0,missingLink=0,missingPhoto=0;
            try{
                JSONArray validated=new JSONArray();
                for(JSONObject profile:discovered.values()){
                    if(Thread.currentThread().isInterrupted())break;
                    String name=profile.optString("name").trim();
                    if(name.isEmpty())continue;
                    JSONObject merged=new JSONObject(profile.toString());
                    String existing=merged.optString("spotify");
                    String url=SpotifyArtistFinder.resolve(this,name,
                        merged.optString("photo"),existing);
                    if(SpotifyArtistVerifier.isArtistUrl(url)){
                        merged.put("spotify",url);
                        linked++;
                        if(merged.optString("spotifyPhoto").isEmpty()){
                            String portrait=SpotifyArtistVerifier.verifiedThumbnail(url,name);
                            if(!portrait.isEmpty())merged.put("spotifyPhoto",portrait);
                        }
                    }
                    String soundcloud=merged.optString("soundcloud");
                    if(!SpotifyArtistVerifier.isArtistUrl(url)){
                        // SoundCloud is a checked fallback, never a guessed slug.
                        soundcloud=SoundCloudArtistFinder.find(name,soundcloud);
                        if(SoundCloudArtistFinder.validProfile(soundcloud)){
                            merged.put("soundcloud",soundcloud);
                            if(merged.optString("previewUrl").isEmpty()){
                                String track=SoundCloudArtistFinder.firstOfficialTrack(soundcloud);
                                if(!track.isEmpty())merged.put("previewUrl",track);
                            }
                        }else missingLink++;
                    }
                    if(merged.optString("previewUrl").isEmpty() &&
                       SpotifyArtistVerifier.isArtistUrl(url)){
                        String track=OfficialMusicPreview.firstSpotifyTrack(url);
                        if(!track.isEmpty())merged.put("previewUrl",track);
                    }
                    if(merged.optString("spotifyPhoto").isEmpty() &&
                        !merged.optString("photo").startsWith("https://"))
                        missingPhoto++;
                    validated.put(merged);
                }
                if(validated.length()>0){
                    JSONObject incoming=new JSONObject().put("artistProfiles",validated);
                    synchronized(ShotgunSyncService.class){
                        AppStore local=new AppStore(this);
                        JSONObject current=null;
                        String url=normalizeUrl(snapshot.optString("url"));
                        String ticket=normalizeUrl(snapshot.optString("ticketUrl"));
                        for(JSONObject item:local.events()){
                            if((!url.isEmpty()&&url.equals(normalizeUrl(item.optString("url"))))||
                               (!ticket.isEmpty()&&ticket.equals(normalizeUrl(item.optString("ticketUrl"))))){
                                current=local.event(item.optLong("id"));break;
                            }
                        }
                        if(current!=null){
                            ArtistProfileMerger.merge(current,incoming);
                            local.saveEvent(current);
                        }
                    }
                }
            }catch(Exception ignored){
                // A lookup/merge failure cannot count as a complete artist page.
                missingLink++;
            }
            final int verified=linked,unlinked=missingLink,unpictured=missingPhoto;
            handler.post(()->{
                if(!active)return;
                artistsPending=Math.max(0,artistsPending-1);
                artistsFinished++;
                artistLinksVerified+=verified;
                artistMissingLinks+=unlinked;
                artistMissingPhotos+=unpictured;
                if(unlinked==0&&unpictured==0)artistEventsComplete++;
                if(metadataDone&&artistsPending==0)finish(SyncState.DONE,finalSummary);
                else status("Étape "+(queue.size()+1)+"/"+(queue.size()+1)+
                    " · Artistes Spotify/SoundCloud",
                    artistsPending+" soirée(s) à enrichir · "+artistLinksVerified+
                    " compte(s) Spotify déjà confirmé(s).");
            });
        });
    }
    private JSONObject findExisting(JSONObject incoming) {
        String ticketUrl=normalizeUrl(incoming.optString("ticketUrl"));
        if(!ticketUrl.isEmpty()){
            for(JSONObject item:store.events()){
                if(ticketUrl.equals(normalizeUrl(item.optString("ticketUrl"))))return item;
            }
        }
        String url=normalizeUrl(incoming.optString("url"));
        for(JSONObject item:store.events()) {
            if(url.equals(normalizeUrl(item.optString("url")))&&!url.isEmpty())return item;
        }
        String name=clean(incoming.optString("title")),date=incoming.optString("date");
        if(date.isEmpty())return null;
        JSONObject found=null;
        for(JSONObject item:store.events()) {
            if(!item.optString("origin").toLowerCase(Locale.ROOT).contains("shotgun"))continue;
            if(!date.equals(item.optString("date"))||!name.equals(clean(item.optString("title"))))continue;
            if(found!=null)return null;
            found=item;
        }
        return found;
    }
    private void saveEventPreservingUserData(JSONObject incoming) throws Exception {
        synchronized(ShotgunSyncService.class){
        JSONObject existing=findExisting(incoming);
        if(existing==null){store.saveEvent(incoming);return;}
        // A verified Shotgun À propos may change when the organizer edits
        // the event. Refresh that sourced field, but never overwrite notes.
        if("event_section".equals(incoming.optString("shotgunAboutSource")) &&
           !incoming.optString("shotgunAbout").trim().isEmpty()){
            existing.put("shotgunAbout",incoming.optString("shotgunAbout"));
            existing.put("shotgunAboutSource","event_section");
        }
        for(String key:new String[]{"poster","description","date","start","end","endDate",
            "venue","city","address","artists","organizer","genres","url","ticketUrl"}) {
            String newValue=incoming.optString(key).trim();
            if(!newValue.isEmpty()&&existing.optString(key).trim().isEmpty())
                existing.put(key,newValue);
        }
        ArtistProfileMerger.merge(existing,incoming);
        store.saveEvent(existing);
        }
    }
    private static String clean(String value) {
        return java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT),
            java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","")
            .replaceAll("[^a-z0-9]+"," ").trim();
    }
    private static String normalizeUrl(String url) {
        try {
            if(!trusted(url))return "";
            return Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString();
        }catch(Exception e){return "";}
    }
    private static boolean trusted(String value) {
        try {
            Uri uri=Uri.parse(value);String h=uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())&&h!=null&&
                (h.equalsIgnoreCase("shotgun.live")||h.toLowerCase(Locale.ROOT).endsWith(".shotgun.live"));
        }catch(Exception e){return false;}
    }
    private static boolean eventUrl(String value) {
        if(!trusted(value))return false;
        String path=Uri.parse(value).getPath();
        return path!=null && path.matches("^/(?:[a-z]{2}/)?(?:events|festivals)/[^/]+/?$");
    }
    private void finish(String state,String message) {
        if(!active) {stopSelf();return;}
        if(SyncState.DONE.equals(state)&&artistsPending>0){
            // One shared final stage: wait for every artist lookup.
            metadataDone=true;
            finalSummary=message;
            status("Étape "+(queue.size()+1)+"/"+(queue.size()+1)+
                " · Recherche de tous les artistes",
                artistsPending+" soirée(s) dont les artistes sont encore vérifiés.");
            return;
        }
        // Finishing all requests is NOT synonymous with successfully finding
        // every artist. Partial imports remain under 100% and are clearly marked.
        if(SyncState.DONE.equals(state)&&(errors>0||
            artistEventsComplete!=queue.size()||missingLineups>0||
            artistMissingLinks>0||artistMissingPhotos>0)){
            state=SyncState.PARTIAL;
            message=processed+"/"+queue.size()+" soirées importées · "+
                artistMissingLinks+" profil(s) Spotify/SoundCloud manquant(s) · "+
                artistMissingPhotos+" portrait(s) absent(s) · "+
                missingLineups+" programmation(s) introuvable(s).";
        }
        active=false;navigationId++;
        artistPool.shutdownNow();
        if(rateLimited || SyncState.BLOCKED.equals(state) && message.contains("429"))
            getSharedPreferences("afterlog_auto_sync",MODE_PRIVATE).edit()
                .putLong("blockedUntil",System.currentTimeMillis()+6*60*60*1000L).apply();
        if(SyncState.DONE.equals(state)){
            // The Spotify enrichment stage has already completed within this
            // foreground sync; do not start a separate post-sync Spotify job.
            // Un ancien échec d'un billet volontairement décoché n'est plus un échec.
            SyncFailureStore.discardStale(this);
            int remaining=SyncFailureStore.actionableCount(this);
            String summary=remaining==0 ? "Synchronisation terminée" :
                "Synchronisation partielle : "+remaining+" soirée(s) cochée(s) non récupérée(s)";
            if(remaining==0){
                SyncState.save(this,SyncState.DONE,summary,
                    queue.size()+1,queue.size()+1,0,
                    "Toutes les soirées et les fiches des artistes sont vérifiées · "+
                    artistLinksVerified+" profils Spotify associés.");
            }else{
                SyncState.save(this,SyncState.PARTIAL,summary,
                    queue.size(),queue.size()+1,0,summary);
            }
        } else {
            int total=gotTickets?queue.size()+1:0;
            // Incomplete music/account info keeps the last step unfinished.
            int done=gotTickets?Math.min(queue.size(),processed+errors):0;
            SyncState.save(this,state,message,done,total,0,message);
        }
        handler.removeCallbacksAndMessages(null);
        if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy() {
        if(active)finish(SyncState.CANCELED,"Synchronisation interrompue par Android.");
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}

    // Ces scripts ne consultent jamais les données privées des billets/QR codes.
    // Le clic DOM n'est fait que sur Mes billets / Passés dans le site déjà connecté.
    static final String DISCOVER_JS = """
      (function(){
        const path=location.pathname.toLowerCase();
        const body=(document.body?.innerText||'').slice(0,18000);
        const links=[...document.querySelectorAll('a[href]')];
        const text=e=>(e?.innerText||e?.textContent||e?.getAttribute('aria-label')||'').trim();
        const auth=(/se connecter|login|log in/i.test(body.slice(0,900)) &&
          (/\\/login|\\/sign-in|\\/signin/.test(path)));
        if(auth)return JSON.stringify({action:'login'});
        const onTickets=/(my.?tickets|mes.?billets|\\/tickets|\\/orders|mes-commandes)/i.test(path)||
          (/mes billets|my tickets/i.test(body.slice(0,1300)) && /passés|past/i.test(body.slice(0,5000)));
        if(!onTickets){
          const nav=links.find(a=>/mes billets|my tickets/i.test(text(a)));
          if(nav){
            try {const u=new URL(nav.href,location.href);
              if(u.protocol==='https:'&&/(^|\\.)shotgun\\.live$/i.test(u.hostname))
                return JSON.stringify({action:'tickets',url:u.toString()});
            }catch(_){}
          }
          const menu=[...document.querySelectorAll('button,[role="button"]')]
            .find(x=>/profil|profile|mon compte|account/i.test(text(x)));
          if(menu){menu.click();return JSON.stringify({action:'menu'});}
          return JSON.stringify({action:'wait'});
        }
        const past=[...document.querySelectorAll('button,[role="tab"],a')].find(el=>
          /^(passés|past)$/i.test(text(el)));
        const selected=past && (past.getAttribute('aria-selected')==='true'||
          past.getAttribute('data-state')==='active'||
          /active|selected/.test(String(past.className)));
        const inPast=/\\/past|\\/passed|[?&]tab=past|[?&]status=past/i.test(location.href);
        const eventPath=/^\\/(?:[a-z]{2}\\/)?(?:events|festivals)\\/[^/]+\\/?$/;
        const ticketPath=/\\/(?:my-tickets|tickets|ticket|orders)\\/[^/]+/i;
        const events=[],seen=new Set();
        const cards=[...document.querySelectorAll('a[href]')];
        for(const anchor of cards) {
          if(events.length>=75)break;
          try {
            const u=new URL(anchor.href,location.href);
            if(u.protocol!=='https:' || !/(^|\\.)shotgun\\.live$/i.test(u.hostname))continue;
            if(!eventPath.test(u.pathname)&&!ticketPath.test(u.pathname))continue;
            if(seen.has(u.pathname))continue;
            const card=anchor.closest('article,li,[class*="card"],[class*="Card"]')||anchor;
            const title=(card.querySelector('h2,h3,h4')?.textContent ||
              anchor.querySelector('h1,h2,h3,h4')?.textContent ||
              anchor.querySelector('img')?.alt || anchor.textContent||'')
              .trim().replace(/\\s+/g,' ').slice(0,160);
            if(title.length<3)continue;
            const img=card.querySelector('img')||anchor.querySelector('img');
            const cover=img?.currentSrc||img?.src||'';
            const event={title,url:u.toString()};
            if(/^https:\\/\\//.test(cover))event.poster=cover;
            const date=card.querySelector('time[datetime]')?.getAttribute('datetime')||'';
            if(/^\\d{4}-\\d\\d-\\d\\d/.test(date))event.date=date.slice(0,10);
            events.push(event);seen.add(u.pathname);
          }catch(_){}
        }
        if(past && !selected && !inPast && !window.__afterlogPastClicked) {
          window.__afterlogPastClicked=true;
          past.click();
          return JSON.stringify({action:'past'});
        }
        if(!inPast && !selected && !window.__afterlogPastClicked) {
          return JSON.stringify({action:'wait'});
        }
        return JSON.stringify({action:'ready',events});
      })();
      """;
}
