package fr.afterlog.app;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.function.Consumer;

/**
 * Découverte de billets dans le WebView VISIBLE et déjà connecté de l'utilisateur.
 * Évite le WebView sans fenêtre du service Android pour la navigation initiale.
 * Ne lit ni QR codes, cookies, formulaires, mots de passe, ni données de paiement.
 */
final class ShotgunTicketDiscovery {
    private final ShotgunBrowserActivity activity;
    private final WebView web;
    private final TextView status;
    private final Consumer<JSONArray> foundCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active, busy, stopped, accountTried;
    private int tries, lastNavigation;
    private long lastActionTime;
    static final String ACCOUNT="https://shotgun.live/fr";

    ShotgunTicketDiscovery(ShotgunBrowserActivity activity,WebView web,TextView status,
                           Consumer<JSONArray> foundCallback) {
        this.activity=activity;this.web=web;this.status=status;this.foundCallback=foundCallback;
    }
    void begin(){
        active=true;busy=false;stopped=false;tries=0;lastNavigation=0;
        status.setText("Recherche des billets dans ta session Shotgun…");
        schedule(1300);
    }
    void stop(){active=false;stopped=true;handler.removeCallbacksAndMessages(null);}
    void pageFinished(){if(active){busy=false;schedule(1100);}}
    void rateLimited(){
        if(!active)return;
        active=false;stopped=true;
        status.setText("Shotgun limite les demandes (HTTP 429). Ne relance pas l'import pour l'instant.");
    }
    void tryAgain(){
        accountTried=false;
        begin();
    }
    private void schedule(int millis){
        if(!active||stopped||activity.isFinishing())return;
        handler.postDelayed(this::inspect,millis);
    }
    private void inspect(){
        if(!active||stopped||activity.isFinishing()||busy)return;
        final String url=web.getUrl();
        if(url==null||!ShotgunBrowserActivity.trusted(Uri.parse(url)))return;
        if(web.getProgress()<90){schedule(1400);return;}
        if(++tries>35) {
            active=false;
            status.setText("Recherche automatique interrompue. Ouvre « Mes billets → Passés » dans Shotgun, puis appuie sur « Lire mes billets ».");
            return;
        }
        busy=true;
        web.evaluateJavascript(SCAN_JS,raw->{
            busy=false;
            if(!active||stopped||activity.isFinishing())return;
            try {
                JSONObject state=new JSONObject(ShotgunBrowserActivity.decoded(raw));
                String action=state.optString("action");
                if("login".equals(action)) {
                    // La connexion appartient à Shotgun. Ne pas déclencher de navigation.
                    status.setText("Connecte-toi sur Shotgun. Après connexion, AfterLog cherchera tes billets.");
                    schedule(1700);
                    return;
                }
                if("ready".equals(action)) {
                    JSONArray tickets=state.optJSONArray("events");
                    if(tickets!=null && tickets.length()>0) {
                        active=false;
                        foundCallback.accept(tickets);
                        return;
                    }
                }
                if("account".equals(action)&&!accountTried){
                    accountTried=true;
                    status.setText("Ouverture de ton compte Shotgun…");
                    web.loadUrl(ACCOUNT);
                    schedule(1800);
                    return;
                }
                if("link".equals(action)){
                    String link=state.optString("url");
                    if(ShotgunBrowserActivity.trusted(Uri.parse(link))) {
                        status.setText("Ouverture de « Mes billets »…");
                        lastNavigation++;tries=0;
                        web.loadUrl(link);
                        schedule(1800);
                        return;
                    }
                }
                if("clicked".equals(action)){
                    String target=state.optString("target","billets");
                    status.setText(target.equals("past")?
                        "Affichage des billets passés…" : "Ouverture des billets…");
                    schedule(2300);return;
                }
                if("empty".equals(action)){
                    status.setText("Billets passés ouverts, mais aucun lien d'événement exploitable n'est visible. Vérifie la liste sur Shotgun.");
                    active=false;return;
                }
                status.setText("Recherche de « Mes billets → Passés »…");
                schedule(1700);
            }catch(Exception ex) {
                status.setText("La navigation Shotgun n'a pas répondu. Tu peux ouvrir Mes billets manuellement.");
                schedule(2000);
            }
        });
    }

    // Uniquement les liens et métadonnées visibles des cartes d'événements.
    // Les clics autorisés sont limités à l'ouverture de Mes billets et de l'onglet Passés.
    private static final String SCAN_JS = """
      (function(){
        const path=location.pathname.toLowerCase();
        if(/\\/login|\\/signin|\\/sign-in/.test(path))return JSON.stringify({action:'login'});
        const clean=s=>(s||'').replace(/\\s+/g,' ').trim();
        const text=e=>clean(e?.innerText||e?.textContent||e?.getAttribute('aria-label')||'');
        const buttons=[...document.querySelectorAll('a[href],button,[role="button"],[role="tab"]')];
        const trusted=url=>{
          try{
            const u=new URL(url,location.href);
            return u.protocol==='https:'&&/(^|\\.)shotgun\\.live$/i.test(u.hostname);
          }catch(_){return false}
        };
        const ticketArea=/(?:tickets?|billets?|orders?)/i.test(path) ||
          !!document.querySelector('[role="tablist"]') &&
            buttons.some(e=>/^(passés|past|à venir|upcoming)$/i.test(text(e)));
        const past=buttons.find(e=>/^(passés|passees|past|past events)$/i.test(text(e)));
        const selected=past && (past.getAttribute('aria-selected')==='true' ||
          past.getAttribute('data-state')==='active' ||
          past.getAttribute('aria-current')==='page' ||
          /(?:^|\\s)(?:active|selected)(?:\\s|$)/i.test(String(past.className)));
        const urlPast=/\\/past(?:\\/|$)|[?&](?:tab|status)=past/i.test(location.href);
        const accountHome=/^\\/(?:[a-z]{2}\\/)?account\\/?$/.test(path);
        // Il faut arriver dans Mes billets avant de toucher les autres événements du site.
        if(!ticketArea) {
          const entry=buttons.find(e=>/mes billets|my tickets/i.test(text(e)) ||
            (e.matches('a[href]')&&/(?:my-tickets|mes-billets|mytickets)/i.test(e.href)));
          if(entry) {
            if(entry.matches('a[href]')&&trusted(entry.href))
              return JSON.stringify({action:'link',url:entry.href});
            entry.click();return JSON.stringify({action:'clicked',target:'tickets'});
          }
          const profile=buttons.find(e=>/^(profil|profile|mon compte|account)$/i.test(text(e)) ||
            /profil|profile|account|avatar/i.test(e.getAttribute('aria-label')||'') ||
            /avatar|profile|account/i.test(e.getAttribute('data-testid')||''));
          if(profile && !window.__afterlog_profile_clicked) {
            window.__afterlog_profile_clicked=true;
            if(profile.matches('a[href]') && trusted(profile.href))
                return JSON.stringify({action:'link',url:profile.href});
            profile.click();
            return JSON.stringify({action:'clicked',target:'tickets'});
          }
          return JSON.stringify({action:'wait'});
        }
        if(past && !selected && !urlPast && !window.__afterlog_past_clicked){
          window.__afterlog_past_clicked=true;
          if(past.matches('a[href]') && trusted(past.href))
            return JSON.stringify({action:'link',url:past.href});
          past.click();
          return JSON.stringify({action:'clicked',target:'past'});
        }
        const eventPath=/^\\/(?:[a-z]{2}\\/)?(?:events|festivals)\\/[^/]+\\/?$/;
        const ticketPath=/\\/(?:my-tickets|tickets|ticket|orders)\\/[^/]+/i;
        const seen=new Set(),events=[];
        for(const anchor of document.querySelectorAll('a[href]')){
          if(events.length>=75)break;
          try{
            const u=new URL(anchor.href,location.href);
            if(!trusted(u.href)||(!eventPath.test(u.pathname)&&!ticketPath.test(u.pathname)))continue;
            if(seen.has(u.pathname))continue;
            const card=anchor.closest('article,li,[class*="card"],[class*="Card"]')||anchor;
            const image=card.querySelector('img')||anchor.querySelector('img');
            const title=clean(card.querySelector('h2,h3,h4')?.textContent||
              anchor.querySelector('h1,h2,h3,h4')?.textContent||
              image?.alt||anchor.textContent).slice(0,180);
            if(title.length<3)continue;
            const e={url:u.toString(),title};
            const poster=image?.currentSrc||image?.src||'';
            if(poster.startsWith('https://'))e.poster=poster;
            const date=card.querySelector('time[datetime]')?.getAttribute('datetime')||'';
            if(/^\\d{4}-\\d\\d-\\d\\d/.test(date))e.date=date.slice(0,10);
            events.push(e);seen.add(u.pathname);
          }catch(_){}
        }
        if(events.length && (selected||urlPast))
          return JSON.stringify({action:'ready',events});
        if(past && (selected||urlPast)) {
          return JSON.stringify({action:events.length?'ready':'empty',events});
        }
        // L'espace /account peut montrer les onglets ailleurs que dans un role tab.
        const link=buttons.find(e=>/mes billets|my tickets/i.test(text(e)));
        if(link){
          if(link.matches('a[href]')&&trusted(link.href))
            return JSON.stringify({action:'link',url:link.href});
          if(!window.__afterlog_tickets_clicked) {
            window.__afterlog_tickets_clicked=true;
            link.click();return JSON.stringify({action:'clicked',target:'tickets'});
          }
        }
        if(accountHome && !past) return JSON.stringify({action:'wait'});
        if(!ticketArea)return JSON.stringify({action:'wait'});
        return JSON.stringify({action:'wait'});
      })();
      """;
}
