package fr.afterlog.app;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lecture automatique des trois listes de billets du compte personnel Shotgun,
 * après la connexion effectuée par l'utilisateur sur le vrai site Shotgun
 * dans une WebView Android visible. Aucune donnée d'authentification, QR code,
 * facture ou moyen de paiement n'est extraite.
 *
 * Cette méthode dépend de l'interface Web de Shotgun : elle n'est pas une API
 * officielle, ne contourne aucun contrôle d'accès et s'arrête en cas de 429.
 */
final class ShotgunTicketDiscovery {
    static final String[] TICKET_PAGES={
        "https://shotgun.live/fr/account/tickets",
        "https://shotgun.live/fr/account/tickets/pending",
        "https://shotgun.live/fr/account/tickets/past"
    };
    static final String ACCOUNT=TICKET_PAGES[0];
    static final int MAX_EVENTS=200;

    private final ShotgunBrowserActivity activity;
    private final WebView web;
    private final TextView status;
    private final Consumer<JSONArray> foundCallback;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String,JSONObject> gathered=new LinkedHashMap<>();
    private boolean active,busy,stopped;
    private int pageIndex,tries,offRouteTries;
    private String lastExpected="";

    ShotgunTicketDiscovery(ShotgunBrowserActivity activity,WebView web,TextView status,
                           Consumer<JSONArray> foundCallback) {
        this.activity=activity;
        this.web=web;
        this.status=status;
        this.foundCallback=foundCallback;
    }
    void begin(){
        active=true;busy=false;stopped=false;pageIndex=0;tries=0;offRouteTries=0;
        gathered.clear();lastExpected="";
        status.setText("Connecte-toi à Shotgun. Les trois listes de billets seront lues automatiquement.");
        schedule(1300);
    }
    void stop(){active=false;stopped=true;handler.removeCallbacksAndMessages(null);}
    void pageFinished(){if(active){busy=false;schedule(1300);}}
    void rateLimited(){
        stop();
        status.setText("Shotgun limite les demandes (HTTP 429) : synchronisation interrompue.");
    }
    void tryAgain(){stop();begin();}
    private void schedule(int delay){
        if(active&&!stopped&&!activity.isFinishing())handler.postDelayed(this::inspect,delay);
    }
    private static boolean loginPage(String address){
        String path=Uri.parse(address).getPath();
        if(path==null)return false;
        return path.matches("(?i).*/(?:login|signin|sign-in|auth)(?:/.*)?");
    }
    private static String cleanUrl(String value){
        if(value==null)return "";
        try{
            Uri uri=Uri.parse(value);
            return uri.buildUpon().clearQuery().fragment(null).build().toString()
                .replaceAll("/+$","");
        }catch(Exception e){return "";}
    }
    private void inspect(){
        if(!active||stopped||activity.isFinishing()||busy)return;
        if(pageIndex>=TICKET_PAGES.length){complete();return;}
        String current=web.getUrl();
        if(current==null||!ShotgunBrowserActivity.trusted(Uri.parse(current))){
            schedule(2000);return;
        }
        if(loginPage(current)){
            status.setText("Connecte-toi sur Shotgun. NØCTARIA reprendra automatiquement ensuite.");
            schedule(1700);return;
        }
        String expected=TICKET_PAGES[pageIndex];
        if(!cleanUrl(current).equals(cleanUrl(expected))){
            // Après authentification, aller directement à la liste demandée,
            // même si Shotgun a renvoyé vers son accueil.
            if(++offRouteTries>12){
                status.setText("Liste Shotgun inaccessible : "+(pageIndex+1)+"/3.");
                nextPage();return;
            }
            if(offRouteTries==1 || offRouteTries%3==0){
                lastExpected=expected;
                status.setText("Ouverture des billets Shotgun : "+(pageIndex+1)+"/3…");
                web.loadUrl(expected);
            }
            schedule(2200);
            return;
        }
        offRouteTries=0;
        if(web.getProgress()<90){schedule(1300);return;}
        if(++tries>12){nextPage();return;}
        busy=true;
        web.evaluateJavascript(SCAN_JS,raw->{
            busy=false;
            if(!active||stopped||activity.isFinishing())return;
            try{
                JSONObject response=new JSONObject(ShotgunBrowserActivity.decoded(raw));
                String action=response.optString("action");
                if("login".equals(action)){
                    status.setText("Termine ta connexion Shotgun pour lancer la synchronisation.");
                    schedule(1800);return;
                }
                if("wait".equals(action)||"scroll".equals(action)){
                    status.setText("Lecture des billets : "+(pageIndex+1)+"/3…");
                    schedule(1500);return;
                }
                JSONArray events=response.optJSONArray("events");
                if(events!=null){
                    for(int i=0;i<events.length() && gathered.size()<MAX_EVENTS;i++){
                        JSONObject event=events.optJSONObject(i);
                        if(event==null)continue;
                        String url=event.optString("url");
                        if(!ShotgunBrowserActivity.trusted(Uri.parse(url)))continue;
                        String key=cleanUrl(url);
                        if(!key.isEmpty()&&!gathered.containsKey(key))gathered.put(key,event);
                    }
                }
                status.setText("Billets "+(pageIndex+1)+"/3 : "+gathered.size()+" soirée(s) repérée(s).");
                nextPage();
            }catch(Exception e){schedule(2000);}
        });
    }
    private void nextPage(){
        if(!active)return;
        pageIndex++;tries=0;offRouteTries=0;lastExpected="";
        if(pageIndex>=TICKET_PAGES.length){complete();return;}
        schedule(1100);
    }
    private void complete(){
        if(!active)return;
        stop();
        JSONArray result=new JSONArray();
        for(JSONObject e:gathered.values())result.put(e);
        status.setText(result.length()+" soirée(s) repérée(s) dans les trois catégories.");
        foundCallback.accept(result);
    }

    // Ce script analyse uniquement les cartes de liste et les liens publics
    // d'événements; jamais les QR codes ni les identifiants des billets.
    static final String SCAN_JS = """
      (function(){
        const path=location.pathname.toLowerCase();
        if(/\\/(?:login|signin|sign-in|auth)(?:\\/|$)/i.test(path) ||
           document.querySelector('input[type="password"]'))
          return JSON.stringify({action:'login'});
        if(!/\\/account\\/tickets(?:\\/(?:pending|past))?\\/?$/.test(path))
          return JSON.stringify({action:'wait'});
        const state=window.__noctariaTicketPage||(window.__noctariaTicketPage={visits:0});
        if(++state.visits===1){
          window.scrollTo(0,document.documentElement.scrollHeight||0);
          for(const node of document.querySelectorAll('main,[role="main"],[class*="scroll"]')){
            if(node.scrollHeight>node.clientHeight)node.scrollTop=node.scrollHeight;
          }
          return JSON.stringify({action:'scroll'});
        }
        const clean=s=>String(s||'').replace(/\\s+/g,' ').trim();
        const eventPath=/^\\/(?:[a-z]{2}\\/)?(?:events|festivals)\\/[^/]+\\/?$/i;
        const ticketPath=/^\\/(?:[a-z]{2}\\/)?(?:account\\/)?(?:tickets?|orders)\\/(?!pending\\/?$|past\\/?$)[a-z0-9_-]{5,}\\/?$/i;
        const events=[],seen=new Set();
        const links=[...document.querySelectorAll('a[href]')];
        for(const anchor of links){
          if(events.length>=200)break;
          let url;
          try{url=new URL(anchor.href,location.href);}catch(_){continue;}
          if(url.protocol!=='https:' || !/(^|\\.)shotgun\\.live$/i.test(url.hostname))continue;
          if(!eventPath.test(url.pathname)&&!ticketPath.test(url.pathname))continue;
          if(seen.has(url.pathname))continue;
          const card=anchor.closest('article,li,[class*="ticket"],[class*="Ticket"],[class*="card"],[class*="Card"]')||anchor;
          const img=card.querySelector('img')||anchor.querySelector('img');
          const title=clean(card.querySelector('h2,h3,h4')?.textContent ||
            anchor.querySelector('h1,h2,h3,h4')?.textContent || img?.alt ||
            anchor.textContent).slice(0,180);
          if(title.length<3 || /^(mes billets|my tickets|à venir|passés|pending|past)$/i.test(title))continue;
          const item={url:url.origin+url.pathname,title};
          const poster=img?.currentSrc||img?.src||'';
          if(poster.startsWith('https://'))item.poster=poster;
          const date=card.querySelector('time[datetime]')?.getAttribute('datetime')||'';
          if(/^\\d{4}-\\d\\d-\\d\\d/.test(date))item.date=date.slice(0,10);
          events.push(item);seen.add(url.pathname);
        }
        if(!events.length &&
            (document.querySelector('[aria-busy="true"]') ||
             /chargement|loading/i.test(clean(document.querySelector('main')?.textContent).slice(0,100))))
          return JSON.stringify({action:'wait'});
        return JSON.stringify({action:'ready',events});
      })();
      """;
}
