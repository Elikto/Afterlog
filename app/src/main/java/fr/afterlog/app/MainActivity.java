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
    private final Handler syncHandler=new Handler(Looper.getMainLooper());
    private LinearLayout syncPanel;
    private String syncLastSignature="";
    private boolean syncActiveView=false;
    private long observedSyncEnd=0;
    private final Runnable syncTicker=new Runnable(){
        @Override public void run(){
            if(!syncActiveView)return;
            renderSyncProgress();
            syncHandler.postDelayed(this,1500);
        }
    };
    private static final int EXPORT = 51, IMPORT = 52, SHOTGUN_REQUEST = 53, CONSUMPTION_REQUEST = 54,
        EVENT_EDITOR_REQUEST=55,EVENT_IMAGES_REQUEST=56;

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

    private void renderSyncProgress() {
        if(syncPanel==null)return;
        JSONObject state=SyncState.read(this);
        String mode=state.optString("state",SyncState.IDLE);
        if(SyncState.IDLE.equals(mode) ||
          (SyncState.DONE.equals(mode) && SyncFailureStore.list(this).length()==0 &&
           !state.optString("phase").toLowerCase(Locale.ROOT).contains("partiel") &&
           !state.optString("detail").toLowerCase(Locale.ROOT).contains("non récupéré"))) {
            syncPanel.removeAllViews();
            syncPanel.setVisibility(View.GONE);
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
        TextView stats=button("Statistiques",false,this::showStats);
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
        JSONObject event=store.event(id);
        if(event==null){showHome();return;}
        currentEventId=id;
        screen("Événement",this::showHome);
        renderJournalFirst(id);
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
    private void renderJournalFirst(long id) {
        add(page,text("Mon journal de soirée",22,Color.WHITE,true),6);
        add(page,text("Tes consommations · enregistrées uniquement sur ton téléphone",13,SOFT,false),12);
        add(page,button("＋ Ajouter une consommation",true,()->editEntry(new JSONObject(),id)),14);
        List<JSONObject> entries=store.entries(id);
        if(entries.isEmpty()){
            LinearLayout empty=card();
            add(empty,text("Aucune consommation enregistrée pour cette soirée.",14,SOFT,false),0);
            add(page,empty,15);
        }
        for(JSONObject entry:entries){
            LinearLayout c=card();
            add(c,text(ConsumptionActivity.iconFor(entry.optString("category"),entry.optString("name"))+
                "  "+entry.optString("name","Consommation"),18,Color.WHITE,true),7);
            JSONObject parts=entry.optJSONObject("parts");
            String countLabel="";
            if(parts!=null){
                ArrayList<String> labels=new ArrayList<>();
                int whole=parts.optInt("entier"),half=parts.optInt("demi"),quarter=parts.optInt("quart");
                if(whole>0)labels.add(whole+" entier"+(whole>1?"s":""));
                if(half>0)labels.add(half+" demi");
                if(quarter>0)labels.add(quarter+" quart"+(quarter>1?"s":""));
                countLabel=android.text.TextUtils.join(" + ",labels);
            }
            if(countLabel.isEmpty())countLabel=entry.optString("quantity","1")+" "+entry.optString("unit");
            String route=entry.optString("route");
            if(route.contains("(para)"))countLabel+=" · para";
            else if(route.contains("(trace)"))countLabel+=" · trace";
            add(c,text(countLabel,15,ACCENT,true),6);
            String measured=entry.optString("measuredAmount");
            if(!measured.isEmpty())add(c,text("Quantité déclarée : "+measured+" "+
                entry.optString("measuredUnit"),13,SOFT,false),5);
            String size=entry.optString("subjectiveSize");
            if(!size.isEmpty())add(c,text("Taille apparente : "+size,13,SOFT,false),5);
            String timing=entry.optString("timeMode",entry.optBoolean("allNight")?"all":"exact");
            String when=timing.equals("all")?"Pendant toute la soirée":
                timing.equals("between")?"Entre "+entry.optString("time")+" et "+entry.optString("timeEnd"):
                "Heure exacte : "+entry.optString("time");
            add(c,text(entry.optString("date")+" · "+when,13,SOFT,false),6);
            if(!route.isEmpty())add(c,text("Mode : "+("Autre".equals(route)?
                entry.optString("routeOther","Autre"):route),13,SOFT,false),5);
            if(entry.optDouble("cost",0)>0)
                add(c,text(String.format(Locale.FRANCE,"%.2f €",entry.optDouble("cost")),14,SOFT,false),5);
            if(!entry.optString("notes").isEmpty())add(c,text(entry.optString("notes"),14,SOFT,false),7);
            add(c,text("Modifier ›",13,Color.WHITE,true),0);
            c.setOnClickListener(v->editEntry(entry,id));
            add(page,c,10);
        }
        add(page,text("Informations de l'événement",21,Color.WHITE,true),12);
    }

    private void renderArtistProfiles(JSONObject event) {
        JSONArray profiles=event.optJSONArray("artistProfiles");
        if(profiles==null||profiles.length()==0){
            addChips(event.optString("artists"),"🎧");
            return;
        }
        for(int i=0;i<profiles.length();i++){
            JSONObject profile=profiles.optJSONObject(i);
            if(profile==null)continue;
            String name=profile.optString("name").trim();
            if(name.isEmpty())continue;
            LinearLayout card=card();
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            add(card,row,0);
            String photo=profile.optString("photo");
            if(photo.startsWith("https://")){
                ImageView image=new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setBackground(rounded(Color.rgb(35,38,53),12));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(64),dp(64));
                lp.rightMargin=dp(13);
                row.addView(image,lp);loadImage(image,photo);
            }
            LinearLayout info=column();
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            add(info,text(name,17,Color.WHITE,true),5);
            String spotify=profile.optString("spotify");
            boolean verified=spotify.matches("^https://open\\.spotify\\.com/artist/[A-Za-z0-9]+.*");
            String target=verified?spotify:"https://open.spotify.com/search/"+Uri.encode(name);
            add(info,text(verified?"Ouvrir l'artiste sur Spotify ↗":
                "Rechercher l'artiste sur Spotify ↗",13,ACCENT,false),0);
            card.setOnClickListener(v->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(target)));}
                catch(Exception e){message("Spotify n'a pas pu être ouvert.");}
            });
            add(page,card,10);
        }
    }

    private void renderFailedEvents(LinearLayout panel) {
        JSONArray failures=SyncFailureStore.list(this);
        if(failures.length()==0)return;
        add(panel,text("Soirées non récupérées · "+failures.length(),18,Color.WHITE,true),8);
        add(panel,text("Voici les soirées que Shotgun n'a pas permis de compléter.",13,SOFT,false),12);
        for(int i=0;i<failures.length();i++){
            JSONObject item=failures.optJSONObject(i);
            if(item==null)continue;
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

    private void renderEventPhotos(JSONObject event) {
        add(page,button("📸  Ajouter des photos à cette soirée",false,()->{
            currentEventId=event.optLong("id");
            Intent picker=new Intent(Intent.ACTION_GET_CONTENT);
            picker.setType("image/*");
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
            startActivityForResult(Intent.createChooser(picker,"Photos de la soirée"),EVENT_IMAGES_REQUEST);
        }),13);
        JSONArray items=EventPhotos.gallery(event);
        if(items.length()==0)return;
        add(page,text("Mes photos · "+items.length(),18,Color.WHITE,true),10);
        for(int start=0;start<items.length();start+=3){
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            add(page,row,8);
            for(int j=0;j<3;j++){
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(105),1);
                lp.setMargins(dp(3),0,dp(3),0);
                if(start+j>=items.length()){row.addView(new View(this),lp);continue;}
                String name=items.optString(start+j);
                ImageView photo=new ImageView(this);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photo.setBackground(rounded(PANEL,12));
                android.graphics.Bitmap bitmap=EventPhotos.preview(this,name,260);
                if(bitmap!=null)photo.setImageBitmap(bitmap);
                row.addView(photo,lp);
                photo.setOnClickListener(v->new AlertDialog.Builder(this)
                    .setTitle("Photo de la soirée")
                    .setMessage("Tu peux agrandir ou retirer cette photo dans « Modifier les informations ».")
                    .setPositiveButton("Modifier la soirée",(d,w)->editEvent(event))
                    .setNegativeButton("Fermer",null).show());
            }
        }
        add(page,new View(this),13);
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
        if(existing.optJSONArray("artistProfiles")==null &&
            incoming.optJSONArray("artistProfiles")!=null) {
            existing.put("artistProfiles",incoming.getJSONArray("artistProfiles"));
            changed=true;
        }
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
        if(requestCode==EVENT_EDITOR_REQUEST) {
            if(resultCode==RESULT_OK && data!=null) {
                long id=data.getLongExtra(EventEditorActivity.EXTRA_SAVED_ID,0);
                store=new AppStore(this);
                if(id>0)showEvent(id);else showHome();
            }else if(currentEventId>0)showEvent(currentEventId);
            else showHome();
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
            if(resultCode==RESULT_OK)showEvent(currentEventId);
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
