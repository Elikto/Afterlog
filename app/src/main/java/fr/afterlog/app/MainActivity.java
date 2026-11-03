package fr.afterlog.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.widget.CheckBox;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(12, 14, 24);
    private static final int PANEL = Color.rgb(25, 28, 44);
    private static final int ACCENT = Color.rgb(186, 166, 255);
    private static final int SOFT = Color.rgb(182, 188, 205);
    private static final String[] CATEGORIES = {
        "Alcool", "Autres substances", "Sans alcool", "Médicaments", "Autre"
    };
    private AppStore store;
    private AppUpdater updater;
    private LinearLayout page;
    private long currentEventId = 0;
    private Runnable backAction;
    private boolean homeScreen=false;
    private boolean eventScreen=false;
    private long renderedEventRevision=-1L;
    private long displayedEventsRevision=-1L;
    private final Handler syncHandler=new Handler(Looper.getMainLooper());
    private LinearLayout syncPanel;
    private String syncLastSignature="";
    private boolean syncActiveView=false;
    private long observedSyncEnd=0;
    private final Runnable syncTicker=new Runnable(){
        @Override public void run(){
            if(!syncActiveView)return;
            if(homeScreen && displayedEventsRevision!=AppStore.revision(MainActivity.this)){
                // Nouveaux événements enregistrés par le service : rafraîchir la
                // liste immédiatement, sans relancer ni redémarrer AfterLog.
                showHome();
            } else if(eventScreen && currentEventId>0 &&
                renderedEventRevision!=AppStore.revision(MainActivity.this)){
                showEvent(currentEventId);
            } else {
                renderSyncProgress();
            }
            syncHandler.postDelayed(this,1500);
        }
    };
    private static final int EXPORT = 51, IMPORT = 52, SHOTGUN_REQUEST = 53, CONSUMPTION_REQUEST = 54,
        EVENT_EDITOR_REQUEST=55,EVENT_IMAGES_REQUEST=56,EVENT_VIDEOS_REQUEST=57,
        SHOTGUN_EVENT_REFRESH=58,EVENT_MEDIA_REQUEST=59;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new AppStore(this);
        updater = new AppUpdater(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showHome();
    }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(radius));
        return bg;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private void add(LinearLayout parent, View child, int marginBottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(marginBottom);
        parent.addView(child, lp);
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(dp(3), 1f);
        return t;
    }
    private TextView button(String label, boolean primary, Runnable click) {
        TextView b = text(label, 15, primary ? BG : Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(15), dp(18), dp(15));
        b.setBackground(rounded(primary ? ACCENT : PANEL, 14));
        b.setOnClickListener(v -> click.run());
        return b;
    }
    private LinearLayout card() {
        LinearLayout l = column();
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackground(rounded(PANEL,18));
        return l;
    }
    private void screen(String title, Runnable back) {
        homeScreen=back==null;
        eventScreen=false;
        backAction=back;
        LinearLayout outer = column(); outer.setBackgroundColor(BG);
        outer.setPadding(dp(16), dp(14), dp(16), 0);
        setContentView(outer);
        if(back==null){
            LinearLayout bar=new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(0,dp(2),0,dp(6));
            TextView update=text("↻ MAJ",12,ACCENT,true);
            update.setGravity(Gravity.CENTER);
            update.setPadding(dp(10),dp(12),dp(10),dp(12));
            update.setContentDescription("Rechercher une mise à jour");
            update.setOnClickListener(v->updater.check());
            bar.addView(update,new LinearLayout.LayoutParams(dp(64),dp(44)));
            TextView brand=text("AfterLog",26,Color.WHITE,true);
            brand.setGravity(Gravity.CENTER);
            bar.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
            TextView gear=text("⚙",28,Color.WHITE,true);
            gear.setGravity(Gravity.CENTER);
            gear.setContentDescription("Paramètres et sauvegarde");
            gear.setOnClickListener(v->showSettings());
            bar.addView(gear,new LinearLayout.LayoutParams(dp(55),dp(48)));
            add(outer,bar,6);
        }else{
            add(outer, button("← Retour", false, back), 12);
            add(outer, text(title, 24, Color.WHITE, true), 6);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        page = column();
        page.setPadding(0,dp(12),0,dp(26));
        scroll.addView(page);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
    }
    private void subtitle(String s) { add(page, text(s,14,SOFT,false),16); }
    private void line(LinearLayout where, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;
        add(where,text(label.toUpperCase(Locale.ROOT),11,ACCENT,true),4);
        add(where,text(value,15,Color.WHITE,false),13);
    }
    private void message(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private String dateNow() { return LocalDate.now().toString(); }
    private String timeNow() { return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")); }

    private void renderTicketSelection(LinearLayout panel) {
        org.json.JSONArray found=TicketSelectionStore.list(this);
        add(panel,text("🎟️  Tes billets passés",20,Color.WHITE,true),9);
        add(panel,text(found.length()+" événement(s) détecté(s) sur Shotgun.\n"
            +"Ils sont tous cochés par défaut. Décoche simplement ceux que tu veux ignorer.",14,SOFT,false),15);
        TextView selectionCount=text(TicketSelectionStore.selectedCount(this)+
            " / "+found.length()+" sélectionné(s)",14,ACCENT,true);
        add(panel,selectionCount,12);

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ctrlLayout=new LinearLayout.LayoutParams(-1,-2);
        ctrlLayout.bottomMargin=dp(17);
        panel.addView(controls,ctrlLayout);

        TextView selectAll=button("✓ Tout cocher",false,()->{
            TicketSelectionStore.markAll(this,true);
            syncLastSignature="";
            renderSyncProgress();
        });
        controls.addView(selectAll,new LinearLayout.LayoutParams(0,-2,1));
        TextView selectNone=button("Tout décocher",false,()->{
            TicketSelectionStore.markAll(this,false);
            syncLastSignature="";
            renderSyncProgress();
        });
        LinearLayout.LayoutParams noneLp=new LinearLayout.LayoutParams(0,-2,1);
        noneLp.leftMargin=dp(8);
        controls.addView(selectNone,noneLp);

        TextView launch=button("Synchroniser",true,()->{
            int n=TicketSelectionStore.selectedCount(this);
            if(n<1){
                message("Coche au moins une soirée à importer.");
                return;
            }
            try {
                AutoSyncScheduler.enable(this);
                ShotgunSyncService.importSelected(this);
                SyncState.save(this,SyncState.RUNNING,"Préparation de la synchronisation",
                    0,n,-1,"Téléchargement des informations des soirées sélectionnées");
                syncLastSignature="";
                renderSyncProgress();
            }catch(Exception ex) {
                message("Synchronisation impossible : "+ex.getMessage());
            }
        });

        for(int i=0;i<found.length();i++){
            JSONObject event=found.optJSONObject(i);
            if(event==null)continue;
            final int index=i;
            LinearLayout card=column();
            card.setPadding(dp(11),dp(12),dp(11),dp(12));
            card.setBackground(rounded(PANEL,15));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.bottomMargin=dp(10);
            panel.addView(card,cp);
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(row,new LinearLayout.LayoutParams(-1,-2));
            String poster=event.optString("poster");
            if(poster.startsWith("https://")){
                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(rounded(Color.rgb(45,44,66),8));
                LinearLayout.LayoutParams imgLp=new LinearLayout.LayoutParams(dp(56),dp(74));
                imgLp.rightMargin=dp(11);
                row.addView(image,imgLp);
                loadImage(image,poster);
            }
            LinearLayout info=column();
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView title=text(event.optString("title","Soirée"),15,Color.WHITE,true);
            title.setMaxLines(3);
            add(info,title,6);
            String date=event.optString("date");
            if(!date.isEmpty())add(info,text(prettyDate(date),12,SOFT,false),0);
            CheckBox checked=new CheckBox(this);
            checked.setButtonTintList(ColorStateList.valueOf(ACCENT));
            checked.setChecked(event.optBoolean("selected",true));
            checked.setContentDescription("Importer "+event.optString("title"));
            row.addView(checked,new LinearLayout.LayoutParams(dp(46),dp(52)));
            checked.setOnCheckedChangeListener((button,isChecked)->{
                TicketSelectionStore.mark(this,index,isChecked);
                int n=TicketSelectionStore.selectedCount(this);
                selectionCount.setText(n+" / "+found.length()+" sélectionné(s)");
                launch.setText("Synchroniser les "+n+" soirée(s)");
                launch.setAlpha(n>0?1f:0.55f);
            });
            row.setOnClickListener(v->checked.setChecked(!checked.isChecked()));
        }
        launch.setText("Synchroniser les "+TicketSelectionStore.selectedCount(this)+" soirée(s)");
        add(panel,launch,13);
        add(panel,text("La synchronisation continuera sur l'accueil. Les cases décochées ne seront pas importées lors de cette sélection.",12,SOFT,false),0);
    }

    private boolean allSelectedEventsPresent() {
        JSONArray wanted=TicketSelectionStore.chosen(this);
        if(wanted.length()==0)return false;
        List<JSONObject> saved=new AppStore(this).events();
        for(int i=0;i<wanted.length();i++){
            JSONObject expected=wanted.optJSONObject(i);
            if(expected==null)continue;
            String ticket=TicketSelectionStore.canonical(expected.optString("url"));
            String title=expected.optString("title").trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+"," ");
            String date=expected.optString("date");
            boolean present=false;
            for(JSONObject event:saved){
                String origin=TicketSelectionStore.canonical(event.optString("ticketUrl"));
                String eventUrl=TicketSelectionStore.canonical(event.optString("url"));
                if(!ticket.isEmpty() && (ticket.equals(origin)||ticket.equals(eventUrl))){
                    present=true;break;
                }
                String candidate=event.optString("title").trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[^\\p{L}\\p{N}]+"," ");
                if(!date.isEmpty()&&date.equals(event.optString("date")) &&
                    !title.isEmpty() && (candidate.equals(title) ||
                        candidate.startsWith(title+" ") || title.startsWith(candidate+" "))){
                    present=true;break;
                }
            }
            if(!present)return false;
        }
        return true;
    }
    private int visibleFailureCount() {
        return SyncFailureStore.actionableCount(this);
    }
    private void renderSyncProgress() {
        if(syncPanel==null)return;
        JSONObject state=SyncState.read(this);
        String mode=state.optString("state",SyncState.IDLE);
        // Seules les erreurs concernant les billets cochés sont pertinentes.
        // Ne pas réafficher un ancien compteur de "fiche non récupérée".
        if(SyncState.IDLE.equals(mode) ||
            (SyncState.DONE.equals(mode) &&
             (allSelectedEventsPresent() || visibleFailureCount()==0))) {
            syncPanel.removeAllViews();
            syncPanel.setVisibility(View.GONE);
            syncLastSignature="";
            return;
        }
        syncPanel.setVisibility(View.VISIBLE);
        String signature=mode+"|"+state.optString("phase")+"|"+state.optInt("done")+
            "|"+state.optInt("total")+"|"+state.optString("detail");
        if(signature.equals(syncLastSignature))return;
        syncLastSignature=signature;
        syncPanel.removeAllViews();
        LinearLayout panel=card();
        if(SyncState.SELECTION.equals(mode)) {
            renderTicketSelection(panel);
            add(syncPanel,panel,18);
            return;
        }
        if(SyncState.BLOCKED.equals(mode) || SyncState.ERROR.equals(mode) ||
            SyncState.CANCELED.equals(mode)) {
            add(panel,text("⚠  Import Shotgun à reprendre",17,Color.WHITE,true),9);
            add(panel,text(state.optString("phase"),14,SOFT,false),12);
            add(panel,button("Ouvrir mes billets sur Shotgun",false,this::connectShotgun),8);
            add(panel,button("Masquer cet avertissement",false,()->{
                SyncState.save(this,SyncState.IDLE,"",0,0,-1,"");
                syncLastSignature="";
                renderSyncProgress();
            }),0);
            renderFailedEvents(panel);
            add(syncPanel,panel,15);
            return;
        }
        String heading=SyncState.RUNNING.equals(mode)?"Synchronisation Shotgun":
            SyncState.DONE.equals(mode)?"Synchronisation terminée":
            SyncState.BLOCKED.equals(mode)?"Synchronisation à reprendre":
            "Synchronisation interrompue";
        add(panel,text("↻  "+heading,17,Color.WHITE,true),8);
        add(panel,text(state.optString("phase"),14,SOFT,false),9);
        int done=state.optInt("done"),total=state.optInt("total");
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(total>0?total:100);
        bar.setIndeterminate(total==0&&SyncState.RUNNING.equals(mode));
        bar.setProgress(Math.min(Math.max(0,done),bar.getMax()));
        add(panel,bar,7);
        String amount=total>0?done+" / "+total+" événements récupérés":
            "Recherche des billets passés…";
        add(panel,text(amount,13,ACCENT,true),5);
        long remaining=state.optLong("eta",-1);
        if(remaining>=0&&SyncState.RUNNING.equals(mode)&&total>0) {
            long min=remaining/60,sec=remaining%60;
            add(panel,text("Temps restant estimé : "+min+" min "+sec+" s",12,SOFT,false),7);
        } else if(SyncState.RUNNING.equals(mode)) {
            add(panel,text("Estimation disponible après les premières fiches.",12,SOFT,false),7);
        }
        if(!state.optString("detail").isEmpty())
            add(panel,text(state.optString("detail"),13,SOFT,false),12);
        android.content.SharedPreferences recent=getSharedPreferences("afterlog_auto_sync",0);
        long last=recent.getLong("lastCheckAt",0);
        if(last>0) {
            String when=new java.text.SimpleDateFormat("dd/MM · HH:mm",Locale.FRANCE)
                .format(new java.util.Date(last));
            add(panel,text("Dernière vérification : "+when,12,SOFT,false),7);
        }
        if(SyncState.RUNNING.equals(mode))
            add(panel,button("Arrêter la synchronisation",false,()->ShotgunSyncService.cancel(this)),0);
        else if(!SyncState.DONE.equals(mode))
            add(panel,button("Reconnecter Shotgun",false,this::connectShotgun),0);
        if(!SyncState.RUNNING.equals(mode) && !SyncState.SELECTION.equals(mode))
            renderFailedEvents(panel);
        add(syncPanel,panel,18);
        if(!SyncState.RUNNING.equals(mode)) {
            long stamp=state.optLong("updatedAt");
            if(stamp>0 && observedSyncEnd!=stamp) {
                observedSyncEnd=stamp;
                store=new AppStore(this);
                // Mettre à jour la liste des événements sans reconstruire la vue en boucle.
                if(total>0 && SyncState.DONE.equals(mode))
                    syncHandler.post(()->{
                        if(syncActiveView&&syncPanel!=null)showHome();
                    });
            }
        }
    }
    private void showHome() {
        store=new AppStore(this);
        displayedEventsRevision=AppStore.revision(this);
        screen("AfterLog",null);
        syncPanel=column();
        syncLastSignature="";
        add(page,syncPanel,8);
        renderSyncProgress();

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        add(page,actions,12);
        TextView addEvent=button("＋ Soirée",true,()->editEvent(new JSONObject()));
        actions.addView(addEvent,new LinearLayout.LayoutParams(0,-2,1));
        TextView sync=button("🎟 Shotgun",false,this::connectShotgun);
        LinearLayout.LayoutParams syncParams=new LinearLayout.LayoutParams(0,-2,1);
        syncParams.leftMargin=dp(8);
        actions.addView(sync,syncParams);

        LinearLayout shortcuts=new LinearLayout(this);
        shortcuts.setOrientation(LinearLayout.HORIZONTAL);
        add(page,shortcuts,18);
        TextView rdr=button("♡ RDR",false,()->startActivity(new Intent(this,RdrActivity.class)));
        shortcuts.addView(rdr,new LinearLayout.LayoutParams(0,-2,1));
        TextView stats=button("Suivi",false,()->
            startActivity(new Intent(this,SuiviActivity.class)));
        LinearLayout.LayoutParams statsParams=new LinearLayout.LayoutParams(0,-2,1);
        statsParams.leftMargin=dp(8);shortcuts.addView(stats,statsParams);

        List<JSONObject> events=store.events();
        add(page,text("MES SOIRÉES   ·   "+events.size(),15,ACCENT,true),11);
        if(events.isEmpty()){
            LinearLayout empty=card();
            add(empty,text("Aucune soirée pour l'instant",17,Color.WHITE,true),7);
            add(empty,text("Ajoute une soirée ou connecte Shotgun pour rechercher tes billets.",13,SOFT,false),0);
            add(page,empty,13);
        }
        for(JSONObject e:events) {
            LinearLayout card=card();
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            add(card,row,0);
            String url=e.optString("poster");
            if(!url.isEmpty()){
                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(rounded(Color.rgb(42,44,64),10));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(73),dp(92));
                lp.rightMargin=dp(12);row.addView(image,lp);loadImage(image,url);
            }
            LinearLayout details=column();
            row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            TextView title=text(e.optString("title","Événement"),17,Color.WHITE,true);
            title.setMaxLines(2);add(details,title,7);
            add(details,text(e.optString("date")+"  "+e.optString("start"),13,ACCENT,false),5);
            String venue=e.optString("venue");
            if(venue.isEmpty())venue=e.optString("city");
            if(!venue.isEmpty())add(details,text(venue,13,SOFT,false),5);
            add(details,text("Ouvrir  ›",12,SOFT,true),0);
            row.setOnClickListener(v->showEvent(e.optLong("id")));
            card.setOnClickListener(v->showEvent(e.optLong("id")));
            add(page,card,10);
        }
    }

    private void showEvent(long id) {
        // Une activité de saisie utilise une instance AppStore distincte.
        // Recharger depuis le disque avant de reconstruire le journal.
        store=new AppStore(this);
        JSONObject event=store.event(id);
        if(event==null){showHome();return;}
        currentEventId=id;
        screen("Événement",this::showHome);
        eventScreen=true;
        renderedEventRevision=AppStore.revision(this);
        renderJournalFirst(id);
        renderRdrAlerts(id);
        // Structure proche de la fiche Shotgun : grande affiche, titre, date et lieu,
        // à propos, programmation, ambiance musicale et localisation.
        String poster=event.optString("poster");
        if(!poster.isEmpty()) {
            ImageView image=new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(rounded(PANEL,18));
            image.setContentDescription("Affiche de "+event.optString("title"));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(265));
            lp.bottomMargin=dp(17);
            page.addView(image,lp);
            loadImage(image,poster);
        }
        add(page,text(event.optString("title","Soirée"),25,Color.WHITE,true),9);
        renderEventPhotos(event);
        if(!event.optString("organizer").isEmpty())
            add(page,text("Par "+event.optString("organizer"),14,SOFT,false),14);

        LinearLayout summary=card();
        String date=prettyDate(event.optString("date"));
        String startTime=event.optString("start");
        String endTime=event.optString("end");
        String timeLabel=startTime.isEmpty()?"":startTime+
            (endTime.isEmpty()?"":" → "+endTime+
                (!event.optString("endDate").isEmpty() && !event.optString("endDate").equals(event.optString("date"))?
                  " (lendemain)":""));
        line(summary,"📅 Date",date);
        line(summary,"🕒 Horaires",timeLabel);
        line(summary,"📍 Lieu",event.optString("venue"));
        line(summary,"Adresse",event.optString("address")+
            (event.optString("city").isEmpty()?"":" · "+event.optString("city")));
        add(page,summary,17);

        add(page,button("Modifier les informations",false,()->editEvent(event)),24);

        String description=event.optString("description");
        if(!description.isEmpty()) {
            add(page,text("À propos",21,Color.WHITE,true),11);
            LinearLayout about=card();
            add(about,text(description,15,Color.WHITE,false),0);
            add(page,about,23);
        }
        if(!event.optString("artists").isEmpty()) {
            add(page,text("Line-up",21,Color.WHITE,true),9);
            renderArtistProfiles(event);
        }
        if(!event.optString("genres").isEmpty()) {
            add(page,text("Vibe · Styles musicaux",21,Color.WHITE,true),9);
            addChips(event.optString("genres"),"♫");
        }
        if(!event.optString("venue").isEmpty()||!event.optString("address").isEmpty()) {
            add(page,text("Localisation",21,Color.WHITE,true),11);
            LinearLayout location=card();
            line(location,"📍 Lieu",event.optString("venue"));
            line(location,"Adresse",event.optString("address")+
                (event.optString("city").isEmpty()?"":", "+event.optString("city")));
            add(location,button("Ouvrir sur la carte",false,()->{
                String query=event.optString("address")+" "+event.optString("venue")+" "+event.optString("city");
                startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q="+Uri.encode(query))));
            }),0);
            add(page,location,22);
        }
        String link=event.optString("url");
        if(link.startsWith("https://"))add(page,button("Voir la fiche officielle sur Shotgun",false,()->
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)))),18);

        if(!event.optString("notes").isEmpty()) {
            add(page,text("Mes notes",19,Color.WHITE,true),9);
            LinearLayout personal=card();
            add(personal,text(event.optString("notes"),15,Color.WHITE,false),0);
            add(page,personal,12);
        }
        add(page,button("Supprimer cette soirée",false,()->new AlertDialog.Builder(this)
            .setTitle("Supprimer la soirée ?")
            .setMessage("Les consommations associées seront également supprimées.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Supprimer",(d,w)->{store.deleteEvent(id);showHome();})
            .show()),0);
    }
    private String primaryQuantity(JSONObject entry) {
        JSONObject parts=entry.optJSONObject("parts");
        if(parts!=null){
            ArrayList<String> fragments=new ArrayList<>();
            int whole=parts.optInt("entier"),half=parts.optInt("demi"),quarter=parts.optInt("quart");
            if(whole>0)fragments.add(whole+" entier"+(whole>1?"s":""));
            if(half>0)fragments.add(half+" demi");
            if(quarter>0)fragments.add(quarter+" quart"+(quarter>1?"s":""));
            if(!fragments.isEmpty())return android.text.TextUtils.join(" + ",fragments);
        }
        JSONArray portions=entry.optJSONArray("portions");
        if(portions!=null&&portions.length()>0){
            ArrayList<String> fragments=new ArrayList<>();
            for(int i=0;i<portions.length();i++){
                JSONObject p=portions.optJSONObject(i);
                if(p==null)continue;
                String size=p.optString("size");
                String route=p.optString("route");
                int count=p.optInt("count",1);
                String label=count+" "+(size.isEmpty()?"prise"+(count>1?"s":""):size.toLowerCase(Locale.FRANCE));
                if(!route.isEmpty())label+=" · "+route;
                String amount=p.optString("measuredAmount");
                if(!amount.isEmpty())label+=" ("+amount+" "+p.optString("measuredUnit")+")";
                fragments.add(label);
            }
            if(!fragments.isEmpty())return android.text.TextUtils.join(" + ",fragments);
        }
        String label=entry.optString("quantity","1")+" "+entry.optString("unit","prise");
        String route=entry.optString("route");
        if(!route.isEmpty())label+=" · "+("Autre".equals(route)?
            entry.optString("routeOther",route):route);
        String size=entry.optString("subjectiveSize");
        if(!size.isEmpty())label+=" · "+size.toLowerCase(Locale.FRANCE);
        String amount=entry.optString("measuredAmount");
        if(!amount.isEmpty())label+=" · "+amount+" "+entry.optString("measuredUnit");
        return label;
    }
    private String timeDescription(JSONObject entry) {
        String timing=entry.optString("timeMode",entry.optBoolean("allNight")?"all":"exact");
        if("all".equals(timing)||entry.optBoolean("allNight"))return "";
        if("between".equals(timing))
            return entry.optString("time")+" – "+entry.optString("timeEnd");
        return entry.optString("time");
    }
    private void renderJournalFirst(long id) {
        List<JSONObject> entries=store.entries(id);
        add(page,text("Mon journal de soirée",21,Color.WHITE,true),12);
        if(entries.isEmpty()){
            add(page,button("＋ Ajouter une consommation",true,
                ()->editEntry(new JSONObject(),id)),13);
        }else{
            // 2 colonnes, même taille pour toutes les bulles. Le + occupe
            // exactement la case qui suit la dernière prise.
            int total=entries.size()+1;
            for(int offset=0;offset<total;offset+=2){
                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                add(page,row,9);
                for(int slot=0;slot<2;slot++){
                    int position=offset+slot;
                    LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,dp(125),1f);
                    layout.setMargins(dp(3),0,dp(3),0);
                    if(position>=total) {
                        row.addView(new View(this),layout);continue;
                    }
                    if(position==entries.size()){
                        TextView plus=text("＋",35,BG,true);
                        plus.setGravity(Gravity.CENTER);
                        plus.setContentDescription("Ajouter une consommation");
                        plus.setBackground(rounded(ACCENT,27));
                        plus.setElevation(dp(2));
                        plus.setOnClickListener(v->editEntry(new JSONObject(),id));
                        row.addView(plus,layout);
                        continue;
                    }
                    JSONObject entry=entries.get(position);
                    LinearLayout bubble=column();
                    bubble.setPadding(dp(13),dp(14),dp(13),dp(10));
                    GradientDrawable bg=new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{Color.rgb(39,37,58),Color.rgb(28,29,44)});
                    bg.setCornerRadius(dp(27));
                    bg.setStroke(dp(1),Color.rgb(62,55,84));
                    bubble.setBackground(bg);
                    bubble.setElevation(dp(2));
                    row.addView(bubble,layout);
                    String icon=ConsumptionActivity.iconFor(
                        entry.optString("category"),entry.optString("name"));
                    TextView name=text(icon+" "+entry.optString("name","Consommation"),
                        15,Color.WHITE,true);
                    name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    add(bubble,name,6);
                    TextView summary=text(primaryQuantity(entry),12,ACCENT,true);
                    summary.setMaxLines(2);
                    summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    add(bubble,summary,4);
                    String when=timeDescription(entry);
                    if(!when.isEmpty()){
                        TextView time=text("◷ "+when,11,SOFT,false);
                        time.setMaxLines(1);
                        time.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        add(bubble,time,0);
                    }
                    // Consulter reste possible en cliquant sur toute la bulle,
                    // sans texte supplémentaire qui change la hauteur.
                    bubble.setContentDescription("Voir la prise "+entry.optString("name")+
                        " : "+primaryQuantity(entry)+(when.isEmpty()?"":", "+when));
                    bubble.setOnClickListener(v->showEntryDetails(entry,id));
                }
            }
        }
        add(page,text("Informations de l'événement",21,Color.WHITE,true),13);
    }
    private void renderRdrAlerts(long eventId){
        List<RiskGuidance.Warning> risks=RiskGuidance.forEvent(store.entries(eventId));
        if(risks.isEmpty())return;
        LinearLayout panel=column();
        panel.setPadding(dp(14),dp(13),dp(14),dp(13));
        GradientDrawable background=new GradientDrawable();
        background.setColor(Color.rgb(37,32,46));
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1),Color.rgb(104,78,83));
        panel.setBackground(background);
        add(page,panel,12);
        add(panel,text("⚠ Prévention · "+risks.size()+" point(s) de vigilance",
            15,Color.rgb(244,191,151),true),8);
        for(int i=0;i<Math.min(2,risks.size());i++) {
            RiskGuidance.Warning warning=risks.get(i);
            add(panel,text("• "+warning.title,13,Color.WHITE,true),5);
            add(panel,text(warning.detail,12,SOFT,false),9);
        }
        add(panel,text("Ces alertes reposent sur les informations saisies, pas sur une analyse du produit ou un diagnostic.",
            11,SOFT,false),9);
        add(panel,button("Voir le suivi, les sources et les contacts d'aide",false,()->
            startActivity(new Intent(this,SuiviActivity.class))),0);
    }

    private void showEntryDetails(JSONObject entry,long eventId){
        LinearLayout content=column();
        content.setPadding(dp(18),dp(13),dp(18),dp(6));
        add(content,text(ConsumptionActivity.iconFor(entry.optString("category"),
            entry.optString("name"))+"  "+entry.optString("name"),
            20,Color.WHITE,true),13);
        add(content,text(primaryQuantity(entry),16,ACCENT,true),12);
        add(content,text("Date : "+entry.optString("date"),14,SOFT,false),9);
        String time=timeDescription(entry);
        if(!time.isEmpty())add(content,text("Heure : "+time,14,SOFT,false),8);
        String route=entry.optString("route");
        if(!route.isEmpty() && (entry.optJSONArray("portions")==null ||
            entry.optJSONArray("portions").length()==0))
            add(content,text("Mode : "+("Autre".equals(route)?
                entry.optString("routeOther"):route),14,SOFT,false),8);
        if(entry.optDouble("cost",0)>0)
            add(content,text(String.format(Locale.FRANCE,"Prix : %.2f €",
                entry.optDouble("cost")),14,SOFT,false),8);
        if(!entry.optString("notes").isEmpty())
            add(content,text("Notes : "+entry.optString("notes"),14,SOFT,false),8);
        AlertDialog popup=new AlertDialog.Builder(this)
            .setTitle("Détails de la prise")
            .setView(content)
            .setPositiveButton("Modifier",(d,w)->editEntry(entry,eventId))
            .setNeutralButton("Supprimer",(d,w)->new AlertDialog.Builder(this)
                .setTitle("Supprimer cette prise ?")
                .setMessage("Cette suppression est définitive.")
                .setNegativeButton("Annuler",null)
                .setPositiveButton("Supprimer",(a,b)->{
                    store.deleteEntry(entry.optLong("id"));
                    showEvent(eventId);
                }).show())
            .setNegativeButton("Fermer",null)
            .create();
        popup.show();
    }

    private void renderArtistProfiles(JSONObject event) {
        JSONArray profiles=event.optJSONArray("artistProfiles");
        if(profiles==null)profiles=new JSONArray();
        // Même sans métadonnée/photo structurée, garder tous les artistes du line-up.
        java.util.HashSet<String> names=new java.util.HashSet<>();
        JSONArray ordered=new JSONArray();
        for(int i=0;i<profiles.length();i++){
            JSONObject p=profiles.optJSONObject(i);
            if(p==null||p.optString("name").trim().isEmpty())continue;
            String key=p.optString("name").trim().toLowerCase(Locale.ROOT);
            if(names.add(key))ordered.put(p);
        }
        for(String artist:event.optString("artists").split(",")){
            String name=artist.trim();
            if(name.isEmpty() || !names.add(name.toLowerCase(Locale.ROOT)))continue;
            JSONObject profile=new JSONObject();
            try{profile.put("name",name);}catch(Exception ignored){}
            ordered.put(profile);
        }
        if(ordered.length()==0)return;
        for(int index=0;index<ordered.length();index+=3) {
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            add(page,row,14);
            for(int slot=0;slot<3;slot++){
                int i=index+slot;
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
                lp.setMargins(dp(4),0,dp(4),0);
                if(i>=ordered.length()){
                    row.addView(new View(this),lp);
                    continue;
                }
                JSONObject artist=ordered.optJSONObject(i);
                if(artist==null)continue;
                String name=artist.optString("name").trim();
                String photo=artist.optString("photo");
                String candidate=artist.optString("spotify");
                String search="https://open.spotify.com/search/"+Uri.encode(name);
                final String[] target={search};
                LinearLayout tile=column();
                tile.setPadding(dp(7),dp(12),dp(7),dp(12));
                tile.setGravity(Gravity.CENTER_HORIZONTAL);
                tile.setBackground(rounded(PANEL,16));
                row.addView(tile,lp);

                ImageView portrait=new ImageView(this);
                portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
                android.graphics.drawable.GradientDrawable circle=
                    new android.graphics.drawable.GradientDrawable();
                circle.setColor(Color.rgb(48,43,72));
                circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                portrait.setBackground(circle);
                portrait.setClipToOutline(true);
                portrait.setContentDescription("Photo de "+name);
                LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(dp(85),dp(85));
                imageLp.gravity=Gravity.CENTER_HORIZONTAL;
                imageLp.bottomMargin=dp(10);
                tile.addView(portrait,imageLp);
                if(photo.startsWith("https://"))loadImage(portrait,photo);
                else portrait.setImageResource(android.R.drawable.ic_menu_myplaces);
                TextView label=text(name,13,Color.WHITE,true);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(2);
                add(tile,label,7);
                SpotifyMarkView spotifyMark=new SpotifyMarkView(this);
                spotifyMark.setVerified(false);
                spotifyMark.setContentDescription("Spotify : recherche de "+name);
                LinearLayout.LayoutParams badgeLp=new LinearLayout.LayoutParams(dp(28),dp(28));
                badgeLp.gravity=Gravity.CENTER_HORIZONTAL;
                badgeLp.bottomMargin=dp(5);
                tile.addView(spotifyMark,badgeLp);
                TextView destination=text("Recherche Spotify",11,ACCENT,false);
                destination.setGravity(Gravity.CENTER);
                destination.setMaxLines(2);
                add(tile,destination,0);
                tile.setContentDescription(name+" · Recherche Spotify");
                tile.setOnClickListener(v->{
                    try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(target[0])));}
                    catch(Exception e){message("Impossible d'ouvrir Spotify.");}
                });
                // Seul un lien artiste explicitement publié par Shotgun peut être
                // validé. Vérifier nom officiel ET correspondance des portraits.
                if(photo.startsWith("https://")){
                    destination.setText("Recherche du profil…");
                    new Thread(()->{
                        String verifiedLink=SpotifyArtistFinder.resolve(
                            getApplicationContext(),name,photo,candidate);
                        runOnUiThread(()->{
                            if(isFinishing())return;
                            boolean matched=SpotifyArtistVerifier.isArtistUrl(verifiedLink);
                            if(matched) {
                                target[0]=verifiedLink;
                                spotifyMark.setVerified(true);
                                spotifyMark.setContentDescription("Spotify vérifié : "+name);
                                destination.setText("Profil Spotify");
                                destination.setTextColor(Color.rgb(30,215,96));
                                tile.setContentDescription(name+" · Profil Spotify vérifié");
                            } else {
                                destination.setText("Recherche Spotify");
                            }
                        });
                    },"AfterLog-spotify-check").start();
                }
            }
        }
        String eventUrl=event.optString("url");
        if(eventUrl.startsWith("https://shotgun.live/")){
            add(page,button("↻ Actualiser les portraits depuis Shotgun",false,()->{
                Intent browser=new Intent(this,ShotgunBrowserActivity.class);
                browser.putExtra(ShotgunBrowserActivity.EXTRA_URL,eventUrl);
                browser.putExtra("afterlog_auto_extract",true);
                startActivityForResult(browser,SHOTGUN_EVENT_REFRESH);
            }),13);
        }
        add(page,text("Icône verte : profil Spotify vérifié avec le nom et la photo. Icône violette : recherche Spotify (correspondance non confirmée).",
            12,SOFT,false),12);
    }

    private void renderFailedEvents(LinearLayout panel) {
        JSONArray failures=SyncFailureStore.actionable(this);
        if(failures.length()==0)return;
        add(panel,text("Soirées non récupérées · "+failures.length(),18,Color.WHITE,true),8);
        add(panel,text("Voici les soirées que Shotgun n'a pas permis de compléter.",13,SOFT,false),12);
        for(int i=0;i<failures.length();i++){
            JSONObject item=failures.optJSONObject(i);
            if(item==null || TicketSelectionStore.isExcluded(this,item.optString("url")))continue;
            LinearLayout c=card();
            add(c,text(item.optString("title","Événement"),16,Color.WHITE,true),6);
            if(!item.optString("date").isEmpty())
                add(c,text(item.optString("date"),13,SOFT,false),5);
            add(c,text("Échec : "+item.optString("reason","Informations indisponibles"),13,SOFT,false),9);
            String url=item.optString("url");
            add(c,button("Réessayer cette soirée",false,()->{
                try{
                    ShotgunSyncService.retryFailed(this,url);
                    SyncState.save(this,SyncState.RUNNING,"Nouvelle tentative",
                        0,1,-1,item.optString("title"));
                    syncLastSignature="";
                    renderSyncProgress();
                }catch(Exception ex){message("Impossible de relancer : "+ex.getMessage());}
            }),8);
            add(c,button("Ajouter manuellement",false,()->{
                JSONObject draft=new JSONObject();
                try{
                    draft.put("title",item.optString("title"));
                    draft.put("date",item.optString("date"));
                    draft.put("poster",item.optString("poster"));
                    draft.put("url",item.optString("url"));
                    draft.put("origin","Shotgun (manuel)");
                }catch(Exception ignored){}
                editEvent(draft);
            }),8);
            if(url.startsWith("https://"))add(c,button("Ouvrir sur Shotgun",false,()->
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)))),0);
            add(panel,c,10);
        }
    }

    private void startGallery(long id,int mediaIndex){
        Intent intent=new Intent(this,EventGalleryActivity.class);
        intent.putExtra(EventGalleryActivity.EXTRA_EVENT_ID,id);
        intent.putExtra(EventGalleryActivity.EXTRA_INDEX,mediaIndex);
        startActivity(intent);
    }
    private void pickMedia(long id){
        currentEventId=id;
        Intent chooser=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        chooser.addCategory(Intent.CATEGORY_OPENABLE);
        chooser.setType("*/*");
        chooser.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
        chooser.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(chooser,"Photos et vidéos"),EVENT_MEDIA_REQUEST);
    }
    private void renderEventPhotos(JSONObject event) {
        long eventId=event.optLong("id");
        JSONArray photos=EventPhotos.gallery(event);
        JSONArray videos=EventVideos.list(event);
        int total=photos.length()+videos.length();
        LinearLayout galleryHeader=new LinearLayout(this);
        galleryHeader.setOrientation(LinearLayout.HORIZONTAL);
        galleryHeader.setGravity(Gravity.CENTER_VERTICAL);
        add(page,galleryHeader,12);
        TextView title=text("Ma galerie"+(total>0?" · "+total:""),20,Color.WHITE,true);
        galleryHeader.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        TextView addMedia=button("＋ Médias",false,()->pickMedia(eventId));
        addMedia.setContentDescription("Ajouter des photos ou des vidéos");
        galleryHeader.addView(addMedia,new LinearLayout.LayoutParams(dp(136),dp(51)));
        if(total==0){
            add(page,text("Ajoute ici les photos et vidéos de ta soirée.",13,SOFT,false),12);
            return;
        }
        for(int offset=0;offset<total;offset+=3){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            add(page,row,8);
            for(int slot=0;slot<3;slot++){
                int index=offset+slot;
                LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(0,dp(108),1f);
                layout.setMargins(dp(3),0,dp(3),0);
                if(index>=total){row.addView(new View(this),layout);continue;}
                boolean video=index>=photos.length();
                String filename=video?videos.optString(index-photos.length()):photos.optString(index);
                FrameLayout thumb=new FrameLayout(this);
                thumb.setBackground(rounded(Color.rgb(35,34,54),15));
                thumb.setClipToOutline(true);
                row.addView(thumb,layout);
                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap picture=video?
                    EventVideos.thumbnail(this,filename):EventPhotos.preview(this,filename,360);
                if(picture!=null)image.setImageBitmap(picture);
                thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));
                if(video){
                    TextView play=text("▶",26,Color.WHITE,true);
                    play.setGravity(Gravity.CENTER);
                    GradientDrawable circle=new GradientDrawable();
                    circle.setColor(Color.argb(204,26,24,42));
                    circle.setShape(GradientDrawable.OVAL);
                    play.setBackground(circle);
                    FrameLayout.LayoutParams playLayout=new FrameLayout.LayoutParams(
                        dp(48),dp(48),Gravity.CENTER);
                    thumb.addView(play,playLayout);
                }
                thumb.setContentDescription((video?"Ouvrir la vidéo ":"Ouvrir la photo ")+(index+1));
                thumb.setOnClickListener(v->startGallery(eventId,index));
            }
        }
        add(page,text("Touche un média pour ouvrir la galerie plein écran.",12,SOFT,false),15);
    }
    private String prettyDate(String date) {
        try {
            return java.time.LocalDate.parse(date)
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy",Locale.FRANCE));
        }catch(Exception ex){return date;}
    }
    private void addChips(String value,String symbol) {
        String[] parts=value.split(",");
        LinearLayout row=null;
        int count=0;
        for(String part:parts) {
            String word=part.trim();
            if(word.isEmpty())continue;
            if(count%2==0) {
                row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                add(page,row,9);
            }
            TextView chip=text(symbol+"  "+word,13,Color.WHITE,true);
            chip.setPadding(dp(11),dp(13),dp(11),dp(13));
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(rounded(PANEL,14));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            lp.setMargins(dp(3),0,dp(3),0);
            row.addView(chip,lp);
            count++;
        }
        if(row!=null && count%2==1)row.addView(new View(this),
            new LinearLayout.LayoutParams(0,dp(1),1));
        add(page,new View(this),13);
    }

    private EditText field(LinearLayout holder,String label,String value,boolean multiline) {
        add(holder,text(label,12,SOFT,true),5);
        EditText input=new EditText(this);
        input.setSingleLine(!multiline);
        if(multiline) {input.setMinLines(3);input.setGravity(Gravity.TOP);}
        input.setText(value);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(SOFT);
        input.setTextSize(15);
        input.setPadding(dp(12),dp(12),dp(12),dp(12));
        input.setBackground(rounded(PANEL,10));
        add(holder,input,12);
        return input;
    }
    private void editEvent(JSONObject original) {
        Intent intent=new Intent(this,EventEditorActivity.class);
        intent.putExtra(EventEditorActivity.EXTRA_EVENT,original.toString());
        startActivityForResult(intent,EVENT_EDITOR_REQUEST);
    }

    private void editEntry(JSONObject original,long eventId) {
        Intent intent=new Intent(this,ConsumptionActivity.class);
        intent.putExtra(ConsumptionActivity.EXTRA_EVENT_ID,eventId);
        intent.putExtra(ConsumptionActivity.EXTRA_ENTRY_ID,original.optLong("id"));
        startActivityForResult(intent,CONSUMPTION_REQUEST);
    }

    private void showStats() {
        screen("Statistiques",this::showHome);
        List<JSONObject> all=store.allEntries();
        List<JSONObject> events=store.events();
        double cost=0;
        LinkedHashMap<String,Integer> count=new LinkedHashMap<>();
        for(JSONObject e:all) {
            cost+=e.optDouble("cost");
            String c=e.optString("category","Autre");
            count.put(c,count.containsKey(c)?count.get(c)+1:1);
        }
        LinearLayout info=card();
        line(info,"Soirées",String.valueOf(events.size()));
        line(info,"Consommations consignées",String.valueOf(all.size()));
        line(info,"Total dépensé",String.format(Locale.FRANCE,"%.2f €",cost));
        add(page,info,16);
        add(page,text("Par catégorie",18,ACCENT,true),12);
        for(Map.Entry<String,Integer> entry:count.entrySet()) {
            LinearLayout c=card();
            add(c,text(entry.getKey()+"  ·  "+entry.getValue()+" entrée(s)",16,Color.WHITE,true),0);
            add(page,c,10);
        }
        subtitle("Les catégories utilisent des unités différentes : leurs quantités ne sont pas additionnées.");
    }

    private void connectShotgun() {
        Intent browser = new Intent(this, ShotgunBrowserActivity.class);
        browser.putExtra("afterlog_auto_sync",true);
        startActivityForResult(browser, SHOTGUN_REQUEST);
    }
    private void importShotgun() {
        EditText input=new EditText(this);
        input.setSingleLine(false);
        input.setTextColor(BG);
        input.setHint("https://shotgun.live/...");
        input.setPadding(dp(16),dp(10),dp(16),dp(10));
        new AlertDialog.Builder(this)
            .setTitle("Importer via le site Shotgun")
            .setMessage("L'ancien téléchargement direct est supprimé (erreur HTTP 429). Le lien s'ouvre maintenant dans le navigateur Shotgun intégré.")
            .setView(input)
            .setNegativeButton("Annuler",null)
            .setNeutralButton("Saisie manuelle",(d,w)->editEvent(new JSONObject()))
            .setPositiveButton("Ouvrir sur Shotgun",(d,w)->{
                String url=input.getText().toString().trim();
                try {
                    Uri uri=Uri.parse(url);
                    String host=uri.getHost();
                    if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||
                        !(host.equalsIgnoreCase("shotgun.live") ||
                          host.equalsIgnoreCase("www.shotgun.live"))) {
                        message("Entre un lien HTTPS shotgun.live");return;
                    }
                    Intent browser=new Intent(this,ShotgunBrowserActivity.class);
                    browser.putExtra(ShotgunBrowserActivity.EXTRA_URL,url);
                    startActivityForResult(browser,SHOTGUN_REQUEST);
                }catch(Exception ex){message("Lien Shotgun invalide");}
            }).show();
    }
    private void receiveShotgunEvents(Intent data) {
        try {
            JSONArray records=new JSONArray(data.getStringExtra(ShotgunBrowserActivity.EXTRA_EVENTS));
            if(records.length()==0){message("Aucun événement récupéré");return;}
            int added=0,updated=0,unchanged=0,ignored=0;
            for(int i=0;i<records.length();i++){
                JSONObject incoming=records.optJSONObject(i);
                if(incoming==null){ignored++;continue;}
                String canonical=normalizedEventUrl(incoming.optString("url"));
                if(canonical.isEmpty() || incoming.optString("title").trim().isEmpty()){
                    ignored++;continue;
                }
                incoming.put("url",canonical);
                JSONObject existing=findMatchingShotgunEvent(incoming);
                if(existing==null) {
                    store.saveEvent(incoming);
                    added++;
                    continue;
                }
                if(mergeShotgunFields(existing,incoming)) {
                    store.saveEvent(existing);
                    updated++;
                } else unchanged++;
            }
            showHome();
            new AlertDialog.Builder(this).setTitle("Import Shotgun terminé")
                .setMessage(added+" soirée(s) ajoutée(s)\n"+
                    updated+" soirée(s) complétée(s) avec leurs informations\n"+
                    unchanged+" déjà à jour, "+ignored+" ignorée(s).\n\n"+
                    "Les notes personnelles et toutes les consommations existantes sont conservées. "+
                    "Les valeurs que tu as déjà saisies ne sont pas remplacées.")
                .setPositiveButton("OK",null).show();
        }catch(Exception e){message("Import impossible : "+e.getMessage());}
    }
    private JSONObject findMatchingShotgunEvent(JSONObject incoming) {
        String url=incoming.optString("url");
        for(JSONObject existing:store.events()){
            String previous=normalizedEventUrl(existing.optString("url"));
            if(!previous.isEmpty() && previous.equalsIgnoreCase(url))return existing;
        }
        // Anciennes fiches importées sans lien exact : utiliser titre+date uniquement
        // en cas d'une correspondance unique (jamais fusionner deux soirées homonymes).
        String title=normalizeTitle(incoming.optString("title"));
        String date=incoming.optString("date");
        JSONObject match=null;
        for(JSONObject existing:store.events()){
            if(!existing.optString("origin").toLowerCase(java.util.Locale.ROOT).contains("shotgun"))
                continue;
            if(!normalizeTitle(existing.optString("title")).equals(title))continue;
            if(date.isEmpty() || existing.optString("date").isEmpty() ||
                !date.equals(existing.optString("date")))continue;
            if(match!=null)return null;
            match=existing;
        }
        return match;
    }
    private String normalizeTitle(String title) {
        return java.text.Normalizer.normalize(title.toLowerCase(java.util.Locale.ROOT),
            java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("[^a-z0-9]+"," ").trim();
    }
    private boolean mergeShotgunFields(JSONObject existing,JSONObject incoming) throws Exception {
        // Ne pas écraser les champs personnalisés ni l'identifiant de la soirée.
        String[] fields={"poster","description","venue","city","address","organizer",
            "artists","genres","date","start","end","endDate","url"};
        boolean changed=false;
        for(String field:fields) {
            String newValue=incoming.optString(field).trim();
            String oldValue=existing.optString(field).trim();
            if(!newValue.isEmpty() && oldValue.isEmpty()) {
                existing.put(field,newValue);
                changed=true;
            }
        }
        if(ArtistProfileMerger.merge(existing,incoming))changed=true;
        return changed;
    }
    private String normalizedEventUrl(String url) {
        try {
            Uri u=Uri.parse(url);
            String host=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme()) || host==null ||
                !(host.equalsIgnoreCase("shotgun.live") || host.equalsIgnoreCase("www.shotgun.live")))
                return "";
            return u.buildUpon().clearQuery().fragment(null).build().toString();
        }catch(Exception e){return "";}
    }

    @Override protected void onResume() {
        super.onResume();
        if(updater!=null){updater.onResume();updater.checkAutomatically();}
        AutoSyncScheduler.schedule(this);
        syncActiveView=true;
        syncHandler.removeCallbacks(syncTicker);
        syncHandler.post(syncTicker);
    }
    @Override protected void onPause() {
        syncActiveView=false;
        syncHandler.removeCallbacks(syncTicker);
        super.onPause();
    }
    // Ouvre la sauvegarde directement depuis le diagnostic du bouton MAJ.
    void openBackupSettings() { showSettings(); }

    private void showSettings() {
        screen("Confidentialité et sauvegarde",this::showHome);
        subtitle("Les consommations sont stockées localement sur cet appareil. Aucun compte Shotgun ou Gmail n'est nécessaire.");
        add(page,button("↻ Rechercher une mise à jour",false,()->updater.check()),10);
        add(page,button("Importer une soirée par lien Shotgun",false,this::importShotgun),10);
        add(page,button("Exporter toutes mes données (JSON)",true,()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,"afterlog-sauvegarde.json");
            startActivityForResult(intent,EXPORT);
        }),10);
        add(page,button("Importer une sauvegarde JSON",false,()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent,IMPORT);
        }),14);
        subtitle("Attention : la sauvegarde JSON actuelle n'inclut pas les fichiers des photos personnelles. Conserve les photos originales sur ton téléphone. Le journal JSON n'est pas chiffré.");
        subtitle("Après connexion à Shotgun, AfterLog recherche « Mes billets → Passés » et affiche les événements à cocher avant de synchroniser. La disponibilité dépend du site et de ta session.");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==SHOTGUN_EVENT_REFRESH){
            if(resultCode==RESULT_OK && data!=null){
                try{
                    JSONArray found=new JSONArray(data.getStringExtra(ShotgunBrowserActivity.EXTRA_EVENTS));
                    JSONObject current=store.event(currentEventId);
                    if(current!=null && found.length()>0){
                        JSONObject incoming=found.optJSONObject(0);
                        if(incoming!=null && mergeShotgunFields(current,incoming)){
                            store.saveEvent(current);
                            message("Fiche et artistes complétés depuis Shotgun.");
                        }else message("Aucune photo supplémentaire détectée sur Shotgun.");
                    }
                }catch(Exception error){message("Extraction Shotgun incomplète.");}
            }
            showEvent(currentEventId);
            return;
        }
        if(requestCode==EVENT_EDITOR_REQUEST) {
            if(resultCode==RESULT_OK && data!=null) {
                long id=data.getLongExtra(EventEditorActivity.EXTRA_SAVED_ID,0);
                store=new AppStore(this);
                if(id>0)showEvent(id);else showHome();
            }else if(currentEventId>0)showEvent(currentEventId);
            else showHome();
            return;
        }
        if(requestCode==EVENT_MEDIA_REQUEST){
            if(resultCode!=RESULT_OK||data==null)return;
            ArrayList<Uri> sources=new ArrayList<>();
            if(data.getClipData()!=null){
                for(int i=0;i<data.getClipData().getItemCount();i++)
                    sources.add(data.getClipData().getItemAt(i).getUri());
            }else if(data.getData()!=null)sources.add(data.getData());
            if(sources.isEmpty())return;
            long target=currentEventId;
            message("Importation de "+sources.size()+" média(s) dans AfterLog…");
            new Thread(()->{
                int added=0,errors=0;
                String failure="";
                try{
                    AppStore localStore=new AppStore(getApplicationContext());
                    JSONObject event=localStore.event(target);
                    if(event==null)throw new Exception("Soirée introuvable");
                    for(Uri uri:sources){
                        try {
                            String mime=getContentResolver().getType(uri);
                            if(mime!=null&&mime.startsWith("image/")){
                                if(EventPhotos.gallery(event).length()>=40)
                                    throw new Exception("40 photos maximum");
                                EventPhotos.append(event,EventPhotos.importUri(this,uri));
                            }else{
                                if(EventVideos.list(event).length()>=EventVideos.MAX_COUNT)
                                    throw new Exception("30 vidéos maximum");
                                EventVideos.append(event,EventVideos.importVideo(this,uri));
                            }
                            added++;
                        }catch(Exception e){
                            errors++;
                            failure=e.getMessage();
                        }
                    }
                    if(added>0)localStore.saveEvent(event);
                }catch(Exception e){errors++;failure=e.getMessage();}
                final int imported=added,rejected=errors;
                final String errorText=failure;
                runOnUiThread(()->{
                    if(isFinishing())return;
                    store=new AppStore(this);
                    if(eventScreen&&currentEventId==target)showEvent(target);
                    message(imported+" média(s) ajouté(s)"+
                        (rejected>0?" · "+rejected+" non importé(s) : "+errorText:""));
                });
            },"AfterLog-media-import").start();
            return;
        }
        if(requestCode==EVENT_VIDEOS_REQUEST){
            if(resultCode==RESULT_OK && data!=null){
                JSONObject current=store.event(currentEventId);
                if(current!=null){
                    int added=0,errors=0;
                    String errorText="";
                    int length=data.getClipData()==null?1:data.getClipData().getItemCount();
                    for(int i=0;i<length&&i<12;i++){
                        Uri uri=data.getClipData()==null?data.getData():
                            data.getClipData().getItemAt(i).getUri();
                        try{
                            if(EventVideos.list(current).length()>=EventVideos.MAX_COUNT)break;
                            EventVideos.append(current,EventVideos.importVideo(this,uri));
                            added++;
                        }catch(Exception ex){errors++;errorText=ex.getMessage();}
                    }
                    store.saveEvent(current);
                    message(added+" vidéo(s) ajoutée(s)"+
                        (errors>0?" · "+errors+" erreur(s) : "+errorText:""));
                    showEvent(currentEventId);
                }
            }
            return;
        }
        if(requestCode==EVENT_IMAGES_REQUEST){
            if(resultCode==RESULT_OK && data!=null){
                JSONObject current=store.event(currentEventId);
                if(current!=null){
                    int added=0,errors=0;
                    int length=data.getClipData()==null?1:data.getClipData().getItemCount();
                    for(int i=0;i<length&&i<40;i++){
                        Uri uri=data.getClipData()==null?data.getData():data.getClipData().getItemAt(i).getUri();
                        try{
                            if(EventPhotos.gallery(current).length()>=40)break;
                            EventPhotos.append(current,EventPhotos.importUri(this,uri));
                            added++;
                        }catch(Exception ex){errors++;}
                    }
                    store.saveEvent(current);
                    message(added+" photo(s) ajoutée(s)"+(errors>0?" · "+errors+" ignorée(s)":""));
                    showEvent(currentEventId);
                }
            }
            return;
        }
        if(requestCode==CONSUMPTION_REQUEST){
            if(resultCode==RESULT_OK) {
                store=new AppStore(this);
                if(currentEventId>0)showEvent(currentEventId);
            }
            return;
        }
        if(requestCode==SHOTGUN_REQUEST && resultCode==RESULT_OK && data==null){
            showHome();
            return;
        }
        if(resultCode!=RESULT_OK || data==null)return;
        if(requestCode==SHOTGUN_REQUEST){receiveShotgunEvents(data);return;}
        if(data.getData()==null)return;
        Uri uri=data.getData();
        try {
            if(requestCode==EXPORT) {
                try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                    if(out==null)throw new IllegalStateException("Fichier inaccessible");
                    out.write(store.backup().getBytes(StandardCharsets.UTF_8));
                }
                message("Sauvegarde exportée");
            } else if(requestCode==IMPORT) {
                StringBuilder json=new StringBuilder();
                try(InputStream in=getContentResolver().openInputStream(uri);
                    BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
                    char[] buf=new char[4096]; int n;
                    while((n=reader.read(buf))!=-1) {
                        json.append(buf,0,n);
                        if(json.length()>20_000_000)throw new IllegalArgumentException("Fichier trop grand");
                    }
                }
                String content=json.toString();
                new AlertDialog.Builder(this).setTitle("Remplacer les données ?")
                    .setMessage("Cette sauvegarde remplacera entièrement ton journal actuel.")
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Importer",(d,w)->{
                        try {store.restore(content);message("Données restaurées");showHome();}
                        catch(Exception ex){message("Erreur de sauvegarde : "+ex.getMessage());}
                    }).show();
            }
        }catch(Exception ex){message("Erreur : "+ex.getMessage());}
    }
    @Override public void onBackPressed() {
        if(backAction!=null)backAction.run();
        else super.onBackPressed();
    }
    private void loadImage(ImageView image,String url) {
        if(!url.startsWith("https://"))return;
        new Thread(()->{
            HttpURLConnection connection=null;
            try {
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(8000);connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                try(InputStream in=connection.getInputStream()) {
                    BitmapFactory.Options opts=new BitmapFactory.Options();
                    opts.inSampleSize=4;
                    Bitmap bmp=BitmapFactory.decodeStream(in,null,opts);
                    if(bmp!=null)runOnUiThread(()->image.setImageBitmap(bmp));
                }
            }catch(Exception ignored){}
            finally {if(connection!=null)connection.disconnect();}
        }).start();
    }
}
