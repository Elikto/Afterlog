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
 * Aucune donnée de mot de passe, cookie, token, requête ou QR code n'est lue par NØCTARIA.
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
    private ShotgunTicketDiscovery discovery;
    private boolean discovering;
    private boolean autoExtractEvent,autoExtractStarted;
    private boolean lineupScrollPrepared=false;
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
        button(nav,"← Retour",()->{
            if(batch!=null && batch.isActive())batch.askCancel();else finish();
        },1);
        button(nav,"↻",()->web.reload(),0.23f);
        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setText("Connecte-toi à Shotgun pour retrouver tes soirées.");
        status.setPadding(0,dp(10),0,dp(10));
        header.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(actions);
        button(actions,"Importer la soirée",this::importCurrent,1);
        button(actions,"↻ Synchroniser mes billets",this::beginDiscovery,1);

        web = new WebView(this);
        discovery=new ShotgunTicketDiscovery(this,web,status,this::onTicketsFound);
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
                else if(autoExtractEvent && !autoExtractStarted && isEventUrl(url)) {
                    autoExtractStarted=true;
                    status.setText("Extraction des informations et portraits d'artistes…");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(ShotgunBrowserActivity.this::importCurrent,2200);
                }
                else if(discovery!=null && discovering)discovery.pageFinished();
                else status.setText("NØCTARIA lit tes trois listes de billets automatiquement après connexion.");

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
                    if(discovery!=null)discovery.rateLimited();
                }
            }
        });
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(dp(12),dp(7),dp(12),dp(10));
        outer.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        button(footer,"‹ Précédent",()->{
            if(batch!=null && batch.isActive()){batch.askCancel();return;}
            if(web.canGoBack()) web.goBack(); else finish();
        },1);

        autoExtractEvent=getIntent().getBooleanExtra("afterlog_auto_extract",false);
        autoSyncMode=getIntent().getBooleanExtra("afterlog_auto_sync",false);
        String initial=getIntent().getStringExtra(EXTRA_URL);
        if(initial!=null && trusted(Uri.parse(initial)))web.loadUrl(initial);
        else if(autoSyncMode) {
            status.setText("Ouverture de ton compte Shotgun. Connecte-toi seulement si nécessaire.");
            web.loadUrl(ShotgunTicketDiscovery.ACCOUNT);
            beginDiscovery();
        }else web.loadUrl(SHOTGUN_LOGIN);
    }

    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private void button(LinearLayout row,String label,Runnable runnable,float weight) {
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
            NeonDialog.builder(this)
                .setTitle("Ouvre la fiche d'une soirée")
                .setMessage("Dans Shotgun, ouvre un événement (page /events/ ou /festivals/). Puis appuie sur « Importer cet événement ». Aucun mot de passe ni billet n'est lu.")
                .setPositiveButton("Compris",null).show();
            return;
        }
        if(web.getProgress()<100){toast("Attends la fin du chargement");return;}
        if(autoExtractEvent&&!lineupScrollPrepared){
            lineupScrollPrepared=true;
            web.evaluateJavascript("(function(){let el=Array.from(document.querySelectorAll('h1,h2,h3,h4')).find(x=>x.textContent && x.textContent.toLowerCase().includes('line up'));if(el)el.scrollIntoView({block:'center'});return true})()",ignored->
                new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::importCurrent,1500));
            return;
        }
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
    private void beginDiscovery() {
        if(discovery==null)return;
        discovering=true;
        discovery.tryAgain();
    }
    private void onTicketsFound(JSONArray tickets) {
        try {
            TicketSelectionStore.saveFound(this,tickets);
            SyncState.save(this,SyncState.SELECTION,
                tickets.length()+" soirée(s) trouvée(s) dans les trois listes",
                0,tickets.length(),-1,
                "Import automatique après la connexion Shotgun.");
            discovering=false;
            if(discovery!=null)discovery.stop();
            if(tickets.length()>0){
                AutoSyncScheduler.enable(this);
                ShotgunSyncService.importSelected(this);
                toast("Synchronisation lancée pour "+tickets.length()+" soirée(s).");
            }else{
                toast("Aucune soirée trouvée dans les trois listes Shotgun.");
            }
            setResult(RESULT_OK);
            finish();
        }catch(Exception e){
            status.setText("La liste n'a pas pu être enregistrée : "+e.getMessage());
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
        NeonDialog.builder(this)
            .setTitle("Déconnecter Shotgun de NØCTARIA ?")
            .setMessage("Les cookies de connexion enregistrés dans le navigateur intégré seront supprimés. Tes soirées NØCTARIA resteront intactes.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Déconnecter",(d,w)->{
                AutoSyncScheduler.disable(this);
                ShotgunSyncService.cancel(this);
                web.loadUrl("about:blank");
                CookieManager.getInstance().removeAllCookies(ok->{
                    CookieManager.getInstance().flush();
                    web.clearHistory();web.clearCache(true);
                    web.loadUrl(SHOTGUN_LOGIN);
                    toast("Session effacée du navigateur NØCTARIA");
                });
            }).show();
    }
    @Override public void onBackPressed() {
        if(batch!=null && batch.isActive()){batch.askCancel();return;}
        if(web!=null && web.canGoBack())web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(discovery!=null)discovery.stop();
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

          // Line-up : associer le portrait provenant de la carte Shotgun à
          // son nom, y compris lorsque le site n'utilise aucun lien <a>.
          const profiles=[];
          const artistsByName=new Map();
          const norm=v=>(v||'').toString().normalize('NFD')
            .replace(/[\\u0300-\\u036f]/g,'').toLowerCase()
            .replace(/[^a-z0-9]+/g,' ').trim();
          const absolute=v=>{
            try{
              const url=new URL(v,location.href);
              return url.protocol==='https:'?url.href:'';
            }catch(_){return '';}
          };
          const artistUrl=v=>/^https:\\/\\/open\\.spotify\\.com\\/(?:intl-[a-z]{2}\\/)?artist\\/[a-zA-Z0-9]{22}\\/?(?:[?#].*)?$/i.test(v||'');
          const picture=element=>{
            if(!element)return '';
            const found=[];
            const read=el=>{
              if(!el)return;
              for(const attr of ['currentSrc','src','data-src','data-lazy-src','data-original','data-image']){
                const raw=el[attr]||el.getAttribute?.(attr);
                if(typeof raw==='string'&&raw)found.push(raw);
              }
              const set=el.getAttribute?.('srcset')||'';
              if(set)for(const candidate of set.split(',')){
                const u=candidate.trim().split(/\\s+/)[0];
                if(u)found.push(u);
              }
              try{
                const css=getComputedStyle(el).backgroundImage||'';
                if(css&&css!=='none'){
                  const matches=css.match(/url\\(([^)]+)\\)/g)||[];
                  for(const match of matches)found.push(match.slice(4,-1).replace(/^["']|["']$/g,''));
                }
              }catch(_){}
            };
            read(element);
            for(const img of element.querySelectorAll?.('img,source,[style*="background"],[style*="image"]')||[]){
              read(img);
              if(found.length>32)break;
            }
            for(const candidate of found) {
              const u=absolute(candidate);
              if(u && u!==result.poster && !/transparent|placeholder|blank\\.gif/i.test(u))
                return u;
            }
            return '';
          };
          const take=(name,photo,shotgunUrl,spotify)=>{
            name=(name||'').toString().trim().replace(/\\s+/g,' ');
            if(name.length<2||name.length>100)return;
            const key=norm(name);
            if(!key)return;
            if(!artistsByName.has(key)){
              if(profiles.length>=55)return;
              const entry={name,photo:'',shotgunUrl:'',spotify:''};
              artistsByName.set(key,entry);
              profiles.push(entry);
            }
            const p=artistsByName.get(key);
            const img=absolute(photo),sg=absolute(shotgunUrl),sp=absolute(spotify);
            if(img && !p.photo)p.photo=img;
            if(sg&&/(^|\\.)shotgun\\.live$/i.test(new URL(sg).hostname) &&
               /\\/artists?\\//i.test(new URL(sg).pathname) && !p.shotgunUrl)p.shotgunUrl=sg;
            if(artistUrl(sp)&&!p.spotify)p.spotify=sp;
          };
          const performers=node?Array.isArray(node.performer)?node.performer:
            [node.performer]:[];
          for(const artist of performers){
            if(typeof artist==='string'){take(artist,'','','');continue;}
            if(!artist||typeof artist!=='object')continue;
            const image=artist.image;
            const imageUrl=typeof image==='string'?image:
              Array.isArray(image)?(typeof image[0]==='string'?image[0]:image[0]?.url):
              image&&typeof image==='object'?image.url:'';
            const urls=Array.isArray(artist.sameAs)?artist.sameAs:[artist.sameAs];
            take(artist.name,imageUrl||'',artist.url,
              urls.find(u=>artistUrl(u))||'');
          }
          for(const name of (result.artists||'').split(',')){
            if(name.trim())take(name,'','','');
          }
          const headings=[...document.querySelectorAll(
            'main h1,main h2,main h3,main h4,section h2,section h3')];
          const lineHeading=headings.find(h=>
            /^(line[\\s-]?up|programmation|artistes|artists)$/i.test(
              (h.textContent||'').trim()));
          let lineArea=null;
          if(lineHeading){
            let container=lineHeading.parentElement;
            for(let k=0;k<5&&container;k++,container=container.parentElement){
              const names=[...container.querySelectorAll('a,h3,h4,p,span')]
                .filter(el=>el.children.length<3 &&
                  artistsByName.has(norm(el.textContent||''))).length;
              if(names>=2 && container.querySelectorAll('img').length>=2){
                lineArea=container;break;
              }
            }
          }
          if(!lineArea)lineArea=document.querySelector('main')||document.body;
          // Les cartes sont parfois des <button> / div React, pas des liens.
          const candidateText=[...lineArea.querySelectorAll(
            'a,button,h3,h4,p,span,strong,[data-testid*="artist"]')];
          for(const label of candidateText){
            const labelText=(label.textContent||'').trim();
            if(labelText.length<2||labelText.length>95||
               label.children.length>3)continue;
            const key=norm(labelText);
            if(!artistsByName.has(key))continue;
            const entry=artistsByName.get(key);
            let container=label.parentElement;
            for(let depth=0;depth<5&&container;depth++,container=container.parentElement){
              if((container.textContent||'').length>350)break;
              const portrait=picture(container);
              if(!portrait)continue;
              const anchor=container.closest('a[href*="/artists/"],a[href*="/artist/"]')||
                container.querySelector('a[href*="/artists/"],a[href*="/artist/"]');
              const social=container.querySelector(
                'a[href*="open.spotify.com/artist/"]');
              take(entry.name,portrait,anchor?.href||'',social?.href||'');
              break;
            }
          }
          for(const img of lineArea.querySelectorAll('img')){
            const alt=norm(img.getAttribute('alt')||'');
            if(!artistsByName.has(alt))continue;
            const card=img.closest('a,button,article,li,[class*="artist"],[class*="Artist"]')||
              img.parentElement;
            const social=card?.querySelector('a[href*="open.spotify.com/artist/"]');
            const link=card?.closest('a[href*="/artists/"]')||
              card?.querySelector('a[href*="/artists/"]');
            take(artistsByName.get(alt).name,picture(img)||picture(card),
              link?.href||'',social?.href||'');
          }
          // Données publiques structurées : Next.js, application/json ou LD+JSON.
          // Limites de taille/profondeur pour ne pas ralentir le téléphone.
          let examined=0;
          const walk=(object,level)=>{
            if(!object || typeof object!=='object'||level>16||examined++>12000)return;
            if(Array.isArray(object)){
              for(let i=0;i<Math.min(object.length,250);i++)walk(object[i],level+1);
              return;
            }
            const candidate=object.name||object.stageName||object.artistName||
              object.displayName;
            const name=typeof candidate==='string'?candidate:'';
            if(name&&artistsByName.has(norm(name))){
              const image=object.profilePicture||object.avatar||object.image||
                object.picture||object.profileImage||object.photo||
                object.imageUrl||object.avatarUrl||object.profilePictureUrl;
              const unbox=x=>typeof x==='string'?x:
                x&&typeof x==='object'?(x.url||x.src||x.original||x.medium||''):'';
              const artistLink=object.url||object.profileUrl||object.shotgunUrl||'';
              const spotify=object.spotifyUrl||object.spotify||'';
              take(artistsByName.get(norm(name)).name,unbox(image),unbox(artistLink),
                unbox(spotify));
            }
            for(const [key,value] of Object.entries(object)){
              if(key==='description'||key==='html'||key==='content'||key==='body')continue;
              if(typeof value==='object'&&value)walk(value,level+1);
            }
          };
          for(const script of document.querySelectorAll(
            'script#__NEXT_DATA__,script[type="application/json"],script[type="application/ld+json"]')){
            const raw=script.textContent||'';
            if(!raw||raw.length>2200000)continue;
            try{walk(JSON.parse(raw),0);}catch(_){}
          }
          // En l'absence de nom structuré, récupérer les liens de profils
          // présents dans les cartes du line-up, sans déduire de Spotify au hasard.
          for(const anchor of lineArea.querySelectorAll(
            'a[href*="/artists/"],a[href*="/artist/"]')){
            const name=(anchor.querySelector('h3,h4')?.textContent||
              anchor.textContent||anchor.querySelector('img')?.alt||'').trim();
            if(artistsByName.has(norm(name))){
              take(artistsByName.get(norm(name)).name,picture(anchor),
                anchor.href,
                anchor.querySelector('a[href*="open.spotify.com/artist/"]')?.href||'');
            }
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
