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
    private static final int EXPORT = 51, IMPORT = 52, SHOTGUN_REQUEST = 53, CONSUMPTION_REQUEST = 54;

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
        if (back != null) add(outer, button("← Retour", false, back), 12);
        add(outer, text(title, 26, Color.WHITE, true), 6);
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

    private void renderSyncProgress() {
        if(syncPanel==null)return;
        JSONObject state=SyncState.read(this);
        String mode=state.optString("state",SyncState.IDLE);
        if(SyncState.IDLE.equals(mode)) {
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
        screen("AfterLog", null);
        syncPanel=column();
        syncLastSignature="";
        add(page,syncPanel,8);
        renderSyncProgress();
        subtitle("Ton journal personnel de soirées. Données conservées sur ton téléphone.");
        add(page, button("＋  Ajouter une soirée", true, () -> editEvent(new JSONObject())),10);
        add(page, button("🔐  Connexion Shotgun · Mes billets", true, this::connectShotgun),10);
        add(page, button("↗  Importer un lien Shotgun", false, this::importShotgun),10);
        add(page, button("Statistiques", false, this::showStats),10);
        add(page, button("↻  Rechercher une mise à jour", false, () -> updater.check()),10);
        add(page, button("Réglages et sauvegarde", false, this::showSettings),24);
        List<JSONObject> events = store.events();
        add(page,text("MES SOIRÉES  •  "+events.size(),15,ACCENT,true),13);
        if(events.isEmpty()) {
            LinearLayout c=card();
            add(c,text("Aucune soirée enregistrée",18,Color.WHITE,true),8);
            add(c,text("Ajoute une soirée manuellement ou colle son lien Shotgun.",14,SOFT,false),0);
            add(page,c,12);
        }
        for(JSONObject e:events) {
            LinearLayout c=card();
            String url=e.optString("poster");
            if(!url.isEmpty()) {
                ImageView cover=new ImageView(this);
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setBackground(rounded(Color.rgb(40,40,60),12));
                LinearLayout.LayoutParams imageLp=new LinearLayout.LayoutParams(-1,dp(155));
                imageLp.bottomMargin=dp(12); c.addView(cover,imageLp);
                loadImage(cover,url);
            }
            add(c,text(e.optString("title","Soirée"),19,Color.WHITE,true),7);
            add(c,text(e.optString("date")+"   "+e.optString("start"),14,ACCENT,false),6);
            add(c,text((e.optString("venue")+" · "+e.optString("city")).replaceAll("^ · | · $",""),14,SOFT,false),7);
            add(c,text("Voir la soirée et mes consommations ›",13,Color.WHITE,true),0);
            c.setOnClickListener(v -> showEvent(e.optLong("id")));
            add(page,c,12);
        }
    }

    private void showEvent(long id) {
        JSONObject event=store.event(id);
        if(event==null){showHome();return;}
        currentEventId=id;
        screen("Événement",this::showHome);
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

        add(page,button("＋  Ajouter une consommation",true,()->editEntry(new JSONObject(),id)),10);
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
            addChips(event.optString("artists"),"🎧");
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

        add(page,text("Mon journal de soirée",22,Color.WHITE,true),5);
        add(page,text("Consommations et notes personnelles · visibles uniquement dans AfterLog",13,SOFT,false),14);
        List<JSONObject> entries=store.entries(id);
        if(entries.isEmpty()) {
            LinearLayout empty=card();
            add(empty,text("Aucune consommation enregistrée",15,SOFT,false),0);
            add(page,empty,12);
        }
        for(JSONObject entry:entries) {
            LinearLayout c=card();
            add(c,text(ConsumptionActivity.iconFor(entry.optString("category"),entry.optString("name"))+
                "  "+entry.optString("name","Consommation"),18,Color.WHITE,true),6);
            add(c,text(entry.optString("category")+" · "+
                entry.optString("quantity","1")+" "+entry.optString("unit"),14,ACCENT,false),6);
            add(c,text(entry.optString("date")+" · "+
                (entry.optBoolean("allNight",false)?"Pendant toute la soirée":entry.optString("time")),13,SOFT,false),6);
            if(entry.optDouble("cost",0)!=0)
                add(c,text(String.format(Locale.FRANCE,"%.2f €",entry.optDouble("cost")),14,SOFT,false),6);
            if(!entry.optString("notes").isEmpty())
                add(c,text(entry.optString("notes"),14,SOFT,false),7);
            add(c,text("Modifier ›",13,Color.WHITE,true),0);
            c.setOnClickListener(v->editEntry(entry,id));
            add(page,c,10);
        }
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
        long id=original.optLong("id");
        screen(id==0?"Nouvelle soirée":"Modifier la soirée",()->{
            if(id==0)showHome();else showEvent(id);
        });
        LinkedHashMap<String,EditText> fields=new LinkedHashMap<>();
        String[][] definitions={
            {"title","Nom de l'événement"},{"date","Date (AAAA-MM-JJ)"},
            {"start","Heure de début (HH:mm)"},{"end","Heure de fin (HH:mm)"},
            {"venue","Lieu"},{"city","Ville"},{"address","Adresse"},
            {"artists","Artistes / line-up"},{"organizer","Organisateur"},
            {"genres","Genres musicaux"},{"poster","Lien de l'affiche"},
            {"url","Lien Shotgun"},{"description","Description"},
            {"notes","Mes notes personnelles"}
        };
        for(String[] def:definitions) {
            boolean multi=def[0].equals("description")||def[0].equals("notes");
            String defaultValue=def[0].equals("date") && id==0 ? dateNow() : "";
            fields.put(def[0],field(page,def[1],original.optString(def[0],defaultValue),multi));
        }
        add(page,button("Enregistrer la soirée",true,()->{
            try {
                JSONObject e=new JSONObject(original.toString());
                for(Map.Entry<String,EditText> item:fields.entrySet())
                    e.put(item.getKey(),item.getValue().getText().toString().trim());
                if(e.optString("title").isEmpty()){message("Indique un nom");return;}
                if(e.optString("origin").isEmpty())e.put("origin","Manuel");
                JSONObject saved=store.saveEvent(e);
                showEvent(saved.getLong("id"));
            } catch(Exception ex){message("Enregistrement impossible : "+ex.getMessage());}
        }),0);
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
        subtitle("Attention : la sauvegarde JSON exportée contient ton journal en clair. Garde-la dans un emplacement privé. L'import remplace toutes les données actuelles.");
        subtitle("Connecte-toi à Shotgun dans le navigateur intégré. Ouvre « Mes billets → Passés », puis importe les liens affichés. Une synchronisation complète automatique n’est pas encore disponible.");
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==CONSUMPTION_REQUEST){
            if(resultCode==RESULT_OK)showEvent(currentEventId);
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
