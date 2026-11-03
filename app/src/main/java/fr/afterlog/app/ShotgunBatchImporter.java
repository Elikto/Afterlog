package fr.afterlog.app;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Import séquentiel déclenché par l'utilisateur à partir de Mes billets / Passés.
 * Une seule page à la fois dans le WebView déjà connecté. Aucun fetch/API ni
 * lecture de billets, QR codes, mots de passe, formulaires, cookies ou tokens.
 */
final class ShotgunBatchImporter {
    private final ShotgunBrowserActivity host;
    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Consumer<String> status;
    private final Consumer<JSONArray> completedCallback;
    private final List<JSONObject> pending = new ArrayList<>();
    private final JSONArray collected = new JSONArray();
    private final Set<String> seen = new HashSet<>();
    private String listPage;
    private boolean active;
    private boolean rateLimited;
    private int current;
    private int token;
    private int attempts;
    private int hops;
    private int succeeded;
    private int failed;
    private boolean inspected;
    private String currentUrl;
    private String lastError="";

    ShotgunBatchImporter(ShotgunBrowserActivity host, WebView web,
            Consumer<String> status, Consumer<JSONArray> completedCallback) {
        this.host=host;this.web=web;this.status=status;this.completedCallback=completedCallback;
    }
    boolean isActive() {return active;}

    void begin() {
        if(active) return;
        if(web.getProgress()<100) {
            host.toast("La page Shotgun est encore en cours de chargement.");
            return;
        }
        String url=web.getUrl();
        if(url==null || !ShotgunBrowserActivity.trusted(Uri.parse(url))) {
            host.toast("Ouvre d'abord Mes billets → Passés sur Shotgun.");
            return;
        }
        listPage=url;
        web.evaluateJavascript(DISCOVER_JS,result->{
            try {
                JSONArray found=new JSONArray(ShotgunBrowserActivity.decoded(result));
                if(found.length()==0) {missingLinks();return;}
                pending.clear();seen.clear();
                for(int i=0;i<found.length() && pending.size()<75;i++) {
                    JSONObject event=found.optJSONObject(i);
                    if(event==null)continue;
                    String link=event.optString("url");
                    if(!valid(link) || !seen.add(stripTrackers(link)))continue;
                    pending.add(event);
                }
                if(pending.isEmpty()) {missingLinks();return;}
                NeonDialog.builder(host)
                    .setTitle("Compléter "+pending.size()+" soirée(s)")
                    .setMessage("NØCTARIA va ouvrir chaque fiche Shotgun, suivre « Voir l'événement » si nécessaire, puis collecter les informations publiques (affiche, dates, lieu, description, artistes).\n\nLes pages seront ouvertes une par une. Garde NØCTARIA au premier plan. Les billets et QR codes ne seront pas enregistrés.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Démarrer",(d,w)->start())
                    .show();
            }catch(Exception e) {host.toast("Impossible de lire cette liste d'événements.");}
        });
    }
    private void missingLinks() {
        NeonDialog.builder(host).setTitle("Aucune soirée détectée")
            .setMessage("Ouvre « Mes billets → Passés », puis assure-toi que les événements sont chargés. Si Shotgun ne présente pas de lien exploitable, ouvre chaque billet puis « Voir l'événement » pour l'importer individuellement.")
            .setPositiveButton("OK",null).show();
    }
    private void start() {
        active=true;rateLimited=false;current=0;attempts=0;hops=0;token++;
        succeeded=0;failed=0;inspected=false;
        status.accept("Import complet démarré : "+pending.size()+" soirée(s).");
        next();
    }
    private void next() {
        if(!active)return;
        if(current>=pending.size()) {
            finish("Import terminé");
            return;
        }
        JSONObject item=pending.get(current);
        currentUrl=item.optString("url");
        attempts=0;hops=0;inspected=false;
        status.accept("Récupération "+(current+1)+"/"+pending.size()+" : "+item.optString("title"));
        navigate(currentUrl);
    }
    private void navigate(String url) {
        if(!active)return;
        if(!valid(url)) {skip("Lien non valide");return;}
        int turn=++token;
        inspected=false;
        web.loadUrl(url);
        handler.postDelayed(()->{
            if(active && turn==token && !inspected) skip("La page ne répond pas");
        },23000);
    }
    void onPageFinished(String url) {
        if(!active)return;
        final int turn=token;
        handler.postDelayed(()->{
            if(active && turn==token && !inspected) inspectPage(turn);
        },1600);
    }
    void onHttp429() {
        if(!active)return;
        rateLimited=true;
        lastError="Shotgun a limité les requêtes (HTTP 429).";
        finish("Import arrêté : limitation Shotgun");
    }
    void onLoadFailure() {
        if(!active)return;
        skip("Page inaccessible");
    }
    private void inspectPage(int turn) {
        if(!active||turn!=token||inspected)return;
        String url=web.getUrl();
        if(url==null||!valid(url)){skip("Page hors Shotgun");return;}
        if(isEvent(url)) {
            inspected=true;
            web.evaluateJavascript(ShotgunBrowserActivity.CURRENT_EVENT_JS,result->{
                if(!active||turn!=token)return;
                try {
                    JSONObject details=new JSONObject(ShotgunBrowserActivity.decoded(result));
                    if(details.optString("title").isEmpty() ||
                       details.optString("title").equalsIgnoreCase("Shotgun")) throw new Exception("Titre manquant");
                    boolean hasDetails=false;
                    for(String key:new String[]{"poster","description","date","start","venue","city","artists","organizer"}) {
                        if(!details.optString(key).trim().isEmpty()){hasDetails=true;break;}
                    }
                    if(!hasDetails) {
                        if(attempts<2) {
                            attempts++;
                            inspected=false;
                            handler.postDelayed(()->{
                                if(active && turn==token)inspectPage(turn);
                            },1800);
                        } else skip("Shotgun ne fournit pas les détails de cet événement");
                        return;
                    }
                    // L'URL enregistrée doit être celle de l'événement, jamais du billet.
                    details.put("url",stripTrackers(url));
                    details.put("origin","Shotgun (détails complets)");
                    JSONObject old=pending.get(current);
                    if(details.optString("poster").isEmpty() && old.has("poster"))
                        details.put("poster",old.optString("poster"));
                    if(details.optString("date").isEmpty() && old.has("date"))
                        details.put("date",old.optString("date"));
                    collected.put(details);succeeded++;
                    advance();
                }catch(Exception e){skip("Détails indisponibles");}
            });
            return;
        }
        if(hops>=3){skip("Lien de l'événement introuvable");return;}
        inspected=true;
        web.evaluateJavascript(FOLLOW_EVENT_JS,result->{
            if(!active||turn!=token)return;
            try{
                JSONObject action=new JSONObject(ShotgunBrowserActivity.decoded(result));
                String kind=action.optString("kind"),link=action.optString("url");
                if("link".equals(kind) && isEvent(link)){
                    hops++;navigate(link);
                    return;
                }
                if("clicked".equals(kind) && hops<3){
                    hops++; inspected=false;
                    final int currentTurn=token;
                    handler.postDelayed(()->{
                        if(!active||currentTurn!=token)return;
                        String after=web.getUrl();
                        if(after!=null&&isEvent(after))inspectPage(currentTurn);
                        else inspectPage(currentTurn);
                    },1300);
                    return;
                }
                skip("Bouton « Voir l'événement » introuvable");
            }catch(Exception e){skip("Navigation Shotgun non reconnue");}
        });
    }
    private void advance() {
        if(!active)return;
        current++;
        token++;
        // Une navigation unique à la fois ; une courte pause entre deux fiches.
        handler.postDelayed(this::next,1700);
    }
    private void skip(String reason) {
        if(!active)return;
        failed++;
        lastError=reason;
        advance();
    }
    void askCancel() {
        if(!active)return;
        NeonDialog.builder(host).setTitle("Arrêter l'import ?")
            .setMessage(succeeded+" fiche(s) déjà récupérée(s) seront conservées. Les autres resteront à compléter.")
            .setNegativeButton("Continuer",null)
            .setPositiveButton("Arrêter",(d,w)->finish("Import interrompu"))
            .show();
    }
    private void finish(String title) {
        if(!active)return;
        active=false;token++;
        final int done=succeeded,errors=failed;
        status.accept(title+" · "+done+" / "+pending.size()+" fiches récupérées");
        String msg=done+" fiche(s) récupérée(s) (selon les informations disponibles), "+errors+" non récupérée(s)."
            +(rateLimited?"\n\nShotgun a répondu HTTP 429 : aucune nouvelle tentative automatique ne sera faite.":"")
            +(lastError.isEmpty()?"":"\nDernier problème : "+lastError)
            +"\n\nLes données privées des billets et QR codes ne sont pas enregistrées.";
        NeonDialog.builder(host).setTitle(title).setMessage(msg)
            .setNegativeButton("Rester sur Shotgun",(d,w)->{})
            .setPositiveButton(done>0?"Enregistrer les fiches":"OK",(d,w)->{
                if(done>0)completedCallback.accept(collected);
            }).show();
    }
    void cancelSilently() {
        active=false;token++;
    }
    private static boolean valid(String value) {
        if(value==null||value.isEmpty())return false;
        try {return ShotgunBrowserActivity.trusted(Uri.parse(value));}
        catch(Exception e){return false;}
    }
    private static boolean isEvent(String value){
        if(!valid(value))return false;
        String path=Uri.parse(value).getPath();
        return path!=null && path.matches("^/(?:[a-z]{2}/)?(?:events|festivals)/[^/]+/?$");
    }
    private static String stripTrackers(String link) {
        try {
            return Uri.parse(link).buildUpon().clearQuery().fragment(null).build().toString();
        }catch(Exception e){return link;}
    }

    // Ne découvre que des liens. Ne lit PAS les numéros, QR codes ni corps de billets.
    // Les URLs de billets ne sont conservées qu'en mémoire de la session d'import.
    private static final String DISCOVER_JS = """
        (function(){
          const seen=new Set(),records=[];
          const cards=[...document.querySelectorAll('a[href]')];
          for(const a of cards){
            if(records.length>=75)break;
            try{
              const u=new URL(a.href,location.href);
              if(u.protocol!=='https:' || !/(^|\\.)shotgun\\.live$/i.test(u.hostname))continue;
              const event=/^\\/(?:[a-z]{2}\\/)?(?:events|festivals)\\/[^/]+\\/?$/.test(u.pathname);
              const ticket=/^\\/(?:[a-z]{2}\\/)?(?:my-tickets|tickets|ticket)\\/[^/]+\\/?$/.test(u.pathname);
              if(!event && !ticket)continue;
              if(seen.has(u.pathname))continue;
              // Ne collecter que les cartes avec un titre d'événement lisible.
              const card=a.closest('article,[class*="card"],[class*="Card"],li')||a;
              const text=(a.querySelector('h1,h2,h3,h4')?.textContent||
                card.querySelector('h2,h3,h4')?.textContent||
                a.querySelector('img')?.alt || a.textContent||'').trim().replace(/\\s+/g,' ');
              if(text.length<3)continue;
              const img=card.querySelector('img')||a.querySelector('img');
              const poster=img?.currentSrc||img?.src||'';
              const entry={title:text.substring(0,180),url:u.toString()};
              if(/^https:\\/\\//.test(poster))entry.poster=poster;
              const date=card.querySelector('time[datetime]')?.getAttribute('datetime')||'';
              if(/^\\d{4}-\\d\\d-\\d\\d/.test(date))entry.date=date.slice(0,10);
              records.push(entry);seen.add(u.pathname);
            }catch(_){}
          }
          return JSON.stringify(records);
        })();
        """;

    // Sur une fiche de billet : recherche un lien explicite « Voir l'événement ».
    // S'il est caché derrière « Plus », ouvre ce menu. Aucun paiement/commande n'est déclenché.
    static final String FOLLOW_EVENT_JS = """
        (function(){
          const eventPath=/^\\/(?:[a-z]{2}\\/)?(?:events|festivals)\\/[^/]+\\/?$/;
          const link=(a)=>{
            try{
              const u=new URL(a.href,location.href);
              return u.protocol==='https:' && /(^|\\.)shotgun\\.live$/i.test(u.hostname)
                && eventPath.test(u.pathname) ? u.href : '';
            }catch(_){return ''}
          };
          const all=[...document.querySelectorAll('a[href]')];
          const direct=all.find(a=>link(a) && /voir l['’]?événement|view event/i.test(a.textContent||''))
               || all.find(a=>link(a));
          if(direct)return JSON.stringify({kind:'link',url:link(direct)});
          const labeled=[...document.querySelectorAll('button,[role=button],a')].filter(
            e=>/^(voir l['’]?événement|view event)$/i.test((e.textContent||'').trim()));
          if(labeled.length){
            labeled[0].click();
            return JSON.stringify({kind:'clicked'});
          }
          const more=[...document.querySelectorAll('button,[role=button]')].find(e=>
            /^(plus|more)$/i.test((e.textContent||e.getAttribute('aria-label')||'').trim()));
          if(more){more.click();return JSON.stringify({kind:'clicked'});}
          return JSON.stringify({kind:'missing'});
        })();
        """;
}
