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
    private static final String RETRY_FAILED="fr.afterlog.app.RETRY_FAILED";
    private static final String EXTRA_FAILED_URL="failed_url";
    private static final String STOP="fr.afterlog.app.SYNC_STOP";
    private static final String CHANNEL="afterlog_sync";
    private static final int NOTIF_ID=4104;
    public static final String EXTRA_URL="start_url";
    private static final String HOME="https://shotgun.live/fr";
    private static final int MAX_EVENTS=75;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WebView browser;
    private AppStore store;
    private NotificationManager notifications;
    private boolean active,seenPast,clickedPast,triedMenu,gotTickets,readingPage,rateLimited;
    private int navigationId,discoveryAttempts,processed,errors,nextIndex,detailAttempts;
    private long startedAt;
    private String phase="",lastError="";
    private final List<JSONObject> queue=new ArrayList<>();
    private final Set<String> seenUrls=new HashSet<>();

    public static void start(Context ctx,String url) {
        Intent i=new Intent(ctx,ShotgunSyncService.class);
        i.setAction(START);
        i.putExtra(EXTRA_URL,url);
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
        boolean retryFailed=RETRY_FAILED.equals(action);
        boolean selectedImport=IMPORT_SELECTED.equals(action)||retryFailed;
        active=true;rateLimited=false;seenPast=false;clickedPast=false;triedMenu=false;
        gotTickets=false;readingPage=false;discoveryAttempts=0;processed=0;
        errors=0;nextIndex=0;navigationId=0;detailAttempts=0;
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
                if(trusted(item.optString("url")))queue.add(item);
            }
            if(queue.isEmpty()) {
                active=false;stopSelf();return START_NOT_STICKY;
            }
            gotTickets=true;
        }
        status(selectedImport?"Synchronisation des soirées sélectionnées…":
            "Recherche des billets passés…",selectedImport?
            "Détails des événements choisis":"Recherche automatique dans Mes billets → Passés");
        if(Build.VERSION.SDK_INT>=29)
            startForeground(NOTIF_ID,notification("Recherche des billets passés…"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIF_ID,notification("Recherche des billets passés…"));
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
            if(selectedImport) next();
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
        return builder.setContentTitle("AfterLog · Shotgun")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Arrêter",cancel)
            .build();
    }
    private void status(String text,String detail) {
        phase=text;
        int total=queue.size();
        long eta=-1;
        if(total>0 && processed>0) {
            long avg=(System.currentTimeMillis()-startedAt)/Math.max(1,processed);
            eta=Math.max(0,(total-processed)*avg/1000);
        }
        SyncState.save(this,SyncState.RUNNING,text,processed,total,eta,detail);
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
                    if(extracted.optString("poster").isEmpty())
                        extracted.put("poster",previous.optString("poster"));
                    if(extracted.optString("date").isEmpty())
                        extracted.put("date",previous.optString("date"));
                    saveEventPreservingUserData(extracted);
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
            finish(SyncState.DONE,errors>0?
                "Import partiel : "+processed+" enrichis, "+errors+" non récupérés.":
                "Import terminé : "+processed+" événements.");
        } else next();
    }
    private void errorOrAdvance(String message) {
        if(gotTickets){failCurrent(message);}
        else finish(SyncState.BLOCKED,message);
    }
    private JSONObject findExisting(JSONObject incoming) {
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
        JSONObject existing=findExisting(incoming);
        if(existing==null){store.saveEvent(incoming);return;}
        for(String key:new String[]{"poster","description","date","start","end","endDate",
            "venue","city","address","artists","organizer","genres","url"}) {
            String newValue=incoming.optString(key).trim();
            if(!newValue.isEmpty()&&existing.optString(key).trim().isEmpty())
                existing.put(key,newValue);
        }
        if(existing.optJSONArray("artistProfiles")==null && incoming.optJSONArray("artistProfiles")!=null)
            existing.put("artistProfiles",incoming.getJSONArray("artistProfiles"));
        store.saveEvent(existing);
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
        active=false;navigationId++;
        if(rateLimited || SyncState.BLOCKED.equals(state) && message.contains("429"))
            getSharedPreferences("afterlog_auto_sync",MODE_PRIVATE).edit()
                .putLong("blockedUntil",System.currentTimeMillis()+6*60*60*1000L).apply();
        SyncState.save(this,state,message,processed,queue.size(),0,
            message+(errors>0?" ("+errors+" fiches non récupérées)":""));
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
