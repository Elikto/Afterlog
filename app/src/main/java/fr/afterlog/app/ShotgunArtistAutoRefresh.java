package fr.afterlog.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import org.json.JSONObject;
import java.net.URL;
import java.util.function.Consumer;

/** Une seule tentative non intrusive de lecture du line-up PUBLIC d'un événement. */
final class ShotgunArtistAutoRefresh {
    private final Activity activity;
    private final WebView web;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Consumer<JSONObject> result;
    private boolean finished=false,processed=false;
    static boolean trusted(String url){
        try{
            URL u=new URL(url);
            return "https".equals(u.getProtocol()) &&
                ("shotgun.live".equals(u.getHost())||
                 "r.shotgun.live".equals(u.getHost())||
                 "www.shotgun.live".equals(u.getHost())) &&
                u.getPath().matches("^/(?:[a-z]{2}/)?(?:events|festivals)/[^/]+/?$");
        }catch(Exception ignored){return false;}
    }
    ShotgunArtistAutoRefresh(Activity activity,LinearLayout parent,
                             String url,Consumer<JSONObject> callback){
        this.activity=activity;this.result=callback;
        web=new WebView(activity);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        web.setAlpha(.02f);
        parent.addView(web,new LinearLayout.LayoutParams(2,2));
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String loaded){
                if(finished||processed ||!trusted(loaded))return;
                processed=true;
                handler.postDelayed(()->{
                    if(finished)return;
                    web.evaluateJavascript("(function(){"+
                       "let h=[...document.querySelectorAll('h2,h3,h4')].find(x=>"+
                       "/line[ -]?up/i.test(x.textContent||''));"+
                       "if(h)h.scrollIntoView({block:'center'});return true})()",ignored->{
                        handler.postDelayed(()->extract(),1700);
                    });
                },2300);
            }
        });
        // Aucune page de billet ni mot de passe : seulement la fiche événement.
        web.loadUrl(url);
        handler.postDelayed(this::stop,19000);
    }
    private void extract(){
        if(finished)return;
        web.evaluateJavascript(ShotgunBrowserActivity.CURRENT_EVENT_JS,raw->{
            if(finished)return;
            try{
                JSONObject event=new JSONObject(ShotgunBrowserActivity.decoded(raw));
                if(event.optJSONArray("artistProfiles")!=null &&
                    event.optJSONArray("artistProfiles").length()>0)
                    result.accept(event);
            }catch(Exception ignored){}
            stop();
        });
    }
    void stop(){
        if(finished)return;
        finished=true;
        handler.removeCallbacksAndMessages(null);
        web.stopLoading();
        if(web.getParent() instanceof LinearLayout)
            ((LinearLayout)web.getParent()).removeView(web);
        web.destroy();
    }
}
