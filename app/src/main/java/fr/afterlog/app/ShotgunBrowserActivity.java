package fr.afterlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.util.Locale;

/**
 * Navigateur dédié au site officiel Shotgun.
 * Aucune donnée de mot de passe, cookie, token, requête ou QR code n'est lue par AfterLog.
 * Seuls les champs des pages d'événements ou les liens explicitement affichés sont importés,
 * à la demande de l'utilisateur, via evaluateJavascript.
 */
public final class ShotgunBrowserActivity extends Activity {
    public static final String EXTRA_URL = "shotgun_url";
    public static final String EXTRA_EVENTS = "imported_events";
    private static final String SHOTGUN_LOGIN = "https://shotgun.live/fr/login";
    private static final int BG = Color.rgb(12, 14, 24);
    private static final int PANEL = Color.rgb(32, 35, 53);
    private static final int ACCENT = Color.rgb(186, 166, 255);
    private WebView web;
    private TextView status;
    private ShotgunBatchImporter batch;
    private boolean autoSyncMode;
    private boolean syncTriggered;
    private final android.os.Handler authHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private int authChecks;
    private final Runnable authPoll=new Runnable(){
        @Override public void run() {
            if(!autoSyncMode || syncTriggered || isFinishing() || web==null || authChecks++>120)return;
            detectLoginAndSync();
            authHandler.postDelayed(this,2500);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);
        setContentView(outer);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(12), dp(9), dp(12), dp(8));
        outer.addView(header,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(nav);
        button(nav,"← AfterLog",()->{
            if(batch!=null && batch.isActive())batch.askCancel();else finish();
        },1);
        button(nav,"Accueil Shotgun",()->web.loadUrl("https://shotgun.live/fr"),1);
        button(nav,"Actualiser",()->web.reload(),1);
        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setText("Connecte-toi sur shotgun.live, puis ouvre Mes billets → Passés.");
        status.setPadding(0,dp(10),0,dp(10));
        header.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(actions);
        button(actions,"Importer cet événement",this::importCurrent,1);
        button(actions,"Connexion terminée · Continuer",()->launchSync(web.getUrl()),1);

        web = new WebView(this);
        batch = new ShotgunBatchImporter(this,web, message -> status.setText(message), this::finishWithEvents);
        outer.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings config = web.getSettings();
        config.setJavaScriptEnabled(true);
        config.setDomStorageEnabled(true);
        config.setAllowFileAccess(false);
        config.setAllowContentAccess(false);
        config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        config.setSupportMultipleWindows(false);
        config.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri uri = req.getUrl();
                if (trusted(uri)) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try {startActivity(new Intent(Intent.ACTION_VIEW,uri));}
                    catch (Exception e) {toast("Impossible d'ouvrir le lien externe");}
                } else toast("Lien externe bloqué");
                return true;
            }
            @Override public void onPageFinished(WebView view,String url) {
                if(batch!=null && batch.isActive()) batch.onPageFinished(url);
                else if(autoSyncMode && !syncTriggered) {
                    status.setText("Connexion Shotgun · recherche de ton compte…");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(()->detectLoginAndSync(),1200);
                } else status.setText("shotgun.live — Utilise le menu Shotgun pour accéder à Mes billets → Passés.");
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError error) {
                if (req.isForMainFrame()) {
                    status.setText("Page inaccessible : vérifie ta connexion puis réessaie.");
                    if(batch!=null)batch.onLoadFailure();
                }
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest req,
                                                       android.webkit.WebResourceResponse response) {
                if (req.isForMainFrame() && response.getStatusCode()==429) {
                    status.setText("Shotgun limite l'accès (HTTP 429). Réessaie plus tard ; ne multiplie pas les demandes.");
                    if(batch!=null)batch.onHttp429();
                }
            }
        });
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(dp(12),dp(7),dp(12),dp(10));
        outer.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        button(footer,"← Page précédente",()->{
            if(batch!=null && batch.isActive()){batch.askCancel();return;}
            if(web.canGoBack()) web.goBack(); else finish();
        },2);
        button(footer,"Déconnecter Shotgun",this::confirmLogout,2);

        autoSyncMode=getIntent().getBooleanExtra("afterlog_auto_sync",false);
        if(autoSyncMode) {
            status.setText("Connecte-toi sur Shotgun. AfterLog cherchera ensuite tes billets passés.");
            authHandler.postDelayed(authPoll,2300);
        }
        String initial=getIntent().getStringExtra(EXTRA_URL);
        if(initial!=null && trusted(Uri.parse(initial))) web.loadUrl(initial);
        else web.loadUrl(SHOTGUN_LOGIN);
    }

    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private void button(LinearLayout row,String label,Runnable runnable,int weight) {
        TextView b=new TextView(this);
        b.setText(label);b.setTextSize(12);b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setPadding(dp(6),dp(12),dp(6),dp(12));
        b.setGravity(Gravity.CENTER);
        b.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,weight);
        lp.setMargins(dp(3),0,dp(3),0);
        row.addView(b,lp);
        b.setOnClickListener(v->runnable.run());
    }
    void toast(String value) {Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    static boolean trusted(Uri uri) {
        String host=uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) &&
            host!=null && (host.equalsIgnoreCase("shotgun.live")
                || host.toLowerCase(Locale.ROOT).endsWith(".shotgun.live"));
    }
    private boolean isEventUrl(String value) {
        if(value==null)return false;
        Uri uri=Uri.parse(value);
        if(!trusted(uri))return false;
        String path=uri.getPath()==null?"":uri.getPath();
        return path.matches("^/(?:[a-z]{2}/)?(?:events|festivals)/[^/]+/?$");
    }
    private void importCurrent() {
        String url=web.getUrl();
        if(!isEventUrl(url)) {
            new AlertDialog.Builder(this)
                .setTitle("Ouvre la fiche d'une soirée")
                .setMessage("Dans Shotgun, ouvre un événement (page /events/ ou /festivals/). Puis appuie sur « Importer cet événement ». Aucun mot de passe ni billet n'est lu.")
                .setPositiveButton("Compris",null).show();
            return;
        }
        if(web.getProgress()<100){toast("Attends la fin du chargement");return;}
        web.evaluateJavascript(CURRENT_EVENT_JS,value->{
            try {
                JSONObject event=new JSONObject(decoded(value));
                String title=event.optString("title").trim();
                if(title.isEmpty())throw new Exception("Nom introuvable");
                event.put("url",url);
                event.put("origin","Shotgun (navigateur)");
                finishWithEvents(new JSONArray().put(event));
            }catch(Exception e){toast("Informations manquantes. Tu peux saisir cette soirée manuellement.");}
        });
    }
    private void detectLoginAndSync() {
        if(!autoSyncMode||syncTriggered||web==null||isFinishing())return;
        String address=web.getUrl();
        if(address==null || !trusted(Uri.parse(address)))return;
        web.evaluateJavascript("""
          (function(){
            const path=location.pathname.toLowerCase();
            const entries=[...document.querySelectorAll('a[href]')];
            const ticket=entries.find(a=>/mes billets|my tickets/i.test(
              (a.innerText||a.textContent||'').trim()));
            const profile=!!document.querySelector(
              'a[href*="/profile"],button[aria-label*="Profil"],button[aria-label*="Profile"],[data-testid*="avatar"]');
            const login=/(login|sign-?in)/.test(path);
            return JSON.stringify({ok:!login && (!!ticket||profile), url:ticket?.href||location.href});
          })();
          """,raw->{
            if(!autoSyncMode||syncTriggered)return;
            try {
                JSONObject state=new JSONObject(decoded(raw));
                if(!state.optBoolean("ok")){
                    status.setText("Connecte-toi à Shotgun. Si la connexion n’est pas détectée, utilise « Connexion terminée ».");
                    return;
                }
                launchSync(state.optString("url",address));
            }catch(Exception ignored){}
        });
    }
    private void launchSync(String url) {
        if(syncTriggered)return;
        syncTriggered=true;
        try {
            // Suspendre la recherche périodique jusqu'au choix des billets à importer.
            AutoSyncScheduler.disable(this);
            ShotgunSyncService.start(this,url);
            setResult(RESULT_OK);
            finish();
        }catch(Exception e) {
            syncTriggered=false;
            toast("Synchronisation impossible : "+e.getMessage());
        }
    }
    private void importVisible() {
        batch.begin();
    }

    static String decoded(String jsonEncoded) throws Exception {
        if(jsonEncoded==null || "null".equals(jsonEncoded))throw new Exception("Aucun résultat");
        return new JSONArray("["+jsonEncoded+"]").getString(0);
    }
    private void finishWithEvents(JSONArray events) {
        Intent result=new Intent();
        result.putExtra(EXTRA_EVENTS,events.toString());
        setResult(RESULT_OK,result);
        finish();
    }
    private void confirmLogout() {
        new AlertDialog.Builder(this)
            .setTitle("Déconnecter Shotgun d'AfterLog ?")
            .setMessage("Les cookies de connexion enregistrés dans le navigateur intégré seront supprimés. Tes soirées AfterLog resteront intactes.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Déconnecter",(d,w)->{
                AutoSyncScheduler.disable(this);
                ShotgunSyncService.cancel(this);
                web.loadUrl("about:blank");
                CookieManager.getInstance().removeAllCookies(ok->{
                    CookieManager.getInstance().flush();
                    web.clearHistory();web.clearCache(true);
                    web.loadUrl(SHOTGUN_LOGIN);
                    toast("Session effacée du navigateur AfterLog");
                });
            }).show();
    }
    @Override public void onBackPressed() {
        if(batch!=null && batch.isActive()){batch.askCancel();return;}
        if(web!=null && web.canGoBack())web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        authHandler.removeCallbacksAndMessages(null);
        if(batch!=null)batch.cancelSilently();
        if(web!=null){
            ViewGroup parent=(ViewGroup)web.getParent();
            if(parent!=null)parent.removeView(web);
            web.destroy();web=null;
        }
        super.onDestroy();
    }

    // Le script ne s'exécute QUE lorsque l'utilisateur choisit d'importer une page événement.
    // Ne lit jamais les formulaires, tokens, cookies, billets ni QR codes.
    static final String CURRENT_EVENT_JS = """
        (function(){
          const m=(name)=>{const e=document.querySelector('meta[property="'+name+'"],meta[name="'+name+'"]');return e?e.content.trim():''};
          const safe=(v)=>typeof v==='string'?v:'';
          const result={title:m('og:title')||safe(document.querySelector('h1')?.textContent).trim()||document.title,
            poster:m('og:image'),description:m('og:description')||m('description'),url:location.href};
          result.title=result.title.replace(/\\s*[|–-]\\s*Shotgun\\s*$/i,'').trim();
          let graph=[];
          for(const script of document.querySelectorAll('script[type="application/ld+json"]')){
            try{let j=JSON.parse(script.textContent);graph.push(...(Array.isArray(j)?j:[j]));}catch(_){}
          }
          const all=[];
          while(graph.length){
            const node=graph.shift();
            if(!node||typeof node!=='object')continue;
            if(Array.isArray(node['@graph']))graph.push(...node['@graph']);
            const kind=Array.isArray(node['@type'])?node['@type'].join(' '):safe(node['@type']);
            if(kind.toLowerCase().includes('event')){all.push(node);break;}
          }
          const node=all[0];
          if(node){
            if(!result.title)result.title=safe(node.name);
            if(!result.description)result.description=safe(node.description);
            if(!result.poster)result.poster=typeof node.image==='string'?node.image:(Array.isArray(node.image)?node.image[0]:'');
            const start=safe(node.startDate),end=safe(node.endDate);
            if(/^\\d{4}-\\d\\d-\\d\\d/.test(start))result.date=start.substring(0,10);
            if(/^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d/.test(start))result.start=start.substring(11,16);
            if(/^\\d{4}-\\d\\d-\\d\\d/.test(end))result.endDate=end.substring(0,10);
            if(/^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d/.test(end))result.end=end.substring(11,16);
            const loc=node.location;
            if(loc&&typeof loc==='object'){
              result.venue=safe(loc.name);
              if(typeof loc.address==='object'&&loc.address){
                result.address=safe(loc.address.streetAddress);
                result.city=safe(loc.address.addressLocality);
              }else result.address=safe(loc.address);
            }
            const names=(v)=>{if(!v)return'';return (Array.isArray(v)?v:[v])
              .map(x=>typeof x==='string'?x:safe(x?.name)).filter(Boolean).join(', ')};
            result.artists=names(node.performer);
            result.organizer=names(node.organizer);
          }
          const txt=(sel,max)=>{
            const n=document.querySelector(sel);
            return (n?.innerText||n?.textContent||'').trim().replace(/\\s+/g,' ').slice(0,max);
          };
          const fromDate=(iso,dateField,timeField)=>{
            if(typeof iso!=='string')return;
            if(/^\\d{4}-\\d\\d-\\d\\d/.test(iso))result[dateField]=iso.slice(0,10);
            if(/^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d/.test(iso))result[timeField]=iso.slice(11,16);
          };
          if(!result.date){
            const metaDate=m('event:start_time')||m('event:startDate');
            if(metaDate)fromDate(metaDate,'date','start');
          }
          if(!result.endDate){
            const metaEnd=m('event:end_time')||m('event:endDate');
            if(metaEnd)fromDate(metaEnd,'endDate','end');
          }
          const dates=[...document.querySelectorAll('time[datetime]')].map(x=>x.getAttribute('datetime'));
          if(!result.date && dates.length)fromDate(dates[0],'date','start');
          if(!result.endDate && dates.length>1)fromDate(dates[dates.length-1],'endDate','end');
          if(!result.poster){
            const img=document.querySelector('main img[src],article img[src]');
            const src=img?.currentSrc||img?.src||'';
            if(/^https:\\/\\//.test(src))result.poster=src;
          }
          if(!result.description){
            result.description=txt('[data-testid*="description"]',7000) ||
              txt('[class*="eventDescription"]',7000) ||
              txt('[class*="EventDescription"]',7000) ||
              txt('[id*="description"]',7000);
          }
          // Texte complet de la section À propos s'il est visible sur la fiche.
          const sections=[...document.querySelectorAll('main h2,main h3,article h2,article h3')];
          const about=sections.find(h=>/^(à propos|about|description)$/i.test((h.textContent||'').trim()));
          if(about){
            const content=about.nextElementSibling;
            const full=(content?.innerText||content?.textContent||'').trim();
            if(full.length>result.description.length && full.length<12000)result.description=full;
          }
          if(!result.address)result.address=txt('main address',250);
          const linked=(pattern,max)=>{
            const anchors=[...document.querySelectorAll('main a[href]')];
            return [...new Set(anchors.filter(a=>pattern.test(a.getAttribute('href')||''))
              .map(a=>(a.textContent||'').trim()).filter(x=>x && x.length<85))]
              .slice(0,max).join(', ');
          };
          if(!result.genres)result.genres=linked(/\\/genre|\\/music\\//i,20);
          if(!result.artists)result.artists=linked(/\\/artist/i,45);
          if(!result.organizer)result.organizer=linked(/\\/organi[sz]er/i,10);
          const profiles=[];
          const seenArtists=new Set();
          const addArtist=(name,photo,spotify)=>{
            name=typeof name==='string'?name.trim():'';
            if(!name||seenArtists.has(name.toLowerCase())||profiles.length>=45)return;
            if(photo && !/^https:\\/\\//.test(photo))photo='';
            if(spotify && !/^https:\\/\\/open\\.spotify\\.com\\/artist\\/[A-Za-z0-9]+/.test(spotify))spotify='';
            seenArtists.add(name.toLowerCase());
            profiles.push({name,photo:photo||'',spotify:spotify||''});
          };
          if(node){
            const perf=Array.isArray(node.performer)?node.performer:[node.performer];
            for(const person of perf){
              if(!person || typeof person!=='object')continue;
              const photo=typeof person.image==='string'?person.image:
                Array.isArray(person.image)?person.image[0]:
                person.image&&typeof person.image==='object'?person.image.url:'';
              const same=Array.isArray(person.sameAs)?person.sameAs:[person.sameAs];
              const spotify=same.find(u=>typeof u==='string'&&
                /^https:\\/\\/open\\.spotify\\.com\\/artist\\/[A-Za-z0-9]+/.test(u))||'';
              addArtist(person.name,photo,spotify);
            }
          }
          for(const a of document.querySelectorAll('main a[href*="/artist"],article a[href*="/artist"]')){
            if(profiles.length>=45)break;
            const name=(a.querySelector('h2,h3,h4')?.textContent ||a.textContent ||
              a.querySelector('img')?.alt||'').trim().replace(/\\s+/g,' ');
            const img=a.querySelector('img');
            const photo=img?.currentSrc||img?.src||'';
            addArtist(name,photo,'');
          }
          if(profiles.length)result.artistProfiles=profiles;
          return JSON.stringify(result);
        })();
        """;

    // Données minimales lues depuis les liens visibles uniquement, sur action explicite.
    private static final String VISIBLE_LINKS_JS = """
        (function(){
          const seen=new Set(),items=[];
          for(const a of document.querySelectorAll('a[href]')){
            if(items.length>=100)break;
            try {
              const u=new URL(a.href,location.origin);
              if(u.protocol!=='https:' || !/(^|\\.)shotgun\\.live$/i.test(u.hostname))continue;
              if(!/^\\/(?:[a-z]{2}\\/)?(?:events|festivals)\\/[^/]+\\/?$/.test(u.pathname))continue;
              u.search='';u.hash='';
              const url=u.toString();
              if(seen.has(url))continue;
              const heading=a.querySelector('h1,h2,h3,h4,[class*="title"],[class*="Title"]');
              const img=a.querySelector('img');
              const title=(heading?.textContent||img?.alt||a.textContent||'').trim().replace(/\\s+/g,' ').slice(0,160);
              if(title.length<3)continue;
              seen.add(url);
              const entry={title,url};
              const poster=img?.getAttribute('src')||'';
              if(poster.startsWith('https://'))entry.poster=poster;
              const date=a.querySelector('time[datetime]')?.getAttribute('datetime')||'';
              if(/^\\d{4}-\\d\\d-\\d\\d/.test(date))entry.date=date.substring(0,10);
              items.push(entry);
            }catch(_){}
          }
          return JSON.stringify(items);
        })();
        """;
}
